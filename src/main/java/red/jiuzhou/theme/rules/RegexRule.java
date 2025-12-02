package red.jiuzhou.theme.rules;

import red.jiuzhou.theme.TransformRule;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则表达式转换规则
 *
 * 使用正则表达式进行模式匹配和替换
 * 适用于批量替换、格式化等场景
 *
 * @author Claude
 * @version 1.0
 */
public class RegexRule implements TransformRule {

    private final String name;
    private final String description;
    private final Pattern pattern;
    private final String replacement;
    private final String fieldPattern;
    private final String filePattern;
    private final int priority;
    private final boolean replaceAll;

    private RegexRule(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.pattern = Pattern.compile(builder.regex, builder.flags);
        this.replacement = builder.replacement;
        this.fieldPattern = builder.fieldPattern;
        this.filePattern = builder.filePattern;
        this.priority = builder.priority;
        this.replaceAll = builder.replaceAll;
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
        if (filePattern != null && !matchesSimplePattern(filePath, filePattern)) {
            return false;
        }

        // 检查字段模式
        if (fieldPattern != null && !matchesSimplePattern(fieldName, fieldPattern)) {
            return false;
        }

        return true;
    }

    @Override
    public String transform(String originalValue, TransformContext context) {
        if (originalValue == null || originalValue.isEmpty()) {
            return originalValue;
        }

        Matcher matcher = pattern.matcher(originalValue);

        if (replaceAll) {
            return matcher.replaceAll(replacement);
        } else {
            return matcher.replaceFirst(replacement);
        }
    }

    @Override
    public boolean validate(String originalValue, String transformedValue) {
        // 基本验证
        if (transformedValue == null) {
            return false;
        }

        // 检查是否有实际变化
        if (originalValue.equals(transformedValue)) {
            // 如果没有匹配，这是正常的
            return true;
        }

        // 检查长度是否合理
        if (transformedValue.length() > originalValue.length() * 5) {
            return false; // 增长过多可能有问题
        }

        return true;
    }

    private boolean matchesSimplePattern(String text, String simplePattern) {
        if (simplePattern == null || simplePattern.isEmpty()) {
            return true;
        }

        String regex = simplePattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");

        return text.toLowerCase().matches(regex.toLowerCase());
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String description = "";
        private String regex;
        private String replacement = "";
        private String fieldPattern;
        private String filePattern;
        private int flags = 0;
        private int priority = 75;
        private boolean replaceAll = true;

        Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder regex(String regex) {
            this.regex = regex;
            return this;
        }

        public Builder replacement(String replacement) {
            this.replacement = replacement;
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

        public Builder caseInsensitive() {
            this.flags |= Pattern.CASE_INSENSITIVE;
            return this;
        }

        public Builder multiline() {
            this.flags |= Pattern.MULTILINE;
            return this;
        }

        public Builder dotAll() {
            this.flags |= Pattern.DOTALL;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder replaceAll(boolean replaceAll) {
            this.replaceAll = replaceAll;
            return this;
        }

        public RegexRule build() {
            Objects.requireNonNull(name, "Rule name is required");
            Objects.requireNonNull(regex, "Regex pattern is required");
            Objects.requireNonNull(replacement, "Replacement is required");
            return new RegexRule(this);
        }
    }

    @Override
    public String toString() {
        return String.format("RegexRule[%s: /%s/ -> '%s']", name, pattern.pattern(), replacement);
    }
}
