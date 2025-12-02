package red.jiuzhou.util;

import cn.hutool.core.io.FileUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.Map;
/**
 * @className: red.jiuzhou.util.YmlConfigUtil.java
 * @description: YmlConfigUtil
 * @author: yanxq
 * @date:  2025-04-15 20:37
 * @version V1.0
 */
public class YmlConfigUtil {

    private static final Yaml yaml;

    static {
        // 配置YAML输出的格式
        DumperOptions options = new DumperOptions();
        // 设置缩进为2空格
        options.setIndent(2);
        // 设置为块状风格
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yaml = new Yaml(options);
    }

    /**
     * 读取指定YML文件并加载为Map对象
     *
     * @param filePath YML文件路径
     * @return 返回YML文件中的配置数据
     */
    public static Map<String, Object> loadYml(String filePath) {
        String ymlContent = FileUtil.readUtf8String(filePath);
        return yaml.load(ymlContent);
    }

    /**
     * 更新YML文件中的指定配置
     *
     * @param filePath YML文件路径
     * @param key      配置的键
     * @param value    配置的值
     * @return 是否更新成功
     */
    public static boolean updateYmlConfig(String filePath, String key, Object value) {
        // 加载YML文件
        Map<String, Object> ymlData = loadYml(filePath);

        // 递归查找配置项并修改
        setNestedMapValue(ymlData, key.split("\\."), value);

        // 将修改后的数据写回YML文件
        String updatedYmlContent = yaml.dump(ymlData);
        FileUtil.writeUtf8String(updatedYmlContent, filePath);
        return true;
    }

    /**
     * 递归修改嵌套Map中的值
     *
     * @param map   当前的Map对象
     * @param keys  键的数组
     * @param value 新的值
     */
    private static void setNestedMapValue(Map<String, Object> map, String[] keys, Object value) {
        Map<String, Object> currentMap = map;
        for (int i = 0; i < keys.length - 1; i++) {
            currentMap = (Map<String, Object>) currentMap.get(keys[i]);
        }
        currentMap.put(keys[keys.length - 1], value);
    }

    /**
     * 获取YML文件中指定配置项的值
     *
     * @param filePath YML文件路径
     * @param key      配置的键
     * @return 配置的值
     */
    public static Object getYmlConfigValue(String filePath, String key) {
        Map<String, Object> ymlData = loadYml(filePath);
        String[] keys = key.split("\\.");
        return getNestedMapValue(ymlData, keys);
    }

    public static Object getYmlConfigValue(String key) {
        String resourcesFilePath = System.getProperty("user.dir") + File.separator + "src"+ File.separator +
                "main"+ File.separator + "resources" + File.separator  + "application.yml";
        return getYmlConfigValue(resourcesFilePath, key);
    }

    /**
     * 递归获取嵌套Map中的值
     *
     * @param map   当前的Map对象
     * @param keys  键的数组
     * @return 配置的值
     */
    private static Object getNestedMapValue(Map<String, Object> map, String[] keys) {
        Map<String, Object> currentMap = map;
        for (int i = 0; i < keys.length - 1; i++) {
            currentMap = (Map<String, Object>) currentMap.get(keys[i]);
        }
        return currentMap.get(keys[keys.length - 1]);
    }

    /**
     * 更新 `resources` 目录下的配置
     *
     * @param filePath YML文件路径（通常是 src/main/resources/application.yml）
     * @param key      配置的键
     * @param value    配置的值
     * @return 是否更新成功
     */
    public static boolean updateResourcesYml(String filePath, String key, Object value) {
        // 直接修改 resources 目录下的 YML 配置
        return updateYmlConfig(filePath, key, value);
    }

    /**
     * 更新 `target/classes` 目录下的配置（仅运行时）
     *
     * @param filePath YML文件路径（通常是 target/classes/application.yml）
     * @param key      配置的键
     * @param value    配置的值
     * @return 是否更新成功
     */
    public static boolean updateTargetYml(String filePath, String key, Object value) {
        // 直接修改 target/classes 目录下的 YML 配置
        return updateYmlConfig(filePath, key, value);
    }

    public static void updateResourcesYml(String key, String value) {
        // 1. 更新 resources 下的配置
        String resourcesFilePath = System.getProperty("user.dir") + File.separator + "src"+ File.separator +
                "main"+ File.separator + "resources" + File.separator  + "application.yml";
        boolean updatedResources = updateResourcesYml(resourcesFilePath, key, value);
        System.out.println("更新 resources 中的配置： " + updatedResources);

        // 2. 更新 target/classes 下的配置
        String targetFilePath = "application.yml";
        boolean updatedTarget = updateTargetYml(targetFilePath, key, value);
        System.out.println("更新 target 中的配置： " + updatedTarget);
    }
}
