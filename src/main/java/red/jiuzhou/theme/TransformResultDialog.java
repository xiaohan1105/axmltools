package red.jiuzhou.theme;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 转换结果详细报告对话框
 * 支持多格式导出（TXT、CSV、HTML、JSON）
 *
 * @author Claude
 * @version 2.0
 */
public class TransformResultDialog {

    private static final Logger log = LoggerFactory.getLogger(TransformResultDialog.class);

    private final Stage owner;
    private final BatchTransformEngine.TransformResult result;
    private Stage dialogStage;

    public TransformResultDialog(Stage owner, BatchTransformEngine.TransformResult result) {
        this.owner = owner;
        this.result = result;
    }

    public void show() {
        dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("转换结果报告");
        dialogStage.setWidth(800);
        dialogStage.setHeight(600);

        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(buildSummaryTab());
        tabPane.getTabs().add(buildDetailsTab());

        if (!result.getErrors().isEmpty()) {
            tabPane.getTabs().add(buildErrorsTab());
        }

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);

        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(12));

        // 导出下拉菜单
        MenuButton exportMenu = new MenuButton("导出报告");
        exportMenu.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");

        MenuItem exportTxt = new MenuItem("导出为文本文件 (.txt)");
        exportTxt.setOnAction(e -> exportReport(ExportFormat.TXT));

        MenuItem exportCsv = new MenuItem("导出为CSV文件 (.csv)");
        exportCsv.setOnAction(e -> exportReport(ExportFormat.CSV));

        MenuItem exportHtml = new MenuItem("导出为HTML报告 (.html)");
        exportHtml.setOnAction(e -> exportReport(ExportFormat.HTML));

        MenuItem exportJson = new MenuItem("导出为JSON文件 (.json)");
        exportJson.setOnAction(e -> exportReport(ExportFormat.JSON));

        exportMenu.getItems().addAll(exportTxt, exportCsv, exportHtml, exportJson);

        Button copyBtn = new Button("复制到剪贴板");
        copyBtn.setOnAction(e -> copyToClipboard());

        Button closeButton = new Button("关闭");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> dialogStage.close());

        buttonBar.getChildren().addAll(exportMenu, copyBtn, closeButton);
        root.setBottom(buttonBar);

        Scene scene = new Scene(root);
        dialogStage.setScene(scene);
        dialogStage.show();
    }

    /**
     * 摘要标签页
     */
    private Tab buildSummaryTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        // 状态显示
        HBox statusBox = buildStatusBox();

        // 统计信息
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(20);
        statsGrid.setVgap(12);
        statsGrid.setPadding(new Insets(15));
        statsGrid.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #ddd; -fx-border-width: 1;");

        int row = 0;

        statsGrid.add(createLabel("总文件数:", true), 0, row);
        statsGrid.add(createLabel(String.valueOf(result.getTotalFiles()), false), 1, row);
        row++;

        statsGrid.add(createLabel("成功:", true), 0, row);
        Label successLabel = createLabel(String.valueOf(result.getSuccessfulFiles()), false);
        successLabel.setTextFill(Color.GREEN);
        statsGrid.add(successLabel, 1, row);
        row++;

        if (result.getTotalFiles() > result.getSuccessfulFiles()) {
            statsGrid.add(createLabel("失败:", true), 0, row);
            Label failLabel = createLabel(String.valueOf(result.getTotalFiles() - result.getSuccessfulFiles()), false);
            failLabel.setTextFill(Color.RED);
            statsGrid.add(failLabel, 1, row);
            row++;
        }

        statsGrid.add(createLabel("修改字段:", true), 0, row);
        statsGrid.add(createLabel(String.valueOf(result.getTotalChangedFields()), false), 1, row);
        row++;

        long durationSeconds = (result.getCompletedAt().toEpochMilli() - result.getStartedAt().toEpochMilli()) / 1000;
        if (durationSeconds <= 0) durationSeconds = 1; // 避免除以0

        statsGrid.add(createLabel("耗时:", true), 0, row);
        statsGrid.add(createLabel(formatDuration(durationSeconds), false), 1, row);
        row++;

        // 性能信息
        if (result.getSuccessfulFiles() > 0 && durationSeconds > 0) {
            double filesPerSecond = (double) result.getSuccessfulFiles() / durationSeconds;
            double fieldsPerSecond = (double) result.getTotalChangedFields() / durationSeconds;

            statsGrid.add(createLabel("处理速度:", true), 0, row);
            statsGrid.add(createLabel(String.format("%.1f 文件/秒, %.1f 字段/秒",
                    filesPerSecond, fieldsPerSecond), false), 1, row);
        }

        content.getChildren().addAll(statusBox, new Separator(), statsGrid);

        Tab tab = new Tab("摘要", new ScrollPane(content));
        tab.setClosable(false);
        return tab;
    }

    /**
     * 详情标签页
     */
    private Tab buildDetailsTab() {
        TableView<BatchTransformEngine.FileTransformResult> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BatchTransformEngine.FileTransformResult, String> fileCol = new TableColumn<>("文件");
        fileCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getFile().getFileName().toString()));
        fileCol.setPrefWidth(300);

        TableColumn<BatchTransformEngine.FileTransformResult, String> pathCol = new TableColumn<>("路径");
        pathCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getFile().getParent() != null ?
                        data.getValue().getFile().getParent().toString() : ""));
        pathCol.setPrefWidth(200);

        TableColumn<BatchTransformEngine.FileTransformResult, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().isSuccess() ? "✓ 成功" : "✗ 失败"));
        statusCol.setPrefWidth(80);
        statusCol.setCellFactory(col -> new TableCell<BatchTransformEngine.FileTransformResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("成功")) {
                        setTextFill(Color.GREEN);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<BatchTransformEngine.FileTransformResult, String> changedCol = new TableColumn<>("修改字段数");
        changedCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(data.getValue().getChangedFields())));
        changedCol.setPrefWidth(100);
        changedCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<BatchTransformEngine.FileTransformResult, String> errorCol = new TableColumn<>("错误信息");
        errorCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getErrorMessage() != null ? data.getValue().getErrorMessage() : "-"));
        errorCol.setCellFactory(col -> new TableCell<BatchTransformEngine.FileTransformResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    if (!"-".equals(item)) {
                        setTooltip(new Tooltip(item));
                        setTextFill(Color.RED);
                    }
                }
            }
        });

        table.getColumns().addAll(fileCol, pathCol, statusCol, changedCol, errorCol);
        table.getItems().addAll(result.getFileResults());

        // 右键菜单
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyPathItem = new MenuItem("复制文件路径");
        copyPathItem.setOnAction(e -> {
            BatchTransformEngine.FileTransformResult selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(selected.getFile().toString());
                clipboard.setContent(content);
            }
        });
        contextMenu.getItems().add(copyPathItem);
        table.setContextMenu(contextMenu);

        Tab tab = new Tab("详情 (" + result.getFileResults().size() + ")", table);
        tab.setClosable(false);
        return tab;
    }

    /**
     * 错误标签页
     */
    private Tab buildErrorsTab() {
        ListView<String> errorListView = new ListView<>();
        errorListView.getItems().addAll(result.getErrors());
        errorListView.setCellFactory(listView -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label errorLabel = new Label("✗ " + item);
                    errorLabel.setTextFill(Color.RED);
                    errorLabel.setWrapText(true);
                    errorLabel.setMaxWidth(Double.MAX_VALUE);
                    setGraphic(errorLabel);
                }
            }
        });

        Label warningLabel = new Label(
                "以下是转换过程中遇到的错误。" +
                        (result.getStatus() == BatchTransformEngine.TransformStatus.ROLLED_BACK ?
                                "\n已回滚所有更改。" : "\n部分文件可能未被修改。")
        );
        warningLabel.setWrapText(true);
        warningLabel.setPadding(new Insets(10));
        warningLabel.setStyle("-fx-background-color: #FFF3CD; -fx-border-color: #FFC107; -fx-border-width: 1;");

        VBox content = new VBox(10, warningLabel, errorListView);
        VBox.setVgrow(errorListView, Priority.ALWAYS);
        content.setPadding(new Insets(10));

        Tab tab = new Tab("错误 (" + result.getErrors().size() + ")", content);
        tab.setClosable(false);
        return tab;
    }

    /**
     * 构建状态显示框
     */
    private HBox buildStatusBox() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(15));

        String statusText;
        String statusColor;
        String statusIcon;

        switch (result.getStatus()) {
            case SUCCESS:
                statusText = "转换成功";
                statusColor = "#4CAF50";
                statusIcon = "✓";
                break;
            case PARTIAL_SUCCESS:
                statusText = "部分成功";
                statusColor = "#FF9800";
                statusIcon = "⚠";
                break;
            case FAILED:
                statusText = "转换失败";
                statusColor = "#F44336";
                statusIcon = "✗";
                break;
            case ROLLED_BACK:
                statusText = "已回滚";
                statusColor = "#9E9E9E";
                statusIcon = "↺";
                break;
            default:
                statusText = "进行中";
                statusColor = "#2196F3";
                statusIcon = "⟳";
        }

        Label iconLabel = new Label(statusIcon);
        iconLabel.setStyle(String.format(
                "-fx-font-size: 36px; -fx-text-fill: %s; -fx-font-weight: bold;",
                statusColor));

        Label textLabel = new Label(statusText);
        textLabel.setStyle(String.format(
                "-fx-font-size: 24px; -fx-text-fill: %s; -fx-font-weight: bold;",
                statusColor));

        box.getChildren().addAll(iconLabel, textLabel);
        return box;
    }

    private Label createLabel(String text, boolean bold) {
        Label label = new Label(text);
        if (bold) {
            label.setStyle("-fx-font-weight: bold;");
        }
        return label;
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return String.format("%d 分 %d 秒", minutes, secs);
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return String.format("%d 小时 %d 分", hours, minutes);
        }
    }

    // ==================== 导出功能 ====================

    private enum ExportFormat {
        TXT, CSV, HTML, JSON
    }

    private void exportReport(ExportFormat format) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("导出转换报告");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String defaultName = String.format("transform_report_%s", timestamp);

        switch (format) {
            case TXT:
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件", "*.txt"));
                fileChooser.setInitialFileName(defaultName + ".txt");
                break;
            case CSV:
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV文件", "*.csv"));
                fileChooser.setInitialFileName(defaultName + ".csv");
                break;
            case HTML:
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML文件", "*.html"));
                fileChooser.setInitialFileName(defaultName + ".html");
                break;
            case JSON:
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON文件", "*.json"));
                fileChooser.setInitialFileName(defaultName + ".json");
                break;
        }

        File file = fileChooser.showSaveDialog(dialogStage);
        if (file != null) {
            try {
                String content;
                switch (format) {
                    case TXT:
                        content = generateTxtReport();
                        break;
                    case CSV:
                        content = generateCsvReport();
                        break;
                    case HTML:
                        content = generateHtmlReport();
                        break;
                    case JSON:
                        content = generateJsonReport();
                        break;
                    default:
                        content = generateTxtReport();
                }

                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                    writer.write(content);
                }

                showInfo("导出成功", "报告已保存到:\n" + file.getAbsolutePath());
                log.info("转换报告已导出: {}", file.getAbsolutePath());

            } catch (IOException e) {
                log.error("导出报告失败", e);
                showError("导出失败: " + e.getMessage());
            }
        }
    }

    private String generateTxtReport() {
        StringBuilder report = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        long durationSeconds = (result.getCompletedAt().toEpochMilli() - result.getStartedAt().toEpochMilli()) / 1000;
        if (durationSeconds <= 0) durationSeconds = 1;

        report.append("═══════════════════════════════════════════════════════════════\n");
        report.append("                    主题转换报告\n");
        report.append("═══════════════════════════════════════════════════════════════\n\n");

        report.append("生成时间: ").append(timestamp).append("\n");
        report.append("主题ID:   ").append(result.getThemeId()).append("\n");
        report.append("\n");

        report.append("─────────────────── 转换摘要 ───────────────────\n\n");
        report.append("状态:     ").append(result.getStatus().getDisplayName()).append("\n");
        report.append("总文件数: ").append(result.getTotalFiles()).append("\n");
        report.append("成功:     ").append(result.getSuccessfulFiles()).append("\n");
        report.append("失败:     ").append(result.getTotalFiles() - result.getSuccessfulFiles()).append("\n");
        report.append("修改字段: ").append(result.getTotalChangedFields()).append("\n");
        report.append("耗时:     ").append(formatDuration(durationSeconds)).append("\n");

        if (result.getSuccessfulFiles() > 0) {
            double filesPerSecond = (double) result.getSuccessfulFiles() / durationSeconds;
            report.append("处理速度: ").append(String.format("%.1f 文件/秒\n", filesPerSecond));
        }

        report.append("\n");
        report.append("─────────────────── 文件明细 ───────────────────\n\n");

        for (BatchTransformEngine.FileTransformResult fileResult : result.getFileResults()) {
            String status = fileResult.isSuccess() ? "[成功]" : "[失败]";
            report.append(String.format("%s %s\n", status, fileResult.getFile().getFileName()));
            report.append("    路径: ").append(fileResult.getFile()).append("\n");
            report.append("    修改字段数: ").append(fileResult.getChangedFields()).append("\n");
            if (fileResult.getErrorMessage() != null) {
                report.append("    错误: ").append(fileResult.getErrorMessage()).append("\n");
            }
            report.append("\n");
        }

        if (!result.getErrors().isEmpty()) {
            report.append("─────────────────── 错误信息 ───────────────────\n\n");
            for (String error : result.getErrors()) {
                report.append("  • ").append(error).append("\n");
            }
            report.append("\n");
        }

        report.append("═══════════════════════════════════════════════════════════════\n");
        report.append("                    报告结束\n");
        report.append("═══════════════════════════════════════════════════════════════\n");

        return report.toString();
    }

    private String generateCsvReport() {
        StringBuilder csv = new StringBuilder();
        // BOM for Excel UTF-8 support
        csv.append("\uFEFF");
        csv.append("文件名,文件路径,状态,修改字段数,错误信息\n");

        for (BatchTransformEngine.FileTransformResult fileResult : result.getFileResults()) {
            csv.append(escapeCsv(fileResult.getFile().getFileName().toString())).append(",");
            csv.append(escapeCsv(fileResult.getFile().toString())).append(",");
            csv.append(fileResult.isSuccess() ? "成功" : "失败").append(",");
            csv.append(fileResult.getChangedFields()).append(",");
            csv.append(escapeCsv(fileResult.getErrorMessage() != null ? fileResult.getErrorMessage() : ""));
            csv.append("\n");
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String generateHtmlReport() {
        StringBuilder html = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        long durationSeconds = (result.getCompletedAt().toEpochMilli() - result.getStartedAt().toEpochMilli()) / 1000;
        if (durationSeconds <= 0) durationSeconds = 1;

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>主题转换报告</title>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: 'Microsoft YaHei', sans-serif; margin: 20px; background: #f5f5f5; }\n");
        html.append("    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append("    h1 { color: #333; border-bottom: 2px solid #3498db; padding-bottom: 10px; }\n");
        html.append("    .summary { background: #f8f9fa; padding: 15px; border-radius: 5px; margin: 20px 0; }\n");
        html.append("    .status { display: inline-block; padding: 8px 20px; border-radius: 4px; font-weight: bold; font-size: 18px; margin-bottom: 15px; }\n");
        html.append("    .status-success { background: #d4edda; color: #155724; }\n");
        html.append("    .status-partial { background: #fff3cd; color: #856404; }\n");
        html.append("    .status-failed { background: #f8d7da; color: #721c24; }\n");
        html.append("    .status-rollback { background: #e2e3e5; color: #383d41; }\n");
        html.append("    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; margin-top: 15px; }\n");
        html.append("    .stat-card { background: white; padding: 15px; border-radius: 5px; border: 1px solid #ddd; text-align: center; }\n");
        html.append("    .stat-value { font-size: 24px; font-weight: bold; color: #3498db; }\n");
        html.append("    .stat-label { color: #666; margin-top: 5px; }\n");
        html.append("    table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
        html.append("    th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }\n");
        html.append("    th { background: #3498db; color: white; }\n");
        html.append("    tr:hover { background: #f5f5f5; }\n");
        html.append("    .success { color: #28a745; font-weight: bold; }\n");
        html.append("    .failed { color: #dc3545; font-weight: bold; }\n");
        html.append("    .errors { background: #fff3cd; padding: 15px; border-radius: 5px; margin-top: 20px; border-left: 4px solid #ffc107; }\n");
        html.append("    .errors h3 { margin-top: 0; color: #856404; }\n");
        html.append("    .errors ul { margin: 0; padding-left: 20px; }\n");
        html.append("    .footer { margin-top: 20px; text-align: center; color: #888; font-size: 12px; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <h1>主题转换报告</h1>\n");

        // 状态
        String statusClass;
        switch (result.getStatus()) {
            case SUCCESS:
                statusClass = "status-success";
                break;
            case PARTIAL_SUCCESS:
                statusClass = "status-partial";
                break;
            case FAILED:
                statusClass = "status-failed";
                break;
            default:
                statusClass = "status-rollback";
        }

        html.append("    <div class=\"summary\">\n");
        html.append("      <div class=\"status ").append(statusClass).append("\">")
                .append(result.getStatus().getDisplayName()).append("</div>\n");
        html.append("      <p><strong>生成时间:</strong> ").append(timestamp).append("</p>\n");
        html.append("      <p><strong>主题ID:</strong> ").append(escapeHtml(result.getThemeId())).append("</p>\n");
        html.append("      <div class=\"stats\">\n");
        html.append("        <div class=\"stat-card\"><div class=\"stat-value\">").append(result.getTotalFiles()).append("</div><div class=\"stat-label\">总文件数</div></div>\n");
        html.append("        <div class=\"stat-card\"><div class=\"stat-value\" style=\"color:#28a745\">").append(result.getSuccessfulFiles()).append("</div><div class=\"stat-label\">成功</div></div>\n");
        html.append("        <div class=\"stat-card\"><div class=\"stat-value\" style=\"color:#dc3545\">").append(result.getTotalFiles() - result.getSuccessfulFiles()).append("</div><div class=\"stat-label\">失败</div></div>\n");
        html.append("        <div class=\"stat-card\"><div class=\"stat-value\">").append(result.getTotalChangedFields()).append("</div><div class=\"stat-label\">修改字段</div></div>\n");
        html.append("        <div class=\"stat-card\"><div class=\"stat-value\">").append(formatDuration(durationSeconds)).append("</div><div class=\"stat-label\">耗时</div></div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");

        // 文件列表
        html.append("    <h2>文件明细</h2>\n");
        html.append("    <table>\n");
        html.append("      <thead><tr><th>文件名</th><th>状态</th><th>修改字段</th><th>错误信息</th></tr></thead>\n");
        html.append("      <tbody>\n");

        for (BatchTransformEngine.FileTransformResult fileResult : result.getFileResults()) {
            html.append("        <tr>\n");
            html.append("          <td title=\"").append(escapeHtml(fileResult.getFile().toString())).append("\">")
                    .append(escapeHtml(fileResult.getFile().getFileName().toString())).append("</td>\n");
            html.append("          <td class=\"").append(fileResult.isSuccess() ? "success" : "failed").append("\">")
                    .append(fileResult.isSuccess() ? "✓ 成功" : "✗ 失败").append("</td>\n");
            html.append("          <td>").append(fileResult.getChangedFields()).append("</td>\n");
            html.append("          <td>").append(escapeHtml(fileResult.getErrorMessage() != null ? fileResult.getErrorMessage() : "-")).append("</td>\n");
            html.append("        </tr>\n");
        }

        html.append("      </tbody>\n");
        html.append("    </table>\n");

        // 错误列表
        if (!result.getErrors().isEmpty()) {
            html.append("    <div class=\"errors\">\n");
            html.append("      <h3>错误信息</h3>\n");
            html.append("      <ul>\n");
            for (String error : result.getErrors()) {
                html.append("        <li>").append(escapeHtml(error)).append("</li>\n");
            }
            html.append("      </ul>\n");
            html.append("    </div>\n");
        }

        html.append("    <div class=\"footer\">\n");
        html.append("      <p>由 DbxmlTool 主题系统生成</p>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String generateJsonReport() {
        StringBuilder json = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        long durationMs = result.getCompletedAt().toEpochMilli() - result.getStartedAt().toEpochMilli();

        json.append("{\n");
        json.append("  \"report\": {\n");
        json.append("    \"generatedAt\": \"").append(timestamp).append("\",\n");
        json.append("    \"themeId\": \"").append(escapeJson(result.getThemeId())).append("\",\n");
        json.append("    \"status\": \"").append(result.getStatus().name()).append("\",\n");
        json.append("    \"summary\": {\n");
        json.append("      \"totalFiles\": ").append(result.getTotalFiles()).append(",\n");
        json.append("      \"successfulFiles\": ").append(result.getSuccessfulFiles()).append(",\n");
        json.append("      \"failedFiles\": ").append(result.getTotalFiles() - result.getSuccessfulFiles()).append(",\n");
        json.append("      \"totalChangedFields\": ").append(result.getTotalChangedFields()).append(",\n");
        json.append("      \"durationMs\": ").append(durationMs).append("\n");
        json.append("    },\n");
        json.append("    \"files\": [\n");

        boolean first = true;
        for (BatchTransformEngine.FileTransformResult fileResult : result.getFileResults()) {
            if (!first) json.append(",\n");
            first = false;

            json.append("      {\n");
            json.append("        \"fileName\": \"").append(escapeJson(fileResult.getFile().getFileName().toString())).append("\",\n");
            json.append("        \"filePath\": \"").append(escapeJson(fileResult.getFile().toString())).append("\",\n");
            json.append("        \"success\": ").append(fileResult.isSuccess()).append(",\n");
            json.append("        \"changedFields\": ").append(fileResult.getChangedFields()).append(",\n");
            json.append("        \"errorMessage\": ").append(fileResult.getErrorMessage() != null ?
                    "\"" + escapeJson(fileResult.getErrorMessage()) + "\"" : "null").append("\n");
            json.append("      }");
        }

        json.append("\n    ],\n");
        json.append("    \"errors\": [\n");

        first = true;
        for (String error : result.getErrors()) {
            if (!first) json.append(",\n");
            first = false;
            json.append("      \"").append(escapeJson(error)).append("\"");
        }

        json.append("\n    ]\n");
        json.append("  }\n");
        json.append("}\n");

        return json.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void copyToClipboard() {
        String report = generateTxtReport();

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(report);
        clipboard.setContent(content);

        showInfo("复制成功", "报告已复制到剪贴板");
    }

    // ==================== 辅助方法 ====================

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(dialogStage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(dialogStage);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
