package red.jiuzhou.analysis.aion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 机制-文件映射服务
 *
 * 建立游戏机制分类与XML文件的双向映射关系：
 * - 机制 → 文件列表（按机制过滤目录树）
 * - 文件 → 机制（显示文件所属机制标签）
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class MechanismFileMapper {

    private static final Logger log = LoggerFactory.getLogger(MechanismFileMapper.class);

    /** 单例实例 */
    private static MechanismFileMapper instance;

    /** 文件路径 → 机制分类 缓存 */
    private final Map<String, AionMechanismCategory> fileToMechanism = new ConcurrentHashMap<>();

    /** 机制分类 → 文件路径集合 缓存 */
    private final Map<AionMechanismCategory, Set<String>> mechanismToFiles = new ConcurrentHashMap<>();

    /** 已扫描的根目录 */
    private final Set<String> scannedRoots = new HashSet<>();

    /** 常用机制（显示在标签栏） */
    private static final List<AionMechanismCategory> COMMON_MECHANISMS = Arrays.asList(
        AionMechanismCategory.ITEM,
        AionMechanismCategory.NPC,
        AionMechanismCategory.SKILL,
        AionMechanismCategory.QUEST,
        AionMechanismCategory.DROP,
        AionMechanismCategory.INSTANCE,
        AionMechanismCategory.SHOP,
        AionMechanismCategory.CRAFT,
        AionMechanismCategory.ABYSS,
        AionMechanismCategory.PET,
        AionMechanismCategory.ENCHANT,
        AionMechanismCategory.CLIENT_STRINGS
    );

    /** 文件夹级别映射（优先级最高） */
    private static final Map<String, AionMechanismCategory> FOLDER_MAPPINGS = new HashMap<>();

    /** 精确文件名映射（次高优先级） */
    private static final Map<String, AionMechanismCategory> EXACT_FILE_MAPPINGS = new HashMap<>();

    static {
        // 文件夹映射
        FOLDER_MAPPINGS.put("animationmarkers", AionMechanismCategory.ANIMATION_MARKERS);
        FOLDER_MAPPINGS.put("animations", AionMechanismCategory.ANIMATION);
        FOLDER_MAPPINGS.put("custompreset", AionMechanismCategory.CHARACTER_PRESET);
        FOLDER_MAPPINGS.put("subzones", AionMechanismCategory.SUBZONE);
        FOLDER_MAPPINGS.put("id", AionMechanismCategory.ID_MAPPING);
        FOLDER_MAPPINGS.put("special01", AionMechanismCategory.GAME_CONFIG);
        FOLDER_MAPPINGS.put("worlds", AionMechanismCategory.NPC); // World目录下主要是NPC刷怪

        // 精确文件名映射
        EXACT_FILE_MAPPINGS.put("abyss.xml", AionMechanismCategory.ABYSS);
        EXACT_FILE_MAPPINGS.put("abyss_rank.xml", AionMechanismCategory.ABYSS);
        EXACT_FILE_MAPPINGS.put("abyss_rank_points.xml", AionMechanismCategory.ABYSS);
        EXACT_FILE_MAPPINGS.put("siege_locations.xml", AionMechanismCategory.ABYSS);

        EXACT_FILE_MAPPINGS.put("npcs.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("npc_shouts.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("npc_walkers.xml", AionMechanismCategory.NPC);

        EXACT_FILE_MAPPINGS.put("items.xml", AionMechanismCategory.ITEM);
        EXACT_FILE_MAPPINGS.put("item_sets.xml", AionMechanismCategory.ITEM);
        EXACT_FILE_MAPPINGS.put("item_random_bonus.xml", AionMechanismCategory.ITEM);

        EXACT_FILE_MAPPINGS.put("skills.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_trees.xml", AionMechanismCategory.SKILL);

        EXACT_FILE_MAPPINGS.put("quests.xml", AionMechanismCategory.QUEST);
        EXACT_FILE_MAPPINGS.put("quest_data.xml", AionMechanismCategory.QUEST);

        EXACT_FILE_MAPPINGS.put("goodslists.xml", AionMechanismCategory.SHOP);
        EXACT_FILE_MAPPINGS.put("merchants.xml", AionMechanismCategory.SHOP);

        EXACT_FILE_MAPPINGS.put("toypets.xml", AionMechanismCategory.PET);
        EXACT_FILE_MAPPINGS.put("familiars.xml", AionMechanismCategory.PET);

        EXACT_FILE_MAPPINGS.put("recipes.xml", AionMechanismCategory.CRAFT);
        EXACT_FILE_MAPPINGS.put("assembly.xml", AionMechanismCategory.CRAFT);

        EXACT_FILE_MAPPINGS.put("titles.xml", AionMechanismCategory.TITLE);
        EXACT_FILE_MAPPINGS.put("portals.xml", AionMechanismCategory.PORTAL);
        EXACT_FILE_MAPPINGS.put("fly_paths.xml", AionMechanismCategory.PORTAL);
        EXACT_FILE_MAPPINGS.put("instances.xml", AionMechanismCategory.INSTANCE);
        EXACT_FILE_MAPPINGS.put("gotchas.xml", AionMechanismCategory.GOTCHA);
        EXACT_FILE_MAPPINGS.put("game_config.xml", AionMechanismCategory.GAME_CONFIG);
        EXACT_FILE_MAPPINGS.put("global_config.xml", AionMechanismCategory.GAME_CONFIG);
    }

    private MechanismFileMapper() {
        // 初始化每个机制的文件集合
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            mechanismToFiles.put(category, ConcurrentHashMap.newKeySet());
        }
    }

    /**
     * 获取单例实例
     */
    public static synchronized MechanismFileMapper getInstance() {
        if (instance == null) {
            instance = new MechanismFileMapper();
        }
        return instance;
    }

    /**
     * 扫描目录并建立映射
     */
    public void scanDirectory(String rootPath) {
        if (rootPath == null || rootPath.isEmpty()) return;

        File root = new File(rootPath);
        if (!root.exists() || !root.isDirectory()) return;

        String normalizedPath = root.getAbsolutePath().toLowerCase();
        if (scannedRoots.contains(normalizedPath)) {
            log.debug("目录已扫描过: {}", rootPath);
            return;
        }

        log.info("开始扫描目录建立机制映射: {}", rootPath);
        long start = System.currentTimeMillis();

        scanRecursively(root, null);

        scannedRoots.add(normalizedPath);
        log.info("目录扫描完成: {} 个文件, 耗时 {}ms",
            fileToMechanism.size(), System.currentTimeMillis() - start);
    }

    /**
     * 递归扫描目录
     */
    private void scanRecursively(File dir, AionMechanismCategory parentCategory) {
        if (dir == null || !dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        // 检查当前目录是否有文件夹级别映射
        String dirName = dir.getName().toLowerCase();
        AionMechanismCategory folderCategory = FOLDER_MAPPINGS.get(dirName);
        if (folderCategory != null) {
            parentCategory = folderCategory;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanRecursively(file, parentCategory);
            } else if (file.getName().toLowerCase().endsWith(".xml")) {
                AionMechanismCategory category = detectMechanism(file, parentCategory);
                registerFile(file.getAbsolutePath(), category);
            }
        }
    }

    /**
     * 检测文件所属机制
     */
    private AionMechanismCategory detectMechanism(File file, AionMechanismCategory parentCategory) {
        String fileName = file.getName().toLowerCase();

        // 1. 精确文件名匹配（最高优先级）
        AionMechanismCategory exact = EXACT_FILE_MAPPINGS.get(fileName);
        if (exact != null) {
            return exact;
        }

        // 2. 继承父目录机制（文件夹级别映射）
        if (parentCategory != null) {
            return parentCategory;
        }

        // 3. 正则模式匹配（按优先级排序）
        List<AionMechanismCategory> sortedCategories = new ArrayList<>(Arrays.asList(AionMechanismCategory.values()));
        sortedCategories.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (AionMechanismCategory category : sortedCategories) {
            if (category == AionMechanismCategory.OTHER) continue;

            Pattern pattern = category.getPattern();
            if (pattern != null && pattern.matcher(fileName).matches()) {
                return category;
            }
        }

        // 4. 兜底分类
        return AionMechanismCategory.OTHER;
    }

    /**
     * 注册文件映射
     */
    private void registerFile(String filePath, AionMechanismCategory category) {
        fileToMechanism.put(filePath.toLowerCase(), category);
        mechanismToFiles.get(category).add(filePath);
    }

    /**
     * 获取文件所属机制
     */
    public AionMechanismCategory getMechanism(String filePath) {
        if (filePath == null) return AionMechanismCategory.OTHER;
        return fileToMechanism.getOrDefault(filePath.toLowerCase(), AionMechanismCategory.OTHER);
    }

    /**
     * 获取机制下的所有文件
     */
    public Set<String> getFiles(AionMechanismCategory category) {
        return mechanismToFiles.getOrDefault(category, Collections.emptySet());
    }

    /**
     * 检查文件是否属于指定机制
     */
    public boolean belongsTo(String filePath, AionMechanismCategory category) {
        return getMechanism(filePath) == category;
    }

    /**
     * 获取常用机制列表（用于标签栏显示）
     */
    public List<AionMechanismCategory> getCommonMechanisms() {
        return COMMON_MECHANISMS;
    }

    /**
     * 获取所有机制及其文件数量统计
     */
    public Map<AionMechanismCategory, Integer> getMechanismStats() {
        Map<AionMechanismCategory, Integer> stats = new LinkedHashMap<>();
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            int count = mechanismToFiles.get(category).size();
            if (count > 0) {
                stats.put(category, count);
            }
        }
        return stats;
    }

    /**
     * 按机制过滤文件路径
     */
    public boolean matchesMechanism(String filePath, AionMechanismCategory category) {
        if (category == null) return true; // null表示不过滤
        return getMechanism(filePath) == category;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        fileToMechanism.clear();
        for (Set<String> files : mechanismToFiles.values()) {
            files.clear();
        }
        scannedRoots.clear();
        log.info("机制映射缓存已清除");
    }

    /**
     * 获取机制的显示信息
     */
    public static class MechanismInfo {
        private final AionMechanismCategory category;
        private final int fileCount;

        public MechanismInfo(AionMechanismCategory category, int fileCount) {
            this.category = category;
            this.fileCount = fileCount;
        }

        public AionMechanismCategory getCategory() { return category; }
        public int getFileCount() { return fileCount; }
        public String getDisplayName() { return category.getDisplayName(); }
        public String getColor() { return category.getColor(); }
        public String getIcon() { return category.getIcon(); }
    }

    /**
     * 获取机制信息列表（用于UI显示）
     */
    public List<MechanismInfo> getMechanismInfoList() {
        List<MechanismInfo> list = new ArrayList<>();
        for (AionMechanismCategory category : COMMON_MECHANISMS) {
            int count = mechanismToFiles.get(category).size();
            list.add(new MechanismInfo(category, count));
        }
        return list;
    }

    /**
     * 检测文件路径的机制（静态方法，不依赖缓存）
     */
    public static AionMechanismCategory detectMechanismStatic(String filePath) {
        if (filePath == null) return AionMechanismCategory.OTHER;

        File file = new File(filePath);
        String fileName = file.getName().toLowerCase();

        // 精确匹配
        AionMechanismCategory exact = EXACT_FILE_MAPPINGS.get(fileName);
        if (exact != null) return exact;

        // 检查父目录
        File parent = file.getParentFile();
        while (parent != null) {
            String dirName = parent.getName().toLowerCase();
            AionMechanismCategory folderCategory = FOLDER_MAPPINGS.get(dirName);
            if (folderCategory != null) return folderCategory;
            parent = parent.getParentFile();
        }

        // 正则匹配
        List<AionMechanismCategory> sortedCategories = new ArrayList<>(Arrays.asList(AionMechanismCategory.values()));
        sortedCategories.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (AionMechanismCategory category : sortedCategories) {
            if (category == AionMechanismCategory.OTHER) continue;
            Pattern pattern = category.getPattern();
            if (pattern != null && pattern.matcher(fileName).matches()) {
                return category;
            }
        }

        return AionMechanismCategory.OTHER;
    }
}
