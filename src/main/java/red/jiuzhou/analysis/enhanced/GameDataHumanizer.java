package red.jiuzhou.analysis.enhanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.analysis.enhanced.EnhancedInsightService.EnhancedInsightResult;
import red.jiuzhou.analysis.enhanced.GameSystemDetector.GameSystemType;
import red.jiuzhou.analysis.enhanced.SmartInsightEngine.FieldAnalysis;
import red.jiuzhou.analysis.enhanced.SmartInsightEngine.DataQualityMetrics;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏数据人性化解读器
 *
 * 将冷冰冰的统计数据转换为游戏策划能够理解的人性化洞察
 *
 * 核心功能：
 * 1. 平衡性分析 - 发现数值不平衡问题，用策划语言描述影响
 * 2. 成长曲线分析 - 识别成长体验问题，提供调优建议
 * 3. 经济健康度 - 评估游戏经济系统的合理性
 * 4. 用户体验预测 - 基于数据预测玩家可能遇到的问题
 */
public class GameDataHumanizer {

    private static final Logger log = LoggerFactory.getLogger(GameDataHumanizer.class);

    /**
     * 平衡性问题类型
     */
    public enum BalanceIssueType {
        POWER_CREEP("数值膨胀", "高等级装备过于强大，低等级装备失去价值", "调整数值曲线，让低级装备保持一定用途"),
        FLAT_PROGRESSION("成长停滞", "数值增长过于平缓，升级缺乏成就感", "增加关键节点的数值跳跃"),
        EXTREME_GAPS("断层严重", "等级间差距过大，中间级别被跳过", "填补数值空隙，提供平滑过渡"),
        OUTLIER_DOMINANCE("单点过强", "个别数值异常突出，破坏整体平衡", "调整异常值，或为其增加获取难度"),
        HOMOGENIZATION("同质化严重", "所有选项数值相近，缺乏多样性", "增加数值差异化，创造不同的使用场景");

        private final String displayName;
        private final String description;
        private final String suggestion;

        BalanceIssueType(String displayName, String description, String suggestion) {
            this.displayName = displayName;
            this.description = description;
            this.suggestion = suggestion;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getSuggestion() { return suggestion; }
    }

    /**
     * 平衡性问题
     */
    public static class BalanceIssue {
        private final BalanceIssueType type;
        private final String fieldName;
        private final String detail;
        private final double severity; // 0-1
        private final List<String> affectedValues;
        private final String gameplayImpact;

        public BalanceIssue(BalanceIssueType type, String fieldName, String detail,
                          double severity, List<String> affectedValues, String gameplayImpact) {
            this.type = type;
            this.fieldName = fieldName;
            this.detail = detail;
            this.severity = severity;
            this.affectedValues = affectedValues;
            this.gameplayImpact = gameplayImpact;
        }

        // Getters
        public BalanceIssueType getType() { return type; }
        public String getFieldName() { return fieldName; }
        public String getDetail() { return detail; }
        public double getSeverity() { return severity; }
        public List<String> getAffectedValues() { return affectedValues; }
        public String getGameplayImpact() { return gameplayImpact; }
    }

    /**
     * 成长曲线分析结果
     */
    public static class GrowthCurveAnalysis {
        private final String fieldName;
        private final CurveType curveType;
        private final double smoothness; // 平滑度
        private final List<GrowthPhase> phases;
        private final String playerExperience;
        private final List<String> optimizationSuggestions;

        public GrowthCurveAnalysis(String fieldName, CurveType curveType, double smoothness,
                                 List<GrowthPhase> phases, String playerExperience,
                                 List<String> optimizationSuggestions) {
            this.fieldName = fieldName;
            this.curveType = curveType;
            this.smoothness = smoothness;
            this.phases = phases;
            this.playerExperience = playerExperience;
            this.optimizationSuggestions = optimizationSuggestions;
        }

