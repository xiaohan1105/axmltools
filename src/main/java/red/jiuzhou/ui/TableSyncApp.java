package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import red.jiuzhou.sync.TableSyncService;
import red.jiuzhou.tabmapping.MappingLoader;
import red.jiuzhou.tabmapping.TableMapping;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据表同步工具界面
 *
 * 功能：
 * - 可视化选择要同步的表
 * - 配置同步方向和冲突解决策略
 * - 实时显示同步进度
 * - 展示同步结果和错误信息
 */
public class TableSyncApp {

    private Stage primaryStage;
    private TableSyncService syncService;

    // UI组件
    private CheckComboBox<String> tableCheckComboBox;
    private ComboBox<TableSyncService.SyncDirection> directionCombo;
    private ComboBox<TableSyncService.ConflictResolution> conflictCombo;
    private ProgressBar progressBar;
    private Label progressLabel;
    private TextArea resultArea;
    private Button startSyncButton;
    private Button stopSyncButton;

    // 数据
    private Task<List<TableSyncService.SyncResult>> currentSyncTask;

    public TableSyncApp(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.syncService = new TableSyncService();
        syncService.setProgressListener(new SyncProgressListener());
    }

    /**
     * 显示同步工具窗口
     */
    public void show() {
        Stage stage = new Stage();
        stage.setTitle("数据表同步工具");
        stage.initOwner(primaryStage);
        stage.setWidth(800);
        stage.setHeight(600);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // 顶部配置区域
        VBox configPane = createConfigPane();
        root.setTop(configPane);

        // 中间进度区域
        VBox progressPane = createProgressPane();
        root.setCenter(progressPane);

        // 底部结果区域
        VBox resultPane = createResultPane();
        root.setBottom(resultPane);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * 创建配置面板
     */
    private VBox createConfigPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        // 标题
        Label titleLabel = new Label("同步配置");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        // 表选择区域
        HBox tableSelectionBox = new HBox(10);
        tableSelectionBox.setAlignment(Pos.CENTER_LEFT);

        Label tableLabel = new Label("选择要同步的表:");
        tableLabel.setPrefWidth(120);

        tableCheckComboBox = new CheckComboBox<>();
        loadTableNames();
        tableCheckComboBox.setPrefWidth(400);

        CheckBox selectAllCheckBox = new CheckBox("全选");
        selectAllCheckBox.setOnAction(e -> {
            if (selectAllCheckBox.isSelected()) {
                tableCheckComboBox.getCheckModel().checkAll();
            } else {
                tableCheckComboBox.getCheckModel().clearChecks();
            }
        });

        tableSelectionBox.getChildren().addAll(tableLabel, tableCheckComboBox, selectAllCheckBox);

        // 同步方向选择
        HBox directionBox = new HBox(10);
        directionBox.setAlignment(Pos.CENTER_LEFT);

        Label directionLabel = new Label("同步方向:");
        directionLabel.setPrefWidth(120);

        directionCombo = new ComboBox<>();
        directionCombo.getItems().addAll(
                TableSyncService.SyncDirection.SVR_TO_CLT,
                TableSyncService.SyncDirection.CLT_TO_SVR,
                TableSyncService.SyncDirection.BI_DIRECTIONAL
        );
        directionCombo.setValue(TableSyncService.SyncDirection.BI_DIRECTIONAL);
        directionCombo.setPrefWidth(200);

        directionBox.getChildren().addAll(directionLabel, directionCombo);

        // 冲突解决策略选择
        HBox conflictBox = new HBox(10);
        conflictBox.setAlignment(Pos.CENTER_LEFT);

        Label conflictLabel = new Label("冲突解决策略:");
        conflictLabel.setPrefWidth(120);

        conflictCombo = new ComboBox<>();
        conflictCombo.getItems().addAll(
                TableSyncService.ConflictResolution.SVR_PRIORITY,
                TableSyncService.ConflictResolution.CLT_PRIORITY,
                TableSyncService.ConflictResolution.TIMESTAMP,
                TableSyncService.ConflictResolution.MANUAL
        );
        conflictCombo.setValue(TableSyncService.ConflictResolution.SVR_PRIORITY);
        conflictCombo.setPrefWidth(200);

        conflictBox.getChildren().addAll(conflictLabel, conflictCombo);

        // 操作按钮
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        startSyncButton = new Button("开始同步");
        startSyncButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        startSyncButton.setPrefWidth(120);
        startSyncButton.setOnAction(e -> startSync());

        stopSyncButton = new Button("停止同步");
        stopSyncButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
        stopSyncButton.setPrefWidth(120);
        stopSyncButton.setDisable(true);
        stopSyncButton.setOnAction(e -> stopSync());

        Button refreshButton = new Button("刷新表列表");
        refreshButton.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white;");
        refreshButton.setOnAction(e -> loadTableNames());

        buttonBox.getChildren().addAll(startSyncButton, stopSyncButton, refreshButton);

        pane.getChildren().addAll(titleLabel, tableSelectionBox, directionBox, conflictBox, buttonBox);
        return pane;
    }

