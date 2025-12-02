package red.jiuzhou.analysis.enhanced;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 枚举值统计分析引擎
 *
 * 专门针对游戏XML数据中的枚举字段进行深度统计分析
 *
 * 核心理念：
 * 1. 识别枚举字段 - 自动检测具有限定值集合的字段
 * 2. 分布分析 - 统计每个枚举值的出现频率和分布规律
 * 3. 游戏洞察 - 从枚举分布中发现游戏设计问题和优化机会
 * 4. 平衡建议 - 基于分布不均衡提供具体的调整建议
 */
public class EnumerationAnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(EnumerationAnalysisEngine.class);

    // 常见游戏枚举类型模式
    private static final Map<String, EnumCategory> GAME_ENUM_PATTERNS = createGameEnumPatterns();

    // 数值型字段的阈值判断
    private static final int MAX_ENUM_UNIQUE_COUNT = 20; // 超过20个唯一值就不太像枚举
    private static final double MIN_REPETITION_RATIO = 0.1; // 至少10%的重复率才算枚举
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    /**
     * 枚举分类
     */
    public enum EnumCategory {
        QUALITY("品质类", "装备品质、道具稀有度等", "影响掉落率和玩家获取体验"),
        TYPE("类型类", "装备类型、技能类型等", "影响游戏系统的丰富度和平衡性"),
        LEVEL("等级类", "等级、层级、阶段", "影响游戏的成长体验和难度曲线"),
        CLASS("职业类", "角色职业、NPC类别", "影响游戏的角色多样性和平衡"),
        ELEMENT("属性类", "元素属性、伤害类型", "影响战斗系统的策略深度"),
        STATUS("状态类", "激活状态、可用性", "影响功能的可用性和用户体验"),
        CATEGORY("分类类", "功能分类、内容分组", "影响界面组织和查找效率"),
        FACTION("阵营类", "敌我关系、归属", "影响剧情和PVP平衡"),
        UNKNOWN("未知类", "无法明确分类", "需要人工确认");

        private final String displayName;
        private final String description;
        private final String gameImpact;

        EnumCategory(String displayName, String description, String gameImpact) {
            this.displayName = displayName;
            this.description = description;
            this.gameImpact = gameImpact;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getGameImpact() { return gameImpact; }
    }

    /**
     * 枚举统计结果
     */
    public static class EnumStatistics {
        private final String fieldName;
        private final EnumCategory category;
        private final Map<String, Integer> distribution; // 值 -> 出现次数
        private final int totalCount;
        private final double entropy; // 信息熵，衡量分布均匀程度
        private final List<DistributionIssue> issues; // 发现的问题
        private final List<String> suggestions; // 优化建议
        private final LocalDateTime analysisTime;

        public EnumStatistics(String fieldName, EnumCategory category, Map<String, Integer> distribution,
                            int totalCount, double entropy, List<DistributionIssue> issues,
                            List<String> suggestions) {
            this.fieldName = fieldName;
            this.category = category;
            this.distribution = distribution;
            this.totalCount = totalCount;
            this.entropy = entropy;
            this.issues = issues;
            this.suggestions = suggestions;
            this.analysisTime = LocalDateTime.now();
        }

        // Getters
        public String getFieldName() { return fieldName; }
        public EnumCategory getCategory() { return category; }
        public Map<String, Integer> getDistribution() { return distribution; }
        public int getTotalCount() { return totalCount; }
        public double getEntropy() { return entropy; }
        public List<DistributionIssue> getIssues() { return issues; }
        public List<String> getSuggestions() { return suggestions; }
        public LocalDateTime getAnalysisTime() { return analysisTime; }

        /**
         * 获取最常见的值
         */
        public String getMostCommonValue() {
            return distribution.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
        }

        /**
         * 获取最少见的值
         */
        public String getRarestValue() {
            return distribution.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
        }

        /**
         * 计算基尼系数（分布不均衡程度）
         */
        public double getGiniCoefficient() {
            List<Integer> counts = new ArrayList<>(distribution.values());
            Collections.sort(counts);

            int n = counts.size();
            double sum = counts.stream().mapToDouble(Integer::doubleValue).sum();

            if (sum == 0) return 0;

            double numerator = 0;
            for (int i = 0; i < n; i++) {
                numerator += (2 * (i + 1) - n - 1) * counts.get(i);
            }

            return numerator / (n * sum);
        }
    }

    /**
     * 分布问题类型
     */
    public static class DistributionIssue {
        public enum IssueType {
            EXTREME_IMBALANCE("极度不均衡", "某些值占比过高，可能导致游戏体验单一"),
            UNUSED_VALUES("存在零使用值", "某些配置的值从未被使用，可能是冗余配置"),
            SINGLETON_DOMINANCE("单值主导", "一个值占据绝大多数，失去了枚举的意义"),
            POLARIZATION("两极分化", "只有少数几个值被大量使用，其他值很少"),
            OVER_FRAGMENTATION("过度分散", "每个值都用得很少，可能设计过于复杂");

            private final String displayName;
            private final String description;

            IssueType(String displayName, String description) {
                this.displayName = displayName;
                this.description = description;
            }

            public String getDisplayName() { return displayName; }
            public String getDescription() { return description; }
        }

        private final IssueType type;
        private final String detail;
        private final double severity; // 0-1，严重程度

        public DistributionIssue(IssueType type, String detail, double severity) {
            this.type = type;
            this.detail = detail;
            this.severity = severity;
        }

        // Getters
        public IssueType getType() { return type; }
        public String getDetail() { return detail; }
        public double getSeverity() { return severity; }
    }

    /**
     * 分析XML文件中的枚举字段
     */
    public Map<String, EnumStatistics> analyzeEnumerations(Path xmlFile) {
        log.info("开始分析XML文件的枚举字段: {}", xmlFile.getFileName());

        try {
            // 解析XML文档
            Document document = parseXmlDocument(xmlFile);
            if (document == null) {
                log.warn("无法解析XML文档: {}", xmlFile);
                return Collections.emptyMap();
            }

            // 提取所有数据记录
            List<Element> records = extractRecords(document);
            if (records.isEmpty()) {
                log.info("XML文件中没有找到数据记录");
                return Collections.emptyMap();
            }

            // 识别枚举字段
            Map<String, List<String>> candidateFields = identifyEnumFields(records);
            log.info("识别到 {} 个潜在的枚举字段", candidateFields.size());

            // 分析每个枚举字段
            Map<String, EnumStatistics> results = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : candidateFields.entrySet()) {
                String fieldName = entry.getKey();
                List<String> values = entry.getValue();

                EnumStatistics stats = analyzeEnumField(fieldName, values);
                if (stats != null) {
                    results.put(fieldName, stats);
                }
            }

            log.info("枚举分析完成，共分析 {} 个字段", results.size());
            return results;

        } catch (Exception e) {
            log.error("枚举分析过程中出现异常", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 解析XML文档
     */
    private Document parseXmlDocument(Path xmlFile) {
        try {
            byte[] bytes = Files.readAllBytes(xmlFile);
            String content = new String(bytes, "UTF-8");
            return DocumentHelper.parseText(content);
        } catch (Exception e) {
            log.warn("解析XML文档失败: {}", xmlFile, e);
            return null;
        }
    }

    /**
     * 提取数据记录
     */
    @SuppressWarnings("unchecked")
    private List<Element> extractRecords(Document document) {
        Element root = document.getRootElement();
        List<Element> children = (List<Element>) root.elements();

        if (children.isEmpty()) {
            return Collections.emptyList();
        }

        // 寻找重复的元素名称作为数据记录
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

        return children;
    }

    /**
     * 识别枚举字段
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> identifyEnumFields(List<Element> records) {
        Map<String, List<String>> fieldValues = new HashMap<>();

        // 收集所有字段的值
        for (Element record : records) {
            // 处理属性
            record.attributes().forEach(attr -> {
                String fieldName = attr.getName();
                String value = attr.getValue();
                if (StringUtils.hasLength(value)) {
                    fieldValues.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(value.trim());
                }
            });

            // 处理子元素
            List<Element> children = (List<Element>) record.elements();
            for (Element child : children) {
                String fieldName = child.getName();
                String value = child.getTextTrim();
                if (StringUtils.hasLength(value)) {
                    fieldValues.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(value);
                }
            }
        }

        // 筛选出候选枚举字段
        Map<String, List<String>> enumFields = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : fieldValues.entrySet()) {
            String fieldName = entry.getKey();
            List<String> values = entry.getValue();

            if (isEnumField(fieldName, values)) {
                enumFields.put(fieldName, values);
            }
        }

        return enumFields;
    }

    /**
     * 判断是否为枚举字段
     */
    private boolean isEnumField(String fieldName, List<String> values) {
        if (values.size() < 3) {
            return false; // 数据太少
        }

        Set<String> uniqueValues = new HashSet<>(values);
        int uniqueCount = uniqueValues.size();

        // 唯一值过多不太像枚举
        if (uniqueCount > MAX_ENUM_UNIQUE_COUNT) {
            return false;
        }

        // 计算重复率
        double repetitionRatio = 1.0 - (double) uniqueCount / values.size();
        if (repetitionRatio < MIN_REPETITION_RATIO) {
            return false;
        }

        // 排除纯数值ID字段（除非是有限的等级、类型编号）
        if (isSequentialNumeric(uniqueValues)) {
            return false;
        }

        return true;
    }

    /**
     * 检查是否为连续数字序列（如ID）
     */
    private boolean isSequentialNumeric(Set<String> values) {
        List<Integer> numbers = new ArrayList<>();

        for (String value : values) {
            if (NUMERIC_PATTERN.matcher(value).matches()) {
                try {
                    numbers.add(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    return false; // 有非整数
                }
            } else {
                return false; // 有非数字
            }
        }

        if (numbers.size() < 3) return false;

        Collections.sort(numbers);

        // 检查是否为连续序列
        for (int i = 1; i < numbers.size(); i++) {
            if (numbers.get(i) - numbers.get(i-1) != 1) {
                return false; // 不连续
            }
        }

        return true; // 是连续数字序列，可能是ID
    }

    /**
     * 分析单个枚举字段
     */
    private EnumStatistics analyzeEnumField(String fieldName, List<String> values) {
        // 统计分布
        Map<String, Integer> distribution = values.stream()
                .collect(Collectors.groupingBy(
                        v -> v,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));

        // 按出现次数排序
        Map<String, Integer> sortedDistribution = distribution.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        // 识别枚举类型
        EnumCategory category = categorizeEnum(fieldName, sortedDistribution.keySet());

        // 计算信息熵
        double entropy = calculateEntropy(sortedDistribution, values.size());

        // 分析分布问题
        List<DistributionIssue> issues = analyzeDistributionIssues(sortedDistribution, values.size());

        // 生成优化建议
        List<String> suggestions = generateSuggestions(category, sortedDistribution, issues);

        return new EnumStatistics(fieldName, category, sortedDistribution, values.size(),
                                entropy, issues, suggestions);
    }

    /**
     * 枚举分类
     */
    private EnumCategory categorizeEnum(String fieldName, Set<String> values) {
        String lowerFieldName = fieldName.toLowerCase();

        // 基于字段名模式匹配
        for (Map.Entry<String, EnumCategory> entry : GAME_ENUM_PATTERNS.entrySet()) {
            if (lowerFieldName.matches(".*(" + entry.getKey() + ").*")) {
                return entry.getValue();
            }
        }

        // 基于值内容特征判断
        List<String> valueList = new ArrayList<>(values);

        // 品质类特征
        if (hasQualityPatterns(valueList)) {
            return EnumCategory.QUALITY;
        }

        // 状态类特征
        if (hasStatusPatterns(valueList)) {
            return EnumCategory.STATUS;
        }

        // 数值等级类特征
        if (hasLevelPatterns(valueList)) {
            return EnumCategory.LEVEL;
        }

        return EnumCategory.UNKNOWN;
    }

    private boolean hasQualityPatterns(List<String> values) {
        String[] qualityKeywords = {"普通", "优秀", "稀有", "史诗", "传说", "神器",
                                  "白", "绿", "蓝", "紫", "橙", "红",
                                  "common", "uncommon", "rare", "epic", "legendary"};

        long matches = values.stream()
                .mapToLong(value -> Arrays.stream(qualityKeywords)
                        .anyMatch(keyword -> value.toLowerCase().contains(keyword)) ? 1 : 0)
                .sum();

        return matches >= values.size() * 0.6; // 60%以上匹配
    }

    private boolean hasStatusPatterns(List<String> values) {
        String[] statusKeywords = {"启用", "禁用", "激活", "未激活", "可用", "不可用",
                                 "开启", "关闭", "正常", "异常", "true", "false",
                                 "enabled", "disabled", "active", "inactive"};

        long matches = values.stream()
                .mapToLong(value -> Arrays.stream(statusKeywords)
                        .anyMatch(keyword -> value.toLowerCase().contains(keyword)) ? 1 : 0)
                .sum();

        return matches >= values.size() * 0.6;
    }

    private boolean hasLevelPatterns(List<String> values) {
        // 检查是否都是数字且范围合理（1-100）
        return values.stream().allMatch(value -> {
            if (NUMERIC_PATTERN.matcher(value).matches()) {
                try {
                    int num = Integer.parseInt(value);
                    return num >= 1 && num <= 100;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        });
    }

    /**
     * 计算信息熵
     */
    private double calculateEntropy(Map<String, Integer> distribution, int totalCount) {
        double entropy = 0.0;

        for (int count : distribution.values()) {
            if (count > 0) {
                double probability = (double) count / totalCount;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }

        return entropy;
    }

    /**
     * 分析分布问题
     */
    private List<DistributionIssue> analyzeDistributionIssues(Map<String, Integer> distribution, int totalCount) {
        List<DistributionIssue> issues = new ArrayList<>();

        List<Integer> counts = new ArrayList<>(distribution.values());
        Collections.sort(counts, Collections.reverseOrder());

        if (counts.isEmpty()) return issues;

        // 检查单值主导
        double topRatio = (double) counts.get(0) / totalCount;
        if (topRatio > 0.8) {
            issues.add(new DistributionIssue(
                    DistributionIssue.IssueType.SINGLETON_DOMINANCE,
                    String.format("最常见值占比 %.1f%%，枚举意义不大", topRatio * 100),
                    topRatio
            ));
        }

        // 检查零使用值
        long unusedCount = distribution.values().stream().mapToLong(count -> count == 0 ? 1 : 0).sum();
        if (unusedCount > 0) {
            issues.add(new DistributionIssue(
                    DistributionIssue.IssueType.UNUSED_VALUES,
                    String.format("有 %d 个枚举值从未被使用", unusedCount),
                    (double) unusedCount / distribution.size()
            ));
        }

        // 检查极度不均衡（基尼系数）
        double gini = calculateGini(counts);
        if (gini > 0.7) {
            issues.add(new DistributionIssue(
                    DistributionIssue.IssueType.EXTREME_IMBALANCE,
                    String.format("分布极度不均衡（基尼系数 %.2f）", gini),
                    gini
            ));
        }

        // 检查两极分化
        if (counts.size() >= 3) {
            double top2Ratio = (double) (counts.get(0) + counts.get(1)) / totalCount;
            if (top2Ratio > 0.8 && counts.size() > 3) {
                issues.add(new DistributionIssue(
                        DistributionIssue.IssueType.POLARIZATION,
                        String.format("前两个值占比 %.1f%%，其他值使用很少", top2Ratio * 100),
                        top2Ratio
                ));
            }
        }

        // 检查过度分散
        double avgRatio = 1.0 / distribution.size();
        long lowUsageCounts = counts.stream().mapToLong(count -> {
            double ratio = (double) count / totalCount;
            return ratio < avgRatio * 0.5 ? 1 : 0;
        }).sum();

        if (lowUsageCounts > distribution.size() * 0.6) {
            issues.add(new DistributionIssue(
                    DistributionIssue.IssueType.OVER_FRAGMENTATION,
                    String.format("%.0f%% 的值使用率很低，可能设计过于复杂",
                                 (double) lowUsageCounts / distribution.size() * 100),
                    (double) lowUsageCounts / distribution.size()
            ));
        }

        return issues;
    }

    private double calculateGini(List<Integer> counts) {
        Collections.sort(counts);
        int n = counts.size();
        double sum = counts.stream().mapToDouble(Integer::doubleValue).sum();

        if (sum == 0) return 0;

        double numerator = 0;
        for (int i = 0; i < n; i++) {
            numerator += (2 * (i + 1) - n - 1) * counts.get(i);
        }

        return numerator / (n * sum);
    }

    /**
     * 生成优化建议
     */
    private List<String> generateSuggestions(EnumCategory category, Map<String, Integer> distribution,
                                           List<DistributionIssue> issues) {
        List<String> suggestions = new ArrayList<>();

        // 基于问题类型生成建议
        for (DistributionIssue issue : issues) {
            switch (issue.getType()) {
                case SINGLETON_DOMINANCE:
                    suggestions.add("考虑增加其他枚举值的使用场景，或者简化为布尔字段");
                    break;
                case UNUSED_VALUES:
                    suggestions.add("删除未使用的枚举值，或为其设计使用场景");
                    break;
                case EXTREME_IMBALANCE:
                    suggestions.add("调整数据生成逻辑，让各枚举值分布更均衡");
                    break;
                case POLARIZATION:
                    suggestions.add("为低频枚举值增加使用场景，或合并相似的值");
                    break;
                case OVER_FRAGMENTATION:
                    suggestions.add("考虑合并功能相近的枚举值，简化设计");
                    break;
            }
        }

        // 基于枚举类型生成特定建议
        switch (category) {
            case QUALITY:
                suggestions.add("品质分布应考虑游戏经济平衡，稀有品质不宜过多");
                break;
            case TYPE:
                suggestions.add("类型多样性有助于游戏深度，但过多类型会增加复杂度");
                break;
            case LEVEL:
                suggestions.add("等级分布应形成平滑的成长曲线");
                break;
            case STATUS:
                suggestions.add("状态字段应有明确的切换逻辑和默认值");
                break;
        }

        return suggestions.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Java 8 compatible method to create game enum patterns map
     */
    private static Map<String, EnumCategory> createGameEnumPatterns() {
        Map<String, EnumCategory> patterns = new HashMap<>();
        patterns.put("quality|品质|rarity|稀有", EnumCategory.QUALITY);
        patterns.put("type|类型|kind|种类", EnumCategory.TYPE);
        patterns.put("level|等级|tier|层级", EnumCategory.LEVEL);
        patterns.put("class|职业|profession|career", EnumCategory.CLASS);
        patterns.put("element|属性|property", EnumCategory.ELEMENT);
        patterns.put("status|状态|state", EnumCategory.STATUS);
        patterns.put("category|分类|group", EnumCategory.CATEGORY);
        patterns.put("faction|阵营|camp", EnumCategory.FACTION);
        return patterns;
    }
}