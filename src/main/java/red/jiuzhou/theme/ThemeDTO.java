package red.jiuzhou.theme;

import java.time.Instant;
import java.util.*;

/**
 * 主题数据传输对象
 *
 * 用于序列化和反序列化，解决不可变对象的序列化问题
 *
 * @author Claude
 * @version 1.0
 */
public class ThemeDTO {

    private String id;
    private String name;
    private String description;
    private String version;
    private String author;
    private Instant createdAt;
    private String type;  // ThemeType的名称
    private String scope; // ThemeScope的名称

    // 转换配置
    private List<RuleDTO> rules;
    private Map<String, String> aiPrompts;
    private List<String> targetFilePatterns;
    private Map<String, String> fieldMappings;

    // 应用配置
    private Map<String, Object> settings;  // ThemeSettings序列化为Map

    // 元数据
    private Map<String, Object> metadata;

    public ThemeDTO() {
        // Jackson需要无参构造器
    }

    /**
     * 从Theme对象创建DTO
     */
    public static ThemeDTO fromTheme(Theme theme) {
        ThemeDTO dto = new ThemeDTO();
        dto.id = theme.getId();
        dto.name = theme.getName();
        dto.description = theme.getDescription();
        dto.version = theme.getVersion();
        dto.author = theme.getAuthor();
        dto.createdAt = theme.getCreatedAt();
        dto.type = theme.getType().name();
        dto.scope = theme.getScope().name();

        // 转换规则
        dto.rules = new ArrayList<>();
        for (TransformRule rule : theme.getRules()) {
            dto.rules.add(RuleDTO.fromRule(rule));
        }

        dto.aiPrompts = new LinkedHashMap<>(theme.getAiPrompts());
        dto.targetFilePatterns = new ArrayList<>(theme.getTargetFilePatterns());
        dto.fieldMappings = new LinkedHashMap<>(theme.getFieldMappings());

        // 转换设置为Map
        dto.settings = settingsToMap(theme.getSettings());

        dto.metadata = new LinkedHashMap<>(theme.getMetadata());

        return dto;
    }

    /**
     * 转换为Theme对象
     */
    public Theme toTheme() {
        Theme.Builder builder = Theme.builder()
                .id(id)
                .name(name)
                .description(description)
                .version(version)
                .author(author)
                .createdAt(createdAt)
                .type(ThemeType.valueOf(type))
                .scope(ThemeScope.valueOf(scope));

        // 恢复规则
        List<RuleDTO> safeRules = rules != null ? rules : Collections.emptyList();
        for (RuleDTO ruleDTO : safeRules) {
            builder.addRule(ruleDTO.toRule());
        }

        // 恢复配置
        Map<String, String> promptMap = aiPrompts != null ? aiPrompts : Collections.emptyMap();
        for (Map.Entry<String, String> entry : promptMap.entrySet()) {
            builder.addAiPrompt(entry.getKey(), entry.getValue());
        }

        List<String> patterns = targetFilePatterns != null ? targetFilePatterns : Collections.emptyList();
        for (String pattern : patterns) {
            builder.addTargetFilePattern(pattern);
        }

        Map<String, String> mapping = fieldMappings != null ? fieldMappings : Collections.emptyMap();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            builder.addFieldMapping(entry.getKey(), entry.getValue());
        }

        // 恢复设置
        builder.settings(mapToSettings(settings));

