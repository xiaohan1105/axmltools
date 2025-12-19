package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.function.Consumer;

/**
 * 机制颜色图例
 *
 * 显示所有机制类型的颜色对照图，支持：
 * - 颜色块 + 图标 + 名称
 * - 点击快速过滤
 * - 悬停显示描述
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class MechanismColorLegend extends VBox {

    /** 机制过滤回调 */
    private Consumer<AionMechanismCategory> onMechanismFilter;

    /** 是否使用紧凑模式 */
    private boolean compactMode = false;

    public MechanismColorLegend() {
        initUI();
    }

    public MechanismColorLegend(boolean compactMode) {
        this.compactMode = compactMode;
        initUI();
    }

    private void initUI() {
        setSpacing(compactMode ? 4 : 8);
        setPadding(new Insets(10));
        setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #e9ecef;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;"
        );

        // 标题
        Label title = new Label("🎨 机制颜色对照");
        title.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #343a40;");
        getChildren().add(title);

        // 分隔线
        Region separator = new Region();
        separator.setStyle("-fx-background-color: #e9ecef;");
        separator.setPrefHeight(1);
        separator.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(separator);

        // 图例内容
        if (compactMode) {
            // 紧凑模式：使用 FlowPane
            FlowPane flowPane = new FlowPane();
            flowPane.setHgap(8);
            flowPane.setVgap(4);
            flowPane.setPadding(new Insets(4, 0, 0, 0));

            for (AionMechanismCategory category : AionMechanismCategory.values()) {
                if (category != AionMechanismCategory.OTHER) {
                    flowPane.getChildren().add(createCompactLegendItem(category));
                }
            }
            // OTHER 放最后
            flowPane.getChildren().add(createCompactLegendItem(AionMechanismCategory.OTHER));

            getChildren().add(flowPane);
        } else {
            // 完整模式：使用 GridPane
            GridPane grid = new GridPane();
            grid.setHgap(16);
            grid.setVgap(6);
            grid.setPadding(new Insets(4, 0, 0, 0));

            AionMechanismCategory[] categories = AionMechanismCategory.values();
            int columns = 2;
            int row = 0;
            int col = 0;

            for (AionMechanismCategory category : categories) {
                grid.add(createLegendItem(category), col, row);
                col++;
                if (col >= columns) {
                    col = 0;
                    row++;
                }
            }

            getChildren().add(grid);
        }
    }

    /**
     * 创建图例项
     */
    private HBox createLegendItem(AionMechanismCategory category) {
        HBox item = new HBox(6);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(2, 4, 2, 4));
        item.setStyle("-fx-cursor: hand;");

        // 颜色块
        Rectangle colorRect = new Rectangle(16, 16);
        colorRect.setArcWidth(4);
        colorRect.setArcHeight(4);
        try {
            colorRect.setFill(Color.web(category.getColor()));
            colorRect.setStroke(Color.web(category.getColor()).darker());
        } catch (Exception e) {
            colorRect.setFill(Color.GRAY);
            colorRect.setStroke(Color.DARKGRAY);
        }
        colorRect.setStrokeWidth(1);

        // 图标
        Label iconLabel = new Label(category.getIcon());
        iconLabel.setStyle("-fx-font-size: 12px;");

        // 名称
        Label nameLabel = new Label(category.getDisplayName());
        try {
            nameLabel.setStyle(String.format(
                "-fx-font-size: 11px; -fx-text-fill: %s;",
                darkenColor(category.getColor(), 0.15)
            ));
        } catch (Exception e) {
            nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #212529;");
        }

        item.getChildren().addAll(colorRect, iconLabel, nameLabel);

        // 悬停提示
        Tooltip tooltip = new Tooltip(
            category.getIcon() + " " + category.getDisplayName() + "\n" +
            category.getDescription() + "\n\n" +
            "点击过滤此机制的文件"
        );
        tooltip.setStyle("-fx-font-size: 11px;");
        Tooltip.install(item, tooltip);

        // 悬停效果
        item.setOnMouseEntered(e -> item.setStyle(
            "-fx-cursor: hand; -fx-background-color: " + lightenColor(category.getColor(), 0.9) + ";" +
            "-fx-background-radius: 4;"
        ));
        item.setOnMouseExited(e -> item.setStyle("-fx-cursor: hand;"));

        // 点击事件
        item.setOnMouseClicked(e -> {
            if (onMechanismFilter != null) {
                onMechanismFilter.accept(category);
            }
        });

        return item;
    }

    /**
     * 创建紧凑图例项
     */
    private HBox createCompactLegendItem(AionMechanismCategory category) {
        HBox item = new HBox(3);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(2, 6, 2, 4));
        item.setStyle("-fx-cursor: hand;");

        // 小颜色块
        Rectangle colorRect = new Rectangle(10, 10);
        colorRect.setArcWidth(2);
        colorRect.setArcHeight(2);
        try {
            colorRect.setFill(Color.web(category.getColor()));
        } catch (Exception e) {
            colorRect.setFill(Color.GRAY);
        }

        // 图标
        Label iconLabel = new Label(category.getIcon());
        iconLabel.setStyle("-fx-font-size: 10px;");

        item.getChildren().addAll(colorRect, iconLabel);

        // 悬停提示
        Tooltip tooltip = new Tooltip(category.getDisplayName() + "\n" + category.getDescription());
        tooltip.setStyle("-fx-font-size: 10px;");
        Tooltip.install(item, tooltip);

        // 悬停效果
        item.setOnMouseEntered(e -> item.setStyle(
            "-fx-cursor: hand; -fx-background-color: " + lightenColor(category.getColor(), 0.85) + ";" +
            "-fx-background-radius: 4;"
        ));
        item.setOnMouseExited(e -> item.setStyle("-fx-cursor: hand;"));

        // 点击事件
        item.setOnMouseClicked(e -> {
            if (onMechanismFilter != null) {
                onMechanismFilter.accept(category);
            }
        });

        return item;
    }

    /**
     * 颜色变浅
     */
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

    /**
     * 颜色变深
     */
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

    /**
     * 设置机制过滤回调
     */
    public void setOnMechanismFilter(Consumer<AionMechanismCategory> callback) {
        this.onMechanismFilter = callback;
    }
}
