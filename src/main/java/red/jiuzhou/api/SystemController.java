package red.jiuzhou.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import red.jiuzhou.api.common.CommonResult;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @className: red.jiuzhou.api.SystemController
 * @description: 系统信息API控制器，提供系统状态和配置信息
 * @author: yanxq
 * @date: 2025-09-17
 * @version V1.0
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    /**
     * 获取系统基本信息
     * @return 系统信息
     */
    @GetMapping("/info")
    public CommonResult<Map<String, Object>> getSystemInfo() {
        try {
            Map<String, Object> result = new HashMap<>();

            // JVM信息
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            Map<String, Object> jvmInfo = new HashMap<>();
            jvmInfo.put("javaVersion", System.getProperty("java.version"));
            jvmInfo.put("javaVendor", System.getProperty("java.vendor"));
            jvmInfo.put("javaHome", System.getProperty("java.home"));
            jvmInfo.put("vmName", runtimeBean.getVmName());
            jvmInfo.put("vmVersion", runtimeBean.getVmVersion());
            jvmInfo.put("vmVendor", runtimeBean.getVmVendor());
            jvmInfo.put("uptime", runtimeBean.getUptime());
            jvmInfo.put("startTime", runtimeBean.getStartTime());

            // 内存信息
            Map<String, Object> memoryInfo = new HashMap<>();
            memoryInfo.put("heapMemoryUsed", memoryBean.getHeapMemoryUsage().getUsed());
            memoryInfo.put("heapMemoryMax", memoryBean.getHeapMemoryUsage().getMax());
            memoryInfo.put("heapMemoryCommitted", memoryBean.getHeapMemoryUsage().getCommitted());
            memoryInfo.put("nonHeapMemoryUsed", memoryBean.getNonHeapMemoryUsage().getUsed());
            memoryInfo.put("nonHeapMemoryMax", memoryBean.getNonHeapMemoryUsage().getMax());

            // 操作系统信息
            Map<String, Object> osInfo = new HashMap<>();
            osInfo.put("osName", System.getProperty("os.name"));
            osInfo.put("osVersion", System.getProperty("os.version"));
            osInfo.put("osArch", System.getProperty("os.arch"));
            osInfo.put("userName", System.getProperty("user.name"));
            osInfo.put("userHome", System.getProperty("user.home"));
            osInfo.put("userDir", System.getProperty("user.dir"));

            // 应用信息
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("applicationName", "DbXmlTool");
            appInfo.put("version", "1.0-SNAPSHOT");
            appInfo.put("databaseName", DatabaseUtil.getDbName());
            appInfo.put("homePath", YamlUtils.getProperty("file.homePath"));
            appInfo.put("confPath", YamlUtils.getProperty("file.confPath"));

            result.put("jvm", jvmInfo);
            result.put("memory", memoryInfo);
            result.put("os", osInfo);
            result.put("application", appInfo);

            return success(result);
        } catch (Exception e) {
            log.error("获取系统信息失败", e);
            return CommonResult.error(1, "获取系统信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取应用配置信息
     * @return 配置信息
     */
    @GetMapping("/config")
    public CommonResult<Map<String, Object>> getSystemConfig() {
        try {
            Map<String, Object> result = new HashMap<>();

            // 获取所有系统属性
            Properties systemProps = System.getProperties();
            Map<String, Object> systemProperties = new HashMap<>();
            for (Object key : systemProps.keySet()) {
                systemProperties.put(key.toString(), systemProps.get(key));
            }

            // 获取应用配置
            Map<String, Object> appConfig = new HashMap<>();
            appConfig.put("databaseName", DatabaseUtil.getDbName());
            appConfig.put("homePath", YamlUtils.getProperty("file.homePath"));
            appConfig.put("confPath", YamlUtils.getProperty("file.confPath"));

            // 尝试获取其他配置项
            String[] configKeys = {
                "xmlPath." + DatabaseUtil.getDbName(),
                "server.port",
                "spring.datasource.url",
                "spring.datasource.username"
            };

            for (String key : configKeys) {
                String value = YamlUtils.getProperty(key);
                if (value != null) {
                    appConfig.put(key, value);
                }
            }

            result.put("systemProperties", systemProperties);
            result.put("applicationConfig", appConfig);

            return success(result);
        } catch (Exception e) {
            log.error("获取系统配置失败", e);
            return CommonResult.error(1, "获取系统配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据库连接状态
     * @return 数据库状态
     */
    @GetMapping("/database-status")
    public CommonResult<Map<String, Object>> getDatabaseStatus() {
        try {
            Map<String, Object> result = new HashMap<>();

            try {
                // 测试数据库连接
                String testSql = "SELECT 1";
                DatabaseUtil.getJdbcTemplate().queryForObject(testSql, Integer.class);

                result.put("connected", true);
                result.put("databaseName", DatabaseUtil.getDbName());
                result.put("message", "数据库连接正常");

                // 获取数据库版本信息
                try {
                    String versionSql = "SELECT VERSION() as version";
                    String version = DatabaseUtil.getJdbcTemplate().queryForObject(versionSql, String.class);
                    result.put("version", version);
                } catch (Exception e) {
                    log.warn("获取数据库版本失败", e);
                }

            } catch (Exception e) {
                result.put("connected", false);
                result.put("error", e.getMessage());
                result.put("message", "数据库连接失败");
            }

            return success(result);
        } catch (Exception e) {
            log.error("获取数据库状态失败", e);
            return CommonResult.error(1, "获取数据库状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取磁盘空间信息
     * @return 磁盘空间信息
     */
    @GetMapping("/disk-space")
    public CommonResult<Map<String, Object>> getDiskSpace() {
        try {
            Map<String, Object> result = new HashMap<>();

            // 获取应用目录的磁盘空间
            String homePath = YamlUtils.getProperty("file.homePath");
            File homeDir = new File(homePath != null ? homePath : System.getProperty("user.dir"));

            Map<String, Object> diskInfo = new HashMap<>();
            diskInfo.put("path", homeDir.getAbsolutePath());
            diskInfo.put("totalSpace", homeDir.getTotalSpace());
            diskInfo.put("freeSpace", homeDir.getFreeSpace());
            diskInfo.put("usableSpace", homeDir.getUsableSpace());
            diskInfo.put("usedSpace", homeDir.getTotalSpace() - homeDir.getFreeSpace());

            // 计算使用率
            double usageRatio = (double) (homeDir.getTotalSpace() - homeDir.getFreeSpace()) / homeDir.getTotalSpace();
            diskInfo.put("usageRatio", String.format("%.2f%%", usageRatio * 100));

            result.put("disk", diskInfo);

            return success(result);
        } catch (Exception e) {
            log.error("获取磁盘空间信息失败", e);
            return CommonResult.error(1, "获取磁盘空间信息失败: " + e.getMessage());
        }
    }

    /**
     * 执行垃圾回收
     * @return 垃圾回收结果
     */
    @PostMapping("/gc")
    public CommonResult<Map<String, Object>> performGarbageCollection() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            // 记录GC前的内存使用情况
            long beforeUsed = memoryBean.getHeapMemoryUsage().getUsed();

            // 执行垃圾回收
            System.gc();

            // 等待一下让GC完成
            Thread.sleep(100);

            // 记录GC后的内存使用情况
            long afterUsed = memoryBean.getHeapMemoryUsage().getUsed();

            Map<String, Object> result = new HashMap<>();
            result.put("beforeGC", beforeUsed);
            result.put("afterGC", afterUsed);
            result.put("freed", beforeUsed - afterUsed);
            result.put("message", "垃圾回收完成");

            return success(result);
        } catch (Exception e) {
            log.error("执行垃圾回收失败", e);
            return CommonResult.error(1, "执行垃圾回收失败: " + e.getMessage());
        }
    }

    /**
     * 获取应用运行时统计信息
     * @return 运行时统计
     */
    @GetMapping("/runtime-stats")
    public CommonResult<Map<String, Object>> getRuntimeStats() {
        try {
            Map<String, Object> result = new HashMap<>();

            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            Map<String, Object> stats = new HashMap<>();
            stats.put("uptime", runtimeBean.getUptime());
            stats.put("startTime", runtimeBean.getStartTime());
            stats.put("currentTime", System.currentTimeMillis());

            // 内存统计
            stats.put("heapMemoryUsed", memoryBean.getHeapMemoryUsage().getUsed());
            stats.put("heapMemoryMax", memoryBean.getHeapMemoryUsage().getMax());
            stats.put("heapMemoryCommitted", memoryBean.getHeapMemoryUsage().getCommitted());

            // 计算内存使用率
            double heapUsageRatio = (double) memoryBean.getHeapMemoryUsage().getUsed() / memoryBean.getHeapMemoryUsage().getMax();
            stats.put("heapUsageRatio", String.format("%.2f%%", heapUsageRatio * 100));

            // 线程信息
            stats.put("activeThreadCount", Thread.activeCount());

            result.put("statistics", stats);

            return success(result);
        } catch (Exception e) {
            log.error("获取运行时统计失败", e);
            return CommonResult.error(1, "获取运行时统计失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     * @return 健康状态
     */
    @GetMapping("/health")
    public CommonResult<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> result = new HashMap<>();
            boolean healthy = true;
            StringBuilder issues = new StringBuilder();

            // 检查数据库连接
            try {
                DatabaseUtil.getJdbcTemplate().queryForObject("SELECT 1", Integer.class);
                result.put("database", "UP");
            } catch (Exception e) {
                result.put("database", "DOWN");
                healthy = false;
                issues.append("数据库连接失败; ");
            }

            // 检查内存使用率
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            double heapUsageRatio = (double) memoryBean.getHeapMemoryUsage().getUsed() / memoryBean.getHeapMemoryUsage().getMax();
            if (heapUsageRatio > 0.9) {
                result.put("memory", "WARN");
                issues.append("内存使用率过高: " + String.format("%.2f%%", heapUsageRatio * 100) + "; ");
            } else {
                result.put("memory", "UP");
            }

            // 检查磁盘空间
            String homePath = YamlUtils.getProperty("file.homePath");
            File homeDir = new File(homePath != null ? homePath : System.getProperty("user.dir"));
            double diskUsageRatio = (double) (homeDir.getTotalSpace() - homeDir.getFreeSpace()) / homeDir.getTotalSpace();
            if (diskUsageRatio > 0.9) {
                result.put("disk", "WARN");
                issues.append("磁盘空间不足: " + String.format("%.2f%%", diskUsageRatio * 100) + "; ");
            } else {
                result.put("disk", "UP");
            }

            result.put("status", healthy ? "UP" : "DOWN");
            result.put("message", issues.length() > 0 ? issues.toString() : "系统运行正常");
            result.put("timestamp", System.currentTimeMillis());

            return success(result);
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return CommonResult.error(1, "健康检查失败: " + e.getMessage());
        }
    }
}