package red.jiuzhou.analysis.enhanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ai.AiModelClient;
import red.jiuzhou.ai.AiModelFactory;
import red.jiuzhou.util.YamlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能洞察引擎 - 游戏策划的AI助手
 *
 * 核心理念：将技术数据转化为策划可理解的游戏设计洞察
 * 不再是冷冰冰的统计数字，而是有温度的设计建议
 */
public class SmartInsightEngine {

    private static final Logger log = LoggerFactory.getLogger(SmartInsightEngine.class);

    private final AiModelClient aiClient;
    private final boolean aiEnabled;

    public SmartInsightEngine() {
        this.aiEnabled = "true".equalsIgnoreCase(YamlUtils.getProperty("ai.insight.enabled"));
        this.aiClient = aiEnabled ? AiModelFactory.getClient("qwen") : null;
        log.info("智能洞察引擎初始化完成，AI增强: {}", aiEnabled ? "启用" : "禁用");
    }

    /**
     * 洞察级别
     */
    public enum InsightLevel {
        CRITICAL("🚨 关键问题", "需要立即处理的严重问题", "#FF4444"),
        HIGH("⚠️ 重要建议", "对游戏体验有显著影响", "#FF8800"),
        MEDIUM("💡 优化建议", "可以改善的地方", "#4CAF50"),
        LOW("📝 参考信息", "值得了解的数据特征", "#2196F3"),
        POSITIVE("✅ 设计亮点", "做得很好的地方", "#00C853");

        private final String displayName;
        private final String description;
        private final String color;

        InsightLevel(String displayName, String description, String color) {
            this.displayName = displayName;
            this.description = description;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getColor() { return color; }
    }

    /**
     * 智能洞察结果
     */
    public static class SmartInsight {
        private final String title;                    // 简洁标题
        private final String description;              // 详细描述
        private final InsightLevel level;              // 重要程度
        private final String impact;                   // 对游戏的影响
        private final List<String> suggestions;       // 具体建议
        private final Map<String, Object> evidence;   // 支撑数据
        private final double confidence;               // AI置信度
        private final String category;                 // 分类标签
        private final LocalDateTime timestamp;         // 生成时间

