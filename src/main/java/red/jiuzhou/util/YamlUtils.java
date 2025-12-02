package red.jiuzhou.util;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @className: red.jiuzhou.util.YamlUtils.java
 * @description: YAML 文件操作工具类
 * @author: yanxq
 * @date:  2025-04-15 20:37
 * @version V1.0
 */
public class YamlUtils {

    // 加载 YAML 文件并返回 Properties 对象
    public static Properties loadYamlProperties(String yamlFile) {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ClassPathResource(yamlFile));
        return yamlFactory.getObject();
    }

    public static String getProperty(String key) {
        return getProperty("application.yml", key);
    }

    /**
     * 获取配置值，如果不存在则返回默认值
     */
    public static String getPropertyOrDefault(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    // 根据键获取配置值
    public static String getProperty(String yamlFile, String key) {
        Properties properties = loadYamlProperties(yamlFile);
        return properties.getProperty(key);
    }

    // 根据键获取配置值，如果没有则返回默认值
    public static String getProperty(String yamlFile, String key, String defaultValue) {
        Properties properties = loadYamlProperties(yamlFile);
        return properties.getProperty(key, defaultValue);
    }

    public static List<String> loadAiModelKeys(String yamlFileName) {
        Yaml yaml = new Yaml();
        try (InputStream in = YamlUtils.class.getClassLoader().getResourceAsStream(yamlFileName)) {
            if (in == null) {
                System.err.println("YAML file not found: " + yamlFileName);
                return Collections.emptyList();
            }

            Map<String, Object> root = yaml.load(in);
            if (!(root instanceof Map)) return Collections.emptyList();

            Object aiObj = root.get("ai");
            if (!(aiObj instanceof Map)) return Collections.emptyList();

            Map<?, ?> aiMap = (Map<?, ?>) aiObj;

            // 过滤：值是 Map 并且包含 apikey 字段的才认为是模型配置
            return aiMap.entrySet()
                    .stream()
                    .filter(e -> e.getValue() instanceof Map)
                    .filter(e -> ((Map<?, ?>) e.getValue()).containsKey("apikey"))
                    .map(e -> e.getKey().toString())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error loading YAML file: " + yamlFileName);
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public static List<String> loadPromptListFromYaml(String compositeKey) {
        String yamlFileName = "application.yml";
        Yaml yaml = new Yaml();
        try (InputStream in = YamlUtils.class.getClassLoader().getResourceAsStream(yamlFileName)) {
            if (in == null) return Collections.emptyList();

            Map<String, Object> root = yaml.load(in);
            if (root == null) return Collections.emptyList();

            // 按点分割key，比如 "promptKey.common" -> ["promptKey","common"]
            String[] keys = compositeKey.split("\\.");

            Object current = root;
            for (String key : keys) {
                if (!(current instanceof Map)) return Collections.emptyList();
                current = ((Map<?, ?>) current).get(key);
                if (current == null) return Collections.emptyList();
            }

            if (!(current instanceof List)) return Collections.emptyList();

            List<?> list = (List<?>) current;
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }


}
