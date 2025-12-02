package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import red.jiuzhou.safety.DataSafetyManager;
import red.jiuzhou.safety.DataSafetyManager.BackupRecord;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 紧急数据恢复工具
 *
 * 核心功能：
 * - 快速查看所有备份
 * - 批量恢复到指定时间点
 * - 对比恢复前后差异
 * - 紧急一键回滚
 * - 恢复验证
 *
 * 使用场景：
 * - 操作失败需要恢复
 * - 发现数据异常
 * - 服务器无法启动
 * - 紧急回滚到稳定版本
 */
@Slf4j
public class EmergencyRecoveryDialog extends Stage {

    private final DataSafetyManager safetyManager;
    private final ObservableList<BackupItem> backupItems = FXCollections.observableArrayList();

    private ComboBox<String> recoveryModeCombo;
    private TextField timePointField;
    private DatePicker datePicker;
    private Spinner<Integer> hourSpinner;
    private Spinner<Integer> minuteSpinner;
    private TableView<BackupItem> backupTable;
    private TextArea logArea;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button quickRecoveryBtn;
    private Button advancedRecoveryBtn;

    @Data
    public static class BackupItem {
        private SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
        private SimpleStringProperty fileName = new SimpleStringProperty();
        private SimpleStringProperty backupTime = new SimpleStringProperty();
        private SimpleStringProperty fileSize = new SimpleStringProperty();
        private SimpleStringProperty status = new SimpleStringProperty("就绪");
        private BackupRecord backupRecord;

        public BackupItem(BackupRecord record) {
            this.backupRecord = record;
            this.fileName.set(new File(record.getOriginalPath()).getName());
            this.backupTime.set(record.getBackupTime().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            this.fileSize.set(formatFileSize(record.getFileSize()));
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    public EmergencyRecoveryDialog(Stage owner) throws Exception {
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("🚨 紧急数据恢复");
        setResizable(true);

        safetyManager = new DataSafetyManager();
        initUI();
        loadAllBackups();
    }

    private void initUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 警告横幅
        HBox warningBanner = createWarningBanner();

        // 恢复模式选择
        VBox modeBox = createModeSelectionBox();

        // 备份列表
        VBox backupListBox = createBackupListBox();

        // 操作控制
        HBox controlBox = createControlBox();

        // 日志区域
        VBox logBox = createLogBox();

        // 状态栏
        HBox statusBar = createStatusBar();

        root.getChildren().addAll(warningBanner, modeBox, backupListBox,
                                 controlBox, logBox, statusBar);

        Scene scene = new Scene(root, 1000, 750);
        setScene(scene);
    }

    private HBox createWarningBanner() {
        HBox banner = new HBox(15);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(15));
        banner.setStyle("-fx-background-color: #dc3545; -fx-border-radius: 5;");

        Label icon = new Label("🚨");
        icon.setFont(Font.font("Arial", 36));

        VBox textBox = new VBox(5);
        Label title = new Label("紧急恢复模式");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("此工具用于紧急情况下快速恢复数据，请谨慎操作");
        subtitle.setFont(Font.font("Arial", 13));
        subtitle.setTextFill(Color.web("#ffe6e6"));

        textBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button helpButton = new Button("?");
        helpButton.setStyle("-fx-background-color: white; -fx-text-fill: #dc3545; " +
                          "-fx-font-weight: bold; -fx-font-size: 16; " +
                          "-fx-background-radius: 50%;");
        helpButton.setTooltip(new Tooltip(
            "紧急恢复使用指南:\n" +
            "1. 快速恢复: 恢复最近的备份\n" +
            "2. 时间点恢复: 恢复到指定时间\n" +
            "3. 选择性恢复: 选择特定文件恢复"));

        banner.getChildren().addAll(icon, textBox, spacer, helpButton);
        return banner;
    }

    private VBox createModeSelectionBox() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5;");

        Label title = new Label("恢复模式");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        HBox modeRow = new HBox(10);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        Label modeLabel = new Label("选择模式:");
        recoveryModeCombo = new ComboBox<>();
        recoveryModeCombo.getItems().addAll(
            "快速恢复（最近备份）",
            "时间点恢复",
            "选择性恢复"
        );
        recoveryModeCombo.setValue("快速恢复（最近备份）");
        recoveryModeCombo.setPrefWidth(200);

        modeRow.getChildren().addAll(modeLabel, recoveryModeCombo);

