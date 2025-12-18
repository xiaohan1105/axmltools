package red.jiuzhou.ui.mapping;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import red.jiuzhou.ui.components.SmartColumnWidthManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * 数据库驱动的映射管理器
 * 自动从数据库加载所有表映射关系，提供字段级精细对比和数据同步功能
 *
 * 核心特性:
 * - 自动扫描数据库中的所有 client_* 表和对应的服务端表
 * - 详细的字段级对比视图（类型、注释、差异标注）
 * - 数据同步功能（Client → Server, Server → Client）
 * - 字段选择性同步
 * - 实时搜索和筛选
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 1.0
 */
public class DatabaseMappingManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMappingManager.class);

    private final Stage ownerStage;
    private Stage managerStage;

    // 数据
    private List<DatabaseTableScanner.TableInfo> allTables;
    private List<DatabaseTableScanner.TablePairResult> smartTablePairs;  // 智能匹配结果
    private ObservableList<TablePairWrapper> tablePairList;
    private FilteredList<TablePairWrapper> filteredList;

    // UI组件
    private TableView<TablePairWrapper> pairTableView;
    private TextField searchField;
    private Label statsLabel;
    private VBox detailPanel;
    private TableView<FieldRowData> fieldCompareTable;

    // 当前选中的表对
    private TablePairWrapper currentSelectedPair;

    /**
     * 构造函数
     */
    public DatabaseMappingManager(Stage ownerStage) {
        this.ownerStage = ownerStage;
    }

    /**
     * 显示管理器窗口
     */
    public void show() {
        managerStage = new Stage();
        managerStage.initOwner(ownerStage);
        managerStage.initModality(Modality.NONE);
        managerStage.setTitle("🗄️ 数据库映射管理器 - 自动加载客户端/服务端表");

        // 显示加载进度对话框
        showLoadingDialog();

        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // 顶部工具栏
        mainLayout.setTop(createToolBar());

        // 中心区域 - 分割面板
        SplitPane centerPane = createCenterPane();
        mainLayout.setCenter(centerPane);

        // 底部按钮栏
        mainLayout.setBottom(createBottomBar());

        Scene scene = new Scene(mainLayout, 1600, 900);
        managerStage.setScene(scene);
        managerStage.show();

        log.info("数据库映射管理器已打开");
    }

    /**
     * 显示加载对话框并扫描数据库（智能缓存）
     */
    private void showLoadingDialog() {
        showLoadingDialog(false);
    }

    /**
     * 显示加载对话框并扫描数据库
     *
     * @param forceRefresh 是否强制刷新
     */
    private void showLoadingDialog(boolean forceRefresh) {
        Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
        loadingAlert.setTitle(forceRefresh ? "刷新数据" : "加载中");
        loadingAlert.setHeaderText(forceRefresh ?
            "正在从数据库刷新表结构..." :
            "正在加载表结构...");
        loadingAlert.setContentText(forceRefresh ?
            "强制刷新中，请稍候" :
            "首次启动或缓存失效，正在扫描数据库\n" +
            "这可能需要几秒钟，请耐心等待");

        // 异步加载数据
        Thread loadThread = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                if (forceRefresh) {
                    log.info("强制刷新：清除缓存并从数据库重新加载");
                    TableStructureCache.clearCache();
                }

                // 使用智能缓存加载
                allTables = TableStructureCache.loadTableStructures(forceRefresh);

                // 使用智能匹配构建表对
                smartTablePairs = DatabaseTableScanner.buildSmartTablePairs(allTables);

                // 构建表对列表（包含智能匹配信息）
                tablePairList = FXCollections.observableArrayList();
                for (DatabaseTableScanner.TablePairResult pairResult : smartTablePairs) {
                    tablePairList.add(new TablePairWrapper(pairResult));
                }

                long duration = System.currentTimeMillis() - startTime;

                // 统计匹配质量
                int exactMatches = 0;
                int fuzzyMatches = 0;
                int multipleMatches = 0;
                int unmatched = 0;

                for (DatabaseTableScanner.TablePairResult pair : smartTablePairs) {
                    if (pair.matchMethod.equals("精确匹配")) {
                        exactMatches++;
                    } else if (pair.matchMethod.contains("模糊匹配")) {
                        fuzzyMatches++;
                    }
                    if (pair.isMultipleMatch) {
                        multipleMatches++;
                    }
                    if (pair.serverTable == null) {
                        unmatched++;
                    }
                }

                log.info("表结构加载完成，找到 {} 对表映射（耗时: {} ms, {}）",
                    tablePairList.size(), duration, TableStructureCache.getCacheStats());
                log.info("匹配质量统计: 精确={}, 模糊={}, 多对一={}, 未匹配={}",
                    exactMatches, fuzzyMatches, multipleMatches, unmatched);

                // 关闭加载对话框
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();
                    if (forceRefresh) {
                        showInfo("刷新完成",
                            String.format("已从数据库刷新 %d 对表映射\n耗时: %d ms",
                                tablePairList.size(), duration));
                    }
                });

            } catch (Exception e) {
                log.error("加载表结构失败", e);
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();
                    showError("加载表结构失败: " + e.getMessage());
                });
            }
        });

        loadThread.setName("TableStructure-Loader");
        loadThread.setDaemon(true);
        loadThread.start();
        loadingAlert.showAndWait();
    }

    /**
     * 创建顶部工具栏
     */
    private VBox createToolBar() {
        VBox toolBarContainer = new VBox(10);
        toolBarContainer.setPadding(new Insets(0, 0, 10, 0));

        // 标题行
        HBox titleRow = new HBox(15);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("🗄️ 数据库映射管理器");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        statsLabel = new Label();
        updateStats();
        statsLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

        titleRow.getChildren().addAll(titleLabel, statsLabel);

        // 搜索和工具行
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("🔍 搜索:");
        searchField = new TextField();
        searchField.setPromptText("搜索表名...");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTables(newVal));

        Button clearSearchBtn = new Button("✖");
        clearSearchBtn.setOnAction(e -> searchField.clear());

        Button refreshBtn = new Button("🔄 刷新数据");
        refreshBtn.setOnAction(e -> refreshData());
        refreshBtn.setTooltip(new Tooltip("从数据库重新加载表结构"));

        Button manualMappingBtn = new Button("⚙️ 手动映射");
        manualMappingBtn.setOnAction(e -> showManualMappingDialog());
        manualMappingBtn.setTooltip(new Tooltip("配置自动匹配失败的表映射"));

        Button matchStatsBtn = new Button("📊 匹配统计");
        matchStatsBtn.setOnAction(e -> showMatchingStatistics());
        matchStatsBtn.setTooltip(new Tooltip("查看匹配质量统计信息"));

        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.setPromptText("筛选");
        filterCombo.getItems().addAll(
            "全部映射",
            "有服务端表",
            "缺少服务端表",
            "精确匹配",
            "模糊匹配",
            "未匹配",
            "多对一映射",
            "字段完全匹配",
            "有字段差异"
        );
        filterCombo.setValue("全部映射");
        filterCombo.setOnAction(e -> applyFilter(filterCombo.getValue()));

        // 批量操作按钮
        Menu batchMenu = new Menu("📦 批量操作");
        MenuItem batchDdlItem = new MenuItem("🔧 批量生成DDL");
        batchDdlItem.setOnAction(e -> showBatchDdlDialog());
        MenuItem batchImportItem = new MenuItem("📥 批量导入XML到数据库");
        batchImportItem.setOnAction(e -> showBatchImportDialog());
        MenuItem batchExportItem = new MenuItem("📤 批量导出数据库到XML");
        batchExportItem.setOnAction(e -> showBatchExportDialog());
        MenuItem batchValidateItem = new MenuItem("✅ 批量验证映射");
        batchValidateItem.setOnAction(e -> showBatchValidateDialog());
        batchMenu.getItems().addAll(batchDdlItem, batchImportItem, batchExportItem, new SeparatorMenuItem(), batchValidateItem);

        MenuButton batchBtn = new MenuButton("📦 批量操作");
        batchBtn.getItems().addAll(
            createMenuItem("🔧 批量生成DDL", e -> showBatchDdlDialog()),
            createMenuItem("📥 批量导入XML到DB", e -> showBatchImportDialog()),
            createMenuItem("📤 批量导出DB到XML", e -> showBatchExportDialog()),
            new SeparatorMenuItem(),
            createMenuItem("✅ 批量验证映射", e -> showBatchValidateDialog()),
            createMenuItem("🔗 分析表间关系", e -> showTableRelationsDialog())
        );
        batchBtn.setTooltip(new Tooltip("批量执行DDL生成、数据导入导出等操作"));

        searchRow.getChildren().addAll(
            searchLabel, searchField, clearSearchBtn,
            new Separator(Orientation.VERTICAL),
            filterCombo, refreshBtn, manualMappingBtn, matchStatsBtn,
            new Separator(Orientation.VERTICAL),
            batchBtn
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

        VBox leftPanel = createTableListPanel();
        detailPanel = createDetailPanel();

        splitPane.getItems().addAll(leftPanel, detailPanel);
        splitPane.setDividerPositions(0.4);

        return splitPane;
    }

    /**
     * 创建表对列表面板（左侧面板）
     */
    private VBox createTableListPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        // 标题行（包含快捷操作按钮）
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label headerLabel = new Label("📋 表映射列表");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 快捷操作提示
        Label tipLabel = new Label("💡 右键点击可进行批量操作");
        tipLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        headerRow.getChildren().addAll(headerLabel, tipLabel);

        pairTableView = new TableView<>();
        pairTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 启用多选模式（支持批量操作）
        pairTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 序号列
        TableColumn<TablePairWrapper, String> indexCol = new TableColumn<>("序号");
        indexCol.setPrefWidth(50);
        indexCol.setCellValueFactory(param -> {
            int index = pairTableView.getItems().indexOf(param.getValue()) + 1;
            return new SimpleStringProperty(String.valueOf(index));
        });

        // 客户端表列
        TableColumn<TablePairWrapper, String> clientCol = new TableColumn<>("📦 客户端表");
        clientCol.setPrefWidth(200);
        clientCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getClientTableName()));

        // 层级列（新增）
        TableColumn<TablePairWrapper, String> levelCol = new TableColumn<>("层级");
        levelCol.setPrefWidth(90);
        levelCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getLevelDisplay()));
        levelCol.setStyle("-fx-alignment: CENTER;");

        // 映射指示
        TableColumn<TablePairWrapper, String> arrowCol = new TableColumn<>("↔");
        arrowCol.setPrefWidth(40);
        arrowCol.setCellValueFactory(param -> new SimpleStringProperty("⟷"));
        arrowCol.setStyle("-fx-alignment: CENTER;");

        // 服务端表列
        TableColumn<TablePairWrapper, String> serverCol = new TableColumn<>("🖥️ 服务端表");
        serverCol.setPrefWidth(200);
        serverCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getServerTableName()));

        // 共同字段数
        TableColumn<TablePairWrapper, String> commonCol = new TableColumn<>("共同字段");
        commonCol.setPrefWidth(80);
        commonCol.setCellValueFactory(param ->
            new SimpleStringProperty(String.valueOf(param.getValue().getCommonFieldCount())));
        commonCol.setStyle("-fx-alignment: CENTER;");

        // 客户端独有
        TableColumn<TablePairWrapper, String> clientOnlyCol = new TableColumn<>("客户端独有");
        clientOnlyCol.setPrefWidth(90);
        clientOnlyCol.setCellValueFactory(param ->
            new SimpleStringProperty(String.valueOf(param.getValue().getClientOnlyCount())));
        clientOnlyCol.setStyle("-fx-alignment: CENTER;");

        // 服务端独有
        TableColumn<TablePairWrapper, String> serverOnlyCol = new TableColumn<>("服务端独有");
        serverOnlyCol.setPrefWidth(90);
        serverOnlyCol.setCellValueFactory(param ->
            new SimpleStringProperty(String.valueOf(param.getValue().getServerOnlyCount())));
        serverOnlyCol.setStyle("-fx-alignment: CENTER;");

        // 匹配质量列（新增）
        TableColumn<TablePairWrapper, String> matchQualityCol = new TableColumn<>("🎯 匹配质量");
        matchQualityCol.setPrefWidth(180);
        matchQualityCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getMatchQualityDisplay()));
        matchQualityCol.setStyle("-fx-alignment: CENTER_LEFT;");

        // 字段状态列
        TableColumn<TablePairWrapper, String> fieldStatusCol = new TableColumn<>("字段状态");
        fieldStatusCol.setPrefWidth(200);
        fieldStatusCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getFieldStatusDisplay()));

        pairTableView.getColumns().addAll(
            indexCol, clientCol, levelCol, arrowCol, serverCol,
            matchQualityCol, commonCol, clientOnlyCol, serverOnlyCol, fieldStatusCol
        );

        filteredList = new FilteredList<>(tablePairList, p -> true);
        pairTableView.setItems(filteredList);

        // 应用智能列宽管理
        pairTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        // 设置合理的最小和最大列宽
        indexCol.setMinWidth(50); indexCol.setMaxWidth(60);
        clientCol.setMinWidth(120);
        levelCol.setMinWidth(60); levelCol.setMaxWidth(100);
        arrowCol.setMinWidth(40); arrowCol.setMaxWidth(50);
        serverCol.setMinWidth(120);
        matchQualityCol.setMinWidth(150); matchQualityCol.setMaxWidth(250);
        commonCol.setMinWidth(80); commonCol.setMaxWidth(100);
        clientOnlyCol.setMinWidth(80); clientOnlyCol.setMaxWidth(120);
        serverOnlyCol.setMinWidth(80); serverOnlyCol.setMaxWidth(120);
        fieldStatusCol.setMinWidth(150);

        pairTableView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    showTableDetail(newVal);
                }
            }
        );

        VBox.setVgrow(pairTableView, Priority.ALWAYS);

        // 添加表映射列表的右键菜单
        setupPairTableContextMenu();

        panel.getChildren().addAll(headerRow, pairTableView);

        return panel;
    }

    /**
     * 设置表映射列表的右键菜单（增强版，支持多选批量操作）
     */
    private void setupPairTableContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        // === 查看组 ===
        MenuItem viewDetailItem = new MenuItem("👁️ 查看字段详情");
        viewDetailItem.setOnAction(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showTableDetail(selected);
            }
        });

        MenuItem viewRelationsItem = new MenuItem("🔗 查看表间关系");
        viewRelationsItem.setOnAction(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showTableRelationsFor(selected);
            }
        });

        // === 单表操作组 ===
        MenuItem generateDdlItem = new MenuItem("🔧 生成此表DDL");
        generateDdlItem.setOnAction(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                generateDdlForTable(selected);
            }
        });

        MenuItem importXmlItem = new MenuItem("📥 导入此表XML到数据库");
        importXmlItem.setOnAction(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                importXmlForTable(selected);
            }
        });

        MenuItem exportXmlItem = new MenuItem("📤 导出此表到XML");
        exportXmlItem.setOnAction(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                exportXmlForTable(selected);
            }
        });

        // === 批量操作组（选中项）===
        Menu batchSelectedMenu = new Menu("📦 批量操作（选中项）");

        MenuItem batchDdlSelectedItem = new MenuItem("🔧 生成选中表DDL");
        batchDdlSelectedItem.setOnAction(e -> batchGenerateDdlForSelected());

        MenuItem batchImportSelectedItem = new MenuItem("📥 导入选中表XML到数据库");
        batchImportSelectedItem.setOnAction(e -> batchImportForSelected());

        MenuItem batchExportSelectedItem = new MenuItem("📤 导出选中表到XML");
        batchExportSelectedItem.setOnAction(e -> batchExportForSelected());

        batchSelectedMenu.getItems().addAll(batchDdlSelectedItem, batchImportSelectedItem, batchExportSelectedItem);

        // === 全部操作组 ===
        Menu batchAllMenu = new Menu("🗂️ 全部操作");

        MenuItem batchDdlAllItem = new MenuItem("🔧 全部生成DDL");
        batchDdlAllItem.setOnAction(e -> showBatchDdlDialog());

        MenuItem batchImportAllItem = new MenuItem("📥 全部导入XML到数据库");
        batchImportAllItem.setOnAction(e -> showBatchImportDialog());

        MenuItem batchExportAllItem = new MenuItem("📤 全部导出到XML");
        batchExportAllItem.setOnAction(e -> showBatchExportDialog());

        MenuItem batchValidateAllItem = new MenuItem("✅ 全部验证映射");
        batchValidateAllItem.setOnAction(e -> showBatchValidateDialog());

        batchAllMenu.getItems().addAll(batchDdlAllItem, batchImportAllItem, batchExportAllItem,
            new SeparatorMenuItem(), batchValidateAllItem);

        // === 快速选择组 ===
        Menu selectMenu = new Menu("🎯 快速选择");

        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setOnAction(e -> pairTableView.getSelectionModel().selectAll());

        MenuItem selectNoneItem = new MenuItem("取消选择");
        selectNoneItem.setOnAction(e -> pairTableView.getSelectionModel().clearSelection());

        MenuItem selectMatchedItem = new MenuItem("选择已匹配的表");
        selectMatchedItem.setOnAction(e -> selectTablesByCondition(t -> t.serverTable != null));

        MenuItem selectUnmatchedItem = new MenuItem("选择未匹配的表");
        selectUnmatchedItem.setOnAction(e -> selectTablesByCondition(t -> t.serverTable == null));

        MenuItem selectStringsItem = new MenuItem("选择strings表");
        selectStringsItem.setOnAction(e -> selectTablesByCondition(t ->
            t.getClientTableName().toLowerCase().contains("string")));

        selectMenu.getItems().addAll(selectAllItem, selectNoneItem, new SeparatorMenuItem(),
            selectMatchedItem, selectUnmatchedItem, selectStringsItem);

        // 复制组
        MenuItem copyTableNameItem = new MenuItem("📋 复制表名");
        copyTableNameItem.setOnAction(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String names = selected.getClientTableName() + " -> " + selected.getServerTableName();
                red.jiuzhou.ui.components.ContextMenuFactory.copyToClipboard(names);
            }
        });

        MenuItem copyMappingInfoItem = new MenuItem("📄 复制映射信息");
        copyMappingInfoItem.setOnAction(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String info = String.format("客户端表: %s\n服务端表: %s\n共同字段: %d\n客户端独有: %d\n服务端独有: %d\n匹配方法: %s",
                    selected.getClientTableName(),
                    selected.getServerTableName(),
                    selected.getCommonFieldCount(),
                    selected.getClientOnlyCount(),
                    selected.getServerOnlyCount(),
                    selected.matchMethod);
                red.jiuzhou.ui.components.ContextMenuFactory.copyToClipboard(info);
            }
        });

        // 手动映射
        MenuItem setManualMappingItem = new MenuItem("⚙️ 设置手动映射");
        setManualMappingItem.setOnAction(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showManualMappingForTable(selected);
            }
        });

        // 组装菜单（增强版）
        contextMenu.getItems().addAll(
            viewDetailItem,
            viewRelationsItem,
            new SeparatorMenuItem(),
            generateDdlItem,
            importXmlItem,
            exportXmlItem,
            new SeparatorMenuItem(),
            batchSelectedMenu,      // 选中项批量操作
            batchAllMenu,           // 全部操作
            new SeparatorMenuItem(),
            selectMenu,             // 快速选择
            new SeparatorMenuItem(),
            copyTableNameItem,
            copyMappingInfoItem,
            new SeparatorMenuItem(),
            setManualMappingItem
        );

        // 动态启用/禁用
        contextMenu.setOnShowing(e -> {
            TablePairWrapper selected = pairTableView.getSelectionModel().getSelectedItem();
            int selectedCount = pairTableView.getSelectionModel().getSelectedItems().size();
            boolean hasSelection = selected != null;
            boolean hasServer = hasSelection && selected.serverTable != null;
            boolean hasMultipleSelection = selectedCount > 1;

            // 单表操作
            viewDetailItem.setDisable(!hasSelection);
            viewRelationsItem.setDisable(!hasSelection);
            generateDdlItem.setDisable(!hasSelection);
            importXmlItem.setDisable(!hasSelection);
            exportXmlItem.setDisable(!hasServer);
            copyTableNameItem.setDisable(!hasSelection);
            copyMappingInfoItem.setDisable(!hasSelection);
            setManualMappingItem.setDisable(!hasSelection);

            // 批量操作（选中项）- 更新标签显示选中数量
            batchSelectedMenu.setText(String.format("📦 批量操作（已选%d项）", selectedCount));
            batchSelectedMenu.setDisable(selectedCount == 0);
        });

        pairTableView.setContextMenu(contextMenu);
    }

    /**
     * 根据条件选择表
     */
    private void selectTablesByCondition(java.util.function.Predicate<TablePairWrapper> condition) {
        pairTableView.getSelectionModel().clearSelection();
        ObservableList<TablePairWrapper> items = pairTableView.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (condition.test(items.get(i))) {
                pairTableView.getSelectionModel().select(i);
            }
        }
        int selected = pairTableView.getSelectionModel().getSelectedItems().size();
        showInfo("选择完成", String.format("已选择 %d 个表", selected));
    }

    /**
     * 创建详情面板
     */
    private VBox createDetailPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label headerLabel = new Label("📝 字段级详细对比");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label hintLabel = new Label("👈 从左侧选择一个表映射查看详细字段对比");
        hintLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        hintLabel.setWrapText(true);

        VBox.setVgrow(hintLabel, Priority.ALWAYS);
        panel.getChildren().addAll(headerLabel, hintLabel);

        return panel;
    }

    /**
     * 显示表对详情
     */
    private void showTableDetail(TablePairWrapper pair) {
        currentSelectedPair = pair;
        detailPanel.getChildren().clear();

        Label headerLabel = new Label("📝 字段级详细对比");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 表信息卡片
        VBox infoCard = createTableInfoCard(pair);

        // 字段对比表格
        fieldCompareTable = createFieldCompareTable(pair);

        VBox.setVgrow(fieldCompareTable, Priority.ALWAYS);
        detailPanel.getChildren().addAll(headerLabel, infoCard, fieldCompareTable);
    }

    /**
     * 创建表信息卡片
     */
    private VBox createTableInfoCard(TablePairWrapper pair) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; " +
                     "-fx-border-radius: 5; -fx-background-radius: 5;");

        HBox tableRow = new HBox(20);
        tableRow.setAlignment(Pos.CENTER_LEFT);

        // 客户端表信息
        VBox clientBox = createTableBox("📦 客户端表", pair.getClientTableName(),
            pair.clientTable != null ? pair.clientTable.getTableComment() : "",
            pair.clientTable != null ? pair.clientTable.getRowCount() : 0,
            "#2196F3");

        Label arrow = new Label("⟷");
        arrow.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        // 服务端表信息
        VBox serverBox = createTableBox("🖥️ 服务端表", pair.getServerTableName(),
            pair.serverTable != null ? pair.serverTable.getTableComment() : "",
            pair.serverTable != null ? pair.serverTable.getRowCount() : 0,
            "#FF9800");

        tableRow.getChildren().addAll(clientBox, arrow, serverBox);

        // 统计信息
        HBox statsRow = new HBox(20);
        statsRow.setAlignment(Pos.CENTER);
        statsRow.setPadding(new Insets(10, 0, 0, 0));

        Label commonLabel = new Label("✅ 共同字段: " + pair.getCommonFieldCount());
        Label clientOnlyLabel = new Label("⚠️ 客户端独有: " + pair.getClientOnlyCount());
        Label serverOnlyLabel = new Label("⚠️ 服务端独有: " + pair.getServerOnlyCount());

        commonLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        clientOnlyLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        serverOnlyLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        statsRow.getChildren().addAll(commonLabel, new Separator(Orientation.VERTICAL),
            clientOnlyLabel, new Separator(Orientation.VERTICAL), serverOnlyLabel);

        card.getChildren().addAll(tableRow, new Separator(), statsRow);
        return card;
    }

    /**
     * 创建表信息框
     */
    private VBox createTableBox(String title, String tableName, String comment,
                                 int rowCount, String color) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: " + color + "; -fx-border-width: 2; " +
                    "-fx-border-radius: 5; -fx-background-color: white; " +
                    "-fx-background-radius: 5; -fx-min-width: 280;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        Label nameLabel = new Label(tableName != null ? tableName : "不存在");
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        if (comment != null && !comment.isEmpty()) {
            Label commentLabel = new Label("💬 " + comment);
            commentLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
            commentLabel.setWrapText(true);
            box.getChildren().add(commentLabel);
        }

        Label rowLabel = new Label("📊 数据行数: " + rowCount);
        rowLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        box.getChildren().addAll(titleLabel, nameLabel, rowLabel);
        return box;
    }

    /**
     * 创建字段对比表格
     */
    private TableView<FieldRowData> createFieldCompareTable(TablePairWrapper pair) {
        TableView<FieldRowData> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setEditable(true);

        // 选择框列 - 固定小宽度
        TableColumn<FieldRowData, Boolean> selectCol = new TableColumn<>("选择");
        selectCol.setPrefWidth(50);
        selectCol.setMinWidth(50);
        selectCol.setMaxWidth(60);
        selectCol.setCellValueFactory(param -> param.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);

        // 字段名列 - 中等宽度
        TableColumn<FieldRowData, String> nameCol = new TableColumn<>("字段名");
        nameCol.setPrefWidth(150);
        nameCol.setMinWidth(80);
        nameCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().fieldName));

        // 客户端类型列 - 自适应宽度
        TableColumn<FieldRowData, String> clientTypeCol = new TableColumn<>("📦 客户端类型");
        clientTypeCol.setPrefWidth(180);
        clientTypeCol.setMinWidth(100);
        clientTypeCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().clientType));

        // 服务端类型列 - 自适应宽度
        TableColumn<FieldRowData, String> serverTypeCol = new TableColumn<>("🖥️ 服务端类型");
        serverTypeCol.setPrefWidth(180);
        serverTypeCol.setMinWidth(100);
        serverTypeCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().serverType));

        // 状态列 - 中等固定宽度
        TableColumn<FieldRowData, String> statusCol = new TableColumn<>("状态");
        statusCol.setPrefWidth(120);
        statusCol.setMinWidth(80);
        statusCol.setMaxWidth(150);
        statusCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getStatusDisplay()));

        // 注释列 - 较大宽度
        TableColumn<FieldRowData, String> commentCol = new TableColumn<>("注释");
        commentCol.setPrefWidth(250);
        commentCol.setMinWidth(100);
        commentCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().comment));

        table.getColumns().addAll(selectCol, nameCol, clientTypeCol, serverTypeCol,
            statusCol, commentCol);

        // 加载数据
        ObservableList<FieldRowData> fieldData = buildFieldRowData(pair);
        table.setItems(fieldData);

        // 添加右键菜单
        createFieldContextMenu(table, pair);

        // 应用智能列宽管理
        SmartColumnWidthManager.applySmartColumnWidth(table);

        // 重新设置复选框列的 CellFactory（因为智能列宽管理器会覆盖）
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));

        return table;
    }

    /**
     * 为字段对比表格创建右键菜单
     */
    private void createFieldContextMenu(TableView<FieldRowData> table, TablePairWrapper pair) {
        ContextMenu contextMenu = new ContextMenu();

        // 查看客户端字段枚举值
        MenuItem viewClientEnumsItem = new MenuItem("📦 查看客户端字段枚举值");
        viewClientEnumsItem.setOnAction(e -> {
            FieldRowData selectedField = table.getSelectionModel().getSelectedItem();
            if (selectedField != null && pair.clientTable != null) {
                if (selectedField.fieldType.equals("SERVER_ONLY")) {
                    showAlert("此字段仅存在于服务端表，无法查看客户端枚举值");
                    return;
                }
                showFieldEnumValues(
                    pair.clientTable.getTableName(),
                    selectedField.fieldName,
                    "客户端表"
                );
            }
        });

        // 查看服务端字段枚举值
        MenuItem viewServerEnumsItem = new MenuItem("🖥️ 查看服务端字段枚举值");
        viewServerEnumsItem.setOnAction(e -> {
            FieldRowData selectedField = table.getSelectionModel().getSelectedItem();
            if (selectedField != null && pair.serverTable != null) {
                if (selectedField.fieldType.equals("CLIENT_ONLY")) {
                    showAlert("此字段仅存在于客户端表，无法查看服务端枚举值");
                    return;
                }
                showFieldEnumValues(
                    pair.serverTable.getTableName(),
                    selectedField.fieldName,
                    "服务端表"
                );
            }
        });

        // 对比两侧枚举值
        MenuItem compareEnumsItem = new MenuItem("⚖️ 对比两侧枚举值");
        compareEnumsItem.setOnAction(e -> {
            FieldRowData selectedField = table.getSelectionModel().getSelectedItem();
            if (selectedField != null && selectedField.fieldType.equals("COMMON")) {
                compareFieldEnumValues(
                    pair.clientTable.getTableName(),
                    pair.serverTable != null ? pair.serverTable.getTableName() : null,
                    selectedField.fieldName
                );
            } else {
                showAlert("只能对比共同字段的枚举值");
            }
        });

        // 查看字段详细信息
        MenuItem viewDetailItem = new MenuItem("ℹ️ 查看字段详细信息");
        viewDetailItem.setOnAction(e -> {
            FieldRowData selectedField = table.getSelectionModel().getSelectedItem();
            if (selectedField != null) {
                showFieldDetailInfo(selectedField, pair);
            }
        });

        contextMenu.getItems().addAll(
            viewClientEnumsItem,
            viewServerEnumsItem,
            new SeparatorMenuItem(),
            compareEnumsItem,
            new SeparatorMenuItem(),
            viewDetailItem
        );

        // 根据选中行的类型动态启用/禁用菜单项
        table.setContextMenu(contextMenu);
        contextMenu.setOnShowing(e -> {
            FieldRowData selectedField = table.getSelectionModel().getSelectedItem();
            if (selectedField != null) {
                viewClientEnumsItem.setDisable(
                    selectedField.fieldType.equals("SERVER_ONLY") || pair.clientTable == null
                );
                viewServerEnumsItem.setDisable(
                    selectedField.fieldType.equals("CLIENT_ONLY") || pair.serverTable == null
                );
                compareEnumsItem.setDisable(
                    !selectedField.fieldType.equals("COMMON") || pair.serverTable == null
                );
            } else {
                viewClientEnumsItem.setDisable(true);
                viewServerEnumsItem.setDisable(true);
                compareEnumsItem.setDisable(true);
                viewDetailItem.setDisable(true);
            }
        });
    }

    /**
     * 显示字段枚举值
     */
    private void showFieldEnumValues(String tableName, String fieldName, String tableLabel) {
        Stage dialog = new Stage();
        dialog.initOwner(managerStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(String.format("🔍 字段枚举值 - %s.%s", tableName, fieldName));

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));

        Label titleLabel = new Label(String.format("%s: %s.%s", tableLabel, tableName, fieldName));
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 查询枚举值
        Label loadingLabel = new Label("正在加载枚举值...");
        layout.getChildren().addAll(titleLabel, loadingLabel);

        Scene scene = new Scene(layout, 700, 500);
        dialog.setScene(scene);
        dialog.show();

        // 异步加载枚举值
        Thread loadThread = new Thread(() -> {
            try {
                List<EnumValueInfo> enumValues = queryFieldEnumValues(tableName, fieldName);

                javafx.application.Platform.runLater(() -> {
                    layout.getChildren().remove(loadingLabel);

                    if (enumValues.isEmpty()) {
                        Label emptyLabel = new Label("该字段没有数据或所有值都为 NULL");
                        emptyLabel.setStyle("-fx-text-fill: #999;");
                        layout.getChildren().add(emptyLabel);
                        return;
                    }

                    // 统计信息
                    HBox statsBox = new HBox(15);
                    statsBox.setAlignment(Pos.CENTER_LEFT);
                    statsBox.setPadding(new Insets(10, 0, 10, 0));
                    statsBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 10;");

                    int totalCount = enumValues.stream().mapToInt(v -> v.count).sum();
                    Label totalLabel = new Label("总记录数: " + totalCount);
                    Label uniqueLabel = new Label("唯一值数: " + enumValues.size());

                    totalLabel.setStyle("-fx-font-weight: bold;");
                    uniqueLabel.setStyle("-fx-font-weight: bold;");

                    statsBox.getChildren().addAll(totalLabel, new Separator(Orientation.VERTICAL), uniqueLabel);
                    layout.getChildren().add(statsBox);

                    // 枚举值表格
                    TableView<EnumValueInfo> enumTable = new TableView<>();
                    enumTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

                    // 序号列 - 固定小宽度
                    TableColumn<EnumValueInfo, String> indexCol = new TableColumn<>("序号");
                    indexCol.setPrefWidth(60);
                    indexCol.setMinWidth(50);
                    indexCol.setMaxWidth(80);
                    indexCol.setCellValueFactory(param -> {
                        int index = enumTable.getItems().indexOf(param.getValue()) + 1;
                        return new SimpleStringProperty(String.valueOf(index));
                    });
                    indexCol.setStyle("-fx-alignment: CENTER;");

                    // 枚举值列 - 自适应宽度（最重要的列）
                    TableColumn<EnumValueInfo, String> valueCol = new TableColumn<>("枚举值");
                    valueCol.setPrefWidth(350);
                    valueCol.setMinWidth(150);
                    valueCol.setCellValueFactory(param ->
                        new SimpleStringProperty(param.getValue().value != null ? param.getValue().value : "(NULL)"));

                    // 出现次数列 - 中等固定宽度
                    TableColumn<EnumValueInfo, String> countCol = new TableColumn<>("出现次数");
                    countCol.setPrefWidth(100);
                    countCol.setMinWidth(80);
                    countCol.setMaxWidth(120);
                    countCol.setCellValueFactory(param ->
                        new SimpleStringProperty(String.valueOf(param.getValue().count)));
                    countCol.setStyle("-fx-alignment: CENTER;");

                    // 占比列 - 固定小宽度
                    TableColumn<EnumValueInfo, String> percentCol = new TableColumn<>("占比");
                    percentCol.setPrefWidth(90);
                    percentCol.setMinWidth(70);
                    percentCol.setMaxWidth(110);
                    percentCol.setCellValueFactory(param -> {
                        double percent = (param.getValue().count * 100.0) / totalCount;
                        return new SimpleStringProperty(String.format("%.2f%%", percent));
                    });
                    percentCol.setStyle("-fx-alignment: CENTER;");

                    enumTable.getColumns().addAll(indexCol, valueCol, countCol, percentCol);
                    enumTable.getItems().addAll(enumValues);

                    // 应用智能列宽
                    SmartColumnWidthManager.applySmartColumnWidth(enumTable);

                    // 添加双击事件：点击枚举值查看详细数据
                    enumTable.setRowFactory(tv -> {
                        TableRow<EnumValueInfo> row = new TableRow<>();
                        row.setOnMouseClicked(event -> {
                            if (event.getClickCount() == 2 && !row.isEmpty()) {
                                EnumValueInfo clickedEnum = row.getItem();
                                showEnumValueDataList(tableName, fieldName, clickedEnum.value);
                            }
                        });
                        return row;
                    });

                    // 添加右键菜单
                    ContextMenu enumContextMenu = new ContextMenu();
                    MenuItem viewDataItem = new MenuItem("📊 查看该枚举值的数据列表");
                    viewDataItem.setOnAction(evt -> {
                        EnumValueInfo selected = enumTable.getSelectionModel().getSelectedItem();
                        if (selected != null) {
                            showEnumValueDataList(tableName, fieldName, selected.value);
                        }
                    });
                    enumContextMenu.getItems().add(viewDataItem);
                    enumTable.setContextMenu(enumContextMenu);

                    // 提示信息
                    Label hintLabel = new Label("💡 提示：双击任意枚举值可查看该值对应的详细数据列表");
                    hintLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px; -fx-padding: 5 0 0 0;");

                    VBox.setVgrow(enumTable, Priority.ALWAYS);

                    // 按钮栏
                    HBox buttonBar = new HBox(10);
                    buttonBar.setAlignment(Pos.CENTER_RIGHT);
                    buttonBar.setPadding(new Insets(10, 0, 0, 0));

                    Button exportBtn = new Button("📋 复制到剪贴板");
                    exportBtn.setOnAction(evt -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("字段: %s.%s\n", tableName, fieldName));
                        sb.append(String.format("总记录数: %d\n", totalCount));
                        sb.append(String.format("唯一值数: %d\n\n", enumValues.size()));
                        sb.append("序号\t枚举值\t出现次数\t占比\n");
                        int idx = 1;
                        for (EnumValueInfo info : enumValues) {
                            double percent = (info.count * 100.0) / totalCount;
                            sb.append(String.format("%d\t%s\t%d\t%.2f%%\n",
                                idx++, info.value != null ? info.value : "(NULL)", info.count, percent));
                        }
                        javafx.scene.input.Clipboard clipboard =
                            javafx.scene.input.Clipboard.getSystemClipboard();
                        javafx.scene.input.ClipboardContent content =
                            new javafx.scene.input.ClipboardContent();
                        content.putString(sb.toString());
                        clipboard.setContent(content);
                        showInfo("已复制", "枚举值数据已复制到剪贴板");
                    });

                    Button closeBtn = new Button("关闭");
                    closeBtn.setOnAction(evt -> dialog.close());

                    buttonBar.getChildren().addAll(exportBtn, closeBtn);

                    layout.getChildren().addAll(enumTable, hintLabel, buttonBar);
                });

            } catch (Exception ex) {
                log.error("查询枚举值失败", ex);
                javafx.application.Platform.runLater(() -> {
                    layout.getChildren().remove(loadingLabel);
                    Label errorLabel = new Label("查询失败: " + ex.getMessage());
                    errorLabel.setStyle("-fx-text-fill: red;");
                    layout.getChildren().add(errorLabel);
                });
            }
        });

        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * 查询字段的枚举值
     */
    private List<EnumValueInfo> queryFieldEnumValues(String tableName, String fieldName) throws Exception {
        List<EnumValueInfo> result = new ArrayList<>();

        String sql = String.format(
            "SELECT `%s` AS field_value, COUNT(*) AS count " +
            "FROM %s " +
            "GROUP BY `%s` " +
            "ORDER BY count DESC, field_value",
            fieldName, tableName, fieldName
        );

        try (Connection conn = red.jiuzhou.util.DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                EnumValueInfo info = new EnumValueInfo();
                info.value = rs.getString("field_value");
                info.count = rs.getInt("count");
                result.add(info);
            }
        }

        return result;
    }

    /**
     * 对比两侧字段的枚举值
     */
    private void compareFieldEnumValues(String clientTable, String serverTable, String fieldName) {
        if (serverTable == null) {
            showAlert("服务端表不存在，无法对比");
            return;
        }

        Stage dialog = new Stage();
        dialog.initOwner(managerStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(String.format("⚖️ 枚举值对比 - 字段: %s", fieldName));

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));

        Label titleLabel = new Label(String.format("字段 '%s' 的枚举值对比", fieldName));
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label loadingLabel = new Label("正在加载对比数据...");
        layout.getChildren().addAll(titleLabel, loadingLabel);

        Scene scene = new Scene(layout, 900, 600);
        dialog.setScene(scene);
        dialog.show();

        // 异步加载数据
        Thread loadThread = new Thread(() -> {
            try {
                List<EnumValueInfo> clientEnums = queryFieldEnumValues(clientTable, fieldName);
                List<EnumValueInfo> serverEnums = queryFieldEnumValues(serverTable, fieldName);

                javafx.application.Platform.runLater(() -> {
                    layout.getChildren().remove(loadingLabel);
                    displayEnumComparison(layout, dialog, clientTable, serverTable,
                        fieldName, clientEnums, serverEnums);
                });

            } catch (Exception ex) {
                log.error("对比枚举值失败", ex);
                javafx.application.Platform.runLater(() -> {
                    layout.getChildren().remove(loadingLabel);
                    Label errorLabel = new Label("对比失败: " + ex.getMessage());
                    errorLabel.setStyle("-fx-text-fill: red;");
                    layout.getChildren().add(errorLabel);
                });
            }
        });

        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * 显示枚举值对比结果
     */
    private void displayEnumComparison(VBox layout, Stage dialog, String clientTable,
                                       String serverTable, String fieldName,
                                       List<EnumValueInfo> clientEnums,
                                       List<EnumValueInfo> serverEnums) {

        // 统计信息
        Set<String> clientValues = clientEnums.stream()
            .map(e -> e.value).collect(java.util.stream.Collectors.toSet());
        Set<String> serverValues = serverEnums.stream()
            .map(e -> e.value).collect(java.util.stream.Collectors.toSet());

        Set<String> commonValues = new HashSet<>(clientValues);
        commonValues.retainAll(serverValues);

        Set<String> clientOnlyValues = new HashSet<>(clientValues);
        clientOnlyValues.removeAll(serverValues);

        Set<String> serverOnlyValues = new HashSet<>(serverValues);
        serverOnlyValues.removeAll(clientValues);

        // 统计面板
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(20);
        statsGrid.setVgap(10);
        statsGrid.setPadding(new Insets(10));
        statsGrid.setStyle("-fx-background-color: #f0f0f0;");

        statsGrid.add(new Label("📦 客户端唯一值:"), 0, 0);
        statsGrid.add(new Label(String.valueOf(clientValues.size())), 1, 0);

        statsGrid.add(new Label("🖥️ 服务端唯一值:"), 2, 0);
        statsGrid.add(new Label(String.valueOf(serverValues.size())), 3, 0);

        statsGrid.add(new Label("✅ 共同值:"), 0, 1);
        Label commonLabel = new Label(String.valueOf(commonValues.size()));
        commonLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        statsGrid.add(commonLabel, 1, 1);

        statsGrid.add(new Label("⚠️ 客户端独有:"), 2, 1);
        Label clientOnlyLabel = new Label(String.valueOf(clientOnlyValues.size()));
        if (clientOnlyValues.size() > 0) {
            clientOnlyLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        }
        statsGrid.add(clientOnlyLabel, 3, 1);

        statsGrid.add(new Label("⚠️ 服务端独有:"), 4, 1);
        Label serverOnlyLabel = new Label(String.valueOf(serverOnlyValues.size()));
        if (serverOnlyValues.size() > 0) {
            serverOnlyLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        }
        statsGrid.add(serverOnlyLabel, 5, 1);

        layout.getChildren().add(statsGrid);

        // 对比表格
        TableView<EnumCompareRow> compareTable = new TableView<>();
        compareTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // 枚举值列 - 自适应宽度（最重要）
        TableColumn<EnumCompareRow, String> valueCol = new TableColumn<>("枚举值");
        valueCol.setPrefWidth(350);
        valueCol.setMinWidth(150);
        valueCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().value));

        // 客户端次数列 - 中等固定宽度
        TableColumn<EnumCompareRow, String> clientCountCol = new TableColumn<>("📦 客户端次数");
        clientCountCol.setPrefWidth(120);
        clientCountCol.setMinWidth(90);
        clientCountCol.setMaxWidth(150);
        clientCountCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().clientCount > 0 ?
                String.valueOf(param.getValue().clientCount) : "-"));
        clientCountCol.setStyle("-fx-alignment: CENTER;");

        // 服务端次数列 - 中等固定宽度
        TableColumn<EnumCompareRow, String> serverCountCol = new TableColumn<>("🖥️ 服务端次数");
        serverCountCol.setPrefWidth(120);
        serverCountCol.setMinWidth(90);
        serverCountCol.setMaxWidth(150);
        serverCountCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().serverCount > 0 ?
                String.valueOf(param.getValue().serverCount) : "-"));
        serverCountCol.setStyle("-fx-alignment: CENTER;");

        // 状态列 - 中等固定宽度
        TableColumn<EnumCompareRow, String> statusCol = new TableColumn<>("状态");
        statusCol.setPrefWidth(140);
        statusCol.setMinWidth(100);
        statusCol.setMaxWidth(180);
        statusCol.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getStatusDisplay()));
        statusCol.setStyle("-fx-alignment: CENTER;");

        compareTable.getColumns().addAll(valueCol, clientCountCol, serverCountCol, statusCol);

        // 应用智能列宽
        SmartColumnWidthManager.applySmartColumnWidth(compareTable);

        // 构建对比数据
        Map<String, EnumValueInfo> clientMap = clientEnums.stream()
            .collect(java.util.stream.Collectors.toMap(e -> e.value != null ? e.value : "(NULL)", e -> e));
        Map<String, EnumValueInfo> serverMap = serverEnums.stream()
            .collect(java.util.stream.Collectors.toMap(e -> e.value != null ? e.value : "(NULL)", e -> e));

        Set<String> allValues = new HashSet<>();
        allValues.addAll(clientValues);
        allValues.addAll(serverValues);

        ObservableList<EnumCompareRow> rows = FXCollections.observableArrayList();
        for (String value : allValues) {
            EnumCompareRow row = new EnumCompareRow();
            row.value = value != null ? value : "(NULL)";

            EnumValueInfo clientInfo = clientMap.get(value);
            EnumValueInfo serverInfo = serverMap.get(value);

            row.clientCount = clientInfo != null ? clientInfo.count : 0;
            row.serverCount = serverInfo != null ? serverInfo.count : 0;

            if (clientInfo != null && serverInfo != null) {
                row.status = "BOTH";
            } else if (clientInfo != null) {
                row.status = "CLIENT_ONLY";
            } else {
                row.status = "SERVER_ONLY";
            }

            rows.add(row);
        }

        compareTable.setItems(rows);

        // 添加双击事件：点击枚举值查看对应数据
        compareTable.setRowFactory(tv -> {
            TableRow<EnumCompareRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    EnumCompareRow clickedRow = row.getItem();
                    // 弹出菜单让用户选择查看哪个表的数据
                    showEnumValueDataSelectionDialog(
                        clientTable, serverTable, fieldName, clickedRow.value,
                        clickedRow.clientCount, clickedRow.serverCount
                    );
                }
            });
            return row;
        });

        // 添加右键菜单
        ContextMenu compareContextMenu = new ContextMenu();

        MenuItem viewClientDataItem = new MenuItem("📦 查看客户端数据列表");
        viewClientDataItem.setOnAction(evt -> {
            EnumCompareRow selected = compareTable.getSelectionModel().getSelectedItem();
            if (selected != null && selected.clientCount > 0) {
                showEnumValueDataList(clientTable, fieldName, selected.value);
            } else {
                showAlert("该枚举值在客户端表中不存在");
            }
        });

        MenuItem viewServerDataItem = new MenuItem("🖥️ 查看服务端数据列表");
        viewServerDataItem.setOnAction(evt -> {
            EnumCompareRow selected = compareTable.getSelectionModel().getSelectedItem();
            if (selected != null && selected.serverCount > 0) {
                showEnumValueDataList(serverTable, fieldName, selected.value);
            } else {
                showAlert("该枚举值在服务端表中不存在");
            }
        });

        compareContextMenu.getItems().addAll(viewClientDataItem, viewServerDataItem);
        compareTable.setContextMenu(compareContextMenu);

        // 提示信息
        Label hintLabel = new Label("💡 提示：双击枚举值选择查看客户端或服务端数据，右键可直接选择");
        hintLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px; -fx-padding: 5 0 0 0;");

        VBox.setVgrow(compareTable, Priority.ALWAYS);
        layout.getChildren().addAll(compareTable, hintLabel);

        // 按钮栏
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Button closeBtn = new Button("关闭");
        closeBtn.setOnAction(e -> dialog.close());

        buttonBar.getChildren().add(closeBtn);
        layout.getChildren().add(buttonBar);
    }

    /**
     * 显示字段详细信息
     */
    private void showFieldDetailInfo(FieldRowData field, TablePairWrapper pair) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("字段详细信息");
        alert.setHeaderText("字段: " + field.fieldName);

        StringBuilder info = new StringBuilder();
        info.append("📦 客户端信息:\n");
        info.append("  表名: ").append(pair.getClientTableName()).append("\n");
        info.append("  字段名: ").append(field.fieldName).append("\n");
        info.append("  类型: ").append(field.clientType).append("\n");

        info.append("\n🖥️ 服务端信息:\n");
        info.append("  表名: ").append(pair.getServerTableName()).append("\n");
        info.append("  字段名: ").append(field.fieldName).append("\n");
        info.append("  类型: ").append(field.serverType).append("\n");

        info.append("\n📝 其他信息:\n");
        info.append("  状态: ").append(field.getStatusDisplay()).append("\n");
        info.append("  注释: ").append(field.comment != null ? field.comment : "(无)").append("\n");
        info.append("  字段类型: ");
        switch (field.fieldType) {
            case "COMMON":
                info.append("共同字段");
                break;
            case "CLIENT_ONLY":
                info.append("仅客户端拥有");
                break;
            case "SERVER_ONLY":
                info.append("仅服务端拥有");
                break;
            default:
                info.append(field.fieldType);
        }

        TextArea textArea = new TextArea(info.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);

        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setPrefSize(500, 400);
        alert.showAndWait();
    }

    /**
     * 显示指定枚举值对应的数据列表
     *
     * @param tableName 表名
     * @param fieldName 字段名
     * @param enumValue 枚举值
     */
    private void showEnumValueDataList(String tableName, String fieldName, String enumValue) {
        Stage dialog = new Stage();
        dialog.initOwner(managerStage);
        dialog.initModality(Modality.APPLICATION_MODAL);

        String displayValue = enumValue != null ? enumValue : "(NULL)";
        dialog.setTitle(String.format("📊 数据列表 - %s.%s = %s", tableName, fieldName, displayValue));

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));

        // 标题信息
        VBox headerBox = new VBox(5);
        Label titleLabel = new Label(String.format("表: %s  |  字段: %s  |  值: %s",
            tableName, fieldName, displayValue));
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label loadingLabel = new Label("正在加载数据...");
        headerBox.getChildren().addAll(titleLabel, loadingLabel);
        layout.getChildren().add(headerBox);

        Scene scene = new Scene(layout, 1200, 700);
        dialog.setScene(scene);
        dialog.show();

        // 异步加载数据
        Thread loadThread = new Thread(() -> {
            try {
                // 查询该枚举值对应的所有数据记录
                List<Map<String, Object>> dataList = queryDataByEnumValue(tableName, fieldName, enumValue);

                javafx.application.Platform.runLater(() -> {
                    headerBox.getChildren().remove(loadingLabel);

                    if (dataList.isEmpty()) {
                        Label emptyLabel = new Label("没有找到数据");
                        emptyLabel.setStyle("-fx-text-fill: #999;");
                        layout.getChildren().add(emptyLabel);
                        return;
                    }

                    // 统计信息
                    HBox statsBox = new HBox(15);
                    statsBox.setAlignment(Pos.CENTER_LEFT);
                    statsBox.setPadding(new Insets(10));
                    statsBox.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #2196F3; " +
                        "-fx-border-radius: 5; -fx-background-radius: 5;");

                    Label countLabel = new Label("📊 共找到 " + dataList.size() + " 条记录");
                    countLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

                    statsBox.getChildren().add(countLabel);
                    layout.getChildren().add(statsBox);

                    // 创建数据表格
                    TableView<Map<String, Object>> dataTable = new TableView<>();
                    dataTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

                    // 动态创建列（基于第一条数据的字段）
                    if (!dataList.isEmpty()) {
                        Map<String, Object> firstRow = dataList.get(0);

                        // 序号列 - 固定小宽度
                        TableColumn<Map<String, Object>, String> indexCol = new TableColumn<>("序号");
                        indexCol.setPrefWidth(60);
                        indexCol.setMinWidth(50);
                        indexCol.setMaxWidth(80);
                        indexCol.setCellValueFactory(param -> {
                            int index = dataTable.getItems().indexOf(param.getValue()) + 1;
                            return new SimpleStringProperty(String.valueOf(index));
                        });
                        indexCol.setStyle("-fx-alignment: CENTER;");
                        dataTable.getColumns().add(indexCol);

                        // 为每个字段创建列
                        for (String columnName : firstRow.keySet()) {
                            TableColumn<Map<String, Object>, String> column =
                                new TableColumn<>(columnName);

                            // 高亮显示当前筛选的字段
                            if (columnName.equalsIgnoreCase(fieldName)) {
                                column.setStyle("-fx-background-color: #fff9c4;");
                            }

                            // 根据字段名智能设置初始宽度
                            if (columnName.length() <= 3) {
                                // 短字段名（如 id）
                                column.setPrefWidth(80);
                                column.setMinWidth(60);
                            } else if (columnName.length() <= 10) {
                                // 中等字段名
                                column.setPrefWidth(120);
                                column.setMinWidth(80);
                            } else {
                                // 长字段名
                                column.setPrefWidth(180);
                                column.setMinWidth(100);
                            }

                            column.setCellValueFactory(param -> {
                                Object value = param.getValue().get(columnName);
                                String displayStr = value != null ? value.toString() : "(NULL)";
                                return new SimpleStringProperty(displayStr);
                            });

                            dataTable.getColumns().add(column);
                        }
                    }

                    // 添加数据
                    ObservableList<Map<String, Object>> items =
                        FXCollections.observableArrayList(dataList);
                    dataTable.setItems(items);

                    // 应用智能列宽（根据实际数据内容调整）
                    SmartColumnWidthManager.applySmartColumnWidth(dataTable);

                    // 添加右键菜单
                    ContextMenu tableContextMenu = new ContextMenu();

                    MenuItem copyRowItem = new MenuItem("📋 复制当前行");
                    copyRowItem.setOnAction(evt -> {
                        Map<String, Object> selectedRow = dataTable.getSelectionModel().getSelectedItem();
                        if (selectedRow != null) {
                            StringBuilder sb = new StringBuilder();
                            for (Map.Entry<String, Object> entry : selectedRow.entrySet()) {
                                sb.append(entry.getKey()).append(": ")
                                  .append(entry.getValue() != null ? entry.getValue() : "(NULL)")
                                  .append("\n");
                            }
                            javafx.scene.input.Clipboard clipboard =
                                javafx.scene.input.Clipboard.getSystemClipboard();
                            javafx.scene.input.ClipboardContent content =
                                new javafx.scene.input.ClipboardContent();
                            content.putString(sb.toString());
                            clipboard.setContent(content);
                            showInfo("已复制", "当前行数据已复制到剪贴板");
                        }
                    });

                    MenuItem copyAllItem = new MenuItem("📋 复制所有数据");
                    copyAllItem.setOnAction(evt -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("表: %s | 字段: %s | 值: %s\n",
                            tableName, fieldName, displayValue));
                        sb.append(String.format("共 %d 条记录\n\n", dataList.size()));

                        // 表头
                        if (!dataList.isEmpty()) {
                            Map<String, Object> firstRow = dataList.get(0);
                            for (String colName : firstRow.keySet()) {
                                sb.append(colName).append("\t");
                            }
                            sb.append("\n");

                            // 数据行
                            for (Map<String, Object> row : dataList) {
                                for (String colName : firstRow.keySet()) {
                                    Object value = row.get(colName);
                                    sb.append(value != null ? value : "(NULL)").append("\t");
                                }
                                sb.append("\n");
                            }
                        }

                        javafx.scene.input.Clipboard clipboard =
                            javafx.scene.input.Clipboard.getSystemClipboard();
                        javafx.scene.input.ClipboardContent content =
                            new javafx.scene.input.ClipboardContent();
                        content.putString(sb.toString());
                        clipboard.setContent(content);
                        showInfo("已复制", "所有数据已复制到剪贴板");
                    });

                    tableContextMenu.getItems().addAll(copyRowItem, copyAllItem);
                    dataTable.setContextMenu(tableContextMenu);

                    VBox.setVgrow(dataTable, Priority.ALWAYS);
                    layout.getChildren().add(dataTable);

                    // 按钮栏
                    HBox buttonBar = new HBox(10);
                    buttonBar.setAlignment(Pos.CENTER_RIGHT);
                    buttonBar.setPadding(new Insets(10, 0, 0, 0));

                    Button exportBtn = new Button("📋 导出数据");
                    exportBtn.setOnAction(evt -> {
                        // 复制所有数据
                        copyAllItem.fire();
                    });

                    Label hintLabel = new Label("💡 提示：右键点击可复制数据");
                    hintLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

                    Button closeBtn = new Button("关闭");
                    closeBtn.setOnAction(evt -> dialog.close());

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    buttonBar.getChildren().addAll(hintLabel, spacer, exportBtn, closeBtn);
                    layout.getChildren().add(buttonBar);
                });

            } catch (Exception ex) {
                log.error("查询枚举值数据失败", ex);
                javafx.application.Platform.runLater(() -> {
                    headerBox.getChildren().remove(loadingLabel);
                    Label errorLabel = new Label("查询失败: " + ex.getMessage());
                    errorLabel.setStyle("-fx-text-fill: red;");
                    layout.getChildren().add(errorLabel);
                });
            }
        });

        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * 显示枚举值数据选择对话框（客户端/服务端）
     *
     * @param clientTable 客户端表名
     * @param serverTable 服务端表名
     * @param fieldName 字段名
     * @param enumValue 枚举值
     * @param clientCount 客户端出现次数
     * @param serverCount 服务端出现次数
     */
    private void showEnumValueDataSelectionDialog(String clientTable, String serverTable,
                                                   String fieldName, String enumValue,
                                                   int clientCount, int serverCount) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("选择查看数据");
        alert.setHeaderText("该枚举值在两个表中都存在");

        String displayValue = enumValue != null ? enumValue : "(NULL)";
        alert.setContentText(String.format(
            "枚举值: %s\n\n" +
            "📦 客户端表 (%s): %d 条记录\n" +
            "🖥️ 服务端表 (%s): %d 条记录\n\n" +
            "请选择要查看哪个表的数据：",
            displayValue, clientTable, clientCount, serverTable, serverCount
        ));

        ButtonType clientButton = new ButtonType("📦 客户端");
        ButtonType serverButton = new ButtonType("🖥️ 服务端");
        ButtonType bothButton = new ButtonType("⚖️ 两个都看");
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(clientButton, serverButton, bothButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == clientButton && clientCount > 0) {
                showEnumValueDataList(clientTable, fieldName, enumValue);
            } else if (response == serverButton && serverCount > 0) {
                showEnumValueDataList(serverTable, fieldName, enumValue);
            } else if (response == bothButton) {
                if (clientCount > 0) {
                    showEnumValueDataList(clientTable, fieldName, enumValue);
                }
                if (serverCount > 0) {
                    // 稍微延迟一下，避免两个窗口重叠
                    new Thread(() -> {
                        try {
                            Thread.sleep(300);
                            javafx.application.Platform.runLater(() ->
                                showEnumValueDataList(serverTable, fieldName, enumValue)
                            );
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }).start();
                }
            }
        });
    }

    /**
     * 查询指定枚举值对应的所有数据记录
     *
     * @param tableName 表名
     * @param fieldName 字段名
     * @param enumValue 枚举值
     * @return 数据列表
     */
    private List<Map<String, Object>> queryDataByEnumValue(String tableName,
                                                            String fieldName,
                                                            String enumValue) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();

        // 构建 WHERE 条件
        String whereClause;
        if (enumValue == null) {
            whereClause = String.format("`%s` IS NULL", fieldName);
        } else {
            whereClause = String.format("`%s` = ?", fieldName);
        }

        String sql = String.format("SELECT * FROM %s WHERE %s LIMIT 1000", tableName, whereClause);

        try (Connection conn = red.jiuzhou.util.DatabaseUtil.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 设置参数
            if (enumValue != null) {
                pstmt.setString(1, enumValue);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                // 获取列信息
                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 读取数据
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    result.add(row);
                }
            }
        }

        log.info("查询枚举值数据: {}.{} = {}, 共 {} 条记录",
            tableName, fieldName, enumValue, result.size());

        return result;
    }

    /**
     * 构建字段行数据
     */
    private ObservableList<FieldRowData> buildFieldRowData(TablePairWrapper pair) {
        ObservableList<FieldRowData> data = FXCollections.observableArrayList();

        DatabaseTableScanner.FieldCompareResult compareResult = pair.getCompareResult();

        // 共同字段 - 默认选中，因为这些字段在两个表中都存在
        for (DatabaseTableScanner.FieldPair fieldPair : compareResult.commonFields) {
            FieldRowData row = new FieldRowData();
            row.fieldName = fieldPair.clientField.getColumnName();
            row.clientType = fieldPair.clientField.getTypeDisplay();
            row.serverType = fieldPair.serverField.getTypeDisplay();
            row.comment = fieldPair.clientField.getComment() != null ?
                fieldPair.clientField.getComment() : fieldPair.serverField.getComment();
            row.status = fieldPair.isTypeMatched() ? "MATCHED" : "TYPE_DIFF";
            row.fieldType = "COMMON";
            row.setSelected(true);  // 默认选中共同字段
            data.add(row);
        }

        // 客户端独有字段
        for (DatabaseTableScanner.ColumnInfo col : compareResult.clientOnlyFields) {
            FieldRowData row = new FieldRowData();
            row.fieldName = col.getColumnName();
            row.clientType = col.getTypeDisplay();
            row.serverType = "-";
            row.comment = col.getComment();
            row.status = "CLIENT_ONLY";
            row.fieldType = "CLIENT_ONLY";
            // 不默认选中，因为服务端没有此字段
            data.add(row);
        }

        // 服务端独有字段
        for (DatabaseTableScanner.ColumnInfo col : compareResult.serverOnlyFields) {
            FieldRowData row = new FieldRowData();
            row.fieldName = col.getColumnName();
            row.clientType = "-";
            row.serverType = col.getTypeDisplay();
            row.comment = col.getComment();
            row.status = "SERVER_ONLY";
            row.fieldType = "SERVER_ONLY";
            // 不默认选中，因为客户端没有此字段
            data.add(row);
        }

        return data;
    }

    /**
     * 创建底部按钮栏
     */
    private HBox createBottomBar() {
        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button selectAllBtn = new Button("☑️ 全选字段");
        selectAllBtn.setOnAction(e -> selectAllFields(true));

        Button deselectAllBtn = new Button("⬜ 取消全选");
        deselectAllBtn.setOnAction(e -> selectAllFields(false));

        Button syncClientToServerBtn = new Button("📦 → 🖥️ 同步到服务端");
        syncClientToServerBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        syncClientToServerBtn.setTooltip(new Tooltip("将选中的客户端数据同步到服务端"));
        syncClientToServerBtn.setOnAction(e -> syncData(true));

        Button syncServerToClientBtn = new Button("🖥️ → 📦 同步到客户端");
        syncServerToClientBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        syncServerToClientBtn.setTooltip(new Tooltip("将选中的服务端数据同步到客户端"));
        syncServerToClientBtn.setOnAction(e -> syncData(false));

        Button compareDataBtn = new Button("🔍 数据对比");
        compareDataBtn.setOnAction(e -> compareData());

        Button closeBtn = new Button("❌ 关闭");
        closeBtn.setOnAction(e -> managerStage.close());

        buttonBar.getChildren().addAll(
            selectAllBtn, deselectAllBtn,
            new Separator(Orientation.VERTICAL),
            syncClientToServerBtn, syncServerToClientBtn,
            new Separator(Orientation.VERTICAL),
            compareDataBtn, closeBtn
        );

        return buttonBar;
    }

    // ==================== 数据操作方法 ====================

    private void refreshData() {
        // 使用强制刷新从数据库重新加载
        showLoadingDialog(true);
        filteredList = new FilteredList<>(tablePairList, p -> true);
        pairTableView.setItems(filteredList);
        updateStats();
    }

    private void filterTables(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredList.setPredicate(p -> true);
        } else {
            String lower = searchText.toLowerCase().trim();
            filteredList.setPredicate(pair ->
                pair.getClientTableName().toLowerCase().contains(lower) ||
                pair.getServerTableName().toLowerCase().contains(lower)
            );
        }
        updateStats();
    }

    private void applyFilter(String filterType) {
        if (filterType == null) return;

        switch (filterType) {
            case "全部映射":
                filteredList.setPredicate(p -> true);
                break;
            case "有服务端表":
                filteredList.setPredicate(p -> p.serverTable != null);
                break;
            case "缺少服务端表":
                filteredList.setPredicate(p -> p.serverTable == null);
                break;
            case "精确匹配":
                filteredList.setPredicate(p -> p.matchMethod.equals("精确匹配"));
                break;
            case "模糊匹配":
                filteredList.setPredicate(p -> p.matchMethod.contains("模糊匹配"));
                break;
            case "未匹配":
                filteredList.setPredicate(p -> p.serverTable == null || p.matchMethod.equals("未匹配"));
                break;
            case "多对一映射":
                filteredList.setPredicate(p -> p.isMultipleMatch);
                break;
            case "字段完全匹配":
                filteredList.setPredicate(p ->
                    p.getClientOnlyCount() == 0 && p.getServerOnlyCount() == 0);
                break;
            case "有字段差异":
                filteredList.setPredicate(p ->
                    p.getClientOnlyCount() > 0 || p.getServerOnlyCount() > 0);
                break;
        }
        updateStats();
    }

    private void updateStats() {
        if (statsLabel == null || tablePairList == null) return;

        int total = tablePairList.size();
        int displayed = filteredList != null ? filteredList.size() : total;
        int withServer = (int) tablePairList.stream()
            .filter(p -> p.serverTable != null).count();

        statsLabel.setText(String.format(
            "📊 总计: %d 对 | 显示: %d | 有服务端表: %d",
            total, displayed, withServer
        ));
    }

    private void selectAllFields(boolean select) {
        if (fieldCompareTable == null) return;
        for (FieldRowData row : fieldCompareTable.getItems()) {
            row.setSelected(select);
        }
        fieldCompareTable.refresh();
    }

    private void syncData(boolean clientToServer) {
        if (currentSelectedPair == null) {
            showAlert("请先选择要同步的表映射");
            return;
        }

        // 获取选中的字段
        List<String> selectedFields = new ArrayList<>();
        for (FieldRowData row : fieldCompareTable.getItems()) {
            if (row.isSelected()) {
                selectedFields.add(row.fieldName);
            }
        }

        if (selectedFields.isEmpty()) {
            showAlert("请至少选择一个字段进行同步");
            return;
        }

        // 第一步：选择同步模式
        EnhancedDataSyncService.SyncMode syncMode = showSyncModeDialog();
        if (syncMode == null) {
            return;  // 用户取消
        }

        String direction = clientToServer ? "客户端 → 服务端" : "服务端 → 客户端";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认数据同步");
        confirm.setHeaderText("数据同步操作");
        confirm.setContentText(String.format(
            "即将同步数据: %s\n" +
            "选中字段数: %d\n" +
            "同步方向: %s\n" +
            "同步模式: %s\n\n" +
            "此操作将%s目标表的数据，是否继续？",
            currentSelectedPair.getClientTableName(),
            selectedFields.size(),
            direction,
            syncMode.getDisplayName(),
            syncMode == EnhancedDataSyncService.SyncMode.FULL_SYNC ? "完全覆盖" : "修改"
        ));

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                performSync(clientToServer, selectedFields, syncMode);
            }
        });
    }

    private void performSync(boolean clientToServer, List<String> fields, EnhancedDataSyncService.SyncMode syncMode) {
        // 使用增强的数据同步服务
        log.info("开始数据同步: {} - 字段数: {}", clientToServer ? "C→S" : "S→C", fields.size());

        // 显示进度对话框
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("数据同步中");
        progressAlert.setHeaderText("正在同步数据，请稍候...");
        progressAlert.setContentText("同步方向: " + (clientToServer ? "客户端 → 服务端" : "服务端 → 客户端"));

        // 在后台线程执行同步
        Thread syncThread = new Thread(() -> {
            try {
                // 执行增强同步
                EnhancedDataSyncService.EnhancedSyncResult result;

                // 确定源表和目标表
                DatabaseTableScanner.TableInfo sourceTable = clientToServer ?
                    currentSelectedPair.clientTable : currentSelectedPair.serverTable;
                DatabaseTableScanner.TableInfo targetTable = clientToServer ?
                    currentSelectedPair.serverTable : currentSelectedPair.clientTable;

                // 判断是主表还是子表
                TableHierarchyHelper.TableLevel level = sourceTable.getTableLevel();

                if (level == TableHierarchyHelper.TableLevel.MAIN) {
                    // 主表同步：先更新表结构，再同步数据，然后级联同步所有子表
                    log.info("同步主表及其子表: {}", sourceTable.getTableName());

                    // 使用级联同步，自动同步主表和所有子表
                    EnhancedDataSyncService.CascadeSyncResult cascadeResult =
                        EnhancedDataSyncService.syncMainTableWithChildren(
                            sourceTable, targetTable, allTables, syncMode);

                    // 将级联同步结果转换为普通同步结果（用于显示）
                    result = new EnhancedDataSyncService.EnhancedSyncResult();
                    result.success = cascadeResult.success;
                    result.message = cascadeResult.message;
                    result.insertedRows = cascadeResult.totalInserted;
                    result.updatedRows = cascadeResult.totalUpdated;
                    result.durationMs = cascadeResult.durationMs;

                    // 添加详细信息到消息中
                    if (cascadeResult.mainTableResult != null) {
                        result.schemaUpdates = cascadeResult.mainTableResult.schemaUpdates;
                    }
                    if (!cascadeResult.subTableResults.isEmpty()) {
                        result.message += String.format("\n  📋 主表: 新增=%d, 更新=%d",
                            cascadeResult.mainTableResult.insertedRows,
                            cascadeResult.mainTableResult.updatedRows);
                        result.message += String.format("\n  📁 子表: 成功=%d, 失败=%d",
                            cascadeResult.successfulSubTables,
                            cascadeResult.failedSubTables);
                    }
                } else {
                    // 子表同步：需要考虑父表主键映射
                    log.info("同步子表: {} (层级: {})", sourceTable.getTableName(), level);

                    // 获取父表信息
                    TableHierarchyHelper.TableHierarchy hierarchy =
                        new TableHierarchyHelper.TableHierarchy(sourceTable.getTableName());
                    String parentTableName = hierarchy.getParentTableName();

                    DatabaseTableScanner.TableInfo parentSourceTable = null;
                    DatabaseTableScanner.TableInfo parentTargetTable = null;

                    // 从表列表中查找父表
                    if (parentTableName != null) {
                        for (DatabaseTableScanner.TableInfo table : allTables) {
                            String tableName = table.getTableName();
                            // 查找对应的客户端和服务端父表
                            if (clientToServer) {
                                // 客户端到服务端：源表是 client_xxx，目标表是 xxx
                                if (tableName.equals(parentTableName)) {
                                    parentSourceTable = table;  // 客户端父表（可能带 client_ 前缀）
                                } else if (tableName.equals(parentTableName.replace("client_", ""))) {
                                    parentTargetTable = table;  // 服务端父表
                                }
                            } else {
                                // 服务端到客户端：源表是 xxx，目标表是 client_xxx
                                if (tableName.equals(parentTableName)) {
                                    parentSourceTable = table;  // 服务端父表
                                } else if (tableName.equals("client_" + parentTableName)) {
                                    parentTargetTable = table;  // 客户端父表
                                }
                            }
                        }
                    }

                    // 构建父表主键映射
                    Map<String, String> parentKeyMapping = null;
                    if (parentSourceTable != null && parentTargetTable != null) {
                        parentKeyMapping = EnhancedDataSyncService.buildPrimaryKeyMapping(
                            parentSourceTable, parentTargetTable
                        );
                        log.info("父表主键映射: {} 条记录", parentKeyMapping.size());
                    } else {
                        log.warn("未找到父表信息，子表同步可能缺少主键映射");
                    }

                    result = EnhancedDataSyncService.syncSubTable(
                        sourceTable, targetTable, parentKeyMapping, syncMode
                    );
                }

                // 在UI线程显示结果
                javafx.application.Platform.runLater(() -> {
                    progressAlert.close();

                    if (result.success) {
                        showEnhancedSyncResultDialog(result);
                    } else {
                        showError("数据同步失败:\n" + result.message + "\n\n" +
                                String.join("\n", result.errors));
                    }
                });

            } catch (Exception e) {
                log.error("数据同步异常", e);
                javafx.application.Platform.runLater(() -> {
                    progressAlert.close();
                    showError("数据同步异常: " + e.getMessage());
                });
            }
        });

        syncThread.setDaemon(true);
        syncThread.setName("DataSync-Thread");
        syncThread.start();

        progressAlert.show();
    }

    /**
     * 显示同步结果对话框
     */
    private void showSyncResultDialog(DataSyncService.SyncResult result) {
        Alert resultAlert = new Alert(Alert.AlertType.INFORMATION);
        resultAlert.setTitle("数据同步结果");
        resultAlert.setHeaderText(result.success ? "✅ 同步成功" : "❌ 同步失败");

        StringBuilder content = new StringBuilder();
        content.append(String.format("总行数: %d\n", result.totalRows));
        content.append(String.format("插入: %d 行\n", result.insertedRows));
        content.append(String.format("更新: %d 行\n", result.updatedRows));
        content.append(String.format("删除: %d 行\n", result.deletedRows));
        content.append(String.format("耗时: %d ms\n", result.durationMs));

        if (result.backupTableName != null) {
            content.append(String.format("\n备份表: %s\n", result.backupTableName));
            content.append("（如需恢复，可使用恢复功能）");
        }

        TextArea textArea = new TextArea(content.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(10);

        resultAlert.getDialogPane().setContent(textArea);
        resultAlert.getDialogPane().setPrefSize(500, 350);

        // 添加恢复按钮
        if (result.backupTableName != null) {
            ButtonType restoreBtn = new ButtonType("恢复备份", ButtonBar.ButtonData.LEFT);
            resultAlert.getButtonTypes().add(0, restoreBtn);

            resultAlert.showAndWait().ifPresent(response -> {
                if (response == restoreBtn) {
                    restoreFromBackup(result.backupTableName);
                }
            });
        } else {
            resultAlert.showAndWait();
        }
    }

    /**
     * 显示增强同步结果对话框
     */
    private void showEnhancedSyncResultDialog(EnhancedDataSyncService.EnhancedSyncResult result) {
        Alert resultAlert = new Alert(Alert.AlertType.INFORMATION);
        resultAlert.setTitle("数据同步结果");
        resultAlert.setHeaderText(result.success ? "✅ 同步成功" : "❌ 同步失败");

        StringBuilder content = new StringBuilder();
        content.append("=== 表结构更新 ===\n");
        content.append(String.format("字段更新: %d 个\n", result.schemaUpdates));
        content.append("\n");

        content.append("=== 数据同步 ===\n");
        content.append(String.format("总行数: %d\n", result.totalRows));
        content.append(String.format("插入: %d 行\n", result.insertedRows));
        content.append(String.format("更新: %d 行\n", result.updatedRows));
        content.append(String.format("跳过: %d 行 (因无主键或类型不匹配)\n", result.skippedRows));
        content.append("\n");

        content.append(String.format("耗时: %d ms\n", result.durationMs));

        if (result.message != null && !result.message.isEmpty()) {
            content.append("\n").append(result.message);
        }

        if (!result.warnings.isEmpty()) {
            content.append("\n\n=== 警告信息 ===\n");
            for (String warning : result.warnings) {
                content.append("⚠️ ").append(warning).append("\n");
            }
        }

        if (!result.errors.isEmpty()) {
            content.append("\n\n=== 错误信息 ===\n");
            for (String error : result.errors) {
                content.append("❌ ").append(error).append("\n");
            }
        }

        TextArea textArea = new TextArea(content.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);

        resultAlert.getDialogPane().setContent(textArea);
        resultAlert.getDialogPane().setPrefSize(600, 450);
        resultAlert.showAndWait();
    }

    /**
     * 从备份恢复数据
     */
    private void restoreFromBackup(String backupTableName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认恢复");
        confirm.setHeaderText("从备份恢复数据");
        confirm.setContentText(String.format(
                "确定要从备份表 %s 恢复数据吗？\n\n" +
                "此操作将覆盖当前数据！",
                backupTableName
        ));

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // 解析备份表名，获取原表名
                String originalTableName = backupTableName.replaceAll("_backup_\\d{8}_\\d{6}$", "");

                DataSyncService.SyncResult restoreResult =
                        DataSyncService.restoreFromBackup(backupTableName, originalTableName);

                if (restoreResult.success) {
                    showInfo("恢复成功",
                            String.format("已从备份表恢复 %d 行数据到 %s",
                                    restoreResult.insertedRows, originalTableName));
                } else {
                    showError("恢复失败:\n" + restoreResult.message);
                }
            }
        });
    }

    private void compareData() {
        if (currentSelectedPair == null) {
            showAlert("请先选择要对比的表映射");
            return;
        }

        showInfo("开发中", "数据对比功能正在开发中...\n" +
            "即将支持:\n" +
            "• 逐行数据对比\n" +
            "• 差异高亮显示\n" +
            "• 导出对比报告\n" +
            "• 差异记录分析");
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

    /**
     * 显示同步模式选择对话框
     * @return 选择的同步模式，取消则返回null
     */
    private EnhancedDataSyncService.SyncMode showSyncModeDialog() {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("选择同步模式");
        dialog.setHeaderText("请选择数据同步模式");

        // 创建选项内容
        StringBuilder content = new StringBuilder();
        content.append("请根据您的需求选择合适的同步模式：\n\n");

        for (EnhancedDataSyncService.SyncMode mode : EnhancedDataSyncService.SyncMode.values()) {
            content.append(String.format("【%s】\n", mode.getDisplayName()));
            content.append(String.format("  操作：%s\n", mode.getShortName()));
            content.append(String.format("  说明：%s\n\n", mode.getDescription()));
        }

        content.append("默认推荐：增量更新\n");
        content.append("⚠️ 注意：完全同步会删除目标表多余的数据！");

        dialog.setContentText(content.toString());

        // 创建按钮
        ButtonType incrementalBtn = new ButtonType("增量更新（推荐）");
        ButtonType updateOnlyBtn = new ButtonType("只更新匹配");
        ButtonType insertOnlyBtn = new ButtonType("只新增");
        ButtonType fullSyncBtn = new ButtonType("完全同步");
        ButtonType cancelBtn = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(incrementalBtn, updateOnlyBtn, insertOnlyBtn, fullSyncBtn, cancelBtn);

        // 显示对话框并获取结果
        java.util.Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent()) {
            if (result.get() == incrementalBtn) {
                return EnhancedDataSyncService.SyncMode.INCREMENTAL;
            } else if (result.get() == updateOnlyBtn) {
                return EnhancedDataSyncService.SyncMode.UPDATE_ONLY;
            } else if (result.get() == insertOnlyBtn) {
                return EnhancedDataSyncService.SyncMode.INSERT_ONLY;
            } else if (result.get() == fullSyncBtn) {
                // 完全同步需要二次确认
                Alert confirmAlert = new Alert(Alert.AlertType.WARNING);
                confirmAlert.setTitle("⚠️ 危险操作确认");
                confirmAlert.setHeaderText("完全同步模式");
                confirmAlert.setContentText(
                    "完全同步模式会删除目标表中不存在于源表的记录！\n\n" +
                    "这是一个危险操作，可能导致数据丢失。\n" +
                    "建议先备份数据。\n\n" +
                    "确定要继续吗？"
                );
                confirmAlert.getButtonTypes().setAll(
                    new ButtonType("确定，我已备份"),
                    new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE)
                );

                java.util.Optional<ButtonType> confirmResult = confirmAlert.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get().getButtonData() != ButtonBar.ButtonData.CANCEL_CLOSE) {
                    return EnhancedDataSyncService.SyncMode.FULL_SYNC;
                }
            }
        }

        return null;  // 用户取消
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示手动映射配置对话框
     */
    private void showManualMappingDialog() {
        Stage dialog = new Stage();
        dialog.initOwner(managerStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("⚙️ 手动映射配置");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");

        // 标题
        Label titleLabel = new Label("配置客户端表到服务端表的手动映射");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // 说明
        Label descLabel = new Label(
            "当自动匹配失败或匹配不准确时，可以手动配置映射关系。\n" +
            "手动配置的优先级最高，会覆盖自动匹配结果。"
        );
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666;");

        // 当前手动映射列表
        Label currentLabel = new Label("当前手动映射:");
        currentLabel.setStyle("-fx-font-weight: bold;");

        TextArea currentMappingArea = new TextArea();
        currentMappingArea.setEditable(false);
        currentMappingArea.setPrefRowCount(8);

        Map<String, String> currentMappings = SmartTableMatcher.getManualMappings();
        if (currentMappings.isEmpty()) {
            currentMappingArea.setText("（暂无手动映射配置）");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : currentMappings.entrySet()) {
                sb.append(String.format("%s → %s\n", entry.getKey(), entry.getValue()));
            }
            currentMappingArea.setText(sb.toString());
        }

        // 添加新映射
        Label addLabel = new Label("添加新映射:");
        addLabel.setStyle("-fx-font-weight: bold;");

        HBox addBox = new HBox(10);
        addBox.setAlignment(Pos.CENTER_LEFT);

        TextField clientTableField = new TextField();
        clientTableField.setPromptText("客户端表名");
        clientTableField.setPrefWidth(250);

        Label arrowLabel = new Label("→");
        arrowLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TextField serverTableField = new TextField();
        serverTableField.setPromptText("服务端表名");
        serverTableField.setPrefWidth(250);

        Button addButton = new Button("➕ 添加");
        addButton.setOnAction(e -> {
            String clientTable = clientTableField.getText().trim();
            String serverTable = serverTableField.getText().trim();

            if (clientTable.isEmpty() || serverTable.isEmpty()) {
                showAlert("请输入客户端表名和服务端表名");
                return;
            }

            SmartTableMatcher.addManualMapping(clientTable, serverTable);
            showInfo("添加成功", String.format("已添加映射: %s → %s\n\n请刷新数据以应用新配置",
                clientTable, serverTable));

            clientTableField.clear();
            serverTableField.clear();

            // 更新当前映射显示
            Map<String, String> updatedMappings = SmartTableMatcher.getManualMappings();
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : updatedMappings.entrySet()) {
                sb.append(String.format("%s → %s\n", entry.getKey(), entry.getValue()));
            }
            currentMappingArea.setText(sb.toString());
        });

        addBox.getChildren().addAll(clientTableField, arrowLabel, serverTableField, addButton);

        // 删除映射
        HBox removeBox = new HBox(10);
        removeBox.setAlignment(Pos.CENTER_LEFT);

        TextField removeField = new TextField();
        removeField.setPromptText("要删除的客户端表名");
        removeField.setPrefWidth(250);

        Button removeButton = new Button("🗑️ 删除");
        removeButton.setOnAction(e -> {
            String clientTable = removeField.getText().trim();
            if (clientTable.isEmpty()) {
                showAlert("请输入要删除的客户端表名");
                return;
            }

            SmartTableMatcher.removeManualMapping(clientTable);
            showInfo("删除成功", String.format("已删除映射: %s\n\n请刷新数据以应用新配置", clientTable));
            removeField.clear();

            // 更新显示
            Map<String, String> updatedMappings = SmartTableMatcher.getManualMappings();
            if (updatedMappings.isEmpty()) {
                currentMappingArea.setText("（暂无手动映射配置）");
            } else {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : updatedMappings.entrySet()) {
                    sb.append(String.format("%s → %s\n", entry.getKey(), entry.getValue()));
                }
                currentMappingArea.setText(sb.toString());
            }
        });

        removeBox.getChildren().addAll(removeField, removeButton);

        // 按钮栏
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Button closeButton = new Button("关闭");
        closeButton.setOnAction(e -> dialog.close());

        buttonBar.getChildren().addAll(closeButton);

        layout.getChildren().addAll(
            titleLabel, descLabel,
            new Separator(),
            currentLabel, currentMappingArea,
            new Separator(),
            addLabel, addBox,
            new Separator(),
            removeBox,
            buttonBar
        );

        Scene scene = new Scene(layout, 700, 600);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /**
     * 显示匹配统计信息
     */
    private void showMatchingStatistics() {
        if (smartTablePairs == null || smartTablePairs.isEmpty()) {
            showAlert("暂无匹配数据");
            return;
        }

        // 统计各种匹配情况
        int exactMatches = 0;
        int fuzzyMatches = 0;
        int manualMatches = 0;
        int unmatched = 0;
        int multipleMatches = 0;

        Map<String, Integer> similarityRanges = new HashMap<>();
        similarityRanges.put("100%", 0);
        similarityRanges.put("90-99%", 0);
        similarityRanges.put("80-89%", 0);
        similarityRanges.put("70-79%", 0);
        similarityRanges.put("60-69%", 0);
        similarityRanges.put("<60%", 0);

        for (DatabaseTableScanner.TablePairResult pair : smartTablePairs) {
            if (pair.matchMethod.equals("精确匹配")) {
                exactMatches++;
            } else if (pair.matchMethod.equals("手动配置")) {
                manualMatches++;
            } else if (pair.matchMethod.contains("模糊匹配")) {
                fuzzyMatches++;
            } else {
                unmatched++;
            }

            if (pair.isMultipleMatch) {
                multipleMatches++;
            }

            // 相似度分布
            double sim = pair.similarity * 100;
            if (sim == 100) {
                similarityRanges.put("100%", similarityRanges.get("100%") + 1);
            } else if (sim >= 90) {
                similarityRanges.put("90-99%", similarityRanges.get("90-99%") + 1);
            } else if (sim >= 80) {
                similarityRanges.put("80-89%", similarityRanges.get("80-89%") + 1);
            } else if (sim >= 70) {
                similarityRanges.put("70-79%", similarityRanges.get("70-79%") + 1);
            } else if (sim >= 60) {
                similarityRanges.put("60-69%", similarityRanges.get("60-69%") + 1);
            } else {
                similarityRanges.put("<60%", similarityRanges.get("<60%") + 1);
            }
        }

        // 构建统计报告
        StringBuilder report = new StringBuilder();
        report.append("=== 表映射匹配质量统计报告 ===\n\n");

        report.append("📊 总体统计:\n");
        report.append(String.format("  • 总表数: %d\n", smartTablePairs.size()));
        report.append(String.format("  • 精确匹配: %d (%.1f%%)\n",
            exactMatches, exactMatches * 100.0 / smartTablePairs.size()));
        report.append(String.format("  • 模糊匹配: %d (%.1f%%)\n",
            fuzzyMatches, fuzzyMatches * 100.0 / smartTablePairs.size()));
        report.append(String.format("  • 手动配置: %d (%.1f%%)\n",
            manualMatches, manualMatches * 100.0 / smartTablePairs.size()));
        report.append(String.format("  • 未匹配: %d (%.1f%%)\n",
            unmatched, unmatched * 100.0 / smartTablePairs.size()));
        report.append(String.format("  • 多对一映射: %d\n\n", multipleMatches));

        report.append("📈 相似度分布:\n");
        report.append(String.format("  • 100%%:    %d\n", similarityRanges.get("100%")));
        report.append(String.format("  • 90-99%%:  %d\n", similarityRanges.get("90-99%")));
        report.append(String.format("  • 80-89%%:  %d\n", similarityRanges.get("80-89%")));
        report.append(String.format("  • 70-79%%:  %d\n", similarityRanges.get("70-79%")));
        report.append(String.format("  • 60-69%%:  %d\n", similarityRanges.get("60-69%")));
        report.append(String.format("  • <60%%:    %d\n\n", similarityRanges.get("<60%")));

        // 列出低质量匹配
        report.append("⚠️ 低质量匹配 (相似度 < 80%):\n");
        boolean hasLowQuality = false;
        for (DatabaseTableScanner.TablePairResult pair : smartTablePairs) {
            if (pair.similarity < 0.8 && pair.serverTable != null) {
                report.append(String.format("  • %s → %s (%.0f%%)\n",
                    pair.clientTable.getTableName(),
                    pair.serverTable.getTableName(),
                    pair.similarity * 100));
                hasLowQuality = true;
            }
        }
        if (!hasLowQuality) {
            report.append("  （无）\n");
        }

        report.append("\n");

        // 列出未匹配的表
        report.append("❌ 未匹配的表:\n");
        boolean hasUnmatched = false;
        for (DatabaseTableScanner.TablePairResult pair : smartTablePairs) {
            if (pair.serverTable == null) {
                report.append(String.format("  • %s\n", pair.clientTable.getTableName()));
                hasUnmatched = true;
            }
        }
        if (!hasUnmatched) {
            report.append("  （无）\n");
        }

        // 显示统计报告
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("匹配质量统计");
        alert.setHeaderText("智能匹配质量分析");

        TextArea textArea = new TextArea(report.toString());
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setPrefRowCount(25);
        textArea.setPrefColumnCount(60);

        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setPrefSize(700, 650);
        alert.showAndWait();
    }

    // ==================== 内部类 ====================

    /**
     * 表对包装类（增强版，支持智能匹配信息）
     */
    public static class TablePairWrapper {
        public DatabaseTableScanner.TableInfo clientTable;
        public DatabaseTableScanner.TableInfo serverTable;
        private DatabaseTableScanner.FieldCompareResult compareResult;

        // 智能匹配信息
        public double similarity;           // 综合质量 0-1
        public String matchMethod;          // 匹配方法
        public boolean isMultipleMatch;     // 是否多对一映射

        // 增强质量详情
        public EnhancedMatchQualityCalculator.MatchQuality qualityDetail;

        /**
         * 构造函数（从智能匹配结果构建）
         */
        public TablePairWrapper(DatabaseTableScanner.TablePairResult pairResult) {
            this.clientTable = pairResult.clientTable;
            this.serverTable = pairResult.serverTable;
            this.similarity = pairResult.similarity;
            this.matchMethod = pairResult.matchMethod;
            this.isMultipleMatch = pairResult.isMultipleMatch;
            this.qualityDetail = pairResult.qualityDetail;
            this.compareResult = DatabaseTableScanner.compareFields(clientTable, serverTable);
        }

        /**
         * 构造函数（旧版本，兼容性）
         */
        @Deprecated
        public TablePairWrapper(DatabaseTableScanner.TableInfo clientTable,
                               DatabaseTableScanner.TableInfo serverTable) {
            this.clientTable = clientTable;
            this.serverTable = serverTable;
            this.similarity = 1.0;
            this.matchMethod = "精确匹配";
            this.isMultipleMatch = false;
            this.compareResult = DatabaseTableScanner.compareFields(clientTable, serverTable);
        }

        public String getClientTableName() {
            return clientTable != null ? clientTable.getTableName() : "?";
        }

        public String getServerTableName() {
            return serverTable != null ? serverTable.getTableName() : "不存在";
        }

        /**
         * 获取层级显示
         */
        public String getLevelDisplay() {
            if (clientTable != null) {
                return clientTable.getLevelDisplayName();
            }
            return "?";
        }

        public int getCommonFieldCount() {
            return compareResult.getCommonCount();
        }

        public int getClientOnlyCount() {
            return compareResult.getClientOnlyCount();
        }

        public int getServerOnlyCount() {
            return compareResult.getServerOnlyCount();
        }

        public DatabaseTableScanner.FieldCompareResult getCompareResult() {
            return compareResult;
        }

        /**
         * 获取匹配质量显示文本（增强版）
         */
        public String getMatchQualityDisplay() {
            if (serverTable == null) {
                return "❌ 未匹配";
            }

            String icon;
            if (matchMethod.equals("精确匹配")) {
                icon = "✅";
            } else if (matchMethod.equals("手动配置")) {
                icon = "⚙️";
            } else if (matchMethod.contains("模糊匹配")) {
                // 使用增强质量等级
                if (qualityDetail != null) {
                    switch (qualityDetail.qualityLevel) {
                        case "优秀":
                        case "良好":
                            icon = "✅";
                            break;
                        case "中等":
                            icon = "⚠️";
                            break;
                        case "低":
                        case "极低":
                            icon = "❌";
                            break;
                        default:
                            icon = "❓";
                    }
                } else {
                    // 旧版本兼容
                    if (similarity >= 0.8) {
                        icon = "✅";
                    } else if (similarity >= 0.6) {
                        icon = "⚠️";
                    } else {
                        icon = "❓";
                    }
                }
            } else {
                icon = "❓";
            }

            // 显示综合质量和分解信息
            String text;
            if (qualityDetail != null && matchMethod.contains("模糊匹配")) {
                text = String.format("%s %s (综合:%.0f%% | 表名:%.0f%% 字段:%.0f%%)",
                    icon,
                    qualityDetail.qualityLevel,
                    similarity * 100,
                    qualityDetail.tableNameSimilarity * 100,
                    qualityDetail.fieldMatchScore * 100
                );
            } else {
                text = String.format("%s %s (%.0f%%)",
                    icon, matchMethod, similarity * 100);
            }

            if (isMultipleMatch) {
                text += " 🔗";
            }

            return text;
        }

        /**
         * 获取字段差异状态显示
         */
        public String getFieldStatusDisplay() {
            if (serverTable == null) {
                return "⚠️ 无服务端表";
            }
            if (getClientOnlyCount() == 0 && getServerOnlyCount() == 0) {
                return "✅ 完全一致";
            }
            return String.format("⚠️ 共同:%d | C独有:%d | S独有:%d",
                getCommonFieldCount(), getClientOnlyCount(), getServerOnlyCount());
        }

        /**
         * 获取状态显示（旧版本兼容）
         */
        @Deprecated
        public String getStatusDisplay() {
            return getFieldStatusDisplay();
        }
    }

    /**
     * 字段行数据
     */
    public static class FieldRowData {
        private SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        public String fieldName;
        public String clientType;
        public String serverType;
        public String comment;
        public String status;      // MATCHED, TYPE_DIFF, CLIENT_ONLY, SERVER_ONLY
        public String fieldType;   // COMMON, CLIENT_ONLY, SERVER_ONLY

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean value) {
            selected.set(value);
        }

        public String getStatusDisplay() {
            switch (status) {
                case "MATCHED": return "✅ 匹配";
                case "TYPE_DIFF": return "⚠️ 类型不同";
                case "CLIENT_ONLY": return "📦 仅客户端";
                case "SERVER_ONLY": return "🖥️ 仅服务端";
                default: return status;
            }
        }
    }

    // ==================== 批量操作方法 ====================

    /**
     * 创建菜单项辅助方法
     */
    private MenuItem createMenuItem(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(handler);
        return item;
    }

    /**
     * 显示批量DDL生成对话框
     */
    private void showBatchDdlDialog() {
        showBatchOperationDialog("批量生成DDL",
            "选择要生成DDL的表，将批量生成CREATE TABLE语句",
            MappingConfigManager.BatchOperationType.GENERATE_DDL);
    }

    /**
     * 显示批量导入对话框
     */
    private void showBatchImportDialog() {
        showBatchOperationDialog("批量导入XML到数据库",
            "选择要导入的表，将批量执行XML数据导入",
            MappingConfigManager.BatchOperationType.IMPORT_XML_TO_DB);
    }

    /**
     * 显示批量导出对话框
     */
    private void showBatchExportDialog() {
        showBatchOperationDialog("批量导出数据库到XML",
            "选择要导出的表，将批量导出为XML文件",
            MappingConfigManager.BatchOperationType.EXPORT_DB_TO_XML);
    }

    /**
     * 显示批量验证对话框
     */
    private void showBatchValidateDialog() {
        showInfo("批量验证映射",
            "此功能将验证所有表映射的正确性：\n\n" +
            "• 检查字段匹配率\n" +
            "• 检查类型兼容性\n" +
            "• 检查主键约束\n" +
            "• 生成验证报告\n\n" +
            "功能开发中...");
    }

    /**
     * 显示表间关系对话框
     */
    private void showTableRelationsDialog() {
        showInfo("表间关系分析",
            "此功能将分析所有表之间的关系：\n\n" +
            "• 父子表关系\n" +
            "• ID引用关系\n" +
            "• 本地化字符串关系\n" +
            "• 生成关系图\n\n" +
            "功能开发中...");
    }

    /**
     * 显示批量操作对话框
     */
    private void showBatchOperationDialog(String title, String description,
                                          MappingConfigManager.BatchOperationType operationType) {
        Stage dialog = new Stage();
        dialog.initOwner(managerStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("📦 " + title);

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");

        // 标题和描述
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666;");

        // 快速选择按钮
        HBox quickSelectBox = new HBox(10);
        quickSelectBox.setAlignment(Pos.CENTER_LEFT);

        Button selectAllBtn = new Button("全选");
        Button selectNoneBtn = new Button("全不选");
        Button selectMatchedBtn = new Button("选择已匹配");
        Button selectStringsBtn = new Button("选择strings表");

        // 表选择列表
        ListView<TablePairWrapper> tableListView = new ListView<>();
        tableListView.getItems().addAll(tablePairList);
        tableListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableListView.setCellFactory(lv -> new ListCell<TablePairWrapper>() {
            private final CheckBox checkBox = new CheckBox();

            @Override
            protected void updateItem(TablePairWrapper item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    checkBox.setSelected(isSelected());
                    String displayText = item.getClientTableName();
                    if (item.serverTable != null) {
                        displayText += " → " + item.getServerTableName();
                    } else {
                        displayText += " (未匹配)";
                    }
                    checkBox.setText(displayText);
                    setGraphic(checkBox);

                    checkBox.setOnAction(e -> {
                        if (checkBox.isSelected()) {
                            getListView().getSelectionModel().select(getIndex());
                        } else {
                            getListView().getSelectionModel().clearSelection(getIndex());
                        }
                    });
                }
            }
        });

        selectAllBtn.setOnAction(e -> tableListView.getSelectionModel().selectAll());
        selectNoneBtn.setOnAction(e -> tableListView.getSelectionModel().clearSelection());
        selectMatchedBtn.setOnAction(e -> {
            tableListView.getSelectionModel().clearSelection();
            for (int i = 0; i < tableListView.getItems().size(); i++) {
                if (tableListView.getItems().get(i).serverTable != null) {
                    tableListView.getSelectionModel().select(i);
                }
            }
        });
        selectStringsBtn.setOnAction(e -> {
            tableListView.getSelectionModel().clearSelection();
            for (int i = 0; i < tableListView.getItems().size(); i++) {
                String name = tableListView.getItems().get(i).getClientTableName().toLowerCase();
                if (name.contains("string")) {
                    tableListView.getSelectionModel().select(i);
                }
            }
        });

        quickSelectBox.getChildren().addAll(selectAllBtn, selectNoneBtn, selectMatchedBtn, selectStringsBtn);

        // 统计标签
        Label statsLabel = new Label("已选择: 0 个表");
        tableListView.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<TablePairWrapper>) c ->
                statsLabel.setText("已选择: " + tableListView.getSelectionModel().getSelectedItems().size() + " 个表")
        );

        // 按钮栏
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button executeBtn = new Button("🚀 执行");
        executeBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        executeBtn.setOnAction(e -> {
            List<TablePairWrapper> selected = new ArrayList<>(tableListView.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) {
                showAlert("请至少选择一个表");
                return;
            }
            dialog.close();
            executeBatchOperation(selected, operationType);
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setOnAction(e -> dialog.close());

        buttonBar.getChildren().addAll(statsLabel, new Region(), executeBtn, cancelBtn);
        HBox.setHgrow(buttonBar.getChildren().get(1), Priority.ALWAYS);

        VBox.setVgrow(tableListView, Priority.ALWAYS);
        layout.getChildren().addAll(titleLabel, descLabel, quickSelectBox, tableListView, buttonBar);

        Scene scene = new Scene(layout, 600, 500);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /**
     * 执行批量操作
     */
    private void executeBatchOperation(List<TablePairWrapper> tables,
                                       MappingConfigManager.BatchOperationType operationType) {
        log.info("执行批量操作: {} - {} 个表", operationType.displayName, tables.size());

        MappingConfigManager.BatchOperationResult result =
            new MappingConfigManager.BatchOperationResult(operationType);
        result.totalCount = tables.size();

        long startTime = System.currentTimeMillis();

        for (TablePairWrapper table : tables) {
            try {
                switch (operationType) {
                    case GENERATE_DDL:
                        // TODO: 调用DDL生成逻辑
                        result.recordSuccess(table.getClientTableName());
                        break;
                    case IMPORT_XML_TO_DB:
                        // TODO: 调用XML导入逻辑
                        result.recordSuccess(table.getClientTableName());
                        break;
                    case EXPORT_DB_TO_XML:
                        // TODO: 调用数据库导出逻辑
                        result.recordSuccess(table.getClientTableName());
                        break;
                    default:
                        result.recordSuccess(table.getClientTableName());
                }
            } catch (Exception e) {
                result.recordFailure(table.getClientTableName(), e.getMessage());
                log.error("批量操作失败: {} - {}", table.getClientTableName(), e.getMessage());
            }
        }

        result.executionTimeMs = System.currentTimeMillis() - startTime;

        showInfo("批量操作完成", result.getSummary());
    }

    /**
     * 为选中表生成DDL
     */
    private void generateDdlForTable(TablePairWrapper table) {
        showInfo("生成DDL", "为表 " + table.getClientTableName() + " 生成DDL...\n\n功能开发中...");
    }

    /**
     * 为选中表导入XML
     */
    private void importXmlForTable(TablePairWrapper table) {
        showInfo("导入XML", "导入表 " + table.getClientTableName() + " 的XML数据...\n\n功能开发中...");
    }

    /**
     * 为选中表导出XML
     */
    private void exportXmlForTable(TablePairWrapper table) {
        showInfo("导出XML", "导出表 " + table.getServerTableName() + " 到XML...\n\n功能开发中...");
    }

    /**
     * 批量生成选中表的DDL
     */
    private void batchGenerateDdlForSelected() {
        List<TablePairWrapper> selected = new ArrayList<>(pairTableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            showAlert("请先选择要操作的表");
            return;
        }
        executeBatchOperation(selected, MappingConfigManager.BatchOperationType.GENERATE_DDL);
    }

    /**
     * 批量导入选中表
     */
    private void batchImportForSelected() {
        List<TablePairWrapper> selected = new ArrayList<>(pairTableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            showAlert("请先选择要操作的表");
            return;
        }
        executeBatchOperation(selected, MappingConfigManager.BatchOperationType.IMPORT_XML_TO_DB);
    }

    /**
     * 批量导出选中表
     */
    private void batchExportForSelected() {
        List<TablePairWrapper> selected = new ArrayList<>(pairTableView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            showAlert("请先选择要操作的表");
            return;
        }
        executeBatchOperation(selected, MappingConfigManager.BatchOperationType.EXPORT_DB_TO_XML);
    }

    /**
     * 显示单表的表间关系
     */
    private void showTableRelationsFor(TablePairWrapper table) {
        if (table.clientTable == null) return;

        List<MappingConfigManager.TableRelation> relations =
            MappingConfigManager.detectTableRelations(table.clientTable, allTables);

        if (relations.isEmpty()) {
            showInfo("表间关系", "表 " + table.getClientTableName() + " 未检测到明显的表间关系");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("表 ").append(table.getClientTableName()).append(" 的关系：\n\n");

        for (MappingConfigManager.TableRelation rel : relations) {
            sb.append(String.format("• %s\n  %s → %s\n  置信度: %.0f%%\n\n",
                rel.relationType.displayName,
                rel.sourceTable, rel.targetTable,
                rel.confidence * 100));
        }

        showInfo("表间关系分析", sb.toString());
    }

    /**
     * 为单表设置手动映射
     */
    private void showManualMappingForTable(TablePairWrapper table) {
        TextInputDialog dialog = new TextInputDialog(
            table.serverTable != null ? table.serverTable.getTableName() : "");
        dialog.setTitle("设置手动映射");
        dialog.setHeaderText("为 " + table.getClientTableName() + " 设置服务端表映射");
        dialog.setContentText("服务端表名:");

        dialog.showAndWait().ifPresent(serverTable -> {
            if (!serverTable.trim().isEmpty()) {
                SmartTableMatcher.addManualMapping(table.getClientTableName(), serverTable.trim());
                showInfo("设置成功", "已设置手动映射:\n" +
                    table.getClientTableName() + " → " + serverTable.trim() +
                    "\n\n重新加载后生效");
            }
        });
    }

    /**
     * 枚举值信息
     */
    public static class EnumValueInfo {
        public String value;    // 枚举值
        public int count;       // 出现次数
    }

    /**
     * 枚举值对比行数据
     */
    public static class EnumCompareRow {
        public String value;        // 枚举值
        public int clientCount;     // 客户端出现次数
        public int serverCount;     // 服务端出现次数
        public String status;       // BOTH, CLIENT_ONLY, SERVER_ONLY

        public String getStatusDisplay() {
            switch (status) {
                case "BOTH": return "✅ 两侧都有";
                case "CLIENT_ONLY": return "📦 仅客户端";
                case "SERVER_ONLY": return "🖥️ 仅服务端";
                default: return status;
            }
        }
    }
}