    /**
     * 创建进度面板
     */
    private VBox createProgressPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(20));
        pane.setAlignment(Pos.CENTER);

        progressBar = new ProgressBar();
        progressBar.setPrefWidth(600);
        progressBar.setProgress(0);

        progressLabel = new Label("准备就绪");
        progressLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d;");

        pane.getChildren().addAll(progressBar, progressLabel);
        return pane;
    }

    /**
     * 创建结果面板
     */
    private VBox createResultPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));
        VBox.setVgrow(pane, Priority.ALWAYS);

        Label resultLabel = new Label("同步结果");
        resultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        pane.getChildren().addAll(resultLabel, resultArea);
        return pane;
    }

    /**
     * 加载表名列表
     */
    private void loadTableNames() {
        try {
            List<TableMapping> mappings = MappingLoader.loadMappings();
            ObservableList<String> tableNames = FXCollections.observableArrayList(
                    mappings.stream()
                            .map(m -> m.svr_tab + " ↔ " + m.clt_tab)
                            .collect(Collectors.toList())
            );
            tableCheckComboBox.getItems().clear();
            tableCheckComboBox.getItems().addAll(tableNames);
        } catch (Exception e) {
            showError("加载表名失败: " + e.getMessage());
        }
    }

    /**
     * 开始同步
     */
    private void startSync() {
        if (tableCheckComboBox.getCheckModel().getCheckedItems().isEmpty()) {
            showAlert("请至少选择一个要同步的表");
            return;
        }

        List<String> selectedTables = tableCheckComboBox.getCheckModel().getCheckedItems().stream()
                .map(table -> table.split(" ↔ ")[0]) // 获取服务端表名
                .collect(Collectors.toList());

        TableSyncService.SyncDirection direction = directionCombo.getValue();
        TableSyncService.ConflictResolution conflictStrategy = conflictCombo.getValue();

        // 禁用控件
        startSyncButton.setDisable(true);
        stopSyncButton.setDisable(false);
        tableCheckComboBox.setDisable(true);
        directionCombo.setDisable(true);
        conflictCombo.setDisable(true);

        // 清空结果区域
        resultArea.clear();

        // 创建同步任务
        currentSyncTask = new Task<List<TableSyncService.SyncResult>>() {
            @Override
            protected List<TableSyncService.SyncResult> call() throws Exception {
                updateMessage("正在执行同步...");
                return syncService.syncTables(selectedTables, direction, conflictStrategy);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    List<TableSyncService.SyncResult> results = getValue();
                    displayResults(results);
                    resetControls();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    showError("同步失败: " + exception.getMessage());
                    resetControls();
                });
            }
        };

        // 绑定进度
        progressBar.progressProperty().bind(currentSyncTask.progressProperty());
        progressLabel.textProperty().bind(currentSyncTask.messageProperty());

        // 启动任务
        Thread thread = new Thread(currentSyncTask);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 停止同步
     */
    private void stopSync() {
        if (currentSyncTask != null) {
            currentSyncTask.cancel();
        }
        resetControls();
        progressLabel.setText("同步已停止");
    }

    /**
     * 重置控件状态
     */
    private void resetControls() {
        startSyncButton.setDisable(false);
        stopSyncButton.setDisable(true);
        tableCheckComboBox.setDisable(false);
        directionCombo.setDisable(false);
        conflictCombo.setDisable(false);
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
    }

    /**
     * 显示同步结果
     */
    private void displayResults(List<TableSyncService.SyncResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 同步完成 ===\n\n");

        int totalTables = results.size();
        int successTables = (int) results.stream().filter(TableSyncService.SyncResult::isSuccess).count();
        int totalRecords = results.stream().mapToInt(r -> r.totalRecords).sum();
        int totalSynced = results.stream().mapToInt(r -> r.syncedRecords).sum();
        int totalSkipped = results.stream().mapToInt(r -> r.skippedRecords).sum();
        int totalConflicts = results.stream().mapToInt(r -> r.conflictRecords).sum();

        sb.append(String.format("总计: %d 个表，成功 %d 个表\n", totalTables, successTables));
        sb.append(String.format("数据: 总计 %d 条，同步 %d 条，跳过 %d 条，冲突 %d 条\n\n",
                totalRecords, totalSynced, totalSkipped, totalConflicts));

        sb.append("详细结果:\n");
        sb.append(String.join("", Collections.nCopies(80, "-")) + "\n");

        for (TableSyncService.SyncResult result : results) {
            if (result.isSuccess()) {
                sb.append(String.format("✓ %s\n", result.getSummary()));
            } else {
                sb.append(String.format("✗ %s\n", result.tableName));
                for (String error : result.errors) {
                    sb.append(String.format("  错误: %s\n", error));
                }
            }
        }

        resultArea.setText(sb.toString());
        progressLabel.setText("同步完成");
    }

    /**
     * 显示错误信息
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示提示信息
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 同步进度监听器
     */
    private class SyncProgressListener implements TableSyncService.SyncProgressListener {
        @Override
        public void onProgress(String tableName, int current, int total) {
            Platform.runLater(() -> {
                double progress = (double) current / total;
                progressBar.setProgress(progress);
                progressLabel.setText(String.format("正在同步表 %s: %d/%d", tableName, current, total));
            });
        }

        @Override
        public void onTableComplete(String tableName, TableSyncService.SyncResult result) {
            Platform.runLater(() -> {
                String status = result.isSuccess() ? "✓" : "✗";
                String summary = String.format("%s %s - 同步 %d 条", status, tableName, result.syncedRecords);

                // 可以在结果区域追加当前表的完成状态
                if (resultArea.getText().isEmpty() || !resultArea.getText().contains("=== 同步完成 ===")) {
                    resultArea.appendText(summary + "\n");
                }
            });
        }

        @Override
        public void onSyncComplete(List<TableSyncService.SyncResult> results) {
            Platform.runLater(() -> {
                // 这个方法会在 Task.succeeded() 中处理
            });
        }
    }
}