        // Getters
        public String getFieldName() { return fieldName; }
        public CurveType getCurveType() { return curveType; }
        public double getSmoothness() { return smoothness; }
        public List<GrowthPhase> getPhases() { return phases; }
        public String getPlayerExperience() { return playerExperience; }
        public List<String> getOptimizationSuggestions() { return optimizationSuggestions; }

        public enum CurveType {
            LINEAR("线性增长", "稳定但可能单调"),
            EXPONENTIAL("指数增长", "后期爆发，可能失控"),
            LOGARITHMIC("对数增长", "前期快速，后期缓慢"),
            STEPPED("阶梯增长", "分段明显，节奏感强"),
            CHAOTIC("无规律", "缺乏设计逻辑");

            private final String displayName;
            private final String characteristic;

            CurveType(String displayName, String characteristic) {
                this.displayName = displayName;
                this.characteristic = characteristic;
            }

            public String getDisplayName() { return displayName; }
            public String getCharacteristic() { return characteristic; }
        }

        public static class GrowthPhase {
            private final String name;
            private final double startValue;
            private final double endValue;
            private final double growthRate;
            private final String description;

            public GrowthPhase(String name, double startValue, double endValue,
                             double growthRate, String description) {
                this.name = name;
                this.startValue = startValue;
                this.endValue = endValue;
                this.growthRate = growthRate;
                this.description = description;
            }

            // Getters
            public String getName() { return name; }
            public double getStartValue() { return startValue; }
            public double getEndValue() { return endValue; }
            public double getGrowthRate() { return growthRate; }
            public String getDescription() { return description; }
        }
    }

    /**
     * 经济健康度评估
     */
    public static class EconomyHealth {
        private final double inflationRisk; // 通胀风险
        private final double deflationRisk; // 通缩风险
        private final double priceDispersion; // 价格离散度
        private final Map<String, Double> categoryBalance; // 各类别价格平衡
        private final String overallAssessment;
        private final List<String> risks;
        private final List<String> recommendations;

        public EconomyHealth(double inflationRisk, double deflationRisk, double priceDispersion,
                           Map<String, Double> categoryBalance, String overallAssessment,
                           List<String> risks, List<String> recommendations) {
            this.inflationRisk = inflationRisk;
            this.deflationRisk = deflationRisk;
            this.priceDispersion = priceDispersion;
            this.categoryBalance = categoryBalance;
            this.overallAssessment = overallAssessment;
            this.risks = risks;
            this.recommendations = recommendations;
        }

        // Getters
        public double getInflationRisk() { return inflationRisk; }
        public double getDeflationRisk() { return deflationRisk; }
        public double getPriceDispersion() { return priceDispersion; }
        public Map<String, Double> getCategoryBalance() { return categoryBalance; }
        public String getOverallAssessment() { return overallAssessment; }
        public List<String> getRisks() { return risks; }
        public List<String> getRecommendations() { return recommendations; }
    }

    /**
     * 分析游戏系统的平衡性问题
     */
    public List<BalanceIssue> analyzeBalance(EnhancedInsightResult result) {
        List<BalanceIssue> issues = new ArrayList<>();

        GameSystemType systemType = result.getSystemDetection().getPrimaryType();
        Map<String, FieldAnalysis> fieldAnalyses = result.getFieldAnalyses();

        log.info("开始分析 {} 系统的平衡性", systemType.getDisplayName());

        // 根据系统类型采用不同的分析策略
        switch (systemType) {
            case EQUIPMENT:
                issues.addAll(analyzeEquipmentBalance(fieldAnalyses));
                break;
            case CHARACTER:
                issues.addAll(analyzeCharacterBalance(fieldAnalyses));
                break;
            case ECONOMY:
                issues.addAll(analyzeEconomyBalance(fieldAnalyses));
                break;
            default:
                issues.addAll(analyzeGenericBalance(fieldAnalyses));
        }

        log.info("平衡性分析完成，发现 {} 个问题", issues.size());
        return issues;
    }

