package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能错误恢复系统
 * 为设计师提供自动错误检测、分析和恢复功能
 *
 * 功能特性：
 * - 智能错误识别和分类
 * - 自动修复常见问题
 * - 错误预防建议
 * - 操作回滚机制
 * - 错误学习和优化
 * - 用户友好的错误解释
 */
public class ErrorRecoverySystem {

    private static final Logger log = LoggerFactory.getLogger(ErrorRecoverySystem.class);

    // 单例实例
    private static ErrorRecoverySystem instance;

    // 错误处理器注册表
    private final Map<String, ErrorHandler> errorHandlers = new ConcurrentHashMap<>();
    private final Map<Class<? extends Exception>, String> exceptionMapping = new ConcurrentHashMap<>();

    // 错误历史和统计
    private final List<ErrorRecord> errorHistory = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> errorFrequency = new ConcurrentHashMap<>();

    // 自动修复配置
    private boolean autoRecoveryEnabled = true;
    private int maxRetryAttempts = 3;
    private long retryDelayMs = 1000;

    // 线程池
    private final ExecutorService recoveryExecutor = Executors.newCachedThreadPool();

    private ErrorRecoverySystem() {
        registerBuiltinHandlers();
    }

    /**
     * 获取单例实例
     */
    public static ErrorRecoverySystem getInstance() {
        if (instance == null) {
            instance = new ErrorRecoverySystem();
        }
        return instance;
    }

    /**
     * 注册内置错误处理器
     */
    private void registerBuiltinHandlers() {
        // SQL错误处理器
        registerHandler("sql-error", new SqlErrorHandler());
        mapExceptionToHandler(SQLException.class, "sql-error");

        // 文件操作错误处理器
        registerHandler("file-error", new FileErrorHandler());
        mapExceptionToHandler(java.io.IOException.class, "file-error");

        // 网络连接错误处理器
        registerHandler("network-error", new NetworkErrorHandler());
        mapExceptionToHandler(java.net.ConnectException.class, "network-error");

        // 内存不足错误处理器
        registerHandler("memory-error", new MemoryErrorHandler());
        // OutOfMemoryError is Error, not Exception, handle separately

        // 数据格式错误处理器
        registerHandler("format-error", new DataFormatErrorHandler());
        mapExceptionToHandler(java.text.ParseException.class, "format-error");

        // 并发访问错误处理器
        registerHandler("concurrency-error", new ConcurrencyErrorHandler());
        mapExceptionToHandler(ConcurrentModificationException.class, "concurrency-error");
    }

    // ========== 公共API ==========

    /**
     * 注册错误处理器
     */
    public void registerHandler(String errorType, ErrorHandler handler) {
        errorHandlers.put(errorType, handler);
        log.debug("注册错误处理器: {}", errorType);
    }

    /**
     * 映射异常类型到处理器
     */
    public void mapExceptionToHandler(Class<? extends Exception> exceptionClass, String handlerType) {
        exceptionMapping.put(exceptionClass, handlerType);
    }

    /**
     * 处理异常
     */
    public RecoveryResult handleException(Exception exception, String context) {
        return handleException(exception, context, null);
    }

    /**
     * 处理异常（带重试操作）
     */
    public RecoveryResult handleException(Exception exception, String context, Runnable retryAction) {
        // 记录错误
        ErrorRecord record = recordError(exception, context);

        // 查找合适的处理器
        ErrorHandler handler = findHandler(exception);
        if (handler == null) {
            return handleGenericError(exception, context, retryAction);
        }

        // 执行错误处理
        RecoveryResult result = handler.handle(exception, context);

        // 如果处理失败且有重试操作，尝试自动恢复
        if (!result.isRecovered() && retryAction != null && autoRecoveryEnabled) {
            result = attemptAutoRecovery(exception, context, retryAction, record);
        }

        // 更新错误统计
        updateErrorStatistics(record, result);

        // 通知用户
        notifyUser(record, result);

        return result;
    }

