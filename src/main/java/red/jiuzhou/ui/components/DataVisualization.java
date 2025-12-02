package red.jiuzhou.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据可视化组件
 * 为设计师提供直观的数据统计和分析图表
 *
 * 功能特性：
 * - 实时数据统计卡片
 * - 交互式图表展示
 * - 数据分布分析
 * - 趋势预测可视化
 * - 可定制的仪表板
 */
public class DataVisualization {

    private final VBox container;
    private final Map<String, StatCard> statCards = new HashMap<>();
    private final TabPane chartTabs;

    // 数据统计
    private List<Map<String, Object>> currentData = new ArrayList<>();
    private Map<String, DataMetrics> columnMetrics = new HashMap<>();

    public DataVisualization() {
        this.container = new VBox();
        this.chartTabs = new TabPane();

        initializeComponents();
        applyStyles();
    }

    /**
     * 初始化组件
     */
    private void initializeComponents() {
        container.getStyleClass().add("data-visualization");
        container.setSpacing(20);
        container.setPadding(new Insets(20));

        // 统计卡片区域
        HBox statsContainer = createStatsContainer();

        // 图表区域
        VBox chartsContainer = createChartsContainer();

        container.getChildren().addAll(statsContainer, chartsContainer);
    }

    /**
     * 创建统计卡片容器
     */
    private HBox createStatsContainer() {
        HBox statsContainer = new HBox();
        statsContainer.getStyleClass().add("stats-container");
        statsContainer.setSpacing(16);
        statsContainer.setAlignment(Pos.CENTER);

        // 创建基础统计卡片
        statCards.put("total", createStatCard("[统计]", "总记录数", "0", "当前数据集的记录总数"));
        statCards.put("columns", createStatCard("[字段]", "字段数量", "0", "数据表的字段总数"));
        statCards.put("quality", createStatCard("[质量]", "数据质量", "0%", "数据完整性和准确性评分"));
        statCards.put("size", createStatCard("[大小]", "数据大小", "0 KB", "当前数据集的存储大小"));

        statsContainer.getChildren().addAll(statCards.values());
        return statsContainer;
    }

    /**
     * 创建统计卡片
     */
    private StatCard createStatCard(String icon, String title, String value, String description) {
        StatCard card = new StatCard(icon, title, value, description);

        // 添加动画效果
        card.setOnMouseEntered(e -> animateCardHover(card, true));
        card.setOnMouseExited(e -> animateCardHover(card, false));

        return card;
    }

