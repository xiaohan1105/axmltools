package red.jiuzhou.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import red.jiuzhou.api.common.CommonResult;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.JSONRecord;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static red.jiuzhou.api.common.CommonResult.error;
import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @className: red.jiuzhou.api.SqlExecutorController
 * @description: SQL执行API控制器，提供SQL查询和执行功能
 * @author: yanxq
 * @date: 2025-09-17
 * @version V1.0
 */
@RestController
@RequestMapping("/api/sql")
@Validated
public class SqlExecutorController {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutorController.class);
    private final JdbcTemplate jdbcTemplate;

    // 允许的SQL语句模式（白名单）
    private static final String[] ALLOWED_SQL_PATTERNS = {
        "^SELECT\\s+.*",
        "^SHOW\\s+(TABLES|COLUMNS|DATABASES).*",
        "^DESCRIBE\\s+.*",
        "^EXPLAIN\\s+.*"
    };

    // 危险的SQL关键词（黑名单）
    private static final String[] DANGEROUS_KEYWORDS = {
        "DROP", "DELETE", "TRUNCATE", "ALTER", "CREATE", "INSERT", "UPDATE",
        "GRANT", "REVOKE", "LOAD_FILE", "INTO\\s+OUTFILE", "INTO\\s+DUMPFILE",
        "UNION.*SELECT", "INFORMATION_SCHEMA", "MYSQL\\."
    };

    public SqlExecutorController() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    /**
     * 执行单条SQL语句（仅限查询语句）
     * @param sql SQL语句
     * @param limit 结果集限制行数（仅对SELECT有效）
     * @return 执行结果
     */
    @PostMapping("/execute")
    public CommonResult<Map<String, Object>> executeSql(
            @RequestParam @NotBlank(message = "SQL语句不能为空") String sql,
            @RequestParam(defaultValue = "1000") @Min(value = 1, message = "limit最小值为1") @Max(value = 10000, message = "limit最大值为10000") int limit) {
        try {
            sql = sql.trim();

            // 安全验证
            if (!isValidSql(sql)) {
                return error(1, "不允许的SQL语句类型或包含危险操作");
            }

            // 移除末尾的分号
            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1);
            }

            Map<String, Object> result = new HashMap<>();
            String sqlType = getSqlType(sql);
            result.put("sqlType", sqlType);
            result.put("originalSql", sql);

            if ("SELECT".equals(sqlType)) {
                return executeSelectSql(sql, limit, result);
            } else {
                return executeUpdateSql(sql, result);
            }

        } catch (Exception e) {
            log.error("SQL执行失败: " + sql, e);
            return error(1, "SQL执行失败: " + e.getMessage());
        }
    }

    /**
     * 批量执行多条SQL语句
     * @param sqlScript SQL脚本，多条语句用分号分隔
     * @param limit 每个SELECT语句的结果集限制行数
     * @return 各SQL语句的执行结果
     */
    @PostMapping("/batch-execute")
    public CommonResult<List<Map<String, Object>>> executeBatchSql(
            @RequestParam String sqlScript,
            @RequestParam(defaultValue = "1000") int limit) {
        try {
            List<String> sqlStatements = splitSqlStatements(sqlScript);
            List<Map<String, Object>> results = new ArrayList<>();

            for (String sql : sqlStatements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    CommonResult<Map<String, Object>> singleResult = executeSql(sql, limit);
                    if (singleResult.isSuccess()) {
                        results.add(singleResult.getData());
                    } else {
                        // 如果某条SQL执行失败，添加错误信息但继续执行其他SQL
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("sql", sql);
                        errorResult.put("error", singleResult.getMsg());
                        errorResult.put("success", false);
                        results.add(errorResult);
                    }
                }
            }

            return success(results);
        } catch (Exception e) {
            log.error("批量SQL执行失败", e);
            return error(1, "批量SQL执行失败: " + e.getMessage());
        }
    }

    /**
     * 验证SQL语法
     * @param sql SQL语句
     * @return 验证结果
     */
    @PostMapping("/validate")
    public CommonResult<Map<String, Object>> validateSql(@RequestParam String sql) {
        try {
            sql = sql.trim();
            if (sql.isEmpty()) {
                return error(1, "SQL语句不能为空");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("sql", sql);
            result.put("sqlType", getSqlType(sql));

            // 尝试预编译SQL来验证语法
            Connection connection = jdbcTemplate.getDataSource().getConnection();
            try {
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.close();
                result.put("valid", true);
                result.put("message", "SQL语法验证通过");
            } catch (SQLException e) {
                result.put("valid", false);
                result.put("message", "SQL语法错误: " + e.getMessage());
            } finally {
                connection.close();
            }

            return success(result);
        } catch (Exception e) {
            log.error("SQL验证失败: " + sql, e);
            return error(1, "SQL验证失败: " + e.getMessage());
        }
    }

    /**
     * 获取表结构信息
     * @param tableName 表名
     * @return 表结构
     */
    @GetMapping("/table-info")
    public CommonResult<Map<String, Object>> getTableInfo(@RequestParam String tableName) {
        try {
            Map<String, Object> result = new HashMap<>();

            // 获取表结构
            String descSql = "DESC " + tableName;
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(descSql);
            result.put("columns", columns);

            // 获取表的DDL
            String ddl = DatabaseUtil.getTableDDL(tableName);
            result.put("ddl", ddl);

            // 获取表记录数
            String countSql = "SELECT COUNT(*) as count FROM " + tableName;
            Map<String, Object> countResult = jdbcTemplate.queryForMap(countSql);
            result.put("rowCount", countResult.get("count"));

            return success(result);
        } catch (Exception e) {
            log.error("获取表信息失败: " + tableName, e);
            return error(1, "获取表信息失败: " + e.getMessage());
        }
    }

    /**
     * 执行SELECT查询
     */
    private CommonResult<Map<String, Object>> executeSelectSql(String sql, int limit, Map<String, Object> result) {
        try {
            // 添加LIMIT限制
            if (!sql.toUpperCase().contains("LIMIT")) {
                sql += " LIMIT " + limit;
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            result.put("success", true);
            result.put("data", rows);
            result.put("rowCount", rows.size());
            result.put("hasMore", rows.size() == limit);

            // 如果有结果，获取列信息
            if (!rows.isEmpty()) {
                Set<String> columnNames = rows.get(0).keySet();
                result.put("columns", new ArrayList<>(columnNames));
            }

            return success(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行UPDATE/INSERT/DELETE语句
     */
    private CommonResult<Map<String, Object>> executeUpdateSql(String sql, Map<String, Object> result) {
        try {
            int affectedRows = jdbcTemplate.update(sql);
            result.put("success", true);
            result.put("affectedRows", affectedRows);
            result.put("message", "执行成功，影响" + affectedRows + "行");

            return success(result);
        } catch (Exception e) {
            // 处理字段长度不够的异常，尝试自动扩容
            if (e.getMessage().contains("Data too long for column")) {
                try {
                    handleDataTooLongError(e.getMessage(), sql);
                    // 重新执行
                    int affectedRows = jdbcTemplate.update(sql);
                    result.put("success", true);
                    result.put("affectedRows", affectedRows);
                    result.put("message", "自动扩容后执行成功，影响" + affectedRows + "行");
                    return success(result);
                } catch (SQLException sqlE) {
                    throw new RuntimeException("数据库操作失败: " + sqlE.getMessage(), sqlE);
                } catch (Exception retryE) {
                    throw retryE;
                }
            }
            throw e;
        }
    }

    /**
     * 处理字段长度不够的错误，自动扩容
     */
    private void handleDataTooLongError(String errorMessage, String originalSql) throws SQLException {
        Pattern pattern = Pattern.compile("Data too long for column '(\\w+)' at row \\d+");
        Matcher matcher = pattern.matcher(errorMessage);

        if (matcher.find()) {
            String columnName = matcher.group(1);
            // 从原始SQL中提取表名
            String tableName = extractTableNameFromSql(originalSql);

            if (tableName != null) {
                // 将VARCHAR字段扩容到更大的长度
                String alterSql = String.format("ALTER TABLE %s MODIFY COLUMN %s VARCHAR(1000)", tableName, columnName);
                jdbcTemplate.execute(alterSql);
                log.info("自动扩容字段: {}.{} 到 VARCHAR(1000)", tableName, columnName);
            }
        }
    }

    /**
     * 从SQL语句中提取表名
     */
    private String extractTableNameFromSql(String sql) {
        // INSERT INTO table_name 或 UPDATE table_name
        Pattern insertPattern = Pattern.compile("INSERT\\s+INTO\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Pattern updatePattern = Pattern.compile("UPDATE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

        Matcher insertMatcher = insertPattern.matcher(sql);
        if (insertMatcher.find()) {
            return insertMatcher.group(1);
        }

        Matcher updateMatcher = updatePattern.matcher(sql);
        if (updateMatcher.find()) {
            return updateMatcher.group(1);
        }

        return null;
    }

    /**
     * 验证SQL语句是否安全
     */
    private boolean isValidSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        String upperSql = sql.toUpperCase().trim();

        // 检查是否匹配允许的SQL模式（白名单）
        boolean isAllowed = false;
        for (String pattern : ALLOWED_SQL_PATTERNS) {
            if (upperSql.matches(pattern)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            log.warn("SQL语句不在允许的模式列表中: {}", sql);
            return false;
        }

        // 检查是否包含危险关键词（黑名单）
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upperSql.matches(".*\\b" + keyword + "\\b.*")) {
                log.warn("SQL语句包含危险关键词 '{}': {}", keyword, sql);
                return false;
            }
        }

        // 检查SQL长度
        if (sql.length() > 5000) {
            log.warn("SQL语句长度超过限制: {}", sql.length());
            return false;
        }

        return true;
    }

    /**
     * 确定SQL语句类型
     */
    private String getSqlType(String sql) {
        sql = sql.trim().toUpperCase();
        if (sql.startsWith("SELECT")) return "SELECT";
        if (sql.startsWith("INSERT")) return "INSERT";
        if (sql.startsWith("UPDATE")) return "UPDATE";
        if (sql.startsWith("DELETE")) return "DELETE";
        if (sql.startsWith("CREATE")) return "CREATE";
        if (sql.startsWith("ALTER")) return "ALTER";
        if (sql.startsWith("DROP")) return "DROP";
        return "OTHER";
    }

    /**
     * 分割SQL脚本为单条语句
     */
    private List<String> splitSqlStatements(String sqlScript) {
        List<String> statements = new ArrayList<>();
        String[] parts = sqlScript.split(";");

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }

        return statements;
    }
}