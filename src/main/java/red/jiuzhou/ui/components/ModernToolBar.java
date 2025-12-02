package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 现代化工具栏组件
 * 专为设计师优化的数据管理工具栏，提供清晰的功能分组和智能提示
 */
public class ModernToolBar {

    private static final Logger log = LoggerFactory.getLogger(ModernToolBar.class);

    private final Stage primaryStage;
    private final ToolBar toolBar;

    public ModernToolBar(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.toolBar = new ToolBar();
        initializeToolBar();
    }

    private void initializeToolBar() {
        toolBar.getStyleClass().add("modern-toolbar");

        // 数据管理组
        HBox dataGroup = createButtonGroup("数据管理",
            createIconButton("📁", "目录管理", "管理数据文件和目录结构", this::handleDirectoryManagement),
            createIconButton("🔗", "映射关系", "配置数据库表与XML的映射关系", this::handleMappingConfiguration)
        );

        // 查询工具组
        HBox queryGroup = createButtonGroup("查询工具",
            createIconButton("🔍", "枚举查询", "快速查询枚举值和数据统计", this::handleEnumQuery),
            createIconButton("⚡", "新建查询", "创建自定义SQL查询", this::handleNewQuery),
            createIconButton("🔄", "SQL转换", "SQL语句格式转换和优化", this::handleSqlConverter)
        );

        // 导入导出组
        HBox dataTransferGroup = createButtonGroup("数据传输",
            createIconButton("📥", "智能导入", "从XML文件导入数据到数据库", this::handleSmartImport),
            createIconButton("📤", "数据导出", "将数据库数据导出为XML", this::handleDataExport),
            createIconButton("⚙️", "DDL生成", "生成数据库表结构", this::handleDdlGeneration)
        );

        // 分隔符和弹性空间
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 状态信息
        Label statusLabel = createStatusLabel();

        // 帮助和设置
        HBox utilityGroup = createButtonGroup("工具",
            createIconButton("❓", "帮助", "查看使用帮助和文档", this::handleHelp),
            createIconButton("⚙️", "设置", "应用程序设置", this::handleSettings)
        );

        toolBar.getItems().addAll(
            dataGroup,
            createSeparator(),
            queryGroup,
            createSeparator(),
            dataTransferGroup,
            spacer,
            statusLabel,
            createSeparator(),
            utilityGroup
        );
    }

    /**
     * 创建功能按钮组
     */
    private HBox createButtonGroup(String groupName, Button... buttons) {
        HBox group = new HBox(8);
        group.setAlignment(Pos.CENTER_LEFT);
        group.getStyleClass().add("button-group");

        // 添加组标签（可选，用于大屏幕）
        Label groupLabel = new Label(groupName);
        groupLabel.getStyleClass().add("group-label");
        groupLabel.setVisible(false); // 默认隐藏，可根据窗口大小动态显示

        group.getChildren().addAll(buttons);
        return group;
    }

    /**
     * 创建带图标和提示的按钮
     */
    private Button createIconButton(String icon, String text, String tooltip, Runnable action) {
        Button button = new Button(icon + " " + text);
        button.getStyleClass().addAll("icon-button", "modern-button");

        // 添加工具提示
        Tooltip tip = new Tooltip(tooltip);
        button.setTooltip(tip);

        // 设置事件处理
        button.setOnAction(e -> {
            try {
                action.run();
            } catch (Exception ex) {
                log.error("执行操作失败: " + text, ex);
                showErrorNotification("操作失败", "执行 " + text + " 时发生错误: " + ex.getMessage());
            }
        });

        return button;
    }

