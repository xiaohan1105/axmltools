package red.jiuzhou.theme;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用主题向导
 *
 * 引导用户安全地应用主题，提供影响预估和确认
 *
 * @author Claude
 * @version 1.0
 */
public class ApplyThemeWizard {

    private final Stage owner;
    private final Theme theme;
    private final Path targetDirectory;

    private Stage wizardStage;
    private List<Path> matchedFiles;
    private boolean confirmed = false;

    public ApplyThemeWizard(Stage owner, Theme theme, Path targetDirectory) {
        this.owner = owner;
        this.theme = theme;
        this.targetDirectory = targetDirectory;
    }

    /**
     * 显示向导并等待用户确认
     *
     * @return true 如果用户确认应用，false 如果取消
     */
    public boolean showAndWait() {
        wizardStage = new Stage();
        wizardStage.initOwner(owner);
        wizardStage.initModality(Modality.APPLICATION_MODAL);
        wizardStage.setTitle("应用主题 - " + theme.getName());

        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(buildOverviewTab());
        tabPane.getTabs().add(buildFileListTab());
        tabPane.getTabs().add(buildSettingsTab());

        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(12));

        Button applyButton = new Button("应用主题");
        applyButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 8 20;");

        Button cancelButton = new Button("取消");
        cancelButton.setStyle("-fx-padding: 8 20;");

        buttonBar.getChildren().addAll(cancelButton, applyButton);

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        root.setBottom(buttonBar);

        applyButton.setOnAction(e -> {
            confirmed = true;
            wizardStage.close();
        });

        cancelButton.setOnAction(e -> {
            confirmed = false;
            wizardStage.close();
        });

        Scene scene = new Scene(root, 700, 550);
        wizardStage.setScene(scene);
        wizardStage.showAndWait();

