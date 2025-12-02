package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.analysis.aion.*;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Aion机制浏览器 - 增强版
 *
 * <p>专为Aion游戏设计的三层级可视化工具：
 * <ul>
 *   <li>第一层：27个游戏机制分类卡片</li>
 *   <li>第二层：机制下的XML文件列表</li>
 *   <li>第三层：文件的字段结构和引用关系</li>
 * </ul>
 *
 * <p>支持层级间的关联跳转，让设计师能快速追踪数据关系。
 *
 * @author Claude
 * @version 2.0
 */
public class AionMechanismExplorerStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(AionMechanismExplorerStage.class);

    // 配置
    private String aionXmlPath;
    private String localizedPath;

    // 导航状态
    private final Stack<NavigationState> navigationHistory = new Stack<>();
    private NavigationState currentState;

    // UI组件
    private HBox breadcrumbBox;
    private FlowPane mechanismCardsPane;
    private VBox fileListBox;
    private ListView<AionMechanismView.FileEntry> fileListView;
    private VBox fieldListBox;
    private TableView<XmlFieldParser.FieldInfo> fieldTable;
    private TextArea detailArea;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private VBox referenceBox;
    private FlowPane referenceTagsPane;

    // 数据
    private AionMechanismView mechanismView;
    private AionMechanismCategory selectedCategory;
    private AionMechanismView.FileEntry selectedFile;
    private XmlFieldParser.ParseResult currentParseResult;

    // 机制名称到分类的映射
    private final Map<String, AionMechanismCategory> mechanismNameMap = new HashMap<>();

    public AionMechanismExplorerStage() {
        setTitle("Aion 机制浏览器 - 三层级可视化导航");
        setWidth(1500);
        setHeight(950);

        loadConfig();
        initMechanismNameMap();
        initUI();
        loadData();
    }

    /**
     * 初始化机制名称映射
     */
    private void initMechanismNameMap() {
        for (AionMechanismCategory cat : AionMechanismCategory.values()) {
            mechanismNameMap.put(cat.getDisplayName(), cat);
        }
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        try {
            aionXmlPath = YamlUtils.getPropertyOrDefault("aion.xmlPath", "D:\\AionReal58\\AionMap\\XML");
            localizedPath = YamlUtils.getPropertyOrDefault("aion.localizedPath", aionXmlPath + "\\China");
            log.info("Aion XML路径: {}", aionXmlPath);
            log.info("本地化路径: {}", localizedPath);
        } catch (Exception e) {
            log.warn("加载配置失败，使用默认值: {}", e.getMessage());
            aionXmlPath = "D:\\AionReal58\\AionMap\\XML";
            localizedPath = aionXmlPath + "\\China";
        }
    }

    /**
     * 初始化UI
     */
    private void initUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f8f9fa;");

        // 顶部：标题和面包屑导航
        VBox topBox = createTopBox();
        root.setTop(topBox);

        // 主体：三栏布局
        HBox mainContent = createMainContent();
        root.setCenter(mainContent);

        // 底部：状态栏
        HBox bottomBar = createBottomBar();
        root.setBottom(bottomBar);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 创建顶部区域
     */
    private VBox createTopBox() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(0, 0, 10, 0));

        // 标题行
        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Aion 机制浏览器");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);

        Button refreshBtn = new Button("刷新");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> loadData());

        Button backBtn = new Button("← 返回");
        backBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-cursor: hand;");
        backBtn.setOnAction(e -> navigateBack());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleBox.getChildren().addAll(titleLabel, progressIndicator, backBtn, refreshBtn, spacer);

        // 面包屑导航
        breadcrumbBox = new HBox(5);
        breadcrumbBox.setAlignment(Pos.CENTER_LEFT);
        breadcrumbBox.setPadding(new Insets(8, 12, 8, 12));
        breadcrumbBox.setStyle("-fx-background-color: white; -fx-background-radius: 5; " +
                "-fx-border-color: #e0e0e0; -fx-border-radius: 5;");
        updateBreadcrumb();

        box.getChildren().addAll(titleBox, breadcrumbBox);
        return box;
    }

    /**
     * 创建主体三栏布局
     */
    private HBox createMainContent() {
        HBox content = new HBox(10);
        content.setPadding(new Insets(5));

        // 第一栏：机制列表
        VBox mechanismColumn = createMechanismColumn();
        mechanismColumn.setPrefWidth(280);
        mechanismColumn.setMinWidth(250);

        // 第二栏：文件列表
        fileListBox = createFileColumn();
        fileListBox.setPrefWidth(320);
        fileListBox.setMinWidth(280);

        // 第三栏：字段详情
        VBox fieldColumn = createFieldColumn();
        HBox.setHgrow(fieldColumn, Priority.ALWAYS);

        content.getChildren().addAll(mechanismColumn, fileListBox, fieldColumn);
        return content;
    }

    /**
     * 创建机制列表栏
     */
    private VBox createMechanismColumn() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        Label header = new Label("游戏机制分类");
        header.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        header.setStyle("-fx-text-fill: #2c3e50;");

        Label hint = new Label("点击选择机制，查看相关文件");
        hint.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11px;");

        mechanismCardsPane = new FlowPane();
        mechanismCardsPane.setHgap(8);
        mechanismCardsPane.setVgap(8);
        mechanismCardsPane.setPadding(new Insets(5));

        ScrollPane scrollPane = new ScrollPane(mechanismCardsPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        box.getChildren().addAll(header, hint, scrollPane);
        return box;
    }

    /**
     * 创建文件列表栏
     */
    private VBox createFileColumn() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        Label header = new Label("配置文件");
        header.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        header.setStyle("-fx-text-fill: #2c3e50;");

        TextField searchField = new TextField();
        searchField.setPromptText("搜索文件名...");
        searchField.setStyle("-fx-background-radius: 5;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterFileList(newVal));

        fileListView = new ListView<>();
        fileListView.setCellFactory(lv -> new FileEntryCell());
        fileListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        selectFile(newVal);
                    }
                });
        VBox.setVgrow(fileListView, Priority.ALWAYS);

        box.getChildren().addAll(header, searchField, fileListView);
        return box;
    }

    /**
     * 创建字段详情栏
     */
    private VBox createFieldColumn() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        // 字段表格区
        fieldListBox = new VBox(8);

        Label fieldHeader = new Label("字段结构");
        fieldHeader.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        fieldHeader.setStyle("-fx-text-fill: #2c3e50;");

        fieldTable = createFieldTable();
        VBox.setVgrow(fieldTable, Priority.ALWAYS);

        fieldListBox.getChildren().addAll(fieldHeader, fieldTable);

        // 引用关系区
        referenceBox = new VBox(8);
        referenceBox.setPadding(new Insets(10, 0, 0, 0));

        Label refHeader = new Label("关联系统 (点击跳转)");
        refHeader.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        refHeader.setStyle("-fx-text-fill: #e74c3c;");

        referenceTagsPane = new FlowPane();
        referenceTagsPane.setHgap(8);
        referenceTagsPane.setVgap(8);

        referenceBox.getChildren().addAll(refHeader, referenceTagsPane);
        referenceBox.setVisible(false);

        // 详情区
        detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setFont(Font.font("Consolas", 12));
        detailArea.setWrapText(true);
        detailArea.setPrefHeight(150);
        detailArea.setStyle("-fx-background-color: #f8f9fa;");

        VBox.setVgrow(fieldListBox, Priority.ALWAYS);
        box.getChildren().addAll(fieldListBox, referenceBox, detailArea);
        return box;
    }

    /**
     * 创建字段表格
     */
    @SuppressWarnings("unchecked")
    private TableView<XmlFieldParser.FieldInfo> createFieldTable() {
        TableView<XmlFieldParser.FieldInfo> table = new TableView<>();

        TableColumn<XmlFieldParser.FieldInfo, String> nameCol = new TableColumn<>("字段名");
        nameCol.setCellValueFactory(data -> {
            XmlFieldParser.FieldInfo field = data.getValue();
            String prefix = field.isAttribute() ? "@" : "";
            return new javafx.beans.property.SimpleStringProperty(prefix + field.getName());
        });
        nameCol.setPrefWidth(150);
        nameCol.setCellFactory(col -> new TableCell<XmlFieldParser.FieldInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    XmlFieldParser.FieldInfo field = getTableView().getItems().get(getIndex());
                    if (field.hasReference()) {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-cursor: hand;");
                    } else {
                        setStyle("-fx-text-fill: #2c3e50;");
                    }
                }
            }
        });

        TableColumn<XmlFieldParser.FieldInfo, String> valueCol = new TableColumn<>("示例值");
        valueCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getSampleValue()));
        valueCol.setPrefWidth(200);

        TableColumn<XmlFieldParser.FieldInfo, String> pathCol = new TableColumn<>("路径");
        pathCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getPath()));
        pathCol.setPrefWidth(200);

        TableColumn<XmlFieldParser.FieldInfo, String> refCol = new TableColumn<>("引用");
        refCol.setCellValueFactory(data -> {
            String ref = data.getValue().getReferenceTarget();
            return new javafx.beans.property.SimpleStringProperty(ref != null ? "→ " + ref : "");
        });
        refCol.setPrefWidth(120);
        refCol.setCellFactory(col -> new TableCell<XmlFieldParser.FieldInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setStyle("");
                    setOnMouseClicked(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold; -fx-cursor: hand;");
                    setOnMouseClicked(e -> {
                        if (e.getButton() == MouseButton.PRIMARY) {
                            XmlFieldParser.FieldInfo field = getTableView().getItems().get(getIndex());
                            if (field.hasReference()) {
                                jumpToMechanism(field.getReferenceTarget());
                            }
                        }
                    });
                }
            }
        });

        table.getColumns().addAll(nameCol, valueCol, pathCol, refCol);

        // 双击跳转
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                XmlFieldParser.FieldInfo selected = table.getSelectionModel().getSelectedItem();
                if (selected != null && selected.hasReference()) {
                    jumpToMechanism(selected.getReferenceTarget());
                }
            }
        });

        return table;
    }

    /**
     * 创建底部状态栏
     */
    private HBox createBottomBar() {
        HBox bar = new HBox(15);
        bar.setPadding(new Insets(10, 5, 5, 5));
        bar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d;");

        Label pathLabel = new Label("路径: " + aionXmlPath);
        pathLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(statusLabel, spacer, pathLabel);
        return bar;
    }

    /**
     * 更新面包屑导航
     */
    private void updateBreadcrumb() {
        breadcrumbBox.getChildren().clear();

        // 首页
        Hyperlink homeLink = new Hyperlink("首页");
        homeLink.setStyle("-fx-text-fill: #3498db; -fx-font-size: 13px;");
        homeLink.setOnAction(e -> navigateToHome());
        breadcrumbBox.getChildren().add(homeLink);

        // 机制层
        if (selectedCategory != null) {
            Label sep1 = new Label(" > ");
            sep1.setStyle("-fx-text-fill: #bdc3c7;");

            Hyperlink mechLink = new Hyperlink(selectedCategory.getDisplayName());
            mechLink.setStyle("-fx-text-fill: #3498db; -fx-font-size: 13px;");
            mechLink.setOnAction(e -> navigateToMechanism(selectedCategory));

            breadcrumbBox.getChildren().addAll(sep1, mechLink);
        }

        // 文件层
        if (selectedFile != null) {
            Label sep2 = new Label(" > ");
            sep2.setStyle("-fx-text-fill: #bdc3c7;");

            Label fileLabel = new Label(selectedFile.getFileName());
            fileLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px; -fx-font-weight: bold;");

            breadcrumbBox.getChildren().addAll(sep2, fileLabel);
        }
    }

    /**
     * 导航到首页
     */
    private void navigateToHome() {
        saveCurrentState();
        selectedCategory = null;
        selectedFile = null;
        currentParseResult = null;

        fileListView.getItems().clear();
        fieldTable.getItems().clear();
        referenceBox.setVisible(false);
        detailArea.clear();

        updateBreadcrumb();
        highlightSelectedMechanism(null);
    }

    /**
     * 导航到指定机制
     */
    private void navigateToMechanism(AionMechanismCategory category) {
        if (category == null) return;

        saveCurrentState();
        selectedFile = null;
        currentParseResult = null;

        selectMechanism(mechanismView.getGroup(category));
    }

    /**
     * 跳转到指定机制（通过名称）
     */
    private void jumpToMechanism(String mechanismName) {
        AionMechanismCategory category = mechanismNameMap.get(mechanismName);
        if (category != null) {
            navigateToMechanism(category);
            statusLabel.setText("已跳转到: " + mechanismName);
        } else {
            statusLabel.setText("未找到机制: " + mechanismName);
        }
    }

    /**
     * 导航返回
     */
    private void navigateBack() {
        if (!navigationHistory.isEmpty()) {
            NavigationState state = navigationHistory.pop();
            restoreState(state);
        }
    }

    /**
     * 保存当前状态
     */
    private void saveCurrentState() {
        if (selectedCategory != null || selectedFile != null) {
            navigationHistory.push(new NavigationState(selectedCategory, selectedFile));
        }
    }

    /**
     * 恢复状态
     */
    private void restoreState(NavigationState state) {
        if (state.category != null) {
            selectMechanism(mechanismView.getGroup(state.category));
            if (state.file != null) {
                fileListView.getSelectionModel().select(state.file);
            }
        } else {
            navigateToHome();
        }
    }

    /**
     * 加载数据
     */
    private void loadData() {
        progressIndicator.setVisible(true);
        statusLabel.setText("正在扫描...");

        CompletableFuture.runAsync(() -> {
            try {
                File publicRoot = new File(aionXmlPath);
                File localizedRoot = new File(localizedPath);

                AionMechanismDetector detector = new AionMechanismDetector(publicRoot, localizedRoot);
                mechanismView = detector.scan();

                Platform.runLater(() -> {
                    updateMechanismCards();
                    progressIndicator.setVisible(false);
                    statusLabel.setText(mechanismView.getStatistics().getSummary());
                });
            } catch (Exception e) {
                log.error("加载数据失败", e);
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    statusLabel.setText("加载失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 更新机制卡片
     */
    private void updateMechanismCards() {
        mechanismCardsPane.getChildren().clear();

        if (mechanismView == null) return;

        List<AionMechanismView.MechanismGroup> groups = mechanismView.getNonEmptyGroups();

        // 按文件数量排序
        Collections.sort(groups, new Comparator<AionMechanismView.MechanismGroup>() {
            @Override
            public int compare(AionMechanismView.MechanismGroup a, AionMechanismView.MechanismGroup b) {
                return Integer.compare(b.getFileCount(), a.getFileCount());
            }
        });

        for (AionMechanismView.MechanismGroup group : groups) {
            VBox card = createMechanismCard(group);
            mechanismCardsPane.getChildren().add(card);
        }
    }

    /**
     * 创建机制卡片
     */
    private VBox createMechanismCard(AionMechanismView.MechanismGroup group) {
        AionMechanismCategory category = group.getCategory();

        VBox card = new VBox(3);
        card.setPadding(new Insets(8));
        card.setMinWidth(110);
        card.setMaxWidth(130);
        card.setAlignment(Pos.CENTER);
        card.setUserData(category);

        String normalStyle = String.format(
                "-fx-background-color: white; " +
                "-fx-border-color: %s; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;",
                category.getColor());
        card.setStyle(normalStyle);

        Label iconLabel = new Label(category.getIcon());
        iconLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        iconLabel.setStyle("-fx-text-fill: " + category.getColor() + ";");

        Label nameLabel = new Label(category.getDisplayName());
        nameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 11));
        nameLabel.setWrapText(true);
        nameLabel.setAlignment(Pos.CENTER);

        Label countLabel = new Label(group.getFileCount() + " 个");
        countLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 10px;");

        card.getChildren().addAll(iconLabel, nameLabel, countLabel);

        // 点击事件
        card.setOnMouseClicked(e -> {
            saveCurrentState();
            selectMechanism(group);
        });

        // 悬停效果
        String hoverStyle = String.format(
                "-fx-background-color: %s22; " +
                "-fx-border-color: %s; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;",
                category.getColor(), category.getColor());

        card.setOnMouseEntered(e -> {
            if (selectedCategory != category) {
                card.setStyle(hoverStyle);
            }
        });
        card.setOnMouseExited(e -> {
            if (selectedCategory != category) {
                card.setStyle(normalStyle);
            }
        });

        Tooltip tooltip = new Tooltip(category.getDescription() + "\n文件数: " + group.getFileCount());
        Tooltip.install(card, tooltip);

        return card;
    }

    /**
     * 高亮选中的机制卡片
     */
    private void highlightSelectedMechanism(AionMechanismCategory category) {
        for (javafx.scene.Node node : mechanismCardsPane.getChildren()) {
            if (node instanceof VBox) {
                VBox card = (VBox) node;
                AionMechanismCategory cardCategory = (AionMechanismCategory) card.getUserData();

                if (cardCategory == category) {
                    card.setStyle(String.format(
                            "-fx-background-color: %s44; " +
                            "-fx-border-color: %s; " +
                            "-fx-border-width: 3; " +
                            "-fx-border-radius: 6; " +
                            "-fx-background-radius: 6; " +
                            "-fx-cursor: hand;",
                            cardCategory.getColor(), cardCategory.getColor()));
                } else {
                    card.setStyle(String.format(
                            "-fx-background-color: white; " +
                            "-fx-border-color: %s; " +
                            "-fx-border-width: 2; " +
                            "-fx-border-radius: 6; " +
                            "-fx-background-radius: 6; " +
                            "-fx-cursor: hand;",
                            cardCategory.getColor()));
                }
            }
        }
    }

    /**
     * 选择机制
     */
    private void selectMechanism(AionMechanismView.MechanismGroup group) {
        if (group == null) return;

        selectedCategory = group.getCategory();
        selectedFile = null;
        currentParseResult = null;

        // 更新文件列表
        ObservableList<AionMechanismView.FileEntry> items = FXCollections.observableArrayList();
        items.addAll(group.getAllFiles());
        fileListView.setItems(items);

        // 清空字段区域
        fieldTable.getItems().clear();
        referenceBox.setVisible(false);

        // 更新详情
        detailArea.setText(String.format(
                "【%s】\n%s\n\n公共文件: %d 个\n本地化文件: %d 个\n\n" +
                "请从中间栏选择一个文件查看字段结构。",
                group.getCategory().getDisplayName(),
                group.getCategory().getDescription(),
                group.getPublicFileCount(),
                group.getLocalizedFileCount()
        ));

        updateBreadcrumb();
        highlightSelectedMechanism(selectedCategory);
        statusLabel.setText("已选择: " + selectedCategory.getDisplayName() + " (" + group.getFileCount() + " 个文件)");
    }

    /**
     * 选择文件
     */
    private void selectFile(AionMechanismView.FileEntry entry) {
        selectedFile = entry;
        updateBreadcrumb();

        // 异步解析文件
        progressIndicator.setVisible(true);
        statusLabel.setText("正在解析: " + entry.getFileName());

        CompletableFuture.runAsync(() -> {
            XmlFieldParser.ParseResult result = XmlFieldParser.parse(entry.getFile());

            Platform.runLater(() -> {
                currentParseResult = result;
                updateFieldTable(result);
                updateReferenceBox(result);
                updateDetailArea(entry, result);
                progressIndicator.setVisible(false);
                statusLabel.setText("已加载: " + entry.getFileName() + " (" + result.getFields().size() + " 个字段)");
            });
        });
    }

    /**
     * 更新字段表格
     */
    private void updateFieldTable(XmlFieldParser.ParseResult result) {
        ObservableList<XmlFieldParser.FieldInfo> items = FXCollections.observableArrayList();

        // 去重显示
        Set<String> seenFields = new HashSet<>();
        for (XmlFieldParser.FieldInfo field : result.getFields()) {
            String key = field.getName() + "|" + field.isAttribute();
            if (!seenFields.contains(key)) {
                seenFields.add(key);
                items.add(field);
            }
        }

        fieldTable.setItems(items);
    }

    /**
     * 更新引用关系区域
     */
    private void updateReferenceBox(XmlFieldParser.ParseResult result) {
        referenceTagsPane.getChildren().clear();

        Set<String> references = result.getReferences();
        if (references.isEmpty()) {
            referenceBox.setVisible(false);
            return;
        }

        referenceBox.setVisible(true);

        for (String ref : references) {
            Button tag = new Button(ref);
            AionMechanismCategory refCategory = mechanismNameMap.get(ref);

            String color = refCategory != null ? refCategory.getColor() : "#3498db";
            tag.setStyle(String.format(
                    "-fx-background-color: %s; " +
                    "-fx-text-fill: white; " +
                    "-fx-background-radius: 15; " +
                    "-fx-padding: 5 12; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-size: 12px;",
                    color));

            tag.setOnAction(e -> jumpToMechanism(ref));

            Tooltip tooltip = new Tooltip("点击跳转到: " + ref);
            Tooltip.install(tag, tooltip);

            referenceTagsPane.getChildren().add(tag);
        }
    }

    /**
     * 更新详情区域
     */
    private void updateDetailArea(AionMechanismView.FileEntry entry, XmlFieldParser.ParseResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("文件: ").append(entry.getFileName()).append("\n");
        sb.append("路径: ").append(entry.getRelativePath()).append("\n");
        sb.append("大小: ").append(entry.getFileSizeReadable()).append("\n");
        sb.append("本地化: ").append(entry.isLocalized() ? "是" : "否").append("\n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        if (result.hasError()) {
            sb.append("解析错误: ").append(result.getError()).append("\n");
        } else {
            sb.append("根元素: ").append(result.getRootElement()).append("\n");
            sb.append("字段数: ").append(result.getFields().size()).append("\n");

            Set<String> refs = result.getReferences();
            if (!refs.isEmpty()) {
                sb.append("\n关联系统:\n");
                for (String ref : refs) {
                    sb.append("  → ").append(ref).append("\n");
                }
            }
        }

        detailArea.setText(sb.toString());
    }

    /**
     * 过滤文件列表
     */
    private void filterFileList(String keyword) {
        if (mechanismView == null || selectedCategory == null) return;

        AionMechanismView.MechanismGroup group = mechanismView.getGroup(selectedCategory);
        if (group == null) return;

        List<AionMechanismView.FileEntry> filtered = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();

        for (AionMechanismView.FileEntry entry : group.getAllFiles()) {
            if (entry.getFileName().toLowerCase().contains(lowerKeyword)) {
                filtered.add(entry);
            }
        }

        fileListView.setItems(FXCollections.observableArrayList(filtered));
    }

    /**
     * 导航状态
     */
    private static class NavigationState {
        final AionMechanismCategory category;
        final AionMechanismView.FileEntry file;

        NavigationState(AionMechanismCategory category, AionMechanismView.FileEntry file) {
            this.category = category;
            this.file = file;
        }
    }

    /**
     * 文件条目单元格
     */
    private class FileEntryCell extends ListCell<AionMechanismView.FileEntry> {
        @Override
        protected void updateItem(AionMechanismView.FileEntry item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                HBox box = new HBox(8);
                box.setAlignment(Pos.CENTER_LEFT);

                Label nameLabel = new Label(item.getFileName());
                nameLabel.setFont(Font.font("Microsoft YaHei", 12));

                Label sizeLabel = new Label(item.getFileSizeReadable());
                sizeLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 10px;");

                box.getChildren().addAll(nameLabel, sizeLabel);

                if (item.isLocalized()) {
                    Label locLabel = new Label("[本地化]");
                    locLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 10px; -fx-font-weight: bold;");
                    box.getChildren().add(locLabel);
                }

                setGraphic(box);
                setText(null);
            }
        }
    }
}