        // 时间点选择器
        HBox timeBox = createTimeSelectionBox();
        timeBox.setManaged(false);
        timeBox.setVisible(false);

        box.getChildren().addAll(title, modeRow, timeBox);

        // 监听模式变化
        recoveryModeCombo.setOnAction(e -> {
            String mode = recoveryModeCombo.getValue();
            timeBox.setManaged(mode.equals("时间点恢复"));
            timeBox.setVisible(mode.equals("时间点恢复"));

            if (mode.equals("快速恢复（最近备份）")) {
                loadRecentBackups();
            } else if (mode.equals("时间点恢复")) {
                // 将在用户输入时间点后加载
            } else {
                loadAllBackups();
            }
        });

        return box;
    }

    private HBox createTimeSelectionBox() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 0, 0, 30));

        Label label = new Label("恢复到:");

        datePicker = new DatePicker(java.time.LocalDate.now());
        datePicker.setPrefWidth(150);

        hourSpinner = new Spinner<>(0, 23, LocalDateTime.now().getHour());
        hourSpinner.setPrefWidth(70);
        hourSpinner.setEditable(true);

        Label colonLabel1 = new Label(":");

        minuteSpinner = new Spinner<>(0, 59, LocalDateTime.now().getMinute());
        minuteSpinner.setPrefWidth(70);
        minuteSpinner.setEditable(true);

        Button loadButton = new Button("加载该时间点的备份");
        loadButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white;");
        loadButton.setOnAction(e -> loadBackupsBeforeTimePoint());

        box.getChildren().addAll(label, datePicker, hourSpinner,
                                colonLabel1, minuteSpinner, loadButton);
        return box;
    }

    private VBox createBackupListBox() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5;");

        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("可用备份列表");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label("共 0 个备份");
        countLabel.setStyle("-fx-text-fill: #666;");

        headerBox.getChildren().addAll(title, spacer, countLabel);

        // 工具栏
        HBox toolbar = new HBox(10);
        Button selectAllBtn = new Button("全选");
        Button deselectAllBtn = new Button("取消全选");
        Button refreshBtn = new Button("刷新");
        Button compareBtn = new Button("对比选中");

        toolbar.getChildren().addAll(selectAllBtn, deselectAllBtn, refreshBtn,
                                    new Separator(), compareBtn);

        // 备份表格
        backupTable = createBackupTable();

        box.getChildren().addAll(headerBox, toolbar, backupTable);

        // 事件处理
        selectAllBtn.setOnAction(e ->
            backupItems.forEach(item -> item.getSelected().set(true)));
        deselectAllBtn.setOnAction(e ->
            backupItems.forEach(item -> item.getSelected().set(false)));
        refreshBtn.setOnAction(e -> loadAllBackups());
        compareBtn.setOnAction(e -> compareSelected());

        backupItems.addListener((javafx.collections.ListChangeListener<BackupItem>) c ->
            countLabel.setText("共 " + backupItems.size() + " 个备份"));

        return box;
    }

    private TableView<BackupItem> createBackupTable() {
        TableView<BackupItem> table = new TableView<>();
        table.setItems(backupItems);
        table.setPrefHeight(300);
        table.setEditable(true);

        TableColumn<BackupItem, Boolean> selectCol = new TableColumn<>("选择");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().getSelected());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);

        TableColumn<BackupItem, String> fileCol = new TableColumn<>("文件名");
        fileCol.setCellValueFactory(cellData -> cellData.getValue().getFileName());
        fileCol.setPrefWidth(250);

        TableColumn<BackupItem, String> timeCol = new TableColumn<>("备份时间");
        timeCol.setCellValueFactory(cellData -> cellData.getValue().getBackupTime());
        timeCol.setPrefWidth(180);

        TableColumn<BackupItem, String> sizeCol = new TableColumn<>("文件大小");
        sizeCol.setCellValueFactory(cellData -> cellData.getValue().getFileSize());
        sizeCol.setPrefWidth(100);

        TableColumn<BackupItem, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().getStatus());
        statusCol.setPrefWidth(150);

        table.getColumns().addAll(selectCol, fileCol, timeCol, sizeCol, statusCol);

        return table;
    }

    private HBox createControlBox() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(15));

        quickRecoveryBtn = new Button("⚡ 快速恢复");
        quickRecoveryBtn.setPrefWidth(150);
        quickRecoveryBtn.setPrefHeight(40);
        quickRecoveryBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; " +
                                 "-fx-font-size: 14; -fx-font-weight: bold;");
        quickRecoveryBtn.setOnAction(e -> performQuickRecovery());

        advancedRecoveryBtn = new Button("🔧 高级恢复");
        advancedRecoveryBtn.setPrefWidth(150);
        advancedRecoveryBtn.setPrefHeight(40);
        advancedRecoveryBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; " +
                                    "-fx-font-size: 14; -fx-font-weight: bold;");
        advancedRecoveryBtn.setOnAction(e -> performAdvancedRecovery());

        Button verifyBtn = new Button("✓ 验证备份");
        verifyBtn.setPrefWidth(150);
        verifyBtn.setPrefHeight(40);
        verifyBtn.setStyle("-fx-font-size: 14;");
        verifyBtn.setOnAction(e -> verifyBackups());

        Button closeBtn = new Button("关闭");
        closeBtn.setPrefWidth(100);
        closeBtn.setPrefHeight(40);
        closeBtn.setStyle("-fx-font-size: 14;");
        closeBtn.setOnAction(e -> close());

        box.getChildren().addAll(quickRecoveryBtn, advancedRecoveryBtn,
                                verifyBtn, closeBtn);
        return box;
    }

    private VBox createLogBox() {
        VBox box = new VBox(5);

        Label title = new Label("恢复日志");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        logArea = new TextArea();
        logArea.setPrefHeight(150);
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11;");

        box.getChildren().addAll(title, logArea);
        return box;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(5));
        bar.setStyle("-fx-background-color: #e0e0e0;");

        statusLabel = new Label("就绪");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        bar.getChildren().addAll(statusLabel, progressBar);
        return bar;
    }

    private void loadAllBackups() {
        log("正在加载所有可用备份...");

        Task<List<BackupItem>> task = new Task<List<BackupItem>>() {
            @Override
            protected List<BackupItem> call() throws Exception {
                List<BackupItem> items = new ArrayList<>();

                // 扫描backup目录
                File backupDir = new File("backup");
                if (!backupDir.exists()) {
                    return items;
                }

                // 获取所有备份子目录
                File[] subdirs = backupDir.listFiles(File::isDirectory);
                if (subdirs != null) {
                    for (File subdir : subdirs) {
                        String fileName = subdir.getName().replace("_backups", "");

                        try {
                            List<BackupRecord> records = safetyManager.getBackupHistory(fileName);
                            items.addAll(records.stream()
                                .map(BackupItem::new)
                                .collect(Collectors.toList()));
                        } catch (Exception e) {
                            log.error("加载备份失败: " + fileName, e);
                        }
                    }
                }

                return items;
            }

            @Override
            protected void succeeded() {
                List<BackupItem> items = getValue();
                Platform.runLater(() -> {
                    backupItems.clear();
                    backupItems.addAll(items);
                    log("加载完成，找到 " + items.size() + " 个备份");
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    log("加载备份失败: " + getException().getMessage());
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void loadRecentBackups() {
        log("加载最近的备份...");
        // 实现加载最近24小时的备份
        loadAllBackups(); // 简化实现
    }

    private void loadBackupsBeforeTimePoint() {
        LocalDateTime timePoint = LocalDateTime.of(
            datePicker.getValue(),
            java.time.LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue())
        );

        log("加载时间点之前的备份: " + timePoint);

        // 过滤备份列表
        backupItems.removeIf(item ->
            item.getBackupRecord().getBackupTime().isAfter(timePoint));
    }

    private void performQuickRecovery() {
        List<BackupItem> selected = backupItems.stream()
            .filter(item -> item.getSelected().get())
            .collect(Collectors.toList());

        if (selected.isEmpty()) {
            showAlert("请选择要恢复的备份", Alert.AlertType.WARNING);
            return;
        }

        // 确认对话框
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认恢复");
        confirm.setHeaderText("即将恢复 " + selected.size() + " 个文件");
        confirm.setContentText("此操作将覆盖当前文件，是否继续？");

        if (confirm.showAndWait().get() != ButtonType.OK) {
            return;
        }

        performRecovery(selected);
    }

    private void performAdvancedRecovery() {
        // 打开高级恢复对话框
        showAlert("高级恢复功能：\n" +
                "- 对比多个版本\n" +
                "- 选择性合并\n" +
                "- 冲突解决\n" +
                "敬请期待！", Alert.AlertType.INFORMATION);
    }

    private void performRecovery(List<BackupItem> items) {
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("正在恢复...");

        Task<Map<String, Boolean>> task = new Task<Map<String, Boolean>>() {
            @Override
            protected Map<String, Boolean> call() throws Exception {
                Map<String, Boolean> results = new HashMap<>();
                int current = 0;

                for (BackupItem item : items) {
                    BackupRecord record = item.getBackupRecord();

                    try {
                        updateMessage("正在恢复: " + item.getFileName().get());

                        // 恢复文件
                        safetyManager.restoreFromBackup(
                            record.getOriginalPath(),
                            record.getBackupTime().format(
                                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                            )
                        );

                        results.put(item.getFileName().get(), true);

                        Platform.runLater(() ->
                            item.getStatus().set("✓ 已恢复"));

                    } catch (Exception e) {
                        results.put(item.getFileName().get(), false);

                        Platform.runLater(() ->
                            item.getStatus().set("✗ 失败: " + e.getMessage()));

                        log.error("恢复失败: " + item.getFileName().get(), e);
                    }

                    current++;
                    updateProgress(current, items.size());
                }

                return results;
            }

            @Override
            protected void succeeded() {
                Map<String, Boolean> results = getValue();
                long successCount = results.values().stream()
                    .filter(Boolean::booleanValue).count();

                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText(String.format("恢复完成: %d/%d 成功",
                                                     successCount, results.size()));

                    log(String.format("\n恢复操作完成！成功: %d, 失败: %d",
                                    successCount, results.size() - successCount));

                    if (successCount == results.size()) {
                        showAlert("所有文件恢复成功！", Alert.AlertType.INFORMATION);
                    } else {
                        showAlert(String.format("部分文件恢复失败\n成功: %d\n失败: %d",
                                              successCount, results.size() - successCount),
                                Alert.AlertType.WARNING);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("恢复失败");
                    Throwable ex = getException();
                    log.error("Recovery failed", ex);
                    showAlert("恢复失败: " + ex.getMessage(), Alert.AlertType.ERROR);
                });
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void verifyBackups() {
        log("开始验证备份完整性...");

        List<BackupItem> selected = backupItems.stream()
            .filter(item -> item.getSelected().get())
            .collect(Collectors.toList());

        if (selected.isEmpty()) {
            showAlert("请选择要验证的备份", Alert.AlertType.WARNING);
            return;
        }

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (BackupItem item : selected) {
                    try {
                        BackupRecord record = item.getBackupRecord();
                        byte[] content = Files.readAllBytes(
                            Paths.get(record.getBackupPath()));

                        // 验证完整性
                        DataSafetyManager.IntegrityCheckResult result =
                            new DataSafetyManager().validateXmlIntegrity(content);

                        if (result.isValid()) {
                            Platform.runLater(() ->
                                item.getStatus().set("✓ 验证通过"));
                        } else {
                            Platform.runLater(() ->
                                item.getStatus().set("✗ 验证失败"));
                        }

                    } catch (Exception e) {
                        Platform.runLater(() ->
                            item.getStatus().set("✗ 错误: " + e.getMessage()));
                    }
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    log("验证完成");
                    showAlert("备份验证完成", Alert.AlertType.INFORMATION);
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void compareSelected() {
        List<BackupItem> selected = backupItems.stream()
            .filter(item -> item.getSelected().get())
            .collect(Collectors.toList());

        if (selected.size() < 2) {
            showAlert("请至少选择2个备份进行对比", Alert.AlertType.WARNING);
            return;
        }

        log("对比功能开发中...");
        showAlert("对比功能：\n" +
                "将显示选中备份之间的差异\n" +
                "敬请期待！", Alert.AlertType.INFORMATION);
    }

    private void log(String message) {
        Platform.runLater(() -> {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            String timestamp = LocalDateTime.now().format(formatter);
            logArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "错误" : "提示");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 快速显示紧急恢复对话框
     */
    public static void showRecovery(Stage owner) {
        try {
            EmergencyRecoveryDialog dialog = new EmergencyRecoveryDialog(owner);
            dialog.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("启动失败");
            alert.setHeaderText("无法启动紧急恢复工具");
            alert.setContentText("错误信息: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @Override
    public void close() {
        safetyManager.shutdown();
        super.close();
    }
}