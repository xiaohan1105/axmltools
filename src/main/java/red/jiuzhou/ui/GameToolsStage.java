package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.game.PointCalculator;
import red.jiuzhou.util.game.SpawnTerritory;
import red.jiuzhou.util.game.WeightedRoundRobin;
import red.jiuzhou.util.game.WorldSpawnService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 刷怪工具窗口
 *
 * 专为游戏设计师打造的刷怪点规划工具：
 * - 刷怪点生成器：巡逻路线、圆形/环形刷怪区域
 * - 概率模拟器：怪物刷新权重、掉落概率验证
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class GameToolsStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(GameToolsStage.class);

    /** 世界刷怪服务 */
    private final WorldSpawnService worldSpawnService = new WorldSpawnService();

    /** 当前选中的地图 */
    private String currentMapName;

    /** 当前地图的刷怪区域列表 */
    private final ObservableList<SpawnTerritory> currentTerritories = FXCollections.observableArrayList();

    /** 刷怪点生成器输入字段（用于联动） */
    private TextField spawnStartX, spawnStartY, spawnStartZ;

    public GameToolsStage() {
        initUI();
    }

    private void initUI() {
        setTitle("刷怪点规划工具");
        setWidth(1100);
        setHeight(700);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // 地图浏览器（新增）
        Tab mapTab = new Tab("🗺️ 地图浏览");
        mapTab.setContent(createMapBrowserPane());

        // 刷怪点生成器
        Tab pointTab = new Tab("📍 刷怪点生成");
        pointTab.setContent(createPointCalculatorPane());

        // 概率模拟器
        Tab weightTab = new Tab("🎲 概率模拟");
        weightTab.setContent(createWeightedSelectorPane());

        // 使用说明
        Tab helpTab = new Tab("📖 使用说明");
        helpTab.setContent(createHelpPane());

        tabPane.getTabs().addAll(mapTab, pointTab, weightTab, helpTab);

        Scene scene = new Scene(tabPane);
        setScene(scene);
    }

    /**
     * 创建地图浏览器面板
     */
    private SplitPane createMapBrowserPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.25);

        // 左侧：地图列表
        VBox leftPane = createMapListPane();

        // 右侧：刷怪区域详情
        VBox rightPane = createTerritoryDetailPane();

        splitPane.getItems().addAll(leftPane, rightPane);
        return splitPane;
    }

    /**
     * 创建地图列表面板
     */
    private VBox createMapListPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));
        pane.setMinWidth(200);

        Label title = new Label("🗺️ 可用地图");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 搜索框
        TextField searchField = new TextField();
        searchField.setPromptText("搜索地图...");

        // 地图列表
        ListView<WorldSpawnService.MapInfo> mapListView = new ListView<>();
        mapListView.setCellFactory(lv -> new ListCell<WorldSpawnService.MapInfo>() {
            @Override
            protected void updateItem(WorldSpawnService.MapInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox cell = new VBox(2);
                    Label nameLabel = new Label(item.getName());
                    nameLabel.setStyle("-fx-font-weight: bold;");
                    Label sizeLabel = new Label(item.getSizeDisplay());
                    sizeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
                    cell.getChildren().addAll(nameLabel, sizeLabel);
                    setGraphic(cell);
                }
            }
        });
        VBox.setVgrow(mapListView, Priority.ALWAYS);

        // 地图统计
        Label statsLabel = new Label("正在加载地图列表...");
        statsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        // 刷新按钮
        Button refreshBtn = new Button("刷新列表");

        // 加载地图列表
        Runnable loadMaps = () -> {
            CompletableFuture.runAsync(() -> {
                List<WorldSpawnService.MapInfo> maps = worldSpawnService.getAvailableMaps();
                Platform.runLater(() -> {
                    mapListView.getItems().setAll(maps);
                    statsLabel.setText(String.format("共 %d 个地图", maps.size()));
                });
            });
        };

        // 搜索过滤
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                loadMaps.run();
            } else {
                String pattern = newVal.toLowerCase();
                CompletableFuture.runAsync(() -> {
                    List<WorldSpawnService.MapInfo> maps = worldSpawnService.getAvailableMaps();
                    List<WorldSpawnService.MapInfo> filtered = new ArrayList<>();
                    for (WorldSpawnService.MapInfo map : maps) {
                        if (map.getName().toLowerCase().contains(pattern)) {
                            filtered.add(map);
                        }
                    }
                    Platform.runLater(() -> mapListView.getItems().setAll(filtered));
                });
            }
        });

        refreshBtn.setOnAction(e -> {
            worldSpawnService.clearCache();
            loadMaps.run();
        });

        // 选择地图时加载刷怪区域
        mapListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadMapTerritories(newVal.getName());
            }
        });

        // 初始加载
        loadMaps.run();

        pane.getChildren().addAll(title, searchField, mapListView, statsLabel, refreshBtn);
        return pane;
    }

    /**
     * 创建刷怪区域详情面板
     */
    private VBox createTerritoryDetailPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        Label title = new Label("刷怪区域列表");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 搜索框
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        TextField territorySearchField = new TextField();
        territorySearchField.setPromptText("搜索区域名或NPC名...");
        territorySearchField.setPrefWidth(250);
        Label countLabel = new Label("");
        countLabel.setStyle("-fx-text-fill: #666;");
        searchBox.getChildren().addAll(territorySearchField, countLabel);

        // 刷怪区域表格
        TableView<SpawnTerritory> territoryTable = new TableView<>(currentTerritories);
        territoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SpawnTerritory, String> nameCol = new TableColumn<>("区域名称");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        nameCol.setPrefWidth(200);

        TableColumn<SpawnTerritory, String> npcCountCol = new TableColumn<>("NPC数");
        npcCountCol.setCellValueFactory(data -> new SimpleStringProperty(
            String.valueOf(data.getValue().getNpcs().size())));
        npcCountCol.setPrefWidth(60);

        TableColumn<SpawnTerritory, String> totalCol = new TableColumn<>("总数量");
        totalCol.setCellValueFactory(data -> new SimpleStringProperty(
            String.valueOf(data.getValue().getTotalNpcCount())));
        totalCol.setPrefWidth(60);

        TableColumn<SpawnTerritory, String> pointsCol = new TableColumn<>("刷怪点");
        pointsCol.setCellValueFactory(data -> new SimpleStringProperty(
            String.valueOf(data.getValue().getSpawnPoints().size())));
        pointsCol.setPrefWidth(60);

        TableColumn<SpawnTerritory, String> areaCol = new TableColumn<>("区域类型");
        areaCol.setCellValueFactory(data -> {
            SpawnTerritory t = data.getValue();
            String type = t.isAerialSpawn() ? "空中" : "地面";
            if (t.isNoRespawn()) type += "/不重生";
            return new SimpleStringProperty(type);
        });
        areaCol.setPrefWidth(80);

        territoryTable.getColumns().addAll(nameCol, npcCountCol, totalCol, pointsCol, areaCol);
        VBox.setVgrow(territoryTable, Priority.ALWAYS);

        // 搜索过滤
        territorySearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterTerritories(newVal, countLabel);
        });

        // 选中区域时显示详情
        TextArea detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setPrefRowCount(8);
        detailArea.setPromptText("选中区域后显示详情...");

        territoryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                detailArea.setText(formatTerritoryDetail(newVal));
            }
        });

        // 右键菜单
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyCoordItem = new MenuItem("复制中心坐标到生成器");
        copyCoordItem.setOnAction(e -> {
            SpawnTerritory selected = territoryTable.getSelectionModel().getSelectedItem();
            if (selected != null && spawnStartX != null) {
                double[] center = selected.getMoveAreaCenter();
                spawnStartX.setText(String.format("%.2f", center[0]));
                spawnStartY.setText(String.format("%.2f", center[1]));
                spawnStartZ.setText(String.format("%.2f", selected.getCheckSurfaceZ()));
            }
        });

        MenuItem copyNpcItem = new MenuItem("复制NPC配置到模拟器");
        MenuItem viewPointsItem = new MenuItem("查看所有刷怪点");
        viewPointsItem.setOnAction(e -> {
            SpawnTerritory selected = territoryTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showSpawnPointsDialog(selected);
            }
        });

        contextMenu.getItems().addAll(copyCoordItem, copyNpcItem, new SeparatorMenuItem(), viewPointsItem);
        territoryTable.setContextMenu(contextMenu);

        pane.getChildren().addAll(title, searchBox, territoryTable, new Label("区域详情:"), detailArea);
        return pane;
    }

    /**
     * 加载地图的刷怪区域
     */
    private void loadMapTerritories(String mapName) {
        currentMapName = mapName;
        currentTerritories.clear();

        CompletableFuture.runAsync(() -> {
            List<SpawnTerritory> territories = worldSpawnService.loadMapSpawns(mapName);
            Platform.runLater(() -> {
                currentTerritories.setAll(territories);
                log.info("加载地图 {} 完成，共 {} 个刷怪区域", mapName, territories.size());
            });
        });
    }

    /**
     * 过滤刷怪区域
     */
    private void filterTerritories(String pattern, Label countLabel) {
        if (currentMapName == null) return;

        if (pattern == null || pattern.isEmpty()) {
            List<SpawnTerritory> all = worldSpawnService.loadMapSpawns(currentMapName);
            currentTerritories.setAll(all);
            countLabel.setText(String.format("共 %d 个区域", all.size()));
        } else {
            String lowerPattern = pattern.toLowerCase();
            List<SpawnTerritory> all = worldSpawnService.loadMapSpawns(currentMapName);
            List<SpawnTerritory> filtered = new ArrayList<>();
            for (SpawnTerritory t : all) {
                boolean match = (t.getName() != null && t.getName().toLowerCase().contains(lowerPattern));
                if (!match) {
                    for (SpawnTerritory.SpawnNpc npc : t.getNpcs()) {
                        if (npc.getName() != null && npc.getName().toLowerCase().contains(lowerPattern)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (match) filtered.add(t);
            }
            currentTerritories.setAll(filtered);
            countLabel.setText(String.format("筛选: %d / %d", filtered.size(), all.size()));
        }
    }

    /**
     * 格式化区域详情
     */
    private String formatTerritoryDetail(SpawnTerritory territory) {
        StringBuilder sb = new StringBuilder();
        sb.append("区域名: ").append(territory.getName()).append("\n");
        sb.append("天气区: ").append(territory.getWeatherZoneName()).append("\n");

        double[] center = territory.getMoveAreaCenter();
        sb.append(String.format("中心点: (%.2f, %.2f, %.2f)\n", center[0], center[1], territory.getCheckSurfaceZ()));

        double[] bounds = territory.getMoveAreaBounds();
        sb.append(String.format("边界框: (%.0f,%.0f) - (%.0f,%.0f)\n", bounds[0], bounds[1], bounds[2], bounds[3]));

        sb.append("\n--- NPC配置 ---\n");
        for (SpawnTerritory.SpawnNpc npc : territory.getNpcs()) {
            sb.append(String.format("  %s x%d (%s)\n", npc.getName(), npc.getCount(), npc.getSpawnTimeDisplay()));
        }

        sb.append("\n--- 属性 ---\n");
        sb.append("空中刷怪: ").append(territory.isAerialSpawn() ? "是" : "否").append("\n");
        sb.append("可重生: ").append(territory.isNoRespawn() ? "否" : "是").append("\n");
        sb.append("生成寻路: ").append(territory.isGeneratePathfind() ? "是" : "否").append("\n");

        return sb.toString();
    }

    /**
     * 显示刷怪点详情对话框
     */
    private void showSpawnPointsDialog(SpawnTerritory territory) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("刷怪点详情");
        alert.setHeaderText(territory.getName());

        StringBuilder sb = new StringBuilder();
        sb.append("移动区域多边形:\n");
        for (int i = 0; i < territory.getMoveAreaPoints().size(); i++) {
            double[] p = territory.getMoveAreaPoints().get(i);
            sb.append(String.format("  %d. (%.2f, %.2f)\n", i + 1, p[0], p[1]));
        }

        sb.append("\n刷怪点:\n");
        for (int i = 0; i < territory.getSpawnPoints().size(); i++) {
            SpawnTerritory.SpawnPoint p = territory.getSpawnPoints().get(i);
            sb.append(String.format("  %d. %s (区域%d)\n", i + 1, p.toString(), p.getMoveAreaIndex()));
        }

        TextArea textArea = new TextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setPrefRowCount(15);
        textArea.setPrefColumnCount(40);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    /**
     * 创建刷怪点生成器面板
     */
    private VBox createPointCalculatorPane() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));

        // 标题
        Label title = new Label("刷怪点生成器");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitle = new Label("生成巡逻路线、刷怪区域的坐标点，可直接复制为XML配置");
        subtitle.setStyle("-fx-text-fill: #666;");

        // 功能选择
        ComboBox<String> modeSelector = new ComboBox<>();
        modeSelector.getItems().addAll(
            "巡逻路线（两点间均匀分布路径点）",
            "圆形刷怪区（BOSS周围刷怪点）",
            "环形刷怪区（安全区外围刷怪点）"
        );
        modeSelector.setValue("巡逻路线（两点间均匀分布路径点）");

        // 输入区域
        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(10);
        inputGrid.setVgap(10);

        // 起点/圆心（保存为成员变量以支持联动）
        spawnStartX = new TextField("0");
        spawnStartY = new TextField("0");
        spawnStartZ = new TextField("0");
        spawnStartX.setPrefWidth(100);
        spawnStartY.setPrefWidth(100);
        spawnStartZ.setPrefWidth(100);

        inputGrid.add(new Label("起点/圆心:"), 0, 0);
        inputGrid.add(new Label("X:"), 1, 0);
        inputGrid.add(spawnStartX, 2, 0);
        inputGrid.add(new Label("Y:"), 3, 0);
        inputGrid.add(spawnStartY, 4, 0);
        inputGrid.add(new Label("Z:"), 5, 0);
        inputGrid.add(spawnStartZ, 6, 0);

        // 终点/半径
        TextField endX = new TextField("100");
        TextField endY = new TextField("100");
        TextField endZ = new TextField("0");
        endX.setPrefWidth(100);
        endY.setPrefWidth(100);
        endZ.setPrefWidth(100);

        inputGrid.add(new Label("终点/半径:"), 0, 1);
        inputGrid.add(new Label("X:"), 1, 1);
        inputGrid.add(endX, 2, 1);
        inputGrid.add(new Label("Y:"), 3, 1);
        inputGrid.add(endY, 4, 1);
        inputGrid.add(new Label("Z:"), 5, 1);
        inputGrid.add(endZ, 6, 1);

        // 刷怪点数量
        TextField pointCount = new TextField("10");
        pointCount.setPrefWidth(100);
        inputGrid.add(new Label("刷怪点数:"), 0, 2);
        inputGrid.add(pointCount, 2, 2);

        // 内半径（环形用）
        TextField innerRadius = new TextField("20");
        innerRadius.setPrefWidth(100);
        inputGrid.add(new Label("内圈半径:"), 3, 2);
        inputGrid.add(innerRadius, 4, 2);

        // 生成按钮
        Button calcBtn = new Button("生成刷怪点");
        calcBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");

        // 结果区域
        TextArea resultArea = new TextArea();
        resultArea.setPromptText("生成的刷怪点坐标将显示在这里...");
        resultArea.setEditable(false);
        resultArea.setPrefRowCount(15);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        // 生成逻辑
        calcBtn.setOnAction(e -> {
            try {
                String mode = modeSelector.getValue();
                int count = Integer.parseInt(pointCount.getText().trim());
                List<PointCalculator.Point3D> points = new ArrayList<>();

                if (mode.startsWith("巡逻路线")) {
                    PointCalculator.Point3D p1 = new PointCalculator.Point3D(
                        Double.parseDouble(spawnStartX.getText()),
                        Double.parseDouble(spawnStartY.getText()),
                        Double.parseDouble(spawnStartZ.getText())
                    );
                    PointCalculator.Point3D p2 = new PointCalculator.Point3D(
                        Double.parseDouble(endX.getText()),
                        Double.parseDouble(endY.getText()),
                        Double.parseDouble(endZ.getText())
                    );
                    points = PointCalculator.interpolateLinear(p1, p2, count);
                } else if (mode.startsWith("圆形刷怪区")) {
                    PointCalculator.Point3D center = new PointCalculator.Point3D(
                        Double.parseDouble(spawnStartX.getText()),
                        Double.parseDouble(spawnStartY.getText()),
                        Double.parseDouble(spawnStartZ.getText())
                    );
                    double radius = Double.parseDouble(endX.getText());
                    points = PointCalculator.generateRandomInCircle(center, radius, count);
                } else if (mode.startsWith("环形刷怪区")) {
                    PointCalculator.Point3D center = new PointCalculator.Point3D(
                        Double.parseDouble(spawnStartX.getText()),
                        Double.parseDouble(spawnStartY.getText()),
                        Double.parseDouble(spawnStartZ.getText())
                    );
                    double inner = Double.parseDouble(innerRadius.getText());
                    double outer = Double.parseDouble(endX.getText());
                    points = PointCalculator.generateRandomInRing(center, inner, outer, count);
                }

                // 格式化输出
                StringBuilder sb = new StringBuilder();
                sb.append("生成 ").append(points.size()).append(" 个刷怪点:\n\n");
                sb.append(String.format("%-6s %-15s %-15s %-15s\n", "序号", "X", "Y", "Z"));
                sb.append("───────────────────────────────────────────────────────\n");

                for (int i = 0; i < points.size(); i++) {
                    PointCalculator.Point3D p = points.get(i);
                    sb.append(String.format("%-6d %-15s %-15s %-15s\n",
                        i + 1, p.getXFormatted(), p.getYFormatted(), p.getZFormatted()));
                }

                // 添加XML格式（可直接复制到配置文件）
                sb.append("\n\n--- 可复制的XML配置 ---\n");
                for (int i = 0; i < points.size(); i++) {
                    PointCalculator.Point3D p = points.get(i);
                    sb.append(String.format("<spot x=\"%s\" y=\"%s\" z=\"%s\" />\n",
                        p.getXFormatted(), p.getYFormatted(), p.getZFormatted()));
                }

                resultArea.setText(sb.toString());
                log.info("刷怪点生成完成: {} 个点", points.size());

            } catch (Exception ex) {
                resultArea.setText("生成失败: " + ex.getMessage());
                log.error("刷怪点生成失败", ex);
            }
        });

        // 复制按钮
        Button copyBtn = new Button("复制结果");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(resultArea.getText());
            clipboard.setContent(content);
        });

        HBox buttonBox = new HBox(10, calcBtn, copyBtn);

        root.getChildren().addAll(
            title, subtitle,
            new Label("刷怪模式:"), modeSelector,
            inputGrid,
            buttonBox,
            new Label("生成结果:"), resultArea
        );

        return root;
    }

    /**
     * 创建概率模拟器面板
     */
    private VBox createWeightedSelectorPane() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));

        // 标题
        Label title = new Label("刷怪概率模拟器");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label desc = new Label("验证怪物刷新权重配置，模拟实际刷怪比例");
        desc.setStyle("-fx-text-fill: #666;");

        // 模式选择
        ComboBox<String> modeSelector = new ComboBox<>();
        modeSelector.getItems().addAll(
            "刷新权重（保证长期比例，如怪物刷新）",
            "掉落概率（每次独立，如道具掉落）",
            "保底机制（不重复，如首杀奖励）"
        );
        modeSelector.setValue("刷新权重（保证长期比例，如怪物刷新）");

        // 怪物配置输入
        Label inputLabel = new Label("输入怪物和权重（每行一个，格式：怪物名,权重）:");
        TextArea inputArea = new TextArea();
        inputArea.setPromptText("小怪,50\n精英怪,30\n稀有怪,15\nBOSS,5");
        inputArea.setText("普通小怪,50\n精英怪物,30\n稀有精英,15\n世界BOSS,5");
        inputArea.setPrefRowCount(6);

        // 模拟次数
        HBox countBox = new HBox(10);
        countBox.setAlignment(Pos.CENTER_LEFT);
        Label countLabel = new Label("模拟刷怪次数:");
        TextField countField = new TextField("100");
        countField.setPrefWidth(80);
        countBox.getChildren().addAll(countLabel, countField);

        // 执行按钮
        Button runBtn = new Button("开始模拟");
        runBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");

        // 结果区域
        TextArea resultArea = new TextArea();
        resultArea.setPromptText("模拟结果将显示在这里...");
        resultArea.setEditable(false);
        resultArea.setPrefRowCount(12);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        // 执行逻辑
        runBtn.setOnAction(e -> {
            try {
                String mode = modeSelector.getValue();
                int count = Integer.parseInt(countField.getText().trim());

                // 解析输入
                WeightedRoundRobin<String> selector = new WeightedRoundRobin<>();
                String[] lines = inputArea.getText().split("\n");
                List<String> items = new ArrayList<>();

                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String name = parts[0].trim();
                        int weight = Integer.parseInt(parts[1].trim());
                        selector.add(name, weight);
                        items.add(name);
                    }
                }

                if (selector.isEmpty()) {
                    resultArea.setText("错误: 请输入至少一个怪物配置");
                    return;
                }

                // 执行选择
                List<String> results;
                if (mode.startsWith("刷新权重")) {
                    results = selector.selectMultipleRoundRobin(count);
                } else if (mode.startsWith("掉落概率")) {
                    results = selector.selectMultipleRandom(count);
                } else {
                    results = selector.selectUniqueRandom(Math.min(count, selector.size()));
                }

                // 统计结果
                java.util.Map<String, Integer> stats = new java.util.LinkedHashMap<>();
                for (String item : items) {
                    stats.put(item, 0);
                }
                for (String result : results) {
                    stats.merge(result, 1, Integer::sum);
                }

                // 格式化输出
                StringBuilder sb = new StringBuilder();
                sb.append("模拟模式: ").append(mode).append("\n");
                sb.append("刷怪次数: ").append(count).append("\n\n");

                sb.append("刷怪统计:\n");
                sb.append(String.format("%-15s %-10s %-10s %-20s\n", "怪物", "出现次数", "实际比例", "分布图"));
                sb.append("────────────────────────────────────────────────────────────\n");

                int maxCount = stats.values().stream().mapToInt(Integer::intValue).max().orElse(1);
                for (java.util.Map.Entry<String, Integer> entry : stats.entrySet()) {
                    int c = entry.getValue();
                    double ratio = (double) c / count * 100;
                    int barLen = maxCount > 0 ? (int) ((double) c / maxCount * 20) : 0;
                    StringBuilder bar = new StringBuilder();
                    for (int i = 0; i < barLen; i++) bar.append("█");
                    sb.append(String.format("%-15s %-10d %-10.1f%% %-20s\n",
                        entry.getKey(), c, ratio, bar.toString()));
                }

                // 显示前20个刷怪序列
                sb.append("\n刷怪序列（前20次）:\n");
                for (int i = 0; i < Math.min(20, results.size()); i++) {
                    sb.append(String.format("%3d. %s\n", i + 1, results.get(i)));
                }
                if (results.size() > 20) {
                    sb.append("... 共 ").append(results.size()).append(" 次刷怪\n");
                }

                resultArea.setText(sb.toString());
                log.info("刷怪概率模拟完成: {} 次", count);

            } catch (Exception ex) {
                resultArea.setText("模拟失败: " + ex.getMessage());
                log.error("刷怪概率模拟失败", ex);
            }
        });

        root.getChildren().addAll(
            title, desc,
            new Label("选择模式:"), modeSelector,
            inputLabel, inputArea,
            countBox,
            runBtn,
            new Label("模拟结果:"), resultArea
        );

        return root;
    }

    /**
     * 创建帮助面板
     */
    private ScrollPane createHelpPane() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        Label title = new Label("刷怪工具使用说明");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // 地图浏览器说明
        VBox mapHelp = new VBox(8);
        mapHelp.setStyle("-fx-background-color: #F3E5F5; -fx-padding: 15; -fx-background-radius: 5;");
        Label mapTitle = new Label("🗺️ 地图浏览器");
        mapTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label mapDesc = new Label(
            "浏览和分析 World 目录下的刷怪配置:\n\n" +
            "• 左侧显示所有地图列表，点击加载刷怪区域\n" +
            "• 支持按地图名搜索过滤\n" +
            "• 显示每个地图的刷怪文件大小\n\n" +
            "• 右侧表格显示所有刷怪区域（territory）\n" +
            "• 支持按区域名或NPC名搜索\n" +
            "• 右键菜单可复制坐标到生成器\n\n" +
            "使用流程: 选择地图 → 查看区域 → 右键复制坐标 → 切换到生成器标签生成新刷怪点"
        );
        mapDesc.setWrapText(true);
        mapHelp.getChildren().addAll(mapTitle, mapDesc);

        // 刷怪点生成器说明
        VBox pointHelp = new VBox(8);
        pointHelp.setStyle("-fx-background-color: #E3F2FD; -fx-padding: 15; -fx-background-radius: 5;");
        Label pointTitle = new Label("📍 刷怪点生成器");
        pointTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label pointDesc = new Label(
            "用于规划怪物刷新区域和巡逻路线:\n\n" +
            "• 巡逻路线: 在两点之间生成均匀分布的路径点\n" +
            "  适用于: NPC巡逻、传送点序列、定点刷怪路线\n\n" +
            "• 圆形刷怪区: 以指定圆心和半径生成随机刷怪点\n" +
            "  适用于: BOSS周围刷小怪、区域随机刷怪\n\n" +
            "• 环形刷怪区: 在环形区域内生成随机点\n" +
            "  适用于: 安全区外围刷怪、城墙周边刷怪\n\n" +
            "生成的坐标可一键复制为XML配置，直接粘贴到spawn配置文件。"
        );
        pointDesc.setWrapText(true);
        pointHelp.getChildren().addAll(pointTitle, pointDesc);

        // 概率模拟器说明
        VBox weightHelp = new VBox(8);
        weightHelp.setStyle("-fx-background-color: #E8F5E9; -fx-padding: 15; -fx-background-radius: 5;");
        Label weightTitle = new Label("🎲 刷怪概率模拟器");
        weightTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label weightDesc = new Label(
            "用于验证和调试怪物刷新配置:\n\n" +
            "• 刷新权重: 保证长期比例严格符合配置\n" +
            "  适用于: 怪物刷新点的权重配置验证\n" +
            "  示例: 普通怪50%、精英怪30%、BOSS 5%\n\n" +
            "• 掉落概率: 每次独立按权重随机\n" +
            "  适用于: 道具掉落概率、抽卡概率验证\n\n" +
            "• 保底机制: 从池中不重复选择\n" +
            "  适用于: 首杀奖励、保底掉落等场景\n\n" +
            "输入格式: 每行一个，格式为 \"怪物名,权重\""
        );
        weightDesc.setWrapText(true);
        weightHelp.getChildren().addAll(weightTitle, weightDesc);

        // 使用场景
        VBox scenarioHelp = new VBox(8);
        scenarioHelp.setStyle("-fx-background-color: #FFF3E0; -fx-padding: 15; -fx-background-radius: 5;");
        Label scenarioTitle = new Label("💡 常见使用场景");
        scenarioTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label scenarioDesc = new Label(
            "场景1: 规划新副本刷怪点\n" +
            "→ 确定BOSS位置坐标作为圆心\n" +
            "→ 设置刷怪半径（如50米）\n" +
            "→ 生成10-20个刷怪点\n" +
            "→ 复制XML配置到spawn文件\n\n" +
            "场景2: 验证刷怪权重配置\n" +
            "→ 输入当前配置的怪物和权重\n" +
            "→ 模拟1000次刷怪\n" +
            "→ 检查实际比例是否符合预期\n\n" +
            "场景3: 设计巡逻路线\n" +
            "→ 输入起点和终点坐标\n" +
            "→ 设置路径点数量（如8个）\n" +
            "→ 生成均匀分布的巡逻点"
        );
        scenarioDesc.setWrapText(true);
        scenarioHelp.getChildren().addAll(scenarioTitle, scenarioDesc);

        content.getChildren().addAll(title, mapHelp, pointHelp, weightHelp, scenarioHelp);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }
}
