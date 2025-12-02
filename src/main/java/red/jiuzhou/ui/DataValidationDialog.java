package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import red.jiuzhou.validation.DataConsistencyValidator;
import red.jiuzhou.validation.DataConsistencyValidator.ValidationReport;
import red.jiuzhou.validation.DataConsistencyValidator.ValidationResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据一致性验证对话框
 * 提供友好的UI界面进行数据验证操作
 */
@Slf4j
public class DataValidationDialog extends Stage {

    private final DataConsistencyValidator validator = new DataConsistencyValidator();
    private TextField directoryField;
    private TextArea logArea;
    private WebView reportView;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button validateBtn;
    private Button exportBtn;

    public DataValidationDialog(Stage owner) {
        initModality(Modality.WINDOW_MODAL);
        initOwner(owner);
        setTitle("数据一致性验证");
        validator.initializeRules();

        initUI();
    }

    private void initUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        // 目录选择区
        HBox pathRow = createPathRow();

        // 验证选项区
        VBox optionsBox = createOptionsBox();

        // 结果显示区
        TabPane resultTabs = createResultTabs();

        // 控制按钮区
        HBox controlBar = createControlBar();

        // 状态栏
        HBox statusBar = createStatusBar();

        root.getChildren().addAll(pathRow, optionsBox, resultTabs, controlBar, statusBar);

        Scene scene = new Scene(root, 900, 650);
        setScene(scene);
    }

    private HBox createPathRow() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("数据目录:");
        directoryField = new TextField();
        directoryField.setPrefWidth(450);
        directoryField.setText("D:\\workspace\\dbxmlTool\\data\\DATA");

        Button browseBtn = new Button("浏览...");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择数据目录");
            File dir = chooser.showDialog(this);
            if (dir != null) {
                directoryField.setText(dir.getAbsolutePath());
            }
        });

        row.getChildren().addAll(label, directoryField, browseBtn);
        return row;
    }

    private VBox createOptionsBox() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5;");

        Label title = new Label("验证选项");
        title.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        CheckBox checkItems = new CheckBox("检查装备与掉落表一致性");
        checkItems.setSelected(true);

        CheckBox checkNPC = new CheckBox("检查NPC等级与经验表一致性");
        checkNPC.setSelected(true);

        CheckBox checkSkills = new CheckBox("检查技能与学习配置一致性");
        checkSkills.setSelected(true);

        CheckBox checkOrphaned = new CheckBox("检测孤立数据");
        checkOrphaned.setSelected(true);

        CheckBox checkBalance = new CheckBox("检查数值平衡性");
        checkBalance.setSelected(true);

        CheckBox checkReferences = new CheckBox("检查引用完整性");
        checkReferences.setSelected(true);

        box.getChildren().addAll(title, checkItems, checkNPC, checkSkills,
                                 checkOrphaned, checkBalance, checkReferences);
        return box;
    }

    private TabPane createResultTabs() {
        TabPane tabs = new TabPane();
        tabs.setPrefHeight(350);

        // 日志标签
        Tab logTab = new Tab("验证日志");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: 'Courier New';");
        logTab.setContent(logArea);
        logTab.setClosable(false);

        // 报告标签
        Tab reportTab = new Tab("验证报告");
        reportView = new WebView();
        reportTab.setContent(reportView);
        reportTab.setClosable(false);

        tabs.getTabs().addAll(logTab, reportTab);
        return tabs;
    }

    private HBox createControlBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10));

        validateBtn = new Button("开始验证");
        validateBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        validateBtn.setOnAction(e -> runValidation());

        exportBtn = new Button("导出报告");
        exportBtn.setDisable(true);
        exportBtn.setOnAction(e -> exportReport());

        Button closeBtn = new Button("关闭");
        closeBtn.setOnAction(e -> {
            validator.shutdown();
            close();
        });

        bar.getChildren().addAll(validateBtn, exportBtn, closeBtn);
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

        bar.getChildren().addAll(statusLabel, progressBar);
        return bar;
    }

    private void runValidation() {
        String directory = directoryField.getText();
        if (directory.isEmpty()) {
            showAlert("请选择数据目录", Alert.AlertType.WARNING);
            return;
        }

        validateBtn.setDisable(true);
        exportBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("正在验证...");
        logArea.clear();

        log("开始数据一致性验证...");
        log("数据目录: " + directory);

        Task<ValidationReport> task = new Task<ValidationReport>() {
            @Override
            protected ValidationReport call() throws Exception {
                return validator.validateAll(directory);
            }

            @Override
            protected void succeeded() {
                ValidationReport report = getValue();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    validateBtn.setDisable(false);
                    exportBtn.setDisable(false);
                    statusLabel.setText(report.getSummary());

                    displayReport(report);
                    log("\n" + report.getSummary());
                    log("验证耗时: " + report.getValidationTime() + " ms");

                    if (report.getTotalErrors() > 0) {
                        showAlert("验证完成，发现 " + report.getTotalErrors() + " 个错误",
                                Alert.AlertType.WARNING);
                    } else {
                        showAlert("验证完成，未发现错误", Alert.AlertType.INFORMATION);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    validateBtn.setDisable(false);
                    statusLabel.setText("验证失败");

                    Throwable ex = getException();
                    log.error("Validation failed", ex);
                    log("验证失败: " + ex.getMessage());
                    showAlert("验证失败: " + ex.getMessage(), Alert.AlertType.ERROR);
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void displayReport(ValidationReport report) {
        String html = validator.generateHtmlReport(report);
        WebEngine engine = reportView.getEngine();
        engine.loadContent(html);

        // 在日志中显示主要问题
        if (!report.getResults().isEmpty()) {
            log("\n主要问题:");
            report.getResultsByType().forEach((type, results) -> {
                log("\n" + type + " (" + results.size() + " 个):");
                results.stream()
                    .limit(5)
                    .forEach(r -> log("  - " + r.getMessage()));
                if (results.size() > 5) {
                    log("  ... 还有 " + (results.size() - 5) + " 个");
                }
            });
        }
    }

    private void exportReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出验证报告");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("HTML文件", "*.html"));
        chooser.setInitialFileName("validation_report_" +
            System.currentTimeMillis() + ".html");

        File file = chooser.showSaveDialog(this);
        if (file != null) {
            try {
                WebEngine engine = reportView.getEngine();
                String html = (String) engine.executeScript("document.documentElement.outerHTML");
                Files.write(Paths.get(file.getAbsolutePath()),
                          html.getBytes("UTF-8"));
                log("报告已导出到: " + file.getAbsolutePath());
                showAlert("报告导出成功", Alert.AlertType.INFORMATION);
            } catch (Exception e) {
                log.error("Export failed", e);
                showAlert("导出失败: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "错误" : "提示");
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void close() {
        validator.shutdown();
        super.close();
    }
}