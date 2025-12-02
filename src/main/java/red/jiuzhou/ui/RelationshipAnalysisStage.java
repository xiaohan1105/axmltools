package red.jiuzhou.ui;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import red.jiuzhou.relationship.XmlRelationshipAnalyzer;
import red.jiuzhou.util.YamlUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A lightweight stage that visualises relationships detected by {@link XmlRelationshipAnalyzer}.
 */
public class RelationshipAnalysisStage extends Stage {

    private final TableView<RelationshipRow> tableView = new TableView<>();
    private final FilteredList<RelationshipRow> filteredRows;
    private final Label tallyLabel = new Label();

    public RelationshipAnalysisStage(XmlRelationshipAnalyzer.RelationshipReport report) {
        setTitle("Name字段关联分析 - 游戏配置对照");
        initModality(Modality.NONE);

        List<RelationshipRow> rowData = report.getRelationshipSnapshots().stream()
            .map(RelationshipRow::new)
            .collect(Collectors.toList());
        ObservableList<RelationshipRow> rows = FXCollections.<RelationshipRow>observableArrayList();
        rows.addAll(rowData);
        filteredRows = new FilteredList<>(rows, r -> true);

        tableView.setItems(filteredRows);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setRowFactory(tv -> createStyledTableRow());
        buildColumns();

        // 增强的过滤和操作栏
        TextField filterField = new TextField();
        filterField.setPromptText("输入文件或name字段关键字过滤");
        filterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(newVal));
        filterField.setPrefWidth(280);

        Button revealButton = new Button("打开结果");
        revealButton.setOnAction(event -> openReportLocation(report));

        Button exportButton = new Button("导出Excel");
        exportButton.setOnAction(event -> exportToExcel(report));

