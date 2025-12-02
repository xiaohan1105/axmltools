package red.jiuzhou.xmltosql;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.AliyunTranslateUtil;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.YamlUtils;

import java.util.*;
/**
*@Author: yanxq
*@CreateTime: 2025/3/24 22:18
*@Description: TODO
*@Version: 1.0
*/
public class XMLToMySQLGenerator {
    private static final Logger log = LoggerFactory.getLogger(XMLToMySQLGenerator.class);
    private static final List<String> WORLD_SPECIAL_TAB_NAMES = loadWorldSpecialTabNames();
    private static final String ORDER_COLUMN = "__order_index";

    private XMLToMySQLGenerator() {
    }

    private static final class GenerationContext {
        private final String fileName;
        private final JSONRecord fieldLenJson;
        private final boolean treatAsWorld;
        private String firstField;

        private GenerationContext(String fileName, JSONRecord fieldLenJson) {
            this.fileName = fileName;
            this.fieldLenJson = fieldLenJson;
            this.treatAsWorld = "world".equals(fileName);
        }

        private String getFileName() {
            return fileName;
        }

        private boolean isWorld() {
            return treatAsWorld;
        }

        private String getFirstField() {
            return firstField;
        }

        private void setFirstField(String firstField) {
            this.firstField = firstField;
        }

        private JSONRecord getFieldLenJson() {
            return fieldLenJson;
        }
    }

