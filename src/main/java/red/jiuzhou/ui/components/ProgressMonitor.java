package red.jiuzhou.ui.components;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能进度监控组件
 * 为长时间运行的数据操作提供详细的进度可视化和状态监控
 *
 * 功能特性：
 * - 多阶段进度跟踪
 * - 实时性能指标
 * - 错误和警告日志
 * - 预计完成时间
 * - 可取消的操作
 * - 详细的状态信息
 */
public class ProgressMonitor {

    private static final Logger log = LoggerFactory.getLogger(ProgressMonitor.class);

    private Stage progressStage;
    private final Stage parentStage;

    // 进度状态
    private final DoubleProperty overallProgress = new SimpleDoubleProperty(0.0);
    private final StringProperty currentOperation = new SimpleStringProperty("准备中...");
    private final StringProperty statusMessage = new SimpleStringProperty("正在初始化");
    private final LongProperty estimatedTimeRemaining = new SimpleLongProperty(0);

    // UI组件
    private ProgressBar mainProgressBar;
    private Label operationLabel;
    private Label statusLabel;
    private Label timeLabel;
    private Label speedLabel;
    private ListView<LogEntry> logListView;
    private VBox stagesContainer;
    private Button cancelButton;
    private Button detailsButton;

    // 阶段管理
    private final List<ProgressStage> stages = new ArrayList<>();
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();

    // 性能统计
    private long startTime;
    private long lastUpdateTime;
    private int processedItems = 0;
    private int totalItems = 0;

    // 控制标志
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private boolean detailsVisible = false;

    public ProgressMonitor(Stage parentStage, String title) {
        this.parentStage = parentStage;
        createProgressWindow(title);
    }

    /**
     * 创建进度窗口
     */
    private void createProgressWindow(String title) {
        progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.initOwner(parentStage);
        progressStage.initStyle(StageStyle.DECORATED);
        progressStage.setTitle(title);
        progressStage.setResizable(false);

        VBox mainContainer = createMainContainer();
        Scene scene = new Scene(mainContainer);
        scene.getStylesheets().add("/modern-theme.css");
        progressStage.setScene(scene);

        // 添加关闭请求处理
        progressStage.setOnCloseRequest(e -> {
            if (!completed.get()) {
                requestCancel();
                e.consume(); // 阻止直接关闭
            }
        });
    }

    /**
     * 创建主容器
     */
    private VBox createMainContainer() {
        VBox container = new VBox();
        container.getStyleClass().add("progress-monitor");
        container.setPrefWidth(500);

        // 创建头部区域
        VBox header = createHeaderSection();

        // 创建主要进度区域
        VBox progressSection = createProgressSection();

        // 创建阶段显示区域
        stagesContainer = createStagesSection();

        // 创建详细信息区域（初始隐藏）
        VBox detailsSection = createDetailsSection();
        detailsSection.setVisible(false);
        detailsSection.setManaged(false);

        // 创建底部按钮区域
        HBox buttonSection = createButtonSection();

        container.getChildren().addAll(
            header, progressSection, stagesContainer, detailsSection, buttonSection
        );

        return container;
    }

    /**
     * 创建头部区域
     */
    private VBox createHeaderSection() {
        VBox header = new VBox(10);
        header.getStyleClass().add("progress-header");
        header.setPadding(new Insets(20, 24, 16, 24));

        // 操作标题
        operationLabel = new Label();
        operationLabel.getStyleClass().add("operation-title");
        operationLabel.textProperty().bind(currentOperation);

        // 状态信息
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-message");
        statusLabel.textProperty().bind(statusMessage);

        header.getChildren().addAll(operationLabel, statusLabel);
        return header;
    }

    /**
     * 创建进度区域
     */
    private VBox createProgressSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("progress-section");
        section.setPadding(new Insets(0, 24, 16, 24));

