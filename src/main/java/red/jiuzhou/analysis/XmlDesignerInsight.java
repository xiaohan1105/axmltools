package red.jiuzhou.analysis;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XML设计洞察数据结构
 *
 * <p>聚合单个XML文件的洞察信息,为游戏策划提供数据分析支持
 *
 * <p><b>核心功能:</b>
 * <ul>
 *   <li>文件摘要信息(路径、大小、条目数等)</li>
 *   <li>指标统计(数值分布、唯一值数量等)</li>
 *   <li>优化建议(基于数据特征生成)</li>
 *   <li>属性洞察(字段类型、值域分析)</li>
 *   <li>样本记录(便于快速预览)</li>
 *   <li>关联分析(字段相关性、平衡性问题)</li>
 * </ul>
 *
 * <p><b>使用场景:</b>
 * <ul>
 *   <li>游戏配置数据质量检查</li>
 *   <li>数值平衡性分析</li>
 *   <li>字段相关性发现</li>
 *   <li>异常数据识别</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 * @see XmlDesignerInsightService
 * @see DataCorrelationAnalyzer
 */
public class XmlDesignerInsight {

    private final XmlFileSummary fileSummary;
    private final List<Metric> metrics;
    private final List<Suggestion> suggestions;
    private final List<AttributeInsight> attributeInsights;
    private final List<Map<String, String>> sampleRecords;
    private final List<AttributeValueDistribution> distributions;
    private final List<RelatedFileComparison> relatedComparisons;
    private final int entryCount;
    private final List<DataCorrelationAnalyzer.FieldCorrelation> correlations;
    private final List<DataCorrelationAnalyzer.DistributionProfile> distributionProfiles;
    private final List<DataCorrelationAnalyzer.BalanceIssue> balanceIssues;
    private final Map<String, DataCorrelationAnalyzer.AttributeType> attributeTypes;