    /**
     * 分析装备系统平衡性
     */
    private List<BalanceIssue> analyzeEquipmentBalance(Map<String, FieldAnalysis> fieldAnalyses) {
        List<BalanceIssue> issues = new ArrayList<>();

        // 查找攻击力字段
        FieldAnalysis attackField = findField(fieldAnalyses, "attack", "atk", "攻击", "damage");
        if (attackField != null && "数值".equals(attackField.getDataType())) {
            Map<String, Object> stats = attackField.getStatistics();
            if (stats.containsKey("max") && stats.containsKey("min")) {
                double max = (Double) stats.get("max");
                double min = (Double) stats.get("min");
                double ratio = max / Math.max(min, 1);

                if (ratio > 20) {
                    issues.add(new BalanceIssue(
                        BalanceIssueType.POWER_CREEP,
                        attackField.getFieldName(),
                        String.format("最高攻击力是最低的 %.1f 倍", ratio),
                        Math.min(ratio / 20.0, 1.0),
                        Arrays.asList(String.format("最低: %.0f", min), String.format("最高: %.0f", max)),
                        "新手装备很快失去价值，玩家升级动力过强，可能导致内容跳跃"
                    ));
                } else if (ratio < 2) {
                    issues.add(new BalanceIssue(
                        BalanceIssueType.FLAT_PROGRESSION,
                        attackField.getFieldName(),
                        String.format("攻击力变化幅度很小（%.1f倍）", ratio),
                        1.0 - ratio / 2.0,
                        Arrays.asList("变化幅度过小"),
                        "升级缺乏成就感，玩家可能感觉不到明显的进步"
                    ));
                }
            }

            // 检查异常值
            if (attackField.hasOutliers()) {
                issues.add(new BalanceIssue(
                    BalanceIssueType.OUTLIER_DOMINANCE,
                    attackField.getFieldName(),
                    "发现数值异常突出的装备",
                    0.8,
                    Arrays.asList("存在异常值"),
                    "部分装备过于强大，可能成为唯一选择，降低装备多样性"
                ));
            }
        }

        return issues;
    }

    /**
     * 分析角色系统平衡性
     */
    private List<BalanceIssue> analyzeCharacterBalance(Map<String, FieldAnalysis> fieldAnalyses) {
        List<BalanceIssue> issues = new ArrayList<>();

        FieldAnalysis hpField = findField(fieldAnalyses, "hp", "health", "生命", "血量");
        FieldAnalysis atkField = findField(fieldAnalyses, "attack", "atk", "攻击");

        if (hpField != null && atkField != null) {
            Map<String, Object> hpStats = hpField.getStatistics();
            Map<String, Object> atkStats = atkField.getStatistics();

            if (hpStats.containsKey("average") && atkStats.containsKey("average")) {
                double avgHp = (Double) hpStats.get("average");
                double avgAtk = (Double) atkStats.get("average");
                double ratio = avgHp / Math.max(avgAtk, 1);

                if (ratio < 3) {
                    issues.add(new BalanceIssue(
                        BalanceIssueType.EXTREME_GAPS,
                        "生命值/攻击力比例",
                        String.format("生命值相对攻击力过低（%.1f倍）", ratio),
                        (3.0 - ratio) / 3.0,
                        Arrays.asList(String.format("平均生命: %.0f", avgHp), String.format("平均攻击: %.0f", avgAtk)),
                        "战斗时间过短，策略性不足，玩家容易频繁死亡影响体验"
                    ));
                }
            }
        }

        return issues;
    }

    /**
     * 分析经济系统平衡性
     */
    private List<BalanceIssue> analyzeEconomyBalance(Map<String, FieldAnalysis> fieldAnalyses) {
        List<BalanceIssue> issues = new ArrayList<>();

        FieldAnalysis priceField = findField(fieldAnalyses, "price", "cost", "价格", "金币");
        if (priceField != null && priceField.hasOutliers()) {
            issues.add(new BalanceIssue(
                BalanceIssueType.OUTLIER_DOMINANCE,
                priceField.getFieldName(),
                "发现价格异常的物品",
                0.9,
                Arrays.asList("存在价格异常值"),
                "异常价格可能破坏游戏经济平衡，影响玩家的购买决策"
            ));
        }

        return issues;
    }

