package red.jiuzhou.xmltosql;

import cn.hutool.core.io.FileUtil;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class SqlFieldReorderTool {

    // 自定义字段优先顺序
    private static final List<String> tagOrder = Arrays.asList("attacks", "skills");

    public static void main(String[] args) throws Exception {
        String sql = FileUtil.readUtf8String("/Users/dream/workspace/dbxmlTool/src/main/resources/CONF/Users/dream/workspace/dbxmlTool/data/DATA/SVR_DATA/China/sql/npcs_npcs.sql");
        String adjusted = adjustFieldOrder(sql);
        FileUtil.writeUtf8String(adjusted, "/Users/dream/workspace/dbxmlTool/src/main/resources/CONF/Users/dream/workspace/dbxmlTool/data/DATA/SVR_DATA/China/sql/npcs_npcs_n.sql");
    }

    public static String adjustFieldOrder(String sqlContent) {
        StringBuilder result = new StringBuilder();

        Pattern createTablePattern = Pattern.compile(
                "(?i)(CREATE TABLE\\s+[^\\(]+\\()(.*?)(\\)\\s*ENGINE=.*?;)",
                Pattern.DOTALL
        );

        Matcher matcher = createTablePattern.matcher(sqlContent);
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(sqlContent, lastEnd, matcher.start()); // 保留前段内容

            String prefix = matcher.group(1);
            String fieldBlock = matcher.group(2);
            String suffix = matcher.group(3);

            // 拆字段
            List<String> lines = new ArrayList<>(Arrays.asList(fieldBlock.split("\n")));
            List<String> fieldLines = new ArrayList<>();
            List<String> otherLines = new ArrayList<>();

            for (String line : lines) {
                if (line.trim().startsWith("`")) {
                    fieldLines.add(line);
                } else {
                    otherLines.add(line); // 可能是约束或注释等
                }
            }

            // 抽出优先字段
            Map<String, String> tagLineMap = new HashMap<>();
            List<String> remaining = new ArrayList<>();

            for (String line : fieldLines) {
                Matcher m = Pattern.compile("^(\\s*)`(\\w+)`").matcher(line);
                if (m.find()) {
                    String indent = m.group(1);
                    String name = m.group(2);
                    if (tagOrder.contains(name)) {
                        tagLineMap.put(name, line);
                        continue;
                    }
                }
                remaining.add(line);
            }

            // 按 tagOrder 顺序插入
            List<String> orderedTags = new ArrayList<>();
            for (String tag : tagOrder) {
                if (tagLineMap.containsKey(tag)) {
                    orderedTags.add(tagLineMap.get(tag));
                }
            }

            // 重新组合字段行（其余 + 有序tag）
            List<String> allFields = new ArrayList<>();
            allFields.addAll(remaining);
            allFields.addAll(orderedTags);

            // 格式化（加逗号 + 缩进 + 换行）
            for (int i = 0; i < allFields.size(); i++) {
                String line = allFields.get(i).trim();
                String indent = "    "; // 默认缩进
                Matcher m = Pattern.compile("^(\\s*)").matcher(allFields.get(i));
                if (m.find()) {
                    indent = m.group(1);
                }
                if (i < allFields.size() - 1 && !line.endsWith(",")) {
                    line += ",";
                } else if (i == allFields.size() - 1 && line.endsWith(",")) {
                    line = line.substring(0, line.length() - 1); // 最后一项不能有逗号
                }
                allFields.set(i, indent + line);
            }

            // 拼接并添加回主 SQL 中
            result.append(prefix).append("\n");
            result.append(String.join("\n", allFields));
            if (!otherLines.isEmpty()) {
                result.append("\n");
                result.append(String.join("\n", otherLines));
            }
            result.append(suffix);
            result.append("\n"); // 保证每个表后有换行

            lastEnd = matcher.end();
        }

        result.append(sqlContent.substring(lastEnd));
        return result.toString();
    }
}
