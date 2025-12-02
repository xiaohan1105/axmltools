package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 智能分页组件
 * 提供现代化的分页导航和页面跳转功能
 */
public class SmartPagination {

    private final HBox panel;
    private final Label infoLabel;
    private final Button firstButton;
    private final Button prevButton;
    private final Button nextButton;
    private final Button lastButton;
    private final TextField pageField;
    private final Label totalPagesLabel;

    private int currentPage = 0;
    private int totalPages = 1;
    private int totalItems = 0;
    private int pageSize = 25;

    private IntConsumer onPageChangeCallback;
    private IntConsumer onPageSizeChangeCallback;

    public SmartPagination() {
        this.panel = new HBox();
        this.infoLabel = new Label();
        this.firstButton = new Button("⏮️");
        this.prevButton = new Button("⬅️");
        this.nextButton = new Button("➡️");
        this.lastButton = new Button("⏭️");
        this.pageField = new TextField();
        this.totalPagesLabel = new Label();

        initializeComponents();
        setupEventHandlers();
        applyStyles();
        updateDisplay();
    }

    private void initializeComponents() {
        panel.getStyleClass().add("smart-pagination");
        panel.setAlignment(Pos.CENTER);
        panel.setSpacing(8);
        panel.setPadding(new Insets(12, 16, 12, 16));

        // 信息标签
        infoLabel.getStyleClass().add("pagination-info");

        // 导航按钮设置
        setupNavigationButtons();

        // 页码输入框
        pageField.setPrefWidth(60);
        pageField.getStyleClass().add("page-input");
        pageField.setPromptText("页码");

        // 总页数标签
        totalPagesLabel.getStyleClass().add("total-pages");

        // 弹性空间
        Region spacer1 = new Region();
        Region spacer2 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        // 页码跳转区域
        HBox pageJumpBox = new HBox(4);
        pageJumpBox.setAlignment(Pos.CENTER);
        pageJumpBox.getChildren().addAll(
            new Label("第"),
            pageField,
            new Label("页 / 共"),
            totalPagesLabel,
            new Label("页")
        );

        // 导航按钮区域
        HBox navigationBox = new HBox(4);
        navigationBox.setAlignment(Pos.CENTER);
        navigationBox.getChildren().addAll(
            firstButton, prevButton, nextButton, lastButton
        );

        panel.getChildren().addAll(
            infoLabel,
            spacer1,
            navigationBox,
            pageJumpBox,
            spacer2
        );
    }

    private void setupNavigationButtons() {
        // 按钮样式
        firstButton.getStyleClass().addAll("pagination-button", "nav-button");
        prevButton.getStyleClass().addAll("pagination-button", "nav-button");
        nextButton.getStyleClass().addAll("pagination-button", "nav-button");
        lastButton.getStyleClass().addAll("pagination-button", "nav-button");

        // 工具提示
        firstButton.setTooltip(new javafx.scene.control.Tooltip("首页"));
        prevButton.setTooltip(new javafx.scene.control.Tooltip("上一页"));
        nextButton.setTooltip(new javafx.scene.control.Tooltip("下一页"));
        lastButton.setTooltip(new javafx.scene.control.Tooltip("末页"));
    }

    private void setupEventHandlers() {
        // 导航按钮事件
        firstButton.setOnAction(e -> goToPage(0));
        prevButton.setOnAction(e -> goToPage(currentPage - 1));
        nextButton.setOnAction(e -> goToPage(currentPage + 1));
        lastButton.setOnAction(e -> goToPage(totalPages - 1));

        // 页码输入事件
        pageField.setOnAction(e -> {
            try {
                int page = Integer.parseInt(pageField.getText()) - 1; // 转换为0基索引
                goToPage(page);
            } catch (NumberFormatException ex) {
                pageField.setText(String.valueOf(currentPage + 1));
            }
        });

        // 失去焦点时验证页码
        pageField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                try {
                    int page = Integer.parseInt(pageField.getText()) - 1;
                    if (page < 0 || page >= totalPages) {
                        pageField.setText(String.valueOf(currentPage + 1));
                    }
                } catch (NumberFormatException ex) {
                    pageField.setText(String.valueOf(currentPage + 1));
                }
            }
        });
    }

    private void applyStyles() {
        panel.getStylesheets().add("/modern-theme.css");
    }

    /**
     * 跳转到指定页面
     */
    private void goToPage(int page) {
        if (page < 0 || page >= totalPages || page == currentPage) {
            return;
        }

        this.currentPage = page;
        updateDisplay();

        if (onPageChangeCallback != null) {
            onPageChangeCallback.accept(page);
        }
    }

    /**
     * 更新显示状态
     */
    private void updateDisplay() {
        // 更新信息标签
        int startItem = currentPage * pageSize + 1;
        int endItem = Math.min((currentPage + 1) * pageSize, totalItems);
        infoLabel.setText(String.format("显示 %d-%d 条，共 %d 条", startItem, endItem, totalItems));

        // 更新页码显示
        pageField.setText(String.valueOf(currentPage + 1));
        totalPagesLabel.setText(String.valueOf(totalPages));

        // 更新按钮状态
        firstButton.setDisable(currentPage <= 0);
        prevButton.setDisable(currentPage <= 0);
        nextButton.setDisable(currentPage >= totalPages - 1);
        lastButton.setDisable(currentPage >= totalPages - 1);

        // 添加按钮状态样式
        updateButtonStyles();
    }

    private void updateButtonStyles() {
        // 清除状态样式
        firstButton.getStyleClass().removeIf(cls -> cls.equals("disabled"));
        prevButton.getStyleClass().removeIf(cls -> cls.equals("disabled"));
        nextButton.getStyleClass().removeIf(cls -> cls.equals("disabled"));
        lastButton.getStyleClass().removeIf(cls -> cls.equals("disabled"));

        // 添加禁用样式
        if (firstButton.isDisabled()) firstButton.getStyleClass().add("disabled");
        if (prevButton.isDisabled()) prevButton.getStyleClass().add("disabled");
        if (nextButton.isDisabled()) nextButton.getStyleClass().add("disabled");
        if (lastButton.isDisabled()) lastButton.getStyleClass().add("disabled");
    }

    // ========== 公共API ==========

    /**
     * 设置总项目数
     */
    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
        this.totalPages = (int) Math.ceil((double) totalItems / pageSize);
        if (this.totalPages == 0) this.totalPages = 1;

        // 如果当前页超出范围，调整到最后一页
        if (currentPage >= totalPages) {
            currentPage = Math.max(0, totalPages - 1);
        }

        updateDisplay();
    }

    /**
     * 设置页面大小
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        setTotalItems(totalItems); // 重新计算总页数
    }

    /**
     * 设置当前页
     */
    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, totalPages - 1));
        updateDisplay();
    }

    /**
     * 获取当前页
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * 获取页面大小
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 获取总页数
     */
    public int getTotalPages() {
        return totalPages;
    }

    /**
     * 设置页面变更回调
     */
    public void setOnPageChange(IntConsumer callback) {
        this.onPageChangeCallback = callback;
    }

    /**
     * 设置页面大小变更回调
     */
    public void setOnPageSizeChange(IntConsumer callback) {
        this.onPageSizeChangeCallback = callback;
    }

    public HBox getPanel() {
        return panel;
    }
}