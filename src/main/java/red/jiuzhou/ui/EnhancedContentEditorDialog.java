package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import red.jiuzhou.ai.AiModelClient;
import red.jiuzhou.ai.AiModelFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 增强内容编辑对话框
 *
 * 为游戏设计师提供便捷的AI辅助内容编辑功能
 *
 * 核心功能：
 * - AI内容润色、翻译、生成
 * - 多模型选择（通义千问、豆包、Kimi、DeepSeek）
 * - 快捷功能按钮（高频操作）
 * - 实时预览和对比
 * - 自定义提示词
 */
@Slf4j
public class EnhancedContentEditorDialog extends Stage {

    @Getter
    private String resultContent;
    private final AtomicBoolean confirmed = new AtomicBoolean(false);

    // UI组件
    private TextArea originalTextArea;
    private TextArea aiResultArea;
    private ComboBox<String> modelSelector;
    private TextField customPromptField;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button applyButton;

    // 原始内容
    private final String originalContent;
    private final String fieldName;
    private final String contextInfo;

    /**
     * 创建增强编辑对话框
     *
     * @param owner 父窗口
     * @param fieldName 字段名称
     * @param content 当前内容
     * @param contextInfo 上下文信息（如：物品名称、ID等）
     */
    public EnhancedContentEditorDialog(Stage owner, String fieldName, String content, String contextInfo) {
        this.originalContent = content != null ? content : "";
        this.fieldName = fieldName;
        this.contextInfo = contextInfo != null ? contextInfo : "";
        this.resultContent = this.originalContent;

        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("🤖 AI内容编辑器 - " + fieldName);
        setResizable(true);

        initUI();
    }

    private void initUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 顶部：标题和模型选择
        VBox header = createHeader();
        root.setTop(header);

        // 中间：内容编辑区域（左右对比）
        SplitPane centerPane = createCenterPane();
        root.setCenter(centerPane);

        // 底部：自定义提示词和操作按钮
        VBox bottomPane = createBottomPane();
        root.setBottom(bottomPane);

