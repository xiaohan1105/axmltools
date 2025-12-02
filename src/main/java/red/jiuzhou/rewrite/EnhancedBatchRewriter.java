package red.jiuzhou.rewrite;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;
import red.jiuzhou.ai.*;
import red.jiuzhou.search.GlobalSearchEngine;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 增强批量改写器
 * 提供智能的批量字段改写功能，支持AI改写、模板化、预览等
 *
 * 功能：
 * - 多字段同时改写
 * - 跨表字段改写
 * - AI智能改写（支持多个模型）
 * - 改写预览
 * - 改写历史和撤销
 * - 改写模板保存和复用
 * - 条件改写
 */
@Slf4j
public class EnhancedBatchRewriter {

    private final ExecutorService executorService = Executors.newFixedThreadPool(8);
    private final List<RewriteHistory> historyList = new ArrayList<>();
    private final Map<String, RewriteTemplate> templates = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 改写选项
    @Data
    public static class RewriteOptions {
        private boolean useAI = false;               // 使用AI改写
        private String aiModel = "qwen";             // AI模型选择
        private String aiPrompt;                     // AI提示词
        private boolean preview = true;              // 预览模式
        private boolean backup = true;               // 备份原文件
        private boolean cacheAI = true;              // 缓存AI结果
        private String condition;                    // 条件表达式
        private RewriteMode mode = RewriteMode.REPLACE; // 改写模式
        private Map<String, String> fieldMapping;    // 字段映射
        private List<String> targetFields;           // 目标字段列表

        public enum RewriteMode {
            REPLACE,        // 替换
            APPEND,         // 追加
            PREPEND,        // 前置
            TRANSFORM,      // 转换（使用函数）
            AI_GENERATE     // AI生成
        }
    }

    // 改写结果
    @Data
    public static class RewriteResult {
        private String file;
        private String field;
        private String originalValue;
        private String newValue;
        private boolean success;
        private String error;
        private long timestamp = System.currentTimeMillis();

        public String getDisplayText() {
            if (success) {
                return String.format("[%s] %s: %s -> %s",
                    new File(file).getName(), field,
                    truncate(originalValue, 30), truncate(newValue, 30));
            } else {
                return String.format("[%s] %s: 失败 - %s",
                    new File(file).getName(), field, error);
            }
        }

        private String truncate(String text, int maxLength) {
            if (text == null) return "";
            if (text.length() <= maxLength) return text;
            return text.substring(0, maxLength - 3) + "...";
        }
    }

    // 改写历史
    @Data
    public static class RewriteHistory {
        private String id = UUID.randomUUID().toString();
        private Date date = new Date();
        private List<RewriteResult> results;
        private RewriteOptions options;
        private Map<String, byte[]> backups;  // 文件备份

        public void rollback() throws IOException {
            // 恢复备份文件
            for (Map.Entry<String, byte[]> entry : backups.entrySet()) {
                Files.write(Paths.get(entry.getKey()), entry.getValue());
            }
        }
    }

    // 改写模板
    @Data
    public static class RewriteTemplate {
        private String name;
        private String description;
        private RewriteOptions options;
        private List<FieldRule> fieldRules;
        private Date createTime = new Date();
        private Date lastUsed;

        @Data
        public static class FieldRule {
            private String fieldName;
            private String pattern;      // 匹配模式
            private String replacement;  // 替换内容
            private String aiPrompt;     // AI提示词
            private String condition;    // 应用条件
        }
    }

