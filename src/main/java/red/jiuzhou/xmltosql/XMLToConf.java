package red.jiuzhou.xmltosql;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.JSONRecord;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public class XMLToConf {
    private static final Logger log = LoggerFactory.getLogger(XMLToConf.class);
    private static String firstField = "";

    private static String fileName;
    static HashMap<String, String> tabNameMap = new HashMap<>();
    public static String generateMySQLTables(String filePath, String xmlStr, String newFileName) {

        try {
            String tabName = FileUtil.getName(filePath).split("\\.")[0];
            JSONRecord jsonConf = new JSONRecord();
            if(StringUtils.hasLength(newFileName)){
                jsonConf.put("real_table_name", tabName);
                tabName = newFileName;
            }
            fileName = tabName;

            Document document = DocumentHelper.parseText(xmlStr);
            Element root = document.getRootElement();

            jsonConf.put("xml_root_tag", root.getName());
            jsonConf.put("file_path", filePath);
            if(!root.attributes().isEmpty()){
                jsonConf.put("xml_root_attr", root.attributes().get(0).getName() + "=" + root.attributes().get(0).getValue());
            }
            // 解析根节点的第一个子节点
            if(root.elements().isEmpty()){
                return "{}";
            }

            if("world".equals(fileName)){
                firstField = root.elements().get(0).getName();
                jsonConf.put("xml_item_tag", "");
            }else{
                root = root.elements().get(0);
                firstField = root.elements().get(0).getName();
                jsonConf.put("xml_item_tag", root.getName());
            }

            jsonConf.put("table_name", tabName);
            jsonConf.put("sql", "select * from " + tabName + " order by CAST(" + firstField + " AS UNSIGNED) ASC");
            parseElement(root, jsonConf, null, "");
            Object clone = jsonConf.getOrCreateRecordset("list").list().get(0).getOrCreateRecordset("list").clone();
            jsonConf.getOrCreateRecordset("list").clear();
            jsonConf.put("list", clone);
            System.out.println(tabNameMap.toString());
            return jsonConf.toString(true);
        } catch (Exception e) {
            log.error("解析XML{}文件生成配置信息失败", filePath, e);
        }

        return null;
    }

    private static void parseElement(Element element, JSONRecord tableConf, JSONRecord columnMapping, String parentTable) {

        List<Element> children = element.elements();
        if (children.isEmpty()) return;
        // 生成表名，使用双下划线区分层级
        String tableName = "".equals(parentTable) ? fileName : parentTable + "__" + element.getName();
        String origTableName = tableName;
        tableName = XMLToMySQLGenerator.shortenString(tableName, 60);
        tabNameMap.put(tableName, origTableName);
        if(children.size() == 1 && !children.get(0).elements().isEmpty()){
            // 递归解析子节点
            for (Element child : children) {
                parseElement(child, tableConf, columnMapping, tableName);
            }
            return;
        }

        JSONRecord subCmap = new JSONRecord();
        subCmap.put("table_name", tableName);
        subCmap.put("db_column", element.getName());
        subCmap.put("xml_tag", element.getName());
        subCmap.put("addDataNode", "");
        subCmap.put("sql", "select * from " + tableName + " where "+firstField+" = '#associated_filed' order by CAST(" + firstField + " AS UNSIGNED) ASC");
        subCmap.put("associatedFiled", firstField);
        if(StringUtils.hasLength(parentTable) && element.getParent() != null && element.getParent().elements().size() == 1
                && !element.getParent().elements().get(0).elements().isEmpty()){
            String delimiter = "__";
            parentTable = getRealTableName(parentTable);
            int lastIndex = parentTable.lastIndexOf(delimiter);
            if (lastIndex != -1) {
                subCmap.put("addDataNode", parentTable.substring(lastIndex).replaceAll("__", ""));
                parentTable = parentTable.substring(0, lastIndex);
            }

            lastIndex = parentTable.lastIndexOf(delimiter);
            if (lastIndex != -1) {
                String lastTagName = parentTable.substring(lastIndex).replace("__", "");
                if(!lastTagName.equals(columnMapping.getValue("xml_tag"))){
                    subCmap.put("addDataNode",  lastTagName + ":" + subCmap.getValue("addDataNode"));
                }

            }

        }
        if(columnMapping != null){
            columnMapping.getOrCreateRecordset("list").add(subCmap);
        }else{
            tableConf.getOrCreateRecordset("list").add(subCmap);
        }
        // 递归解析子节点
        for (Element child : children) {
            parseElement(child, tableConf, subCmap, tableName );
        }
    }

    private static String getRealTableName(String tableName){
        log.info("tabNameMap = {}; tableName = {}", tabNameMap, tableName);
        String realTabname = tabNameMap.get(tableName);
        int i = realTabname.lastIndexOf("__");
        if(i == -1){
            return realTabname;
        }
        String substring = realTabname.substring(0, i);
        int i1 = substring.lastIndexOf("_");
        if(i1 == -1){
            return realTabname;
        }
        String nsubstring = substring.substring(i1);
        if(nsubstring.length() == 2){
            return getRealTableName(substring) +  realTabname.substring(i);
        }
        return realTabname;
    }

    public static void main(String[] args) {
        String filePath = "D:\\workspace\\dbxmlTool\\data\\DATA\\SVR_DATA\\Worlds\\IDRun\\world.xml";
        String allNodeStr = FileUtil.readUtf8String("D:\\workspace\\dbxmlTool\\src\\main\\resources\\CONF\\SVR_DATA\\Worlds\\allNodeXml\\world.xml");
        final String s = generateMySQLTables(filePath, allNodeStr, null);
        System.out.println(s);
    }

}
