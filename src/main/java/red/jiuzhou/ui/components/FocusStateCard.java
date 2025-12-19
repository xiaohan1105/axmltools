package red.jiuzhou.ui.components;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.function.Consumer;

/**
 * 焦点状态卡片
 *
 * 当用户选择某个机制时，显示一个精美的状态卡片：
 * - 机制名称和图标
 * - 机制描述
 * - 匹配文件数量
 * - 快速操作按钮
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class FocusStateCard extends VBox {

    /** 当前焦点机制 */
    private AionMechanismCategory currentMechanism;

    /** 文件数量 */
    private int fileCount;

    /** 清除焦点回调 */
    private Consumer<Void> onClearFocus;

    /** 在浏览器中打开回调 */
    private Consumer<AionMechanismCategory> onOpenInExplorer;

    // UI组件
    private HBox headerBox;
    private Circle colorIndicator;
    private Label iconLabel;
    private Label nameLabel;
    private Label descLabel;
    private Label statsLabel;
    private Button clearBtn;
    private Button exploreBtn;

    /** 动画时长 */
    private static final Duration ANIMATION_DURATION = Duration.millis(250);

    public FocusStateCard() {
        initUI();
        setVisible(false);
        setManaged(false);
    }

    private void initUI() {
        setSpacing(8);
        setPadding(new Insets(12, 16, 12, 16));
        setAlignment(Pos.CENTER_LEFT);

        // 默认样式
        setStyle(
            "-fx-background-color: linear-gradient(to right, #f8f9fa, #ffffff);" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #e9ecef;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;"
        );

        // 头部：颜色指示器 + 图标 + 名称
        headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        colorIndicator = new Circle(8);
        colorIndicator.setFill(Color.GRAY);
        colorIndicator.setStroke(Color.DARKGRAY);
        colorIndicator.setStrokeWidth(1);

        iconLabel = new Label("");
        iconLabel.setStyle("-fx-font-size: 18px;");

        nameLabel = new Label("机制名称");
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #212529;");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        // 统计标签
        statsLabel = new Label("0 个文件");
        statsLabel.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-text-fill: white;" +
            "-fx-background-color: #6c757d;" +
            "-fx-background-radius: 10;" +
            "-fx-padding: 3 10;"
        );

        headerBox.getChildren().addAll(colorIndicator, iconLabel, nameLabel, headerSpacer, statsLabel);

        // 描述
        descLabel = new Label("机制描述");
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
        descLabel.setWrapText(true);

        // 操作按钮
        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        clearBtn = new Button("✕ 清除焦点");
        clearBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #dc3545;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10;" +
            "-fx-border-color: #dc3545;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;"
        );
        clearBtn.setOnAction(e -> {
            if (onClearFocus != null) {
                onClearFocus.accept(null);
            }
        });
        setupButtonHover(clearBtn, "#dc3545");

        exploreBtn = new Button("📊 在浏览器中打开");
        exploreBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #007bff;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10;" +
            "-fx-border-color: #007bff;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;"
        );
        exploreBtn.setOnAction(e -> {
            if (onOpenInExplorer != null && currentMechanism != null) {
                onOpenInExplorer.accept(currentMechanism);
            }
        });
        setupButtonHover(exploreBtn, "#007bff");

        buttonBox.getChildren().addAll(clearBtn, exploreBtn);

        getChildren().addAll(headerBox, descLabel, buttonBox);
    }

    /**
     * 设置按钮悬停效果
     */
    private void setupButtonHover(Button btn, String color) {
        String normalStyle = String.format(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: %s;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10;" +
            "-fx-border-color: %s;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;",
            color, color
        );

        String hoverStyle = String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10;" +
            "-fx-border-color: %s;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;",
            color, color
        );

        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
    }

    /**
     * 显示焦点状态
     */
    public void showFocus(AionMechanismCategory mechanism, int fileCount) {
        if (mechanism == null) {
            hideFocus();
            return;
        }

        this.currentMechanism = mechanism;
        this.fileCount = fileCount;

        // 更新UI
        String color = mechanism.getColor();

        try {
            colorIndicator.setFill(Color.web(color));
            colorIndicator.setStroke(Color.web(color).darker());

            // 添加发光效果
            DropShadow glow = new DropShadow();
            glow.setColor(Color.web(color, 0.5));
            glow.setRadius(8);
            glow.setSpread(0.3);
            colorIndicator.setEffect(glow);
        } catch (Exception e) {
            colorIndicator.setFill(Color.GRAY);
            colorIndicator.setStroke(Color.DARKGRAY);
        }

        iconLabel.setText(mechanism.getIcon());
        nameLabel.setText(mechanism.getDisplayName());
        nameLabel.setStyle(String.format(
            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: %s;",
            color
        ));

        descLabel.setText(mechanism.getDescription());

        statsLabel.setText(fileCount + " 个文件");
        statsLabel.setStyle(String.format(
            "-fx-font-size: 11px;" +
            "-fx-text-fill: white;" +
            "-fx-background-color: %s;" +
            "-fx-background-radius: 10;" +
            "-fx-padding: 3 10;",
            color
        ));

        // 更新卡片边框颜色
        setStyle(String.format(
            "-fx-background-color: linear-gradient(to right, %s, #ffffff);" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: %s;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1 1 1 3;",
            lightenColor(color, 0.95),
            lightenColor(color, 0.6)
        ));

        // 显示动画
        if (!isVisible()) {
            setOpacity(0);
            setVisible(true);
            setManaged(true);

            FadeTransition fadeIn = new FadeTransition(ANIMATION_DURATION, this);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            TranslateTransition slide = new TranslateTransition(ANIMATION_DURATION, this);
            slide.setFromY(-10);
            slide.setToY(0);

            ParallelTransition parallel = new ParallelTransition(fadeIn, slide);
            parallel.play();
        }

        // 脉冲动画强调
        playPulseAnimation();
    }

    /**
     * 隐藏焦点状态
     */
    public void hideFocus() {
        if (!isVisible()) return;

        FadeTransition fadeOut = new FadeTransition(ANIMATION_DURATION, this);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            setVisible(false);
            setManaged(false);
        });
        fadeOut.play();

        currentMechanism = null;
    }

    /**
     * 播放脉冲动画
     */
    private void playPulseAnimation() {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), colorIndicator);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.3);
        pulse.setToY(1.3);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.play();
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

    // ==================== Setters ====================

    /**
     * 设置清除焦点回调
     */
    public void setOnClearFocus(Consumer<Void> callback) {
        this.onClearFocus = callback;
    }

    /**
     * 设置在浏览器中打开回调
     */
    public void setOnOpenInExplorer(Consumer<AionMechanismCategory> callback) {
        this.onOpenInExplorer = callback;
    }

    /**
     * 获取当前焦点机制
     */
    public AionMechanismCategory getCurrentMechanism() {
        return currentMechanism;
    }

    /**
     * 更新文件数量
     */
    public void updateFileCount(int count) {
        this.fileCount = count;
        if (currentMechanism != null) {
            statsLabel.setText(count + " 个文件");
        }
    }
}
