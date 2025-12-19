package red.jiuzhou.ui.features;

/**
 * High level grouping for launchable application features.
 */
public enum FeatureCategory {

    ANALYTICS("策划洞察"),
    DESIGN_SYSTEM("主题与视觉"),
    AI_ASSISTANT("智能助手"),
    DOMAIN_EDITOR("领域编辑器"),
    GAME_TOOLS("游戏工具");

    private final String displayName;

    FeatureCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
