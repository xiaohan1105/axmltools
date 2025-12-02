package red.jiuzhou.analysis.aion;

import java.util.regex.Pattern;

/**
 * Aion游戏机制分类枚举
 *
 * <p>定义27个Aion特有的游戏系统分类，用于精确识别XML配置文件所属的业务模块。
 *
 * <p><b>分类优先级规则:</b>
 * <ol>
 *   <li>文件夹级别匹配（AnimationMarkers、Custompreset等）</li>
 *   <li>精确文件名匹配</li>
 *   <li>正则模式匹配（Aion特有系统优先）</li>
 * </ol>
 *
 * @author Claude
 * @version 1.0
 */
public enum AionMechanismCategory {

    // ========== Aion特有系统（高优先级）==========

    ABYSS("深渊系统", "深渊点数、深渊排名、要塞战等PvPvE核心系统",
            Pattern.compile("(?i)^(abyss|siege|fortress|artifact).*"),
            10),

    LUNA("Luna货币", "Luna商店、Luna抽卡、Luna消费等虚拟货币系统",
            Pattern.compile("(?i)^luna_.*"),
            10),

    HOUSING("房屋系统", "房屋建造、家具、房屋商店等玩家住宅系统",
            Pattern.compile("(?i)^housing_.*"),
            10),

    STIGMA_TRANSFORM("烙印变身", "烙印石、变身系统等角色增强机制",
            Pattern.compile("(?i).*(stigma|transform).*"),
            9),

    PET("宠物系统", "宠物、坐骑、召唤物等随从系统",
            Pattern.compile("(?i).*(toypet|pet_|familiar|mount).*"),
            9),

    // ========== 核心战斗系统 ==========

    SKILL("技能系统", "技能配置、技能学习、德瓦尼恩技能等",
            Pattern.compile("(?i)^(skill_|devanion_skill|passiveskill).*"),
            8),

    NPC_AI("NPC AI系统", "NPC行为模式、AI决策逻辑",
            Pattern.compile("(?i)^NpcAIPattern.*"),
            10),  // 高优先级，避免被NPC吞掉

    NPC("NPC系统", "NPC定义、怪物、BOSS等非玩家角色",
            Pattern.compile("(?i)^(npc|monster|spawn).*"),
            7),

    ITEM("物品系统", "物品定义、装备、消耗品等",
            Pattern.compile("(?i)^(item_|items).*"),
            7),

    ENCHANT("强化系统", "装备强化、附魔、升级等",
            Pattern.compile("(?i).*(enchant|upgrade|tuning).*"),
            8),

    // ========== 游戏内容系统 ==========

    QUEST("任务系统", "主线任务、支线任务、每日任务等",
            Pattern.compile("(?i)^(quest|challenge_task|work_order).*"),
            7),

    INSTANCE("副本系统", "副本配置、副本冷却、难度设定等",
            Pattern.compile("(?i)^(instance_|instant_dungeon).*"),
            8),

    DROP("掉落系统", "掉落表、奖励配置、宝箱等",
            Pattern.compile("(?i).*(drop|loot|treasure|chest).*"),
            7),

    CRAFT("制作系统", "合成配方、制作材料、分解等",
            Pattern.compile("(?i).*(combine|recipe|assembly|disassembly|extract).*"),
            7),

    // ========== 经济系统 ==========

    SHOP("商店交易", "商店列表、NPC商店、交易配置等",
            Pattern.compile("(?i).*(goodslist|shop|purchase|trade).*"),
            7),

    GOTCHA("抽卡系统", "抽卡配置、概率表、奖池等",
            Pattern.compile("(?i).*gotcha.*"),
            9),

    // ========== 角色成长系统 ==========

    PLAYER_GROWTH("角色成长", "经验表、等级提升、属性成长等",
            Pattern.compile("(?i).*(pcexp|player_exp|boost_time|level_up).*"),
            8),

    TITLE("称号系统", "称号获取、称号属性、称号展示等",
            Pattern.compile("(?i)^title.*"),
            8),

    // ========== 社交系统 ==========

    LEGION("军团系统", "军团创建、军团领地、军团战等",
            Pattern.compile("(?i).*(legion|guild|clan).*"),
            7),

    PVP_RANKING("PVP与排名", "PVP排名、竞技场、战场等",
            Pattern.compile("(?i).*(pvp_|ranking|arena|battleground).*"),
            7),

    // ========== 世界系统 ==========

    PORTAL("传送系统", "传送门、飞行路线、瞬移点等",
            Pattern.compile("(?i).*(portal|fly_path|teleport|rift).*"),
            7),

    TIME_EVENT("时间事件", "登录奖励、限时活动、节日事件等",
            Pattern.compile("(?i).*(_times\\.xml$|login_event|event_|schedule).*"),
            7),

    // ========== 客户端资源 ==========

    CLIENT_STRINGS("客户端字符串", "UI文本、物品名称、技能描述等本地化字符串",
            Pattern.compile("(?i)^client_strings_.*"),
            9),

    // ========== 文件夹级别分类（通过detectByFolder处理）==========

    ANIMATION("动画系统", "动画配置文件（Animations文件夹）",
            Pattern.compile("(?i)^animation.*"),
            5),

