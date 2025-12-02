package red.jiuzhou.ui.layout;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.css.PseudoClass;

import java.util.ArrayList;
import java.util.List;

/**
 * 响应式布局管理器
 * 提供智能的自适应布局功能，支持多种屏幕尺寸和分辨率
 *
 * @author Claude Code Enhanced
 * @version 2.0
 */
public class ResponsiveLayoutManager {

    // 断点定义 (像素)
    private static final double BREAKPOINT_MOBILE = 768;
    private static final double BREAKPOINT_TABLET = 1024;
    private static final double BREAKPOINT_DESKTOP = 1440;
    private static final double BREAKPOINT_LARGE = 1920;

    // 响应式级别枚举
    public enum ResponsiveLevel {
        MOBILE("mobile", "移动设备"),
        TABLET("tablet", "平板设备"),
        DESKTOP("desktop", "桌面设备"),
        LARGE("large", "大屏设备");

        private final String className;
        private final String description;

        ResponsiveLevel(String className, String description) {
            this.className = className;
            this.description = description;
        }

        public String getClassName() { return className; }
        public String getDescription() { return description; }
    }

    private final Stage stage;
    private final BorderPane rootContainer;
    private final SplitPane mainSplitPane;
    private final VBox leftPanel;
    private final VBox rightPanel;

    // 响应式属性
    private final ObjectProperty<ResponsiveLevel> currentLevel = new SimpleObjectProperty<>(ResponsiveLevel.DESKTOP);
    private final DoubleProperty windowWidth = new SimpleDoubleProperty();
    private final DoubleProperty windowHeight = new SimpleDoubleProperty();

    // 布局配置
    private final List<LayoutRule> layoutRules = new ArrayList<>();
    private double previousDividerPosition = 0.3;
    private boolean leftPanelVisible = true;

    // 伪类样式
    private static final PseudoClass MOBILE_PSEUDO = PseudoClass.getPseudoClass("mobile");
    private static final PseudoClass TABLET_PSEUDO = PseudoClass.getPseudoClass("tablet");
    private static final PseudoClass DESKTOP_PSEUDO = PseudoClass.getPseudoClass("desktop");
    private static final PseudoClass LARGE_PSEUDO = PseudoClass.getPseudoClass("large");

    public ResponsiveLayoutManager(Stage stage) {
        this.stage = stage;
        this.rootContainer = new BorderPane();
        this.mainSplitPane = new SplitPane();
        this.leftPanel = createLeftPanel();
        this.rightPanel = createRightPanel();

        initializeLayout();
        setupResponsiveListeners();
        applyDefaultLayoutRules();
    }

    private void initializeLayout() {
        // 配置根容器
        rootContainer.getStyleClass().add("responsive-container");
        rootContainer.setPadding(new Insets(8));

        // 配置分割面板
        mainSplitPane.getStyleClass().add("main-split-pane");
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.getItems().addAll(leftPanel, rightPanel);
        mainSplitPane.setDividerPositions(0.3);

        // 设置到根容器
        rootContainer.setCenter(mainSplitPane);

        // 设置初始样式类
        updateResponsiveClasses();
    }

    private VBox createLeftPanel() {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("left-panel");
        panel.setPadding(new Insets(12));
        panel.setMinWidth(200);
        panel.setPrefWidth(350);
        return panel;
    }

    private VBox createRightPanel() {
        VBox panel = new VBox();
        panel.getStyleClass().add("right-panel");
        panel.setPadding(new Insets(12));
        return panel;
    }

    private void setupResponsiveListeners() {
        // 监听窗口尺寸变化
        windowWidth.bind(stage.widthProperty());
        windowHeight.bind(stage.heightProperty());

        // 监听宽度变化触发响应式调整
        windowWidth.addListener((obs, oldVal, newVal) -> {
            updateResponsiveLayoutInternal();
        });

        // 监听高度变化进行精细调整
        windowHeight.addListener((obs, oldVal, newVal) -> {
            adjustVerticalLayout();
        });

        // 初始化响应式级别
        calculateResponsiveLevel();
    }

    private void updateResponsiveLayoutInternal() {
        ResponsiveLevel newLevel = calculateResponsiveLevel();

        if (!currentLevel.get().equals(newLevel)) {
            ResponsiveLevel oldLevel = currentLevel.get();
            currentLevel.set(newLevel);

            // 更新样式类
            updateResponsiveClasses();

            // 应用布局规则
            applyLayoutRules(oldLevel, newLevel);

            // 记录切换事件
            System.out.println("响应式级别切换: " + oldLevel.getDescription() + " -> " + newLevel.getDescription());
        }
    }

