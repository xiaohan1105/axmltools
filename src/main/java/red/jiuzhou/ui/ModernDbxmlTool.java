package red.jiuzhou.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import red.jiuzhou.ui.components.*;
import red.jiuzhou.ui.wizards.DataImportWizard;

/**
 * 现代化 DB XML Tool 主应用
 * 集成所有新的用户体验组件和现代化界面设计
 *
 * 新功能特性：
 * - 现代化工具栏和导航
 * - 智能数据表格和可视化
 * - 实时通知和状态反馈
 * - 上下文感知帮助系统
 * - 智能错误恢复机制
 * - 数据验证和操作指导
 */
@SpringBootApplication(scanBasePackages = {"red.jiuzhou.api", "red.jiuzhou.util"})
public class ModernDbxmlTool extends Application {

    private static final Logger log = LoggerFactory.getLogger(ModernDbxmlTool.class);

    private ConfigurableApplicationContext springContext;

    // 核心组件
    private ModernToolBar modernToolBar;
    private SmartDataTable dataTable;
    private DataVisualization dataVisualization;
    private NotificationSystem notificationSystem;
    private ContextualHelpSystem helpSystem;
    private ValidationFeedbackSystem validationSystem;
    private ErrorRecoverySystem errorRecoverySystem;

    // UI容器
    private BorderPane mainContainer;
    private TabPane centerTabPane;
    private SplitPane mainSplitPane;

