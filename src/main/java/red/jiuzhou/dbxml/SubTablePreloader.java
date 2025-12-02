package red.jiuzhou.dbxml;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.stream.Collectors;
public class SubTablePreloader {
    private static final Logger log = LoggerFactory.getLogger(SubTablePreloader.class);

    /**
     * 子表缓存结构：tableName -> (主键值 -> 子表数据列表)
     */
    private final Map<String, Map<String, List<Map<String, Object>>>> preloadedSubTableData = new HashMap<>();
    private final Map<String, String> tableSortFieldMap = new HashMap<>();

    /**
     * 批量预加载表中所有子表数据（递归）
     */
    public void preloadAllSubTables(TableConf tableConf) {
        log.info("批量预加载子表数据...{}", tableConf.getList());

        if (tableConf.getList() == null || tableConf.getList().isEmpty()) {
            return;
        }
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

        for (ColumnMapping mapping : tableConf.getList()) {
            preload(mapping, jdbcTemplate);

        }
    }

    /**
     * 加载某个子表（递归嵌套）
     */
    private void preload(ColumnMapping mapping, JdbcTemplate jdbcTemplate) {
        String preloadSql = buildPreloadSql(mapping);
        String tableName = mapping.getTableName();

        log.info("预加载子表 [{}] 数据...", tableName);
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(preloadSql);
        } catch (Exception e) {
            log.error("预加载子表 [{}] 数据失败：{}", tableName, e.getMessage());
            return;
        }

        Map<String, List<Map<String, Object>>> grouped = rows.stream()
                .filter(row -> row.get(mapping.getAssociatedFiled()) != null)
                .collect(Collectors.groupingBy(row -> String.valueOf(row.get(mapping.getAssociatedFiled()))));

        preloadedSubTableData.put(tableName, grouped);

        // 自动提取排序字段并缓存
        String sortField = extractSortField(mapping.getSql());
        if (sortField != null) {
            tableSortFieldMap.put(tableName, sortField);
        }

        // 递归加载嵌套子表
        if (mapping.getList() != null && !mapping.getList().isEmpty()) {
            for (ColumnMapping sub : mapping.getList()) {
                preload(sub, jdbcTemplate);

            }
        }
    }

    /**
     * 获取子表数据（已按排序字段排好序）
     */
    public List<Map<String, Object>> getSubData(String tableName, String id) {
        List<Map<String, Object>> data = Optional.ofNullable(preloadedSubTableData.get(tableName))
                .map(map -> map.get(id))
                .orElse(Collections.emptyList());

        String sortKey = tableSortFieldMap.get(tableName);
        if (sortKey != null) {
            data = data.stream()
                    .sorted(Comparator.comparing(m -> {
                        Object val = m.get(sortKey);
                        return val == null ? "" : val.toString();
                    }))
                    .collect(Collectors.toList());
        }

        return data;
    }

    /**
     * 构建用于预加载的 SQL（清除 where 条件，仅保留 order by）
     */
    private String buildPreloadSql(ColumnMapping mapping) {
        String rawSql = mapping.getSql();
        String lowerSql = rawSql.toLowerCase();
        int whereIndex = lowerSql.indexOf("where");
        if (whereIndex != -1) {
            int orderIndex = lowerSql.indexOf("order by");
            String orderClause = (orderIndex != -1) ? rawSql.substring(orderIndex) : "";
            return "select * from " + mapping.getTableName() + " where 1=1 " + orderClause;
        }
        return rawSql;
    }

    /**
     * 提取排序字段（自动处理 CAST 语法）
     */
    private String extractSortField(String sql) {
        String lowerSql = sql.toLowerCase();
        int orderIndex = lowerSql.indexOf("order by");
        if (orderIndex == -1){
            return null;
        }

        String orderClause = sql.substring(orderIndex + 8).trim(); // 去掉 "order by"
        if (orderClause.startsWith("cast(")) {
            int start = orderClause.indexOf("(");
            int asIdx = orderClause.toLowerCase().indexOf("as");
            if (start != -1 && asIdx != -1 && asIdx > start) {
                return orderClause.substring(start + 1, asIdx).trim();
            }
        } else {
            return orderClause.split("\\s+")[0].trim(); // 提取字段名
        }

        return null;
    }
}