    private ResponsiveLevel calculateResponsiveLevel() {
        double width = windowWidth.get();

        if (width < BREAKPOINT_MOBILE) {
            return ResponsiveLevel.MOBILE;
        } else if (width < BREAKPOINT_TABLET) {
            return ResponsiveLevel.TABLET;
        } else if (width < BREAKPOINT_DESKTOP) {
            return ResponsiveLevel.DESKTOP;
        } else {
            return ResponsiveLevel.LARGE;
        }
    }

    private void updateResponsiveClasses() {
        ResponsiveLevel level = currentLevel.get();

        // 移除所有响应式伪类
        rootContainer.pseudoClassStateChanged(MOBILE_PSEUDO, false);
        rootContainer.pseudoClassStateChanged(TABLET_PSEUDO, false);
        rootContainer.pseudoClassStateChanged(DESKTOP_PSEUDO, false);
        rootContainer.pseudoClassStateChanged(LARGE_PSEUDO, false);

        // 添加当前级别的伪类
        switch (level) {
            case MOBILE:
                rootContainer.pseudoClassStateChanged(MOBILE_PSEUDO, true);
                break;
            case TABLET:
                rootContainer.pseudoClassStateChanged(TABLET_PSEUDO, true);
                break;
            case DESKTOP:
                rootContainer.pseudoClassStateChanged(DESKTOP_PSEUDO, true);
                break;
            case LARGE:
                rootContainer.pseudoClassStateChanged(LARGE_PSEUDO, true);
                break;
        }
    }

    private void applyLayoutRules(ResponsiveLevel oldLevel, ResponsiveLevel newLevel) {
        for (LayoutRule rule : layoutRules) {
            if (rule.matchesTransition(oldLevel, newLevel)) {
                rule.apply(this);
            }
        }

        // 应用默认规则
        applyDefaultRules(newLevel);
    }

    private void applyDefaultRules(ResponsiveLevel level) {
        switch (level) {
            case MOBILE:
                applyMobileLayout();
                break;
            case TABLET:
                applyTabletLayout();
                break;
            case DESKTOP:
                applyDesktopLayout();
                break;
            case LARGE:
                applyLargeLayout();
                break;
        }
    }

    private void applyMobileLayout() {
        // 移动设备：隐藏左侧面板，全屏显示右侧内容
        if (leftPanelVisible) {
            previousDividerPosition = mainSplitPane.getDividerPositions()[0];
            leftPanelVisible = false;
        }

        leftPanel.setManaged(false);
        leftPanel.setVisible(false);
        rootContainer.setPadding(new Insets(4));

        // 调整分割面板
        if (mainSplitPane.getItems().contains(leftPanel)) {
            mainSplitPane.getItems().remove(leftPanel);
        }
    }

    private void applyTabletLayout() {
        // 平板设备：可收起的左侧面板
        if (!leftPanelVisible) {
            leftPanelVisible = true;
        }

        leftPanel.setManaged(true);
        leftPanel.setVisible(true);
        leftPanel.setPrefWidth(280);

        // 确保左侧面板在分割面板中
        if (!mainSplitPane.getItems().contains(leftPanel)) {
            mainSplitPane.getItems().add(0, leftPanel);
        }

        mainSplitPane.setDividerPositions(0.25);
        rootContainer.setPadding(new Insets(6));
    }

    private void applyDesktopLayout() {
        // 桌面设备：标准分割布局
        if (!leftPanelVisible) {
            leftPanelVisible = true;
        }

        leftPanel.setManaged(true);
        leftPanel.setVisible(true);
        leftPanel.setPrefWidth(350);

        // 确保左侧面板在分割面板中
        if (!mainSplitPane.getItems().contains(leftPanel)) {
            mainSplitPane.getItems().add(0, leftPanel);
        }

        mainSplitPane.setDividerPositions(previousDividerPosition);
        rootContainer.setPadding(new Insets(8));
    }

    private void applyLargeLayout() {
        // 大屏设备：宽屏优化布局
        if (!leftPanelVisible) {
            leftPanelVisible = true;
        }

        leftPanel.setManaged(true);
        leftPanel.setVisible(true);
        leftPanel.setPrefWidth(400);

        // 确保左侧面板在分割面板中
        if (!mainSplitPane.getItems().contains(leftPanel)) {
            mainSplitPane.getItems().add(0, leftPanel);
        }

        mainSplitPane.setDividerPositions(0.28);
        rootContainer.setPadding(new Insets(12));
    }

