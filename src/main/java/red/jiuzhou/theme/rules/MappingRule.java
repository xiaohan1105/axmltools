package red.jiuzhou.theme.rules;

import red.jiuzhou.theme.TransformRule;

import java.util.*;

/**
 * 映射转换规则
 *
 * 使用预定义的映射表直接转换值
 * 适用于枚举、固定值等场景
 *
 * @author Claude
 * @version 1.0
 */
public class MappingRule implements TransformRule {

    private final String name;
    private final String description;
    private final Map<String, String> mappings;
    private final String fieldPattern;
    private final String filePattern;
    private final boolean caseSensitive;
    private final String defaultValue;
    private final int priority;

    private MappingRule(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.mappings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.mappings));
        this.fieldPattern = builder.fieldPattern;
        this.filePattern = builder.filePattern;
        this.caseSensitive = builder.caseSensitive;
        this.defaultValue = builder.defaultValue;
        this.priority = builder.priority;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean matches(String filePath, String fieldName) {
        // 检查文件模式
        if (filePattern != null && !matchesPattern(filePath, filePattern)) {
            return false;
        }

        // 检查字段模式
        if (fieldPattern != null && !matchesPattern(fieldName, fieldPattern)) {
            return false;
        }

        return true;
    }

    @Override
    public String transform(String originalValue, TransformContext context) {
        if (originalValue == null || originalValue.isEmpty()) {
            return originalValue;
        }

        String lookupKey = caseSensitive ? originalValue : originalValue.toLowerCase();
        String mapped = mappings.get(lookupKey);

        if (mapped != null) {
            return mapped;
        }

        // 如果没有找到映射，尝试部分匹配
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (lookupKey.contains(entry.getKey()) || entry.getKey().contains(lookupKey)) {
                return entry.getValue();
            }
        }

        // 返回默认值或原值
        return defaultValue != null ? defaultValue : originalValue;
    }

    @Override
    public boolean validate(String originalValue, String transformedValue) {
        return transformedValue != null && !transformedValue.isEmpty();
    }

    private boolean matchesPattern(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }

        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");

        String target = caseSensitive ? text : text.toLowerCase();
        String regexPattern = caseSensitive ? regex : regex.toLowerCase();

        return target.matches(regexPattern);
    }

    public Map<String, String> getMappings() {
        return mappings;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String description = "";
        private Map<String, String> mappings = new LinkedHashMap<>();
        private String fieldPattern;
        private String filePattern;
        private boolean caseSensitive = false;
        private String defaultValue;
        private int priority = 50;

        Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addMapping(String from, String to) {
            String key = caseSensitive ? from : from.toLowerCase();
            this.mappings.put(key, to);
            return this;
        }

        public Builder addMappings(Map<String, String> mappings) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                addMapping(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder fieldPattern(String pattern) {
            this.fieldPattern = pattern;
            return this;
        }

        public Builder filePattern(String pattern) {
            this.filePattern = pattern;
            return this;
        }

        public Builder caseSensitive(boolean value) {
            this.caseSensitive = value;
            return this;
        }

        public Builder defaultValue(String value) {
            this.defaultValue = value;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public MappingRule build() {
            Objects.requireNonNull(name, "Rule name is required");
            if (mappings.isEmpty()) {
                throw new IllegalStateException("At least one mapping is required");
            }
            return new MappingRule(this);
        }
    }

    @Override
    public String toString() {
        return String.format("MappingRule[%s: %d mappings]", name, mappings.size());
    }
}
