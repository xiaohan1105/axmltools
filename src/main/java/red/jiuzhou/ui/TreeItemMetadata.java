package red.jiuzhou.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TreeItem 元数据存储器
 * 用于存储 TreeItem 的额外数据，避免直接使用 UserData
 */
public class TreeItemMetadata {

    // 使用静态 Map 来存储所有 TreeItem 的元数据
    private static final Map<String, Object> metadataStore = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> propertiesStore = new ConcurrentHashMap<>();

    // 生成唯一键
    private static String generateKey(Object treeItem) {
        return "treeItem_" + System.identityHashCode(treeItem);
    }

    /**
     * 设置元数据
     */
    public static void setMetadata(Object treeItem, Object metadata) {
        String key = generateKey(treeItem);
        metadataStore.put(key, metadata);
    }

    /**
     * 获取元数据
     */
    public static Object getMetadata(Object treeItem) {
        String key = generateKey(treeItem);
        return metadataStore.get(key);
    }

    /**
     * 设置属性
     */
    public static void setProperty(Object treeItem, String propertyKey, Object value) {
        String key = generateKey(treeItem);
        propertiesStore.computeIfAbsent(key, k -> new HashMap<>()).put(propertyKey, value);
    }

    /**
     * 获取属性
     */
    public static Object getProperty(Object treeItem, String propertyKey) {
        String key = generateKey(treeItem);
        Map<String, Object> props = propertiesStore.get(key);
        return props != null ? props.get(propertyKey) : null;
    }

    /**
     * 移除元数据
     */
    public static void removeMetadata(Object treeItem) {
        String key = generateKey(treeItem);
        metadataStore.remove(key);
        propertiesStore.remove(key);
    }
}