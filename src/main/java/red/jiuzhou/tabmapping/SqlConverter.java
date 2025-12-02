package red.jiuzhou.tabmapping;


import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlConverter {

    public static String convert(String sql, List<TableMapping> mappings, boolean isInsert) {
        String tableName = extractTableName(sql, isInsert);
        if (tableName == null) {
            return "-- 无法识别 SQL 中的表名";
        }

        for (TableMapping mapping : mappings) {
            if (tableName.equalsIgnoreCase(mapping.svr_tab)) {
                return isInsert
                        ? convertInsert(sql, mapping.svr_tab, mapping.clt_tab, mapping.getSameFieldsSet())
                        : convertUpdate(sql, mapping.svr_tab, mapping.clt_tab, mapping.getSameFieldsSet());
            } else if (tableName.equalsIgnoreCase(mapping.clt_tab)) {
                return isInsert
                        ? convertInsert(sql, mapping.clt_tab, mapping.svr_tab, mapping.getSameFieldsSet())
                        : convertUpdate(sql, mapping.clt_tab, mapping.svr_tab, mapping.getSameFieldsSet());
            }
        }

        return "-- 未匹配到任何表映射";
    }


    public static String convertInsert(String sql, String sourceTable, String targetTable, Set<String> allowedFields) {
        Pattern pattern = Pattern.compile("insert\\s+into\\s+" + sourceTable + "\\s*\\((.*?)\\)\\s*values\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);

        if (matcher.find()) {
            String fieldStr = matcher.group(1);
            String valueStr = matcher.group(2);

            String[] fields = fieldStr.split("\\s*,\\s*");
            String[] values = valueStr.split("\\s*,\\s*");

            List<String> targetFields = new ArrayList<>();
            List<String> targetValues = new ArrayList<>();

            for (int i = 0; i < fields.length; i++) {
                if (allowedFields.contains(fields[i])) {
                    targetFields.add(fields[i]);
                    targetValues.add(values[i]);
                }
            }

            return String.format("INSERT INTO %s (%s) VALUES (%s);",
                    targetTable,
                    String.join(", ", targetFields),
                    String.join(", ", targetValues));
        }
        return null;
    }

    public static String convertUpdate(String sql, String sourceTable, String targetTable, Set<String> allowedFields) {
        Pattern pattern = Pattern.compile("update\\s+" + sourceTable + "\\s+set\\s+(.*?)\\s+where\\s+(.*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);

        if (matcher.find()) {
            String setClause = matcher.group(1);
            String whereClause = matcher.group(2);

            String[] assignments = setClause.split("\\s*,\\s*");
            List<String> filteredAssignments = new ArrayList<>();

            for (String assignment : assignments) {
                String[] parts = assignment.split("=");
                if (parts.length == 2 && allowedFields.contains(parts[0].trim())) {
                    filteredAssignments.add(assignment.trim());
                }
            }

            return String.format("UPDATE %s SET %s WHERE %s",
                    targetTable,
                    String.join(", ", filteredAssignments),
                    whereClause);
        }
        return null;
    }

    private static String extractTableName(String sql, boolean isInsert) {
        Pattern pattern;
        if (isInsert) {
            pattern = Pattern.compile("insert\\s+into\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        } else {
            pattern = Pattern.compile("update\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        }

        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

}
