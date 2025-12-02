package red.jiuzhou.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据关联性分析器
 *
 * <p>为游戏策划提供数据直觉和关联洞察,帮助发现字段间的隐藏关系
 *
 * <p><b>核心功能:</b>
 * <ul>
 *   <li>字段相关性分析 - 自动发现字段间的正负相关关系</li>
 *   <li>数据分布分析 - 识别正态分布、均匀分布等模式</li>
 *   <li>平衡性检查 - 发现数值平衡问题和异常点</li>
 *   <li>属性类型推断 - 智能识别游戏属性(攻击、防御、等级等)</li>
 *   <li>增长模式识别 - 识别线性、幂次、阶梯增长等模式</li>
 * </ul>
 *
 * <p><b>技术特点:</b>
 * <ul>
 *   <li>基于统计学方法(相关系数、标准差等)</li>
 *   <li>游戏领域知识增强(识别常见属性模式)</li>
 *   <li>生成策划友好的中文洞察提示</li>
 *   <li>支持多维度数据关联分析</li>
 * </ul>
 *
 * <p><b>使用示例:</b>
 * <pre>{@code
 * DataCorrelationAnalyzer analyzer = new DataCorrelationAnalyzer();
 * List<FieldCorrelation> correlations = analyzer.analyzeCorrelations(records);
 * List<BalanceIssue> issues = analyzer.detectBalanceIssues(records);
 * }</pre>
 *
 * @author Claude
 * @version 1.0
 * @see XmlDesignerInsight
 * @see BalanceImpactAnalyzer
 */
