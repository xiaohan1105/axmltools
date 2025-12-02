package red.jiuzhou.analysis.enhanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 游戏系统智能检测器
 * 不再是简单的字段匹配，而是深度理解游戏数据的上下文和系统特征
 */
public class GameSystemDetector {

    private static final Logger log = LoggerFactory.getLogger(GameSystemDetector.class);

    // 装备系统特征模式
    private static final Set<String> EQUIPMENT_INDICATORS = new HashSet<>(Arrays.asList(
        "attack", "atk", "攻击", "defense", "def", "防御", "damage", "伤害",
        "weapon", "武器", "armor", "装备", "equipment", "gear", "item",
        "level", "等级", "tier", "grade", "quality", "品质", "rarity", "稀有"
    ));

    // 角色系统特征模式
    private static final Set<String> CHARACTER_INDICATORS = new HashSet<>(Arrays.asList(
        "hp", "health", "生命", "blood", "血量", "mp", "mana", "魔法",
        "exp", "experience", "经验", "level", "等级", "skill", "技能",
        "class", "职业", "race", "种族", "character", "角色", "hero", "英雄"
    ));

    // 经济系统特征模式
    private static final Set<String> ECONOMY_INDICATORS = new HashSet<>(Arrays.asList(
        "price", "价格", "cost", "费用", "gold", "金币", "money", "货币",
        "coin", "银币", "buy", "购买", "sell", "出售", "shop", "商店",
        "reward", "奖励", "drop", "掉落", "loot", "战利品"
    ));

    // 战斗系统特征模式
    private static final Set<String> COMBAT_INDICATORS = new HashSet<>(Arrays.asList(
        "damage", "伤害", "attack", "攻击", "defense", "防御", "crit", "暴击",
        "accuracy", "命中", "dodge", "闪避", "speed", "速度", "initiative", "先手",
        "skill", "技能", "spell", "法术", "buff", "增益", "debuff", "减益"
    ));

    // 任务系统特征模式
    private static final Set<String> QUEST_INDICATORS = new HashSet<>(Arrays.asList(
        "quest", "任务", "mission", "objective", "目标", "target", "完成",
        "reward", "奖励", "requirement", "需求", "condition", "条件",
        "progress", "进度", "status", "状态", "complete", "完成"
    ));

    /**
     * 游戏系统类型枚举
     */
    public enum GameSystemType {
        EQUIPMENT("装备系统", "武器、防具等装备数据"),
        CHARACTER("角色系统", "角色属性、等级成长"),
        ECONOMY("经济系统", "价格、货币、交易"),
        COMBAT("战斗系统", "战斗数值、技能效果"),
        QUEST("任务系统", "任务流程、奖励配置"),
        NPC("NPC系统", "非玩家角色配置"),
        WORLD("世界系统", "地图、场景配置"),
        CONFIG("配置系统", "游戏参数、设置"),
        UNKNOWN("未知系统", "无法明确分类的数据");

        private final String displayName;
        private final String description;

        GameSystemType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * 系统检测结果
     */
    public static class SystemDetectionResult {
        private final GameSystemType primaryType;
        private final GameSystemType secondaryType;
        private final double confidence;
        private final String reasoning;
        private final Map<String, Object> characteristics;

        public SystemDetectionResult(GameSystemType primaryType, GameSystemType secondaryType,
                                   double confidence, String reasoning, Map<String, Object> characteristics) {
            this.primaryType = primaryType;
            this.secondaryType = secondaryType;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.characteristics = characteristics;
        }

        public GameSystemType getPrimaryType() { return primaryType; }
        public GameSystemType getSecondaryType() { return secondaryType; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public Map<String, Object> getCharacteristics() { return characteristics; }
    }

    /**
     * 数据上下文信息
     */
    public static class DataContext {
        private final String fileName;
        private final List<String> fieldNames;
        private final Map<String, Integer> fieldCounts;
        private final Map<String, Set<String>> sampleValues;
        private final int recordCount;

