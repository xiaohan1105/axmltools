package red.jiuzhou.theme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 批量转换引擎
 *
 * 负责将主题应用到多个XML文件，支持：
 * - 事务性操作（全成功或全回滚）
 * - 并行处理
 * - 进度追踪
 * - 错误收集
 *
 * @author Claude
 * @version 1.0
 */
public class BatchTransformEngine {

    private static final Logger log = LoggerFactory.getLogger(BatchTransformEngine.class);

    private final ExecutorService executorService;
    private final Map<String, String> transformCache = new ConcurrentHashMap<>();

    public BatchTransformEngine(int maxConcurrency) {
        this.executorService = Executors.newFixedThreadPool(maxConcurrency);
    }

    /**
     * 应用主题到文件列表
     *
     * @param theme 主题
     * @param files 目标文件列表
     * @param progressCallback 进度回调
     * @return 转换结果
     */
    public TransformResult applyTheme(Theme theme, List<Path> files,
                                      Consumer<TransformProgress> progressCallback) {
        log.info("开始应用主题: {} 到 {} 个文件", theme.getName(), files.size());

        TransformResult result = new TransformResult(theme.getId(), Instant.now());
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // 清空缓存
        if (!theme.getSettings().isCacheAiResults()) {
            transformCache.clear();
        }

        // 第一阶段：预检查
        Map<Path, Path> backupMap = new HashMap<>();
        try {
            if (theme.getSettings().isBackupBeforeApply()) {
                for (Path file : files) {
                    Path backup = createBackup(file);
                    backupMap.put(file, backup);
                }
                log.info("已创建 {} 个文件备份", backupMap.size());
            }
        } catch (Exception e) {
            log.error("创建备份失败", e);
            result.addError("备份失败: " + e.getMessage());
            return result;
        }

        // 第二阶段：并行转换
        List<Future<FileTransformResult>> futures = new ArrayList<>();
        for (Path file : files) {
            Future<FileTransformResult> future = executorService.submit(() ->
                    transformFile(file, theme, processedCount, files.size(), progressCallback)
            );
            futures.add(future);
        }

        // 收集结果
        List<FileTransformResult> fileResults = new ArrayList<>();
        for (Future<FileTransformResult> future : futures) {
            try {
                FileTransformResult fileResult = future.get();
                fileResults.add(fileResult);

                if (fileResult.isSuccess()) {
                    successCount.incrementAndGet();
                } else {
                    result.addError(fileResult.getFile() + ": " + fileResult.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("获取转换结果失败", e);
                result.addError("转换失败: " + e.getMessage());
            }
        }

        result.setFileResults(fileResults);
        result.setTotalFiles(files.size());
        result.setSuccessfulFiles(successCount.get());
        result.setCompletedAt(Instant.now());

        // 第三阶段：验证和决策
        boolean shouldCommit = true;

        if (theme.getSettings().isTransactional()) {
            // 事务模式：只要有一个失败就回滚
            if (successCount.get() < files.size()) {
                shouldCommit = false;
                log.warn("事务模式下检测到失败，准备回滚");
            }
        } else if (theme.getSettings().isSkipOnError()) {
            // 跳过模式：失败的跳过，成功的提交
            shouldCommit = true;
        }

        // 第四阶段：提交或回滚
        if (shouldCommit) {
            log.info("转换成功，提交更改: {}/{} 个文件", successCount.get(), files.size());
            result.setStatus(TransformStatus.SUCCESS);
        } else {
            log.warn("转换失败，回滚更改");
            rollbackChanges(backupMap);
            result.setStatus(TransformStatus.ROLLED_BACK);
        }

        // 清理备份
        if (shouldCommit && theme.getSettings().isBackupBeforeApply()) {
            cleanupBackups(backupMap);
        }

        log.info("主题应用完成: 成功 {}, 失败 {}", successCount.get(), files.size() - successCount.get());
        return result;
    }

    /**
     * 转换单个文件
     * 读取XML文件,应用所有转换规则,然后写回文件
     *
     * @param file 文件路径
     * @param theme 主题
     * @param processedCount 已处理计数器
     * @param totalFiles 总文件数
     * @param progressCallback 进度回调
     * @return 文件转换结果
     */
    private FileTransformResult transformFile(Path file, Theme theme,
                                              AtomicInteger processedCount, int totalFiles,
                                              Consumer<TransformProgress> progressCallback) {
        FileTransformResult result = new FileTransformResult(file);

        try {
            // 读取XML
            Document doc = parseXmlFile(file);

            // 转换所有字段
            int changedCount = transformDocument(doc, file, theme);

            // 写回文件
            if (changedCount > 0) {
                writeXmlFile(doc, file);
                result.setSuccess(true);
                result.setChangedFields(changedCount);
            } else {
                result.setSuccess(true);
                result.setChangedFields(0);
            }

        } catch (Exception e) {
            log.error("转换文件失败: {}", file, e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        } finally {
            int processed = processedCount.incrementAndGet();
            if (progressCallback != null) {
                progressCallback.accept(new TransformProgress(processed, totalFiles, file));
            }
        }

        return result;
    }

    /**
     * 转换Document中的所有字段
     * 遍历所有XML元素和属性,应用匹配的转换规则
     *
     * @param doc XML文档对象
     * @param file 文件路径
     * @param theme 主题
     * @return 实际修改的字段数量
     */
    private int transformDocument(Document doc, Path file, Theme theme) {
        int changedCount = 0;
        String filePath = file.toString();

        // 获取所有元素
        NodeList allElements = doc.getElementsByTagName("*");

        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            NamedNodeMap attributes = element.getAttributes();

            // 提取记录数据（用于上下文）
            Map<String, String> recordData = new HashMap<>();
            for (int j = 0; j < attributes.getLength(); j++) {
                Attr attr = (Attr) attributes.item(j);
                recordData.put(attr.getName(), attr.getValue());
            }

            String recordId = recordData.get("id");

            // 转换每个属性
            for (int j = 0; j < attributes.getLength(); j++) {
                Attr attr = (Attr) attributes.item(j);
                String fieldName = attr.getName();
                String originalValue = attr.getValue();

                // 查找匹配的规则
                for (TransformRule rule : theme.getRules()) {
                    if (rule.matches(filePath, fieldName)) {
                        try {
                            // 检查缓存
                            String cacheKey = null;
                            if (theme.getSettings().isCacheAiResults()) {
                                cacheKey = rule.getName() + ":" + originalValue;
                                String cached = transformCache.get(cacheKey);
                                if (cached != null) {
                                    attr.setValue(cached);
                                    changedCount++;
                                    break;
                                }
                            }

                            // 执行转换
                            TransformRule.TransformContext context = new TransformRule.TransformContext(
                                    filePath, fieldName, recordId, recordData, theme.getSettings()
                            );

                            String transformedValue = rule.transform(originalValue, context);

                            // 验证结果
                            if (rule.validate(originalValue, transformedValue)) {
                                attr.setValue(transformedValue);
                                changedCount++;

                                // 缓存结果
                                if (cacheKey != null && theme.getSettings().isCacheAiResults()) {
                                    transformCache.put(cacheKey, transformedValue);
                                }
                            }

                            break; // 一个字段只应用一个规则
                        } catch (Exception e) {
                            log.warn("应用规则失败: {} on {}={}", rule.getName(), fieldName, originalValue, e);
                            if (!theme.getSettings().isSkipOnError()) {
                                throw e;
                            }
                        }
                    }
                }
            }
        }

        return changedCount;
    }

    /**
     * 创建文件备份
     * 将文件复制到.theme-backup目录,文件名包含时间戳
     *
     * @param file 要备份的文件
     * @return 备份文件路径
     * @throws IOException 如果创建备份失败
     */
    private Path createBackup(Path file) throws IOException {
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-");
        Path backupDir = file.getParent().resolve(".theme-backup");
        Files.createDirectories(backupDir);

        Path backupFile = backupDir.resolve(file.getFileName() + "." + timestamp + ".bak");
        Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);

        return backupFile;
    }

    /**
     * 回滚更改
     * 从备份文件恢复所有被修改的文件
     *
     * @param backupMap 备份映射表(原文件 -> 备份文件)
     */
    private void rollbackChanges(Map<Path, Path> backupMap) {
        for (Map.Entry<Path, Path> entry : backupMap.entrySet()) {
            try {
                Files.copy(entry.getValue(), entry.getKey(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("回滚失败: {}", entry.getKey(), e);
            }
        }
    }

    /**
     * 清理备份文件
     * 删除临时备份文件以释放磁盘空间
     *
     * @param backupMap 备份映射表
     */
    private void cleanupBackups(Map<Path, Path> backupMap) {
        for (Path backup : backupMap.values()) {
            try {
                Files.deleteIfExists(backup);
            } catch (IOException e) {
                log.warn("删除备份失败: {}", backup, e);
            }
        }
    }

    /**
     * 解析XML文件
     * 自动检测文件编码(UTF-8或UTF-16)
     *
     * @param file 文件路径
     * @return 解析后的Document对象
     * @throws Exception 如果解析失败
     */
    private Document parseXmlFile(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        // 尝试检测编码
        byte[] bytes = Files.readAllBytes(file);
        String content = new String(bytes, StandardCharsets.UTF_8);

        // 如果包含UTF-16 BOM，使用UTF-16
        if (content.startsWith("\uFEFF") || bytes.length >= 2 &&
                ((bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) ||
                        (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF))) {
            content = new String(bytes, StandardCharsets.UTF_16);
        }

        return builder.parse(new InputSource(new StringReader(content)));
    }

    /**
     * 写入XML文件
     * 先写入临时文件再原子性移动,确保数据安全
     *
     * @param doc 文档对象
     * @param file 目标文件路径
     * @throws Exception 如果写入失败
     */
    private void writeXmlFile(Document doc, Path file) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);

        // 写入到临时文件，然后移动（原子操作）
        Path tempFile = file.getParent().resolve(file.getFileName() + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(tempFile.toFile()), StandardCharsets.UTF_16)) {
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
        }

        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 转换进度
     */
    public static class TransformProgress {
        private final int processed;
        private final int total;
        private final Path currentFile;

        public TransformProgress(int processed, int total, Path currentFile) {
            this.processed = processed;
            this.total = total;
            this.currentFile = currentFile;
        }

        public int getProcessed() { return processed; }
        public int getTotal() { return total; }
        public Path getCurrentFile() { return currentFile; }
        public double getPercentage() { return (double) processed / total * 100; }
    }

    /**
     * 单个文件的转换结果
     */
    public static class FileTransformResult {
        private final Path file;
        private boolean success;
        private int changedFields;
        private String errorMessage;

        public FileTransformResult(Path file) {
            this.file = file;
        }

        // Getters and setters
        public Path getFile() { return file; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getChangedFields() { return changedFields; }
        public void setChangedFields(int count) { this.changedFields = count; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String msg) { this.errorMessage = msg; }
    }

    /**
     * 批量转换结果
     */
    public static class TransformResult {
        private final String themeId;
        private final Instant startedAt;
        private Instant completedAt;
        private TransformStatus status;
        private int totalFiles;
        private int successfulFiles;
        private List<FileTransformResult> fileResults;
        private List<String> errors;

        public TransformResult(String themeId, Instant startedAt) {
            this.themeId = themeId;
            this.startedAt = startedAt;
            this.status = TransformStatus.IN_PROGRESS;
            this.fileResults = new ArrayList<>();
            this.errors = new ArrayList<>();
        }

        public void addError(String error) {
            errors.add(error);
        }

        // Getters and setters
        public String getThemeId() { return themeId; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant time) { this.completedAt = time; }
        public TransformStatus getStatus() { return status; }
        public void setStatus(TransformStatus status) { this.status = status; }
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int count) { this.totalFiles = count; }
        public int getSuccessfulFiles() { return successfulFiles; }
        public void setSuccessfulFiles(int count) { this.successfulFiles = count; }
        public List<FileTransformResult> getFileResults() { return fileResults; }
        public void setFileResults(List<FileTransformResult> results) { this.fileResults = results; }
        public List<String> getErrors() { return errors; }

        public int getTotalChangedFields() {
            return fileResults.stream()
                    .mapToInt(FileTransformResult::getChangedFields)
                    .sum();
        }
    }

    /**
     * 转换状态
     */
    public enum TransformStatus {
        IN_PROGRESS("进行中"),
        SUCCESS("成功"),
        PARTIAL_SUCCESS("部分成功"),
        FAILED("失败"),
        ROLLED_BACK("已回滚");

        private final String displayName;

        TransformStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
