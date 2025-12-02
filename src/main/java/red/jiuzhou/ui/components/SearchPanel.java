package red.jiuzhou.ui.components;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * 智能搜索面板组件
 * 提供实时搜索、筛选和数据操作功能
 */
public class SearchPanel {

    private final HBox panel;
    private final TextField searchField;
    private final ComboBox<String> pageSizeComboBox;
    private final Label filterStatusLabel;
    private final Button clearFiltersButton;

    private Consumer<String> onSearchCallback;
    private Runnable onClearFiltersCallback;

    // 搜索防抖
    private Timeline searchDebounceTimer;

    public SearchPanel() {
        this.panel = new HBox();
        this.searchField = new TextField();
        this.pageSizeComboBox = new ComboBox<>();
        this.filterStatusLabel = new Label();
        this.clearFiltersButton = new Button("清除筛选");

        initializeComponents();
        setupEventHandlers();
        applyStyles();
    }

    private void initializeComponents() {
        panel.getStyleClass().add("search-panel");
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setSpacing(12);
        panel.setPadding(new Insets(16));

        // 搜索框配置
        searchField.setPromptText("智能搜索 - 支持ID、名称、描述等字段 (Ctrl+F)");
        searchField.setPrefWidth(300);
        searchField.getStyleClass().add("modern-search-field");

        // 页面大小选择
        Label pageSizeLabel = new Label("每页:");
        pageSizeComboBox.getItems().addAll("15", "25", "50", "100", "全量");
        pageSizeComboBox.setValue("25");
        pageSizeComboBox.getStyleClass().add("page-size-combo");

        // 筛选状态标签
        filterStatusLabel.getStyleClass().add("filter-status");
        filterStatusLabel.setVisible(false);

        // 清除筛选按钮
        clearFiltersButton.getStyleClass().addAll("clear-button", "secondary");
        clearFiltersButton.setVisible(false);

        // 弹性空间
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 操作按钮组
        HBox actionGroup = createActionGroup();

        panel.getChildren().addAll(
            searchField,
            new Separator(),
            pageSizeLabel,
            pageSizeComboBox,
            spacer,
            filterStatusLabel,
            clearFiltersButton,
            new Separator(),
            actionGroup
        );
    }

    private HBox createActionGroup() {
        HBox actionGroup = new HBox(8);
        actionGroup.setAlignment(Pos.CENTER_RIGHT);

        Button refreshButton = new Button("刷新");
        refreshButton.setTooltip(new Tooltip("刷新数据"));
        refreshButton.getStyleClass().addAll("icon-button", "refresh-button");

        Button exportButton = new Button("导出");
        exportButton.setTooltip(new Tooltip("导出数据"));
        exportButton.getStyleClass().addAll("icon-button", "export-button");

        Button settingsButton = new Button("设置");
        settingsButton.setTooltip(new Tooltip("表格设置"));
        settingsButton.getStyleClass().addAll("icon-button", "settings-button");

        actionGroup.getChildren().addAll(refreshButton, exportButton, settingsButton);
        return actionGroup;
    }

    private void setupEventHandlers() {
        // 搜索防抖处理
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (searchDebounceTimer != null) {
                searchDebounceTimer.stop();
            }

            searchDebounceTimer = new Timeline(new KeyFrame(Duration.millis(300), e -> {
                if (onSearchCallback != null) {
                    onSearchCallback.accept(newText);
                    updateFilterStatus(newText);
                }
            }));

            searchDebounceTimer.play();
        });

        // 清除筛选
        clearFiltersButton.setOnAction(e -> {
            searchField.clear();
            clearFilterStatus();
            if (onClearFiltersCallback != null) {
                onClearFiltersCallback.run();
            }
        });

        // 页面大小变更
        pageSizeComboBox.setOnAction(e -> {
            // TODO: 触发页面大小变更回调
        });
    }

    private void applyStyles() {
        panel.getStylesheets().add("/modern-theme.css");
    }

    /**
     * 更新筛选状态显示
     */
    private void updateFilterStatus(String searchTerm) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            filterStatusLabel.setText("搜索: \"" + searchTerm + "\"");
            filterStatusLabel.setVisible(true);
            clearFiltersButton.setVisible(true);
        } else {
            clearFilterStatus();
        }
    }

    /**
     * 清除筛选状态
     */
    private void clearFilterStatus() {
        filterStatusLabel.setVisible(false);
        clearFiltersButton.setVisible(false);
    }

    /**
     * 聚焦搜索框
     */
    public void focusSearchField() {
        searchField.requestFocus();
    }

    /**
     * 获取当前搜索词
     */
    public String getSearchTerm() {
        return searchField.getText();
    }

    /**
     * 设置搜索词
     */
    public void setSearchTerm(String searchTerm) {
        searchField.setText(searchTerm);
    }

    /**
     * 获取选中的页面大小
     */
    public int getPageSize() {
        String selected = pageSizeComboBox.getValue();
        return "全量".equals(selected) ? Integer.MAX_VALUE : Integer.parseInt(selected);
    }

    // 回调设置方法
    public void setOnSearch(Consumer<String> callback) {
        this.onSearchCallback = callback;
    }

    public void setOnClearFilters(Runnable callback) {
        this.onClearFiltersCallback = callback;
    }

    public HBox getPanel() {
        return panel;
    }
}