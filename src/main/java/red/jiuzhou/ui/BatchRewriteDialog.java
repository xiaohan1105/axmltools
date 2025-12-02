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
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import red.jiuzhou.rewrite.EnhancedBatchRewriter;
import red.jiuzhou.rewrite.EnhancedBatchRewriter.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批量改写对话框
 * 提供友好的UI界面进行批量字段改写操作
 *
 * 功能：
 * - 多字段选择和编辑
 * - AI改写配置
 * - 改写预览
 * - 模板管理
 * - 历史记录
 */
@Slf4j
public class BatchRewriteDialog extends Stage {

    private final EnhancedBatchRewriter rewriter = new EnhancedBatchRewriter();
    private final ObservableList<FieldItem> fieldItems = FXCollections.observableArrayList();
    private final ObservableList<PreviewItem> previewItems = FXCollections.observableArrayList();
    private List<String> targetFiles = new ArrayList<>();

    // UI组件
    private TableView<FieldItem> fieldTable;
    private TableView<PreviewItem> previewTable;
    private ComboBox<RewriteOptions.RewriteMode> modeCombo;
    private CheckBox useAICheck;
    private ComboBox<String> aiModelCombo;
    private TextArea aiPromptArea;
    private TextField conditionField;
    private CheckBox previewCheck;
    private CheckBox backupCheck;
    private CheckBox cacheAICheck;
    private ComboBox<String> templateCombo;
    private TextArea logArea;
    private ProgressBar progressBar;
    private Label statusLabel;

    // 字段项
    @Data
    public static class FieldItem {
        private SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
        private SimpleStringProperty fieldName = new SimpleStringProperty();
        private SimpleStringProperty oldValue = new SimpleStringProperty();
        private SimpleStringProperty newValue = new SimpleStringProperty();
        private SimpleStringProperty aiPrompt = new SimpleStringProperty();

        public FieldItem(String field) {
            this.fieldName.set(field);
        }
    }

    // 预览项
    @Data
    public static class PreviewItem {
        private SimpleBooleanProperty accept = new SimpleBooleanProperty(true);
        private SimpleStringProperty file = new SimpleStringProperty();
        private SimpleStringProperty field = new SimpleStringProperty();
        private SimpleStringProperty original = new SimpleStringProperty();
        private SimpleStringProperty preview = new SimpleStringProperty();
        private RewriteResult result;

        public PreviewItem(RewriteResult result) {
            this.result = result;
            this.file.set(new File(result.getFile()).getName());
            this.field.set(result.getField());
            this.original.set(result.getOriginalValue());
            this.preview.set(result.getNewValue());
        }
    }

    public BatchRewriteDialog(Stage owner) {
        initModality(Modality.WINDOW_MODAL);
        initOwner(owner);
        setTitle("批量智能改写");

        initUI();
        setupEventHandlers();
        loadTemplates();
    }

    private void initUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 创建标签页
        TabPane tabPane = new TabPane();

        // 字段配置标签页
        Tab fieldTab = new Tab("字段配置");
        fieldTab.setClosable(false);
        fieldTab.setContent(createFieldConfigPane());

        // AI配置标签页
        Tab aiTab = new Tab("AI配置");
        aiTab.setClosable(false);
        aiTab.setContent(createAIConfigPane());

        // 预览标签页
        Tab previewTab = new Tab("预览");
        previewTab.setClosable(false);
        previewTab.setContent(createPreviewPane());

        // 模板标签页
        Tab templateTab = new Tab("模板");
        templateTab.setClosable(false);
        templateTab.setContent(createTemplatePane());

        // 历史标签页
        Tab historyTab = new Tab("历史");
        historyTab.setClosable(false);
        historyTab.setContent(createHistoryPane());

        tabPane.getTabs().addAll(fieldTab, aiTab, previewTab, templateTab, historyTab);

        // 底部控制栏
        HBox controlBar = createControlBar();

        // 状态栏
        HBox statusBar = createStatusBar();