        // 主进度条
        mainProgressBar = new ProgressBar();
        mainProgressBar.getStyleClass().add("main-progress");
        mainProgressBar.setPrefWidth(400);
        mainProgressBar.progressProperty().bind(overallProgress);

        // 进度百分比标签
        Label percentLabel = new Label();
        percentLabel.getStyleClass().add("progress-percent");
        percentLabel.textProperty().bind(overallProgress.multiply(100).asString("%.1f%%"));

        // 性能指标行
        HBox metricsRow = createMetricsRow();

        section.getChildren().addAll(mainProgressBar, percentLabel, metricsRow);
        return section;
    }

    /**
     * 创建性能指标行
     */
    private HBox createMetricsRow() {
        HBox metricsRow = new HBox(20);
        metricsRow.setAlignment(Pos.CENTER);
        metricsRow.getStyleClass().add("metrics-row");

        // 剩余时间
        timeLabel = new Label("估计剩余: --");
        timeLabel.getStyleClass().add("metric-label");

        // 处理速度
        speedLabel = new Label("速度: --");
        speedLabel.getStyleClass().add("metric-label");

        // 已处理/总数
        Label itemsLabel = new Label("项目: 0/0");
        itemsLabel.getStyleClass().add("metric-label");

        // 绑定数据更新
        itemsLabel.textProperty().bind(
            javafx.beans.binding.Bindings.format("项目: %d/%d", processedItems, totalItems)
        );

        metricsRow.getChildren().addAll(timeLabel, speedLabel, itemsLabel);
        return metricsRow;
    }

    /**
     * 创建阶段显示区域
     */
    private VBox createStagesSection() {
        VBox section = new VBox(8);
        section.getStyleClass().add("stages-section");
        section.setPadding(new Insets(0, 24, 16, 24));

        return section;
    }

    /**
     * 创建详细信息区域
     */
    private VBox createDetailsSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("details-section");
        section.setPadding(new Insets(16, 24, 16, 24));

        // 日志标题
        Label logTitle = new Label("详细日志");
        logTitle.getStyleClass().add("section-title");

        // 日志列表
        logListView = new ListView<>();
        logListView.getStyleClass().add("log-list");
        logListView.setPrefHeight(200);
        logListView.setItems(logEntries);

        // 自定义日志项显示
        logListView.setCellFactory(listView -> new LogEntryCell());

        section.getChildren().addAll(logTitle, logListView);
        return section;
    }

    /**
     * 创建按钮区域
     */
    private HBox createButtonSection() {
        HBox buttonSection = new HBox(12);
        buttonSection.getStyleClass().add("button-section");
        buttonSection.setPadding(new Insets(16, 24, 20, 24));
        buttonSection.setAlignment(Pos.CENTER_RIGHT);

        // 详细信息切换按钮
        detailsButton = new Button("显示详情");
        detailsButton.getStyleClass().addAll("details-button", "secondary");
        detailsButton.setOnAction(e -> toggleDetails());

        // 取消按钮
        cancelButton = new Button("取消");
        cancelButton.getStyleClass().addAll("cancel-button", "danger");
        cancelButton.setOnAction(e -> requestCancel());

        buttonSection.getChildren().addAll(detailsButton, cancelButton);
        return buttonSection;
    }

    /**
     * 切换详细信息显示
     */
    private void toggleDetails() {
        VBox detailsSection = (VBox) ((VBox) progressStage.getScene().getRoot())
            .getChildren().get(3);

        detailsVisible = !detailsVisible;
        detailsSection.setVisible(detailsVisible);
        detailsSection.setManaged(detailsVisible);

        detailsButton.setText(detailsVisible ? "隐藏详情" : "显示详情");

        // 调整窗口大小
        Platform.runLater(() -> {
            progressStage.sizeToScene();
        });
    }

    /**
     * 请求取消操作
     */
    private void requestCancel() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("确认取消");
        confirmation.setHeaderText("取消操作");
        confirmation.setContentText("确定要取消当前操作吗？已处理的数据可能会丢失。");

        confirmation.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                cancelled.set(true);
                addLogEntry(LogLevel.WARNING, "用户取消操作");
                progressStage.close();
            }
        });
    }

    // ========== 公共API ==========

    /**
     * 显示进度监控器
     */
    public void show() {
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;
        progressStage.show();

        // 添加初始日志
        addLogEntry(LogLevel.INFO, "开始执行操作");
    }

    /**
     * 添加进度阶段
     */
    public void addStage(String name, double weight) {
        ProgressStage stage = new ProgressStage(name, weight);
        stages.add(stage);
        updateStagesDisplay();
    }

    /**
     * 更新整体进度
     */
    public void updateProgress(double progress, String operation, String status) {
        Platform.runLater(() -> {
            overallProgress.set(Math.max(0, Math.min(1, progress)));
            currentOperation.set(operation);
            statusMessage.set(status);

            updatePerformanceMetrics();
            addLogEntry(LogLevel.INFO, String.format("%s - %.1f%%", status, progress * 100));
        });
    }

    /**
     * 更新阶段进度
     */
    public void updateStageProgress(int stageIndex, double progress) {
        if (stageIndex >= 0 && stageIndex < stages.size()) {
            stages.get(stageIndex).setProgress(progress);
            updateStagesDisplay();
        }
    }

    /**
     * 设置总项目数
     */
    public void setTotalItems(int total) {
        this.totalItems = total;
    }

    /**
     * 更新已处理项目数
     */
    public void updateProcessedItems(int processed) {
        this.processedItems = processed;
        updatePerformanceMetrics();
    }

    /**
     * 添加日志条目
     */
    public void addLogEntry(LogLevel level, String message) {
        Platform.runLater(() -> {
            LogEntry entry = new LogEntry(level, message, LocalDateTime.now());
            logEntries.add(entry);

            // 保持日志列表在合理大小
            if (logEntries.size() > 100) {
                logEntries.remove(0);
            }

            // 自动滚动到最新条目
            if (logListView != null) {
                logListView.scrollTo(logEntries.size() - 1);
            }
        });
    }

    /**
     * 完成操作
     */
    public void complete(String message) {
        Platform.runLater(() -> {
            completed.set(true);
            overallProgress.set(1.0);
            statusMessage.set(message);
            addLogEntry(LogLevel.SUCCESS, message);

            // 更新按钮
            cancelButton.setText("关闭");
            cancelButton.getStyleClass().removeAll("danger");
            cancelButton.getStyleClass().add("primary");

            // 添加完成动画
            playCompletionAnimation();
        });
    }

    /**
     * 操作失败
     */
    public void fail(String errorMessage) {
        Platform.runLater(() -> {
            statusMessage.set("操作失败: " + errorMessage);
            addLogEntry(LogLevel.ERROR, errorMessage);

            // 更新UI状态
            mainProgressBar.getStyleClass().add("error");
            cancelButton.setText("关闭");
        });
    }

    /**
     * 检查是否被取消
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 关闭监控器
     */
    public void close() {
        Platform.runLater(() -> {
            if (progressStage != null) {
                progressStage.close();
            }
        });
    }

    // ========== 私有方法 ==========

    /**
     * 更新性能指标
     */
    private void updatePerformanceMetrics() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        if (processedItems > 0 && totalItems > 0) {
            // 计算处理速度
            double itemsPerSecond = (double) processedItems / (elapsedTime / 1000.0);
            speedLabel.setText(String.format("速度: %.1f 项/秒", itemsPerSecond));

            // 估算剩余时间
            int remainingItems = totalItems - processedItems;
            if (itemsPerSecond > 0) {
                long remainingSeconds = (long) (remainingItems / itemsPerSecond);
                timeLabel.setText("估计剩余: " + formatDuration(remainingSeconds));
            }
        }

        lastUpdateTime = currentTime;
    }

    /**
     * 格式化持续时间
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分" + (seconds % 60) + "秒";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "时" + minutes + "分";
        }
    }

    /**
     * 更新阶段显示
     */
    private void updateStagesDisplay() {
        Platform.runLater(() -> {
            stagesContainer.getChildren().clear();

            for (int i = 0; i < stages.size(); i++) {
                ProgressStage stage = stages.get(i);
                HBox stageRow = createStageRow(stage, i);
                stagesContainer.getChildren().add(stageRow);
            }
        });
    }

    /**
     * 创建阶段行
     */
    private HBox createStageRow(ProgressStage stage, int index) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stage-row");

        // 阶段图标
        String iconText = stage.isCompleted() ? "[完成]" : stage.isActive() ? "[进行中]" : "[等待]";
        Label icon = new Label(iconText);
        icon.getStyleClass().add("stage-icon");

        // 阶段名称
        Label nameLabel = new Label(stage.getName());
        nameLabel.getStyleClass().add("stage-name");

        // 阶段进度条
        ProgressBar stageProgress = new ProgressBar(stage.getProgress());
        stageProgress.getStyleClass().add("stage-progress");
        stageProgress.setPrefWidth(200);

        // 阶段百分比
        Label percentLabel = new Label(String.format("%.0f%%", stage.getProgress() * 100));
        percentLabel.getStyleClass().add("stage-percent");

        row.getChildren().addAll(icon, nameLabel, stageProgress, percentLabel);
        return row;
    }

    /**
     * 播放完成动画
     */
    private void playCompletionAnimation() {
        // 进度条闪烁动画
        FadeTransition fade = new FadeTransition(Duration.millis(500), mainProgressBar);
        fade.setFromValue(1.0);
        fade.setToValue(0.7);
        fade.setCycleCount(3);
        fade.setAutoReverse(true);
        fade.play();
    }

    // ========== 内部类 ==========

    /**
     * 进度阶段
     */
    public static class ProgressStage {
        private final String name;
        private final double weight;
        private double progress = 0.0;

        public ProgressStage(String name, double weight) {
            this.name = name;
            this.weight = weight;
        }

        public String getName() { return name; }
        public double getWeight() { return weight; }
        public double getProgress() { return progress; }
        public void setProgress(double progress) { this.progress = Math.max(0, Math.min(1, progress)); }
        public boolean isCompleted() { return progress >= 1.0; }
        public boolean isActive() { return progress > 0 && progress < 1.0; }
    }

    /**
     * 日志级别
     */
    public enum LogLevel {
        INFO("[信息]", "log-info"),
        SUCCESS("[成功]", "log-success"),
        WARNING("[警告]", "log-warning"),
        ERROR("[错误]", "log-error");

        private final String icon;
        private final String styleClass;

        LogLevel(String icon, String styleClass) {
            this.icon = icon;
            this.styleClass = styleClass;
        }

        public String getIcon() { return icon; }
        public String getStyleClass() { return styleClass; }
    }

    /**
     * 日志条目
     */
    public static class LogEntry {
        private final LogLevel level;
        private final String message;
        private final LocalDateTime timestamp;

        public LogEntry(LogLevel level, String message, LocalDateTime timestamp) {
            this.level = level;
            this.message = message;
            this.timestamp = timestamp;
        }

        public LogLevel getLevel() { return level; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }

        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }

    /**
     * 日志条目单元格
     */
    private static class LogEntryCell extends ListCell<LogEntry> {
        @Override
        protected void updateItem(LogEntry entry, boolean empty) {
            super.updateItem(entry, empty);

            if (empty || entry == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().removeAll("log-info", "log-success", "log-warning", "log-error");
            } else {
                setText(String.format("[%s] %s %s",
                    entry.getFormattedTimestamp(),
                    entry.getLevel().getIcon(),
                    entry.getMessage()));

                getStyleClass().removeAll("log-info", "log-success", "log-warning", "log-error");
                getStyleClass().add(entry.getLevel().getStyleClass());
            }
        }
    }
}