package red.jiuzhou.dbxml;

import com.alibaba.fastjson.annotation.JSONField;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @version V1.0
 * @className: red.jiuzhou.dbxml.ColumnMapping.java
 * @description: 列映射关系
 * @author: yanxq
 * @date: 2025-04-09 15:58
 */
public class ColumnMapping {
    @JSONField(name = "table_name")
    private String tableName;

    @JSONField(name = "associatedFiled")
    private String associatedFiled;

    @JSONField(name = "db_column")
    private String dbColumn;

    @JSONField(name = "xml_tag")
    private String xmlTag;

    @JSONField(name = "addDataNode")
    private String addDataNode;

    @JSONField(name = "fileds")
    private String fields;

    @JSONField(name = "exclude_fileds")
    private String excludeFields;

    @JSONField(name = "sql")
    private String sql;

    @JSONField(name = "list") // 递归嵌套结构
    private List<ColumnMapping> list;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getAssociatedFiled() {
        return associatedFiled;
    }

    public void setAssociatedFiled(String associatedFiled) {
        this.associatedFiled = associatedFiled;
    }

    public String getDbColumn() {
        return dbColumn;
    }

    public void setDbColumn(String dbColumn) {
        this.dbColumn = dbColumn;
    }

    public String getXmlTag() {
        return xmlTag;
    }

    public void setXmlTag(String xmlTag) {
        this.xmlTag = xmlTag;
    }

    public String getAddDataNode() {
        return addDataNode;
    }

    public void setAddDataNode(String addDataNode) {
        this.addDataNode = addDataNode;
    }

    public String getFields() {
        return fields;
    }

    public void setFields(String fields) {
        this.fields = fields;
    }

    public String getExcludeFields() {
        return excludeFields;
    }

    public void setExcludeFields(String excludeFields) {
        this.excludeFields = excludeFields;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<ColumnMapping> getList() {
        return list;
    }

    public void setList(List<ColumnMapping> list) {
        this.list = list;
    }

    /**
     * @param xmlTag xml标签
     * @return ColumnMapping
     * @methodName: getColumnMappingByXmlTag
     * @description: 根据xml标签获取列映射关系
     * @author: yanxq
     * @date: 2025-04-09 15:59
     */
    public ColumnMapping getColumnMappingByXmlTag(String xmlTag) {

        for (ColumnMapping columnMapping : list) {
            if (columnMapping.getAddDataNode().equals(xmlTag) || columnMapping.getAddDataNode().contains(xmlTag + ":")) {
                return columnMapping;
            }
            if (columnMapping.getAddDataNode().trim().isEmpty() && columnMapping.getXmlTag().equals(xmlTag)) {
                return columnMapping;
            }
        }
        return null;
    }

    public List<String> getListDbcolumnList() {
        return list.stream()
                .map(ColumnMapping::getDbColumn)
                .collect(Collectors.toList());

    }

    public List<String> getListXmlTagList() {
        return list.stream()
                .map(ColumnMapping::getXmlTag)
                .collect(Collectors.toList());
    }

    public void chk() {
        if (!StringUtils.hasLength(this.getTableName())) {
            throw new RuntimeException("tableName is null");
        }
        if (!StringUtils.hasLength(this.getAssociatedFiled())) {
            throw new RuntimeException("associatedFiled is null");
        }
        if (!StringUtils.hasLength(this.getDbColumn())) {
            throw new RuntimeException("dbColumn is null");
        }
        if (!StringUtils.hasLength(this.getXmlTag())) {
            throw new RuntimeException("xmlTag is null");
        }
        if (!StringUtils.hasLength(this.getSql())) {
            throw new RuntimeException("sql is null");
        }
        if (this.getList() != null) {
            for (ColumnMapping columnMapping : this.getList()) {
                columnMapping.chk();
            }
        }

    }

    /**
     * @methodName:  getAllTableNameList
     * @return List<String> 表名列表
     * @description: 获取所有表名列表
     * @author: yanxq
     * @date:  2025-04-09 16:00
     */
    public List<String> getAllTableNameList() {
        if (this.getList() == null) {
            return new ArrayList<>();
        }
        List<String> tabNameList = this.getList().stream()
                .map(ColumnMapping::getTableName)
                .collect(Collectors.toList());
        List<List<String>> subTabNameList = this.getList().stream()
                .map(ColumnMapping::getAllTableNameList)
                .collect(Collectors.toList());
        subTabNameList.forEach(tabNameList::addAll);
        return tabNameList;
    }

    @Override
    public String toString() {
        return "ColumnMapping{" +
                "tableName='" + tableName + '\'' +
                ", associatedFiled='" + associatedFiled + '\'' +
                ", dbColumn='" + dbColumn + '\'' +
                ", xmlTag='" + xmlTag + '\'' +
                ", addDataNode='" + addDataNode + '\'' +
                ", fields='" + fields + '\'' +
                ", excludeFields='" + excludeFields + '\'' +
                ", sql='" + sql + '\'' +
                ", list=" + list +
                '}';
    }
}
