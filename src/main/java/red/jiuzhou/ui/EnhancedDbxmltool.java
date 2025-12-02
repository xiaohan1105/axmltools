package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import red.jiuzhou.dbxml.DirectoryManagerDialog;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.ui.error.EnhancedErrorHandler;
import red.jiuzhou.ui.layout.LayoutStateManager;
import red.jiuzhou.ui.layout.LayoutState;
import red.jiuzhou.ui.layout.ResponsiveLayoutManager;
import red.jiuzhou.ui.layout.WindowStateManager;
import red.jiuzhou.relationship.XmlRelationshipAnalyzer;
import red.jiuzhou.ui.features.*;
import red.jiuzhou.util.AIAssistant;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.IncrementalMenuJsonGenerator;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.YmlConfigUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;

/**
 * 增强版主程序
 * 集成响应式布局、错误恢复、状态持久化等高级功�?
 *
 * @author Claude Code Enhanced
 * @version 2.0
 */
@SpringBootApplication(scanBasePackages = {"red.jiuzhou.api", "red.jiuzhou.util"})
@SuppressWarnings("unchecked")
public class EnhancedDbxmltool extends Application {

    private static final Logger log = LoggerFactory.getLogger(EnhancedDbxmltool.class);

    // Spring 上下�?
    private ConfigurableApplicationContext springContext;

    // 核心管理�?
    private ResponsiveLayoutManager layoutManager;
    private WindowStateManager windowStateManager;
    private LayoutStateManager layoutStateManager;
    private EnhancedErrorHandler errorHandler;

    // UI 组件
    private Stage primaryStage;
    private FeatureRegistry featureRegistry = FeatureRegistry.defaultRegistry();

    // 应用状�?
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<String> currentTabName = new AtomicReference<>("");

    @Override
    public void init() {
        try {
            // 初始�?Spring 上下�?
            springContext = new SpringApplicationBuilder(EnhancedDbxmltool.class).run();
            log.info("Spring 上下文初始化完成");

            // 初始化核心管理器
            initializeManagers();

            // 初始化错误处理器
            initializeErrorHandler();

            log.info("增强版DbxmlTool 初始化完成");

        } catch (Exception e) {
            log.error("应用程序初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    @Override
    public void stop() {
        try {
            // 清理资源
            cleanupResources();

            // 关闭 Spring 上下�?
            if (springContext != null) {
                springContext.close();
            }

            // 关闭功能任务执行�?
            FeatureTaskExecutor.shutdown();

            log.info("应用程序正常关闭");

        } catch (Exception e) {
            log.error("关闭应用程序时出错", e);
        }
    }

    /**
     * 初始化管理器
     */
    private void initializeManagers() {
        try {
            // 获取配置目录
            String configDir = YamlUtils.getProperty("file.homePath");
            if (configDir == null || configDir.trim().isEmpty()) {
                configDir = System.getProperty("user.home") + File.separator + ".dbxmlTool";
            }

            // 初始化布局状态管理器
            layoutStateManager = new LayoutStateManager(configDir);

            // 初始化错误处理器
            errorHandler = new EnhancedErrorHandler(null); // 传入 null，稍后设�?stage

            // 响应式布局管理器将�?start 方法中初始化（需�?stage�?
            // 窗口状态管理器将在 start 方法中初始化（需�?stage �?layoutManager�?

        } catch (Exception e) {
            log.error("初始化管理器失败", e);
            throw new RuntimeException("管理器初始化失败", e);
        }
    }

    /**
     * 初始化错误处理器
     */
    private void initializeErrorHandler() {
        // 添加状态变更监听器
        layoutStateManager.addStateChangeListener(newState -> {
            // 当布局状态变更时，可以执行相关操�?
            log.debug("布局状态已变更: {}", newState.getLastModified());
        });

        // 设置全局错误处理
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            errorHandler.handleExceptionAsync(throwable, "全局未捕获异常", thread.getName());
        });
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            this.primaryStage = primaryStage;

            // 完成管理器初始化
            completeManagerInitialization(primaryStage);

            // 创建主界�?
            createMainInterface();

            // 应用已保存的状�?
            applySavedState();

            // 显示界面
            showInterface();

            // 标记为已初始�?
            initialized.set(true);

            log.info("增强版DbxmlTool 启动完成");

        } catch (Exception e) {
            log.error("启动应用程序失败", e);
            errorHandler.handleException(e, "应用程序启动", "start");
        }
    }

