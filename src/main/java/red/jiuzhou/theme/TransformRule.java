package red.jiuzhou.theme;

/**
 * 转换规则接口
 *
 * 定义如何转换XML内容的规则
 *
 * @author Claude
 * @version 1.0
 */
public interface TransformRule {

    /**
     * 规则名称
     */
    String getName();

    /**
     * 规则描述
     */
    String getDescription();

    /**
     * 规则优先级（数值越大优先级越高）
     */
    int getPriority();

    /**
     * 检查此规则是否适用于指定的文件和字段
     *
     * @param filePath 文件路径
     * @param fieldName 字段名
     * @return true 如果规则适用
     */
    boolean matches(String filePath, String fieldName);

    /**
     * 执行转换
     *
     * @param originalValue 原始值
     * @param context 转换上下文
     * @return 转换后的值
     */
    String transform(String originalValue, TransformContext context);

    /**
     * 验证转换结果
     *
     * @param originalValue 原始值
     * @param transformedValue 转换后的值
     * @return true 如果结果有效
     */
    default boolean validate(String originalValue, String transformedValue) {
        return transformedValue != null && !transformedValue.isEmpty();
    }

    /**
     * 转换上下文
     */
    class TransformContext {
        private final String filePath;
        private final String fieldName;
        private final String recordId;
        private final java.util.Map<String, String> recordData;
        private final ThemeSettings settings;

        public TransformContext(String filePath, String fieldName, String recordId,
                                java.util.Map<String, String> recordData, ThemeSettings settings) {
            this.filePath = filePath;
            this.fieldName = fieldName;
            this.recordId = recordId;
            this.recordData = recordData;
            this.settings = settings;
        }

        public String getFilePath() { return filePath; }
        public String getFieldName() { return fieldName; }
        public String getRecordId() { return recordId; }
        public java.util.Map<String, String> getRecordData() { return recordData; }
        public ThemeSettings getSettings() { return settings; }

        /**
         * 获取记录中的其他字段值
         */
        public String getFieldValue(String field) {
            return recordData.get(field);
        }

        /**
         * 检查是否为数值字段
         */
        public boolean isNumericField() {
            return fieldName.matches(".*(?i)(level|attack|defense|hp|mp|damage|price|count|value|rate).*");
        }

        /**
         * 检查是否为ID字段
         */
        public boolean isIdField() {
            return fieldName.matches(".*(?i)(id|key|ref)$");
        }
    }
}
