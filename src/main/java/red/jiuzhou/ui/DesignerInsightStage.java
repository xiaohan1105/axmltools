package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
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
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.analysis.XmlDesignerInsight;
import red.jiuzhou.analysis.XmlDesignerInsight.AttributeInsight;
import red.jiuzhou.analysis.XmlDesignerInsight.AlignmentCategory;
import red.jiuzhou.analysis.XmlDesignerInsight.AttributeAlignment;
import red.jiuzhou.analysis.XmlDesignerInsight.AttributeValueDistribution;
import red.jiuzhou.analysis.XmlDesignerInsight.Metric;
import red.jiuzhou.analysis.XmlDesignerInsight.Severity;
import red.jiuzhou.analysis.XmlDesignerInsight.Suggestion;
import red.jiuzhou.analysis.XmlDesignerInsight.ValueCount;
import red.jiuzhou.analysis.XmlDesignerInsight.RelatedFileComparison;
import red.jiuzhou.analysis.XmlDesignerInsight.XmlFileSummary;
import red.jiuzhou.analysis.XmlDesignerInsightService;
import red.jiuzhou.ui.components.StatCard;
import red.jiuzhou.ui.features.FeatureTaskExecutor;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * JavaFX stage providing designer-oriented insights for XML content.
 */
@SuppressWarnings("unchecked")
public class DesignerInsightStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(DesignerInsightStage.class);

    private final XmlDesignerInsightService insightService = new XmlDesignerInsightService();
    private final TreeView<FsNode> explorerTree = new TreeView<>();
    private final Label fileTitle = new Label("选择 XML 文件以查看洞察");
    private final Label fileMeta = new Label("");
    private final TableView<MetricRow> metricTable = new TableView<>();
    private final TableView<AttributeRow> attributeTable = new TableView<>();
    private final TableView<ValueDistributionRow> distributionTable = new TableView<>();
    private final Label distributionTitle = new Label("选择字段查看值分布");
    private final TableView<RelatedFileRow> relatedFileTable = new TableView<>();
    private final TableView<AlignmentRow> alignmentTable = new TableView<>();
    private final Label alignmentTitle = new Label("选择对比文件以查看字段差异");
    private final ListView<SuggestionRow> suggestionList = new ListView<>();
    private final ListView<String> sampleList = new ListView<>();
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();
    private final TableView<CorrelationRow> correlationTable = new TableView<>();
    private final TableView<DistributionRow> distributionProfileTable = new TableView<>();
    private final ListView<BalanceIssueRow> balanceIssueList = new ListView<>();
    private final TableView<AttributeTypeRow> attributeTypeTable = new TableView<>();
    private final StackPane insightStack = new StackPane();
    private final Button openFileButton = new Button("打开文件");
    private final Button openFolderButton = new Button("打开目录");
    private final Map<String, AttributeValueDistribution> distributionIndex = new HashMap<>();

    // 统计卡片
    private StatCard recordCountCard;
    private StatCard fieldCountCard;
    private StatCard completenessCard;
    private StatCard issueCountCard;

    private XmlDesignerInsight currentInsight;
    private Path currentPath;
    private Task<XmlDesignerInsight> currentTask;
    private boolean refreshRequired = true;

    public DesignerInsightStage() {
        setTitle("设计师洞察面板");
        setScene(buildScene());
        setMinWidth(960);
        setMinHeight(640);
        setOnCloseRequest(event -> {
            if (currentTask != null && currentTask.isRunning()) {
                currentTask.cancel();
            }
        });
        setOnHidden(event -> refreshRequired = true);
        setOnShown(event -> {
            if (refreshRequired) {
                populateTreeRoots();
            }
        });
        populateTreeRoots();
    }

    public void inspectFile(Path path) {
        if (path == null) {
            return;
        }
        if (!isShowing()) {
            show();
        }
        toFront();
        loadInsightFor(path);
    }

    private Scene buildScene() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().add(buildExplorerPane());
        splitPane.getItems().add(buildInsightPane());
        splitPane.setDividerPositions(0.27);

        BorderPane container = new BorderPane(splitPane);
        container.setPadding(new Insets(12));

        return new Scene(container, 1200, 720);
    }

    private VBox buildExplorerPane() {
        Label title = new Label("XML 目录");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        explorerTree.setShowRoot(false);
        explorerTree.setMinWidth(260);
        explorerTree.setPrefWidth(320);
        explorerTree.setCellFactory(createTreeCellFactory());
        explorerTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null || newItem.getValue() == null) {
                return;
            }
            FsNode node = newItem.getValue();
            if (node.isDirectory()) {
                showDirectorySummary(node);
            } else {
                loadInsightFor(node.getPath());
            }
        });

        VBox wrapper = new VBox(10, title, explorerTree);
        VBox.setVgrow(explorerTree, Priority.ALWAYS);
        wrapper.setPadding(new Insets(8, 12, 8, 0));
        return wrapper;
    }

    private StackPane buildInsightPane() {
        VBox header = new VBox(6);
        fileTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        fileMeta.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        fileMeta.setWrapText(true);
        openFileButton.setDisable(true);
        openFolderButton.setDisable(true);
        openFileButton.setOnAction(e -> openCurrentFile());
        openFolderButton.setOnAction(e -> revealInExplorer());
        HBox actionBar = new HBox(8, openFileButton, openFolderButton);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        // 统计卡片区域
        HBox statsBox = createInsightStatsPanel();

        header.getChildren().addAll(fileTitle, fileMeta, actionBar, statsBox);

        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(buildOverviewTab());
        tabPane.getTabs().add(buildAttributeTab());
        tabPane.getTabs().add(buildDataInsightTab()); // 新增：数据洞察
        tabPane.getTabs().add(buildComparisonTab());
        tabPane.getTabs().add(buildSuggestionTab());
        tabPane.getTabs().add(buildSampleTab());

        VBox content = new VBox(12, header, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        content.setPadding(new Insets(0, 0, 0, 12));

        loadingIndicator.setVisible(false);

        insightStack.getChildren().addAll(content, loadingIndicator);
        StackPane.setAlignment(loadingIndicator, javafx.geometry.Pos.CENTER);

        return insightStack;
    }

    private Tab buildOverviewTab() {
        metricTable.getColumns().clear();
        metricTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        metricTable.setPlaceholder(new Label("暂无指标"));

        TableColumn<MetricRow, String> nameCol = new TableColumn<>("指标");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setMaxWidth(220);

        TableColumn<MetricRow, String> valueCol = new TableColumn<>("值");
        valueCol.setCellValueFactory(data -> data.getValue().valueProperty());

        TableColumn<MetricRow, String> detailCol = new TableColumn<>("说明");
        detailCol.setCellValueFactory(data -> data.getValue().detailProperty());

        metricTable.getColumns().addAll(nameCol, valueCol, detailCol);

        VBox box = new VBox(metricTable);
        VBox.setVgrow(metricTable, Priority.ALWAYS);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("概览", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildAttributeTab() {
        attributeTable.getColumns().clear();
        attributeTable.setRowFactory(table -> new TableRow<AttributeRow>() {
            @Override
            protected void updateItem(AttributeRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setStyle("");
                } else if (row.getDuplicateCount() > 0) {
                    setStyle("-fx-background-color: rgba(255,105,97,0.35);");
                } else if (row.getCoverageValue() < 60) {
                    setStyle("-fx-background-color: rgba(255,214,102,0.35);");
                } else {
                    setStyle("");
                }
            }
        });
        attributeTable.setPlaceholder(new Label("无可用字段信息"));
        attributeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AttributeRow, String> nameCol = new TableColumn<>("字段");
        nameCol.setPrefWidth(140);
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<AttributeRow, String> coverageCol = new TableColumn<>("覆盖率");
        coverageCol.setPrefWidth(80);
        coverageCol.setCellValueFactory(data -> data.getValue().coverageProperty());

        TableColumn<AttributeRow, String> uniqueCol = new TableColumn<>("唯一值");
        uniqueCol.setCellValueFactory(data -> data.getValue().uniqueProperty());

        TableColumn<AttributeRow, String> duplicateCol = new TableColumn<>("重复样本");
        duplicateCol.setCellValueFactory(data -> data.getValue().duplicateProperty());

        TableColumn<AttributeRow, String> blankCol = new TableColumn<>("空白占比");
        blankCol.setCellValueFactory(data -> data.getValue().blankProperty());

        TableColumn<AttributeRow, String> rangeCol = new TableColumn<>("范围");
        rangeCol.setCellValueFactory(data -> data.getValue().rangeProperty());

        TableColumn<AttributeRow, String> averageCol = new TableColumn<>("均值");
        averageCol.setCellValueFactory(data -> data.getValue().averageProperty());

        attributeTable.getColumns().addAll(nameCol, coverageCol, uniqueCol, duplicateCol, blankCol, rangeCol, averageCol);

        attributeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                showDistributionForAttribute(newSel.getName());
            } else {
                showDistributionForAttribute(null);
            }
        });

        distributionTable.getColumns().clear();
        distributionTable.setPlaceholder(new Label("暂无值统计"));
        distributionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ValueDistributionRow, String> valueCol = new TableColumn<>("值");
        valueCol.setCellValueFactory(data -> data.getValue().valueProperty());
        valueCol.setPrefWidth(200);

        TableColumn<ValueDistributionRow, String> countCol = new TableColumn<>("次数");
        countCol.setCellValueFactory(data -> data.getValue().countProperty());
        countCol.setPrefWidth(80);

        TableColumn<ValueDistributionRow, String> percentCol = new TableColumn<>("占比");
        percentCol.setCellValueFactory(data -> data.getValue().percentageProperty());

        distributionTable.getColumns().addAll(valueCol, countCol, percentCol);

        distributionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        VBox distributionBox = new VBox(6, distributionTitle, distributionTable);
        VBox.setVgrow(distributionTable, Priority.ALWAYS);
        distributionBox.setPadding(new Insets(12, 10, 0, 10));

        VBox box = new VBox(10, attributeTable, distributionBox);
        box.setPadding(new Insets(10));
        VBox.setVgrow(attributeTable, Priority.ALWAYS);

        Tab tab = new Tab("字段画像", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildComparisonTab() {
        relatedFileTable.getColumns().clear();
        relatedFileTable.setPlaceholder(new Label("暂无对比文件"));
        relatedFileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<RelatedFileRow, String> nameCol = new TableColumn<>("文件");
        nameCol.setPrefWidth(160);
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<RelatedFileRow, String> relationCol = new TableColumn<>("关联");
        relationCol.setPrefWidth(120);
        relationCol.setCellValueFactory(data -> data.getValue().relationProperty());

        TableColumn<RelatedFileRow, String> entryCol = new TableColumn<>("条目");
        entryCol.setPrefWidth(70);
        entryCol.setCellValueFactory(data -> data.getValue().entryCountProperty());

        TableColumn<RelatedFileRow, String> deltaCol = new TableColumn<>("差异");
        deltaCol.setPrefWidth(70);
        deltaCol.setCellValueFactory(data -> data.getValue().entryDeltaProperty());

        TableColumn<RelatedFileRow, String> fieldCol = new TableColumn<>("字段概览");
        fieldCol.setPrefWidth(150);
        fieldCol.setCellValueFactory(data -> data.getValue().fieldSummaryProperty());

        TableColumn<RelatedFileRow, String> similarityCol = new TableColumn<>("相似度");
        similarityCol.setPrefWidth(80);
        similarityCol.setCellValueFactory(data -> data.getValue().similarityProperty());

        relatedFileTable.getColumns().addAll(nameCol, relationCol, entryCol, deltaCol, fieldCol, similarityCol);

        relatedFileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> showAlignmentDetails(newSel));

        alignmentTable.getColumns().clear();
        alignmentTable.setPlaceholder(new Label("暂无字段差异"));
        alignmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AlignmentRow, String> attrCol = new TableColumn<>("字段");
        attrCol.setPrefWidth(150);
        attrCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<AlignmentRow, String> categoryCol = new TableColumn<>("类别");
        categoryCol.setPrefWidth(90);
        categoryCol.setCellValueFactory(data -> data.getValue().categoryProperty());

        TableColumn<AlignmentRow, String> currentCol = new TableColumn<>("当前覆盖");
        currentCol.setPrefWidth(110);
        currentCol.setCellValueFactory(data -> data.getValue().currentCoverageProperty());

        TableColumn<AlignmentRow, String> relatedCol = new TableColumn<>("对比覆盖");
        relatedCol.setPrefWidth(110);
        relatedCol.setCellValueFactory(data -> data.getValue().relatedCoverageProperty());

        TableColumn<AlignmentRow, String> deltaCoverageCol = new TableColumn<>("差异");
        deltaCoverageCol.setPrefWidth(80);
        deltaCoverageCol.setCellValueFactory(data -> data.getValue().deltaProperty());

        alignmentTable.getColumns().addAll(attrCol, categoryCol, currentCol, relatedCol, deltaCoverageCol);

        alignmentTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        VBox alignmentBox = new VBox(6, alignmentTitle, alignmentTable);
        VBox.setVgrow(alignmentTable, Priority.ALWAYS);

        VBox container = new VBox(10, relatedFileTable, alignmentBox);
        VBox.setVgrow(relatedFileTable, Priority.SOMETIMES);
        VBox.setVgrow(alignmentBox, Priority.ALWAYS);
        container.setPadding(new Insets(10));

        Tab tab = new Tab("版本对比", container);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildSuggestionTab() {
        suggestionList.setPlaceholder(new Label("暂无建议"));
        suggestionList.setCellFactory(listView -> new ListCell<SuggestionRow>() {
            @Override
            protected void updateItem(SuggestionRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label severity = new Label(item.getSeverityLabel());
                    severity.setPadding(new Insets(2, 6, 2, 6));
                    severity.setTextFill(Color.WHITE);
                    severity.setStyle("-fx-background-color: " + item.getSeverityColor() + "; -fx-background-radius: 4px;");

                    Label title = new Label(item.getTitle());
                    title.setStyle("-fx-font-weight: bold;");

                    Label desc = new Label(item.getDescription());
                    desc.setWrapText(true);

                    VBox info = new VBox(2, title, desc);
                    info.setMaxWidth(Double.MAX_VALUE);

                    HBox row = new HBox(12, severity, info);
                    HBox.setHgrow(info, Priority.ALWAYS);
                    row.setFillHeight(true);
                    row.setPadding(new Insets(6));
                    setGraphic(row);
                }
            }
        });

        VBox box = new VBox(suggestionList);
        VBox.setVgrow(suggestionList, Priority.ALWAYS);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("智能建议", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildSampleTab() {
        sampleList.setPlaceholder(new Label("暂无示例"));
        sampleList.setCellFactory(listView -> new ListCell<String>() {
            private final Text text = new Text();

            {
                text.wrappingWidthProperty().bind(listView.widthProperty().subtract(24));
                text.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    text.setText(item);
                    setGraphic(text);
                }
            }
        });

        VBox box = new VBox(sampleList);
        VBox.setVgrow(sampleList, Priority.ALWAYS);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("示例记录", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildDataInsightTab() {
        TabPane subTabs = new TabPane();
        subTabs.getTabs().add(buildCorrelationSubTab());
        subTabs.getTabs().add(buildDistributionSubTab());
        subTabs.getTabs().add(buildBalanceSubTab());
        subTabs.getTabs().add(buildAttributeTypeSubTab());

        VBox box = new VBox(subTabs);
        VBox.setVgrow(subTabs, Priority.ALWAYS);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("数据洞察", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildCorrelationSubTab() {
        correlationTable.getColumns().clear();

        Label placeholder = new Label("暂无字段关联数据\n\n可能原因：\n• 数值字段少于2个\n• 字段之间相关性太弱（<0.3）\n• 数据点太少（<3个）");
        placeholder.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        placeholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        correlationTable.setPlaceholder(placeholder);
        correlationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<CorrelationRow, String> field1Col = new TableColumn<>("字段1");
        field1Col.setCellValueFactory(data -> data.getValue().field1Property());
        field1Col.setPrefWidth(120);

        TableColumn<CorrelationRow, String> field2Col = new TableColumn<>("字段2");
        field2Col.setCellValueFactory(data -> data.getValue().field2Property());
        field2Col.setPrefWidth(120);

        TableColumn<CorrelationRow, String> corrCol = new TableColumn<>("相关系数");
        corrCol.setCellValueFactory(data -> data.getValue().correlationProperty());
        corrCol.setPrefWidth(90);

        TableColumn<CorrelationRow, String> typeCol = new TableColumn<>("类型");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        typeCol.setPrefWidth(130);

        TableColumn<CorrelationRow, String> insightCol = new TableColumn<>("策划提示");
        insightCol.setCellValueFactory(data -> data.getValue().insightProperty());

        correlationTable.getColumns().addAll(field1Col, field2Col, corrCol, typeCol, insightCol);

        Label title = new Label("字段关联性分析");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label desc = new Label("展示数值字段之间的相关关系，帮助理解数据之间的联动影响");
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        desc.setWrapText(true);

        VBox box = new VBox(8, title, desc, correlationTable);
        VBox.setVgrow(correlationTable, Priority.ALWAYS);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("字段关联", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildDistributionSubTab() {
        distributionProfileTable.getColumns().clear();

        Label placeholder = new Label("暂无分布数据\n\n可能原因：\n• 没有数值字段\n• 数据点太少（<3个）");
        placeholder.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        placeholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        distributionProfileTable.setPlaceholder(placeholder);
        distributionProfileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DistributionRow, String> fieldCol = new TableColumn<>("字段");
        fieldCol.setCellValueFactory(data -> data.getValue().fieldProperty());
        fieldCol.setPrefWidth(120);

        TableColumn<DistributionRow, String> typeCol = new TableColumn<>("分布类型");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        typeCol.setPrefWidth(110);

        TableColumn<DistributionRow, String> evennessCol = new TableColumn<>("均匀度");
        evennessCol.setCellValueFactory(data -> data.getValue().evennessProperty());
        evennessCol.setPrefWidth(80);

        TableColumn<DistributionRow, String> gapsCol = new TableColumn<>("间隙数");
        gapsCol.setCellValueFactory(data -> data.getValue().gapsProperty());
        gapsCol.setPrefWidth(70);

        TableColumn<DistributionRow, String> insightCol = new TableColumn<>("策划提示");
        insightCol.setCellValueFactory(data -> data.getValue().insightProperty());

        distributionProfileTable.getColumns().addAll(fieldCol, typeCol, evennessCol, gapsCol, insightCol);

        Label title = new Label("数值分布特征");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label desc = new Label("分析数值的分布模式，发现数值设计中的不均衡和间隙");
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        desc.setWrapText(true);

        VBox box = new VBox(8, title, desc, distributionProfileTable);
        VBox.setVgrow(distributionProfileTable, Priority.ALWAYS);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("分布特征", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildBalanceSubTab() {
        Label placeholder = new Label("未发现平衡性问题\n\n这是好事！说明数据配置比较合理");
        placeholder.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 12px; -fx-font-weight: bold;");
        placeholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        balanceIssueList.setPlaceholder(placeholder);
        balanceIssueList.setCellFactory(listView -> new ListCell<BalanceIssueRow>() {
            @Override
            protected void updateItem(BalanceIssueRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label severity = new Label(item.getSeverityLabel());
                    severity.setPadding(new Insets(2, 6, 2, 6));
                    severity.setTextFill(Color.WHITE);
                    severity.setStyle("-fx-background-color: " + item.getSeverityColor() + "; -fx-background-radius: 4px;");

                    Label category = new Label(item.getCategory());
                    category.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

                    Label desc = new Label(item.getDescription());
                    desc.setWrapText(true);
                    desc.setStyle("-fx-font-size: 12px;");

                    Label suggestion = new Label("建议：" + item.getSuggestion());
                    suggestion.setWrapText(true);
                    suggestion.setStyle("-fx-font-size: 11px; -fx-text-fill: #0066CC;");

                    VBox info = new VBox(4, category, desc, suggestion);
                    info.setMaxWidth(Double.MAX_VALUE);

                    if (!item.getAffectedRecords().isEmpty()) {
                        Label affected = new Label("涉及记录：" + String.join(", ", item.getAffectedRecords()));
                        affected.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");
                        affected.setWrapText(true);
                        info.getChildren().add(affected);
                    }

                    HBox row = new HBox(12, severity, info);
                    HBox.setHgrow(info, Priority.ALWAYS);
                    row.setFillHeight(true);
                    row.setPadding(new Insets(8));
                    setGraphic(row);
                }
            }
        });

        Label title = new Label("平衡性检测");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label desc = new Label("自动检测数值设计中的异常值和潜在平衡性问题");
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        desc.setWrapText(true);

        VBox box = new VBox(8, title, desc, balanceIssueList);
        VBox.setVgrow(balanceIssueList, Priority.ALWAYS);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("平衡性", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildAttributeTypeSubTab() {
        attributeTypeTable.getColumns().clear();

        Label placeholder = new Label("暂无字段数据");
        placeholder.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        attributeTypeTable.setPlaceholder(placeholder);
        attributeTypeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AttributeTypeRow, String> fieldCol = new TableColumn<>("字段名");
        fieldCol.setCellValueFactory(data -> data.getValue().fieldProperty());
        fieldCol.setPrefWidth(150);

        TableColumn<AttributeTypeRow, String> categoryCol = new TableColumn<>("属性类别");
        categoryCol.setCellValueFactory(data -> data.getValue().categoryProperty());
        categoryCol.setPrefWidth(120);

        TableColumn<AttributeTypeRow, String> descCol = new TableColumn<>("说明");
        descCol.setCellValueFactory(data -> data.getValue().descriptionProperty());

        attributeTypeTable.getColumns().addAll(fieldCol, categoryCol, descCol);

        Label title = new Label("字段类型识别");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label desc = new Label("自动识别字段的游戏属性类型，帮助快速理解数据结构");
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        desc.setWrapText(true);

        VBox box = new VBox(8, title, desc, attributeTypeTable);
        VBox.setVgrow(attributeTypeTable, Priority.ALWAYS);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("字段识别", box);
        tab.setClosable(false);
        return tab;
    }

    /**
     * 创建洞察统计面板
     */
    private HBox createInsightStatsPanel() {
        HBox statsBox = new HBox(10);
        statsBox.setAlignment(Pos.CENTER_LEFT);
        statsBox.setPadding(new Insets(8, 0, 8, 0));

        // 记录数卡片
        recordCountCard = StatCard.create("📝", "记录数", "-", StatCard.COLOR_PRIMARY)
                .small()
                .tooltip("XML文件中的数据记录总数");

        // 字段数卡片
        fieldCountCard = StatCard.create("📋", "字段数", "-", StatCard.COLOR_INFO)
                .small()
                .tooltip("检测到的字段/属性数量");

        // 完整度卡片
        completenessCard = StatCard.create("✓", "完整度", "-", StatCard.COLOR_SUCCESS)
                .small()
                .tooltip("数据完整度评估");

        // 问题数卡片
        issueCountCard = StatCard.create("⚠", "问题", "-", StatCard.COLOR_WARNING)
                .small()
                .tooltip("检测到的数据问题和建议数量");

        statsBox.getChildren().addAll(recordCountCard, fieldCountCard, completenessCard, issueCountCard);
        return statsBox;
    }

    /**
     * 更新洞察统计卡片
     */
    private void updateInsightStats(XmlDesignerInsight insight) {
        if (insight == null) {
            recordCountCard.value("-");
            fieldCountCard.value("-");
            completenessCard.value("-");
            issueCountCard.value("-");
            return;
        }

        Platform.runLater(() -> {
            // 记录数
            int entryCount = insight.getEntryCount();
            recordCountCard.valueAnimated(String.valueOf(entryCount));

            // 字段数
            List<AttributeInsight> attrs = insight.getAttributeInsights();
            if (attrs != null) {
                fieldCountCard.valueAnimated(String.valueOf(attrs.size()));
            }

            // 完整度 - 计算平均覆盖率
            if (attrs != null && !attrs.isEmpty() && entryCount > 0) {
                double avgCoverage = attrs.stream()
                        .mapToDouble(attr -> 100.0 * attr.getPresentCount() / entryCount)
                        .average()
                        .orElse(0);
                completenessCard.valueAnimated(String.format("%.0f%%", avgCoverage));

                // 根据完整度设置颜色
                if (avgCoverage >= 80) {
                    completenessCard.color(StatCard.COLOR_SUCCESS);
                } else if (avgCoverage >= 50) {
                    completenessCard.color(StatCard.COLOR_WARNING);
                } else {
                    completenessCard.color(StatCard.COLOR_DANGER);
                }
            }

            // 问题/建议数
            List<Suggestion> suggestions = insight.getSuggestions();
            if (suggestions != null) {
                int issueCount = suggestions.size();
                issueCountCard.valueAnimated(String.valueOf(issueCount));
                if (issueCount == 0) {
                    issueCountCard.color(StatCard.COLOR_SUCCESS);
                } else if (issueCount <= 3) {
                    issueCountCard.color(StatCard.COLOR_WARNING);
                } else {
                    issueCountCard.color(StatCard.COLOR_DANGER);
                }
            }
        });
    }

    private void populateTreeRoots() {
        List<Path> roots = insightService.resolveConfiguredRoots();
        TreeItem<FsNode> syntheticRoot = new TreeItem<>(new FsNode(null, true, "ROOT"));
        syntheticRoot.setExpanded(true);
        for (Path rootPath : roots) {
            TreeItem<FsNode> item = createTreeItem(new FsNode(rootPath, true, rootPath.toString()));
            syntheticRoot.getChildren().add(item);
        }
        explorerTree.setRoot(syntheticRoot);
        refreshRequired = false;
    }

    private TreeItem<FsNode> createTreeItem(FsNode node) {
        TreeItem<FsNode> item = new TreeItem<>(node);
        if (node.isDirectory()) {
            item.getChildren().add(new TreeItem<>()); // placeholder
            item.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                if (isExpanded) {
                    populateChildren(item);
                }
            });
        }
        return item;
    }

    private void populateChildren(TreeItem<FsNode> parent) {
        FsNode node = parent.getValue();
        if (node == null || !node.isDirectory()) {
            return;
        }
        if (!parent.isExpanded()) {
            return;
        }
        if (!parent.getChildren().isEmpty() && parent.getChildren().get(0).getValue() != null) {
            return; // already populated
        }
        parent.getChildren().clear();
        try (Stream<Path> stream = Files.list(node.getPath())) {
            List<FsNode> children = new ArrayList<>();
            stream.filter(path -> Files.isDirectory(path) || path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".xml"))
                    .sorted(new FileComparator())
                    .forEach(path -> children.add(new FsNode(path, Files.isDirectory(path))));

            for (FsNode child : children) {
                parent.getChildren().add(createTreeItem(child));
            }
        } catch (Exception e) {
            log.warn("Failed to populate directory {}", node.getPath(), e);
        }
    }

    private Callback<TreeView<FsNode>, TreeCell<FsNode>> createTreeCellFactory() {
        return treeView -> new TreeCell<FsNode>() {
            @Override
            protected void updateItem(FsNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getDisplayName());
                }
            }
        };
    }

    private void loadInsightFor(Path path) {
        if (path == null) {
            return;
        }
        cancelCurrentTask();
        showLoading(true);
        currentTask = new Task<XmlDesignerInsight>() {
            @Override
            protected XmlDesignerInsight call() {
                return insightService.analyze(path);
            }
        };

        currentTask.setOnSucceeded(evt -> {
            XmlDesignerInsight insight = currentTask.getValue();
            updateInsight(insight);
            showLoading(false);
        });

        currentTask.setOnFailed(evt -> {
            Throwable ex = currentTask.getException();
            log.error("Failed to load insight for {}", path, ex);
            showLoading(false);
            Alert alert = new Alert(Alert.AlertType.ERROR, "解析 XML 失败: " + (ex != null ? ex.getMessage() : "未知错误"), ButtonType.OK);
            alert.initOwner(this);
            alert.showAndWait();
        });

        currentTask.setOnCancelled(evt -> showLoading(false));

        FeatureTaskExecutor.run(currentTask, "designer-insight-loader");
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
    }

    private void updateInsight(XmlDesignerInsight insight) {
        this.currentInsight = insight;
        updateInsightStats(insight);  // 更新统计卡片
        XmlFileSummary summary = insight.getFileSummary();
        currentPath = summary.getPath();
        fileTitle.setText(summary.getDisplayName());
        fileMeta.setText(buildMetaText(summary, insight.getEntryCount()));
        openFileButton.setDisable(currentPath == null || !Files.exists(currentPath));
        openFolderButton.setDisable(currentPath == null || currentPath.getParent() == null);

        ObservableList<MetricRow> metricRows = FXCollections.observableArrayList();
        for (Metric metric : insight.getMetrics()) {
            metricRows.add(new MetricRow(metric.getName(), metric.getValue(), metric.getDetail()));
        }
        metricTable.setItems(metricRows);

        ObservableList<AttributeRow> attributeRows = FXCollections.observableArrayList();
        for (AttributeInsight ai : insight.getAttributeInsights()) {
            attributeRows.add(AttributeRow.from(ai, insight.getEntryCount()));
        }
        attributeTable.setItems(attributeRows);
        distributionIndex.clear();
        for (AttributeValueDistribution distribution : insight.getDistributions()) {
            distributionIndex.put(distribution.getAttributeName(), distribution);
        }
        if (!attributeRows.isEmpty()) {
            attributeTable.getSelectionModel().select(0);
            showDistributionForAttribute(attributeRows.get(0).getName());
        } else {
            showDistributionForAttribute(null);
        }

        ObservableList<SuggestionRow> suggestions = FXCollections.observableArrayList();
        for (Suggestion suggestion : insight.getSuggestions()) {
            suggestions.add(new SuggestionRow(suggestion));
        }
        suggestionList.setItems(suggestions);

        List<String> sampleTexts = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(1);
        for (Map<String, String> record : insight.getSampleRecords()) {
            sampleTexts.add(formatRecord(index.getAndIncrement(), record));
        }
        sampleList.setItems(FXCollections.observableArrayList(sampleTexts));

        ObservableList<RelatedFileRow> relatedRows = FXCollections.observableArrayList();
        for (RelatedFileComparison comparison : insight.getRelatedComparisons()) {
            relatedRows.add(new RelatedFileRow(comparison));
        }
        relatedFileTable.setItems(relatedRows);
        if (!relatedRows.isEmpty()) {
            relatedFileTable.getSelectionModel().select(0);
            showAlignmentDetails(relatedRows.get(0));
        } else {
            relatedFileTable.getSelectionModel().clearSelection();
            showAlignmentDetails(null);
        }

        // 填充新增的数据洞察标签页
        try {
            ObservableList<CorrelationRow> correlationRows = FXCollections.observableArrayList();
            if (insight.getCorrelations() != null) {
                for (red.jiuzhou.analysis.DataCorrelationAnalyzer.FieldCorrelation correlation : insight.getCorrelations()) {
                    correlationRows.add(new CorrelationRow(correlation));
                }
            }
            correlationTable.setItems(correlationRows);
            System.out.println("UI: 字段关联数据已加载，行数: " + correlationRows.size());

            ObservableList<DistributionRow> distributionRows = FXCollections.observableArrayList();
            if (insight.getDistributionProfiles() != null) {
                for (red.jiuzhou.analysis.DataCorrelationAnalyzer.DistributionProfile profile : insight.getDistributionProfiles()) {
                    distributionRows.add(new DistributionRow(profile));
                }
            }
            distributionProfileTable.setItems(distributionRows);
            System.out.println("UI: 分布特征数据已加载，行数: " + distributionRows.size());

            ObservableList<BalanceIssueRow> balanceRows = FXCollections.observableArrayList();
            if (insight.getBalanceIssues() != null) {
                for (red.jiuzhou.analysis.DataCorrelationAnalyzer.BalanceIssue issue : insight.getBalanceIssues()) {
                    balanceRows.add(new BalanceIssueRow(issue));
                }
            }
            balanceIssueList.setItems(balanceRows);
            System.out.println("UI: 平衡性问题已加载，行数: " + balanceRows.size());

            ObservableList<AttributeTypeRow> typeRows = FXCollections.observableArrayList();
            if (insight.getAttributeTypes() != null) {
                for (Map.Entry<String, red.jiuzhou.analysis.DataCorrelationAnalyzer.AttributeType> entry : insight.getAttributeTypes().entrySet()) {
                    typeRows.add(new AttributeTypeRow(entry.getKey(), entry.getValue()));
                }
            }
            attributeTypeTable.setItems(typeRows);
            System.out.println("UI: 字段类型已加载，行数: " + typeRows.size());

            System.out.println("UI: 字段画像数据已加载，行数: " + attributeRows.size());

        } catch (Exception e) {
            System.err.println("UI: 加载数据洞察时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlignmentDetails(RelatedFileRow row) {
        if (row == null) {
            alignmentTitle.setText("选择对比文件以查看字段差异");
            alignmentTable.getItems().clear();
            return;
        }
        alignmentTitle.setText("字段差异 · " + row.getName());
        ObservableList<AlignmentRow> rows = FXCollections.observableArrayList();
        for (AttributeAlignment alignment : row.getAlignments()) {
            rows.add(new AlignmentRow(alignment));
        }
        alignmentTable.setItems(rows);
    }

    private void showDirectorySummary(FsNode node) {
        cancelCurrentTask();
        fileTitle.setText("目录：" + node.getDisplayName());
        String pathText = node.getPath() != null ? node.getPath().toString() : "";
        fileMeta.setText("路径: " + pathText + '\n' + "展开目录并选择具体的 XML 文件以查看洞察。");
        clearInsightTables();
        showLoading(false);
    }

    private void clearInsightTables() {
        metricTable.getItems().clear();
        attributeTable.getItems().clear();
        suggestionList.getItems().clear();
        sampleList.getItems().clear();
        distributionTable.getItems().clear();
        distributionIndex.clear();
        distributionTitle.setText("选择字段查看值分布");
        relatedFileTable.getItems().clear();
        relatedFileTable.getSelectionModel().clearSelection();
        alignmentTable.getItems().clear();
        alignmentTitle.setText("选择对比文件以查看字段差异");
        correlationTable.getItems().clear();
        distributionProfileTable.getItems().clear();
        balanceIssueList.getItems().clear();
        attributeTypeTable.getItems().clear();
        currentInsight = null;
        currentPath = null;
        openFileButton.setDisable(true);
        openFolderButton.setDisable(true);
    }

    private String buildMetaText(XmlFileSummary summary, int entryCount) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
                .withZone(ZoneId.systemDefault());
        StringBuilder sb = new StringBuilder();
        sb.append("路径: ").append(summary.getPath()).append('\n');
        sb.append("根节点: ").append(summary.getRootElement());
        if (summary.getEntryElement() != null && summary.getEntryElement().length() > 0) {
            sb.append("  /  数据节点: ").append(summary.getEntryElement());
        }
        sb.append('\n');
        if (summary.getInferredTableName() != null && !summary.getInferredTableName().isEmpty()) {
            sb.append("数据库表: ").append(summary.getInferredTableName());
            if (!summary.isTableExists()) {
                sb.append(" (未找到)");
            } else {
                Integer rowCount = summary.getDatabaseRowCount();
                sb.append("  行数: ").append(rowCount != null ? rowCount : "-");
                if (rowCount != null) {
                    int diff = rowCount - entryCount;
                    sb.append("  差值: ").append(diff);
                }
            }
            sb.append('\n');
        }
        sb.append("条目数: ").append(entryCount).append("  大小: ").append(formatFileSize(summary.getFileSize()));
        sb.append('\n');
        sb.append("最后修改: ").append(formatter.format(summary.getLastModified()));
        return sb.toString();
    }

    private String formatRecord(int index, Map<String, String> record) {
        StringBuilder sb = new StringBuilder();
        sb.append("记录 #").append(index).append('\n');
        int shown = 0;
        for (Map.Entry<String, String> entry : record.entrySet()) {
            sb.append(" • ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
            shown++;
            if (shown >= 14) {
                sb.append("   …");
                break;
            }
        }
        return sb.toString();
    }

    private void showLoading(boolean show) {
        Platform.runLater(() -> {
            loadingIndicator.setVisible(show);
            loadingIndicator.setManaged(show);
        });
    }

    private void showDistributionForAttribute(String attributeName) {
        if (attributeName == null || attributeName.isEmpty()) {
            distributionTitle.setText("选择字段查看值分布");
            distributionTable.setItems(FXCollections.observableArrayList());
            return;
        }
        distributionTitle.setText("值分布：" + attributeName);
        AttributeValueDistribution distribution = distributionIndex.get(attributeName);
        ObservableList<ValueDistributionRow> rows = FXCollections.observableArrayList();
        if (distribution != null) {
            for (ValueCount valueCount : distribution.getTopValues()) {
                rows.add(ValueDistributionRow.from(valueCount));
            }
        }
        distributionTable.setItems(rows);
    }

    private void openCurrentFile() {
        if (currentPath == null || !Files.exists(currentPath)) {
            return;
        }
        performDesktopOpen(currentPath, "无法打开文件");
    }

    private void revealInExplorer() {
        if (currentPath == null) {
            return;
        }
        Path parent = currentPath.getParent();
        if (parent == null || !Files.exists(parent)) {
            return;
        }
        performDesktopOpen(parent, "无法打开目录");
    }

    private void performDesktopOpen(Path path, String errorTitle) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException ex) {
            log.warn("Failed to open {}", path, ex);
            Alert alert = new Alert(Alert.AlertType.ERROR, errorTitle + ": " + ex.getMessage(), ButtonType.OK);
            alert.initOwner(this);
            alert.showAndWait();
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = size;
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.CHINA, "%.2f %s", value, units[unitIndex]);
    }

    private static class FsNode {
        private final Path path;
        private final boolean directory;
        private final String customLabel;

        FsNode(Path path, boolean directory) {
            this(path, directory, null);
        }

        FsNode(Path path, boolean directory, String customLabel) {
            this.path = path;
            this.directory = directory;
            this.customLabel = customLabel;
        }

        public Path getPath() {
            return path;
        }

        public boolean isDirectory() {
            return directory;
        }

        public String getDisplayName() {
            if (customLabel != null) {
                return customLabel;
            }
            if (path == null) {
                return "";
            }
            Path fileName = path.getFileName();
            if (fileName == null) {
                return path.toString();
            }
            return fileName.toString();
        }
    }

    private static class FileComparator implements Comparator<Path> {
        @Override
        public int compare(Path o1, Path o2) {
            boolean d1 = Files.isDirectory(o1);
            boolean d2 = Files.isDirectory(o2);
            if (d1 && !d2) {
                return -1;
            }
            if (!d1 && d2) {
                return 1;
            }
            return o1.getFileName().toString().compareToIgnoreCase(o2.getFileName().toString());
        }
    }

    private static class MetricRow {
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty value = new SimpleStringProperty();
        private final SimpleStringProperty detail = new SimpleStringProperty();

        MetricRow(String name, String value, String detail) {
            this.name.set(name);
            this.value.set(value);
            this.detail.set(detail);
        }

        SimpleStringProperty nameProperty() {
            return name;
        }

        SimpleStringProperty valueProperty() {
            return value;
        }

        SimpleStringProperty detailProperty() {
            return detail;
        }
    }

    private static class AttributeRow {
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty coverage = new SimpleStringProperty();
        private final SimpleStringProperty unique = new SimpleStringProperty();
        private final SimpleStringProperty duplicate = new SimpleStringProperty();
        private final SimpleStringProperty blank = new SimpleStringProperty();
        private final SimpleStringProperty range = new SimpleStringProperty();
        private final SimpleStringProperty average = new SimpleStringProperty();
        private final SimpleDoubleProperty coverageValue = new SimpleDoubleProperty();
        private final SimpleDoubleProperty blankValue = new SimpleDoubleProperty();
        private final SimpleIntegerProperty duplicateCount = new SimpleIntegerProperty();

        static AttributeRow from(AttributeInsight insight, int entryCount) {
            AttributeRow row = new AttributeRow();
            row.name.set(insight.getName());
            double coverage = entryCount == 0 ? 0.0 : (insight.getPresentCount() * 100.0 / entryCount);
            double blank = entryCount == 0 ? 0.0 : (insight.getBlankCount() * 100.0 / entryCount);
            row.coverageValue.set(coverage);
            row.blankValue.set(blank);
            row.coverage.set(String.format(Locale.CHINA, "%.1f%%", coverage));
            row.blank.set(String.format(Locale.CHINA, "%.1f%%", blank));

            String uniqueText = insight.getUniqueCount() + (insight.isUniqueCountTruncated() ? "+" : "");
            row.unique.set(uniqueText);

            row.duplicateCount.set(insight.getDuplicateSamples());
            row.duplicate.set(String.valueOf(insight.getDuplicateSamples()));

            if (insight.getMinimumValue() != null && insight.getMaximumValue() != null) {
                if (insight.getMinimumValue().equals(insight.getMaximumValue())) {
                    row.range.set(String.format(Locale.CHINA, "%.2f", insight.getMinimumValue()));
                } else {
                    row.range.set(String.format(Locale.CHINA, "%.2f ~ %.2f", insight.getMinimumValue(), insight.getMaximumValue()));
                }
            } else {
                row.range.set("-");
            }

            if (insight.getAverageValue() != null) {
                row.average.set(String.format(Locale.CHINA, "%.2f", insight.getAverageValue()));
            } else {
                row.average.set("-");
            }

            return row;
        }

        SimpleStringProperty nameProperty() {
            return name;
        }

        SimpleStringProperty coverageProperty() {
            return coverage;
        }

        SimpleStringProperty uniqueProperty() {
            return unique;
        }

        SimpleStringProperty duplicateProperty() {
            return duplicate;
        }

        SimpleStringProperty blankProperty() {
            return blank;
        }

        SimpleStringProperty rangeProperty() {
            return range;
        }

        SimpleStringProperty averageProperty() {
            return average;
        }

        double getCoverageValue() {
            return coverageValue.get();
        }

        int getDuplicateCount() {
            return duplicateCount.get();
        }

        String getName() {
            return name.get();
        }
    }

    private static class ValueDistributionRow {
        private final SimpleStringProperty value = new SimpleStringProperty();
        private final SimpleStringProperty count = new SimpleStringProperty();
        private final SimpleStringProperty percentage = new SimpleStringProperty();

        static ValueDistributionRow from(ValueCount valueCount) {
            ValueDistributionRow row = new ValueDistributionRow();
            row.value.set(valueCount.getValue());
            row.count.set(String.valueOf(valueCount.getCount()));
            row.percentage.set(String.format(Locale.CHINA, "%.1f%%", valueCount.getPercentage()));
            return row;
        }

        SimpleStringProperty valueProperty() {
            return value;
        }

        SimpleStringProperty countProperty() {
            return count;
        }

        SimpleStringProperty percentageProperty() {
            return percentage;
        }
    }

    private static class RelatedFileRow {
        private final RelatedFileComparison comparison;
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty relation = new SimpleStringProperty();
        private final SimpleStringProperty entryCount = new SimpleStringProperty();
        private final SimpleStringProperty entryDelta = new SimpleStringProperty();
        private final SimpleStringProperty fieldSummary = new SimpleStringProperty();
        private final SimpleStringProperty similarity = new SimpleStringProperty();

        RelatedFileRow(RelatedFileComparison comparison) {
            this.comparison = comparison;
            this.name.set(comparison.getRelatedSummary().getDisplayName());
            this.relation.set(comparison.getRelationHint());
            this.entryCount.set(String.valueOf(comparison.getEntryCount()));
            this.entryDelta.set(formatDelta(comparison.getEntryDelta()));
            this.fieldSummary.set(String.format(Locale.CHINA,
                    "共享 %d / 当前+%d / 对方+%d",
                    comparison.getSharedAttributeCount(),
                    comparison.getOnlyInCurrentCount(),
                    comparison.getOnlyInRelatedCount()));
            this.similarity.set(String.format(Locale.CHINA, "%.0f%%", comparison.getSimilarityScore() * 100));
        }

        SimpleStringProperty nameProperty() {
            return name;
        }

        SimpleStringProperty relationProperty() {
            return relation;
        }

        SimpleStringProperty entryCountProperty() {
            return entryCount;
        }

        SimpleStringProperty entryDeltaProperty() {
            return entryDelta;
        }

        SimpleStringProperty fieldSummaryProperty() {
            return fieldSummary;
        }

        SimpleStringProperty similarityProperty() {
            return similarity;
        }

        String getName() {
            return name.get();
        }

        List<AttributeAlignment> getAlignments() {
            return comparison.getAlignments();
        }

        private String formatDelta(int delta) {
            if (delta == 0) {
                return "0";
            }
            return (delta > 0 ? "+" : "") + delta;
        }
    }

    private static class AlignmentRow {
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty category = new SimpleStringProperty();
        private final SimpleStringProperty currentCoverage = new SimpleStringProperty();
        private final SimpleStringProperty relatedCoverage = new SimpleStringProperty();
        private final SimpleStringProperty delta = new SimpleStringProperty();

        AlignmentRow(AttributeAlignment alignment) {
            this.name.set(alignment.getAttributeName());
            this.category.set(describeCategory(alignment.getCategory()));
            this.currentCoverage.set(formatPercentage(alignment.getCurrentCoverage()));
            this.relatedCoverage.set(formatPercentage(alignment.getRelatedCoverage()));
            this.delta.set(formatPercentage(alignment.getCoverageDelta()));
        }

        SimpleStringProperty nameProperty() {
            return name;
        }

        SimpleStringProperty categoryProperty() {
            return category;
        }

        SimpleStringProperty currentCoverageProperty() {
            return currentCoverage;
        }

        SimpleStringProperty relatedCoverageProperty() {
            return relatedCoverage;
        }

        SimpleStringProperty deltaProperty() {
            return delta;
        }

        private String describeCategory(AlignmentCategory category) {
            switch (category) {
                case ONLY_CURRENT:
                    return "仅当前";
                case ONLY_RELATED:
                    return "仅对方";
                default:
                    return "共享";
            }
        }

        private String formatPercentage(double value) {
            return String.format(Locale.CHINA, "%.1f%%", value);
        }
    }

    private static class SuggestionRow {
        private final String title;
        private final String description;
        private final Severity severity;

        SuggestionRow(Suggestion suggestion) {
            this.title = suggestion.getTitle();
            this.description = suggestion.getDescription();
            this.severity = suggestion.getSeverity();
        }

        String getTitle() {
            return title;
        }

        String getDescription() {
            return description;
        }

        String getSeverityLabel() {
            switch (severity) {
                case CRITICAL:
                    return "严重";
                case WARNING:
                    return "警示";
                default:
                    return "提示";
            }
        }

        String getSeverityColor() {
            switch (severity) {
                case CRITICAL:
                    return "#F25F5C";
                case WARNING:
                    return "#FFA630";
                default:
                    return "#3366CC";
            }
        }

        Severity getSeverity() {
            return severity;
        }
    }

    private static class CorrelationRow {
        private final SimpleStringProperty field1 = new SimpleStringProperty();
        private final SimpleStringProperty field2 = new SimpleStringProperty();
        private final SimpleStringProperty correlation = new SimpleStringProperty();
        private final SimpleStringProperty type = new SimpleStringProperty();
        private final SimpleStringProperty insight = new SimpleStringProperty();

        CorrelationRow(red.jiuzhou.analysis.DataCorrelationAnalyzer.FieldCorrelation corr) {
            this.field1.set(corr.getField1());
            this.field2.set(corr.getField2());
            this.correlation.set(String.format(Locale.CHINA, "%.3f", corr.getCorrelation()));
            this.type.set(corr.getType().getDisplayName());
            this.insight.set(corr.getInsight());
        }

        SimpleStringProperty field1Property() { return field1; }
        SimpleStringProperty field2Property() { return field2; }
        SimpleStringProperty correlationProperty() { return correlation; }
        SimpleStringProperty typeProperty() { return type; }
        SimpleStringProperty insightProperty() { return insight; }
    }

    private static class DistributionRow {
        private final SimpleStringProperty field = new SimpleStringProperty();
        private final SimpleStringProperty type = new SimpleStringProperty();
        private final SimpleStringProperty evenness = new SimpleStringProperty();
        private final SimpleStringProperty gaps = new SimpleStringProperty();
        private final SimpleStringProperty insight = new SimpleStringProperty();

        DistributionRow(red.jiuzhou.analysis.DataCorrelationAnalyzer.DistributionProfile profile) {
            this.field.set(profile.getFieldName());
            this.type.set(profile.getType().getDisplayName());
            this.evenness.set(String.format(Locale.CHINA, "%.1f%%", profile.getEvenness() * 100));
            this.gaps.set(String.valueOf(profile.getGaps().size()));
            this.insight.set(profile.getInsight());
        }

        SimpleStringProperty fieldProperty() { return field; }
        SimpleStringProperty typeProperty() { return type; }
        SimpleStringProperty evennessProperty() { return evenness; }
        SimpleStringProperty gapsProperty() { return gaps; }
        SimpleStringProperty insightProperty() { return insight; }
    }

    private static class BalanceIssueRow {
        private final String category;
        private final red.jiuzhou.analysis.DataCorrelationAnalyzer.Severity severity;
        private final String description;
        private final String suggestion;
        private final List<String> affectedRecords;

        BalanceIssueRow(red.jiuzhou.analysis.DataCorrelationAnalyzer.BalanceIssue issue) {
            this.category = issue.getCategory();
            this.severity = issue.getSeverity();
            this.description = issue.getDescription();
            this.suggestion = issue.getSuggestion();
            this.affectedRecords = issue.getAffectedRecords();
        }

        String getCategory() { return category; }
        String getDescription() { return description; }
        String getSuggestion() { return suggestion; }
        List<String> getAffectedRecords() { return affectedRecords; }

        String getSeverityLabel() {
            switch (severity) {
                case CRITICAL: return "严重";
                case WARNING: return "警告";
                default: return "提示";
            }
        }

        String getSeverityColor() {
            switch (severity) {
                case CRITICAL: return "#F25F5C";
                case WARNING: return "#FFA630";
                default: return "#3366CC";
            }
        }
    }

    private static class AttributeTypeRow {
        private final SimpleStringProperty field = new SimpleStringProperty();
        private final SimpleStringProperty category = new SimpleStringProperty();
        private final SimpleStringProperty description = new SimpleStringProperty();

        AttributeTypeRow(String fieldName, red.jiuzhou.analysis.DataCorrelationAnalyzer.AttributeType type) {
            this.field.set(fieldName);
            this.category.set(type.getCategory().getDisplayName());
            this.description.set(type.getCategory().getDescription());
        }

        SimpleStringProperty fieldProperty() { return field; }
        SimpleStringProperty categoryProperty() { return category; }
        SimpleStringProperty descriptionProperty() { return description; }
    }
}
