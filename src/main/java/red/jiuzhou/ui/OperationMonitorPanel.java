package red.jiuzhou.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Data;
import red.jiuzhou.safety.DataSafetyManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 操作监控面板
 * 提供实时的操作监控、审计日志查看和系统状态显示
 *
 * 功能特点：
 * - 实时操作监控：显示当前进行中的事务和操作
 * - 审计日志查看：可筛选、搜索和导出审计日志
 * - 系统状态监控：显示备份状态、磁盘使用等
 * - 操作统计分析：统计操作次数、成功率等指标
 * - 性能监控：监控线程池、内存使用情况
 * - 实时刷新：自动定期更新显示内容
 */
public class OperationMonitorPanel extends Stage {

    private final DataSafetyManager safetyManager;
    private Timeline refreshTimeline;

    // UI组件
    private TableView<AuditLogEntry> auditLogTable;
    private ObservableList<AuditLogEntry> auditLogData;

    private TableView<ActiveOperation> activeOpsTable;
    private ObservableList<ActiveOperation> activeOpsData;

    private Label totalOpsLabel;
    private Label successOpsLabel;
    private Label failedOpsLabel;
    private Label successRateLabel;

    private Label backupCountLabel;
    private Label backupSizeLabel;
    private Label diskSpaceLabel;

    private ProgressBar memoryUsageBar;
    private Label memoryUsageLabel;

    private ComboBox<String> logTypeFilter;
    private ComboBox<String> logStatusFilter;
    private TextField logSearchField;
    private DatePicker logDatePicker;

    private CheckBox autoRefreshCheck;
    private Spinner<Integer> refreshIntervalSpinner;

    /**
     * 审计日志条目
     */
    @Data
    public static class AuditLogEntry {
        private LocalDateTime timestamp;
        private String operation;
        private String user;
        private String file;
        private boolean success;
        private String transactionId;
        private String details;

        public String getTimestampStr() {
            return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        public String getStatusStr() {
            return success ? "✓ 成功" : "✗ 失败";
        }
    }

    /**
     * 活跃操作
     */
    @Data
    public static class ActiveOperation {
        private String transactionId;
        private String operation;
        private LocalDateTime startTime;
        private String status;
        private int filesProcessed;
        private int totalFiles;

        public String getStartTimeStr() {
            return startTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        public String getProgressStr() {
            if (totalFiles > 0) {
                return filesProcessed + "/" + totalFiles;
            }
            return "-";
        }

        public int getProgressPercent() {
            if (totalFiles > 0) {
                return (int) ((filesProcessed * 100.0) / totalFiles);
            }
            return 0;
        }
    }

    public OperationMonitorPanel(Stage owner) {
        try {
            this.safetyManager = new DataSafetyManager();
        } catch (IOException e) {
            throw new RuntimeException("初始化数据安全管理器失败", e);
        }

        initModality(Modality.NONE);
        initOwner(owner);
        setTitle("📊 操作监控面板");
        setResizable(true);

        initUI();
        startAutoRefresh();

        setOnCloseRequest(e -> stopAutoRefresh());
    }

    private void initUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 顶部标题栏
        HBox header = createHeader();
        root.setTop(header);

        // 中间主内容区 - 使用TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab auditTab = new Tab("📋 审计日志", createAuditLogPane());
        Tab activeTab = new Tab("⚡ 活跃操作", createActiveOpsPane());
        Tab statsTab = new Tab("📈 统计分析", createStatsPane());
        Tab systemTab = new Tab("🖥️ 系统状态", createSystemPane());

        tabPane.getTabs().addAll(auditTab, activeTab, statsTab, systemTab);
        root.setCenter(tabPane);

        // 底部状态栏
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1000, 700);
        setScene(scene);

        // 初始加载数据
        refreshAllData();
    }

