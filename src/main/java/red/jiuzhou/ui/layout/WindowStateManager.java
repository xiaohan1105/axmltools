package red.jiuzhou.ui.layout;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.prefs.Preferences;

/**
 * 窗口状态管理器
 * 负责窗口状态的持久化、恢复和智能调整
 *
 * @author Claude Code Enhanced
 * @version 2.0
 */
public class WindowStateManager {

    private static final Logger log = LoggerFactory.getLogger(WindowStateManager.class);
    private static final String PREF_NODE = "dbxmltool_window_state";
    private static final String PREF_X = "window_x";
    private static final String PREF_Y = "window_y";
    private static final String PREF_WIDTH = "window_width";
    private static final String PREF_HEIGHT = "window_height";
    private static final String PREF_MAXIMIZED = "window_maximized";
    private static final String PREF_DIVIDER_POS = "divider_position";

    private final Stage stage;
    private final Preferences preferences;
    private final ResponsiveLayoutManager layoutManager;

    // 窗口属性
    private final DoubleProperty x = new SimpleDoubleProperty();
    private final DoubleProperty y = new SimpleDoubleProperty();
    private final DoubleProperty width = new SimpleDoubleProperty();
    private final DoubleProperty height = new SimpleDoubleProperty();
    private final BooleanProperty maximized = new SimpleBooleanProperty();
    private final DoubleProperty dividerPosition = new SimpleDoubleProperty(0.3);

    // 智能调整配置
    private final BooleanProperty smartResize = new SimpleBooleanProperty(true);
    private final DoubleProperty minWindowWidth = new SimpleDoubleProperty(800);
    private final DoubleProperty minWindowHeight = new SimpleDoubleProperty(600);
    private final DoubleProperty optimalWidth = new SimpleDoubleProperty(1400);
    private final DoubleProperty optimalHeight = new SimpleDoubleProperty(800);

    public WindowStateManager(Stage stage, ResponsiveLayoutManager layoutManager) {
        this.stage = stage;
        this.layoutManager = layoutManager;
        this.preferences = Preferences.userRoot().node(PREF_NODE);

        initializeBindings();
        loadWindowState();
        setupEventHandlers();
    }

    private void initializeBindings() {
        // 绑定窗口属性
        x.bind(stage.xProperty());
        y.bind(stage.yProperty());
        width.bind(stage.widthProperty());
        height.bind(stage.heightProperty());
        maximized.bind(stage.maximizedProperty());

        // 监听分割面板位置变化
        layoutManager.getMainSplitPane().getDividers().get(0).positionProperty()
            .addListener((obs, oldVal, newVal) -> {
                dividerPosition.set(newVal.doubleValue());
                saveWindowStateAsync();
            });

        // 监听窗口尺寸变化进行智能调整
        width.addListener((obs, oldVal, newVal) -> handleWidthChange(oldVal.doubleValue(), newVal.doubleValue()));
        height.addListener((obs, oldVal, newVal) -> handleHeightChange(oldVal.doubleValue(), newVal.doubleValue()));
    }

    private void handleWidthChange(double oldWidth, double newWidth) {
        if (!smartResize.get()) return;

        // 确保窗口最小尺寸
        if (newWidth < minWindowWidth.get()) {
            stage.setWidth(minWindowWidth.get());
            return;
        }

        // 根据内容调整分割面板位置
        adjustDividerByWidth(newWidth);

        // 触发响应式布局检查（使用公共方法）
        layoutManager.updateResponsiveLayout();
    }

    private void handleHeightChange(double oldHeight, double newHeight) {
        if (!smartResize.get()) return;

        // 确保窗口最小尺寸
        if (newHeight < minWindowHeight.get()) {
            stage.setHeight(minWindowHeight.get());
            return;
        }

        // 根据高度调整内容间距
        adjustContentSpacing(newHeight);
    }

