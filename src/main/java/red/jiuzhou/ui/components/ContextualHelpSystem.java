package red.jiuzhou.ui.components;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上下文感知帮助系统
 * 为设计师提供智能的操作指导和动态帮助提示
 *
 * 功能特性：
 * - 基于上下文的智能提示
 * - 交互式操作引导
 * - 快捷键提示
 * - 新手引导模式
 * - 操作建议和最佳实践
 * - 可视化操作演示
 */
public class ContextualHelpSystem {

    private static final Logger log = LoggerFactory.getLogger(ContextualHelpSystem.class);

    // 单例实例
    private static ContextualHelpSystem instance;

    private final Stage primaryStage;
    private final Map<String, HelpContext> helpContexts = new ConcurrentHashMap<>();
    private final Map<Node, String> nodeContextMap = new ConcurrentHashMap<>();

    // 帮助面板
    private HelpPanel helpPanel;
    private Popup helpTooltip;
    private Timeline tooltipTimer;

    // 引导模式
    private boolean guidedModeActive = false;
    private TourGuide tourGuide;

    // 配置
    private boolean helpEnabled = true;
    private boolean smartSuggestionsEnabled = true;
    private Duration tooltipDelay = Duration.millis(800);

    private ContextualHelpSystem(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initializeHelpSystem();
        registerDefaultContexts();
    }

    /**
     * 获取单例实例
     */
    public static ContextualHelpSystem getInstance(Stage primaryStage) {
        if (instance == null) {
            instance = new ContextualHelpSystem(primaryStage);
        }
        return instance;
    }

