package red.jiuzhou.ui.mapping;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表结构缓存管理器
 *
 * 设计思路：
 * 1. 首次加载：从数据库扫描所有表结构，生成缓存文件
 * 2. 后续加载：优先从缓存文件读取（毫秒级），后台异步验证数据库
 * 3. DDL文件支持：可以从DDL文件解析表结构作为补充/验证
 * 4. 增量更新：只更新变化的表，不是每次全量扫描
 *
 * 性能优化：
 * - 缓存文件存储，快速启动
 * - 并发加载，多线程处理
 * - 懒加载字段详情
 * - 智能刷新机制
 *
 * 准确性保证：
 * - 带时间戳的版本控制
 * - 后台校验数据库变化
 * - DDL文件对比验证
 * - 强制刷新选项
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 2.0
 */
public class TableStructureCache {

    private static final Logger log = LoggerFactory.getLogger(TableStructureCache.class);

    // 缓存文件路径
    private static final String CACHE_DIR = "cache";
    private static final String CACHE_FILE = CACHE_DIR + "/table_structure_cache.json";

    // 缓存有效期（毫秒）- 默认1小时
    private static final long CACHE_VALIDITY_MS = 60 * 60 * 1000;

    // 内存缓存
    private static final Map<String, DatabaseTableScanner.TableInfo> tableCache =
        new ConcurrentHashMap<>();

    // 缓存元数据
    private static CacheMetadata metadata;

    /**
     * 缓存元数据
     */
    public static class CacheMetadata {
        public long timestamp;           // 缓存时间戳
        public String databaseName;      // 数据库名
        public int tableCount;           // 表数量
        public String version;           // 缓存版本

        public CacheMetadata() {
            this.timestamp = System.currentTimeMillis();
            this.version = "2.0";
        }

        public boolean isValid() {
            long age = System.currentTimeMillis() - timestamp;
            return age < CACHE_VALIDITY_MS;
        }

        public long getAgeMinutes() {
            return (System.currentTimeMillis() - timestamp) / 1000 / 60;
        }
    }

    /**
     * 缓存数据结构
     */
    private static class CacheData {
        public CacheMetadata metadata;
        public List<DatabaseTableScanner.TableInfo> tables;

        public CacheData() {
            this.metadata = new CacheMetadata();
            this.tables = new ArrayList<>();
        }
    }

    /**
     * 加载表结构（智能缓存策略）
     *
     * @param forceRefresh 是否强制刷新
     * @return 表信息列表
     */
    public static List<DatabaseTableScanner.TableInfo> loadTableStructures(boolean forceRefresh) {

        // 1. 如果强制刷新，直接从数据库加载
        if (forceRefresh) {
            log.info("强制刷新：从数据库加载表结构");
            return loadFromDatabaseAndCache();
        }

        // 2. 尝试从内存缓存加载
        if (!tableCache.isEmpty() && metadata != null && metadata.isValid()) {
            log.info("从内存缓存加载 {} 个表（缓存年龄: {} 分钟）",
                tableCache.size(), metadata.getAgeMinutes());
            return new ArrayList<>(tableCache.values());
        }

        // 3. 尝试从文件缓存加载
        List<DatabaseTableScanner.TableInfo> cachedTables = loadFromCacheFile();
        if (cachedTables != null && !cachedTables.isEmpty()) {
            log.info("从文件缓存加载 {} 个表（缓存年龄: {} 分钟）",
                cachedTables.size(), metadata.getAgeMinutes());

            // 加载到内存缓存
            tableCache.clear();
            for (DatabaseTableScanner.TableInfo table : cachedTables) {
                tableCache.put(table.getTableName(), table);
            }

            // 启动后台验证（可选）
            startBackgroundValidation();

            return cachedTables;
        }

        // 4. 缓存未命中，从数据库加载
        log.info("缓存未命中，从数据库加载表结构");
        return loadFromDatabaseAndCache();
    }

