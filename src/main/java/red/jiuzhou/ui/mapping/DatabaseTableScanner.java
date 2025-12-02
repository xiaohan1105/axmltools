package red.jiuzhou.ui.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.DatabaseUtil;

import java.sql.*;
import java.util.*;

/**
 * 数据库表扫描器
 * 自动扫描数据库中的所有表结构，包括表名、字段、类型、注释等信息
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 1.0
 */
public class DatabaseTableScanner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTableScanner.class);

    /**
     * 特殊客户端表名映射
     * key: 客户端表名(不带client_前缀), value: 对应的服务端表名
     * 例如: quest表在客户端叫quest, 服务端叫server_quest
     */
    private static final Map<String, String> SPECIAL_CLIENT_TABLES = new HashMap<>();
    static {
        // 添加特殊表名映射: quest表客户端没有client_前缀
        SPECIAL_CLIENT_TABLES.put("quest", "server_quest");
        // 可以在这里添加更多特殊表名
    }

    /**
     * 表结构信息
     */
    public static class TableInfo {
        private String tableName;           // 表名
        private String tableComment;        // 表注释
        private List<ColumnInfo> columns;   // 字段列表
        private int rowCount;               // 数据行数（估算）
        private boolean isClientTable;      // 是否为客户端表

        // 层级信息
        private TableHierarchyHelper.TableHierarchy hierarchy;  // 表层级结构

        public TableInfo(String tableName) {
            this.tableName = tableName;
            this.columns = new ArrayList<>();
            // 判断是否为客户端表: 1. 以client_开头 2. 在特殊表名映射中
            this.isClientTable = tableName.startsWith("client_") || SPECIAL_CLIENT_TABLES.containsKey(tableName);
            this.hierarchy = TableHierarchyHelper.parseTableHierarchy(tableName);
        }

        // Getters and Setters
        public String getTableName() {
            return tableName;
        }

        public String getTableComment() {
            return tableComment;
        }

        public void setTableComment(String tableComment) {
            this.tableComment = tableComment;
        }

        public List<ColumnInfo> getColumns() {
            return columns;
        }

        public void addColumn(ColumnInfo column) {
            this.columns.add(column);
        }

        public int getRowCount() {
            return rowCount;
        }

        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }

        public boolean isClientTable() {
            return isClientTable;
        }

        /**
         * 获取表层级结构
         */
        public TableHierarchyHelper.TableHierarchy getHierarchy() {
            return hierarchy;
        }

        /**
         * 获取表层级
         */
        public TableHierarchyHelper.TableLevel getTableLevel() {
            return hierarchy.getLevel();
        }

        /**
         * 是否为主表
         */
        public boolean isMainTable() {
            return hierarchy.isMainTable();
        }

        /**
         * 是否为子表
         */
        public boolean isSubTable() {
            return hierarchy.isSubTable();
        }

        /**
         * 获取层级显示名称
         */
        public String getLevelDisplayName() {
            return hierarchy.getLevelDisplayName();
        }

        /**
         * 获取对应的服务端表名（去掉 client_ 前缀或使用特殊映射）
         */
        public String getServerTableName() {
            if (!isClientTable) {
                return null;
            }

            // 优先检查特殊表名映射
            if (SPECIAL_CLIENT_TABLES.containsKey(tableName)) {
                return SPECIAL_CLIENT_TABLES.get(tableName);
            }

            // 标准情况: 去掉client_前缀
            if (tableName.startsWith("client_")) {
                return tableName.substring("client_".length());
            }

            return null;
        }

        /**
         * 获取对应的客户端表名（添加 client_ 前缀或使用特殊映射反查）
         */
        public String getClientTableName() {
            if (isClientTable) {
                return tableName;
            }

            // 检查特殊表名反向映射: 服务端表名 -> 客户端表名
            for (Map.Entry<String, String> entry : SPECIAL_CLIENT_TABLES.entrySet()) {
                if (entry.getValue().equals(tableName)) {
                    return entry.getKey();  // 返回特殊客户端表名
                }
            }

            // 标准情况: 添加client_前缀
            return "client_" + tableName;
        }
    }

    /**
     * 字段信息
     */
    public static class ColumnInfo {
        private String columnName;      // 字段名
        private String dataType;        // 数据类型
        private String columnType;      // 完整类型（包含长度）
        private boolean nullable;       // 是否可为空
        private String columnDefault;   // 默认值
        private String comment;         // 字段注释
        private boolean isPrimaryKey;   // 是否主键
        private int ordinalPosition;    // 字段位置

        public ColumnInfo(String columnName) {
            this.columnName = columnName;
        }

        // Getters and Setters
        public String getColumnName() {
            return columnName;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public String getColumnType() {
            return columnType;
        }

        public void setColumnType(String columnType) {
            this.columnType = columnType;
        }

        public boolean isNullable() {
            return nullable;
        }

        public void setNullable(boolean nullable) {
            this.nullable = nullable;
        }

        public String getColumnDefault() {
            return columnDefault;
        }

        public void setColumnDefault(String columnDefault) {
            this.columnDefault = columnDefault;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }

        public void setPrimaryKey(boolean primaryKey) {
            isPrimaryKey = primaryKey;
        }

        public int getOrdinalPosition() {
            return ordinalPosition;
        }

        public void setOrdinalPosition(int ordinalPosition) {
            this.ordinalPosition = ordinalPosition;
        }

        /**
         * 获取类型显示文本
         */
        public String getTypeDisplay() {
            StringBuilder sb = new StringBuilder();
            sb.append(columnType != null ? columnType : dataType);
            if (!nullable) {
                sb.append(" NOT NULL");
            }
            if (isPrimaryKey) {
                sb.append(" PK");
            }
            return sb.toString();
        }
    }

    /**
     * 扫描数据库中的所有表
     *
     * @return 表信息列表
     */
    public static List<TableInfo> scanAllTables() {
        List<TableInfo> tables = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection()) {
            String dbName = DatabaseUtil.getDbName();

            log.info("开始扫描数据库表: {}", dbName);

            // 查询所有表
            String sql = "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS " +
                        "FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = ? " +
                        "ORDER BY TABLE_NAME";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, dbName);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableComment = rs.getString("TABLE_COMMENT");
                    int tableRows = rs.getInt("TABLE_ROWS");

                    TableInfo tableInfo = new TableInfo(tableName);
                    tableInfo.setTableComment(tableComment);
                    tableInfo.setRowCount(tableRows);

                    // 加载表的字段信息
                    loadTableColumns(conn, dbName, tableInfo);

                    tables.add(tableInfo);
                }
            }

            log.info("成功扫描 {} 个表", tables.size());

        } catch (Exception e) {
            log.error("扫描数据库表失败", e);
        }

        return tables;
    }

    /**
     * 加载表的字段信息
     */
    private static void loadTableColumns(Connection conn, String dbName, TableInfo tableInfo) {
        try {
            // 查询字段信息
            String sql = "SELECT " +
                        "COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, " +
                        "IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT, " +
                        "COLUMN_KEY, ORDINAL_POSITION " +
                        "FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                        "ORDER BY ORDINAL_POSITION";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, dbName);
                pstmt.setString(2, tableInfo.getTableName());
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    ColumnInfo columnInfo = new ColumnInfo(columnName);

                    columnInfo.setDataType(rs.getString("DATA_TYPE"));
                    columnInfo.setColumnType(rs.getString("COLUMN_TYPE"));
                    columnInfo.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                    columnInfo.setColumnDefault(rs.getString("COLUMN_DEFAULT"));
                    columnInfo.setComment(rs.getString("COLUMN_COMMENT"));
                    columnInfo.setPrimaryKey("PRI".equals(rs.getString("COLUMN_KEY")));
                    columnInfo.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));

                    tableInfo.addColumn(columnInfo);
                }
            }

        } catch (Exception e) {
            log.error("加载表字段信息失败: {}", tableInfo.getTableName(), e);
        }
    }

    /**
     * 查找客户端表对应的服务端表
     *
     * @param clientTableName 客户端表名
     * @param allTables 所有表列表
     * @return 服务端表信息，如果不存在返回null
     */
    public static TableInfo findServerTable(String clientTableName, List<TableInfo> allTables) {
        if (!clientTableName.startsWith("client_")) {
            return null;
        }

        String serverTableName = clientTableName.substring("client_".length());

        for (TableInfo table : allTables) {
            if (table.getTableName().equals(serverTableName)) {
                return table;
            }
        }

        return null;
    }

    /**
     * 获取所有客户端表
     */
    public static List<TableInfo> getClientTables(List<TableInfo> allTables) {
        List<TableInfo> clientTables = new ArrayList<>();
        for (TableInfo table : allTables) {
            if (table.isClientTable()) {
                clientTables.add(table);
            }
        }
        return clientTables;
    }

    /**
     * 快速获取所有表名列表（用于缓存验证）
     * 不加载字段信息，只获取表名
     *
     * @return 表名列表
     */
    public static List<String> getTableNameList() {
        List<String> tableNames = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection()) {
            String dbName = DatabaseUtil.getDbName();

            String sql = "SELECT TABLE_NAME " +
                        "FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = ? " +
                        "ORDER BY TABLE_NAME";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, dbName);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    tableNames.add(rs.getString("TABLE_NAME"));
                }
            }

        } catch (Exception e) {
            log.error("获取表名列表失败", e);
        }

        return tableNames;
    }

    /**
     * 表映射配对（增强版，包含匹配信息）
     */
    public static class TablePairResult {
        public TableInfo clientTable;
        public TableInfo serverTable;
        public double similarity;          // 综合质量 0-1
        public String matchMethod;         // 匹配方法：精确匹配/模糊匹配/手动配置/未匹配
        public boolean isMultipleMatch;    // 是否多对一映射

        // 增强质量详情
        public EnhancedMatchQualityCalculator.MatchQuality qualityDetail;

        public TablePairResult(TableInfo clientTable, TableInfo serverTable,
                              double similarity, String matchMethod) {
            this.clientTable = clientTable;
            this.serverTable = serverTable;
            this.similarity = similarity;
            this.matchMethod = matchMethod;
            this.isMultipleMatch = false;
        }
    }

    /**
     * 构建表映射配对（使用智能匹配）
     * 返回 List<TablePairResult> 包含匹配质量信息
     */
    public static List<TablePairResult> buildSmartTablePairs(List<TableInfo> allTables) {
        List<TablePairResult> pairs = new ArrayList<>();

        List<TableInfo> clientTables = getClientTables(allTables);
        List<TableInfo> serverTables = new ArrayList<>();

        // 收集所有服务端表
        for (TableInfo table : allTables) {
            if (!table.isClientTable()) {
                serverTables.add(table);
            }
        }

        log.info("开始智能匹配 {} 个客户端表到 {} 个服务端表",
                clientTables.size(), serverTables.size());

        // 统计各层级的表数量
        Map<TableHierarchyHelper.TableLevel, Integer> levelStats = new HashMap<>();
        for (TableInfo clientTable : clientTables) {
            TableHierarchyHelper.TableLevel level = clientTable.getTableLevel();
            levelStats.put(level, levelStats.getOrDefault(level, 0) + 1);
        }
        log.info("客户端表层级统计: 主表={}, 一级子表={}, 二级子表={}",
                levelStats.getOrDefault(TableHierarchyHelper.TableLevel.MAIN, 0),
                levelStats.getOrDefault(TableHierarchyHelper.TableLevel.LEVEL_1, 0),
                levelStats.getOrDefault(TableHierarchyHelper.TableLevel.LEVEL_2, 0));

        // 分离主表和子表
        List<TableInfo> mainTables = new ArrayList<>();
        List<TableInfo> subTables = new ArrayList<>();
        for (TableInfo table : clientTables) {
            if (table.getTableLevel() == TableHierarchyHelper.TableLevel.MAIN) {
                mainTables.add(table);
            } else {
                subTables.add(table);
            }
        }

        // 记录成功匹配的主表（用于子表依赖检查）
        Map<String, TableInfo> matchedMainTables = new HashMap<>();

        // 第一步：先匹配所有主表
        log.info("第一步：匹配 {} 个主表", mainTables.size());
        for (TableInfo clientTable : mainTables) {
            log.debug("处理客户端主表: {}", clientTable.getTableName());

            SmartTableMatcher.MatchResult matchResult =
                SmartTableMatcher.smartMatch(clientTable, serverTables);

            if (matchResult != null) {
                // 找到对应的TableInfo
                TableInfo serverTable = null;
                for (TableInfo table : allTables) {
                    if (table.getTableName().equals(matchResult.serverTable)) {
                        serverTable = table;
                        break;
                    }
                }

                if (serverTable != null) {
                    TablePairResult pair = new TablePairResult(
                        clientTable,
                        serverTable,
                        matchResult.similarity,
                        matchResult.matchMethod
                    );
                    pair.qualityDetail = matchResult.qualityDetail;
                    pairs.add(pair);

                    // 记录成功匹配的主表
                    String baseName = clientTable.getTableName().replace("client_", "");
                    matchedMainTables.put(baseName, serverTable);
                    log.debug("✅ 主表匹配成功: {} → {}", clientTable.getTableName(), serverTable.getTableName());
                }
            } else {
                // 未找到匹配
                TablePairResult pair = new TablePairResult(
                    clientTable,
                    null,
                    0.0,
                    "未匹配"
                );
                pairs.add(pair);
                log.warn("❌ 客户端主表未找到匹配: {}", clientTable.getTableName());
            }
        }

        // 第二步：只匹配主表已匹配成功的子表
        log.info("第二步：匹配 {} 个子表（仅匹配主表已成功的子表）", subTables.size());
        int skippedSubTables = 0;
        for (TableInfo clientTable : subTables) {
            // 获取子表的主表名
            TableHierarchyHelper.TableHierarchy hierarchy =
                new TableHierarchyHelper.TableHierarchy(clientTable.getTableName());
            String rootTableName = hierarchy.getRootTableName();  // 获取根主表名
            String rootBaseName = rootTableName.replace("client_", "");

            // 检查主表是否匹配成功
            if (!matchedMainTables.containsKey(rootBaseName)) {
                // 主表未匹配，跳过此子表
                log.debug("⏭️  跳过子表 {} (主表 {} 未匹配)", clientTable.getTableName(), rootTableName);
                skippedSubTables++;

                // 仍然添加到结果中，但标记为"主表未匹配"
                TablePairResult pair = new TablePairResult(
                    clientTable,
                    null,
                    0.0,
                    "主表未匹配"
                );
                pairs.add(pair);
                continue;
            }

            // 主表已匹配，继续匹配此子表
            log.debug("处理客户端子表: {} (主表: {})", clientTable.getTableName(), rootTableName);

            SmartTableMatcher.MatchResult matchResult =
                SmartTableMatcher.smartMatch(clientTable, serverTables);

            if (matchResult != null) {
                // 找到对应的TableInfo
                TableInfo serverTable = null;
                for (TableInfo table : allTables) {
                    if (table.getTableName().equals(matchResult.serverTable)) {
                        serverTable = table;
                        break;
                    }
                }

                if (serverTable != null) {
                    TablePairResult pair = new TablePairResult(
                        clientTable,
                        serverTable,
                        matchResult.similarity,
                        matchResult.matchMethod
                    );
                    pair.qualityDetail = matchResult.qualityDetail;
                    pairs.add(pair);
                    log.debug("✅ 子表匹配成功: {} → {}", clientTable.getTableName(), serverTable.getTableName());
                }
            } else {
                // 未找到匹配
                TablePairResult pair = new TablePairResult(
                    clientTable,
                    null,
                    0.0,
                    "未匹配"
                );
                pairs.add(pair);
                log.warn("❌ 客户端子表未找到匹配: {}", clientTable.getTableName());
            }
        }

        if (skippedSubTables > 0) {
            log.info("跳过了 {} 个子表（因主表未匹配）", skippedSubTables);
        }

        // 检测多对一映射
        detectManyToOneMapping(pairs);

        log.info("完成智能匹配，找到 {} 对表映射关系", pairs.size());
        return pairs;
    }

    /**
     * 检测并标记多对一映射
     */
    private static void detectManyToOneMapping(List<TablePairResult> pairs) {
        Map<String, List<TablePairResult>> serverTableMap = new HashMap<>();

        for (TablePairResult pair : pairs) {
            if (pair.serverTable != null) {
                String serverTableName = pair.serverTable.getTableName();
                serverTableMap.computeIfAbsent(serverTableName, k -> new ArrayList<>())
                             .add(pair);
            }
        }

        // 标记多对一的情况
        for (Map.Entry<String, List<TablePairResult>> entry : serverTableMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                log.info("检测到多对一映射: {} ← {}",
                        entry.getKey(),
                        entry.getValue().stream()
                            .map(p -> p.clientTable.getTableName())
                            .collect(java.util.stream.Collectors.joining(", ")));

                for (TablePairResult pair : entry.getValue()) {
                    pair.isMultipleMatch = true;
                }
            }
        }
    }

    /**
     * 构建表映射配对（旧版本，保留兼容性）
     * 返回 Map<客户端表, 服务端表>
     */
    @Deprecated
    public static Map<TableInfo, TableInfo> buildTablePairs(List<TableInfo> allTables) {
        Map<TableInfo, TableInfo> pairs = new LinkedHashMap<>();

        List<TableInfo> clientTables = getClientTables(allTables);

        for (TableInfo clientTable : clientTables) {
            TableInfo serverTable = findServerTable(clientTable.getTableName(), allTables);
            pairs.put(clientTable, serverTable);
        }

        log.info("找到 {} 对表映射关系", pairs.size());
        return pairs;
    }

    /**
     * 对比两个表的字段差异
     *
     * @return 字段对比结果
     */
    public static FieldCompareResult compareFields(TableInfo clientTable, TableInfo serverTable) {
        FieldCompareResult result = new FieldCompareResult();

        if (clientTable == null || serverTable == null) {
            return result;
        }

        Set<String> clientFieldNames = new HashSet<>();
        Set<String> serverFieldNames = new HashSet<>();

        // 收集字段名
        for (ColumnInfo col : clientTable.getColumns()) {
            clientFieldNames.add(col.getColumnName());
        }
        for (ColumnInfo col : serverTable.getColumns()) {
            serverFieldNames.add(col.getColumnName());
        }

        // 找出共同字段
        result.commonFields = new ArrayList<>();
        for (ColumnInfo clientCol : clientTable.getColumns()) {
            String fieldName = clientCol.getColumnName();
            if (serverFieldNames.contains(fieldName)) {
                // 找到对应的服务端字段
                ColumnInfo serverCol = null;
                for (ColumnInfo col : serverTable.getColumns()) {
                    if (col.getColumnName().equals(fieldName)) {
                        serverCol = col;
                        break;
                    }
                }
                result.commonFields.add(new FieldPair(clientCol, serverCol));
            }
        }

        // 找出客户端独有字段
        result.clientOnlyFields = new ArrayList<>();
        for (ColumnInfo clientCol : clientTable.getColumns()) {
            if (!serverFieldNames.contains(clientCol.getColumnName())) {
                result.clientOnlyFields.add(clientCol);
            }
        }

        // 找出服务端独有字段
        result.serverOnlyFields = new ArrayList<>();
        for (ColumnInfo serverCol : serverTable.getColumns()) {
            if (!clientFieldNames.contains(serverCol.getColumnName())) {
                result.serverOnlyFields.add(serverCol);
            }
        }

        return result;
    }

    /**
     * 字段对比结果
     */
    public static class FieldCompareResult {
        public List<FieldPair> commonFields = new ArrayList<>();         // 共同字段
        public List<ColumnInfo> clientOnlyFields = new ArrayList<>();    // 客户端独有
        public List<ColumnInfo> serverOnlyFields = new ArrayList<>();    // 服务端独有

        public int getCommonCount() {
            return commonFields.size();
        }

        public int getClientOnlyCount() {
            return clientOnlyFields.size();
        }

        public int getServerOnlyCount() {
            return serverOnlyFields.size();
        }
    }

    /**
     * 字段配对
     */
    public static class FieldPair {
        public ColumnInfo clientField;
        public ColumnInfo serverField;

        public FieldPair(ColumnInfo clientField, ColumnInfo serverField) {
            this.clientField = clientField;
            this.serverField = serverField;
        }

        /**
         * 判断字段类型是否匹配
         */
        public boolean isTypeMatched() {
            if (clientField == null || serverField == null) {
                return false;
            }
            return clientField.getColumnType().equals(serverField.getColumnType());
        }
    }
}
