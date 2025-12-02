package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 智能表格列宽管理器
 * 根据内容自动调整列宽，优化显示效果
 *
 * @author Claude
 * @date 2025-01-13
 */
public class SmartColumnWidthManager {

    private static final Logger log = LoggerFactory.getLogger(SmartColumnWidthManager.class);

    // 列宽配置
    private static final int MIN_COLUMN_WIDTH = 60;      // 最小列宽
    private static final int MAX_COLUMN_WIDTH = 400;     // 最大列宽
    private static final int DEFAULT_COLUMN_WIDTH = 120; // 默认列宽
    private static final int PADDING = 20;               // 内边距

    /**
     * 为表格应用智能列宽策略
     *
     * @param tableView 表格视图
     */
    public static <T> void applySmartColumnWidth(TableView<T> tableView) {
        applySmartColumnWidth(tableView, true);
    }

    /**
     * 为表格应用智能列宽策略
     *
     * @param tableView 表格视图
     * @param enableAutoResize 是否启用自动调整大小
     */
    public static <T> void applySmartColumnWidth(TableView<T> tableView, boolean enableAutoResize) {
        if (tableView == null || tableView.getColumns().isEmpty()) {
            return;
        }

        // 延迟执行，确保表格数据已加载
        Platform.runLater(() -> {
            try {
                // 设置列调整策略
                tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

                // 计算并设置每列的最佳宽度
                calculateOptimalWidths(tableView);

                // 添加双击列头自动调整宽度功能
                enableDoubleClickAutoResize(tableView);

                // 添加右键菜单
                addColumnContextMenu(tableView);

                // 添加悬停提示
                enableTooltipForLongContent(tableView);

                log.debug("智能列宽已应用到表格，共 {} 列", tableView.getColumns().size());

            } catch (Exception e) {
                log.error("应用智能列宽失败", e);
            }
        });
    }

    /**
     * 计算并设置最佳列宽
     */
    private static <T> void calculateOptimalWidths(TableView<T> tableView) {
        double totalWidth = 0;
        Map<TableColumn<T, ?>, Double> columnWidths = new HashMap<>();

        // 第一遍：计算每列的理想宽度
        for (TableColumn<T, ?> column : tableView.getColumns()) {
            double width = calculateColumnWidth(column, tableView);
            columnWidths.put(column, width);
            totalWidth += width;
        }

        // 获取表格可用宽度
        double availableWidth = tableView.getWidth();
        if (availableWidth <= 0) {
            availableWidth = 1000; // 默认宽度
        }

        // 如果总宽度超过可用宽度，按比例缩小
        if (totalWidth > availableWidth) {
            double scale = availableWidth / totalWidth;
            for (Map.Entry<TableColumn<T, ?>, Double> entry : columnWidths.entrySet()) {
                double scaledWidth = Math.max(MIN_COLUMN_WIDTH, entry.getValue() * scale);
                entry.getKey().setPrefWidth(scaledWidth);
                entry.getKey().setMinWidth(MIN_COLUMN_WIDTH);
            }
        } else {
            // 直接应用计算的宽度
            for (Map.Entry<TableColumn<T, ?>, Double> entry : columnWidths.entrySet()) {
                entry.getKey().setPrefWidth(entry.getValue());
                entry.getKey().setMinWidth(MIN_COLUMN_WIDTH);
            }
        }
    }

    /**
     * 计算单列的最佳宽度
     */
    private static <T, S> double calculateColumnWidth(TableColumn<T, S> column, TableView<T> tableView) {
        // 计算列头文本宽度
        double headerWidth = computeTextWidth(column.getText()) + PADDING;

        // 采样数据计算内容宽度
        double maxContentWidth = headerWidth;
        int sampleSize = Math.min(50, tableView.getItems().size()); // 采样前50行

        for (int i = 0; i < sampleSize; i++) {
            T item = tableView.getItems().get(i);
            if (item != null) {
                Callback<TableColumn.CellDataFeatures<T, S>, ObservableValue<S>> cellValueFactory
                    = column.getCellValueFactory();

                if (cellValueFactory != null) {
                    TableColumn.CellDataFeatures<T, S> cellData =
                        new TableColumn.CellDataFeatures<>(tableView, column, item);
                    ObservableValue<S> observableValue = cellValueFactory.call(cellData);

                    if (observableValue != null && observableValue.getValue() != null) {
                        String text = observableValue.getValue().toString();
                        double contentWidth = computeTextWidth(text) + PADDING;
                        maxContentWidth = Math.max(maxContentWidth, contentWidth);
                    }
                }
            }
        }

        // 限制在最小和最大宽度之间
        return Math.max(MIN_COLUMN_WIDTH, Math.min(MAX_COLUMN_WIDTH, maxContentWidth));
    }