    ANIMATION_MARKERS("动画标记", "动画标记数据（AnimationMarkers文件夹，4000+文件）",
            Pattern.compile("$^"),  // 不通过正则匹配，仅通过文件夹匹配
            0),

    CHARACTER_PRESET("角色预设", "角色外观预设（Custompreset文件夹，1200+文件）",
            Pattern.compile("$^"),
            0),

    SUBZONE("副本区域", "副本区域配置（Subzones文件夹，300+文件）",
            Pattern.compile("$^"),
            0),

    // ========== 配置与映射 ==========

    ID_MAPPING("ID映射表", "各类ID映射表（物品ID、NPC ID等）",
            Pattern.compile("(?i).*_id\\.xml$"),
            6),

    GAME_CONFIG("游戏配置", "全局游戏配置、常量定义等",
            Pattern.compile("(?i).*(config|setting|constant|param|global).*"),
            5),

    // ========== 兜底分类 ==========

    OTHER("其他", "未分类的XML配置文件",
            Pattern.compile(".*"),
            0);

    private final String displayName;
    private final String description;
    private final Pattern pattern;
    private final int priority;  // 优先级，数字越大越优先匹配

    AionMechanismCategory(String displayName, String description, Pattern pattern, int priority) {
        this.displayName = displayName;
        this.description = description;
        this.pattern = pattern;
        this.priority = priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 检查文件名是否匹配当前分类
     *
     * @param fileName 文件名（不含路径）
     * @return 是否匹配
     */
    public boolean matches(String fileName) {
        return pattern.matcher(fileName).matches();
    }

    /**
     * 获取分类的图标字符（用于UI显示）
     */
    public String getIcon() {
        switch (this) {
            case ABYSS: return "A";
            case SKILL: return "S";
            case ITEM: return "I";
            case NPC: return "N";
            case NPC_AI: return "AI";
            case QUEST: return "Q";
            case INSTANCE: return "D";
            case SHOP: return "$";
            case LUNA: return "L";
            case HOUSING: return "H";
            case PET: return "P";
            case CRAFT: return "C";
            case DROP: return "D";
            case ENCHANT: return "E";
            case STIGMA_TRANSFORM: return "T";
            case PLAYER_GROWTH: return "G";
            case TITLE: return "T";
            case LEGION: return "L";
            case PVP_RANKING: return "R";
            case PORTAL: return "P";
            case TIME_EVENT: return "E";
            case CLIENT_STRINGS: return "S";
            case ANIMATION: return "A";
            case ANIMATION_MARKERS: return "M";
            case CHARACTER_PRESET: return "C";
            case SUBZONE: return "Z";
            case ID_MAPPING: return "#";
            case GAME_CONFIG: return "C";
            case GOTCHA: return "G";
            default: return "?";
        }
    }

    /**
     * 获取分类的颜色（用于UI显示，返回CSS颜色值）
     */
    public String getColor() {
        switch (this) {
            case ABYSS: return "#8B0000";        // 深红色 - 深渊
            case SKILL: return "#4169E1";        // 皇家蓝 - 技能
            case ITEM: return "#DAA520";         // 金色 - 物品
            case NPC: return "#228B22";          // 森林绿 - NPC
            case NPC_AI: return "#006400";       // 深绿色 - AI
            case QUEST: return "#FF8C00";        // 深橙色 - 任务
            case INSTANCE: return "#800080";     // 紫色 - 副本
            case SHOP: return "#FFD700";         // 金色 - 商店
            case LUNA: return "#E6E6FA";         // 淡紫色 - Luna
            case HOUSING: return "#8B4513";      // 棕色 - 房屋
            case PET: return "#FF69B4";          // 粉色 - 宠物
            case CRAFT: return "#CD853F";        // 秘鲁色 - 制作
            case DROP: return "#32CD32";         // 酸橙绿 - 掉落
            case ENCHANT: return "#00CED1";      // 深青色 - 强化
            case STIGMA_TRANSFORM: return "#9400D3"; // 深紫色 - 烙印变身
            case PLAYER_GROWTH: return "#00FF7F"; // 春绿色 - 成长
            case TITLE: return "#FFB6C1";        // 浅粉色 - 称号
            case LEGION: return "#4682B4";       // 钢蓝色 - 军团
            case PVP_RANKING: return "#DC143C";  // 猩红色 - PVP
            case PORTAL: return "#00BFFF";       // 深天蓝 - 传送
            case TIME_EVENT: return "#FF4500";   // 橙红色 - 时间事件
            case CLIENT_STRINGS: return "#708090"; // 石板灰 - 字符串
            case ANIMATION: return "#9370DB";    // 中紫色 - 动画
            case ANIMATION_MARKERS: return "#BA55D3"; // 中兰花紫 - 动画标记
            case CHARACTER_PRESET: return "#DDA0DD"; // 梅红色 - 预设
            case SUBZONE: return "#20B2AA";      // 浅海绿 - 副本区域
            case ID_MAPPING: return "#778899";   // 浅石板灰 - ID映射
            case GAME_CONFIG: return "#696969";  // 暗灰色 - 配置
            case GOTCHA: return "#FF1493";       // 深粉色 - 抽卡
            default: return "#A9A9A9";           // 深灰色 - 其他
        }
    }
}