    private void adjustDividerByWidth(double width) {
        ResponsiveLayoutManager.ResponsiveLevel level = layoutManager.getCurrentLevel();
        double targetDividerPosition = 0.3;

        switch (level) {
            case MOBILE:
                // 移动设备不需要分割面板
                break;
            case TABLET:
                targetDividerPosition = 0.25;
                break;
            case DESKTOP:
                targetDividerPosition = 0.3;
                break;
            case LARGE:
                targetDividerPosition = 0.28;
                break;
        }

        if (layoutManager.getMainSplitPane().getDividers().size() > 0) {
            layoutManager.getMainSplitPane().setDividerPositions(targetDividerPosition);
        }
    }

    private void adjustContentSpacing(double height) {
        String spacingClass = "spacing-normal";

        if (height < 700) {
            spacingClass = "spacing-compact";
        } else if (height > 900) {
            spacingClass = "spacing-comfortable";
        }

        layoutManager.getRootContainer().getStyleClass().removeIf(css ->
            css.startsWith("spacing-"));
        layoutManager.getRootContainer().getStyleClass().add(spacingClass);
    }

    private void setupEventHandlers() {
        // 窗口关闭时保存状态
        stage.setOnCloseRequest(this::handleWindowClose);

        // 窗口状态变化时保存
        maximized.addListener((obs, oldVal, newVal) -> saveWindowStateAsync());

        // 监听屏幕变化（多显示器环境）
        // 注意：getOutputScaleXProperty() 在 JavaFX 8 中不可用，使用替代方案
        // Screen.getPrimary().getOutputScaleXProperty().addListener((obs, oldVal, newVal) -> {
        //     adjustForScreenScale();
        // });
    }

    private void handleWindowClose(WindowEvent event) {
        saveWindowState();
    }

    /**
     * 加载窗口状态
     */
    public void loadWindowState() {
        try {
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

            double loadedX = preferences.getDouble(PREF_X, -1);
            double loadedY = preferences.getDouble(PREF_Y, -1);
            double loadedWidth = preferences.getDouble(PREF_WIDTH, optimalWidth.get());
            double loadedHeight = preferences.getDouble(PREF_HEIGHT, optimalHeight.get());
            boolean loadedMaximized = preferences.getBoolean(PREF_MAXIMIZED, false);
            double loadedDividerPos = preferences.getDouble(PREF_DIVIDER_POS, 0.3);

            // 验证窗口位置是否在屏幕范围内
            if (isPositionValid(loadedX, loadedY, loadedWidth, loadedHeight, screenBounds)) {
                stage.setX(loadedX);
                stage.setY(loadedY);
            } else {
                // 居中显示
                centerWindowOnScreen(screenBounds, loadedWidth, loadedHeight);
            }

            // 设置窗口尺寸
            stage.setWidth(loadedWidth);
            stage.setHeight(loadedHeight);

            // 设置最大化状态
            stage.setMaximized(loadedMaximized);

            // 设置分割面板位置
            if (!loadedMaximized && layoutManager.getMainSplitPane().getDividers().size() > 0) {
                layoutManager.getMainSplitPane().setDividerPositions(loadedDividerPos);
            }

            dividerPosition.set(loadedDividerPos);

        } catch (Exception e) {
            // 加载失败时使用默认设置
            applyDefaultWindowSettings();
        }
    }

    /**
     * 保存窗口状态
     */
    public void saveWindowState() {
        try {
            if (!stage.isMaximized()) {
                preferences.putDouble(PREF_X, stage.getX());
                preferences.putDouble(PREF_Y, stage.getY());
                preferences.putDouble(PREF_WIDTH, stage.getWidth());
                preferences.putDouble(PREF_HEIGHT, stage.getHeight());
            }
            preferences.putBoolean(PREF_MAXIMIZED, stage.isMaximized());
            preferences.putDouble(PREF_DIVIDER_POS, dividerPosition.get());

            preferences.flush();

        } catch (Exception e) {
            System.err.println("保存窗口状态失败: " + e.getMessage());
        }
    }