    /**
     * 完成管理器初始化
     */
    private void completeManagerInitialization(Stage primaryStage) {
        // 初始化响应式布局管理�?
        layoutManager = new ResponsiveLayoutManager(primaryStage);

        // 初始化窗口状态管理器
        windowStateManager = new WindowStateManager(primaryStage, layoutManager);

        // 设置错误处理器的 owner stage
        errorHandler = new EnhancedErrorHandler(primaryStage);

        // 添加布局状态监听器
        layoutManager.currentLevelProperty().addListener((obs, oldVal, newVal) -> {
            log.info("响应式级别变�? {} -> {}", oldVal.getDescription(), newVal.getDescription());
        });

        // 添加窗口状态监听器
        windowStateManager.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (initialized.get()) {
                layoutStateManager.updateWindowState(primaryStage,
                    layoutManager.getMainSplitPane().getDividers().get(0).getPosition());
            }
        });
    }

    /**
     * 创建主界�?
     */
    private void createMainInterface() {
        // 增量生成菜单 JSON
        IncrementalMenuJsonGenerator.createJsonIncrementally();

        // 初始�?AI 助手
        initializeAIAssistant();

        // 创建主布局
        BorderPane root = layoutManager.getRootContainer();

        // 添加顶部工具�?
        ToolBar toolBar = createEnhancedToolBar();
        root.setTop(toolBar);

        // 创建主要内容区域
        createMainContentArea();

        // 创建状态栏
        StatusBar statusBar = createStatusBar();
        root.setBottom(statusBar);
    }

    /**
     * 初始�?AI 助手
     */
    private void initializeAIAssistant() {
        try {
            AIAssistant aiAssistant = springContext.getBean(AIAssistant.class);

            // 初始�?AI 转换服务
            red.jiuzhou.theme.AITransformService.initialize(aiAssistant);

            log.info("AI 助手初始化成功");

        } catch (Exception e) {
            log.warn("AI 助手初始化失败: {}", e.getMessage());
            // 不影响应用程序启�?
        }
    }

    /**
     * 创建增强的工具栏
     */
    private ToolBar createEnhancedToolBar() {
        ToolBar toolBar = new ToolBar();
        toolBar.getStyleClass().add("enhanced-tool-bar");

        // 主菜单按�?
        MenuButton mainMenuButton = new MenuButton("主菜单");
        mainMenuButton.getStyleClass().add("main-menu-button");

        MenuItem newProjectItem = new MenuItem("新建项目");
        MenuItem openProjectItem = new MenuItem("打开项目");
        MenuItem saveProjectItem = new MenuItem("保存项目");
        SeparatorMenuItem separator1 = new SeparatorMenuItem();
        MenuItem importDataItem = new MenuItem("导入数据");
        MenuItem exportDataItem = new MenuItem("导出数据");
        SeparatorMenuItem separator2 = new SeparatorMenuItem();
        MenuItem settingsItem = new MenuItem("设置");
        MenuItem aboutItem = new MenuItem("关于");
        MenuItem exitItem = new MenuItem("退出");

        mainMenuButton.getItems().addAll(
            newProjectItem, openProjectItem, saveProjectItem, separator1,
            importDataItem, exportDataItem, separator2,
            settingsItem, aboutItem, separator2, exitItem
        );

        // 设置菜单项事�?
        exitItem.setOnAction(e -> handleExitAction());
        settingsItem.setOnAction(e -> handleSettingsAction());
        aboutItem.setOnAction(e -> handleAboutAction());

        // 功能按钮
        Button enumQueryBtn = new Button("枚举查询");
        enumQueryBtn.getStyleClass().addAll("feature-button", "primary");

        Button sqlConverterBtn = new Button("SQL转换");
        sqlConverterBtn.getStyleClass().addAll("feature-button", "secondary");

        Button relationBtn = new Button("字段关联");
        relationBtn.getStyleClass().addAll("feature-button", "info");

        Button directoryBtn = new Button("目录管理");
        directoryBtn.getStyleClass().addAll("feature-button", "warning");

        // 响应式布局切换按钮
        ToggleButton leftPanelToggleBtn = new ToggleButton("左侧面板");
        leftPanelToggleBtn.getStyleClass().addAll("toggle-button");
        leftPanelToggleBtn.setSelected(true);
        leftPanelToggleBtn.setOnAction(e -> toggleLeftPanel());

        // 视图模式按钮
        ComboBox<String> viewModeCombo = new ComboBox<>();
        viewModeCombo.getItems().addAll("自适应", "紧凑", "宽松", "全屏");
        viewModeCombo.setValue("自适应");
        viewModeCombo.getStyleClass().add("view-mode-combo");
        viewModeCombo.setOnAction(e -> handleViewModeChange(viewModeCombo.getValue()));

        // 添加工具提示 - 分离 ToggleButton
        setupButtonTooltips(enumQueryBtn, sqlConverterBtn, relationBtn, directoryBtn);
        setupToggleButtonTooltip(leftPanelToggleBtn);

        // 设置按钮事件
        setupButtonEvents(enumQueryBtn, sqlConverterBtn, relationBtn, directoryBtn);

        // 窗口控制按钮
        Button minimizeBtn = new Button("─");
        Button maximizeBtn = new Button("□");
        Button closeBtn = new Button("✕");

        minimizeBtn.getStyleClass().addAll("window-control", "minimize");
        maximizeBtn.getStyleClass().addAll("window-control", "maximize");
        closeBtn.getStyleClass().addAll("window-control", "close");

        // 窗口控制事件
        minimizeBtn.setOnAction(e -> primaryStage.setIconified(true));
        maximizeBtn.setOnAction(e -> windowStateManager.toggleMaximize());
        closeBtn.setOnAction(e -> handleExitAction());

        // 响应式分隔符
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 添加到工具栏
        toolBar.getItems().addAll(
            mainMenuButton,
            new Separator(),
            enumQueryBtn,
            sqlConverterBtn,
            relationBtn,
            directoryBtn,
            new Separator(),
            leftPanelToggleBtn,
            viewModeCombo,
            spacer,
            minimizeBtn,
            maximizeBtn,
            closeBtn
        );

        return toolBar;
    }

    /**
     * 创建主要内容区域
     */
    private void createMainContentArea() {
        VBox leftPanel = layoutManager.getLeftPanel();
        VBox rightPanel = layoutManager.getRightPanel();

        // 配置左侧面板
        setupLeftPanel(leftPanel);

        // 配置右侧面板
        setupRightPanel(rightPanel);
    }

    /**
     * 设置左侧面板
     */
    private void setupLeftPanel(VBox leftPanel) {
        // 添加快速操作区�?
        HBox quickActions = createQuickActions();
        leftPanel.getChildren().add(quickActions);

        // 添加文件�?
        TreeView<String> fileTree = createFileTree();
        VBox.setVgrow(fileTree, Priority.ALWAYS);
        leftPanel.getChildren().add(fileTree);

        // 添加状态信�?
        Label leftPanelStatus = new Label("就绪");
        leftPanelStatus.getStyleClass().add("panel-status");
        leftPanel.getChildren().add(leftPanelStatus);
    }

    /**
     * 创建快速操作区�?
     */
    private HBox createQuickActions() {
        HBox quickActions = new HBox(8);
        quickActions.getStyleClass().add("quick-actions");
        quickActions.setAlignment(Pos.CENTER_LEFT);

        Button openLocationBtn = new Button("打开位置");
        Button copyPathBtn = new Button("复制路径");
        Button openFileBtn = new Button("打开文件");
        Button refreshBtn = new Button("刷新");

        openLocationBtn.getStyleClass().addAll("quick-action-button", "small");
        copyPathBtn.getStyleClass().addAll("quick-action-button", "small");
        openFileBtn.getStyleClass().addAll("quick-action-button", "small");
        refreshBtn.getStyleClass().addAll("quick-action-button", "small");

        // 设置事件
        setupQuickActionEvents(openLocationBtn, copyPathBtn, openFileBtn, refreshBtn);

        quickActions.getChildren().addAll(openLocationBtn, copyPathBtn, openFileBtn, refreshBtn);

        return quickActions;
    }

    /**
     * 创建文件�?
     */
    private TreeView<String> createFileTree() {
        TreeView<String> fileTree = new TreeView<>();
        fileTree.getStyleClass().add("file-tree");
        fileTree.setRoot(new TreeItem<>("根目录"));
        fileTree.setShowRoot(false);

        try {
            // 加载左侧菜单配置
            String leftMenuJson = FileUtil.readUtf8String(
                YamlUtils.getProperty("file.homePath") + File.separator + "leftMenu.json");

            // 构建树形结构
            buildFileTree(fileTree.getRoot(), JSON.parseObject(leftMenuJson), "");

            // 设置单元格工�?
            fileTree.setCellFactory(tv -> new FileTreeCell());

        } catch (Exception e) {
            log.error("创建文件树失败", e);
            errorHandler.handleException(e, "文件树创建", "createFileTree");
        }

        return fileTree;
    }

    /**
     * 构建文件树结�?
     */
    private void buildFileTree(TreeItem<String> parentItem, JSONObject node, String fullPath) {
        String name = node.getString("name");
        JSONArray children = node.getJSONArray("children");

        String currentPath = fullPath.isEmpty() ? name : fullPath + File.separator + name;

        TreeItem<String> currentItem = new TreeItem<>(name);
        currentItem.setExpanded(true);

        // 创建数据对象并存�?
        FileTreeData data = new FileTreeData(name, currentPath);
        data.setDirectory(children != null && !children.isEmpty());
        data.setJsonNode(node);
        TreeItemMetadata.setMetadata(currentItem, data);

        if (children != null && !children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                buildFileTree(currentItem, children.getJSONObject(i), currentPath);
            }
        }

        parentItem.getChildren().add(currentItem);
    }

    /**
     * 设置右侧面板
     */
    private void setupRightPanel(VBox rightPanel) {
        // 创建标签�?
        TabPane tabPane = createTabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        rightPanel.getChildren().add(tabPane);
    }

    /**
     * 创建标签�?
     */
    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // 添加欢迎标签�?
        Tab welcomeTab = new Tab("欢迎使用");
        welcomeTab.setClosable(false);
        welcomeTab.setContent(createWelcomeContent());
        tabPane.getTabs().add(welcomeTab);

        // 监听标签页切�?
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                currentTabName.set(newTab.getText());
                log.info("切换到标签页: {}", newTab.getText());
            }
        });

        return tabPane;
    }

    /**
     * 创建欢迎内容
     */
    private Node createWelcomeContent() {
        VBox welcomeContent = new VBox(20);
        welcomeContent.getStyleClass().add("welcome-content");
        welcomeContent.setAlignment(Pos.CENTER);
        welcomeContent.setPadding(new Insets(40));

        Label titleLabel = new Label("欢迎使用 DB_XML Tool");
        titleLabel.getStyleClass().add("welcome-title");

        Label subtitleLabel = new Label("专业的数据库与XML文件转换工具");
        subtitleLabel.getStyleClass().add("welcome-subtitle");

        // 快速开始按�?
        HBox quickStartBox = new HBox(15);
        quickStartBox.setAlignment(Pos.CENTER);

        Button newProjectBtn = new Button("新建项目");
        Button openProjectBtn = new Button("打开项目");
        Button importDataBtn = new Button("导入数据");

        newProjectBtn.getStyleClass().addAll("action-button", "primary", "large");
        openProjectBtn.getStyleClass().addAll("action-button", "secondary", "large");
        importDataBtn.getStyleClass().addAll("action-button", "outline", "large");

        quickStartBox.getChildren().addAll(newProjectBtn, openProjectBtn, importDataBtn);

        // 最近项目列�?
        VBox recentProjectsBox = new VBox(10);
        recentProjectsBox.getStyleClass().add("recent-projects");
        recentProjectsBox.setAlignment(Pos.CENTER_LEFT);

        Label recentTitle = new Label("最近项目");
        recentTitle.getStyleClass().add("section-title");

        ListView<String> recentProjectsList = new ListView<>();
        recentProjectsList.getStyleClass().add("recent-projects-list");
        recentProjectsList.setPrefHeight(200);
        recentProjectsList.getItems().addAll("项目示例 1", "项目示例 2", "项目示例 3");

        recentProjectsBox.getChildren().addAll(recentTitle, recentProjectsList);

        welcomeContent.getChildren().addAll(titleLabel, subtitleLabel, quickStartBox, recentProjectsBox);

        return welcomeContent;
    }

    /**
     * 创建状态栏
     */
    private StatusBar createStatusBar() {
        StatusBar statusBar = new StatusBar();
        statusBar.getStyleClass().add("status-bar");

        // 添加状态信�?
        statusBar.setStatus("就绪");
        statusBar.setProgress(0);

        return statusBar;
    }

    /**
     * 应用已保存的状�?
     */
    private void applySavedState() {
        try {
            // 应用窗口状�?
            LayoutState.WindowState windowState = layoutStateManager.getCurrentState().getWindowState();
            if (windowState.getX() >= 0 && windowState.getY() >= 0) {
                primaryStage.setX(windowState.getX());
                primaryStage.setY(windowState.getY());
            }

            if (windowState.getWidth() > 0 && windowState.getHeight() > 0) {
                primaryStage.setWidth(windowState.getWidth());
                primaryStage.setHeight(windowState.getHeight());
            }

            primaryStage.setMaximized(windowState.isMaximized());

            // 应用UI状�?
            LayoutState.UIState uiState = layoutStateManager.getCurrentState().getUiState();
            applyTheme(uiState.getTheme());

            log.info("已保存的状态应用完成");

        } catch (Exception e) {
            log.error("应用保存状态失败", e);
        }
    }

    /**
     * 应用主题
     */
    private void applyTheme(String theme) {
        try {
            Scene scene = primaryStage.getScene();
            if (scene != null) {
                // 移除现有样式�?
                scene.getStylesheets().clear();

                // 添加基础主题
                scene.getStylesheets().add("/modern-theme.css");

                // 添加增强响应式主�?
                scene.getStylesheets().add("/enhanced-responsive-theme.css");

                // 添加主题变体
                if ("dark".equals(theme)) {
                    scene.getStylesheets().add("/themes/dark-theme.css");
                }

                log.info("主题已应用: {}", theme);

            }
        } catch (Exception e) {
            log.error("应用主题失败: {}", theme, e);
        }
    }

    /**
     * 显示界面
     */
    private void showInterface() {
        // 设置场景
        Scene scene = new Scene(layoutManager.getRootContainer(), 1400, 800);
        scene.getStylesheets().add("/enhanced-responsive-theme.css");

        primaryStage.setScene(scene);
        primaryStage.setTitle("DB_XML Tool Enhanced");
        primaryStage.show();

        // 应用最终布局
        Platform.runLater(() -> {
            // 初始化响应式级别
            layoutManager.updateResponsiveLayout(); // 使用公共方法
        });
    }

    /**
     * 清理资源
     */
    private void cleanupResources() {
        try {
            // 保存当前状�?
            if (windowStateManager != null && layoutManager != null) {
                layoutStateManager.updateWindowState(primaryStage,
                    layoutManager.getMainSplitPane().getDividers().get(0).getPosition());
            }

            // 关闭状态管理器
            if (layoutStateManager != null) {
                layoutStateManager.close();
            }

            log.info("资源清理完成");

        } catch (Exception e) {
            log.error("资源清理失败", e);
        }
    }

    /**
     * 事件处理方法
     */

    private void setupButtonTooltips(Button... buttons) {
        // 设置按钮工具提示
        for (Button button : buttons) {
            Tooltip tooltip = new Tooltip(getTooltipText(button));
            tooltip.setStyle("-fx-font-size: 12px;");
            Tooltip.install(button, tooltip);
        }
    }

    private String getTooltipText(Button button) {
        // 根据按钮文本返回对应的工具提�?
        String text = button.getText();
        switch (text) {
            case "枚举查询": return "查询数据库枚举值";
            case "SQL转换": return "SQL语句转换工具";
            case "字段关联": return "分析字段关联关系";
            case "目录管理": return "管理项目目录";
            default: return "点击执行操作";
        }
    }

    private void setupToggleButtonTooltip(ToggleButton toggleButton) {
        Tooltip tooltip = new Tooltip("切换左侧面板显示");
        tooltip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(toggleButton, tooltip);
    }

    private void setupButtonEvents(Button enumBtn, Button sqlBtn, Button relationBtn, Button directoryBtn) {
        enumBtn.setOnAction(e -> openEnumQuery());
        sqlBtn.setOnAction(e -> openSqlConverter());
        relationBtn.setOnAction(e -> analyzeFieldRelationships());
        directoryBtn.setOnAction(e -> openDirectoryManager());
    }

    private void setupQuickActionEvents(Button openLocationBtn, Button copyPathBtn, Button openFileBtn, Button refreshBtn) {
        // TODO: 实现快速操作事�?
    }

    private void openEnumQuery() {
        try {
            new EnumQuery().showQueryWindow(primaryStage);
        } catch (Exception e) {
            errorHandler.handleException(e, "打开枚举查询", "openEnumQuery");
        }
    }

    private void openSqlConverter() {
        try {
            // 使用现有实例的方法，而不是创建新�?Application
            SQLConverterApp converterApp = new SQLConverterApp();
            // 调用现有的显示方法，假设存在这样的方�?
            // converterApp.showConverterWindow(new Stage());

            // 或者使用备用方案：在当前应用中显示转换�?
            showNotification("SQL转换器功能正在开发中", NotificationType.INFO);
        } catch (Exception e) {
            errorHandler.handleException(e, "打开SQL转换器", "openSqlConverter");
        }
    }

    private void analyzeFieldRelationships() {
        // TODO: 实现字段关联分析
    }

    private void openDirectoryManager() {
        try {
            DirectoryManagerDialog dialog = new DirectoryManagerDialog(this::refreshDirectories);
            dialog.show(primaryStage);
        } catch (Exception e) {
            errorHandler.handleException(e, "打开目录管理器", "openDirectoryManager");
        }
    }

    private void refreshDirectories() {
        try {
            IncrementalMenuJsonGenerator.createJsonIncrementally();
        } catch (Exception e) {
            errorHandler.handleException(e, "刷新目录", "refreshDirectories");
        }
    }

    private void toggleLeftPanel() {
        layoutManager.toggleLeftPanel();
    }

    private void handleViewModeChange(String viewMode) {
        switch (viewMode) {
            case "紧凑":
                primaryStage.setWidth(1200);
                primaryStage.setHeight(700);
                break;
            case "宽松":
                primaryStage.setWidth(1600);
                primaryStage.setHeight(900);
                break;
            case "全屏":
                primaryStage.setFullScreen(true);
                break;
            case "自适应":
            default:
                primaryStage.setFullScreen(false);
                windowStateManager.fitWindowToContent();
                break;
        }
    }

    private void handleExitAction() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "确定要退出应用程序吗？", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                cleanupResources();
                Platform.exit();
                System.exit(0);
            }
        });
    }

    private void handleSettingsAction() {
        // TODO: 实现设置对话�?
        showNotification("设置功能正在开发中", NotificationType.INFO);
    }

    private void handleAboutAction() {
        Alert aboutAlert = new Alert(Alert.AlertType.INFORMATION);
        aboutAlert.setTitle("关于");
        aboutAlert.setHeaderText("DB_XML Tool Enhanced");
        aboutAlert.setContentText("版本 2.0\n专业的数据库与XML文件转换工具\n\n集成功能:\n• 响应式布局\n• 智能错误恢复\n• 状态持久化\n• 现代化UI设计");
        aboutAlert.showAndWait();
    }

    private void showNotification(String message, NotificationType type) {
        // TODO: 实现通知显示
        log.info("通知: [{}] {}", type, message);
    }

    /**
     * 文件树单元格
     */
    private static class FileTreeCell extends TreeCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item);
                // TODO: 添加文件/文件夹图�?
            }
        }
    }

    /**
     * 文件树数据项
     */
    private static class FileTreeData {
        private String fullPath;
        private String name;
        private boolean isDirectory;
        private JSONObject jsonNode;

        public FileTreeData(String name, String fullPath) {
            this.name = name;
            this.fullPath = fullPath;
            this.isDirectory = false;
        }

        // Getters and Setters
        public String getFullPath() { return fullPath; }
        public void setFullPath(String fullPath) { this.fullPath = fullPath; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isDirectory() { return isDirectory; }
        public void setDirectory(boolean directory) { isDirectory = directory; }
        public JSONObject getJsonNode() { return jsonNode; }
        public void setJsonNode(JSONObject jsonNode) { this.jsonNode = jsonNode; }
    }

    /**
     * 状态栏组件
     */
    private static class StatusBar extends HBox {
        private final Label statusLabel;
        private final ProgressBar progressBar;
        private final Label progressLabel;

        public StatusBar() {
            setStyle("-fx-background-color: #f8f9fa; -fx-padding: 4px 8px; -fx-alignment: center-left;");
            setSpacing(10);

            statusLabel = new Label("就绪");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");

            progressBar = new ProgressBar();
            progressBar.setProgress(0);
            progressBar.setPrefWidth(200);
            progressBar.setVisible(false);

            progressLabel = new Label("");
            progressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");
            progressLabel.setVisible(false);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label memoryLabel = new Label();
            memoryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");

            // 更新内存使用情况
            updateMemoryUsage(memoryLabel);

            getChildren().addAll(statusLabel, progressBar, progressLabel, spacer, memoryLabel);
        }

        public void setStatus(String status) {
            statusLabel.setText(status);
        }

        public void setProgress(double progress) {
            if (progress > 0) {
                progressBar.setVisible(true);
                progressBar.setProgress(progress);
                progressLabel.setVisible(true);
                progressLabel.setText(String.format("%.0f%%", progress * 100));
            } else {
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
            }
        }

        private void updateMemoryUsage(Label memoryLabel) {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            String memoryText = String.format("内存: %.1fMB / %.1fMB",
                usedMemory / (1024.0 * 1024.0),
                totalMemory / (1024.0 * 1024.0));

            memoryLabel.setText(memoryText);

            // 定期更新
            javafx.animation.Timeline memoryUpdateTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> updateMemoryUsage(memoryLabel))
            );
            memoryUpdateTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
            memoryUpdateTimer.play();
        }
    }

    /**
     * 通知类型枚举
     */
    private enum NotificationType {
        INFO, WARNING, ERROR, SUCCESS
    }

    /**
     * 主方法入�?
     */
    public static void main(String[] args) {
        launch(args);
    }
}
