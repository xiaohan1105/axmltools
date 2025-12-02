package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 智能数据表格组件
 * 专为设计师优化的数据展示和操作界面
 *
 * 功能特性：
 * - 现代化的视觉设计
 * - 智能搜索和筛选
 * - 流畅的分页体验
 * - 直观的数据操作
 * - 实时状态反馈
 */
public class SmartDataTable {

    private static final Logger log = LoggerFactory.getLogger(SmartDataTable.class);

    // UI组件
    private final VBox container;
    private final SearchPanel searchPanel;
    private final TableView<Map<String, Object>> tableView;
    private final StatusBar statusBar;
    private final SmartPagination pagination;

    // 数据管理
    private List<Map<String, Object>> allData;
    private List<Map<String, Object>> filteredData;
    private final DataManager dataManager;

    // 配置选项
    private int pageSize = 25;
    private String currentSearchTerm = "";
    private final List<FilterCriteria> activeFilters = new ArrayList<>();

    // 线程池
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SmartDataTable() {
        this.container = new VBox();
        this.searchPanel = new SearchPanel();
        this.tableView = new TableView<>();
        this.statusBar = new StatusBar();
        this.pagination = new SmartPagination();
        this.dataManager = new DataManager();

        initializeComponents();
        setupEventHandlers();
        applyStyles();
    }

    /**
     * 初始化组件
     */
    private void initializeComponents() {
        container.getStyleClass().add("smart-data-table");
        container.setSpacing(0);

        // 配置表格
        setupTableView();

        // 组装界面
        container.getChildren().addAll(
            searchPanel.getPanel(),
            tableView,
            statusBar.getPanel(),
            pagination.getPanel()
        );

        VBox.setVgrow(tableView, Priority.ALWAYS);
    }