        public DataContext(String fileName, List<String> fieldNames,
                         Map<String, Integer> fieldCounts, Map<String, Set<String>> sampleValues,
                         int recordCount) {
            this.fileName = fileName;
            this.fieldNames = fieldNames;
            this.fieldCounts = fieldCounts;
            this.sampleValues = sampleValues;
            this.recordCount = recordCount;
        }

        public String getFileName() { return fileName; }
        public List<String> getFieldNames() { return fieldNames; }
        public Map<String, Integer> getFieldCounts() { return fieldCounts; }
        public Map<String, Set<String>> getSampleValues() { return sampleValues; }
        public int getRecordCount() { return recordCount; }
    }

    /**
     * 检测游戏系统类型
     *
     * @param context 数据上下文信息
     * @return 系统检测结果
     */
    public SystemDetectionResult detectGameSystem(DataContext context) {
        log.debug("开始检测游戏系统类型，文件: {}, 字段数: {}, 记录数: {}",
                 context.getFileName(), context.getFieldNames().size(), context.getRecordCount());

        // 计算各系统的匹配分数
        Map<GameSystemType, Double> scores = new HashMap<>();
        Map<GameSystemType, String> reasonings = new HashMap<>();

        // 1. 基于字段名的初步匹配
        scores.put(GameSystemType.EQUIPMENT, calculateEquipmentScore(context, reasonings));
        scores.put(GameSystemType.CHARACTER, calculateCharacterScore(context, reasonings));
        scores.put(GameSystemType.ECONOMY, calculateEconomyScore(context, reasonings));
        scores.put(GameSystemType.COMBAT, calculateCombatScore(context, reasonings));
        scores.put(GameSystemType.QUEST, calculateQuestScore(context, reasonings));

        // 2. 基于文件名的额外权重
        applyFileNameBonus(context.getFileName(), scores);

        // 3. 基于数据特征的调整
        adjustScoresByDataCharacteristics(context, scores);

        // 4. 选择最佳匹配
        GameSystemType primaryType = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(GameSystemType.UNKNOWN);

        GameSystemType secondaryType = scores.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(primaryType))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        double confidence = scores.get(primaryType);
        String reasoning = reasonings.get(primaryType);

        // 5. 提取系统特征
        Map<String, Object> characteristics = extractSystemCharacteristics(context, primaryType);

        log.info("系统检测完成: {} (置信度: {:.2f}), 推理: {}",
                primaryType.getDisplayName(), confidence, reasoning);

        return new SystemDetectionResult(primaryType, secondaryType, confidence, reasoning, characteristics);
    }

    /**
     * 计算装备系统匹配分数
     */
    private double calculateEquipmentScore(DataContext context, Map<GameSystemType, String> reasonings) {
        double score = 0.0;
        List<String> evidence = new ArrayList<>();

        // 检查核心装备属性
        Set<String> foundAttributes = new HashSet<>();
        for (String field : context.getFieldNames()) {
            String lowerField = field.toLowerCase();
            if (EQUIPMENT_INDICATORS.stream().anyMatch(lowerField::contains)) {
                foundAttributes.add(field);
                score += 0.15;
            }
        }

        // 装备系统通常有攻击、防御、品质等维度
        if (foundAttributes.size() >= 3) {
            score += 0.3;
            evidence.add("包含多个装备属性: " + foundAttributes);
        }

        // 检查是否有等级、品质分层
        if (hasLevelProgression(context)) {
            score += 0.2;
            evidence.add("具有等级或品质分层");
        }

        // 检查数值范围是否符合装备特征
        if (hasTypicalEquipmentRanges(context)) {
            score += 0.15;
            evidence.add("数值范围符合装备特征");
        }

        reasonings.put(GameSystemType.EQUIPMENT, String.join("; ", evidence));
        return Math.min(score, 1.0);
    }

