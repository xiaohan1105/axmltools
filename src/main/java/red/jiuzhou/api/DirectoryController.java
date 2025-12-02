package red.jiuzhou.api;

import cn.hutool.core.io.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import red.jiuzhou.api.common.CommonResult;
import red.jiuzhou.util.IncrementalMenuJsonGenerator;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static red.jiuzhou.api.common.CommonResult.error;
import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @className: red.jiuzhou.api.DirectoryController
 * @description: 目录管理API控制器，提供目录操作和管理功能
 * @author: yanxq
 * @date: 2025-09-17
 * @version V1.0
 */
@RestController
@RequestMapping("/api/directory")
public class DirectoryController {

    private static final Logger log = LoggerFactory.getLogger(DirectoryController.class);

    /**
     * 获取指定路径下的目录和文件列表
     * @param path 目录路径（可选，默认为配置的主目录）
     * @param includeFiles 是否包含文件（默认只返回目录）
     * @param recursive 是否递归获取子目录
     * @return 目录和文件列表
     */
    @GetMapping("/list")
    public CommonResult<Map<String, Object>> listDirectory(
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "false") boolean includeFiles,
            @RequestParam(defaultValue = "false") boolean recursive) {
        try {
            if (path == null || path.isEmpty()) {
                path = YamlUtils.getProperty("file.homePath");
            }

            File directory = new File(path);
            if (!directory.exists()) {
                return error(1, "目录不存在: " + path);
            }

            if (!directory.isDirectory()) {
                return error(1, "指定路径不是目录: " + path);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("currentPath", path);
            result.put("parentPath", directory.getParent());

            if (recursive) {
                result.put("items", getDirectoryTreeRecursive(directory, includeFiles));
            } else {
                result.put("items", getDirectoryItems(directory, includeFiles));
            }

            return success(result);
        } catch (Exception e) {
            log.error("获取目录列表失败: " + path, e);
            return error(1, "获取目录列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建目录
     * @param path 目录路径
     * @param recursive 是否递归创建父目录
     * @return 创建结果
     */
    @PostMapping("/create")
    public CommonResult<Map<String, Object>> createDirectory(
            @RequestParam String path,
            @RequestParam(defaultValue = "true") boolean recursive) {
        try {
            File directory = new File(path);

            if (directory.exists()) {
                return error(1, "目录已存在: " + path);
            }

            boolean success = recursive ? directory.mkdirs() : directory.mkdir();

            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("path", path);
                result.put("created", true);
                result.put("message", "目录创建成功");
                return success(result);
            } else {
                return error(1, "目录创建失败: " + path);
            }
        } catch (Exception e) {
            log.error("创建目录失败: " + path, e);
            return error(1, "创建目录失败: " + e.getMessage());
        }
    }

    /**
     * 删除目录
     * @param path 目录路径
     * @param recursive 是否递归删除（删除非空目录）
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    public CommonResult<Map<String, Object>> deleteDirectory(
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean recursive) {
        try {
            File directory = new File(path);

            if (!directory.exists()) {
                return error(1, "目录不存在: " + path);
            }

            if (!directory.isDirectory()) {
                return error(1, "指定路径不是目录: " + path);
            }

            boolean success;
            if (recursive) {
                success = FileUtil.del(directory);
            } else {
                success = directory.delete();
            }

            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("path", path);
                result.put("deleted", true);
                result.put("message", "目录删除成功");
                return success(result);
            } else {
                return error(1, "目录删除失败，可能目录不为空");
            }
        } catch (Exception e) {
            log.error("删除目录失败: " + path, e);
            return error(1, "删除目录失败: " + e.getMessage());
        }
    }

    /**
     * 重命名目录
     * @param oldPath 原目录路径
     * @param newPath 新目录路径
     * @return 重命名结果
     */
    @PutMapping("/rename")
    public CommonResult<Map<String, Object>> renameDirectory(
            @RequestParam String oldPath,
            @RequestParam String newPath) {
        try {
            File oldDirectory = new File(oldPath);
            File newDirectory = new File(newPath);

            if (!oldDirectory.exists()) {
                return error(1, "原目录不存在: " + oldPath);
            }

            if (!oldDirectory.isDirectory()) {
                return error(1, "指定路径不是目录: " + oldPath);
            }

            if (newDirectory.exists()) {
                return error(1, "目标路径已存在: " + newPath);
            }

            boolean success = oldDirectory.renameTo(newDirectory);

            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("oldPath", oldPath);
                result.put("newPath", newPath);
                result.put("renamed", true);
                result.put("message", "目录重命名成功");
                return success(result);
            } else {
                return error(1, "目录重命名失败");
            }
        } catch (Exception e) {
            log.error("重命名目录失败: " + oldPath + " -> " + newPath, e);
            return error(1, "重命名目录失败: " + e.getMessage());
        }
    }

    /**
     * 获取目录信息
     * @param path 目录路径
     * @return 目录信息
     */
    @GetMapping("/info")
    public CommonResult<Map<String, Object>> getDirectoryInfo(@RequestParam String path) {
        try {
            File directory = new File(path);

            if (!directory.exists()) {
                return error(1, "目录不存在: " + path);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("path", path);
            result.put("absolutePath", directory.getAbsolutePath());
            result.put("name", directory.getName());
            result.put("parent", directory.getParent());
            result.put("isDirectory", directory.isDirectory());
            result.put("isFile", directory.isFile());
            result.put("lastModified", directory.lastModified());
            result.put("canRead", directory.canRead());
            result.put("canWrite", directory.canWrite());
            result.put("canExecute", directory.canExecute());

            if (directory.isDirectory()) {
                File[] children = directory.listFiles();
                if (children != null) {
                    long dirCount = Arrays.stream(children).filter(File::isDirectory).count();
                    long fileCount = Arrays.stream(children).filter(File::isFile).count();
                    result.put("directoryCount", dirCount);
                    result.put("fileCount", fileCount);
                    result.put("totalItems", children.length);
                }
            }

            return success(result);
        } catch (Exception e) {
            log.error("获取目录信息失败: " + path, e);
            return error(1, "获取目录信息失败: " + e.getMessage());
        }
    }

    /**
     * 重新生成左侧菜单JSON
     * @return 操作结果
     */
    @PostMapping("/regenerate-menu")
    public CommonResult<Map<String, Object>> regenerateMenu() {
        try {
            IncrementalMenuJsonGenerator.createJsonIncrementally();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "左侧菜单重新生成成功");

            // 返回生成的菜单内容
            String menuJsonPath = YamlUtils.getProperty("file.homePath") + File.separator + "leftMenu.json";
            if (FileUtil.exist(menuJsonPath)) {
                String menuContent = FileUtil.readUtf8String(menuJsonPath);
                result.put("menuContent", menuContent);
            }

            return success(result);
        } catch (Exception e) {
            log.error("重新生成菜单失败", e);
            return error(1, "重新生成菜单失败: " + e.getMessage());
        }
    }

    /**
     * 获取左侧菜单内容
     * @return 菜单JSON内容
     */
    @GetMapping("/menu")
    public CommonResult<Map<String, Object>> getMenu() {
        try {
            String menuJsonPath = YamlUtils.getProperty("file.homePath") + File.separator + "leftMenu.json";

            if (!FileUtil.exist(menuJsonPath)) {
                return error(1, "菜单文件不存在: " + menuJsonPath);
            }

            String menuContent = FileUtil.readUtf8String(menuJsonPath);
            Map<String, Object> result = new HashMap<>();
            result.put("menuJsonPath", menuJsonPath);
            result.put("menuContent", menuContent);

            return success(result);
        } catch (Exception e) {
            log.error("获取菜单内容失败", e);
            return error(1, "获取菜单内容失败: " + e.getMessage());
        }
    }

    /**
     * 搜索目录和文件
     * @param basePath 搜索基础路径
     * @param pattern 搜索模式（支持通配符）
     * @param includeFiles 是否包含文件
     * @param caseSensitive 是否区分大小写
     * @return 搜索结果
     */
    @GetMapping("/search")
    public CommonResult<List<Map<String, Object>>> searchDirectories(
            @RequestParam(required = false) String basePath,
            @RequestParam String pattern,
            @RequestParam(defaultValue = "true") boolean includeFiles,
            @RequestParam(defaultValue = "false") boolean caseSensitive) {
        try {
            if (basePath == null || basePath.isEmpty()) {
                basePath = YamlUtils.getProperty("file.homePath");
            }

            File baseDir = new File(basePath);
            if (!baseDir.exists() || !baseDir.isDirectory()) {
                return error(1, "基础目录不存在或不是目录: " + basePath);
            }

            List<Map<String, Object>> results = new ArrayList<>();
            searchRecursive(baseDir, pattern, includeFiles, caseSensitive, results);

            return success(results);
        } catch (Exception e) {
            log.error("搜索目录失败", e);
            return error(1, "搜索目录失败: " + e.getMessage());
        }
    }

    // 私有辅助方法
    private List<Map<String, Object>> getDirectoryItems(File directory, boolean includeFiles) {
        List<Map<String, Object>> items = new ArrayList<>();
        File[] children = directory.listFiles();

        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() || (includeFiles && child.isFile())) {
                    Map<String, Object> item = createFileItem(child);
                    items.add(item);
                }
            }
        }

        return items.stream()
                .sorted((a, b) -> {
                    boolean aIsDir = (Boolean) a.get("isDirectory");
                    boolean bIsDir = (Boolean) b.get("isDirectory");
                    if (aIsDir != bIsDir) {
                        return bIsDir ? 1 : -1; // 目录排在前面
                    }
                    return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> getDirectoryTreeRecursive(File directory, boolean includeFiles) {
        List<Map<String, Object>> items = new ArrayList<>();
        File[] children = directory.listFiles();

        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    Map<String, Object> item = createFileItem(child);
                    item.put("children", getDirectoryTreeRecursive(child, includeFiles));
                    items.add(item);
                } else if (includeFiles && child.isFile()) {
                    Map<String, Object> item = createFileItem(child);
                    items.add(item);
                }
            }
        }

        return items.stream()
                .sorted((a, b) -> {
                    boolean aIsDir = (Boolean) a.get("isDirectory");
                    boolean bIsDir = (Boolean) b.get("isDirectory");
                    if (aIsDir != bIsDir) {
                        return bIsDir ? 1 : -1;
                    }
                    return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> createFileItem(File file) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", file.getName());
        item.put("path", file.getAbsolutePath());
        item.put("isDirectory", file.isDirectory());
        item.put("isFile", file.isFile());
        item.put("lastModified", file.lastModified());
        item.put("size", file.length());
        item.put("canRead", file.canRead());
        item.put("canWrite", file.canWrite());
        return item;
    }

    private void searchRecursive(File directory, String pattern, boolean includeFiles,
                                boolean caseSensitive, List<Map<String, Object>> results) {
        File[] children = directory.listFiles();
        if (children == null) return;

        for (File child : children) {
            String name = child.getName();
            String searchName = caseSensitive ? name : name.toLowerCase();
            String searchPattern = caseSensitive ? pattern : pattern.toLowerCase();

            // 简单的通配符匹配
            if (matchesPattern(searchName, searchPattern)) {
                if (child.isDirectory() || (includeFiles && child.isFile())) {
                    results.add(createFileItem(child));
                }
            }

            if (child.isDirectory()) {
                searchRecursive(child, pattern, includeFiles, caseSensitive, results);
            }
        }
    }

    private boolean matchesPattern(String name, String pattern) {
        // 简单的通配符匹配，支持 * 和 ?
        pattern = pattern.replace("*", ".*").replace("?", ".");
        return name.matches(pattern);
    }
}