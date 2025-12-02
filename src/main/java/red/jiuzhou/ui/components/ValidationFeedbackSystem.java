package red.jiuzhou.ui.components;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 数据验证和操作反馈系统
 * 为设计师提供实时的数据验证提示和操作确认反馈
 *
 * 功能特性：
 * - 实时字段验证
 * - 可视化验证状态
 * - 智能错误提示
 * - 操作确认对话框
 * - 数据完整性检查
 * - 批量验证支持
 */
public class ValidationFeedbackSystem {

    private static final Logger log = LoggerFactory.getLogger(ValidationFeedbackSystem.class);

    // 单例实例
    private static ValidationFeedbackSystem instance;

    // 验证器注册表
    private final Map<String, FieldValidator> validators = new ConcurrentHashMap<>();
    private final Map<Node, ValidationBinding> fieldBindings = new ConcurrentHashMap<>();

    // 验证结果缓存
    private final Map<String, ValidationResult> validationCache = new ConcurrentHashMap<>();

    // 全局验证状态
    private final BooleanProperty globalValidationState = new SimpleBooleanProperty(true);

    private ValidationFeedbackSystem() {
        registerBuiltinValidators();
    }

    /**
     * 获取单例实例
     */
    public static ValidationFeedbackSystem getInstance() {
        if (instance == null) {
            instance = new ValidationFeedbackSystem();
        }
        return instance;
    }

    /**
     * 注册内置验证器
     */
    private void registerBuiltinValidators() {
        // 必填字段验证器
        registerValidator("required", new RequiredValidator());

        // 电子邮件验证器
        registerValidator("email", new EmailValidator());

        // 数字验证器
        registerValidator("number", new NumberValidator());

        // 长度验证器
        registerValidator("length", new LengthValidator());

        // 正则表达式验证器
        registerValidator("regex", new RegexValidator());

        // 日期验证器
        registerValidator("date", new DateValidator());

        // URL验证器
        registerValidator("url", new UrlValidator());

        // 文件路径验证器
        registerValidator("filepath", new FilePathValidator());
    }

    // ========== 公共API ==========

    /**
     * 注册自定义验证器
     */
    public void registerValidator(String name, FieldValidator validator) {
        validators.put(name, validator);
        log.debug("注册验证器: {}", name);
    }

    /**
     * 为字段绑定验证
     */
    public ValidationBinding bindValidation(Node field, String fieldName, String... validatorNames) {
        ValidationBinding binding = new ValidationBinding(field, fieldName, Arrays.asList(validatorNames));
        fieldBindings.put(field, binding);

        // 如果是输入控件，添加实时验证
        if (field instanceof TextInputControl) {
            TextInputControl textInput = (TextInputControl) field;
            textInput.textProperty().addListener((obs, oldText, newText) -> {
                validateField(binding);
            });
        }

        // 初始验证
        validateField(binding);
        return binding;
    }

    /**
     * 移除字段验证绑定
     */
    public void unbindValidation(Node field) {
        ValidationBinding binding = fieldBindings.remove(field);
        if (binding != null) {
            binding.cleanup();
        }
    }

    /**
     * 验证所有字段
     */
    public ValidationSummary validateAll() {
        ValidationSummary summary = new ValidationSummary();

        for (ValidationBinding binding : fieldBindings.values()) {
            ValidationResult result = validateField(binding);
            summary.addResult(binding.getFieldName(), result);
        }

        // 更新全局验证状态
        globalValidationState.set(summary.isValid());

        return summary;
    }

    /**
     * 验证单个字段
     */
    public ValidationResult validateField(String fieldName, Object value, String... validatorNames) {
        List<ValidationError> errors = new ArrayList<>();

        for (String validatorName : validatorNames) {
            FieldValidator validator = validators.get(validatorName);
            if (validator != null) {
                ValidationResult result = validator.validate(value);
                if (!result.isValid()) {
                    errors.addAll(result.getErrors());
                }
            }
        }

        ValidationResult result = new ValidationResult(errors.isEmpty(), errors);
        validationCache.put(fieldName, result);
        return result;
    }

    /**
     * 显示操作确认对话框
     */
    public void showConfirmation(String title, String message, Runnable onConfirm, Runnable onCancel) {
        ConfirmationDialog dialog = new ConfirmationDialog(title, message);
        dialog.setOnConfirm(onConfirm);
        dialog.setOnCancel(onCancel);
        dialog.show();
    }

    /**
     * 显示数据完整性检查结果
     */
    public void showIntegrityCheck(List<IntegrityIssue> issues) {
        IntegrityCheckDialog dialog = new IntegrityCheckDialog(issues);
        dialog.show();
    }

