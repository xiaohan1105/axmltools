package red.jiuzhou.ui.wizards;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 智能数据导入向导
 * 提供分步骤的可视化数据导入流程，让设计师能够直观地理解和控制导入过程
 *
 * 向导步骤：
 * 1. 文件选择和验证
 * 2. 数据预览和映射配置
 * 3. AI处理选项配置
 * 4. 导入执行和进度监控
 * 5. 结果确认和后续操作
 */
public class DataImportWizard {

    private static final Logger log = LoggerFactory.getLogger(DataImportWizard.class);

    private final Stage primaryStage;
    private Stage wizardStage;

    // 向导数据
    private final ImportConfig importConfig = new ImportConfig();

    // UI组件
    private VBox mainContainer;
    private StackPane contentPane;
    private HBox navigationPane;
    private ProgressIndicator stepProgress;
    private Label stepLabel;

    // 步骤管理
    private final List<WizardStep> steps = new ArrayList<>();
    private int currentStepIndex = 0;

    public DataImportWizard(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initializeSteps();
    }

    /**
     * 显示导入向导
     */
    public void show() {
        createWizardWindow();
        showCurrentStep();
        wizardStage.show();
    }

    /**
     * 初始化向导步骤
     */
    private void initializeSteps() {
        steps.add(new FileSelectionStep());
        steps.add(new DataPreviewStep());
        steps.add(new AIConfigurationStep());
        steps.add(new ImportExecutionStep());
        steps.add(new ResultConfirmationStep());
    }

    /**
     * 创建向导窗口
     */
    private void createWizardWindow() {
        wizardStage = new Stage();
        wizardStage.initModality(Modality.APPLICATION_MODAL);
        wizardStage.initOwner(primaryStage);
        wizardStage.setTitle("智能数据导入向导");
        wizardStage.setResizable(false);

        mainContainer = new VBox();
        mainContainer.getStyleClass().add("wizard-container");

        // 创建头部
        VBox header = createHeader();

        // 创建内容区域
        contentPane = new StackPane();
        contentPane.getStyleClass().add("wizard-content");
        contentPane.setPrefSize(800, 500);

        // 创建导航区域
        navigationPane = createNavigation();

        mainContainer.getChildren().addAll(header, contentPane, navigationPane);

        Scene scene = new Scene(mainContainer);
        scene.getStylesheets().add("/modern-theme.css");
        wizardStage.setScene(scene);
    }

    /**
     * 创建头部区域
     */
    private VBox createHeader() {
        VBox header = new VBox();
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(20, 24, 16, 24));

        // 标题
        Label titleLabel = new Label("智能数据导入向导");
        titleLabel.getStyleClass().add("wizard-title");

        // 步骤指示器
        HBox stepIndicator = createStepIndicator();

        // 当前步骤描述
        stepLabel = new Label();
        stepLabel.getStyleClass().add("step-description");