    public static ContextualHelpSystem getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ContextualHelpSystem must be initialized with a Stage first");
        }
        return instance;
    }

    /**
     * 初始化帮助系统
     */
    private void initializeHelpSystem() {
        // 全局快捷键监听
        if (primaryStage.getScene() != null) {
            primaryStage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKeyPress);
        }

        // 创建帮助面板
        helpPanel = new HelpPanel();
    }

    /**
     * 注册默认上下文
     */
    private void registerDefaultContexts() {
        // 数据表格相关帮助
        registerContext("data-table", HelpContext.builder()
            .title("数据表格操作")
            .description("高效的数据查看和编辑功能")
            .addTip("💡 使用 Ctrl+F 快速搜索数据")
            .addTip("⚡ 双击列标题可以自动调整列宽")
            .addTip("🔍 右键单击单元格查看更多操作")
            .addKeyboardShortcut("Ctrl+C", "复制选中单元格")
            .addKeyboardShortcut("Ctrl+A", "选择全部数据")
            .addKeyboardShortcut("Delete", "删除选中行")
            .build());

        // 数据导入帮助
        registerContext("data-import", HelpContext.builder()
            .title("智能数据导入")
            .description("从XML文件导入数据到数据库")
            .addTip("📁 支持拖拽文件到导入区域")
            .addTip("🤖 启用AI处理可以自动优化数据质量")
            .addTip("⚙️ 预览数据可以提前发现问题")
            .addBestPractice("导入前先备份数据库")
            .addBestPractice("大文件建议分批导入")
            .build());

        // 查询功能帮助
        registerContext("query-builder", HelpContext.builder()
            .title("查询构建器")
            .description("创建和执行SQL查询")
            .addTip("💻 支持SQL语法高亮和自动补全")
            .addTip("📊 查询结果可以导出为多种格式")
            .addKeyboardShortcut("F5", "执行查询")
            .addKeyboardShortcut("Ctrl+Space", "显示自动补全")
            .build());

        // 数据可视化帮助
        registerContext("data-visualization", HelpContext.builder()
            .title("数据可视化")
            .description("直观的数据统计和图表分析")
            .addTip("📈 图表支持交互式缩放和筛选")
            .addTip("💾 可以保存图表配置用于下次使用")
            .addTip("🔄 数据更新时图表会自动刷新")
            .build());
    }

    /**
     * 处理全局快捷键
     */
    private void handleGlobalKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.F1) {
            toggleHelpPanel();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.H) {
            showContextualHelp();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && guidedModeActive) {
            exitGuidedMode();
            event.consume();
        }
    }

    // ========== 公共API ==========

    /**
     * 注册帮助上下文
     */
    public void registerContext(String contextId, HelpContext context) {
        helpContexts.put(contextId, context);
        log.debug("注册帮助上下文: {}", contextId);
    }

    /**
     * 为节点绑定帮助上下文
     */
    public void bindHelp(Node node, String contextId) {
        nodeContextMap.put(node, contextId);

        // 添加鼠标悬停监听
        node.setOnMouseEntered(e -> {
            if (helpEnabled) {
                scheduleTooltip(node, contextId);
            }
        });

        node.setOnMouseExited(e -> {
            cancelTooltip();
        });

        // 添加焦点监听
        if (node instanceof Control) {
            node.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (isNowFocused && helpEnabled) {
                    showQuickHelp(node, contextId);
                }
            });
        }
    }

    /**
     * 显示上下文帮助
     */
    public void showContextualHelp() {
        Node focusedNode = primaryStage.getScene().getFocusOwner();
        if (focusedNode != null) {
            String contextId = findContextForNode(focusedNode);
            if (contextId != null) {
                showHelpForContext(contextId);
            } else {
                showGeneralHelp();
            }
        } else {
            showGeneralHelp();
        }
    }

    /**
     * 切换帮助面板
     */
    public void toggleHelpPanel() {
        if (helpPanel.isVisible()) {
            helpPanel.hide();
        } else {
            helpPanel.show();
        }
    }

    /**
     * 开始新手引导
     */
    public void startGuidedTour(String tourId) {
        if (tourGuide == null) {
            tourGuide = new TourGuide();
        }
        guidedModeActive = true;
        tourGuide.startTour(tourId);
    }

    /**
     * 退出引导模式
     */
    public void exitGuidedMode() {
        guidedModeActive = false;
        if (tourGuide != null) {
            tourGuide.stopTour();
        }
    }

    /**
     * 显示操作建议
     */
    public void showSuggestion(String message, Duration duration) {
        if (!smartSuggestionsEnabled) return;

        NotificationSystem.getInstance().showInfo("💡 操作建议", message);
    }

    /**
     * 显示最佳实践提示
     */
    public void showBestPractice(String practice) {
        NotificationSystem.getInstance().showInfo("⭐ 最佳实践", practice);
    }

    // ========== 私有方法 ==========

    /**
     * 计划显示工具提示
     */
    private void scheduleTooltip(Node node, String contextId) {
        cancelTooltip();

        tooltipTimer = new Timeline(new KeyFrame(tooltipDelay, e -> {
            showTooltip(node, contextId);
        }));
        tooltipTimer.play();
    }

    /**
     * 取消工具提示
     */
    private void cancelTooltip() {
        if (tooltipTimer != null) {
            tooltipTimer.stop();
        }
        hideTooltip();
    }

    /**
     * 显示工具提示
     */
    private void showTooltip(Node node, String contextId) {
        HelpContext context = helpContexts.get(contextId);
        if (context == null) return;

        if (helpTooltip == null) {
            helpTooltip = new Popup();
            helpTooltip.setAutoHide(true);
        } else {
            helpTooltip.hide();
        }

        VBox tooltipContent = createTooltipContent(context);
        helpTooltip.getContent().clear();
        helpTooltip.getContent().add(tooltipContent);

        // 计算位置
        Bounds bounds = node.localToScreen(node.getBoundsInLocal());
        double x = bounds.getMinX();
        double y = bounds.getMaxY() + 5;

        helpTooltip.show(primaryStage, x, y);

        // 自动隐藏
        Timeline autoHide = new Timeline(new KeyFrame(Duration.seconds(8), e -> hideTooltip()));
        autoHide.play();
    }

    /**
     * 隐藏工具提示
     */
    private void hideTooltip() {
        if (helpTooltip != null && helpTooltip.isShowing()) {
            helpTooltip.hide();
        }
    }

    /**
     * 创建工具提示内容
     */
    private VBox createTooltipContent(HelpContext context) {
        VBox content = new VBox(8);
        content.getStyleClass().add("help-tooltip");
        content.setPadding(new Insets(12));
        content.setMaxWidth(300);

        // 标题
        Label titleLabel = new Label(context.getTitle());
        titleLabel.getStyleClass().add("tooltip-title");

        // 描述
        Label descLabel = new Label(context.getDescription());
        descLabel.getStyleClass().add("tooltip-description");
        descLabel.setWrapText(true);

        content.getChildren().addAll(titleLabel, descLabel);

        // 添加一个提示
        if (!context.getTips().isEmpty()) {
            Label tipLabel = new Label(context.getTips().get(0));
            tipLabel.getStyleClass().add("tooltip-tip");
            tipLabel.setWrapText(true);
            content.getChildren().add(tipLabel);
        }

        // 添加快捷键（如果有）
        if (!context.getKeyboardShortcuts().isEmpty()) {
            Map.Entry<String, String> shortcut = context.getKeyboardShortcuts().entrySet().iterator().next();
            Label shortcutLabel = new Label("💡 " + shortcut.getKey() + " - " + shortcut.getValue());
            shortcutLabel.getStyleClass().add("tooltip-shortcut");
            content.getChildren().add(shortcutLabel);
        }

        return content;
    }

    /**
     * 显示快速帮助
     */
    private void showQuickHelp(Node node, String contextId) {
        HelpContext context = helpContexts.get(contextId);
        if (context != null && smartSuggestionsEnabled) {
            // 在状态栏显示快速提示
            String quickTip = context.getTitle() + " - 按 F1 查看详细帮助";
            // TODO: 集成状态栏显示
        }
    }

    /**
     * 查找节点的上下文
     */
    private String findContextForNode(Node node) {
        // 直接映射
        String contextId = nodeContextMap.get(node);
        if (contextId != null) return contextId;

        // 向上查找父节点
        Node parent = node.getParent();
        while (parent != null) {
            contextId = nodeContextMap.get(parent);
            if (contextId != null) return contextId;
            parent = parent.getParent();
        }

        // 基于样式类推断
        return inferContextFromStyleClass(node);
    }

    /**
     * 基于样式类推断上下文
     */
    private String inferContextFromStyleClass(Node node) {
        if (node.getStyleClass().contains("table-view")) {
            return "data-table";
        } else if (node.getStyleClass().contains("text-area")) {
            return "query-builder";
        }
        return null;
    }

    /**
     * 显示特定上下文的帮助
     */
    private void showHelpForContext(String contextId) {
        HelpContext context = helpContexts.get(contextId);
        if (context != null) {
            helpPanel.showContext(context);
            helpPanel.show();
        }
    }

    /**
     * 显示通用帮助
     */
    private void showGeneralHelp() {
        helpPanel.showGeneralHelp();
        helpPanel.show();
    }

    // ========== 配置方法 ==========

    public void setHelpEnabled(boolean enabled) {
        this.helpEnabled = enabled;
    }

    public void setSmartSuggestionsEnabled(boolean enabled) {
        this.smartSuggestionsEnabled = enabled;
    }

    public void setTooltipDelay(Duration delay) {
        this.tooltipDelay = delay;
    }

    // ========== 内部类 ==========

    /**
     * 帮助上下文
     */
    public static class HelpContext {
        private final String title;
        private final String description;
        private final List<String> tips;
        private final List<String> bestPractices;
        private final Map<String, String> keyboardShortcuts;
        private final List<String> relatedTopics;

        private HelpContext(Builder builder) {
            this.title = builder.title;
            this.description = builder.description;
            this.tips = new ArrayList<>(builder.tips);
            this.bestPractices = new ArrayList<>(builder.bestPractices);
            this.keyboardShortcuts = new HashMap<>(builder.keyboardShortcuts);
            this.relatedTopics = new ArrayList<>(builder.relatedTopics);
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public List<String> getTips() { return tips; }
        public List<String> getBestPractices() { return bestPractices; }
        public Map<String, String> getKeyboardShortcuts() { return keyboardShortcuts; }
        public List<String> getRelatedTopics() { return relatedTopics; }

        public static class Builder {
            private String title;
            private String description;
            private final List<String> tips = new ArrayList<>();
            private final List<String> bestPractices = new ArrayList<>();
            private final Map<String, String> keyboardShortcuts = new HashMap<>();
            private final List<String> relatedTopics = new ArrayList<>();

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder addTip(String tip) {
                this.tips.add(tip);
                return this;
            }

            public Builder addBestPractice(String practice) {
                this.bestPractices.add(practice);
                return this;
            }

            public Builder addKeyboardShortcut(String keys, String description) {
                this.keyboardShortcuts.put(keys, description);
                return this;
            }

            public Builder addRelatedTopic(String topic) {
                this.relatedTopics.add(topic);
                return this;
            }

            public HelpContext build() {
                return new HelpContext(this);
            }
        }
    }

    /**
     * 帮助面板
     */
    private class HelpPanel {
        private Stage helpStage;
        private VBox contentContainer;
        private TreeView<String> topicTree;
        private VBox helpContent;

        public void show() {
            if (helpStage == null) {
                createHelpWindow();
            }
            helpStage.show();
            helpStage.toFront();
        }

        public void hide() {
            if (helpStage != null) {
                helpStage.hide();
            }
        }

        public boolean isVisible() {
            return helpStage != null && helpStage.isShowing();
        }

        private void createHelpWindow() {
            helpStage = new Stage();
            helpStage.setTitle("帮助 - DB XML Tool");
            helpStage.initOwner(primaryStage);

            SplitPane splitPane = new SplitPane();

            // 左侧主题树
            topicTree = createTopicTree();
            VBox leftPane = new VBox(topicTree);
            leftPane.setPrefWidth(200);

            // 右侧内容区域
            helpContent = new VBox();
            helpContent.getStyleClass().add("help-content");
            helpContent.setPadding(new Insets(20));

            splitPane.getItems().addAll(leftPane, helpContent);
            splitPane.setDividerPositions(0.3);

            javafx.scene.Scene scene = new javafx.scene.Scene(splitPane, 800, 600);
            scene.getStylesheets().add("/modern-theme.css");
            helpStage.setScene(scene);
        }

        private TreeView<String> createTopicTree() {
            TreeItem<String> root = new TreeItem<>("帮助主题");
            root.setExpanded(true);

            // 添加主题分类
            TreeItem<String> gettingStarted = new TreeItem<>("快速开始");
            TreeItem<String> dataOperations = new TreeItem<>("数据操作");
            TreeItem<String> advanced = new TreeItem<>("高级功能");
            TreeItem<String> troubleshooting = new TreeItem<>("故障排除");

            root.getChildren().addAll(gettingStarted, dataOperations, advanced, troubleshooting);

            TreeView<String> tree = new TreeView<>(root);
            tree.setShowRoot(false);
            tree.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        showTopicContent(newSelection.getValue());
                    }
                }
            );

            return tree;
        }

        public void showContext(HelpContext context) {
            displayContextContent(context);
        }

        public void showGeneralHelp() {
            displayGeneralContent();
        }

        private void showTopicContent(String topic) {
            // TODO: 根据主题显示相应内容
            displayTopicContent(topic);
        }

        private void displayContextContent(HelpContext context) {
            helpContent.getChildren().clear();

            // 标题
            Label titleLabel = new Label(context.getTitle());
            titleLabel.getStyleClass().add("help-title");

            // 描述
            Label descLabel = new Label(context.getDescription());
            descLabel.getStyleClass().add("help-description");
            descLabel.setWrapText(true);

            helpContent.getChildren().addAll(titleLabel, descLabel);

            // 提示
            if (!context.getTips().isEmpty()) {
                addHelpSection("💡 使用提示", context.getTips());
            }

            // 最佳实践
            if (!context.getBestPractices().isEmpty()) {
                addHelpSection("⭐ 最佳实践", context.getBestPractices());
            }

            // 快捷键
            if (!context.getKeyboardShortcuts().isEmpty()) {
                addShortcutSection(context.getKeyboardShortcuts());
            }
        }

        private void displayGeneralContent() {
            helpContent.getChildren().clear();

            Label titleLabel = new Label("DB XML Tool 帮助");
            titleLabel.getStyleClass().add("help-title");

            Label descLabel = new Label("欢迎使用 DB XML Tool！这是一个专业的数据库与XML转换工具。");
            descLabel.getStyleClass().add("help-description");

            // 通用快捷键
            Map<String, String> globalShortcuts = new HashMap<>();
            globalShortcuts.put("F1", "显示/隐藏帮助面板");
            globalShortcuts.put("Ctrl+H", "显示上下文帮助");
            globalShortcuts.put("Esc", "退出引导模式");

            helpContent.getChildren().addAll(titleLabel, descLabel);
            addShortcutSection(globalShortcuts);
        }

        private void displayTopicContent(String topic) {
            helpContent.getChildren().clear();

            Label titleLabel = new Label(topic);
            titleLabel.getStyleClass().add("help-title");

            Label contentLabel = new Label("此主题的详细内容正在开发中...");
            contentLabel.getStyleClass().add("help-description");

            helpContent.getChildren().addAll(titleLabel, contentLabel);
        }

        private void addHelpSection(String title, List<String> items) {
            Label sectionTitle = new Label(title);
            sectionTitle.getStyleClass().add("help-section-title");
            helpContent.getChildren().add(sectionTitle);

            for (String item : items) {
                Label itemLabel = new Label("• " + item);
                itemLabel.getStyleClass().add("help-item");
                itemLabel.setWrapText(true);
                helpContent.getChildren().add(itemLabel);
            }
        }

        private void addShortcutSection(Map<String, String> shortcuts) {
            Label sectionTitle = new Label("⌨️ 快捷键");
            sectionTitle.getStyleClass().add("help-section-title");
            helpContent.getChildren().add(sectionTitle);

            GridPane shortcutGrid = new GridPane();
            shortcutGrid.getStyleClass().add("shortcut-grid");
            shortcutGrid.setHgap(20);
            shortcutGrid.setVgap(8);

            int row = 0;
            for (Map.Entry<String, String> shortcut : shortcuts.entrySet()) {
                Label keyLabel = new Label(shortcut.getKey());
                keyLabel.getStyleClass().add("shortcut-key");

                Label descLabel = new Label(shortcut.getValue());
                descLabel.getStyleClass().add("shortcut-desc");

                shortcutGrid.add(keyLabel, 0, row);
                shortcutGrid.add(descLabel, 1, row);
                row++;
            }

            helpContent.getChildren().add(shortcutGrid);
        }
    }

    /**
     * 导览指南
     */
    private class TourGuide {
        private List<TourStep> currentTour;
        private int currentStepIndex = 0;
        private Popup highlightOverlay;

        public void startTour(String tourId) {
            currentTour = createTour(tourId);
            currentStepIndex = 0;
            showCurrentStep();
        }

        public void stopTour() {
            hideHighlight();
            currentTour = null;
        }

        private List<TourStep> createTour(String tourId) {
            // TODO: 根据tourId创建相应的导览步骤
            return new ArrayList<>();
        }

        private void showCurrentStep() {
            if (currentTour == null || currentStepIndex >= currentTour.size()) {
                stopTour();
                return;
            }

            TourStep step = currentTour.get(currentStepIndex);
            highlightElement(step.getTargetNode());
            showStepTooltip(step);
        }

        private void highlightElement(Node target) {
            // TODO: 实现元素高亮显示
        }

        private void showStepTooltip(TourStep step) {
            // TODO: 显示步骤说明工具提示
        }

        private void hideHighlight() {
            if (highlightOverlay != null) {
                highlightOverlay.hide();
            }
        }
    }

    /**
     * 导览步骤
     */
    private static class TourStep {
        private final Node targetNode;
        private final String title;
        private final String description;
        private final String action;

        public TourStep(Node targetNode, String title, String description, String action) {
            this.targetNode = targetNode;
            this.title = title;
            this.description = description;
            this.action = action;
        }

        public Node getTargetNode() { return targetNode; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getAction() { return action; }
    }
}