package red.jiuzhou.analysis.enhanced;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import red.jiuzhou.analysis.enhanced.GameSystemDetector.GameSystemType;
import red.jiuzhou.analysis.enhanced.GameSystemDetector.SystemDetectionResult;
import red.jiuzhou.analysis.enhanced.SmartInsightEngine.SmartInsight;
import red.jiuzhou.analysis.enhanced.SmartInsightEngine.GameDataContext;
import red.jiuzhou.analysis.enhanced.SmartInsightEngine.FieldAnalysis;
import red.jiuzhou.analysis.enhanced.SmartInsightEngine.DataQualityMetrics;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.YamlUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 增强的洞察服务 - 新一代游戏数据分析引擎
 *
 * 核心改进：
 * 1. 智能游戏系统识别 - 深度理解数据的游戏语义
 * 2. 策划友好的洞察生成 - 从技术数据到设计建议的转换
 * 3. AI增强分析 - 集成大语言模型提供深度洞察
 * 4. 性能优化 - 更好的缓存和异步处理
 * 5. 健壮的错误处理 - 确保功能始终可用
 */
public class EnhancedInsightService {

    private static final Logger log = LoggerFactory.getLogger(EnhancedInsightService.class);

    private static final int SAMPLE_RECORD_LIMIT = 50;
    private static final int MAX_UNIQUE_VALUES = 100;
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final List<Charset> CHARSET_FALLBACKS = buildCharsetFallbacks();

    private final GameSystemDetector systemDetector;
    private final SmartInsightEngine insightEngine;
    private final Map<String, CachedAnalysis> analysisCache;
    private final boolean cacheEnabled;

    public EnhancedInsightService() {
        this.systemDetector = new GameSystemDetector();
        this.insightEngine = new SmartInsightEngine();
        this.analysisCache = new HashMap<>();
        this.cacheEnabled = "true".equalsIgnoreCase(YamlUtils.getProperty("insight.cache.enabled"));
        log.info("增强洞察服务初始化完成，缓存: {}", cacheEnabled ? "启用" : "禁用");
    }

    /**
     * 缓存的分析结果
     */
    private static class CachedAnalysis {
        private final EnhancedInsightResult result;
        private final long timestamp;
        private final long fileLastModified;

        public CachedAnalysis(EnhancedInsightResult result, long fileLastModified) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
            this.fileLastModified = fileLastModified;
        }

        public boolean isValid(long currentFileLastModified, long maxAge) {
            return fileLastModified == currentFileLastModified &&
                   (System.currentTimeMillis() - timestamp) < maxAge;
        }