        header.getChildren().addAll(titleLabel, stepIndicator, stepLabel);
        return header;
    }

    /**
     * 创建步骤指示器
     */
    private HBox createStepIndicator() {
        HBox indicator = new HBox();
        indicator.getStyleClass().add("step-indicator");
        indicator.setAlignment(Pos.CENTER);
        indicator.setSpacing(8);

        for (int i = 0; i < steps.size(); i++) {
            // 步骤圆点
            Region stepDot = new Region();
            stepDot.getStyleClass().addAll("step-dot", i == 0 ? "active" : "inactive");
            stepDot.setPrefSize(12, 12);

            indicator.getChildren().add(stepDot);

            // 连接线（除了最后一个步骤）
            if (i < steps.size() - 1) {
                Region connector = new Region();
                connector.getStyleClass().add("step-connector");
                connector.setPrefWidth(60);
                connector.setPrefHeight(2);
                indicator.getChildren().add(connector);
            }
        }

        return indicator;
    }

    /**
     * 创建导航区域
     */
    private HBox createNavigation() {
        HBox navigation = new HBox();
        navigation.getStyleClass().add("wizard-navigation");
        navigation.setPadding(new Insets(16, 24, 20, 24));
        navigation.setSpacing(12);
        navigation.setAlignment(Pos.CENTER_RIGHT);

        Button cancelButton = new Button("取消");
        cancelButton.getStyleClass().addAll("wizard-button", "cancel-button");
        cancelButton.setOnAction(e -> wizardStage.close());

        Button prevButton = new Button("⬅️ 上一步");
        prevButton.getStyleClass().addAll("wizard-button", "secondary");
        prevButton.setOnAction(e -> previousStep());

        Button nextButton = new Button("下一步 ➡️");
        nextButton.getStyleClass().addAll("wizard-button", "primary");
        nextButton.setOnAction(e -> nextStep());

        Button finishButton = new Button("完成");
        finishButton.getStyleClass().addAll("wizard-button", "success");
        finishButton.setOnAction(e -> finishWizard());
        finishButton.setVisible(false);

        // 添加弹性空间
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        navigation.getChildren().addAll(spacer, cancelButton, prevButton, nextButton, finishButton);

        // 保存按钮引用以便更新状态
        prevButton.setUserData("prev");
        nextButton.setUserData("next");
        finishButton.setUserData("finish");

        return navigation;
    }

    /**
     * 显示当前步骤
     */
    private void showCurrentStep() {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) return;

        WizardStep currentStep = steps.get(currentStepIndex);

        // 更新步骤描述
        stepLabel.setText(String.format("第 %d 步：%s",
            currentStepIndex + 1, currentStep.getTitle()));

        // 更新步骤指示器
        updateStepIndicator();

        // 显示步骤内容
        contentPane.getChildren().clear();
        contentPane.getChildren().add(currentStep.createContent());

        // 更新导航按钮状态
        updateNavigationButtons();
    }

    /**
     * 更新步骤指示器
     */
    private void updateStepIndicator() {
        HBox indicator = (HBox) ((VBox) mainContainer.getChildren().get(0))
            .getChildren().get(1);

        int dotIndex = 0;
        for (int i = 0; i < indicator.getChildren().size(); i += 2) {
            Region dot = (Region) indicator.getChildren().get(i);
            dot.getStyleClass().removeAll("active", "completed", "inactive");

            if (dotIndex < currentStepIndex) {
                dot.getStyleClass().add("completed");
            } else if (dotIndex == currentStepIndex) {
                dot.getStyleClass().add("active");
            } else {
                dot.getStyleClass().add("inactive");
            }
            dotIndex++;
        }
    }

    /**
     * 更新导航按钮状态
     */
    private void updateNavigationButtons() {
        navigationPane.getChildren().forEach(node -> {
            if (node instanceof Button) {
                Button button = (Button) node;
                String userData = (String) button.getUserData();

                if ("prev".equals(userData)) {
                    button.setDisable(currentStepIndex <= 0);
                } else if ("next".equals(userData)) {
                    button.setVisible(currentStepIndex < steps.size() - 1);
                    button.setDisable(!canProceedToNext());
                } else if ("finish".equals(userData)) {
                    button.setVisible(currentStepIndex == steps.size() - 1);
                }
            }
        });
    }

    /**
     * 检查是否可以进入下一步
     */
    private boolean canProceedToNext() {
        if (currentStepIndex >= steps.size()) return false;
        return steps.get(currentStepIndex).validate();
    }

    /**
     * 进入下一步
     */
    private void nextStep() {
        if (currentStepIndex < steps.size() - 1 && canProceedToNext()) {
            // 保存当前步骤的数据
            steps.get(currentStepIndex).saveData(importConfig);

            currentStepIndex++;
            showCurrentStep();
        }
    }

    /**
     * 返回上一步
     */
    private void previousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--;
            showCurrentStep();
        }
    }

    /**
     * 完成向导
     */
    private void finishWizard() {
        // 保存最后一步的数据
        steps.get(currentStepIndex).saveData(importConfig);

        // 显示完成确认
        Alert confirmation = new Alert(Alert.AlertType.INFORMATION);
        confirmation.setTitle("导入完成");
        confirmation.setHeaderText("数据导入已成功完成");
        confirmation.setContentText("共导入 " + importConfig.getProcessedRecords() + " 条记录。\n" +
            "您可以在数据表格中查看导入的数据。");

        confirmation.showAndWait();
        wizardStage.close();
    }

    // ========== 内部类：导入配置 ==========

    public static class ImportConfig {
        private StringProperty sourceFile = new SimpleStringProperty();
        private StringProperty targetTable = new SimpleStringProperty();
        private boolean useAI = false;
        private String aiModel = "";
        private List<String> selectedColumns = new ArrayList<>();
        private int processedRecords = 0;

        // Getters and Setters
        public String getSourceFile() { return sourceFile.get(); }
        public void setSourceFile(String sourceFile) { this.sourceFile.set(sourceFile); }
        public StringProperty sourceFileProperty() { return sourceFile; }

        public String getTargetTable() { return targetTable.get(); }
        public void setTargetTable(String targetTable) { this.targetTable.set(targetTable); }
        public StringProperty targetTableProperty() { return targetTable; }

        public boolean isUseAI() { return useAI; }
        public void setUseAI(boolean useAI) { this.useAI = useAI; }

        public String getAiModel() { return aiModel; }
        public void setAiModel(String aiModel) { this.aiModel = aiModel; }

        public List<String> getSelectedColumns() { return selectedColumns; }
        public void setSelectedColumns(List<String> selectedColumns) { this.selectedColumns = selectedColumns; }

        public int getProcessedRecords() { return processedRecords; }
        public void setProcessedRecords(int processedRecords) { this.processedRecords = processedRecords; }
    }

    // ========== 抽象步骤类 ==========

    public abstract static class WizardStep {
        public abstract String getTitle();
        public abstract VBox createContent();
        public abstract boolean validate();
        public abstract void saveData(ImportConfig config);
    }

    // ========== 具体步骤实现 ==========

    /**
     * 步骤1：文件选择
     */
    private class FileSelectionStep extends WizardStep {
        private TextField filePathField;
        private Label fileInfoLabel;
        private File selectedFile;

        @Override
        public String getTitle() {
            return "选择要导入的XML文件";
        }

        @Override
        public VBox createContent() {
            VBox content = new VBox(20);
            content.setPadding(new Insets(30));
            content.getStyleClass().add("wizard-step-content");

            // 标题和说明
            Label titleLabel = new Label("📁 选择数据文件");
            titleLabel.getStyleClass().add("step-title");

            Label descLabel = new Label("请选择要导入的XML数据文件。支持UTF-8和UTF-16编码格式。");
            descLabel.getStyleClass().add("step-description");

            // 文件选择区域
            HBox fileSelection = new HBox(12);
            fileSelection.setAlignment(Pos.CENTER_LEFT);

            filePathField = new TextField();
            filePathField.setPromptText("点击选择文件或直接输入文件路径");
            filePathField.setPrefWidth(400);
            filePathField.setEditable(false);

            Button browseButton = new Button("浏览...");
            browseButton.getStyleClass().addAll("browse-button", "primary");
            browseButton.setOnAction(e -> browseFile());

            fileSelection.getChildren().addAll(filePathField, browseButton);

            // 文件信息显示
            fileInfoLabel = new Label();
            fileInfoLabel.getStyleClass().add("file-info");
            fileInfoLabel.setVisible(false);

            // 拖拽提示区域
            VBox dropZone = new VBox();
            dropZone.getStyleClass().add("drop-zone");
            dropZone.setAlignment(Pos.CENTER);
            dropZone.setPrefHeight(120);

            Label dropLabel = new Label("📄 拖拽XML文件到此处");
            dropLabel.getStyleClass().add("drop-hint");
            Label dropSubLabel = new Label("或使用上方的浏览按钮选择文件");
            dropSubLabel.getStyleClass().add("drop-sub-hint");

            dropZone.getChildren().addAll(dropLabel, dropSubLabel);

            content.getChildren().addAll(
                titleLabel, descLabel, fileSelection, fileInfoLabel, dropZone
            );

            return content;
        }

        private void browseFile() {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择XML文件");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML文件", "*.xml")
            );

            selectedFile = fileChooser.showOpenDialog(wizardStage);
            if (selectedFile != null) {
                filePathField.setText(selectedFile.getAbsolutePath());
                updateFileInfo();
            }
        }

        private void updateFileInfo() {
            if (selectedFile != null && selectedFile.exists()) {
                long fileSize = selectedFile.length();
                String sizeText = formatFileSize(fileSize);
                fileInfoLabel.setText(String.format("✅ 文件大小: %s | 最后修改: %s",
                    sizeText, new java.util.Date(selectedFile.lastModified())));
                fileInfoLabel.setVisible(true);
            }
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }

        @Override
        public boolean validate() {
            return selectedFile != null && selectedFile.exists() && selectedFile.getName().endsWith(".xml");
        }

        @Override
        public void saveData(ImportConfig config) {
            if (selectedFile != null) {
                config.setSourceFile(selectedFile.getAbsolutePath());
            }
        }
    }

    /**
     * 步骤2：数据预览
     */
    private class DataPreviewStep extends WizardStep {
        @Override
        public String getTitle() {
            return "预览数据和配置映射";
        }

        @Override
        public VBox createContent() {
            VBox content = new VBox(20);
            content.setPadding(new Insets(30));

            Label titleLabel = new Label("📊 数据预览");
            titleLabel.getStyleClass().add("step-title");

            // TODO: 实现数据预览表格
            Label placeholder = new Label("数据预览功能开发中...\n" +
                "将显示：\n" +
                "• XML文件结构预览\n" +
                "• 字段映射配置\n" +
                "• 数据类型验证");
            placeholder.getStyleClass().add("placeholder-text");

            content.getChildren().addAll(titleLabel, placeholder);
            return content;
        }

        @Override
        public boolean validate() {
            return true; // 暂时总是返回true
        }

        @Override
        public void saveData(ImportConfig config) {
            // TODO: 保存映射配置
        }
    }

    /**
     * 步骤3：AI配置
     */
    private class AIConfigurationStep extends WizardStep {
        @Override
        public String getTitle() {
            return "配置AI处理选项";
        }

        @Override
        public VBox createContent() {
            VBox content = new VBox(20);
            content.setPadding(new Insets(30));

            Label titleLabel = new Label("🤖 AI增强处理");
            titleLabel.getStyleClass().add("step-title");

            // TODO: 实现AI配置界面
            Label placeholder = new Label("AI配置功能开发中...\n" +
                "将支持：\n" +
                "• 智能数据清洗\n" +
                "• 自动格式转换\n" +
                "• 数据质量优化");
            placeholder.getStyleClass().add("placeholder-text");

            content.getChildren().addAll(titleLabel, placeholder);
            return content;
        }

        @Override
        public boolean validate() {
            return true;
        }

        @Override
        public void saveData(ImportConfig config) {
            // TODO: 保存AI配置
        }
    }

    /**
     * 步骤4：执行导入
     */
    private class ImportExecutionStep extends WizardStep {
        @Override
        public String getTitle() {
            return "执行数据导入";
        }

        @Override
        public VBox createContent() {
            VBox content = new VBox(20);
            content.setPadding(new Insets(30));

            Label titleLabel = new Label("⚡ 正在导入数据");
            titleLabel.getStyleClass().add("step-title");

            // TODO: 实现导入进度显示
            ProgressBar progressBar = new ProgressBar(0.75);
            progressBar.setPrefWidth(400);

            Label statusLabel = new Label("正在处理数据... (75%)");
            statusLabel.getStyleClass().add("progress-status");

            content.getChildren().addAll(titleLabel, progressBar, statusLabel);
            return content;
        }

        @Override
        public boolean validate() {
            return true;
        }

        @Override
        public void saveData(ImportConfig config) {
            config.setProcessedRecords(1250); // 模拟处理记录数
        }
    }

    /**
     * 步骤5：结果确认
     */
    private class ResultConfirmationStep extends WizardStep {
        @Override
        public String getTitle() {
            return "确认导入结果";
        }

        @Override
        public VBox createContent() {
            VBox content = new VBox(20);
            content.setPadding(new Insets(30));

            Label titleLabel = new Label("✅ 导入完成");
            titleLabel.getStyleClass().add("step-title");

            // 结果统计
            GridPane statsGrid = new GridPane();
            statsGrid.setHgap(20);
            statsGrid.setVgap(10);
            statsGrid.getStyleClass().add("stats-grid");

            addStatRow(statsGrid, 0, "成功导入:", "1,250 条记录");
            addStatRow(statsGrid, 1, "处理时间:", "2分30秒");
            addStatRow(statsGrid, 2, "数据质量:", "优秀 (98.5%)");
            addStatRow(statsGrid, 3, "目标表:", importConfig.getTargetTable());

            content.getChildren().addAll(titleLabel, statsGrid);
            return content;
        }

        private void addStatRow(GridPane grid, int row, String label, String value) {
            Label labelNode = new Label(label);
            labelNode.getStyleClass().add("stat-label");

            Label valueNode = new Label(value);
            valueNode.getStyleClass().add("stat-value");

            grid.add(labelNode, 0, row);
            grid.add(valueNode, 1, row);
        }

        @Override
        public boolean validate() {
            return true;
        }

        @Override
        public void saveData(ImportConfig config) {
            // 最终确认，无需额外保存
        }
    }
}