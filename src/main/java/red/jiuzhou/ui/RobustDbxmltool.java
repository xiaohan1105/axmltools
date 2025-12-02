package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
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
import red.jiuzhou.ui.error.EnhancedErrorHandler;
import red.jiuzhou.ui.layout.LayoutStateManager;
import red.jiuzhou.ui.layout.LayoutState;
import red.jiuzhou.ui.layout.ResponsiveLayoutManager;
import red.jiuzhou.ui.layout.WindowStateManager;
import red.jiuzhou.ui.features.*;
import red.jiuzhou.util.AIAssistant;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.IncrementalMenuJsonGenerator;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.YmlConfigUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 健壮版主程序
 * 采用最佳实践设计，确保兼容性和可维护性
 *
 * @author Claude Code Robust
 * @version 3.0
 */
@SpringBootApplication(scanBasePackages = {"red.jiuzhou.api", "red.jiuzhou.util"})
@SuppressWarnings("unchecked")
public class RobustDbxmltool extends Application {

    private static final Logger log = LoggerFactory.getLogger(RobustDbxmltool.class);

    // 核心组件
    private ConfigurableApplicationContext springContext;
    private final FeatureRegistry featureRegistry = FeatureRegistry.defaultRegistry();

    // UI 管理器
    private ResponsiveLayoutManager layoutManager;
    private WindowStateManager windowStateManager;
    private LayoutStateManager layoutStateManager;
    private EnhancedErrorHandler errorHandler;

    // 应用状态
    private Stage primaryStage;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<String> currentTabName = new AtomicReference<>("");

    // UI 组件映射
    private final Map<String, Node> uiComponents = new ConcurrentHashMap<>();
    private final Map<String, Object> componentStates = new ConcurrentHashMap<>();

    @Override
    public void init() {
        try {
            log.info("初始化健壮版 DB_XML Tool...");

            // 初始化Spring 上下文
            springContext = new SpringApplicationBuilder(RobustDbxmltool.class).run();

            // 设置全局异常处理
            setupGlobalExceptionHandling();

            log.info("初始化完成");

        } catch (Exception e) {
            log.error("初始化失败", e);
            throw new RuntimeException("应用初始化失败", e);
        }
    }

    @Override
    public void stop() {
        try {
            log.info("正在关闭应用...");

            // 保存状态
            if (layoutStateManager != null) {
                layoutStateManager.close();
            }

            // 关闭 Spring 上下文
            if (springContext != null) {
                springContext.close();
            }

            // 关闭功能执行器
            FeatureTaskExecutor.shutdown();

            log.info("应用已安全关闭");

        } catch (Exception e) {
            log.error("关闭应用时出错", e);
        }
    }

    /**
     * 主入�?
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        try {
            // 完成初始�?
            completeInitialization(primaryStage);

            // 创建主界�?
            createMainInterface();

            // 应用已保存的状�?
            applySavedState();

            // 显示界面
            showInterface();

            // 标记为已初始�?
            initialized.set(true);

            log.info("健壮�?DB_XML Tool 启动成功");

        } catch (Exception e) {
            log.error("启动失败", e);
            handleStartupError(e);
        }
    }

    /**
     * 完成初始�?
     */
    private void completeInitialization(Stage stage) {
        // 获取配置目录
        String configDir = getConfigDirectory();

        // 初始化布局管理�?
        layoutManager = new ResponsiveLayoutManager(stage);

        // 初始化窗口状态管理器
        windowStateManager = new WindowStateManager(stage, layoutManager);

        // 初始化布局状态管理器
        layoutStateManager = new LayoutStateManager(configDir);

        // 初始化错误处理器
        errorHandler = new EnhancedErrorHandler(stage);

        // 设置状态监听器
        setupStateListeners();

        log.info("管理器初始化完成");
    }

