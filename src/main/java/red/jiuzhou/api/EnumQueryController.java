package red.jiuzhou.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import red.jiuzhou.api.common.CommonResult;
import red.jiuzhou.util.DatabaseUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static red.jiuzhou.api.common.CommonResult.error;
import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @className: red.jiuzhou.api.EnumQueryController
 * @description: 枚举查询API控制器，提供数据库字段枚举值及其分布的查询功能
 * @author: yanxq
 * @date: 2025-09-17
 * @version V1.0
 */
@RestController
@RequestMapping("/api/enum")
public class EnumQueryController {

    private static final Logger log = LoggerFactory.getLogger(EnumQueryController.class);
    private final JdbcTemplate jdbcTemplate;

    public EnumQueryController() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    /**
     * 获取数据库中所有表名
     * @return 表名列表
     */
    @GetMapping("/tables")
    public CommonResult<List<String>> getAllTables() {
        try {
            String sql = "SHOW TABLES";
            List<String> tables = jdbcTemplate.queryForList(sql, String.class);
            return success(tables);
        } catch (Exception e) {
            log.error("获取表列表失败", e);
            return error(1, "获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定表的所有字段名
     * @param tableName 表名
     * @return 字段名列表
     */
    @GetMapping("/columns")
    public CommonResult<List<String>> getTableColumns(@RequestParam String tableName) {
        try {
            String sql = "SHOW COLUMNS FROM " + tableName;
            List<String> columns = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Field"));
            return success(columns);
        } catch (Exception e) {
            log.error("获取表字段失败: " + tableName, e);
            return error(1, "获取表字段失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定表和字段的枚举值分布
     * @param tableName 表名
     * @param columnName 字段名
     * @return 枚举值及其出现次数的映射
     */
    @GetMapping("/distribution")
    public CommonResult<List<Map<String, Object>>> getEnumDistribution(
            @RequestParam String tableName,
            @RequestParam String columnName) {
        try {
            String sql = String.format("SELECT %s AS value, COUNT(*) AS count FROM %s GROUP BY %s ORDER BY count DESC",
                    columnName, tableName, columnName);

            List<Map<String, Object>> result = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("value", rs.getObject("value"));
                row.put("count", rs.getInt("count"));
                return row;
            });

            return success(result);
        } catch (Exception e) {
            log.error("查询枚举分布失败: " + tableName + "." + columnName, e);
            return error(1, "查询枚举分布失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定表的总记录数
     * @param tableName 表名
     * @return 总记录数
     */
    @GetMapping("/count")
    public CommonResult<Integer> getTableCount(@RequestParam String tableName) {
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return success(count);
        } catch (Exception e) {
            log.error("获取表记录数失败: " + tableName, e);
            return error(1, "获取表记录数失败: " + e.getMessage());
        }
    }

    /**
     * 批量查询多个字段的枚举分布
     * @param tableName 表名
     * @param columnNames 字段名列表，逗号分隔
     * @return 各字段的枚举分布
     */
    @GetMapping("/batch-distribution")
    public CommonResult<Map<String, List<Map<String, Object>>>> getBatchEnumDistribution(
            @RequestParam String tableName,
            @RequestParam String columnNames) {
        try {
            Map<String, List<Map<String, Object>>> result = new HashMap<>();
            String[] columns = columnNames.split(",");

            for (String column : columns) {
                column = column.trim();
                String sql = String.format("SELECT %s AS value, COUNT(*) AS count FROM %s GROUP BY %s ORDER BY count DESC LIMIT 50",
                        column, tableName, column);

                List<Map<String, Object>> distribution = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("value", rs.getObject("value"));
                    row.put("count", rs.getInt("count"));
                    return row;
                });

                result.put(column, distribution);
            }

            return success(result);
        } catch (Exception e) {
            log.error("批量查询枚举分布失败: " + tableName + " - " + columnNames, e);
            return error(1, "批量查询枚举分布失败: " + e.getMessage());
        }
    }
}