        Scene scene = new Scene(root, 1200, 800);
        setScene(scene);
    }

    /**
     * 创建顶部区域
     */
    private VBox createHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea 0%, #764ba2 100%);");

        // 标题栏
        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("🤖");
        icon.setFont(Font.font("Arial", 36));

        VBox textBox = new VBox(3);
        Label title = new Label("AI内容编辑器");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("编辑字段：" + fieldName +
            (contextInfo.isEmpty() ? "" : " (" + contextInfo + ")"));
        subtitle.setFont(Font.font("Arial", 13));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        textBox.getChildren().addAll(title, subtitle);
        titleBox.getChildren().addAll(icon, textBox);

        // 模型选择和快捷功能
        HBox toolBar = new HBox(15);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Label modelLabel = new Label("AI模型:");
        modelLabel.setTextFill(Color.WHITE);
        modelLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        modelSelector = new ComboBox<>();
        modelSelector.getItems().addAll("qwen", "doubao", "kimi", "deepseek");
        modelSelector.setValue("qwen");
        modelSelector.setPrefWidth(120);
        modelSelector.setStyle("-fx-font-size: 12px;");

        Label quickLabel = new Label("快捷功能:");
        quickLabel.setTextFill(Color.WHITE);
        quickLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        // 高频快捷按钮
        Button polishBtn = createQuickButton("✨ 润色优化",
            "优化这段内容，使其更加流畅、专业、易读，保持原意不变");
        Button translateEnBtn = createQuickButton("🌐 翻译成英文",
            "将以下内容翻译成英文，保持游戏术语的准确性");
        Button translateCnBtn = createQuickButton("🇨🇳 翻译成中文",
            "将以下内容翻译成中文，使用游戏玩家易懂的表达");
        Button expandBtn = createQuickButton("📝 扩写补充",
            "扩展这段内容，添加更多细节和描述，使其更加丰富生动");
        Button summarizeBtn = createQuickButton("📋 精简概括",
            "精简这段内容，保留核心信息，使其更加简洁明了");
        Button fixBtn = createQuickButton("🔧 修正错误",
            "检查并修正这段内容中的语法、拼写、逻辑错误");

        polishBtn.setOnAction(e -> executeQuickAction(
            "优化这段内容，使其更加流畅、专业、易读，保持原意不变"));
        translateEnBtn.setOnAction(e -> executeQuickAction(
            "将以下内容翻译成英文，保持游戏术语的准确性"));
        translateCnBtn.setOnAction(e -> executeQuickAction(
            "将以下内容翻译成中文，使用游戏玩家易懂的表达"));
        expandBtn.setOnAction(e -> executeQuickAction(
            "扩展这段内容，添加更多细节和描述，使其更加丰富生动"));
        summarizeBtn.setOnAction(e -> executeQuickAction(
            "精简这段内容，保留核心信息，使其更加简洁明了"));
        fixBtn.setOnAction(e -> executeQuickAction(
            "检查并修正这段内容中的语法、拼写、逻辑错误"));

        HBox quickButtons = new HBox(8);
        quickButtons.getChildren().addAll(
            polishBtn, translateEnBtn, translateCnBtn,
            expandBtn, summarizeBtn, fixBtn
        );

        toolBar.getChildren().addAll(
            modelLabel, modelSelector,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            quickLabel, quickButtons
        );

        header.getChildren().addAll(titleBox, toolBar);
        return header;
    }

    /**
     * 创建快捷按钮
     */
    private Button createQuickButton(String text, String tooltip) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: rgba(255,255,255,0.9); " +
                     "-fx-text-fill: #667eea; " +
                     "-fx-font-size: 11px; " +
                     "-fx-font-weight: bold; " +
                     "-fx-padding: 6 12;");
        btn.setTooltip(new Tooltip(tooltip));

        // 鼠标悬停效果
        btn.setOnMouseEntered(e ->
            btn.setStyle("-fx-background-color: white; " +
                        "-fx-text-fill: #667eea; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 6 12;")
        );
        btn.setOnMouseExited(e ->
            btn.setStyle("-fx-background-color: rgba(255,255,255,0.9); " +
                        "-fx-text-fill: #667eea; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 6 12;")
        );

        return btn;
    }

    /**
     * 创建中间内容区域
     */
    private SplitPane createCenterPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.5);

        // 左侧：原始内容
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(15));
        leftPane.setStyle("-fx-background-color: white;");

        Label leftTitle = new Label("📄 原始内容（可编辑）");
        leftTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        originalTextArea = new TextArea(originalContent);
        originalTextArea.setWrapText(true);
        originalTextArea.setFont(Font.font("Consolas", 13));
        originalTextArea.setStyle("-fx-border-color: #ddd; -fx-border-radius: 3;");
        VBox.setVgrow(originalTextArea, Priority.ALWAYS);

        Label leftHint = new Label("💡 提示：可以直接编辑原始内容");
        leftHint.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        leftPane.getChildren().addAll(leftTitle, originalTextArea, leftHint);

        // 右侧：AI结果
        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(15));
        rightPane.setStyle("-fx-background-color: #f8f9fa;");

        HBox rightTitleBox = new HBox(10);
        rightTitleBox.setAlignment(Pos.CENTER_LEFT);

        Label rightTitle = new Label("✨ AI处理结果");
        rightTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Button copyToLeftBtn = new Button("← 复制到左侧");
        copyToLeftBtn.setStyle("-fx-font-size: 11px;");
        copyToLeftBtn.setOnAction(e -> {
            originalTextArea.setText(aiResultArea.getText());
            showStatus("已复制到左侧编辑区", false);
        });

        Button swapBtn = new Button("⇄ 交换内容");
        swapBtn.setStyle("-fx-font-size: 11px;");
        swapBtn.setOnAction(e -> {
            String temp = originalTextArea.getText();
            originalTextArea.setText(aiResultArea.getText());
            aiResultArea.setText(temp);
            showStatus("已交换左右内容", false);
        });

        rightTitleBox.getChildren().addAll(rightTitle, copyToLeftBtn, swapBtn);

        aiResultArea = new TextArea();
        aiResultArea.setWrapText(true);
        aiResultArea.setFont(Font.font("Consolas", 13));
        aiResultArea.setStyle("-fx-border-color: #ddd; -fx-border-radius: 3;");
        aiResultArea.setPromptText("AI处理结果将显示在这里...");
        VBox.setVgrow(aiResultArea, Priority.ALWAYS);

        Label rightHint = new Label("💡 提示：可以继续编辑AI生成的内容");
        rightHint.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        rightPane.getChildren().addAll(rightTitleBox, aiResultArea, rightHint);

        splitPane.getItems().addAll(leftPane, rightPane);
        return splitPane;
    }

    /**
     * 创建底部区域
     */
    private VBox createBottomPane() {
        VBox bottomPane = new VBox(15);
        bottomPane.setPadding(new Insets(15));
        bottomPane.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");

        // 自定义提示词区域
        VBox promptBox = new VBox(8);
        Label promptLabel = new Label("✏️ 自定义提示词（可选）：");
        promptLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        HBox promptInputBox = new HBox(10);
        promptInputBox.setAlignment(Pos.CENTER_LEFT);

        customPromptField = new TextField();
        customPromptField.setPromptText("输入自定义提示词，例如：将这段描述改写成更有史诗感的风格...");
        customPromptField.setPrefHeight(35);
        HBox.setHgrow(customPromptField, Priority.ALWAYS);

        Button executeCustomBtn = new Button("▶️ 执行自定义提示");
        executeCustomBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        executeCustomBtn.setPrefHeight(35);
        executeCustomBtn.setOnAction(e -> executeCustomPrompt());

        promptInputBox.getChildren().addAll(customPromptField, executeCustomBtn);

        Label promptHint = new Label("💡 提示：留空则使用快捷功能的默认提示词");
        promptHint.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        promptBox.getChildren().addAll(promptLabel, promptInputBox, promptHint);

        // 状态栏
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setVisible(false);
        statusBox.setManaged(false);

        progressBar = new ProgressBar();
        progressBar.setPrefWidth(200);
        progressBar.setProgress(-1); // 不确定进度

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 12px;");

        statusBox.getChildren().addAll(progressBar, statusLabel);

        // 操作按钮
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button useOriginalBtn = new Button("使用原始内容");
        useOriginalBtn.setPrefWidth(120);
        useOriginalBtn.setOnAction(e -> {
            resultContent = originalTextArea.getText();
            confirmed.set(true);
            close();
        });

        Button useAiResultBtn = new Button("使用AI结果");
        useAiResultBtn.setPrefWidth(120);
        useAiResultBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white;");
        useAiResultBtn.setOnAction(e -> {
            if (aiResultArea.getText().trim().isEmpty()) {
                showAlert("请先使用AI功能生成内容", Alert.AlertType.WARNING);
                return;
            }
            resultContent = aiResultArea.getText();
            confirmed.set(true);
            close();
        });

        applyButton = new Button("✓ 应用并关闭");
        applyButton.setPrefWidth(120);
        applyButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        applyButton.setOnAction(e -> {
            // 默认使用左侧内容（可能已经过手动编辑）
            resultContent = originalTextArea.getText();
            confirmed.set(true);
            close();
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setPrefWidth(100);
        cancelBtn.setOnAction(e -> {
            confirmed.set(false);
            close();
        });

        buttonBox.getChildren().addAll(useOriginalBtn, useAiResultBtn, applyButton, cancelBtn);

        bottomPane.getChildren().addAll(promptBox, statusBox, buttonBox);

        // 保存statusBox引用以便控制显示
        statusBox.setId("statusBox");

        return bottomPane;
    }

    /**
     * 执行快捷操作
     */
    private void executeQuickAction(String prompt) {
        String content = originalTextArea.getText().trim();
        if (content.isEmpty()) {
            showAlert("原始内容为空，无法处理", Alert.AlertType.WARNING);
            return;
        }

        executeAI(prompt, content);
    }

    /**
     * 执行自定义提示词
     */
    private void executeCustomPrompt() {
        String prompt = customPromptField.getText().trim();
        if (prompt.isEmpty()) {
            showAlert("请输入自定义提示词", Alert.AlertType.WARNING);
            return;
        }

        String content = originalTextArea.getText().trim();
        if (content.isEmpty()) {
            showAlert("原始内容为空，无法处理", Alert.AlertType.WARNING);
            return;
        }

        executeAI(prompt, content);
    }

    /**
     * 执行AI处理
     */
    private void executeAI(String prompt, String content) {
        String model = modelSelector.getValue();

        showStatus("正在使用 " + model + " 处理内容...", true);
        aiResultArea.setDisable(true);

        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                try {
                    AiModelClient client = AiModelFactory.getClient(model);

                    // 构建完整提示词
                    String fullPrompt = prompt + "\n\n内容：\n" + content;

                    // 调用AI
                    String result = client.chat(fullPrompt);

                    return result != null ? result.trim() : "";
                } catch (Exception e) {
                    log.error("AI处理失败", e);
                    throw e;
                }
            }
        };

        task.setOnSucceeded(e -> {
            String result = task.getValue();
            aiResultArea.setText(result);
            aiResultArea.setDisable(false);
            showStatus("AI处理完成！", false);

            // 3秒后隐藏状态栏
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(() -> hideStatus());
                } catch (InterruptedException ex) {
                    // ignore
                }
            }).start();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            String errorMsg = ex != null ? ex.getMessage() : "未知错误";
            aiResultArea.setText("AI处理失败：" + errorMsg);
            aiResultArea.setDisable(false);
            showStatus("处理失败", false);
            showAlert("AI处理失败：" + errorMsg, Alert.AlertType.ERROR);
        });

        new Thread(task).start();
    }

    /**
     * 显示状态信息
     */
    private void showStatus(String message, boolean showProgress) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            progressBar.setVisible(showProgress);

            // 找到statusBox并显示
            Scene scene = getScene();
            if (scene != null && scene.getRoot() instanceof BorderPane) {
                BorderPane root = (BorderPane) scene.getRoot();
                VBox bottom = (VBox) root.getBottom();
                HBox statusBox = (HBox) bottom.lookup("#statusBox");
                if (statusBox != null) {
                    statusBox.setVisible(true);
                    statusBox.setManaged(true);
                }
            }
        });
    }

    /**
     * 隐藏状态栏
     */
    private void hideStatus() {
        Platform.runLater(() -> {
            Scene scene = getScene();
            if (scene != null && scene.getRoot() instanceof BorderPane) {
                BorderPane root = (BorderPane) scene.getRoot();
                VBox bottom = (VBox) root.getBottom();
                HBox statusBox = (HBox) bottom.lookup("#statusBox");
                if (statusBox != null) {
                    statusBox.setVisible(false);
                    statusBox.setManaged(false);
                }
            }
        });
    }

    /**
     * 显示警告对话框
     */
    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "错误" : "提示");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示对话框并等待结果
     */
    public boolean showAndWaitForResult() {
        showAndWait();
        return confirmed.get();
    }

    /**
     * 快速创建并显示对话框
     */
    public static String editContent(Stage owner, String fieldName, String content, String contextInfo) {
        EnhancedContentEditorDialog dialog = new EnhancedContentEditorDialog(
            owner, fieldName, content, contextInfo
        );

        if (dialog.showAndWaitForResult()) {
            return dialog.getResultContent();
        }

        return content; // 取消则返回原内容
    }
}
