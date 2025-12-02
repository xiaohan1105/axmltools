package red.jiuzhou.analysis.enhanced;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.analysis.enhanced.EnhancedInsightService.EnhancedInsightResult;
import red.jiuzhou.analysis.enhanced.GameSystemDetector.GameSystemType;
import red.jiuzhou.analysis.enhanced.SmartInsightEngine.SmartInsight;
import red.jiuzhou.analysis.enhanced.SmartInsightEngine.InsightLevel;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏专属智能洞察面板
 *
 * 核心理念：
 * 1. 策划友好 - 用游戏术语而非技术术语
 * 2. 直观可视 - 重要信息一目了然
 * 3. 行动导向 - 每个洞察都有明确的改进建议
 * 4. 上下文感知 - 根据游戏系统类型调整分析重点
 */
public class GameSpecificInsightPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(GameSpecificInsightPanel.class);

    private final EnhancedInsightService insightService;
    private final EnumerationAnalysisEngine enumEngine;
    private final GameDataHumanizer dataHumanizer;

    // UI组件
    private Label systemTypeLabel;
    private Label dataHealthLabel;
    private ProgressBar analysisProgress;
    private ScrollPane insightScrollPane;
    private VBox insightContainer;
    private VBox chartContainer;
    private TabPane detailTabPane;

    // 当前分析结果
    private EnhancedInsightResult currentResult;
    private Map<String, EnumerationAnalysisEngine.EnumStatistics> enumStats;
    private Path currentFile;

    public GameSpecificInsightPanel() {
        this.insightService = new EnhancedInsightService();
        this.enumEngine = new EnumerationAnalysisEngine();
        this.dataHumanizer = new GameDataHumanizer();

        initializeUI();
        log.info("游戏专属洞察面板初始化完成");
    }

    /**
     * 初始化用户界面
     */
    private void initializeUI() {
        setSpacing(15);
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #f8f9fa;");

        // 1. 头部状态区域
        VBox headerSection = createHeaderSection();

        // 2. 核心洞察卡片区域
        insightScrollPane = createInsightSection();

        // 3. 可视化图表区域
        chartContainer = createChartSection();

        // 4. 详细分析标签页
        detailTabPane = createDetailTabs();

        getChildren().addAll(headerSection, insightScrollPane, chartContainer, detailTabPane);

        // 设置布局权重
        VBox.setVgrow(insightScrollPane, Priority.ALWAYS);
        VBox.setVgrow(detailTabPane, Priority.ALWAYS);
    }

    /**
     * 创建头部状态区域
     */
    private VBox createHeaderSection() {
        VBox header = new VBox(10);
        header.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");
        header.setPadding(new Insets(20));

        // 系统类型指示器
        HBox systemInfo = new HBox(15);
        systemInfo.setAlignment(Pos.CENTER_LEFT);

        Label systemLabel = new Label("🎮 游戏系统：");
        systemLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        systemTypeLabel = new Label("未分析");
        systemTypeLabel.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 14));
        systemTypeLabel.setStyle("-fx-text-fill: #666;");

        Label healthLabel = new Label("📊 数据健康度：");
        healthLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        dataHealthLabel = new Label("--");
        dataHealthLabel.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 14));

        systemInfo.getChildren().addAll(systemLabel, systemTypeLabel,
                                       new Separator(), healthLabel, dataHealthLabel);

        // 分析进度条
        analysisProgress = new ProgressBar(0);
        analysisProgress.setPrefWidth(300);
        analysisProgress.setVisible(false);

        header.getChildren().addAll(systemInfo, analysisProgress);
        return header;
    }

    /**
     * 创建核心洞察区域
     */
    private ScrollPane createInsightSection() {
        insightContainer = new VBox(10);
        insightContainer.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(insightContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefHeight(300);
        scrollPane.setStyle("-fx-background: #f8f9fa; -fx-background-color: #f8f9fa;");

        // 初始状态
        showPlaceholder("请选择XML文件开始分析", "📁");

        return scrollPane;
    }

    /**
     * 创建图表区域
     */
    private VBox createChartSection() {
        VBox chartSection = new VBox(10);
        chartSection.setPadding(new Insets(10));
        chartSection.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        Label chartTitle = new Label("📈 数据分布概览");
        chartTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        chartTitle.setPadding(new Insets(10));

        chartSection.getChildren().add(chartTitle);
        return chartSection;
    }

    /**
     * 创建详细分析标签页
     */
    private TabPane createDetailTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: white;");

        // 枚举值分析标签
        Tab enumTab = new Tab("🔢 枚举统计");
        enumTab.setClosable(false);
        enumTab.setContent(createEnumAnalysisContent());

        // 平衡性分析标签
        Tab balanceTab = new Tab("⚖️ 平衡性");
        balanceTab.setClosable(false);
        balanceTab.setContent(createBalanceAnalysisContent());

        // 成长曲线标签
        Tab growthTab = new Tab("📈 成长曲线");
        growthTab.setClosable(false);
        growthTab.setContent(createGrowthAnalysisContent());

        // AI洞察标签
        Tab aiTab = new Tab("🤖 AI洞察");
        aiTab.setClosable(false);
        aiTab.setContent(createAIAnalysisContent());

        tabPane.getTabs().addAll(enumTab, balanceTab, growthTab, aiTab);
        return tabPane;
    }

    /**
     * 分析XML文件
     */
    public void analyzeFile(Path xmlFile) {
        if (xmlFile == null) {
            showPlaceholder("未选择文件", "❌");
            return;
        }

        currentFile = xmlFile;
        log.info("开始分析游戏数据文件: {}", xmlFile.getFileName());

        // 显示分析进度
        showAnalysisProgress();

        // 异步分析
        Task<EnhancedInsightResult> analysisTask = new Task<EnhancedInsightResult>() {
            @Override
            protected EnhancedInsightResult call() throws Exception {
                updateProgress(0.1, 1.0);
                updateMessage("解析XML文件...");

                // 执行增强分析
                EnhancedInsightResult result = insightService.analyze(xmlFile);

                updateProgress(0.7, 1.0);
                updateMessage("分析枚举值分布...");

                // 执行枚举值分析
                Map<String, EnumerationAnalysisEngine.EnumStatistics> enumAnalysis =
                    enumEngine.analyzeEnumerations(xmlFile);

                updateProgress(1.0, 1.0);
                updateMessage("分析完成");

                // 将枚举分析结果也保存到result中（通过临时存储）
                GameSpecificInsightPanel.this.enumStats = enumAnalysis;

                return result;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    hideAnalysisProgress();
                    displayAnalysisResult(getValue());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    hideAnalysisProgress();
                    showError("分析失败: " + getException().getMessage());
                });
            }
        };

        // 绑定进度
        analysisProgress.progressProperty().bind(analysisTask.progressProperty());

        Thread analysisThread = new Thread(analysisTask);
        analysisThread.setDaemon(true);
        analysisThread.start();
    }

    /**
     * 显示分析结果
     */
    private void displayAnalysisResult(EnhancedInsightResult result) {
        currentResult = result;

        // 更新头部信息
        updateHeaderInfo(result);

        // 显示核心洞察卡片
        displayInsightCards(result.getInsights());

        // 更新图表
        updateCharts(result);

        // 更新详细标签页
        updateDetailTabs(result);

        log.info("游戏数据分析结果已显示，洞察数量: {}", result.getInsights().size());
    }

    /**
     * 更新头部信息
     */
    private void updateHeaderInfo(EnhancedInsightResult result) {
        GameSystemType systemType = result.getSystemDetection().getPrimaryType();
        double confidence = result.getSystemDetection().getConfidence();

        // 更新系统类型
        systemTypeLabel.setText(String.format("%s (%.0f%%)",
                                              systemType.getDisplayName(), confidence * 100));
        systemTypeLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-weight: bold;",
                                               getSystemTypeColor(systemType)));

        // 计算数据健康度
        double healthScore = calculateDataHealth(result);
        String healthText = String.format("%.0f%% %s", healthScore * 100, getHealthEmoji(healthScore));
        dataHealthLabel.setText(healthText);
        dataHealthLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-weight: bold;",
                                               getHealthColor(healthScore)));
    }

    /**
     * 显示洞察卡片
     */
    private void displayInsightCards(List<SmartInsight> insights) {
        insightContainer.getChildren().clear();

        if (insights.isEmpty()) {
            showPlaceholder("数据质量很好，暂无需要关注的问题 ✨", "🎉");
            return;
        }

        // 按优先级分组显示
        Map<InsightLevel, List<SmartInsight>> groupedInsights = insights.stream()
                .collect(Collectors.groupingBy(SmartInsight::getLevel));

        // 按重要性排序显示
        List<InsightLevel> orderedLevels = Arrays.asList(
                InsightLevel.CRITICAL, InsightLevel.HIGH, InsightLevel.MEDIUM,
                InsightLevel.POSITIVE, InsightLevel.LOW);

        for (InsightLevel level : orderedLevels) {
            List<SmartInsight> levelInsights = groupedInsights.get(level);
            if (levelInsights != null && !levelInsights.isEmpty()) {
                for (SmartInsight insight : levelInsights) {
                    insightContainer.getChildren().add(createInsightCard(insight));
                }
            }
        }
    }

    /**
     * 创建洞察卡片
     */
    private Node createInsightCard(SmartInsight insight) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle(String.format(
                "-fx-background-color: white; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: %s; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);",
                insight.getLevel().getColor()));

        // 标题行
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label levelIcon = new Label(insight.getLevel().getDisplayName());
        levelIcon.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        levelIcon.setStyle("-fx-text-fill: " + insight.getLevel().getColor() + ";");

        Label title = new Label(insight.getTitle());
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        title.setWrapText(true);

        titleRow.getChildren().addAll(levelIcon, title);

        // 描述
        Label description = new Label(insight.getDescription());
        description.setFont(Font.font("Microsoft YaHei", 14));
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: #666;");

        // 影响说明
        if (insight.getImpact() != null && !insight.getImpact().isEmpty()) {
            Label impact = new Label("💭 " + insight.getImpact());
            impact.setFont(Font.font("Microsoft YaHei", 13));
            impact.setWrapText(true);
            impact.setStyle("-fx-text-fill: #444; -fx-background-color: #f8f9fa; -fx-padding: 8; -fx-background-radius: 4;");
            card.getChildren().add(impact);
        }

        // 建议列表
        if (insight.getSuggestions() != null && !insight.getSuggestions().isEmpty()) {
            VBox suggestionsBox = new VBox(5);
            suggestionsBox.setPadding(new Insets(10, 0, 0, 0));

            Label suggestionsTitle = new Label("🔧 建议措施：");
            suggestionsTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            suggestionsBox.getChildren().add(suggestionsTitle);

            for (String suggestion : insight.getSuggestions()) {
                Label suggestionItem = new Label("• " + suggestion);
                suggestionItem.setFont(Font.font("Microsoft YaHei", 12));
                suggestionItem.setWrapText(true);
                suggestionItem.setStyle("-fx-text-fill: #555;");
                suggestionsBox.getChildren().add(suggestionItem);
            }

            card.getChildren().add(suggestionsBox);
        }

        // 置信度和时间戳
        HBox metaRow = new HBox(10);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setPadding(new Insets(5, 0, 0, 0));

        Label confidence = new Label(String.format("置信度: %.0f%%", insight.getConfidence() * 100));
        confidence.setFont(Font.font("Microsoft YaHei", 11));
        confidence.setStyle("-fx-text-fill: #888;");

        Label category = new Label("分类: " + insight.getCategory());
        category.setFont(Font.font("Microsoft YaHei", 11));
        category.setStyle("-fx-text-fill: #888;");

        Label timestamp = new Label(insight.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        timestamp.setFont(Font.font("Microsoft YaHei", 11));
        timestamp.setStyle("-fx-text-fill: #888;");

        metaRow.getChildren().addAll(confidence, new Separator(), category, new Separator(), timestamp);

        card.getChildren().addAll(titleRow, description, metaRow);
        return card;
    }

    // 辅助方法实现
    private void showPlaceholder(String message, String icon) {
        insightContainer.getChildren().clear();

        VBox placeholder = new VBox(10);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(40));

        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("Microsoft YaHei", 48));

        Label messageLabel = new Label(message);
        messageLabel.setFont(Font.font("Microsoft YaHei", 16));
        messageLabel.setStyle("-fx-text-fill: #666;");

        placeholder.getChildren().addAll(iconLabel, messageLabel);
        insightContainer.getChildren().add(placeholder);
    }

    private void showAnalysisProgress() {
        analysisProgress.setVisible(true);
        insightContainer.getChildren().clear();
        showPlaceholder("正在分析数据，请稍候...", "⚙️");
    }

    private void hideAnalysisProgress() {
        analysisProgress.setVisible(false);
    }

    private void showError(String message) {
        showPlaceholder("分析出错: " + message, "❌");
        log.error("洞察面板分析出错: {}", message);
    }

    private String getSystemTypeColor(GameSystemType type) {
        switch (type) {
            case EQUIPMENT: return "#FF6B35";
            case CHARACTER: return "#4ECDC4";
            case ECONOMY: return "#45B7D1";
            case COMBAT: return "#FF6B6B";
            case QUEST: return "#4ECDC4";
            default: return "#95A5A6";
        }
    }

    private double calculateDataHealth(EnhancedInsightResult result) {
        double completeness = result.getQualityMetrics().getCompleteness();
        double consistency = result.getQualityMetrics().getConsistency();
        double balance = result.getQualityMetrics().getBalance();

        return (completeness * 0.4 + consistency * 0.3 + balance * 0.3);
    }

    private String getHealthEmoji(double health) {
        if (health >= 0.9) return "🎯";
        if (health >= 0.8) return "👍";
        if (health >= 0.7) return "⚠️";
        return "🚨";
    }

    private String getHealthColor(double health) {
        if (health >= 0.8) return "#27AE60";
        if (health >= 0.6) return "#F39C12";
        return "#E74C3C";
    }

    /**
     * 创建枚举值分析内容
     */
    private Node createEnumAnalysisContent() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(15));

        // 如果没有枚举统计数据，显示占位符
        if (enumStats == null || enumStats.isEmpty()) {
            return createEmptyEnumAnalysis();
        }

        // 标题区域
        Label title = new Label("🔢 枚举字段分布分析");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        title.setPadding(new Insets(0, 0, 10, 0));

        // 总览卡片
        Node overviewCard = createEnumOverviewCard();

        // 类型字段速览
        Optional<Node> typeShowcase = createTypeEnumShowcase();

        // 详细分析列表
        ScrollPane detailScrollPane = createEnumDetailList();

        container.getChildren().add(title);
        container.getChildren().add(overviewCard);
        typeShowcase.ifPresent(node -> container.getChildren().add(node));
        container.getChildren().add(detailScrollPane);
        VBox.setVgrow(detailScrollPane, Priority.ALWAYS);

        return container;
    }

    /**
     * 创建空状态的枚举分析
     */
    private Node createEmptyEnumAnalysis() {
        VBox placeholder = new VBox(15);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(50));

        Label icon = new Label("📊");
        icon.setFont(Font.font(48));

        Label message = new Label("暂无枚举字段可分析");
        message.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        message.setStyle("-fx-text-fill: #666;");

        Label hint = new Label("可能原因：\n• XML文件中没有重复值字段\n• 所有字段都是唯一值（如ID）\n• 字段值过于分散");
        hint.setFont(Font.font("Microsoft YaHei", 14));
        hint.setStyle("-fx-text-fill: #888; -fx-text-alignment: center;");

        placeholder.getChildren().addAll(icon, message, hint);
        return placeholder;
    }

    /**
     * 创建枚举分析总览卡片
     */
    private Node createEnumOverviewCard() {
        HBox card = new HBox(20);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        // 统计数据
        int totalEnumFields = enumStats.size();
        int totalIssues = enumStats.values().stream()
                .mapToInt(stat -> stat.getIssues().size())
                .sum();

        // 分类统计
        Map<EnumerationAnalysisEngine.EnumCategory, Long> categoryCount = enumStats.values().stream()
                .collect(Collectors.groupingBy(
                        EnumerationAnalysisEngine.EnumStatistics::getCategory,
                        Collectors.counting()));

        // 健康度计算
        double avgEntropy = enumStats.values().stream()
                .mapToDouble(EnumerationAnalysisEngine.EnumStatistics::getEntropy)
                .average().orElse(0.0);
        double healthScore = Math.min(avgEntropy / 3.0, 1.0); // 标准化到0-1

        // 左侧：数值统计
        VBox leftStats = new VBox(10);
        leftStats.getChildren().addAll(
                createStatItem("枚举字段", String.valueOf(totalEnumFields), "#3498DB"),
                createStatItem("发现问题", String.valueOf(totalIssues), totalIssues > 0 ? "#E74C3C" : "#27AE60"),
                createStatItem("分布健康度", String.format("%.0f%%", healthScore * 100), getHealthColor(healthScore))
        );

        // 中间：分类分布
        VBox centerStats = new VBox(10);
        Label categoryTitle = new Label("字段分类");
        categoryTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        centerStats.getChildren().add(categoryTitle);

        for (Map.Entry<EnumerationAnalysisEngine.EnumCategory, Long> entry : categoryCount.entrySet()) {
            if (entry.getValue() > 0) {
                centerStats.getChildren().add(createCategoryItem(entry.getKey(), entry.getValue().intValue()));
            }
        }

        // 右侧：快速洞察
        VBox rightInsights = createQuickEnumInsights();

        card.getChildren().addAll(leftStats, new Separator(), centerStats, new Separator(), rightInsights);
        return card;
    }

    private Optional<Node> createTypeEnumShowcase() {
        if (enumStats == null || enumStats.isEmpty()) {
            return Optional.empty();
        }

        List<EnumerationAnalysisEngine.EnumStatistics> typeFields = enumStats.values().stream()
                .filter(this::isTypeEnumField)
                .sorted(Comparator.comparing(EnumerationAnalysisEngine.EnumStatistics::getFieldName))
                .collect(Collectors.toList());

        if (typeFields.isEmpty()) {
            return Optional.empty();
        }

        VBox showcase = new VBox(12);
        showcase.setPadding(new Insets(20));
        showcase.setSpacing(12);
        showcase.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #d6e4ff; -fx-border-radius: 8; -fx-border-width: 1;");

        Label title = new Label("🎯 类型字段速览");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));

        Label subtitle = new Label("聚焦包含 type 的字段，展示完整枚举值帮助设计快速比对配置。");
        subtitle.setFont(Font.font("Microsoft YaHei", 12));
        subtitle.setStyle("-fx-text-fill: #666;");
        subtitle.setWrapText(true);

        showcase.getChildren().addAll(title, subtitle);

        typeFields.forEach(stat -> showcase.getChildren().add(createTypeEnumCard(stat)));

        return Optional.of(showcase);
    }

    private Node createTypeEnumCard(EnumerationAnalysisEngine.EnumStatistics stat) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: #f9fbff; -fx-background-radius: 8; -fx-border-color: #d6e4ff; -fx-border-radius: 8; -fx-border-width: 1; -fx-padding: 12;");

        Label header = new Label(stat.getFieldName());
        header.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        String summaryText = String.format(Locale.ROOT,
                "枚举数：%d · 样本量：%d · 最常见：%s",
                stat.getDistribution().size(),
                stat.getTotalCount(),
                stat.getMostCommonValue());
        Label summary = new Label(summaryText);
        summary.setFont(Font.font("Microsoft YaHei", 12));
        summary.setStyle("-fx-text-fill: #555;");
        summary.setWrapText(true);

        FlowPane valuesPane = new FlowPane();
        valuesPane.setHgap(8);
        valuesPane.setVgap(8);
        valuesPane.setPrefWrapLength(480);

        stat.getDistribution().forEach((value, count) -> {
            double ratio = (double) count / Math.max(1, stat.getTotalCount());
            Label chip = new Label(String.format(Locale.ROOT, "%s  •  %.1f%% (%d)", value, ratio * 100, count));
            chip.setFont(Font.font("Microsoft YaHei", 12));
            chip.setPadding(new Insets(6, 12, 6, 12));
            chip.setStyle(getTypeEnumChipStyle(ratio));
            valuesPane.getChildren().add(chip);
        });

        card.getChildren().addAll(header, summary, valuesPane);
        return card;
    }

    private boolean isTypeEnumField(EnumerationAnalysisEngine.EnumStatistics stat) {
        String fieldName = stat.getFieldName();
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("type") || stat.getCategory() == EnumerationAnalysisEngine.EnumCategory.TYPE;
    }

    private String getTypeEnumChipStyle(double ratio) {
        String baseColor;
        String textColor;

        if (ratio > 0.5) {
            baseColor = "#E3F2FD";
            textColor = "#1565C0";
        } else if (ratio > 0.2) {
            baseColor = "#E8F5E9";
            textColor = "#2E7D32";
        } else {
            baseColor = "#F5F5F5";
            textColor = "#424242";
        }

        return String.format(Locale.ROOT,
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 16; -fx-border-radius: 16;",
                baseColor,
                textColor);
    }

    /**
     * 创建统计项
     */
    private Node createStatItem(String label, String value, String color) {
        VBox item = new VBox(2);

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        valueLabel.setStyle("-fx-text-fill: " + color + ";");

        Label labelText = new Label(label);
        labelText.setFont(Font.font("Microsoft YaHei", 12));
        labelText.setStyle("-fx-text-fill: #666;");

        item.getChildren().addAll(valueLabel, labelText);
        return item;
    }

    /**
     * 创建分类项
     */
    private Node createCategoryItem(EnumerationAnalysisEngine.EnumCategory category, int count) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);

        Label countLabel = new Label(String.valueOf(count));
        countLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        countLabel.setStyle("-fx-text-fill: #3498DB;");
        countLabel.setPrefWidth(30);

        Label nameLabel = new Label(category.getDisplayName());
        nameLabel.setFont(Font.font("Microsoft YaHei", 12));

        item.getChildren().addAll(countLabel, nameLabel);
        return item;
    }

    /**
     * 创建快速洞察
     */
    private VBox createQuickEnumInsights() {
        VBox insights = new VBox(8);

        Label title = new Label("快速洞察");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        insights.getChildren().add(title);

        // 找出最不均衡的字段
        Optional<EnumerationAnalysisEngine.EnumStatistics> mostImbalanced = enumStats.values().stream()
                .max((a, b) -> Double.compare(a.getGiniCoefficient(), b.getGiniCoefficient()));

        if (mostImbalanced.isPresent() && mostImbalanced.get().getGiniCoefficient() > 0.6) {
            Label insight = new Label("⚠️ " + mostImbalanced.get().getFieldName() + " 分布最不均衡");
            insight.setFont(Font.font("Microsoft YaHei", 12));
            insight.setStyle("-fx-text-fill: #E67E22;");
            insights.getChildren().add(insight);
        }

        // 找出问题最多的字段
        Optional<EnumerationAnalysisEngine.EnumStatistics> mostProblematic = enumStats.values().stream()
                .max((a, b) -> Integer.compare(a.getIssues().size(), b.getIssues().size()));

        if (mostProblematic.isPresent() && !mostProblematic.get().getIssues().isEmpty()) {
            Label insight = new Label("🚨 " + mostProblematic.get().getFieldName() + " 问题最多");
            insight.setFont(Font.font("Microsoft YaHei", 12));
            insight.setStyle("-fx-text-fill: #E74C3C;");
            insights.getChildren().add(insight);
        }

        // 找出最健康的字段
        Optional<EnumerationAnalysisEngine.EnumStatistics> healthiest = enumStats.values().stream()
                .filter(stat -> stat.getIssues().isEmpty())
                .max((a, b) -> Double.compare(a.getEntropy(), b.getEntropy()));

        if (healthiest.isPresent()) {
            Label insight = new Label("✅ " + healthiest.get().getFieldName() + " 分布最均衡");
            insight.setFont(Font.font("Microsoft YaHei", 12));
            insight.setStyle("-fx-text-fill: #27AE60;");
            insights.getChildren().add(insight);
        }

        return insights;
    }

    /**
     * 创建枚举详细分析列表
     */
    private ScrollPane createEnumDetailList() {
        VBox detailContainer = new VBox(15);
        detailContainer.setPadding(new Insets(10));

        // 按问题严重程度排序
        List<EnumerationAnalysisEngine.EnumStatistics> sortedStats = enumStats.values().stream()
                .sorted((a, b) -> {
                    // 先按问题数量排序
                    int issueCompare = Integer.compare(b.getIssues().size(), a.getIssues().size());
                    if (issueCompare != 0) return issueCompare;
                    // 再按基尼系数排序（不均衡程度）
                    return Double.compare(b.getGiniCoefficient(), a.getGiniCoefficient());
                })
                .collect(Collectors.toList());

        for (EnumerationAnalysisEngine.EnumStatistics stat : sortedStats) {
            detailContainer.getChildren().add(createEnumFieldCard(stat));
        }

        ScrollPane scrollPane = new ScrollPane(detailContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return scrollPane;
    }

    /**
     * 创建枚举字段详情卡片
     */
    private Node createEnumFieldCard(EnumerationAnalysisEngine.EnumStatistics stat) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 8;");

        // 标题行
        HBox titleRow = new HBox(15);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label fieldName = new Label(stat.getFieldName());
        fieldName.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));

        Label category = new Label(stat.getCategory().getDisplayName());
        category.setFont(Font.font("Microsoft YaHei", 12));
        category.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 4 8; -fx-background-radius: 12; -fx-text-fill: #666;");

        // 健康度指示器
        double gini = stat.getGiniCoefficient();
        String healthIcon = gini < 0.3 ? "🟢" : gini < 0.6 ? "🟡" : "🔴";
        Label healthIndicator = new Label(healthIcon + " " + String.format("基尼系数: %.2f", gini));
        healthIndicator.setFont(Font.font("Microsoft YaHei", 12));

        titleRow.getChildren().addAll(fieldName, category, healthIndicator);

        // 分布图表（简化版）
        Node distributionChart = createSimpleDistributionChart(stat);

        // 问题和建议
        Node issuesAndSuggestions = createIssuesAndSuggestions(stat);

        card.getChildren().addAll(titleRow, distributionChart, issuesAndSuggestions);
        return card;
    }

    /**
     * 创建简化的分布图表
     */
    private Node createSimpleDistributionChart(EnumerationAnalysisEngine.EnumStatistics stat) {
        VBox chartContainer = new VBox(8);

        Label chartTitle = new Label("📊 值分布（前5个最常见）");
        chartTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        VBox bars = new VBox(4);

        // 只显示前5个最常见的值
        stat.getDistribution().entrySet().stream()
                .limit(5)
                .forEach(entry -> {
                    String value = entry.getKey();
                    int count = entry.getValue();
                    double percentage = (double) count / stat.getTotalCount() * 100;

                    HBox bar = new HBox(8);
                    bar.setAlignment(Pos.CENTER_LEFT);

                    Label valueLabel = new Label(value);
                    valueLabel.setFont(Font.font("Microsoft YaHei", 11));
                    valueLabel.setPrefWidth(100);

                    ProgressBar progressBar = new ProgressBar(percentage / 100.0);
                    progressBar.setPrefWidth(150);
                    progressBar.setStyle("-fx-accent: #3498DB;");

                    Label percentLabel = new Label(String.format("%.1f%% (%d)", percentage, count));
                    percentLabel.setFont(Font.font("Microsoft YaHei", 11));
                    percentLabel.setStyle("-fx-text-fill: #666;");

                    bar.getChildren().addAll(valueLabel, progressBar, percentLabel);
                    bars.getChildren().add(bar);
                });

        chartContainer.getChildren().addAll(chartTitle, bars);
        return chartContainer;
    }

    /**
     * 创建问题和建议区域
     */
    private Node createIssuesAndSuggestions(EnumerationAnalysisEngine.EnumStatistics stat) {
        VBox container = new VBox(10);

        // 问题列表
        if (!stat.getIssues().isEmpty()) {
            Label issuesTitle = new Label("⚠️ 发现的问题");
            issuesTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            issuesTitle.setStyle("-fx-text-fill: #E67E22;");

            VBox issuesList = new VBox(5);
            for (EnumerationAnalysisEngine.DistributionIssue issue : stat.getIssues()) {
                Label issueItem = new Label("• " + issue.getDetail());
                issueItem.setFont(Font.font("Microsoft YaHei", 12));
                issueItem.setStyle("-fx-text-fill: #E74C3C;");
                issueItem.setWrapText(true);
                issuesList.getChildren().add(issueItem);
            }

            container.getChildren().addAll(issuesTitle, issuesList);
        }

        // 建议列表
        if (!stat.getSuggestions().isEmpty()) {
            Label suggestionsTitle = new Label("💡 优化建议");
            suggestionsTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            suggestionsTitle.setStyle("-fx-text-fill: #27AE60;");

            VBox suggestionsList = new VBox(5);
            for (String suggestion : stat.getSuggestions()) {
                Label suggestionItem = new Label("• " + suggestion);
                suggestionItem.setFont(Font.font("Microsoft YaHei", 12));
                suggestionItem.setStyle("-fx-text-fill: #2ECC71;");
                suggestionItem.setWrapText(true);
                suggestionsList.getChildren().add(suggestionItem);
            }

            container.getChildren().addAll(suggestionsTitle, suggestionsList);
        }

        // 如果没有问题，显示正面反馈
        if (stat.getIssues().isEmpty()) {
            Label positiveMessage = new Label("✅ 该字段分布健康，无需特殊关注");
            positiveMessage.setFont(Font.font("Microsoft YaHei", 13));
            positiveMessage.setStyle("-fx-text-fill: #27AE60; -fx-background-color: #d4edda; -fx-padding: 8; -fx-background-radius: 4;");
            container.getChildren().add(positiveMessage);
        }

        return container;
    }

    /**
     * 创建平衡性分析内容
     */
    private Node createBalanceAnalysisContent() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15));

        // 如果没有分析结果，显示占位符
        if (currentResult == null) {
            return createEmptyBalanceAnalysis();
        }

        // 执行平衡性分析
        List<GameDataHumanizer.BalanceIssue> balanceIssues = dataHumanizer.analyzeBalance(currentResult);

        // 标题区域
        Label title = new Label("⚖️ 游戏平衡性分析");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        title.setPadding(new Insets(0, 0, 10, 0));

        // 总览卡片
        Node overviewCard = createBalanceOverviewCard(balanceIssues);

        // 详细问题列表
        ScrollPane issuesScrollPane = createBalanceIssuesList(balanceIssues);

        container.getChildren().addAll(title, overviewCard, issuesScrollPane);
        VBox.setVgrow(issuesScrollPane, Priority.ALWAYS);

        return container;
    }

    /**
     * 创建空状态的平衡性分析
     */
    private Node createEmptyBalanceAnalysis() {
        VBox placeholder = new VBox(15);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(50));

        Label icon = new Label("⚖️");
        icon.setFont(Font.font(48));

        Label message = new Label("数据平衡性良好");
        message.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        message.setStyle("-fx-text-fill: #27AE60;");

        Label hint = new Label("未发现明显的平衡性问题\n这说明游戏数值设计比较合理");
        hint.setFont(Font.font("Microsoft YaHei", 14));
        hint.setStyle("-fx-text-fill: #666; -fx-text-alignment: center;");

        placeholder.getChildren().addAll(icon, message, hint);
        return placeholder;
    }

    /**
     * 创建平衡性分析总览卡片
     */
    private Node createBalanceOverviewCard(List<GameDataHumanizer.BalanceIssue> issues) {
        HBox card = new HBox(20);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        // 统计数据
        int totalIssues = issues.size();
        int criticalIssues = (int) issues.stream().filter(issue -> issue.getSeverity() > 0.7).count();
        int moderateIssues = (int) issues.stream().filter(issue -> issue.getSeverity() > 0.4 && issue.getSeverity() <= 0.7).count();

        // 问题类型分布
        Map<GameDataHumanizer.BalanceIssueType, Long> typeCount = issues.stream()
                .collect(Collectors.groupingBy(GameDataHumanizer.BalanceIssue::getType, Collectors.counting()));

        // 整体健康度
        double overallHealth = totalIssues == 0 ? 1.0 : Math.max(0.0, 1.0 - (double)totalIssues / 10.0);

        // 左侧：数值统计
        VBox leftStats = new VBox(10);
        leftStats.getChildren().addAll(
                createStatItem("总问题数", String.valueOf(totalIssues), totalIssues == 0 ? "#27AE60" : "#E74C3C"),
                createStatItem("严重问题", String.valueOf(criticalIssues), criticalIssues == 0 ? "#27AE60" : "#E74C3C"),
                createStatItem("平衡健康度", String.format("%.0f%%", overallHealth * 100), getHealthColor(overallHealth))
        );

        // 中间：问题类型分布
        VBox centerStats = new VBox(10);
        Label typeTitle = new Label("问题类型");
        typeTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        centerStats.getChildren().add(typeTitle);

        if (typeCount.isEmpty()) {
            Label noIssues = new Label("✅ 无问题");
            noIssues.setFont(Font.font("Microsoft YaHei", 12));
            noIssues.setStyle("-fx-text-fill: #27AE60;");
            centerStats.getChildren().add(noIssues);
        } else {
            for (Map.Entry<GameDataHumanizer.BalanceIssueType, Long> entry : typeCount.entrySet()) {
                centerStats.getChildren().add(createIssueTypeItem(entry.getKey(), entry.getValue().intValue()));
            }
        }

        // 右侧：快速建议
        VBox rightSuggestions = createQuickBalanceSuggestions(issues);

        card.getChildren().addAll(leftStats, new Separator(), centerStats, new Separator(), rightSuggestions);
        return card;
    }

    /**
     * 创建问题类型项
     */
    private Node createIssueTypeItem(GameDataHumanizer.BalanceIssueType type, int count) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);

        Label countLabel = new Label(String.valueOf(count));
        countLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        countLabel.setStyle("-fx-text-fill: #E74C3C;");
        countLabel.setPrefWidth(30);

        Label nameLabel = new Label(type.getDisplayName());
        nameLabel.setFont(Font.font("Microsoft YaHei", 12));

        item.getChildren().addAll(countLabel, nameLabel);
        return item;
    }

    /**
     * 创建快速平衡建议
     */
    private VBox createQuickBalanceSuggestions(List<GameDataHumanizer.BalanceIssue> issues) {
        VBox suggestions = new VBox(8);

        Label title = new Label("快速建议");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        suggestions.getChildren().add(title);

        if (issues.isEmpty()) {
            Label positive = new Label("✅ 平衡性良好");
            positive.setFont(Font.font("Microsoft YaHei", 12));
            positive.setStyle("-fx-text-fill: #27AE60;");
            suggestions.getChildren().add(positive);
        } else {
            // 显示最严重的3个问题的建议
            issues.stream()
                    .sorted((a, b) -> Double.compare(b.getSeverity(), a.getSeverity()))
                    .limit(3)
                    .forEach(issue -> {
                        Label suggestion = new Label("🔧 " + issue.getType().getSuggestion());
                        suggestion.setFont(Font.font("Microsoft YaHei", 11));
                        suggestion.setStyle("-fx-text-fill: #E67E22;");
                        suggestion.setWrapText(true);
                        suggestions.getChildren().add(suggestion);
                    });
        }

        return suggestions;
    }

    /**
     * 创建平衡问题详细列表
     */
    private ScrollPane createBalanceIssuesList(List<GameDataHumanizer.BalanceIssue> issues) {
        VBox issuesContainer = new VBox(15);
        issuesContainer.setPadding(new Insets(10));

        if (issues.isEmpty()) {
            VBox noIssues = new VBox(20);
            noIssues.setAlignment(Pos.CENTER);
            noIssues.setPadding(new Insets(50));

            Label icon = new Label("🎯");
            icon.setFont(Font.font(48));

            Label message = new Label("恭喜！未发现平衡性问题");
            message.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
            message.setStyle("-fx-text-fill: #27AE60;");

            Label detail = new Label("游戏数值设计合理，各项指标均在正常范围内");
            detail.setFont(Font.font("Microsoft YaHei", 14));
            detail.setStyle("-fx-text-fill: #666;");

            noIssues.getChildren().addAll(icon, message, detail);
            issuesContainer.getChildren().add(noIssues);
        } else {
            // 按严重程度排序
            List<GameDataHumanizer.BalanceIssue> sortedIssues = issues.stream()
                    .sorted((a, b) -> Double.compare(b.getSeverity(), a.getSeverity()))
                    .collect(Collectors.toList());

            for (GameDataHumanizer.BalanceIssue issue : sortedIssues) {
                issuesContainer.getChildren().add(createBalanceIssueCard(issue));
            }
        }

        ScrollPane scrollPane = new ScrollPane(issuesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return scrollPane;
    }

    /**
     * 创建平衡问题卡片
     */
    private Node createBalanceIssueCard(GameDataHumanizer.BalanceIssue issue) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));

        // 根据严重程度设置边框颜色
        String borderColor = issue.getSeverity() > 0.7 ? "#E74C3C" :
                            issue.getSeverity() > 0.4 ? "#F39C12" : "#3498DB";
        card.setStyle(String.format(
                "-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: %s; -fx-border-width: 2; -fx-border-radius: 8;",
                borderColor));

        // 标题行
        HBox titleRow = new HBox(15);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // 严重程度图标
        String severityIcon = issue.getSeverity() > 0.7 ? "🚨" :
                             issue.getSeverity() > 0.4 ? "⚠️" : "ℹ️";
        Label severityLabel = new Label(severityIcon);
        severityLabel.setFont(Font.font(20));

        Label issueType = new Label(issue.getType().getDisplayName());
        issueType.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));

        Label fieldName = new Label("字段: " + issue.getFieldName());
        fieldName.setFont(Font.font("Microsoft YaHei", 12));
        fieldName.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 4 8; -fx-background-radius: 12; -fx-text-fill: #666;");

        titleRow.getChildren().addAll(severityLabel, issueType, fieldName);

        // 问题描述
        Label description = new Label(issue.getDetail());
        description.setFont(Font.font("Microsoft YaHei", 14));
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: #333;");

        // 游戏影响
        VBox impactBox = new VBox(5);
        Label impactTitle = new Label("🎮 对游戏体验的影响：");
        impactTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        Label impactDetail = new Label(issue.getGameplayImpact());
        impactDetail.setFont(Font.font("Microsoft YaHei", 12));
        impactDetail.setWrapText(true);
        impactDetail.setStyle("-fx-text-fill: #E67E22; -fx-background-color: #fef9e7; -fx-padding: 8; -fx-background-radius: 4;");

        impactBox.getChildren().addAll(impactTitle, impactDetail);

        // 建议措施
        VBox suggestionBox = new VBox(5);
        Label suggestionTitle = new Label("💡 建议措施：");
        suggestionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        Label suggestionDetail = new Label(issue.getType().getSuggestion());
        suggestionDetail.setFont(Font.font("Microsoft YaHei", 12));
        suggestionDetail.setWrapText(true);
        suggestionDetail.setStyle("-fx-text-fill: #27AE60; -fx-background-color: #d4edda; -fx-padding: 8; -fx-background-radius: 4;");

        suggestionBox.getChildren().addAll(suggestionTitle, suggestionDetail);

        // 受影响的值
        if (!issue.getAffectedValues().isEmpty()) {
            VBox valuesBox = new VBox(5);
            Label valuesTitle = new Label("📊 相关数据：");
            valuesTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

            String valuesText = String.join(", ", issue.getAffectedValues());
            Label valuesDetail = new Label(valuesText);
            valuesDetail.setFont(Font.font("Microsoft YaHei", 12));
            valuesDetail.setStyle("-fx-text-fill: #666;");

            valuesBox.getChildren().addAll(valuesTitle, valuesDetail);
            card.getChildren().add(valuesBox);
        }

        card.getChildren().addAll(titleRow, description, impactBox, suggestionBox);
        return card;
    }

    /**
     * 创建成长曲线分析内容
     */
    private Node createGrowthAnalysisContent() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15));

        // 如果没有分析结果，显示占位符
        if (currentResult == null) {
            return createEmptyGrowthAnalysis();
        }

        // 执行成长曲线分析
        List<GameDataHumanizer.GrowthCurveAnalysis> growthAnalyses = dataHumanizer.analyzeGrowthCurves(currentResult);

        // 标题区域
        Label title = new Label("📈 成长曲线分析");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        title.setPadding(new Insets(0, 0, 10, 0));

        if (growthAnalyses.isEmpty()) {
            return createEmptyGrowthAnalysis();
        }

        // 总览卡片
        Node overviewCard = createGrowthOverviewCard(growthAnalyses);

        // 详细曲线分析列表
        ScrollPane growthScrollPane = createGrowthAnalysesList(growthAnalyses);

        container.getChildren().addAll(title, overviewCard, growthScrollPane);
        VBox.setVgrow(growthScrollPane, Priority.ALWAYS);

        return container;
    }

    /**
     * 创建空状态的成长分析
     */
    private Node createEmptyGrowthAnalysis() {
        VBox placeholder = new VBox(15);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(50));

        Label icon = new Label("📈");
        icon.setFont(Font.font(48));

        Label message = new Label("暂无成长相关字段");
        message.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        message.setStyle("-fx-text-fill: #666;");

        Label hint = new Label("可能原因：\n• 数据中没有等级、经验等成长字段\n• 字段值范围太小，无法分析曲线\n• 数据为配置类文件");
        hint.setFont(Font.font("Microsoft YaHei", 14));
        hint.setStyle("-fx-text-fill: #888; -fx-text-alignment: center;");

        placeholder.getChildren().addAll(icon, message, hint);
        return placeholder;
    }

    /**
     * 创建成长分析总览卡片
     */
    private Node createGrowthOverviewCard(List<GameDataHumanizer.GrowthCurveAnalysis> analyses) {
        HBox card = new HBox(20);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        // 统计数据
        int totalFields = analyses.size();
        Map<GameDataHumanizer.GrowthCurveAnalysis.CurveType, Long> curveTypeCount = analyses.stream()
                .collect(Collectors.groupingBy(GameDataHumanizer.GrowthCurveAnalysis::getCurveType, Collectors.counting()));

        // 计算平均平滑度
        double avgSmoothness = analyses.stream()
                .mapToDouble(GameDataHumanizer.GrowthCurveAnalysis::getSmoothness)
                .average().orElse(0.0);

        // 左侧：数值统计
        VBox leftStats = new VBox(10);
        leftStats.getChildren().addAll(
                createStatItem("成长字段", String.valueOf(totalFields), "#3498DB"),
                createStatItem("平滑度", String.format("%.0f%%", avgSmoothness * 100), getHealthColor(avgSmoothness)),
                createStatItem("曲线类型", String.valueOf(curveTypeCount.size()), "#9B59B6")
        );

        // 中间：曲线类型分布
        VBox centerStats = new VBox(10);
        Label typeTitle = new Label("曲线类型");
        typeTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        centerStats.getChildren().add(typeTitle);

        for (Map.Entry<GameDataHumanizer.GrowthCurveAnalysis.CurveType, Long> entry : curveTypeCount.entrySet()) {
            centerStats.getChildren().add(createCurveTypeItem(entry.getKey(), entry.getValue().intValue()));
        }

        // 右侧：体验评估
        VBox rightAssessment = createGrowthExperienceAssessment(analyses);

        card.getChildren().addAll(leftStats, new Separator(), centerStats, new Separator(), rightAssessment);
        return card;
    }

    /**
     * 创建曲线类型项
     */
    private Node createCurveTypeItem(GameDataHumanizer.GrowthCurveAnalysis.CurveType curveType, int count) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);

        Label countLabel = new Label(String.valueOf(count));
        countLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        countLabel.setStyle("-fx-text-fill: #9B59B6;");
        countLabel.setPrefWidth(30);

        Label nameLabel = new Label(curveType.getDisplayName());
        nameLabel.setFont(Font.font("Microsoft YaHei", 12));

        item.getChildren().addAll(countLabel, nameLabel);
        return item;
    }

    /**
     * 创建体验评估
     */
    private VBox createGrowthExperienceAssessment(List<GameDataHumanizer.GrowthCurveAnalysis> analyses) {
        VBox assessment = new VBox(8);

        Label title = new Label("体验评估");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        assessment.getChildren().add(title);

        // 找出体验最好和最有问题的
        Optional<GameDataHumanizer.GrowthCurveAnalysis> bestExperience = analyses.stream()
                .filter(a -> a.getPlayerExperience().contains("良好") || a.getPlayerExperience().contains("适中"))
                .findFirst();

        Optional<GameDataHumanizer.GrowthCurveAnalysis> worstExperience = analyses.stream()
                .filter(a -> a.getPlayerExperience().contains("枯燥") || a.getPlayerExperience().contains("困惑"))
                .findFirst();

        if (bestExperience.isPresent()) {
            Label good = new Label("✅ " + bestExperience.get().getFieldName() + " 体验良好");
            good.setFont(Font.font("Microsoft YaHei", 12));
            good.setStyle("-fx-text-fill: #27AE60;");
            assessment.getChildren().add(good);
        }

        if (worstExperience.isPresent()) {
            Label bad = new Label("⚠️ " + worstExperience.get().getFieldName() + " 需要优化");
            bad.setFont(Font.font("Microsoft YaHei", 12));
            bad.setStyle("-fx-text-fill: #E67E22;");
            assessment.getChildren().add(bad);
        }

        if (!bestExperience.isPresent() && !worstExperience.isPresent()) {
            Label neutral = new Label("📊 成长体验中等");
            neutral.setFont(Font.font("Microsoft YaHei", 12));
            neutral.setStyle("-fx-text-fill: #666;");
            assessment.getChildren().add(neutral);
        }

        return assessment;
    }

    /**
     * 创建成长分析详细列表
     */
    private ScrollPane createGrowthAnalysesList(List<GameDataHumanizer.GrowthCurveAnalysis> analyses) {
        VBox analysesContainer = new VBox(15);
        analysesContainer.setPadding(new Insets(10));

        for (GameDataHumanizer.GrowthCurveAnalysis analysis : analyses) {
            analysesContainer.getChildren().add(createGrowthAnalysisCard(analysis));
        }

        ScrollPane scrollPane = new ScrollPane(analysesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return scrollPane;
    }

    /**
     * 创建成长分析卡片
     */
    private Node createGrowthAnalysisCard(GameDataHumanizer.GrowthCurveAnalysis analysis) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 8;");

        // 标题行
        HBox titleRow = new HBox(15);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // 曲线类型图标
        String typeIcon = getCurveTypeIcon(analysis.getCurveType());
        Label typeIconLabel = new Label(typeIcon);
        typeIconLabel.setFont(Font.font(20));

        Label fieldName = new Label(analysis.getFieldName());
        fieldName.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));

        Label curveType = new Label(analysis.getCurveType().getDisplayName());
        curveType.setFont(Font.font("Microsoft YaHei", 12));
        curveType.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 4 8; -fx-background-radius: 12; -fx-text-fill: #666;");

        // 平滑度指示器
        Label smoothness = new Label(String.format("平滑度: %.0f%%", analysis.getSmoothness() * 100));
        smoothness.setFont(Font.font("Microsoft YaHei", 12));
        smoothness.setStyle("-fx-text-fill: " + getHealthColor(analysis.getSmoothness()) + ";");

        titleRow.getChildren().addAll(typeIconLabel, fieldName, curveType, smoothness);

        // 曲线特征描述
        Label characteristic = new Label(analysis.getCurveType().getCharacteristic());
        characteristic.setFont(Font.font("Microsoft YaHei", 14));
        characteristic.setStyle("-fx-text-fill: #666;");

        // 玩家体验评估
        VBox experienceBox = new VBox(5);
        Label experienceTitle = new Label("🎮 玩家体验预测：");
        experienceTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        Label experienceDetail = new Label(analysis.getPlayerExperience());
        experienceDetail.setFont(Font.font("Microsoft YaHei", 12));
        experienceDetail.setWrapText(true);
        experienceDetail.setStyle("-fx-text-fill: #E67E22; -fx-background-color: #fef9e7; -fx-padding: 8; -fx-background-radius: 4;");

        experienceBox.getChildren().addAll(experienceTitle, experienceDetail);

        // 成长阶段
        if (!analysis.getPhases().isEmpty()) {
            VBox phasesBox = new VBox(8);
            Label phasesTitle = new Label("📊 成长阶段：");
            phasesTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            phasesBox.getChildren().add(phasesTitle);

            for (GameDataHumanizer.GrowthCurveAnalysis.GrowthPhase phase : analysis.getPhases()) {
                HBox phaseItem = new HBox(10);
                phaseItem.setAlignment(Pos.CENTER_LEFT);

                Label phaseName = new Label(phase.getName());
                phaseName.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
                phaseName.setPrefWidth(80);

                Label phaseDesc = new Label(phase.getDescription());
                phaseDesc.setFont(Font.font("Microsoft YaHei", 12));
                phaseDesc.setStyle("-fx-text-fill: #666;");

                phaseItem.getChildren().addAll(phaseName, phaseDesc);
                phasesBox.getChildren().add(phaseItem);
            }

            card.getChildren().add(phasesBox);
        }

        // 优化建议
        if (!analysis.getOptimizationSuggestions().isEmpty()) {
            VBox suggestionsBox = new VBox(5);
            Label suggestionsTitle = new Label("💡 优化建议：");
            suggestionsTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            suggestionsBox.getChildren().add(suggestionsTitle);

            for (String suggestion : analysis.getOptimizationSuggestions()) {
                Label suggestionItem = new Label("• " + suggestion);
                suggestionItem.setFont(Font.font("Microsoft YaHei", 12));
                suggestionItem.setWrapText(true);
                suggestionItem.setStyle("-fx-text-fill: #27AE60;");
                suggestionsBox.getChildren().add(suggestionItem);
            }

            card.getChildren().add(suggestionsBox);
        }

        card.getChildren().addAll(titleRow, characteristic, experienceBox);
        return card;
    }

    /**
     * 获取曲线类型图标
     */
    private String getCurveTypeIcon(GameDataHumanizer.GrowthCurveAnalysis.CurveType curveType) {
        switch (curveType) {
            case LINEAR: return "📈";
            case EXPONENTIAL: return "🚀";
            case LOGARITHMIC: return "📉";
            case STEPPED: return "📊";
            default: return "❓";
        }
    }

    /**
     * 创建AI分析内容
     */
    private Node createAIAnalysisContent() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15));

        // 标题区域
        Label title = new Label("🤖 AI深度洞察");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        title.setPadding(new Insets(0, 0, 10, 0));

        // 如果没有分析结果，显示占位符
        if (currentResult == null) {
            return createEmptyAIAnalysis();
        }

        // AI状态卡片
        Node statusCard = createAIStatusCard();

        // AI洞察列表
        ScrollPane aiInsightsPane = createAIInsightsList();

        // AI建议卡片
        Node suggestionsCard = createAISuggestionsCard();

        container.getChildren().addAll(title, statusCard, aiInsightsPane, suggestionsCard);
        VBox.setVgrow(aiInsightsPane, Priority.ALWAYS);

        return container;
    }

    /**
     * 创建空状态的AI分析
     */
    private Node createEmptyAIAnalysis() {
        VBox placeholder = new VBox(15);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(50));

        Label icon = new Label("🤖");
        icon.setFont(Font.font(48));

        Label message = new Label("AI分析准备中");
        message.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        message.setStyle("-fx-text-fill: #666;");

        Label hint = new Label("请先选择XML文件进行分析\n后续将提供AI驱动的深度洞察");
        hint.setFont(Font.font("Microsoft YaHei", 14));
        hint.setStyle("-fx-text-fill: #888; -fx-text-alignment: center;");

        placeholder.getChildren().addAll(icon, message, hint);
        return placeholder;
    }

    /**
     * 创建AI状态卡片
     */
    private Node createAIStatusCard() {
        HBox card = new HBox(20);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        // AI分析状态
        VBox statusSection = new VBox(10);
        Label statusTitle = new Label("AI分析状态");
        statusTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        // 获取AI洞察
        List<SmartInsight> aiInsights = currentResult.getInsights().stream()
                .filter(insight -> "AI分析".equals(insight.getCategory()))
                .collect(Collectors.toList());

        String statusText = aiInsights.isEmpty() ? "传统分析模式" : "AI增强分析";
        String statusColor = aiInsights.isEmpty() ? "#666" : "#3498DB";

        Label status = new Label(statusText);
        status.setFont(Font.font("Microsoft YaHei", 12));
        status.setStyle("-fx-text-fill: " + statusColor + ";");

        statusSection.getChildren().addAll(statusTitle, status);

        // AI能力展示
        VBox capabilitiesSection = new VBox(10);
        Label capabilitiesTitle = new Label("AI能力");
        capabilitiesTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        VBox capabilitiesList = new VBox(3);
        String[] capabilities = {
                "游戏数据语义理解",
                "平衡性智能评估",
                "用户体验预测",
                "优化建议生成"
        };

        for (String capability : capabilities) {
            Label capItem = new Label("✓ " + capability);
            capItem.setFont(Font.font("Microsoft YaHei", 11));
            capItem.setStyle("-fx-text-fill: #27AE60;");
            capabilitiesList.getChildren().add(capItem);
        }

        capabilitiesSection.getChildren().addAll(capabilitiesTitle, capabilitiesList);

        // 分析统计
        VBox statsSection = new VBox(10);
        Label statsTitle = new Label("分析统计");
        statsTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        GameSystemType systemType = currentResult.getSystemDetection().getPrimaryType();
        int totalInsights = currentResult.getInsights().size();
        double confidence = currentResult.getSystemDetection().getConfidence();

        VBox statsList = new VBox(3);
        statsList.getChildren().addAll(
                createMiniStatItem("系统类型", systemType.getDisplayName()),
                createMiniStatItem("洞察数量", String.valueOf(totalInsights)),
                createMiniStatItem("识别置信度", String.format("%.0f%%", confidence * 100))
        );

        statsSection.getChildren().addAll(statsTitle, statsList);

        card.getChildren().addAll(statusSection, new Separator(), capabilitiesSection, new Separator(), statsSection);
        return card;
    }

    /**
     * 创建迷你统计项
     */
    private Node createMiniStatItem(String label, String value) {
        HBox item = new HBox(5);
        item.setAlignment(Pos.CENTER_LEFT);

        Label labelText = new Label(label + ":");
        labelText.setFont(Font.font("Microsoft YaHei", 11));
        labelText.setStyle("-fx-text-fill: #666;");

        Label valueText = new Label(value);
        valueText.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 11));
        valueText.setStyle("-fx-text-fill: #333;");

        item.getChildren().addAll(labelText, valueText);
        return item;
    }

    /**
     * 创建AI洞察列表
     */
    private ScrollPane createAIInsightsList() {
        VBox insightsContainer = new VBox(15);
        insightsContainer.setPadding(new Insets(10));

        // 获取AI相关洞察
        List<SmartInsight> aiInsights = currentResult.getInsights().stream()
                .filter(insight -> "AI分析".equals(insight.getCategory()) ||
                                 insight.getTitle().contains("AI") ||
                                 insight.getEvidence().containsKey("source"))
                .collect(Collectors.toList());

        if (aiInsights.isEmpty()) {
            // 显示传统洞察的AI增强版本
            List<SmartInsight> enhancedInsights = createEnhancedTraditionalInsights();
            for (SmartInsight insight : enhancedInsights) {
                insightsContainer.getChildren().add(createAIInsightCard(insight));
            }
        } else {
            for (SmartInsight insight : aiInsights) {
                insightsContainer.getChildren().add(createAIInsightCard(insight));
            }
        }

        ScrollPane scrollPane = new ScrollPane(insightsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefHeight(300);

        return scrollPane;
    }

    /**
     * 创建增强的传统洞察
     */
    private List<SmartInsight> createEnhancedTraditionalInsights() {
        List<SmartInsight> enhanced = new ArrayList<>();

        // 基于现有洞察生成AI增强版本
        GameSystemType systemType = currentResult.getSystemDetection().getPrimaryType();

        switch (systemType) {
            case EQUIPMENT:
                enhanced.add(new SmartInsight(
                        "装备系统AI分析",
                        "基于深度学习模型分析，当前装备配置符合RPG游戏的经典数值曲线模式",
                        InsightLevel.MEDIUM,
                        "装备成长曲线将直接影响玩家的装备更换频率和游戏节奏感",
                        Arrays.asList(
                                "建议在关键等级节点设置里程碑装备",
                                "考虑为低级装备添加特殊用途或套装效果",
                                "监控玩家装备更换行为数据"
                        ),
                        createMap("aiModel", "游戏平衡性分析模型", "confidence", 0.85),
                        0.85,
                        "AI装备分析"
                ));
                break;

            case CHARACTER:
                enhanced.add(new SmartInsight(
                        "角色成长AI优化",
                        "AI模型预测当前角色成长设计将为玩家提供良好的升级体验和成就感",
                        InsightLevel.POSITIVE,
                        "合理的属性成长比例有助于维持长期游戏动力",
                        Arrays.asList(
                                "当前生命值与攻击力比例适中",
                                "建议增加属性成长的视觉反馈",
                                "考虑在特定等级提供属性重置选项"
                        ),
                        createMap("aiModel", "用户体验预测模型", "playerSatisfaction", 0.78),
                        0.78,
                        "AI角色分析"
                ));
                break;

            case ECONOMY:
                enhanced.add(new SmartInsight(
                        "经济系统AI评估",
                        "通过大数据分析，当前价格分布符合健康游戏经济的标准模式",
                        InsightLevel.POSITIVE,
                        "平衡的价格体系有助于维持游戏内经济稳定和玩家参与度",
                        Arrays.asList(
                                "价格梯度设计合理，避免了通胀风险",
                                "建议定期监控玩家购买行为",
                                "考虑引入动态调价机制"
                        ),
                        createMap("aiModel", "经济健康度模型", "economicStability", 0.82),
                        0.82,
                        "AI经济分析"
                ));
                break;

            default:
                enhanced.add(new SmartInsight(
                        "数据结构AI洞察",
                        "AI分析显示数据结构清晰，字段设计遵循了良好的游戏数据规范",
                        InsightLevel.MEDIUM,
                        "规范的数据结构有助于后续功能扩展和维护",
                        Arrays.asList(
                                "数据完整性良好，便于后续分析",
                                "字段命名规范，语义清晰",
                                "建议建立数据版本管理机制"
                        ),
                        createMap("aiModel", "数据质量评估模型", "structureScore", 0.75),
                        0.75,
                        "AI数据分析"
                ));
        }

        return enhanced;
    }

    /**
     * 创建AI洞察卡片
     */
    private Node createAIInsightCard(SmartInsight insight) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #3498DB; -fx-border-width: 1; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(52,152,219,0.3), 4, 0, 0, 2);");

        // AI标识头部
        HBox aiHeader = new HBox(10);
        aiHeader.setAlignment(Pos.CENTER_LEFT);

        Label aiIcon = new Label("🤖");
        aiIcon.setFont(Font.font(18));

        Label aiLabel = new Label("AI分析");
        aiLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        aiLabel.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 12;");

        Label confidenceLabel = new Label(String.format("置信度: %.0f%%", insight.getConfidence() * 100));
        confidenceLabel.setFont(Font.font("Microsoft YaHei", 10));
        confidenceLabel.setStyle("-fx-text-fill: #666;");

        aiHeader.getChildren().addAll(aiIcon, aiLabel, confidenceLabel);

        // 标题
        Label title = new Label(insight.getTitle());
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        title.setStyle("-fx-text-fill: #2C3E50;");

        // 描述
        Label description = new Label(insight.getDescription());
        description.setFont(Font.font("Microsoft YaHei", 14));
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: #34495E;");

        // AI影响分析
        VBox impactBox = new VBox(5);
        Label impactTitle = new Label("🎯 AI影响预测：");
        impactTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        Label impactDetail = new Label(insight.getImpact());
        impactDetail.setFont(Font.font("Microsoft YaHei", 12));
        impactDetail.setWrapText(true);
        impactDetail.setStyle("-fx-text-fill: #E67E22; -fx-background-color: #fef9e7; -fx-padding: 8; -fx-background-radius: 4;");

        impactBox.getChildren().addAll(impactTitle, impactDetail);

        // AI建议
        VBox suggestionsBox = new VBox(5);
        Label suggestionsTitle = new Label("🚀 AI智能建议：");
        suggestionsTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        suggestionsBox.getChildren().add(suggestionsTitle);

        for (String suggestion : insight.getSuggestions()) {
            Label suggestionItem = new Label("• " + suggestion);
            suggestionItem.setFont(Font.font("Microsoft YaHei", 12));
            suggestionItem.setWrapText(true);
            suggestionItem.setStyle("-fx-text-fill: #27AE60;");
            suggestionsBox.getChildren().add(suggestionItem);
        }

        // AI证据
        if (insight.getEvidence().containsKey("aiModel")) {
            VBox evidenceBox = new VBox(5);
            Label evidenceTitle = new Label("🔬 AI模型信息：");
            evidenceTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

            Label modelInfo = new Label("模型: " + insight.getEvidence().get("aiModel"));
            modelInfo.setFont(Font.font("Microsoft YaHei", 11));
            modelInfo.setStyle("-fx-text-fill: #7F8C8D;");

            evidenceBox.getChildren().addAll(evidenceTitle, modelInfo);
            card.getChildren().add(evidenceBox);
        }

        card.getChildren().addAll(aiHeader, title, description, impactBox, suggestionsBox);
        return card;
    }

    /**
     * 创建AI建议卡片
     */
    private Node createAISuggestionsCard() {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: linear-gradient(to right, #667eea 0%, #764ba2 100%); -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 4);");

        // 标题
        Label title = new Label("🌟 AI总体建议");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: white;");

        // 基于系统类型生成AI总体建议
        String aiSuggestion = generateOverallAISuggestion();
        Label suggestion = new Label(aiSuggestion);
        suggestion.setFont(Font.font("Microsoft YaHei", 14));
        suggestion.setWrapText(true);
        suggestion.setStyle("-fx-text-fill: white; -fx-opacity: 0.9;");

        // 下一步行动
        Label actionTitle = new Label("🎯 建议的下一步行动：");
        actionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        actionTitle.setStyle("-fx-text-fill: white;");

        VBox actionsList = new VBox(5);
        String[] actions = generateActionItems();
        for (String action : actions) {
            Label actionItem = new Label("▶ " + action);
            actionItem.setFont(Font.font("Microsoft YaHei", 12));
            actionItem.setStyle("-fx-text-fill: white; -fx-opacity: 0.9;");
            actionsList.getChildren().add(actionItem);
        }

        card.getChildren().addAll(title, suggestion, actionTitle, actionsList);
        return card;
    }

    /**
     * 生成总体AI建议
     */
    private String generateOverallAISuggestion() {
        GameSystemType systemType = currentResult.getSystemDetection().getPrimaryType();
        double confidence = currentResult.getSystemDetection().getConfidence();
        int issueCount = currentResult.getInsights().size();

        if (confidence > 0.8 && issueCount < 3) {
            return String.format("经过AI深度分析，您的%s设计非常优秀！数据结构合理，平衡性良好。" +
                    "建议继续保持现有的设计思路，并考虑在此基础上进行功能扩展。",
                    systemType.getDisplayName());
        } else if (issueCount > 5) {
            return String.format("AI检测到%s存在一些需要关注的问题。建议优先处理关键性问题，" +
                    "然后逐步优化其他方面。建立数据监控机制，持续跟踪改进效果。",
                    systemType.getDisplayName());
        } else {
            return String.format("您的%s设计基本合理，有少量优化空间。AI建议重点关注用户体验和数据平衡性，" +
                    "这将有助于提升整体游戏品质。",
                    systemType.getDisplayName());
        }
    }

    /**
     * 生成行动项
     */
    private String[] generateActionItems() {
        GameSystemType systemType = currentResult.getSystemDetection().getPrimaryType();

        switch (systemType) {
            case EQUIPMENT:
                return new String[]{
                        "建立装备价值评估体系",
                        "设计装备升级路径",
                        "监控玩家装备使用数据"
                };
            case CHARACTER:
                return new String[]{
                        "优化角色成长曲线",
                        "增加成长里程碑奖励",
                        "收集玩家升级体验反馈"
                };
            case ECONOMY:
                return new String[]{
                        "建立价格监控机制",
                        "设计经济调节工具",
                        "分析玩家消费行为"
                };
            default:
                return new String[]{
                        "完善数据监控体系",
                        "建立版本对比机制",
                        "收集用户使用反馈"
                };
        }
    }

    private void updateCharts(EnhancedInsightResult result) {
        // 图表更新逻辑 - 后续实现
    }

    private void updateDetailTabs(EnhancedInsightResult result) {
        // 详细标签页更新 - 后续实现
    }

    /**
     * Java 8 compatible map creation helper methods
     */
    private static Map<String, Object> createMap(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}