    /**
     * 创建分隔符
     */
    private Separator createSeparator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("toolbar-separator");
        return separator;
    }

    /**
     * 创建状态标签
     */
    private Label createStatusLabel() {
        Label statusLabel = new Label("就绪");
        statusLabel.getStyleClass().addAll("status-label", "status-ready");
        return statusLabel;
    }

    // ========== 事件处理方法 ==========

    private void handleDirectoryManagement() {
        log.info("打开目录管理");
        // TODO: 集成现有的 DirectoryManagerDialog
    }

    private void handleMappingConfiguration() {
        log.info("打开映射配置");
        // TODO: 显示映射关系配置界面
    }

    private void handleEnumQuery() {
        log.info("打开枚举查询");
        // TODO: 集成现有的 EnumQuery
    }

    private void handleNewQuery() {
        log.info("打开新查询");
        // TODO: 集成现有的 SqlQryApp
    }

    private void handleSqlConverter() {
        log.info("打开SQL转换器");
        // TODO: 集成现有的 SQLConverterApp
    }

    private void handleSmartImport() {
        log.info("开始智能导入");
        // TODO: 打开智能导入向导
        showImportWizard();
    }

    private void handleDataExport() {
        log.info("开始数据导出");
        // TODO: 打开导出向导
        showExportWizard();
    }

    private void handleDdlGeneration() {
        log.info("生成DDL");
        // TODO: 集成现有的 DDL 生成功能
    }

    private void handleHelp() {
        log.info("显示帮助");
        showHelpDialog();
    }

    private void handleSettings() {
        log.info("打开设置");
        showSettingsDialog();
    }

    // ========== 向导和对话框方法 ==========

    /**
     * 显示智能导入向导
     */
    private void showImportWizard() {
        Alert wizard = new Alert(Alert.AlertType.INFORMATION);
        wizard.setTitle("智能导入向导");
        wizard.setHeaderText("选择导入方式");
        wizard.setContentText("请选择您要进行的导入操作：\n\n" +
            "• 标准导入 - 直接从XML导入数据\n" +
            "• AI增强导入 - 使用AI优化和转换数据\n" +
            "• 批量导入 - 导入多个文件");

        ButtonType standardBtn = new ButtonType("标准导入");
        ButtonType aiBtn = new ButtonType("AI增强导入");
        ButtonType batchBtn = new ButtonType("批量导入");
        ButtonType cancelBtn = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

        wizard.getButtonTypes().setAll(standardBtn, aiBtn, batchBtn, cancelBtn);
        wizard.showAndWait().ifPresent(result -> {
            if (result == aiBtn) {
                // TODO: 打开AI增强导入界面
                log.info("启动AI增强导入");
            } else if (result == standardBtn) {
                // TODO: 打开标准导入界面
                log.info("启动标准导入");
            } else if (result == batchBtn) {
                // TODO: 打开批量导入界面
                log.info("启动批量导入");
            }
        });
    }

    /**
     * 显示导出向导
     */
    private void showExportWizard() {
        Alert wizard = new Alert(Alert.AlertType.INFORMATION);
        wizard.setTitle("数据导出向导");
        wizard.setHeaderText("配置导出选项");
        wizard.setContentText("请选择导出配置：\n\n" +
            "• 完整导出 - 导出所有数据\n" +
            "• 筛选导出 - 根据条件导出\n" +
            "• 增量导出 - 仅导出变更数据");

        ButtonType fullBtn = new ButtonType("完整导出");
        ButtonType filterBtn = new ButtonType("筛选导出");
        ButtonType incrementalBtn = new ButtonType("增量导出");
        ButtonType cancelBtn = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

        wizard.getButtonTypes().setAll(fullBtn, filterBtn, incrementalBtn, cancelBtn);
        wizard.showAndWait().ifPresent(result -> {
            // TODO: 根据选择启动相应的导出流程
            log.info("启动导出: " + result.getText());
        });
    }

    /**
     * 显示帮助对话框
     */
    private void showHelpDialog() {
        Alert help = new Alert(Alert.AlertType.INFORMATION);
        help.setTitle("使用帮助");
        help.setHeaderText("DB XML Tool 使用指南");

        String helpContent = "快速开始\n" +
            "1. 使用\"目录管理\"配置数据文件路径\n" +
            "2. 通过\"映射关系\"设置数据库表与XML的对应关系\n" +
            "3. 使用\"智能导入\"从XML文件导入数据\n\n" +
            "查询功能\n" +
            "- 枚举查询：快速统计字段值分布\n" +
            "- 新建查询：创建自定义SQL查询\n" +
            "- SQL转换：格式化和优化SQL语句\n\n" +
            "数据管理\n" +
            "- 支持大数据量的分页展示\n" +
            "- 实时搜索和筛选\n" +
            "- 批量数据操作\n\n" +
            "提示：鼠标悬停在按钮上可查看详细说明";

        help.setContentText(helpContent);
        help.getDialogPane().setPrefWidth(500);
        help.showAndWait();
    }

    /**
     * 显示设置对话框
     */
    private void showSettingsDialog() {
        Alert settings = new Alert(Alert.AlertType.INFORMATION);
        settings.setTitle("应用程序设置");
        settings.setHeaderText("配置应用程序选项");
        settings.setContentText("设置功能开发中...\n\n" +
            "即将支持：\n" +
            "- 界面主题切换\n" +
            "- 数据库连接配置\n" +
            "- AI模型配置\n" +
            "- 性能参数调优");
        settings.showAndWait();
    }

    /**
     * 显示错误通知
     */
    private void showErrorNotification(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("操作失败");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 获取工具栏实例
     */
    public ToolBar getToolBar() {
        return toolBar;
    }

    /**
     * 更新状态信息
     */
    public void updateStatus(String status, String styleClass) {
        toolBar.getItems().stream()
            .filter(item -> item instanceof Label && item.getStyleClass().contains("status-label"))
            .findFirst()
            .ifPresent(item -> {
                Label statusLabel = (Label) item;
                statusLabel.setText(status);
                statusLabel.getStyleClass().removeIf(cls -> cls.startsWith("status-"));
                statusLabel.getStyleClass().add(styleClass);
            });
    }
}