    /**
     * 获取配置目录
     */
    private String getConfigDirectory() {
        try {
            String configDir = YamlUtils.getProperty("file.homePath");
            if (configDir == null || configDir.trim().isEmpty()) {
                configDir = System.getProperty("user.home") + File.separator + ".dbxmlTool";
            }

            // 确保目录存在
            File dir = new File(configDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            return configDir;
        } catch (Exception e) {
            log.warn("获取配置目录失败，使用默认值", e);
            return System.getProperty("user.home") + File.separator + ".dbxmlTool";
        }
    }

    /**
     * 设置全局异常处理
     */
    private void setupGlobalExceptionHandling() {
        // 设置未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("未捕获异常[{}]: {}", thread.getName(), throwable.getMessage(), throwable);
            errorHandler.handleExceptionAsync(throwable, "全局未捕获异常", thread.getName());
        });

        // 设置JavaFX异常处理�?
        Platform.setImplicitExit(false);
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            Platform.runLater(() -> {
                log.error("JavaFX线程异常: {}", throwable.getMessage(), throwable);
                errorHandler.handleException(throwable, "JavaFX线程异常", thread.getName());
            });
        });
    }

    /**
     * 设置状态监听器
     */
    private void setupStateListeners() {
        // 监听响应式级别变�?
        layoutManager.currentLevelProperty().addListener((obs, oldVal, newVal) -> {
            log.info("响应式级别变�? {} -> {}", oldVal, newVal);
            // 保存响应式级别状�?
            componentStates.put("responsiveLevel", newVal.name());
        });

        // 监听窗口尺寸变化
        windowStateManager.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (initialized.get()) {
                // 异步保存窗口状�?
                saveWindowStateAsync();
            }
        });
    }

    /**
     * 异步保存窗口状�?
     */
    private void saveWindowStateAsync() {
        if (layoutStateManager != null && primaryStage != null) {
            double dividerPosition = layoutManager.getMainSplitPane().getDividers().isEmpty()
                ? 0.3
                : layoutManager.getMainSplitPane().getDividers().get(0).getPosition();

            // 使用新线程避免阻塞UI
            new Thread(() -> {
                try {
                    layoutStateManager.updateWindowState(primaryStage, dividerPosition);
                } catch (Exception e) {
                    log.warn("保存窗口状态失败", e);
                }
            }, "WindowStateSaver").start();
        }
    }

    /**
     * 创建主界�?
     */
    private void createMainInterface() {
        log.info("创建主界�?..");

        // 增量生成菜单
        try {
            IncrementalMenuJsonGenerator.createJsonIncrementally();
        } catch (Exception e) {
            log.warn("生成菜单JSON失败，使用默认配置", e);
        }

        // 初始化AI助手
        initializeAIAssistant();

        // 创建根容�?
        BorderPane root = layoutManager.getRootContainer();

        // 创建工具�?
        ToolBar toolBar = createModernToolBar();
        root.setTop(toolBar);

        // 创建主要内容区域
        createMainContentArea(root);

        // 创建状态栏
        StatusBar statusBar = createStatusBar();
        root.setBottom(statusBar);

        // 缓存主要组件
        uiComponents.put("root", root);
        uiComponents.put("toolBar", toolBar);
        uiComponents.put("statusBar", statusBar);
    }

    /**
     * 初始化AI助手
     */
    private void initializeAIAssistant() {
        try {
            AIAssistant aiAssistant = springContext.getBean(AIAssistant.class);

            // 初始化AI服务
            red.jiuzhou.theme.AITransformService.initialize(aiAssistant);

            log.info("AI助手初始化成功");

        } catch (Exception e) {
            log.warn("AI助手初始化失败，将使用基础功能: {}", e.getMessage());
            // AI功能失败不影响主要功能
        }
    }

    /**
     * 创建现代化工具栏
     */
    private ToolBar createModernToolBar() {
        ToolBar toolBar = new ToolBar();
        toolBar.getStyleClass().add("modern-toolbar");
        toolBar.setStyle("-fx-padding: 8px 12px;");

        // 主菜单按�?
        MenuButton mainMenu = createMainMenu();

        // 功能按钮�?
        Button[] functionButtons = createFunctionButtons();

        // 视图控制�?
        Node[] viewControls = createViewControls();

        // 窗口控制按钮
        Button[] windowControls = createWindowControls();

        // 添加到工具栏
        toolBar.getItems().add(mainMenu);
        toolBar.getItems().add(new Separator());
        toolBar.getItems().addAll(Arrays.asList(functionButtons));
        toolBar.getItems().add(new Separator());
        toolBar.getItems().addAll(Arrays.asList(viewControls));

        // 添加弹性空�?
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolBar.getItems().add(spacer);

        toolBar.getItems().addAll(Arrays.asList(windowControls));

        return toolBar;
    }

    /**
     * 创建主菜�?
     */
    private MenuButton createMainMenu() {
        MenuButton mainMenu = new MenuButton("文件");
        mainMenu.getStyleClass().add("main-menu-button");

        MenuItem newItem = new MenuItem("新建");
        MenuItem openItem = new MenuItem("打开");
        MenuItem saveItem = new MenuItem("保存");
        SeparatorMenuItem sep1 = new SeparatorMenuItem();
        MenuItem importItem = new MenuItem("导入数据");
        MenuItem exportItem = new MenuItem("导出数据");
        SeparatorMenuItem sep2 = new SeparatorMenuItem();
        MenuItem settingsItem = new MenuItem("设置");
        MenuItem exitItem = new MenuItem("退出");

        mainMenu.getItems().addAll(
            newItem, openItem, saveItem, sep1, importItem, exportItem, sep2, settingsItem, exitItem
        );

        // 设置事件
        exitItem.setOnAction(e -> handleExitAction());

        return mainMenu;
    }

    /**
     * 创建功能按钮
     */
    private Button[] createFunctionButtons() {
        Button enumBtn = createModernButton("枚举查询", "primary");
        Button sqlBtn = createModernButton("SQL转换", "secondary");
        Button relationBtn = createModernButton("字段关联", "info");
        Button directoryBtn = createModernButton("目录管理", "warning");

        // 设置事件
        enumBtn.setOnAction(e -> handleEnumQuery());
        sqlBtn.setOnAction(e -> handleSqlConversion());
        relationBtn.setOnAction(e -> handleFieldAnalysis());
        directoryBtn.setOnAction(e -> handleDirectoryManagement());

        // 设置工具提示
        setTooltip(enumBtn, "查询数据库枚举值");
        setTooltip(sqlBtn, "SQL语句转换工具");
        setTooltip(relationBtn, "分析字段关联关系");
        setTooltip(directoryBtn, "管理项目目录");

        return new Button[]{enumBtn, sqlBtn, relationBtn, directoryBtn};
    }

    /**
     * 创建现代化按�?
     */
    private Button createModernButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("modern-button", styleClass);
        return button;
    }

    /**
     * 设置工具提示
     */
    private void setTooltip(Control control, String tooltip) {
        Tooltip.install(control, new Tooltip(tooltip));
    }

    /**
     * 创建视图控制组件
     */
    private Node[] createViewControls() {
        ToggleButton leftPanelToggle = new ToggleButton("左侧面板");
        leftPanelToggle.setSelected(true);
        leftPanelToggle.setOnAction(e -> toggleLeftPanel());

        ComboBox<String> viewModeCombo = new ComboBox<>();
        viewModeCombo.getItems().addAll("自适应", "紧凑", "宽松", "全屏");
        viewModeCombo.setValue("自适应");
        viewModeCombo.setOnAction(e -> handleViewModeChange(viewModeCombo.getValue()));

        return new Node[]{leftPanelToggle, viewModeCombo};
    }

    /**
     * 创建窗口控制按钮
     */
    private Button[] createWindowControls() {
        Button minimizeBtn = new Button("─");
        Button maximizeBtn = new Button("□");
        Button closeBtn = new Button("✕");

        minimizeBtn.getStyleClass().addAll("window-control", "minimize");
        maximizeBtn.getStyleClass().addAll("window-control", "maximize");
        closeBtn.getStyleClass().addAll("window-control", "close");

        // 设置事件
        minimizeBtn.setOnAction(e -> primaryStage.setIconified(true));
        maximizeBtn.setOnAction(e -> windowStateManager.toggleMaximize());
        closeBtn.setOnAction(e -> handleExitAction());

        return new Button[]{minimizeBtn, maximizeBtn, closeBtn};
    }

    /**
     * 创建主要内容区域
     */
    private void createMainContentArea(BorderPane root) {
        // 获取分割面板
        SplitPane splitPane = layoutManager.getMainSplitPane();

        // 配置左侧面板
        setupLeftPanel();

        // 配置右侧面板
        setupRightPanel();

        root.setCenter(splitPane);
    }

    /**
     * 设置左侧面板
     */
    private void setupLeftPanel() {
        VBox leftPanel = layoutManager.getLeftPanel();

        // 添加快速操作区�?
        HBox quickActions = createQuickActions();
        leftPanel.getChildren().add(quickActions);

        // 添加文件�?
        TreeView<String> fileTree = createFileTree();
        VBox.setVgrow(fileTree, Priority.ALWAYS);
        leftPanel.getChildren().add(fileTree);

        // 缓存组件
        uiComponents.put("leftPanel", leftPanel);
        uiComponents.put("fileTree", fileTree);
        uiComponents.put("quickActions", quickActions);
    }

    /**
     * 创建快速操作区�?
     */
    private HBox createQuickActions() {
        HBox quickActions = new HBox(8);
        quickActions.getStyleClass().add("quick-actions");
        quickActions.setAlignment(Pos.CENTER_LEFT);

        String[] actionNames = {"打开位置", "复制路径", "打开文件", "刷新"};
        for (String name : actionNames) {
            Button btn = createModernButton(name, "small");
            quickActions.getChildren().add(btn);
        }

        return quickActions;
    }

    /**
     * 创建文件�?
     */
    private TreeView<String> createFileTree() {
        TreeView<String> fileTree = new TreeView<>();
        fileTree.getStyleClass().add("modern-tree");
        fileTree.setRoot(new TreeItem<>("根目录"));
        fileTree.setShowRoot(false);

        try {
            // 加载配置
            String leftMenuJson = FileUtil.readUtf8String(
                YamlUtils.getProperty("file.homePath") + File.separator + "leftMenu.json");

            // 构建树形结构
            buildFileTreeFromJson(fileTree.getRoot(), leftMenuJson);

            // 设置单元格工�?
            fileTree.setCellFactory(tv -> new ModernTreeCell());

        } catch (Exception e) {
            log.error("创建文件树失败", e);
            // 创建默认树结�?
            createDefaultFileTree(fileTree);
        }

        return fileTree;
    }

    /**
     * 从JSON构建文件�?
     */
    private void buildFileTreeFromJson(TreeItem<String> rootItem, String jsonContent) {
        try {
            JSONObject root = JSON.parseObject(jsonContent);
            buildTreeItem(rootItem, root, "");
        } catch (Exception e) {
            log.error("解析JSON失败", e);
            throw new RuntimeException("解析菜单配置失败", e);
        }
    }

    /**
     * 递归构建树项
     */
    private void buildTreeItem(TreeItem<String> parentItem, JSONObject node, String basePath) {
        String name = node.getString("name");
        JSONArray children = node.getJSONArray("children");

        String fullPath = basePath.isEmpty() ? name : basePath + File.separator + name;

        TreeItem<String> item = new TreeItem<>(name);
        item.setExpanded(true);

        // 存储元数�?
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fullPath", fullPath);
        metadata.put("node", node);
        TreeItemMetadata.setMetadata(item, metadata);

        // 递归处理子节�?
        if (children != null && !children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                JSONObject childNode = children.getJSONObject(i);
                buildTreeItem(item, childNode, fullPath);
            }
        }

        parentItem.getChildren().add(item);
    }

    /**
     * 创建默认文件�?
     */
    private void createDefaultFileTree(TreeView<String> fileTree) {
        TreeItem<String> root = fileTree.getRoot();

        // 添加默认节点
        TreeItem<String> item1 = new TreeItem<>("示例目录1");
        TreeItem<String> item2 = new TreeItem<>("示例目录2");
        TreeItem<String> item3 = new TreeItem<>("示例文件.xml");

        root.getChildren().addAll(Arrays.asList(item1, item2, item3));
    }

    /**
     * 设置右侧面板
     */
    private void setupRightPanel() {
        VBox rightPanel = layoutManager.getRightPanel();

        // 创建标签�?
        TabPane tabPane = createTabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        rightPanel.getChildren().add(tabPane);

        // 缓存组件
        uiComponents.put("rightPanel", rightPanel);
        uiComponents.put("tabPane", tabPane);
    }

    /**
     * 创建标签�?
     */
    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("modern-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // 添加欢迎标签�?
        Tab welcomeTab = createWelcomeTab();
        welcomeTab.setClosable(false);
        tabPane.getTabs().add(welcomeTab);

        // 监听标签切换
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                currentTabName.set(newTab.getText());
                log.info("切换到标签页: {}", newTab.getText());
            }
        });

        return tabPane;
    }

    /**
     * 创建欢迎标签�?
     */
    private Tab createWelcomeTab() {
        Tab tab = new Tab("欢迎");

        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Label title = new Label("欢迎使用 DB_XML Tool");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label subtitle = new Label("专业的数据库与XML文件转换工具");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");

        content.getChildren().addAll(title, subtitle);

        tab.setContent(content);

        return tab;
    }

    /**
     * 创建状态栏
     */
    private StatusBar createStatusBar() {
        return new StatusBar();
    }

    /**
     * 现代化树单元�?
     */
    private static class ModernTreeCell extends TreeCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item);

                // 根据数据设置图标
                Object userData = TreeItemMetadata.getMetadata(getTreeItem());
                if (userData instanceof Map) {
                    Map<String, Object> metadata = (Map<String, Object>) userData;
                    JSONObject node = (JSONObject) metadata.get("node");

                    if (node != null && node.getJSONArray("children") != null) {
                        setGraphic(createFolderIcon());
                    } else {
                        setGraphic(createFileIcon());
                    }
                } else {
                    setGraphic(createDefaultIcon());
                }
            }
        }

        private Node createFolderIcon() {
            Label icon = new Label("📁");
            icon.setStyle("-fx-font-size: 16px;");
            return icon;
        }

        private Node createFileIcon() {
            Label icon = new Label("📄");
            icon.setStyle("-fx-font-size: 16px;");
            return icon;
        }

        private Node createDefaultIcon() {
            Label icon = new Label("📎");
            icon.setStyle("-fx-font-size: 16px;");
            return icon;
        }
    }

    /**
     * 应用已保存的状�?
     */
    private void applySavedState() {
        try {
            LayoutState state = layoutStateManager.getCurrentState();

            // 应用窗口状�?
            applyWindowState(state.getWindowState());

            // 应用UI状�?
            applyUIState(state.getUiState());

            log.info("已保存的状态应用完成");

        } catch (Exception e) {
            log.error("应用保存状态失败", e);
            // 使用默认状态
            applyDefaultState();
        }
    }

    /**
     * 应用窗口状�?
     */
    private void applyWindowState(LayoutState.WindowState windowState) {
        if (windowState != null && primaryStage != null) {
            if (windowState.getX() >= 0 && windowState.getY() >= 0) {
                primaryStage.setX(windowState.getX());
                primaryStage.setY(windowState.getY());
            }

            if (windowState.getWidth() > 0 && windowState.getHeight() > 0) {
                primaryStage.setWidth(windowState.getWidth());
                primaryStage.setHeight(windowState.getHeight());
            }

            primaryStage.setMaximized(windowState.isMaximized());
        }
    }

    /**
     * 应用UI状�?
     */
    private void applyUIState(LayoutState.UIState uiState) {
        if (uiState != null) {
            applyTheme(uiState.getTheme());
            applyFontSize(uiState.getFontSize());
        }
    }

    /**
     * 应用默认状�?
     */
    private void applyDefaultState() {
        primaryStage.setWidth(1400);
        primaryStage.setHeight(800);
        primaryStage.setMaximized(false);
        applyTheme("light");
        applyFontSize(14);
    }

    /**
     * 应用主题
     */
    private void applyTheme(String theme) {
        Scene scene = primaryStage.getScene();
        if (scene != null) {
            // 清除现有样式
            scene.getStylesheets().clear();

            // 添加基础样式
            scene.getStylesheets().add("/modern-theme.css");

            // 添加增强主题
            scene.getStylesheets().add("/enhanced-responsive-theme.css");

            // 应用主题变体
            if ("dark".equals(theme)) {
                scene.getStylesheets().add("/themes/dark-theme.css");
            }
        }
    }

    /**
     * 应用字体大小
     */
    private void applyFontSize(double fontSize) {
        Scene scene = primaryStage.getScene();
        if (scene != null) {
            String style = String.format("-fx-font-size: %.1fpx;", fontSize);
            scene.getRoot().setStyle(style);
        }
    }

    /**
     * 显示界面
     */
    private void showInterface() {
        Scene scene = new Scene(
            layoutManager.getRootContainer(),
            1400,
            800
        );

        // 应用样式
        scene.getStylesheets().addAll(
            "/modern-theme.css",
            "/enhanced-responsive-theme.css"
        );

        primaryStage.setScene(scene);
        primaryStage.setTitle("DB_XML Tool Robust Version");
        primaryStage.show();

        // 触发响应式布局更新
        Platform.runLater(() -> {
            layoutManager.updateResponsiveLayout();
        });
    }

    /**
     * 处理启动错误
     */
    private void handleStartupError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("启动错误");
        alert.setHeaderText("应用启动失败");
        alert.setContentText("错误信息: " + e.getMessage());
        alert.showAndWait();

        // 尝试以安全模式启�?
        Platform.runLater(this::startInSafeMode);
    }

    /**
     * 安全模式启动
     */
    private void startInSafeMode() {
        try {
            log.info("以安全模式启�?..");

            // 创建最小界�?
            VBox root = new VBox();
            root.setPadding(new Insets(20));
            root.setAlignment(Pos.CENTER);

            Label errorLabel = new Label("启动失败，已进入安全模式");
            errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");

            Button retryButton = new Button("重试");
            retryButton.setOnAction(e -> {
                // 清理并重�?
                try {
                    stop();
                    start(new Stage());
                } catch (Exception ex) {
                    log.error("重启失败", ex);
                }
            });

            root.getChildren().addAll(errorLabel, retryButton);

            Scene scene = new Scene(root, 400, 200);
            primaryStage.setScene(scene);
            primaryStage.setTitle("安全模式");
            primaryStage.show();

        } catch (Exception e) {
            log.error("安全模式启动失败", e);
        }
    }

    /**
     * 处理退出操�?
     */
    private void handleExitAction() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("退出确认");
        alert.setHeaderText("确定要退出应用程序吗？");
        alert.setContentText("所有未保存的数据将丢失！");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            cleanupResources();
            Platform.exit();
        }
    }

    /**
     * 清理资源
     */
    private void cleanupResources() {
        log.info("正在清理资源...");

        try {
            // 保存当前状�?
            saveWindowStateAsync();

            // 清理组件缓存
            uiComponents.clear();
            componentStates.clear();

            // 等待保存完成
            Thread.sleep(1000);

        } catch (Exception e) {
            log.error("清理资源失败", e);
        }
    }

    // 事件处理方法

    /**
     * 切换左侧面板
     */
    private void toggleLeftPanel() {
        layoutManager.toggleLeftPanel();
    }

    /**
     * 处理视图模式变化
     */
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

    /**
     * 处理枚举查询
     */
    private void handleEnumQuery() {
        try {
            new EnumQuery().showQueryWindow(primaryStage);
        } catch (Exception e) {
            errorHandler.handleException(e, "打开枚举查询", "handleEnumQuery");
        }
    }

    /**
     * 处理SQL转换
     */
    private void handleSqlConversion() {
        try {
            showNotification("SQL转换器功能正在开发中", "INFO");
        } catch (Exception e) {
            errorHandler.handleException(e, "打开SQL转换器", "handleSqlConversion");
        }
    }

    /**
     * 处理字段分析
     */
    private void handleFieldAnalysis() {
        try {
            showNotification("字段关联分析功能正在开发中", "INFO");
        } catch (Exception e) {
            errorHandler.handleException(e, "字段关联分析", "handleFieldAnalysis");
        }
    }

    /**
     * 处理目录管理
     */
    private void handleDirectoryManagement() {
        try {
            DirectoryManagerDialog dialog = new DirectoryManagerDialog(this::refreshDirectories);
            dialog.show(primaryStage);
        } catch (Exception e) {
            errorHandler.handleException(e, "打开目录管理", "handleDirectoryManagement");
        }
    }

    /**
     * 刷新目录
     */
    private void refreshDirectories() {
        try {
            IncrementalMenuJsonGenerator.createJsonIncrementally();

            // 重新构建文件�?
            TreeView<String> fileTree = (TreeView<String>) uiComponents.get("fileTree");
            if (fileTree != null) {
                fileTree.getRoot().getChildren().clear();

                String leftMenuJson = FileUtil.readUtf8String(
                    YamlUtils.getProperty("file.homePath") + File.separator + "leftMenu.json");
                buildFileTreeFromJson(fileTree.getRoot(), leftMenuJson);
            }

            showNotification("目录已刷新", "SUCCESS");

        } catch (Exception e) {
            log.error("刷新目录失败", e);
        }
    }

    /**
     * 显示通知
     */
    private void showNotification(String message, String type) {
        log.info("通知 [{}]: {}", type, message);
        // TODO: 实现通知显示
    }

    /**
     * 状态栏组件
     */
    private static class StatusBar extends HBox {
        private final Label statusLabel;
        private final ProgressBar progressBar;
        private final Label memoryLabel;

        public StatusBar() {
            setStyle("-fx-background-color: #f8f9fa; -fx-padding: 4px 8px;");
            setSpacing(10);
            setAlignment(Pos.CENTER_LEFT);

            statusLabel = new Label("就绪");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");

            progressBar = new ProgressBar();
            progressBar.setProgress(0);
            progressBar.setPrefWidth(200);
            progressBar.setVisible(false);

            memoryLabel = new Label();
            memoryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            getChildren().addAll(statusLabel, progressBar, spacer, memoryLabel);

            // 定期更新内存使用情况
            updateMemoryUsage();
        }

        private void updateMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            String memoryText = String.format(
                "内存: %.1fMB / %.1fMB",
                usedMemory / (1024.0 * 1024.0),
                totalMemory / (1024.0 * 1024.0)
            );

            memoryLabel.setText(memoryText);

            // 定期更新
            javafx.animation.Timeline timer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5),
                e -> updateMemoryUsage()
            )
            );
            timer.setCycleCount(javafx.animation.Animation.INDEFINITE);
            timer.play();
        }

        public void setStatus(String status) {
            statusLabel.setText(status);
        }

        public void setProgress(double progress) {
            if (progress > 0) {
                progressBar.setVisible(true);
                progressBar.setProgress(progress);
                statusLabel.setText(String.format("处理中.. %.0f%%", progress * 100));
            } else {
                progressBar.setVisible(false);
            }
        }
    }
}
