package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.dbxml.DbToXmlGenerator;
import red.jiuzhou.dbxml.WorldDbToXmlGenerator;
import red.jiuzhou.util.XmlUtil;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批量导入导出工具
 * 支持批量处理目录下所有XML文件的导入导出操作
 *
 * @author Claude
 * @date 2025-11-13
 */
public class BatchImportExportApp {

    private static final Logger log = LoggerFactory.getLogger(BatchImportExportApp.class);

    private TextArea resultArea;
    private TextField directoryField;
    private Stage currentStage;

    public void show(Stage primaryStage) {
        currentStage = new Stage();
        currentStage.setTitle("📁 批量导入/导出工具");
        currentStage.initOwner(primaryStage);

        // 目录选择区域
        Label dirLabel = new Label("目录:");
        directoryField = new TextField();
        directoryField.setPromptText("请选择包含XML文件的目录");
        directoryField.setPrefWidth(600);
        directoryField.setEditable(false);

        Button chooseDirBtn = new Button("📂 选择目录");
        chooseDirBtn.setOnAction(e -> chooseDirectory());

        HBox dirBox = new HBox(10, dirLabel, directoryField, chooseDirBtn);
        dirBox.setAlignment(Pos.CENTER_LEFT);
        dirBox.setPadding(new Insets(10));

        // 结果显示区域
        resultArea = new TextArea();
        resultArea.setPrefHeight(400);
        resultArea.setWrapText(true);
        resultArea.setEditable(false);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        // 按钮区域
        Button batchExportBtn = new Button("📤 批量导出 (DB→XML)");
        batchExportBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        batchExportBtn.setTooltip(new Tooltip("将数据库中的数据批量导出为XML文件"));
        batchExportBtn.setOnAction(e -> batchExport());

        Button batchImportBtn = new Button("📥 批量导入 (XML→DB)");
        batchImportBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        batchImportBtn.setTooltip(new Tooltip("将目录下所有XML文件批量导入到数据库"));
        batchImportBtn.setOnAction(e -> batchImport());

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        HBox buttonBox = new HBox(15, batchExportBtn, batchImportBtn, spinner);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10));

        // 主布局
        VBox root = new VBox(10);
        root.getChildren().addAll(dirBox, resultArea, buttonBox);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 1200, 600);
        currentStage.setScene(scene);
        currentStage.show();

        // 自动加载默认目录
        loadDefaultDirectory();
    }

    /**
     * 选择目录
     */
    private void chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择包含XML文件的目录");

        // 设置初始目录
        String currentPath = directoryField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                chooser.setInitialDirectory(currentDir);
            }
        }

        File selectedDir = chooser.showDialog(currentStage);
        if (selectedDir != null) {
            directoryField.setText(selectedDir.getAbsolutePath());
            resultArea.appendText(String.format("已选择目录: %s\n\n", selectedDir.getAbsolutePath()));
        }
    }

    /**
     * 加载默认目录
     */
    private void loadDefaultDirectory() {
        try {
            String cltDataPath = YamlUtils.getProperty("file.cltDataPath");
            if (cltDataPath != null && !cltDataPath.isEmpty()) {
                directoryField.setText(cltDataPath);
                resultArea.appendText(String.format("默认目录: %s\n", cltDataPath));
                resultArea.appendText("提示: 您可以点击'选择目录'按钮更改处理目录\n\n");
            }
        } catch (Exception e) {
            log.warn("加载默认目录失败: {}", e.getMessage());
        }
    }

    /**
     * 批量导出 (DB → XML)
     */
    private void batchExport() {
        String directory = directoryField.getText();
        if (directory == null || directory.trim().isEmpty()) {
            showAlert("请先选择目录！");
            return;
        }

        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            showAlert("选择的目录不存在！");
            return;
        }

        resultArea.clear();
        resultArea.appendText("========================================\n");
        resultArea.appendText("开始批量导出 (数据库 → XML文件)\n");
        resultArea.appendText("========================================\n\n");

        new Thread(() -> {
            try {
                // 获取目录下所有XML文件（作为模板）
                List<File> xmlFiles = FileUtil.loopFiles(directory).stream()
                        .filter(file -> file.getName().endsWith(".xml"))
                        .collect(Collectors.toList());

                Platform.runLater(() -> resultArea.appendText(
                        String.format("找到 %d 个XML文件，开始批量导出...\n\n", xmlFiles.size())));

                int successCount = 0;
                int failedCount = 0;
                StringBuilder failedFiles = new StringBuilder();

                for (File xmlFile : xmlFiles) {
                    try {
                        String tableName = xmlFile.getName().replace(".xml", "");

                        // 创建final变量供Lambda表达式使用
                        final int currentIndex = successCount + failedCount + 1;
                        final String currentTableName = tableName;
                        final int totalFiles = xmlFiles.size();

                        Platform.runLater(() -> resultArea.appendText(
                                String.format("[%d/%d] 导出: %s\n",
                                        currentIndex,
                                        totalFiles,
                                        currentTableName)));

                        // 导出数据库数据到XML
                        String tabFilePath = stripXmlExtension(xmlFile.getAbsolutePath());
                        String mapType = deriveMapType(tableName, xmlFile);

                        if ("world".equalsIgnoreCase(tableName)) {
                            WorldDbToXmlGenerator generator = new WorldDbToXmlGenerator(tableName, mapType, tabFilePath);
                            generator.processAndMerge();
                        } else {
                            DbToXmlGenerator generator = new DbToXmlGenerator(tableName, mapType, tabFilePath);
                            generator.processAndMerge();
                        }

                        successCount++;
                        Platform.runLater(() -> resultArea.appendText("  ✅ 导出成功\n"));

                    } catch (Exception ex) {
                        failedCount++;
                        log.error("导出文件失败: {}", xmlFile.getName(), ex);
                        failedFiles.append(String.format("  ❌ %s: %s\n",
                                xmlFile.getName(), XmlUtil.getErrorMsg(ex)));
                        Platform.runLater(() -> resultArea.appendText("  ❌ 导出失败\n"));
                    }
                }

                int finalSuccessCount = successCount;
                int finalFailedCount = failedCount;
                String finalFailedFiles = failedFiles.toString();

                Platform.runLater(() -> {
                    resultArea.appendText("\n========================================\n");
                    resultArea.appendText("批量导出完成！\n");
                    resultArea.appendText(String.format("成功: %d 个\n", finalSuccessCount));
                    resultArea.appendText(String.format("失败: %d 个\n", finalFailedCount));

                    if (finalFailedCount > 0) {
                        resultArea.appendText("\n失败文件列表:\n");
                        resultArea.appendText(finalFailedFiles);
                    }

                    resultArea.appendText("========================================\n");
                });

                log.info("批量导出完成: 成功={}, 失败={}", successCount, failedCount);

            } catch (Exception ex) {
                log.error("批量导出出错: {}", XmlUtil.getErrorMsg(ex));
                Platform.runLater(() -> resultArea.appendText(
                        "批量导出失败，请检查日志！\n" + XmlUtil.getErrorMsg(ex)));
            }
        }).start();
    }

    /**
     * 批量导入 (XML → DB)
     */
    private void batchImport() {
        String directory = directoryField.getText();
        if (directory == null || directory.trim().isEmpty()) {
            showAlert("请先选择目录！");
            return;
        }

        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            showAlert("选择的目录不存在！");
            return;
        }

        resultArea.clear();
        resultArea.appendText("========================================\n");
        resultArea.appendText("开始批量导入 (XML文件 → 数据库)\n");
        resultArea.appendText("========================================\n\n");

        new Thread(() -> {
            try {
                // 获取目录下所有XML文件
                List<File> xmlFiles = FileUtil.loopFiles(directory).stream()
                        .filter(file -> file.getName().endsWith(".xml"))
                        .collect(Collectors.toList());

                Platform.runLater(() -> resultArea.appendText(
                        String.format("找到 %d 个XML文件，开始批量导入...\n\n", xmlFiles.size())));

                int successCount = 0;
                int failedCount = 0;
                StringBuilder failedFiles = new StringBuilder();

                for (File xmlFile : xmlFiles) {
                    try {
                        // 创建final变量供Lambda表达式使用
                        final int currentIndex = successCount + failedCount + 1;
                        final String currentFileName = xmlFile.getName();
                        final int totalFiles = xmlFiles.size();

                        Platform.runLater(() -> resultArea.appendText(
                                String.format("[%d/%d] 导入: %s\n",
                                        currentIndex,
                                        totalFiles,
                                        currentFileName)));

                        // 解析XML并生成SQL，然后导入数据库
                        String sqlFilePath = XmlProcess.parseOneXml(xmlFile.getAbsolutePath());
                        red.jiuzhou.util.DatabaseUtil.executeSqlScript(sqlFilePath);

                        successCount++;
                        Platform.runLater(() -> resultArea.appendText("  ✅ 导入成功\n"));

                    } catch (Exception ex) {
                        failedCount++;
                        log.error("导入文件失败: {}", xmlFile.getName(), ex);
                        failedFiles.append(String.format("  ❌ %s: %s\n",
                                xmlFile.getName(), XmlUtil.getErrorMsg(ex)));
                        Platform.runLater(() -> resultArea.appendText("  ❌ 导入失败\n"));
                    }
                }

                int finalSuccessCount = successCount;
                int finalFailedCount = failedCount;
                String finalFailedFiles = failedFiles.toString();

                Platform.runLater(() -> {
                    resultArea.appendText("\n========================================\n");
                    resultArea.appendText("批量导入完成！\n");
                    resultArea.appendText(String.format("成功: %d 个\n", finalSuccessCount));
                    resultArea.appendText(String.format("失败: %d 个\n", finalFailedCount));

                    if (finalFailedCount > 0) {
                        resultArea.appendText("\n失败文件列表:\n");
                        resultArea.appendText(finalFailedFiles);
                    }

                    resultArea.appendText("========================================\n");
                });

                log.info("批量导入完成: 成功={}, 失败={}", successCount, failedCount);

            } catch (Exception ex) {
                log.error("批量导入出错: {}", XmlUtil.getErrorMsg(ex));
                Platform.runLater(() -> resultArea.appendText(
                        "批量导入失败，请检查日志！\n" + XmlUtil.getErrorMsg(ex)));
            }
        }).start();
    }

    /**
     * 显示警告对话框
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("警告");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 移除文件路径中的.xml扩展名
     */
    private String stripXmlExtension(String filePath) {
        if (filePath == null) {
            return null;
        }
        return filePath.toLowerCase().endsWith(".xml")
                ? filePath.substring(0, filePath.length() - 4)
                : filePath;
    }

    /**
     * 推导mapType（仅对world表有效）
     */
    private String deriveMapType(String tabName, File xmlFile) {
        if (tabName == null || xmlFile == null) {
            return null;
        }
        if (!"world".equalsIgnoreCase(tabName)) {
            return null;
        }
        File parent = xmlFile.getParentFile();
        return parent != null ? parent.getName() : null;
    }
}