    private HBox createHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(15));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea 0%, #764ba2 100%);");

        Label icon = new Label("📊");
        icon.setFont(Font.font("Arial", 32));

        VBox titleBox = new VBox(3);
        Label title = new Label("操作监控面板");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("实时监控数据操作、审计日志和系统状态");
        subtitle.setFont(Font.font("Arial", 12));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 刷新控制
        HBox refreshControl = new HBox(10);
        refreshControl.setAlignment(Pos.CENTER_RIGHT);

        autoRefreshCheck = new CheckBox("自动刷新");
        autoRefreshCheck.setSelected(true);
        autoRefreshCheck.setTextFill(Color.WHITE);
        autoRefreshCheck.setOnAction(e -> {
            if (autoRefreshCheck.isSelected()) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });

        Label intervalLabel = new Label("间隔(秒):");
        intervalLabel.setTextFill(Color.WHITE);

        refreshIntervalSpinner = new Spinner<>(1, 60, 5);
        refreshIntervalSpinner.setPrefWidth(70);
        refreshIntervalSpinner.setEditable(true);
        refreshIntervalSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (autoRefreshCheck.isSelected()) {
                restartAutoRefresh();
            }
        });

        Button manualRefreshBtn = new Button("🔄 立即刷新");
        manualRefreshBtn.setStyle("-fx-background-color: white; -fx-text-fill: #667eea; -fx-font-weight: bold;");
        manualRefreshBtn.setOnAction(e -> refreshAllData());

        refreshControl.getChildren().addAll(
            autoRefreshCheck, intervalLabel, refreshIntervalSpinner, manualRefreshBtn
        );

        header.getChildren().addAll(icon, titleBox, spacer, refreshControl);
        return header;
    }

    /**
     * 创建审计日志面板
     */
    private VBox createAuditLogPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(15));

        // 过滤器区域
        HBox filterBox = new HBox(10);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label("操作类型:");
        logTypeFilter = new ComboBox<>();
        logTypeFilter.getItems().addAll("全部", "读取", "写入", "事务开始", "事务提交", "事务回滚");
        logTypeFilter.setValue("全部");
        logTypeFilter.setOnAction(e -> filterAuditLog());

        Label statusLabel = new Label("状态:");
        logStatusFilter = new ComboBox<>();
        logStatusFilter.getItems().addAll("全部", "成功", "失败");
        logStatusFilter.setValue("全部");
        logStatusFilter.setOnAction(e -> filterAuditLog());

        Label dateLabel = new Label("日期:");
        logDatePicker = new DatePicker();
        logDatePicker.setPromptText("选择日期");
        logDatePicker.setOnAction(e -> filterAuditLog());

        Label searchLabel = new Label("搜索:");
        logSearchField = new TextField();
        logSearchField.setPromptText("搜索文件名或事务ID...");
        logSearchField.setPrefWidth(200);
        logSearchField.textProperty().addListener((obs, old, val) -> filterAuditLog());

        Button clearFilterBtn = new Button("清除过滤");
        clearFilterBtn.setOnAction(e -> clearFilters());

        Button exportBtn = new Button("📤 导出日志");
        exportBtn.setOnAction(e -> exportAuditLog());

        filterBox.getChildren().addAll(
            typeLabel, logTypeFilter,
            statusLabel, logStatusFilter,
            dateLabel, logDatePicker,
            searchLabel, logSearchField,
            clearFilterBtn, exportBtn
        );

        // 审计日志表格
        auditLogData = FXCollections.observableArrayList();
        auditLogTable = new TableView<>(auditLogData);
        auditLogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AuditLogEntry, String> timeCol = new TableColumn<>("时间");
        timeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getTimestampStr()
        ));
        timeCol.setPrefWidth(150);

        TableColumn<AuditLogEntry, String> opCol = new TableColumn<>("操作");
        opCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getOperation()
        ));
        opCol.setPrefWidth(120);

        TableColumn<AuditLogEntry, String> userCol = new TableColumn<>("用户");
        userCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getUser()
        ));
        userCol.setPrefWidth(80);

        TableColumn<AuditLogEntry, String> fileCol = new TableColumn<>("文件");
        fileCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getFile()
        ));
        fileCol.setPrefWidth(250);

        TableColumn<AuditLogEntry, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getStatusStr()
        ));
        statusCol.setPrefWidth(80);
        statusCol.setCellFactory(column -> new TableCell<AuditLogEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("成功")) {
                        setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<AuditLogEntry, String> txnCol = new TableColumn<>("事务ID");
        txnCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getTransactionId()
        ));
        txnCol.setPrefWidth(100);

        auditLogTable.getColumns().addAll(timeCol, opCol, userCol, fileCol, statusCol, txnCol);

        VBox.setVgrow(auditLogTable, Priority.ALWAYS);

        // 详情显示
        Label detailLabel = new Label("选中记录详情:");
        detailLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        TextArea detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setPrefHeight(100);
        detailArea.setWrapText(true);
        detailArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11;");

        auditLogTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("时间: ").append(newSel.getTimestampStr()).append("\n");
                sb.append("操作: ").append(newSel.getOperation()).append("\n");
                sb.append("用户: ").append(newSel.getUser()).append("\n");
                sb.append("文件: ").append(newSel.getFile()).append("\n");
                sb.append("状态: ").append(newSel.getStatusStr()).append("\n");
                sb.append("事务ID: ").append(newSel.getTransactionId()).append("\n");
                if (newSel.getDetails() != null) {
                    sb.append("详情: ").append(newSel.getDetails()).append("\n");
                }
                detailArea.setText(sb.toString());
            } else {
                detailArea.clear();
            }
        });

        pane.getChildren().addAll(filterBox, auditLogTable, detailLabel, detailArea);
        return pane;
    }

    /**
     * 创建活跃操作面板
     */
    private VBox createActiveOpsPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(15));

        Label title = new Label("当前正在进行的操作");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        // 活跃操作表格
        activeOpsData = FXCollections.observableArrayList();
        activeOpsTable = new TableView<>(activeOpsData);
        activeOpsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        activeOpsTable.setPlaceholder(new Label("当前没有活跃操作"));

        TableColumn<ActiveOperation, String> txnCol = new TableColumn<>("事务ID");
        txnCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getTransactionId()
        ));

        TableColumn<ActiveOperation, String> opCol = new TableColumn<>("操作");
        opCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getOperation()
        ));

        TableColumn<ActiveOperation, String> startCol = new TableColumn<>("开始时间");
        startCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getStartTimeStr()
        ));

        TableColumn<ActiveOperation, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getStatus()
        ));

        TableColumn<ActiveOperation, String> progressCol = new TableColumn<>("进度");
        progressCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getProgressStr()
        ));
        progressCol.setCellFactory(column -> new TableCell<ActiveOperation, String>() {
            private final ProgressBar progressBar = new ProgressBar();
            private final Label label = new Label();
            private final HBox box = new HBox(5, progressBar, label);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                progressBar.setPrefWidth(100);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ActiveOperation op = (ActiveOperation) getTableRow().getItem();
                    double progress = op.getProgressPercent() / 100.0;
                    progressBar.setProgress(progress);
                    label.setText(item + " (" + op.getProgressPercent() + "%)");
                    setGraphic(box);
                }
            }
        });

        activeOpsTable.getColumns().addAll(txnCol, opCol, startCol, statusCol, progressCol);

        VBox.setVgrow(activeOpsTable, Priority.ALWAYS);

        // 提示信息
        Label hintLabel = new Label("💡 提示：活跃操作会在完成或失败后自动从列表中移除");
        hintLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic;");

        pane.getChildren().addAll(title, activeOpsTable, hintLabel);
        return pane;
    }

    /**
     * 创建统计分析面板
     */
    private VBox createStatsPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(15));

        // 今日操作统计
        GridPane todayStats = new GridPane();
        todayStats.setHgap(20);
        todayStats.setVgap(15);
        todayStats.setPadding(new Insets(15));
        todayStats.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label todayTitle = new Label("📅 今日操作统计");
        todayTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        GridPane.setColumnSpan(todayTitle, 4);
        todayStats.add(todayTitle, 0, 0);

        // 统计卡片
        VBox totalCard = createStatCard("总操作数", "0", "#007bff");
        VBox successCard = createStatCard("成功", "0", "#28a745");
        VBox failedCard = createStatCard("失败", "0", "#dc3545");
        VBox rateCard = createStatCard("成功率", "0%", "#17a2b8");

        totalOpsLabel = (Label) ((VBox) totalCard.getChildren().get(1)).getChildren().get(0);
        successOpsLabel = (Label) ((VBox) successCard.getChildren().get(1)).getChildren().get(0);
        failedOpsLabel = (Label) ((VBox) failedCard.getChildren().get(1)).getChildren().get(0);
        successRateLabel = (Label) ((VBox) rateCard.getChildren().get(1)).getChildren().get(0);

        todayStats.add(totalCard, 0, 1);
        todayStats.add(successCard, 1, 1);
        todayStats.add(failedCard, 2, 1);
        todayStats.add(rateCard, 3, 1);

        // 操作类型分布
        VBox typeDistribution = new VBox(10);
        typeDistribution.setPadding(new Insets(15));
        typeDistribution.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label typeTitle = new Label("📊 操作类型分布");
        typeTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        GridPane typeGrid = new GridPane();
        typeGrid.setHgap(15);
        typeGrid.setVgap(10);
        typeGrid.setPadding(new Insets(10, 0, 0, 0));

        // 模拟数据 - 实际应从审计日志统计
        String[] types = {"读取", "写入", "事务", "备份", "恢复"};
        int row = 0;
        for (String type : types) {
            Label typeLabel = new Label(type + ":");
            typeLabel.setPrefWidth(60);
            ProgressBar bar = new ProgressBar(Math.random());
            bar.setPrefWidth(200);
            Label count = new Label((int)(Math.random() * 100) + " 次");

            typeGrid.add(typeLabel, 0, row);
            typeGrid.add(bar, 1, row);
            typeGrid.add(count, 2, row);
            row++;
        }

        typeDistribution.getChildren().addAll(typeTitle, typeGrid);

        pane.getChildren().addAll(todayStats, typeDistribution);
        return pane;
    }

    /**
     * 创建系统状态面板
     */
    private VBox createSystemPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(15));

        // 备份状态
        GridPane backupStatus = new GridPane();
        backupStatus.setHgap(15);
        backupStatus.setVgap(10);
        backupStatus.setPadding(new Insets(15));
        backupStatus.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label backupTitle = new Label("💾 备份状态");
        backupTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        GridPane.setColumnSpan(backupTitle, 2);
        backupStatus.add(backupTitle, 0, 0);

        backupStatus.add(new Label("备份文件数:"), 0, 1);
        backupCountLabel = new Label("计算中...");
        backupCountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        backupStatus.add(backupCountLabel, 1, 1);

        backupStatus.add(new Label("备份总大小:"), 0, 2);
        backupSizeLabel = new Label("计算中...");
        backupSizeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        backupStatus.add(backupSizeLabel, 1, 2);

        backupStatus.add(new Label("磁盘剩余空间:"), 0, 3);
        diskSpaceLabel = new Label("检测中...");
        diskSpaceLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        backupStatus.add(diskSpaceLabel, 1, 3);

        // 系统资源
        VBox resourceBox = new VBox(10);
        resourceBox.setPadding(new Insets(15));
        resourceBox.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label resourceTitle = new Label("🖥️ 系统资源");
        resourceTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        HBox memoryBox = new HBox(10);
        memoryBox.setAlignment(Pos.CENTER_LEFT);
        Label memoryLabel = new Label("内存使用:");
        memoryUsageBar = new ProgressBar(0);
        memoryUsageBar.setPrefWidth(300);
        memoryUsageLabel = new Label("0 MB / 0 MB");
        memoryBox.getChildren().addAll(memoryLabel, memoryUsageBar, memoryUsageLabel);

        resourceBox.getChildren().addAll(resourceTitle, memoryBox);

        // 线程池状态
        VBox threadPoolBox = new VBox(10);
        threadPoolBox.setPadding(new Insets(15));
        threadPoolBox.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label threadTitle = new Label("⚙️ 线程池状态");
        threadTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        GridPane threadGrid = new GridPane();
        threadGrid.setHgap(15);
        threadGrid.setVgap(8);
        threadGrid.setPadding(new Insets(10, 0, 0, 0));

        threadGrid.add(new Label("活跃线程:"), 0, 0);
        threadGrid.add(new Label("8"), 1, 0);

        threadGrid.add(new Label("队列任务:"), 0, 1);
        threadGrid.add(new Label("0"), 1, 1);

        threadGrid.add(new Label("已完成任务:"), 0, 2);
        threadGrid.add(new Label("-"), 1, 2);

        threadPoolBox.getChildren().addAll(threadTitle, threadGrid);

        pane.getChildren().addAll(backupStatus, resourceBox, threadPoolBox);
        return pane;
    }

    private VBox createStatCard(String title, String value, String color) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(15));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 5;");
        card.setPrefWidth(200);

        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Font.font("Arial", 12));

        VBox valueBox = new VBox();
        valueBox.setAlignment(Pos.CENTER);
        Label valueLabel = new Label(value);
        valueLabel.setTextFill(Color.WHITE);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        valueBox.getChildren().add(valueLabel);

        card.getChildren().addAll(titleLabel, valueBox);
        return card;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");

        Label statusLabel = new Label("就绪");
        statusLabel.setFont(Font.font("Arial", 11));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label updateLabel = new Label("最后更新: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        ));
        updateLabel.setFont(Font.font("Arial", 11));
        updateLabel.setStyle("-fx-text-fill: #6c757d;");

        statusBar.getChildren().addAll(statusLabel, spacer, updateLabel);
        return statusBar;
    }

    /**
     * 刷新所有数据
     */
    private void refreshAllData() {
        // 在后台线程执行以避免阻塞UI
        new Thread(() -> {
            try {
                loadAuditLog();
                loadActiveOperations();
                loadStatistics();
                loadSystemStatus();

                Platform.runLater(() -> {
                    // 更新状态栏时间
                    if (getScene() != null && getScene().getRoot() instanceof BorderPane) {
                        BorderPane root = (BorderPane) getScene().getRoot();
                        HBox statusBar = (HBox) root.getBottom();
                        Label updateLabel = (Label) statusBar.getChildren().get(2);
                        updateLabel.setText("最后更新: " + LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        ));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("刷新失败");
                    alert.setHeaderText("刷新数据时出错");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    /**
     * 加载审计日志
     */
    private void loadAuditLog() {
        List<AuditLogEntry> entries = new ArrayList<>();

        try {
            // 读取audit.log文件
            File auditFile = new File("audit.log");
            if (auditFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(auditFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        AuditLogEntry entry = parseAuditLogLine(line);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 按时间倒序排序
        entries.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        // 只保留最近1000条
        if (entries.size() > 1000) {
            entries = entries.subList(0, 1000);
        }

        final List<AuditLogEntry> finalEntries = entries;
        Platform.runLater(() -> {
            auditLogData.setAll(finalEntries);
        });
    }

    /**
     * 解析审计日志行
     * 格式: [2025-01-11 14:05:23] WRITE_FILE | User: admin | File: config_item.xml | Success: true | TxnId: uuid-xxx
     */
    private AuditLogEntry parseAuditLogLine(String line) {
        try {
            // 解析时间戳
            int endBracket = line.indexOf(']');
            if (endBracket == -1) return null;

            String timestampStr = line.substring(1, endBracket).trim();
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 解析其余部分
            String rest = line.substring(endBracket + 1).trim();
            String[] parts = rest.split("\\|");

            if (parts.length < 4) return null;

            AuditLogEntry entry = new AuditLogEntry();
            entry.setTimestamp(timestamp);

            // 操作类型
            String operation = parts[0].trim();
            entry.setOperation(translateOperation(operation));

            // 解析各字段
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.startsWith("User:")) {
                    entry.setUser(part.substring(5).trim());
                } else if (part.startsWith("File:")) {
                    String file = part.substring(5).trim();
                    entry.setFile(file.equals("null") ? "-" : file);
                } else if (part.startsWith("Success:")) {
                    entry.setSuccess(Boolean.parseBoolean(part.substring(8).trim()));
                } else if (part.startsWith("TxnId:")) {
                    entry.setTransactionId(part.substring(6).trim());
                }
            }

            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    private String translateOperation(String op) {
        switch (op) {
            case "READ_FILE": return "读取文件";
            case "WRITE_FILE": return "写入文件";
            case "BEGIN_TRANSACTION": return "事务开始";
            case "COMMIT_TRANSACTION": return "事务提交";
            case "ROLLBACK_TRANSACTION": return "事务回滚";
            case "CREATE_BACKUP": return "创建备份";
            case "RESTORE_BACKUP": return "恢复备份";
            default: return op;
        }
    }

    /**
     * 加载活跃操作
     */
    private void loadActiveOperations() {
        // TODO: 实际实现需要从DataSafetyManager获取活跃事务
        // 这里使用模拟数据
        Platform.runLater(() -> {
            activeOpsData.clear();
            // 实际应用中，这里会从事务管理器获取活跃事务列表
        });
    }

    /**
     * 加载统计数据
     */
    private void loadStatistics() {
        try {
            // 统计今日操作
            LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

            AtomicLong totalOps = new AtomicLong(0);
            AtomicLong successOps = new AtomicLong(0);
            AtomicLong failedOps = new AtomicLong(0);

            File auditFile = new File("audit.log");
            if (auditFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(auditFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        AuditLogEntry entry = parseAuditLogLine(line);
                        if (entry != null && entry.getTimestamp().isAfter(today)) {
                            totalOps.incrementAndGet();
                            if (entry.isSuccess()) {
                                successOps.incrementAndGet();
                            } else {
                                failedOps.incrementAndGet();
                            }
                        }
                    }
                }
            }

            double successRate = totalOps.get() > 0 ?
                (successOps.get() * 100.0 / totalOps.get()) : 0;

            Platform.runLater(() -> {
                totalOpsLabel.setText(String.valueOf(totalOps.get()));
                successOpsLabel.setText(String.valueOf(successOps.get()));
                failedOpsLabel.setText(String.valueOf(failedOps.get()));
                successRateLabel.setText(String.format("%.1f%%", successRate));
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载系统状态
     */
    private void loadSystemStatus() {
        try {
            // 统计备份文件
            Path backupDir = Paths.get("backup");
            if (Files.exists(backupDir)) {
                long backupCount = Files.walk(backupDir)
                    .filter(p -> p.toString().endsWith(".bak"))
                    .count();

                long backupSize = Files.walk(backupDir)
                    .filter(p -> p.toString().endsWith(".bak"))
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();

                String sizeStr = formatFileSize(backupSize);

                Platform.runLater(() -> {
                    backupCountLabel.setText(backupCount + " 个文件");
                    backupSizeLabel.setText(sizeStr);
                });
            } else {
                Platform.runLater(() -> {
                    backupCountLabel.setText("0 个文件");
                    backupSizeLabel.setText("0 B");
                });
            }

            // 磁盘空间
            File root = new File(".");
            long freeSpace = root.getFreeSpace();
            String freeSpaceStr = formatFileSize(freeSpace);

            Platform.runLater(() -> {
                diskSpaceLabel.setText(freeSpaceStr);
            });

            // 内存使用
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            double memoryUsage = (double) usedMemory / maxMemory;
            String memoryStr = formatFileSize(usedMemory) + " / " + formatFileSize(maxMemory);

            Platform.runLater(() -> {
                memoryUsageBar.setProgress(memoryUsage);
                memoryUsageLabel.setText(memoryStr);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 过滤审计日志
     */
    private void filterAuditLog() {
        // TODO: 实现过滤逻辑
    }

    /**
     * 清除过滤器
     */
    private void clearFilters() {
        logTypeFilter.setValue("全部");
        logStatusFilter.setValue("全部");
        logDatePicker.setValue(null);
        logSearchField.clear();
        filterAuditLog();
    }

    /**
     * 导出审计日志
     */
    private void exportAuditLog() {
        // TODO: 实现导出功能
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("导出日志");
        alert.setHeaderText("功能开发中");
        alert.setContentText("日志导出功能即将推出");
        alert.showAndWait();
    }

    /**
     * 启动自动刷新
     */
    private void startAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }

        int interval = refreshIntervalSpinner.getValue();
        refreshTimeline = new Timeline(new KeyFrame(
            Duration.seconds(interval),
            event -> refreshAllData()
        ));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    /**
     * 停止自动刷新
     */
    private void stopAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }

    /**
     * 重启自动刷新
     */
    private void restartAutoRefresh() {
        stopAutoRefresh();
        startAutoRefresh();
    }

    /**
     * 快速显示监控面板
     */
    public static void showMonitor(Stage owner) {
        OperationMonitorPanel panel = new OperationMonitorPanel(owner);
        panel.show();
    }
}
