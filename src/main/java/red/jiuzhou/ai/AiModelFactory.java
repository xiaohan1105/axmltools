package red.jiuzhou.ai;

import java.util.Arrays;
import java.util.List;

public class AiModelFactory {

    private static final List<String> SUPPORTED_MODELS = Arrays.asList("qwen", "doubao", "kimi", "deepseek");

    public static AiModelClient getClient(String modelName) {
        switch (modelName.toLowerCase()) {
            case "qwen": return new TongYiClient();
            case "doubao": return new DoubaoClient();
            case "kimi": return new KimiClient();
            case "deepseek": return new DeepSeekClient();
            default: throw new IllegalArgumentException("不支持的模型类型：" + modelName);
        }
    }

    /**
     * 获取模型的规范名称
     */
    public static String canonicalName(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        String normalized = modelName.toLowerCase().trim();
        if (SUPPORTED_MODELS.contains(normalized)) {
            return normalized;
        }
        // 处理别名
        if (normalized.contains("tongyi") || normalized.contains("qianwen")) {
            return "qwen";
        }
        if (normalized.contains("deep") && normalized.contains("seek")) {
            return "deepseek";
        }
        throw new IllegalArgumentException("不支持的模型类型：" + modelName);
    }

    /**
     * 返回支持的模型列表
     */
    public static List<String> supportedModels() {
        return SUPPORTED_MODELS;
    }
}