        Button copyButton = new Button("复制选中");
        copyButton.setOnAction(event -> copySelectedRows());
        copyButton.setDisable(true);

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            copyButton.setDisable(selected == null);
        });

        ToolBar toolBar = new ToolBar(filterField, revealButton, exportButton, copyButton);
        toolBar.setStyle("-fx-spacing: 8; -fx-background-color: transparent;");
        toolBar.setPadding(new Insets(6, 0, 6, 0));

        // 增强的统计信息
        Label statsLabel = buildStatsLabel(rowData);
        HBox header = new HBox(12, new Label("命中关系:"), tallyLabel, new Label("|"), statsLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 0, 6, 0));

        // 关键洞察提示
        VBox insightBox = buildInsightBox(rowData);

        VBox topBox = new VBox(4, header, toolBar, insightBox);
        topBox.setPadding(new Insets(0, 8, 8, 8));

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(tableView);
        root.setPadding(new Insets(8));

        updateTally();
        Scene scene = new Scene(root, 1200, 700);
        setScene(scene);
    }

    private javafx.scene.control.TableRow<RelationshipRow> createStyledTableRow() {
        return new javafx.scene.control.TableRow<RelationshipRow>() {
            @Override
            protected void updateItem(RelationshipRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setStyle("");
                } else {
                    // 根据置信度着色
                    String confidenceStr = row.confidence.get();
                    if (confidenceStr.contains("极高") || confidenceStr.contains("100%")) {
                        setStyle("-fx-background-color: rgba(76, 175, 80, 0.15);");
                    } else if (confidenceStr.contains("很高") || confidenceStr.startsWith("9")) {
                        setStyle("-fx-background-color: rgba(139, 195, 74, 0.12);");
                    } else if (confidenceStr.contains("高")) {
                        setStyle("-fx-background-color: rgba(255, 235, 59, 0.10);");
                    } else {
                        setStyle("");
                    }
                }
            }
        };
    }

    private Label buildStatsLabel(List<RelationshipRow> rows) {
        int highConfidence = 0;
        int oneToOne = 0;
        for (RelationshipRow row : rows) {
            String conf = row.confidence.get();
            if (conf.contains("极高") || conf.contains("很高")) {
                highConfidence++;
            }
            // 简单判断一对一关系（源覆盖和目标覆盖都接近100%）
            String sourceCov = row.sourceCoverage.get();
            String targetCov = row.targetCoverage.get();
            if (sourceCov.startsWith("9") && targetCov.startsWith("9")) {
                oneToOne++;
            }
        }
        String text = String.format("高置信度: %d  |  一对一: %d", highConfidence, oneToOne);
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #666;");
        return label;
    }

    private VBox buildInsightBox(List<RelationshipRow> rows) {
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 8; -fx-background-radius: 4;");

        Label title = new Label("💡 关键洞察：");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        // 分析并生成洞察
        List<String> insights = analyzeInsights(rows);
        if (insights.isEmpty()) {
            Label noInsight = new Label("未发现特别需要关注的关联模式");
            noInsight.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
            box.getChildren().addAll(title, noInsight);
        } else {
            box.getChildren().add(title);
            for (String insight : insights) {
                Label label = new Label("• " + insight);
                label.setStyle("-fx-font-size: 11px; -fx-text-fill: #333;");
                label.setWrapText(true);
                box.getChildren().add(label);
            }
        }

        return box;
    }

    private List<String> analyzeInsights(List<RelationshipRow> rows) {
        List<String> insights = new ArrayList<>();

        // 分析强关联
        long strongLinks = rows.stream()
            .filter(r -> r.confidence.get().contains("极高"))
            .count();
        if (strongLinks > 5) {
            insights.add(String.format("发现 %d 个极高置信度关联，数据设计规范性较好", strongLinks));
        }

        // 分析跨文件关联
        Set<String> sourceFiles = rows.stream()
            .map(r -> r.sourceFile.get())
            .collect(Collectors.toSet());
        if (sourceFiles.size() > 10) {
            insights.add(String.format("涉及 %d 个源文件，数据关联复杂度较高", sourceFiles.size()));
        }

        // 分析被频繁引用的name字段
        Map<String, Long> fieldFrequency = rows.stream()
            .collect(Collectors.groupingBy(r -> r.sourceColumn.get(), Collectors.counting()));
        fieldFrequency.entrySet().stream()
            .filter(e -> e.getValue() >= 3)
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(2)
            .forEach(e -> {
                insights.add(String.format("Name字段 '%s' 被 %d 个配置表引用，是重要的核心数据", e.getKey(), e.getValue()));
            });

        return insights;
    }

    private void exportToExcel(XmlRelationshipAnalyzer.RelationshipReport report) {
        // TODO: 实现Excel导出功能
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("导出");
        alert.setHeaderText("功能开发中");
        alert.setContentText("Excel导出功能即将推出");
        alert.showAndWait();
    }

    private void copySelectedRows() {
        RelationshipRow selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String text = String.format("%s.%s -> %s.%s (匹配:%s, 置信度:%s)",
                selected.sourceFile.get(),
                selected.sourceColumn.get(),
                selected.targetFile.get(),
                selected.targetColumn.get(),
                selected.matchCount.get(),
                selected.confidence.get());

            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }
    }

    private void buildColumns() {
        TableColumn<RelationshipRow, String> sourceFileCol = new TableColumn<>("源文件");
        sourceFileCol.setCellValueFactory(param -> param.getValue().sourceFile);
        sourceFileCol.setPrefWidth(220);

        TableColumn<RelationshipRow, String> sourceColumnCol = new TableColumn<>("源字段");
        sourceColumnCol.setCellValueFactory(param -> param.getValue().sourceColumn);
        sourceColumnCol.setPrefWidth(260);

        TableColumn<RelationshipRow, String> targetFileCol = new TableColumn<>("目标文件");
        targetFileCol.setCellValueFactory(param -> param.getValue().targetFile);
        targetFileCol.setPrefWidth(220);

        TableColumn<RelationshipRow, String> targetColumnCol = new TableColumn<>("目标字段");
        targetColumnCol.setCellValueFactory(param -> param.getValue().targetColumn);
        targetColumnCol.setPrefWidth(260);

        TableColumn<RelationshipRow, Number> matchColumn = new TableColumn<>("匹配值数量");
        matchColumn.setCellValueFactory(param -> param.getValue().matchCount);
        matchColumn.setPrefWidth(110);

        TableColumn<RelationshipRow, String> sourceCoverageCol = new TableColumn<>("源覆盖率");
        sourceCoverageCol.setCellValueFactory(param -> param.getValue().sourceCoverage);
        sourceCoverageCol.setPrefWidth(110);

        TableColumn<RelationshipRow, String> targetCoverageCol = new TableColumn<>("目标覆盖率");
        targetCoverageCol.setCellValueFactory(param -> param.getValue().targetCoverage);
        targetCoverageCol.setPrefWidth(110);

        TableColumn<RelationshipRow, String> confidenceCol = new TableColumn<>("置信度");
        confidenceCol.setCellValueFactory(param -> param.getValue().confidence);
        confidenceCol.setPrefWidth(100);

        TableColumn<RelationshipRow, String> semanticCol = new TableColumn<>("语义相似度");
        semanticCol.setCellValueFactory(param -> param.getValue().nameSimilarity);
        semanticCol.setPrefWidth(120);

        TableColumn<RelationshipRow, String> sampleCol = new TableColumn<>("示例值");
        sampleCol.setCellValueFactory(param -> param.getValue().sampleValues);
        sampleCol.setPrefWidth(240);

        tableView.getColumns().addAll(
            sourceFileCol,
            sourceColumnCol,
            targetFileCol,
            targetColumnCol,
            matchColumn,
            sourceCoverageCol,
            targetCoverageCol,
            confidenceCol,
            semanticCol,
            sampleCol
        );
    }

    private void applyFilter(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            filteredRows.setPredicate(r -> true);
        } else {
            String lower = keyword.trim().toLowerCase(Locale.ROOT);
            filteredRows.setPredicate(row ->
                row.sourceFile.get().toLowerCase(Locale.ROOT).contains(lower)
                    || row.sourceColumn.get().toLowerCase(Locale.ROOT).contains(lower)
                    || row.targetFile.get().toLowerCase(Locale.ROOT).contains(lower)
                    || row.targetColumn.get().toLowerCase(Locale.ROOT).contains(lower)
                    || row.sampleValues.get().toLowerCase(Locale.ROOT).contains(lower)
            );
        }
        updateTally();
    }

    private void updateTally() {
        tallyLabel.setText(filteredRows.size() + " / " + tableView.getItems().size());
    }

    private void openReportLocation(XmlRelationshipAnalyzer.RelationshipReport report) {
        try {
            String confDir = YamlUtils.getProperty("file.confPath");
            if (confDir == null || confDir.trim().isEmpty()) {
                return;
            }
            Path confPath = Paths.get(confDir);
            Path reportFile = confPath.resolve("analysis").resolve("relationship-analysis.json");
            if (java.nio.file.Files.exists(reportFile) && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(reportFile.toFile());
            }
        } catch (Exception ignored) {
        }
    }

    private static final class RelationshipRow {
        private final SimpleStringProperty sourceFile;
        private final SimpleStringProperty sourceColumn;
        private final SimpleStringProperty targetFile;
        private final SimpleStringProperty targetColumn;
        private final SimpleIntegerProperty matchCount;
        private final SimpleStringProperty sourceCoverage;
        private final SimpleStringProperty targetCoverage;
        private final SimpleStringProperty confidence;
        private final SimpleStringProperty nameSimilarity;
        private final SimpleStringProperty sampleValues;

        RelationshipRow(XmlRelationshipAnalyzer.RelationshipSnapshot relationship) {
            this.sourceFile = new SimpleStringProperty(relationship.getSourceFile());
            this.sourceColumn = new SimpleStringProperty(relationship.getSourcePath());
            this.targetFile = new SimpleStringProperty(relationship.getTargetFile());
            this.targetColumn = new SimpleStringProperty(relationship.getTargetPath());
            this.matchCount = new SimpleIntegerProperty(relationship.getMatchCount());
            this.sourceCoverage = new SimpleStringProperty(formatPercent(relationship.getSourceCoverage()));
            this.targetCoverage = new SimpleStringProperty(formatPercent(relationship.getTargetCoverage()));
            this.confidence = new SimpleStringProperty(formatPercent(relationship.getConfidence()));
            this.nameSimilarity = new SimpleStringProperty(formatPercent(relationship.getNameSimilarity()));
            this.sampleValues = new SimpleStringProperty(String.join(", ", relationship.getSamples()));
        }

        public String getSourceFile() {
            return sourceFile.get();
        }

        public String getSourceColumn() {
            return sourceColumn.get();
        }

        public String getTargetFile() {
            return targetFile.get();
        }

        public String getTargetColumn() {
            return targetColumn.get();
        }

        public int getMatchCount() {
            return matchCount.get();
        }

        public String getSourceCoverage() {
            return sourceCoverage.get();
        }

        public String getTargetCoverage() {
            return targetCoverage.get();
        }

        public String getConfidence() {
            return confidence.get();
        }

        public String getNameSimilarity() {
            return nameSimilarity.get();
        }

        public String getSampleValues() {
            return sampleValues.get();
        }

        private String formatPercent(double value) {
            return String.format(Locale.ROOT, "%.1f%%", value * 100);
        }
    }

}