        root.getChildren().addAll(tabPane, controlBar, statusBar);

        Scene scene = new Scene(root, 1000, 700);
        setScene(scene);
    }

    private VBox createFieldConfigPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // 文件选择区
        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        Label fileLabel = new Label("目标文件:");
        TextField fileField = new TextField();
        fileField.setPrefWidth(400);
        fileField.setEditable(false);
        fileField.setPromptText("选择要改写的XML文件");

        Button selectFilesBtn = new Button("选择文件...");
        Button selectDirBtn = new Button("选择目录...");

        fileRow.getChildren().addAll(fileLabel, fileField, selectFilesBtn, selectDirBtn);

        // 改写模式选择
        HBox modeRow = new HBox(10);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        Label modeLabel = new Label("改写模式:");
        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll(RewriteOptions.RewriteMode.values());
        modeCombo.setValue(RewriteOptions.RewriteMode.REPLACE);
        modeCombo.setPrefWidth(150);

        Label conditionLabel = new Label("条件:");
        conditionField = new TextField();
        conditionField.setPrefWidth(300);
        conditionField.setPromptText("例如: level > 50");

        modeRow.getChildren().addAll(modeLabel, modeCombo, conditionLabel, conditionField);

        // 字段表格
        Label tableLabel = new Label("字段配置:");
        fieldTable = createFieldTable();

        // 字段操作按钮
        HBox fieldButtons = new HBox(10);
        Button addFieldBtn = new Button("添加字段");
        Button removeFieldBtn = new Button("移除选中");
        Button clearFieldsBtn = new Button("清空");
        Button autoDetectBtn = new Button("自动检测字段");

        fieldButtons.getChildren().addAll(addFieldBtn, removeFieldBtn, clearFieldsBtn,
                                         new Separator(), autoDetectBtn);

        pane.getChildren().addAll(fileRow, modeRow, tableLabel, fieldTable, fieldButtons);

        // 事件处理
        selectFilesBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择XML文件");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML文件", "*.xml"));
            List<File> files = chooser.showOpenMultipleDialog(this);
            if (files != null) {
                targetFiles = files.stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.toList());
                fileField.setText(files.size() + " 个文件");
            }
        });

        selectDirBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("选择目录");
            File dir = chooser.showDialog(this);
            if (dir != null) {
                try {
                    targetFiles = Files.walk(Paths.get(dir.getAbsolutePath()))
                        .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                        .map(p -> p.toString())
                        .collect(Collectors.toList());
                    fileField.setText(dir.getName() + " (" + targetFiles.size() + " 个XML文件)");
                } catch (Exception ex) {
                    showAlert("读取目录失败: " + ex.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });

        addFieldBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("添加字段");
            dialog.setHeaderText("输入字段名称");
            dialog.showAndWait().ifPresent(name -> {
                fieldItems.add(new FieldItem(name));
            });
        });

        removeFieldBtn.setOnAction(e -> {
            fieldItems.removeIf(item -> item.getSelected().get());
        });

        clearFieldsBtn.setOnAction(e -> fieldItems.clear());

        autoDetectBtn.setOnAction(e -> autoDetectFields());

        return pane;
    }

    private TableView<FieldItem> createFieldTable() {
        TableView<FieldItem> table = new TableView<>();
        table.setEditable(true);
        table.setItems(fieldItems);
        table.setPrefHeight(300);

        TableColumn<FieldItem, Boolean> selectCol = new TableColumn<>("选择");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().getSelected());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);

        TableColumn<FieldItem, String> fieldCol = new TableColumn<>("字段名");
        fieldCol.setCellValueFactory(cellData -> cellData.getValue().getFieldName());
        fieldCol.setCellFactory(TextFieldTableCell.forTableColumn());
        fieldCol.setPrefWidth(150);
        fieldCol.setEditable(true);

        TableColumn<FieldItem, String> oldValueCol = new TableColumn<>("原值示例");
        oldValueCol.setCellValueFactory(cellData -> cellData.getValue().getOldValue());
        oldValueCol.setPrefWidth(200);

        TableColumn<FieldItem, String> newValueCol = new TableColumn<>("新值/规则");
        newValueCol.setCellValueFactory(cellData -> cellData.getValue().getNewValue());
        newValueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        newValueCol.setPrefWidth(200);
        newValueCol.setEditable(true);

        TableColumn<FieldItem, String> promptCol = new TableColumn<>("AI提示词");
        promptCol.setCellValueFactory(cellData -> cellData.getValue().getAiPrompt());
        promptCol.setCellFactory(TextFieldTableCell.forTableColumn());
        promptCol.setPrefWidth(300);
        promptCol.setEditable(true);

        table.getColumns().addAll(selectCol, fieldCol, oldValueCol, newValueCol, promptCol);

        return table;
    }

    private VBox createAIConfigPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // AI启用选项
        useAICheck = new CheckBox("使用AI智能改写");
        useAICheck.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        // AI模型选择
        HBox modelRow = new HBox(10);
        modelRow.setAlignment(Pos.CENTER_LEFT);

        Label modelLabel = new Label("AI模型:");
        aiModelCombo = new ComboBox<>();
        aiModelCombo.getItems().addAll("qwen", "doubao", "kimi", "deepseek");
        aiModelCombo.setValue("qwen");
        aiModelCombo.setPrefWidth(150);

        cacheAICheck = new CheckBox("缓存AI结果");
        cacheAICheck.setSelected(true);

        modelRow.getChildren().addAll(modelLabel, aiModelCombo, new Separator(), cacheAICheck);

        // 全局提示词
        Label promptLabel = new Label("全局AI提示词:");
        aiPromptArea = new TextArea();
        aiPromptArea.setPrefHeight(200);
        aiPromptArea.setWrapText(true);
        aiPromptArea.setPromptText(
            "输入AI改写的指导说明...\n" +
            "例如：\n" +
            "请将以下内容改写为更加生动有趣的描述，保持原意不变。\n" +
            "要求：\n" +
            "1. 使用丰富的形容词\n" +
            "2. 加入适当的比喻\n" +
            "3. 保持简洁，不超过原文1.5倍长度"
        );

        // 提示词模板
        Label templateLabel = new Label("提示词模板:");
        HBox templateRow = new HBox(10);

        ComboBox<String> promptTemplateCombo = new ComboBox<>();
        promptTemplateCombo.getItems().addAll(
            "武侠风格改写",
            "科幻风格改写",
            "幽默风格改写",
            "正式文档风格",
            "游戏描述优化",
            "技能说明优化"
        );
        promptTemplateCombo.setPrefWidth(200);

        Button applyTemplateBtn = new Button("应用模板");
        Button savePromptBtn = new Button("保存为模板");

        templateRow.getChildren().addAll(promptTemplateCombo, applyTemplateBtn, savePromptBtn);

        // AI设置
        TitledPane aiSettings = new TitledPane("高级设置", createAISettings());
        aiSettings.setExpanded(false);

        pane.getChildren().addAll(useAICheck, modelRow, promptLabel, aiPromptArea,
                                 templateLabel, templateRow, aiSettings);

        // 事件处理
        useAICheck.setOnAction(e -> {
            boolean enabled = useAICheck.isSelected();
            aiModelCombo.setDisable(!enabled);
            aiPromptArea.setDisable(!enabled);
            cacheAICheck.setDisable(!enabled);
        });

        applyTemplateBtn.setOnAction(e -> {
            String template = promptTemplateCombo.getValue();
            if (template != null) {
                aiPromptArea.setText(getPromptTemplate(template));
            }
        });

        savePromptBtn.setOnAction(e -> savePromptAsTemplate());

        return pane;
    }

    private VBox createAISettings() {
        VBox settings = new VBox(10);
        settings.setPadding(new Insets(10));

        // 温度设置
        HBox tempRow = new HBox(10);
        Label tempLabel = new Label("创造性 (Temperature):");
        Slider tempSlider = new Slider(0, 1, 0.7);
        tempSlider.setShowTickLabels(true);
        tempSlider.setShowTickMarks(true);
        Label tempValue = new Label("0.7");
        tempSlider.valueProperty().addListener((obs, old, val) ->
            tempValue.setText(String.format("%.1f", val.doubleValue())));

        tempRow.getChildren().addAll(tempLabel, tempSlider, tempValue);

        // 最大长度设置
        HBox lengthRow = new HBox(10);
        Label lengthLabel = new Label("最大长度:");
        Spinner<Integer> lengthSpinner = new Spinner<>(50, 1000, 200, 50);
        lengthSpinner.setPrefWidth(100);

        lengthRow.getChildren().addAll(lengthLabel, lengthSpinner);

        settings.getChildren().addAll(tempRow, lengthRow);

        return settings;
    }

    private VBox createPreviewPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // 预览控制
        HBox controlRow = new HBox(10);
        controlRow.setAlignment(Pos.CENTER_LEFT);

        Button generatePreviewBtn = new Button("生成预览");
        generatePreviewBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");

        Button refreshBtn = new Button("刷新");
        Button clearBtn = new Button("清空");

        Label statsLabel = new Label("预览: 0 个改动");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        controlRow.getChildren().addAll(generatePreviewBtn, refreshBtn, clearBtn,
                                       spacer, statsLabel);

        // 预览表格
        previewTable = createPreviewTable();

        // 差异显示区
        Label diffLabel = new Label("改写对比:");
        SplitPane diffPane = new SplitPane();
        diffPane.setPrefHeight(200);

        TextArea originalArea = new TextArea();
        originalArea.setEditable(false);
        originalArea.setWrapText(true);
        originalArea.setStyle("-fx-background-color: #ffebee;");

        TextArea previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setStyle("-fx-background-color: #e8f5e9;");

        diffPane.getItems().addAll(originalArea, previewArea);

        pane.getChildren().addAll(controlRow, previewTable, diffLabel, diffPane);

        // 事件处理
        generatePreviewBtn.setOnAction(e -> generatePreview());
        refreshBtn.setOnAction(e -> generatePreview());
        clearBtn.setOnAction(e -> previewItems.clear());

        previewTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, item) -> {
                if (item != null) {
                    originalArea.setText(item.getOriginal().get());
                    previewArea.setText(item.getPreview().get());
                }
            });

        previewItems.addListener((javafx.collections.ListChangeListener<PreviewItem>) c ->
            statsLabel.setText("预览: " + previewItems.size() + " 个改动"));

        return pane;
    }

    private TableView<PreviewItem> createPreviewTable() {
        TableView<PreviewItem> table = new TableView<>();
        table.setEditable(true);
        table.setItems(previewItems);
        table.setPrefHeight(250);

        TableColumn<PreviewItem, Boolean> acceptCol = new TableColumn<>("接受");
        acceptCol.setCellValueFactory(cellData -> cellData.getValue().getAccept());
        acceptCol.setCellFactory(CheckBoxTableCell.forTableColumn(acceptCol));
        acceptCol.setPrefWidth(60);
        acceptCol.setEditable(true);

        TableColumn<PreviewItem, String> fileCol = new TableColumn<>("文件");
        fileCol.setCellValueFactory(cellData -> cellData.getValue().getFile());
        fileCol.setPrefWidth(200);

        TableColumn<PreviewItem, String> fieldCol = new TableColumn<>("字段");
        fieldCol.setCellValueFactory(cellData -> cellData.getValue().getField());
        fieldCol.setPrefWidth(150);

        TableColumn<PreviewItem, String> originalCol = new TableColumn<>("原值");
        originalCol.setCellValueFactory(cellData -> cellData.getValue().getOriginal());
        originalCol.setPrefWidth(250);

        TableColumn<PreviewItem, String> previewCol = new TableColumn<>("新值");
        previewCol.setCellValueFactory(cellData -> cellData.getValue().getPreview());
        previewCol.setPrefWidth(250);

        table.getColumns().addAll(acceptCol, fileCol, fieldCol, originalCol, previewCol);

        return table;
    }

    private VBox createTemplatePane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // 模板列表
        Label listLabel = new Label("改写模板:");
        ListView<String> templateList = new ListView<>();
        templateList.setPrefHeight(200);

        // 模板操作按钮
        HBox buttonRow = new HBox(10);
        Button loadBtn = new Button("加载");
        Button saveBtn = new Button("保存当前配置为模板");
        Button deleteBtn = new Button("删除");
        Button exportBtn = new Button("导出");
        Button importBtn = new Button("导入");

        buttonRow.getChildren().addAll(loadBtn, saveBtn, deleteBtn,
                                       new Separator(), exportBtn, importBtn);

        // 模板详情
        Label detailLabel = new Label("模板详情:");
        TextArea detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setPrefHeight(200);

        pane.getChildren().addAll(listLabel, templateList, buttonRow,
                                 detailLabel, detailArea);

        // 事件处理
        loadBtn.setOnAction(e -> {
            String selected = templateList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                loadTemplate(selected);
            }
        });

        saveBtn.setOnAction(e -> saveAsTemplate());
        deleteBtn.setOnAction(e -> {
            String selected = templateList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                templateList.getItems().remove(selected);
            }
        });

        return pane;
    }

    private VBox createHistoryPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // 历史列表
        Label listLabel = new Label("改写历史:");
        ListView<String> historyList = new ListView<>();
        historyList.setPrefHeight(300);

        // 历史操作按钮
        HBox buttonRow = new HBox(10);
        Button viewBtn = new Button("查看详情");
        Button undoBtn = new Button("撤销");
        Button clearBtn = new Button("清空历史");

        buttonRow.getChildren().addAll(viewBtn, undoBtn, new Separator(), clearBtn);

        // 历史详情
        Label detailLabel = new Label("操作详情:");
        TextArea detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setPrefHeight(150);

        pane.getChildren().addAll(listLabel, historyList, buttonRow,
                                 detailLabel, detailArea);

        // 加载历史
        updateHistoryList(historyList);

        // 事件处理
        undoBtn.setOnAction(e -> {
            try {
                rewriter.undoLastRewrite();
                updateHistoryList(historyList);
                showAlert("撤销成功", Alert.AlertType.INFORMATION);
            } catch (Exception ex) {
                showAlert("撤销失败: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        clearBtn.setOnAction(e -> {
            rewriter.clearHistory();
            historyList.getItems().clear();
        });

        return pane;
    }

    private HBox createControlBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10));

        previewCheck = new CheckBox("预览模式");
        previewCheck.setSelected(true);

        backupCheck = new CheckBox("备份文件");
        backupCheck.setSelected(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button executeBtn = new Button("执行改写");
        executeBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                           "-fx-font-size: 14; -fx-font-weight: bold;");

        Button cancelBtn = new Button("取消");

        bar.getChildren().addAll(previewCheck, backupCheck, spacer, executeBtn, cancelBtn);

        // 事件处理
        executeBtn.setOnAction(e -> executeRewrite());
        cancelBtn.setOnAction(e -> close());

        return bar;
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

        logArea = new TextArea();
        logArea.setPrefHeight(80);
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10;");

        bar.getChildren().addAll(statusLabel, progressBar);

        VBox wrapper = new VBox(5);
        wrapper.getChildren().addAll(bar, logArea);

        return bar;
    }

    private void setupEventHandlers() {
        // 模式变化时更新UI
        modeCombo.setOnAction(e -> {
            RewriteOptions.RewriteMode mode = modeCombo.getValue();
            boolean aiMode = mode == RewriteOptions.RewriteMode.AI_GENERATE;
            useAICheck.setSelected(aiMode);
            useAICheck.setDisable(aiMode);
        });
    }

    private void generatePreview() {
        if (targetFiles.isEmpty()) {
            showAlert("请先选择要改写的文件", Alert.AlertType.WARNING);
            return;
        }

        if (fieldItems.isEmpty()) {
            showAlert("请添加要改写的字段", Alert.AlertType.WARNING);
            return;
        }

        previewItems.clear();
        progressBar.setVisible(true);
        statusLabel.setText("正在生成预览...");

        Task<List<RewriteResult>> task = new Task<List<RewriteResult>>() {
            @Override
            protected List<RewriteResult> call() throws Exception {
                RewriteOptions options = buildOptions();
                options.setPreview(true);
                return rewriter.batchRewrite(targetFiles, options);
            }

            @Override
            protected void succeeded() {
                List<RewriteResult> results = getValue();
                Platform.runLater(() -> {
                    results.forEach(r -> previewItems.add(new PreviewItem(r)));
                    progressBar.setVisible(false);
                    statusLabel.setText("预览生成完成");
                    log("生成 " + results.size() + " 个预览项");
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("预览生成失败");
                    Throwable ex = getException();
                    log.error("Preview failed", ex);
                    showAlert("预览失败: " + ex.getMessage(), Alert.AlertType.ERROR);
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void executeRewrite() {
        if (targetFiles.isEmpty()) {
            showAlert("请先选择要改写的文件", Alert.AlertType.WARNING);
            return;
        }

        if (fieldItems.isEmpty()) {
            showAlert("请添加要改写的字段", Alert.AlertType.WARNING);
            return;
        }

        // 确认对话框
        if (!previewCheck.isSelected()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("确认执行");
            confirm.setHeaderText("即将执行改写操作");
            confirm.setContentText("将改写 " + targetFiles.size() + " 个文件，是否继续？");
            if (confirm.showAndWait().get() != ButtonType.OK) {
                return;
            }
        }

        progressBar.setVisible(true);
        statusLabel.setText("正在执行改写...");

        Task<List<RewriteResult>> task = new Task<List<RewriteResult>>() {
            @Override
            protected List<RewriteResult> call() throws Exception {
                RewriteOptions options = buildOptions();
                return rewriter.batchRewrite(targetFiles, options);
            }

            @Override
            protected void succeeded() {
                List<RewriteResult> results = getValue();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);

                    long successCount = results.stream()
                        .filter(RewriteResult::isSuccess).count();
                    statusLabel.setText(String.format("改写完成: %d/%d 成功",
                                                     successCount, results.size()));

                    log("改写完成，共 " + results.size() + " 项，成功 " + successCount + " 项");

                    if (previewCheck.isSelected()) {
                        showAlert("改写预览完成\n" +
                                "成功: " + successCount + " 项\n" +
                                "失败: " + (results.size() - successCount) + " 项",
                                Alert.AlertType.INFORMATION);
                    } else {
                        showAlert("改写执行完成\n" +
                                "成功: " + successCount + " 项\n" +
                                "失败: " + (results.size() - successCount) + " 项",
                                Alert.AlertType.INFORMATION);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("改写失败");
                    Throwable ex = getException();
                    log.error("Rewrite failed", ex);
                    showAlert("改写失败: " + ex.getMessage(), Alert.AlertType.ERROR);
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private RewriteOptions buildOptions() {
        RewriteOptions options = new RewriteOptions();
        options.setMode(modeCombo.getValue());
        options.setUseAI(useAICheck.isSelected());
        options.setAiModel(aiModelCombo.getValue());
        options.setAiPrompt(aiPromptArea.getText());
        options.setPreview(previewCheck.isSelected());
        options.setBackup(backupCheck.isSelected());
        options.setCacheAI(cacheAICheck.isSelected());
        options.setCondition(conditionField.getText());

        // 收集目标字段
        List<String> fields = fieldItems.stream()
            .filter(item -> item.getSelected().get())
            .map(item -> item.getFieldName().get())
            .collect(Collectors.toList());
        options.setTargetFields(fields);

        // 收集字段映射
        Map<String, String> mapping = new HashMap<>();
        fieldItems.stream()
            .filter(item -> item.getSelected().get())
            .forEach(item -> {
                String newValue = item.getNewValue().get();
                if (newValue != null && !newValue.isEmpty()) {
                    mapping.put(item.getFieldName().get(), newValue);
                }
            });
        options.setFieldMapping(mapping);

        return options;
    }

    private void autoDetectFields() {
        if (targetFiles.isEmpty()) {
            showAlert("请先选择文件", Alert.AlertType.WARNING);
            return;
        }

        // 分析第一个文件以检测字段
        // 这里简化处理，实际应该分析XML结构
        fieldItems.clear();
        fieldItems.add(new FieldItem("name"));
        fieldItems.add(new FieldItem("description"));
        fieldItems.add(new FieldItem("level"));
        fieldItems.add(new FieldItem("attack"));
        fieldItems.add(new FieldItem("defense"));

        log("自动检测到 " + fieldItems.size() + " 个字段");
    }

    private String getPromptTemplate(String templateName) {
        Map<String, String> templates = new HashMap<>();

        templates.put("武侠风格改写",
            "请将以下游戏内容改写为充满武侠风格的描述：\n" +
            "要求：\n" +
            "1. 使用武侠用语和成语\n" +
            "2. 加入江湖气息\n" +
            "3. 保持原意的基础上增加韵味\n" +
            "4. 适当引用诗词");

        templates.put("科幻风格改写",
            "请将以下内容改写为科幻风格：\n" +
            "要求：\n" +
            "1. 使用科技术语\n" +
            "2. 加入未来感元素\n" +
            "3. 保持逻辑严谨");

        templates.put("游戏描述优化",
            "请优化以下游戏描述文本：\n" +
            "要求：\n" +
            "1. 使描述更加生动有趣\n" +
            "2. 突出物品/技能的特色\n" +
            "3. 保持简洁，不超过100字\n" +
            "4. 符合游戏世界观");

        return templates.getOrDefault(templateName, "");
    }

    private void savePromptAsTemplate() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("保存提示词模板");
        dialog.setHeaderText("输入模板名称");
        dialog.showAndWait().ifPresent(name -> {
            // 保存模板逻辑
            log("保存模板: " + name);
        });
    }

    private void loadTemplate(String templateName) {
        try {
            List<RewriteResult> results = rewriter.rewriteWithTemplate(targetFiles, templateName);
            log("加载模板 " + templateName + " 成功");
        } catch (Exception e) {
            showAlert("加载模板失败: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void saveAsTemplate() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("保存为模板");
        dialog.setHeaderText("输入模板名称");
        dialog.showAndWait().ifPresent(name -> {
            RewriteOptions options = buildOptions();
            List<RewriteTemplate.FieldRule> rules = new ArrayList<>();

            fieldItems.forEach(item -> {
                RewriteTemplate.FieldRule rule = new RewriteTemplate.FieldRule();
                rule.setFieldName(item.getFieldName().get());
                rule.setReplacement(item.getNewValue().get());
                rule.setAiPrompt(item.getAiPrompt().get());
                rules.add(rule);
            });

            rewriter.saveTemplate(name, "用户创建的模板", options, rules);
            log("保存模板: " + name);
        });
    }

    private void loadTemplates() {
        rewriter.loadTemplates();
        // 更新模板列表
    }

    private void updateHistoryList(ListView<String> list) {
        list.getItems().clear();
        List<RewriteHistory> history = rewriter.getHistory();
        history.forEach(h -> {
            String item = String.format("%s - %d 个结果",
                h.getDate(), h.getResults().size());
            list.getItems().add(item);
        });
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText("[" + new Date() + "] " + message + "\n");
        });
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "错误" : "提示");
        alert.setContentText(message);
        alert.showAndWait();
    }
}