        return confirmed;
    }

    public List<Path> getMatchedFiles() {
        return matchedFiles;
    }

    /**
     * 概览标签页
     */
    private Tab buildOverviewTab() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // 主题信息
        Label themeLabel = new Label("主题：" + theme.getName() + " v" + theme.getVersion());
        themeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label descLabel = new Label(theme.getDescription());
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666;");

        // 目标目录
        Label dirLabel = new Label("目标目录：");
        dirLabel.setStyle("-fx-font-weight: bold;");

        Label dirPath = new Label(targetDirectory.toString());
        dirPath.setStyle("-fx-font-family: 'Consolas'; -fx-background-color: #f5f5f5; " +
                "-fx-padding: 8; -fx-border-color: #ddd; -fx-border-width: 1;");

        // 扫描文件
        Label scanLabel = new Label("正在扫描文件...");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(40, 40);

        HBox scanBox = new HBox(10, scanLabel, progress);
        scanBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        content.getChildren().addAll(themeLabel, descLabel,
                new Separator(), dirLabel, dirPath,
                new Separator(), scanBox);

        // 异步扫描文件
        javafx.concurrent.Task<List<Path>> scanTask = new javafx.concurrent.Task<List<Path>>() {
            @Override
            protected List<Path> call() throws Exception {
                List<Path> files = new ArrayList<>();
                Files.walk(targetDirectory)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                        .filter(p -> theme.matchesFile(p.toString()))
                        .forEach(files::add);
                return files;
            }
        };

        scanTask.setOnSucceeded(e -> {
            matchedFiles = scanTask.getValue();
            progress.setVisible(false);

            // 显示扫描结果
            VBox resultBox = buildScanResult();
            content.getChildren().remove(scanBox);
            content.getChildren().add(resultBox);
        });

        scanTask.setOnFailed(e -> {
            progress.setVisible(false);
            scanLabel.setText("扫描失败: " + scanTask.getException().getMessage());
            scanLabel.setTextFill(Color.RED);
        });

        new Thread(scanTask).start();

        Tab tab = new Tab("概览", new ScrollPane(content));
        tab.setClosable(false);
        return tab;
    }

    /**
     * 构建扫描结果显示
     */
    private VBox buildScanResult() {
        VBox box = new VBox(10);

        // 统计信息
        Label countLabel = new Label(String.format("找到 %d 个匹配文件", matchedFiles.size()));
        countLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 预估信息
        int estimatedFields = matchedFiles.size() * 50;  // 粗略估计
        int estimatedTime = matchedFiles.size() * 2;     // 每个文件约2秒

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(8);
        statsGrid.setPadding(new Insets(10));
        statsGrid.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #ddd; -fx-border-width: 1;");

        statsGrid.add(new Label("匹配文件:"), 0, 0);
        statsGrid.add(new Label(matchedFiles.size() + " 个"), 1, 0);

        statsGrid.add(new Label("预计字段:"), 0, 1);
        statsGrid.add(new Label("约 " + estimatedFields + " 个"), 1, 1);

        statsGrid.add(new Label("预计耗时:"), 0, 2);
        statsGrid.add(new Label("约 " + formatDuration(estimatedTime) + ""), 1, 2);

        // 警告信息
        if (matchedFiles.size() > 100) {
            Label warningLabel = new Label("⚠ 文件数量较多，建议分批处理");
            warningLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
            box.getChildren().add(warningLabel);
        }

        // 备份提示
        Label backupLabel = new Label("✓ 将自动备份所有文件到 .theme-backup 目录");
        backupLabel.setStyle("-fx-text-fill: #4CAF50;");

        box.getChildren().addAll(countLabel, statsGrid, backupLabel);

        return box;
    }

    /**
     * 文件列表标签页
     */
    private Tab buildFileListTab() {
        ListView<Path> fileListView = new ListView<>();
        fileListView.setPlaceholder(new Label("正在扫描文件..."));

        // 当文件扫描完成后更新列表
        javafx.concurrent.Task<Void> updateTask = new javafx.concurrent.Task<Void>() {
            @Override
            protected Void call() throws Exception {
                while (matchedFiles == null) {
                    Thread.sleep(100);
                }
                return null;
            }
        };

        updateTask.setOnSucceeded(e -> {
            fileListView.getItems().addAll(matchedFiles);
        });

        new Thread(updateTask).start();

        // 自定义单元格显示文件名和路径
        fileListView.setCellFactory(listView -> new ListCell<Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label fileName = new Label(item.getFileName().toString());
                    fileName.setStyle("-fx-font-weight: bold;");

                    Label filePath = new Label(item.getParent().toString());
                    filePath.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");

                    VBox vbox = new VBox(2, fileName, filePath);
                    setGraphic(vbox);
                }
            }
        });

        Tab tab = new Tab("文件列表", fileListView);
        tab.setClosable(false);
        return tab;
    }

    /**
     * 设置标签页
     */
    private Tab buildSettingsTab() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label title = new Label("应用设置");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        ThemeSettings settings = theme.getSettings();

        grid.add(new Label("保留数值:"), 0, 0);
        grid.add(new Label(settings.isPreserveNumericValues() ? "✓ 是" : "✗ 否"), 1, 0);

        grid.add(new Label("保留ID:"), 0, 1);
        grid.add(new Label(settings.isPreserveIds() ? "✓ 是" : "✗ 否"), 1, 1);

        grid.add(new Label("使用AI转换:"), 0, 2);
        grid.add(new Label(settings.isUseAiTransform() ? "✓ 是 (" + settings.getAiModel() + ")" : "✗ 否"), 1, 2);

        grid.add(new Label("事务模式:"), 0, 3);
        grid.add(new Label(settings.isTransactional() ? "✓ 是（全成功或全回滚）" : "✗ 否（允许部分失败）"), 1, 3);

        grid.add(new Label("并发线程数:"), 0, 4);
        grid.add(new Label(settings.getMaxConcurrency() + " 个"), 1, 4);

        grid.add(new Label("备份:"), 0, 5);
        grid.add(new Label(settings.isBackupBeforeApply() ? "✓ 应用前自动备份" : "✗ 不备份"), 1, 5);

        Label noteLabel = new Label(
                "注意：这些设置由主题定义，无法在此修改。\n" +
                "如需调整，请编辑主题配置。"
        );
        noteLabel.setWrapText(true);
        noteLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        content.getChildren().addAll(title, grid, new Separator(), noteLabel);

        Tab tab = new Tab("设置", new ScrollPane(content));
        tab.setClosable(false);
        return tab;
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        } else {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format("%d 分 %d 秒", minutes, secs);
        }
    }
}