    /**
     * 异步处理异常
     */
    public void handleExceptionAsync(Exception exception, String context, Runnable retryAction) {
        Task<RecoveryResult> task = new Task<RecoveryResult>() {
            @Override
            protected RecoveryResult call() {
                return handleException(exception, context, retryAction);
            }

            @Override
            protected void succeeded() {
                RecoveryResult result = getValue();
                log.info("异步错误处理完成: {}", result.isRecovered() ? "成功" : "失败");
            }

            @Override
            protected void failed() {
                log.error("异步错误处理失败", getException());
            }
        };

        recoveryExecutor.submit(task);
    }

    /**
     * 预防性检查
     */
    public List<PreventionSuggestion> performPreventiveCheck(String operation, Map<String, Object> parameters) {
        List<PreventionSuggestion> suggestions = new ArrayList<>();

        // 基于历史错误数据生成建议
        for (ErrorRecord record : errorHistory) {
            if (record.getContext().contains(operation)) {
                PreventionSuggestion suggestion = generatePreventionSuggestion(record, parameters);
                if (suggestion != null) {
                    suggestions.add(suggestion);
                }
            }
        }

        return suggestions;
    }

    /**
     * 获取错误统计
     */
    public ErrorStatistics getErrorStatistics() {
        return new ErrorStatistics(errorHistory, errorFrequency);
    }

    /**
     * 清理错误历史
     */
    public void cleanupHistory(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        errorHistory.removeIf(record -> record.getTimestamp().isBefore(cutoff));
    }

    // ========== 私有方法 ==========

    /**
     * 记录错误
     */
    private ErrorRecord recordError(Exception exception, String context) {
        ErrorRecord record = new ErrorRecord(
            exception.getClass().getSimpleName(),
            exception.getMessage(),
            context,
            LocalDateTime.now(),
            getStackTraceString(exception)
        );

        errorHistory.add(record);
        errorFrequency.merge(record.getErrorType(), 1, Integer::sum);

        log.warn("记录错误: {} - {}", record.getErrorType(), record.getMessage());
        return record;
    }

    /**
     * 查找错误处理器
     */
    private ErrorHandler findHandler(Exception exception) {
        // 直接类型匹配
        String handlerType = exceptionMapping.get(exception.getClass());
        if (handlerType != null) {
            return errorHandlers.get(handlerType);
        }

        // 父类型匹配
        for (Map.Entry<Class<? extends Exception>, String> entry : exceptionMapping.entrySet()) {
            if (entry.getKey().isAssignableFrom(exception.getClass())) {
                return errorHandlers.get(entry.getValue());
            }
        }

        return null;
    }

    /**
     * 处理通用错误
     */
    private RecoveryResult handleGenericError(Exception exception, String context, Runnable retryAction) {
        String userMessage = generateUserFriendlyMessage(exception);

        List<RecoveryAction> actions = new ArrayList<>();
        if (retryAction != null) {
            actions.add(new RecoveryAction("重试操作", retryAction));
        }
        actions.add(new RecoveryAction("查看详细信息", () -> showErrorDetails(exception)));
        actions.add(new RecoveryAction("报告问题", () -> reportError(exception, context)));

        return new RecoveryResult(false, userMessage, actions);
    }

    /**
     * 尝试自动恢复
     */
    private RecoveryResult attemptAutoRecovery(Exception exception, String context,
                                             Runnable retryAction, ErrorRecord record) {
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            log.info("自动恢复尝试 {}/{}", attempt, maxRetryAttempts);

            try {
                Thread.sleep(retryDelayMs * attempt); // 指数退避
                retryAction.run();

                // 恢复成功
                String message = String.format("自动恢复成功（尝试 %d/%d）", attempt, maxRetryAttempts);
                return new RecoveryResult(true, message, Collections.emptyList());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception retryException) {
                log.warn("自动恢复尝试 {} 失败: {}", attempt, retryException.getMessage());

                // 记录重试失败
                recordError(retryException, context + " (自动恢复尝试 " + attempt + ")");

                if (attempt == maxRetryAttempts) {
                    // 所有尝试都失败了
                    String message = "自动恢复失败，请手动处理";
                    List<RecoveryAction> actions = Arrays.asList(
                        new RecoveryAction("手动重试", retryAction),
                        new RecoveryAction("查看错误详情", () -> showErrorDetails(exception)),
                        new RecoveryAction("获取帮助", () -> showRecoveryHelp(exception))
                    );
                    return new RecoveryResult(false, message, actions);
                }
            }
        }