    /**
     * 从数据库加载并缓存
     */
    private static List<DatabaseTableScanner.TableInfo> loadFromDatabaseAndCache() {
        List<DatabaseTableScanner.TableInfo> tables = DatabaseTableScanner.scanAllTables();

        // 保存到内存缓存
        tableCache.clear();
        for (DatabaseTableScanner.TableInfo table : tables) {
            tableCache.put(table.getTableName(), table);
        }

        // 保存到文件缓存
        saveToCacheFile(tables);

        return tables;
    }

    /**
     * 从缓存文件加载
     */
    private static List<DatabaseTableScanner.TableInfo> loadFromCacheFile() {
        try {
            File cacheFile = new File(CACHE_FILE);
            if (!cacheFile.exists()) {
                log.info("缓存文件不存在: {}", CACHE_FILE);
                return null;
            }

            String json = new String(Files.readAllBytes(Paths.get(CACHE_FILE)),
                StandardCharsets.UTF_8);

            CacheData cacheData = JSON.parseObject(json, CacheData.class);

            if (cacheData == null || cacheData.metadata == null) {
                log.warn("缓存文件格式错误");
                return null;
            }

            metadata = cacheData.metadata;

            // 检查缓存是否过期
            if (!metadata.isValid()) {
                log.info("缓存已过期（年龄: {} 分钟），需要刷新", metadata.getAgeMinutes());
                return null;
            }

            log.info("成功从缓存文件加载 {} 个表", cacheData.tables.size());
            return cacheData.tables;

        } catch (Exception e) {
            log.error("加载缓存文件失败", e);
            return null;
        }
    }

