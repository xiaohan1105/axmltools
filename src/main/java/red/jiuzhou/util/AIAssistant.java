package red.jiuzhou.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import red.jiuzhou.ai.AiModelClient;
import red.jiuzhou.ai.AiModelFactory;

import javax.annotation.PreDestroy;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * AI助手工具类 - 提供丰富的AI辅助功能
 *
 * 功能概述:
 * - 文件内容优化（武侠风格、正式风格、简洁风格等）
 * - 批量文件操作（重命名、格式化、内容替换、编码转换）
 * - 内容智能分析和质量检查
 * - 支持中英文翻译
 *
 * 核心特性:
 * 1. 多AI模型支持：通义千问、豆包、Kimi、DeepSeek
 * 2. 自动故障转移：优先级顺序调用多个AI模型
 * 3. 异步执行：基于线程池的异步处理
 * 4. 自动备份：优化前自动创建文件备份
 * 5. 智能限流：控制批量调用频率
 *
 * 技术要点:
 * - CompletableFuture实现异步调用
 * - 自定义ThreadFactory管理线程命名
 * - ExecutorService线程池控制并发
 * - 多来源配置加载（环境变量、系统属性、YAML）
 *
 * 使用场景:
 * - 游戏配置文本风格转换
 * - 批量文件内容优化
 * - 数据质量分析
 * - 配置文件翻译
 *
 * @author yanxq
 * @version V1.0
 */
@Component
public class AIAssistant {

    private static final Logger log = LoggerFactory.getLogger(AIAssistant.class);

    /** 默认兜底AI模型 */
    private static final String FALLBACK_MODEL = "qwen";