    /**
     * 计算角色系统匹配分数
     */
    private double calculateCharacterScore(DataContext context, Map<GameSystemType, String> reasonings) {
        double score = 0.0;
        List<String> evidence = new ArrayList<>();

        Set<String> foundAttributes = new HashSet<>();
        for (String field : context.getFieldNames()) {
            String lowerField = field.toLowerCase();
            if (CHARACTER_INDICATORS.stream().anyMatch(lowerField::contains)) {
                foundAttributes.add(field);
                score += 0.12;
            }
        }

        // 角色系统通常有生命、魔法、经验等基础属性
        if (foundAttributes.size() >= 3) {
            score += 0.25;
            evidence.add("包含多个角色属性: " + foundAttributes);
        }

        // 检查是否有成长体系
        if (hasCharacterProgression(context)) {
            score += 0.25;
            evidence.add("具有角色成长体系");
        }

        reasonings.put(GameSystemType.CHARACTER, String.join("; ", evidence));
        return Math.min(score, 1.0);
    }

    /**
     * 计算经济系统匹配分数
     */
    private double calculateEconomyScore(DataContext context, Map<GameSystemType, String> reasonings) {
        double score = 0.0;
        List<String> evidence = new ArrayList<>();

        Set<String> foundAttributes = new HashSet<>();
        for (String field : context.getFieldNames()) {
            String lowerField = field.toLowerCase();
            if (ECONOMY_INDICATORS.stream().anyMatch(lowerField::contains)) {
                foundAttributes.add(field);
                score += 0.2;
            }
        }

        // 经济系统的价格分布特征
        if (hasEconomicDistribution(context)) {
            score += 0.3;
            evidence.add("具有经济分布特征");
        }

        reasonings.put(GameSystemType.ECONOMY, String.join("; ", evidence));
        return Math.min(score, 1.0);
    }

    /**
     * 计算战斗系统匹配分数
     */
    private double calculateCombatScore(DataContext context, Map<GameSystemType, String> reasonings) {
        double score = 0.0;
        List<String> evidence = new ArrayList<>();

        Set<String> foundAttributes = new HashSet<>();
        for (String field : context.getFieldNames()) {
            String lowerField = field.toLowerCase();
            if (COMBAT_INDICATORS.stream().anyMatch(lowerField::contains)) {
                foundAttributes.add(field);
                score += 0.15;
            }
        }

        if (foundAttributes.size() >= 2) {
            score += 0.2;
            evidence.add("包含战斗相关属性: " + foundAttributes);
        }

        reasonings.put(GameSystemType.COMBAT, String.join("; ", evidence));
        return Math.min(score, 1.0);
    }

    /**
     * 计算任务系统匹配分数
     */
    private double calculateQuestScore(DataContext context, Map<GameSystemType, String> reasonings) {
        double score = 0.0;
        List<String> evidence = new ArrayList<>();

        Set<String> foundAttributes = new HashSet<>();
        for (String field : context.getFieldNames()) {
            String lowerField = field.toLowerCase();
            if (QUEST_INDICATORS.stream().anyMatch(lowerField::contains)) {
                foundAttributes.add(field);
                score += 0.2;
            }
        }

        reasonings.put(GameSystemType.QUEST, String.join("; ", evidence));
        return Math.min(score, 1.0);
    }

    /**
     * 根据文件名调整分数
     */
    private void applyFileNameBonus(String fileName, Map<GameSystemType, Double> scores) {
        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.contains("item") || lowerFileName.contains("weapon") ||
            lowerFileName.contains("armor") || lowerFileName.contains("equipment")) {
            scores.computeIfPresent(GameSystemType.EQUIPMENT, (k, v) -> v + 0.2);
        }

        if (lowerFileName.contains("character") || lowerFileName.contains("hero") ||
            lowerFileName.contains("player") || lowerFileName.contains("npc")) {
            scores.computeIfPresent(GameSystemType.CHARACTER, (k, v) -> v + 0.2);
        }

        if (lowerFileName.contains("quest") || lowerFileName.contains("mission") ||
            lowerFileName.contains("task")) {
            scores.computeIfPresent(GameSystemType.QUEST, (k, v) -> v + 0.2);
        }

        if (lowerFileName.contains("shop") || lowerFileName.contains("price") ||
            lowerFileName.contains("economy")) {
            scores.computeIfPresent(GameSystemType.ECONOMY, (k, v) -> v + 0.2);
        }
    }