    /**
     * 显示批量操作确认
     */
    public void showBatchConfirmation(String operation, int itemCount, Runnable onConfirm) {
        String message = String.format("确定要%s %d 个项目吗？此操作不可撤销。", operation, itemCount);
        showConfirmation("批量操作确认", message, onConfirm, null);
    }

    // ========== 私有方法 ==========

    /**
     * 验证字段
     */
    private ValidationResult validateField(ValidationBinding binding) {
        Object value = binding.getValue();
        ValidationResult result = validateField(binding.getFieldName(), value,
            binding.getValidatorNames().toArray(new String[0]));

        // 更新UI状态
        Platform.runLater(() -> updateFieldAppearance(binding, result));

        return result;
    }

    /**
     * 更新字段外观
     */
    private void updateFieldAppearance(ValidationBinding binding, ValidationResult result) {
        Node field = binding.getField();

        // 移除现有样式
        field.getStyleClass().removeAll("validation-valid", "validation-invalid", "validation-warning");

        if (result.isValid()) {
            field.getStyleClass().add("validation-valid");
            hideValidationMessage(binding);
        } else {
            field.getStyleClass().add("validation-invalid");
            showValidationMessage(binding, result);

            // 添加摇摆动画提醒用户
            playValidationAnimation(field);
        }
    }

