package red.jiuzhou.theme;

/**
 * 主题应用范围
 *
 * 定义主题可以应用到哪些游戏内容类型
 *
 * @author Claude
 * @version 1.0
 */
public enum ThemeScope {

    /**
     * 应用到所有内容
     */
    ALL("全部", "应用到所有游戏内容"),

    /**
     * 仅物品/道具
     */
    ITEMS("物品", "武器、装备、消耗品等道具系统"),

    /**
     * 仅NPC/怪物
     */
    NPCS("NPC", "NPC、怪物、宠物等角色系统"),

    /**
     * 仅技能
     */
    SKILLS("技能", "主动技能、被动技能、Buff等"),

    /**
     * 仅任务
     */
    QUESTS("任务", "主线任务、支线任务、日常任务"),

    /**
     * 仅地图/场景
     */
    MAPS("地图", "地图、场景、区域描述"),

    /**
     * 仅对话/剧情
     */
    DIALOGS("对话", "NPC对话、剧情文本"),

    /**
     * 仅UI文本
     */
    UI("界面", "UI界面、按钮、提示文本"),

    /**
     * 自定义范围
     */
    CUSTOM("自定义", "自定义文件范围");

    private final String displayName;
    private final String description;

    ThemeScope(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 检查文件路径是否匹配此范围
     */
    public boolean matchesPath(String filePath) {
        if (this == ALL) {
            return true;
        }

        String lowerPath = filePath.toLowerCase();

        switch (this) {
            case ITEMS:
                return lowerPath.contains("item") || lowerPath.contains("weapon") || lowerPath.contains("armor");
            case NPCS:
                return lowerPath.contains("npc") || lowerPath.contains("monster") || lowerPath.contains("mob");
            case SKILLS:
                return lowerPath.contains("skill") || lowerPath.contains("ability") || lowerPath.contains("buff");
            case QUESTS:
                return lowerPath.contains("quest") || lowerPath.contains("mission");
            case MAPS:
                return lowerPath.contains("map") || lowerPath.contains("zone") || lowerPath.contains("area");
            case DIALOGS:
                return lowerPath.contains("dialog") || lowerPath.contains("conversation") || lowerPath.contains("story");
            case UI:
                return lowerPath.contains("ui") || lowerPath.contains("string") || lowerPath.contains("text");
            case CUSTOM:
                return false; // 自定义范围需要额外配置
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
