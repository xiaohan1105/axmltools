package red.jiuzhou.ui.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据管理器
 * 负责数据的搜索、筛选、排序和分页处理
 */
public class DataManager {

    private static final Logger log = LoggerFactory.getLogger(DataManager.class);

    // 搜索优先级字段
    private static final List<String> PRIORITY_SEARCH_FIELDS = Arrays.asList(
        "id", "name", "title", "description", "text", "content", "value"
    );

    /**
     * 执行智能搜索
     *
     * @param data 源数据
     * @param searchTerm 搜索词
     * @param filters 筛选条件
     * @return 筛选后的数据
     */
    public List<Map<String, Object>> search(List<Map<String, Object>> data,
                                           String searchTerm,
                                           List<SmartDataTable.FilterCriteria> filters) {

        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>(data);

        // 应用搜索词过滤
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            result = performTextSearch(result, searchTerm.trim());
        }

        // 应用其他筛选条件
        if (filters != null && !filters.isEmpty()) {
            result = applyFilters(result, filters);
        }

        log.debug("搜索完成: 原始数据 {} 条，筛选后 {} 条", data.size(), result.size());
        return result;
    }

    /**
     * 执行文本搜索
     */
    private List<Map<String, Object>> performTextSearch(List<Map<String, Object>> data, String searchTerm) {
        String lowerSearchTerm = searchTerm.toLowerCase();

        // 获取搜索字段
        Set<String> searchFields = getSearchFields(data);

        return data.parallelStream()
                .filter(row -> matchesSearchTerm(row, lowerSearchTerm, searchFields))
                .collect(Collectors.toList());
    }

    /**
     * 获取搜索字段（优先使用重要字段）
     */
    private Set<String> getSearchFields(List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> allFields = data.get(0).keySet();
        Set<String> searchFields = new LinkedHashSet<>();

        // 优先添加重要字段
        for (String priorityField : PRIORITY_SEARCH_FIELDS) {
            for (String field : allFields) {
                if (field.toLowerCase().contains(priorityField.toLowerCase())) {
                    searchFields.add(field);
                }
            }
        }

        // 如果没有找到重要字段，使用所有字段（限制数量避免性能问题）
        if (searchFields.isEmpty()) {
            searchFields.addAll(allFields.stream()
                    .limit(8) // 限制搜索字段数量
                    .collect(Collectors.toSet()));
        }

        return searchFields;
    }

    /**
     * 检查行是否匹配搜索词
     */
    private boolean matchesSearchTerm(Map<String, Object> row, String searchTerm, Set<String> searchFields) {
        for (String field : searchFields) {
            Object value = row.get(field);
            if (value != null && value.toString().toLowerCase().contains(searchTerm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 应用筛选条件
     */
    private List<Map<String, Object>> applyFilters(List<Map<String, Object>> data,
                                                  List<SmartDataTable.FilterCriteria> filters) {
        return data.stream()
                .filter(row -> matchesAllFilters(row, filters))
                .collect(Collectors.toList());
    }

    /**
     * 检查行是否匹配所有筛选条件
     */
    private boolean matchesAllFilters(Map<String, Object> row, List<SmartDataTable.FilterCriteria> filters) {
        for (SmartDataTable.FilterCriteria filter : filters) {
            if (!matchesFilter(row, filter)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查行是否匹配单个筛选条件
     */
    private boolean matchesFilter(Map<String, Object> row, SmartDataTable.FilterCriteria filter) {
        Object value = row.get(filter.getColumn());
        Object filterValue = filter.getValue();

        if (value == null) {
            return filterValue == null;
        }

        String operator = filter.getOperator();
        String valueStr = value.toString();
        String filterStr = filterValue != null ? filterValue.toString() : "";

        switch (operator.toLowerCase()) {
            case "equals":
            case "=":
                return valueStr.equals(filterStr);
            case "contains":
            case "like":
                return valueStr.toLowerCase().contains(filterStr.toLowerCase());
            case "starts_with":
                return valueStr.toLowerCase().startsWith(filterStr.toLowerCase());
            case "ends_with":
                return valueStr.toLowerCase().endsWith(filterStr.toLowerCase());
            case "not_equals":
            case "!=":
                return !valueStr.equals(filterStr);
            case "greater_than":
            case ">":
                return compareNumeric(valueStr, filterStr) > 0;
            case "less_than":
            case "<":
                return compareNumeric(valueStr, filterStr) < 0;
            case "greater_equal":
            case ">=":
                return compareNumeric(valueStr, filterStr) >= 0;
            case "less_equal":
            case "<=":
                return compareNumeric(valueStr, filterStr) <= 0;
            default:
                log.warn("未知的筛选操作符: {}", operator);
                return true;
        }
    }

    /**
     * 数值比较
     */
    private int compareNumeric(String value1, String value2) {
        try {
            double num1 = Double.parseDouble(value1);
            double num2 = Double.parseDouble(value2);
            return Double.compare(num1, num2);
        } catch (NumberFormatException e) {
            // 如果不是数字，使用字符串比较
            return value1.compareTo(value2);
        }
    }

    /**
     * 获取分页数据
     *
     * @param data 源数据
     * @param pageIndex 页面索引（从0开始）
     * @param pageSize 页面大小
     * @return 分页数据
     */
    public List<Map<String, Object>> getPage(List<Map<String, Object>> data, int pageIndex, int pageSize) {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        // 全量显示
        if (pageSize == Integer.MAX_VALUE) {
            return new ArrayList<>(data);
        }

        int startIndex = pageIndex * pageSize;
        int endIndex = Math.min(startIndex + pageSize, data.size());

        if (startIndex >= data.size()) {
            return new ArrayList<>();
        }

        return data.subList(startIndex, endIndex);
    }

    /**
     * 排序数据
     *
     * @param data 源数据
     * @param column 排序列
     * @param ascending 是否升序
     * @return 排序后的数据
     */
    public List<Map<String, Object>> sort(List<Map<String, Object>> data, String column, boolean ascending) {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> sortedData = new ArrayList<>(data);

        sortedData.sort((row1, row2) -> {
            Object val1 = row1.get(column);
            Object val2 = row2.get(column);

            int result = compareValues(val1, val2);
            return ascending ? result : -result;
        });

        return sortedData;
    }

    /**
     * 比较两个值
     */
    private int compareValues(Object val1, Object val2) {
        if (val1 == null && val2 == null) return 0;
        if (val1 == null) return -1;
        if (val2 == null) return 1;

        // 尝试数值比较
        try {
            double num1 = Double.parseDouble(val1.toString());
            double num2 = Double.parseDouble(val2.toString());
            return Double.compare(num1, num2);
        } catch (NumberFormatException e) {
            // 字符串比较
            return val1.toString().compareTo(val2.toString());
        }
    }

    /**
     * 统计字段值分布
     *
     * @param data 数据
     * @param column 列名
     * @return 值分布统计
     */
    public Map<String, Integer> getValueDistribution(List<Map<String, Object>> data, String column) {
        if (data == null || data.isEmpty()) {
            return new HashMap<>();
        }

        return data.stream()
                .map(row -> row.get(column))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.groupingBy(
                        value -> value,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }

    /**
     * 获取唯一值列表
     *
     * @param data 数据
     * @param column 列名
     * @return 唯一值列表
     */
    public List<String> getUniqueValues(List<Map<String, Object>> data, String column) {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        return data.stream()
                .map(row -> row.get(column))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 导出数据为CSV格式
     */
    public String exportToCsv(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        StringBuilder csv = new StringBuilder();

        // 添加表头
        Set<String> columns = data.get(0).keySet();
        csv.append(String.join(",", columns)).append("\n");

        // 添加数据行
        for (Map<String, Object> row : data) {
            List<String> values = columns.stream()
                    .map(col -> {
                        Object value = row.get(col);
                        String str = value != null ? value.toString() : "";
                        // 处理包含逗号的值
                        if (str.contains(",") || str.contains("\"")) {
                            str = "\"" + str.replace("\"", "\"\"") + "\"";
                        }
                        return str;
                    })
                    .collect(Collectors.toList());

            csv.append(String.join(",", values)).append("\n");
        }

        return csv.toString();
    }
}