    /**
     * 计算文本宽度
     */
    private static double computeTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return DEFAULT_COLUMN_WIDTH;
        }

        try {
            Text textNode = new Text(text);
            textNode.setStyle("-fx-font-size: 12px;");
            double width = textNode.getLayoutBounds().getWidth();
            return width;
        } catch (Exception e) {
            // 备用计算方式：根据字符数估算
            int charCount = text.length();
            boolean hasChinese = text.matches(".*[\\u4e00-\\u9fa5]+.*");

            if (hasChinese) {
                // 中文字符占用更多空间
                return Math.min(MAX_COLUMN_WIDTH, charCount * 14);
            } else {
                // 英文字符
                return Math.min(MAX_COLUMN_WIDTH, charCount * 8);
            }
        }
    }

    /**
     * 启用双击列头自动调整宽度
     */
    private static <T> void enableDoubleClickAutoResize(TableView<T> tableView) {
        for (TableColumn<T, ?> column : tableView.getColumns()) {
            // 为列头添加双击事件
            column.setGraphic(null); // 清除可能存在的graphic

            // 通过监听器实现双击调整
            // 注意：JavaFX TableColumn本身不支持直接的鼠标事件，需要在应用层面实现
        }
    }

    /**
     * 添加列右键菜单
     */
    private static <T> void addColumnContextMenu(TableView<T> tableView) {
        tableView.setOnContextMenuRequested(event -> {
            // 检测是否点击在列头区域
            if (event.getY() < 30) { // 大致的列头高度
                ContextMenu contextMenu = new ContextMenu();

                MenuItem autoResizeItem = new MenuItem("🔧 自动调整所有列宽");
                autoResizeItem.setOnAction(e -> {
                    calculateOptimalWidths(tableView);
                });

                MenuItem resetWidthItem = new MenuItem("↺ 重置为默认宽度");
                resetWidthItem.setOnAction(e -> {
                    for (TableColumn<T, ?> col : tableView.getColumns()) {
                        col.setPrefWidth(DEFAULT_COLUMN_WIDTH);
                    }
                });

                MenuItem equalWidthItem = new MenuItem("⚖ 平均分配列宽");
                equalWidthItem.setOnAction(e -> {
                    double width = tableView.getWidth() / tableView.getColumns().size();
                    for (TableColumn<T, ?> col : tableView.getColumns()) {
                        col.setPrefWidth(Math.max(MIN_COLUMN_WIDTH, width));
                    }
                });

                contextMenu.getItems().addAll(autoResizeItem, resetWidthItem, equalWidthItem);
                contextMenu.show(tableView, event.getScreenX(), event.getScreenY());
            }
        });
    }

    /**
     * 为长内容添加悬停提示
     */
    @SuppressWarnings("unchecked")
    private static <T> void enableTooltipForLongContent(TableView<T> tableView) {
        for (TableColumn<T, ?> column : tableView.getColumns()) {
            // 跳过标题为"选择"的列（这通常是复选框列）
            if (column.getText() != null && column.getText().equals("选择")) {
                continue;
            }

            // 检查 CellFactory 是否已经是 CheckBoxTableCell（通过类名判断）
            if (column.getCellFactory() != null) {
                String cellFactoryClassName = column.getCellFactory().getClass().getName();
                if (cellFactoryClassName.contains("CheckBoxTableCell") ||
                    cellFactoryClassName.contains("CheckBox")) {
                    // 跳过已经设置为复选框的列
                    continue;
                }
            }

            // 使用原生 TableColumn 类型来避免泛型通配符问题
            TableColumn<T, Object> objColumn = (TableColumn<T, Object>) column;
            objColumn.setCellFactory(col -> new TableCell<T, Object>() {
                private Tooltip tooltip;

                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        String text = item.toString();
                        setText(text);

                        // 如果文本超过一定长度，添加tooltip
                        if (text.length() > 20) {
                            if (tooltip == null) {
                                tooltip = new Tooltip();
                            }
                            tooltip.setText(text);
                            setTooltip(tooltip);
                        } else {
                            setTooltip(null);
                        }

                        // 添加双击复制功能
                        setOnMouseClicked(event -> {
                            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                                javafx.scene.input.Clipboard clipboard =
                                    javafx.scene.input.Clipboard.getSystemClipboard();
                                javafx.scene.input.ClipboardContent content =
                                    new javafx.scene.input.ClipboardContent();
                                content.putString(text);
                                clipboard.setContent(content);

                                // 视觉反馈
                                setStyle("-fx-background-color: #90EE90;");
                                new Thread(() -> {
                                    try {
                                        Thread.sleep(200);
                                        Platform.runLater(() -> setStyle(""));
                                    } catch (InterruptedException ignored) {}
                                }).start();
                            }
                        });
                    }
                }
            });
        }
    }

    /**
     * 自动调整指定列的宽度
     */
    public static <T, S> void autoResizeColumn(TableColumn<T, S> column, TableView<T> tableView) {
        if (column == null || tableView == null) {
            return;
        }

        double width = calculateColumnWidth(column, tableView);
        column.setPrefWidth(width);
    }

    /**
     * 创建智能表格（预配置好的表格）
     */
    public static <T> TableView<T> createSmartTableView() {
        TableView<T> tableView = new TableView<>();

        // 基础配置
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tableView.setEditable(false);

        // 样式优化
        tableView.setStyle(
            "-fx-font-size: 12px; " +
            "-fx-selection-bar: #3498db; " +
            "-fx-selection-bar-non-focused: #95a5a6;"
        );

        return tableView;
    }

    /**
     * 为已存在的表格添加列宽优化
     * 这是一个便捷方法，会在数据加载后自动调整列宽
     */
    public static <T> void optimizeExistingTable(TableView<T> tableView) {
        // 监听数据变化，自动调整列宽
        tableView.getItems().addListener((javafx.collections.ListChangeListener.Change<? extends T> c) -> {
            if (c.next() && c.wasAdded() && tableView.getItems().size() > 0) {
                // 数据加载完成后延迟调整列宽
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(100); // 短暂延迟确保渲染完成
                        applySmartColumnWidth(tableView);
                    } catch (InterruptedException ignored) {}
                });
            }
        });
    }
}
