package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 右键菜单工厂
 *
 * 提供统一的右键菜单创建和管理，确保一致的用户体验
 *
 * @author Claude
 * @version 1.0
 */
public class ContextMenuFactory {

    private static final Logger log = LoggerFactory.getLogger(ContextMenuFactory.class);

    // ==================== 树视图菜单 ====================

    /**
     * 创建树视图右键菜单
     */
    public static <T> ContextMenu createTreeViewMenu(
            TreeView<T> treeView,
            TreeMenuCallbacks<T> callbacks) {

        ContextMenu menu = new ContextMenu();

        // 展开/折叠组
        MenuItem expandItem = createMenuItem("📂 展开此项", "展开当前节点及所有子节点");
        MenuItem collapseItem = createMenuItem("📁 折叠此项", "折叠当前节点及所有子节点");
        MenuItem expandAllItem = createMenuItem("📂 全部展开", "展开所有节点");
        MenuItem collapseAllItem = createMenuItem("📁 全部折叠", "折叠所有节点");

        expandItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                expandRecursively(selected, true);
            }
        });

        collapseItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                expandRecursively(selected, false);
            }
        });

        expandAllItem.setOnAction(e -> expandRecursively(treeView.getRoot(), true));
        collapseAllItem.setOnAction(e -> expandRecursively(treeView.getRoot(), false));

        // 复制组
        MenuItem copyPathItem = createMenuItem("📋 复制路径", "复制节点路径到剪贴板");
        MenuItem copyNameItem = createMenuItem("📝 复制名称", "复制节点名称到剪贴板");

        copyPathItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && callbacks.getFullPath != null) {
                String path = callbacks.getFullPath.apply(selected);
                copyToClipboard(path);
                showToast("已复制路径: " + path);
            }
        });

        copyNameItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String name = selected.getValue().toString();
                copyToClipboard(name);
                showToast("已复制: " + name);
            }
        });

        // 打开组
        MenuItem openItem = createMenuItem("📄 打开", "在应用中打开此项");
        MenuItem openFolderItem = createMenuItem("📁 在资源管理器中显示", "打开所在文件夹");
        MenuItem openExternalItem = createMenuItem("🔗 使用外部程序打开", "使用系统默认程序打开");

        openItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && callbacks.onOpen != null) {
                callbacks.onOpen.accept(selected);
            }
        });

        openFolderItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && callbacks.getFullPath != null) {
                String path = callbacks.getFullPath.apply(selected);
                openInExplorer(path);
            }
        });

        openExternalItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && callbacks.getFullPath != null) {
                String path = callbacks.getFullPath.apply(selected);
                openWithDesktop(path);
            }
        });

        // 操作组
        MenuItem refreshItem = createMenuItem("🔄 刷新", "刷新当前视图");
        refreshItem.setOnAction(e -> {
            if (callbacks.onRefresh != null) {
                callbacks.onRefresh.run();
            }
        });

        // 搜索组
        MenuItem searchItem = createMenuItem("🔍 搜索...", "搜索此节点下的内容 (Ctrl+F)");
        searchItem.setOnAction(e -> {
            if (callbacks.onSearch != null) {
                callbacks.onSearch.run();
            }
        });

        // 组装菜单
        menu.getItems().addAll(
            openItem,
            openFolderItem,
            openExternalItem,
            new SeparatorMenuItem(),
            expandItem,
            collapseItem,
            expandAllItem,
            collapseAllItem,
            new SeparatorMenuItem(),
            copyPathItem,
            copyNameItem,
            new SeparatorMenuItem(),
            searchItem,
            refreshItem
        );

        // 动态启用/禁用菜单项
        menu.setOnShowing(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            boolean hasSelection = selected != null;
            boolean isLeaf = hasSelection && selected.isLeaf();
            boolean hasPath = hasSelection && callbacks.getFullPath != null;

            openItem.setDisable(!isLeaf);
            openFolderItem.setDisable(!hasPath);
            openExternalItem.setDisable(!hasPath || !isLeaf);
            expandItem.setDisable(!hasSelection || isLeaf);
            collapseItem.setDisable(!hasSelection || isLeaf);
            copyPathItem.setDisable(!hasPath);
            copyNameItem.setDisable(!hasSelection);
        });

        return menu;
    }

    /**
     * 树视图菜单回调接口
     */
    public static class TreeMenuCallbacks<T> {
        public java.util.function.Function<TreeItem<T>, String> getFullPath;
        public Consumer<TreeItem<T>> onOpen;
        public Runnable onRefresh;
        public Runnable onSearch;
    }

    // ==================== 表格菜单 ====================

    /**
     * 创建表格行右键菜单
     */
    public static <T> ContextMenu createTableRowMenu(
            TableView<T> tableView,
            TableMenuCallbacks<T> callbacks) {

        ContextMenu menu = new ContextMenu();

        // 复制组
        MenuItem copyRowItem = createMenuItem("📋 复制行", "复制选中行数据 (Ctrl+C)");
        MenuItem copyAllItem = createMenuItem("📄 复制全部", "复制所有行数据");
        MenuItem copyCellItem = createMenuItem("📝 复制单元格", "复制当前单元格值");

        copyRowItem.setOnAction(e -> {
            T selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && callbacks.formatRow != null) {
                String text = callbacks.formatRow.apply(selected);
                copyToClipboard(text);
                showToast("已复制行数据");
            }
        });

        copyAllItem.setOnAction(e -> {
            if (callbacks.formatAll != null) {
                String text = callbacks.formatAll.get();
                copyToClipboard(text);
                showToast("已复制 " + tableView.getItems().size() + " 行数据");
            }
        });

        copyCellItem.setOnAction(e -> {
            TablePosition<T, ?> pos = tableView.getFocusModel().getFocusedCell();
            if (pos != null && pos.getTableColumn() != null) {
                Object cellValue = pos.getTableColumn().getCellData(pos.getRow());
                if (cellValue != null) {
                    copyToClipboard(cellValue.toString());
                    showToast("已复制: " + cellValue);
                }
            }
        });

        // 选择组
        MenuItem selectAllItem = createMenuItem("🔘 全选", "选择所有行 (Ctrl+A)");
        MenuItem clearSelectionItem = createMenuItem("❌ 清除选择", "取消所有选择");

        selectAllItem.setOnAction(e -> tableView.getSelectionModel().selectAll());
        clearSelectionItem.setOnAction(e -> tableView.getSelectionModel().clearSelection());

        // 导出组
        Menu exportMenu = new Menu("📤 导出");
        MenuItem exportCsvItem = createMenuItem("📊 导出为 CSV", "导出为CSV格式");
        MenuItem exportJsonItem = createMenuItem("📋 导出为 JSON", "导出为JSON格式");
        MenuItem exportExcelItem = createMenuItem("📗 导出为 Excel", "导出为Excel格式");

        exportCsvItem.setOnAction(e -> {
            if (callbacks.exportCsv != null) {
                callbacks.exportCsv.run();
            }
        });

        exportJsonItem.setOnAction(e -> {
            if (callbacks.exportJson != null) {
                callbacks.exportJson.run();
            }
        });

        exportExcelItem.setOnAction(e -> {
            if (callbacks.exportExcel != null) {
                callbacks.exportExcel.run();
            }
        });

        exportMenu.getItems().addAll(exportCsvItem, exportJsonItem, exportExcelItem);

        // 查看组
        MenuItem viewDetailItem = createMenuItem("👁️ 查看详情", "查看选中行详细信息");
        MenuItem viewReferencesItem = createMenuItem("🔗 查看引用", "查看此项的引用关系");

        viewDetailItem.setOnAction(e -> {
            T selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && callbacks.onViewDetail != null) {
                callbacks.onViewDetail.accept(selected);
            }
        });

        viewReferencesItem.setOnAction(e -> {
            T selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && callbacks.onViewReferences != null) {
                callbacks.onViewReferences.accept(selected);
            }
        });

        // 编辑组
        MenuItem editItem = createMenuItem("✏️ 编辑", "编辑选中行");
        MenuItem deleteItem = createMenuItem("🗑️ 删除", "删除选中行 (Delete)");

        editItem.setOnAction(e -> {
            T selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && callbacks.onEdit != null) {
                callbacks.onEdit.accept(selected);
            }
        });

        deleteItem.setOnAction(e -> {
            List<T> selected = tableView.getSelectionModel().getSelectedItems();
            if (!selected.isEmpty() && callbacks.onDelete != null) {
                callbacks.onDelete.accept(selected);
            }
        });

        // 筛选组
        MenuItem filterItem = createMenuItem("🔍 筛选...", "按条件筛选 (Ctrl+F)");
        MenuItem clearFilterItem = createMenuItem("🧹 清除筛选", "清除所有筛选条件");

        filterItem.setOnAction(e -> {
            if (callbacks.onFilter != null) {
                callbacks.onFilter.run();
            }
        });

        clearFilterItem.setOnAction(e -> {
            if (callbacks.onClearFilter != null) {
                callbacks.onClearFilter.run();
            }
        });

        // 组装菜单
        menu.getItems().addAll(
            viewDetailItem,
            viewReferencesItem,
            new SeparatorMenuItem(),
            copyRowItem,
            copyCellItem,
            copyAllItem,
            new SeparatorMenuItem(),
            selectAllItem,
            clearSelectionItem,
            new SeparatorMenuItem(),
            filterItem,
            clearFilterItem,
            new SeparatorMenuItem(),
            exportMenu
        );

        // 如果有编辑权限，添加编辑菜单
        if (callbacks.onEdit != null || callbacks.onDelete != null) {
            menu.getItems().addAll(
                new SeparatorMenuItem(),
                editItem,
                deleteItem
            );
        }

        // 动态启用/禁用
        menu.setOnShowing(e -> {
            boolean hasSelection = !tableView.getSelectionModel().isEmpty();
            boolean hasData = !tableView.getItems().isEmpty();

            copyRowItem.setDisable(!hasSelection);
            copyCellItem.setDisable(!hasSelection);
            copyAllItem.setDisable(!hasData);
            viewDetailItem.setDisable(!hasSelection || callbacks.onViewDetail == null);
            viewReferencesItem.setDisable(!hasSelection || callbacks.onViewReferences == null);
            editItem.setDisable(!hasSelection || callbacks.onEdit == null);
            deleteItem.setDisable(!hasSelection || callbacks.onDelete == null);
            exportMenu.setDisable(!hasData);
            clearFilterItem.setDisable(callbacks.onClearFilter == null);
        });

        return menu;
    }

    /**
     * 表格菜单回调接口
     */
    public static class TableMenuCallbacks<T> {
        public java.util.function.Function<T, String> formatRow;
        public Supplier<String> formatAll;
        public Consumer<T> onViewDetail;
        public Consumer<T> onViewReferences;
        public Consumer<T> onEdit;
        public Consumer<List<T>> onDelete;
        public Runnable onFilter;
        public Runnable onClearFilter;
        public Runnable exportCsv;
        public Runnable exportJson;
        public Runnable exportExcel;
    }

    // ==================== 列头菜单 ====================

    /**
     * 创建表格列头右键菜单
     */
    public static <S, T> ContextMenu createColumnHeaderMenu(
            TableColumn<S, T> column,
            TableView<S> tableView,
            ColumnMenuCallbacks<S, T> callbacks) {

        ContextMenu menu = new ContextMenu();

        // 排序组
        MenuItem sortAscItem = createMenuItem("⬆️ 升序排列", "按此列升序排列");
        MenuItem sortDescItem = createMenuItem("⬇️ 降序排列", "按此列降序排列");
        MenuItem clearSortItem = createMenuItem("🔄 清除排序", "恢复默认顺序");

        sortAscItem.setOnAction(e -> {
            column.setSortType(TableColumn.SortType.ASCENDING);
            tableView.getSortOrder().clear();
            tableView.getSortOrder().add(column);
        });

        sortDescItem.setOnAction(e -> {
            column.setSortType(TableColumn.SortType.DESCENDING);
            tableView.getSortOrder().clear();
            tableView.getSortOrder().add(column);
        });

        clearSortItem.setOnAction(e -> tableView.getSortOrder().clear());

        // 筛选组
        MenuItem filterByColumnItem = createMenuItem("🔍 筛选此列...", "按此列值筛选");
        MenuItem showUniqueValuesItem = createMenuItem("📊 查看唯一值", "显示此列所有唯一值");
        MenuItem statisticsItem = createMenuItem("📈 列统计", "显示此列统计信息");

        filterByColumnItem.setOnAction(e -> {
            if (callbacks.onFilterColumn != null) {
                callbacks.onFilterColumn.accept(column);
            }
        });

        showUniqueValuesItem.setOnAction(e -> {
            if (callbacks.onShowUniqueValues != null) {
                callbacks.onShowUniqueValues.accept(column);
            }
        });

        statisticsItem.setOnAction(e -> {
            if (callbacks.onShowStatistics != null) {
                callbacks.onShowStatistics.accept(column);
            }
        });

        // 列显示组
        MenuItem hideColumnItem = createMenuItem("👁️ 隐藏此列", "隐藏当前列");
        Menu showColumnsMenu = new Menu("📋 显示列");
        MenuItem autoFitItem = createMenuItem("↔️ 自适应宽度", "自动调整列宽");
        MenuItem resetWidthItem = createMenuItem("🔄 重置列宽", "恢复默认列宽");

        hideColumnItem.setOnAction(e -> column.setVisible(false));

        autoFitItem.setOnAction(e -> {
            if (callbacks.onAutoFitColumn != null) {
                callbacks.onAutoFitColumn.accept(column);
            }
        });

        resetWidthItem.setOnAction(e -> {
            if (callbacks.defaultWidth != null) {
                column.setPrefWidth(callbacks.defaultWidth);
            }
        });

        // 复制列数据
        MenuItem copyColumnItem = createMenuItem("📋 复制此列数据", "复制此列所有值");
        copyColumnItem.setOnAction(e -> {
            StringBuilder sb = new StringBuilder();
            for (S item : tableView.getItems()) {
                Object value = column.getCellData(item);
                if (value != null) {
                    sb.append(value.toString()).append("\n");
                }
            }
            copyToClipboard(sb.toString().trim());
            showToast("已复制 " + tableView.getItems().size() + " 个值");
        });

        // 组装菜单
        menu.getItems().addAll(
            sortAscItem,
            sortDescItem,
            clearSortItem,
            new SeparatorMenuItem(),
            filterByColumnItem,
            showUniqueValuesItem,
            statisticsItem,
            new SeparatorMenuItem(),
            copyColumnItem,
            new SeparatorMenuItem(),
            autoFitItem,
            resetWidthItem,
            hideColumnItem,
            showColumnsMenu
        );

        // 动态更新显示列菜单
        menu.setOnShowing(e -> {
            showColumnsMenu.getItems().clear();
            for (TableColumn<S, ?> col : tableView.getColumns()) {
                CheckMenuItem colItem = new CheckMenuItem(col.getText());
                colItem.setSelected(col.isVisible());
                colItem.setOnAction(event -> col.setVisible(colItem.isSelected()));
                showColumnsMenu.getItems().add(colItem);
            }
        });

        return menu;
    }

    /**
     * 列头菜单回调接口
     */
    public static class ColumnMenuCallbacks<S, T> {
        public Consumer<TableColumn<S, T>> onFilterColumn;
        public Consumer<TableColumn<S, T>> onShowUniqueValues;
        public Consumer<TableColumn<S, T>> onShowStatistics;
        public Consumer<TableColumn<S, T>> onAutoFitColumn;
        public Double defaultWidth;
    }

    // ==================== 文件操作菜单 ====================

    /**
     * 创建文件操作右键菜单
     */
    public static ContextMenu createFileMenu(
            Supplier<File> getSelectedFile,
            FileMenuCallbacks callbacks) {

        ContextMenu menu = new ContextMenu();

        // 打开组
        MenuItem openItem = createMenuItem("📄 打开", "在应用中打开文件");
        MenuItem openWithItem = createMenuItem("🔗 使用其他程序打开", "选择程序打开");
        MenuItem openFolderItem = createMenuItem("📁 打开所在文件夹", "在资源管理器中显示");

        openItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null && callbacks.onOpen != null) {
                callbacks.onOpen.accept(file);
            }
        });

        openWithItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null) {
                openWithDesktop(file.getAbsolutePath());
            }
        });

        openFolderItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null) {
                openInExplorer(file.getAbsolutePath());
            }
        });

        // 复制组
        MenuItem copyPathItem = createMenuItem("📋 复制路径", "复制文件完整路径");
        MenuItem copyNameItem = createMenuItem("📝 复制文件名", "复制文件名");
        MenuItem copyContentItem = createMenuItem("📄 复制内容", "复制文件内容");

        copyPathItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null) {
                copyToClipboard(file.getAbsolutePath());
                showToast("已复制路径");
            }
        });

        copyNameItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null) {
                copyToClipboard(file.getName());
                showToast("已复制文件名");
            }
        });

        copyContentItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null && callbacks.onCopyContent != null) {
                callbacks.onCopyContent.accept(file);
            }
        });

        // 分析组
        MenuItem analyzeItem = createMenuItem("🔬 分析文件", "分析文件结构和内容");
        MenuItem viewReferencesItem = createMenuItem("🔗 查看引用关系", "查看此文件的引用");
        MenuItem compareItem = createMenuItem("⚖️ 对比文件", "与其他文件对比");

        analyzeItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null && callbacks.onAnalyze != null) {
                callbacks.onAnalyze.accept(file);
            }
        });

        viewReferencesItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null && callbacks.onViewReferences != null) {
                callbacks.onViewReferences.accept(file);
            }
        });

        compareItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null && callbacks.onCompare != null) {
                callbacks.onCompare.accept(file);
            }
        });

        // 导出组
        MenuItem exportItem = createMenuItem("📤 导出", "导出文件数据");
        exportItem.setOnAction(e -> {
            File file = getSelectedFile.get();
            if (file != null && callbacks.onExport != null) {
                callbacks.onExport.accept(file);
            }
        });

        // 组装菜单
        menu.getItems().addAll(
            openItem,
            openWithItem,
            openFolderItem,
            new SeparatorMenuItem(),
            copyPathItem,
            copyNameItem,
            copyContentItem,
            new SeparatorMenuItem(),
            analyzeItem,
            viewReferencesItem,
            compareItem,
            new SeparatorMenuItem(),
            exportItem
        );

        // 动态启用/禁用
        menu.setOnShowing(e -> {
            File file = getSelectedFile.get();
            boolean hasFile = file != null && file.exists();

            openItem.setDisable(!hasFile);
            openWithItem.setDisable(!hasFile);
            openFolderItem.setDisable(!hasFile);
            copyPathItem.setDisable(!hasFile);
            copyNameItem.setDisable(!hasFile);
            copyContentItem.setDisable(!hasFile || callbacks.onCopyContent == null);
            analyzeItem.setDisable(!hasFile || callbacks.onAnalyze == null);
            viewReferencesItem.setDisable(!hasFile || callbacks.onViewReferences == null);
            compareItem.setDisable(!hasFile || callbacks.onCompare == null);
            exportItem.setDisable(!hasFile || callbacks.onExport == null);
        });

        return menu;
    }

    /**
     * 文件菜单回调接口
     */
    public static class FileMenuCallbacks {
        public Consumer<File> onOpen;
        public Consumer<File> onCopyContent;
        public Consumer<File> onAnalyze;
        public Consumer<File> onViewReferences;
        public Consumer<File> onCompare;
        public Consumer<File> onExport;
    }

    // ==================== 工具方法 ====================

    /**
     * 创建带图标和提示的菜单项
     */
    public static MenuItem createMenuItem(String text, String tooltip) {
        MenuItem item = new MenuItem(text);
        if (tooltip != null && !tooltip.isEmpty()) {
            // 菜单项不支持直接设置tooltip，但可以通过graphic实现
            Tooltip.install(item.getGraphic(), new Tooltip(tooltip));
        }
        return item;
    }

    /**
     * 创建带快捷键的菜单项
     */
    public static MenuItem createMenuItem(String text, String tooltip, String accelerator) {
        MenuItem item = createMenuItem(text, tooltip);
        if (accelerator != null && !accelerator.isEmpty()) {
            item.setAccelerator(javafx.scene.input.KeyCombination.valueOf(accelerator));
        }
        return item;
    }

    /**
     * 复制文本到剪贴板
     */
    public static void copyToClipboard(String text) {
        if (text != null && !text.isEmpty()) {
            Platform.runLater(() -> {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                clipboard.setContent(content);
            });
        }
    }

    /**
     * 在资源管理器中打开文件所在目录并选中文件
     */
    public static void openInExplorer(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                log.warn("文件不存在: {}", path);
                showError("打开失败", "文件不存在: " + path);
                return;
            }

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows: 使用 explorer /select 选中文件
                if (file.isDirectory()) {
                    Runtime.getRuntime().exec(new String[]{"explorer.exe", file.getAbsolutePath()});
                } else {
                    Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", file.getAbsolutePath()});
                }
            } else if (os.contains("mac")) {
                // macOS: 使用 open -R 选中文件
                Runtime.getRuntime().exec(new String[]{"open", "-R", file.getAbsolutePath()});
            } else {
                // Linux: 尝试使用 xdg-open 打开父目录
                File folder = file.isDirectory() ? file : file.getParentFile();
                if (folder != null) {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", folder.getAbsolutePath()});
                }
            }
        } catch (Exception e) {
            log.error("打开资源管理器失败: {}", path, e);
            showError("打开失败", "无法打开资源管理器: " + e.getMessage());
        }
    }

    /**
     * 使用系统默认程序打开文件
     */
    public static void openWithDesktop(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            log.error("打开文件失败: {}", path, e);
            showError("打开失败", "无法打开文件: " + e.getMessage());
        }
    }

    /**
     * 递归展开/折叠树节点
     */
    public static <T> void expandRecursively(TreeItem<T> item, boolean expand) {
        if (item == null) return;
        item.setExpanded(expand);
        for (TreeItem<T> child : item.getChildren()) {
            expandRecursively(child, expand);
        }
    }

    /**
     * 显示简短提示消息
     */
    public static void showToast(String message) {
        log.info(message);
        // 可以扩展为实际的Toast提示
    }

    /**
     * 显示错误对话框
     */
    public static void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 显示信息对话框
     */
    public static void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 导出数据到文件
     */
    public static void exportToFile(Window owner, String defaultName, String content, String... extensions) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("导出文件");
        fileChooser.setInitialFileName(defaultName);

        if (extensions != null && extensions.length > 0) {
            for (String ext : extensions) {
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(ext.toUpperCase() + " 文件", "*." + ext)
                );
            }
        }

        File file = fileChooser.showSaveDialog(owner);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
                showToast("已导出到: " + file.getName());
            } catch (Exception e) {
                log.error("导出文件失败", e);
                showError("导出失败", "无法导出文件: " + e.getMessage());
            }
        }
    }

    /**
     * 格式化Map为表格字符串
     */
    public static String formatMapAsTable(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        int maxKeyLen = map.keySet().stream().mapToInt(String::length).max().orElse(10);

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(String.format("%-" + maxKeyLen + "s : %s%n",
                entry.getKey(), entry.getValue()));
        }

        return sb.toString();
    }

    /**
     * 格式化List为CSV字符串
     */
    public static <T> String formatListAsCsv(List<T> list, java.util.function.Function<T, String[]> rowMapper, String[] headers) {
        StringBuilder sb = new StringBuilder();

        // 写入表头
        if (headers != null) {
            sb.append(String.join(",", headers)).append("\n");
        }

        // 写入数据
        for (T item : list) {
            String[] row = rowMapper.apply(item);
            sb.append(String.join(",", row)).append("\n");
        }

        return sb.toString();
    }
}
