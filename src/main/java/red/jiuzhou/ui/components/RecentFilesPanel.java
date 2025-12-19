package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.ui.components.DesignerWorkflowTracker.FileAccessRecord;

import java.util.List;
import java.util.function.Consumer;

/**
 * 最近访问文件面板
 *
 * 显示设计师最近访问的文件列表，支持：
 * - 按时间排序显示
 * - 机制颜色标记
 * - 快速跳转到文件
 * - 会话统计信息
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class RecentFilesPanel extends VBox {

    /** 文件列表 */
    private ListView<FileAccessRecord> fileList;

    /** 统计标签 */
    private Label statsLabel;

    /** 文件点击回调 */
    private Consumer<String> onFileClicked;

    /** 机制过滤回调 */
    private Consumer<AionMechanismCategory> onMechanismFilter;

    /** 显示的最大文件数 */
    private int maxDisplayCount = 15;

    public RecentFilesPanel() {
        initUI();
        bindToTracker();
    }

    private void initUI() {
        setSpacing(0);
        setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #e9ecef;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;"
        );

        // 标题栏
        HBox titleBar = createTitleBar();

        // 文件列表
        fileList = new ListView<>();
        fileList.setPrefHeight(300);
        fileList.setCellFactory(lv -> new RecentFileCell());
        fileList.setPlaceholder(new Label("暂无访问记录"));
        VBox.setVgrow(fileList, Priority.ALWAYS);

        // 点击事件
        fileList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                FileAccessRecord selected = fileList.getSelectionModel().getSelectedItem();
                if (selected != null && onFileClicked != null) {
                    onFileClicked.accept(selected.getFilePath());
                }
            }
        });

        // 统计栏
        HBox statsBar = createStatsBar();

        getChildren().addAll(titleBar, fileList, statsBar);
    }

    /**
     * 创建标题栏
     */
    private HBox createTitleBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 8, 10));
        bar.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef);" +
            "-fx-border-color: #dee2e6;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Label icon = new Label("📋");
        icon.setStyle("-fx-font-size: 14px;");

        Label title = new Label("最近访问");
        title.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #343a40;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button("清除");
        clearBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #6c757d;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;"
        );
        clearBtn.setOnAction(e -> {
            DesignerWorkflowTracker.getInstance().clearHistory();
        });

        bar.getChildren().addAll(icon, title, spacer, clearBtn);
        return bar;
    }

    /**
     * 创建统计栏
     */
    private HBox createStatsBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 10, 6, 10));
        bar.setStyle(
            "-fx-background-color: #f8f9fa;" +
            "-fx-border-color: #e9ecef;" +
            "-fx-border-width: 1 0 0 0;"
        );

        statsLabel = new Label("会话统计");
        statsLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");

        bar.getChildren().add(statsLabel);
        return bar;
    }

    /**
     * 绑定到工作流跟踪器
     */
    private void bindToTracker() {
        DesignerWorkflowTracker tracker = DesignerWorkflowTracker.getInstance();

        // 监听历史变化
        tracker.addHistoryListener(this::updateFileList);

        // 初始加载
        updateFileList(tracker.getRecentFiles());
    }

    /**
     * 更新文件列表
     */
    private void updateFileList(List<FileAccessRecord> records) {
        fileList.getItems().clear();

        int count = 0;
        for (FileAccessRecord record : records) {
            if (count >= maxDisplayCount) break;
            fileList.getItems().add(record);
            count++;
        }

        // 更新统计
        updateStats();
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        DesignerWorkflowTracker.WorkflowStats stats =
            DesignerWorkflowTracker.getInstance().getSessionStats();

        StringBuilder sb = new StringBuilder();
        sb.append("访问: ").append(stats.getTotalAccesses()).append(" 次");
        sb.append(" | 文件: ").append(stats.getUniqueFiles()).append(" 个");
        sb.append(" | 时长: ").append(stats.getSessionDuration());

        statsLabel.setText(sb.toString());
    }

    /**
     * 设置文件点击回调
     */
    public void setOnFileClicked(Consumer<String> callback) {
        this.onFileClicked = callback;
    }

    /**
     * 设置机制过滤回调
     */
    public void setOnMechanismFilter(Consumer<AionMechanismCategory> callback) {
        this.onMechanismFilter = callback;
    }

    /**
     * 设置显示的最大文件数
     */
    public void setMaxDisplayCount(int count) {
        this.maxDisplayCount = count;
        updateFileList(DesignerWorkflowTracker.getInstance().getRecentFiles());
    }

    /**
     * 最近文件单元格
     */
    private class RecentFileCell extends ListCell<FileAccessRecord> {
        @Override
        protected void updateItem(FileAccessRecord item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            HBox container = new HBox(8);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(4, 8, 4, 8));

            // 时间标签
            Label timeLabel = new Label(item.getFormattedTime());
            timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #868e96;");
            timeLabel.setMinWidth(50);

            // 机制颜色条
            Region colorBar = new Region();
            colorBar.setMinWidth(3);
            colorBar.setMaxWidth(3);
            colorBar.setMinHeight(20);
            try {
                colorBar.setStyle(String.format(
                    "-fx-background-color: %s; -fx-background-radius: 2;",
                    item.getMechanismColor()
                ));
            } catch (Exception e) {
                colorBar.setStyle("-fx-background-color: #6c757d; -fx-background-radius: 2;");
            }

            // 机制图标
            Label mechIcon = new Label(item.getMechanismIcon());
            mechIcon.setStyle("-fx-font-size: 11px;");

            // 文件名
            Label nameLabel = new Label(item.getFileName());
            nameLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            // 根据机制着色文件名
            try {
                String color = darkenColor(item.getMechanismColor(), 0.15);
                nameLabel.setStyle(String.format(
                    "-fx-font-size: 11px; -fx-text-fill: %s;",
                    color
                ));
            } catch (Exception e) {
                nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #212529;");
            }

            // 机制按钮（点击过滤）
            Button mechBtn = new Button(item.getMechanism().getDisplayName());
            mechBtn.setStyle(String.format(
                "-fx-font-size: 9px;" +
                "-fx-background-color: %s;" +
                "-fx-text-fill: %s;" +
                "-fx-background-radius: 8;" +
                "-fx-padding: 1 6;" +
                "-fx-cursor: hand;",
                lightenColor(item.getMechanismColor(), 0.85),
                item.getMechanismColor()
            ));
            mechBtn.setOnAction(e -> {
                if (onMechanismFilter != null) {
                    onMechanismFilter.accept(item.getMechanism());
                }
            });

            container.getChildren().addAll(timeLabel, colorBar, mechIcon, nameLabel, mechBtn);

            // 悬停提示
            Tooltip tooltip = new Tooltip(
                item.getFileName() + "\n" +
                "机制: " + item.getMechanism().getDisplayName() + "\n" +
                "路径: " + item.getFilePath() + "\n\n" +
                "双击打开文件"
            );
            tooltip.setStyle("-fx-font-size: 11px;");
            setTooltip(tooltip);

            setGraphic(container);
            setText(null);
        }

        private String lightenColor(String hexColor, double factor) {
            try {
                Color color = Color.web(hexColor);
                double r = color.getRed() + (1 - color.getRed()) * factor;
                double g = color.getGreen() + (1 - color.getGreen()) * factor;
                double b = color.getBlue() + (1 - color.getBlue()) * factor;
                return String.format("#%02X%02X%02X",
                    (int)(r * 255), (int)(g * 255), (int)(b * 255));
            } catch (Exception e) {
                return "#f8f9fa";
            }
        }

        private String darkenColor(String hexColor, double factor) {
            try {
                Color color = Color.web(hexColor);
                double r = color.getRed() * (1 - factor);
                double g = color.getGreen() * (1 - factor);
                double b = color.getBlue() * (1 - factor);
                return String.format("#%02X%02X%02X",
                    (int)(r * 255), (int)(g * 255), (int)(b * 255));
            } catch (Exception e) {
                return "#212529";
            }
        }
    }
}
