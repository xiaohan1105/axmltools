package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismFileMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 机制标签栏组件
 *
 * 显示游戏机制分类的快捷标签，支持：
 * - 点击标签过滤目录树
 * - 显示每个机制的文件数量
 * - 当前选中机制高亮
 * - 全部/展开更多切换
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class MechanismTagBar extends VBox {

    /** 当前选中的机制 */
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

    public MechanismTagBar() {
        initUI();
    }

    private void initUI() {
        setSpacing(5);
        setPadding(new Insets(8, 10, 8, 10));
        setStyle("-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef); " +
                 "-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        // 标题行
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("🎮 机制分类");
        titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        // 全部按钮
        allButton = createTagButton(null, "全部", "#6c757d", 0);
        allButton.setSelected(true);
        allButton.setOnAction(e -> selectMechanism(null));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 更多/收起按钮
        moreButton = new Button("更多 ▼");
        moreButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #007bff; " +
                           "-fx-font-size: 11px; -fx-cursor: hand;");
        moreButton.setOnAction(e -> toggleExpand());

        titleRow.getChildren().addAll(titleLabel, allButton, spacer, moreButton);

        // 标签流式布局
        tagPane = new FlowPane();
        tagPane.setHgap(6);
        tagPane.setVgap(6);
        tagPane.setPadding(new Insets(5, 0, 0, 0));

        getChildren().addAll(titleRow, tagPane);

        // 初始化标签
        refreshTags();
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

            ToggleButton btn = createTagButton(category, category.getDisplayName(), category.getColor(), count);
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

        // 更新更多按钮
        if (mechanisms.size() > COLLAPSED_COUNT) {
            moreButton.setVisible(true);
            moreButton.setText(expanded ? "收起 ▲" : "更多 ▼ (" + (mechanisms.size() - COLLAPSED_COUNT) + ")");
        } else {
            moreButton.setVisible(false);
        }
    }

    /**
     * 创建标签按钮
     */
    private ToggleButton createTagButton(AionMechanismCategory category, String text, String color, int count) {
        String displayText = count > 0 ? text + " (" + count + ")" : text;
        ToggleButton btn = new ToggleButton(displayText);

        // 计算浅色背景
        String lightBg = lightenColor(color, 0.85);
        String hoverBg = lightenColor(color, 0.75);

        btn.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-size: 11px; " +
            "-fx-background-radius: 12; " +
            "-fx-padding: 3 10; " +
            "-fx-cursor: hand; " +
            "-fx-border-color: %s; " +
            "-fx-border-radius: 12; " +
            "-fx-border-width: 1;",
            lightBg, color, color
        ));

        // 选中状态样式
        btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                btn.setStyle(String.format(
                    "-fx-background-color: %s; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 11px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-background-radius: 12; " +
                    "-fx-padding: 3 10; " +
                    "-fx-cursor: hand; " +
                    "-fx-border-color: %s; " +
                    "-fx-border-radius: 12; " +
                    "-fx-border-width: 1;",
                    color, color
                ));
            } else {
                btn.setStyle(String.format(
                    "-fx-background-color: %s; " +
                    "-fx-text-fill: %s; " +
                    "-fx-font-size: 11px; " +
                    "-fx-background-radius: 12; " +
                    "-fx-padding: 3 10; " +
                    "-fx-cursor: hand; " +
                    "-fx-border-color: %s; " +
                    "-fx-border-radius: 12; " +
                    "-fx-border-width: 1;",
                    lightBg, color, color
                ));
            }
        });

        // Tooltip
        if (category != null) {
            btn.setTooltip(new Tooltip(category.getDescription() + "\n文件数: " + count));
        }

        return btn;
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
     * 选择机制
     */
    public void selectMechanism(AionMechanismCategory category) {
        this.selectedMechanism = category;

        // 更新按钮状态
        allButton.setSelected(category == null);
        for (Map.Entry<AionMechanismCategory, ToggleButton> entry : tagButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == category);
        }

        // 触发回调
        if (onMechanismSelected != null) {
            onMechanismSelected.accept(category);
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
        for (Map.Entry<AionMechanismCategory, ToggleButton> entry : tagButtons.entrySet()) {
            AionMechanismCategory category = entry.getKey();
            ToggleButton btn = entry.getValue();
            int count = counts.getOrDefault(category, 0);
            String text = category.getDisplayName() + " (" + count + ")";
            btn.setText(text);
        }
    }
}