    /**
     * 卡片悬停动画
     */
    private void animateCardHover(StatCard card, boolean hover) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
        scale.setToX(hover ? 1.05 : 1.0);
        scale.setToY(hover ? 1.05 : 1.0);
        scale.play();
    }

    /**
     * 创建图表容器
     */
    private VBox createChartsContainer() {
        VBox chartsContainer = new VBox();
        chartsContainer.getStyleClass().add("charts-container");

        // 图表标题
        Label chartsTitle = new Label("[图表] 数据分析图表");
        chartsTitle.getStyleClass().add("charts-title");

        // 创建图表标签页
        chartTabs.getStyleClass().add("chart-tabs");
        chartTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // 添加各种图表
        addDistributionChart();
        addTrendChart();
        addCorrelationChart();
        addQualityChart();

        chartsContainer.getChildren().addAll(chartsTitle, chartTabs);
        VBox.setVgrow(chartTabs, Priority.ALWAYS);

        return chartsContainer;
    }

    /**
     * 添加数据分布图表
     */
    private void addDistributionChart() {
        Tab distributionTab = new Tab("[分布] 数据分布");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // 字段选择器
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("选择字段:");
        ComboBox<String> fieldCombo = new ComboBox<>();
        fieldCombo.setPromptText("选择要分析的字段");
        fieldCombo.getStyleClass().add("field-selector");

        Button refreshButton = new Button("[刷新] 刷新");
        refreshButton.getStyleClass().addAll("refresh-button", "secondary");
        refreshButton.setOnAction(e -> updateDistributionChart(fieldCombo.getValue()));

        controls.getChildren().addAll(label, fieldCombo, refreshButton);

        // 图表区域
        StackPane chartPane = new StackPane();
        chartPane.getStyleClass().add("chart-pane");
        chartPane.setPrefHeight(350);

        // 默认显示提示
        Label chartHint = new Label("选择字段后将显示数据分布图表");
        chartHint.getStyleClass().add("chart-hint");
        chartPane.getChildren().add(chartHint);

        content.getChildren().addAll(controls, chartPane);
        distributionTab.setContent(content);
        chartTabs.getTabs().add(distributionTab);
    }

    /**
     * 添加趋势分析图表
     */
    private void addTrendChart() {
        Tab trendTab = new Tab("[趋势] 趋势分析");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // 创建折线图
        LineChart<String, Number> lineChart = createLineChart();
        content.getChildren().add(lineChart);

        trendTab.setContent(content);
        chartTabs.getTabs().add(trendTab);
    }

    /**
     * 添加关联性分析图表
     */
    private void addCorrelationChart() {
        Tab correlationTab = new Tab("[关联] 关联分析");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // 创建散点图
        ScatterChart<Number, Number> scatterChart = createScatterChart();
        content.getChildren().add(scatterChart);

        correlationTab.setContent(content);
        chartTabs.getTabs().add(correlationTab);
    }

    /**
     * 添加数据质量图表
     */
    private void addQualityChart() {
        Tab qualityTab = new Tab("[质量] 数据质量");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // 质量指标网格
        GridPane qualityGrid = createQualityGrid();

        // 饼图显示质量分布
        PieChart qualityPie = createQualityPieChart();

        content.getChildren().addAll(qualityGrid, qualityPie);
        qualityTab.setContent(content);
        chartTabs.getTabs().add(qualityTab);
    }

    /**
     * 创建折线图
     */
    private LineChart<String, Number> createLineChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("时间");
        yAxis.setLabel("数量");

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("数据变化趋势");
        lineChart.getStyleClass().add("trend-chart");

        // 添加示例数据
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("记录数量");
        series.getData().add(new XYChart.Data<>("1月", 100));
        series.getData().add(new XYChart.Data<>("2月", 150));
        series.getData().add(new XYChart.Data<>("3月", 200));
        series.getData().add(new XYChart.Data<>("4月", 180));
        series.getData().add(new XYChart.Data<>("5月", 250));

        lineChart.getData().add(series);
        return lineChart;
    }

    /**
     * 创建散点图
     */
    private ScatterChart<Number, Number> createScatterChart() {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("字段A");
        yAxis.setLabel("字段B");

        ScatterChart<Number, Number> scatterChart = new ScatterChart<>(xAxis, yAxis);
        scatterChart.setTitle("字段关联性分析");
        scatterChart.getStyleClass().add("scatter-chart");

        // 添加示例数据
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("数据点");

        Random random = new Random();
        for (int i = 0; i < 50; i++) {
            double x = random.nextGaussian() * 10 + 50;
            double y = x * 0.8 + random.nextGaussian() * 5 + 20;
            series.getData().add(new XYChart.Data<>(x, y));
        }

        scatterChart.getData().add(series);
        return scatterChart;
    }

    /**
     * 创建质量指标网格
     */
    private GridPane createQualityGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("quality-grid");
        grid.setHgap(20);
        grid.setVgap(15);

        // 质量指标
        addQualityMetric(grid, 0, 0, "完整性", "98.5%", "数据字段填充完整度");
        addQualityMetric(grid, 1, 0, "一致性", "96.2%", "数据格式和规则一致性");
        addQualityMetric(grid, 0, 1, "准确性", "94.8%", "数据值的准确程度");
        addQualityMetric(grid, 1, 1, "时效性", "99.1%", "数据更新的及时性");

        return grid;
    }

    /**
     * 添加质量指标
     */
    private void addQualityMetric(GridPane grid, int col, int row, String name, String value, String description) {
        VBox metricBox = new VBox(5);
        metricBox.getStyleClass().add("quality-metric");
        metricBox.setAlignment(Pos.CENTER);

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("metric-name");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value");

        ProgressBar progressBar = new ProgressBar(Double.parseDouble(value.replace("%", "")) / 100.0);
        progressBar.getStyleClass().add("metric-progress");

        Tooltip tooltip = new Tooltip(description);
        Tooltip.install(metricBox, tooltip);

        metricBox.getChildren().addAll(nameLabel, valueLabel, progressBar);
        grid.add(metricBox, col, row);
    }

    /**
     * 创建质量饼图
     */
    private PieChart createQualityPieChart() {
        PieChart pieChart = new PieChart();
        pieChart.setTitle("数据质量分布");
        pieChart.getStyleClass().add("quality-pie");

        pieChart.setData(FXCollections.observableArrayList(
            new PieChart.Data("优质数据", 85),
            new PieChart.Data("良好数据", 12),
            new PieChart.Data("需要修复", 3)
        ));

        return pieChart;
    }

    /**
     * 更新分布图表
     */
    private void updateDistributionChart(String fieldName) {
        if (fieldName == null || currentData.isEmpty()) return;

        // TODO: 实现动态图表更新
        // 根据选定字段生成分布图表
    }

    /**
     * 应用样式
     */
    private void applyStyles() {
        container.getStylesheets().add("/modern-theme.css");
    }

    // ========== 公共API ==========

    /**
     * 更新数据
     */
    public void updateData(List<Map<String, Object>> data) {
        this.currentData = new ArrayList<>(data);
        analyzeData();
        updateStatCards();
        updateCharts();
    }

    /**
     * 分析数据
     */
    private void analyzeData() {
        if (currentData.isEmpty()) return;

        // 分析每个字段的指标
        Set<String> columns = currentData.get(0).keySet();

        for (String column : columns) {
            DataMetrics metrics = analyzeColumn(column);
            columnMetrics.put(column, metrics);
        }
    }

    /**
     * 分析单个字段
     */
    private DataMetrics analyzeColumn(String columnName) {
        DataMetrics metrics = new DataMetrics();

        List<Object> values = currentData.stream()
            .map(row -> row.get(columnName))
            .collect(Collectors.toList());

        metrics.totalCount = values.size();
        metrics.nullCount = (int) values.stream().filter(Objects::isNull).count();
        metrics.uniqueCount = (int) values.stream().filter(Objects::nonNull).distinct().count();
        metrics.completeness = (double) (metrics.totalCount - metrics.nullCount) / metrics.totalCount * 100;

        return metrics;
    }

    /**
     * 更新统计卡片
     */
    private void updateStatCards() {
        // 添加淡入动画
        for (StatCard card : statCards.values()) {
            FadeTransition fade = new FadeTransition(Duration.millis(300), card);
            fade.setFromValue(0.7);
            fade.setToValue(1.0);
            fade.play();
        }

        // 更新数据
        statCards.get("total").updateValue(String.valueOf(currentData.size()));
        statCards.get("columns").updateValue(String.valueOf(currentData.isEmpty() ? 0 : currentData.get(0).keySet().size()));

        double avgQuality = columnMetrics.values().stream()
            .mapToDouble(m -> m.completeness)
            .average()
            .orElse(0.0);
        statCards.get("quality").updateValue(String.format("%.1f%%", avgQuality));

        // 估算数据大小
        long estimatedSize = currentData.size() * 50L; // 粗略估算
        statCards.get("size").updateValue(formatBytes(estimatedSize));
    }

    /**
     * 更新图表
     */
    private void updateCharts() {
        // TODO: 实现图表数据更新
        // 更新各个图表的数据
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public VBox getContainer() {
        return container;
    }

    // ========== 内部类 ==========

    /**
     * 统计卡片
     */
    public static class StatCard extends VBox {
        private final Label iconLabel;
        private final Label titleLabel;
        private final Label valueLabel;
        private final Label descLabel;

        public StatCard(String icon, String title, String value, String description) {
            this.iconLabel = new Label(icon);
            this.titleLabel = new Label(title);
            this.valueLabel = new Label(value);
            this.descLabel = new Label(description);

            initializeCard();
        }

        private void initializeCard() {
            getStyleClass().add("stat-card");
            setAlignment(Pos.CENTER);
            setSpacing(8);
            setPadding(new Insets(20));

            iconLabel.getStyleClass().add("stat-icon");
            titleLabel.getStyleClass().add("stat-title");
            valueLabel.getStyleClass().add("stat-value");
            descLabel.getStyleClass().add("stat-description");

            descLabel.setWrapText(true);
            descLabel.setMaxWidth(150);

            getChildren().addAll(iconLabel, titleLabel, valueLabel, descLabel);

            // 添加工具提示
            Tooltip tooltip = new Tooltip(descLabel.getText());
            Tooltip.install(this, tooltip);
        }

        public void updateValue(String newValue) {
            valueLabel.setText(newValue);
        }
    }

    /**
     * 数据指标
     */
    public static class DataMetrics {
        public int totalCount;
        public int nullCount;
        public int uniqueCount;
        public double completeness;
        public double mean;
        public double median;
        public double standardDeviation;
    }
}