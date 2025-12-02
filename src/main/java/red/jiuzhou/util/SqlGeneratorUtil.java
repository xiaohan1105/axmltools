package red.jiuzhou.util;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.stream.Collectors;

public class SqlGeneratorUtil {

    public static List<String> getTableColumns(JdbcTemplate jdbcTemplate, String tableName, String schema) {
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        return jdbcTemplate.query(sql, new Object[]{schema, tableName}, (rs, rowNum) -> rs.getString("COLUMN_NAME"));
    }

    public static List<String> getPrimaryKeys(JdbcTemplate jdbcTemplate, String tableName, String schema) {
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY'";
        return jdbcTemplate.query(sql, new Object[]{schema, tableName}, (rs, rowNum) -> rs.getString("COLUMN_NAME"));
    }

    public static Map<String, Object> getRandomRow(JdbcTemplate jdbcTemplate, String tableName) {
        String sql = "SELECT * FROM `" + tableName + "` ORDER BY RAND() LIMIT 1";
        return jdbcTemplate.queryForMap(sql);
    }

    public static String buildInsertFromRandomRow(String tableName) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        String schema = DatabaseUtil.getDbName();
        List<String> columns = getTableColumns(jdbcTemplate, tableName, schema);
        Map<String, Object> row;
        try {
            row = getRandomRow(jdbcTemplate, tableName);
        } catch (Exception e) {
            return "-- 无法获取随机数据：可能表无数据或表名错误";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO `").append(tableName).append("` (")
                .append(columns.stream().map(col -> "`" + col + "`").collect(Collectors.joining(", ")))
                .append(") VALUES (");

        List<String> values = columns.stream()
                .map(col -> toSqlValue(row.get(col)))
                .collect(Collectors.toList());

        sql.append(String.join(", ", values)).append(");");
        return sql.toString();
    }

    public static String buildUpdateFromRandomRow(String tableName) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        String schema = DatabaseUtil.getDbName();
        List<String> columns = getTableColumns(jdbcTemplate, tableName, schema);
        List<String> primaryKeys = getPrimaryKeys(jdbcTemplate, tableName, schema);
        if (primaryKeys.isEmpty()) {
            // 没主键时用第一个字段
            if (columns.isEmpty()) return "-- 表无字段";
            primaryKeys = Collections.singletonList(columns.get(0));
        }


        Map<String, Object> row;
        try {
            row = getRandomRow(jdbcTemplate, tableName);
        } catch (Exception e) {
            return "-- 无法获取随机数据：可能表无数据或表名错误";
        }

        List<String> setParts = new ArrayList<>();
        for (String col : columns) {
            if (primaryKeys.contains(col)) continue;
            setParts.add("`" + col + "` = " + toSqlValue(row.get(col)));
        }

        List<String> whereParts = primaryKeys.stream()
                .map(pk -> "`" + pk + "` = " + toSqlValue(row.get(pk)))
                .collect(Collectors.toList());

        return "UPDATE `" + tableName + "` SET " +
                String.join(", ", setParts) +
                " WHERE " + String.join(" AND ", whereParts) + ";";
    }

    public static String buildDeleteFromRandomRow(String tableName) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        String schema = DatabaseUtil.getDbName();
        List<String> columns = getTableColumns(jdbcTemplate, tableName, schema);
        List<String> primaryKeys = getPrimaryKeys(jdbcTemplate, tableName, schema);
        if (columns.isEmpty()) return "-- 表无字段";

        if (primaryKeys.isEmpty()) {
            primaryKeys = Collections.singletonList(columns.get(0));
        }

        Map<String, Object> row;
        try {
            row = getRandomRow(jdbcTemplate, tableName);
        } catch (Exception e) {
            return "-- 无法获取随机数据：可能表无数据或表名错误";
        }

        List<String> whereParts = primaryKeys.stream()
                .map(pk -> "`" + pk + "` = " + toSqlValue(row.get(pk)))
                .collect(Collectors.toList());

        return "DELETE FROM `" + tableName + "` WHERE " + String.join(" AND ", whereParts) + ";";
    }

    public static String buildSelectByRandomPrimaryKey(String tableName) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        String schema = DatabaseUtil.getDbName();
        List<String> columns = getTableColumns(jdbcTemplate, tableName, schema);
        List<String> primaryKeys = getPrimaryKeys(jdbcTemplate, tableName, schema);
        if (columns.isEmpty()) return "-- 表无字段";

        if (primaryKeys.isEmpty()) {
            primaryKeys = Collections.singletonList(columns.get(0));
        }

        Map<String, Object> row;
        try {
            row = getRandomRow(jdbcTemplate, tableName);
        } catch (Exception e) {
            return "-- 无法获取随机数据：可能表无数据或表名错误";
        }

        List<String> whereParts = primaryKeys.stream()
                .map(pk -> "`" + pk + "` = " + toSqlValue(row.get(pk)))
                .collect(Collectors.toList());

        return "SELECT " +
                columns.stream().map(col -> "`" + col + "`").collect(Collectors.joining(", ")) +
                " FROM `" + tableName + "` WHERE " + String.join(" AND ", whereParts) + ";";
    }

    private static String toSqlValue(Object val) {
        if (val == null) return "NULL";
        if (val instanceof Number) return val.toString();
        return "'" + val.toString().replace("'", "''") + "'";
    }
}
