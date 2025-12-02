package red.jiuzhou.theme;

import java.time.Instant;
import java.util.*;

/**
 * 主题定义
 *
 * 代表一个完整的游戏主题配置，包括风格、转换规则、AI配置等。
 *
 * 设计哲学：
 * - 主题是不可变的（创建后不可修改）
 * - 使用Builder模式构建
 * - 包含完整的元信息用于追踪和管理
 *
 * @author Claude
 * @version 1.0
 */
public class Theme {

    private final String id;
    private final String name;
    private final String description;
    private final String version;
    private final String author;
    private final Instant createdAt;
    private final ThemeType type;
    private final ThemeScope scope;

    // 转换配置
    private final List<TransformRule> rules;
    private final Map<String, String> aiPrompts;
    private final List<String> targetFilePatterns;
    private final Map<String, String> fieldMappings;

    // 应用配置
    private final ThemeSettings settings;

    // 元数据
    private final Map<String, Object> metadata;

    private Theme(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.author = builder.author;
        this.createdAt = builder.createdAt;
        this.type = builder.type;
        this.scope = builder.scope;
        this.rules = Collections.unmodifiableList(new ArrayList<>(builder.rules));
        this.aiPrompts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.aiPrompts));
        this.targetFilePatterns = Collections.unmodifiableList(new ArrayList<>(builder.targetFilePatterns));
        this.fieldMappings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fieldMappings));
        this.settings = builder.settings;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public String getAuthor() { return author; }
    public Instant getCreatedAt() { return createdAt; }
    public ThemeType getType() { return type; }
    public ThemeScope getScope() { return scope; }
    public List<TransformRule> getRules() { return rules; }
    public Map<String, String> getAiPrompts() { return aiPrompts; }
    public List<String> getTargetFilePatterns() { return targetFilePatterns; }
    public Map<String, String> getFieldMappings() { return fieldMappings; }
    public ThemeSettings getSettings() { return settings; }
    public Map<String, Object> getMetadata() { return metadata; }

    /**
     * 检查文件是否匹配此主题的目标模式
     *
     * @param filePath 文件路径
     * @return true 如果文件匹配主题的目标模式,false 否则
     */
    public boolean matchesFile(String filePath) {
        if (targetFilePatterns.isEmpty()) {
            return true; // 空模式表示匹配所有
        }

        String normalizedPath = filePath.replace('\\', '/').toLowerCase(Locale.ENGLISH);
        for (String pattern : targetFilePatterns) {
            if (matchesPattern(normalizedPath, pattern.toLowerCase(Locale.ENGLISH))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String path, String pattern) {
        // 简单的通配符匹配
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return path.matches(regex);
    }

    /**
     * 获取适用于指定字段的AI提示词
     * 支持精确匹配和模糊匹配(根据字段名称特征自动选择合适的提示词)
     *
     * @param fieldName 字段名称
     * @return AI提示词,如果没有匹配的则返回默认提示词
     */
    public String getAiPromptForField(String fieldName) {
        // 先尝试精确匹配
        if (aiPrompts.containsKey(fieldName)) {
            return aiPrompts.get(fieldName);
        }

        // 尝试模糊匹配
        String lowerFieldName = fieldName.toLowerCase(Locale.ENGLISH);
        if (lowerFieldName.contains("name") || lowerFieldName.contains("title")) {
            return aiPrompts.getOrDefault("name", aiPrompts.get("default"));
        } else if (lowerFieldName.contains("desc") || lowerFieldName.contains("description")) {
            return aiPrompts.getOrDefault("description", aiPrompts.get("default"));
        }

        return aiPrompts.get("default");
    }

    @Override
    public String toString() {
        return String.format("Theme[%s v%s: %s]", name, version, description);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String name;
        private String description = "";
        private String version = "1.0.0";
        private String author = "Unknown";
        private Instant createdAt = Instant.now();
        private ThemeType type = ThemeType.TEXT_STYLE;
        private ThemeScope scope = ThemeScope.ALL;

        private List<TransformRule> rules = new ArrayList<>();
        private Map<String, String> aiPrompts = new LinkedHashMap<>();
        private List<String> targetFilePatterns = new ArrayList<>();
        private Map<String, String> fieldMappings = new LinkedHashMap<>();

        private ThemeSettings settings = ThemeSettings.defaultSettings();
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder type(ThemeType type) {
            this.type = type;
            return this;
        }

        public Builder scope(ThemeScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder addRule(TransformRule rule) {
            this.rules.add(rule);
            return this;
        }

        public Builder addAiPrompt(String fieldName, String prompt) {
            this.aiPrompts.put(fieldName, prompt);
            return this;
        }

        public Builder addTargetFilePattern(String pattern) {
            this.targetFilePatterns.add(pattern);
            return this;
        }

        public Builder addFieldMapping(String from, String to) {
            this.fieldMappings.put(from, to);
            return this;
        }

        public Builder settings(ThemeSettings settings) {
            this.settings = settings;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Theme build() {
            Objects.requireNonNull(name, "Theme name is required");
            return new Theme(this);
        }
    }
}
