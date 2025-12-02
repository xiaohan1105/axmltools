package red.jiuzhou.api;

import org.springframework.web.bind.annotation.*;
import red.jiuzhou.api.common.CommonResult;

import java.util.*;

import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @className: red.jiuzhou.api.ApiDocController
 * @description: API文档控制器，提供所有可用API端点的文档
 * @author: yanxq
 * @date: 2025-09-17
 * @version V1.0
 */
@RestController
@RequestMapping("/api/doc")
public class ApiDocController {

    /**
     * 获取安全使用指南
     */
    @GetMapping("/security-guide")
    public CommonResult<Map<String, Object>> getSecurityGuide() {
        Map<String, Object> guide = new HashMap<>();

        guide.put("title", "DbXmlTool API 安全使用指南");
        guide.put("version", "v1.0");
        guide.put("lastUpdated", "2025-09-17");

        // 认证信息
        Map<String, Object> auth = new HashMap<>();
        auth.put("type", "No Authentication Required");
        auth.put("note", "认证已禁用，所有API端点可直接访问");
        guide.put("authentication", auth);

        // 安全限制
        Map<String, Object> security = new HashMap<>();
        security.put("sqlExecution", "仅限SELECT、SHOW、DESCRIBE、EXPLAIN语句");
        security.put("fileAccess", "限制在配置的安全目录内");
        security.put("inputValidation", "所有输入都经过验证和清理");
        security.put("rateLimiting", "建议实施API速率限制");
        guide.put("securityFeatures", security);

        // 权限说明
        Map<String, Object> permissions = new HashMap<>();
        permissions.put("ADMIN", Arrays.asList(
            "SQL执行", "文件删除", "系统管理", "所有读写操作"
        ));
        permissions.put("USER", Arrays.asList(
            "文件读取", "数据查询", "基本API访问"
        ));
        guide.put("permissions", permissions);

        // 安全建议
        guide.put("recommendations", Arrays.asList(
            "定期更改默认密码",
            "在生产环境中配置HTTPS",
            "启用访问日志监控",
            "限制API访问的网络范围",
            "定期备份重要数据"
        ));

        return success(guide);
    }

    /**
     * 获取所有API端点文档
     * @return API文档列表
     */
    @GetMapping("/endpoints")
    public CommonResult<Map<String, Object>> getAllEndpoints() {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> endpoints = new ArrayList<>();

        // 现有API端点
        endpoints.addAll(getTabApiEndpoints());
        endpoints.addAll(getFileApiEndpoints());

        // 新增API端点
        endpoints.addAll(getEnumQueryApiEndpoints());
        endpoints.addAll(getSqlExecutorApiEndpoints());
        endpoints.addAll(getSqlConverterApiEndpoints());
        endpoints.addAll(getDirectoryApiEndpoints());
        endpoints.addAll(getJsonEditorApiEndpoints());
        endpoints.addAll(getSystemApiEndpoints());

        result.put("totalEndpoints", endpoints.size());
        result.put("endpoints", endpoints);
        result.put("baseUrl", "http://localhost:8080");
        result.put("version", "1.0");
        result.put("description", "DbXmlTool API - 提供完整的GUI功能的API访问");

        return success(result);
    }

    /**
     * 获取API使用统计
     * @return API统计信息
     */
    @GetMapping("/stats")
    public CommonResult<Map<String, Object>> getApiStats() {
        Map<String, Object> result = new HashMap<>();

        Map<String, Integer> controllerStats = new HashMap<>();
        controllerStats.put("TabController", 2);
        controllerStats.put("FileController", 2);
        controllerStats.put("EnumQueryController", 5);
        controllerStats.put("SqlExecutorController", 4);
        controllerStats.put("SqlConverterController", 6);
        controllerStats.put("DirectoryController", 8);
        controllerStats.put("JsonEditorController", 7);
        controllerStats.put("SystemController", 7);

        int totalEndpoints = controllerStats.values().stream().mapToInt(Integer::intValue).sum();

        result.put("totalControllers", controllerStats.size());
        result.put("totalEndpoints", totalEndpoints);
        result.put("controllerStats", controllerStats);

        return success(result);
    }

