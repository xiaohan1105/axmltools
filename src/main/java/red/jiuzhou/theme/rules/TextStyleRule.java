package red.jiuzhou.theme.rules;

import red.jiuzhou.theme.TransformRule;

/**
 * 文本风格转换规则
 *
 * 使用AI模型转换文本内容，保持原始数值和结构
 *
 * @author Claude
 * @version 1.0
 */
public class TextStyleRule implements TransformRule {

    private final String name;
    private final String description;
    private final String style;
    private final String aiPrompt;
    private final int priority;

    public TextStyleRule(String name, String style, String aiPrompt) {
        this(name, style, aiPrompt, 100);
    }

    public TextStyleRule(String name, String style, String aiPrompt, int priority) {
        this.name = name;
        this.style = style;
        this.aiPrompt = aiPrompt;
        this.priority = priority;
        this.description = String.format("转换文本为%s风格", style);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean matches(String filePath, String fieldName) {
        // 只处理文本字段，跳过数值和ID字段
        String lowerField = fieldName.toLowerCase();

        // 跳过ID字段
        if (lowerField.matches(".*(?i)(id|key|ref)$")) {
            return false;
        }

        // 跳过明显的数值字段
        if (lowerField.matches(".*(?i)(level|attack|defense|hp|mp|damage|price|count|value|rate|percent).*")) {
            return false;
        }

        // 匹配文本字段
        return lowerField.matches(".*(?i)(name|title|desc|description|text|comment|note|info).*");
    }

    @Override
    public String transform(String originalValue, TransformContext context) {
        if (originalValue == null || originalValue.isEmpty()) {
            return originalValue;
        }

        // 如果配置不使用AI转换，直接返回原值
        if (!context.getSettings().isUseAiTransform()) {
            return originalValue;
        }

        // 如果是纯数值，直接返回
        if (originalValue.matches("^[0-9.\\-+]+$")) {
            return originalValue;
        }

        // 构建完整的AI提示词
        String fullPrompt = buildPrompt(originalValue, context);

        // 调用AI服务进行转换
        try {
            String transformed = callAiService(fullPrompt, context.getSettings().getAiModel());
            return transformed != null ? transformed : originalValue;
        } catch (Exception e) {
            // AI服务失败时返回原值
            return originalValue;
        }
    }

    private String buildPrompt(String originalValue, TransformContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(aiPrompt).append("\n\n");
        prompt.append("原文: ").append(originalValue).append("\n");
        prompt.append("字段: ").append(context.getFieldName()).append("\n");

        // 添加上下文信息
        if (context.getRecordData() != null && !context.getRecordData().isEmpty()) {
            String id = context.getRecordId();
            if (id != null) {
                prompt.append("记录ID: ").append(id).append("\n");
            }
        }

        prompt.append("\n请只输出转换后的文本，不要包含任何解释。");
        return prompt.toString();
    }

    /**
     * 调用AI服务
     */
    private String callAiService(String prompt, String model) {
        red.jiuzhou.theme.AITransformService service =
                red.jiuzhou.theme.AITransformService.getInstance();

        if (service == null) {
            // AI服务未初始化，返回null让上层返回原值
            return null;
        }

        // 从prompt中提取原文
        String originalText = extractOriginalText(prompt);
        return service.transform(originalText, prompt, model);
    }

    /**
     * 从完整提示词中提取原文
     */
    private String extractOriginalText(String fullPrompt) {
        // fullPrompt格式: "AI提示词\n\n原文: XXX\n字段: YYY\n..."
        int startIdx = fullPrompt.indexOf("原文: ");
        if (startIdx == -1) {
            return "";
        }
        startIdx += 4;  // "原文: ".length()

        int endIdx = fullPrompt.indexOf("\n", startIdx);
        if (endIdx == -1) {
            endIdx = fullPrompt.length();
        }

        return fullPrompt.substring(startIdx, endIdx).trim();
    }

    @Override
    public boolean validate(String originalValue, String transformedValue) {
        if (transformedValue == null || transformedValue.isEmpty()) {
            return false;
        }

        // 检查长度是否合理（不应该缩短太多或增长太多）
        int originalLength = originalValue.length();
        int transformedLength = transformedValue.length();

        if (transformedLength < originalLength * 0.3) {
            return false; // 缩短太多
        }

        if (transformedLength > originalLength * 3) {
            return false; // 增长太多
        }

        return true;
    }

    public String getStyle() {
        return style;
    }

    public String getAiPrompt() {
        return aiPrompt;
    }

    @Override
    public String toString() {
        return String.format("TextStyleRule[%s: %s]", name, style);
    }
}
