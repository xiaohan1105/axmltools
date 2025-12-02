package red.jiuzhou.ui.mapping;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.tabmapping.MappingLoader;
import red.jiuzhou.tabmapping.TableMapping;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.*;

/**
 * 增强版可视化表映射管理器
 * 支持 XML文件 ↔ 客户端表 ↔ 服务端表 的三方映射关系管理
 *
 * 核心特性:
 * - 三方关系可视化展示
 * - XML文件浏览和关联
 * - 智能映射建议
 * - 映射关系图表
 * - 批量导入导出
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 2.0
 */
public class EnhancedTableMappingManager {

    private static final Logger log = LoggerFactory.getLogger(EnhancedTableMappingManager.class);

    private final Stage ownerStage;
    private Stage managerStage;

    // 数据列表
    private ObservableList<TableMapping> mappingList;
    private FilteredList<TableMapping> filteredMappingList;

    // UI组件
    private TableView<TableMapping> mappingTable;
    private TextField searchField;
    private Label statsLabel;
    private VBox detailPanel;
    private ComboBox<String> viewModeCombo;

    // 配置文件路径
    private static final String CONFIG_FILE = "tabMapping.json";

    /**
     * 构造函数
     */
    public EnhancedTableMappingManager(Stage ownerStage) {
        this.ownerStage = ownerStage;
        loadMappingData();
    }

    /**
     * 显示映射管理器窗口
     */
    public void show() {
        managerStage = new Stage();
        managerStage.initOwner(ownerStage);
        managerStage.initModality(Modality.NONE);
        managerStage.setTitle("🌐 增强版映射管理器 - XML ↔ 客户端 ↔ 服务端");

        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // 顶部工具栏
        mainLayout.setTop(createToolBar());

        // 中心区域
        SplitPane centerPane = createCenterPane();
        mainLayout.setCenter(centerPane);

        // 底部按钮栏
        mainLayout.setBottom(createBottomBar());

        Scene scene = new Scene(mainLayout, 1600, 850);
        managerStage.setScene(scene);
        managerStage.show();

        log.info("增强版映射管理器已打开");
    }

    /**
     * 创建顶部工具栏
     */
    private VBox createToolBar() {
        VBox toolBarContainer = new VBox(10);
        toolBarContainer.setPadding(new Insets(0, 0, 10, 0));

        // 第一行：标题和统计
        HBox titleRow = new HBox(15);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("🌐 增强版映射管理器");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        statsLabel = new Label();
        updateStats();
        statsLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

        titleRow.getChildren().addAll(titleLabel, statsLabel);

        // 第二行：搜索和工具
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("🔍 搜索:");
        searchField = new TextField();
        searchField.setPromptText("搜索 XML文件名、表名或字段名...");
        searchField.setPrefWidth(350);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterMappings(newVal));

        Button clearSearchBtn = new Button("✖");
        clearSearchBtn.setOnAction(e -> searchField.clear());

        // 视图模式选择
        Label viewLabel = new Label("视图:");
        viewModeCombo = new ComboBox<>();
        viewModeCombo.getItems().addAll(
            "📊 表格视图",
            "🌳 树形视图",
            "📈 关系图谱"
        );
        viewModeCombo.setValue("📊 表格视图");
        viewModeCombo.setOnAction(e -> switchViewMode(viewModeCombo.getValue()));

        // 筛选器
        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.setPromptText("筛选");
        filterCombo.getItems().addAll(
            "全部映射",
            "有XML信息",
            "无XML信息",
            "直接映射",
            "复杂映射",
            "嵌套映射",
            "有冗余字段"
        );
        filterCombo.setValue("全部映射");
        filterCombo.setOnAction(e -> applyFilter(filterCombo.getValue()));

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setOnAction(e -> refreshData());

        searchRow.getChildren().addAll(
            searchLabel, searchField, clearSearchBtn,
            new Separator(Orientation.VERTICAL),
            viewLabel, viewModeCombo,
            new Separator(Orientation.VERTICAL),
            filterCombo, refreshBtn
        );