    /**
     * 显示验证消息
     */
    private void showValidationMessage(ValidationBinding binding, ValidationResult result) {
        Label messageLabel = binding.getMessageLabel();
        if (messageLabel == null) {
            messageLabel = createMessageLabel();
            binding.setMessageLabel(messageLabel);

            // 将消息标签添加到字段下方
            insertMessageLabel(binding.getField(), messageLabel);
        }

        // 设置错误消息
        String message = result.getErrors().stream()
            .map(ValidationError::getMessage)
            .findFirst()
            .orElse("验证失败");

        messageLabel.setText("[错误] " + message);
        messageLabel.setVisible(true);

        // 淡入动画
        FadeTransition fade = new FadeTransition(Duration.millis(200), messageLabel);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * 隐藏验证消息
     */
    private void hideValidationMessage(ValidationBinding binding) {
        Label messageLabel = binding.getMessageLabel();
        if (messageLabel != null && messageLabel.isVisible()) {
            FadeTransition fade = new FadeTransition(Duration.millis(200), messageLabel);
            fade.setFromValue(1);
            fade.setToValue(0);
            fade.setOnFinished(e -> messageLabel.setVisible(false));
            fade.play();
        }
    }

    /**
     * 创建消息标签
     */
    private Label createMessageLabel() {
        Label label = new Label();
        label.getStyleClass().add("validation-message");
        label.setTextFill(Color.RED);
        label.setVisible(false);
        label.setWrapText(true);
        return label;
    }

    /**
     * 将消息标签插入到字段下方
     */
    private void insertMessageLabel(Node field, Label messageLabel) {
        if (field.getParent() instanceof VBox) {
            VBox parent = (VBox) field.getParent();
            int index = parent.getChildren().indexOf(field);
            if (index >= 0 && index < parent.getChildren().size() - 1) {
                parent.getChildren().add(index + 1, messageLabel);
            } else {
                parent.getChildren().add(messageLabel);
            }
        } else if (field.getParent() instanceof GridPane) {
            GridPane parent = (GridPane) field.getParent();
            Integer rowIndex = GridPane.getRowIndex(field);
            Integer colIndex = GridPane.getColumnIndex(field);
            if (rowIndex != null && colIndex != null) {
                GridPane.setRowIndex(messageLabel, rowIndex + 1);
                GridPane.setColumnIndex(messageLabel, colIndex);
                parent.getChildren().add(messageLabel);
            }
        }
    }

    /**
     * 播放验证动画
     */
    private void playValidationAnimation(Node field) {
        // 摇摆动画
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), field);
        shake.setFromX(0);
        shake.setToX(3);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> field.setTranslateX(0));
        shake.play();
    }

    // ========== 内部类 ==========

    /**
     * 字段验证器接口
     */
    public interface FieldValidator {
        ValidationResult validate(Object value);
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;

        public ValidationResult(boolean valid, List<ValidationError> errors) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        }

        public boolean isValid() { return valid; }
        public List<ValidationError> getErrors() { return errors; }

        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList());
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, Arrays.asList(new ValidationError(message)));
        }
    }

    /**
     * 验证错误
     */
    public static class ValidationError {
        private final String message;
        private final String code;

        public ValidationError(String message) {
            this(message, null);
        }

        public ValidationError(String message, String code) {
            this.message = message;
            this.code = code;
        }

        public String getMessage() { return message; }
        public String getCode() { return code; }
    }

    /**
     * 验证绑定
     */
    public static class ValidationBinding {
        private final Node field;
        private final String fieldName;
        private final List<String> validatorNames;
        private Label messageLabel;

        public ValidationBinding(Node field, String fieldName, List<String> validatorNames) {
            this.field = field;
            this.fieldName = fieldName;
            this.validatorNames = new ArrayList<>(validatorNames);
        }

        public Node getField() { return field; }
        public String getFieldName() { return fieldName; }
        public List<String> getValidatorNames() { return validatorNames; }
        public Label getMessageLabel() { return messageLabel; }
        public void setMessageLabel(Label messageLabel) { this.messageLabel = messageLabel; }

        public Object getValue() {
            if (field instanceof TextInputControl) {
                return ((TextInputControl) field).getText();
            } else if (field instanceof ComboBox) {
                return ((ComboBox<?>) field).getValue();
            } else if (field instanceof CheckBox) {
                return ((CheckBox) field).isSelected();
            }
            return null;
        }

        public void cleanup() {
            if (messageLabel != null && messageLabel.getParent() instanceof Pane) {
                ((Pane) messageLabel.getParent()).getChildren().remove(messageLabel);
            }
        }
    }

    /**
     * 验证摘要
     */
    public static class ValidationSummary {
        private final Map<String, ValidationResult> results = new HashMap<>();
        private boolean valid = true;

        public void addResult(String fieldName, ValidationResult result) {
            results.put(fieldName, result);
            if (!result.isValid()) {
                valid = false;
            }
        }

        public boolean isValid() { return valid; }
        public Map<String, ValidationResult> getResults() { return results; }
        public List<String> getInvalidFields() {
            return results.entrySet().stream()
                .filter(entry -> !entry.getValue().isValid())
                .map(Map.Entry::getKey)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    /**
     * 完整性问题
     */
    public static class IntegrityIssue {
        private final String field;
        private final String issue;
        private final String severity;
        private final String suggestion;

        public IntegrityIssue(String field, String issue, String severity, String suggestion) {
            this.field = field;
            this.issue = issue;
            this.severity = severity;
            this.suggestion = suggestion;
        }

        public String getField() { return field; }
        public String getIssue() { return issue; }
        public String getSeverity() { return severity; }
        public String getSuggestion() { return suggestion; }
    }

    // ========== 内置验证器实现 ==========

    /**
     * 必填字段验证器
     */
    private static class RequiredValidator implements FieldValidator {
        @Override
        public ValidationResult validate(Object value) {
            if (value == null || value.toString().trim().isEmpty()) {
                return ValidationResult.failure("此字段为必填项");
            }
            return ValidationResult.success();
        }
    }

    /**
     * 电子邮件验证器
     */
    private static class EmailValidator implements FieldValidator {
        private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
        );

        @Override
        public ValidationResult validate(Object value) {
            if (value == null || value.toString().trim().isEmpty()) {
                return ValidationResult.success(); // 空值交给required验证器处理
            }

            if (!EMAIL_PATTERN.matcher(value.toString()).matches()) {
                return ValidationResult.failure("请输入有效的电子邮件地址");
            }
            return ValidationResult.success();
        }
    }

    /**
     * 数字验证器
     */
    private static class NumberValidator implements FieldValidator {
        @Override
        public ValidationResult validate(Object value) {
            if (value == null || value.toString().trim().isEmpty()) {
                return ValidationResult.success();
            }

            try {
                Double.parseDouble(value.toString());
                return ValidationResult.success();
            } catch (NumberFormatException e) {
                return ValidationResult.failure("请输入有效的数字");
            }
        }
    }

    /**
     * 长度验证器
     */
    private static class LengthValidator implements FieldValidator {
        private int minLength = 0;
        private int maxLength = Integer.MAX_VALUE;

        public LengthValidator setRange(int minLength, int maxLength) {
            this.minLength = minLength;
            this.maxLength = maxLength;
            return this;
        }

        @Override
        public ValidationResult validate(Object value) {
            if (value == null) {
                return ValidationResult.success();
            }

            String str = value.toString();
            if (str.length() < minLength) {
                return ValidationResult.failure("长度不能少于 " + minLength + " 个字符");
            }
            if (str.length() > maxLength) {
                return ValidationResult.failure("长度不能超过 " + maxLength + " 个字符");
            }
            return ValidationResult.success();
        }
    }

    /**
     * 正则表达式验证器
     */
    private static class RegexValidator implements FieldValidator {
        private Pattern pattern;
        private String errorMessage = "格式不正确";

        public RegexValidator setPattern(String regex, String errorMessage) {
            this.pattern = Pattern.compile(regex);
            this.errorMessage = errorMessage;
            return this;
        }

        @Override
        public ValidationResult validate(Object value) {
            if (value == null || value.toString().trim().isEmpty()) {
                return ValidationResult.success();
            }

            if (pattern == null || pattern.matcher(value.toString()).matches()) {
                return ValidationResult.success();
            }
            return ValidationResult.failure(errorMessage);
        }
    }

    /**
     * 日期验证器
     */
    private static class DateValidator implements FieldValidator {
        @Override
        public ValidationResult validate(Object value) {
            if (value == null || value.toString().trim().isEmpty()) {
                return ValidationResult.success();
            }

            // TODO: 实现日期格式验证
            return ValidationResult.success();
        }
    }

    /**
     * URL验证器
     */
    private static class UrlValidator implements FieldValidator {
        @Override
        public ValidationResult validate(Object value) {
            if (value == null || value.toString().trim().isEmpty()) {
                return ValidationResult.success();
            }

            try {
                new java.net.URL(value.toString());
                return ValidationResult.success();
            } catch (java.net.MalformedURLException e) {
                return ValidationResult.failure("请输入有效的URL地址");
            }
        }
    }

    /**
     * 文件路径验证器
     */
    private static class FilePathValidator implements FieldValidator {
        @Override
        public ValidationResult validate(Object value) {
            if (value == null || value.toString().trim().isEmpty()) {
                return ValidationResult.success();
            }

            java.io.File file = new java.io.File(value.toString());
            if (!file.exists()) {
                return ValidationResult.failure("文件或目录不存在");
            }
            return ValidationResult.success();
        }
    }

    // ========== 对话框组件 ==========

    /**
     * 确认对话框
     */
    private static class ConfirmationDialog {
        private final Alert dialog;
        private Runnable onConfirm;
        private Runnable onCancel;

        public ConfirmationDialog(String title, String message) {
            dialog = new Alert(Alert.AlertType.CONFIRMATION);
            dialog.setTitle(title);
            dialog.setHeaderText(null);
            dialog.setContentText(message);

            ButtonType confirmBtn = new ButtonType("确认", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelBtn = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getButtonTypes().setAll(confirmBtn, cancelBtn);
        }

        public void setOnConfirm(Runnable onConfirm) { this.onConfirm = onConfirm; }
        public void setOnCancel(Runnable onCancel) { this.onCancel = onCancel; }

        public void show() {
            dialog.showAndWait().ifPresent(result -> {
                if (result.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                    if (onConfirm != null) onConfirm.run();
                } else if (onCancel != null) {
                    onCancel.run();
                }
            });
        }
    }

    /**
     * 完整性检查对话框
     */
    private static class IntegrityCheckDialog {
        private final Alert dialog;

        public IntegrityCheckDialog(List<IntegrityIssue> issues) {
            dialog = new Alert(Alert.AlertType.WARNING);
            dialog.setTitle("数据完整性检查");
            dialog.setHeaderText("发现 " + issues.size() + " 个数据完整性问题");

            // 创建问题列表
            VBox content = new VBox(10);
            for (IntegrityIssue issue : issues) {
                HBox issueRow = new HBox(10);
                issueRow.setAlignment(Pos.CENTER_LEFT);

                Label severityLabel = new Label(getSeverityIcon(issue.getSeverity()));
                Label fieldLabel = new Label(issue.getField() + ":");
                fieldLabel.getStyleClass().add("issue-field");

                Label issueLabel = new Label(issue.getIssue());
                issueLabel.setWrapText(true);

                issueRow.getChildren().addAll(severityLabel, fieldLabel, issueLabel);
                content.getChildren().add(issueRow);
            }

            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setPrefHeight(300);
            scrollPane.setFitToWidth(true);

            dialog.getDialogPane().setContent(scrollPane);
            dialog.getDialogPane().setPrefWidth(500);
        }

        private String getSeverityIcon(String severity) {
            switch (severity.toLowerCase()) {
                case "error": return "[错误]";
                case "warning": return "[警告]";
                case "info": return "[信息]";
                default: return "•";
            }
        }

        public void show() {
            dialog.showAndWait();
        }
    }
}