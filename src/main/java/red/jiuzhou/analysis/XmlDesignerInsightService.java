package red.jiuzhou.analysis;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import red.jiuzhou.analysis.XmlDesignerInsight.AttributeInsight;
import red.jiuzhou.analysis.XmlDesignerInsight.AttributeValueDistribution;
import red.jiuzhou.analysis.XmlDesignerInsight.Builder;
import red.jiuzhou.analysis.XmlDesignerInsight.Metric;
import red.jiuzhou.analysis.XmlDesignerInsight.Severity;
import red.jiuzhou.analysis.XmlDesignerInsight.Suggestion;
import red.jiuzhou.analysis.XmlDesignerInsight.ValueCount;
import red.jiuzhou.analysis.XmlDesignerInsight.XmlFileSummary;
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
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * Service that inspects XML files and produces designer friendly insight data.
 */
public class XmlDesignerInsightService {

    private static final Logger log = LoggerFactory.getLogger(XmlDesignerInsightService.class);

    private static final int SAMPLE_RECORD_LIMIT = 24;
    private static final int MAX_TRACKED_UNIQUE_VALUES = 500;
    private static final int VALUE_TRUNCATE_LIMIT = 220;
    private static final int TOP_VALUE_LIMIT = 12;
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final List<Charset> CHARSET_FALLBACKS = buildCharsetFallbacks();

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
                log.warn("Configured XML path does not exist: {}", candidate);
            }
        }
        return roots;
    }

    public XmlFileSummary summarize(Path xmlFile) {
        Instant lastModified = Instant.EPOCH;
        try {
            lastModified = Instant.ofEpochMilli(Files.getLastModifiedTime(xmlFile).toMillis());
        } catch (IOException ex) {
            log.warn("Failed to read last modified time for {}", xmlFile, ex);
        }

        long size = readFileSize(xmlFile);
        Document document = safeRead(xmlFile);
        Element root = document != null ? document.getRootElement() : null;
        Element container = resolveEntryContainer(root);
        TableContext tableContext = resolveTableContext(xmlFile);
        return new XmlFileSummary(
                xmlFile,
                xmlFile.getFileName().toString(),
                size,
                lastModified,
                root != null ? root.getName() : "",
                container != null ? container.getName() : "",
                tableContext.tableName,
                tableContext.tableExists,
                tableContext.rowCount
        );
    }

    public XmlDesignerInsight analyze(Path xmlFile) {
        return analyze(xmlFile, SAMPLE_RECORD_LIMIT);
    }

    public XmlDesignerInsight analyze(Path xmlFile, int sampleLimit) {
        Document document = safeRead(xmlFile);
        if (document == null) {
            TableContext tableContext = resolveTableContext(xmlFile);
            XmlFileSummary summary = new XmlFileSummary(
                    xmlFile,
                    xmlFile.getFileName().toString(),
                    readFileSize(xmlFile),
                    Instant.ofEpochMilli(xmlFile.toFile().lastModified()),
                    "",
                    "",
                    tableContext.tableName,
                    tableContext.tableExists,
                    tableContext.rowCount
            );
            return XmlDesignerInsight.builder(summary)
                    .addSuggestion(new Suggestion(
                            "Unable to Parse",
                            "The file could not be parsed as well-formed XML. Check the file contents or locks.",
                            Severity.CRITICAL))
                    .withEntryCount(0)
                    .build();
        }

        Element root = document.getRootElement();
        Element entryContainer = resolveEntryContainer(root);
        List<Element> entries = resolveEntries(entryContainer, root);

        String entryElementName = entries.isEmpty()
                ? (entryContainer != null ? entryContainer.getName() : "")
                : entries.get(0).getName();

        XmlFileSummary summary = buildSummary(xmlFile, root, entryElementName);
        Builder builder = XmlDesignerInsight.builder(summary).withEntryCount(entries.size());
        AttributeAggregator aggregator = new AttributeAggregator();
        List<Map<String, String>> sampleRecords = new ArrayList<>();

        for (Element element : entries) {
            Map<String, String> flattened = flattenElement(element);
            if (!flattened.isEmpty()) {
                aggregator.accept(flattened);
            }
            if (sampleRecords.size() < sampleLimit) {
                sampleRecords.add(flattened);
            }
        }

        aggregator.resolvePrimaryKeyCandidate(entries.size());

        appendMetrics(builder, summary, entries.size(), aggregator);
        appendSuggestions(builder, summary, aggregator, entries.size());

        for (AttributeStats stats : aggregator.attributeStats.values()) {
            builder.addAttributeInsight(stats.toInsight());
            builder.addDistribution(stats.toDistribution(entries.size()));
        }

        for (Map<String, String> record : sampleRecords) {
            builder.addSampleRecord(record);
        }

        // 执行高级分析
        performAdvancedAnalysis(builder, entries, aggregator);

        return builder.build();
    }

    /**
     * 执行高级数据分析，包括关联性、分布特征和平衡性检测
     */
    private void performAdvancedAnalysis(Builder builder, List<Element> entries, AttributeAggregator aggregator) {
        if (entries.isEmpty()) {
            log.debug("数据洞察: 跳过高级分析，因为没有数据记录");
            return;
        }

        try {
            log.debug("数据洞察: 开始执行高级分析，记录数: {}, 字段数: {}",
                entries.size(), aggregator.attributeStats.size());

            // 收集所有数据
            List<Map<String, String>> allRecords = new ArrayList<>();
            for (Element element : entries) {
                Map<String, String> flattened = flattenElement(element);
                if (!flattened.isEmpty()) {
                    allRecords.add(flattened);
                }
            }

            log.debug("数据洞察: 展平后的记录数: {}", allRecords.size());

            // 识别字段类型
            int typeCount = 0;
            for (String fieldName : aggregator.attributeStats.keySet()) {
                DataCorrelationAnalyzer.AttributeType type = DataCorrelationAnalyzer.identifyAttributeType(fieldName);
                builder.addAttributeType(fieldName, type);
                typeCount++;
            }
            log.debug("数据洞察: 识别了 {} 个字段类型", typeCount);

            // 提取数值字段
            Map<String, List<Double>> numericFields = new LinkedHashMap<>();
            for (String fieldName : aggregator.attributeStats.keySet()) {
                List<Double> values = extractNumericValuesFromRecords(allRecords, fieldName);
                if (values.size() >= 3) { // 至少需要3个数据点
                    numericFields.put(fieldName, values);
                }
            }
            log.debug("数据洞察: 找到 {} 个数值字段", numericFields.size());

            // 分析字段间相关性（只分析前10个数值字段，避免过多计算）
            List<String> fieldNames = new ArrayList<>(numericFields.keySet());
            int maxFields = Math.min(10, fieldNames.size());
            int correlationCount = 0;
            for (int i = 0; i < maxFields; i++) {
                for (int j = i + 1; j < maxFields; j++) {
                    String field1 = fieldNames.get(i);
                    String field2 = fieldNames.get(j);
                    List<Double> values1 = numericFields.get(field1);
                    List<Double> values2 = numericFields.get(field2);

                    DataCorrelationAnalyzer.FieldCorrelation correlation =
                        DataCorrelationAnalyzer.analyzeCorrelation(field1, values1, field2, values2);

                    // 只添加有意义的相关性
                    if (Math.abs(correlation.getCorrelation()) > 0.3) {
                        builder.addCorrelation(correlation);
                        correlationCount++;
                    }
                }
            }
            log.debug("数据洞察: 发现 {} 个显著相关性", correlationCount);

            // 分析数值分布特征（只分析前15个字段）
            int count = 0;
            for (Map.Entry<String, List<Double>> entry : numericFields.entrySet()) {
                if (count++ >= 15) break;

                DataCorrelationAnalyzer.DistributionProfile profile =
                    DataCorrelationAnalyzer.analyzeDistribution(entry.getKey(), entry.getValue());
                builder.addDistributionProfile(profile);
            }
            log.debug("数据洞察: 分析了 {} 个字段的分布特征", count);

            // 检测平衡性问题
            String idField = findIdField(aggregator);
            Map<String, List<Map<String, String>>> fieldRecords = new LinkedHashMap<>();
            for (String fieldName : numericFields.keySet()) {
                fieldRecords.put(fieldName, allRecords);
            }

            List<DataCorrelationAnalyzer.BalanceIssue> issues =
                DataCorrelationAnalyzer.detectBalanceIssues(fieldRecords, idField != null ? idField : "id");

            for (DataCorrelationAnalyzer.BalanceIssue issue : issues) {
                builder.addBalanceIssue(issue);
            }
            log.debug("数据洞察: 检测到 {} 个平衡性问题", issues.size());

            log.info("数据洞察: 高级分析完成 - 字段类型:{}, 数值字段:{}, 相关性:{}, 分布特征:{}, 平衡问题:{}",
                typeCount, numericFields.size(), correlationCount, count, issues.size());

        } catch (Exception e) {
            log.error("数据洞察: 高级分析出现异常", e);
            // 不抛出异常，让基础分析结果仍然可用
        }
    }

    /**
     * 从记录中提取数值列表
     */
    private List<Double> extractNumericValuesFromRecords(List<Map<String, String>> records, String fieldName) {
        List<Double> values = new ArrayList<>();
        for (Map<String, String> record : records) {
            String value = record.get(fieldName);
            if (value != null && !value.trim().isEmpty()) {
                try {
                    values.add(Double.parseDouble(value.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return values;
    }

    /**
     * 查找ID字段
     */
    private String findIdField(AttributeAggregator aggregator) {
        // 优先查找名为id的字段
        if (aggregator.attributeStats.containsKey("id")) {
            return "id";
        }
        // 查找包含id的字段
        for (String fieldName : aggregator.attributeStats.keySet()) {
            if (fieldName.toLowerCase(Locale.ENGLISH).contains("id")) {
                return fieldName;
            }
        }
        return null;
    }

    private long readFileSize(Path xmlFile) {
        try {
            return Files.size(xmlFile);
        } catch (IOException ex) {
            return 0L;
        }
    }

    private XmlFileSummary buildSummary(Path xmlFile, Element root, String entryElementName) {
        long size = readFileSize(xmlFile);
        Instant lastModified = Instant.ofEpochMilli(xmlFile.toFile().lastModified());
        TableContext tableContext = resolveTableContext(xmlFile);
        return new XmlFileSummary(
                xmlFile,
                xmlFile.getFileName().toString(),
                size,
                lastModified,
                root != null ? root.getName() : "",
                entryElementName,
                tableContext.tableName,
                tableContext.tableExists,
                tableContext.rowCount
        );
    }

    private void appendMetrics(Builder builder,
                               XmlFileSummary summary,
                               int entryCount,
                               AttributeAggregator aggregator) {
        DecimalFormat format = new DecimalFormat("#,###");
        builder.addMetric(new Metric("Record Count",
                format.format(entryCount),
                "Total number of parsed data entries."));

        builder.addMetric(new Metric("Field Count",
                String.valueOf(aggregator.attributeStats.size()),
                "Distinct field names observed across all records."));

        if (aggregator.primaryKeyCandidate != null) {
            builder.addMetric(new Metric("Primary Key Candidate",
                    aggregator.primaryKeyCandidate,
                    "Field covers at least 95% of records without duplicates; likely a primary key."));
        }

        builder.addMetric(new Metric("File Size",
                formatFileSize(summary.getFileSize()),
                "Approximate size of the XML file on disk."));

        if (StringUtils.hasLength(summary.getInferredTableName())) {
            String detail = summary.isTableExists()
                    ? "Matching table found in the database for cross checking."
                    : "No matching table was found in the database; review mapping or initialisation scripts.";
            builder.addMetric(new Metric("Inferred Table Name",
                    summary.getInferredTableName(),
                    detail));
        }

        if (summary.isTableExists() && summary.getDatabaseRowCount() != null) {
            builder.addMetric(new Metric("Database Row Count",
                    format.format(summary.getDatabaseRowCount()),
                    "Rows currently stored in the mapped database table."));
            int diff = summary.getDatabaseRowCount() - entryCount;
            if (diff != 0) {
                builder.addMetric(new Metric("Record Delta",
                        String.valueOf(diff),
                        diff > 0
                                ? "Database contains more rows than the XML file; export may be incomplete."
                                : "XML contains more rows than the database table; data might not be synced."));
            }
        }
    }

    private void appendSuggestions(Builder builder,
                                   XmlFileSummary summary,
                                   AttributeAggregator aggregator,
                                   int entryCount) {
        if (entryCount == 0) {
            builder.addSuggestion(new Suggestion(
                    "No Records Found",
                    "No data entries were detected in the XML document; verify element configuration.",
                    Severity.WARNING));
            return;
        }

        if (entryCount > 5000) {
            builder.addSuggestion(new Suggestion(
                    "Large Record Volume",
                    "More than 5,000 records were parsed. Consider pagination or splitting the file to improve responsiveness.",
                    Severity.INFO));
        }

        if (StringUtils.hasLength(summary.getInferredTableName()) && !summary.isTableExists()) {
            builder.addSuggestion(new Suggestion(
                    "Missing Database Table",
                    "The inferred table \"" + summary.getInferredTableName() + "\" does not exist in the active database.",
                    Severity.WARNING));
        }

        // 检查是否启用数据库同步检查
        boolean checkDbSync = "true".equalsIgnoreCase(YamlUtils.getProperty("insight.checkDatabaseSync"));

        if (checkDbSync && summary.isTableExists() && summary.getDatabaseRowCount() != null) {
            int diff = summary.getDatabaseRowCount() - entryCount;
            double ratio = entryCount == 0 ? 0D : Math.abs(diff) * 1.0 / entryCount;

            // 优化：只有当XML有较多数据时才进行严格检查
            // 如果XML记录很少（<10），可能是测试数据或部分导出，不应报严重警告
            boolean isSignificantDataset = entryCount >= 10;

            // 调整阈值：差异需要超过30%且绝对值超过50才报警告
            if (isSignificantDataset && ratio > 0.3 && Math.abs(diff) > 50) {
                builder.addSuggestion(new Suggestion(
                        "数据同步差异",
                        String.format(Locale.US,
                                "数据库表比XML文件%s %d 行 (差异%.0f%%)。如果XML是部分导出或测试数据，这是正常的。可在application.yml中设置insight.checkDatabaseSync=false关闭此检查。",
                                diff > 0 ? "多" : "少",
                                Math.abs(diff),
                                ratio * 100),
                        Severity.INFO));  // 改为INFO
            } else if (diff != 0 && Math.abs(diff) > 20) {
                // 中等差异，差异要>20才提示
                builder.addSuggestion(new Suggestion(
                        "数据记录数差异",
                        String.format(Locale.US,
                                "数据库表比XML%s %d 行。通常XML和数据库不完全同步是正常的。",
                                diff > 0 ? "多" : "少",
                                Math.abs(diff)),
                        Severity.INFO));
            }
        }

        for (AttributeStats stats : aggregator.attributeStats.values()) {
            double coverage = stats.coverageRatio(entryCount) * 100.0;
            if (coverage < 60) {
                builder.addSuggestion(new Suggestion(
                        "Low Coverage Field: " + stats.attributeName,
                        String.format(Locale.US,
                                "%s appears in only %.0f%% of records. Check whether it is optional or missing data.",
                                stats.attributeName,
                                coverage),
                        Severity.WARNING));
            } else if (coverage < 90) {
                builder.addSuggestion(new Suggestion(
                        "Moderate Coverage Field: " + stats.attributeName,
                        String.format(Locale.US,
                                "%s covers about %.0f%% of records. Confirm whether additional values are required.",
                                stats.attributeName,
                                coverage),
                        Severity.INFO));
            }

            if (stats.blankCount > 0) {
                builder.addSuggestion(new Suggestion(
                        "Null Values Detected: " + stats.attributeName,
                        String.format(Locale.US,
                                "%s contains %d blank values. Provide defaults or remove empty entries.",
                                stats.attributeName,
                                stats.blankCount),
                        Severity.INFO));
            }

            if (stats.duplicateSamples > 0) {
                builder.addSuggestion(new Suggestion(
                        "Duplicate Values: " + stats.attributeName,
                        "Sampled records include duplicate values. Review uniqueness constraints if necessary.",
                        Severity.INFO));
            }

            if (stats.truncated) {
                builder.addSuggestion(new Suggestion(
                        "Many Distinct Values: " + stats.attributeName,
                        String.format(Locale.US,
                                "%s has more than %d distinct values; only the most frequent ones are shown.",
                                stats.attributeName,
                                MAX_TRACKED_UNIQUE_VALUES),
                        Severity.INFO));
            }

            if (Objects.equals(stats.attributeName, aggregator.primaryKeyCandidate) && stats.blankCount > 0) {
                builder.addSuggestion(new Suggestion(
                        "Primary Key Candidate Missing Values: " + stats.attributeName,
                        "Field is considered a primary key but still contains blanks. Review data integrity.",
                        Severity.WARNING));
            }
        }
    }

    private Document safeRead(Path xmlFile) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(xmlFile);
        } catch (IOException ex) {
            log.warn("Unable to read XML file {}", xmlFile, ex);
            return null;
        }

        for (Charset charset : CHARSET_FALLBACKS) {
            try {
                CharsetDecoder decoder = charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
                String text = buffer.toString();
                if (!StringUtils.hasLength(text)) {
                    continue;
                }
                return DocumentHelper.parseText(text);
            } catch (CharacterCodingException | DocumentException ex) {
                // try next charset
            }
        }

        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            return DocumentHelper.parseText(content);
        } catch (DocumentException ex) {
            log.warn("Failed to parse XML file {}", xmlFile, ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Element resolveEntryContainer(Element root) {
        if (root == null) {
            return null;
        }
        List<Element> children = new ArrayList<>((List<Element>) root.elements());
        if (children.isEmpty()) {
            return null;
        }
        Map<String, Long> counts = children.stream()
                .collect(Collectors.groupingBy(Element::getName, LinkedHashMap::new, Collectors.counting()));
        Optional<Map.Entry<String, Long>> repeated = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .max(Map.Entry.comparingByValue());
        if (repeated.isPresent()) {
            String name = repeated.get().getKey();
            return children.stream()
                    .filter(child -> child.getName().equals(name))
                    .findFirst()
                    .orElse(children.get(0));
        }
        return children.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Element> resolveEntries(Element container, Element root) {
        if (container != null) {
            return new ArrayList<>((List<Element>) container.elements());
        }
        if (root == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>((List<Element>) root.elements());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> flattenElement(Element element) {
        Map<String, String> result = new LinkedHashMap<>();
        element.attributes().forEach(attribute ->
                result.put(attribute.getName(), truncate(attribute.getValue())));

        List<Element> children = new ArrayList<>((List<Element>) element.elements());
        for (Element child : children) {
            List<Element> grandChildren = new ArrayList<>((List<Element>) child.elements());
            if (grandChildren.isEmpty()) {
                result.put(child.getName(), truncate(child.getTextTrim()));
            }
        }
        return result;
    }

    private String truncate(String value) {
        if (!StringUtils.hasLength(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= VALUE_TRUNCATE_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, VALUE_TRUNCATE_LIMIT) + "...";
    }

    private TableContext resolveTableContext(Path xmlFile) {
        String inferredName = inferTableName(xmlFile.getFileName().toString());
        if (!StringUtils.hasLength(inferredName)) {
            return new TableContext(null, false, null);
        }
        JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();
        try {
            Integer rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM `" + inferredName + "`", Integer.class);
            return new TableContext(inferredName, true, rowCount);
        } catch (DataAccessException ex) {
            return new TableContext(inferredName, false, null);
        }
    }

    private String inferTableName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;

        // 移除常见的前缀和后缀
        base = base.replaceAll("^(client_|server_|svr_|clt_)", "");  // 移除client_、server_等前缀
        base = base.replaceAll("_(config|data|info)$", "");  // 移除_config、_data等后缀

        base = base.replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("__+", "_")
                .toLowerCase(Locale.ENGLISH);
        return StringUtils.hasLength(base) ? base : null;
    }

    private String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = size;
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.US, "%.2f %s", value, units[unitIndex]);
    }

    private static List<Charset> buildCharsetFallbacks() {
        List<Charset> charsets = new ArrayList<>();
        charsets.add(StandardCharsets.UTF_8);
        charsets.add(StandardCharsets.UTF_16LE);
        charsets.add(StandardCharsets.UTF_16BE);
        for (String name : Arrays.asList("GBK", "GB2312", "UTF-16")) {
            try {
                charsets.add(Charset.forName(name));
            } catch (Exception ignored) {
                // ignore missing charset
            }
        }
        return charsets;
    }

    private static class TableContext {
        private final String tableName;
        private final boolean tableExists;
        private final Integer rowCount;

        private TableContext(String tableName, boolean tableExists, Integer rowCount) {
            this.tableName = tableName;
            this.tableExists = tableExists;
            this.rowCount = rowCount;
        }
    }

    private static class AttributeAggregator {
        private final Map<String, AttributeStats> attributeStats = new LinkedHashMap<>();
        private String primaryKeyCandidate;

        void accept(Map<String, String> record) {
            record.forEach((name, value) ->
                    attributeStats.computeIfAbsent(name, AttributeStats::new).record(value));
        }

        void resolvePrimaryKeyCandidate(int entryCount) {
            double bestCoverage = 0;
            String bestCandidate = null;
            for (AttributeStats stats : attributeStats.values()) {
                double coverage = stats.coverageRatio(entryCount);
                if (coverage < 0.95) {
                    continue;
                }
                if (stats.duplicateSamples > 0 || stats.blankCount > 0) {
                    continue;
                }
                if (coverage > bestCoverage) {
                    bestCoverage = coverage;
                    bestCandidate = stats.attributeName;
                }
            }
            this.primaryKeyCandidate = bestCandidate;
        }
    }

    private static class AttributeStats {
        private final String attributeName;
        private final Map<String, Integer> valueCounts = new LinkedHashMap<>();
        private int presentCount;
        private int duplicateSamples;
        private int blankCount;
        private boolean truncated;
        private Double minimumValue;
        private Double maximumValue;
        private Double runningAverage;
        private int numericSamples;

        private AttributeStats(String attributeName) {
            this.attributeName = attributeName;
        }

        void record(String rawValue) {
            presentCount++;
            String value = rawValue == null ? "" : rawValue.trim();
            if (!StringUtils.hasLength(value)) {
                blankCount++;
            }

            if (!truncated && !valueCounts.containsKey(value) && valueCounts.size() >= MAX_TRACKED_UNIQUE_VALUES) {
                truncated = true;
            }

            if (!truncated || valueCounts.containsKey(value)) {
                int count = valueCounts.getOrDefault(value, 0) + 1;
                valueCounts.put(value, count);
                if (count == 2) {
                    duplicateSamples++;
                }
            }

            if (StringUtils.hasLength(value) && NUMERIC_PATTERN.matcher(value).matches()) {
                try {
                    double numeric = Double.parseDouble(value);
                    if (minimumValue == null || numeric < minimumValue) {
                        minimumValue = numeric;
                    }
                    if (maximumValue == null || numeric > maximumValue) {
                        maximumValue = numeric;
                    }
                    numericSamples++;
                    if (runningAverage == null) {
                        runningAverage = numeric;
                    } else {
                        runningAverage += (numeric - runningAverage) / numericSamples;
                    }
                } catch (NumberFormatException ignored) {
                    // ignore invalid numeric conversion
                }
            }
        }

        AttributeInsight toInsight() {
            return new AttributeInsight(
                    attributeName,
                    presentCount,
                    valueCounts.size(),
                    truncated,
                    duplicateSamples,
                    blankCount,
                    minimumValue,
                    maximumValue,
                    runningAverage
            );
        }

        AttributeValueDistribution toDistribution(int entryCount) {
            List<Map.Entry<String, Integer>> sorted = valueCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .collect(Collectors.toList());

            List<ValueCount> top = new ArrayList<>();
            int limit = Math.min(sorted.size(), TOP_VALUE_LIMIT);
            for (int i = 0; i < limit; i++) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                double ratio = entryCount == 0 ? 0D : entry.getValue() * 100.0 / entryCount;
                String label = StringUtils.hasLength(entry.getKey()) ? entry.getKey() : "(empty)";
                top.add(new ValueCount(label, entry.getValue(), ratio));
            }

            if (truncated && sorted.size() > limit) {
                int remaining = sorted.size() - limit;
                top.add(new ValueCount("Remaining Values (truncated)", remaining, 0D));
            }

            return new AttributeValueDistribution(attributeName, top);
        }

        double coverageRatio(int entryCount) {
            if (entryCount == 0) {
                return 0D;
            }
            return presentCount * 1.0 / entryCount;
        }
    }
}
