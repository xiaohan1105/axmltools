package red.jiuzhou.ui.mapping;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.tabmapping.TableMapping;

import java.util.Optional;

/**
 * 映射编辑对话框
 * 用于新建和编辑表映射配置
 *
 * 功能特点:
 * - 直观的表单界面
 * - 字段输入验证
 * - 字段辅助输入
 * - 实时预览
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class MappingEditDialog extends Dialog<TableMapping> {

    private static final Logger log = LoggerFactory.getLogger(MappingEditDialog.class);

    // 输入字段
    private TextField cltTabField;
    private TextField svrTabField;
    private TextArea sameFieldsArea;
    private TextArea cltRedundantArea;
    private TextArea svrRedundantArea;

    // 编辑模式
    private final boolean isEditMode;
    private final TableMapping originalMapping;

    /**
     * 构造函数
     *
     * @param owner 父窗口
     * @param mapping 要编辑的映射(null表示新建)
     */
    public MappingEditDialog(Stage owner, TableMapping mapping) {
        this.originalMapping = mapping;
        this.isEditMode = (mapping != null);

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(isEditMode ? "✏️ 编辑表映射" : "➕ 新建表映射");
        setResizable(true);

        // 创建对话框内容
        VBox content = createDialogContent();

        // 设置对话框面板
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(800, 600);

        // 添加按钮
        ButtonType saveButtonType = new ButtonType("💾 保存", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("❌ 取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, cancelButtonType);

        // 禁用保存按钮直到数据有效
        Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);

        // 添加验证监听器
        cltTabField.textProperty().addListener((obs, oldVal, newVal) -> {
            saveButton.setDisable(!isFormValid());
        });
        svrTabField.textProperty().addListener((obs, oldVal, newVal) -> {
            saveButton.setDisable(!isFormValid());
        });

        // 如果是编辑模式,填充现有数据
        if (isEditMode) {
            fillFormData(mapping);
            saveButton.setDisable(false);
        }

        // 设置结果转换器
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return createMappingFromForm();
            }
            return null;
        });
    }

    /**
     * 创建对话框内容
     */
    private VBox createDialogContent() {
        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(20));

        // 标题和说明
        Label titleLabel = new Label(isEditMode ? "编辑表映射配置" : "新建表映射配置");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label descLabel = new Label(
            "请填写客户端表和服务器表的映射关系。字段名使用英文逗号分隔。\n" +
            "提示: 共同字段是两端都存在的字段,冗余字段是只在一端存在的字段。"
        );
        descLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        descLabel.setWrapText(true);

        // 表名配置区域
        VBox tableSection = createTableNameSection();

        // 字段配置区域
        VBox fieldSection = createFieldSection();

        // 快捷操作区域
        HBox quickActionsBox = createQuickActionsBar();

        mainBox.getChildren().addAll(
            titleLabel,
            descLabel,
            new Separator(),
            tableSection,
            new Separator(),
            fieldSection,
            quickActionsBox
        );

        return mainBox;
    }

    /**
     * 创建表名配置区域
     */
    private VBox createTableNameSection() {
        VBox section = new VBox(10);

        Label sectionLabel = new Label("📦 表名配置");
        sectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // 客户端表名
        Label cltLabel = new Label("客户端表名:");
        cltLabel.setMinWidth(100);
        cltTabField = new TextField();
        cltTabField.setPromptText("例如: client_item_templates");
        cltTabField.setPrefWidth(400);

        // 服务器表名
        Label svrLabel = new Label("服务器表名:");
        svrLabel.setMinWidth(100);
        svrTabField = new TextField();
        svrTabField.setPromptText("例如: item_templates");
        svrTabField.setPrefWidth(400);

        grid.add(cltLabel, 0, 0);
        grid.add(cltTabField, 1, 0);
        grid.add(svrLabel, 0, 1);
        grid.add(svrTabField, 1, 1);

        section.getChildren().addAll(sectionLabel, grid);
        return section;
    }

    /**
     * 创建字段配置区域
     */
    private VBox createFieldSection() {
        VBox section = new VBox(10);

        Label sectionLabel = new Label("📝 字段映射配置");
        sectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // 共同字段
        Label sameLabel = new Label("✅ 共同字段 (两端都存在的字段):");
        sameFieldsArea = new TextArea();
        sameFieldsArea.setPromptText("多个字段用英文逗号分隔,例如: id, name, desc, level");
        sameFieldsArea.setPrefRowCount(3);
        sameFieldsArea.setWrapText(true);

        // 客户端冗余字段
        Label cltRedundantLabel = new Label("⚠️ 客户端冗余字段 (仅客户端存在):");
        cltRedundantArea = new TextArea();
        cltRedundantArea.setPromptText("例如: client_only_field1, client_only_field2");
        cltRedundantArea.setPrefRowCount(2);
        cltRedundantArea.setWrapText(true);

        // 服务器冗余字段
        Label svrRedundantLabel = new Label("⚠️ 服务器冗余字段 (仅服务器存在):");
        svrRedundantArea = new TextArea();
        svrRedundantArea.setPromptText("例如: server_only_field1, server_only_field2");
        svrRedundantArea.setPrefRowCount(2);
        svrRedundantArea.setWrapText(true);

        VBox.setVgrow(sameFieldsArea, Priority.SOMETIMES);

        section.getChildren().addAll(
            sectionLabel,
            sameLabel, sameFieldsArea,
            cltRedundantLabel, cltRedundantArea,
            svrRedundantLabel, svrRedundantArea
        );

        return section;
    }

    /**
     * 创建快捷操作栏
     */
    private HBox createQuickActionsBar() {
        HBox actionsBar = new HBox(10);
        actionsBar.setAlignment(Pos.CENTER_LEFT);
        actionsBar.setPadding(new Insets(10, 0, 0, 0));

        Label hintLabel = new Label("💡 快捷操作:");
        hintLabel.setStyle("-fx-font-weight: bold;");

        Button formatBtn = new Button("🎨 格式化字段");
        formatBtn.setTooltip(new Tooltip("自动格式化字段列表,去除多余空格"));
        formatBtn.setOnAction(e -> formatFields());

        Button clearBtn = new Button("🗑️ 清空表单");
        clearBtn.setOnAction(e -> clearForm());

        Button validateBtn = new Button("✔️ 验证字段");
        validateBtn.setTooltip(new Tooltip("检查字段名是否有重复或格式错误"));
        validateBtn.setOnAction(e -> validateFields());

        actionsBar.getChildren().addAll(hintLabel, formatBtn, clearBtn, validateBtn);
        return actionsBar;
    }

    /**
     * 填充表单数据
     */
    private void fillFormData(TableMapping mapping) {
        cltTabField.setText(mapping.getCltTab());
        svrTabField.setText(mapping.getSvrTab());
        sameFieldsArea.setText(mapping.getSameFileds());
        cltRedundantArea.setText(mapping.getCltRedundantFields());
        svrRedundantArea.setText(mapping.getSvrRedundantFields());
    }

    /**
     * 从表单创建映射对象
     */
    private TableMapping createMappingFromForm() {
        TableMapping mapping = new TableMapping();
        mapping.setCltTab(cltTabField.getText().trim());
        mapping.setSvrTab(svrTabField.getText().trim());
        mapping.setSameFileds(cleanFieldText(sameFieldsArea.getText()));
        mapping.setCltRedundantFields(cleanFieldText(cltRedundantArea.getText()));
        mapping.setSvrRedundantFields(cleanFieldText(svrRedundantArea.getText()));
        return mapping;
    }

    /**
     * 清理字段文本
     */
    private String cleanFieldText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        // 去除多余空格,保留逗号分隔
        return text.trim().replaceAll("\\s*,\\s*", ", ");
    }

    /**
     * 验证表单是否有效
     */
    private boolean isFormValid() {
        String cltTab = cltTabField.getText();
        String svrTab = svrTabField.getText();

        return cltTab != null && !cltTab.trim().isEmpty()
            && svrTab != null && !svrTab.trim().isEmpty();
    }

    /**
     * 格式化字段
     */
    private void formatFields() {
        sameFieldsArea.setText(cleanFieldText(sameFieldsArea.getText()));
        cltRedundantArea.setText(cleanFieldText(cltRedundantArea.getText()));
        svrRedundantArea.setText(cleanFieldText(svrRedundantArea.getText()));

        showInfo("格式化完成", "已自动整理字段格式");
    }

    /**
     * 清空表单
     */
    private void clearForm() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认清空");
        confirm.setHeaderText("清空表单");
        confirm.setContentText("确定要清空所有输入内容吗?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                cltTabField.clear();
                svrTabField.clear();
                sameFieldsArea.clear();
                cltRedundantArea.clear();
                svrRedundantArea.clear();
            }
        });
    }

    /**
     * 验证字段
     */
    private void validateFields() {
        StringBuilder report = new StringBuilder();
        report.append("字段验证报告:\n\n");

        // 检查共同字段
        String sameFields = sameFieldsArea.getText();
        if (sameFields != null && !sameFields.trim().isEmpty()) {
            String[] fields = sameFields.split(",");
            report.append("✅ 共同字段数: ").append(fields.length).append("\n");

            // 检查重复
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

        // 检查冗余字段
        int cltRedundantCount = countFields(cltRedundantArea.getText());
        int svrRedundantCount = countFields(svrRedundantArea.getText());

        report.append("📊 客户端冗余字段数: ").append(cltRedundantCount).append("\n");
        report.append("📊 服务器冗余字段数: ").append(svrRedundantCount).append("\n");

        showInfo("验证结果", report.toString());
    }

    /**
     * 统计字段数量
     */
    private int countFields(String fieldText) {
        if (fieldText == null || fieldText.trim().isEmpty()) {
            return 0;
        }
        return fieldText.split(",").length;
    }

    /**
     * 显示提示信息
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
