package red.jiuzhou.ui;

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
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 安全确认对话框
 * 用于关键数据操作前的二次确认
 *
 * 特点：
 * - 清晰显示操作影响范围
 * - 要求用户确认理解风险
 * - 防止误操作
 * - 提供详细的操作说明
 */
public class SafetyConfirmDialog extends Stage {

    private final AtomicBoolean confirmed = new AtomicBoolean(false);
    private CheckBox understandCheckBox;
    private TextField confirmTextField;
    private Button confirmButton;

    @Data
    public static class OperationInfo {
        private String operationName;           // 操作名称
        private String operationDescription;    // 操作描述
        private List<String> affectedFiles;     // 影响的文件列表
        private List<String> risks;             // 风险列表
        private List<String> precautions;       // 注意事项
        private boolean requiresConfirmText = false;  // 是否需要输入确认文本
        private String confirmText = "CONFIRM";       // 需要输入的确认文本
        private int estimatedTime = 0;          // 预计耗时（秒）
        private String backupInfo;              // 备份信息
    }

    public SafetyConfirmDialog(Stage owner, OperationInfo info) {
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("⚠️ 安全确认");
        setResizable(false);

        initUI(info);
    }

    private void initUI(OperationInfo info) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #ffffff;");

        // 警告标题
        HBox titleBox = createTitleBox();

        // 操作信息
        VBox operationBox = createOperationBox(info);

        // 影响范围
        VBox affectedBox = createAffectedBox(info);

        // 风险提示
        VBox riskBox = createRiskBox(info);

        // 注意事项
        VBox precautionBox = createPrecautionBox(info);

        // 确认区域
        VBox confirmBox = createConfirmBox(info);

        // 按钮区域
        HBox buttonBox = createButtonBox(info);

        root.getChildren().addAll(titleBox, new Separator(),
            operationBox, affectedBox, riskBox, precautionBox,
            new Separator(), confirmBox, buttonBox);

