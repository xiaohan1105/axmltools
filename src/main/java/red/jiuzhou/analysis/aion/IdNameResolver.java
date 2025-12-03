package red.jiuzhou.analysis.aion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * ID到NAME解析服务
 *
 * <p>用于将游戏数据中的ID引用转换为可读的NAME标签，
 * 为游戏设计师提供直观的数据展示。
 *
 * <p>支持的系统：
 * <ul>
 *   <li>物品系统 - item_id → 物品名称</li>
 *   <li>NPC系统 - npc_id → NPC名称</li>
 *   <li>技能系统 - skill_id → 技能名称</li>
 *   <li>任务系统 - quest_id → 任务名称</li>
 *   <li>副本系统 - instance_id → 副本名称</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class IdNameResolver {

    private static final Logger log = LoggerFactory.getLogger(IdNameResolver.class);

    // 单例实例
    private static volatile IdNameResolver instance;

    // 系统映射缓存: 系统名 -> (ID -> NAME)
    private final Map<String, Map<String, String>> systemCache = new ConcurrentHashMap<>();

    // 缓存加载时间戳
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    // 缓存有效期（5分钟）
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    // 系统配置: 系统名 -> [表名, ID列, NAME列]
    private static final Map<String, String[]> SYSTEM_CONFIG = new LinkedHashMap<>();

    // ID字段模式匹配
    private static final Map<Pattern, String> FIELD_PATTERNS = new LinkedHashMap<>();

    static {
        // 配置各系统的表和字段映射
        // 格式: 系统名 -> [表名, ID列, NAME列]
        SYSTEM_CONFIG.put("物品系统", new String[]{"client_items", "id", "name"});
        SYSTEM_CONFIG.put("NPC系统", new String[]{"client_npcs_npc", "id", "name"});
        SYSTEM_CONFIG.put("技能系统", new String[]{"client_skills", "id", "name"});
        SYSTEM_CONFIG.put("任务系统", new String[]{"client_quests", "id", "name"});
        SYSTEM_CONFIG.put("副本系统", new String[]{"instance_cooltime", "id", "name"});
        SYSTEM_CONFIG.put("地图系统", new String[]{"world", "id", "name"});
        SYSTEM_CONFIG.put("称号系统", new String[]{"client_titles", "id", "name"});
        SYSTEM_CONFIG.put("宠物系统", new String[]{"client_toypets", "id", "name"});
        SYSTEM_CONFIG.put("坐骑系统", new String[]{"client_rides", "id", "name"});

        // 配置字段名模式到系统的映射
        FIELD_PATTERNS.put(Pattern.compile("(?i).*item[_]?id.*"), "物品系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*npc[_]?id.*"), "NPC系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*monster[_]?id.*"), "NPC系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*skill[_]?id.*"), "技能系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*quest[_]?id.*"), "任务系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*instance[_]?id.*"), "副本系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*map[_]?id.*"), "地图系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*world[_]?id.*"), "地图系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*title[_]?id.*"), "称号系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*pet[_]?id.*"), "宠物系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*ride[_]?id.*"), "坐骑系统");
        FIELD_PATTERNS.put(Pattern.compile("(?i).*mount[_]?id.*"), "坐骑系统");
    }

    private IdNameResolver() {
        // 私有构造函数
    }

    /**
     * 获取单例实例
     */
    public static IdNameResolver getInstance() {
        if (instance == null) {
            synchronized (IdNameResolver.class) {
                if (instance == null) {
                    instance = new IdNameResolver();
                }
            }
        }
        return instance;
    }

    /**
     * 根据字段名判断所属系统
     *
     * @param fieldName 字段名
     * @return 系统名，如果无法识别返回null
     */
    public String detectSystem(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        for (Map.Entry<Pattern, String> entry : FIELD_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(fieldName).matches()) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 检查字段是否是ID引用字段
     *
     * @param fieldName 字段名
     * @return 是否为ID引用字段
     */
    public boolean isIdField(String fieldName) {
        return detectSystem(fieldName) != null;
    }

    /**
     * 解析ID获取对应的NAME
     *
     * @param systemName 系统名（如"物品系统"）
     * @param idValue ID值
     * @return NAME值，如果未找到返回原ID
     */
    public String resolveName(String systemName, String idValue) {
        if (systemName == null || idValue == null || idValue.isEmpty()) {
            return idValue;
        }

        // 确保缓存已加载
        ensureCacheLoaded(systemName);

        Map<String, String> cache = systemCache.get(systemName);
        if (cache == null) {
            return idValue;
        }

        return cache.getOrDefault(idValue, idValue);
    }

    /**
     * 根据字段名和ID值解析NAME
     *
     * @param fieldName 字段名（用于自动检测系统）
     * @param idValue ID值
     * @return NAME值，如果未找到返回原ID
     */
    public String resolveByField(String fieldName, String idValue) {
        String system = detectSystem(fieldName);
        if (system == null) {
            return idValue;
        }
        return resolveName(system, idValue);
    }

    /**
     * 格式化显示ID和NAME
     *
     * @param fieldName 字段名
     * @param idValue ID值
     * @return 格式化字符串，如 "123 (火球术)"
     */
    public String formatIdWithName(String fieldName, String idValue) {
        if (idValue == null || idValue.isEmpty()) {
            return idValue;
        }

        String name = resolveByField(fieldName, idValue);
        if (name.equals(idValue)) {
            return idValue;  // 未找到NAME，只显示ID
        }

        return idValue + " (" + name + ")";
    }

    /**
     * 批量解析ID
     *
     * @param systemName 系统名
     * @param idValues ID值列表
     * @return ID到NAME的映射
     */
    public Map<String, String> batchResolve(String systemName, Collection<String> idValues) {
        if (systemName == null || idValues == null || idValues.isEmpty()) {
            return Collections.emptyMap();
        }

        ensureCacheLoaded(systemName);

        Map<String, String> cache = systemCache.get(systemName);
        if (cache == null) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String id : idValues) {
            result.put(id, cache.getOrDefault(id, id));
        }

        return result;
    }

    /**
     * 获取系统的所有ID-NAME映射（用于下拉选择等）
     *
     * @param systemName 系统名
     * @return ID到NAME的完整映射
     */
    public Map<String, String> getAllMappings(String systemName) {
        ensureCacheLoaded(systemName);
        Map<String, String> cache = systemCache.get(systemName);
        return cache != null ? Collections.unmodifiableMap(cache) : Collections.emptyMap();
    }

    /**
     * 获取支持的所有系统名称
     */
    public Set<String> getSupportedSystems() {
        return Collections.unmodifiableSet(SYSTEM_CONFIG.keySet());
    }

    /**
     * 清除指定系统的缓存
     */
    public void clearCache(String systemName) {
        systemCache.remove(systemName);
        cacheTimestamps.remove(systemName);
        log.info("已清除系统 {} 的缓存", systemName);
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        systemCache.clear();
        cacheTimestamps.clear();
        log.info("已清除所有系统缓存");
    }

    /**
     * 预加载所有系统的缓存
     */
    public void preloadAllSystems() {
        log.info("开始预加载所有系统的ID-NAME映射...");
        long startTime = System.currentTimeMillis();

        for (String systemName : SYSTEM_CONFIG.keySet()) {
            try {
                loadSystemCache(systemName);
            } catch (Exception e) {
                log.warn("预加载系统 {} 失败: {}", systemName, e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("预加载完成，耗时 {}ms", elapsed);
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : systemCache.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

    // ========== 私有方法 ==========

    private void ensureCacheLoaded(String systemName) {
        Long timestamp = cacheTimestamps.get(systemName);
        long now = System.currentTimeMillis();

        // 检查缓存是否存在且未过期
        if (timestamp != null && (now - timestamp) < CACHE_TTL_MS) {
            return;
        }

        // 加载缓存
        loadSystemCache(systemName);
    }

    private void loadSystemCache(String systemName) {
        String[] config = SYSTEM_CONFIG.get(systemName);
        if (config == null) {
            log.warn("未知系统: {}", systemName);
            return;
        }

        String tableName = config[0];
        String idColumn = config[1];
        String nameColumn = config[2];

        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

            // 检查表是否存在
            if (!DatabaseUtil.tableExists(tableName)) {
                log.debug("表 {} 不存在，跳过加载系统 {}", tableName, systemName);
                systemCache.put(systemName, Collections.emptyMap());
                cacheTimestamps.put(systemName, System.currentTimeMillis());
                return;
            }

            // 查询ID和NAME
            String sql = String.format(
                "SELECT `%s`, `%s` FROM `%s` WHERE `%s` IS NOT NULL AND `%s` IS NOT NULL",
                idColumn, nameColumn, tableName, idColumn, nameColumn
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            Map<String, String> mapping = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object id = row.get(idColumn);
                Object name = row.get(nameColumn);
                if (id != null && name != null) {
                    mapping.put(id.toString(), name.toString());
                }
            }

            systemCache.put(systemName, mapping);
            cacheTimestamps.put(systemName, System.currentTimeMillis());

            log.debug("已加载系统 {} 的映射，共 {} 条", systemName, mapping.size());

        } catch (Exception e) {
            log.warn("加载系统 {} 的映射失败: {}", systemName, e.getMessage());
            // 放入空映射，避免重复尝试
            systemCache.put(systemName, Collections.emptyMap());
            cacheTimestamps.put(systemName, System.currentTimeMillis());
        }
    }

    /**
     * 自定义系统配置
     *
     * @param systemName 系统名
     * @param tableName 表名
     * @param idColumn ID列名
     * @param nameColumn NAME列名
     */
    public void registerSystem(String systemName, String tableName, String idColumn, String nameColumn) {
        SYSTEM_CONFIG.put(systemName, new String[]{tableName, idColumn, nameColumn});
        clearCache(systemName);
        log.info("已注册自定义系统: {} -> {}.{}/{}", systemName, tableName, idColumn, nameColumn);
    }

    /**
     * 添加字段模式匹配
     *
     * @param pattern 正则模式
     * @param systemName 对应的系统名
     */
    public void registerFieldPattern(String pattern, String systemName) {
        FIELD_PATTERNS.put(Pattern.compile(pattern), systemName);
        log.info("已注册字段模式: {} -> {}", pattern, systemName);
    }
}