    @Override
    public void init() {
        // 初始化 Spring 上下文
        springContext = new SpringApplicationBuilder(ModernDbxmlTool.class).run();
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            initializeComponents(primaryStage);
            createMainLayout(primaryStage);
            setupEventHandlers();
            configureHelpSystem();
            showWelcomeMessage();

            log.info("现代化 DB XML Tool 启动成功");

        } catch (Exception e) {
            log.error("应用启动失败", e);
            showStartupError(primaryStage, e);
        }
    }

    /**
     * 初始化核心组件
     */
    private void initializeComponents(Stage primaryStage) {
        // 初始化通知系统（必须最先初始化）
        notificationSystem = NotificationSystem.getInstance(primaryStage);

        // 初始化错误恢复系统
        errorRecoverySystem = ErrorRecoverySystem.getInstance();

        // 初始化帮助系统
        helpSystem = ContextualHelpSystem.getInstance(primaryStage);

        // 初始化验证系统
        validationSystem = ValidationFeedbackSystem.getInstance();

        // 初始化现代化工具栏
        modernToolBar = new ModernToolBar(primaryStage);

        // 初始化数据组件
        dataTable = new SmartDataTable();
        dataVisualization = new DataVisualization();

        log.info("核心组件初始化完成");
    }

    /**
     * 创建主布局
     */
    private void createMainLayout(Stage primaryStage) {
        mainContainer = new BorderPane();
        mainContainer.getStyleClass().add("main-container");

        // 顶部工具栏
        mainContainer.setTop(modernToolBar.getToolBar());

        // 中央区域
        createCenterArea();
        mainContainer.setCenter(mainSplitPane);

        // 底部状态栏（集成到数据表格中）

        // 创建场景
        Scene scene = new Scene(mainContainer, 1400, 800);
        scene.getStylesheets().add("/modern-theme.css");

        primaryStage.setScene(scene);
        primaryStage.setTitle("DB XML Tool - 现代化数据管理平台");
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(700);
        primaryStage.show();

        // 应用现代化主题
        applyModernTheme();
    }

    /**
     * 创建中央区域
     */
    private void createCenterArea() {
        mainSplitPane = new SplitPane();
        mainSplitPane.getStyleClass().add("main-split-pane");

        // 左侧导航面板
        VBox leftPanel = createNavigationPanel();

        // 右侧主工作区
        centerTabPane = createWorkArea();

        mainSplitPane.getItems().addAll(leftPanel, centerTabPane);
        mainSplitPane.setDividerPositions(0.25);
    }

    /**
     * 创建导航面板
     */
    private VBox createNavigationPanel() {
        VBox navigationPanel = new VBox();
        navigationPanel.getStyleClass().add("navigation-panel");
        navigationPanel.setPrefWidth(300);

        // 快速操作面板
        VBox quickActions = createQuickActionsPanel();

        // 数据源树
        TreeView<String> dataSourceTree = createDataSourceTree();

        // 最近使用
        VBox recentItems = createRecentItemsPanel();

        navigationPanel.getChildren().addAll(
            quickActions,
            new Separator(),
            createSectionHeader("数据源"),
            dataSourceTree,
            new Separator(),
            recentItems
        );

        VBox.setVgrow(dataSourceTree, Priority.ALWAYS);
        return navigationPanel;
    }

    /**
     * 创建快速操作面板
     */
    private VBox createQuickActionsPanel() {
        VBox quickActions = new VBox(8);
        quickActions.getStyleClass().add("quick-actions");
        quickActions.setPadding(new Insets(16));

        Label titleLabel = createSectionHeader("快速操作");

        Button importButton = createActionButton("📥", "智能导入", "导入XML数据到数据库");
        importButton.setOnAction(e -> showImportWizard());

        Button exportButton = createActionButton("📤", "数据导出", "导出数据库数据");
        exportButton.setOnAction(e -> showExportDialog());

        Button queryButton = createActionButton("🔍", "新建查询", "创建SQL查询");
        queryButton.setOnAction(e -> showQueryBuilder());

        Button analyticsButton = createActionButton("📊", "数据分析", "查看数据统计和图表");
        analyticsButton.setOnAction(e -> showDataAnalytics());

        quickActions.getChildren().addAll(
            titleLabel, importButton, exportButton, queryButton, analyticsButton
        );

        return quickActions;
    }

    /**
     * 创建操作按钮
     */
    private Button createActionButton(String icon, String text, String tooltip) {
        Button button = new Button(icon + " " + text);
        button.getStyleClass().addAll("action-button", "modern-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip(tooltip));

        // 绑定帮助上下文
        helpSystem.bindHelp(button, determineHelpContext(text));

        return button;
    }

    /**
     * 创建数据源树
     */
    private TreeView<String> createDataSourceTree() {
        TreeItem<String> root = new TreeItem<>("数据源");
        root.setExpanded(true);

        // 添加示例数据源
        TreeItem<String> databases = new TreeItem<>("数据库表");
        databases.setExpanded(true);

        TreeItem<String> xmlFiles = new TreeItem<>("XML文件");
        xmlFiles.setExpanded(true);

        root.getChildren().addAll(databases, xmlFiles);

        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.getStyleClass().add("data-source-tree");

        // 绑定帮助上下文
        helpSystem.bindHelp(tree, "data-navigation");

        // 添加选择监听器
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isLeaf()) {
                loadDataSource(newVal.getValue());
            }
        });

        return tree;
    }

    /**
     * 创建最近使用面板
     */
    private VBox createRecentItemsPanel() {
        VBox recentPanel = new VBox(8);
        recentPanel.getStyleClass().add("recent-items");
        recentPanel.setPadding(new Insets(16));

        Label titleLabel = createSectionHeader("最近使用");

        // 添加最近使用的项目
        VBox itemsList = new VBox(4);
        itemsList.getChildren().addAll(
            createRecentItem("🗃️", "用户表", "2分钟前"),
            createRecentItem("📄", "配置文件.xml", "1小时前"),
            createRecentItem("🔍", "数据统计查询", "今天")
        );

        recentPanel.getChildren().addAll(titleLabel, itemsList);
        return recentPanel;
    }

    /**
     * 创建最近使用项目
     */
    private HBox createRecentItem(String icon, String name, String time) {
        HBox item = new HBox(8);
        item.getStyleClass().add("recent-item");
        item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("recent-icon");

        VBox textBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("recent-name");

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("recent-time");

        textBox.getChildren().addAll(nameLabel, timeLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        item.getChildren().addAll(iconLabel, textBox);

        // 添加点击事件
        item.setOnMouseClicked(e -> openRecentItem(name));

        return item;
    }

    /**
     * 创建工作区
     */
    private TabPane createWorkArea() {
        TabPane workArea = new TabPane();
        workArea.getStyleClass().add("work-area");
        workArea.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // 添加欢迎标签页
        Tab welcomeTab = createWelcomeTab();
        workArea.getTabs().add(welcomeTab);

        // 绑定帮助上下文
        helpSystem.bindHelp(workArea, "work-area");

        return workArea;
    }

    /**
     * 创建欢迎标签页
     */
    private Tab createWelcomeTab() {
        Tab welcomeTab = new Tab("🏠 开始");
        welcomeTab.setClosable(false);

        VBox welcomeContent = new VBox(30);
        welcomeContent.getStyleClass().add("welcome-content");
        welcomeContent.setPadding(new Insets(40));
        welcomeContent.setAlignment(javafx.geometry.Pos.CENTER);

        // 欢迎标题
        Label titleLabel = new Label("欢迎使用 DB XML Tool");
        titleLabel.getStyleClass().add("welcome-title");

        Label subtitleLabel = new Label("现代化的数据库与XML转换管理平台");
        subtitleLabel.getStyleClass().add("welcome-subtitle");

        // 快速开始卡片
        HBox quickStartCards = createQuickStartCards();

        // 最新功能介绍
        VBox featuresBox = createFeaturesBox();

        welcomeContent.getChildren().addAll(
            titleLabel, subtitleLabel, quickStartCards, featuresBox
        );

        ScrollPane scrollPane = new ScrollPane(welcomeContent);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("welcome-scroll");

        welcomeTab.setContent(scrollPane);
        return welcomeTab;
    }

    /**
     * 创建快速开始卡片
     */
    private HBox createQuickStartCards() {
        HBox cardsContainer = new HBox(20);
        cardsContainer.setAlignment(javafx.geometry.Pos.CENTER);

        VBox importCard = createWelcomeCard(
            "📥", "智能导入", "使用AI增强功能导入XML数据", this::showImportWizard
        );

        VBox queryCard = createWelcomeCard(
            "🔍", "数据查询", "创建和执行SQL查询", this::showQueryBuilder
        );

        VBox analyticsCard = createWelcomeCard(
            "📊", "数据分析", "查看数据统计和可视化图表", this::showDataAnalytics
        );

        cardsContainer.getChildren().addAll(importCard, queryCard, analyticsCard);
        return cardsContainer;
    }

    /**
     * 创建欢迎卡片
     */
    private VBox createWelcomeCard(String icon, String title, String description, Runnable action) {
        VBox card = new VBox(12);
        card.getStyleClass().add("welcome-card");
        card.setPadding(new Insets(24));
        card.setAlignment(javafx.geometry.Pos.CENTER);
        card.setPrefWidth(200);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("card-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");

        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("card-description");
        descLabel.setWrapText(true);

        Button actionButton = new Button("开始使用");
        actionButton.getStyleClass().addAll("card-button", "primary");
        actionButton.setOnAction(e -> action.run());

        card.getChildren().addAll(iconLabel, titleLabel, descLabel, actionButton);

        // 添加悬停效果
        card.setOnMouseEntered(e -> card.getStyleClass().add("card-hover"));
        card.setOnMouseExited(e -> card.getStyleClass().remove("card-hover"));

        return card;
    }

    /**
     * 创建功能介绍
     */
    private VBox createFeaturesBox() {
        VBox featuresBox = new VBox(16);
        featuresBox.getStyleClass().add("features-box");

        Label featuresTitle = new Label("✨ 新功能亮点");
        featuresTitle.getStyleClass().add("features-title");

        GridPane featuresGrid = new GridPane();
        featuresGrid.getStyleClass().add("features-grid");
        featuresGrid.setHgap(20);
        featuresGrid.setVgap(12);

        addFeatureItem(featuresGrid, 0, 0, "🚀", "智能数据处理", "AI驱动的数据清洗和转换");
        addFeatureItem(featuresGrid, 1, 0, "📊", "可视化分析", "直观的数据统计图表");
        addFeatureItem(featuresGrid, 0, 1, "⚡", "实时反馈", "操作状态的即时通知");
        addFeatureItem(featuresGrid, 1, 1, "🛡️", "智能恢复", "自动错误检测和修复");

        featuresBox.getChildren().addAll(featuresTitle, featuresGrid);
        return featuresBox;
    }

    /**
     * 添加功能项目
     */
    private void addFeatureItem(GridPane grid, int col, int row, String icon, String title, String desc) {
        HBox featureItem = new HBox(12);
        featureItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("feature-icon");

        VBox textBox = new VBox(4);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("feature-title");

        Label descLabel = new Label(desc);
        descLabel.getStyleClass().add("feature-description");

        textBox.getChildren().addAll(titleLabel, descLabel);
        featureItem.getChildren().addAll(iconLabel, textBox);

        grid.add(featureItem, col, row);
    }

    /**
     * 创建章节标题
     */
    private Label createSectionHeader(String text) {
        Label header = new Label(text);
        header.getStyleClass().add("section-header");
        return header;
    }

    /**
     * 设置事件处理器
     */
    private void setupEventHandlers() {
        // 全局错误处理
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            log.error("未捕获的异常", exception);
            errorRecoverySystem.handleExceptionAsync(
                (Exception) exception,
                "全局异常处理",
                null
            );
        });

        log.info("事件处理器设置完成");
    }

    /**
     * 配置帮助系统
     */
    private void configureHelpSystem() {
        // 为主要组件注册帮助上下文
        helpSystem.bindHelp(dataTable.getContainer(), "data-table");
        helpSystem.bindHelp(dataVisualization.getContainer(), "data-visualization");

        log.info("帮助系统配置完成");
    }

    /**
     * 应用现代化主题
     */
    private void applyModernTheme() {
        // 主题已通过CSS文件应用
        log.info("现代化主题已应用");
    }

    // ========== 事件处理方法 ==========

    /**
     * 显示导入向导
     */
    private void showImportWizard() {
        try {
            DataImportWizard wizard = new DataImportWizard(getMainStage());
            wizard.show();
            notificationSystem.showInfo("导入向导", "智能导入向导已打开");
        } catch (Exception e) {
            errorRecoverySystem.handleException(e, "显示导入向导");
        }
    }

    /**
     * 显示导出对话框
     */
    private void showExportDialog() {
        notificationSystem.showInfo("导出功能", "数据导出功能开发中...");
    }

    /**
     * 显示查询构建器
     */
    private void showQueryBuilder() {
        Tab queryTab = new Tab("🔍 SQL查询");

        // 创建查询构建器界面
        VBox queryContent = new VBox(16);
        queryContent.setPadding(new Insets(20));

        Label titleLabel = new Label("SQL查询构建器");
        titleLabel.getStyleClass().add("query-title");

        TextArea sqlArea = new TextArea();
        sqlArea.setPromptText("在这里输入SQL查询语句...");
        sqlArea.getStyleClass().add("sql-editor");
        sqlArea.setPrefRowCount(10);

        // 绑定验证
        validationSystem.bindValidation(sqlArea, "sql-query", "required");

        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Button executeButton = new Button("⚡ 执行查询");
        executeButton.getStyleClass().addAll("execute-button", "primary");
        executeButton.setOnAction(e -> executeQuery(sqlArea.getText()));

        Button formatButton = new Button("🎨 格式化");
        formatButton.getStyleClass().addAll("format-button", "secondary");

        Button saveButton = new Button("💾 保存");
        saveButton.getStyleClass().addAll("save-button", "secondary");

        buttonBar.getChildren().addAll(executeButton, formatButton, saveButton);

        queryContent.getChildren().addAll(titleLabel, sqlArea, buttonBar);
        VBox.setVgrow(sqlArea, Priority.ALWAYS);

        queryTab.setContent(queryContent);
        centerTabPane.getTabs().add(queryTab);
        centerTabPane.getSelectionModel().select(queryTab);

        // 绑定帮助上下文
        helpSystem.bindHelp(queryContent, "query-builder");
    }

    /**
     * 显示数据分析
     */
    private void showDataAnalytics() {
        Tab analyticsTab = new Tab("📊 数据分析");
        analyticsTab.setContent(dataVisualization.getContainer());

        centerTabPane.getTabs().add(analyticsTab);
        centerTabPane.getSelectionModel().select(analyticsTab);

        // 加载示例数据
        loadSampleDataForVisualization();
    }

    /**
     * 加载数据源
     */
    private void loadDataSource(String dataSourceName) {
        try {
            Tab dataTab = new Tab("📋 " + dataSourceName);
            dataTab.setContent(dataTable.getContainer());

            centerTabPane.getTabs().add(dataTab);
            centerTabPane.getSelectionModel().select(dataTab);

            // 加载示例数据
            loadSampleDataForTable();

            notificationSystem.showSuccess("数据加载", "数据源 '" + dataSourceName + "' 已加载");

        } catch (Exception e) {
            errorRecoverySystem.handleException(e, "加载数据源: " + dataSourceName);
        }
    }

    /**
     * 打开最近项目
     */
    private void openRecentItem(String itemName) {
        notificationSystem.showInfo("打开项目", "正在打开: " + itemName);
        // TODO: 实现打开最近项目的逻辑
    }

    /**
     * 执行查询
     */
    private void executeQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            notificationSystem.showWarning("查询执行", "请输入SQL查询语句");
            return;
        }

        ProgressMonitor progressMonitor = new ProgressMonitor(getMainStage(), "执行查询");
        progressMonitor.show();

        // 模拟查询执行
        new Thread(() -> {
            try {
                progressMonitor.updateProgress(0.3, "解析SQL语句", "正在分析查询结构...");
                Thread.sleep(1000);

                progressMonitor.updateProgress(0.6, "执行查询", "正在从数据库获取数据...");
                Thread.sleep(1500);

                progressMonitor.updateProgress(0.9, "处理结果", "正在格式化查询结果...");
                Thread.sleep(500);

                progressMonitor.complete("查询执行完成");

                javafx.application.Platform.runLater(() -> {
                    notificationSystem.showSuccess("查询完成", "SQL查询已成功执行");
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                progressMonitor.fail("查询被中断");
            } catch (Exception e) {
                progressMonitor.fail("查询执行失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 加载示例数据到表格
     */
    private void loadSampleDataForTable() {
        // TODO: 加载真实数据
        // 这里使用模拟数据进行演示
        java.util.List<java.util.Map<String, Object>> sampleData = createSampleTableData();
        dataTable.setData(sampleData);
    }

    /**
     * 加载示例数据到可视化组件
     */
    private void loadSampleDataForVisualization() {
        // TODO: 加载真实数据
        java.util.List<java.util.Map<String, Object>> sampleData = createSampleTableData();
        dataVisualization.updateData(sampleData);
    }

    /**
     * 创建示例表格数据
     */
    private java.util.List<java.util.Map<String, Object>> createSampleTableData() {
        java.util.List<java.util.Map<String, Object>> data = new java.util.ArrayList<>();

        for (int i = 1; i <= 100; i++) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", i);
            row.put("name", "用户" + i);
            row.put("email", "user" + i + "@example.com");
            row.put("age", 20 + (i % 50));
            row.put("department", i % 2 == 0 ? "技术部" : "设计部");
            row.put("created_date", "2024-" + String.format("%02d", (i % 12) + 1) + "-01");
            data.add(row);
        }

        return data;
    }

    /**
     * 确定帮助上下文
     */
    private String determineHelpContext(String buttonText) {
        if (buttonText.contains("导入")) return "data-import";
        if (buttonText.contains("导出")) return "data-export";
        if (buttonText.contains("查询")) return "query-builder";
        if (buttonText.contains("分析")) return "data-visualization";
        return "general";
    }

    /**
     * 显示欢迎消息
     */
    private void showWelcomeMessage() {
        javafx.application.Platform.runLater(() -> {
            notificationSystem.showSuccess(
                "欢迎使用 DB XML Tool",
                "现代化数据管理平台已准备就绪！"
            );
        });
    }

    /**
     * 显示启动错误
     */
    private void showStartupError(Stage primaryStage, Exception e) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("启动失败");
        alert.setHeaderText("应用程序启动时发生错误");
        alert.setContentText("错误信息: " + e.getMessage());
        alert.showAndWait();
        javafx.application.Platform.exit();
    }

    /**
     * 获取主舞台
     */
    private Stage getMainStage() {
        return (Stage) mainContainer.getScene().getWindow();
    }
}