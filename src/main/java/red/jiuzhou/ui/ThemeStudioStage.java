package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.theme.*;
import red.jiuzhou.theme.rules.*;
import red.jiuzhou.ui.features.FeatureTaskExecutor;
import red.jiuzhou.util.YamlUtils;

// 额外导入（如果需要）
import java.util.ArrayList;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 主题工作室界面
 *
 * 提供主题管理、创建、应用的可视化界面
 *
 * @author Claude
 * @version 1.0
 */
public class ThemeStudioStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(ThemeStudioStage.class);

    private final ThemeManager themeManager;
    private final FileDependencyAnalyzer dependencyAnalyzer;
    private BatchTransformEngine transformEngine;

    // UI组件
    private final ListView<ThemeSummaryItem> themeListView = new ListView<>();
    private final Label themeDetailTitle = new Label("选择主题查看详情");
    private final TextArea themeDetailArea = new TextArea();
    private final Button createThemeButton = new Button("创建新主题");
    private final Button applyThemeButton = new Button("应用主题");
    private final Button deleteThemeButton = new Button("删除主题");
    private final Button exportThemeButton = new Button("导出主题");
    private final Button importThemeButton = new Button("导入主题");
    private final ProgressBar progressBar = new ProgressBar(0);
    private static final String STATUS_READY = "就绪";

    private final Label statusLabel = new Label(STATUS_READY);
    private final TableView<ThemeManager.ThemeVersion> versionTable = new TableView<>();

    private Theme currentTheme;

    public ThemeStudioStage() {
        // 初始化管理器
        String confPath = YamlUtils.getPropertyOrDefault("file.confPath", "src/main/resources/CONF");
        Path themeDir = Paths.get(confPath, "themes");

        this.themeManager = new ThemeManager(themeDir);
        this.dependencyAnalyzer = new FileDependencyAnalyzer();
        this.transformEngine = new BatchTransformEngine(4); // 4个并发线程

        setTitle("主题工作室");
        setScene(buildScene());
        setMinWidth(1000);
        setMinHeight(700);

        setOnCloseRequest(event -> {
            if (transformEngine != null) {
                transformEngine.shutdown();
            }
        });

        loadThemeList();
    }

    private Scene buildScene() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().add(buildThemeListPane());
        splitPane.getItems().add(buildDetailPane());
        splitPane.setDividerPositions(0.35);

        VBox statusBar = buildStatusBar();

        BorderPane root = new BorderPane();
        root.setCenter(splitPane);
        root.setBottom(statusBar);

        return new Scene(root, 1200, 800);
    }

    private VBox buildThemeListPane() {
        Label title = new Label("主题库");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        themeListView.setPlaceholder(new Label("暂无主题\n点击\"创建新主题\"开始"));
        themeListView.setCellFactory(listView -> new ThemeSummaryCell());
        themeListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadThemeDetail(newVal.summary);
            }
        });

        HBox buttonBar = new HBox(8);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        buttonBar.getChildren().addAll(createThemeButton, importThemeButton);

        VBox box = new VBox(10, title, themeListView, buttonBar);
        VBox.setVgrow(themeListView, Priority.ALWAYS);
        box.setPadding(new Insets(12, 8, 12, 12));

        createThemeButton.setOnAction(e -> showCreateThemeDialog());
        importThemeButton.setOnAction(e -> importTheme());

        return box;
    }

    private VBox buildDetailPane() {
        themeDetailTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        themeDetailArea.setEditable(false);
        themeDetailArea.setWrapText(true);
        themeDetailArea.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px;");

        HBox actionBar = new HBox(8);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.getChildren().addAll(applyThemeButton, exportThemeButton, deleteThemeButton);

        applyThemeButton.setDisable(true);
        exportThemeButton.setDisable(true);
        deleteThemeButton.setDisable(true);

        applyThemeButton.setOnAction(e -> applyTheme());
        exportThemeButton.setOnAction(e -> exportTheme());
        deleteThemeButton.setOnAction(e -> deleteTheme());

        // 版本历史表格
        Label versionLabel = new Label("版本历史");
        versionLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        versionTable.setPlaceholder(new Label("暂无版本历史"));
        versionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ThemeManager.ThemeVersion, String> tagCol = new TableColumn<>("标签");
        tagCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getTag()));
        tagCol.setPrefWidth(150);

        TableColumn<ThemeManager.ThemeVersion, String> versionCol = new TableColumn<>("版本");
        versionCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getThemeVersion()));
        versionCol.setPrefWidth(100);

        TableColumn<ThemeManager.ThemeVersion, String> timeCol = new TableColumn<>("创建时间");
        timeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                formatTime(data.getValue().getCreatedAt())));

        TableColumn<ThemeManager.ThemeVersion, String> commentCol = new TableColumn<>("备注");
        commentCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getComment()));

        versionTable.getColumns().addAll(tagCol, versionCol, timeCol, commentCol);

        VBox versionBox = new VBox(6, versionLabel, versionTable);
        versionBox.setPadding(new Insets(12, 0, 0, 0));
        VBox.setVgrow(versionTable, Priority.SOMETIMES);

        VBox box = new VBox(12, themeDetailTitle, actionBar, themeDetailArea, versionBox);
        box.setPadding(new Insets(12, 12, 12, 8));
        VBox.setVgrow(themeDetailArea, Priority.ALWAYS);

        return box;
    }

    private VBox buildStatusBar() {
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        statusLabel.setStyle("-fx-font-size: 11px;");

        HBox statusBox = new HBox(12, statusLabel, progressBar);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        VBox box = new VBox(statusBox);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        return box;
    }

    /**
     * 加载主题列表
     */
    private void loadThemeList() {
        updateStatus("正在加载主题库...", true);

        Task<List<ThemeManager.ThemeSummary>> task = new Task<List<ThemeManager.ThemeSummary>>() {
            @Override
            protected List<ThemeManager.ThemeSummary> call() {
                return themeManager.listThemes();
            }
        };

        task.setOnSucceeded(event -> {
            List<ThemeManager.ThemeSummary> summaries = task.getValue();
            ObservableList<ThemeSummaryItem> items = FXCollections.observableArrayList();
            for (ThemeManager.ThemeSummary summary : summaries) {
                items.add(new ThemeSummaryItem(summary));
            }
            themeListView.setItems(items);
            updateStatus(String.format("共 %d 套主题", summaries.size()), false);
        });

        task.setOnFailed(event -> {
            log.error("加载主题库失败", task.getException());
            showError("加载主题失败", task.getException() != null ? task.getException().getMessage() : "未知错误");
            updateStatus("加载主题失败", false);
        });

        FeatureTaskExecutor.run(task, "theme-list-loader");
    }

    /**
     * 加载主题详情
     */
    private void loadThemeDetail(ThemeManager.ThemeSummary summary) {
        updateStatus("正在加载主题详情...", true);

        Task<Theme> task = new Task<Theme>() {
            @Override
            protected Theme call() throws Exception {
                return themeManager.loadTheme(summary.getId());
            }
        };

        task.setOnSucceeded(event -> {
            currentTheme = task.getValue();
            displayThemeDetail(currentTheme);
            loadVersionHistory(currentTheme.getId());
            applyThemeButton.setDisable(false);
            exportThemeButton.setDisable(false);
            deleteThemeButton.setDisable(false);
            updateStatus("详情已更新", false);
        });

        task.setOnFailed(event -> {
            log.error("加载主题失败", task.getException());
            showError("加载主题失败", task.getException().getMessage());
            updateStatus("加载主题失败", false);
        });

        FeatureTaskExecutor.run(task, "theme-detail-loader");
    }

    /**
     * 显示主题详情
     */
    private void displayThemeDetail(Theme theme) {
        themeDetailTitle.setText(theme.getName() + " v" + theme.getVersion());

        StringBuilder detail = new StringBuilder();
        detail.append("主题ID: ").append(theme.getId()).append("\n");
        detail.append("描述: ").append(theme.getDescription()).append("\n");
        detail.append("作者: ").append(theme.getAuthor()).append("\n");
        detail.append("类型: ").append(theme.getType().getDisplayName()).append("\n");
        detail.append("范围: ").append(theme.getScope().getDisplayName()).append("\n");
        detail.append("创建时间: ").append(formatTime(theme.getCreatedAt())).append("\n\n");

        detail.append("转换规则 (").append(theme.getRules().size()).append("):\n");
        for (TransformRule rule : theme.getRules()) {
            detail.append("  • ").append(rule.getName())
                    .append(" - ").append(rule.getDescription()).append("\n");
        }

        detail.append("\nAI提示词:\n");
        for (Map.Entry<String, String> entry : theme.getAiPrompts().entrySet()) {
            detail.append("  • ").append(entry.getKey()).append(": ")
                    .append(entry.getValue().substring(0, Math.min(50, entry.getValue().length())))
                    .append("...\n");
        }

        detail.append("\n目标文件模式:\n");
        for (String pattern : theme.getTargetFilePatterns()) {
            detail.append("  • ").append(pattern).append("\n");
        }

        themeDetailArea.setText(detail.toString());
    }

    /**
     * 加载版本历史
     */
    private void loadVersionHistory(String themeId) {
        updateStatus("正在加载版本历史...", true);

        Task<List<ThemeManager.ThemeVersion>> task = new Task<List<ThemeManager.ThemeVersion>>() {
            @Override
            protected List<ThemeManager.ThemeVersion> call() {
                return themeManager.getThemeVersions(themeId);
            }
        };

        task.setOnSucceeded(event -> {
            ObservableList<ThemeManager.ThemeVersion> versions = FXCollections.observableArrayList(task.getValue());
            versionTable.setItems(versions);
            updateStatus(String.format("共 %d 个版本", versions.size()), false);
        });

        task.setOnFailed(event -> {
            log.error("加载版本历史失败", task.getException());
            showError("加载版本历史失败", task.getException() != null ? task.getException().getMessage() : "未知错误");
            updateStatus("加载版本历史失败", false);
        });

        FeatureTaskExecutor.run(task, "theme-version-loader");
    }

    /**
     * 显示创建主题对话框
     */
    private void showCreateThemeDialog() {
        Dialog<Theme> dialog = new Dialog<>();
        dialog.setTitle("创建新主题");
        dialog.setHeaderText("填写主题信息");

        ButtonType createButtonType = new ButtonType("创建", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 20));

        TextField nameField = new TextField();
        nameField.setPromptText("主题名称");
        TextField descField = new TextField();
        descField.setPromptText("主题描述");
        ComboBox<ThemeType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(ThemeType.values());
        typeCombo.setValue(ThemeType.TEXT_STYLE);
        ComboBox<ThemeScope> scopeCombo = new ComboBox<>();
        scopeCombo.getItems().addAll(ThemeScope.values());
        scopeCombo.setValue(ThemeScope.ALL);
        TextField styleField = new TextField();
        styleField.setPromptText("例如: 哈利波特风格、武侠风格");
        TextArea promptArea = new TextArea();
        promptArea.setPromptText("AI转换提示词");
        promptArea.setPrefRowCount(4);

        grid.add(new Label("主题名称:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("描述:"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("类型:"), 0, 2);
        grid.add(typeCombo, 1, 2);
        grid.add(new Label("范围:"), 0, 3);
        grid.add(scopeCombo, 1, 3);
        grid.add(new Label("风格:"), 0, 4);
        grid.add(styleField, 1, 4);
        grid.add(new Label("AI提示词:"), 0, 5);
        grid.add(promptArea, 1, 5);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                String name = nameField.getText();
                String desc = descField.getText();
                String style = styleField.getText();
                String prompt = promptArea.getText();

                if (name.isEmpty() || style.isEmpty() || prompt.isEmpty()) {
                    showError("输入错误", "主题名称、风格和AI提示词不能为空");
                    return null;
                }

                // 构建主题
                Theme.Builder builder = Theme.builder()
                        .name(name)
                        .description(desc)
                        .type(typeCombo.getValue())
                        .scope(scopeCombo.getValue())
                        .addAiPrompt("default", prompt)
                        .addAiPrompt("name", prompt)
                        .addAiPrompt("description", prompt);

                // 添加默认的文本风格规则
                TextStyleRule rule = new TextStyleRule(
                        name + "文本转换",
                        style,
                        prompt
                );
                builder.addRule(rule);

                // 根据scope添加目标文件模式
                ThemeScope scope = scopeCombo.getValue();
                if (scope == ThemeScope.ALL) {
                    builder.addTargetFilePattern("*.xml");
                } else {
                    builder.addTargetFilePattern("*" + scope.name().toLowerCase() + "*.xml");
                }

                return builder.build();
            }
            return null;
        });

        Optional<Theme> result = dialog.showAndWait();
        result.ifPresent(theme -> {
            try {
                themeManager.saveTheme(theme);
                loadThemeList();
                statusLabel.setText("主题创建成功: " + theme.getName());
            } catch (Exception e) {
                log.error("保存主题失败", e);
                showError("保存失败", e.getMessage());
            }
        });
    }

    /**
     * 应用主题
     */
    private void applyTheme() {
        if (currentTheme == null) {
            return;
        }

        // 选择目标目录
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择要应用主题的目录");
        File selectedDir = chooser.showDialog(this);

        if (selectedDir == null) {
            return;
        }

        // 使用向导引导用户
        ApplyThemeWizard wizard = new ApplyThemeWizard(
                this, currentTheme, selectedDir.toPath());

        boolean confirmed = wizard.showAndWait();
        if (!confirmed) {
            return;
        }

        // 获取匹配的文件列表
        List<Path> matchedFiles = wizard.getMatchedFiles();
        if (matchedFiles == null || matchedFiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("无匹配文件");
            alert.setContentText("在选择的目录中没有找到匹配的文件。");
            alert.showAndWait();
            return;
        }

        // 执行应用
        applyThemeAsync(matchedFiles);
    }

    /**
     * 异步应用主题
     */
    private void applyThemeAsync(List<Path> matchingFiles) {
        Task<BatchTransformEngine.TransformResult> task = new Task<BatchTransformEngine.TransformResult>() {
            @Override
            protected BatchTransformEngine.TransformResult call() throws Exception {
                updateMessage("开始处理 " + matchingFiles.size() + " 个文件");

                // 应用主题
                return transformEngine.applyTheme(currentTheme, matchingFiles, progress -> {
                    Platform.runLater(() -> {
                        updateProgress(progress.getProcessed(), progress.getTotal());
                        updateMessage(String.format("处理中: %s (%d/%d)",
                                progress.getCurrentFile().getFileName(),
                                progress.getProcessed(), progress.getTotal()));
                    });
                });
            }
        };

        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.setVisible(true);

        task.setOnSucceeded(event -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            progressBar.setProgress(0);
            BatchTransformEngine.TransformResult result = task.getValue();

            // 显示详细结果报告
            TransformResultDialog resultDialog = new TransformResultDialog(this, result);
            resultDialog.show();

            statusLabel.setText("应用完成: " + result.getStatus().getDisplayName());
        });

        task.setOnFailed(event -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            progressBar.setProgress(0);
            log.error("应用主题失败", task.getException());
            showError("应用失败", task.getException().getMessage());
            statusLabel.setText("应用失败");
        });

        FeatureTaskExecutor.run(task, "theme-apply-runner");
    }

    private void updateStatus(String message, boolean busy) {
        Platform.runLater(() -> {
            statusLabel.textProperty().unbind();
            if (message != null && !message.isEmpty()) {
                statusLabel.setText(message);
            } else {
                statusLabel.setText(STATUS_READY);
            }
            progressBar.progressProperty().unbind();
            progressBar.setVisible(busy);
            progressBar.setProgress(busy ? ProgressIndicator.INDETERMINATE_PROGRESS : 0);
        });
    }

    /**
     * 导出主题
     */
    private void exportTheme() {
        if (currentTheme == null) {
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出目录");
        File targetDir = chooser.showDialog(this);

        if (targetDir != null) {
            try {
                Path exportPath = targetDir.toPath().resolve(currentTheme.getId());
                themeManager.exportTheme(currentTheme.getId(), exportPath);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("导出成功");
                alert.setContentText("主题已导出到: " + exportPath);
                alert.showAndWait();

                statusLabel.setText("导出成功: " + currentTheme.getName());
            } catch (Exception e) {
                log.error("导出主题失败", e);
                showError("导出失败", e.getMessage());
            }
        }
    }

    /**
     * 导入主题
     */
    private void importTheme() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择主题包目录");
        File packageDir = chooser.showDialog(this);

        if (packageDir != null) {
            try {
                Theme theme = themeManager.importTheme(packageDir.toPath());
                loadThemeList();

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("导入成功");
                alert.setContentText("主题已导入: " + theme.getName());
                alert.showAndWait();

                statusLabel.setText("导入成功: " + theme.getName());
            } catch (Exception e) {
                log.error("导入主题失败", e);
                showError("导入失败", e.getMessage());
            }
        }
    }

    /**
     * 删除主题
     */
    private void deleteTheme() {
        if (currentTheme == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("删除主题: " + currentTheme.getName());
        confirm.setContentText("此操作不可恢复。是否继续？");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                themeManager.deleteTheme(currentTheme.getId());
                currentTheme = null;
                loadThemeList();
                themeDetailArea.clear();
                themeDetailTitle.setText("选择主题查看详情");
                statusLabel.setText("主题已删除");
            } catch (Exception e) {
                log.error("删除主题失败", e);
                showError("删除失败", e.getMessage());
            }
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String formatTime(java.time.Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

    /**
     * 主题摘要显示项
     */
    private static class ThemeSummaryItem {
        final ThemeManager.ThemeSummary summary;

        ThemeSummaryItem(ThemeManager.ThemeSummary summary) {
            this.summary = summary;
        }

        @Override
        public String toString() {
            return summary.getName();
        }
    }

    /**
     * 主题摘要单元格
     */
    private static class ThemeSummaryCell extends ListCell<ThemeSummaryItem> {
        @Override
        protected void updateItem(ThemeSummaryItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                Label nameLabel = new Label(item.summary.getName());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

                Label typeLabel = new Label(item.summary.getType().getDisplayName());
                typeLabel.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                        "-fx-padding: 2 6 2 6; -fx-background-radius: 3; -fx-font-size: 10px;");

                Label descLabel = new Label(item.summary.getDescription());
                descLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
                descLabel.setWrapText(true);

                HBox headerBox = new HBox(8, nameLabel, typeLabel);
                headerBox.setAlignment(Pos.CENTER_LEFT);

                VBox vbox = new VBox(4, headerBox, descLabel);
                vbox.setPadding(new Insets(6));

                setGraphic(vbox);
            }
        }
    }
}