    /**
     * 通用平衡性分析
     */
    private List<BalanceIssue> analyzeGenericBalance(Map<String, FieldAnalysis> fieldAnalyses) {
        List<BalanceIssue> issues = new ArrayList<>();

        // 检查所有数值字段的异常值情况
        long outlierFields = fieldAnalyses.values().stream()
                .filter(field -> "数值".equals(field.getDataType()))
                .mapToLong(field -> field.hasOutliers() ? 1 : 0)
                .sum();

        long numericFields = fieldAnalyses.values().stream()
                .mapToLong(field -> "数值".equals(field.getDataType()) ? 1 : 0)
                .sum();

        if (numericFields > 0 && outlierFields > numericFields * 0.3) {
            issues.add(new BalanceIssue(
                BalanceIssueType.OUTLIER_DOMINANCE,
                "多个数值字段",
                String.format("有 %d 个字段存在异常值", outlierFields),
                (double) outlierFields / numericFields,
                Arrays.asList("大量异常值"),
                "数据质量可能存在系统性问题，建议全面检查数值配置"
            ));
        }

        return issues;
    }

    /**
     * 分析成长曲线
     */
    public List<GrowthCurveAnalysis> analyzeGrowthCurves(EnhancedInsightResult result) {
        List<GrowthCurveAnalysis> analyses = new ArrayList<>();

        Map<String, FieldAnalysis> fieldAnalyses = result.getFieldAnalyses();

        // 查找可能的成长相关字段
        List<String> growthFields = fieldAnalyses.keySet().stream()
                .filter(name -> isGrowthRelatedField(name))
                .collect(Collectors.toList());

        log.info("发现 {} 个成长相关字段", growthFields.size());

        for (String fieldName : growthFields) {
            FieldAnalysis field = fieldAnalyses.get(fieldName);
            if ("数值".equals(field.getDataType())) {
                GrowthCurveAnalysis analysis = analyzeFieldGrowthCurve(field);
                if (analysis != null) {
                    analyses.add(analysis);
                }
            }
        }

        return analyses;
    }

    /**
     * 判断是否为成长相关字段
     */
    private boolean isGrowthRelatedField(String fieldName) {
        String lowerName = fieldName.toLowerCase();
        return lowerName.matches(".*(level|等级|exp|experience|经验|attack|攻击|hp|health|生命|defense|防御|power|威力).*");
    }

    /**
     * 分析单个字段的成长曲线
     */
    private GrowthCurveAnalysis analyzeFieldGrowthCurve(FieldAnalysis field) {
        Map<String, Object> stats = field.getStatistics();
        if (!stats.containsKey("min") || !stats.containsKey("max") || !stats.containsKey("average")) {
            return null;
        }

        double min = (Double) stats.get("min");
        double max = (Double) stats.get("max");
        double avg = (Double) stats.get("average");

        // 简化的曲线类型判断
        GrowthCurveAnalysis.CurveType curveType = determineCurveType(min, max, avg);

        // 计算平滑度（基于数值分布的连续性）
        double smoothness = calculateSmoothness(field);

        // 生成成长阶段
        List<GrowthCurveAnalysis.GrowthPhase> phases = generateGrowthPhases(min, max, curveType);

        // 评估玩家体验
        String playerExperience = evaluatePlayerExperience(curveType, smoothness, max / Math.max(min, 1));

        // 生成优化建议
        List<String> suggestions = generateGrowthSuggestions(curveType, smoothness, field);

        return new GrowthCurveAnalysis(field.getFieldName(), curveType, smoothness,
                                     phases, playerExperience, suggestions);
    }