    /**
     * 配置表格视图
     */
    private void setupTableView() {
        tableView.getStyleClass().add("modern-table");
        tableView.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.getStyleClass().add("data-row");

            // 添加行号显示
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    int index = tableView.getItems().indexOf(newItem) + 1;
                    row.getStyleClass().removeIf(style -> style.startsWith("row-"));
                    row.getStyleClass().add("row-" + (index % 2 == 0 ? "even" : "odd"));
                }
            });

            return row;
        });

        // 设置选择模式
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getSelectionModel().setCellSelectionEnabled(true);

        // 添加键盘快捷键
        setupKeyboardShortcuts();

        // 添加右键菜单
        setupContextMenu();
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        tableView.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case C:
                        copySelectedCells();
                        break;
                    case A:
                        tableView.getSelectionModel().selectAll();
                        break;
                    case F:
                        searchPanel.focusSearchField();
                        break;
                }
            } else if (event.getCode() == KeyCode.DELETE) {
                deleteSelectedRows();
            }
        });
    }

    /**
     * 设置右键菜单
     */
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem copyItem = new MenuItem("📋 复制");
        copyItem.setOnAction(e -> copySelectedCells());

        MenuItem selectAllItem = new MenuItem("🔘 全选");
        selectAllItem.setOnAction(e -> tableView.getSelectionModel().selectAll());

        MenuItem clearSelectionItem = new MenuItem("❌清除选择");
        clearSelectionItem.setOnAction(e -> tableView.getSelectionModel().clearSelection());

        SeparatorMenuItem separator = new SeparatorMenuItem();

        MenuItem deleteItem = new MenuItem("🗑️ 删除选中");
        deleteItem.setOnAction(e -> deleteSelectedRows());

        MenuItem editItem = new MenuItem("✏️ 编辑");
        editItem.setOnAction(e -> editSelectedRow());

        contextMenu.getItems().addAll(
            copyItem, selectAllItem, clearSelectionItem,
            separator, editItem, deleteItem
        );

        tableView.setContextMenu(contextMenu);
    }

    /**
     * 设置事件处理器
     */
    private void setupEventHandlers() {
        // 搜索事件
        searchPanel.setOnSearch(this::performSearch);
        searchPanel.setOnClearFilters(this::clearAllFilters);

        // 分页事件
        pagination.setOnPageChange(this::loadPage);
        pagination.setOnPageSizeChange(this::changePageSize);

        // 表格选择事件
        tableView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> updateStatusBar()
        );
    }

    /**
     * 应用样式
     */
    private void applyStyles() {
        container.getStylesheets().add("/modern-theme.css");
    }

    // ========== 数据操作方法 ==========

    /**
     * 设置数据源
     */
    public void setData(List<Map<String, Object>> data) {
        this.allData = new ArrayList<>(data);
        this.filteredData = new ArrayList<>(data);

        Platform.runLater(() -> {
            createColumns();
            pagination.setTotalItems(data.size());
            loadPage(0);
            updateStatusBar();
        });
    }

    /**
     * 动态创建列
     */
    private void createColumns() {
        tableView.getColumns().clear();

        if (allData.isEmpty()) return;

        Map<String, Object> firstRow = allData.get(0);

        // 添加行号列
        TableColumn<Map<String, Object>, String> indexColumn = new TableColumn<>("#");
        indexColumn.setCellValueFactory(param -> {
            int index = tableView.getItems().indexOf(param.getValue()) + 1;
            return new SimpleStringProperty(String.valueOf(index));
        });
        indexColumn.setPrefWidth(50);
        indexColumn.getStyleClass().add("index-column");
        indexColumn.setSortable(false);
        tableView.getColumns().add(indexColumn);

        // 添加数据列
        for (String columnName : firstRow.keySet()) {
            TableColumn<Map<String, Object>, Object> column = createDataColumn(columnName);
            tableView.getColumns().add(column);
        }
    }

    /**
     * 创建数据列
     */
    private TableColumn<Map<String, Object>, Object> createDataColumn(String columnName) {
        TableColumn<Map<String, Object>, Object> column = new TableColumn<>(columnName);
        column.setCellValueFactory(new PropertyValueFactory<>(columnName));
        column.setPrefWidth(120);
        column.getStyleClass().add("data-column");

        // 添加列头右键菜单
        ContextMenu headerMenu = new ContextMenu();

        MenuItem filterItem = new MenuItem("🔍 筛选此列");
        filterItem.setOnAction(e -> showColumnFilter(columnName));

        MenuItem sortAscItem = new MenuItem("⬆️ 升序排列");
        sortAscItem.setOnAction(e -> sortColumn(columnName, true));

        MenuItem sortDescItem = new MenuItem("⬇️ 降序排列");
        sortDescItem.setOnAction(e -> sortColumn(columnName, false));

        MenuItem hideItem = new MenuItem("👁️ 隐藏列");
        hideItem.setOnAction(e -> hideColumn(column));

        headerMenu.getItems().addAll(filterItem, sortAscItem, sortDescItem, hideItem);

        column.setContextMenu(headerMenu);

        return column;
    }

    /**
     * 执行搜索
     */
    private void performSearch(String searchTerm) {
        this.currentSearchTerm = searchTerm;

        Task<List<Map<String, Object>>> searchTask = new Task<List<Map<String, Object>>>() {
            @Override
            protected List<Map<String, Object>> call() {
                return dataManager.search(allData, searchTerm, activeFilters);
            }

            @Override
            protected void succeeded() {
                filteredData = getValue();
                Platform.runLater(() -> {
                    pagination.setTotalItems(filteredData.size());
                    loadPage(0);
                    updateStatusBar();
                    statusBar.showMessage("搜索完成，找到 " + filteredData.size() + " 条结果", "success");
                });
            }
        };

        executor.submit(searchTask);
        statusBar.showMessage("正在搜索...", "info");
    }

    /**
     * 加载指定页面
     */
    private void loadPage(int pageIndex) {
        List<Map<String, Object>> pageData = dataManager.getPage(filteredData, pageIndex, pageSize);

        ObservableList<Map<String, Object>> observableData = FXCollections.observableArrayList(pageData);
        tableView.setItems(observableData);

        pagination.setCurrentPage(pageIndex);
    }

    /**
     * 更改页面大小
     */
    private void changePageSize(int newPageSize) {
        this.pageSize = newPageSize;
        pagination.setPageSize(newPageSize);
        pagination.setTotalItems(filteredData.size());
        loadPage(0);
    }

    /**
     * 复制选中单元格
     */
    private void copySelectedCells() {
        ObservableList<TablePosition> selectedCells = tableView.getSelectionModel().getSelectedCells();

        if (selectedCells.isEmpty()) {
            statusBar.showMessage("请先选择要复制的单元格", "warning");
            return;
        }

        StringBuilder clipboardContent = new StringBuilder();

        // 按行列排序选中的单元格
        selectedCells.sort((pos1, pos2) -> {
            int rowCompare = Integer.compare(pos1.getRow(), pos2.getRow());
            return rowCompare != 0 ? rowCompare : Integer.compare(pos1.getColumn(), pos2.getColumn());
        });

        int lastRow = -1;
        for (TablePosition position : selectedCells) {
            if (lastRow != -1 && lastRow != position.getRow()) {
                clipboardContent.append("\n");
            } else if (lastRow == position.getRow() && clipboardContent.length() > 0
                      && !clipboardContent.toString().endsWith("\n")) {
                clipboardContent.append("\t");
            }

            Object cellValue = position.getTableColumn().getCellData(position.getRow());
            clipboardContent.append(cellValue != null ? cellValue.toString() : "");
            lastRow = position.getRow();
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(clipboardContent.toString());
        Clipboard.getSystemClipboard().setContent(content);

        statusBar.showMessage("已复制 " + selectedCells.size() + " 个单元格", "success");
    }

    /**
     * 删除选中行
     */
    private void deleteSelectedRows() {
        ObservableList<Map<String, Object>> selectedItems = tableView.getSelectionModel().getSelectedItems();

        if (selectedItems.isEmpty()) {
            statusBar.showMessage("请先选择要删除的行", "warning");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("确认删除");
        confirmation.setHeaderText("删除确认");
        confirmation.setContentText("确定要删除选中的 " + selectedItems.size() + " 行数据吗？");

        confirmation.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                // TODO: 实现实际的删除逻辑
                allData.removeAll(selectedItems);
                filteredData.removeAll(selectedItems);

                pagination.setTotalItems(filteredData.size());
                loadPage(pagination.getCurrentPage());
                updateStatusBar();

                statusBar.showMessage("已删除 " + selectedItems.size() + " 行数据", "success");
            }
        });
    }

    /**
     * 编辑选中行
     */
    private void editSelectedRow() {
        Map<String, Object> selectedItem = tableView.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            statusBar.showMessage("请先选择要编辑的行", "warning");
            return;
        }

        // TODO: 打开数据编辑对话框
        statusBar.showMessage("编辑功能开发中...", "info");
    }

    /**
     * 显示列筛选器
     */
    private void showColumnFilter(String columnName) {
        // TODO: 实现列筛选对话框
        statusBar.showMessage("列筛选功能开发中...", "info");
    }

    /**
     * 排序列
     */
    private void sortColumn(String columnName, boolean ascending) {
        filteredData.sort((row1, row2) -> {
            Object val1 = row1.get(columnName);
            Object val2 = row2.get(columnName);

            if (val1 == null && val2 == null) return 0;
            if (val1 == null) return ascending ? -1 : 1;
            if (val2 == null) return ascending ? 1 : -1;

            int result = val1.toString().compareTo(val2.toString());
            return ascending ? result : -result;
        });

        loadPage(pagination.getCurrentPage());
        statusBar.showMessage("已按 " + columnName + " " + (ascending ? "升序" : "降序") + " 排列", "success");
    }

    /**
     * 隐藏列
     */
    private void hideColumn(TableColumn<Map<String, Object>, Object> column) {
        column.setVisible(false);
        statusBar.showMessage("已隐藏列: " + column.getText(), "info");
    }

    /**
     * 清除所有筛选条件
     */
    private void clearAllFilters() {
        activeFilters.clear();
        currentSearchTerm = "";
        filteredData = new ArrayList<>(allData);

        pagination.setTotalItems(filteredData.size());
        loadPage(0);
        updateStatusBar();

        statusBar.showMessage("已清除所有筛选条件", "info");
    }

    /**
     * 更新状态栏
     */
    private void updateStatusBar() {
        int selectedCount = tableView.getSelectionModel().getSelectedItems().size();
        int totalCount = filteredData.size();
        int allCount = allData.size();

        String status = String.format("显示 %d/%d 条记录", totalCount, allCount);
        if (selectedCount > 0) {
            status += String.format("，已选择 %d 条", selectedCount);
        }

        statusBar.setStatus(status);
    }

    // ========== 内部类 ==========

    /**
     * 筛选条件
     */
    public static class FilterCriteria {
        private final String column;
        private final String operator;
        private final Object value;

        public FilterCriteria(String column, String operator, Object value) {
            this.column = column;
            this.operator = operator;
            this.value = value;
        }

        // Getters
        public String getColumn() { return column; }
        public String getOperator() { return operator; }
        public Object getValue() { return value; }
    }

    // ========== 公共API ==========

    public VBox getContainer() {
        return container;
    }

    public void refresh() {
        loadPage(pagination.getCurrentPage());
        updateStatusBar();
    }

    public void exportData() {
        // TODO: 实现数据导出功能
        statusBar.showMessage("导出功能开发中...", "info");
    }

    public void importData() {
        // TODO: 实现数据导入功能
        statusBar.showMessage("导入功能开发中...", "info");
    }
}