    /**
     * 异步保存窗口状态
     */
    private void saveWindowStateAsync() {
        javafx.application.Platform.runLater(this::saveWindowState);
    }

    /**
     * 应用默认窗口设置
     */
    private void applyDefaultWindowSettings() {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

        double width = Math.min(optimalWidth.get(), screenBounds.getWidth() * 0.8);
        double height = Math.min(optimalHeight.get(), screenBounds.getHeight() * 0.8);

        centerWindowOnScreen(screenBounds, width, height);

        stage.setWidth(width);
        stage.setHeight(height);
        stage.setMaximized(false);
    }

    /**
     * 将窗口居中显示在屏幕上
     */
    private void centerWindowOnScreen(Rectangle2D screenBounds, double windowWidth, double windowHeight) {
        double x = screenBounds.getMinX() + (screenBounds.getWidth() - windowWidth) / 2;
        double y = screenBounds.getMinY() + (screenBounds.getHeight() - windowHeight) / 2;

        stage.setX(x);
        stage.setY(y);
    }

    /**
     * 验证窗口位置是否有效
     */
    private boolean isPositionValid(double x, double y, double width, double height, Rectangle2D screenBounds) {
        // 检查是否为默认值
        if (x < 0 || y < 0) return false;

        // 检查窗口是否在屏幕范围内
        return x + width <= screenBounds.getMaxX() &&
               y + height <= screenBounds.getMaxY() &&
               x >= screenBounds.getMinX() &&
               y >= screenBounds.getMinY();
    }

    /**
     * 根据屏幕缩放调整窗口
     */
    private void adjustForScreenScale() {
        if (maximized.get()) return;

        // 注意：getOutputScaleX/Y 在 JavaFX 8 中不可用，使用默认值
        double scaleX = 1.0; // Screen.getPrimary().getOutputScaleX();
        double scaleY = 1.0; // Screen.getPrimary().getOutputScaleY();

        // 根据缩放比例调整窗口大小
        if (scaleX > 1.5 || scaleY > 1.5) {
            // 高DPI显示器，增加窗口大小
            double newWidth = width.get() * 1.2;
            double newHeight = height.get() * 1.2;

            // 确保不超过屏幕大小
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            newWidth = Math.min(newWidth, screenBounds.getWidth() * 0.9);
            newHeight = Math.min(newHeight, screenBounds.getHeight() * 0.9);

            stage.setWidth(newWidth);
            stage.setHeight(newHeight);
        }
    }

    /**
     * 智能调整窗口大小以适应内容
     */
    public void fitWindowToContent() {
        if (maximized.get()) return;

        // 计算内容所需的最小尺寸
        double contentWidth = calculatePreferredContentWidth();
        double contentHeight = calculatePreferredContentHeight();

        // 加上边距和装饰
        double windowWidth = contentWidth + 50; // 左右边距
        double windowHeight = contentHeight + 100; // 上下边距 + 工具栏

        // 限制在合理范围内
        windowWidth = Math.max(minWindowWidth.get(), Math.min(windowWidth, Screen.getPrimary().getVisualBounds().getWidth() * 0.9));
        windowHeight = Math.max(minWindowHeight.get(), Math.min(windowHeight, Screen.getPrimary().getVisualBounds().getHeight() * 0.9));

        // 平滑调整到新尺寸
        animateResize(windowWidth, windowHeight);
    }

    private double calculatePreferredContentWidth() {
        // 根据当前响应式级别计算首选内容宽度
        ResponsiveLayoutManager.ResponsiveLevel level = layoutManager.getCurrentLevel();

        switch (level) {
            case MOBILE: return Screen.getPrimary().getVisualBounds().getWidth() * 0.95;
            case TABLET: return 1000;
            case DESKTOP: return 1200;
            case LARGE: return 1400;
            default: return 1200;
        }
    }

