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
 * Java 8 兼容的健壮版主程�?
 * 采用最佳实践设计，确保 Java 8 完全兼容性和可维护�?
 *
 * @author Claude Code Robust
 * @version 3.1 - Java 8 Compatible Edition
 */
@SpringBootApplication(scanBasePackages = {"red.jiuzhou.api", "red.jiuzhou.util"})
@SuppressWarnings("unchecked")
public class CompatibleDbxmltool extends Application {

    private static final Logger log = LoggerFactory.getLogger(CompatibleDbxmltool.class);

    // 核心组件
    private ConfigurableApplicationContext springContext;
    private final FeatureRegistry featureRegistry = FeatureRegistry.defaultRegistry();

    // UI 管理�?
    private ResponsiveLayoutManager layoutManager;
    private WindowStateManager windowStateManager;
    private LayoutStateManager layoutStateManager;
    private EnhancedErrorHandler errorHandler;

    // 应用状�?
    private Stage primaryStage;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<String> currentTabName = new AtomicReference<>("");

    // UI 组件映射
    private final Map<String, Node> uiComponents = new ConcurrentHashMap<>();
    private final Map<String, Object> componentStates = new ConcurrentHashMap<>();

    // 菜单和路径管�?
    private Map<String, Map<String, String>> leftMenuMap = new HashMap<>();
    private Stack<String> locationStack = new Stack<>();
    private String currentLocation = "";