    // 私有方法：获取各个控制器的端点信息

    private List<Map<String, Object>> getTabApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpoint("GET", "/api/tab/getTabInfo", "获取表信息",
                "根据表名获取完整的表配置信息，包括DDL、字段枚举值、SQL模板等",
                Arrays.asList("tabName", "fields(可选)", "allSqlFlg(可选)")));

        endpoints.add(createEndpoint("GET", "/api/tab/getTabListByTabName", "获取表配置路径列表",
                "根据表名获取所有匹配的配置文件路径",
                Arrays.asList("tabName")));

        return endpoints;
    }

    private List<Map<String, Object>> getFileApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpoint("GET", "/api/file/getFileList", "获取XML文件列表",
                "获取指定路径或配置路径下的所有XML文件",
                Arrays.asList("filePath(可选)")));

        endpoints.add(createEndpoint("GET", "/api/file/getSimpleFileList", "获取简化文件列表",
                "获取XML文件名列表（不包含完整路径）",
                Arrays.asList("filePath(可选)")));

        return endpoints;
    }

    private List<Map<String, Object>> getEnumQueryApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpoint("GET", "/api/enum/tables", "获取所有表名",
                "获取数据库中所有表的名称列表", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/enum/columns", "获取表字段",
                "获取指定表的所有字段名称", Arrays.asList("tableName")));

        endpoints.add(createEndpoint("GET", "/api/enum/distribution", "查询字段枚举分布",
                "统计指定表字段的所有不同值及其出现次数",
                Arrays.asList("tableName", "columnName")));

        endpoints.add(createEndpoint("GET", "/api/enum/count", "获取表记录数",
                "获取指定表的总记录数", Arrays.asList("tableName")));

        endpoints.add(createEndpoint("GET", "/api/enum/batch-distribution", "批量查询字段分布",
                "同时查询多个字段的枚举分布",
                Arrays.asList("tableName", "columnNames")));

        return endpoints;
    }

    private List<Map<String, Object>> getSqlExecutorApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpoint("POST", "/api/sql/execute", "执行SQL语句",
                "执行单条SQL语句，支持SELECT、INSERT、UPDATE、DELETE等",
                Arrays.asList("sql", "limit(可选，默认1000)")));

        endpoints.add(createEndpoint("POST", "/api/sql/batch-execute", "批量执行SQL",
                "批量执行多条SQL语句", Arrays.asList("sqlScript", "limit(可选)")));

        endpoints.add(createEndpoint("POST", "/api/sql/validate", "验证SQL语法",
                "验证SQL语句的语法正确性", Arrays.asList("sql")));

        endpoints.add(createEndpoint("GET", "/api/sql/table-info", "获取表结构信息",
                "获取表的详细结构信息，包括字段、DDL、记录数等",
                Arrays.asList("tableName")));

        return endpoints;
    }

    private List<Map<String, Object>> getSqlConverterApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpoint("GET", "/api/sql-converter/databases", "获取数据库列表",
                "获取所有可用的数据库名称", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/sql-converter/tables", "获取表列表",
                "获取指定数据库中的所有表", Arrays.asList("database(可选)")));

        endpoints.add(createEndpoint("GET", "/api/sql-converter/table-fields", "获取表字段信息",
                "获取指定表的详细字段信息", Arrays.asList("tableName")));

        endpoints.add(createEndpoint("POST", "/api/sql-converter/convert", "转换SQL语句",
                "将SQL语句从源表结构转换为目标表结构", Arrays.asList("request body")));

        endpoints.add(createEndpoint("GET", "/api/sql-converter/template", "生成SQL模板",
                "根据表结构生成INSERT、UPDATE、DELETE、SELECT模板",
                Arrays.asList("tableName", "operationType")));

        endpoints.add(createEndpoint("POST", "/api/sql-converter/batch-convert", "批量转换SQL",
                "批量转换多条SQL语句", Arrays.asList("request body")));

        return endpoints;
    }

    private List<Map<String, Object>> getDirectoryApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpoint("GET", "/api/directory/list", "列出目录内容",
                "获取指定目录下的文件和子目录列表",
                Arrays.asList("path(可选)", "includeFiles(可选)", "recursive(可选)")));

        endpoints.add(createEndpoint("POST", "/api/directory/create", "创建目录",
                "创建新目录", Arrays.asList("path", "recursive(可选)")));

        endpoints.add(createEndpoint("DELETE", "/api/directory/delete", "删除目录",
                "删除指定目录", Arrays.asList("path", "recursive(可选)")));

        endpoints.add(createEndpoint("PUT", "/api/directory/rename", "重命名目录",
                "重命名目录", Arrays.asList("oldPath", "newPath")));

        endpoints.add(createEndpoint("GET", "/api/directory/info", "获取目录信息",
                "获取目录的详细信息", Arrays.asList("path")));

        endpoints.add(createEndpoint("POST", "/api/directory/regenerate-menu", "重新生成菜单",
                "重新生成左侧导航菜单JSON文件", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/directory/menu", "获取菜单内容",
                "获取左侧菜单的JSON内容", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/directory/search", "搜索目录和文件",
                "在指定路径下搜索文件和目录",
                Arrays.asList("basePath(可选)", "pattern", "includeFiles(可选)", "caseSensitive(可选)")));

        return endpoints;
    }

    private List<Map<String, Object>> getJsonEditorApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpoint("GET", "/api/json-editor/read", "读取JSON文件",
                "读取指定路径的JSON文件内容", Arrays.asList("filePath")));

        endpoints.add(createEndpoint("POST", "/api/json-editor/write", "写入JSON文件",
                "写入JSON内容到指定文件", Arrays.asList("request body")));

        endpoints.add(createEndpoint("POST", "/api/json-editor/validate", "验证JSON格式",
                "验证JSON字符串的格式正确性", Arrays.asList("content")));

        endpoints.add(createEndpoint("POST", "/api/json-editor/format", "格式化JSON",
                "格式化JSON字符串，添加缩进和换行", Arrays.asList("content")));

        endpoints.add(createEndpoint("POST", "/api/json-editor/minify", "压缩JSON",
                "压缩JSON字符串，移除空白字符", Arrays.asList("content")));

        endpoints.add(createEndpoint("GET", "/api/json-editor/metadata", "获取JSON文件元数据",
                "获取JSON文件的元数据信息", Arrays.asList("filePath")));

        endpoints.add(createEndpoint("POST", "/api/json-editor/create", "创建JSON文件",
                "创建新的JSON文件", Arrays.asList("request body")));

        return endpoints;
    }

    private List<Map<String, Object>> getSystemApiEndpoints() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpoint("GET", "/api/system/info", "获取系统信息",
                "获取JVM、操作系统、应用程序等基本信息", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/system/config", "获取系统配置",
                "获取应用程序的配置信息", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/system/database-status", "数据库状态检查",
                "检查数据库连接状态", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/system/disk-space", "获取磁盘空间",
                "获取磁盘空间使用情况", Collections.emptyList()));

        endpoints.add(createEndpoint("POST", "/api/system/gc", "执行垃圾回收",
                "手动触发JVM垃圾回收", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/system/runtime-stats", "运行时统计",
                "获取应用程序运行时统计信息", Collections.emptyList()));

        endpoints.add(createEndpoint("GET", "/api/system/health", "健康检查",
                "执行系统健康检查", Collections.emptyList()));

        return endpoints;
    }

    private Map<String, Object> createEndpoint(String method, String path, String name, String description, List<String> parameters) {
        Map<String, Object> endpoint = new HashMap<>();
        endpoint.put("method", method);
        endpoint.put("path", path);
        endpoint.put("name", name);
        endpoint.put("description", description);
        endpoint.put("parameters", parameters);
        return endpoint;
    }
}