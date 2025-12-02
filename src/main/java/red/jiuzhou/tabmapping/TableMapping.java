package red.jiuzhou.tabmapping;

import java.util.Set;
import java.util.HashSet;

/**
 * 表映射配置类
 * 用于管理客户端表和服务端表之间的字段映射关系
 */
public class TableMapping {
    // 基础字段
    public String svr_tab;
    public String clt_tab;
    public String same_fileds;
    public String svr_redundant_fields;
    public String clt_redundant_fields;

    // 扩展字段 - XML信息
    private String xmlFilePath;
    private String xmlFileName;
    private String xmlNodePath;

    // 扩展字段 - 映射元数据
    private String mappingType;
    private String status;
    private String description;
    private long createdTime;
    private long updatedTime;

    // ========== 基础字段的 Getter/Setter ==========

    public String getSvrTab() {
        return svr_tab;
    }

    public void setSvrTab(String svrTab) {
        this.svr_tab = svrTab;
    }

    public String getCltTab() {
        return clt_tab;
    }

    public void setCltTab(String cltTab) {
        this.clt_tab = cltTab;
    }

    public String getSameFileds() {
        return same_fileds;
    }

    public void setSameFileds(String sameFileds) {
        this.same_fileds = sameFileds;
    }

    public String getSvrRedundantFields() {
        return svr_redundant_fields;
    }

    public void setSvrRedundantFields(String svrRedundantFields) {
        this.svr_redundant_fields = svrRedundantFields;
    }

    public String getCltRedundantFields() {
        return clt_redundant_fields;
    }

    public void setCltRedundantFields(String cltRedundantFields) {
        this.clt_redundant_fields = cltRedundantFields;
    }

    // ========== XML信息相关方法 ==========

    public String getXmlFilePath() {
        return xmlFilePath;
    }

    public void setXmlFilePath(String xmlFilePath) {
        this.xmlFilePath = xmlFilePath;
    }

    public String getXmlFileName() {
        return xmlFileName;
    }

    public void setXmlFileName(String xmlFileName) {
        this.xmlFileName = xmlFileName;
    }

    public String getXmlNodePath() {
        return xmlNodePath;
    }

    public void setXmlNodePath(String xmlNodePath) {
        this.xmlNodePath = xmlNodePath;
    }

    public boolean hasXmlInfo() {
        return xmlFilePath != null && !xmlFilePath.isEmpty();
    }

    // ========== 映射元数据相关方法 ==========

    public String getMappingType() {
        return mappingType;
    }

    public void setMappingType(String mappingType) {
        this.mappingType = mappingType;
    }

    public String getMappingTypeDisplay() {
        if (mappingType == null) return "未知";
        switch (mappingType) {
            case "auto": return "自动匹配";
            case "manual": return "手动配置";
            case "partial": return "部分匹配";
            default: return mappingType;
        }
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusDisplay() {
        if (status == null) return "未知";
        switch (status) {
            case "active": return "已启用";
            case "inactive": return "已禁用";
            case "pending": return "待确认";
            case "error": return "错误";
            default: return status;
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMappingDescription() {
        StringBuilder sb = new StringBuilder();
        if (clt_tab != null) {
            sb.append("客户端表: ").append(clt_tab);
        }
        if (svr_tab != null) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append("服务端表: ").append(svr_tab);
        }
        return sb.toString();
    }

    // ========== 时间戳相关方法 ==========

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(long updatedTime) {
        this.updatedTime = updatedTime;
    }

    // ========== 工具方法 ==========

    public Set<String> getSameFieldsSet() {
        Set<String> set = new HashSet<>();
        if (same_fileds != null && !same_fileds.trim().isEmpty()) {
            for (String field : same_fileds.split("\\s*,\\s*")) {
                set.add(field.trim());
            }
        }
        return set;
    }

    @Override
    public String toString() {
        return "TableMapping{" +
                "clt_tab='" + clt_tab + '\'' +
                ", svr_tab='" + svr_tab + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