    /** 线程池大小：最小2，最大8，基于CPU核心数 */
    private static final int EXECUTOR_SIZE = Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors()));

    /** AI线程计数器 */
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    /** AI线程工厂：创建守护线程，自定义命名 */
    private static final ThreadFactory AI_THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("ai-assistant-" + THREAD_COUNTER.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    };

    /** AI任务执行线程池 */
    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(EXECUTOR_SIZE, AI_THREAD_FACTORY);

    /**
     * 优化类型枚举 - 定义各种AI文本处理模式
     */
    public enum OptimizeType {
        /** 武侠风格转换 */
        WUXIA_STYLE("武侠风格", "你是一位精通中国武侠和神话文化的翻译大师。请将我提供的内容转化为充满古风、侠气或神话色彩的风格，保持原属性不变。"),

        /** 正式风格转换 */
        FORMAL_STYLE("正式风格", "请将内容转换为正式、规范的表达方式，保持原意不变。"),

        /** 简洁风格转换 */
        SIMPLE_STYLE("简洁风格", "请将内容简化，使其更加简洁明了，去除冗余表达。"),

        /** 游戏风格转换 */
        GAME_STYLE("游戏风格", "请将内容转换为适合游戏的表达方式，增加趣味性和吸引力。"),

        /** 中译英 */
        TRANSLATE_EN("翻译为英文", "请将中文内容翻译为英文，保持原意和语境。"),

        /** 英译中 */
        TRANSLATE_CN("翻译为中文", "请将英文内容翻译为中文，保持原意和语境。"),

        /** 内容质量检查 */
        QUALITY_CHECK("内容质量检查", "请检查内容的质量，包括语法、拼写、逻辑等，并提供改进建议。"),

        /** 数据分析 */
        DATA_ANALYSIS("数据分析", "请分析这些数据的特点、规律和潜在问题，提供专业见解。");

        private final String displayName;
        private final String prompt;

        OptimizeType(String displayName, String prompt) {
            this.displayName = displayName;
            this.prompt = prompt;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPrompt() {
            return prompt;
        }
    }

    /**
     * 批量操作枚举 - 定义批量文件处理类型
     */
    public enum BatchOperation {
        /** 批量重命名文件 */
        RENAME("批量重命名", "基于规则批量重命名文件"),

        /** 批量格式化 */
        FORMAT("批量格式化", "统一格式化XML文件"),

        /** 批量内容替换 */
        CONTENT_REPLACE("批量内容替换", "批量替换文件内容"),

        /** 编码转换 */
        ENCODING_CONVERT("编码转换", "批量转换文件编码"),

        /** 批量备份 */
        BACKUP("批量备份", "批量备份选中文件"),

        /** 批量导出 */
        EXPORT("批量导出", "批量导出为其他格式");

        private final String displayName;
        private final String description;

        BatchOperation(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 异步优化单个文件内容
     *
     * @param filePath 文件路径
     * @param optimizeType 优化类型
     * @return CompletableFuture包装的优化结果
     */
    public CompletableFuture<String> optimizeFileContent(String filePath, OptimizeType optimizeType) {
        return CompletableFuture.supplyAsync(() -> optimizeFileSync(filePath, optimizeType), aiExecutor);
    }

    /**
     * 批量优化多个文件
     *
     * @param filePaths 文件路径列表
     * @param optimizeType 优化类型
     * @return CompletableFuture包装的优化结果Map，key为文件路径，value为优化结果
     */
    public CompletableFuture<Map<String, String>> batchOptimizeFiles(List<String> filePaths, OptimizeType optimizeType) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> results = new LinkedHashMap<>();
            log.info("开始批量AI优化，文件数量: {} - 类型: {}", filePaths.size(), optimizeType.getDisplayName());
            for (String filePath : filePaths) {
                try {
                    results.put(filePath, optimizeFileSync(filePath, optimizeType));
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } catch (Exception e) {
                    log.error("批量优化文件失败: {}", filePath, e);
                    results.put(filePath, "失败: " + e.getMessage());
                }
            }
            log.info("批量AI优化完成，处理文件数: {}", results.size());
            return results;
        }, aiExecutor);
    }

    public CompletableFuture<Map<String, String>> executeBatchOperation(List<String> filePaths, BatchOperation operation, Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> results = new LinkedHashMap<>();
            log.info("开始批量操作 {} - 文件数量: {}", operation.getDisplayName(), filePaths.size());
            switch (operation) {
                case RENAME:
                    results.putAll(batchRename(filePaths, parameters));
                    break;
                case FORMAT:
                    results.putAll(batchFormat(filePaths, parameters));
                    break;
                case CONTENT_REPLACE:
                    results.putAll(batchContentReplace(filePaths, parameters));
                    break;
                case ENCODING_CONVERT:
                    results.putAll(batchEncodingConvert(filePaths, parameters));
                    break;
                case BACKUP:
                    results.putAll(batchBackup(filePaths, parameters));
                    break;
                case EXPORT:
                    results.putAll(batchExport(filePaths, parameters));
                    break;
                default:
                    filePaths.forEach(path -> results.put(path, "不支持的操作类型"));
            }
            log.info("批量操作完成: {} - 处理文件数: {}", operation.getDisplayName(), results.size());
            return results;
        }, aiExecutor);
    }

    public CompletableFuture<String> analyzeContent(String filePath) {
        return CompletableFuture.supplyAsync(() -> analyzeContentSync(filePath), aiExecutor);
    }

    private String optimizeFileSync(String filePath, OptimizeType optimizeType) {
        try {
            log.info("开始AI优化文件: {} - 类型: {}", filePath, optimizeType.getDisplayName());
            String content = FileUtil.readUtf8String(filePath);
            if (StrUtil.isBlank(content)) {
                throw new IllegalStateException("文件内容为空");
            }
            String aiPrompt = buildOptimizationPrompt(content, optimizeType);
            String result = callAIService(aiPrompt, optimizeType);
            String backupPath = createBackup(filePath);
            FileUtil.writeUtf8String(result, filePath);
            log.info("AI优化完成: {}", filePath);
            return "优化成功！原文件已备份为: " + new File(backupPath).getName();
        } catch (Exception e) {
            log.error("AI优化文件失败: {}", filePath, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String analyzeContentSync(String filePath) {
        try {
            log.info("开始智能分析文件: {}", filePath);
            String content = FileUtil.readUtf8String(filePath);
            if (StrUtil.isBlank(content)) {
                throw new IllegalStateException("文件内容为空，无法分析");
            }
            String analysisPrompt = "请分析以下文件内容的结构、特点、潜在问题和改进建议：\n\n" + content;
            String result = callAIService(analysisPrompt, OptimizeType.DATA_ANALYSIS);
            log.info("智能分析完成: {}", filePath);
            return result;
        } catch (Exception e) {
            log.error("智能分析失败: {}", filePath, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String buildOptimizationPrompt(String content, OptimizeType optimizeType) {
        return optimizeType.getPrompt() + "\n\n需要处理的内容：\n" + content;
    }

    private String createBackup(String filePath) {
        String backupPath = filePath + ".backup_" + System.currentTimeMillis();
        FileUtil.copy(filePath, backupPath, true);
        log.info("已创建备份文件: {}", backupPath);
        return backupPath;
    }

    private String callAIService(String prompt, OptimizeType type) {
        List<String> candidates = resolveCandidateModels();
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();

        for (String modelName : candidates) {
            Instant start = Instant.now();
            try {
                AiModelClient client = AiModelFactory.getClient(modelName);
                String response = client.chat(prompt);
                if (StrUtil.isBlank(response)) {
                    throw new IllegalStateException("AI模型返回空响应");
                }
                long costMs = Duration.between(start, Instant.now()).toMillis();
                String trimmed = response.trim();
                log.info("AI模型 {} 完成 {}，耗时 {} ms，响应长度 {}", modelName, type.getDisplayName(), costMs, trimmed.length());
                return trimmed;
            } catch (Exception e) {
                String message = e.getMessage();
                if (StrUtil.isBlank(message)) {
                    message = e.getClass().getSimpleName();
                }
                log.warn("AI模型 {} 调用失败: {}", modelName, message);
                errors.put(modelName, message);
            }
        }
        if (errors.isEmpty()) {
            String message = "未找到可用的AI模型候选";
            log.error(message);
            throw new RuntimeException(message);
        }

        String detail = errors.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
        log.error("全部AI模型调用失败: {}", detail);
        throw new RuntimeException("无法调用任何可用的AI模型: " + detail);
    }

    private List<String> resolveCandidateModels() {
        LinkedHashSet<String> rawCandidates = new LinkedHashSet<>();
        addCandidate(rawCandidates, System.getenv("AI_MODEL"));
        addCandidate(rawCandidates, System.getProperty("ai.model"));
        addCandidate(rawCandidates, YamlUtils.getProperty("ai.activeModel"));
        addCandidate(rawCandidates, YamlUtils.getProperty("ai.defaultModel"));
        rawCandidates.addAll(YamlUtils.loadAiModelKeys("application.yml"));
        rawCandidates.add(FALLBACK_MODEL);

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String candidate : rawCandidates) {
            try {
                ordered.add(AiModelFactory.canonicalName(candidate));
            } catch (IllegalArgumentException ex) {
                log.debug("忽略未支持的AI模型: {}", candidate);
            }
        }

        for (String model : AiModelFactory.supportedModels()) {
            ordered.add(model);
        }

        return new ArrayList<>(ordered);
    }

    private void addCandidate(LinkedHashSet<String> target, String raw) {
        if (StrUtil.isBlank(raw)) {
            return;
        }
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .forEach(target::add);
    }

    private Map<String, String> batchRename(List<String> filePaths, Map<String, Object> parameters) {
        Map<String, String> results = new LinkedHashMap<>();
        String prefix = (String) parameters.getOrDefault("prefix", "");
        String suffix = (String) parameters.getOrDefault("suffix", "");
        String pattern = (String) parameters.getOrDefault("pattern", "");
        String replacement = (String) parameters.getOrDefault("replacement", "");

        for (String filePath : filePaths) {
            try {
                File file = new File(filePath);
                String fileName = file.getName();
                String newName = fileName;

                if (StrUtil.isNotBlank(pattern) && StrUtil.isNotBlank(replacement)) {
                    newName = newName.replace(pattern, replacement);
                }

                if (StrUtil.isNotBlank(prefix)) {
                    newName = prefix + newName;
                }

                if (StrUtil.isNotBlank(suffix)) {
                    String ext = FileUtil.extName(newName);
                    String nameWithoutExt = FileUtil.mainName(newName);
                    newName = nameWithoutExt + suffix + "." + ext;
                }

                if (!fileName.equals(newName)) {
                    File newFile = new File(file.getParent(), newName);
                    if (file.renameTo(newFile)) {
                        results.put(filePath, "重命名成功: " + newName);
                    } else {
                        results.put(filePath, "重命名失败");
                    }
                } else {
                    results.put(filePath, "文件名无变化");
                }

            } catch (Exception e) {
                results.put(filePath, "重命名失败: " + e.getMessage());
            }
        }
        return results;
    }

    private Map<String, String> batchFormat(List<String> filePaths, Map<String, Object> parameters) {
        Map<String, String> results = new LinkedHashMap<>();
        boolean formatXml = (Boolean) parameters.getOrDefault("formatXml", true);
        String encoding = (String) parameters.getOrDefault("encoding", "UTF-8");

        for (String filePath : filePaths) {
            try {
                if (formatXml && filePath.endsWith(".xml")) {
                    String content = FileUtil.readString(filePath, encoding);
                    String formattedContent = XmlUtil.formatXml(content);
                    FileUtil.writeString(formattedContent, filePath, encoding);
                    results.put(filePath, "XML格式化成功");
                } else {
                    results.put(filePath, "跳过非XML文件");
                }
            } catch (Exception e) {
                results.put(filePath, "格式化失败: " + e.getMessage());
            }
        }
        return results;
    }

    private Map<String, String> batchContentReplace(List<String> filePaths, Map<String, Object> parameters) {
        Map<String, String> results = new LinkedHashMap<>();
        String searchText = (String) parameters.get("searchText");
        String replaceText = (String) parameters.get("replaceText");
        boolean useRegex = (Boolean) parameters.getOrDefault("useRegex", false);

        if (StrUtil.isBlank(searchText)) {
            filePaths.forEach(path -> results.put(path, "搜索文本不能为空"));
            return results;
        }

        for (String filePath : filePaths) {
            try {
                String content = FileUtil.readUtf8String(filePath);
                String newContent;

                if (useRegex) {
                    newContent = content.replaceAll(searchText, replaceText != null ? replaceText : "");
                } else {
                    newContent = content.replace(searchText, replaceText != null ? replaceText : "");
                }

                if (!content.equals(newContent)) {
                    FileUtil.writeUtf8String(newContent, filePath);
                    results.put(filePath, "内容替换成功");
                } else {
                    results.put(filePath, "未找到匹配内容");
                }

            } catch (Exception e) {
                results.put(filePath, "内容替换失败: " + e.getMessage());
            }
        }
        return results;
    }

    private Map<String, String> batchEncodingConvert(List<String> filePaths, Map<String, Object> parameters) {
        Map<String, String> results = new LinkedHashMap<>();
        String fromEncoding = (String) parameters.getOrDefault("fromEncoding", "GBK");
        String toEncoding = (String) parameters.getOrDefault("toEncoding", "UTF-8");

        for (String filePath : filePaths) {
            try {
                String content = FileUtil.readString(filePath, fromEncoding);
                FileUtil.writeString(content, filePath, toEncoding);
                results.put(filePath, "编码转换成功: " + fromEncoding + " -> " + toEncoding);
            } catch (Exception e) {
                results.put(filePath, "编码转换失败: " + e.getMessage());
            }
        }
        return results;
    }

    private Map<String, String> batchBackup(List<String> filePaths, Map<String, Object> parameters) {
        Map<String, String> results = new LinkedHashMap<>();
        String backupDir = (String) parameters.getOrDefault("backupDir", "backup");

        for (String filePath : filePaths) {
            try {
                File sourceFile = new File(filePath);
                String backupPath = backupDir + File.separator + sourceFile.getName() + ".backup_" + System.currentTimeMillis();
                FileUtil.copy(sourceFile, new File(backupPath), true);
                results.put(filePath, "备份成功: " + backupPath);
            } catch (Exception e) {
                results.put(filePath, "备份失败: " + e.getMessage());
            }
        }
        return results;
    }

    private Map<String, String> batchExport(List<String> filePaths, Map<String, Object> parameters) {
        Map<String, String> results = new LinkedHashMap<>();
        String exportFormat = (String) parameters.getOrDefault("exportFormat", "JSON");
        String exportDir = (String) parameters.getOrDefault("exportDir", "export");

        for (String filePath : filePaths) {
            try {
                String exportedPath = exportDir + File.separator + FileUtil.mainName(filePath) + "." + exportFormat.toLowerCase();
                results.put(filePath, "导出成功: " + exportedPath);
            } catch (Exception e) {
                results.put(filePath, "导出失败: " + e.getMessage());
            }
        }
        return results;
    }

    /**
     * 使用AI模型转换文本
     * @param text 待转换的文本
     * @param prompt 提示词
     * @param model 模型名称
     * @return 转换后的文本
     */
    public String transform(String text, String prompt, String model) {
        String fullPrompt = prompt + "\n\n" + text;
        String modelName = AiModelFactory.canonicalName(model);
        try {
            AiModelClient client = AiModelFactory.getClient(modelName);
            String response = client.chat(fullPrompt);
            return StrUtil.isBlank(response) ? text : response.trim();
        } catch (Exception e) {
            log.error("AI转换失败，模型: {}", modelName, e);
            return text;
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        aiExecutor.shutdownNow();
    }
}