    private static List<String> loadWorldSpecialTabNames() {
        String configured = YamlUtils.getProperty("world.specialTabName");
        if (!StringUtils.hasLength(configured)) {
            return Collections.emptyList();
        }
        String[] parts = configured.split(",");
        List<String> list = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (StringUtils.hasLength(trimmed)) {
                list.add(trimmed);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public static String generateMysqlTables(String xmlFileName, String xmlStr, JSONRecord filedLenJson, String newFileName) {
        try {
            String resolvedFileName = StringUtils.hasLength(newFileName) ? newFileName : xmlFileName;
            GenerationContext context = new GenerationContext(resolvedFileName, filedLenJson);
            Document document = DocumentHelper.parseText(xmlStr);
            Element root = document.getRootElement();
            if(root.elements().isEmpty()){
                log.warn("XML文件{}根节点为空，无法生成DDL", xmlFileName);
                throw new RuntimeException("配置错误：XML文件根节点为空，无法生成表结构");
            }
            // 解析根节点的第一个子节点

            if(context.isWorld()){
                context.setFirstField(root.elements().get(0).getName());
            }else{
                root = root.elements().get(0);
                context.setFirstField(root.elements().get(0).getName());
            }
            Map<String, String> tableDefinitions = new LinkedHashMap<>();
            Map<String, String> foreignKeys = new HashMap<>();
            parseElement(root, context, tableDefinitions, foreignKeys, new HashSet<>(), true, "");
            StringBuilder sqlBuf = new StringBuilder();
            // 输出 SQL 语句
            tableDefinitions.forEach((tableName, sql) -> {
                sqlBuf.append(sql).append("\n\n");
                //System.out.println(sql);
                if (foreignKeys.containsKey(tableName)) {
                    //System.out.println(foreignKeys.get(tableName));
                    sqlBuf.append(foreignKeys.get(tableName)).append("\n\n");
                }
                //System.out.println();
            });
            return sqlBuf.toString();
        } catch (Exception e) {
            log.error("解析XML{}文件生成MySQL表失败", xmlFileName + ".xml", e);
        }
        return null;
    }

    private static void parseElement(Element element,
                                     GenerationContext context,
                                     Map<String, String> tableDefinitions,
                                     Map<String, String> foreignKeys,
                                     Set<String> processedTables,
                                     boolean isRoot,
                                     String parentTable) {
        List<Element> children = element.elements();
        if (children.isEmpty()) {
            return;
        }

        // 生成表名，使用双下划线区分层级
        String tableName = "".equals(parentTable) ? context.getFileName() : parentTable + "__" + element.getName();
        tableName = shortenString(tableName, 60);

        // 需要创建表的情况
        if (!processedTables.contains(tableName)) {

            if(children.size() == 1 && !children.get(0).elements().isEmpty()){
                // 递归解析子节点
                for (Element child : children) {
                    parseElement(child, context, tableDefinitions, foreignKeys, processedTables, false, tableName);
                }
                return;
            }

            processedTables.add(tableName);
            tableDefinitions.put(tableName, generateCreateTableSQL(element, context, tableName, !isRoot));
            // 如果不是根表，则让子表的 `id` 继承父表的 `id`
            if (!isRoot) {
                parentTable = getRealParentTable(element.getParent(), parentTable);
                String tabFirstField = "__parent_" + context.getFirstField();
                if(parentTable.equals(context.getFileName())){
                    tabFirstField = context.getFirstField();
                }
                String foreignKeySQL = "ALTER TABLE " + tableName + " ADD CONSTRAINT fk_" + tableName +
                        " FOREIGN KEY (__parent_"+context.getFirstField()+") REFERENCES " + parentTable + "("+tabFirstField+") ON DELETE CASCADE ON UPDATE CASCADE;";
                //foreignKeys.put(tableName, foreignKeySQL);
            }
        }

        // 递归解析子节点
        for (Element child : children) {
            parseElement(child, context, tableDefinitions, foreignKeys, processedTables, false, tableName);
        }
    }

    private static String getRealParentTable(Element element, String parentTable){
        if((element != null && element.elements().size() == 1) ||
                (element != null && element.isRootElement())){
            String delimiter = "__";
            int lastIndex = parentTable.lastIndexOf(delimiter);
            if (lastIndex != -1) {
                parentTable = parentTable.substring(0, lastIndex);
                getRealParentTable(element.getParent(), parentTable);
            }
        }
        return parentTable;
    }

    private static String generateCreateTableSQL(Element element, GenerationContext context, String tableName, boolean isChildTable) {
        final String dbName = DatabaseUtil.getDbName();

        List<Element> fields = element.elements();
        List<Attribute> attributes = element.attributes();
        StringBuilder sql = new StringBuilder("DROP TABLE IF EXISTS " + dbName + "." + tableName + ";\n" +
                "CREATE TABLE "  + dbName + "." + tableName + " (\n");
        String tabFirstField = "`" + context.getFirstField() + "`";
        if(context.isWorld()){
            // 主表的 id 是自增，子表的 id 继承父表
            tabFirstField = "`__parent_" + context.getFirstField() + "`";
        }

        if (isChildTable) {
            sql.append("    ").append(tabFirstField).append(" VARCHAR(255) COMMENT '继承父").append(context.getFirstField()).append("',\n");
            sql.append("    `").append(ORDER_COLUMN).append("` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',\n");
        } else {
            sql.append("    `").append(context.getFirstField()).append("` VARCHAR(255) PRIMARY KEY COMMENT '").append(context.getFirstField()).append("',\n");
            sql.append("    `").append(ORDER_COLUMN).append("` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',\n");
        }
        // **字段数量超过 50 时，默认使用 TEXT 避免行大小超限**
        // MySQL InnoDB 单行最大约8KB,大量VARCHAR字段会导致 "Row size too large" 错误
        boolean useTextType = fields.size() > 50;
        // 解析字段
        int i = 0;
        if(WORLD_SPECIAL_TAB_NAMES.contains(tableName)){
            sql.append("    ").append("`world__id`").append(" ").append("VARCHAR(64)")
                    .append(" COMMENT '").append("world__id").append("',\n");

        }
        if(context.isWorld()){
            sql.append("    ").append("`mapTp`").append(" ").append("VARCHAR(64)")
                    .append(" COMMENT '").append("mapTp").append("',\n");
        }
        for (Element field : fields) {
            i++;
            if(!isChildTable && i == 1){
                continue;
            }
            String fieldType = getColumnType(field.getName(), useTextType, context);
            String fieldName = field.getName();
            sql.append("    ").append("`" + fieldName + "`").append(" ").append(fieldType)
                    .append(" COMMENT '").append( AliyunTranslateUtil.translate(fieldName)).append("',\n");
            if(field.elements().isEmpty() && !field.attributes().isEmpty()){
                field.attributes().forEach( attribute -> {
                    sql.append("    ").append("`_attr__" +field.getName() + "__" + attribute.getName() + "`").append(" ").append("VARCHAR(128)")
                            .append(" COMMENT '").append( AliyunTranslateUtil.translate(attribute.getName())).append("',\n");
                });
            }
        }
        for (Attribute attribute : attributes) {
            String attributeName = attribute.getName();
            sql.append("    ").append("`_attr_" + attributeName + "`").append(" ").append("VARCHAR(128)")
                    .append(" COMMENT '").append( AliyunTranslateUtil.translate(attributeName)).append("',\n");
        }

        // 去掉最后的逗号
        sql.setLength(sql.length() - 2);
        sql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = '"+AliyunTranslateUtil.translate(tableName)+"';");

        return sql.toString();
    }

    // 根据字段名和表字段数量动态调整字段类型
    private static String getColumnType(String fieldName, boolean useTextType, GenerationContext context) {
        if (context.getFieldLenJson() == null) {
            // 字段数量过多时使用TEXT避免行大小超限
            return useTextType ? "TEXT" : "VARCHAR(64)";
        }
        int len = context.getFieldLenJson().containsKey(fieldName)
                ? context.getFieldLenJson().getIntValue(fieldName) : 0;
        if(len == 0){
            return useTextType ? "TEXT" : "VARCHAR(64)";
        }

        // 策略: VARCHAR长度超过255或字段数量过多时使用TEXT类型
        // 这样可以避免MySQL的"Row size too large"错误
        if (useTextType || len > 255) {
            return "TEXT";
        }

        return "VARCHAR(" + len + ")";
    }

    public static String shortenString(String input, int maxLength) {
        if (input.length() <= maxLength) {
            return input;
        }

        String[] parts = input.split("__");
        for (int i = parts.length - 1; i >= 0; i--) {
            parts[i] = shortenSubString(parts[i]);
            String newString = String.join("__", parts);
            if (newString.length() <= maxLength) {
                return newString;
            }
        }
        return input; // 兜底，若无法缩短则返回原字符串
    }

    private static String shortenSubString(String part) {
        String[] subParts = part.split("_");
        StringBuilder sb = new StringBuilder();
        for (String sub : subParts) {
            char lastChar = sub.charAt(sub.length() - 1);
            sb.append(sub.length() > 1 ? ("" + sub.charAt(0)) : sub).append("_");
            //sb.append(sub.length() > 1 ? ("" + sub.charAt(0) + lastChar) : sub).append("_");
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
    }
}