    /**
     * 批量改写字段
     */
    public List<RewriteResult> batchRewrite(List<String> files, RewriteOptions options) throws Exception {
        List<RewriteResult> allResults = new ArrayList<>();
        Map<String, byte[]> backups = new HashMap<>();

        // 备份文件
        if (options.isBackup()) {
            for (String file : files) {
                backups.put(file, Files.readAllBytes(Paths.get(file)));
            }
        }

        // 并行处理文件
        List<Future<List<RewriteResult>>> futures = new ArrayList<>();
        for (String file : files) {
            Future<List<RewriteResult>> future = executorService.submit(() ->
                rewriteFile(file, options));
            futures.add(future);
        }

        // 收集结果
        for (Future<List<RewriteResult>> future : futures) {
            try {
                allResults.addAll(future.get(60, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                log.warn("Rewrite timeout for file");
            }
        }

        // 保存历史
        if (!options.isPreview()) {
            RewriteHistory history = new RewriteHistory();
            history.setResults(allResults);
            history.setOptions(options);
            history.setBackups(backups);
            historyList.add(history);
        }

        return allResults;
    }

    /**
     * 改写单个文件
     */
    private List<RewriteResult> rewriteFile(String filePath, RewriteOptions options) throws Exception {
        List<RewriteResult> results = new ArrayList<>();

        // 加载文档
        Document doc = loadDocument(filePath);

        // 获取目标字段
        List<String> targetFields = options.getTargetFields();
        if (targetFields == null || targetFields.isEmpty()) {
            return results;
        }

        // 对每个字段进行改写
        for (String field : targetFields) {
            List<Element> elements = findElementsByField(doc, field);

            for (Element element : elements) {
                RewriteResult result = rewriteElement(element, field, options);
                result.setFile(filePath);
                results.add(result);
            }
        }

        // 保存文档（如果不是预览模式）
        if (!options.isPreview() && results.stream().anyMatch(RewriteResult::isSuccess)) {
            saveDocument(doc, filePath);
        }

        return results;
    }

    /**
     * 改写元素
     */
    private RewriteResult rewriteElement(Element element, String field, RewriteOptions options) {
        RewriteResult result = new RewriteResult();
        result.setField(field);

        try {
            // 获取原始值
            String originalValue = getFieldValue(element, field);
            result.setOriginalValue(originalValue);

            // 检查条件
            if (options.getCondition() != null && !evaluateCondition(element, options.getCondition())) {
                result.setSuccess(false);
                result.setError("条件不满足");
                return result;
            }

            // 执行改写
            String newValue = null;
            switch (options.getMode()) {
                case REPLACE:
                    newValue = performReplace(originalValue, options);
                    break;
                case APPEND:
                    newValue = originalValue + options.getFieldMapping().get(field);
                    break;
                case PREPEND:
                    newValue = options.getFieldMapping().get(field) + originalValue;
                    break;
                case TRANSFORM:
                    newValue = performTransform(originalValue, options);
                    break;
                case AI_GENERATE:
                    newValue = performAIRewrite(originalValue, options);
                    break;
            }

            // 设置新值
            if (newValue != null && !newValue.equals(originalValue)) {
                setFieldValue(element, field, newValue);
                result.setNewValue(newValue);
                result.setSuccess(true);
            } else {
                result.setSuccess(false);
                result.setError("无变化");
            }

        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            log.error("Failed to rewrite element", e);
        }

        return result;
    }

    /**
     * 执行替换
     */
    private String performReplace(String original, RewriteOptions options) {
        if (options.getFieldMapping() == null) {
            return original;
        }

        String replacement = options.getFieldMapping().values().stream()
            .findFirst().orElse(original);
        return replacement;
    }

    /**
     * 执行转换
     */
    private String performTransform(String original, RewriteOptions options) {
        // 支持简单的转换函数
        String transform = options.getFieldMapping().values().stream()
            .findFirst().orElse("");

        if (transform.startsWith("uppercase")) {
            return original.toUpperCase();
        } else if (transform.startsWith("lowercase")) {
            return original.toLowerCase();
        } else if (transform.startsWith("capitalize")) {
            return capitalize(original);
        } else if (transform.startsWith("reverse")) {
            return new StringBuilder(original).reverse().toString();
        } else if (transform.startsWith("trim")) {
            return original.trim();
        } else if (transform.startsWith("replace:")) {
            String[] parts = transform.substring(8).split(",", 2);
            if (parts.length == 2) {
                return original.replace(parts[0], parts[1]);
            }
        }

        return original;
    }

    /**
     * 执行AI改写
     */
    private String performAIRewrite(String original, RewriteOptions options) throws Exception {
        if (options.getAiPrompt() == null || options.getAiPrompt().isEmpty()) {
            return original;
        }

        // 获取AI客户端
        AiModelClient aiClient = AiModelFactory.getClient(options.getAiModel());
        if (aiClient == null) {
            throw new IllegalArgumentException("Unknown AI model: " + options.getAiModel());
        }

        // 构建完整提示词
        String fullPrompt = options.getAiPrompt() + "\n\n原始内容：\n" + original;

        // 调用AI
        String result = aiClient.chat(fullPrompt);

        // 如果需要缓存，保存结果
        if (options.isCacheAI()) {
            cacheAIResult(original, options.getAiPrompt(), result);
        }

        return result;
    }

    /**
     * 使用模板改写
     */
    public List<RewriteResult> rewriteWithTemplate(List<String> files, String templateName) throws Exception {
        RewriteTemplate template = templates.get(templateName);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateName);
        }

        // 更新模板使用时间
        template.setLastUsed(new Date());

        // 应用模板规则
        RewriteOptions options = template.getOptions();
        List<RewriteResult> allResults = new ArrayList<>();

        for (RewriteTemplate.FieldRule rule : template.getFieldRules()) {
            // 为每个字段规则创建选项
            RewriteOptions fieldOptions = cloneOptions(options);
            fieldOptions.setTargetFields(Collections.singletonList(rule.getFieldName()));

            if (rule.getAiPrompt() != null) {
                fieldOptions.setAiPrompt(rule.getAiPrompt());
            }

            if (rule.getCondition() != null) {
                fieldOptions.setCondition(rule.getCondition());
            }

            // 执行改写
            List<RewriteResult> results = batchRewrite(files, fieldOptions);
            allResults.addAll(results);
        }

        return allResults;
    }