        Scene scene = new Scene(root, 600, 700);
        setScene(scene);
    }

    private HBox createTitleBox() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #fff3cd; -fx-padding: 15;");

        Label icon = new Label("⚠️");
        icon.setFont(Font.font("Arial", FontWeight.BOLD, 32));

        VBox textBox = new VBox(5);
        Label title = new Label("危险操作警告");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#856404"));

        Label subtitle = new Label("此操作将修改游戏数据文件，请仔细阅读以下信息");
        subtitle.setFont(Font.font("Arial", 12));
        subtitle.setTextFill(Color.web("#856404"));

        textBox.getChildren().addAll(title, subtitle);
        box.getChildren().addAll(icon, textBox);

        return box;
    }

    private VBox createOperationBox(OperationInfo info) {
        VBox box = new VBox(8);

        Label title = new Label("操作详情");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        int row = 0;

        // 操作名称
        grid.add(createLabel("操作名称:", true), 0, row);
        grid.add(createLabel(info.getOperationName(), false), 1, row++);

        // 操作描述
        if (info.getOperationDescription() != null) {
            grid.add(createLabel("操作描述:", true), 0, row);
            Label desc = createLabel(info.getOperationDescription(), false);
            desc.setWrapText(true);
            desc.setMaxWidth(400);
            grid.add(desc, 1, row++);
        }

        // 预计耗时
        if (info.getEstimatedTime() > 0) {
            grid.add(createLabel("预计耗时:", true), 0, row);
            grid.add(createLabel(info.getEstimatedTime() + " 秒", false), 1, row++);
        }

        // 备份信息
        if (info.getBackupInfo() != null) {
            grid.add(createLabel("备份策略:", true), 0, row);
            grid.add(createLabel(info.getBackupInfo(), false), 1, row++);
        }

        box.getChildren().addAll(title, grid);
        return box;
    }

    private VBox createAffectedBox(OperationInfo info) {
        VBox box = new VBox(8);

        Label title = new Label("影响范围");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        VBox fileListBox = new VBox(5);
        fileListBox.setStyle("-fx-background-color: #e9ecef; -fx-padding: 10; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        if (info.getAffectedFiles() != null && !info.getAffectedFiles().isEmpty()) {
            Label count = new Label("将影响 " + info.getAffectedFiles().size() + " 个文件：");
            count.setFont(Font.font("Arial", FontWeight.BOLD, 12));

            ListView<String> fileList = new ListView<>();
            fileList.getItems().addAll(info.getAffectedFiles());
            fileList.setPrefHeight(Math.min(150, info.getAffectedFiles().size() * 24 + 10));
            fileList.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11;");

            fileListBox.getChildren().addAll(count, fileList);
        } else {
            Label noFiles = new Label("未指定影响文件");
            noFiles.setStyle("-fx-font-style: italic; -fx-text-fill: #6c757d;");
            fileListBox.getChildren().add(noFiles);
        }

        box.getChildren().addAll(title, fileListBox);
        return box;
    }

    private VBox createRiskBox(OperationInfo info) {
        VBox box = new VBox(8);

        Label title = new Label("⚠️ 风险提示");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#dc3545"));

        VBox riskListBox = new VBox(5);
        riskListBox.setStyle("-fx-background-color: #f8d7da; -fx-padding: 10; -fx-border-color: #f5c6cb; -fx-border-radius: 5;");

        if (info.getRisks() != null && !info.getRisks().isEmpty()) {
            for (String risk : info.getRisks()) {
                HBox riskItem = new HBox(8);
                riskItem.setAlignment(Pos.TOP_LEFT);

                Label bullet = new Label("•");
                bullet.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                bullet.setTextFill(Color.web("#dc3545"));

                Label riskText = new Label(risk);
                riskText.setWrapText(true);
                riskText.setMaxWidth(500);
                riskText.setFont(Font.font("Arial", 12));

                riskItem.getChildren().addAll(bullet, riskText);
                riskListBox.getChildren().add(riskItem);
            }
        } else {
            // 默认风险提示
            Label defaultRisk = new Label("• 此操作将永久修改数据文件\n• 错误的操作可能导致数据损坏\n• 请确保已理解操作影响");
            defaultRisk.setWrapText(true);
            riskListBox.getChildren().add(defaultRisk);
        }

        box.getChildren().addAll(title, riskListBox);
        return box;
    }

    private VBox createPrecautionBox(OperationInfo info) {
        VBox box = new VBox(8);

        Label title = new Label("📋 注意事项");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#0c5460"));

        VBox precautionListBox = new VBox(5);
        precautionListBox.setStyle("-fx-background-color: #d1ecf1; -fx-padding: 10; -fx-border-color: #bee5eb; -fx-border-radius: 5;");

        List<String> precautions = info.getPrecautions();
        if (precautions == null || precautions.isEmpty()) {
            // 默认注意事项
            precautions = new ArrayList<>();
            precautions.add("操作前已自动创建备份，可在需要时恢复");
            precautions.add("建议在测试环境先验证操作效果");
            precautions.add("操作过程中请勿关闭程序");
            precautions.add("如有疑问，请先咨询技术人员");
        }

        for (String precaution : precautions) {
            HBox item = new HBox(8);
            item.setAlignment(Pos.TOP_LEFT);

            Label bullet = new Label("✓");
            bullet.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            bullet.setTextFill(Color.web("#0c5460"));

            Label text = new Label(precaution);
            text.setWrapText(true);
            text.setMaxWidth(500);
            text.setFont(Font.font("Arial", 12));

            item.getChildren().addAll(bullet, text);
            precautionListBox.getChildren().add(item);
        }

        box.getChildren().addAll(title, precautionListBox);
        return box;
    }

    private VBox createConfirmBox(OperationInfo info) {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: #fff; -fx-padding: 15; -fx-border-color: #dee2e6; -fx-border-width: 2; -fx-border-radius: 5;");

        // 理解确认
        understandCheckBox = new CheckBox("我已仔细阅读上述信息，并理解此操作的风险和影响");
        understandCheckBox.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        understandCheckBox.setOnAction(e -> updateConfirmButton());

        box.getChildren().add(understandCheckBox);

        // 如果需要输入确认文本
        if (info.isRequiresConfirmText()) {
            VBox textConfirmBox = new VBox(8);
            textConfirmBox.setPadding(new Insets(10, 0, 0, 0));

            Label instruction = new Label("请输入 \"" + info.getConfirmText() + "\" 以确认操作：");
            instruction.setFont(Font.font("Arial", 12));
            instruction.setTextFill(Color.web("#dc3545"));

            confirmTextField = new TextField();
            confirmTextField.setPromptText("输入确认文本");
            confirmTextField.setFont(Font.font("Courier New", 13));
            confirmTextField.textProperty().addListener((obs, old, val) -> updateConfirmButton());

            textConfirmBox.getChildren().addAll(instruction, confirmTextField);
            box.getChildren().add(textConfirmBox);
        }

        return box;
    }

    private HBox createButtonBox(OperationInfo info) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(10, 0, 0, 0));

        Button cancelButton = new Button("取消");
        cancelButton.setPrefWidth(100);
        cancelButton.setStyle("-fx-font-size: 13;");
        cancelButton.setOnAction(e -> {
            confirmed.set(false);
            close();
        });

        confirmButton = new Button("确认执行");
        confirmButton.setPrefWidth(100);
        confirmButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold;");
        confirmButton.setDisable(true);
        confirmButton.setOnAction(e -> {
            confirmed.set(true);
            close();
        });

        box.getChildren().addAll(cancelButton, confirmButton);
        return box;
    }

    private void updateConfirmButton() {
        boolean canConfirm = understandCheckBox.isSelected();

        if (confirmTextField != null) {
            String requiredText = "CONFIRM"; // 默认值，实际应从OperationInfo获取
            canConfirm = canConfirm && requiredText.equals(confirmTextField.getText().trim());
        }

        confirmButton.setDisable(!canConfirm);
    }

    private Label createLabel(String text, boolean bold) {
        Label label = new Label(text);
        if (bold) {
            label.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        } else {
            label.setFont(Font.font("Arial", 12));
        }
        return label;
    }

    /**
     * 显示对话框并等待用户确认
     */
    public boolean showAndWaitConfirmation() {
        showAndWait();
        return confirmed.get();
    }

    /**
     * 快速创建并显示确认对话框
     */
    public static boolean confirm(Stage owner, OperationInfo info) {
        SafetyConfirmDialog dialog = new SafetyConfirmDialog(owner, info);
        return dialog.showAndWaitConfirmation();
    }

    /**
     * 创建简单的操作信息
     */
    public static OperationInfo createSimpleOperation(String name, String description,
                                                      List<String> files, List<String> risks) {
        OperationInfo info = new OperationInfo();
        info.setOperationName(name);
        info.setOperationDescription(description);
        info.setAffectedFiles(files);
        info.setRisks(risks);
        info.setBackupInfo("自动备份已启用，可保留最近10个版本");
        return info;
    }

    /**
     * 创建高风险操作信息（需要输入确认）
     */
    public static OperationInfo createHighRiskOperation(String name, String description,
                                                        List<String> files, List<String> risks) {
        OperationInfo info = createSimpleOperation(name, description, files, risks);
        info.setRequiresConfirmText(true);
        info.setConfirmText("CONFIRM");
        return info;
    }
}