        public EnhancedInsightResult getResult() { return result; }
    }

    /**
     * 增强的洞察结果
     */
    public static class EnhancedInsightResult {
        private final EnhancedFileSummary fileSummary;
        private final List<SmartInsight> insights;
        private final SystemDetectionResult systemDetection;
        private final DataQualityMetrics qualityMetrics;
        private final Map<String, FieldAnalysis> fieldAnalyses;
        private final List<Map<String, String>> sampleRecords;
        private final long analysisTimeMs;
        private final Instant analysisTimestamp;

        public EnhancedInsightResult(EnhancedFileSummary fileSummary, List<SmartInsight> insights,
                                   SystemDetectionResult systemDetection, DataQualityMetrics qualityMetrics,
                                   Map<String, FieldAnalysis> fieldAnalyses, List<Map<String, String>> sampleRecords,
                                   long analysisTimeMs) {
            this.fileSummary = fileSummary;
            this.insights = insights;
            this.systemDetection = systemDetection;
            this.qualityMetrics = qualityMetrics;
            this.fieldAnalyses = fieldAnalyses;
            this.sampleRecords = sampleRecords;
            this.analysisTimeMs = analysisTimeMs;
            this.analysisTimestamp = Instant.now();
        }

        // Getters
        public EnhancedFileSummary getFileSummary() { return fileSummary; }
        public List<SmartInsight> getInsights() { return insights; }
        public SystemDetectionResult getSystemDetection() { return systemDetection; }
        public DataQualityMetrics getQualityMetrics() { return qualityMetrics; }
        public Map<String, FieldAnalysis> getFieldAnalyses() { return fieldAnalyses; }
        public List<Map<String, String>> getSampleRecords() { return sampleRecords; }
        public long getAnalysisTimeMs() { return analysisTimeMs; }
        public Instant getAnalysisTimestamp() { return analysisTimestamp; }
    }

    /**
     * 增强的文件摘要
     */
    public static class EnhancedFileSummary {
        private final Path path;
        private final String displayName;
        private final long fileSize;
        private final Instant lastModified;
        private final String rootElement;
        private final String entryElement;
        private final int recordCount;
        private final GameSystemType detectedSystem;
        private final double systemConfidence;

        public EnhancedFileSummary(Path path, String displayName, long fileSize, Instant lastModified,
                                 String rootElement, String entryElement, int recordCount,
                                 GameSystemType detectedSystem, double systemConfidence) {
            this.path = path;
            this.displayName = displayName;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
            this.rootElement = rootElement;
            this.entryElement = entryElement;
            this.recordCount = recordCount;
            this.detectedSystem = detectedSystem;
            this.systemConfidence = systemConfidence;
        }

        // Getters
        public Path getPath() { return path; }
        public String getDisplayName() { return displayName; }
        public long getFileSize() { return fileSize; }
        public Instant getLastModified() { return lastModified; }
        public String getRootElement() { return rootElement; }
        public String getEntryElement() { return entryElement; }
        public int getRecordCount() { return recordCount; }
        public GameSystemType getDetectedSystem() { return detectedSystem; }
        public double getSystemConfidence() { return systemConfidence; }
    }

    /**
     * 配置的根目录解析（兼容原有接口）
     */
    public List<Path> resolveConfiguredRoots() {
        String dbName = DatabaseUtil.getDbName();
        String raw = YamlUtils.getProperty("xmlPath." + dbName);
        if (!StringUtils.hasLength(raw)) {
            return Collections.emptyList();
        }

        List<Path> roots = new ArrayList<>();
        for (String fragment : raw.split(",")) {
            if (!StringUtils.hasLength(fragment)) {
                continue;
            }
            Path candidate = Paths.get(fragment.trim());
            if (Files.exists(candidate)) {
                roots.add(candidate);
            } else {
                log.warn("配置的XML路径不存在: {}", candidate);
            }
        }
        return roots;
    }

    /**
     * 分析XML文件（主要接口）
     */
    public EnhancedInsightResult analyze(Path xmlFile) {
        long startTime = System.currentTimeMillis();
        String cacheKey = xmlFile.toString();

        log.info("开始分析XML文件: {}", xmlFile);

        try {
            // 检查缓存
            if (cacheEnabled) {
                CachedAnalysis cached = analysisCache.get(cacheKey);
                if (cached != null) {
                    long fileLastModified = Files.getLastModifiedTime(xmlFile).toMillis();
                    long maxAge = 5 * 60 * 1000; // 5分钟缓存
                    if (cached.isValid(fileLastModified, maxAge)) {
                        log.debug("使用缓存的分析结果: {}", xmlFile);
                        return cached.getResult();
                    }
                }
            }

            // 执行分析
            EnhancedInsightResult result = performAnalysis(xmlFile, startTime);

            // 更新缓存
            if (cacheEnabled) {
                long fileLastModified = Files.getLastModifiedTime(xmlFile).toMillis();
                analysisCache.put(cacheKey, new CachedAnalysis(result, fileLastModified));
            }

            return result;

        } catch (Exception e) {
            log.error("分析XML文件失败: " + xmlFile, e);
            return createFallbackResult(xmlFile, startTime, e);
        }
    }

    /**
     * 执行实际的分析工作
     */
    private EnhancedInsightResult performAnalysis(Path xmlFile, long startTime) throws IOException {
        // 1. 解析XML文档
        Document document = safeReadXmlDocument(xmlFile);
        if (document == null) {
            throw new RuntimeException("无法解析XML文档");
        }

        Element root = document.getRootElement();
        List<Element> entries = extractDataEntries(root);

        log.debug("XML解析完成: 根元素={}, 数据条目={}", root.getName(), entries.size());

        // 2. 提取数据上下文
        GameSystemDetector.DataContext dataContext = buildDataContext(xmlFile, entries);

        // 3. 检测游戏系统类型
        SystemDetectionResult systemDetection = systemDetector.detectGameSystem(dataContext);
        log.info("游戏系统检测: {} (置信度: {:.2f})",
                systemDetection.getPrimaryType().getDisplayName(), systemDetection.getConfidence());

        // 4. 分析字段特征
        Map<String, FieldAnalysis> fieldAnalyses = analyzeFields(entries);

        // 5. 计算数据质量指标
        DataQualityMetrics qualityMetrics = calculateQualityMetrics(fieldAnalyses, entries.size());

        // 6. 构建游戏数据上下文
        GameDataContext gameContext = new GameDataContext(
                xmlFile.getFileName().toString(),
                systemDetection.getPrimaryType(),
                fieldAnalyses,
                qualityMetrics,
                entries.size(),
                systemDetection.getCharacteristics()
        );

        // 7. 生成智能洞察
        List<SmartInsight> insights = insightEngine.generateInsights(gameContext);

        // 8. 提取样本记录
        List<Map<String, String>> sampleRecords = extractSampleRecords(entries);

        // 9. 构建文件摘要
        EnhancedFileSummary fileSummary = buildEnhancedFileSummary(
                xmlFile, root, entries.size(), systemDetection);

        long analysisTime = System.currentTimeMillis() - startTime;
        log.info("分析完成: 耗时 {}ms, 洞察数量: {}", analysisTime, insights.size());

        return new EnhancedInsightResult(
                fileSummary, insights, systemDetection, qualityMetrics,
                fieldAnalyses, sampleRecords, analysisTime);
    }

    /**
     * 安全读取XML文档
     */
    private Document safeReadXmlDocument(Path xmlFile) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(xmlFile);
        } catch (IOException ex) {
            log.warn("无法读取XML文件: {}", xmlFile, ex);
            return null;
        }

        // 尝试不同的字符编码
        for (Charset charset : CHARSET_FALLBACKS) {
            try {
                CharsetDecoder decoder = charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
                String text = buffer.toString();
                if (StringUtils.hasLength(text)) {
                    return DocumentHelper.parseText(text);
                }
            } catch (CharacterCodingException | DocumentException ex) {
                // 尝试下一个编码
            }
        }

        // 最后尝试UTF-8
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            return DocumentHelper.parseText(content);
        } catch (DocumentException ex) {
            log.warn("无法解析XML文档: {}", xmlFile, ex);
            return null;
        }
    }

    /**
     * 提取数据条目
     */
    @SuppressWarnings("unchecked")
    private List<Element> extractDataEntries(Element root) {
        if (root == null) {
            return Collections.emptyList();
        }

        List<Element> children = new ArrayList<>((List<Element>) root.elements());
        if (children.isEmpty()) {
            return Collections.emptyList();
        }

        // 寻找重复的元素名称，作为数据条目
        Map<String, Long> nameCounts = children.stream()
                .collect(Collectors.groupingBy(Element::getName, Collectors.counting()));

        Optional<String> dataElementName = nameCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);

        if (dataElementName.isPresent()) {
            return children.stream()
                    .filter(child -> child.getName().equals(dataElementName.get()))
                    .collect(Collectors.toList());
        }

        // 如果没有重复元素，返回所有子元素
        return children;
    }

    /**
     * 构建数据上下文
     */
    private GameSystemDetector.DataContext buildDataContext(Path xmlFile, List<Element> entries) {
        if (entries.isEmpty()) {
            return new GameSystemDetector.DataContext(
                    xmlFile.getFileName().toString(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    0
            );
        }

        // 收集所有字段名
        Set<String> allFieldNames = new HashSet<>();
        Map<String, Integer> fieldCounts = new HashMap<>();
        Map<String, Set<String>> sampleValues = new HashMap<>();

        for (Element entry : entries) {
            Map<String, String> flattenedEntry = flattenElement(entry);
            for (Map.Entry<String, String> field : flattenedEntry.entrySet()) {
                String fieldName = field.getKey();
                String value = field.getValue();

                allFieldNames.add(fieldName);
                fieldCounts.merge(fieldName, 1, Integer::sum);

                sampleValues.computeIfAbsent(fieldName, k -> new HashSet<>()).add(value);
            }
        }

        return new GameSystemDetector.DataContext(
                xmlFile.getFileName().toString(),
                new ArrayList<>(allFieldNames),
                fieldCounts,
                sampleValues,
                entries.size()
        );
    }

    /**
     * 分析字段特征
     */
    private Map<String, FieldAnalysis> analyzeFields(List<Element> entries) {
        Map<String, FieldAnalysis> analyses = new HashMap<>();

        if (entries.isEmpty()) {
            return analyses;
        }

        // 收集字段信息
        Map<String, List<String>> fieldValues = new HashMap<>();
        for (Element entry : entries) {
            Map<String, String> flattened = flattenElement(entry);
            for (Map.Entry<String, String> field : flattened.entrySet()) {
                fieldValues.computeIfAbsent(field.getKey(), k -> new ArrayList<>())
                          .add(field.getValue());
            }
        }

        // 分析每个字段
        for (Map.Entry<String, List<String>> fieldEntry : fieldValues.entrySet()) {
            String fieldName = fieldEntry.getKey();
            List<String> values = fieldEntry.getValue();

            FieldAnalysis analysis = analyzeField(fieldName, values, entries.size());
            analyses.put(fieldName, analysis);
        }

        return analyses;
    }

    /**
     * 分析单个字段
     */
    private FieldAnalysis analyzeField(String fieldName, List<String> values, int totalRecords) {
        // 计算覆盖率
        long nonEmptyCount = values.stream().mapToLong(v -> StringUtils.hasLength(v) ? 1 : 0).sum();
        double coverage = (double) nonEmptyCount / totalRecords;

        // 计算唯一值数量
        Set<String> uniqueValues = new HashSet<>(values);
        int uniqueCount = uniqueValues.size();

        // 判断数据类型
        String dataType = inferDataType(values);

        // 检测异常值
        boolean hasOutliers = detectOutliers(values, dataType);

        // 计算统计信息
        Map<String, Object> statistics = calculateStatistics(values, dataType);

        // 获取样本值
        List<String> sampleValues = uniqueValues.stream()
                .limit(5)
                .collect(Collectors.toList());

        return new FieldAnalysis(fieldName, dataType, coverage, uniqueCount,
                                hasOutliers, statistics, sampleValues);
    }

    /**
     * 推断数据类型
     */
    private String inferDataType(List<String> values) {
        long numericCount = values.stream()
                .filter(StringUtils::hasLength)
                .mapToLong(v -> NUMERIC_PATTERN.matcher(v.trim()).matches() ? 1 : 0)
                .sum();

        double numericRatio = (double) numericCount / Math.max(values.size(), 1);

        if (numericRatio > 0.8) {
            return "数值";
        } else if (numericRatio > 0.3) {
            return "混合";
        } else {
            return "文本";
        }
    }

    /**
     * 检测异常值
     */
    private boolean detectOutliers(List<String> values, String dataType) {
        if (!"数值".equals(dataType)) {
            return false;
        }

        List<Double> numericValues = values.stream()
                .filter(StringUtils::hasLength)
                .map(String::trim)
                .map(v -> {
                    try {
                        return Double.parseDouble(v);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        if (numericValues.size() < 5) {
            return false;
        }

        // 使用IQR方法检测异常值
        int q1Index = numericValues.size() / 4;
        int q3Index = numericValues.size() * 3 / 4;
        double q1 = numericValues.get(q1Index);
        double q3 = numericValues.get(q3Index);
        double iqr = q3 - q1;
        double lowerBound = q1 - 1.5 * iqr;
        double upperBound = q3 + 1.5 * iqr;

        return numericValues.stream()
                .anyMatch(v -> v < lowerBound || v > upperBound);
    }

    /**
     * 计算统计信息
     */
    private Map<String, Object> calculateStatistics(List<String> values, String dataType) {
        Map<String, Object> stats = new HashMap<>();

        if ("数值".equals(dataType)) {
            List<Double> numericValues = values.stream()
                    .filter(StringUtils::hasLength)
                    .map(String::trim)
                    .map(v -> {
                        try {
                            return Double.parseDouble(v);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!numericValues.isEmpty()) {
                stats.put("min", Collections.min(numericValues));
                stats.put("max", Collections.max(numericValues));
                stats.put("average", numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
                stats.put("count", numericValues.size());
            }
        }

        return stats;
    }

    /**
     * 计算数据质量指标
     */
    private DataQualityMetrics calculateQualityMetrics(Map<String, FieldAnalysis> fieldAnalyses, int totalRecords) {
        if (fieldAnalyses.isEmpty()) {
            return new DataQualityMetrics(0.0, 0.0, 0.0, 0.0, Collections.emptyMap());
        }

        // 完整性：平均字段覆盖率
        double completeness = fieldAnalyses.values().stream()
                .mapToDouble(FieldAnalysis::getCoverage)
                .average()
                .orElse(0.0);

        // 一致性：数据类型的一致性
        long numericFields = fieldAnalyses.values().stream()
                .mapToLong(f -> "数值".equals(f.getDataType()) ? 1 : 0)
                .sum();
        double consistency = (double) numericFields / fieldAnalyses.size();

        // 平衡性：异常值的反向指标
        long fieldsWithOutliers = fieldAnalyses.values().stream()
                .mapToLong(f -> f.hasOutliers() ? 1 : 0)
                .sum();
        double balance = 1.0 - (double) fieldsWithOutliers / fieldAnalyses.size();

        // 成长性：假设有等级或progression字段
        boolean hasProgression = fieldAnalyses.keySet().stream()
                .anyMatch(name -> name.toLowerCase().matches(".*(level|等级|exp|experience).*"));
        double progression = hasProgression ? 0.8 : 0.3;

        Map<String, Double> subMetrics = new HashMap<>();
        subMetrics.put("fieldCount", (double) fieldAnalyses.size());
        subMetrics.put("recordCount", (double) totalRecords);

        return new DataQualityMetrics(completeness, consistency, balance, progression, subMetrics);
    }

    /**
     * 提取样本记录
     */
    private List<Map<String, String>> extractSampleRecords(List<Element> entries) {
        return entries.stream()
                .limit(SAMPLE_RECORD_LIMIT)
                .map(this::flattenElement)
                .collect(Collectors.toList());
    }

    /**
     * 扁平化XML元素
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> flattenElement(Element element) {
        Map<String, String> result = new LinkedHashMap<>();

        // 添加属性
        element.attributes().forEach(attribute ->
                result.put(attribute.getName(), truncateValue(attribute.getValue())));

        // 添加子元素
        List<Element> children = new ArrayList<>((List<Element>) element.elements());
        for (Element child : children) {
            List<Element> grandChildren = new ArrayList<>((List<Element>) child.elements());
            if (grandChildren.isEmpty()) {
                result.put(child.getName(), truncateValue(child.getTextTrim()));
            }
        }

        return result;
    }

    /**
     * 截断过长的值
     */
    private String truncateValue(String value) {
        if (!StringUtils.hasLength(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 200) {
            return trimmed;
        }
        return trimmed.substring(0, 200) + "...";
    }

    /**
     * 构建增强的文件摘要
     */
    private EnhancedFileSummary buildEnhancedFileSummary(Path xmlFile, Element root, int recordCount,
                                                       SystemDetectionResult systemDetection) {
        long fileSize = 0;
        Instant lastModified = Instant.EPOCH;

        try {
            fileSize = Files.size(xmlFile);
            lastModified = Instant.ofEpochMilli(Files.getLastModifiedTime(xmlFile).toMillis());
        } catch (IOException ex) {
            log.warn("无法读取文件信息: {}", xmlFile, ex);
        }

        return new EnhancedFileSummary(
                xmlFile,
                xmlFile.getFileName().toString(),
                fileSize,
                lastModified,
                root != null ? root.getName() : "",
                "", // entryElement可以从systemDetection中推导
                recordCount,
                systemDetection.getPrimaryType(),
                systemDetection.getConfidence()
        );
    }

    /**
     * 创建兜底结果
     */
    private EnhancedInsightResult createFallbackResult(Path xmlFile, long startTime, Exception error) {
        try {
            SystemDetectionResult fallbackDetection = new SystemDetectionResult(
                    GameSystemType.UNKNOWN, null, 0.0,
                    "分析失败: " + error.getMessage(),
                    Collections.emptyMap()
            );

            EnhancedFileSummary fallbackSummary = new EnhancedFileSummary(
                    xmlFile,
                    xmlFile.getFileName().toString(),
                    Files.exists(xmlFile) ? Files.size(xmlFile) : 0,
                    Files.exists(xmlFile) ? Instant.ofEpochMilli(Files.getLastModifiedTime(xmlFile).toMillis()) : Instant.EPOCH,
                    "",
                    "",
                    0,
                    GameSystemType.UNKNOWN,
                    0.0
            );

            List<SmartInsight> fallbackInsights = Arrays.asList(
                    new SmartInsight(
                            "分析失败",
                            "文件分析过程中出现错误: " + error.getMessage(),
                            SmartInsightEngine.InsightLevel.CRITICAL,
                            "无法获取有效的数据洞察",
                            Arrays.asList("检查文件格式是否正确", "确认文件是否可读", "查看详细错误日志"),
                            createMap("error", error.getClass().getSimpleName()),
                            0.95,
                            "错误处理"
                    )
            );

            DataQualityMetrics fallbackMetrics = new DataQualityMetrics(
                    0.0, 0.0, 0.0, 0.0, Collections.emptyMap());

            long analysisTime = System.currentTimeMillis() - startTime;

            return new EnhancedInsightResult(
                    fallbackSummary,
                    fallbackInsights,
                    fallbackDetection,
                    fallbackMetrics,
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    analysisTime
            );

        } catch (Exception fallbackError) {
            log.error("创建兜底结果失败", fallbackError);
            throw new RuntimeException("分析失败且无法创建兜底结果", error);
        }
    }

    /**
     * 构建字符集回退列表
     */
    private static List<Charset> buildCharsetFallbacks() {
        List<Charset> charsets = new ArrayList<>();
        charsets.add(StandardCharsets.UTF_8);
        charsets.add(StandardCharsets.UTF_16LE);
        charsets.add(StandardCharsets.UTF_16BE);

        for (String name : Arrays.asList("GBK", "GB2312", "UTF-16")) {
            try {
                charsets.add(Charset.forName(name));
            } catch (Exception ignored) {
                // 忽略不支持的字符集
            }
        }

        return charsets;
    }

    /**
     * Java 8 compatible map creation helper method
     */
    private static Map<String, Object> createMap(String key1, Object value1) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        return map;
    }
}