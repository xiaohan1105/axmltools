package red.jiuzhou.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.StringUtils;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.dbxml.TableConf;

import javax.sql.DataSource;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @className: red.jiuzhou.util.DatabaseUtil.java
 * @description: 数据库工具类
 * @author: yanxq
 * @date:  2025-03-28 14:33
 * @version V1.0
 */
public class DatabaseUtil {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUtil.class);
    private static final DataSource dataSource;
    private static final JdbcTemplate jdbcTemplate;
    private static final DataSourceTransactionManager transactionManager;
    // 每页显示的行数
    public static final int ROWS_PER_PAGE = 15;
    private static final Map<String, JdbcTemplate> jdbcTemplateCache = new HashMap<>();

    // 静态代码块初始化
    static {
        // 1. 读取 application.yml 配置
        Properties properties = loadYamlProperties("application.yml");

        // 2. 创建 DataSource (MySQL 的 JDBC URL 格式)
        dataSource = DataSourceBuilder.create()
                .url(properties.getProperty("spring.datasource.url"))
                .username(properties.getProperty("spring.datasource.username"))
                .password(properties.getProperty("spring.datasource.password"))
                .driverClassName(properties.getProperty("spring.datasource.driver-class-name"))
                .build();

        // 3. 创建 JdbcTemplate
        jdbcTemplate = new JdbcTemplate(dataSource);

        // 4. 创建事务管理器
        transactionManager = new DataSourceTransactionManager(dataSource);
    }

    public static JdbcTemplate getJdbcTemplate(String databaseName) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            return jdbcTemplate; // 没传就用默认
        }

        // 先查缓存
        if (jdbcTemplateCache.containsKey(databaseName)) {
            return jdbcTemplateCache.get(databaseName);
        }

        try {
            // 读取默认配置
            Properties properties = loadYamlProperties("application.yml");
            String urlTemplate = properties.getProperty("spring.datasource.url");

            // 修改 URL 中的数据库名
            String modifiedUrl = urlTemplate.replaceFirst(
                    "(jdbc:mysql://[^/]+:\\d{1,5}/)([^/?]+)",
                    "$1" + databaseName
            );

            log.info("modifiedUrl:::::::::::{}", modifiedUrl);
            DataSource dataSource = DataSourceBuilder.create()
                    .url(modifiedUrl)
                    .username(properties.getProperty("spring.datasource.username"))
                    .password(properties.getProperty("spring.datasource.password"))
                    .driverClassName(properties.getProperty("spring.datasource.driver-class-name"))
                    .build();

            JdbcTemplate newJdbcTemplate = new JdbcTemplate(dataSource);

            jdbcTemplateCache.put(databaseName, newJdbcTemplate);
            return newJdbcTemplate;
        } catch (Exception e) {
            log.error("切换数据库失败: {}", databaseName, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 JdbcTemplate
     */
    public static JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    /**
     * 获取数据库连接
     * 注意：使用完毕后需要关闭连接
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 获取事务状态（开启事务）
     */
    public static TransactionStatus beginTransaction() {
        TransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        return transactionManager.getTransaction(transactionDefinition);
    }

    /**
     * 提交事务
     */
    public static void commitTransaction(TransactionStatus status) {
        transactionManager.commit(status);
    }

    /**
     * 回滚事务
     */
    public static void rollbackTransaction(TransactionStatus status) {
        transactionManager.rollback(status);
    }

    /**
     * 读取 application.yml 配置
     */
    private static Properties loadYamlProperties(String yamlFile) {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ClassPathResource(yamlFile));
        return yamlFactory.getObject();
    }

    /**
     * 删除表中主键重复的记录，只保留第一条
     * @param tableName 表名
     * @return 删除的重复记录数量
     */
    public static int removeDuplicatePrimaryKeys(String tableName) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate();

        try {
            // 获取表的主键列名
            String primaryKeyColumn = getPrimaryKeyColumn(tableName);
            if (primaryKeyColumn == null) {
                log.warn("表 {} 没有主键，跳过去重", tableName);
                return 0;
            }

            log.info("开始清理表 {} 的重复主键（主键列：{}）", tableName, primaryKeyColumn);

            // 查找重复的主键
            String findDuplicatesSql = String.format(
                "SELECT `%s`, COUNT(*) as cnt FROM `%s` GROUP BY `%s` HAVING cnt > 1",
                primaryKeyColumn, tableName, primaryKeyColumn
            );

            List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(findDuplicatesSql);

            if (duplicates.isEmpty()) {
                log.info("表 {} 没有重复的主键", tableName);
                return 0;
            }

            log.warn("发现表 {} 有 {} 个重复的主键值", tableName, duplicates.size());

            int totalDeleted = 0;

            // 对每个重复的主键，删除除第一条以外的所有记录
            // 使用临时表来避免MySQL的"You can't specify target table for update in FROM clause"错误
            for (Map<String, Object> duplicate : duplicates) {
                Object pkValue = duplicate.get(primaryKeyColumn);
                int count = ((Number) duplicate.get("cnt")).intValue();

                try {
                    // 方法1: 使用子查询和临时表删除重复记录
                    String deleteSql = String.format(
                        "DELETE FROM `%s` WHERE `%s` = ? " +
                        "AND CONCAT_WS(',', `%s`, IFNULL(CAST(id AS CHAR), '')) NOT IN ( " +
                        "  SELECT * FROM ( " +
                        "    SELECT CONCAT_WS(',', `%s`, IFNULL(CAST(MIN(id) AS CHAR), '')) " +
                        "    FROM `%s` WHERE `%s` = ? " +
                        "  ) AS temp " +
                        ")",
                        tableName, primaryKeyColumn, primaryKeyColumn,
                        primaryKeyColumn, tableName, primaryKeyColumn
                    );

                    int deleted;
                    try {
                        deleted = jdbcTemplate.update(deleteSql, pkValue, pkValue);
                    } catch (Exception e1) {
                        // 如果上面的方法失败，使用更简单的方法
                        log.debug("尝试第二种删除方式: {}", e1.getMessage());

                        // 方法2: 先查找要保留的记录，然后删除其他的
                        // 查找该主键值的所有记录，获取最小的id（或其他唯一标识）
                        String findMinIdSql = String.format(
                            "SELECT MIN(id) as min_id FROM `%s` WHERE `%s` = ?",
                            tableName, primaryKeyColumn
                        );

                        Long minId = jdbcTemplate.queryForObject(findMinIdSql, Long.class, pkValue);

                        if (minId != null) {
                            // 删除除了最小id之外的所有记录
                            String deleteByIdSql = String.format(
                                "DELETE FROM `%s` WHERE `%s` = ? AND id != ?",
                                tableName, primaryKeyColumn
                            );
                            deleted = jdbcTemplate.update(deleteByIdSql, pkValue, minId);
                        } else {
                            // 如果表没有id列，使用LIMIT方式
                            log.debug("表没有id列，使用保守删除方式");
                            deleted = 0;

                            // 重复删除，每次删除一条（除了第一条）
                            for (int i = 1; i < count; i++) {
                                String deleteLimitSql = String.format(
                                    "DELETE FROM `%s` WHERE `%s` = ? LIMIT 1",
                                    tableName, primaryKeyColumn
                                );
                                deleted += jdbcTemplate.update(deleteLimitSql, pkValue);
                            }
                        }
                    }

                    totalDeleted += deleted;
                    log.info("删除主键值 {} 的 {} 条重复记录（共{}条，保留1条）", pkValue, deleted, count);

                } catch (Exception e) {
                    log.error("删除主键 {} 的重复记录失败: {}", pkValue, e.getMessage());
                }
            }

            log.info("表 {} 去重完成，共删除 {} 条重复记录", tableName, totalDeleted);
            return totalDeleted;

        } catch (Exception e) {
            log.error("清理表 {} 的重复主键失败: {}", tableName, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 获取表的主键列名
     */
    private static String getPrimaryKeyColumn(String tableName) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate();

        try {
            String sql = "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY'";

            List<String> primaryKeys = jdbcTemplate.queryForList(sql, String.class, tableName);

            if (primaryKeys.isEmpty()) {
                return null;
            }

            // 返回第一个主键列（通常表只有一个主键或联合主键的第一列）
            return primaryKeys.get(0);

        } catch (Exception e) {
            log.error("获取表 {} 的主键列失败: {}", tableName, e.getMessage());
            return null;
        }
    }

    /**
     * 判断列是否为数值类型
     */
    private static boolean isNumericType(String tableName, String columnName) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate();

        try {
            String sql = "SELECT DATA_TYPE FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";

            String dataType = jdbcTemplate.queryForObject(sql, String.class, tableName, columnName);

            return dataType != null && (
                dataType.toLowerCase().contains("int") ||
                dataType.toLowerCase().contains("decimal") ||
                dataType.toLowerCase().contains("numeric") ||
                dataType.toLowerCase().contains("double") ||
                dataType.toLowerCase().contains("float")
            );

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除表中的数据
     * @param tableName 表名，可以包含WHERE条件（如 "table_name where condition"）
     * @return 是否成功
     */
    public static boolean delTable(String tableName){
        TransactionStatus transactionStatus = beginTransaction();
        // 获取 JdbcTemplate 实例
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

        try {
            // 先检查表是否存在
            String checkTableName = tableName;
            if (tableName.toLowerCase().contains(" where ")) {
                // 提取表名（WHERE之前的部分）
                checkTableName = tableName.substring(0, tableName.toLowerCase().indexOf(" where ")).trim();
            }

            // 检查表是否存在（如果检查失败会抛异常，被外层catch捕获并准确报告）
            if (!tableExists(checkTableName)) {
                log.warn("表 {} 不存在，无需清空（可能DDL创建失败或表名配置错误）", checkTableName);
                rollbackTransaction(transactionStatus);
                // 表不存在时返回true，因为目标状态（空表）已达成
                // 如果是表名配置错误，后续的插入操作会失败并准确报错
                return true;
            }

            // 判断是否包含WHERE条件
            String deleteSql;
            if (tableName.toLowerCase().contains(" where ")) {
                // 包含WHERE条件，使用DELETE FROM
                deleteSql = "DELETE FROM " + tableName;
                log.debug("执行条件删除: {}", deleteSql);
            } else {
                // 不包含WHERE条件，优先使用TRUNCATE（更快且重置自增ID）
                // 但如果表有外键约束，则回退到DELETE
                try {
                    deleteSql = "TRUNCATE TABLE `" + tableName + "`";
                    jdbcTemplate.execute(deleteSql);
                    log.debug("使用TRUNCATE清空表: {}", tableName);
                    commitTransaction(transactionStatus);
                    return true;
                } catch (Exception truncateEx) {
                    // TRUNCATE失败（可能是外键约束），使用DELETE
                    log.debug("TRUNCATE失败，改用DELETE: {}", truncateEx.getMessage());
                    deleteSql = "DELETE FROM `" + tableName + "`";
                }
            }

            int deletedRows = jdbcTemplate.update(deleteSql);
            log.info("成功清空表 {} - 删除了 {} 行数据", tableName, deletedRows);
            commitTransaction(transactionStatus);
            return true;

        } catch (Exception e) {
            rollbackTransaction(transactionStatus);
            log.error("清空表失败 {}: {}", tableName, e.getMessage(), e);
            return false;
        }
    }

    public static void batchInsert(String tableName, List<Map<String, String>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        // **1. 校验表名，防止 SQL 注入**
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("非法表名: " + tableName);
        }

        // **2. 计算完整的字段集合**（遍历所有 Map，确保字段不丢）
        Set<String> columnSet = new LinkedHashSet<>();
        for (Map<String, String> row : dataList) {
            columnSet.addAll(row.keySet());  // 获取所有可能的字段
        }
        List<String> columns = new ArrayList<>(columnSet);
        List<String> wrappedColumns = columns.stream()
                .map(column -> "`" + column + "`")
                .collect(Collectors.toList());

        // **3. 生成 SQL 语句**
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName,
                String.join(",", wrappedColumns),
                String.join(",", Collections.nCopies(columns.size(), "?")));

        try {
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public int getBatchSize() {
                    return dataList.size();
                }

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Map<String, String> row = dataList.get(i);
                    int index = 1;
                    for (String column : columns) {  // 遍历完整字段
                        Object value = row.getOrDefault(column, null); // **如果字段缺失，填 NULL**
                        ps.setObject(index++, value);
                    }
                }
            });
        } catch (Exception e) {
            log.error("批量插入数据失败: {}", dataList.toString(), e);
            throw new RuntimeException(e);
        }

        System.out.println("批量插入 " + dataList.size() + " 条数据成功");
    }



    /**
     * 获取总记录数
     */
    public static int getTotalRowCount(String tabName) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tabName, Integer.class);
    }
    public static List<Map<String, Object>> fetchPageData(String tabName, int pageIndex, String whereCondition, String tabFilePath) {
        int offset = pageIndex * ROWS_PER_PAGE;
        TableConf tale = TabConfLoad.getTale(tabName, tabFilePath);
        if (tale == null) {
            return Collections.emptyList();
        }
        String sql = TabConfLoad.getTale(tabName, tabFilePath).getSql();

        if ("world".equals(tabName)) {
            sql = sql.replaceFirst("(?i)where\\s+[^o]+(?=\\s+order)", "");
        }

        if (whereCondition != null && !whereCondition.trim().isEmpty()) {
            // 用正则精准匹配 FROM 后的表名
            // 例子：SELECT * FROM airline AS a ORDER BY id
            Pattern pattern = Pattern.compile("(?i)(from\\s+[^\\s,]+(?:\\s+as\\s+\\w+)?)(.*)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(sql);
            if (matcher.find()) {
                String fromPart = matcher.group(1); // FROM airline 或 FROM airline AS a
                String restPart = matcher.group(2); // 后续的ORDER BY等
                sql = sql.replaceFirst(Pattern.quote(fromPart), fromPart + " " + whereCondition);
                sql = sql + " LIMIT ? OFFSET ?";
            } else {
                // 正常走，不然LIMIT OFFSET也要加
                sql = sql + " LIMIT ? OFFSET ?";
            }
        } else {
            sql = sql + " LIMIT ? OFFSET ?";
        }

        return DatabaseUtil.getJdbcTemplate().queryForList(sql, ROWS_PER_PAGE, offset);
    }

    public static List<String> getTableNamesByPrefix(String prefix) {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE ?";
        return jdbcTemplate.queryForList(sql, String.class, prefix + "%");
    }

    public static List<String> getTableNamesByPrefix(String prefix, String databaseName) {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name LIKE ?";
        return DatabaseUtil.getJdbcTemplate(databaseName).queryForList(sql, String.class, databaseName, prefix + "%");
    }

    public static boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return count != null && count > 0;
    }

    public static boolean tableExists(String tableName, String databaseName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
        Integer count = getJdbcTemplate(databaseName).queryForObject(sql, Integer.class, databaseName, tableName);
        return count != null && count > 0;
    }
    public static List<String> listAllDatabases() {
        String sql = "SHOW DATABASES";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public static String getDbName(){
        return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    }

    public static String getTableDDL(String tableName) {
        // 获取原始的 CREATE TABLE 语句
        String sql = "SHOW CREATE TABLE " + tableName;
        String ddl = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getString("Create Table"));

        // 使用正则表达式提取字段名
        Pattern pattern = Pattern.compile("`(\\w+)`\\s+");  // 匹配字段名
        Matcher matcher = pattern.matcher(ddl);

        StringBuilder cleanedDDL = new StringBuilder("CREATE TABLE `" + tableName + "` (\n");

        // 遍历所有匹配的字段名并构建简化后的 DDL
        while (matcher.find()) {
            cleanedDDL.append("  `").append(matcher.group(1)).append("`,\n");
        }

        // 去除最后一个多余的逗号
        if (cleanedDDL.length() > 0) {
            // 去除最后的逗号和换行
            cleanedDDL.setLength(cleanedDDL.length() - 2);
        }

        cleanedDDL.append("\n);");

        return cleanedDDL.toString();
    }

    public static JSONRecord getDistinctValuesWithCount(String tableName, boolean subTab, String fields) throws SQLException {
        JSONRecord result = new JSONRecord();
        String property = YamlUtils.getProperty("include-fields." + tableName);
        log.info("subTab:{}, fields: {}", subTab, fields);
        if(subTab && fields != null){
            property = fields;
        }
        log.info("property: " + property);
        Map<String, String> fieldTypeMap = property != null
                ? Arrays.stream(property.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.split("~", 4))
                .filter(arr -> arr.length >= 1 && !arr[0].trim().isEmpty())
                .collect(Collectors.toMap(
                        arr -> arr[0].trim().toLowerCase(), // 字段名
                        arr -> (arr.length >= 2 && !arr[1].trim().isEmpty())
                                ? arr[1].trim().toLowerCase() // 类型
                                : "string" // 默认类型
                ))
                : Collections.emptyMap();
        log.info("fieldTypeMap: " + fieldTypeMap);
        Map<String, String> fieldValueMap = property != null
                ? Arrays.stream(property.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.split("~", 4))
                .filter(arr -> arr.length >= 1 && !arr[0].trim().isEmpty())
                .collect(Collectors.toMap(
                        arr -> arr[0].trim().toLowerCase(),
                        arr -> (arr.length >= 3 && !arr[2].trim().isEmpty())
                                ? arr[2].trim()
                                : ""
                ))
                : Collections.emptyMap();

        Map<String, String> fieldValueMap2 = property != null
                ? Arrays.stream(property.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.split("~", 4))
                .filter(arr -> arr.length >= 1 && !arr[0].trim().isEmpty())
                .collect(Collectors.toMap(
                        arr -> arr[0].trim().toLowerCase(),
                        arr -> (arr.length >= 4 && !arr[3].trim().isEmpty())
                                ? arr[3].trim()
                                : ""
                ))
                : Collections.emptyMap();

        log.info("fieldValueMap: " + fieldValueMap);
        Set<String> includeFieldSet = fieldTypeMap.keySet();

        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new SQLException("DataSource is null");
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    if (includeFieldSet.isEmpty() || !includeFieldSet.contains(columnName.toLowerCase())) {
                        continue; // 如果设置了 include 列表，且当前字段不在其中，就跳过
                    }

                    String sql = String.format("SELECT %s, COUNT(*) AS cnt FROM %s GROUP BY %s", columnName, tableName,  columnName);

                    if(StringUtils.hasLength(fieldValueMap.get(columnName.toLowerCase()))){
                        // 构造 SQL：统计每个值的数量
                        sql = String.format("SELECT %s, COUNT(*) AS cnt FROM %s WHERE %s LIKE '%s' GROUP BY %s", columnName, tableName, columnName,
                                fieldValueMap.get(columnName.toLowerCase()).replace("*", "%"), columnName);
                    }

                    if("string".equals(fieldTypeMap.get(columnName.toLowerCase()))){
                        sql = sql + " ORDER BY cnt DESC";
                    }else{
                        sql = sql + " ORDER BY CAST("+columnName+" AS UNSIGNED) DESC";
                    }

                    if(StringUtils.hasLength(fieldValueMap2.get(columnName.toLowerCase()))){
                        String likeVal = fieldValueMap2.get(columnName.toLowerCase()).replace("*", "%");
                        String sp = "-1";
                        if(likeVal.startsWith("%")){
                            sp = "1";
                        }

                        sql = String.format("SELECT SUBSTRING_INDEX(%s,'%s',%s) AS name_prefix,COUNT(*) AS cnt " +
                                "FROM %s WHERE %s LIKE '%s' GROUP BY name_prefix",
                                columnName,likeVal.replace("%", ""), sp, tableName, columnName, likeVal);

                        if("string".equals(fieldTypeMap.get(columnName.toLowerCase()))){
                            sql = sql + " ORDER BY cnt DESC";
                        }else{
                            sql = sql + " ORDER BY CAST("+columnName+" AS UNSIGNED) DESC";
                        }
                    }


                    log.info("sql::::::::::{}", sql);
                    try {
                        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                        List<Map<String, Object>> valueCountList = null;
                        if(!StringUtils.hasLength(fieldValueMap2.get(columnName.toLowerCase()))){
                            valueCountList = rows.stream()
//                                    .filter(row -> row.get(columnName) != null)
                                    .map(row -> {
                                        Map<String, Object> map = new LinkedHashMap<>();
                                        map.put("value", row.get(columnName) == null ? "NULL" :row.get(columnName).toString().trim());
                                        map.put("count", row.get("cnt"));
                                        return map;
                                    })
                                    .collect(Collectors.toList());
                        }else{
                            valueCountList = rows.stream()
//                                    .filter(row -> row.get("name_prefix") != null)
                                    .map(row -> {
                                        Map<String, Object> map = new LinkedHashMap<>();
                                        map.put("value", row.get("name_prefix") == null ? "NULL" :row.get("name_prefix").toString().trim());
                                        map.put("count", row.get("cnt"));
                                        return map;
                                    })
                                    .collect(Collectors.toList());
                        }
                        if(!valueCountList.isEmpty()){
                            result.put(columnName, valueCountList);
                        }
                    } catch (Exception e) {
                        result.put(columnName, Collections.singletonList("ERROR: " + e.getMessage()));
                    }
                }
            }
        }

        return result;
    }


    public static void executeSqlScript(String filePath) throws SQLException {
        Connection conn = null;
        try {
            // 检查文件是否存在且不为空
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("SQL文件不存在: " + filePath);
            }

            // 检查文件大小,如果为空则跳过执行
            if (file.length() == 0) {
                log.warn("SQL文件为空,跳过执行: {}", filePath);
                return;
            }

            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            ScriptUtils.executeSqlScript(conn, new FileSystemResource(filePath));
            // 全部成功后提交事务
            conn.commit();
        } catch (Exception ex) {
            if (conn != null) {
                // 失败则回滚
                conn.rollback();
            }
            throw new RuntimeException("SQL 执行失败，已回滚", ex);
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    public static List<String> getColumnNamesFromDb(String tableName) {
        return jdbcTemplate.query("SELECT * FROM " + tableName + " LIMIT 1", rs -> {
            ResultSetMetaData metaData = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                columns.add(metaData.getColumnName(i));
            }
            return columns;
        });
    }

    /**
     * 获取指定表中某字段的最大长度（仅适用于 VARCHAR 类型字段）
     * @param tableName 表名
     * @param columnName 字段名
     * @return 字段的最大长度（如 VARCHAR(255) 返回 255）
     * @throws Exception 如果字段不存在
     */
    public static int getColumnLength(String tableName, String columnName)  {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, tableName, columnName);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException(String.format("字段 [%s.%s] 不存在！", tableName, columnName));
        }
    }

    /**
     * 如果字段类型是 VARCHAR 且长度不足，则自动修改为目标长度
     * @param tableName 表名
     * @param columnName 字段名
     * @param requiredLength 期望长度
     * @throws Exception 字段不存在或 SQL 执行失败
     */
    public static void ensureVarcharLengthIfNeeded(String tableName, String columnName, int requiredLength) throws Exception {
        String typeSql = "SELECT DATA_TYPE FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        String dataType = jdbcTemplate.queryForObject(typeSql, String.class, tableName, columnName);
        if (!"varchar".equalsIgnoreCase(dataType)) {
            log.warn("字段 [{}] 不是 VARCHAR 类型，跳过调整", columnName);
            return;
        }

        int currentLength = getColumnLength(tableName, columnName);
        if (currentLength < requiredLength) {
            String alterSql = String.format("ALTER TABLE `%s` MODIFY COLUMN `%s` VARCHAR(%d)",
                    tableName, columnName, requiredLength);
            jdbcTemplate.execute(alterSql);
            log.info("已将字段 [{}] 长度由 {} 调整为 {}", columnName, currentLength, requiredLength);
        }
    }
    public static void main(String[] args) throws Exception {
        int len = getColumnLength("client_strings_item", "body");
        System.out.println(len);
        ensureVarcharLengthIfNeeded("client_strings_item", "body", 2048);

        len = getColumnLength("client_strings_item", "body");
        System.out.println(len);
    }
}