    /**
     * 根据数据特征调整分数
     */
    private void adjustScoresByDataCharacteristics(DataContext context, Map<GameSystemType, Double> scores) {
        // 小数据集可能是配置文件
        if (context.getRecordCount() < 10) {
            scores.put(GameSystemType.CONFIG, 0.3);
        }

        // 大量文本字段可能是配置或文本系统
        long textFieldCount = context.getFieldNames().stream()
                .mapToLong(field -> context.getSampleValues().getOrDefault(field, new HashSet<>()).stream()
                        .mapToLong(value -> value.length() > 20 ? 1 : 0).sum())
                .sum();

        if (textFieldCount > context.getFieldNames().size() * 0.5) {
            scores.replaceAll((k, v) -> v * 0.7); // 降低其他系统分数
            scores.put(GameSystemType.CONFIG, Math.max(scores.getOrDefault(GameSystemType.CONFIG, 0.0), 0.4));
        }
    }

    /**
     * 检查是否有等级进度特征
     */
    private boolean hasLevelProgression(DataContext context) {
        return context.getFieldNames().stream()
                .anyMatch(field -> field.toLowerCase().matches(".*(level|等级|lv|grade|tier).*"));
    }

    /**
     * 检查是否有角色成长特征
     */
    private boolean hasCharacterProgression(DataContext context) {
        return context.getFieldNames().stream()
                .anyMatch(field -> field.toLowerCase().matches(".*(exp|experience|经验|level|等级).*"));
    }

    /**
     * 检查是否有典型装备数值范围
     */
    private boolean hasTypicalEquipmentRanges(DataContext context) {
        // 简化实现，检查是否有数值型字段
        return context.getFieldNames().stream()
                .anyMatch(field -> {
                    Set<String> values = context.getSampleValues().get(field);
                    if (values == null) return false;
                    return values.stream().anyMatch(this::isNumeric);
                });
    }

    /**
     * 检查是否有经济分布特征
     */
    private boolean hasEconomicDistribution(DataContext context) {
        return context.getFieldNames().stream()
                .anyMatch(field -> field.toLowerCase().matches(".*(price|cost|金币|货币|money).*"));
    }

    /**
     * 提取系统特征
     */
    private Map<String, Object> extractSystemCharacteristics(DataContext context, GameSystemType systemType) {
        Map<String, Object> characteristics = new HashMap<>();

        characteristics.put("recordCount", context.getRecordCount());
        characteristics.put("fieldCount", context.getFieldNames().size());
        characteristics.put("detectedFields", getRelevantFields(context, systemType));
        characteristics.put("hasProgressionSystem", hasLevelProgression(context));
        characteristics.put("complexity", categorizeComplexity(context));

        return characteristics;
    }

    /**
     * 获取相关字段
     */
    private List<String> getRelevantFields(DataContext context, GameSystemType systemType) {
        Set<String> indicators;
        switch (systemType) {
            case EQUIPMENT:
                indicators = EQUIPMENT_INDICATORS;
                break;
            case CHARACTER:
                indicators = CHARACTER_INDICATORS;
                break;
            case ECONOMY:
                indicators = ECONOMY_INDICATORS;
                break;
            case COMBAT:
                indicators = COMBAT_INDICATORS;
                break;
            case QUEST:
                indicators = QUEST_INDICATORS;
                break;
            default:
                return new ArrayList<>();
        }

        return context.getFieldNames().stream()
                .filter(field -> indicators.stream()
                        .anyMatch(indicator -> field.toLowerCase().contains(indicator)))
                .collect(Collectors.toList());
    }

    /**
     * 分类复杂度
     */
    private String categorizeComplexity(DataContext context) {
        int fieldCount = context.getFieldNames().size();
        int recordCount = context.getRecordCount();

        if (fieldCount <= 5 && recordCount <= 20) return "简单";
        if (fieldCount <= 15 && recordCount <= 100) return "中等";
        return "复杂";
    }

    /**
     * 检查是否为数值
     */
    private boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        try {
            Double.parseDouble(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}