public class DataCorrelationAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DataCorrelationAnalyzer.class);

    // 常见游戏属性模式识别
    private static final Pattern ID_PATTERN = Pattern.compile("(?i).*(id|编号|序号).*");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("(?i).*(level|等级|lv|lvl).*");
    private static final Pattern HP_PATTERN = Pattern.compile("(?i).*(hp|blood|health|血量|生命).*");
    private static final Pattern ATTACK_PATTERN = Pattern.compile("(?i).*(atk|attack|攻击|伤害|damage).*");
    private static final Pattern DEFENSE_PATTERN = Pattern.compile("(?i).*(def|defense|防御|护甲).*");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i).*(price|金币|金钱|cost|价格).*");
    private static final Pattern QUALITY_PATTERN = Pattern.compile("(?i).*(quality|品质|rarity|稀有度|rare).*");
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("(?i).*(weight|权重|概率|probability).*");

    /**
     * 字段相关性分析结果
     */
    public static class FieldCorrelation {
        private final String field1;
        private final String field2;
        private final double correlation; // -1到1之间，越接近1或-1表示相关性越强
        private final CorrelationType type;
        private final String insight; // 给策划的直观提示

        public FieldCorrelation(String field1, String field2, double correlation, CorrelationType type, String insight) {
            this.field1 = field1;
            this.field2 = field2;
            this.correlation = correlation;
            this.type = type;
            this.insight = insight;
        }

        public String getField1() { return field1; }
        public String getField2() { return field2; }
        public double getCorrelation() { return correlation; }
        public CorrelationType getType() { return type; }
        public String getInsight() { return insight; }
    }

    public enum CorrelationType {
        POSITIVE_LINEAR("正相关 - 同步增长"),
        NEGATIVE_LINEAR("负相关 - 反向变化"),
        POWER_GROWTH("幂次增长 - 指数型"),
        LINEAR_GROWTH("线性增长 - 匀速型"),
        STEP_GROWTH("阶梯增长 - 分段型"),
        NO_CORRELATION("无明显关联");

        private final String displayName;
        CorrelationType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /**
     * 数据分布特征
     */
    public static class DistributionProfile {
        private final String fieldName;
        private final DistributionType type;
        private final double skewness; // 偏度：正值右偏，负值左偏
        private final double evenness; // 均匀度：0-1之间
        private final String insight;
        private final List<GapInfo> gaps; // 数值间隙

        public DistributionProfile(String fieldName, DistributionType type, double skewness,
                                  double evenness, String insight, List<GapInfo> gaps) {
            this.fieldName = fieldName;
            this.type = type;
            this.skewness = skewness;
            this.evenness = evenness;
            this.insight = insight;
            this.gaps = gaps;
        }

        public String getFieldName() { return fieldName; }
        public DistributionType getType() { return type; }
        public double getSkewness() { return skewness; }
        public double getEvenness() { return evenness; }
        public String getInsight() { return insight; }
        public List<GapInfo> getGaps() { return gaps; }
    }

    public enum DistributionType {
        UNIFORM("均匀分布"),
        NORMAL("正态分布"),
        SKEWED_LEFT("左偏分布"),
        SKEWED_RIGHT("右偏分布"),
        POWER_LAW("幂律分布"),
        DISCRETE("离散分布");

        private final String displayName;
        DistributionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /**
     * 数值间隙信息
     */
    public static class GapInfo {
        private final double start;
        private final double end;
        private final String description;

        public GapInfo(double start, double end, String description) {
            this.start = start;
            this.end = end;
            this.description = description;
        }

        public double getStart() { return start; }
        public double getEnd() { return end; }
        public String getDescription() { return description; }
    }

    /**
     * 平衡性问题
     */
    public static class BalanceIssue {
        private final String category;
        private final Severity severity;
        private final String description;
        private final String suggestion;
        private final List<String> affectedRecords;

        public BalanceIssue(String category, Severity severity, String description,
                          String suggestion, List<String> affectedRecords) {
            this.category = category;
            this.severity = severity;
            this.description = description;
            this.suggestion = suggestion;
            this.affectedRecords = affectedRecords;
        }

        public String getCategory() { return category; }
        public Severity getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getSuggestion() { return suggestion; }
        public List<String> getAffectedRecords() { return affectedRecords; }
    }

    public enum Severity {
        CRITICAL("严重"), WARNING("警告"), INFO("提示");

        private final String displayName;
        Severity(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /**
     * 属性类型识别
     */
    public static class AttributeType {
        private final String fieldName;
        private final GameAttributeCategory category;
        private final String description;

        public AttributeType(String fieldName, GameAttributeCategory category, String description) {
            this.fieldName = fieldName;
            this.category = category;
            this.description = description;
        }

        public String getFieldName() { return fieldName; }
        public GameAttributeCategory getCategory() { return category; }
        public String getDescription() { return description; }
    }

    public enum GameAttributeCategory {
        IDENTIFIER("标识符", "唯一识别数据"),
        COMBAT_STAT("战斗属性", "影响战斗能力"),
        ECONOMY("经济属性", "金币、价格等"),
        PROGRESSION("成长属性", "等级、经验等"),
        QUALITY("品质属性", "稀有度、质量等"),
        PROBABILITY("概率权重", "随机掉落等"),
        REFERENCE("外键引用", "关联其他表"),
        DESCRIPTIVE("描述性", "名称、描述文本"),
        UNKNOWN("未分类", "");

        private final String displayName;
        private final String description;

        GameAttributeCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * 识别字段类型
     */
    public static AttributeType identifyAttributeType(String fieldName) {
        if (ID_PATTERN.matcher(fieldName).matches()) {
            return new AttributeType(fieldName, GameAttributeCategory.IDENTIFIER, "唯一标识，用于关联其他数据");
        }
        if (LEVEL_PATTERN.matcher(fieldName).matches()) {
            return new AttributeType(fieldName, GameAttributeCategory.PROGRESSION, "等级相关，影响成长曲线");
        }
        if (HP_PATTERN.matcher(fieldName).matches()) {
            return new AttributeType(fieldName, GameAttributeCategory.COMBAT_STAT, "生命值，核心战斗属性");
        }
        if (ATTACK_PATTERN.matcher(fieldName).matches()) {
            return new AttributeType(fieldName, GameAttributeCategory.COMBAT_STAT, "攻击力，影响输出能力");
        }
        if (DEFENSE_PATTERN.matcher(fieldName).matches()) {
            return new AttributeType(fieldName, GameAttributeCategory.COMBAT_STAT, "防御力，影响生存能力");
        }
        if (PRICE_PATTERN.matcher(fieldName).matches()) {
            return new AttributeType(fieldName, GameAttributeCategory.ECONOMY, "价格，影响游戏经济平衡");
        }
        if (QUALITY_PATTERN.matcher(fieldName).matches()) {
            return new AttributeType(fieldName, GameAttributeCategory.QUALITY, "品质，决定稀有度和价值");
        }
        if (WEIGHT_PATTERN.matcher(fieldName).matches()) {
            return new AttributeType(fieldName, GameAttributeCategory.PROBABILITY, "权重，影响随机概率");
        }
        return new AttributeType(fieldName, GameAttributeCategory.UNKNOWN, "");
    }

    /**
     * 计算两个数值字段的相关性
     */
    public static FieldCorrelation analyzeCorrelation(
            String field1Name, List<Double> values1,
            String field2Name, List<Double> values2) {

        if (values1.size() != values2.size() || values1.isEmpty()) {
            return new FieldCorrelation(field1Name, field2Name, 0,
                CorrelationType.NO_CORRELATION, "数据不足或维度不匹配");
        }

        // 计算皮尔逊相关系数
        double correlation = calculatePearsonCorrelation(values1, values2);

        // 判断相关性类型
        CorrelationType type;
        String insight;

        if (Math.abs(correlation) < 0.3) {
            type = CorrelationType.NO_CORRELATION;
            insight = String.format("%s 和 %s 没有明显关联，可以独立调整", field1Name, field2Name);
        } else if (correlation > 0.7) {
            // 检查是否是幂次增长
            boolean isPowerGrowth = detectPowerGrowth(values1, values2);
            if (isPowerGrowth) {
                type = CorrelationType.POWER_GROWTH;
                insight = String.format("%s 随 %s 呈指数增长，注意后期数值膨胀风险", field2Name, field1Name);
            } else {
                type = CorrelationType.LINEAR_GROWTH;
                insight = String.format("%s 随 %s 线性增长，增长平稳", field2Name, field1Name);
            }
        } else if (correlation > 0.3) {
            type = CorrelationType.POSITIVE_LINEAR;
            insight = String.format("%s 和 %s 呈正相关，一起增长", field1Name, field2Name);
        } else if (correlation < -0.3) {
            type = CorrelationType.NEGATIVE_LINEAR;
            insight = String.format("%s 增大时 %s 减小，存在制衡关系", field1Name, field2Name);
        } else {
            type = CorrelationType.NO_CORRELATION;
            insight = String.format("%s 和 %s 弱相关", field1Name, field2Name);
        }

        return new FieldCorrelation(field1Name, field2Name, correlation, type, insight);
    }

    /**
     * 计算皮尔逊相关系数
     */
    private static double calculatePearsonCorrelation(List<Double> x, List<Double> y) {
        int n = x.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += x.get(i);
            sumY += y.get(i);
            sumXY += x.get(i) * y.get(i);
            sumX2 += x.get(i) * x.get(i);
            sumY2 += y.get(i) * y.get(i);
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        if (denominator == 0) return 0;
        return numerator / denominator;
    }

    /**
     * 检测是否为幂次增长
     */
    private static boolean detectPowerGrowth(List<Double> x, List<Double> y) {
        // 简化判断：计算增长率的变化
        if (x.size() < 5) return false;

        List<Double> growthRates = new ArrayList<>();
        for (int i = 1; i < Math.min(x.size(), 10); i++) {
            double dx = x.get(i) - x.get(i - 1);
            double dy = y.get(i) - y.get(i - 1);
            if (dx > 0) {
                growthRates.add(dy / dx);
            }
        }

        if (growthRates.size() < 3) return false;

        // 如果增长率持续增加，可能是幂次增长
        int increasing = 0;
        for (int i = 1; i < growthRates.size(); i++) {
            if (growthRates.get(i) > growthRates.get(i - 1) * 1.1) {
                increasing++;
            }
        }

        return increasing >= growthRates.size() * 0.6;
    }

    /**
     * 分析数值分布特征
     */
    public static DistributionProfile analyzeDistribution(String fieldName, List<Double> values) {
        if (values.isEmpty()) {
            return new DistributionProfile(fieldName, DistributionType.DISCRETE, 0, 0,
                "数据为空", Collections.emptyList());
        }

        // 排序并计算统计量
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double median = sorted.get(sorted.size() / 2);

        // 计算偏度
        double skewness = calculateSkewness(sorted, mean);

        // 计算均匀度
        double evenness = calculateEvenness(sorted);

        // 检测数值间隙
        List<GapInfo> gaps = detectGaps(sorted);

        // 判断分布类型
        DistributionType type;
        String insight;

        if (Math.abs(skewness) < 0.5 && evenness > 0.7) {
            type = DistributionType.UNIFORM;
            insight = "数值分布均匀，各区间样本数量相近，设计较为平衡";
        } else if (Math.abs(skewness) < 0.5) {
            type = DistributionType.NORMAL;
            insight = "接近正态分布，大部分数值集中在中间，符合常规设计";
        } else if (skewness > 1.0) {
            type = DistributionType.SKEWED_RIGHT;
            insight = "数值右偏，大量低值和少量高值，注意高端数值可能过强";
        } else if (skewness < -1.0) {
            type = DistributionType.SKEWED_LEFT;
            insight = "数值左偏，大量高值和少量低值，低等级内容可能缺失";
        } else if (detectPowerLawDistribution(sorted)) {
            type = DistributionType.POWER_LAW;
            insight = "符合幂律分布，极少数高值占主导，可能存在严重不平衡";
        } else {
            type = DistributionType.DISCRETE;
            insight = "离散分布，数值分段明显";
        }

        return new DistributionProfile(fieldName, type, skewness, evenness, insight, gaps);
    }

    /**
     * 计算偏度
     */
    private static double calculateSkewness(List<Double> sorted, double mean) {
        double m3 = 0;
        double m2 = 0;
        for (double v : sorted) {
            double diff = v - mean;
            m3 += Math.pow(diff, 3);
            m2 += Math.pow(diff, 2);
        }
        m3 /= sorted.size();
        m2 /= sorted.size();

        if (m2 == 0) return 0;
        return m3 / Math.pow(m2, 1.5);
    }

    /**
     * 计算均匀度
     */
    private static double calculateEvenness(List<Double> sorted) {
        if (sorted.size() < 2) return 1.0;

        // 将数据分成10个区间，计算分布均匀度
        int buckets = Math.min(10, sorted.size());
        int[] counts = new int[buckets];

        double min = sorted.get(0);
        double max = sorted.get(sorted.size() - 1);
        double range = max - min;

        if (range == 0) return 1.0;

        for (double v : sorted) {
            int bucket = Math.min(buckets - 1, (int) ((v - min) / range * buckets));
            counts[bucket]++;
        }

        // 计算基尼系数的变体
        double expectedCount = sorted.size() / (double) buckets;
        double variance = 0;
        for (int count : counts) {
            variance += Math.pow(count - expectedCount, 2);
        }
        variance /= buckets;

        double maxVariance = Math.pow(expectedCount, 2) * (buckets - 1) / buckets;
        return maxVariance == 0 ? 1.0 : 1.0 - (variance / maxVariance);
    }

    /**
     * 检测数值间隙
     */
    private static List<GapInfo> detectGaps(List<Double> sorted) {
        List<GapInfo> gaps = new ArrayList<>();
        if (sorted.size() < 2) return gaps;

        // 计算平均间距
        List<Double> diffs = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            diffs.add(sorted.get(i) - sorted.get(i - 1));
        }

        double avgDiff = diffs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double threshold = avgDiff * 3; // 超过平均间距3倍视为间隙

        for (int i = 1; i < sorted.size(); i++) {
            double diff = sorted.get(i) - sorted.get(i - 1);
            if (diff > threshold && diff > 1) {
                gaps.add(new GapInfo(sorted.get(i - 1), sorted.get(i),
                    String.format("%.1f ~ %.1f 之间缺少数值", sorted.get(i - 1), sorted.get(i))));
            }
        }

        return gaps;
    }

    /**
     * 检测是否符合幂律分布
     */
    private static boolean detectPowerLawDistribution(List<Double> sorted) {
        if (sorted.size() < 10) return false;

        // 检查是否少数高值占据大部分
        int topCount = sorted.size() / 10; // 前10%
        double topSum = sorted.subList(sorted.size() - topCount, sorted.size())
                .stream().mapToDouble(Double::doubleValue).sum();
        double totalSum = sorted.stream().mapToDouble(Double::doubleValue).sum();

        // 如果前10%的和占总和的50%以上，认为是幂律分布
        return topSum / totalSum > 0.5;
    }

    /**
     * 检测平衡性问题
     */
    public static List<BalanceIssue> detectBalanceIssues(
            Map<String, List<Map<String, String>>> records,
            String idField) {

        List<BalanceIssue> issues = new ArrayList<>();

        // 检测异常值
        for (Map.Entry<String, List<Map<String, String>>> entry : records.entrySet()) {
            String fieldName = entry.getKey();
            List<Double> numericValues = extractNumericValues(entry.getValue(), fieldName);

            if (numericValues.size() < 5) continue;

            List<Double> sorted = new ArrayList<>(numericValues);
            Collections.sort(sorted);

            // 检测极端异常值
            double q1 = sorted.get(sorted.size() / 4);
            double q3 = sorted.get(sorted.size() * 3 / 4);
            double iqr = q3 - q1;
            double lowerBound = q1 - 3 * iqr;
            double upperBound = q3 + 3 * iqr;

            List<String> outliers = new ArrayList<>();
            for (int i = 0; i < entry.getValue().size(); i++) {
                Map<String, String> record = entry.getValue().get(i);
                try {
                    double value = Double.parseDouble(record.getOrDefault(fieldName, "0"));
                    if (value < lowerBound || value > upperBound) {
                        String id = record.getOrDefault(idField, "记录" + i);
                        outliers.add(String.format("%s (值: %.2f)", id, value));
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            if (!outliers.isEmpty() && outliers.size() <= 5) {
                issues.add(new BalanceIssue(
                    "极端异常值",
                    Severity.WARNING,
                    String.format("%s 存在 %d 个极端异常值，可能是配置错误或刻意设计",
                        fieldName, outliers.size()),
                    "检查这些数值是否合理，或考虑调整到正常范围",
                    outliers.subList(0, Math.min(3, outliers.size()))
                ));
            }
        }

        return issues;
    }

    /**
     * 提取数值列表
     */
    private static List<Double> extractNumericValues(List<Map<String, String>> records, String fieldName) {
        return records.stream()
            .map(record -> record.get(fieldName))
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> {
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}