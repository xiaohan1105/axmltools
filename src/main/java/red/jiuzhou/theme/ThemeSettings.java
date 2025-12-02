package red.jiuzhou.theme;

import java.util.*;

/**
 * 主题应用设置
 *
 * 控制主题如何应用到实际数据
 *
 * @author Claude
 * @version 1.0
 */
public class ThemeSettings {

    // 转换设置
    private final boolean preserveNumericValues;  // 保留数值
    private final boolean preserveIds;            // 保留ID
    private final boolean preserveReferences;     // 保留引用关系
    private final boolean useAiTransform;         // 使用AI转换
    private final String aiModel;                 // AI模型名称

    // 应用设置
    private final boolean backupBeforeApply;      // 应用前备份
    private final boolean validateAfterApply;     // 应用后验证
    private final boolean transactional;          // 事务性应用
    private final int maxConcurrency;             // 最大并发数

    // 质量控制
    private final double minConfidenceThreshold;  // 最低置信度阈值
    private final boolean skipOnError;            // 出错时跳过
    private final boolean collectErrors;          // 收集错误信息

    // 缓存设置
    private final boolean cacheAiResults;         // 缓存AI结果
    private final int cacheSizeLimit;             // 缓存大小限制

    // 高级设置
    private final Map<String, Object> customSettings;

    private ThemeSettings(Builder builder) {
        this.preserveNumericValues = builder.preserveNumericValues;
        this.preserveIds = builder.preserveIds;
        this.preserveReferences = builder.preserveReferences;
        this.useAiTransform = builder.useAiTransform;
        this.aiModel = builder.aiModel;
        this.backupBeforeApply = builder.backupBeforeApply;
        this.validateAfterApply = builder.validateAfterApply;
        this.transactional = builder.transactional;
        this.maxConcurrency = builder.maxConcurrency;
        this.minConfidenceThreshold = builder.minConfidenceThreshold;
        this.skipOnError = builder.skipOnError;
        this.collectErrors = builder.collectErrors;
        this.cacheAiResults = builder.cacheAiResults;
        this.cacheSizeLimit = builder.cacheSizeLimit;
        this.customSettings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.customSettings));
    }

    // Getters
    public boolean isPreserveNumericValues() { return preserveNumericValues; }
    public boolean isPreserveIds() { return preserveIds; }
    public boolean isPreserveReferences() { return preserveReferences; }
    public boolean isUseAiTransform() { return useAiTransform; }
    public String getAiModel() { return aiModel; }
    public boolean isBackupBeforeApply() { return backupBeforeApply; }
    public boolean isValidateAfterApply() { return validateAfterApply; }
    public boolean isTransactional() { return transactional; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public double getMinConfidenceThreshold() { return minConfidenceThreshold; }
    public boolean isSkipOnError() { return skipOnError; }
    public boolean isCollectErrors() { return collectErrors; }
    public boolean isCacheAiResults() { return cacheAiResults; }
    public int getCacheSizeLimit() { return cacheSizeLimit; }
    public Map<String, Object> getCustomSettings() { return customSettings; }

    public Object getCustomSetting(String key) {
        return customSettings.get(key);
    }

    /**
     * 创建默认设置
     */
    public static ThemeSettings defaultSettings() {
        return builder()
                .preserveNumericValues(true)
                .preserveIds(true)
                .preserveReferences(true)
                .useAiTransform(true)
                .aiModel("qwen-plus")
                .backupBeforeApply(true)
                .validateAfterApply(true)
                .transactional(true)
                .maxConcurrency(4)
                .minConfidenceThreshold(0.7)
                .skipOnError(false)
                .collectErrors(true)
                .cacheAiResults(true)
                .cacheSizeLimit(1000)
                .build();
    }

    /**
     * 创建快速模式设置（性能优先）
     */
    public static ThemeSettings fastMode() {
        return builder()
                .preserveNumericValues(true)
                .preserveIds(true)
                .preserveReferences(true)
                .useAiTransform(true)
                .aiModel("qwen-turbo")
                .backupBeforeApply(false)
                .validateAfterApply(false)
                .transactional(false)
                .maxConcurrency(8)
                .minConfidenceThreshold(0.5)
                .skipOnError(true)
                .collectErrors(false)
                .cacheAiResults(true)
                .cacheSizeLimit(5000)
                .build();
    }

    /**
     * 创建安全模式设置（质量优先）
     */
    public static ThemeSettings safeMode() {
        return builder()
                .preserveNumericValues(true)
                .preserveIds(true)
                .preserveReferences(true)
                .useAiTransform(true)
                .aiModel("qwen-plus")
                .backupBeforeApply(true)
                .validateAfterApply(true)
                .transactional(true)
                .maxConcurrency(2)
                .minConfidenceThreshold(0.9)
                .skipOnError(false)
                .collectErrors(true)
                .cacheAiResults(true)
                .cacheSizeLimit(500)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean preserveNumericValues = true;
        private boolean preserveIds = true;
        private boolean preserveReferences = true;
        private boolean useAiTransform = true;
        private String aiModel = "qwen-plus";
        private boolean backupBeforeApply = true;
        private boolean validateAfterApply = true;
        private boolean transactional = true;
        private int maxConcurrency = 4;
        private double minConfidenceThreshold = 0.7;
        private boolean skipOnError = false;
        private boolean collectErrors = true;
        private boolean cacheAiResults = true;
        private int cacheSizeLimit = 1000;
        private Map<String, Object> customSettings = new LinkedHashMap<>();

        public Builder preserveNumericValues(boolean value) {
            this.preserveNumericValues = value;
            return this;
        }

        public Builder preserveIds(boolean value) {
            this.preserveIds = value;
            return this;
        }

        public Builder preserveReferences(boolean value) {
            this.preserveReferences = value;
            return this;
        }

        public Builder useAiTransform(boolean value) {
            this.useAiTransform = value;
            return this;
        }

        public Builder aiModel(String model) {
            this.aiModel = model;
            return this;
        }

        public Builder backupBeforeApply(boolean value) {
            this.backupBeforeApply = value;
            return this;
        }

        public Builder validateAfterApply(boolean value) {
            this.validateAfterApply = value;
            return this;
        }

        public Builder transactional(boolean value) {
            this.transactional = value;
            return this;
        }

        public Builder maxConcurrency(int value) {
            this.maxConcurrency = value;
            return this;
        }

        public Builder minConfidenceThreshold(double value) {
            this.minConfidenceThreshold = value;
            return this;
        }

        public Builder skipOnError(boolean value) {
            this.skipOnError = value;
            return this;
        }

        public Builder collectErrors(boolean value) {
            this.collectErrors = value;
            return this;
        }

        public Builder cacheAiResults(boolean value) {
            this.cacheAiResults = value;
            return this;
        }

        public Builder cacheSizeLimit(int value) {
            this.cacheSizeLimit = value;
            return this;
        }

        public Builder addCustomSetting(String key, Object value) {
            this.customSettings.put(key, value);
            return this;
        }

        public ThemeSettings build() {
            return new ThemeSettings(this);
        }
    }

    @Override
    public String toString() {
        return String.format("ThemeSettings[model=%s, concurrent=%d, transactional=%b]",
                aiModel, maxConcurrency, transactional);
    }
}
