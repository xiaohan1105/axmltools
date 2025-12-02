package red.jiuzhou.api;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import red.jiuzhou.api.common.CommonResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static red.jiuzhou.api.common.CommonResult.error;
import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @className: red.jiuzhou.api.JsonEditorController
 * @description: JSON编辑器API控制器，提供JSON文件的读写和编辑功能
 * @author: yanxq
 * @date: 2025-09-17
 * @version V1.0
 */
@RestController
@RequestMapping("/api/json-editor")
public class JsonEditorController {

    private static final Logger log = LoggerFactory.getLogger(JsonEditorController.class);

    /**
     * 读取JSON文件内容
     * @param filePath JSON文件路径
     * @return JSON文件内容
     */
    @GetMapping("/read")
    public CommonResult<Map<String, Object>> readJsonFile(@RequestParam String filePath) {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                return error(1, "文件不存在: " + filePath);
            }

            if (!file.isFile()) {
                return error(1, "指定路径不是文件: " + filePath);
            }

            String content = FileUtil.readUtf8String(file);

            Map<String, Object> result = new HashMap<>();
            result.put("filePath", filePath);
            result.put("fileName", file.getName());
            result.put("fileSize", file.length());
            result.put("lastModified", file.lastModified());
            result.put("content", content);

            // 尝试解析JSON以验证格式
            try {
                Object jsonObject = JSON.parse(content);
                result.put("isValidJson", true);
                result.put("parsedJson", jsonObject);
            } catch (Exception e) {
                result.put("isValidJson", false);
                result.put("parseError", e.getMessage());
            }