    private double calculatePreferredContentHeight() {
        // 根据屏幕高度计算首选内容高度
        double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();
        return Math.max(600, screenHeight * 0.8);
    }

    /**
     * 动画调整窗口大小
     */
    private void animateResize(double targetWidth, double targetHeight) {
        // 直接设置窗口大小，避免动画兼容性问题
        stage.setWidth(targetWidth);
        stage.setHeight(targetHeight);

        // 如果需要动画效果，可以在 JavaFX 8+ 版本中启用
        /*
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                new javafx.animation.KeyValue(stage.widthProperty(), stage.getWidth()),
                new javafx.animation.KeyValue(stage.heightProperty(), stage.getHeight())
            ),
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(300),
                new javafx.animation.KeyValue(stage.widthProperty(), targetWidth),
                new javafx.animation.KeyValue(stage.heightProperty(), targetHeight)
            )
        );
        timeline.play();
        */
    }

    /**
     * 恢复窗口状态
     */
    public void restoreState() {
        try {
            double x = preferences.getDouble(PREF_X, -1);
            double y = preferences.getDouble(PREF_Y, -1);
            double width = preferences.getDouble(PREF_WIDTH, optimalWidth.get());
            double height = preferences.getDouble(PREF_HEIGHT, optimalHeight.get());
            boolean maximized = preferences.getBoolean(PREF_MAXIMIZED, false);

            // 只在窗口不在屏幕上时才调整位置
            if (x < 0 || y < 0 || x > Screen.getPrimary().getVisualBounds().getWidth() ||
                y > Screen.getPrimary().getVisualBounds().getHeight()) {
                centerWindow();
            } else {
                stage.setX(x);
                stage.setY(y);
            }

            stage.setWidth(width);
            stage.setHeight(height);

            // 延迟设置最大化状态，避免冲突
            Platform.runLater(() -> {
                if (maximized) {
                    stage.setMaximized(true);
                }
            });
        } catch (Exception e) {
            log.warn("恢复窗口状态失败", e);
            centerWindow();
        }
    }

    /**
     * 居中窗口
     */
    private void centerWindow() {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double centerX = screenBounds.getMinX() + (screenBounds.getWidth() - optimalWidth.get()) / 2;
        double centerY = screenBounds.getMinY() + (screenBounds.getHeight() - optimalHeight.get()) / 2;

        stage.setX(centerX);
        stage.setY(centerY);
        stage.setWidth(optimalWidth.get());
        stage.setHeight(optimalHeight.get());
    }

    /**
     * 切换最大化状态
     */
    public void toggleMaximize() {
        stage.setMaximized(!stage.isMaximized());
    }

    /**
     * 重置窗口布局
     */
    public void resetLayout() {
        stage.setMaximized(false);
        applyDefaultWindowSettings();

        // 重置分割面板位置
        layoutManager.getMainSplitPane().setDividerPositions(0.3);
    }

    // Getters and Setters
    public DoubleProperty xProperty() { return x; }
    public DoubleProperty yProperty() { return y; }
    public DoubleProperty widthProperty() { return width; }
    public DoubleProperty heightProperty() { return height; }
    public BooleanProperty maximizedProperty() { return maximized; }
    public DoubleProperty dividerPositionProperty() { return dividerPosition; }

    public BooleanProperty smartResizeProperty() { return smartResize; }
    public boolean isSmartResize() { return smartResize.get(); }
    public void setSmartResize(boolean smartResize) { this.smartResize.set(smartResize); }

    public DoubleProperty minWindowWidthProperty() { return minWindowWidth; }
    public DoubleProperty minWindowHeightProperty() { return minWindowHeight; }
    public DoubleProperty optimalWidthProperty() { return optimalWidth; }
    public DoubleProperty optimalHeightProperty() { return optimalHeight; }
}