    @Override
    public void init() {
        try {
            log.info("初始化Java 8兼容版DB_XML Tool...");

            // 初始�?Spring 上下�?
            springContext = new SpringApplicationBuilder(CompatibleDbxmltool.class).run();

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

            // 保存状�?
            if (layoutStateManager != null) {
                layoutStateManager.close();
            }

            // 关闭 Spring 上下�?
            if (springContext != null) {
                springContext.close();
            }

            // 关闭功能执行�?
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

            log.info("Java 8兼容版DB_XML Tool 启动成功");

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

        // 初始化布局管理器（使用 Java 8 兼容版本�?
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
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("未捕获的异常", throwable);
            if (errorHandler != null) {
                Platform.runLater(() ->
                    errorHandler.handleError(throwable, "未捕获的异常"));
            }
        });
    }

    /**
     * 设置状态监听器
     */
    private void setupStateListeners() {
        // 监听窗口大小变化
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (layoutManager != null) {
                layoutManager.handleWindowResize(newVal.doubleValue(), primaryStage.getHeight());
            }
        });

        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (layoutManager != null) {
                layoutManager.handleWindowResize(primaryStage.getWidth(), newVal.doubleValue());
            }
        });
    }

    /**
     * 创建主界�?
     */
    private void createMainInterface() {
        // 设置窗口标题
        primaryStage.setTitle("DB_XML Tool - Java 8 兼容�?v3.1");

        // 创建主容�?
        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-container");

        // 创建顶部工具�?
        ToolBar toolBar = createToolBar();
        root.setTop(toolBar);

        // 创建主要内容区域
        SplitPane mainContent = createMainContent();
        root.setCenter(mainContent);

        // 创建底部状态栏
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        // 创建场景
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/enhanced-responsive-theme.css").toExternalForm());

        // 设置场景
        primaryStage.setScene(scene);

        // 注册UI组件
        registerUIComponents(root, toolBar, mainContent, statusBar);
    }

    /**
     * 创建工具�?
     */
    private ToolBar createToolBar() {
        ToolBar toolBar = new ToolBar();
        toolBar.getStyleClass().add("main-toolbar");

        // 文件操作按钮
        Button openBtn = new Button("打开");
        openBtn.setOnAction(e -> handleOpenAction());

        Button saveBtn = new Button("保存");
        saveBtn.setOnAction(e -> handleSaveAction());

        // 编辑操作按钮
        Button convertBtn = new Button("转换");
        convertBtn.setOnAction(e -> handleConvertAction());

        // AI助手按钮
        Button aiBtn = new Button("AI 助手");
        aiBtn.setOnAction(e -> handleAIAssistantAction());

        // 添加按钮到工具栏
        toolBar.getItems().addAll(
            createSeparator(),
            openBtn,
            saveBtn,
            createSeparator(),
            convertBtn,
            createSeparator(),
            aiBtn
        );

        return toolBar;
    }

    /**
     * 创建主要内容区域
     */
    private SplitPane createMainContent() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.3);

        // 左侧面板
        VBox leftPanel = createLeftPanel();
        splitPane.getItems().add(leftPanel);

        // 右侧标签�?
        TabPane rightPanel = createTabPane();
        splitPane.getItems().add(rightPanel);

        return splitPane;
    }

    /**
     * 创建左侧面板
     */
    private VBox createLeftPanel() {
        VBox leftPanel = new VBox();
        leftPanel.getStyleClass().add("left-panel");
        leftPanel.setPrefWidth(300);
        leftPanel.setSpacing(10);
        leftPanel.setPadding(new Insets(10));

        // 位置导航
        HBox navBox = new HBox(10);
        navBox.setAlignment(Pos.CENTER_LEFT);

        Button backBtn = new Button("返回");
        backBtn.setOnAction(e -> handleNavigateBack());

        Label locationLabel = new Label("当前位置: ");
        Label locationValue = new Label("根目录");
        navBox.getChildren().addAll(backBtn, locationLabel, locationValue);

        // 目录�?
        TreeView<String> dirTree = new TreeView<>();
        setupDirectoryTree(dirTree);

        // 添加到面�?
        leftPanel.getChildren().addAll(navBox, dirTree);

        VBox.setVgrow(dirTree, Priority.ALWAYS);

        return leftPanel;
    }

    /**
     * 创建标签�?
     */
    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);

        // 数据库配置标签页
        Tab dbTab = new Tab("数据库配置");
        dbTab.setContent(createDatabaseConfigTab());
        tabPane.getTabs().add(dbTab);

        // XML配置标签�?
        Tab xmlTab = new Tab("XML配置");
        xmlTab.setContent(createXmlConfigTab());
        tabPane.getTabs().add(xmlTab);

        // 日志标签�?
        Tab logTab = new Tab("日志");
        logTab.setContent(createLogTab());
        tabPane.getTabs().add(logTab);

        // 监听标签切换
        tabPane.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldTab, newTab) -> {
                if (newTab != null) {
                    currentTabName.set(newTab.getText());
                    log.debug("切换到标签页: {}", newTab.getText());
                }
            }
        );

        return tabPane;
    }

    /**
     * 设置目录�?
     */
    private void setupDirectoryTree(TreeView<String> treeView) {
        TreeItem<String> root = new TreeItem<>("根目录");
        root.setExpanded(true);

        // 加载菜单配置
        loadMenuConfiguration();

        // 构建树结�?
        buildTreeFromMenu(root, leftMenuMap);

        treeView.setRoot(root);

        // 设置单元格工�?
        treeView.setCellFactory(tv -> new TreeCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    // 使用简单的样式，避免复杂的 UserData 操作
                    getStyleClass().add("tree-cell-item");
                }
            }
        });

        // 处理选择事件
        treeView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldItem, newItem) -> {
                if (newItem != null) {
                    handleTreeSelection(newItem);
                }
            }
        );
    }

    /**
     * 加载菜单配置
     */
    private void loadMenuConfiguration() {
        try {
            String menuPath = System.getProperty("user.dir") + File.separator + "leftMenu.json";
            if (FileUtil.exist(menuPath)) {
                String menuContent = FileUtil.readUtf8String(menuPath);
                JSONObject menuJson = JSON.parseObject(menuContent);
                leftMenuMap = parseMenuStructure(menuJson);
            }
        } catch (Exception e) {
            log.error("加载菜单配置失败", e);
            leftMenuMap = new HashMap<>();
        }
    }

    /**
     * 解析菜单结构
     */
    private Map<String, Map<String, String>> parseMenuStructure(JSONObject menuJson) {
        Map<String, Map<String, String>> menuMap = new HashMap<>();

        for (String key : menuJson.keySet()) {
            JSONObject section = menuJson.getJSONObject(key);
            Map<String, String> sectionMap = new HashMap<>();

            for (String itemKey : section.keySet()) {
                sectionMap.put(itemKey, section.getString(itemKey));
            }

            menuMap.put(key, sectionMap);
        }

        return menuMap;
    }

    /**
     * 构建树形菜单
     */
    private void buildTreeFromMenu(TreeItem<String> root, Map<String, Map<String, String>> menuMap) {
        for (Map.Entry<String, Map<String, String>> entry : menuMap.entrySet()) {
            TreeItem<String> categoryItem = new TreeItem<>(entry.getKey());
            categoryItem.setExpanded(false);

            for (Map.Entry<String, String> item : entry.getValue().entrySet()) {
                TreeItem<String> leafItem = new TreeItem<>(item.getKey());
                categoryItem.getChildren().add(leafItem);
            }

            root.getChildren().add(categoryItem);
        }
    }

    /**
     * 处理树选择
     */
    private void handleTreeSelection(TreeItem<String> selectedItem) {
        String path = getTreeItemPath(selectedItem);
        currentLocation = path;

        // 更新位置�?
        if (!locationStack.isEmpty() && !locationStack.peek().equals(path)) {
            locationStack.push(path);
        }

        log.debug("选择�? {}", path);
    }

    /**
     * 获取树项路径
     */
    private String getTreeItemPath(TreeItem<String> item) {
        StringBuilder path = new StringBuilder();
        TreeItem<String> current = item;

        while (current != null) {
            if (path.length() > 0) {
                path.insert(0, " > ");
            }
            path.insert(0, current.getValue());
            current = current.getParent();
        }

        return path.toString();
    }

    /**
     * 创建状态栏
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));

        Label statusLabel = new Label("就绪");
        Label locationLabel = new Label("位置: " + currentLocation);

        // 添加弹性空�?
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLabel, spacer, locationLabel);

        return statusBar;
    }

    /**
     * 注册UI组件
     */
    private void registerUIComponents(BorderPane root, ToolBar toolBar, SplitPane mainContent, HBox statusBar) {
        uiComponents.put("root", root);
        uiComponents.put("toolbar", toolBar);
        uiComponents.put("mainContent", mainContent);
        uiComponents.put("statusBar", statusBar);
    }

    /**
     * 应用已保存的状�?
     */
    private void applySavedState() {
        try {
            if (windowStateManager != null) {
                windowStateManager.restoreState();
            }

            if (layoutStateManager != null) {
                LayoutState savedState = layoutStateManager.loadState();
                if (savedState != null) {
                    applyLayoutState(savedState);
                }
            }
        } catch (Exception e) {
            log.warn("应用保存的状态失败", e);
        }
    }

    /**
     * 应用布局状�?
     */
    private void applyLayoutState(LayoutState state) {
        // 应用窗口状�?
        if (state.getWindowState() != null) {
            LayoutState.WindowState ws = state.getWindowState();
            primaryStage.setX(ws.getX());
            primaryStage.setY(ws.getY());
            primaryStage.setWidth(ws.getWidth());
            primaryStage.setHeight(ws.getHeight());

            if (ws.isMaximized()) {
                primaryStage.setMaximized(true);
            }
        }

        // 应用UI状�?
        if (state.getUiState() != null) {
            LayoutState.UIState uiState = state.getUiState();
            // 应用主题、字体等UI状�?
            applyUIState(uiState);
        }
    }

    /**
     * 应用UI状�?
     */
    private void applyUIState(LayoutState.UIState uiState) {
        // 应用主题
        String theme = uiState.getTheme();
        if (theme != null && !theme.isEmpty()) {
            applyTheme(theme);
        }

        // 应用字体大小
        double fontSize = uiState.getFontSize();
        if (fontSize > 0) {
            applyFontSize(fontSize);
        }
    }

    /**
     * 应用主题
     */
    private void applyTheme(String theme) {
        try {
            Scene scene = primaryStage.getScene();
            String themeFile = "/themes/" + theme + "-theme.css";

            // 移除现有主题
            scene.getStylesheets().removeIf(s -> s.contains("-theme.css"));

            // 添加新主�?
            String themeUrl = getClass().getResource(themeFile).toExternalForm();
            if (themeUrl != null) {
                scene.getStylesheets().add(themeUrl);
                log.info("已应用主题: {}", theme);
            }
        } catch (Exception e) {
            log.warn("应用主题失败: {}", theme, e);
        }
    }

    /**
     * 应用字体大小
     */
    private void applyFontSize(double fontSize) {
        String style = String.format("-fx-font-size: %.1fpt;", fontSize);
        primaryStage.getScene().getRoot().setStyle(style);
    }

    /**
     * 显示界面
     */
    private void showInterface() {
        primaryStage.show();

        // 触发响应式布局
        if (layoutManager != null) {
            layoutManager.handleWindowResize(primaryStage.getWidth(), primaryStage.getHeight());
        }
    }

    /**
     * 处理启动错误
     */
    private void handleStartupError(Exception e) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("启动错误");
        alert.setHeaderText("应用启动失败");
        alert.setContentText("错误信息: " + e.getMessage());
        alert.showAndWait();

        Platform.exit();
    }

    // 事件处理方法

    private void handleOpenAction() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择目录");
        File selectedDir = chooser.showDialog(primaryStage);

        if (selectedDir != null) {
            log.info("选择的目录: {}", selectedDir.getAbsolutePath());
        }
    }

    private void handleSaveAction() {
        log.info("保存操作");
        // 实现保存逻辑
    }

    private void handleConvertAction() {
        log.info("转换操作");
        // 实现转换逻辑
    }

    private void handleAIAssistantAction() {
        log.info("打开AI助手");
        // 实现AI助手逻辑
    }

    private void handleNavigateBack() {
        if (!locationStack.isEmpty()) {
            String previousLocation = locationStack.pop();
            log.info("返回到: {}", previousLocation);
        }
    }

    // 辅助方法

    private Node createSeparator() {
        return new Separator();
    }

    private Node createDatabaseConfigTab() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getChildren().add(new Label("数据库配置"));
        return content;
    }

    private Node createXmlConfigTab() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getChildren().add(new Label("XML配置"));
        return content;
    }

    private Node createLogTab() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(20);

        content.getChildren().addAll(new Label("应用日志"), logArea);
        return content;
    }
}