            return success(result);
        } catch (Exception e) {
            log.error("读取JSON文件失败: " + filePath, e);
            return error(1, "读取JSON文件失败: " + e.getMessage());
        }
    }

    /**
     * 写入JSON文件内容
     * @param request 写入请求
     * @return 写入结果
     */
    @PostMapping("/write")
    public CommonResult<Map<String, Object>> writeJsonFile(@RequestBody WriteJsonRequest request) {
        try {
            String filePath = request.getFilePath();
            String content = request.getContent();
            boolean validateJson = request.isValidateJson();
            boolean createBackup = request.isCreateBackup();

            File file = new File(filePath);

            // 验证JSON格式
            if (validateJson) {
                try {
                    JSON.parse(content);
                } catch (Exception e) {
                    return error(1, "JSON格式错误: " + e.getMessage());
                }
            }

            // 创建备份
            if (createBackup && file.exists()) {
                String backupPath = filePath + ".backup." + System.currentTimeMillis();
                FileUtil.copy(file, new File(backupPath), true);
                log.info("已创建备份文件: " + backupPath);
            }

            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 写入文件
            FileUtil.writeUtf8String(content, file);

            Map<String, Object> result = new HashMap<>();
            result.put("filePath", filePath);
            result.put("fileName", file.getName());
            result.put("fileSize", file.length());
            result.put("lastModified", file.lastModified());
            result.put("success", true);
            result.put("message", "JSON文件保存成功");

            return success(result);
        } catch (Exception e) {
            log.error("写入JSON文件失败: " + request.getFilePath(), e);
            return error(1, "写入JSON文件失败: " + e.getMessage());
        }
    }

    /**
     * 验证JSON格式
     * @param content JSON内容
     * @return 验证结果
     */
    @PostMapping("/validate")
    public CommonResult<Map<String, Object>> validateJson(@RequestParam String content) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("content", content);

            try {
                Object parsedJson = JSON.parse(content);
                result.put("isValid", true);
                result.put("parsedJson", parsedJson);
                result.put("message", "JSON格式正确");

                // 格式化JSON
                if (parsedJson instanceof JSONObject) {
                    result.put("formattedJson", JSON.toJSONString(parsedJson, true));
                } else {
                    result.put("formattedJson", JSON.toJSONString(parsedJson, true));
                }
            } catch (Exception e) {
                result.put("isValid", false);
                result.put("error", e.getMessage());
                result.put("message", "JSON格式错误");
            }

            return success(result);
        } catch (Exception e) {
            log.error("验证JSON失败", e);
            return error(1, "验证JSON失败: " + e.getMessage());
        }
    }

    /**
     * 格式化JSON内容
     * @param content JSON内容
     * @return 格式化后的JSON
     */
    @PostMapping("/format")
    public CommonResult<Map<String, Object>> formatJson(@RequestParam String content) {
        try {
            Object parsedJson = JSON.parse(content);
            String formattedJson = JSON.toJSONString(parsedJson, true);

            Map<String, Object> result = new HashMap<>();
            result.put("originalContent", content);
            result.put("formattedContent", formattedJson);
            result.put("success", true);

            return success(result);
        } catch (Exception e) {
            log.error("格式化JSON失败", e);
            return error(1, "格式化JSON失败: " + e.getMessage());
        }
    }

    /**
     * 压缩JSON内容（移除空白字符）
     * @param content JSON内容
     * @return 压缩后的JSON
     */
    @PostMapping("/minify")
    public CommonResult<Map<String, Object>> minifyJson(@RequestParam String content) {
        try {
            Object parsedJson = JSON.parse(content);
            String minifiedJson = JSON.toJSONString(parsedJson);

            Map<String, Object> result = new HashMap<>();
            result.put("originalContent", content);
            result.put("minifiedContent", minifiedJson);
            result.put("originalSize", content.length());
            result.put("minifiedSize", minifiedJson.length());
            result.put("compressionRatio", String.format("%.2f%%",
                (1.0 - (double) minifiedJson.length() / content.length()) * 100));

            return success(result);
        } catch (Exception e) {
            log.error("压缩JSON失败", e);
            return error(1, "压缩JSON失败: " + e.getMessage());
        }
    }

    /**
     * 获取JSON文件的元数据信息
     * @param filePath JSON文件路径
     * @return 文件元数据
     */
    @GetMapping("/metadata")
    public CommonResult<Map<String, Object>> getJsonMetadata(@RequestParam String filePath) {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                return error(1, "文件不存在: " + filePath);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("filePath", filePath);
            result.put("fileName", file.getName());
            result.put("fileSize", file.length());
            result.put("lastModified", file.lastModified());
            result.put("canRead", file.canRead());
            result.put("canWrite", file.canWrite());
            result.put("absolutePath", file.getAbsolutePath());
            result.put("parent", file.getParent());

            // 分析JSON内容
            if (file.length() > 0) {
                try {
                    String content = FileUtil.readUtf8String(file);
                    Object parsedJson = JSON.parse(content);

                    result.put("isValidJson", true);
                    result.put("contentLength", content.length());
                    result.put("lineCount", content.split("\n").length);

                    // 分析JSON结构
                    if (parsedJson instanceof JSONObject) {
                        JSONObject jsonObj = (JSONObject) parsedJson;
                        result.put("jsonType", "object");
                        result.put("keyCount", jsonObj.size());
                        result.put("keys", jsonObj.keySet());
                    } else {
                        result.put("jsonType", "array");
                    }

                } catch (Exception e) {
                    result.put("isValidJson", false);
                    result.put("parseError", e.getMessage());
                }
            } else {
                result.put("isEmpty", true);
            }

            return success(result);
        } catch (Exception e) {
            log.error("获取JSON文件元数据失败: " + filePath, e);
            return error(1, "获取JSON文件元数据失败: " + e.getMessage());
        }
    }

    /**
     * 创建新的JSON文件
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping("/create")
    public CommonResult<Map<String, Object>> createJsonFile(@RequestBody CreateJsonRequest request) {
        try {
            String filePath = request.getFilePath();
            String initialContent = request.getInitialContent();
            boolean overwrite = request.isOverwrite();

            File file = new File(filePath);

            if (file.exists() && !overwrite) {
                return error(1, "文件已存在: " + filePath + "。如需覆盖，请设置overwrite为true");
            }

            // 如果没有提供初始内容，创建空的JSON对象
            if (initialContent == null || initialContent.trim().isEmpty()) {
                initialContent = "{}";
            }

            // 验证JSON格式
            try {
                JSON.parse(initialContent);
            } catch (Exception e) {
                return error(1, "初始内容JSON格式错误: " + e.getMessage());
            }

            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 创建文件
            FileUtil.writeUtf8String(initialContent, file);

            Map<String, Object> result = new HashMap<>();
            result.put("filePath", filePath);
            result.put("fileName", file.getName());
            result.put("fileSize", file.length());
            result.put("lastModified", file.lastModified());
            result.put("created", true);
            result.put("message", "JSON文件创建成功");

            return success(result);
        } catch (Exception e) {
            log.error("创建JSON文件失败: " + request.getFilePath(), e);
            return error(1, "创建JSON文件失败: " + e.getMessage());
        }
    }

    // 请求类
    public static class WriteJsonRequest {
        private String filePath;
        private String content;
        private boolean validateJson = true;
        private boolean createBackup = false;

        // Getters and Setters
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public boolean isValidateJson() { return validateJson; }
        public void setValidateJson(boolean validateJson) { this.validateJson = validateJson; }

        public boolean isCreateBackup() { return createBackup; }
        public void setCreateBackup(boolean createBackup) { this.createBackup = createBackup; }
    }

    public static class CreateJsonRequest {
        private String filePath;
        private String initialContent;
        private boolean overwrite = false;

        // Getters and Setters
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getInitialContent() { return initialContent; }
        public void setInitialContent(String initialContent) { this.initialContent = initialContent; }

        public boolean isOverwrite() { return overwrite; }
        public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }
    }
}