    /**
     * 保存模板
     */
    public void saveTemplate(String name, String description, RewriteOptions options,
                            List<RewriteTemplate.FieldRule> fieldRules) {
        RewriteTemplate template = new RewriteTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setOptions(options);
        template.setFieldRules(fieldRules);

        templates.put(name, template);

        // 持久化到文件
        saveTemplates();
    }

    /**
     * 加载模板
     */
    public void loadTemplates() {
        try {
            Path templateFile = Paths.get("config/rewrite-templates.json");
            if (Files.exists(templateFile)) {
                String json = new String(Files.readAllBytes(templateFile), StandardCharsets.UTF_8);
                Map<String, RewriteTemplate> loaded = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, RewriteTemplate.class));
                templates.putAll(loaded);
            }
        } catch (Exception e) {
            log.error("Failed to load templates", e);
        }
    }

    /**
     * 保存模板到文件
     */
    private void saveTemplates() {
        try {
            Path templateFile = Paths.get("config/rewrite-templates.json");
            Files.createDirectories(templateFile.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(templates);
            Files.write(templateFile, json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to save templates", e);
        }
    }

    /**
     * 获取改写预览
     */
    public Map<String, List<String>> getPreview(List<String> files, RewriteOptions options) throws Exception {
        Map<String, List<String>> preview = new HashMap<>();

        // 设置为预览模式
        options.setPreview(true);

        // 执行改写
        List<RewriteResult> results = batchRewrite(files, options);

        // 组织预览结果
        for (RewriteResult result : results) {
            String key = new File(result.getFile()).getName();
            preview.computeIfAbsent(key, k -> new ArrayList<>())
                .add(result.getDisplayText());
        }

        return preview;
    }

    /**
     * 撤销改写
     */
    public void undoLastRewrite() throws IOException {
        if (!historyList.isEmpty()) {
            RewriteHistory lastHistory = historyList.remove(historyList.size() - 1);
            lastHistory.rollback();
            log.info("Undone rewrite: " + lastHistory.getId());
        } else {
            throw new IllegalStateException("No rewrite history to undo");
        }
    }

    /**
     * 获取改写历史
     */
    public List<RewriteHistory> getHistory() {
        return new ArrayList<>(historyList);
    }

    /**
     * 清除历史
     */
    public void clearHistory() {
        historyList.clear();
    }

    /**
     * 查找包含指定字段的元素
     */
    private List<Element> findElementsByField(Document doc, String field) {
        List<Element> elements = new ArrayList<>();

        // 查找属性
        NodeList allElements = doc.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            if (element.hasAttribute(field)) {
                elements.add(element);
            }
        }

        // 查找子元素
        NodeList fieldElements = doc.getElementsByTagName(field);
        for (int i = 0; i < fieldElements.getLength(); i++) {
            elements.add((Element) fieldElements.item(i));
        }

        return elements;
    }

    /**
     * 获取字段值
     */
    private String getFieldValue(Element element, String field) {
        // 先尝试获取属性
        if (element.hasAttribute(field)) {
            return element.getAttribute(field);
        }

        // 再尝试获取子元素
        NodeList children = element.getElementsByTagName(field);
        if (children.getLength() > 0) {
            return children.item(0).getTextContent();
        }

        // 如果元素名称匹配，返回文本内容
        if (element.getNodeName().equals(field)) {
            return element.getTextContent();
        }

        return "";
    }

    /**
     * 设置字段值
     */
    private void setFieldValue(Element element, String field, String value) {
        // 如果是属性
        if (element.hasAttribute(field)) {
            element.setAttribute(field, value);
            return;
        }

        // 如果是子元素
        NodeList children = element.getElementsByTagName(field);
        if (children.getLength() > 0) {
            children.item(0).setTextContent(value);
            return;
        }

        // 如果元素名称匹配
        if (element.getNodeName().equals(field)) {
            element.setTextContent(value);
        }
    }

    /**
     * 评估条件
     */
    private boolean evaluateCondition(Element element, String condition) {
        // 简单的条件评估实现
        // 支持格式: field operator value
        String[] parts = condition.split("\\s+", 3);
        if (parts.length != 3) {
            return true;  // 无效条件，默认通过
        }

        String field = parts[0];
        String operator = parts[1];
        String value = parts[2].replace("'", "").replace("\"", "");

        String fieldValue = getFieldValue(element, field);
        if (fieldValue.isEmpty()) {
            return false;
        }

        try {
            switch (operator) {
                case "=":
                case "==":
                    return fieldValue.equals(value);
                case "!=":
                    return !fieldValue.equals(value);
                case ">":
                    return Double.parseDouble(fieldValue) > Double.parseDouble(value);
                case ">=":
                    return Double.parseDouble(fieldValue) >= Double.parseDouble(value);
                case "<":
                    return Double.parseDouble(fieldValue) < Double.parseDouble(value);
                case "<=":
                    return Double.parseDouble(fieldValue) <= Double.parseDouble(value);
                case "contains":
                    return fieldValue.contains(value);
                case "startsWith":
                    return fieldValue.startsWith(value);
                case "endsWith":
                    return fieldValue.endsWith(value);
                default:
                    return true;
            }
        } catch (NumberFormatException e) {
            // 非数字比较，使用字符串比较
            return fieldValue.compareTo(value) > 0;
        }
    }

    /**
     * 加载XML文档
     */
    private Document loadDocument(String filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // 处理UTF-16编码
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            byte[] bytes = readAllBytesCompat(is);
            String content = new String(bytes, StandardCharsets.UTF_16);
            if (!content.startsWith("<?xml")) {
                content = new String(bytes, StandardCharsets.UTF_8);
            }
            return builder.parse(new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * 保存XML文档
     */
    private void saveDocument(Document doc, String filePath) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filePath));
        transformer.transform(source, result);
    }

    /**
     * 缓存AI结果
     */
    private void cacheAIResult(String original, String prompt, String result) {
        // 实现AI结果缓存逻辑
        // 可以保存到本地文件或数据库
    }

    /**
     * 克隆选项
     */
    private RewriteOptions cloneOptions(RewriteOptions options) {
        RewriteOptions cloned = new RewriteOptions();
        cloned.setUseAI(options.isUseAI());
        cloned.setAiModel(options.getAiModel());
        cloned.setAiPrompt(options.getAiPrompt());
        cloned.setPreview(options.isPreview());
        cloned.setBackup(options.isBackup());
        cloned.setCacheAI(options.isCacheAI());
        cloned.setCondition(options.getCondition());
        cloned.setMode(options.getMode());
        cloned.setFieldMapping(options.getFieldMapping() != null ?
            new HashMap<>(options.getFieldMapping()) : null);
        cloned.setTargetFields(options.getTargetFields() != null ?
            new ArrayList<>(options.getTargetFields()) : null);
        return cloned;
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Java 8兼容的readAllBytes方法
     */
    private byte[] readAllBytesCompat(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * 关闭改写器
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}