        // 恢复元数据
        Map<String, Object> meta = metadata != null ? metadata : Collections.emptyMap();
        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            builder.addMetadata(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    private static Map<String, Object> settingsToMap(ThemeSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("preserveNumericValues", settings.isPreserveNumericValues());
        map.put("preserveIds", settings.isPreserveIds());
        map.put("preserveReferences", settings.isPreserveReferences());
        map.put("useAiTransform", settings.isUseAiTransform());
        map.put("aiModel", settings.getAiModel());
        map.put("backupBeforeApply", settings.isBackupBeforeApply());
        map.put("validateAfterApply", settings.isValidateAfterApply());
        map.put("transactional", settings.isTransactional());
        map.put("maxConcurrency", settings.getMaxConcurrency());
        map.put("minConfidenceThreshold", settings.getMinConfidenceThreshold());
        map.put("skipOnError", settings.isSkipOnError());
        map.put("collectErrors", settings.isCollectErrors());
        map.put("cacheAiResults", settings.isCacheAiResults());
        map.put("cacheSizeLimit", settings.getCacheSizeLimit());
        map.put("customSettings", settings.getCustomSettings());
        return map;
    }

    private static ThemeSettings mapToSettings(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return ThemeSettings.defaultSettings();
        }

        ThemeSettings.Builder builder = ThemeSettings.builder()
                .preserveNumericValues(booleanValue(map, "preserveNumericValues", true))
                .preserveIds(booleanValue(map, "preserveIds", true))
                .preserveReferences(booleanValue(map, "preserveReferences", true))
                .useAiTransform(booleanValue(map, "useAiTransform", true))
                .aiModel(stringValue(map, "aiModel", "qwen-plus"))
                .backupBeforeApply(booleanValue(map, "backupBeforeApply", true))
                .validateAfterApply(booleanValue(map, "validateAfterApply", true))
                .transactional(booleanValue(map, "transactional", true))
                .maxConcurrency(intValue(map, "maxConcurrency", 4))
                .minConfidenceThreshold(doubleValue(map, "minConfidenceThreshold", 0.7))
                .skipOnError(booleanValue(map, "skipOnError", false))
                .collectErrors(booleanValue(map, "collectErrors", true))
                .cacheAiResults(booleanValue(map, "cacheAiResults", true))
                .cacheSizeLimit(intValue(map, "cacheSizeLimit", 1000));

        @SuppressWarnings("unchecked")
        Map<String, Object> customSettings = (Map<String, Object>) map.get("customSettings");
        if (customSettings != null) {
            for (Map.Entry<String, Object> entry : customSettings.entrySet()) {
                builder.addCustomSetting(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }

    private static boolean booleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String normalized = ((String) value).trim();
            if (normalized.isEmpty()) {
                return defaultValue;
            }
            if ("1".equals(normalized)) {
                return true;
            }
            if ("0".equals(normalized)) {
                return false;
            }
            return Boolean.parseBoolean(normalized);
        }
        return defaultValue;
    }

    private static int intValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String normalized = ((String) value).trim();
            if (normalized.isEmpty()) {
                return defaultValue;
            }
            try {
                return (int) Math.round(Double.parseDouble(normalized));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double doubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            String normalized = ((String) value).trim();
            if (normalized.isEmpty()) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(normalized);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String stringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }

    // Getters and setters for Jackson
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public List<RuleDTO> getRules() { return rules; }
    public void setRules(List<RuleDTO> rules) { this.rules = rules; }

    public Map<String, String> getAiPrompts() { return aiPrompts; }
    public void setAiPrompts(Map<String, String> aiPrompts) { this.aiPrompts = aiPrompts; }

    public List<String> getTargetFilePatterns() { return targetFilePatterns; }
    public void setTargetFilePatterns(List<String> targetFilePatterns) { this.targetFilePatterns = targetFilePatterns; }

    public Map<String, String> getFieldMappings() { return fieldMappings; }
    public void setFieldMappings(Map<String, String> fieldMappings) { this.fieldMappings = fieldMappings; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) { this.settings = settings; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    /**
     * 规则DTO
     */
    public static class RuleDTO {
        private String ruleType;  // "text", "mapping", "regex"
        private String name;
        private String description;
        private int priority;
        private Map<String, Object> config;

        public RuleDTO() {}

        public static RuleDTO fromRule(TransformRule rule) {
            RuleDTO dto = new RuleDTO();
            dto.name = rule.getName();
            dto.description = rule.getDescription();
            dto.priority = rule.getPriority();
            dto.config = new LinkedHashMap<>();

            if (rule instanceof red.jiuzhou.theme.rules.TextStyleRule) {
                dto.ruleType = "text";
                red.jiuzhou.theme.rules.TextStyleRule textRule =
                        (red.jiuzhou.theme.rules.TextStyleRule) rule;
                dto.config.put("style", textRule.getStyle());
                dto.config.put("aiPrompt", textRule.getAiPrompt());

            } else if (rule instanceof red.jiuzhou.theme.rules.MappingRule) {
                dto.ruleType = "mapping";
                red.jiuzhou.theme.rules.MappingRule mappingRule =
                        (red.jiuzhou.theme.rules.MappingRule) rule;
                dto.config.put("mappings", mappingRule.getMappings());

            } else if (rule instanceof red.jiuzhou.theme.rules.RegexRule) {
                dto.ruleType = "regex";
                red.jiuzhou.theme.rules.RegexRule regexRule =
                        (red.jiuzhou.theme.rules.RegexRule) rule;
                dto.config.put("pattern", regexRule.getPattern().pattern());
                dto.config.put("replacement", regexRule.getReplacement());

            } else {
                dto.ruleType = "custom";
                dto.config.put("className", rule.getClass().getName());
            }

            return dto;
        }

        public TransformRule toRule() {
            switch (ruleType) {
                case "text":
                    return new red.jiuzhou.theme.rules.TextStyleRule(
                            name,
                            (String) config.get("style"),
                            (String) config.get("aiPrompt"),
                            priority
                    );

                case "mapping":
                    @SuppressWarnings("unchecked")
                    Map<String, String> mappings = (Map<String, String>) config.get("mappings");
                    red.jiuzhou.theme.rules.MappingRule.Builder mappingBuilder =
                            red.jiuzhou.theme.rules.MappingRule.builder(name);
                    mappingBuilder.description(description);
                    mappingBuilder.priority(priority);
                    mappingBuilder.addMappings(mappings);
                    return mappingBuilder.build();

                case "regex":
                    return red.jiuzhou.theme.rules.RegexRule.builder(name)
                            .description(description)
                            .regex((String) config.get("pattern"))
                            .replacement((String) config.get("replacement"))
                            .priority(priority)
                            .build();

                default:
                    throw new IllegalStateException("Unknown rule type: " + ruleType);
            }
        }

        // Getters and setters
        public String getRuleType() { return ruleType; }
        public void setRuleType(String ruleType) { this.ruleType = ruleType; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }
}
