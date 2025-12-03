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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.analysis.aion.IdNameResolver;
import red.jiuzhou.dbxml.DbToXmlGenerator;
import red.jiuzhou.dbxml.XmlToDbGenerator;
import red.jiuzhou.sync.TableSyncService;
import red.jiuzhou.tabmapping.MappingLoader;
import red.jiuzhou.tabmapping.TableMapping;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据操作中心
 *
 * <p>整合所有数据操作功能的统一入口：
 * <ul>
 *   <li>数据导出 (DB -> XML)</li>
 *   <li>数据导入 (XML -> DB)</li>
 *   <li>表同步 (客户端 ↔ 服务端)</li>
 *   <li>批量编辑</li>
 * </ul>
 *
 * <p>为游戏设计师提供直观、高效的数据操作界面。
 *
 * @author Claude
 * @version 1.0
 */
public class DataOperationCenterStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(DataOperationCenterStage.class);

    // 操作类型枚举
    public enum OperationType {
        EXPORT("导出", "DB → XML", "#28a745", "将数据库表导出为XML文件"),
        IMPORT("导入", "XML → DB", "#007bff", "将XML文件导入到数据库"),
        SYNC("同步", "客户端 ↔ 服务端", "#6f42c1", "同步客户端和服务端数据表"),
        EDIT("编辑", "批量修改", "#fd7e14", "批量修改数据内容");

        final String name;
        final String subTitle;
        final String color;
        final String description;

        OperationType(String name, String subTitle, String color, String description) {
            this.name = name;
            this.subTitle = subTitle;
            this.color = color;
            this.description = description;
        }
    }

    // 操作范围枚举
    public enum OperationScope {
        CURRENT("当前表"),
        SELECTED("选中文件"),
        ALL("全部表");

        final String label;

        OperationScope(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // UI组件
    private ToggleGroup operationToggleGroup;
    private ToggleButton[] operationButtons;
    private OperationType currentOperation = OperationType.EXPORT;

    private ComboBox<OperationScope> scopeComboBox;
    private CheckComboBox<String> tableCheckComboBox;
    private CheckBox selectAllCheckBox;

    private CheckBox showIdNameCheckBox;
    private CheckBox autoBackupCheckBox;
    private CheckBox aiAssistCheckBox;

    private ProgressBar progressBar;
    private Label progressLabel;
    private TextArea logArea;

    private Button previewButton;
    private Button executeButton;
    private Button cancelButton;

    // 同步相关组件
    private VBox syncOptionsPane;
    private ComboBox<TableSyncService.SyncDirection> syncDirectionCombo;
    private ComboBox<TableSyncService.ConflictResolution> conflictResolutionCombo;

    // 状态
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Task<?> currentTask;
    private final Stage ownerStage;

    public DataOperationCenterStage(Stage owner) {
        this.ownerStage = owner;
        initializeStage();
        buildUI();
        loadTableList();
    }

    private void initializeStage() {
        setTitle("数据操作中心");
        initOwner(ownerStage);
        initModality(Modality.NONE);
        setWidth(900);
        setHeight(700);
        setMinWidth(800);
        setMinHeight(600);

        // 关闭时清理
        setOnCloseRequest(e -> {
            if (currentTask != null && currentTask.isRunning()) {
                currentTask.cancel();
            }
            executor.shutdownNow();
        });
    }

    private void buildUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f8f9fa;");
        root.setPadding(new Insets(0));

        // 顶部：操作类型选择
        root.setTop(createOperationTypePanel());

        // 中部：配置区域
        root.setCenter(createConfigPanel());

        // 底部：执行和日志区域
        root.setBottom(createExecutionPanel());

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 创建操作类型选择面板（顶部卡片按钮）
     */
    private VBox createOperationTypePanel() {
        VBox container = new VBox(0);
        container.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        // 标题栏
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(15, 20, 10, 20));

        Label titleLabel = new Label("数据操作中心");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#2c3e50"));

        Label subtitleLabel = new Label("选择操作类型，配置参数，一键执行");
        subtitleLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleBar.getChildren().addAll(titleLabel, subtitleLabel, spacer);

        // 操作类型按钮组
        HBox buttonGroup = new HBox(15);
        buttonGroup.setAlignment(Pos.CENTER);
        buttonGroup.setPadding(new Insets(10, 20, 20, 20));

        operationToggleGroup = new ToggleGroup();
        operationButtons = new ToggleButton[OperationType.values().length];

        for (int i = 0; i < OperationType.values().length; i++) {
            OperationType type = OperationType.values()[i];
            ToggleButton btn = createOperationButton(type);
            btn.setToggleGroup(operationToggleGroup);
            operationButtons[i] = btn;
            buttonGroup.getChildren().add(btn);
        }

        // 默认选中导出
        operationButtons[0].setSelected(true);

        // 监听切换
        operationToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                ToggleButton selected = (ToggleButton) newVal;
                currentOperation = (OperationType) selected.getUserData();
                updateUIForOperation();
            } else if (oldVal != null) {
                // 防止取消选择
                oldVal.setSelected(true);
            }
        });

        container.getChildren().addAll(titleBar, buttonGroup);
        return container;
    }

    private ToggleButton createOperationButton(OperationType type) {
        VBox content = new VBox(5);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(15, 25, 15, 25));

        Label nameLabel = new Label(type.name);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        nameLabel.setTextFill(Color.web(type.color));

        Label subLabel = new Label(type.subTitle);
        subLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        content.getChildren().addAll(nameLabel, subLabel);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(content);
        btn.setUserData(type);
        btn.setPrefWidth(160);
        btn.setPrefHeight(80);

        // 样式
        String baseStyle = "-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-width: 2; " +
                "-fx-cursor: hand; -fx-background-color: white;";
        String normalBorder = "-fx-border-color: #dee2e6;";
        String selectedBorder = "-fx-border-color: " + type.color + ";";

        btn.setStyle(baseStyle + normalBorder);

        btn.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                btn.setStyle(baseStyle + selectedBorder + "-fx-background-color: " + type.color + "15;");
            } else {
                btn.setStyle(baseStyle + normalBorder);
            }
        });

        // Tooltip
        Tooltip tooltip = new Tooltip(type.description);
        btn.setTooltip(tooltip);

        return btn;
    }

    /**
     * 创建配置面板
     */
    private VBox createConfigPanel() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));

        // 操作范围
        HBox scopeBox = new HBox(15);
        scopeBox.setAlignment(Pos.CENTER_LEFT);

        Label scopeLabel = new Label("操作范围:");
        scopeLabel.setPrefWidth(100);
        scopeLabel.setStyle("-fx-font-weight: bold;");

        scopeComboBox = new ComboBox<>();
        scopeComboBox.getItems().addAll(OperationScope.values());
        scopeComboBox.setValue(OperationScope.SELECTED);
        scopeComboBox.setPrefWidth(150);

        scopeBox.getChildren().addAll(scopeLabel, scopeComboBox);

        // 表/文件选择
        VBox selectionBox = new VBox(10);
        selectionBox.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; " +
                "-fx-border-radius: 5; -fx-background-radius: 5;");
        selectionBox.setPadding(new Insets(15));

        HBox selectionHeader = new HBox(15);
        selectionHeader.setAlignment(Pos.CENTER_LEFT);

        Label selectionLabel = new Label("选择表/文件:");
        selectionLabel.setStyle("-fx-font-weight: bold;");

        selectAllCheckBox = new CheckBox("全选");
        selectAllCheckBox.setOnAction(e -> {
            if (selectAllCheckBox.isSelected()) {
                tableCheckComboBox.getCheckModel().checkAll();
            } else {
                tableCheckComboBox.getCheckModel().clearChecks();
            }
        });

        Button refreshBtn = new Button("刷新");
        refreshBtn.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-font-size: 11px;");
        refreshBtn.setOnAction(e -> loadTableList());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        selectionHeader.getChildren().addAll(selectionLabel, selectAllCheckBox, spacer, refreshBtn);

        tableCheckComboBox = new CheckComboBox<>();
        tableCheckComboBox.setPrefWidth(Double.MAX_VALUE);
        tableCheckComboBox.setMaxWidth(Double.MAX_VALUE);

        selectionBox.getChildren().addAll(selectionHeader, tableCheckComboBox);
        VBox.setVgrow(selectionBox, Priority.ALWAYS);

        // 同步专用选项（初始隐藏）
        syncOptionsPane = createSyncOptionsPane();
        syncOptionsPane.setVisible(false);
        syncOptionsPane.setManaged(false);

        // 高级选项
        VBox optionsBox = new VBox(10);
        optionsBox.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; " +
                "-fx-border-radius: 5; -fx-background-radius: 5;");
        optionsBox.setPadding(new Insets(15));

        Label optionsLabel = new Label("高级选项:");
        optionsLabel.setStyle("-fx-font-weight: bold;");

        HBox checkboxRow = new HBox(25);
        checkboxRow.setAlignment(Pos.CENTER_LEFT);

        showIdNameCheckBox = new CheckBox("显示ID对应的NAME");
        showIdNameCheckBox.setSelected(true);
        showIdNameCheckBox.setTooltip(new Tooltip("在数据中显示ID字段对应的名称，如 \"123 (火球术)\""));

        autoBackupCheckBox = new CheckBox("自动备份");
        autoBackupCheckBox.setSelected(true);
        autoBackupCheckBox.setTooltip(new Tooltip("执行操作前自动备份受影响的数据"));

        aiAssistCheckBox = new CheckBox("AI辅助");
        aiAssistCheckBox.setSelected(false);
        aiAssistCheckBox.setTooltip(new Tooltip("使用AI分析数据并提供建议"));

        checkboxRow.getChildren().addAll(showIdNameCheckBox, autoBackupCheckBox, aiAssistCheckBox);

        optionsBox.getChildren().addAll(optionsLabel, checkboxRow);

        container.getChildren().addAll(scopeBox, selectionBox, syncOptionsPane, optionsBox);
        return container;
    }

    private VBox createSyncOptionsPane() {
        VBox pane = new VBox(10);
        pane.setStyle("-fx-background-color: white; -fx-border-color: #6f42c1; " +
                "-fx-border-radius: 5; -fx-background-radius: 5;");
        pane.setPadding(new Insets(15));

        Label titleLabel = new Label("同步选项:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #6f42c1;");

        HBox row1 = new HBox(15);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label dirLabel = new Label("同步方向:");
        dirLabel.setPrefWidth(100);

        syncDirectionCombo = new ComboBox<>();
        syncDirectionCombo.getItems().addAll(
                TableSyncService.SyncDirection.SVR_TO_CLT,
                TableSyncService.SyncDirection.CLT_TO_SVR,
                TableSyncService.SyncDirection.BI_DIRECTIONAL
        );
        syncDirectionCombo.setValue(TableSyncService.SyncDirection.BI_DIRECTIONAL);
        syncDirectionCombo.setPrefWidth(200);

        row1.getChildren().addAll(dirLabel, syncDirectionCombo);

        HBox row2 = new HBox(15);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label conflictLabel = new Label("冲突策略:");
        conflictLabel.setPrefWidth(100);

        conflictResolutionCombo = new ComboBox<>();
        conflictResolutionCombo.getItems().addAll(
                TableSyncService.ConflictResolution.SVR_PRIORITY,
                TableSyncService.ConflictResolution.CLT_PRIORITY,
                TableSyncService.ConflictResolution.TIMESTAMP,
                TableSyncService.ConflictResolution.MANUAL
        );
        conflictResolutionCombo.setValue(TableSyncService.ConflictResolution.SVR_PRIORITY);
        conflictResolutionCombo.setPrefWidth(200);

        row2.getChildren().addAll(conflictLabel, conflictResolutionCombo);

        pane.getChildren().addAll(titleLabel, row1, row2);
        return pane;
    }

    /**
     * 创建执行面板
     */
    private VBox createExecutionPanel() {
        VBox container = new VBox(10);
        container.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        container.setPadding(new Insets(15, 20, 15, 20));

        // 进度条
        HBox progressBox = new HBox(15);
        progressBox.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressLabel = new Label("就绪");
        progressLabel.setStyle("-fx-text-fill: #6c757d;");
        progressLabel.setPrefWidth(200);

        progressBox.getChildren().addAll(progressBar, progressLabel);

        // 按钮组
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        previewButton = new Button("预览变更");
        previewButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-weight: bold;");
        previewButton.setPrefWidth(120);
        previewButton.setOnAction(e -> previewChanges());

        executeButton = new Button("执行操作");
        executeButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        executeButton.setPrefWidth(120);
        executeButton.setOnAction(e -> executeOperation());

        cancelButton = new Button("取消");
        cancelButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
        cancelButton.setPrefWidth(100);
        cancelButton.setDisable(true);
        cancelButton.setOnAction(e -> cancelOperation());

        buttonBox.getChildren().addAll(previewButton, executeButton, cancelButton);

        // 日志区域
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        logArea.setPromptText("操作日志将显示在这里...");

        container.getChildren().addAll(progressBox, buttonBox, logArea);
        return container;
    }

    /**
     * 根据操作类型更新UI
     */
    private void updateUIForOperation() {
        boolean isSync = currentOperation == OperationType.SYNC;
        syncOptionsPane.setVisible(isSync);
        syncOptionsPane.setManaged(isSync);

        // 更新执行按钮文字
        switch (currentOperation) {
            case EXPORT:
                executeButton.setText("开始导出");
                executeButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
                break;
            case IMPORT:
                executeButton.setText("开始导入");
                executeButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold;");
                break;
            case SYNC:
                executeButton.setText("开始同步");
                executeButton.setStyle("-fx-background-color: #6f42c1; -fx-text-fill: white; -fx-font-weight: bold;");
                break;
            case EDIT:
                executeButton.setText("开始编辑");
                executeButton.setStyle("-fx-background-color: #fd7e14; -fx-text-fill: white; -fx-font-weight: bold;");
                break;
        }

        appendLog("切换到 [" + currentOperation.name + "] 模式");
    }

    /**
     * 加载表列表
     */
    private void loadTableList() {
        try {
            List<String> tables = new ArrayList<>();

            // 根据当前操作类型加载不同的表
            if (currentOperation == OperationType.SYNC) {
                // 同步操作：加载映射配置中的表
                List<TableMapping> mappings = MappingLoader.loadMappings();
                for (TableMapping mapping : mappings) {
                    tables.add(mapping.getSvrTab() + " ↔ " + mapping.getCltTab());
                }
            } else {
                // 其他操作：加载数据库中的表
                String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name";
                List<Map<String, Object>> rows = DatabaseUtil.getJdbcTemplate().queryForList(sql);
                for (Map<String, Object> row : rows) {
                    tables.add(row.get("table_name").toString());
                }
            }

            Platform.runLater(() -> {
                tableCheckComboBox.getItems().clear();
                tableCheckComboBox.getItems().addAll(tables);
                appendLog("已加载 " + tables.size() + " 个表/映射");
            });

        } catch (Exception e) {
            log.error("加载表列表失败", e);
            Platform.runLater(() -> appendLog("错误: 加载表列表失败 - " + e.getMessage()));
        }
    }

    /**
     * 预览变更
     */
    private void previewChanges() {
        List<String> selected = new ArrayList<>(tableCheckComboBox.getCheckModel().getCheckedItems());
        if (selected.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "请先选择要操作的表/文件");
            return;
        }

        StringBuilder preview = new StringBuilder();
        preview.append("=== 操作预览 ===\n");
        preview.append("操作类型: ").append(currentOperation.name).append(" (").append(currentOperation.subTitle).append(")\n");
        preview.append("选中数量: ").append(selected.size()).append(" 个\n");
        preview.append("选中项目:\n");

        for (String item : selected) {
            preview.append("  - ").append(item).append("\n");
        }

        preview.append("\n高级选项:\n");
        preview.append("  显示ID名称: ").append(showIdNameCheckBox.isSelected() ? "是" : "否").append("\n");
        preview.append("  自动备份: ").append(autoBackupCheckBox.isSelected() ? "是" : "否").append("\n");
        preview.append("  AI辅助: ").append(aiAssistCheckBox.isSelected() ? "是" : "否").append("\n");

        if (currentOperation == OperationType.SYNC) {
            preview.append("\n同步选项:\n");
            preview.append("  同步方向: ").append(syncDirectionCombo.getValue()).append("\n");
            preview.append("  冲突策略: ").append(conflictResolutionCombo.getValue()).append("\n");
        }

        logArea.setText(preview.toString());
    }

    /**
     * 执行操作
     */
    private void executeOperation() {
        List<String> selected = new ArrayList<>(tableCheckComboBox.getCheckModel().getCheckedItems());
        if (selected.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "请先选择要操作的表/文件");
            return;
        }

        // 确认对话框
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认操作");
        confirm.setHeaderText("即将执行 [" + currentOperation.name + "] 操作");
        confirm.setContentText("将对 " + selected.size() + " 个表/文件执行操作，是否继续？");

        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        // 开始执行
        setExecuting(true);
        appendLog("\n=== 开始执行 " + currentOperation.name + " ===");

        currentTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int total = selected.size();
                int completed = 0;

                for (String item : selected) {
                    if (isCancelled()) {
                        Platform.runLater(() -> appendLog("操作已取消"));
                        break;
                    }

                    updateMessage("处理: " + item);
                    updateProgress(completed, total);

                    try {
                        executeForItem(item);
                        Platform.runLater(() -> appendLog("完成: " + item));
                    } catch (Exception e) {
                        Platform.runLater(() -> appendLog("失败: " + item + " - " + e.getMessage()));
                    }

                    completed++;
                    updateProgress(completed, total);
                }

                return null;
            }
        };

        progressBar.progressProperty().bind(currentTask.progressProperty());
        currentTask.messageProperty().addListener((obs, oldVal, newVal) -> {
            progressLabel.setText(newVal);
        });

        currentTask.setOnSucceeded(e -> {
            setExecuting(false);
            appendLog("=== 操作完成 ===");
        });

        currentTask.setOnFailed(e -> {
            setExecuting(false);
            Throwable ex = currentTask.getException();
            appendLog("=== 操作失败: " + (ex != null ? ex.getMessage() : "未知错误") + " ===");
        });

        currentTask.setOnCancelled(e -> {
            setExecuting(false);
            appendLog("=== 操作已取消 ===");
        });

        executor.submit(currentTask);
    }

    private void executeForItem(String item) throws Exception {
        switch (currentOperation) {
            case EXPORT:
                executeExport(item);
                break;
            case IMPORT:
                executeImport(item);
                break;
            case SYNC:
                executeSync(item);
                break;
            case EDIT:
                executeEdit(item);
                break;
        }
    }

    private void executeExport(String tableName) throws Exception {
        String xmlPath = YamlUtils.getProperty("aion.xmlPath");
        if (xmlPath == null || xmlPath.isEmpty()) {
            throw new RuntimeException("未配置XML路径");
        }

        // 使用DbToXmlGenerator导出
        log.info("导出表: {} 到 {}", tableName, xmlPath);
        // TODO: 实际调用导出逻辑
        Thread.sleep(500);  // 模拟操作
    }

    private void executeImport(String tableName) throws Exception {
        log.info("导入表: {}", tableName);
        // TODO: 实际调用导入逻辑
        Thread.sleep(500);
    }

    private void executeSync(String mappingItem) throws Exception {
        log.info("同步: {}", mappingItem);
        // TODO: 调用TableSyncService
        Thread.sleep(500);
    }

    private void executeEdit(String tableName) throws Exception {
        log.info("编辑表: {}", tableName);
        // TODO: 批量编辑逻辑
        Thread.sleep(500);
    }

    /**
     * 取消操作
     */
    private void cancelOperation() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
    }

    private void setExecuting(boolean executing) {
        Platform.runLater(() -> {
            executeButton.setDisable(executing);
            previewButton.setDisable(executing);
            cancelButton.setDisable(!executing);
            tableCheckComboBox.setDisable(executing);
            scopeComboBox.setDisable(executing);

            for (ToggleButton btn : operationButtons) {
                btn.setDisable(executing);
            }

            if (!executing) {
                progressBar.progressProperty().unbind();
                progressBar.setProgress(0);
                progressLabel.setText("就绪");
            }
        });
    }

    private void appendLog(String message) {
        String timestamp = String.format("[%tT] ", new Date());
        logArea.appendText(timestamp + message + "\n");
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(this);
        alert.showAndWait();
    }
}
