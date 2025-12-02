package red.jiuzhou.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 路径验证工具类，防止目录遍历攻击
 * @author yanxq
 * @date 2025-09-17
 * @version V1.0
 */
@Component
public class PathValidator {

    private static final Logger log = LoggerFactory.getLogger(PathValidator.class);

    // 允许的基础路径列表
    private static final List<String> ALLOWED_BASE_PATHS = Arrays.asList(
        System.getProperty("user.dir") + File.separator + "data",
        System.getProperty("user.dir") + File.separator + "src" + File.separator + "main" + File.separator + "resources",
        "D:\\workspace\\dbxmlTool\\data",
        "D:\\workspace\\dbxmlTool\\src\\main\\resources",
        // 游戏数据目录
        "D:\\AionReal58\\AionMap\\XML",
        "D:\\客户端补丁\\L10N\\CHS\\Data\\strings",
        "D:\\客户端补丁\\data\\china\\items"
    );

    /**
     * 验证文件路径是否安全
     * @param userPath 用户提供的路径
     * @return 验证通过的规范化路径
     * @throws SecurityException 如果路径不安全
     */
    public String validatePath(String userPath) throws SecurityException {
        if (!StringUtils.hasLength(userPath)) {
            throw new SecurityException("路径不能为空");
        }

        try {
            // 规范化路径
            Path normalizedPath = Paths.get(userPath).normalize().toAbsolutePath();
            String resolvedPath = normalizedPath.toString();

            // 检查是否在允许的基础路径内
            boolean isAllowed = ALLOWED_BASE_PATHS.stream()
                    .anyMatch(basePath -> {
                        try {
                            Path base = Paths.get(basePath).normalize().toAbsolutePath();
                            return normalizedPath.startsWith(base);
                        } catch (Exception e) {
                            return false;
                        }
                    });

            if (!isAllowed) {
                log.warn("路径访问被拒绝: {}", userPath);
                throw new SecurityException("路径访问被拒绝: 不在允许的目录范围内");
            }

            // 检查路径中是否包含危险字符
            if (containsDangerousPatterns(userPath)) {
                log.warn("路径包含危险模式: {}", userPath);
                throw new SecurityException("路径包含危险字符");
            }

            return resolvedPath;

        } catch (InvalidPathException e) {
            log.warn("无效的路径格式: {}", userPath);
            throw new SecurityException("无效的路径格式: " + e.getMessage());
        }
    }

    /**
     * 检查路径是否包含危险模式
     * @param path 要检查的路径
     * @return 如果包含危险模式返回true
     */
    private boolean containsDangerousPatterns(String path) {
        String[] dangerousPatterns = {
            "..", "//", "\\\\", "%2e%2e", "%2f", "%5c",
            "CON", "PRN", "AUX", "NUL", // Windows保留名
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        };

        String upperPath = path.toUpperCase();
        for (String pattern : dangerousPatterns) {
            if (upperPath.contains(pattern.toUpperCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 验证文件是否存在且可读
     * @param filePath 文件路径
     * @return 验证通过的文件对象
     * @throws SecurityException 如果文件不安全或不存在
     */
    public File validateFile(String filePath) throws SecurityException {
        String validatedPath = validatePath(filePath);
        File file = new File(validatedPath);

        if (!file.exists()) {
            throw new SecurityException("文件不存在: " + filePath);
        }

        if (!file.canRead()) {
            throw new SecurityException("文件不可读: " + filePath);
        }

        return file;
    }

    /**
     * 验证目录是否存在且可访问
     * @param directoryPath 目录路径
     * @return 验证通过的目录对象
     * @throws SecurityException 如果目录不安全或不存在
     */
    public File validateDirectory(String directoryPath) throws SecurityException {
        String validatedPath = validatePath(directoryPath);
        File directory = new File(validatedPath);

        if (!directory.exists()) {
            throw new SecurityException("目录不存在: " + directoryPath);
        }

        if (!directory.isDirectory()) {
            throw new SecurityException("路径不是目录: " + directoryPath);
        }

        if (!directory.canRead()) {
            throw new SecurityException("目录不可访问: " + directoryPath);
        }

        return directory;
    }
}