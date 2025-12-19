package red.jiuzhou.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.game.PointCalculator;
import red.jiuzhou.util.game.WeightedRoundRobin;

import java.util.ArrayList;
import java.util.List;

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

    public GameToolsStage() {
        initUI();
    }

    private void initUI() {
        setTitle("刷怪点规划工具");
        setWidth(900);
        setHeight(650);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // 刷怪点生成器
        Tab pointTab = new Tab("📍 刷怪点生成");
        pointTab.setContent(createPointCalculatorPane());

        // 概率模拟器
        Tab weightTab = new Tab("🎲 概率模拟");
        weightTab.setContent(createWeightedSelectorPane());

        // 使用说明
        Tab helpTab = new Tab("📖 使用说明");
        helpTab.setContent(createHelpPane());

        tabPane.getTabs().addAll(pointTab, weightTab, helpTab);

        Scene scene = new Scene(tabPane);
        setScene(scene);
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

        // 起点/圆心
        TextField startX = new TextField("0");
        TextField startY = new TextField("0");
        TextField startZ = new TextField("0");
        startX.setPrefWidth(100);
        startY.setPrefWidth(100);
        startZ.setPrefWidth(100);

        inputGrid.add(new Label("起点/圆心:"), 0, 0);
        inputGrid.add(new Label("X:"), 1, 0);
        inputGrid.add(startX, 2, 0);
        inputGrid.add(new Label("Y:"), 3, 0);
        inputGrid.add(startY, 4, 0);
        inputGrid.add(new Label("Z:"), 5, 0);
        inputGrid.add(startZ, 6, 0);

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
                        Double.parseDouble(startX.getText()),
                        Double.parseDouble(startY.getText()),
                        Double.parseDouble(startZ.getText())
                    );
                    PointCalculator.Point3D p2 = new PointCalculator.Point3D(
                        Double.parseDouble(endX.getText()),
                        Double.parseDouble(endY.getText()),
                        Double.parseDouble(endZ.getText())
                    );
                    points = PointCalculator.interpolateLinear(p1, p2, count);
                } else if (mode.startsWith("圆形刷怪区")) {
                    PointCalculator.Point3D center = new PointCalculator.Point3D(
                        Double.parseDouble(startX.getText()),
                        Double.parseDouble(startY.getText()),
                        Double.parseDouble(startZ.getText())
                    );
                    double radius = Double.parseDouble(endX.getText());
                    points = PointCalculator.generateRandomInCircle(center, radius, count);
                } else if (mode.startsWith("环形刷怪区")) {
                    PointCalculator.Point3D center = new PointCalculator.Point3D(
                        Double.parseDouble(startX.getText()),
                        Double.parseDouble(startY.getText()),
                        Double.parseDouble(startZ.getText())
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

        content.getChildren().addAll(title, pointHelp, weightHelp, scenarioHelp);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }
}
