package red.jiuzhou.ui.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.DatabaseUtil;

import java.sql.*;
import java.util.*;

/**
 * 增强版数据同步服务
 *
 * 功能特性：
 * 1. 主表同步：
 *    - 步骤1：更新字段结构（类型相同的字段）
 *    - 步骤2：根据主键同步数据（增加新记录，更新相似记录，不删除）
 *
 * 2. 子表同步：
 *    - 同步时考虑上层主表的主键对照
 *    - 保持层级关系的一致性
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 2.0
 */
public class EnhancedDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(EnhancedDataSyncService.class);

    /**
     * 同步模式
     */
    public enum SyncMode {
        /**
         * 增量更新（默认）：新增不存在的记录 + 更新已存在的记录
         */
        INCREMENTAL("增量更新", "新增 + 更新", "适合日常数据同步"),

        /**
         * 只更新匹配：只更新已存在的记录，不新增新记录
         */
        UPDATE_ONLY("只更新匹配", "只更新", "只同步已存在的数据"),

        /**
         * 只新增：只新增新记录，不更新已存在的记录
         */
        INSERT_ONLY("只新增", "只新增", "只添加新数据，保留原有数据不变"),

        /**
         * 完全同步：删除目标表中不存在于源表的记录，然后执行增量更新
         */
        FULL_SYNC("完全同步", "增删改", "使目标表与源表完全一致（危险操作）");

        private final String displayName;
        private final String shortName;
        private final String description;

        SyncMode(String displayName, String shortName, String description) {
            this.displayName = displayName;
            this.shortName = shortName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getShortName() {
            return shortName;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return displayName + " - " + description;
        }
    }

    /**
     * 同步结果
     */
    public static class EnhancedSyncResult {
        public boolean success;
        public int schemaUpdates;       // 字段结构更新数
        public int insertedRows;        // 新增记录数
        public int updatedRows;         // 更新记录数
        public int skippedRows;         // 跳过记录数
        public int totalRows;           // 总记录数
        public String message;
        public List<String> errors;
        public List<String> warnings;
        public long durationMs;

        public EnhancedSyncResult() {
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }
    }

    /**
     * 主键映射信息（用于子表同步）
     */
    public static class PrimaryKeyMapping {
        public String sourcePrimaryKey;     // 源表主键值
        public String targetPrimaryKey;     // 目标表主键值

        public PrimaryKeyMapping(String sourcePrimaryKey, String targetPrimaryKey) {
            this.sourcePrimaryKey = sourcePrimaryKey;
            this.targetPrimaryKey = targetPrimaryKey;
        }
    }

    /**
     * 同步主表数据（使用默认增量模式）
     *
     * @param sourceTableInfo 源表（通常是客户端表）
     * @param targetTableInfo 目标表（通常是服务端表）
     * @return 同步结果
     */
    public static EnhancedSyncResult syncMainTable(
            DatabaseTableScanner.TableInfo sourceTableInfo,
            DatabaseTableScanner.TableInfo targetTableInfo) {
        return syncMainTable(sourceTableInfo, targetTableInfo, SyncMode.INCREMENTAL);
    }

    /**
     * 同步主表数据（支持自定义同步模式）
     *
     * @param sourceTableInfo 源表（通常是客户端表）
     * @param targetTableInfo 目标表（通常是服务端表）
     * @param syncMode 同步模式
     * @return 同步结果
     */
    public static EnhancedSyncResult syncMainTable(
            DatabaseTableScanner.TableInfo sourceTableInfo,
            DatabaseTableScanner.TableInfo targetTableInfo,
            SyncMode syncMode) {

        EnhancedSyncResult result = new EnhancedSyncResult();
        long startTime = System.currentTimeMillis();

        String sourceTable = sourceTableInfo.getTableName();
        String targetTable = targetTableInfo.getTableName();

        log.info("开始同步主表: {} → {} [模式: {}]", sourceTable, targetTable, syncMode.getDisplayName());

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);  // 开启事务

            // 步骤1：更新字段结构
            result.schemaUpdates = updateTableSchema(conn, sourceTableInfo, targetTableInfo);
            log.info("字段结构更新完成，修改了 {} 个字段", result.schemaUpdates);

            // 步骤2：获取主键字段
            String primaryKeyColumn = getPrimaryKeyColumn(targetTableInfo);
            if (primaryKeyColumn == null) {
                // 尝试为表创建主键
                primaryKeyColumn = createPrimaryKeyIfNeeded(conn, targetTableInfo);
                if (primaryKeyColumn == null) {
                    result.errors.add("目标表没有主键且无法创建主键");
                    conn.rollback();
                    return result;
                }
                result.warnings.add("已为目标表创建主键: " + primaryKeyColumn);
            }

            // 步骤3：获取共同字段
            List<String> commonFields = getCommonFields(sourceTableInfo, targetTableInfo);
            if (commonFields.isEmpty()) {
                result.errors.add("没有共同字段，无法同步数据");
                conn.rollback();
                return result;
            }

            // 步骤4：同步数据（根据选择的模式）
            syncDataIncremental(conn, sourceTable, targetTable, primaryKeyColumn, commonFields, syncMode, result);

            conn.commit();
            result.success = true;
            log.info("主表同步成功: 新增={}, 更新={}, 跳过={}",
                    result.insertedRows, result.updatedRows, result.skippedRows);

        } catch (Exception e) {
            result.success = false;
            result.errors.add("同步失败: " + e.getMessage());
            log.error("主表同步失败", e);
        }

        result.durationMs = System.currentTimeMillis() - startTime;
        return result;
    }

    /**
     * 级联同步主表及其所有子表
     *
     * @param sourceMainTable 源主表
     * @param targetMainTable 目标主表
     * @param allTables 所有表信息（用于查找子表）
     * @param syncMode 同步模式
     * @return 综合同步结果
     */
    public static CascadeSyncResult syncMainTableWithChildren(
            DatabaseTableScanner.TableInfo sourceMainTable,
            DatabaseTableScanner.TableInfo targetMainTable,
            List<DatabaseTableScanner.TableInfo> allTables,
            SyncMode syncMode) {

        CascadeSyncResult cascadeResult = new CascadeSyncResult();
        long startTime = System.currentTimeMillis();

        log.info("=== 开始级联同步主表及子表 ===");
        log.info("源主表: {}", sourceMainTable.getTableName());
        log.info("目标主表: {}", targetMainTable.getTableName());
        log.info("同步模式: {}", syncMode.getDisplayName());

        // 步骤1：同步主表
        log.info("步骤1：同步主表...");
        EnhancedSyncResult mainResult = syncMainTable(sourceMainTable, targetMainTable, syncMode);
        cascadeResult.mainTableResult = mainResult;
        cascadeResult.totalInserted += mainResult.insertedRows;
        cascadeResult.totalUpdated += mainResult.updatedRows;

        if (!mainResult.success) {
            cascadeResult.success = false;
            cascadeResult.message = "主表同步失败: " + mainResult.message;
            log.error("主表同步失败，停止子表同步");
            return cascadeResult;
        }

        log.info("主表同步成功！新增={}, 更新={}", mainResult.insertedRows, mainResult.updatedRows);

        // 步骤2：查找所有子表
        log.info("步骤2：查找子表...");
        List<TablePair> childPairs = findChildTables(sourceMainTable, targetMainTable, allTables);

        if (childPairs.isEmpty()) {
            log.info("未找到子表，级联同步完成");
            cascadeResult.success = true;
            cascadeResult.message = "主表同步完成，无子表";
            cascadeResult.durationMs = System.currentTimeMillis() - startTime;
            return cascadeResult;
        }

        log.info("找到 {} 个子表对", childPairs.size());

        // 步骤3：构建主表主键映射
        log.info("步骤3：构建主表主键映射...");
        Map<String, String> parentKeyMapping;
        try {
            parentKeyMapping = buildPrimaryKeyMapping(
                sourceMainTable,
                targetMainTable
            );
            log.info("主表主键映射构建完成，共 {} 条映射", parentKeyMapping.size());
        } catch (SQLException e) {
            log.error("构建主表主键映射失败", e);
            cascadeResult.success = false;
            cascadeResult.message = "构建主表主键映射失败: " + e.getMessage();
            cascadeResult.durationMs = System.currentTimeMillis() - startTime;
            return cascadeResult;
        }

        // 步骤4：同步所有子表
        log.info("步骤4：同步 {} 个子表...", childPairs.size());
        for (int i = 0; i < childPairs.size(); i++) {
            TablePair pair = childPairs.get(i);
            log.info("  [{}/{}] 同步子表: {} → {}",
                i + 1, childPairs.size(),
                pair.sourceTable.getTableName(),
                pair.targetTable.getTableName());

            try {
                EnhancedSyncResult subResult = syncSubTable(
                    pair.sourceTable,
                    pair.targetTable,
                    parentKeyMapping,
                    syncMode
                );

                cascadeResult.subTableResults.add(subResult);
                cascadeResult.totalInserted += subResult.insertedRows;
                cascadeResult.totalUpdated += subResult.updatedRows;

                if (subResult.success) {
                    cascadeResult.successfulSubTables++;
                    log.info("    ✅ 子表同步成功！新增={}, 更新={}",
                        subResult.insertedRows, subResult.updatedRows);
                } else {
                    cascadeResult.failedSubTables++;
                    log.warn("    ❌ 子表同步失败: {}", subResult.message);
                }

            } catch (Exception e) {
                cascadeResult.failedSubTables++;
                log.error("    ❌ 子表同步异常", e);

                EnhancedSyncResult errorResult = new EnhancedSyncResult();
                errorResult.success = false;
                errorResult.message = "同步异常: " + e.getMessage();
                cascadeResult.subTableResults.add(errorResult);
            }
        }

        // 汇总结果
        cascadeResult.success = (cascadeResult.failedSubTables == 0);
        cascadeResult.durationMs = System.currentTimeMillis() - startTime;
        cascadeResult.message = String.format(
            "主表同步完成，子表：成功=%d, 失败=%d, 总计新增=%d, 更新=%d",
            cascadeResult.successfulSubTables,
            cascadeResult.failedSubTables,
            cascadeResult.totalInserted,
            cascadeResult.totalUpdated
        );

        log.info("=== 级联同步完成 ===");
        log.info("耗时: {} ms", cascadeResult.durationMs);
        log.info("主表: {}", mainResult.success ? "✅ 成功" : "❌ 失败");
        log.info("子表: 成功={}, 失败={}", cascadeResult.successfulSubTables, cascadeResult.failedSubTables);
        log.info("数据: 新增={}, 更新={}", cascadeResult.totalInserted, cascadeResult.totalUpdated);

        return cascadeResult;
    }

    /**
     * 查找主表的所有子表对
     */
    private static List<TablePair> findChildTables(
            DatabaseTableScanner.TableInfo sourceMainTable,
            DatabaseTableScanner.TableInfo targetMainTable,
            List<DatabaseTableScanner.TableInfo> allTables) {

        List<TablePair> childPairs = new ArrayList<>();
        String sourceMainName = sourceMainTable.getTableName();
        String targetMainName = targetMainTable.getTableName();

        // 去除 client_ 前缀以便匹配
        String sourceBaseName = sourceMainName.replace("client_", "");
        String targetBaseName = targetMainName.replace("client_", "");

        // 查找源表的子表
        Map<String, DatabaseTableScanner.TableInfo> sourceChildren = new HashMap<>();
        Map<String, DatabaseTableScanner.TableInfo> targetChildren = new HashMap<>();

        for (DatabaseTableScanner.TableInfo table : allTables) {
            String tableName = table.getTableName();
            String baseTableName = tableName.replace("client_", "");

            // 检查是否是源主表的子表
            if (baseTableName.startsWith(sourceBaseName + "__")) {
                // 这是源主表的子表
                String childSuffix = baseTableName.substring(sourceBaseName.length());
                sourceChildren.put(childSuffix, table);
                log.debug("找到源子表: {} (后缀: {})", tableName, childSuffix);
            }

            // 检查是否是目标主表的子表
            if (baseTableName.startsWith(targetBaseName + "__")) {
                // 这是目标主表的子表
                String childSuffix = baseTableName.substring(targetBaseName.length());
                targetChildren.put(childSuffix, table);
                log.debug("找到目标子表: {} (后缀: {})", tableName, childSuffix);
            }
        }

        // 匹配子表对
        for (Map.Entry<String, DatabaseTableScanner.TableInfo> entry : sourceChildren.entrySet()) {
            String suffix = entry.getKey();
            DatabaseTableScanner.TableInfo sourceChild = entry.getValue();
            DatabaseTableScanner.TableInfo targetChild = targetChildren.get(suffix);

            if (targetChild != null) {
                childPairs.add(new TablePair(sourceChild, targetChild));
                log.debug("匹配子表对: {} <-> {}", sourceChild.getTableName(), targetChild.getTableName());
            } else {
                log.warn("源子表 {} 没有对应的目标子表", sourceChild.getTableName());
            }
        }

        return childPairs;
    }

    /**
     * 表对（用于子表配对）
     */
    private static class TablePair {
        DatabaseTableScanner.TableInfo sourceTable;
        DatabaseTableScanner.TableInfo targetTable;

        TablePair(DatabaseTableScanner.TableInfo sourceTable, DatabaseTableScanner.TableInfo targetTable) {
            this.sourceTable = sourceTable;
            this.targetTable = targetTable;
        }
    }

    /**
     * 级联同步结果
     */
    public static class CascadeSyncResult {
        public boolean success;
        public String message;
        public long durationMs;

        public EnhancedSyncResult mainTableResult;                  // 主表同步结果
        public List<EnhancedSyncResult> subTableResults;            // 子表同步结果列表

        public int successfulSubTables = 0;                         // 成功的子表数量
        public int failedSubTables = 0;                             // 失败的子表数量
        public int totalInserted = 0;                               // 总新增记录数
        public int totalUpdated = 0;                                // 总更新记录数

        public CascadeSyncResult() {
            this.subTableResults = new ArrayList<>();
        }
    }

    /**
     * 同步子表数据（考虑主表主键映射）
     *
     * @param sourceTableInfo 源子表
     * @param targetTableInfo 目标子表
     * @param parentKeyMapping 父表主键映射关系
     * @param syncMode 同步模式
     * @return 同步结果
     */
    public static EnhancedSyncResult syncSubTable(
            DatabaseTableScanner.TableInfo sourceTableInfo,
            DatabaseTableScanner.TableInfo targetTableInfo,
            Map<String, String> parentKeyMapping,
            SyncMode syncMode) {

        EnhancedSyncResult result = new EnhancedSyncResult();
        long startTime = System.currentTimeMillis();

        String sourceTable = sourceTableInfo.getTableName();
        String targetTable = targetTableInfo.getTableName();

        log.info("开始同步子表: {} → {} (父表映射数: {})",
                sourceTable, targetTable, parentKeyMapping != null ? parentKeyMapping.size() : 0);

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);

            // 步骤1：更新字段结构
            result.schemaUpdates = updateTableSchema(conn, sourceTableInfo, targetTableInfo);

            // 步骤2：获取外键字段（指向父表）
            String foreignKeyColumn = inferForeignKeyColumn(sourceTableInfo);
            if (foreignKeyColumn == null) {
                result.warnings.add("无法推断外键字段，可能影响数据一致性");
            }

            // 步骤3：获取主键和共同字段
            String primaryKeyColumn = getPrimaryKeyColumn(targetTableInfo);
            if (primaryKeyColumn == null) {
                // 子表没有主键，尝试创建复合主键（外键+子表标识字段）
                primaryKeyColumn = createCompositePrimaryKeyForSubTable(
                    conn, targetTableInfo, foreignKeyColumn);
                if (primaryKeyColumn == null) {
                    result.errors.add("子表没有主键且无法创建主键");
                    conn.rollback();
                    return result;
                }
                result.warnings.add("已为子表创建复合主键");
            }

            List<String> commonFields = getCommonFields(sourceTableInfo, targetTableInfo);
            if (commonFields.isEmpty()) {
                result.errors.add("没有共同字段");
                conn.rollback();
                return result;
            }

            // 步骤4：同步数据（考虑父表主键映射）
            syncSubTableDataWithMapping(conn, sourceTable, targetTable,
                    primaryKeyColumn, foreignKeyColumn, commonFields,
                    parentKeyMapping, syncMode, result);

            conn.commit();
            result.success = true;
            log.info("子表同步成功: 新增={}, 更新={}, 跳过={}",
                    result.insertedRows, result.updatedRows, result.skippedRows);

        } catch (Exception e) {
            result.success = false;
            result.errors.add("同步失败: " + e.getMessage());
            log.error("子表同步失败", e);
        }

        result.durationMs = System.currentTimeMillis() - startTime;
        return result;
    }

    /**
     * 更新表结构（只更新类型相同的字段）
     */
    private static int updateTableSchema(Connection conn,
                                        DatabaseTableScanner.TableInfo sourceTableInfo,
                                        DatabaseTableScanner.TableInfo targetTableInfo) throws SQLException {
        int updateCount = 0;

        // 创建目标表字段映射
        Map<String, DatabaseTableScanner.ColumnInfo> targetColumns = new HashMap<>();
        for (DatabaseTableScanner.ColumnInfo col : targetTableInfo.getColumns()) {
            targetColumns.put(col.getColumnName().toLowerCase(), col);
        }

        // 只处理共同字段（在源表和目标表中都存在的字段）
        for (DatabaseTableScanner.ColumnInfo sourceCol : sourceTableInfo.getColumns()) {
            String fieldName = sourceCol.getColumnName().toLowerCase();
            DatabaseTableScanner.ColumnInfo targetCol = targetColumns.get(fieldName);

            if (targetCol != null) {
                // 字段存在于目标表，检查是否需要扩展长度
                if (isSameBaseDataType(sourceCol.getDataType(), targetCol.getDataType())) {
                    // 基础类型相同，检查是否需要扩展字段长度
                    if (needsLengthExtension(sourceCol, targetCol)) {
                        extendColumnLength(conn, targetTableInfo.getTableName(), sourceCol, targetCol);
                        updateCount++;
                        log.info("扩展字段长度: {}.{} {} → {}",
                                targetTableInfo.getTableName(),
                                fieldName,
                                targetCol.getColumnType(),
                                sourceCol.getColumnType());
                    }
                } else {
                    // 基础类型不同，记录警告但不修改
                    log.warn("字段类型不匹配，跳过更新: {}.{} 目标={} 源={}",
                            targetTableInfo.getTableName(),
                            fieldName,
                            targetCol.getColumnType(),
                            sourceCol.getColumnType());
                }
            }
            // 不添加新字段：如果目标表中不存在该字段，直接跳过
        }

        return updateCount;
    }

    /**
     * 增量同步数据（不删除）
     */
    private static void syncDataIncremental(Connection conn,
                                           String sourceTable,
                                           String targetTable,
                                           String primaryKeyColumn,
                                           List<String> commonFields,
                                           SyncMode syncMode,
                                           EnhancedSyncResult result) throws SQLException {

        log.info("数据同步模式: {}", syncMode.getDisplayName());

        // 构建字段列表（使用反引号保护字段名，防止与保留关键字冲突）
        String fieldList = commonFields.stream()
                .map(f -> "`" + f + "`")
                .collect(java.util.stream.Collectors.joining(", "));

        // 查询源表数据
        String selectSql = String.format("SELECT %s FROM %s", fieldList, sourceTable);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {

            PreparedStatement insertStmt = null;
            PreparedStatement updateStmt = null;
            PreparedStatement checkStmt = null;

            try {
                // 准备INSERT语句
                String placeholders = String.join(", ", Collections.nCopies(commonFields.size(), "?"));
                String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                        targetTable, fieldList, placeholders);
                insertStmt = conn.prepareStatement(insertSql);

                // 准备UPDATE语句（使用反引号保护字段名）
                List<String> updateSetClauses = new ArrayList<>();
                for (String field : commonFields) {
                    if (!field.equals(primaryKeyColumn)) {
                        updateSetClauses.add("`" + field + "` = ?");
                    }
                }
                String updateSql = String.format("UPDATE %s SET %s WHERE `%s` = ?",
                        targetTable,
                        String.join(", ", updateSetClauses),
                        primaryKeyColumn);
                updateStmt = conn.prepareStatement(updateSql);

                // 准备CHECK语句（使用反引号保护字段名）
                String checkSql = String.format("SELECT COUNT(*) FROM %s WHERE `%s` = ?",
                        targetTable, primaryKeyColumn);
                checkStmt = conn.prepareStatement(checkSql);

                while (rs.next()) {
                    result.totalRows++;

                    // 获取主键值
                    Object primaryKeyValue = rs.getObject(primaryKeyColumn);
                    if (primaryKeyValue == null) {
                        result.skippedRows++;
                        continue;
                    }

                    // 检查记录是否存在
                    checkStmt.setObject(1, primaryKeyValue);
                    ResultSet checkRs = checkStmt.executeQuery();
                    checkRs.next();
                    boolean exists = checkRs.getInt(1) > 0;
                    checkRs.close();

                    // 根据同步模式执行不同的操作
                    switch (syncMode) {
                        case INCREMENTAL:
                            // 增量更新：新增 + 更新
                            if (exists) {
                                // 更新现有记录
                                int paramIndex = 1;
                                for (String field : commonFields) {
                                    if (!field.equals(primaryKeyColumn)) {
                                        updateStmt.setObject(paramIndex++, rs.getObject(field));
                                    }
                                }
                                updateStmt.setObject(paramIndex, primaryKeyValue);
                                updateStmt.executeUpdate();
                                result.updatedRows++;
                            } else {
                                // 插入新记录
                                for (int i = 0; i < commonFields.size(); i++) {
                                    insertStmt.setObject(i + 1, rs.getObject(commonFields.get(i)));
                                }
                                insertStmt.executeUpdate();
                                result.insertedRows++;
                            }
                            break;

                        case UPDATE_ONLY:
                            // 只更新匹配：只更新已存在的记录
                            if (exists) {
                                int paramIndex = 1;
                                for (String field : commonFields) {
                                    if (!field.equals(primaryKeyColumn)) {
                                        updateStmt.setObject(paramIndex++, rs.getObject(field));
                                    }
                                }
                                updateStmt.setObject(paramIndex, primaryKeyValue);
                                updateStmt.executeUpdate();
                                result.updatedRows++;
                            } else {
                                // 记录不存在，跳过
                                result.skippedRows++;
                            }
                            break;

                        case INSERT_ONLY:
                            // 只新增：只插入新记录，不更新已存在的
                            if (exists) {
                                // 记录已存在，跳过
                                result.skippedRows++;
                            } else {
                                // 插入新记录
                                for (int i = 0; i < commonFields.size(); i++) {
                                    insertStmt.setObject(i + 1, rs.getObject(commonFields.get(i)));
                                }
                                insertStmt.executeUpdate();
                                result.insertedRows++;
                            }
                            break;

                        case FULL_SYNC:
                            // 完全同步：新增 + 更新（删除操作在后面单独处理）
                            if (exists) {
                                int paramIndex = 1;
                                for (String field : commonFields) {
                                    if (!field.equals(primaryKeyColumn)) {
                                        updateStmt.setObject(paramIndex++, rs.getObject(field));
                                    }
                                }
                                updateStmt.setObject(paramIndex, primaryKeyValue);
                                updateStmt.executeUpdate();
                                result.updatedRows++;
                            } else {
                                for (int i = 0; i < commonFields.size(); i++) {
                                    insertStmt.setObject(i + 1, rs.getObject(commonFields.get(i)));
                                }
                                insertStmt.executeUpdate();
                                result.insertedRows++;
                            }
                            break;
                    }
                }

                // 如果是完全同步模式，删除目标表中不存在于源表的记录
                if (syncMode == SyncMode.FULL_SYNC) {
                    int deletedCount = deleteOrphanedRecords(conn, sourceTable, targetTable, primaryKeyColumn);
                    result.message = String.format("完全同步：删除了 %d 条目标表中多余的记录", deletedCount);
                    log.info("完全同步模式：删除了 {} 条目标表中多余的记录", deletedCount);
                }

            } finally {
                if (insertStmt != null) insertStmt.close();
                if (updateStmt != null) updateStmt.close();
                if (checkStmt != null) checkStmt.close();
            }
        }
    }

    /**
     * 删除目标表中不存在于源表的记录（用于完全同步模式）
     */
    private static int deleteOrphanedRecords(Connection conn, String sourceTable,
                                            String targetTable, String primaryKeyColumn) throws SQLException {
        String deleteSql = String.format(
            "DELETE FROM %s WHERE `%s` NOT IN (SELECT `%s` FROM %s)",
            targetTable, primaryKeyColumn, primaryKeyColumn, sourceTable
        );

        try (Statement stmt = conn.createStatement()) {
            int deletedCount = stmt.executeUpdate(deleteSql);
            return deletedCount;
        }
    }

    /**
     * 同步子表数据（考虑父表主键映射）
     */
    private static void syncSubTableDataWithMapping(Connection conn,
                                                    String sourceTable,
                                                    String targetTable,
                                                    String primaryKeyColumn,
                                                    String foreignKeyColumn,
                                                    List<String> commonFields,
                                                    Map<String, String> parentKeyMapping,
                                                    SyncMode syncMode,
                                                    EnhancedSyncResult result) throws SQLException {

        // 如果没有父表映射，直接调用普通同步
        if (parentKeyMapping == null || parentKeyMapping.isEmpty()) {
            syncDataIncremental(conn, sourceTable, targetTable, primaryKeyColumn, commonFields, syncMode, result);
            return;
        }

        // 构建字段列表（使用反引号保护字段名）
        String fieldList = commonFields.stream()
                .map(f -> "`" + f + "`")
                .collect(java.util.stream.Collectors.joining(", "));

        // 查询源表数据
        String selectSql = String.format("SELECT %s FROM %s", fieldList, sourceTable);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {

            PreparedStatement insertStmt = null;
            PreparedStatement updateStmt = null;
            PreparedStatement checkStmt = null;

            try {
                // 准备语句（与主表相同）
                String placeholders = String.join(", ", Collections.nCopies(commonFields.size(), "?"));
                String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                        targetTable, fieldList, placeholders);
                insertStmt = conn.prepareStatement(insertSql);

                // 准备UPDATE语句（使用反引号保护字段名）
                List<String> updateSetClauses = new ArrayList<>();
                for (String field : commonFields) {
                    if (!field.equals(primaryKeyColumn)) {
                        updateSetClauses.add("`" + field + "` = ?");
                    }
                }
                String updateSql = String.format("UPDATE %s SET %s WHERE `%s` = ?",
                        targetTable,
                        String.join(", ", updateSetClauses),
                        primaryKeyColumn);
                updateStmt = conn.prepareStatement(updateSql);

                // 准备CHECK语句（使用反引号保护字段名）
                String checkSql = String.format("SELECT COUNT(*) FROM %s WHERE `%s` = ?",
                        targetTable, primaryKeyColumn);
                checkStmt = conn.prepareStatement(checkSql);

                while (rs.next()) {
                    result.totalRows++;

                    // 获取外键值
                    Object foreignKeyValue = null;
                    if (foreignKeyColumn != null) {
                        foreignKeyValue = rs.getObject(foreignKeyColumn);
                        if (foreignKeyValue != null) {
                            // 转换外键值（使用父表映射）
                            String mappedKey = parentKeyMapping.get(foreignKeyValue.toString());
                            if (mappedKey == null) {
                                result.skippedRows++;
                                result.warnings.add("跳过记录：外键值未在父表映射中找到: " + foreignKeyValue);
                                continue;
                            }
                            foreignKeyValue = mappedKey;
                        }
                    }

                    // 获取主键值
                    Object primaryKeyValue = rs.getObject(primaryKeyColumn);
                    if (primaryKeyValue == null) {
                        result.skippedRows++;
                        continue;
                    }

                    // 检查记录是否存在
                    checkStmt.setObject(1, primaryKeyValue);
                    ResultSet checkRs = checkStmt.executeQuery();
                    checkRs.next();
                    boolean exists = checkRs.getInt(1) > 0;
                    checkRs.close();

                    if (exists) {
                        // 更新现有记录
                        int paramIndex = 1;
                        for (String field : commonFields) {
                            if (!field.equals(primaryKeyColumn)) {
                                Object value = rs.getObject(field);
                                // 如果是外键字段，使用映射后的值
                                if (field.equals(foreignKeyColumn) && foreignKeyValue != null) {
                                    value = foreignKeyValue;
                                }
                                updateStmt.setObject(paramIndex++, value);
                            }
                        }
                        updateStmt.setObject(paramIndex, primaryKeyValue);
                        updateStmt.executeUpdate();
                        result.updatedRows++;
                    } else {
                        // 插入新记录
                        for (int i = 0; i < commonFields.size(); i++) {
                            Object value = rs.getObject(commonFields.get(i));
                            // 如果是外键字段，使用映射后的值
                            if (commonFields.get(i).equals(foreignKeyColumn) && foreignKeyValue != null) {
                                value = foreignKeyValue;
                            }
                            insertStmt.setObject(i + 1, value);
                        }
                        insertStmt.executeUpdate();
                        result.insertedRows++;
                    }
                }

            } finally {
                if (insertStmt != null) insertStmt.close();
                if (updateStmt != null) updateStmt.close();
                if (checkStmt != null) checkStmt.close();
            }
        }
    }

    // ========== 辅助方法 ==========

    private static String getPrimaryKeyColumn(DatabaseTableScanner.TableInfo tableInfo) {
        for (DatabaseTableScanner.ColumnInfo col : tableInfo.getColumns()) {
            if (col.isPrimaryKey()) {
                return col.getColumnName();
            }
        }
        return null;
    }

    private static List<String> getCommonFields(DatabaseTableScanner.TableInfo source,
                                               DatabaseTableScanner.TableInfo target) {
        List<String> commonFields = new ArrayList<>();
        Set<String> targetFieldNames = new HashSet<>();

        for (DatabaseTableScanner.ColumnInfo col : target.getColumns()) {
            targetFieldNames.add(col.getColumnName().toLowerCase());
        }

        for (DatabaseTableScanner.ColumnInfo col : source.getColumns()) {
            if (targetFieldNames.contains(col.getColumnName().toLowerCase())) {
                commonFields.add(col.getColumnName());
            }
        }

        return commonFields;
    }

    private static boolean isSameDataType(String type1, String type2) {
        // 简化类型比较（忽略大小写和长度）
        type1 = type1.toLowerCase().replaceAll("\\(.*\\)", "");
        type2 = type2.toLowerCase().replaceAll("\\(.*\\)", "");
        return type1.equals(type2);
    }

    /**
     * 检查基础数据类型是否相同（忽略长度）
     */
    private static boolean isSameBaseDataType(String type1, String type2) {
        return isSameDataType(type1, type2);
    }

    /**
     * 检查是否需要扩展字段长度
     * 只有当源字段长度大于目标字段长度时才返回true
     */
    private static boolean needsLengthExtension(DatabaseTableScanner.ColumnInfo source,
                                               DatabaseTableScanner.ColumnInfo target) {
        // 提取长度信息
        Integer sourceLength = extractLength(source.getColumnType());
        Integer targetLength = extractLength(target.getColumnType());

        // 如果无法提取长度，不扩展
        if (sourceLength == null || targetLength == null) {
            return false;
        }

        // 只有源字段长度大于目标字段长度时才扩展
        return sourceLength > targetLength;
    }

    /**
     * 从列类型中提取长度
     * 例如: VARCHAR(255) -> 255, INT(11) -> 11
     */
    private static Integer extractLength(String columnType) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\((\\d+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(columnType);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 扩展字段长度
     */
    private static void extendColumnLength(Connection conn, String tableName,
                                          DatabaseTableScanner.ColumnInfo sourceCol,
                                          DatabaseTableScanner.ColumnInfo targetCol) throws SQLException {
        String columnName = sourceCol.getColumnName();
        String sourceColumnType = sourceCol.getColumnType();

        // 使用源表的列类型（包含更大的长度）
        String sql = String.format("ALTER TABLE %s MODIFY COLUMN `%s` %s %s",
                tableName,
                columnName,
                sourceColumnType,
                targetCol.isNullable() ? "NULL" : "NOT NULL");

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            log.debug("成功扩展列长度: {}.{} 从 {} 到 {}",
                    tableName, columnName, targetCol.getColumnType(), sourceColumnType);
        } catch (SQLException e) {
            log.error("扩展列长度失败: {}.{}", tableName, columnName, e);
            throw e;
        }
    }

    private static boolean needsSchemaUpdate(DatabaseTableScanner.ColumnInfo source,
                                            DatabaseTableScanner.ColumnInfo target) {
        // 比较列类型（包括长度）
        return !source.getColumnType().equals(target.getColumnType());
    }

    private static void updateColumnSchema(Connection conn, String tableName,
                                          DatabaseTableScanner.ColumnInfo columnInfo) throws SQLException {
        String columnName = columnInfo.getColumnName();
        String sourceColumnType = columnInfo.getColumnType();

        // 检查目标表中该字段的实际最大数据长度，避免数据截断
        String adjustedColumnType = getOptimalColumnType(conn, tableName, columnName, sourceColumnType);

        String sql = String.format("ALTER TABLE %s MODIFY COLUMN `%s` %s %s",
                tableName,
                columnName,
                adjustedColumnType,
                columnInfo.isNullable() ? "NULL" : "NOT NULL");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.debug("成功更新字段结构: {}.{} → {}", tableName, columnName, adjustedColumnType);
        }
    }

    /**
     * 获取最优列类型（避免数据截断）
     *
     * @param conn 数据库连接
     * @param tableName 表名
     * @param columnName 列名
     * @param sourceColumnType 源表列类型
     * @return 最优列类型
     */
    private static String getOptimalColumnType(Connection conn, String tableName,
                                               String columnName, String sourceColumnType) {
        try {
            // 解析源表列类型
            String baseType = sourceColumnType.replaceAll("\\(.*\\)", "").trim().toUpperCase();

            // 只对字符串类型和二进制类型进行长度检查
            if (!baseType.matches("VARCHAR|CHAR|TEXT|VARBINARY|BINARY|BLOB")) {
                return sourceColumnType;  // 非字符串类型直接返回
            }

            // 提取源表定义的长度
            int sourceLength = extractLengthInt(sourceColumnType);

            // 查询目标表中该字段的实际最大长度
            String checkSql = String.format(
                "SELECT COALESCE(MAX(LENGTH(`%s`)), 0) AS max_length FROM %s",
                columnName, tableName
            );

            int actualMaxLength = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkSql)) {
                if (rs.next()) {
                    actualMaxLength = rs.getInt("max_length");
                }
            }

            // 如果实际数据长度超过源表定义，使用更大的长度
            if (actualMaxLength > sourceLength) {
                log.warn("检测到字段 {}.{} 的实际数据长度({}) 超过源表定义({}), 自动调整长度",
                    tableName, columnName, actualMaxLength, sourceLength);

                // 计算新长度（向上取整到最近的合理值）
                int newLength = calculateOptimalLength(actualMaxLength);

                // 替换长度
                String adjustedType;
                if (sourceColumnType.contains("(")) {
                    adjustedType = sourceColumnType.replaceAll("\\(\\d+\\)", "(" + newLength + ")");
                } else {
                    adjustedType = baseType + "(" + newLength + ")";
                }

                log.info("字段类型调整: {} → {}", sourceColumnType, adjustedType);
                return adjustedType;
            }

            return sourceColumnType;

        } catch (Exception e) {
            log.warn("检查字段长度时出错，使用源表定义: {}", e.getMessage());
            return sourceColumnType;
        }
    }

    /**
     * 从列类型字符串中提取长度（返回基本类型int）
     */
    private static int extractLengthInt(String columnType) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\((\\d+)\\)");
            java.util.regex.Matcher matcher = pattern.matcher(columnType);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        // 默认返回一个较小的值，确保会使用实际数据长度
        return 0;
    }

    /**
     * 计算最优长度（向上取整到合理值）
     */
    private static int calculateOptimalLength(int actualLength) {
        // 留出20%的冗余空间
        int withBuffer = (int) (actualLength * 1.2);

        // 向上取整到最近的50、100、200、500、1000等
        if (withBuffer <= 50) return 50;
        if (withBuffer <= 100) return 100;
        if (withBuffer <= 200) return 200;
        if (withBuffer <= 500) return 500;
        if (withBuffer <= 1000) return 1000;
        if (withBuffer <= 2000) return 2000;
        if (withBuffer <= 5000) return 5000;

        // 超过5000，向上取整到千位
        return ((withBuffer / 1000) + 1) * 1000;
    }

    private static void addNewColumn(Connection conn, String tableName,
                                    DatabaseTableScanner.ColumnInfo columnInfo) throws SQLException {
        // 首先检查列是否已存在
        String checkSql = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                         "WHERE TABLE_SCHEMA = DATABASE() " +
                         "AND TABLE_NAME = ? " +
                         "AND COLUMN_NAME = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setString(1, tableName);
            pstmt.setString(2, columnInfo.getColumnName());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                log.debug("列 {}.{} 已存在，跳过添加", tableName, columnInfo.getColumnName());
                return;  // 列已存在，直接返回
            }
        }

        // 列不存在，执行添加
        String sql = String.format("ALTER TABLE %s ADD COLUMN `%s` %s %s",
                tableName,
                columnInfo.getColumnName(),
                columnInfo.getColumnType(),
                columnInfo.isNullable() ? "NULL" : "NOT NULL");

        // 如果有默认值，添加默认值
        if (columnInfo.getColumnDefault() != null) {
            sql += " DEFAULT " + columnInfo.getColumnDefault();
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("成功添加列: {}.{}", tableName, columnInfo.getColumnName());
        }
    }

    /**
     * 推断外键字段（指向父表）
     * 规则：子表名格式为 parent__child，外键可能是 parent_id 或其他命名方式
     */
    private static String inferForeignKeyColumn(DatabaseTableScanner.TableInfo subTableInfo) {
        TableHierarchyHelper.TableHierarchy hierarchy = subTableInfo.getHierarchy();
        if (hierarchy.isMainTable()) {
            return null;  // 主表没有外键
        }

        String parentTableName = hierarchy.getParentTableName();
        if (parentTableName == null) {
            return null;
        }

        // 去除 client_ 前缀（如果存在）
        String parentBaseNameWithPrefix = parentTableName;
        String parentBaseName = parentTableName.startsWith("client_") ?
            parentTableName.substring("client_".length()) : parentTableName;

        // 尝试多种可能的外键命名方式
        String[] possibleFkNames = {
            // 使用完整父表名（带client_前缀）
            parentBaseNameWithPrefix + "_id",
            parentBaseNameWithPrefix + "Id",
            parentBaseNameWithPrefix + "_key",
            "fk_" + parentBaseNameWithPrefix,
            // 使用去掉client_前缀的父表名
            parentBaseName + "_id",
            parentBaseName + "Id",
            parentBaseName + "_key",
            "fk_" + parentBaseName,
            // 通用命名
            "parent_id",
            "parentId"
        };

        Set<String> columnNames = new HashSet<>();
        for (DatabaseTableScanner.ColumnInfo col : subTableInfo.getColumns()) {
            columnNames.add(col.getColumnName().toLowerCase());
        }

        for (String fkName : possibleFkNames) {
            if (columnNames.contains(fkName.toLowerCase())) {
                log.debug("找到外键列: {} 用于子表 {}", fkName, subTableInfo.getTableName());
                return fkName;
            }
        }

        log.warn("无法推断子表 {} 的外键列，父表: {}", subTableInfo.getTableName(), parentTableName);
        return null;  // 无法推断
    }

    /**
     * 构建主表主键映射（用于子表同步）
     *
     * @param sourceMainTable 源主表
     * @param targetMainTable 目标主表
     * @return 主键映射 (源主键值 -> 目标主键值)
     */
    public static Map<String, String> buildPrimaryKeyMapping(
            DatabaseTableScanner.TableInfo sourceMainTable,
            DatabaseTableScanner.TableInfo targetMainTable) throws SQLException {

        Map<String, String> mapping = new HashMap<>();

        String sourcePkColumn = getPrimaryKeyColumn(sourceMainTable);
        String targetPkColumn = getPrimaryKeyColumn(targetMainTable);

        if (sourcePkColumn == null || targetPkColumn == null) {
            log.warn("无法构建主键映射：主表缺少主键");
            return mapping;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            // 这里假设主键值相同的记录是对应的
            // 在实际应用中，可能需要更复杂的匹配逻辑（例如根据业务字段匹配）
            String sql = String.format(
                    "SELECT s.%s AS source_pk, t.%s AS target_pk " +
                    "FROM %s s " +
                    "INNER JOIN %s t ON s.%s = t.%s",
                    sourcePkColumn, targetPkColumn,
                    sourceMainTable.getTableName(),
                    targetMainTable.getTableName(),
                    sourcePkColumn, targetPkColumn
            );

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String sourcePk = rs.getString("source_pk");
                    String targetPk = rs.getString("target_pk");
                    mapping.put(sourcePk, targetPk);
                }
            }
        }

        log.info("构建主键映射完成: {} 条记录", mapping.size());
        return mapping;
    }

    /**
     * 为表创建主键（如果需要）
     * 主表：使用 id 字段作为自增主键
     *
     * @param conn 数据库连接
     * @param tableInfo 表信息
     * @return 主键列名
     * @throws SQLException SQL异常
     */
    private static String createPrimaryKeyIfNeeded(Connection conn,
                                                   DatabaseTableScanner.TableInfo tableInfo)
                                                   throws SQLException {
        String tableName = tableInfo.getTableName();
        log.info("为表 {} 创建主键...", tableName);

        // 检查表中是否已有 id 字段
        boolean hasIdColumn = false;
        String idColumnType = null;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + tableName)) {
            while (rs.next()) {
                String columnName = rs.getString("Field");
                if ("id".equalsIgnoreCase(columnName)) {
                    hasIdColumn = true;
                    idColumnType = rs.getString("Type");
                    break;
                }
            }
        }

        // 如果没有 id 字段，先添加一个
        if (!hasIdColumn) {
            log.info("表 {} 没有 id 字段，添加 id 字段...", tableName);
            String addColumnSql = String.format(
                "ALTER TABLE %s ADD COLUMN id INT AUTO_INCREMENT UNIQUE FIRST",
                tableName
            );
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(addColumnSql);
                log.info("成功添加 id 字段到表 {}", tableName);
            }
        } else {
            log.info("表 {} 已存在 id 字段 (类型: {})", tableName, idColumnType);
        }

        // 设置 id 为主键
        try {
            String setPkSql = String.format("ALTER TABLE %s ADD PRIMARY KEY (id)", tableName);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(setPkSql);
                log.info("成功为表 {} 设置 id 为主键", tableName);
            }
        } catch (SQLException e) {
            // 如果主键已存在，忽略错误
            if (e.getMessage().contains("Multiple primary key")) {
                log.warn("表 {} 已有主键，跳过主键创建", tableName);
            } else {
                throw e;
            }
        }

        return "id";
    }

    /**
     * 为子表创建复合主键
     * 复合主键 = 外键字段 + 子表标识字段（如 id 或序号）
     *
     * @param conn 数据库连接
     * @param tableInfo 子表信息
     * @param foreignKeyColumn 外键列名
     * @return 主键列名（逗号分隔）
     * @throws SQLException SQL异常
     */
    private static String createCompositePrimaryKeyForSubTable(Connection conn,
                                                               DatabaseTableScanner.TableInfo tableInfo,
                                                               String foreignKeyColumn)
                                                               throws SQLException {
        String tableName = tableInfo.getTableName();
        log.info("为子表 {} 创建复合主键（外键: {}）...", tableName, foreignKeyColumn);

        // 检查表中的字段
        List<String> existingColumns = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + tableName)) {
            while (rs.next()) {
                existingColumns.add(rs.getString("Field").toLowerCase());
            }
        }

        // 确定子表标识字段（优先使用 id，其次 idx，最后 seq）
        String subTableIdColumn = null;
        if (existingColumns.contains("id")) {
            subTableIdColumn = "id";
        } else if (existingColumns.contains("idx")) {
            subTableIdColumn = "idx";
        } else if (existingColumns.contains("seq")) {
            subTableIdColumn = "seq";
        } else {
            // 如果都没有，创建一个 idx 字段
            log.info("子表 {} 没有标识字段，创建 idx 字段...", tableName);
            String addColumnSql = String.format(
                "ALTER TABLE %s ADD COLUMN idx INT NOT NULL DEFAULT 0",
                tableName
            );
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(addColumnSql);
                log.info("成功添加 idx 字段到子表 {}", tableName);
                subTableIdColumn = "idx";
            }
        }

        // 设置复合主键
        String compositePk = foreignKeyColumn + "," + subTableIdColumn;
        try {
            String setPkSql = String.format(
                "ALTER TABLE %s ADD PRIMARY KEY (%s, %s)",
                tableName, foreignKeyColumn, subTableIdColumn
            );
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(setPkSql);
                log.info("成功为子表 {} 设置复合主键 ({}, {})",
                        tableName, foreignKeyColumn, subTableIdColumn);
            }
        } catch (SQLException e) {
            // 如果主键已存在，忽略错误
            if (e.getMessage().contains("Multiple primary key")) {
                log.warn("子表 {} 已有主键，跳过主键创建", tableName);
            } else {
                throw e;
            }
        }

        return compositePk;
    }
}
