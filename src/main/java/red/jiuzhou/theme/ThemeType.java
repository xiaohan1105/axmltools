package red.jiuzhou.theme;

/**
 * 主题类型枚举
 *
 * 定义不同类型的主题及其应用范围
 *
 * @author Claude
 * @version 1.0
 */
public enum ThemeType {

    /**
     * 文本风格主题
     * 只修改文本内容（名称、描述等），保留所有数值
     */
    TEXT_STYLE("文本风格", "只转换文本内容，保持数值不变"),

    /**
     * 完整主题
     * 修改文本、数值、逻辑等所有内容
     */
    COMPLETE("完整主题", "转换所有内容，包括文本、数值和逻辑"),

    /**
     * 视觉主题
     * 专注于UI、特效、外观描述
     */
    VISUAL("视觉主题", "专注于UI、特效、外观等视觉元素"),

    /**
     * 混合主题
     * 组合多个子主题
     */
    MIXED("混合主题", "组合多个主题的特性");

    private final String displayName;
    private final String description;

    ThemeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
