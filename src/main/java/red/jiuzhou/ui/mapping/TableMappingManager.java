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
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.tabmapping.MappingLoader;
import red.jiuzhou.tabmapping.TableMapping;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 可视化表映射管理器
 * 提供直观的表映射和字段映射管理界面
 *
 * 功能特点:
 * - 表格视图展示所有映射关系
 * - 双栏对比显示字段映射
 * - 实时搜索和筛选
 * - 映射统计和验证
 * - 可视化编辑映射关系
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class TableMappingManager {

    private static final Logger log = LoggerFactory.getLogger(TableMappingManager.class);

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

    // 配置文件路径
    private static final String CONFIG_FILE = "tabMapping.json";

    /**
     * 构造函数
     *
     * @param ownerStage 父窗口
     */
    public TableMappingManager(Stage ownerStage) {
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
        managerStage.setTitle("📊 表映射管理器 - 可视化配置");

        // 创建主布局
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // 顶部工具栏
        mainLayout.setTop(createToolBar());

        // 中心区域 - 分割面板
        SplitPane centerPane = createCenterPane();
        mainLayout.setCenter(centerPane);

        // 底部按钮栏
        mainLayout.setBottom(createBottomBar());

        // 创建场景
        Scene scene = new Scene(mainLayout, 1400, 800);
        managerStage.setScene(scene);
        managerStage.show();

        log.info("表映射管理器已打开");
    }

    /**
     * 创建顶部工具栏
     */
    private VBox createToolBar() {
        VBox toolBarContainer = new VBox(10);
        toolBarContainer.setPadding(new Insets(0, 0, 10, 0));

        // 第一行：标题和统计信息
        HBox titleRow = new HBox(15);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("📊 表映射配置管理");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        statsLabel = new Label();
        updateStats();
        statsLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

        titleRow.getChildren().addAll(titleLabel, statsLabel);

        // 第二行：搜索和筛选工具
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("🔍 搜索:");
        searchField = new TextField();
        searchField.setPromptText("输入客户端表名、服务器表名或字段名进行搜索...");
        searchField.setPrefWidth(400);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterMappings(newVal));

        Button clearSearchBtn = new Button("✖ 清除");
        clearSearchBtn.setOnAction(e -> searchField.clear());

        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.setPromptText("筛选条件");
        filterCombo.getItems().addAll(
            "全部映射",
            "有冗余字段",
            "无冗余字段",
            "字段数>10",
            "客户端表",
            "服务器表"
        );
        filterCombo.setValue("全部映射");
        filterCombo.setOnAction(e -> applyFilter(filterCombo.getValue()));

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setTooltip(new Tooltip("从配置文件重新加载数据"));
        refreshBtn.setOnAction(e -> refreshData());

        searchRow.getChildren().addAll(
            searchLabel, searchField, clearSearchBtn,
            new Separator(Orientation.VERTICAL),
            filterCombo, refreshBtn
        );

        toolBarContainer.getChildren().addAll(titleRow, searchRow);
        return toolBarContainer;
    }

    /**
     * 创建中心分割面板
     */
    private SplitPane createCenterPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);

        // 左侧：映射列表
        VBox leftPanel = createMappingListPanel();

        // 右侧：详情面板
        detailPanel = createDetailPanel();

        splitPane.getItems().addAll(leftPanel, detailPanel);
        splitPane.setDividerPositions(0.5);

        return splitPane;
    }

    /**
     * 创建映射列表面板
     */
    private VBox createMappingListPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label headerLabel = new Label("📋 表映射列表");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 创建表格
        mappingTable = new TableView<>();
        mappingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 序号列
        TableColumn<TableMapping, String> indexCol = new TableColumn<>("序号");
        indexCol.setPrefWidth(60);
        indexCol.setCellValueFactory(param -> {
            int index = mappingTable.getItems().indexOf(param.getValue()) + 1;
            return new SimpleStringProperty(String.valueOf(index));
        });

        // 客户端表名列
        TableColumn<TableMapping, String> cltTabCol = new TableColumn<>("客户端表名");
        cltTabCol.setPrefWidth(250);
        cltTabCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getCltTab()));
        cltTabCol.setStyle("-fx-alignment: CENTER-LEFT;");

        // 服务器表名列
        TableColumn<TableMapping, String> svrTabCol = new TableColumn<>("服务器表名");
        svrTabCol.setPrefWidth(250);
        svrTabCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getSvrTab()));
        svrTabCol.setStyle("-fx-alignment: CENTER-LEFT;");

        // 字段数量列
        TableColumn<TableMapping, String> fieldCountCol = new TableColumn<>("字段数");
        fieldCountCol.setPrefWidth(80);
        fieldCountCol.setCellValueFactory(param -> {
            String sameFields = param.getValue().getSameFileds();
            int count = sameFields != null && !sameFields.isEmpty()
                ? sameFields.split(",").length : 0;
            return new SimpleStringProperty(String.valueOf(count));
        });
        fieldCountCol.setStyle("-fx-alignment: CENTER;");

        // 状态列
        TableColumn<TableMapping, String> statusCol = new TableColumn<>("状态");
        statusCol.setPrefWidth(100);
        statusCol.setCellValueFactory(param -> {
            boolean hasRedundant = hasRedundantFields(param.getValue());
            return new SimpleStringProperty(hasRedundant ? "⚠️ 有冗余" : "✅ 正常");
        });
        statusCol.setStyle("-fx-alignment: CENTER;");

        mappingTable.getColumns().addAll(indexCol, cltTabCol, svrTabCol, fieldCountCol, statusCol);

        // 设置数据
        filteredMappingList = new FilteredList<>(mappingList, p -> true);
        mappingTable.setItems(filteredMappingList);

        // 选择监听器 - 显示详情
        mappingTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    showMappingDetail(newVal);
                }
            }
        );

        // 双击编辑
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

        Label headerLabel = new Label("📝 字段映射详情");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label hintLabel = new Label("👈 请从左侧列表选择一个表映射查看详细的字段对应关系");
        hintLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(Double.MAX_VALUE);

        VBox.setVgrow(hintLabel, Priority.ALWAYS);
        panel.getChildren().addAll(headerLabel, hintLabel);

        return panel;
    }

    /**
     * 显示映射详情
     */
    private void showMappingDetail(TableMapping mapping) {
        detailPanel.getChildren().clear();

        // 标题
        Label headerLabel = new Label("📝 字段映射详情");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 表信息卡片
        VBox infoCard = createInfoCard(mapping);

        // 字段对比区域
        HBox fieldCompareBox = createFieldCompareView(mapping);

        VBox.setVgrow(fieldCompareBox, Priority.ALWAYS);
        detailPanel.getChildren().addAll(headerLabel, infoCard, fieldCompareBox);
    }

    /**
     * 创建信息卡片
     */
    private VBox createInfoCard(TableMapping mapping) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 5;");

        Label cltLabel = new Label("📦 客户端表: " + mapping.getCltTab());
        cltLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label svrLabel = new Label("🖥️ 服务器表: " + mapping.getSvrTab());
        svrLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        int fieldCount = mapping.getSameFileds() != null && !mapping.getSameFileds().isEmpty()
            ? mapping.getSameFileds().split(",").length : 0;
        Label countLabel = new Label("📊 映射字段数: " + fieldCount);

        card.getChildren().addAll(cltLabel, svrLabel, countLabel);
        return card;
    }

    /**
     * 创建字段对比视图
     */
    private HBox createFieldCompareView(TableMapping mapping) {
        HBox compareBox = new HBox(15);
        compareBox.setPadding(new Insets(10, 0, 0, 0));

        // 左侧：客户端字段
        VBox leftColumn = createFieldColumn(
            "客户端字段",
            mapping.getSameFileds(),
            mapping.getCltRedundantFields(),
            "#4CAF50"
        );

        // 中间：映射指示器
        VBox centerColumn = createMappingIndicator(mapping.getSameFileds());

        // 右侧：服务器字段
        VBox rightColumn = createFieldColumn(
            "服务器字段",
            mapping.getSameFileds(),
            mapping.getSvrRedundantFields(),
            "#2196F3"
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
        fieldList.setPrefHeight(400);

        List<String> allFields = new ArrayList<>();

        // 添加共同字段
        if (commonFields != null && !commonFields.isEmpty()) {
            Arrays.stream(commonFields.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(field -> allFields.add("✅ " + field + " (共同)"));
        }

        // 添加冗余字段
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

        for (int i = 0; i < Math.min(fieldCount, 10); i++) {
            Label arrow = new Label("⟷");
            arrow.setStyle("-fx-font-size: 18px; -fx-text-fill: #666;");
            indicator.getChildren().add(arrow);
        }

        if (fieldCount > 10) {
            Label more = new Label("...\n+" + (fieldCount - 10));
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

        Button editBtn = new Button("✏️ 编辑映射");
        editBtn.setOnAction(e -> {
            TableMapping selected = mappingTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editMapping(selected);
            } else {
                showAlert("请先选择要编辑的映射");
            }
        });

        Button deleteBtn = new Button("🗑️ 删除映射");
        deleteBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        deleteBtn.setOnAction(e -> deleteMapping());

        Button exportBtn = new Button("📤 导出配置");
        exportBtn.setOnAction(e -> exportConfig());

        Button validateBtn = new Button("✔️ 验证配置");
        validateBtn.setOnAction(e -> validateMappings());

        Button saveBtn = new Button("💾 保存配置");
        saveBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                        "-fx-font-weight: bold;");
        saveBtn.setOnAction(e -> saveConfig());

        buttonBar.getChildren().addAll(
            addBtn, editBtn, deleteBtn,
            new Separator(Orientation.VERTICAL),
            exportBtn, validateBtn,
            new Separator(Orientation.VERTICAL),
            saveBtn
        );

        return buttonBar;
    }

    /**
     * 加载映射数据
     */
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

    /**
     * 刷新数据
     */
    private void refreshData() {
        loadMappingData();
        if (filteredMappingList != null) {
            filteredMappingList = new FilteredList<>(mappingList, p -> true);
            mappingTable.setItems(filteredMappingList);
        }
        updateStats();
        showInfo("数据已刷新", "成功从配置文件重新加载 " + mappingList.size() + " 条映射");
    }

    /**
     * 筛选映射
     */
    private void filterMappings(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredMappingList.setPredicate(p -> true);
        } else {
            String lower = searchText.toLowerCase().trim();
            filteredMappingList.setPredicate(mapping -> {
                // 搜索表名
                if (mapping.getCltTab() != null && mapping.getCltTab().toLowerCase().contains(lower)) {
                    return true;
                }
                if (mapping.getSvrTab() != null && mapping.getSvrTab().toLowerCase().contains(lower)) {
                    return true;
                }
                // 搜索字段名
                if (mapping.getSameFileds() != null && mapping.getSameFileds().toLowerCase().contains(lower)) {
                    return true;
                }
                return false;
            });
        }
        updateStats();
    }

    /**
     * 应用筛选器
     */
    private void applyFilter(String filterType) {
        if (filterType == null) return;

        switch (filterType) {
            case "全部映射":
                filteredMappingList.setPredicate(p -> true);
                break;
            case "有冗余字段":
                filteredMappingList.setPredicate(this::hasRedundantFields);
                break;
            case "无冗余字段":
                filteredMappingList.setPredicate(m -> !hasRedundantFields(m));
                break;
            case "字段数>10":
                filteredMappingList.setPredicate(m -> {
                    String fields = m.getSameFileds();
                    return fields != null && fields.split(",").length > 10;
                });
                break;
        }
        updateStats();
    }

    /**
     * 检查是否有冗余字段
     */
    private boolean hasRedundantFields(TableMapping mapping) {
        return (mapping.getCltRedundantFields() != null && !mapping.getCltRedundantFields().trim().isEmpty())
            || (mapping.getSvrRedundantFields() != null && !mapping.getSvrRedundantFields().trim().isEmpty());
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        if (statsLabel == null) return;

        int total = mappingList.size();
        int displayed = filteredMappingList != null ? filteredMappingList.size() : total;
        int withRedundant = (int) mappingList.stream().filter(this::hasRedundantFields).count();

        statsLabel.setText(String.format(
            "📊 总计: %d 条映射 | 显示: %d 条 | 有冗余字段: %d 条",
            total, displayed, withRedundant
        ));
    }

    /**
     * 新建映射
     */
    private void addNewMapping() {
        MappingEditDialog dialog = new MappingEditDialog(managerStage, null);
        dialog.showAndWait().ifPresent(newMapping -> {
            mappingList.add(newMapping);
            updateStats();
            showInfo("新建成功", "已添加新的表映射配置");
        });
    }

    /**
     * 编辑映射
     */
    private void editMapping(TableMapping mapping) {
        MappingEditDialog dialog = new MappingEditDialog(managerStage, mapping);
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

    /**
     * 删除映射
     */
    private void deleteMapping() {
        TableMapping selected = mappingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先选择要删除的映射");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("删除映射配置");
        confirm.setContentText("确定要删除映射: " + selected.getCltTab() + " ↔ " + selected.getSvrTab() + " ?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                mappingList.remove(selected);
                updateStats();
                showInfo("删除成功", "已删除表映射配置");
            }
        });
    }

    /**
     * 保存配置
     */
    private void saveConfig() {
        try {
            String configPath = YamlUtils.getProperty("file.homePath") + File.separator + CONFIG_FILE;
            String json = JSON.toJSONString(mappingList, SerializerFeature.PrettyFormat);
            FileUtil.writeUtf8String(json, configPath);

            log.info("映射配置已保存到: {}", configPath);
            showInfo("保存成功", "映射配置已保存到文件:\n" + configPath);
        } catch (Exception e) {
            log.error("保存配置失败", e);
            showAlert("保存配置失败: " + e.getMessage());
        }
    }

    /**
     * 导出配置
     */
    private void exportConfig() {
        showInfo("功能开发中", "配置导出功能即将上线");
    }

    /**
     * 验证映射
     */
    private void validateMappings() {
        StringBuilder report = new StringBuilder();
        report.append("📋 映射配置验证报告\n\n");

        int totalMappings = mappingList.size();
        int emptyFields = 0;
        int withRedundant = 0;

        for (TableMapping mapping : mappingList) {
            if (mapping.getSameFileds() == null || mapping.getSameFileds().trim().isEmpty()) {
                emptyFields++;
            }
            if (hasRedundantFields(mapping)) {
                withRedundant++;
            }
        }

        report.append(String.format("✅ 总映射数: %d\n", totalMappings));
        report.append(String.format("⚠️ 空字段映射: %d\n", emptyFields));
        report.append(String.format("📊 含冗余字段: %d\n", withRedundant));
        report.append(String.format("✔️ 正常映射: %d\n", totalMappings - emptyFields));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("验证结果");
        alert.setHeaderText("映射配置验证完成");
        alert.setContentText(report.toString());
        alert.showAndWait();
    }

    /**
     * 显示提示信息
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示警告信息
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
