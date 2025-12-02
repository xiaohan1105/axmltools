package red.jiuzhou.ui;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 智能启动器
 * 自动检测 Java 版本并选择合适的实现
 */
public class Launcher {

    private static final Logger log = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {
        try {
            // 检测 Java 版本
            String javaVersion = System.getProperty("java.version");
            log.info("检测到 Java 版本: {}", javaVersion);

            // 判断是否使用 Java 8 兼容版本
            if (isJava8OrCompatible(javaVersion)) {
                log.info("使用 Java 8 兼容版实现");
                System.setProperty("dbxmltool.compatibility.mode", "java8");
                CompatibleDbxmltool.main(args);
            } else {
                log.info("使用增强版实现");
                System.setProperty("dbxmltool.compatibility.mode", "enhanced");
                RobustDbxmltool.main(args);
            }
        } catch (Exception e) {
            log.error("启动失败", e);

            // 如果启动失败，尝试使用兼容版本
            log.info("尝试使用 Java 8 兼容版本...");
            try {
                CompatibleDbxmltool.main(args);
            } catch (Exception ex) {
                log.error("兼容版本也启动失败", ex);

                // 最后尝试原始版本
                log.info("尝试使用原始版本...");
                try {
                    Dbxmltool.main(args);
                } catch (Exception exc) {
                    log.error("所有版本都启动失败", exc);
                    System.err.println("无法启动应用程序。请检查 Java 版本和依赖项。");
                    System.exit(1);
                }
            }
        }
    }

    /**
     * 检查是否为 Java 8 或兼容版本
     */
    private static boolean isJava8OrCompatible(String version) {
        try {
            // 处理版本字符串
            if (version.startsWith("1.8")) {
                return true; // Java 8
            } else if (version.startsWith("8.")) {
                return true; // Java 8 (新版本号格式)
            } else if (version.startsWith("9.") ||
                       version.startsWith("10.") ||
                       version.startsWith("11.")) {
                return false; // 使用增强版本
            } else {
                // 默认使用兼容版本，确保最大兼容性
                log.warn("未知 Java 版本: {}，使用兼容版本", version);
                return true;
            }
        } catch (Exception e) {
            log.warn("解析 Java 版本失败，使用兼容版本", e);
            return true;
        }
    }
}