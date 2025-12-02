package red.jiuzhou.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import red.jiuzhou.api.common.CommonResult;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.JSONRecord;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static red.jiuzhou.api.common.CommonResult.error;
import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @className: red.jiuzhou.api.SqlConverterController
 * @description: SQL转换器API控制器，提供SQL语句转换功能
 * @author: yanxq
 * @date: 2025-09-17
 * @version V1.0
 */
@RestController
@RequestMapping("/api/sql-converter")
public class SqlConverterController {

    private static final Logger log = LoggerFactory.getLogger(SqlConverterController.class);
    private final JdbcTemplate jdbcTemplate;

    public SqlConverterController() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    /**
     * 获取数据库中所有表名
     * @return 表名列表
     */
    @GetMapping("/databases")
    public CommonResult<List<String>> getAllDatabases() {
        try {
            String sql = "SHOW DATABASES";
            List<String> databases = jdbcTemplate.queryForList(sql, String.class);
            return success(databases);
        } catch (Exception e) {
            log.error("获取数据库列表失败", e);
            return error(1, "获取数据库列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定数据库中的所有表
     * @param database 数据库名（可选，默认使用当前数据库）
     * @return 表名列表
     */
    @GetMapping("/tables")
    public CommonResult<List<String>> getTables(@RequestParam(required = false) String database) {
        try {
            String sql;
            if (database != null && !database.isEmpty()) {
                sql = "SHOW TABLES FROM " + database;
            } else {
                sql = "SHOW TABLES";
            }
            List<String> tables = jdbcTemplate.queryForList(sql, String.class);
            return success(tables);
        } catch (Exception e) {
            log.error("获取表列表失败", e);
            return error(1, "获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的字段信息
     * @param tableName 表名
     * @return 字段信息列表
     */
    @GetMapping("/table-fields")
    public CommonResult<List<Map<String, Object>>> getTableFields(@RequestParam String tableName) {
        try {
            String sql = "SHOW COLUMNS FROM " + tableName;
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);

            // 处理返回结果，提取字段名和类型
            List<Map<String, Object>> fields = columns.stream().map(column -> {
                Map<String, Object> field = new HashMap<>();
                field.put("name", column.get("Field"));
                field.put("type", column.get("Type"));
                field.put("nullable", "YES".equals(column.get("Null")));
                field.put("key", column.get("Key"));
                field.put("default", column.get("Default"));
                field.put("extra", column.get("Extra"));
                return field;
            }).collect(Collectors.toList());

            return success(fields);
        } catch (Exception e) {
            log.error("获取表字段失败: " + tableName, e);
            return error(1, "获取表字段失败: " + e.getMessage());
        }
    }

    /**
     * 转换SQL语句：从源表结构转换为目标表结构
     * @param request 转换请求参数
     * @return 转换后的SQL语句
     */
    @PostMapping("/convert")
    public CommonResult<Map<String, Object>> convertSql(@RequestBody SqlConvertRequest request) {
        try {
            Map<String, Object> result = new HashMap<>();

            // 获取源表和目标表的字段信息
            List<Map<String, Object>> sourceFields = getTableFieldsInternal(request.getSourceTable());
            List<Map<String, Object>> targetFields = getTableFieldsInternal(request.getTargetTable());

            // 创建字段映射
            Map<String, String> fieldMapping = createFieldMapping(sourceFields, targetFields, request.getFieldMappings());

            String convertedSql;
            if ("INSERT".equalsIgnoreCase(request.getOperationType())) {
                convertedSql = convertInsertSql(request.getSourceSql(), request.getTargetTable(), fieldMapping, request.getSelectedFields());
            } else if ("UPDATE".equalsIgnoreCase(request.getOperationType())) {
                convertedSql = convertUpdateSql(request.getSourceSql(), request.getTargetTable(), fieldMapping, request.getSelectedFields());
            } else {
                return error(1, "不支持的操作类型: " + request.getOperationType());
            }

            result.put("originalSql", request.getSourceSql());
            result.put("convertedSql", convertedSql);
            result.put("fieldMapping", fieldMapping);
            result.put("operationType", request.getOperationType());

            return success(result);
        } catch (Exception e) {
            log.error("SQL转换失败", e);
            return error(1, "SQL转换失败: " + e.getMessage());
        }
    }

    /**
     * 生成SQL模板
     * @param tableName 表名
     * @param operationType 操作类型（INSERT/UPDATE/DELETE/SELECT）
     * @return SQL模板
     */
    @GetMapping("/template")
    public CommonResult<Map<String, Object>> generateSqlTemplate(
            @RequestParam String tableName,
            @RequestParam String operationType) {
        try {
            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> fields = getTableFieldsInternal(tableName);

            String template;
            switch (operationType.toUpperCase()) {
                case "INSERT":
                    template = generateInsertTemplate(tableName, fields);
                    break;
                case "UPDATE":
                    template = generateUpdateTemplate(tableName, fields);
                    break;
                case "DELETE":
                    template = generateDeleteTemplate(tableName, fields);
                    break;
                case "SELECT":
                    template = generateSelectTemplate(tableName, fields);
                    break;
                default:
                    return error(1, "不支持的操作类型: " + operationType);
            }

            result.put("template", template);
            result.put("tableName", tableName);
            result.put("operationType", operationType);
            result.put("fields", fields);

            return success(result);
        } catch (Exception e) {
            log.error("生成SQL模板失败", e);
            return error(1, "生成SQL模板失败: " + e.getMessage());
        }
    }

    /**
     * 批量转换SQL语句
     * @param request 批量转换请求
     * @return 转换结果列表
     */
    @PostMapping("/batch-convert")
    public CommonResult<List<Map<String, Object>>> batchConvertSql(@RequestBody BatchSqlConvertRequest request) {
        try {
            List<Map<String, Object>> results = new ArrayList<>();

            for (String sql : request.getSqlList()) {
                try {
                    SqlConvertRequest singleRequest = new SqlConvertRequest();
                    singleRequest.setSourceSql(sql);
                    singleRequest.setSourceTable(request.getSourceTable());
                    singleRequest.setTargetTable(request.getTargetTable());
                    singleRequest.setOperationType(request.getOperationType());
                    singleRequest.setFieldMappings(request.getFieldMappings());
                    singleRequest.setSelectedFields(request.getSelectedFields());

                    CommonResult<Map<String, Object>> singleResult = convertSql(singleRequest);
                    if (singleResult.isSuccess()) {
                        results.add(singleResult.getData());
                    } else {
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("originalSql", sql);
                        errorResult.put("error", singleResult.getMsg());
                        errorResult.put("success", false);
                        results.add(errorResult);
                    }
                } catch (Exception e) {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("originalSql", sql);
                    errorResult.put("error", e.getMessage());
                    errorResult.put("success", false);
                    results.add(errorResult);
                }
            }

            return success(results);
        } catch (Exception e) {
            log.error("批量SQL转换失败", e);
            return error(1, "批量SQL转换失败: " + e.getMessage());
        }
    }

    // 内部方法
    private List<Map<String, Object>> getTableFieldsInternal(String tableName) {
        String sql = "SHOW COLUMNS FROM " + tableName;
        return jdbcTemplate.queryForList(sql);
    }

    private Map<String, String> createFieldMapping(List<Map<String, Object>> sourceFields,
                                                   List<Map<String, Object>> targetFields,
                                                   Map<String, String> customMappings) {
        Map<String, String> mapping = new HashMap<>();

        // 先添加自定义映射
        if (customMappings != null) {
            mapping.putAll(customMappings);
        }

        // 自动映射同名字段
        Set<String> targetFieldNames = targetFields.stream()
                .map(field -> (String) field.get("Field"))
                .collect(Collectors.toSet());

        for (Map<String, Object> sourceField : sourceFields) {
            String sourceFieldName = (String) sourceField.get("Field");
            if (!mapping.containsKey(sourceFieldName) && targetFieldNames.contains(sourceFieldName)) {
                mapping.put(sourceFieldName, sourceFieldName);
            }
        }

        return mapping;
    }

    private String convertInsertSql(String sourceSql, String targetTable,
                                   Map<String, String> fieldMapping, List<String> selectedFields) {
        // 简化的INSERT转换逻辑
        Pattern insertPattern = Pattern.compile("INSERT\\s+INTO\\s+\\w+\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = insertPattern.matcher(sourceSql);

        if (matcher.find()) {
            String[] sourceColumns = matcher.group(1).split(",");
            String[] sourceValues = matcher.group(2).split(",");

            List<String> targetColumns = new ArrayList<>();
            List<String> targetValues = new ArrayList<>();

            for (int i = 0; i < sourceColumns.length; i++) {
                String sourceCol = sourceColumns[i].trim();
                String targetCol = fieldMapping.get(sourceCol);

                if (targetCol != null && (selectedFields == null || selectedFields.contains(sourceCol))) {
                    targetColumns.add(targetCol);
                    targetValues.add(sourceValues[i].trim());
                }
            }

            return String.format("INSERT INTO %s (%s) VALUES (%s)",
                    targetTable,
                    String.join(", ", targetColumns),
                    String.join(", ", targetValues));
        }

        return sourceSql; // 如果无法解析，返回原始SQL
    }

    private String convertUpdateSql(String sourceSql, String targetTable,
                                   Map<String, String> fieldMapping, List<String> selectedFields) {
        // 简化的UPDATE转换逻辑
        return sourceSql.replaceFirst("UPDATE\\s+\\w+", "UPDATE " + targetTable);
    }

    private String generateInsertTemplate(String tableName, List<Map<String, Object>> fields) {
        List<String> fieldNames = fields.stream()
                .map(field -> (String) field.get("Field"))
                .collect(Collectors.toList());

        List<String> placeholders = fields.stream()
                .map(field -> "?")
                .collect(Collectors.toList());

        return String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName,
                String.join(", ", fieldNames),
                String.join(", ", placeholders));
    }

    private String generateUpdateTemplate(String tableName, List<Map<String, Object>> fields) {
        List<String> setClause = fields.stream()
                .filter(field -> !"PRI".equals(field.get("Key"))) // 排除主键
                .map(field -> field.get("Field") + " = ?")
                .collect(Collectors.toList());

        String primaryKey = fields.stream()
                .filter(field -> "PRI".equals(field.get("Key")))
                .map(field -> (String) field.get("Field"))
                .findFirst()
                .orElse("id");

        return String.format("UPDATE %s SET %s WHERE %s = ?",
                tableName,
                String.join(", ", setClause),
                primaryKey);
    }

    private String generateDeleteTemplate(String tableName, List<Map<String, Object>> fields) {
        String primaryKey = fields.stream()
                .filter(field -> "PRI".equals(field.get("Key")))
                .map(field -> (String) field.get("Field"))
                .findFirst()
                .orElse("id");

        return String.format("DELETE FROM %s WHERE %s = ?", tableName, primaryKey);
    }

    private String generateSelectTemplate(String tableName, List<Map<String, Object>> fields) {
        List<String> fieldNames = fields.stream()
                .map(field -> (String) field.get("Field"))
                .collect(Collectors.toList());

        return String.format("SELECT %s FROM %s WHERE 1=1", String.join(", ", fieldNames), tableName);
    }

    // 请求类
    public static class SqlConvertRequest {
        private String sourceSql;
        private String sourceTable;
        private String targetTable;
        private String operationType;
        private Map<String, String> fieldMappings;
        private List<String> selectedFields;

        // Getters and Setters
        public String getSourceSql() { return sourceSql; }
        public void setSourceSql(String sourceSql) { this.sourceSql = sourceSql; }

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }

        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }

        public Map<String, String> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(Map<String, String> fieldMappings) { this.fieldMappings = fieldMappings; }

        public List<String> getSelectedFields() { return selectedFields; }
        public void setSelectedFields(List<String> selectedFields) { this.selectedFields = selectedFields; }
    }

    public static class BatchSqlConvertRequest {
        private List<String> sqlList;
        private String sourceTable;
        private String targetTable;
        private String operationType;
        private Map<String, String> fieldMappings;
        private List<String> selectedFields;

        // Getters and Setters
        public List<String> getSqlList() { return sqlList; }
        public void setSqlList(List<String> sqlList) { this.sqlList = sqlList; }

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }

        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }

        public Map<String, String> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(Map<String, String> fieldMappings) { this.fieldMappings = fieldMappings; }

        public List<String> getSelectedFields() { return selectedFields; }
        public void setSelectedFields(List<String> selectedFields) { this.selectedFields = selectedFields; }
    }
}