        public SmartInsight(String title, String description, InsightLevel level,
                          String impact, List<String> suggestions, Map<String, Object> evidence,
                          double confidence, String category) {
            this.title = title;
            this.description = description;
            this.level = level;
            this.impact = impact;
            this.suggestions = suggestions;
            this.evidence = evidence;
            this.confidence = confidence;
            this.category = category;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public InsightLevel getLevel() { return level; }
        public String getImpact() { return impact; }
        public List<String> getSuggestions() { return suggestions; }
        public Map<String, Object> getEvidence() { return evidence; }
        public double getConfidence() { return confidence; }
        public String getCategory() { return category; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * 游戏数据上下文
     */
    public static class GameDataContext {
        private final String fileName;
        private final GameSystemDetector.GameSystemType systemType;
        private final Map<String, FieldAnalysis> fieldAnalyses;
        private final DataQualityMetrics qualityMetrics;
        private final int recordCount;
        private final Map<String, Object> systemCharacteristics;

        public GameDataContext(String fileName, GameSystemDetector.GameSystemType systemType,
                             Map<String, FieldAnalysis> fieldAnalyses, DataQualityMetrics qualityMetrics,
                             int recordCount, Map<String, Object> systemCharacteristics) {
            this.fileName = fileName;
            this.systemType = systemType;
            this.fieldAnalyses = fieldAnalyses;
            this.qualityMetrics = qualityMetrics;
            this.recordCount = recordCount;
            this.systemCharacteristics = systemCharacteristics;
        }

        // Getters
        public String getFileName() { return fileName; }
        public GameSystemDetector.GameSystemType getSystemType() { return systemType; }
        public Map<String, FieldAnalysis> getFieldAnalyses() { return fieldAnalyses; }
        public DataQualityMetrics getQualityMetrics() { return qualityMetrics; }
        public int getRecordCount() { return recordCount; }
        public Map<String, Object> getSystemCharacteristics() { return systemCharacteristics; }
    }

    /**
     * 字段分析结果
     */
    public static class FieldAnalysis {
        private final String fieldName;
        private final String dataType;        // 数值/文本/布尔等
        private final double coverage;        // 覆盖率
        private final int uniqueCount;        // 唯一值数量
        private final boolean hasOutliers;    // 是否有异常值
        private final Map<String, Object> statistics; // 统计信息
        private final List<String> sampleValues; // 样本值

        public FieldAnalysis(String fieldName, String dataType, double coverage, int uniqueCount,
                           boolean hasOutliers, Map<String, Object> statistics, List<String> sampleValues) {
            this.fieldName = fieldName;
            this.dataType = dataType;
            this.coverage = coverage;
            this.uniqueCount = uniqueCount;
            this.hasOutliers = hasOutliers;
            this.statistics = statistics;
            this.sampleValues = sampleValues;
        }

        // Getters
        public String getFieldName() { return fieldName; }
        public String getDataType() { return dataType; }
        public double getCoverage() { return coverage; }
        public int getUniqueCount() { return uniqueCount; }
        public boolean hasOutliers() { return hasOutliers; }
        public Map<String, Object> getStatistics() { return statistics; }
        public List<String> getSampleValues() { return sampleValues; }
    }

    /**
     * 数据质量指标
     */
    public static class DataQualityMetrics {
        private final double completeness;     // 完整性
        private final double consistency;     // 一致性
        private final double balance;         // 平衡性
        private final double progression;     // 成长性
        private final Map<String, Double> subMetrics; // 子指标

        public DataQualityMetrics(double completeness, double consistency, double balance,
                                double progression, Map<String, Double> subMetrics) {
            this.completeness = completeness;
            this.consistency = consistency;
            this.balance = balance;
            this.progression = progression;
            this.subMetrics = subMetrics;
        }

        // Getters
        public double getCompleteness() { return completeness; }
        public double getConsistency() { return consistency; }
        public double getBalance() { return balance; }
        public double getProgression() { return progression; }
        public Map<String, Double> getSubMetrics() { return subMetrics; }
    }

    /**
     * 生成智能洞察
     */
    public List<SmartInsight> generateInsights(GameDataContext context) {
        log.info("开始生成智能洞察：{} ({})", context.getFileName(), context.getSystemType().getDisplayName());

        List<SmartInsight> insights = new ArrayList<>();

        try {
            // 1. 数据质量洞察
            insights.addAll(analyzeDataQuality(context));

            // 2. 系统特定洞察
            insights.addAll(analyzeGameSystem(context));

            // 3. 平衡性洞察
            insights.addAll(analyzeBalance(context));

            // 4. 用户体验洞察
            insights.addAll(analyzeUserExperience(context));

            // 5. AI增强洞察（如果启用）
            if (aiEnabled) {
                insights.addAll(generateAIInsights(context, insights));
            }

            // 6. 按重要性排序
            insights.sort((a, b) -> {
                int levelCompare = a.getLevel().ordinal() - b.getLevel().ordinal();
                if (levelCompare != 0) return levelCompare;
                return Double.compare(b.getConfidence(), a.getConfidence());
            });

            log.info("洞察生成完成，共 {} 条洞察，AI增强: {}", insights.size(), aiEnabled);

        } catch (Exception e) {
            log.error("生成洞察时发生错误", e);
            // 返回基础洞察，确保功能可用
            insights.add(createFallbackInsight(context));
        }

        return insights;
    }

    /**
     * 分析数据质量
     */
    private List<SmartInsight> analyzeDataQuality(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        DataQualityMetrics quality = context.getQualityMetrics();

        // 完整性检查
        if (quality.getCompleteness() < 0.7) {
            insights.add(new SmartInsight(
                "数据完整性不足",
                String.format("数据完整性仅为 %.1f%%，可能影响分析准确性", quality.getCompleteness() * 100),
                InsightLevel.HIGH,
                "不完整的数据可能导致游戏功能异常或玩家体验问题",
                Arrays.asList(
                    "检查数据导入流程，确保所有必要字段都有值",
                    "为空值字段设置合理的默认值",
                    "建立数据完整性监控机制"
                ),
                createMap("completeness", quality.getCompleteness(), "threshold", 0.7),
                0.9,
                "数据质量"
            ));
        } else if (quality.getCompleteness() > 0.95) {
            insights.add(new SmartInsight(
                "数据完整性优秀",
                String.format("数据完整性达到 %.1f%%，数据质量很好", quality.getCompleteness() * 100),
                InsightLevel.POSITIVE,
                "高质量的数据有助于确保游戏功能正常运行",
                Arrays.asList("继续保持现有的数据管理标准"),
                createMap("completeness", quality.getCompleteness()),
                0.95,
                "数据质量"
            ));
        }

        // 一致性检查
        if (quality.getConsistency() < 0.8) {
            insights.add(new SmartInsight(
                "数据一致性问题",
                "发现数据格式或命名不一致的情况",
                InsightLevel.MEDIUM,
                "不一致的数据可能导致游戏逻辑错误",
                Arrays.asList(
                    "统一数据格式和命名规范",
                    "建立数据验证规则",
                    "定期检查数据一致性"
                ),
                createMap("consistency", quality.getConsistency()),
                0.85,
                "数据质量"
            ));
        }

        return insights;
    }

    /**
     * 分析游戏系统特性
     */
    private List<SmartInsight> analyzeGameSystem(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        switch (context.getSystemType()) {
            case EQUIPMENT:
                insights.addAll(analyzeEquipmentSystem(context));
                break;
            case CHARACTER:
                insights.addAll(analyzeCharacterSystem(context));
                break;
            case ECONOMY:
                insights.addAll(analyzeEconomySystem(context));
                break;
            case QUEST:
                insights.addAll(analyzeQuestSystem(context));
                break;
            default:
                insights.addAll(analyzeGenericSystem(context));
        }

        return insights;
    }

    /**
     * 分析装备系统
     */
    private List<SmartInsight> analyzeEquipmentSystem(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        // 检查装备平衡性
        FieldAnalysis attackField = findField(context, "attack", "atk", "攻击", "damage");
        FieldAnalysis levelField = findField(context, "level", "等级", "tier", "grade");

        if (attackField != null && levelField != null) {
            // 分析攻击力分布
            Map<String, Object> stats = attackField.getStatistics();
            if (stats.containsKey("max") && stats.containsKey("min")) {
                double max = (Double) stats.get("max");
                double min = (Double) stats.get("min");
                double ratio = max / Math.max(min, 1);

                if (ratio > 20) {
                    insights.add(new SmartInsight(
                        "装备攻击力差距过大",
                        String.format("最高攻击力是最低的 %.1f 倍，可能造成装备价值失衡", ratio),
                        InsightLevel.HIGH,
                        "巨大的属性差距会让低级装备迅速失去价值，影响游戏体验",
                        Arrays.asList(
                            "考虑收缩攻击力的数值范围",
                            "为低级装备添加特殊效果或用途",
                            "建立平滑的成长曲线"
                        ),
                        createMap("ratio", ratio, "max", max, "min", min),
                        0.8,
                        "装备平衡"
                    ));
                } else if (ratio < 3) {
                    insights.add(new SmartInsight(
                        "装备成长曲线平缓",
                        String.format("攻击力变化范围较小（%.1f倍），升级感可能不足", ratio),
                        InsightLevel.MEDIUM,
                        "过于平缓的属性成长可能让玩家感受不到明显的进步",
                        Arrays.asList(
                            "适当增加高级装备的属性优势",
                            "考虑添加更多属性维度",
                            "通过特效和外观增强升级感"
                        ),
                        createMap("ratio", ratio),
                        0.7,
                        "装备成长"
                    ));
                }
            }
        }

        // 检查装备数量分布
        if (context.getRecordCount() < 20) {
            insights.add(new SmartInsight(
                "装备种类偏少",
                String.format("当前只有 %d 种装备，可能影响游戏深度", context.getRecordCount()),
                InsightLevel.MEDIUM,
                "装备种类不足会限制玩家的选择和搭配乐趣",
                Arrays.asList(
                    "考虑增加更多装备种类",
                    "为不同职业设计专属装备",
                    "添加套装或组合效果"
                ),
                createMap("count", context.getRecordCount()),
                0.8,
                "内容丰富度"
            ));
        }

        return insights;
    }

    /**
     * 分析角色系统
     *
     */
    private List<SmartInsight> analyzeCharacterSystem(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        // 分析属性平衡
        FieldAnalysis hpField = findField(context, "hp", "health", "生命", "血量");
        FieldAnalysis atkField = findField(context, "attack", "atk", "攻击");

        if (hpField != null && atkField != null) {
            Map<String, Object> hpStats = hpField.getStatistics();
            Map<String, Object> atkStats = atkField.getStatistics();

            if (hpStats.containsKey("average") && atkStats.containsKey("average")) {
                double avgHp = (Double) hpStats.get("average");
                double avgAtk = (Double) atkStats.get("average");
                double hpAtkRatio = avgHp / Math.max(avgAtk, 1);

                if (hpAtkRatio < 3) {
                    insights.add(new SmartInsight(
                        "角色生存能力偏低",
                        String.format("平均生命值与攻击力比值为 %.1f，角色可能过于脆弱", hpAtkRatio),
                        InsightLevel.MEDIUM,
                        "生命值过低会导致战斗时间过短，影响策略性",
                        Arrays.asList(
                            "适当提高角色生命值",
                            "增加防御或护甲系统",
                            "调整战斗节奏"
                        ),
                        createMap("ratio", hpAtkRatio, "avgHp", avgHp, "avgAtk", avgAtk),
                        0.75,
                        "战斗平衡"
                    ));
                } else if (hpAtkRatio > 15) {
                    insights.add(new SmartInsight(
                        "战斗时间可能过长",
                        String.format("生命值相对攻击力过高（%.1f倍），战斗可能拖沓", hpAtkRatio),
                        InsightLevel.LOW,
                        "过长的战斗时间可能让玩家感到乏味",
                        Arrays.asList(
                            "考虑提高输出能力",
                            "添加破甲或穿透机制",
                            "增加战斗的变化性"
                        ),
                        createMap("ratio", hpAtkRatio),
                        0.65,
                        "战斗节奏"
                    ));
                }
            }
        }

        return insights;
    }

    /**
     * 分析经济系统
     */
    private List<SmartInsight> analyzeEconomySystem(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        FieldAnalysis priceField = findField(context, "price", "cost", "价格", "金币");
        if (priceField != null && priceField.hasOutliers()) {
            insights.add(new SmartInsight(
                "发现价格异常值",
                "部分物品的价格明显偏离正常范围",
                InsightLevel.HIGH,
                "异常的价格可能破坏游戏经济平衡",
                Arrays.asList(
                    "检查价格异常的物品是否配置错误",
                    "考虑建立价格区间限制",
                    "定期评估经济系统平衡性"
                ),
                createMap("hasOutliers", true, "field", priceField.getFieldName()),
                0.85,
                "经济平衡"
            ));
        }

        return insights;
    }

    /**
     * 分析任务系统
     */
    private List<SmartInsight> analyzeQuestSystem(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        if (context.getRecordCount() < 10) {
            insights.add(new SmartInsight(
                "任务数量可能不足",
                String.format("当前只有 %d 个任务，可能影响游戏可玩性", context.getRecordCount()),
                InsightLevel.MEDIUM,
                "任务数量不足会导致内容匮乏，玩家很快失去目标",
                Arrays.asList(
                    "增加更多主线和支线任务",
                    "设计日常任务系统",
                    "考虑程序化生成任务"
                ),
                createMap("count", context.getRecordCount()),
                0.8,
                "内容丰富度"
            ));
        }

        return insights;
    }

    /**
     * 分析通用系统
     */
    private List<SmartInsight> analyzeGenericSystem(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        // 基础数据分析
        insights.add(new SmartInsight(
            "数据概览",
            String.format("数据集包含 %d 条记录，%d 个字段",
                         context.getRecordCount(), context.getFieldAnalyses().size()),
            InsightLevel.LOW,
            "数据规模影响分析的深度和准确性",
            Arrays.asList("数据规模适中，可以进行有效分析"),
            createMap("records", context.getRecordCount(), "fields", context.getFieldAnalyses().size()),
            0.95,
            "基础信息"
        ));

        return insights;
    }

    /**
     * 分析平衡性
     */
    private List<SmartInsight> analyzeBalance(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        // 检查数值分布的均匀性
        long outlierFields = context.getFieldAnalyses().values().stream()
                .mapToLong(field -> field.hasOutliers() ? 1 : 0)
                .sum();

        if (outlierFields > context.getFieldAnalyses().size() * 0.3) {
            insights.add(new SmartInsight(
                "多个字段存在异常值",
                String.format("有 %d 个字段检测到异常值，需要关注数据质量", outlierFields),
                InsightLevel.HIGH,
                "大量异常值可能表明数据配置存在系统性问题",
                Arrays.asList(
                    "逐一检查异常值的合理性",
                    "建立数据验证机制",
                    "考虑数据清洗流程"
                ),
                createMap("outlierFields", outlierFields, "totalFields", context.getFieldAnalyses().size()),
                0.8,
                "数据异常"
            ));
        }

        return insights;
    }

    /**
     * 分析用户体验
     */
    private List<SmartInsight> analyzeUserExperience(GameDataContext context) {
        List<SmartInsight> insights = new ArrayList<>();

        // 检查数据覆盖率
        double avgCoverage = context.getFieldAnalyses().values().stream()
                .mapToDouble(FieldAnalysis::getCoverage)
                .average()
                .orElse(0.0);

        if (avgCoverage < 0.8) {
            insights.add(new SmartInsight(
                "数据覆盖率偏低",
                String.format("平均字段覆盖率为 %.1f%%，可能影响功能完整性", avgCoverage * 100),
                InsightLevel.MEDIUM,
                "数据缺失会导致功能不完整，影响玩家体验",
                Arrays.asList(
                    "为缺失字段提供默认值",
                    "检查数据生成逻辑",
                    "完善数据填充机制"
                ),
                createMap("coverage", avgCoverage),
                0.8,
                "用户体验"
            ));
        }

        return insights;
    }

    /**
     * 生成AI增强洞察
     */
    private List<SmartInsight> generateAIInsights(GameDataContext context, List<SmartInsight> existingInsights) {
        List<SmartInsight> aiInsights = new ArrayList<>();

        try {
            // 构建AI分析提示
            String prompt = buildAIPrompt(context, existingInsights);

            log.debug("发送AI分析请求...");
            String aiResponse = aiClient.chat(prompt);

            // 解析AI响应并生成洞察
            SmartInsight aiInsight = parseAIResponse(aiResponse, context);
            if (aiInsight != null) {
                aiInsights.add(aiInsight);
                log.info("AI增强洞察生成成功");
            }

        } catch (Exception e) {
            log.warn("AI增强洞察生成失败: {}", e.getMessage());
            // 失败时不影响主流程
        }

        return aiInsights;
    }

    /**
     * 构建AI分析提示
     */
    private String buildAIPrompt(GameDataContext context, List<SmartInsight> existingInsights) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("作为游戏策划专家，请分析以下游戏数据并提供深度洞察：\n\n");

        prompt.append("## 基础信息\n");
        prompt.append("- 文件：").append(context.getFileName()).append("\n");
        prompt.append("- 系统类型：").append(context.getSystemType().getDisplayName()).append("\n");
        prompt.append("- 记录数：").append(context.getRecordCount()).append("\n");
        prompt.append("- 字段数：").append(context.getFieldAnalyses().size()).append("\n\n");

        prompt.append("## 字段分析\n");
        context.getFieldAnalyses().values().stream()
                .limit(5) // 只展示前5个字段
                .forEach(field -> {
                    prompt.append("- ").append(field.getFieldName())
                           .append("：覆盖率 ").append(String.format("%.1f%%", field.getCoverage() * 100))
                           .append("，唯一值 ").append(field.getUniqueCount());
                    if (field.hasOutliers()) prompt.append("（有异常值）");
                    prompt.append("\n");
                });

        prompt.append("\n## 已有洞察\n");
        existingInsights.stream()
                .limit(3) // 只展示前3个洞察
                .forEach(insight -> prompt.append("- ").append(insight.getTitle()).append("\n"));

        prompt.append("\n请从游戏策划角度，提供一个具体的、可操作的深度洞察，包括：\n");
        prompt.append("1. 问题描述（50字内）\n");
        prompt.append("2. 对游戏的影响（80字内）\n");
        prompt.append("3. 具体建议（3个要点，每个30字内）\n");
        prompt.append("请用简洁、专业的语言，避免重复已有洞察。");

        return prompt.toString();
    }

    /**
     * 解析AI响应
     */
    private SmartInsight parseAIResponse(String response, GameDataContext context) {
        try {
            // 简化的AI响应解析
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            return new SmartInsight(
                "AI深度洞察",
                response.trim(),
                InsightLevel.MEDIUM,
                "基于AI分析的深度洞察",
                Arrays.asList("参考AI建议进行优化"),
                createMap("source", "AI分析", "model", "通义千问"),
                0.7,
                "AI分析"
            );

        } catch (Exception e) {
            log.warn("解析AI响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 创建兜底洞察
     */
    private SmartInsight createFallbackInsight(GameDataContext context) {
        return new SmartInsight(
            "数据分析完成",
            String.format("已成功分析 %s，包含 %d 条记录",
                         context.getFileName(), context.getRecordCount()),
            InsightLevel.LOW,
            "基础数据分析为进一步优化提供了基础",
            Arrays.asList("数据结构正常，可以进行深入分析"),
            createMap("status", "success"),
            0.95,
            "基础分析"
        );
    }

    /**
     * 查找指定字段
     */
    private FieldAnalysis findField(GameDataContext context, String... candidates) {
        for (String candidate : candidates) {
            for (FieldAnalysis field : context.getFieldAnalyses().values()) {
                if (field.getFieldName().toLowerCase().contains(candidate.toLowerCase())) {
                    return field;
                }
            }
        }
        return null;
    }

    /**
     * Java 8 compatible map creation helper methods
     */
    private static Map<String, Object> createMap(String key1, Object value1) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        return map;
    }

    private static Map<String, Object> createMap(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private static Map<String, Object> createMap(String key1, Object value1, String key2, Object value2, String key3, Object value3) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        return map;
    }

    private static Map<String, Object> createMap(String key1, Object value1, String key2, Object value2, String key3, Object value3, String key4, Object value4) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        map.put(key4, value4);
        return map;
    }
}
