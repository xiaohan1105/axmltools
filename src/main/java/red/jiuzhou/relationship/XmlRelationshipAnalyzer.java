package red.jiuzhou.relationship;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Analyse XML datasets to detect value-based relationships between columns across files.
 *
 * <p>This utility is designed to scan the configured XML directories (see {@code xmlPath.*}) and
 * identify direct relationships (i.e. shared identifiers) between fields. The detected relations
 * can then be surfaced in the GUI for analysts.</p>
 */
public final class XmlRelationshipAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(XmlRelationshipAnalyzer.class);

    private XmlRelationshipAnalyzer() {
    }

    /**
     * Run analysis for the current database (derived from {@link DatabaseUtil#getDbName()}) and
     * persist the report to the configured {@code file.confPath} directory.
     *
     * @return analysis report (never {@code null})
     */
    public static RelationshipReport analyzeCurrentDatabase() {
        String dbName = DatabaseUtil.getDbName();
        String configuredPaths = YamlUtils.getProperty("xmlPath." + dbName);
        if (!StringUtils.hasLength(configuredPaths)) {
            log.warn("No xmlPath configured for database: {}", dbName);
            return RelationshipReport.empty(Collections.emptyList(), AnalyzerConfig.defaultConfig());
        }

        List<Path> baseDirs = parseConfiguredPaths(configuredPaths);
        AnalyzerConfig config = AnalyzerConfig.defaultConfig();
        RelationshipReport report = analyze(baseDirs, config, null);
        persistReport(report);
        return report;
    }

    public static RelationshipReport analyzeCurrentDatabase(AnalysisOptions options) {
        String dbName = DatabaseUtil.getDbName();
        String configuredPaths = YamlUtils.getProperty("xmlPath." + dbName);
        AnalyzerConfig config = options != null && options.getConfig() != null
            ? options.getConfig()
            : AnalyzerConfig.defaultConfig();

        if (!StringUtils.hasLength(configuredPaths)) {
            log.warn("No xmlPath configured for database: {}", dbName);
            return RelationshipReport.empty(Collections.emptyList(), config);
        }

        List<Path> baseDirs = parseConfiguredPaths(configuredPaths);
        RelationshipReport report = analyze(baseDirs, config, options);
        persistReport(report);
        return report;
    }

    /**
     * Analyse XML files inside {@code baseDirs} with the provided configuration.
     */
    public static RelationshipReport analyze(List<Path> baseDirs, AnalyzerConfig config) {
        return analyze(baseDirs, config, null);
    }

    public static RelationshipReport analyze(List<Path> baseDirs,
                                            AnalyzerConfig config,
                                            AnalysisOptions options) {
        Objects.requireNonNull(config, "config must not be null");
        if (baseDirs == null || baseDirs.isEmpty()) {
            return RelationshipReport.empty(Collections.emptyList(), config);
        }

        Map<ColumnKey, ColumnCollector> collectors = collectColumns(baseDirs, config, options);
        List<ColumnCollector> allColumns = new ArrayList<>(collectors.values());

        List<ColumnCollector> keyColumns = allColumns.stream()
            .filter(column -> column.isLikelyKey(config))
            .collect(Collectors.toList());

        Map<String, List<ColumnCollector>> valueIndex = buildValueIndex(keyColumns, config, options);
        List<Relationship> relationships = detectRelationships(allColumns, keyColumns, valueIndex, config, options);

        return new RelationshipReport(baseDirs, config, allColumns, keyColumns, relationships);
    }

    /**
     * Convert the comma-separated configuration string into existing directories.
     */
    private static List<Path> parseConfiguredPaths(String configuredPaths) {
        String[] parts = configuredPaths.split(",");
        List<Path> paths = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasLength(part)) {
                continue;
            }
            Path path = Paths.get(part.trim());
            if (Files.isDirectory(path)) {
                paths.add(path);
            } else {
                log.warn("Configured xml path does not exist or is not a directory: {}", path);
            }
        }
        return paths;
    }

    private static Map<ColumnKey, ColumnCollector> collectColumns(List<Path> baseDirs,
                                                                  AnalyzerConfig config,
                                                                  AnalysisOptions options) {
        Map<ColumnKey, ColumnCollector> collectors = new LinkedHashMap<>();
        SAXReader reader = buildSafeSaxReader();

        for (Path baseDir : baseDirs) {
            checkCancellation(options);
            try {
                Files.walk(baseDir)
                    .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .forEach(path -> {
                        checkCancellation(options);
                        notifyProgress(options, path);
                        parseFile(reader, baseDir, path, collectors, config, options);
                    });
            } catch (AnalysisCancelledException cancel) {
                throw cancel;
            } catch (Exception e) {
                log.warn("Failed to walk directory {}", baseDir, e);
            }
        }

        return collectors;
    }

    private static SAXReader buildSafeSaxReader() {
        SAXReader reader = new SAXReader();
        reader.setValidation(false);
        reader.setEntityResolver(getEmptyEntityResolver());
        return reader;
    }

    private static EntityResolver getEmptyEntityResolver() {
        return (publicId, systemId) -> new InputSource(new StringReader(""));
    }

    private static void parseFile(SAXReader reader,
                                  Path baseDir,
                                  Path file,
                                  Map<ColumnKey, ColumnCollector> collectors,
                                  AnalyzerConfig config,
                                  AnalysisOptions options) {
        checkCancellation(options);
        try {
            Document document = reader.read(file.toFile());
            Element root = document.getRootElement();
            if (root == null) {
                return;
            }
            String fileKey = buildFileKey(baseDir, file);
            ColumnTraversalContext context = new ColumnTraversalContext(fileKey, file);
            traverseElement(root, root.getName(), context, collectors, config, options);
        } catch (DocumentException e) {
            log.warn("Failed to parse XML file: {}", file, e);
        }
    }

    private static String buildFileKey(Path baseDir, Path file) {
        Path relative;
        try {
            relative = baseDir.relativize(file);
        } catch (IllegalArgumentException e) {
            relative = file.getFileName();
        }
        return relative.toString().replace(File.separatorChar, '/');
    }

    private static void traverseElement(Element element,
                                        String currentPath,
                                        ColumnTraversalContext context,
                                        Map<ColumnKey, ColumnCollector> collectors,
                                        AnalyzerConfig config,
                                        AnalysisOptions options) {
        checkCancellation(options);
        if (element == null) {
            return;
        }

        // record attributes for this element
        for (Attribute attribute : element.attributes()) {
            if (attribute == null) {
                continue;
            }
            ColumnKey key = new ColumnKey(context.fileKey,
                currentPath + "/@" + attribute.getName());
            ColumnCollector collector = collectors.computeIfAbsent(key,
                k -> new ColumnCollector(k, context, attribute.getName(), true));
            collector.addValue(attribute.getValue(), config);
        }

        List<Element> children = element.elements();
        if (children == null || children.isEmpty()) {
            ColumnKey key = new ColumnKey(context.fileKey, currentPath);
            ColumnCollector collector = collectors.computeIfAbsent(key,
                k -> new ColumnCollector(k, context, element.getName(), false));
            collector.addValue(element.getText(), config);
            return;
        }

        for (Element child : children) {
            if (child == null) {
                continue;
            }
            String childPath = currentPath + "/" + child.getName();
            traverseElement(child, childPath, context, collectors, config, options);
        }
    }

    private static Map<String, List<ColumnCollector>> buildValueIndex(List<ColumnCollector> keyColumns,
                                                                     AnalyzerConfig config,
                                                                     AnalysisOptions options) {
        Map<String, List<ColumnCollector>> valueIndex = new HashMap<>();
        for (ColumnCollector column : keyColumns) {
            checkCancellation(options);
            if (column.isOverflow()) {
                continue;
            }
            for (String value : column.getValues()) {
                checkCancellation(options);
                valueIndex.computeIfAbsent(value, k -> new ArrayList<>()).add(column);
            }
        }
        return valueIndex;
    }

    private static void notifyProgress(AnalysisOptions options, Path file) {
        if (options == null) {
            return;
        }
        Consumer<Path> callback = options.getProgressCallback();
        if (callback != null) {
            try {
                callback.accept(file);
            } catch (Exception ignored) {
            }
        }
    }

    private static void checkCancellation(AnalysisOptions options) {
        if (options == null) {
            return;
        }
        BooleanSupplier supplier = options.getCancellationRequested();
        if (supplier != null && supplier.getAsBoolean()) {
            throw new AnalysisCancelledException();
        }
    }

    private static List<Relationship> detectRelationships(List<ColumnCollector> allColumns,
                                                           List<ColumnCollector> keyColumns,
                                                           Map<String, List<ColumnCollector>> valueIndex,
                                                           AnalyzerConfig config,
                                                           AnalysisOptions options) {
        List<Relationship> relationships = new ArrayList<>();

        for (ColumnCollector source : allColumns) {
            checkCancellation(options);
            if (source.isOverflow() || source.uniqueValueCount() < config.minSourceUniqueValues) {
                continue;
            }

            Map<ColumnCollector, MatchStats> matchMap = new HashMap<>();
            for (String value : source.getValues()) {
                checkCancellation(options);
                List<ColumnCollector> candidates = valueIndex.get(value);
                if (candidates == null) {
                    continue;
                }
                for (ColumnCollector candidate : candidates) {
                    if (source == candidate) {
                        continue;
                    }
                    matchMap.computeIfAbsent(candidate, key -> new MatchStats(config.sampleSize))
                        .record(value);
                }
            }

            if (matchMap.isEmpty()) {
                continue;
            }

            List<Relationship> sourceRels = matchMap.entrySet().stream()
                .map(entry -> buildRelationship(source, entry.getKey(), entry.getValue(), config, options))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(Relationship::getConfidence).reversed())
                .collect(Collectors.toList());

            if (config.maxRelationshipsPerSource > 0 && sourceRels.size() > config.maxRelationshipsPerSource) {
                relationships.addAll(sourceRels.subList(0, config.maxRelationshipsPerSource));
            } else {
                relationships.addAll(sourceRels);
            }
        }

        relationships.sort(Comparator.comparing(Relationship::getConfidence).reversed());
        return relationships;
    }

    private static Optional<Relationship> buildRelationship(ColumnCollector source,
                                                            ColumnCollector target,
                                                            MatchStats stats,
                                                            AnalyzerConfig config,
                                                            AnalysisOptions options) {
        int matchCount = stats.getMatchCount();
        if (matchCount < config.minMatchCount) {
            return Optional.empty();
        }

        double sourceCoverage = (double) matchCount / source.uniqueValueCount();
        double targetCoverage = target.uniqueValueCount() == 0
            ? 0.0
            : (double) matchCount / target.uniqueValueCount();

        if (sourceCoverage < config.minSourceCoverage) {
            return Optional.empty();
        }
        if (targetCoverage < config.minTargetCoverage) {
            return Optional.empty();
        }

        Optional<SemanticMatch> semanticMatch = assessSemanticCompatibility(source, target, config);
        if (!semanticMatch.isPresent()) {
            return Optional.empty();
        }

        double confidence = computeConfidence(sourceCoverage, targetCoverage, source, target, semanticMatch.get(), config);

        Relationship relationship = new Relationship(
            source,
            target,
            matchCount,
            sourceCoverage,
            targetCoverage,
            confidence,
            semanticMatch.get().getNameSimilarity(),
            stats.getSamples()
        );

        return Optional.of(relationship);
    }

    private static double computeConfidence(double sourceCoverage,
                                            double targetCoverage,
                                            ColumnCollector source,
                                            ColumnCollector target,
                                            SemanticMatch semanticMatch,
                                            AnalyzerConfig config) {
        double coverageScore = (sourceCoverage * config.sourceCoverageWeight)
            + (targetCoverage * config.targetCoverageWeight);
        double nameSimilarity = semanticMatch.getNameSimilarity();
        double semanticWeight = semanticMatch.getSourceKind().isIdLike() && semanticMatch.getTargetKind().isIdLike()
            ? 0.4 : 0.6;
        double confidence = coverageScore * (semanticWeight + (1.0 - semanticWeight) * nameSimilarity);
        if (source.hasIdentifierHint()) {
            confidence += 0.05;
        }
        if (target.hasIdentifierHint()) {
            confidence += 0.05;
        }
        if (source.sameFile(target)) {
            confidence -= 0.05; // penalise self-file matches slightly
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private static Optional<SemanticMatch> assessSemanticCompatibility(ColumnCollector source,
                                                                       ColumnCollector target,
                                                                       AnalyzerConfig config) {
        ColumnCollector.SemanticKind sourceKind = source.getSemanticKind(config);
        ColumnCollector.SemanticKind targetKind = target.getSemanticKind(config);
        if (!sourceKind.isCompatibleWith(targetKind)) {
            return Optional.empty();
        }

        Set<String> sourceTokens = source.getNameTokens();
        Set<String> targetTokens = target.getNameTokens();

        double similarity;
        if (sourceTokens.isEmpty() || targetTokens.isEmpty()) {
            similarity = (sourceKind.isIdLike() && targetKind.isIdLike() && source.hasToken("id") && target.hasToken("id"))
                ? 1.0 : 0.0;
        } else {
            similarity = tokenSimilarity(sourceTokens, targetTokens);
        }

        double minSimilarity = config.getMinNameTokenSimilarity();
        if (sourceKind.isIdLike() && targetKind.isIdLike()) {
            minSimilarity = config.getMinIdNameTokenSimilarity();
        }

        if (similarity < minSimilarity) {
            return Optional.empty();
        }

        return Optional.of(new SemanticMatch(sourceKind, targetKind, similarity));
    }

    private static double tokenSimilarity(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private static final class SemanticMatch {
        private final ColumnCollector.SemanticKind sourceKind;
        private final ColumnCollector.SemanticKind targetKind;
        private final double nameSimilarity;

        SemanticMatch(ColumnCollector.SemanticKind sourceKind,
                      ColumnCollector.SemanticKind targetKind,
                      double nameSimilarity) {
            this.sourceKind = sourceKind;
            this.targetKind = targetKind;
            this.nameSimilarity = nameSimilarity;
        }

        ColumnCollector.SemanticKind getSourceKind() {
            return sourceKind;
        }

        ColumnCollector.SemanticKind getTargetKind() {
            return targetKind;
        }

        double getNameSimilarity() {
            return nameSimilarity;
        }
    }

    private static void persistReport(RelationshipReport report) {
        String confPath = YamlUtils.getProperty("file.confPath");
        if (!StringUtils.hasLength(confPath)) {
            return;
        }

        Path directory = Paths.get(confPath, "analysis");
        Path output = directory.resolve("relationship-analysis.json");
        try {
            Files.createDirectories(directory);
            String json = report.toJson();
            FileUtil.writeUtf8String(json, output.toFile());
            log.info("Relationship analysis report written to {}", output);
        } catch (Exception e) {
            log.warn("Failed to persist relationship analysis report to {}", output, e);
        }
    }

    private static final class ColumnTraversalContext {
        private final String fileKey;
        private final Path filePath;

        ColumnTraversalContext(String fileKey, Path filePath) {
            this.fileKey = fileKey;
            this.filePath = filePath;
        }

        String getFileName() {
            String filename = filePath.getFileName().toString();
            int idx = filename.lastIndexOf('.');
            return idx == -1 ? filename : filename.substring(0, idx);
        }
    }

    private static final class ColumnCollector {
        private static final Set<String> GENERIC_TOKENS = new HashSet<>(Arrays.asList(
            "data", "value", "values", "info", "list", "node", "detail", "attr", "attribute",
            "adddatanode", "group", "item", "items", "entry", "entries", "record", "records"
        ));
        private final ColumnKey key;
        private final String fileName;
        private final String columnName;
        private final boolean attribute;
        private final LinkedHashSet<String> values = new LinkedHashSet<>();
        private final LinkedHashSet<String> samples = new LinkedHashSet<>();
        private int totalCount;
        private int blankCount;
        private boolean overflow = false;
        private final Set<String> nameTokens;

        ColumnCollector(ColumnKey key, ColumnTraversalContext context, String columnName, boolean attribute) {
            this.key = key;
            this.fileName = context.getFileName();
            this.columnName = columnName;
            this.attribute = attribute;
            this.nameTokens = Collections.unmodifiableSet(tokenizeName(columnName));
        }

        void addValue(String rawValue, AnalyzerConfig config) {
            totalCount++;
            if (rawValue == null) {
                blankCount++;
                return;
            }

            String value = rawValue.trim();
            if (value.isEmpty()) {
                blankCount++;
                return;
            }

            if (value.length() > config.maxValueLength) {
                return;
            }

            if (overflow || values.size() >= config.maxUniqueValuesPerColumn) {
                overflow = true;
                return;
            }

            values.add(value);
            if (samples.size() < config.sampleSize) {
                samples.add(value);
            }
        }

        boolean isOverflow() {
            return overflow;
        }

        int uniqueValueCount() {
            return values.size();
        }

        Set<String> getValues() {
            return Collections.unmodifiableSet(values);
        }

        String getFileKey() {
            return key.fileKey;
        }

        String getColumnPath() {
            return key.columnPath;
        }

        String getColumnName() {
            return columnName;
        }

        String getFileName() {
            return fileName;
        }

        boolean isAttribute() {
            return attribute;
        }

        boolean hasIdentifierHint() {
            String lower = columnName.toLowerCase(Locale.ROOT);
            if (lower.equals("id")) {
                return true;
            }
            if (lower.endsWith("_id") || lower.endsWith("id")) {
                return true;
            }
            if (lower.endsWith("_code") || lower.contains("key")) {
                return true;
            }
            if (lower.endsWith("_name") || lower.equals("name") || lower.contains("_item") || lower.equals("item")) {
                return true;
            }
            return false;
        }

        boolean isLikelyKey(AnalyzerConfig config) {
            if (overflow) {
                return false;
            }
            int populated = totalCount - blankCount;
            if (populated < config.minRowsForKey) {
                return false;
            }
            if (values.isEmpty()) {
                return false;
            }

            double uniqueness = (double) values.size() / populated;
            String lower = columnName.toLowerCase(Locale.ROOT);

            // 🎯 只分析name字段的关联关系 - 为游戏设计师提供最可靠的对照
            // name字段通常包含：道具名称、技能名称、NPC名称、地图名称等核心配置
            if (lower.endsWith("_name") || lower.equals("name")) {
                // name字段要求较高的唯一性，确保关联质量
                return uniqueness >= config.nameUniqueness;
            }

            // 其他字段一律不作为关联键，避免噪音干扰
            return false;
        }

        boolean sameFile(ColumnCollector other) {
            return this.getFileKey().equals(other.getFileKey());
        }

        List<String> getSamples() {
            return new ArrayList<>(samples);
        }

        Set<String> getNameTokens() {
            return nameTokens;
        }

        boolean hasToken(String token) {
            return nameTokens.contains(token.toLowerCase(Locale.ROOT));
        }

        SemanticKind getSemanticKind(AnalyzerConfig config) {
            return SemanticKind.from(this, config);
        }

        boolean isEnumLike(AnalyzerConfig config) {
            int unique = uniqueValueCount();
            if (unique == 0 || unique > config.getEnumMaxUniqueValues()) {
                return false;
            }
            if (isMostlyNumeric()) {
                return false;
            }
            for (String sample : getSamples()) {
                if (sample != null && sample.length() > config.getEnumMaxSampleLength()) {
                    return false;
                }
            }
            return true;
        }

        boolean isMostlyNumeric() {
            List<String> sampleList = getSamples();
            if (sampleList.isEmpty()) {
                return false;
            }
            int numeric = 0;
            for (String sample : sampleList) {
                if (sample != null && isNumeric(sample)) {
                    numeric++;
                }
            }
            return numeric >= Math.max(2, Math.round(sampleList.size() * 0.6f));
        }

        private static boolean isNumeric(String value) {
            if (value == null || value.isEmpty()) {
                return false;
            }
            try {
                Double.parseDouble(value.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private static Set<String> tokenizeName(String name) {
            if (name == null) {
                return Collections.emptySet();
            }
            String cleaned = name
                .replace("_attr_", "")
                .replace("__", "_")
                .toLowerCase(Locale.ROOT);
            String[] parts = cleaned.split("[^a-z0-9]+");
            Set<String> tokens = new LinkedHashSet<>();
            for (String part : parts) {
                if (part == null || part.isEmpty()) {
                    continue;
                }
                if (GENERIC_TOKENS.contains(part)) {
                    continue;
                }
                tokens.add(part);
            }
            return tokens;
        }

        enum SemanticKind {
            ID,
            CODE,
            NAME,
            DESC,
            ENUM,
            NUMERIC,
            OTHER;

            boolean isIdLike() {
                return this == ID || this == CODE;
            }

            boolean isCompatibleWith(SemanticKind other) {
                if (this == other) {
                    return true;
                }
                if (this.isIdLike() && other.isIdLike()) {
                    return true;
                }
                return false;
            }

            static SemanticKind from(ColumnCollector column, AnalyzerConfig config) {
                String lower = column.columnName.toLowerCase(Locale.ROOT);

                // 🎯 优先识别NAME字段 - 游戏设计师最关心的关联
                if (column.hasToken("name") || lower.endsWith("_name") || lower.equals("name")) {
                    return NAME;
                }

                // 其他类型保持原有逻辑
                if (column.hasToken("id") || lower.endsWith("_id") || lower.equals("id")) {
                    return ID;
                }
                if (column.hasToken("code") || lower.endsWith("_code") || lower.equals("code")) {
                    return CODE;
                }
                if (column.hasToken("desc") || column.hasToken("description") || lower.contains("desc")) {
                    return DESC;
                }
                if (column.isEnumLike(config)) {
                    return ENUM;
                }
                if (column.isMostlyNumeric()) {
                    return NUMERIC;
                }
                return OTHER;
            }
        }
    }

    private static final class ColumnKey {
        private final String fileKey;
        private final String columnPath;

        ColumnKey(String fileKey, String columnPath) {
            this.fileKey = fileKey;
            this.columnPath = columnPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ColumnKey)) return false;
            ColumnKey columnKey = (ColumnKey) o;
            return Objects.equals(fileKey, columnKey.fileKey)
                && Objects.equals(columnPath, columnKey.columnPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileKey, columnPath);
        }
    }

    private static final class MatchStats {
        private final int sampleSize;
        private final LinkedHashSet<String> samples = new LinkedHashSet<>();
        private int matchCount;

        MatchStats(int sampleSize) {
            this.sampleSize = sampleSize;
        }

        void record(String value) {
            matchCount++;
            if (samples.size() < sampleSize) {
                samples.add(value);
            }
        }

        int getMatchCount() {
            return matchCount;
        }

        List<String> getSamples() {
            return new ArrayList<>(samples);
        }
    }

    public static final class AnalysisCancelledException extends RuntimeException {
        AnalysisCancelledException() {
            super("analysis_cancelled");
        }
    }

    public static final class AnalysisOptions {
        private AnalyzerConfig config;
        private Consumer<Path> progressCallback;
        private BooleanSupplier cancellationRequested;

        public static AnalysisOptions create() {
            return new AnalysisOptions();
        }

        public AnalysisOptions withConfig(AnalyzerConfig config) {
            this.config = config;
            return this;
        }

        public AnalysisOptions withProgressCallback(Consumer<Path> callback) {
            this.progressCallback = callback;
            return this;
        }

        public AnalysisOptions withCancellationSupplier(BooleanSupplier supplier) {
            this.cancellationRequested = supplier;
            return this;
        }

        AnalyzerConfig getConfig() {
            return config;
        }

        Consumer<Path> getProgressCallback() {
            return progressCallback;
        }

        BooleanSupplier getCancellationRequested() {
            return cancellationRequested;
        }
    }

    public static final class Relationship {
        private final String sourceFileKey;
        private final String sourceColumnName;
        private final String sourceColumnPath;
        private final String targetFileKey;
        private final String targetColumnName;
        private final String targetColumnPath;
        private final int matchCount;
        private final double sourceCoverage;
        private final double targetCoverage;
        private final double confidence;
        private final double nameTokenSimilarity;
        private final List<String> samples;

        Relationship(ColumnCollector source,
                     ColumnCollector target,
                     int matchCount,
                     double sourceCoverage,
                     double targetCoverage,
                     double confidence,
                     double nameTokenSimilarity,
                     List<String> samples) {
            this.sourceFileKey = source.getFileKey();
            this.sourceColumnName = source.getColumnName();
            this.sourceColumnPath = source.getColumnPath();
            this.targetFileKey = target.getFileKey();
            this.targetColumnName = target.getColumnName();
            this.targetColumnPath = target.getColumnPath();
            this.matchCount = matchCount;
            this.sourceCoverage = sourceCoverage;
            this.targetCoverage = targetCoverage;
            this.confidence = confidence;
            this.nameTokenSimilarity = nameTokenSimilarity;
            this.samples = samples;
        }

        public String getSourceFileKey() {
            return sourceFileKey;
        }

        public String getSourceColumnName() {
            return sourceColumnName;
        }

        public String getSourceColumnPath() {
            return sourceColumnPath;
        }

        public String getTargetFileKey() {
            return targetFileKey;
        }

        public String getTargetColumnName() {
            return targetColumnName;
        }

        public String getTargetColumnPath() {
            return targetColumnPath;
        }

        public int getMatchCount() {
            return matchCount;
        }

        public double getSourceCoverage() {
            return sourceCoverage;
        }

        public double getTargetCoverage() {
            return targetCoverage;
        }

        public double getConfidence() {
            return confidence;
        }

        public double getNameTokenSimilarity() {
            return nameTokenSimilarity;
        }

        public List<String> getSamples() {
            return samples;
        }

        public String getFormattedSourcePath() {
            return getSourceFileKey() + " :: " + getSourceColumnPath();
        }

        public String getFormattedTargetPath() {
            return getTargetFileKey() + " :: " + getTargetColumnPath();
        }
    }

    public static final class RelationshipReport {
        private final List<Path> baseDirectories;
        private final AnalyzerConfig config;
        private final List<ColumnCollector> columns;
        private final List<ColumnCollector> keyColumns;
        private final List<Relationship> relationships;
        private final Instant generatedAt;

        RelationshipReport(List<Path> baseDirectories,
                           AnalyzerConfig config,
                           List<ColumnCollector> columns,
                           List<ColumnCollector> keyColumns,
                           List<Relationship> relationships) {
            this.baseDirectories = Collections.unmodifiableList(new ArrayList<>(baseDirectories));
            this.config = config;
            this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
            this.keyColumns = Collections.unmodifiableList(new ArrayList<>(keyColumns));
            this.relationships = Collections.unmodifiableList(new ArrayList<>(relationships));
            this.generatedAt = Instant.now();
        }

        static RelationshipReport empty(List<Path> baseDirectories, AnalyzerConfig config) {
            return new RelationshipReport(baseDirectories, config, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        public List<Relationship> getRelationships() {
            return relationships;
        }

        public List<RelationshipSnapshot> getRelationshipSnapshots() {
            List<RelationshipSnapshot> snapshots = new ArrayList<>(relationships.size());
            for (Relationship relationship : relationships) {
                snapshots.add(new RelationshipSnapshot(
                    relationship.getSourceFileKey(),
                    relationship.getSourceColumnName(),
                    relationship.getSourceColumnPath(),
                    relationship.getTargetFileKey(),
                    relationship.getTargetColumnName(),
                    relationship.getTargetColumnPath(),
                    relationship.getMatchCount(),
                    relationship.getSourceCoverage(),
                    relationship.getTargetCoverage(),
                    relationship.getConfidence(),
                    relationship.getNameTokenSimilarity(),
                    relationship.getSamples()
                ));
            }
            return snapshots;
        }

        public String toJson() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("generated_at", generatedAt.toString());
            root.put("base_directories", baseDirectories.stream().map(Path::toString).collect(Collectors.toList()));
            root.put("relationship_count", relationships.size());
            root.put("config", config.toMap());
            root.put("relationships", buildRelationshipArray());
            return JSON.toJSONString(root, SerializerFeature.PrettyFormat);
        }

        private JSONArray buildRelationshipArray() {
            JSONArray array = new JSONArray();
            for (Relationship relationship : relationships) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source_file", relationship.getSourceFileKey());
                item.put("source_column", relationship.getSourceColumnName());
                item.put("source_path", relationship.getSourceColumnPath());
                item.put("target_file", relationship.getTargetFileKey());
                item.put("target_column", relationship.getTargetColumnName());
                item.put("target_path", relationship.getTargetColumnPath());
                item.put("match_count", relationship.getMatchCount());
                item.put("source_coverage", relationship.getSourceCoverage());
                item.put("target_coverage", relationship.getTargetCoverage());
                item.put("confidence", relationship.getConfidence());
                item.put("name_similarity", relationship.getNameTokenSimilarity());
                item.put("sample_values", relationship.getSamples());
                array.add(item);
            }
            return array;
        }
    }

    public static final class AnalyzerConfig {
        private final double minSourceCoverage;
        private final double minTargetCoverage;
        private final int minMatchCount;
        private final int minRowsForKey;
        private final double minUniqueness;
        private final double strictUniqueness;
        private final double nameUniqueness;
        private final int maxUniqueValuesPerColumn;
        private final int maxValueLength;
        private final int sampleSize;
        private final int maxRelationshipsPerSource;
        private final int minSourceUniqueValues;
        private final double sourceCoverageWeight;
        private final double targetCoverageWeight;
        private final double minNameTokenSimilarity;
        private final double minIdNameTokenSimilarity;
        private final int enumMaxUniqueValues;
        private final int enumMaxSampleLength;

        private AnalyzerConfig(double minSourceCoverage,
                               double minTargetCoverage,
                               int minMatchCount,
                               int minRowsForKey,
                               double minUniqueness,
                               double strictUniqueness,
                               double nameUniqueness,
                               int maxUniqueValuesPerColumn,
                               int maxValueLength,
                               int sampleSize,
                               int maxRelationshipsPerSource,
                               int minSourceUniqueValues,
                               double sourceCoverageWeight,
                               double targetCoverageWeight,
                               double minNameTokenSimilarity,
                               double minIdNameTokenSimilarity,
                               int enumMaxUniqueValues,
                               int enumMaxSampleLength) {
            this.minSourceCoverage = minSourceCoverage;
            this.minTargetCoverage = minTargetCoverage;
            this.minMatchCount = minMatchCount;
            this.minRowsForKey = minRowsForKey;
            this.minUniqueness = minUniqueness;
            this.strictUniqueness = strictUniqueness;
            this.nameUniqueness = nameUniqueness;
            this.maxUniqueValuesPerColumn = maxUniqueValuesPerColumn;
            this.maxValueLength = maxValueLength;
            this.sampleSize = sampleSize;
            this.maxRelationshipsPerSource = maxRelationshipsPerSource;
            this.minSourceUniqueValues = minSourceUniqueValues;
            this.sourceCoverageWeight = sourceCoverageWeight;
            this.targetCoverageWeight = targetCoverageWeight;
            this.minNameTokenSimilarity = minNameTokenSimilarity;
            this.minIdNameTokenSimilarity = minIdNameTokenSimilarity;
            this.enumMaxUniqueValues = enumMaxUniqueValues;
            this.enumMaxSampleLength = enumMaxSampleLength;
        }

        static AnalyzerConfig defaultConfig() {
            return new AnalyzerConfig(
                0.6,   // minSourceCoverage
                0.02,  // minTargetCoverage
                3,     // minMatchCount
                5,     // minRowsForKey
                0.85,  // minUniqueness
                0.98,  // strictUniqueness
                0.95,  // nameUniqueness
                120_000, // maxUniqueValuesPerColumn
                256,   // maxValueLength
                5,     // sampleSize
                5,     // maxRelationshipsPerSource
                3,     // minSourceUniqueValues
                0.7,   // sourceCoverageWeight
                0.3,   // targetCoverageWeight
                0.45,  // minNameTokenSimilarity
                0.25,  // minIdNameTokenSimilarity
                40,    // enumMaxUniqueValues
                32     // enumMaxSampleLength
            );
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("min_source_coverage", minSourceCoverage);
            map.put("min_target_coverage", minTargetCoverage);
            map.put("min_match_count", minMatchCount);
            map.put("min_rows_for_key", minRowsForKey);
            map.put("min_uniqueness", minUniqueness);
            map.put("strict_uniqueness", strictUniqueness);
            map.put("name_uniqueness", nameUniqueness);
            map.put("max_unique_values_per_column", maxUniqueValuesPerColumn);
            map.put("max_value_length", maxValueLength);
            map.put("sample_size", sampleSize);
            map.put("max_relationships_per_source", maxRelationshipsPerSource);
            map.put("min_source_unique_values", minSourceUniqueValues);
            map.put("source_coverage_weight", sourceCoverageWeight);
            map.put("target_coverage_weight", targetCoverageWeight);
            map.put("min_name_token_similarity", minNameTokenSimilarity);
            map.put("min_id_name_token_similarity", minIdNameTokenSimilarity);
            map.put("enum_max_unique_values", enumMaxUniqueValues);
            map.put("enum_max_sample_length", enumMaxSampleLength);
            return map;
        }

        double getMinNameTokenSimilarity() {
            return minNameTokenSimilarity;
        }

        double getMinIdNameTokenSimilarity() {
            return minIdNameTokenSimilarity;
        }

        int getEnumMaxUniqueValues() {
            return enumMaxUniqueValues;
        }

        int getEnumMaxSampleLength() {
            return enumMaxSampleLength;
        }
    }

    public static final class RelationshipSnapshot {
        private final String sourceFile;
        private final String sourceColumn;
        private final String sourcePath;
        private final String targetFile;
        private final String targetColumn;
        private final String targetPath;
        private final int matchCount;
        private final double sourceCoverage;
        private final double targetCoverage;
        private final double confidence;
        private final double nameSimilarity;
        private final List<String> samples;

        RelationshipSnapshot(String sourceFile,
                             String sourceColumn,
                             String sourcePath,
                             String targetFile,
                             String targetColumn,
                             String targetPath,
                             int matchCount,
                             double sourceCoverage,
                             double targetCoverage,
                             double confidence,
                             double nameSimilarity,
                             List<String> samples) {
            this.sourceFile = sourceFile;
            this.sourceColumn = sourceColumn;
            this.sourcePath = sourcePath;
            this.targetFile = targetFile;
            this.targetColumn = targetColumn;
            this.targetPath = targetPath;
            this.matchCount = matchCount;
            this.sourceCoverage = sourceCoverage;
            this.targetCoverage = targetCoverage;
            this.confidence = confidence;
            this.nameSimilarity = nameSimilarity;
            this.samples = samples == null ? Collections.emptyList() : new ArrayList<>(samples);
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public String getSourceColumn() {
            return sourceColumn;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public String getTargetFile() {
            return targetFile;
        }

        public String getTargetColumn() {
            return targetColumn;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public int getMatchCount() {
            return matchCount;
        }

        public double getSourceCoverage() {
            return sourceCoverage;
        }

        public double getTargetCoverage() {
            return targetCoverage;
        }

        public double getConfidence() {
            return confidence;
        }

        public double getNameSimilarity() {
            return nameSimilarity;
        }

        public List<String> getSamples() {
            return samples;
        }
    }
}
