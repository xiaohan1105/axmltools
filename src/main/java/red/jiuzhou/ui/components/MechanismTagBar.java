package red.jiuzhou.ui.components;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismFileMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 机制标签栏组件（优化版）
 *
 * 显示游戏机制分类的快捷标签，支持：
 * - 点击标签过滤目录树
 * - 显示每个机制的文件数量
 * - 当前选中机制高亮（焦点模式）
 * - 焦点状态指示条
 * - 平滑动画过渡
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class MechanismTagBar extends VBox {

    /** 当前选中的机制（焦点） */
    private AionMechanismCategory selectedMechanism = null;

    /** 机制选择回调 */
    private Consumer<AionMechanismCategory> onMechanismSelected;

    /** 标签按钮映射 */
    private final Map<AionMechanismCategory, ToggleButton> tagButtons = new HashMap<>();

    /** 标签容器 */
    private FlowPane tagPane;

    /** 展开/折叠状态 */
    private boolean expanded = false;

    /** 常用机制数量（折叠时显示） */
    private static final int COLLAPSED_COUNT = 8;

    /** 全部标签按钮 */
    private ToggleButton allButton;

    /** 更多按钮 */
    private Button moreButton;

    /** 焦点指示条 */
    private HBox focusIndicator;

    /** 焦点机制标签 */
    private Label focusLabel;

    /** 焦点清除按钮 */
    private Button clearFocusBtn;

    /** 动画持续时间 */
    private static final Duration ANIMATION_DURATION = Duration.millis(200);

    public MechanismTagBar() {
        initUI();
    }

    private void initUI() {
        setSpacing(0);

        // 主容器样式
        setStyle(
            "-fx-background-color: linear-gradient(to bottom, #ffffff, #f8f9fa);" +
            "-fx-border-color: #e9ecef;" +
            "-fx-border-width: 0 0 1 0;"
        );

        // 焦点指示条（默认隐藏）
        focusIndicator = createFocusIndicator();
        focusIndicator.setVisible(false);
        focusIndicator.setManaged(false);

        // 标题行
        HBox titleRow = createTitleRow();

        // 标签流式布局
        tagPane = new FlowPane();
        tagPane.setHgap(8);
        tagPane.setVgap(8);
        tagPane.setPadding(new Insets(8, 12, 10, 12));

        getChildren().addAll(focusIndicator, titleRow, tagPane);

        // 初始化标签
        refreshTags();
    }

    /**
     * 创建焦点指示条
     */
    private HBox createFocusIndicator() {
        HBox indicator = new HBox(10);
        indicator.setAlignment(Pos.CENTER_LEFT);
        indicator.setPadding(new Insets(8, 12, 8, 12));

        // 焦点图标
        Label focusIcon = new Label("🎯");
        focusIcon.setStyle("-fx-font-size: 14px;");

        // 焦点标签（动态更新）
        focusLabel = new Label("当前焦点: ");
        focusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 清除焦点按钮
        clearFocusBtn = new Button("✕ 清除焦点");
        clearFocusBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #6c757d;" +
            "-fx-font-size: 11px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 8;"
        );
        clearFocusBtn.setOnAction(e -> selectMechanism(null));

        // 鼠标悬停效果
        clearFocusBtn.setOnMouseEntered(e ->
            clearFocusBtn.setStyle(
                "-fx-background-color: #f8f9fa;" +
                "-fx-text-fill: #dc3545;" +
                "-fx-font-size: 11px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 4 8;" +
                "-fx-background-radius: 4;"
            )
        );
        clearFocusBtn.setOnMouseExited(e ->
            clearFocusBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #6c757d;" +
                "-fx-font-size: 11px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 4 8;"
            )
        );

        indicator.getChildren().addAll(focusIcon, focusLabel, spacer, clearFocusBtn);

        return indicator;
    }

    /**
     * 创建标题行
     */
    private HBox createTitleRow() {
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setPadding(new Insets(10, 12, 0, 12));

        // 标题
        Label titleLabel = new Label("🎮 机制分类");
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #343a40;");

        // 全部按钮
        allButton = createAllButton();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 更多/收起按钮
        moreButton = new Button("展开更多 ▼");
        moreButton.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #007bff;" +
            "-fx-font-size: 11px;" +
            "-fx-cursor: hand;"
        );
        moreButton.setOnAction(e -> toggleExpand());

        titleRow.getChildren().addAll(titleLabel, allButton, spacer, moreButton);

        return titleRow;
    }

    /**
     * 创建"全部"按钮
     */
    private ToggleButton createAllButton() {
        ToggleButton btn = new ToggleButton("全部");
        btn.setSelected(true);

        btn.setStyle(
            "-fx-background-color: #6c757d;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 14;" +
            "-fx-padding: 4 12;" +
            "-fx-cursor: hand;"
        );

        btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                btn.setStyle(
                    "-fx-background-color: #6c757d;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-size: 11px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 14;" +
                    "-fx-padding: 4 12;" +
                    "-fx-cursor: hand;"
                );
            } else {
                btn.setStyle(
                    "-fx-background-color: #e9ecef;" +
                    "-fx-text-fill: #495057;" +
                    "-fx-font-size: 11px;" +
                    "-fx-background-radius: 14;" +
                    "-fx-padding: 4 12;" +
                    "-fx-cursor: hand;"
                );
            }
        });

        btn.setOnAction(e -> selectMechanism(null));

        return btn;
    }

    /**
     * 刷新标签（根据实际文件统计）
     */
    public void refreshTags() {
        tagPane.getChildren().clear();
        tagButtons.clear();

        MechanismFileMapper mapper = MechanismFileMapper.getInstance();
        List<AionMechanismCategory> mechanisms = mapper.getCommonMechanisms();
        Map<AionMechanismCategory, Integer> stats = mapper.getMechanismStats();

        int displayCount = expanded ? mechanisms.size() : Math.min(COLLAPSED_COUNT, mechanisms.size());

        for (int i = 0; i < displayCount && i < mechanisms.size(); i++) {
            AionMechanismCategory category = mechanisms.get(i);
            int count = stats.getOrDefault(category, 0);

            ToggleButton btn = createTagButton(category, count);
            btn.setOnAction(e -> {
                if (btn.isSelected()) {
                    selectMechanism(category);
                } else {
                    selectMechanism(null);
                }
            });

            tagButtons.put(category, btn);
            tagPane.getChildren().add(btn);
        }

        // 更新全部按钮的文件计数
        int allFilesCount = 0;
        for (Integer count : stats.values()) {
            allFilesCount += count;
        }
        allButton.setText("全部 (" + allFilesCount + ")");

        // 更新更多按钮
        updateMoreButton(mechanisms, stats);
    }

    /**
     * 更新更多按钮
     */
    private void updateMoreButton(List<AionMechanismCategory> mechanisms, Map<AionMechanismCategory, Integer> stats) {
        if (mechanisms.size() > COLLAPSED_COUNT) {
            moreButton.setVisible(true);
            int hiddenCount = 0;
            for (int i = COLLAPSED_COUNT; i < mechanisms.size(); i++) {
                hiddenCount += stats.getOrDefault(mechanisms.get(i), 0);
            }
            moreButton.setText(expanded ?
                "收起 ▲" :
                "展开更多 ▼ (" + (mechanisms.size() - COLLAPSED_COUNT) + "类)");
        } else {
            moreButton.setVisible(false);
        }
    }

    /**
     * 创建标签按钮
     */
    private ToggleButton createTagButton(AionMechanismCategory category, int count) {
        // 创建按钮内容
        HBox content = new HBox(5);
        content.setAlignment(Pos.CENTER);

        // 机制图标/颜色指示
        Circle colorDot = new Circle(4);
        try {
            colorDot.setFill(Color.web(category.getColor()));
        } catch (Exception e) {
            colorDot.setFill(Color.GRAY);
        }

        // 机制名称
        Label nameLabel = new Label(category.getDisplayName());
        nameLabel.setStyle("-fx-font-size: 11px;");

        // 文件数量
        Label countLabel = new Label("(" + count + ")");
        countLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #868e96;");

        content.getChildren().addAll(colorDot, nameLabel, countLabel);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(content);

        String color = category.getColor();
        String lightBg = lightenColor(color, 0.92);

        // 默认样式
        btn.setStyle(String.format(
            "-fx-background-color: %s;" +
            "-fx-background-radius: 16;" +
            "-fx-padding: 5 12;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: %s;" +
            "-fx-border-radius: 16;" +
            "-fx-border-width: 1;",
            lightBg, lightenColor(color, 0.7)
        ));

        // 选中状态样式变化
        btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                // 选中：高亮
                btn.setStyle(String.format(
                    "-fx-background-color: %s;" +
                    "-fx-background-radius: 16;" +
                    "-fx-padding: 5 12;" +
                    "-fx-cursor: hand;" +
                    "-fx-border-color: %s;" +
                    "-fx-border-radius: 16;" +
                    "-fx-border-width: 2;" +
                    "-fx-effect: dropshadow(gaussian, %s, 6, 0.3, 0, 2);",
                    color, color, color
                ));
                nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: white; -fx-font-weight: bold;");
                countLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(255,255,255,0.8);");
                colorDot.setFill(Color.WHITE);

                // 选中动画
                playSelectAnimation(btn);
            } else {
                // 未选中：普通
                btn.setStyle(String.format(
                    "-fx-background-color: %s;" +
                    "-fx-background-radius: 16;" +
                    "-fx-padding: 5 12;" +
                    "-fx-cursor: hand;" +
                    "-fx-border-color: %s;" +
                    "-fx-border-radius: 16;" +
                    "-fx-border-width: 1;",
                    lightBg, lightenColor(color, 0.7)
                ));
                nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #212529;");
                countLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #868e96;");
                try {
                    colorDot.setFill(Color.web(color));
                } catch (Exception e) {
                    colorDot.setFill(Color.GRAY);
                }
            }
        });

        // Tooltip
        Tooltip tooltip = new Tooltip(
            category.getIcon() + " " + category.getDisplayName() + "\n" +
            category.getDescription() + "\n" +
            "📁 文件数: " + count + "\n\n" +
            "💡 点击聚焦此机制"
        );
        tooltip.setStyle("-fx-font-size: 11px;");
        btn.setTooltip(tooltip);

        return btn;
    }

    /**
     * 播放选中动画
     */
    private void playSelectAnimation(ToggleButton btn) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(100), btn);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.05);
        scale.setToY(1.05);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }

    /**
     * 将颜色变浅
     */
    private String lightenColor(String hexColor, double factor) {
        try {
            Color color = Color.web(hexColor);
            double r = color.getRed() + (1 - color.getRed()) * factor;
            double g = color.getGreen() + (1 - color.getGreen()) * factor;
            double b = color.getBlue() + (1 - color.getBlue()) * factor;
            return String.format("#%02X%02X%02X",
                (int)(r * 255), (int)(g * 255), (int)(b * 255));
        } catch (Exception e) {
            return "#f8f9fa";
        }
    }

    /**
     * 切换展开/折叠
     */
    private void toggleExpand() {
        expanded = !expanded;
        refreshTags();

        // 保持当前选中状态
        if (selectedMechanism != null && tagButtons.containsKey(selectedMechanism)) {
            tagButtons.get(selectedMechanism).setSelected(true);
        }
    }

    /**
     * 选择机制（设置焦点）
     */
    public void selectMechanism(AionMechanismCategory category) {
        this.selectedMechanism = category;

        // 更新按钮状态
        allButton.setSelected(category == null);
        for (Map.Entry<AionMechanismCategory, ToggleButton> entry : tagButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == category);
        }

        // 更新焦点指示条
        updateFocusIndicator(category);

        // 触发回调
        if (onMechanismSelected != null) {
            onMechanismSelected.accept(category);
        }
    }

    /**
     * 更新焦点指示条
     */
    private void updateFocusIndicator(AionMechanismCategory category) {
        if (category == null) {
            // 隐藏焦点指示条
            if (focusIndicator.isVisible()) {
                FadeTransition fade = new FadeTransition(ANIMATION_DURATION, focusIndicator);
                fade.setFromValue(1.0);
                fade.setToValue(0.0);
                fade.setOnFinished(e -> {
                    focusIndicator.setVisible(false);
                    focusIndicator.setManaged(false);
                });
                fade.play();
            }
        } else {
            // 显示焦点指示条
            focusLabel.setText("当前焦点: " + category.getIcon() + " " + category.getDisplayName());

            // 设置指示条背景色
            focusIndicator.setStyle(String.format(
                "-fx-background-color: linear-gradient(to right, %s, %s);" +
                "-fx-border-color: %s;" +
                "-fx-border-width: 0 0 1 0;",
                lightenColor(category.getColor(), 0.9),
                lightenColor(category.getColor(), 0.95),
                lightenColor(category.getColor(), 0.6)
            ));
            focusLabel.setStyle(String.format(
                "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: %s;",
                category.getColor()
            ));

            if (!focusIndicator.isVisible()) {
                focusIndicator.setOpacity(0);
                focusIndicator.setVisible(true);
                focusIndicator.setManaged(true);

                FadeTransition fade = new FadeTransition(ANIMATION_DURATION, focusIndicator);
                fade.setFromValue(0.0);
                fade.setToValue(1.0);
                fade.play();
            }
        }
    }

    /**
     * 获取当前选中的机制
     */
    public AionMechanismCategory getSelectedMechanism() {
        return selectedMechanism;
    }

    /**
     * 设置机制选择回调
     */
    public void setOnMechanismSelected(Consumer<AionMechanismCategory> callback) {
        this.onMechanismSelected = callback;
    }

    /**
     * 清除选择
     */
    public void clearSelection() {
        selectMechanism(null);
    }

    /**
     * 高亮指定机制（用于从文件反向定位）
     */
    public void highlightMechanism(AionMechanismCategory category) {
        // 如果是更多机制，先展开
        if (!tagButtons.containsKey(category) && category != null) {
            expanded = true;
            refreshTags();
        }
        selectMechanism(category);
    }

    /**
     * 更新机制文件数量
     */
    public void updateCounts(Map<AionMechanismCategory, Integer> counts) {
        // 需要重新创建按钮以更新数量显示
        refreshTags();

        // 恢复选中状态
        if (selectedMechanism != null && tagButtons.containsKey(selectedMechanism)) {
            tagButtons.get(selectedMechanism).setSelected(true);
        }
    }
}
