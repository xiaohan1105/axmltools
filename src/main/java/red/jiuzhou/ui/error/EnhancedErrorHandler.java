package red.jiuzhou.ui.error;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 增强的错误处理器
 * 提供优雅的异常处理、用户友好的错误提示和智能错误恢复机制
 *
 * @author Claude Code Enhanced
 * @version 2.0
 */
public class EnhancedErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(EnhancedErrorHandler.class);

    private final Stage ownerStage;
    private final ErrorRecoveryStrategies recoveryStrategies;
    private final NotificationManager notificationManager;

    // 错误统计
    private volatile int errorCount = 0;
    private volatile long lastErrorTime = 0;
    private final Object errorLock = new Object();

    public EnhancedErrorHandler(Stage ownerStage) {
        this.ownerStage = ownerStage;
        this.recoveryStrategies = new ErrorRecoveryStrategies();
        this.notificationManager = new NotificationManager(ownerStage);

        // 设置全局异常处理器
        setupGlobalExceptionHandler();
    }

    /**
     * 设置全局异常处理器
     */
    private void setupGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            handleExceptionAsync(throwable, "全局未捕获异常", thread.getName());
        });

        // 设置JavaFX的异常处理器
        Platform.setImplicitExit(false);
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            Platform.runLater(() -> {
                handleException(throwable, "JavaFX线程异常", thread.getName());
            });
        });
    }

    /**
     * 异步处理异常
     */
    public CompletableFuture<Void> handleExceptionAsync(Throwable throwable, String context, String component) {
        return CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> {
                handleException(throwable, context, component);
            });
        });
    }

    /**
     * 处理异常的主方法
     */
    public void handleException(Throwable throwable, String context, String component) {
        if (throwable == null) {
            return;
        }

        synchronized (errorLock) {
            errorCount++;
            lastErrorTime = System.currentTimeMillis();
        }

        // 记录错误日志
        log.error("异常处理 - 上下文: {}, 组件: {}", context, component, throwable);

        // 分析异常类型和严重程度
        ErrorAnalysis analysis = analyzeException(throwable, context, component);

        // 根据严重程度选择处理方式
        switch (analysis.getSeverity()) {
            case FATAL:
                handleFatalError(analysis);
                break;
            case ERROR:
                handleError(analysis);
                break;
            case WARNING:
                handleWarning(analysis);
                break;
            case INFO:
                handleInfo(analysis);
                break;
        }

        // 尝试自动恢复
        attemptRecovery(analysis);
    }

    /**
     * 分析异常
     */
    private ErrorAnalysis analyzeException(Throwable throwable, String context, String component) {
        ErrorSeverity severity = ErrorSeverity.ERROR;
        ErrorCategory category = ErrorCategory.UNKNOWN;
        boolean recoverable = true;

        // 根据异常类型分析
        if (throwable instanceof OutOfMemoryError) {
            severity = ErrorSeverity.FATAL;
            category = ErrorCategory.MEMORY;
            recoverable = false;
        } else if (throwable instanceof StackOverflowError) {
            severity = ErrorSeverity.FATAL;
            category = ErrorCategory.STACK;
            recoverable = false;
        } else if (throwable instanceof NullPointerException) {
            severity = ErrorSeverity.ERROR;
            category = ErrorCategory.NULL_POINTER;
        } else if (throwable instanceof IllegalArgumentException) {
            severity = ErrorSeverity.WARNING;
            category = ErrorCategory.INVALID_ARGUMENT;
        } else if (throwable instanceof java.sql.SQLException) {
            severity = ErrorSeverity.ERROR;
            category = ErrorCategory.DATABASE;
            recoverable = isRecoverableDatabaseError((java.sql.SQLException) throwable);
        } else if (throwable instanceof java.io.IOException) {
            severity = ErrorSeverity.ERROR;
            category = ErrorCategory.IO;
        } else if (throwable instanceof RuntimeException) {
            severity = ErrorSeverity.ERROR;
            category = ErrorCategory.RUNTIME;
        } else if (throwable instanceof Exception) {
            severity = ErrorSeverity.WARNING;
            category = ErrorCategory.GENERAL;
        }

        return new ErrorAnalysis(throwable, context, component, severity, category, recoverable);
    }

    /**
     * 判断数据库错误是否可恢复
     */
    private boolean isRecoverableDatabaseError(java.sql.SQLException sqlException) {
        int errorCode = sqlException.getErrorCode();
        String sqlState = sqlException.getSQLState();

        // 连接超时、网络问题等通常可恢复
        if (sqlState != null) {
            return sqlState.startsWith("08") || // Connection exception
                   sqlState.startsWith("58") || // System error
                   errorCode == 0;              // General error
        }

        return false;
    }

    /**
     * 处理致命错误
     */
    private void handleFatalError(ErrorAnalysis analysis) {
        // 显示致命错误对话框
        Optional<ButtonType> result = showFatalErrorDialog(analysis);

        if (result.isPresent() && result.get() == ButtonType.OK) {
            // 保存关键数据
            saveCriticalData();

            // 优雅退出
            gracefulShutdown();
        }
    }

    
    /**
     * 处理警告
     */
    private void handleWarning(ErrorAnalysis analysis) {
        notificationManager.showWarning(analysis.getErrorMessage(), analysis.getRecoverySuggestion());
    }

    /**
     * 处理信息
     */
    private void handleInfo(ErrorAnalysis analysis) {
        notificationManager.showInfo(analysis.getErrorMessage(), "信息");
    }

    /**
     * 尝试自动恢复
     */
    private void attemptRecovery(ErrorAnalysis analysis) {
        if (!analysis.isRecoverable()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000); // 短暂延迟

                boolean recovered = recoveryStrategies.attemptRecovery(analysis);

                if (recovered) {
                    Platform.runLater(() -> {
                        notificationManager.showSuccess("自动恢复成功", "问题已自动解决");
                    });
                }

            } catch (Exception e) {
                log.warn("自动恢复失败", e);
            }
        });
    }

    /**
     * 判断是否应该显示详细错误对话框
     */
    private boolean shouldShowDetailedError(ErrorAnalysis analysis) {
        synchronized (errorLock) {
            // 同一错误在5分钟内不重复显示详细对话框
            if (System.currentTimeMillis() - lastErrorTime < 5 * 60 * 1000) {
                return false;
            }

            // 错误次数过多时不显示详细对话框
            return errorCount <= 3;
        }
    }

    /**
     * 显示致命错误对话框
     */
    private Optional<ButtonType> showFatalErrorDialog(ErrorAnalysis analysis) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("致命错误");
        alert.setHeaderText("应用程序遇到致命错误");
        alert.setContentText(analysis.getErrorMessage());
        alert.initOwner(ownerStage);
        alert.initModality(Modality.APPLICATION_MODAL);

        // 添加详细错误信息
        TextArea textArea = new TextArea(getDetailedErrorMessage(analysis));
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        VBox.setVgrow(textArea, Priority.ALWAYS);
        VBox content = new VBox(10, new Label("详细错误信息:"), textArea);
        alert.getDialogPane().setExpandableContent(content);

        return alert.showAndWait();
    }

    /**
     * 显示错误对话框
     */
    private Optional<ButtonType> showErrorDialog(ErrorAnalysis analysis) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("操作错误");
        alert.setHeaderText(analysis.getErrorMessage());
        alert.setContentText("是否要重试此操作？");

        ButtonType retryButton = new ButtonType("重试", ButtonBar.ButtonData.YES);
        ButtonType ignoreButton = new ButtonType("忽略", ButtonBar.ButtonData.NO);
        ButtonType detailsButton = new ButtonType("详细信息", ButtonBar.ButtonData.HELP);

        alert.getButtonTypes().setAll(retryButton, ignoreButton, detailsButton);

        alert.initOwner(ownerStage);
        alert.initModality(Modality.APPLICATION_MODAL);

        AtomicReference<ButtonType> result = new AtomicReference<>();

        // 处理详细信息按钮
        alert.showingProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                Button detailsBtn = (Button) alert.getDialogPane().lookupButton(detailsButton);
                detailsBtn.setOnAction(e -> {
                    showDetailedErrorDialog(analysis);
                });
            }
        });

        Optional<ButtonType> dialogResult = alert.showAndWait();
        dialogResult.ifPresent(result::set);

        return Optional.ofNullable(result.get());
    }

    /**
     * 显示详细错误对话框
     */
    private void showDetailedErrorDialog(ErrorAnalysis analysis) {
        Stage detailStage = new Stage();
        detailStage.initOwner(ownerStage);
        detailStage.initModality(Modality.APPLICATION_MODAL);
        detailStage.setTitle("错误详细信息");
        detailStage.setWidth(600);
        detailStage.setHeight(400);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        // 基本信息
        VBox infoBox = new VBox(5);
        infoBox.getChildren().addAll(
            new Label("上下文: " + analysis.getContext()),
            new Label("组件: " + analysis.getComponent()),
            new Label("严重程度: " + analysis.getSeverity().getDescription()),
            new Label("类别: " + analysis.getCategory().getDescription()),
            new Label("时间: " + new java.util.Date().toString())
        );

        // 错误信息
        TextArea errorText = new TextArea(getDetailedErrorMessage(analysis));
        errorText.setEditable(false);
        errorText.setWrapText(true);
        VBox.setVgrow(errorText, Priority.ALWAYS);

        // 按钮区域
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button copyButton = new Button("复制错误信息");
        Button closeButton = new Button("关闭");

        copyButton.setOnAction(e -> {
            copyToClipboard(getDetailedErrorMessage(analysis));
            notificationManager.showInfo("错误信息已复制到剪贴板", "复制成功");
        });

        closeButton.setOnAction(e -> detailStage.close());

        buttonBox.getChildren().addAll(copyButton, closeButton);

        root.getChildren().addAll(
            new Label("错误基本信息"),
            infoBox,
            new Separator(),
            new Label("详细错误堆栈"),
            errorText,
            buttonBox
        );

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.getStylesheets().add("/enhanced-responsive-theme.css");
        detailStage.setScene(scene);
        detailStage.show();
    }

    /**
     * 获取详细错误信息
     */
    private String getDetailedErrorMessage(ErrorAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("异常类型: ").append(analysis.getThrowable().getClass().getSimpleName()).append("\n");
        sb.append("异常消息: ").append(analysis.getThrowable().getMessage()).append("\n");
        sb.append("上下文: ").append(analysis.getContext()).append("\n");
        sb.append("组件: ").append(analysis.getComponent()).append("\n");
        sb.append("恢复建议: ").append(analysis.getRecoverySuggestion()).append("\n\n");

        sb.append("堆栈跟踪:\n");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        analysis.getThrowable().printStackTrace(pw);
        sb.append(sw.toString());

        return sb.toString();
    }

    /**
     * 复制到剪贴板
     */
    private void copyToClipboard(String content) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent clipContent = new javafx.scene.input.ClipboardContent();
        clipContent.putString(content);
        clipboard.setContent(clipContent);
    }

    /**
     * 重试操作
     */
    private void retryOperation(ErrorAnalysis analysis) {
        CompletableFuture.runAsync(() -> {
            try {
                // 根据错误类型执行相应的重试逻辑
                boolean success = recoveryStrategies.retryOperation(analysis);

                Platform.runLater(() -> {
                    if (success) {
                        notificationManager.showSuccess("重试成功", "操作已成功完成");
                    } else {
                        notificationManager.showError("重试失败", "请检查相关设置或联系技术支持");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    notificationManager.showError("重试失败", e.getMessage());
                });
            }
        });
    }

    /**
     * 保存关键数据
     */
    private void saveCriticalData() {
        try {
            // TODO: 实现关键数据保存逻辑
            log.info("正在保存关键数据...");
        } catch (Exception e) {
            log.error("保存关键数据失败", e);
        }
    }

    /**
     * 优雅退出
     */
    private void gracefulShutdown() {
        try {
            // 清理资源
            log.info("正在清理资源...");

            // 关闭线程池
            shutdownThreadPools();

            // 关闭数据库连接
            closeDatabaseConnections();

            // 保存设置
            saveSettings();

            // 延迟退出
            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(e -> {
                Platform.exit();
                System.exit(1);
            });
            delay.play();

        } catch (Exception e) {
            log.error("优雅退出过程中出错", e);
            Platform.exit();
            System.exit(1);
        }
    }

    /**
     * 关闭线程池
     */
    private void shutdownThreadPools() {
        // TODO: 实现线程池关闭逻辑
    }

    /**
     * 关闭数据库连接
     */
    private void closeDatabaseConnections() {
        // TODO: 实现数据库连接关闭逻辑
    }

    /**
     * 保存设置
     */
    private void saveSettings() {
        // TODO: 实现设置保存逻辑
    }

    // Getters
    public int getErrorCount() {
        synchronized (errorLock) {
            return errorCount;
        }
    }

    public long getLastErrorTime() {
        synchronized (errorLock) {
            return lastErrorTime;
        }
    }

    /**
     * 重置错误统计
     */
    public void resetErrorCount() {
        synchronized (errorLock) {
            errorCount = 0;
            lastErrorTime = 0;
        }
    }

    /**
     * 错误严重程度枚举
     */
    public enum ErrorSeverity {
        INFO("信息", 1),
        WARNING("警告", 2),
        ERROR("错误", 3),
        FATAL("致命", 4);

        private final String description;
        private final int level;

        ErrorSeverity(String description, int level) {
            this.description = description;
            this.level = level;
        }

        public String getDescription() { return description; }
        public int getLevel() { return level; }
    }

    /**
     * 错误类别枚举
     */
    public enum ErrorCategory {
        UNKNOWN("未知错误"),
        MEMORY("内存错误"),
        STACK("栈溢出"),
        NULL_POINTER("空指针异常"),
        INVALID_ARGUMENT("非法参数"),
        DATABASE("数据库错误"),
        IO("输入输出错误"),
        RUNTIME("运行时错误"),
        GENERAL("一般异常");

        private final String description;

        ErrorCategory(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * 错误分析结果
     */
    public static class ErrorAnalysis {
        private final Throwable throwable;
        private final String context;
        private final String component;
        private final ErrorSeverity severity;
        private final ErrorCategory category;
        private final boolean recoverable;

        public ErrorAnalysis(Throwable throwable, String context, String component,
                           ErrorSeverity severity, ErrorCategory category, boolean recoverable) {
            this.throwable = throwable;
            this.context = context;
            this.component = component;
            this.severity = severity;
            this.category = category;
            this.recoverable = recoverable;
        }

        // Getter 方法
        public Throwable getThrowable() { return throwable; }
        public String getContext() { return context; }
        public String getComponent() { return component; }
        public ErrorSeverity getSeverity() { return severity; }
        public ErrorCategory getCategory() { return category; }
        public boolean isRecoverable() { return recoverable; }

        public String getErrorMessage() {
            String message = throwable.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = throwable.getClass().getSimpleName();
            }
            return message;
        }

        public String getRecoverySuggestion() {
            switch (category) {
                case DATABASE:
                    return "请检查数据库连接配置和网络状态";
                case MEMORY:
                    return "请关闭不必要的应用程序释放内存";
                case IO:
                    return "请检查文件权限和磁盘空间";
                case NULL_POINTER:
                    return "这是一个程序内部错误，请尝试重启应用程序";
                default:
                    return "请重试操作，如问题持续存在请联系技术支持";
            }
        }
    }

    /**
     * 兼容方法 - 处理错误
     */
    public void handleError(Throwable throwable, String context) {
        handleException(throwable, context, "Unknown");
    }

    /**
     * 兼容方法 - 处理错误分析
     */
    public void handleError(ErrorAnalysis analysis) {
        handleException(analysis.getThrowable(), analysis.getContext(), analysis.getComponent());
    }
}