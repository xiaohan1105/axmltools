package red.jiuzhou.analysis.aion;

/**
 * 机制检测结果
 *
 * <p>包含检测到的机制分类、置信度、识别原因等信息。
 *
 * @author Claude
 * @version 1.0
 */
public class DetectionResult {

    private final AionMechanismCategory category;
    private final double confidence;
    private final String reasoning;
    private final boolean localized;
    private final String relativePath;  // 相对于根目录的路径

    private DetectionResult(Builder builder) {
        this.category = builder.category;
        this.confidence = builder.confidence;
        this.reasoning = builder.reasoning;
        this.localized = builder.localized;
        this.relativePath = builder.relativePath;
    }

    public AionMechanismCategory getCategory() {
        return category;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public boolean isLocalized() {
        return localized;
    }

    public String getRelativePath() {
        return relativePath;
    }

    /**
     * 获取置信度等级描述
     */
    public String getConfidenceLevel() {
        if (confidence >= 0.9) {
            return "高";
        } else if (confidence >= 0.7) {
            return "中";
        } else if (confidence >= 0.5) {
            return "低";
        } else {
            return "猜测";
        }
    }

    /**
     * 获取带本地化标记的分类显示名
     */
    public String getDisplayName() {
        String name = category.getDisplayName();
        if (localized) {
            return name + " [本地化]";
        }
        return name;
    }

    @Override
    public String toString() {
        return String.format("DetectionResult{category=%s, confidence=%.2f, localized=%s, path=%s}",
                category.getDisplayName(), confidence, localized, relativePath);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AionMechanismCategory category = AionMechanismCategory.OTHER;
        private double confidence = 0.5;
        private String reasoning = "";
        private boolean localized = false;
        private String relativePath = "";

        public Builder category(AionMechanismCategory category) {
            this.category = category;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning != null ? reasoning : "";
            return this;
        }

        public Builder localized(boolean localized) {
            this.localized = localized;
            return this;
        }

        public Builder relativePath(String relativePath) {
            this.relativePath = relativePath != null ? relativePath : "";
            return this;
        }

        public DetectionResult build() {
            return new DetectionResult(this);
        }
    }
}