    private void adjustVerticalLayout() {
        double height = windowHeight.get();

        // 根据高度调整内容布局
        if (height < 600) {
            // 紧凑布局
            rootContainer.setStyle("-fx-spacing: 4;");
        } else if (height < 800) {
            // 标准布局
            rootContainer.setStyle("-fx-spacing: 8;");
        } else {
            // 宽松布局
            rootContainer.setStyle("-fx-spacing: 12;");
        }
    }

    private void applyDefaultLayoutRules() {
        // 添加默认布局规则

        // 移动设备规则
        addLayoutRule(new SimpleLayoutRule(ResponsiveLevel.DESKTOP, ResponsiveLevel.MOBILE) {
            @Override
            public void apply(ResponsiveLayoutManager manager) {
                // 保存当前分割位置
                previousDividerPosition = manager.getMainSplitPane().getDividerPositions()[0];
            }
        });

        // 从移动设备恢复规则
        addLayoutRule(new SimpleLayoutRule(ResponsiveLevel.MOBILE, ResponsiveLevel.TABLET) {
            @Override
            public void apply(ResponsiveLayoutManager manager) {
                // 恢复左侧面板
                if (!manager.getMainSplitPane().getItems().contains(manager.getLeftPanel())) {
                    manager.getMainSplitPane().getItems().add(0, manager.getLeftPanel());
                }
                manager.getMainSplitPane().setDividerPositions(0.25);
            }
        });
    }

    /**
     * 添加自定义布局规则
     */
    public void addLayoutRule(LayoutRule rule) {
        layoutRules.add(rule);
    }

    /**
     * 切换左侧面板显示状态（适用于移动设备）
     */
    public void toggleLeftPanel() {
        if (currentLevel.get() == ResponsiveLevel.MOBILE) {
            // 移动设备：临时显示左侧面板
            leftPanel.setVisible(!leftPanel.isVisible());
            leftPanel.setManaged(leftPanel.isVisible());

            if (leftPanel.isVisible()) {
                // 创建临时覆盖布局
                showMobileLeftPanel();
            } else {
                hideMobileLeftPanel();
            }
        }
    }

    private void showMobileLeftPanel() {
        // 移动端左侧面板以覆盖方式显示
        leftPanel.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0.5, 0, 0);");
        leftPanel.setTranslateX(0);
    }

    private void hideMobileLeftPanel() {
        // 隐藏移动端左侧面板
        leftPanel.setTranslateX(-leftPanel.getWidth());
    }

    // Getters
    public BorderPane getRootContainer() { return rootContainer; }
    public VBox getLeftPanel() { return leftPanel; }
    public VBox getRightPanel() { return rightPanel; }
    public SplitPane getMainSplitPane() { return mainSplitPane; }
    public ResponsiveLevel getCurrentLevel() { return currentLevel.get(); }
    public ObjectProperty<ResponsiveLevel> currentLevelProperty() { return currentLevel; }

    /**
     * 公共方法：更新响应式布局
     */
    public void updateResponsiveLayout() {
        ResponsiveLevel newLevel = calculateResponsiveLevel();

        if (!currentLevel.get().equals(newLevel)) {
            ResponsiveLevel oldLevel = currentLevel.get();
            currentLevel.set(newLevel);

            // 更新样式类
            updateResponsiveClasses();

            // 应用布局规则
            applyLayoutRules(oldLevel, newLevel);

            // 记录切换事件
            System.out.println("响应式级别切换: " + oldLevel.getDescription() + " -> " + newLevel.getDescription());
        }
    }

    /**
     * 处理窗口大小变化 - Java 8 兼容版本
     */
    public void handleWindowResize(double width, double height) {
        if (stage != null) {
            updateResponsiveLayout();
        }
    }

    /**
     * 布局规则接口
     */
    public interface LayoutRule {
        boolean matchesTransition(ResponsiveLevel from, ResponsiveLevel to);
        void apply(ResponsiveLayoutManager manager);
    }

    /**
     * 简单布局规则实现
     */
    public static abstract class SimpleLayoutRule implements LayoutRule {
        private final ResponsiveLevel fromLevel;
        private final ResponsiveLevel toLevel;

        public SimpleLayoutRule(ResponsiveLevel fromLevel, ResponsiveLevel toLevel) {
            this.fromLevel = fromLevel;
            this.toLevel = toLevel;
        }

        @Override
        public boolean matchesTransition(ResponsiveLevel from, ResponsiveLevel to) {
            return fromLevel == from && toLevel == to;
        }

        @Override
        public abstract void apply(ResponsiveLayoutManager manager);
    }
}