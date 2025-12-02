package red.jiuzhou.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class PathUtil {
    public static String getConfPath(String fullPath) {
        String os = System.getProperty("os.name").toLowerCase();
        String joinedPath;
        String confPath = YamlUtils.getProperty("file.confPath");
        if(confPath.endsWith(File.separator)){
            confPath = confPath.substring(0, confPath.length() - 1);
        }
        //log.info("confPath:::{}", confPath);
        //log.info("fullPath:::{}", fullPath);
        if (os.startsWith("windows")) {
            // Windows：保留盘符 D:
            String driveLetter = fullPath.substring(0, 1);
            // 去掉冒号
            String pathWithoutDrive = fullPath.substring(2);
            joinedPath = confPath + File.separator + driveLetter + pathWithoutDrive;
        } else {
            // Linux / Mac：直接拼接
            // 如果 fullPath 是绝对路径，去掉开头的 /
            String relativePath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
            joinedPath = confPath + File.separator + relativePath;
        }
        //log.info("joinedPath:::{}", joinedPath);
        return joinedPath;
    }

    public static String restoreOriginalPath(String fullPath) {
        String confPath = YamlUtils.getProperty("file.confPath");
        if (confPath.endsWith(File.separator)) {
            confPath = confPath.substring(0, confPath.length() - 1);
        }

        if (!fullPath.startsWith(confPath)) {
            log.warn("路径 [{}] 不包含配置路径 [{}]", fullPath, confPath);
            return fullPath;
        }

        // 去掉 confPath 部分
        String relativePath = fullPath.substring(confPath.length());
        if (relativePath.startsWith(File.separator)) {
            relativePath = relativePath.substring(1);
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.startsWith("windows")) {
            // Windows 还原 D:/xxx/xxx
            if (relativePath.length() >= 2 && relativePath.charAt(1) == File.separatorChar) {
                String driveLetter = relativePath.substring(0, 1);
                String restPath = relativePath.substring(2);
                return driveLetter + ":" + File.separator + restPath;
            } else {
                log.warn("无法识别盘符格式的路径：{}", relativePath);
                return relativePath;
            }
        } else {
            // Linux/macOS 还原 /xxx/xxx
            return File.separator + relativePath;
        }
    }

    public static void main(String[] args) {
        String fullPath = "/Users/dream/workspace/dbxmlTool/src/main/resources/CONF/D/dream/workspace/";
        System.out.println(restoreOriginalPath(fullPath));
    }
}
