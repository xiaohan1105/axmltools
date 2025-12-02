package red.jiuzhou.theme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.AIAssistant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI转换服务
 *
 * 提供统一的AI转换接口，支持缓存和错误处理
 *
 * @author Claude
 * @version 1.0
 */
public class AITransformService {

    private static final Logger log = LoggerFactory.getLogger(AITransformService.class);

    private static AITransformService instance;
    private final AIAssistant aiAssistant;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private boolean cacheEnabled = true;
    private int maxCacheSize = 10000;

    private AITransformService(AIAssistant aiAssistant) {
        this.aiAssistant = aiAssistant;
    }

    /**
     * 初始化服务（应用启动时调用）
     * 必须在使用AI转换功能前调用此方法进行初始化
     *
     * @param aiAssistant AI助手实例
     */
    public static synchronized void initialize(AIAssistant aiAssistant) {
        if (instance == null) {
            instance = new AITransformService(aiAssistant);
            log.info("AITransformService 已初始化");
        }
    }

    /**
     * 获取服务实例
     * 单例模式,返回全局唯一的服务实例
     *
     * @return AITransformService实例,如果未初始化则返回null
     */
    public static AITransformService getInstance() {
        if (instance == null) {
            log.warn("AITransformService 未初始化，AI转换功能将不可用");
        }
        return instance;
    }

    /**
     * 转换文本
     *
     * @param text 原文本
     * @param prompt AI提示词
     * @param model 模型名称
     * @return 转换后的文本，如果失败返回原文本
     */
    public String transform(String text, String prompt, String model) {
        if (aiAssistant == null) {
            log.warn("AIAssistant 未注入，返回原文本");
            return text;
        }

        if (text == null || text.isEmpty()) {
            return text;
        }

        // 检查缓存
        String cacheKey = generateCacheKey(text, prompt, model);
        if (cacheEnabled) {
            String cached = cache.get(cacheKey);
            if (cached != null) {
                log.debug("缓存命中: {}", cacheKey);
                return cached;
            }
        }

        try {
            log.debug("AI转换: {} -> {}", text, model);

            // 调用AI服务
            String result = aiAssistant.transform(text, prompt, model);

            if (result != null && !result.isEmpty()) {
                // 缓存结果
                if (cacheEnabled && cache.size() < maxCacheSize) {
                    cache.put(cacheKey, result);
                }
                return result;
            } else {
                log.warn("AI返回空结果，使用原文本");
                return text;
            }

        } catch (Exception e) {
            log.error("AI转换失败: {}", e.getMessage());
            return text;  // 失败时返回原文本
        }
    }

    /**
     * 批量转换（优化性能）
     * 批量处理多个文本,自动利用缓存提升性能
     *
     * @param texts 文本列表
     * @param prompt AI提示词
     * @param model 模型名称
     * @return 转换结果映射表(原文 -> 转换结果)
     */
    public Map<String, String> batchTransform(Iterable<String> texts, String prompt, String model) {
        Map<String, String> results = new ConcurrentHashMap<>();

        for (String text : texts) {
            String result = transform(text, prompt, model);
            results.put(text, result);
        }

        return results;
    }

    /**
     * 清空缓存
     * 清除所有已缓存的转换结果
     */
    public void clearCache() {
        cache.clear();
        log.info("缓存已清空");
    }

    /**
     * 获取缓存统计
     * 返回缓存的大小、使用率等信息
     *
     * @return 缓存统计对象
     */
    public CacheStats getCacheStats() {
        return new CacheStats(cache.size(), maxCacheSize, cacheEnabled);
    }

    /**
     * 设置缓存启用状态
     * 如果禁用缓存,会自动清空现有缓存
     *
     * @param enabled true启用缓存,false禁用缓存
     */
    public void setCacheEnabled(boolean enabled) {
        this.cacheEnabled = enabled;
        if (!enabled) {
            clearCache();
        }
    }

    /**
     * 设置最大缓存大小
     * 如果新大小小于当前缓存条目数,会自动清空缓存
     *
     * @param size 最大缓存条目数
     */
    public void setMaxCacheSize(int size) {
        this.maxCacheSize = size;
        // 如果当前缓存超过新大小，清空
        if (cache.size() > size) {
            clearCache();
        }
    }

    private String generateCacheKey(String text, String prompt, String model) {
        // 简单的缓存键生成
        return model + ":" + prompt.hashCode() + ":" + text;
    }

    /**
     * 缓存统计
     */
    public static class CacheStats {
        private final int size;
        private final int maxSize;
        private final boolean enabled;

        public CacheStats(int size, int maxSize, boolean enabled) {
            this.size = size;
            this.maxSize = maxSize;
            this.enabled = enabled;
        }

        public int getSize() { return size; }
        public int getMaxSize() { return maxSize; }
        public boolean isEnabled() { return enabled; }
        public double getUtilization() { return (double) size / maxSize; }

        @Override
        public String toString() {
            return String.format("Cache[%d/%d, %.1f%%, %s]",
                    size, maxSize, getUtilization() * 100,
                    enabled ? "enabled" : "disabled");
        }
    }
}