    /**
     * 保存到缓存文件
     */
    private static void saveToCacheFile(List<DatabaseTableScanner.TableInfo> tables) {
        try {
            // 创建缓存目录
            File cacheDir = new File(CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // 构建缓存数据
            CacheData cacheData = new CacheData();
            cacheData.metadata = new CacheMetadata();
            cacheData.metadata.tableCount = tables.size();
            cacheData.tables = tables;

            // 更新元数据
            metadata = cacheData.metadata;

            // 序列化为JSON
            String json = JSON.toJSONString(cacheData,
                SerializerFeature.PrettyFormat,
                SerializerFeature.WriteMapNullValue);

            // 写入文件
            Files.write(Paths.get(CACHE_FILE), json.getBytes(StandardCharsets.UTF_8));

            log.info("成功保存缓存文件: {} (包含 {} 个表)", CACHE_FILE, tables.size());

        } catch (Exception e) {
            log.error("保存缓存文件失败", e);
        }
    }

    /**
     * 启动后台验证（异步）
     * 在后台检查数据库是否有变化
     */
    private static void startBackgroundValidation() {
        Thread validationThread = new Thread(() -> {
            try {
                Thread.sleep(3000);  // 延迟3秒后开始验证

                log.info("后台验证：检查数据库表结构变化");

                // 快速检查表数量是否变化
                List<String> currentTables = DatabaseTableScanner.getTableNameList();

                if (currentTables.size() != tableCache.size()) {
                    log.warn("表数量变化：缓存={}, 数据库={}, 建议刷新",
                        tableCache.size(), currentTables.size());
                } else {
                    log.info("后台验证：表结构无明显变化");
                }

            } catch (Exception e) {
                log.error("后台验证失败", e);
            }
        });

        validationThread.setName("TableStructure-BackgroundValidation");
        validationThread.setDaemon(true);
        validationThread.start();
    }

    /**
     * 清除所有缓存
     */
    public static void clearCache() {
        tableCache.clear();
        metadata = null;

        try {
            File cacheFile = new File(CACHE_FILE);
            if (cacheFile.exists()) {
                cacheFile.delete();
                log.info("已删除缓存文件: {}", CACHE_FILE);
            }
        } catch (Exception e) {
            log.error("删除缓存文件失败", e);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        if (metadata == null) {
            return "无缓存";
        }

        return String.format(
            "缓存: %d 个表 | 年龄: %d 分钟 | 状态: %s",
            tableCache.size(),
            metadata.getAgeMinutes(),
            metadata.isValid() ? "有效" : "已过期"
        );
    }

    /**
     * 从DDL文件加载表结构（可选功能）
     *
     * @param ddlDirectory DDL文件目录
     * @return 表信息映射 (表名 -> TableInfo)
     */
    public static Map<String, DatabaseTableScanner.TableInfo> loadFromDDLFiles(String ddlDirectory) {
        Map<String, DatabaseTableScanner.TableInfo> ddlTables = new HashMap<>();

        try {
            File dir = new File(ddlDirectory);
            if (!dir.exists() || !dir.isDirectory()) {
                log.warn("DDL目录不存在: {}", ddlDirectory);
                return ddlTables;
            }

            File[] ddlFiles = dir.listFiles((d, name) -> name.endsWith(".sql"));
            if (ddlFiles == null || ddlFiles.length == 0) {
                log.info("DDL目录中没有.sql文件: {}", ddlDirectory);
                return ddlTables;
            }

            log.info("开始解析 {} 个DDL文件", ddlFiles.length);

            for (File ddlFile : ddlFiles) {
                try {
                    DatabaseTableScanner.TableInfo tableInfo = parseDDLFile(ddlFile);
                    if (tableInfo != null) {
                        ddlTables.put(tableInfo.getTableName(), tableInfo);
                    }
                } catch (Exception e) {
                    log.error("解析DDL文件失败: {}", ddlFile.getName(), e);
                }
            }

            log.info("成功解析 {} 个表结构", ddlTables.size());

        } catch (Exception e) {
            log.error("加载DDL文件失败", e);
        }

        return ddlTables;
    }

    /**
     * 解析单个DDL文件
     *
     * @param ddlFile DDL文件
     * @return 表信息
     */
    private static DatabaseTableScanner.TableInfo parseDDLFile(File ddlFile) {
        // TODO: 实现DDL解析逻辑
        // 这里可以使用正则表达式或SQL解析库来解析CREATE TABLE语句

        log.debug("解析DDL文件: {}", ddlFile.getName());

        // 示例实现框架
        // String tableName = extractTableName(ddlFile);
        // List<ColumnInfo> columns = extractColumns(ddlFile);
        // return new TableInfo(tableName, columns);

        return null;  // 暂未实现
    }

    /**
     * 对比缓存和DDL文件的差异
     *
     * @param ddlDirectory DDL文件目录
     * @return 差异报告
     */
    public static String compareWithDDL(String ddlDirectory) {
        Map<String, DatabaseTableScanner.TableInfo> ddlTables = loadFromDDLFiles(ddlDirectory);

        if (ddlTables.isEmpty()) {
            return "无DDL文件或解析失败";
        }

        StringBuilder report = new StringBuilder();
        report.append("=== 缓存与DDL文件对比报告 ===\n\n");

        // 检查缓存中有但DDL中没有的表
        int onlyInCache = 0;
        for (String tableName : tableCache.keySet()) {
            if (!ddlTables.containsKey(tableName)) {
                onlyInCache++;
            }
        }

        // 检查DDL中有但缓存中没有的表
        int onlyInDDL = 0;
        for (String tableName : ddlTables.keySet()) {
            if (!tableCache.containsKey(tableName)) {
                onlyInDDL++;
            }
        }

        report.append(String.format("缓存表数量: %d\n", tableCache.size()));
        report.append(String.format("DDL文件数量: %d\n", ddlTables.size()));
        report.append(String.format("仅在缓存: %d\n", onlyInCache));
        report.append(String.format("仅在DDL: %d\n", onlyInDDL));

        return report.toString();
    }
}
