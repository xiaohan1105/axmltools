package red.jiuzhou.dbxml;

import com.alibaba.fastjson.annotation.JSONField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
/**
 * @className: red.jiuzhou.dbxml.TableConf.java
 * @description: 表配置
 * @author: yanxq
 * @date:  2025-04-15 20:41
 * @version V1.0
 */
public class TableConf {
    private static final Logger log = LoggerFactory.getLogger(DbToXmlGenerator.class);

    @JSONField(name = "file_path")
    private String filePath;

    @JSONField(name = "table_name")
    private String tableName;

    @JSONField(name = "real_table_name")
    private String realTableName;

    @JSONField(name = "xml_root_tag")
    private String xmlRootTag;

    @JSONField(name = "xml_root_attr")
    private String xmlRootAttr;

    @JSONField(name = "xml_item_tag")
    private String xmlItemTag;

    @JSONField(name = "sql")
    private String sql;

    @JSONField(name = "change_fileds") // 注意 JSON 中是 `change_fileds`，如果是拼写错误可改为 `change_fields`
    private String changeFields;

    @JSONField(name = "list")
    private List<ColumnMapping> list;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getXmlRootTag() { return xmlRootTag; }
    public void setXmlRootTag(String xmlRootTag) { this.xmlRootTag = xmlRootTag; }

    public String getXmlItemTag() { return xmlItemTag; }
    public void setXmlItemTag(String xmlItemTag) { this.xmlItemTag = xmlItemTag; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public String getChangeFields() { return changeFields; }
    public void setChangeFields(String changeFields) { this.changeFields = changeFields; }

    public List<ColumnMapping> getList() {
        if(list == null){
            list = new ArrayList<>();
        }
        return list;
    }
    public void setList(List<ColumnMapping> list) { this.list = list; }

    public String getXmlRootAttr() {
        return xmlRootAttr;
    }

    public void setXmlRootAttr(String xmlRootAttr) {
        this.xmlRootAttr = xmlRootAttr;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getRealTableName() {
        return realTableName;
    }

    public void setRealTableName(String realTableName) {
        this.realTableName = realTableName;
    }

    public List<String> getListDbcolumnList() {
        List<String> dbColumnList = new ArrayList<>();
        getList().forEach( columnMapping -> {
            if(!columnMapping.getAddDataNode().isEmpty()){
                if(columnMapping.getAddDataNode().contains(":")){
                    String[] split = columnMapping.getAddDataNode().split(":");
                    dbColumnList.add(split[0]);
                }else{
                    dbColumnList.add(columnMapping.getAddDataNode());
                }
            }else{
                dbColumnList.add(columnMapping.getDbColumn());
            }
        });
        return dbColumnList;
    }

    public List<String> getListXmlTagList() {

        return list.stream()
                .map(ColumnMapping::getXmlTag)
                .collect(Collectors.toList());
    }

    public ColumnMapping getColumnMapping(String dbColumn) {
        for (ColumnMapping columnMapping : getList()) {
            if (columnMapping.getAddDataNode().equals(dbColumn) || columnMapping.getAddDataNode().contains(dbColumn + ":")) {
                return columnMapping;
            }
            if (columnMapping.getAddDataNode().trim().isEmpty() && columnMapping.getDbColumn().equals(dbColumn)) {
                return columnMapping;
            }
        }
        return null;
    }
    public ColumnMapping getColumnMappingByXmlTag(String xmlTag) {
        for (ColumnMapping columnMapping : getList()) {
            if (columnMapping.getAddDataNode().equals(xmlTag) || columnMapping.getAddDataNode().contains(xmlTag + ":")) {
                return columnMapping;
            }
            if (columnMapping.getAddDataNode().trim().isEmpty() && columnMapping.getXmlTag().equals(xmlTag)) {
                return columnMapping;
            }
        }
        return null;
    }

     public void chk(){
        if (!StringUtils.hasLength(this.getTableName())){
            throw new RuntimeException("tableName is null");
        }
        if (!StringUtils.hasLength(this.getXmlRootTag())){
            throw new RuntimeException("xmlRootTag is null");
        }
//        if (!StringUtils.hasLength(this.getXmlItemTag())){
//            throw new RuntimeException("xmlItemTag is null");
//        }
        if (!StringUtils.hasLength(this.getSql())){
            throw new RuntimeException("sql is null");
        }
        if(this.getList() != null){
            for (ColumnMapping columnMapping : this.getList()) {
                columnMapping.chk();
            }
        }
     }

     public List<String> getAllTableNameList(){
        if(this.getList() == null){
            ArrayList<String> tabNameList = new ArrayList<>();
            tabNameList.add(this.getTableName());
            return tabNameList;
        }
         List<String> tabNameList = this.getList().stream()
                 .map(ColumnMapping::getTableName)
                 .collect(Collectors.toList());
         List<List<String>> subTabNameList = this.getList().stream()
                 .map(ColumnMapping::getAllTableNameList)
                 .collect(Collectors.toList());
         subTabNameList.forEach(tabNameList::addAll);

         tabNameList.add(this.getTableName());
         // Using Stream API to remove duplicates
         return tabNameList.stream()
                 .distinct()
                 .collect(Collectors.toList());
         //return tabNameList;


     }

    @Override
    public String toString() {
        return "TableConf{" +
                "tableName='" + tableName + '\'' +
                ", xmlRootTag='" + xmlRootTag + '\'' +
                ", xmlItemTag='" + xmlItemTag + '\'' +
                ", sql='" + sql + '\'' +
                ", changeFields='" + changeFields + '\'' +
                ", list=" + list +
                '}';
    }
}