package red.jiuzhou.ui.mapping;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.tabmapping.TableMapping;

import java.io.File;

/**
 * 增强版映射编辑对话框
 * 支持 XML文件 ↔ 客户端表 ↔ 服务端表 的完整映射配置
 *
 * 新增功能:
 * - XML文件选择和关联
 * - 映射类型选择
 * - 状态管理
 * - 描述和备注
 * - 时间戳管理
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 2.0
 */
public class EnhancedMappingEditDialog extends Dialog<TableMapping> {

    private static final Logger log = LoggerFactory.getLogger(EnhancedMappingEditDialog.class);

    // XML文件信息
    private TextField xmlFilePathField;
    private TextField xmlFileNameField;
    private TextField xmlNodePathField;

    // 表名字段
    private TextField cltTabField;
    private TextField svrTabField;

    // 映射配置
    private ComboBox<String> mappingTypeCombo;
    private ComboBox<String> statusCombo;
    private TextArea descriptionArea;

    // 字段映射
    private TextArea sameFieldsArea;
    private TextArea cltRedundantArea;
    private TextArea svrRedundantArea;

    private final boolean isEditMode;
    private final TableMapping originalMapping;
    private final Stage ownerStage;

    public EnhancedMappingEditDialog(Stage owner, TableMapping mapping) {
        this.ownerStage = owner;
        this.originalMapping = mapping;
        this.isEditMode = (mapping != null);

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(isEditMode ? "✏️ 编辑映射配置" : "➕ 新建映射配置");
        setResizable(true);

        VBox content = createDialogContent();
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(900, 700);

        ButtonType saveButtonType = new ButtonType("💾 保存", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("❌ 取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, cancelButtonType);

        Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);

        cltTabField.textProperty().addListener((obs, oldVal, newVal) ->
            saveButton.setDisable(!isFormValid()));
        svrTabField.textProperty().addListener((obs, oldVal, newVal) ->
            saveButton.setDisable(!isFormValid()));

        if (isEditMode) {
            fillFormData(mapping);
            saveButton.setDisable(false);
        }

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return createMappingFromForm();
            }
            return null;
        });
    }

    private VBox createDialogContent() {
        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(20));

        Label titleLabel = new Label(isEditMode ? "编辑映射配置" : "新建映射配置");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label descLabel = new Label(
            "配置 XML文件、客户端表、服务端表 三者之间的映射关系。\n" +
            "支持复杂的表名映射和字段映射配置。"
        );
        descLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        descLabel.setWrapText(true);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // 基本信息标签页
        Tab basicTab = new Tab("📋 基本信息");
        basicTab.setContent(createBasicInfoPane());

        // 字段映射标签页
        Tab fieldsTab = new Tab("📝 字段映射");
        fieldsTab.setContent(createFieldMappingPane());

        // 高级设置标签页
        Tab advancedTab = new Tab("⚙️ 高级设置");
        advancedTab.setContent(createAdvancedPane());

        tabPane.getTabs().addAll(basicTab, fieldsTab, advancedTab);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        mainBox.getChildren().addAll(titleLabel, descLabel, new Separator(), tabPane);
        return mainBox;
    }

    /**
     * 创建基本信息面板
     */
    private VBox createBasicInfoPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(15));

        // XML文件配置
        VBox xmlSection = new VBox(10);
        Label xmlLabel = new Label("📄 XML源文件配置");
        xmlLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        GridPane xmlGrid = new GridPane();
        xmlGrid.setHgap(10);
        xmlGrid.setVgap(10);
        xmlGrid.setPadding(new Insets(10));

        Label xmlPathLabel = new Label("文件路径:");
        xmlPathLabel.setMinWidth(100);
        xmlFilePathField = new TextField();
        xmlFilePathField.setPromptText("D:\\path\\to\\items.xml");
        xmlFilePathField.setPrefWidth(500);

        Button browseBtn = new Button("📂 浏览");
        browseBtn.setOnAction(e -> browseXmlFile());

        HBox xmlPathBox = new HBox(5, xmlFilePathField, browseBtn);
        HBox.setHgrow(xmlFilePathField, Priority.ALWAYS);

        Label xmlNameLabel = new Label("文件名:");
        xmlFileNameField = new TextField();
        xmlFileNameField.setPromptText("items.xml");

        Label xmlNodeLabel = new Label("节点路径:");
        xmlNodePathField = new TextField();
        xmlNodePathField.setPromptText("item_templates (可选)");

        xmlGrid.add(xmlPathLabel, 0, 0);
        xmlGrid.add(xmlPathBox, 1, 0);
        xmlGrid.add(xmlNameLabel, 0, 1);
        xmlGrid.add(xmlFileNameField, 1, 1);
        xmlGrid.add(xmlNodeLabel, 0, 2);
        xmlGrid.add(xmlNodePathField, 1, 2);

        xmlSection.getChildren().addAll(xmlLabel, xmlGrid);

        // 表名配置
        VBox tableSection = new VBox(10);
        Label tableLabel = new Label("📦 表名映射配置");
        tableLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        GridPane tableGrid = new GridPane();
        tableGrid.setHgap(10);
        tableGrid.setVgap(10);
        tableGrid.setPadding(new Insets(10));

        Label cltLabel = new Label("客户端表名: *");
        cltLabel.setMinWidth(100);
        cltTabField = new TextField();
        cltTabField.setPromptText("client_item_misc");
        cltTabField.setPrefWidth(500);

        Label svrLabel = new Label("服务端表名: *");
        svrLabel.setMinWidth(100);
        svrTabField = new TextField();
        svrTabField.setPromptText("item_misc");
        svrTabField.setPrefWidth(500);

        tableGrid.add(cltLabel, 0, 0);
        tableGrid.add(cltTabField, 1, 0);
        tableGrid.add(svrLabel, 0, 1);
        tableGrid.add(svrTabField, 1, 1);

        // 表名映射说明示例
        VBox exampleBox = createExampleBox();

        tableSection.getChildren().addAll(tableLabel, tableGrid, exampleBox);

        pane.getChildren().addAll(xmlSection, new Separator(), tableSection);
        return pane;
    }

    /**
     * 创建示例说明框
     */
    private VBox createExampleBox() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #f0f8ff; -fx-border-color: #4682b4; " +
                    "-fx-border-radius: 5; -fx-background-radius: 5;");

        Label titleLabel = new Label("💡 映射示例说明");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label example1 = new Label("• items.xml → client_item_misc ↔ item_misc");
        Label example2 = new Label("• assembly.xml → client_assembly_items ↔ assembly_items");
        Label example3 = new Label("• world.xml → client_world__npcs ↔ world__npc_spawn__territory");
        example1.setStyle("-fx-font-size: 10px;");
        example2.setStyle("-fx-font-size: 10px;");
        example3.setStyle("-fx-font-size: 10px;");

        box.getChildren().addAll(titleLabel, example1, example2, example3);
        return box;
    }

    /**
     * 创建字段映射面板
     */
    private VBox createFieldMappingPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(15));

        Label hintLabel = new Label("配置客户端表和服务端表之间的字段映射关系");
        hintLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        Label sameLabel = new Label("✅ 共同字段 (两端都存在的字段) *");
        sameLabel.setStyle("-fx-font-weight: bold;");
        sameFieldsArea = new TextArea();
        sameFieldsArea.setPromptText("id, name, desc, level, type\n(多个字段用英文逗号分隔)");
        sameFieldsArea.setPrefRowCount(4);
        sameFieldsArea.setWrapText(true);

        Label cltRedundantLabel = new Label("⚠️ 客户端冗余字段 (仅客户端存在)");
        cltRedundantArea = new TextArea();
        cltRedundantArea.setPromptText("client_display_name, client_icon_path");
        cltRedundantArea.setPrefRowCount(3);
        cltRedundantArea.setWrapText(true);

        Label svrRedundantLabel = new Label("⚠️ 服务端冗余字段 (仅服务端存在)");
        svrRedundantArea = new TextArea();
        svrRedundantArea.setPromptText("server_cache_key, server_internal_id");
        svrRedundantArea.setPrefRowCount(3);
        svrRedundantArea.setWrapText(true);

        HBox quickActions = createFieldQuickActions();

        VBox.setVgrow(sameFieldsArea, Priority.SOMETIMES);

        pane.getChildren().addAll(
            hintLabel, new Separator(),
            sameLabel, sameFieldsArea,
            cltRedundantLabel, cltRedundantArea,
            svrRedundantLabel, svrRedundantArea,
            new Separator(), quickActions
        );

        return pane;
    }

    /**
     * 创建字段快捷操作
     */
    private HBox createFieldQuickActions() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("💡 快捷操作:");
        label.setStyle("-fx-font-weight: bold;");

        Button formatBtn = new Button("🎨 格式化");
        formatBtn.setOnAction(e -> formatFields());

        Button validateBtn = new Button("✔️ 验证");
        validateBtn.setOnAction(e -> validateFields());

        Button suggestBtn = new Button("💡 智能建议");
        suggestBtn.setTooltip(new Tooltip("根据表名自动建议字段映射"));
        suggestBtn.setOnAction(e -> suggestFields());

        box.getChildren().addAll(label, formatBtn, validateBtn, suggestBtn);
        return box;
    }

    /**
     * 创建高级设置面板
     */
    private VBox createAdvancedPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(15));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);

        Label typeLabel = new Label("映射类型:");
        typeLabel.setMinWidth(100);
        mappingTypeCombo = new ComboBox<>();
        mappingTypeCombo.getItems().addAll(
            "direct - 直接映射",
            "complex - 复杂映射",
            "nested - 嵌套映射",
            "merged - 合并映射"
        );
        mappingTypeCombo.setValue("direct - 直接映射");
        mappingTypeCombo.setPrefWidth(300);

        Label statusLabel = new Label("状态:");
        statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll(
            "active - 激活",
            "inactive - 未激活",
            "deprecated - 已废弃"
        );
        statusCombo.setValue("active - 激活");
        statusCombo.setPrefWidth(300);

        grid.add(typeLabel, 0, 0);
        grid.add(mappingTypeCombo, 1, 0);
        grid.add(statusLabel, 0, 1);
        grid.add(statusCombo, 1, 1);

        Label descLabel = new Label("描述备注:");
        descLabel.setStyle("-fx-font-weight: bold;");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("输入该映射的描述信息、注意事项等...");
        descriptionArea.setPrefRowCount(5);
        descriptionArea.setWrapText(true);
        VBox.setVgrow(descriptionArea, Priority.ALWAYS);

        pane.getChildren().addAll(grid, new Separator(), descLabel, descriptionArea);
        return pane;
    }

    private void browseXmlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择XML文件");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("XML文件", "*.xml")
        );

        File selectedFile = fileChooser.showOpenDialog(ownerStage);
        if (selectedFile != null) {
            xmlFilePathField.setText(selectedFile.getAbsolutePath());
            xmlFileNameField.setText(selectedFile.getName());
        }
    }

    private void formatFields() {
        sameFieldsArea.setText(cleanFieldText(sameFieldsArea.getText()));
        cltRedundantArea.setText(cleanFieldText(cltRedundantArea.getText()));
        svrRedundantArea.setText(cleanFieldText(svrRedundantArea.getText()));
        showInfo("格式化完成", "已自动整理字段格式");
    }

    private void validateFields() {
        StringBuilder report = new StringBuilder("字段验证报告:\n\n");

        String sameFields = sameFieldsArea.getText();
        if (sameFields != null && !sameFields.trim().isEmpty()) {
            String[] fields = sameFields.split(",");
            report.append("✅ 共同字段数: ").append(fields.length).append("\n");

            long uniqueCount = java.util.Arrays.stream(fields)
                .map(String::trim)
                .distinct()
                .count();
            if (uniqueCount < fields.length) {
                report.append("⚠️ 发现重复字段\n");
            }
        } else {
            report.append("⚠️ 未填写共同字段\n");
        }

        int cltRedundant = countFields(cltRedundantArea.getText());
        int svrRedundant = countFields(svrRedundantArea.getText());

        report.append("📊 客户端冗余: ").append(cltRedundant).append("\n");
        report.append("📊 服务端冗余: ").append(svrRedundant).append("\n");

        showInfo("验证结果", report.toString());
    }

    private void suggestFields() {
        showInfo("智能建议",
            "智能字段建议功能开发中...\n\n" +
            "即将支持:\n" +
            "• 根据表名自动推荐字段\n" +
            "• 从XML文件解析字段结构\n" +
            "• 从数据库读取表结构\n" +
            "• AI辅助字段映射"
        );
    }

    private void fillFormData(TableMapping mapping) {
        if (mapping.getXmlFilePath() != null) {
            xmlFilePathField.setText(mapping.getXmlFilePath());
        }
        if (mapping.getXmlFileName() != null) {
            xmlFileNameField.setText(mapping.getXmlFileName());
        }
        if (mapping.getXmlNodePath() != null) {
            xmlNodePathField.setText(mapping.getXmlNodePath());
        }

        cltTabField.setText(mapping.getCltTab());
        svrTabField.setText(mapping.getSvrTab());
        sameFieldsArea.setText(mapping.getSameFileds());
        cltRedundantArea.setText(mapping.getCltRedundantFields());
        svrRedundantArea.setText(mapping.getSvrRedundantFields());

        if (mapping.getMappingType() != null) {
            String typeValue = mapping.getMappingType() + " - " +
                             mapping.getMappingTypeDisplay();
            mappingTypeCombo.setValue(typeValue);
        }

        if (mapping.getStatus() != null) {
            String statusValue = mapping.getStatus() + " - " +
                               mapping.getStatusDisplay().replace("✅ ", "")
                                   .replace("⏸️ ", "").replace("⚠️ ", "");
            statusCombo.setValue(statusValue);
        }

        if (mapping.getDescription() != null) {
            descriptionArea.setText(mapping.getDescription());
        }
    }

    private TableMapping createMappingFromForm() {
        TableMapping mapping = new TableMapping();

        // XML信息
        mapping.setXmlFilePath(cleanText(xmlFilePathField.getText()));
        mapping.setXmlFileName(cleanText(xmlFileNameField.getText()));
        mapping.setXmlNodePath(cleanText(xmlNodePathField.getText()));

        // 表名
        mapping.setCltTab(cltTabField.getText().trim());
        mapping.setSvrTab(svrTabField.getText().trim());

        // 字段映射
        mapping.setSameFileds(cleanFieldText(sameFieldsArea.getText()));
        mapping.setCltRedundantFields(cleanFieldText(cltRedundantArea.getText()));
        mapping.setSvrRedundantFields(cleanFieldText(svrRedundantArea.getText()));

        // 高级配置
        String typeValue = mappingTypeCombo.getValue();
        if (typeValue != null) {
            mapping.setMappingType(typeValue.split(" - ")[0]);
        }

        String statusValue = statusCombo.getValue();
        if (statusValue != null) {
            mapping.setStatus(statusValue.split(" - ")[0]);
        }

        mapping.setDescription(cleanText(descriptionArea.getText()));

        // 时间戳
        if (isEditMode && originalMapping.getCreatedTime() > 0) {
            mapping.setCreatedTime(originalMapping.getCreatedTime());
        } else {
            mapping.setCreatedTime(System.currentTimeMillis());
        }
        mapping.setUpdatedTime(System.currentTimeMillis());

        return mapping;
    }

    private String cleanText(String text) {
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private String cleanFieldText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        return text.trim().replaceAll("\\s*,\\s*", ", ");
    }

    private boolean isFormValid() {
        String cltTab = cltTabField.getText();
        String svrTab = svrTabField.getText();
        return cltTab != null && !cltTab.trim().isEmpty()
            && svrTab != null && !svrTab.trim().isEmpty();
    }

    private int countFields(String fieldText) {
        if (fieldText == null || fieldText.trim().isEmpty()) {
            return 0;
        }
        return fieldText.split(",").length;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