        toolBarContainer.getChildren().addAll(titleRow, searchRow);
        return toolBarContainer;
    }

    /**
     * 创建中心面板
     */
    private SplitPane createCenterPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);

        VBox leftPanel = createMappingListPanel();
        detailPanel = createDetailPanel();

        splitPane.getItems().addAll(leftPanel, detailPanel);
        splitPane.setDividerPositions(0.55);

        return splitPane;
    }

    /**
     * 创建映射列表面板
     */
    private VBox createMappingListPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label headerLabel = new Label("📋 映射配置列表");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        mappingTable = new TableView<>();
        mappingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 序号列
        TableColumn<TableMapping, String> indexCol = new TableColumn<>("序号");
        indexCol.setPrefWidth(50);
        indexCol.setCellValueFactory(param -> {
            int index = mappingTable.getItems().indexOf(param.getValue()) + 1;
            return new SimpleStringProperty(String.valueOf(index));
        });

        // XML文件列
        TableColumn<TableMapping, String> xmlCol = new TableColumn<>("📄 XML文件");
        xmlCol.setPrefWidth(200);
        xmlCol.setCellValueFactory(param -> {
            String fileName = param.getValue().getXmlFileName();
            return new SimpleStringProperty(fileName != null ? fileName : "-");
        });

        // 客户端表列
        TableColumn<TableMapping, String> cltTabCol = new TableColumn<>("📦 客户端表");
        cltTabCol.setPrefWidth(220);
        cltTabCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getCltTab()));
        cltTabCol.setStyle("-fx-alignment: CENTER-LEFT;");

        // 映射指示符
        TableColumn<TableMapping, String> arrowCol = new TableColumn<>("↔");
        arrowCol.setPrefWidth(40);
        arrowCol.setCellValueFactory(param -> new SimpleStringProperty("⟷"));
        arrowCol.setStyle("-fx-alignment: CENTER;");

        // 服务端表列
        TableColumn<TableMapping, String> svrTabCol = new TableColumn<>("🖥️ 服务端表");
        svrTabCol.setPrefWidth(220);
        svrTabCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getSvrTab()));
        svrTabCol.setStyle("-fx-alignment: CENTER-LEFT;");

        // 映射类型列
        TableColumn<TableMapping, String> typeCol = new TableColumn<>("类型");
        typeCol.setPrefWidth(90);
        typeCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getMappingTypeDisplay()));
        typeCol.setStyle("-fx-alignment: CENTER;");

        // 字段数列
        TableColumn<TableMapping, String> fieldCountCol = new TableColumn<>("字段");
        fieldCountCol.setPrefWidth(60);
        fieldCountCol.setCellValueFactory(param -> {
            String sameFields = param.getValue().getSameFileds();
            int count = sameFields != null && !sameFields.isEmpty()
                ? sameFields.split(",").length : 0;
            return new SimpleStringProperty(String.valueOf(count));
        });
        fieldCountCol.setStyle("-fx-alignment: CENTER;");

        // 状态列
        TableColumn<TableMapping, String> statusCol = new TableColumn<>("状态");
        statusCol.setPrefWidth(80);
        statusCol.setCellValueFactory(param -> {
            String status = param.getValue().getStatusDisplay();
            return new SimpleStringProperty(status);
        });

        mappingTable.getColumns().addAll(
            indexCol, xmlCol, cltTabCol, arrowCol, svrTabCol,
            typeCol, fieldCountCol, statusCol
        );

        filteredMappingList = new FilteredList<>(mappingList, p -> true);
        mappingTable.setItems(filteredMappingList);

        mappingTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    showMappingDetail(newVal);
                }
            }
        );

        mappingTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TableMapping selected = mappingTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    editMapping(selected);
                }
            }
        });

        VBox.setVgrow(mappingTable, Priority.ALWAYS);
        panel.getChildren().addAll(headerLabel, mappingTable);

        return panel;
    }

    /**
     * 创建详情面板
     */
    private VBox createDetailPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label headerLabel = new Label("📝 映射关系详情");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label hintLabel = new Label("👈 从左侧选择一个映射配置查看详细信息");
        hintLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        hintLabel.setWrapText(true);

        VBox.setVgrow(hintLabel, Priority.ALWAYS);
        panel.getChildren().addAll(headerLabel, hintLabel);

        return panel;
    }

    /**
     * 显示映射详情
     */
    private void showMappingDetail(TableMapping mapping) {
        detailPanel.getChildren().clear();

        Label headerLabel = new Label("📝 映射关系详情");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 三方关系卡片
        VBox relationCard = createThreeWayRelationCard(mapping);

        // 字段映射详情
        HBox fieldCompareBox = createFieldCompareView(mapping);

        VBox.setVgrow(fieldCompareBox, Priority.ALWAYS);
        detailPanel.getChildren().addAll(headerLabel, relationCard, fieldCompareBox);
    }

    /**
     * 创建三方关系卡片
     */
    private VBox createThreeWayRelationCard(TableMapping mapping) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef);" +
                     "-fx-background-radius: 8; -fx-border-color: #dee2e6; " +
                     "-fx-border-radius: 8; -fx-border-width: 1;");

        // 标题
        Label titleLabel = new Label("🌐 三方映射关系");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // XML文件信息
        HBox xmlRow = new HBox(10);
        xmlRow.setAlignment(Pos.CENTER_LEFT);
        Label xmlIcon = new Label("📄");
        xmlIcon.setStyle("-fx-font-size: 16px;");

        VBox xmlInfo = new VBox(3);
        Label xmlFileLabel = new Label("XML源文件: " +
            (mapping.hasXmlInfo() ? mapping.getXmlFileName() : "未关联"));
        xmlFileLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        if (mapping.hasXmlInfo()) {
            Label xmlPathLabel = new Label("路径: " + mapping.getXmlFilePath());
            xmlPathLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
            xmlInfo.getChildren().add(xmlPathLabel);
        }

        xmlInfo.getChildren().add(0, xmlFileLabel);
        xmlRow.getChildren().addAll(xmlIcon, xmlInfo);

        // 映射流向图
        HBox flowBox = new HBox(15);
        flowBox.setAlignment(Pos.CENTER);
        flowBox.setPadding(new Insets(10, 0, 10, 0));

        VBox xmlBox = createFlowBox("📄 XML", mapping.getXmlFileName(), "#4CAF50");
        Label arrow1 = new Label("→");
        arrow1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        VBox cltBox = createFlowBox("📦 客户端", mapping.getCltTab(), "#2196F3");
        Label arrow2 = new Label("↔");
        arrow2.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        VBox svrBox = createFlowBox("🖥️ 服务端", mapping.getSvrTab(), "#FF9800");

        flowBox.getChildren().addAll(xmlBox, arrow1, cltBox, arrow2, svrBox);

        // 映射元信息
        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(15);
        metaGrid.setVgap(5);
        metaGrid.setPadding(new Insets(10, 0, 0, 0));

        addMetaInfo(metaGrid, 0, "映射类型:", mapping.getMappingTypeDisplay());
        addMetaInfo(metaGrid, 1, "状态:", mapping.getStatusDisplay());
        addMetaInfo(metaGrid, 2, "描述:", mapping.getDescription() != null ?
            mapping.getDescription() : "-");

        card.getChildren().addAll(titleLabel, xmlRow, new Separator(), flowBox,
                                  new Separator(), metaGrid);

        return card;
    }

    /**
     * 创建流程框
     */
    private VBox createFlowBox(String title, String content, String color) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: " + color + "; -fx-border-width: 2; " +
                    "-fx-border-radius: 5; -fx-background-color: white; " +
                    "-fx-background-radius: 5; -fx-min-width: 150;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        Label contentLabel = new Label(content != null ? content : "-");
        contentLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(140);

        box.getChildren().addAll(titleLabel, contentLabel);
        return box;
    }

    /**
     * 添加元信息
     */
    private void addMetaInfo(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 11px;");

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    /**
     * 创建字段对比视图
     */
    private HBox createFieldCompareView(TableMapping mapping) {
        HBox compareBox = new HBox(15);
        compareBox.setPadding(new Insets(10, 0, 0, 0));

        VBox leftColumn = createFieldColumn(
            "客户端字段",
            mapping.getSameFileds(),
            mapping.getCltRedundantFields(),
            "#2196F3"
        );

        VBox centerColumn = createMappingIndicator(mapping.getSameFileds());

        VBox rightColumn = createFieldColumn(
            "服务器字段",
            mapping.getSameFileds(),
            mapping.getSvrRedundantFields(),
            "#FF9800"
        );

        HBox.setHgrow(leftColumn, Priority.ALWAYS);
        HBox.setHgrow(centerColumn, Priority.NEVER);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        compareBox.getChildren().addAll(leftColumn, centerColumn, rightColumn);
        return compareBox;
    }

    /**
     * 创建字段列
     */
    private VBox createFieldColumn(String title, String commonFields,
                                    String redundantFields, String color) {
        VBox column = new VBox(5);
        column.setPadding(new Insets(10));
        column.setStyle("-fx-border-color: " + color + "; -fx-border-width: 2; " +
                       "-fx-border-radius: 5; -fx-background-radius: 5;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        ListView<String> fieldList = new ListView<>();
        fieldList.setPrefHeight(300);

        List<String> allFields = new ArrayList<>();

        if (commonFields != null && !commonFields.isEmpty()) {
            Arrays.stream(commonFields.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(field -> allFields.add("✅ " + field + " (共同)"));
        }

        if (redundantFields != null && !redundantFields.isEmpty()) {
            Arrays.stream(redundantFields.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(field -> allFields.add("⚠️ " + field + " (冗余)"));
        }

        if (allFields.isEmpty()) {
            allFields.add("(无字段)");
        }

        fieldList.setItems(FXCollections.observableArrayList(allFields));

        VBox.setVgrow(fieldList, Priority.ALWAYS);
        column.getChildren().addAll(titleLabel, fieldList);

        return column;
    }

    /**
     * 创建映射指示器
     */
    private VBox createMappingIndicator(String commonFields) {
        VBox indicator = new VBox(10);
        indicator.setAlignment(Pos.CENTER);
        indicator.setPadding(new Insets(20, 10, 20, 10));
        indicator.setStyle("-fx-background-color: #fafafa;");

        int fieldCount = commonFields != null && !commonFields.isEmpty()
            ? commonFields.split(",").length : 0;

        for (int i = 0; i < Math.min(fieldCount, 8); i++) {
            Label arrow = new Label("⟷");
            arrow.setStyle("-fx-font-size: 18px; -fx-text-fill: #666;");
            indicator.getChildren().add(arrow);
        }

        if (fieldCount > 8) {
            Label more = new Label("...\n+" + (fieldCount - 8));
            more.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
            indicator.getChildren().add(more);
        }

        return indicator;
    }

    /**
     * 创建底部按钮栏
     */
    private HBox createBottomBar() {
        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button addBtn = new Button("➕ 新建映射");
        addBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addBtn.setOnAction(e -> addNewMapping());

        Button editBtn = new Button("✏️ 编辑");
        editBtn.setOnAction(e -> {
            TableMapping selected = mappingTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editMapping(selected);
            } else {
                showAlert("请先选择要编辑的映射");
            }
        });

        Button deleteBtn = new Button("🗑️ 删除");
        deleteBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        deleteBtn.setOnAction(e -> deleteMapping());

        Button browseXmlBtn = new Button("📂 关联XML");
        browseXmlBtn.setTooltip(new Tooltip("为选中的映射关联XML文件"));
        browseXmlBtn.setOnAction(e -> browseXmlFile());

        Button validateBtn = new Button("✔️ 验证");
        validateBtn.setOnAction(e -> validateMappings());

        Button saveBtn = new Button("💾 保存配置");
        saveBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                        "-fx-font-weight: bold;");
        saveBtn.setOnAction(e -> saveConfig());

        buttonBar.getChildren().addAll(
            addBtn, editBtn, deleteBtn,
            new Separator(Orientation.VERTICAL),
            browseXmlBtn, validateBtn,
            new Separator(Orientation.VERTICAL),
            saveBtn
        );

        return buttonBar;
    }

    // ==================== 数据操作方法 ====================

    private void loadMappingData() {
        try {
            List<TableMapping> mappings = MappingLoader.loadMappings();
            mappingList = FXCollections.observableArrayList(mappings);
            log.info("成功加载 {} 条映射配置", mappings.size());
        } catch (Exception e) {
            log.error("加载映射配置失败", e);
            mappingList = FXCollections.observableArrayList();
            showAlert("加载映射配置失败: " + e.getMessage());
        }
    }

    private void refreshData() {
        loadMappingData();
        filteredMappingList = new FilteredList<>(mappingList, p -> true);
        mappingTable.setItems(filteredMappingList);
        updateStats();
        showInfo("数据已刷新", "成功重新加载 " + mappingList.size() + " 条映射配置");
    }

    private void filterMappings(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredMappingList.setPredicate(p -> true);
        } else {
            String lower = searchText.toLowerCase().trim();
            filteredMappingList.setPredicate(mapping -> {
                if (mapping.getXmlFileName() != null &&
                    mapping.getXmlFileName().toLowerCase().contains(lower)) {
                    return true;
                }
                if (mapping.getCltTab() != null &&
                    mapping.getCltTab().toLowerCase().contains(lower)) {
                    return true;
                }
                if (mapping.getSvrTab() != null &&
                    mapping.getSvrTab().toLowerCase().contains(lower)) {
                    return true;
                }
                if (mapping.getSameFileds() != null &&
                    mapping.getSameFileds().toLowerCase().contains(lower)) {
                    return true;
                }
                return false;
            });
        }
        updateStats();
    }

    private void applyFilter(String filterType) {
        if (filterType == null) return;

        switch (filterType) {
            case "全部映射":
                filteredMappingList.setPredicate(p -> true);
                break;
            case "有XML信息":
                filteredMappingList.setPredicate(TableMapping::hasXmlInfo);
                break;
            case "无XML信息":
                filteredMappingList.setPredicate(m -> !m.hasXmlInfo());
                break;
            case "直接映射":
                filteredMappingList.setPredicate(m ->
                    "direct".equals(m.getMappingType()));
                break;
            case "复杂映射":
                filteredMappingList.setPredicate(m ->
                    "complex".equals(m.getMappingType()));
                break;
            case "嵌套映射":
                filteredMappingList.setPredicate(m ->
                    "nested".equals(m.getMappingType()));
                break;
            case "有冗余字段":
                filteredMappingList.setPredicate(this::hasRedundantFields);
                break;
        }
        updateStats();
    }

    private boolean hasRedundantFields(TableMapping mapping) {
        return (mapping.getCltRedundantFields() != null &&
                !mapping.getCltRedundantFields().trim().isEmpty())
            || (mapping.getSvrRedundantFields() != null &&
                !mapping.getSvrRedundantFields().trim().isEmpty());
    }

    private void updateStats() {
        if (statsLabel == null) return;

        int total = mappingList.size();
        int displayed = filteredMappingList != null ? filteredMappingList.size() : total;
        int withXml = (int) mappingList.stream().filter(TableMapping::hasXmlInfo).count();
        int withRedundant = (int) mappingList.stream().filter(this::hasRedundantFields).count();

        statsLabel.setText(String.format(
            "📊 总计: %d | 显示: %d | 关联XML: %d | 冗余字段: %d",
            total, displayed, withXml, withRedundant
        ));
    }

    private void switchViewMode(String mode) {
        showInfo("功能开发中", "视图切换功能即将上线\n当前选择: " + mode);
    }

    private void addNewMapping() {
        EnhancedMappingEditDialog dialog =
            new EnhancedMappingEditDialog(managerStage, null);
        dialog.showAndWait().ifPresent(newMapping -> {
            mappingList.add(newMapping);
            updateStats();
            showInfo("新建成功", "已添加新的表映射配置");
        });
    }

    private void editMapping(TableMapping mapping) {
        EnhancedMappingEditDialog dialog =
            new EnhancedMappingEditDialog(managerStage, mapping);
        dialog.showAndWait().ifPresent(editedMapping -> {
            int index = mappingList.indexOf(mapping);
            if (index >= 0) {
                mappingList.set(index, editedMapping);
                mappingTable.refresh();
                showMappingDetail(editedMapping);
                showInfo("编辑成功", "已更新表映射配置");
            }
        });
    }

    private void deleteMapping() {
        TableMapping selected = mappingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先选择要删除的映射");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("删除映射配置");
        confirm.setContentText("确定要删除映射:\n" + selected.getMappingDescription() + " ?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                mappingList.remove(selected);
                updateStats();
                showInfo("删除成功", "已删除表映射配置");
            }
        });
    }

    private void browseXmlFile() {
        TableMapping selected = mappingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先选择一个映射配置");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择XML文件");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("XML文件", "*.xml")
        );

        File selectedFile = fileChooser.showOpenDialog(managerStage);
        if (selectedFile != null) {
            selected.setXmlFilePath(selectedFile.getAbsolutePath());
            selected.setXmlFileName(selectedFile.getName());
            selected.setUpdatedTime(System.currentTimeMillis());

            mappingTable.refresh();
            showMappingDetail(selected);
            showInfo("关联成功", "已关联XML文件:\n" + selectedFile.getName());
        }
    }

    private void validateMappings() {
        StringBuilder report = new StringBuilder();
        report.append("📋 映射配置验证报告\n\n");

        int totalMappings = mappingList.size();
        int withXml = 0;
        int emptyFields = 0;
        int withRedundant = 0;

        for (TableMapping mapping : mappingList) {
            if (mapping.hasXmlInfo()) withXml++;
            if (mapping.getSameFileds() == null || mapping.getSameFileds().trim().isEmpty()) {
                emptyFields++;
            }
            if (hasRedundantFields(mapping)) {
                withRedundant++;
            }
        }

        report.append(String.format("✅ 总映射数: %d\n", totalMappings));
        report.append(String.format("📄 关联XML: %d\n", withXml));
        report.append(String.format("⚠️ 空字段映射: %d\n", emptyFields));
        report.append(String.format("📊 含冗余字段: %d\n", withRedundant));
        report.append(String.format("✔️ 正常映射: %d\n", totalMappings - emptyFields));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("验证结果");
        alert.setHeaderText("映射配置验证完成");
        alert.setContentText(report.toString());
        alert.showAndWait();
    }

    private void saveConfig() {
        try {
            String configPath = YamlUtils.getProperty("file.homePath") +
                               File.separator + CONFIG_FILE;
            String json = JSON.toJSONString(mappingList,
                                           SerializerFeature.PrettyFormat);
            FileUtil.writeUtf8String(json, configPath);

            log.info("映射配置已保存到: {}", configPath);
            showInfo("保存成功", "映射配置已保存到文件:\n" + configPath);
        } catch (Exception e) {
            log.error("保存配置失败", e);
            showAlert("保存配置失败: " + e.getMessage());
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