        return new RecoveryResult(false, "自动恢复被中断", Collections.emptyList());
    }

    /**
     * 更新错误统计
     */
    private void updateErrorStatistics(ErrorRecord record, RecoveryResult result) {
        // TODO: 实现错误统计更新逻辑
        // 可以用于改进错误处理策略
    }

    /**
     * 通知用户
     */
    private void notifyUser(ErrorRecord record, RecoveryResult result) {
        Platform.runLater(() -> {
            if (result.isRecovered()) {
                NotificationSystem.getInstance().showSuccess(
                    "问题已解决",
                    result.getUserMessage()
                );
            } else {
                NotificationSystem.getInstance().showError(
                    "发生错误",
                    result.getUserMessage()
                );
            }
        });
    }

    /**
     * 生成预防建议
     */
    private PreventionSuggestion generatePreventionSuggestion(ErrorRecord record, Map<String, Object> parameters) {
        // 基于历史错误模式生成建议
        if (record.getErrorType().contains("SQL")) {
            return new PreventionSuggestion(
                "数据库连接",
                "建议在执行SQL操作前检查数据库连接状态",
                "high"
            );
        } else if (record.getErrorType().contains("File")) {
            return new PreventionSuggestion(
                "文件操作",
                "建议先检查文件是否存在且有足够的权限",
                "medium"
            );
        }
        return null;
    }

    /**
     * 生成用户友好的错误消息
     */
    private String generateUserFriendlyMessage(Exception exception) {
        String originalMessage = exception.getMessage();

        // SQL异常友好化
        if (exception instanceof SQLException) {
            if (originalMessage.contains("Communications link failure")) {
                return "数据库连接已断开，请检查网络连接或联系管理员";
            } else if (originalMessage.contains("Access denied")) {
                return "数据库访问被拒绝，请检查用户名和密码";
            } else if (originalMessage.contains("Table") && originalMessage.contains("doesn't exist")) {
                return "数据表不存在，可能需要先创建表结构";
            }
        }

        // 文件异常友好化
        if (exception instanceof java.io.IOException) {
            if (originalMessage.contains("Permission denied")) {
                return "文件访问权限不足，请检查文件权限设置";
            } else if (originalMessage.contains("No such file")) {
                return "文件不存在，请检查文件路径是否正确";
            } else if (originalMessage.contains("disk space")) {
                return "磁盘空间不足，请清理磁盘后重试";
            }
        }

        // 内存异常友好化 (OutOfMemoryError is Error, not Exception)
        if (exception.getCause() instanceof OutOfMemoryError) {
            return "内存不足，建议关闭其他应用程序或处理较小的数据集";
        }

        // 默认消息
        return "操作遇到问题: " + (originalMessage != null ? originalMessage : "未知错误");
    }

    /**
     * 显示错误详情
     */
    private void showErrorDetails(Exception exception) {
        ErrorDetailsDialog dialog = new ErrorDetailsDialog(exception);
        Platform.runLater(dialog::show);
    }

    /**
     * 显示恢复帮助
     */
    private void showRecoveryHelp(Exception exception) {
        // TODO: 显示针对特定错误类型的恢复帮助
        ContextualHelpSystem.getInstance().showContextualHelp();
    }

    /**
     * 报告错误
     */
    private void reportError(Exception exception, String context) {
        // TODO: 实现错误报告功能
        log.info("用户报告错误: {} - {}", exception.getClass().getSimpleName(), context);
    }

    /**
     * 获取堆栈跟踪字符串
     */
    private String getStackTraceString(Exception exception) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    // ========== 内部类 ==========

    /**
     * 错误处理器接口
     */
    public interface ErrorHandler {
        RecoveryResult handle(Exception exception, String context);
    }

    /**
     * 恢复结果
     */
    public static class RecoveryResult {
        private final boolean recovered;
        private final String userMessage;
        private final List<RecoveryAction> actions;

        public RecoveryResult(boolean recovered, String userMessage, List<RecoveryAction> actions) {
            this.recovered = recovered;
            this.userMessage = userMessage;
            this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
        }

        public boolean isRecovered() { return recovered; }
        public String getUserMessage() { return userMessage; }
        public List<RecoveryAction> getActions() { return actions; }
    }

    /**
     * 恢复操作
     */
    public static class RecoveryAction {
        private final String name;
        private final Runnable action;

        public RecoveryAction(String name, Runnable action) {
            this.name = name;
            this.action = action;
        }

        public String getName() { return name; }
        public void execute() { action.run(); }
    }

    /**
     * 错误记录
     */
    public static class ErrorRecord {
        private final String errorType;
        private final String message;
        private final String context;
        private final LocalDateTime timestamp;
        private final String stackTrace;

        public ErrorRecord(String errorType, String message, String context,
                          LocalDateTime timestamp, String stackTrace) {
            this.errorType = errorType;
            this.message = message;
            this.context = context;
            this.timestamp = timestamp;
            this.stackTrace = stackTrace;
        }

        public String getErrorType() { return errorType; }
        public String getMessage() { return message; }
        public String getContext() { return context; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getStackTrace() { return stackTrace; }
    }

    /**
     * 预防建议
     */
    public static class PreventionSuggestion {
        private final String category;
        private final String suggestion;
        private final String priority;

        public PreventionSuggestion(String category, String suggestion, String priority) {
            this.category = category;
            this.suggestion = suggestion;
            this.priority = priority;
        }

        public String getCategory() { return category; }
        public String getSuggestion() { return suggestion; }
        public String getPriority() { return priority; }
    }

    /**
     * 错误统计
     */
    public static class ErrorStatistics {
        private final int totalErrors;
        private final Map<String, Integer> errorsByType;
        private final LocalDateTime oldestError;
        private final LocalDateTime newestError;

        public ErrorStatistics(List<ErrorRecord> errorHistory, Map<String, Integer> errorFrequency) {
            this.totalErrors = errorHistory.size();
            this.errorsByType = new HashMap<>(errorFrequency);

            if (!errorHistory.isEmpty()) {
                this.oldestError = errorHistory.stream()
                    .map(ErrorRecord::getTimestamp)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
                this.newestError = errorHistory.stream()
                    .map(ErrorRecord::getTimestamp)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            } else {
                this.oldestError = null;
                this.newestError = null;
            }
        }

        public int getTotalErrors() { return totalErrors; }
        public Map<String, Integer> getErrorsByType() { return errorsByType; }
        public LocalDateTime getOldestError() { return oldestError; }
        public LocalDateTime getNewestError() { return newestError; }
    }

    // ========== 具体错误处理器实现 ==========

    /**
     * SQL错误处理器
     */
    private static class SqlErrorHandler implements ErrorHandler {
        @Override
        public RecoveryResult handle(Exception exception, String context) {
            SQLException sqlEx = (SQLException) exception;

            String userMessage;
            List<RecoveryAction> actions = new ArrayList<>();

            switch (sqlEx.getErrorCode()) {
                case 1045: // Access denied
                    userMessage = "数据库访问被拒绝，请检查用户名和密码";
                    actions.add(new RecoveryAction("重新配置数据库连接", () -> {
                        // TODO: 打开数据库配置对话框
                    }));
                    break;

                case 1146: // Table doesn't exist
                    userMessage = "数据表不存在，需要先创建表结构";
                    actions.add(new RecoveryAction("生成DDL", () -> {
                        // TODO: 自动生成表结构
                    }));
                    break;

                case 2003: // Can't connect
                    userMessage = "无法连接到数据库服务器，请检查网络和服务状态";
                    actions.add(new RecoveryAction("测试连接", () -> {
                        // TODO: 执行连接测试
                    }));
                    break;

                default:
                    userMessage = "数据库操作失败: " + sqlEx.getMessage();
                    break;
            }

            actions.add(new RecoveryAction("查看SQL错误详情", () -> {
                // TODO: 显示SQL错误详细信息
            }));

            return new RecoveryResult(false, userMessage, actions);
        }
    }

    /**
     * 文件错误处理器
     */
    private static class FileErrorHandler implements ErrorHandler {
        @Override
        public RecoveryResult handle(Exception exception, String context) {
            java.io.IOException ioEx = (java.io.IOException) exception;
            String message = ioEx.getMessage();

            String userMessage;
            List<RecoveryAction> actions = new ArrayList<>();

            if (message.contains("Permission denied")) {
                userMessage = "文件访问权限不足";
                actions.add(new RecoveryAction("更改文件权限", () -> {
                    // TODO: 提供权限更改指导
                }));
            } else if (message.contains("No such file")) {
                userMessage = "文件不存在";
                actions.add(new RecoveryAction("选择其他文件", () -> {
                    // TODO: 打开文件选择器
                }));
            } else if (message.contains("disk space")) {
                userMessage = "磁盘空间不足";
                actions.add(new RecoveryAction("清理磁盘空间", () -> {
                    // TODO: 提供磁盘清理建议
                }));
            } else {
                userMessage = "文件操作失败: " + message;
            }

            return new RecoveryResult(false, userMessage, actions);
        }
    }

    /**
     * 网络错误处理器
     */
    private static class NetworkErrorHandler implements ErrorHandler {
        @Override
        public RecoveryResult handle(Exception exception, String context) {
            String userMessage = "网络连接失败，请检查网络设置";

            List<RecoveryAction> actions = Arrays.asList(
                new RecoveryAction("检查网络连接", () -> {
                    // TODO: 执行网络诊断
                }),
                new RecoveryAction("重试连接", () -> {
                    // TODO: 重新尝试网络操作
                })
            );

            return new RecoveryResult(false, userMessage, actions);
        }
    }

    /**
     * 内存错误处理器
     */
    private static class MemoryErrorHandler implements ErrorHandler {
        @Override
        public RecoveryResult handle(Exception exception, String context) {
            String userMessage = "内存不足，建议减少数据处理量或增加内存分配";

            List<RecoveryAction> actions = Arrays.asList(
                new RecoveryAction("垃圾回收", () -> {
                    System.gc();
                    NotificationSystem.getInstance().showInfo("内存清理", "已执行垃圾回收");
                }),
                new RecoveryAction("调整处理参数", () -> {
                    // TODO: 打开内存配置对话框
                })
            );

            return new RecoveryResult(false, userMessage, actions);
        }
    }

    /**
     * 数据格式错误处理器
     */
    private static class DataFormatErrorHandler implements ErrorHandler {
        @Override
        public RecoveryResult handle(Exception exception, String context) {
            String userMessage = "数据格式不正确，请检查输入数据的格式";

            List<RecoveryAction> actions = Arrays.asList(
                new RecoveryAction("查看格式要求", () -> {
                    // TODO: 显示数据格式说明
                }),
                new RecoveryAction("数据格式转换", () -> {
                    // TODO: 提供数据格式转换工具
                })
            );

            return new RecoveryResult(false, userMessage, actions);
        }
    }

    /**
     * 并发访问错误处理器
     */
    private static class ConcurrencyErrorHandler implements ErrorHandler {
        @Override
        public RecoveryResult handle(Exception exception, String context) {
            String userMessage = "数据并发访问冲突，请稍后重试";

            List<RecoveryAction> actions = Arrays.asList(
                new RecoveryAction("自动重试", () -> {
                    // TODO: 实现自动重试逻辑
                }),
                new RecoveryAction("刷新数据", () -> {
                    // TODO: 刷新当前数据视图
                })
            );

            return new RecoveryResult(false, userMessage, actions);
        }
    }

    /**
     * 错误详情对话框
     */
    private static class ErrorDetailsDialog {
        private final Exception exception;

        public ErrorDetailsDialog(Exception exception) {
            this.exception = exception;
        }

        public void show() {
            Alert dialog = new Alert(Alert.AlertType.ERROR);
            dialog.setTitle("错误详情");
            dialog.setHeaderText(exception.getClass().getSimpleName());

            // 创建详细信息区域
            javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea();
            textArea.setText(getDetailedErrorInfo());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);

            dialog.getDialogPane().setExpandableContent(textArea);
            dialog.getDialogPane().setExpanded(true);
            dialog.getDialogPane().setPrefSize(600, 400);

            dialog.showAndWait();
        }

        private String getDetailedErrorInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append("错误类型: ").append(exception.getClass().getName()).append("\n");
            sb.append("错误消息: ").append(exception.getMessage()).append("\n");
            sb.append("发生时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            sb.append("堆栈跟踪:\n");

            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            exception.printStackTrace(pw);
            sb.append(sw.toString());

            return sb.toString();
        }
    }
}