    private GrowthCurveAnalysis.CurveType determineCurveType(double min, double max, double avg) {
        double ratio = max / Math.max(min, 1);

        // 基于平均值相对于最值的位置判断曲线类型
        double avgPosition = (avg - min) / Math.max(max - min, 1);

        if (ratio < 2) {
            return GrowthCurveAnalysis.CurveType.LINEAR;
        } else if (avgPosition < 0.3) {
            return GrowthCurveAnalysis.CurveType.EXPONENTIAL;
        } else if (avgPosition > 0.7) {
            return GrowthCurveAnalysis.CurveType.LOGARITHMIC;
        } else {
            return GrowthCurveAnalysis.CurveType.STEPPED;
        }
    }

    private double calculateSmoothness(FieldAnalysis field) {
        // 基于唯一值数量和覆盖率估算平滑度
        double uniqueRatio = (double) field.getUniqueCount() / Math.max(field.getCoverage() * 100, 1);
        return Math.min(uniqueRatio / 10.0, 1.0);
    }

    private List<GrowthCurveAnalysis.GrowthPhase> generateGrowthPhases(double min, double max,
                                                                    GrowthCurveAnalysis.CurveType curveType) {
        List<GrowthCurveAnalysis.GrowthPhase> phases = new ArrayList<>();

        switch (curveType) {
            case LINEAR:
                phases.add(new GrowthCurveAnalysis.GrowthPhase("稳定成长", min, max, (max - min) / max, "持续稳定的数值增长"));
                break;
            case EXPONENTIAL:
                phases.add(new GrowthCurveAnalysis.GrowthPhase("缓慢起步", min, min * 2, 0.1, "前期增长较慢"));
                phases.add(new GrowthCurveAnalysis.GrowthPhase("快速爆发", min * 2, max, 0.8, "后期快速增长"));
                break;
            case LOGARITHMIC:
                phases.add(new GrowthCurveAnalysis.GrowthPhase("快速起步", min, (min + max) / 2, 0.7, "前期快速增长"));
                phases.add(new GrowthCurveAnalysis.GrowthPhase("增长放缓", (min + max) / 2, max, 0.2, "后期增长放缓"));
                break;
            default:
                phases.add(new GrowthCurveAnalysis.GrowthPhase("不规律增长", min, max, 0.5, "增长模式不规律"));
        }

        return phases;
    }

    private String evaluatePlayerExperience(GrowthCurveAnalysis.CurveType curveType, double smoothness, double ratio) {
        switch (curveType) {
            case LINEAR:
                return smoothness > 0.7 ? "体验稳定，但可能单调" : "体验稳定，节奏适中";
            case EXPONENTIAL:
                return ratio > 10 ? "前期枯燥，后期刺激过度" : "前期平淡，后期有成就感";
            case LOGARITHMIC:
                return "前期体验良好，后期可能乏味";
            case STEPPED:
                return "节奏分明，有明确的成长里程碑";
            default:
                return "体验不一致，可能让玩家困惑";
        }
    }

    private List<String> generateGrowthSuggestions(GrowthCurveAnalysis.CurveType curveType,
                                                 double smoothness, FieldAnalysis field) {
        List<String> suggestions = new ArrayList<>();

        switch (curveType) {
            case LINEAR:
                if (smoothness < 0.5) {
                    suggestions.add("考虑在关键节点增加数值跳跃，提升升级成就感");
                }
                break;
            case EXPONENTIAL:
                suggestions.add("前期数值增长过慢，考虑提高初期成长速度");
                suggestions.add("后期增长过快，可能需要设置成长上限");
                break;
            case LOGARITHMIC:
                suggestions.add("后期成长乏力，考虑引入新的成长维度");
                break;
            default:
                suggestions.add("成长曲线不规律，建议重新设计数值体系");
        }

        return suggestions;
    }

    /**
     * 查找指定的字段
     */
    private FieldAnalysis findField(Map<String, FieldAnalysis> fieldAnalyses, String... candidates) {
        for (String candidate : candidates) {
            for (FieldAnalysis field : fieldAnalyses.values()) {
                if (field.getFieldName().toLowerCase().contains(candidate.toLowerCase())) {
                    return field;
                }
            }
        }
        return null;
    }
}