    private XmlDesignerInsight(Builder builder) {
        this.fileSummary = builder.fileSummary;
        this.metrics = Collections.unmodifiableList(new ArrayList<>(builder.metrics));
        this.suggestions = Collections.unmodifiableList(new ArrayList<>(builder.suggestions));
        this.attributeInsights = Collections.unmodifiableList(new ArrayList<>(builder.attributeInsights));
        this.sampleRecords = Collections.unmodifiableList(new ArrayList<>(builder.sampleRecords));
        this.distributions = Collections.unmodifiableList(new ArrayList<>(builder.distributions));
        this.relatedComparisons = Collections.unmodifiableList(new ArrayList<>(builder.relatedComparisons));
        this.entryCount = builder.entryCount;
        this.correlations = Collections.unmodifiableList(new ArrayList<>(builder.correlations));
        this.distributionProfiles = Collections.unmodifiableList(new ArrayList<>(builder.distributionProfiles));
        this.balanceIssues = Collections.unmodifiableList(new ArrayList<>(builder.balanceIssues));
        this.attributeTypes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributeTypes));
    }

    public XmlFileSummary getFileSummary() {
        return fileSummary;
    }

    public List<Metric> getMetrics() {
        return metrics;
    }

    public List<Suggestion> getSuggestions() {
        return suggestions;
    }

    public List<AttributeInsight> getAttributeInsights() {
        return attributeInsights;
    }

    public List<Map<String, String>> getSampleRecords() {
        return sampleRecords;
    }

    public List<AttributeValueDistribution> getDistributions() {
        return distributions;
    }

    public List<RelatedFileComparison> getRelatedComparisons() {
        return relatedComparisons;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public List<DataCorrelationAnalyzer.FieldCorrelation> getCorrelations() {
        return correlations;
    }

    public List<DataCorrelationAnalyzer.DistributionProfile> getDistributionProfiles() {
        return distributionProfiles;
    }

    public List<DataCorrelationAnalyzer.BalanceIssue> getBalanceIssues() {
        return balanceIssues;
    }

    public Map<String, DataCorrelationAnalyzer.AttributeType> getAttributeTypes() {
        return attributeTypes;
    }

    public static Builder builder(XmlFileSummary summary) {
        return new Builder(summary);
    }

    public static class Builder {
        private final XmlFileSummary fileSummary;
        private final List<Metric> metrics = new ArrayList<>();
        private final List<Suggestion> suggestions = new ArrayList<>();
        private final List<AttributeInsight> attributeInsights = new ArrayList<>();
        private final List<Map<String, String>> sampleRecords = new ArrayList<>();
        private final List<AttributeValueDistribution> distributions = new ArrayList<>();
        private final List<RelatedFileComparison> relatedComparisons = new ArrayList<>();
        private final List<DataCorrelationAnalyzer.FieldCorrelation> correlations = new ArrayList<>();
        private final List<DataCorrelationAnalyzer.DistributionProfile> distributionProfiles = new ArrayList<>();
        private final List<DataCorrelationAnalyzer.BalanceIssue> balanceIssues = new ArrayList<>();
        private final Map<String, DataCorrelationAnalyzer.AttributeType> attributeTypes = new LinkedHashMap<>();
        private int entryCount;

        private Builder(XmlFileSummary summary) {
            this.fileSummary = summary;
        }

        public Builder addMetric(Metric metric) {
            if (metric != null) {
                metrics.add(metric);
            }
            return this;
        }

        public Builder addSuggestion(Suggestion suggestion) {
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
            return this;
        }

        public Builder addAttributeInsight(AttributeInsight insight) {
            if (insight != null) {
                attributeInsights.add(insight);
            }
            return this;
        }

        public Builder addSampleRecord(Map<String, String> record) {
            if (record != null) {
                sampleRecords.add(new LinkedHashMap<>(record));
            }
            return this;
        }

        public Builder addDistribution(AttributeValueDistribution distribution) {
            if (distribution != null) {
                distributions.add(distribution);
            }
            return this;
        }

        public Builder addRelatedComparison(RelatedFileComparison comparison) {
            if (comparison != null) {
                relatedComparisons.add(comparison);
            }
            return this;
        }

        public Builder withEntryCount(int entryCount) {
            this.entryCount = Math.max(entryCount, 0);
            return this;
        }

        public Builder addCorrelation(DataCorrelationAnalyzer.FieldCorrelation correlation) {
            if (correlation != null) {
                correlations.add(correlation);
            }
            return this;
        }

        public Builder addDistributionProfile(DataCorrelationAnalyzer.DistributionProfile profile) {
            if (profile != null) {
                distributionProfiles.add(profile);
            }
            return this;
        }

        public Builder addBalanceIssue(DataCorrelationAnalyzer.BalanceIssue issue) {
            if (issue != null) {
                balanceIssues.add(issue);
            }
            return this;
        }

        public Builder addAttributeType(String fieldName, DataCorrelationAnalyzer.AttributeType type) {
            if (fieldName != null && type != null) {
                attributeTypes.put(fieldName, type);
            }
            return this;
        }

        public XmlDesignerInsight build() {
            return new XmlDesignerInsight(this);
        }
    }

    public static class XmlFileSummary {
        private final Path path;
        private final String displayName;
        private final long fileSize;
        private final Instant lastModified;
        private final String rootElement;
        private final String entryElement;
        private final String inferredTableName;
        private final boolean tableExists;
        private final Integer databaseRowCount;

        public XmlFileSummary(Path path,
                              String displayName,
                              long fileSize,
                              Instant lastModified,
                              String rootElement,
                              String entryElement,
                              String inferredTableName,
                              boolean tableExists,
                              Integer databaseRowCount) {
            this.path = path;
            this.displayName = displayName;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
            this.rootElement = rootElement;
            this.entryElement = entryElement;
            this.inferredTableName = inferredTableName;
            this.tableExists = tableExists;
            this.databaseRowCount = databaseRowCount;
        }

        public Path getPath() {
            return path;
        }

        public String getDisplayName() {
            return displayName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public Instant getLastModified() {
            return lastModified;
        }

        public String getRootElement() {
            return rootElement;
        }

        public String getEntryElement() {
            return entryElement;
        }

        public String getInferredTableName() {
            return inferredTableName;
        }

        public boolean isTableExists() {
            return tableExists;
        }

        public Integer getDatabaseRowCount() {
            return databaseRowCount;
        }
    }

    public static class Metric {
        private final String name;
        private final String value;
        private final String detail;

        public Metric(String name, String value, String detail) {
            this.name = name;
            this.value = value;
            this.detail = detail;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String getDetail() {
            return detail;
        }
    }

    public static class Suggestion {
        private final String title;
        private final String description;
        private final Severity severity;

        public Suggestion(String title, String description, Severity severity) {
            this.title = title;
            this.description = description;
            this.severity = severity;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public Severity getSeverity() {
            return severity;
        }
    }

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    public static class AttributeInsight {
        private final String name;
        private final int presentCount;
        private final int uniqueCount;
        private final boolean uniqueCountTruncated;
        private final int duplicateSamples;
        private final int blankCount;
        private final Double minimumValue;
        private final Double maximumValue;
        private final Double averageValue;

        public AttributeInsight(String name,
                                int presentCount,
                                int uniqueCount,
                                boolean uniqueCountTruncated,
                                int duplicateSamples,
                                int blankCount,
                                Double minimumValue,
                                Double maximumValue,
                                Double averageValue) {
            this.name = name;
            this.presentCount = presentCount;
            this.uniqueCount = uniqueCount;
            this.uniqueCountTruncated = uniqueCountTruncated;
            this.duplicateSamples = duplicateSamples;
            this.blankCount = blankCount;
            this.minimumValue = minimumValue;
            this.maximumValue = maximumValue;
            this.averageValue = averageValue;
        }

        public String getName() {
            return name;
        }

        public int getPresentCount() {
            return presentCount;
        }

        public int getUniqueCount() {
            return uniqueCount;
        }

        public boolean isUniqueCountTruncated() {
            return uniqueCountTruncated;
        }

        public int getDuplicateSamples() {
            return duplicateSamples;
        }

        public int getBlankCount() {
            return blankCount;
        }

        public Double getMinimumValue() {
            return minimumValue;
        }

        public Double getMaximumValue() {
            return maximumValue;
        }

        public Double getAverageValue() {
            return averageValue;
        }
    }

    public static class AttributeValueDistribution {
        private final String attributeName;
        private final List<ValueCount> topValues;

        public AttributeValueDistribution(String attributeName, List<ValueCount> topValues) {
            this.attributeName = attributeName;
            this.topValues = Collections.unmodifiableList(new ArrayList<>(topValues));
        }

        public String getAttributeName() {
            return attributeName;
        }

        public List<ValueCount> getTopValues() {
            return topValues;
        }
    }

    public static class ValueCount {
        private final String value;
        private final int count;
        private final double percentage;

        public ValueCount(String value, int count, double percentage) {
            this.value = value;
            this.count = count;
            this.percentage = percentage;
        }

        public String getValue() {
            return value;
        }

        public int getCount() {
            return count;
        }

        public double getPercentage() {
            return percentage;
        }
    }

    public static class RelatedFileComparison {
        private final XmlFileSummary relatedSummary;
        private final String relationHint;
        private final double similarityScore;
        private final int entryCount;
        private final int entryDelta;
        private final int sharedAttributeCount;
        private final int onlyInCurrentCount;
        private final int onlyInRelatedCount;
        private final List<AttributeAlignment> alignments;

        public RelatedFileComparison(XmlFileSummary relatedSummary,
                                     String relationHint,
                                     double similarityScore,
                                     int entryCount,
                                     int entryDelta,
                                     int sharedAttributeCount,
                                     int onlyInCurrentCount,
                                     int onlyInRelatedCount,
                                     List<AttributeAlignment> alignments) {
            this.relatedSummary = relatedSummary;
            this.relationHint = relationHint;
            this.similarityScore = similarityScore;
            this.entryCount = entryCount;
            this.entryDelta = entryDelta;
            this.sharedAttributeCount = sharedAttributeCount;
            this.onlyInCurrentCount = onlyInCurrentCount;
            this.onlyInRelatedCount = onlyInRelatedCount;
            this.alignments = Collections.unmodifiableList(new ArrayList<>(alignments));
        }

        public XmlFileSummary getRelatedSummary() {
            return relatedSummary;
        }

        public String getRelationHint() {
            return relationHint;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public int getEntryCount() {
            return entryCount;
        }

        public int getEntryDelta() {
            return entryDelta;
        }

        public int getSharedAttributeCount() {
            return sharedAttributeCount;
        }

        public int getOnlyInCurrentCount() {
            return onlyInCurrentCount;
        }

        public int getOnlyInRelatedCount() {
            return onlyInRelatedCount;
        }

        public List<AttributeAlignment> getAlignments() {
            return alignments;
        }
    }

    public static class AttributeAlignment {
        private final String attributeName;
        private final AlignmentCategory category;
        private final double currentCoverage;
        private final double relatedCoverage;
        private final double coverageDelta;

        public AttributeAlignment(String attributeName,
                                  AlignmentCategory category,
                                  double currentCoverage,
                                  double relatedCoverage,
                                  double coverageDelta) {
            this.attributeName = attributeName;
            this.category = category;
            this.currentCoverage = currentCoverage;
            this.relatedCoverage = relatedCoverage;
            this.coverageDelta = coverageDelta;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public AlignmentCategory getCategory() {
            return category;
        }

        public double getCurrentCoverage() {
            return currentCoverage;
        }

        public double getRelatedCoverage() {
            return relatedCoverage;
        }

        public double getCoverageDelta() {
            return coverageDelta;
        }
    }

    public enum AlignmentCategory {
        SHARED,
        ONLY_CURRENT,
        ONLY_RELATED
    }
}
