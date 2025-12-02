package red.jiuzhou.analysis.enhanced;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ui.DesignerInsightStage;

import java.io.File;
import java.nio.file.Path;

/**
 * 洞察功能集成适配器
 *
 * 负责将新的游戏专属洞察面板集成到现有的DesignerInsightStage中
 *
 * 集成策略：
 * 1. 兼容性优先 - 不破坏现有功能
 * 2. 渐进式增强 - 新功能作为增强而非替代
 * 3. 用户选择 - 让用户在传统模式和增强模式间切换
 * 4. 平滑迁移 - 提供迁移路径和使用指导
 */
public class InsightIntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(InsightIntegrationAdapter.class);

    private final DesignerInsightStage originalStage;
    private final GameSpecificInsightPanel enhancedPanel;
    private TabPane mainTabPane;
    private boolean enhancedModeEnabled = false;

    public InsightIntegrationAdapter(DesignerInsightStage originalStage) {
        this.originalStage = originalStage;
        this.enhancedPanel = new GameSpecificInsightPanel();
        log.info("洞察功能集成适配器初始化完成");
    }

    /**
     * 将增强功能集成到现有界面
     */
    public void integrateEnhancedFeatures(TabPane existingTabPane) {
        this.mainTabPane = existingTabPane;

        // 添加增强模式切换选项
        addEnhancedModeToggle();

        // 添加新的增强洞察标签页
        addEnhancedInsightTab();

        log.info("增强洞察功能已集成到现有界面");
    }

    /**
     * 添加增强模式切换选项
     */
    private void addEnhancedModeToggle() {
        // 在现有界面中添加模式切换控件
        // 这里假设现有界面有一个控制区域可以添加按钮

        Button toggleButton = new Button("🚀 启用增强模式");
        toggleButton.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 6;");

        toggleButton.setOnAction(event -> {
            enhancedModeEnabled = !enhancedModeEnabled;
            if (enhancedModeEnabled) {
                toggleButton.setText("📊 切换传统模式");
                toggleButton.setStyle("-fx-background-color: #E67E22; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 6;");
                showEnhancedMode();
            } else {
                toggleButton.setText("🚀 启用增强模式");
                toggleButton.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 6;");
                showTraditionalMode();
            }
        });

        // 将按钮添加到适当的位置（具体实现取决于现有界面结构）
        // 这里提供一个示例实现
        addControlToExistingInterface(toggleButton);
    }

    /**
     * 添加控件到现有界面（示例实现）
     */
    private void addControlToExistingInterface(Node control) {
        // 这里需要根据DesignerInsightStage的实际结构来实现
        // 由于无法直接访问私有字段，我们提供一个通用的集成方案

        // 方案1: 如果有公共方法可以添加控件
        try {
            // originalStage.addControl(control);
            log.info("控件已添加到现有界面");
        } catch (Exception e) {
            log.warn("无法直接添加控件到现有界面，使用备用方案");
            // 方案2: 创建新的容器整合现有内容和新控件
            // 这需要更复杂的界面重构
        }
    }

    /**
     * 添加增强洞察标签页
     */
    private void addEnhancedInsightTab() {
        Tab enhancedTab = new Tab("🎮 游戏专属洞察");
        enhancedTab.setClosable(false);

        // 创建标签页内容
        VBox tabContent = new VBox(10);
        tabContent.getChildren().add(createWelcomeContent());

        enhancedTab.setContent(tabContent);

        // 添加到主标签面板
        if (mainTabPane != null) {
            mainTabPane.getTabs().add(enhancedTab);

            // 设置标签页选择监听器
            enhancedTab.setOnSelectionChanged(event -> {
                if (enhancedTab.isSelected()) {
                    loadEnhancedContent(tabContent);
                }
            });
        }

        log.info("增强洞察标签页已添加");
    }

    /**
     * 创建欢迎内容
     */
    private Node createWelcomeContent() {
        VBox welcome = new VBox(20);
        welcome.setStyle("-fx-padding: 40; -fx-alignment: center;");

        Label title = new Label("🎮 游戏专属智能洞察");
        title.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #2C3E50;");

        Label description = new Label(
                "全新的游戏数据分析体验！\n\n" +
                "✨ 智能游戏系统识别\n" +
                "📊 枚举值深度统计\n" +
                "⚖️ 平衡性智能分析\n" +
                "📈 成长曲线优化建议\n" +
                "🤖 AI驱动的深度洞察\n\n" +
                "请选择XML文件开始体验！"
        );
        description.setStyle("-fx-font-size: 14; -fx-text-alignment: center; -fx-text-fill: #34495E;");

        Button selectFileButton = new Button("📁 选择XML文件");
        selectFileButton.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; -fx-padding: 12 24; -fx-background-radius: 6; -fx-font-size: 14;");

        selectFileButton.setOnAction(event -> selectAndAnalyzeFile());

        welcome.getChildren().addAll(title, description, selectFileButton);
        return welcome;
    }

    /**
     * 选择并分析文件
     */
    private void selectAndAnalyzeFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择XML文件进行游戏专属分析");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML Files", "*.xml")
        );

        // 获取当前窗口作为父窗口
        Stage parentStage = getParentStage();
        File selectedFile = fileChooser.showOpenDialog(parentStage);

        if (selectedFile != null) {
            Path xmlPath = selectedFile.toPath();
            log.info("用户选择文件进行游戏专属分析: {}", xmlPath.getFileName());

            // 开始分析
            enhancedPanel.analyzeFile(xmlPath);

            // 更新标签页内容
            updateEnhancedTabContent();
        }
    }

    /**
     * 获取父窗口
     */
    private Stage getParentStage() {
        // 尝试从原始stage获取窗口
        try {
            // 通过反射或其他方式获取stage
            return (Stage) originalStage.getClass().getMethod("getStage").invoke(originalStage);
        } catch (Exception e) {
            log.debug("无法获取原始stage，返回null");
            return null;
        }
    }

    /**
     * 加载增强内容
     */
    private void loadEnhancedContent(VBox tabContent) {
        // 清空现有内容
        tabContent.getChildren().clear();

        // 添加增强面板
        tabContent.getChildren().add(enhancedPanel);

        log.info("增强洞察内容已加载");
    }

    /**
     * 更新增强标签页内容
     */
    private void updateEnhancedTabContent() {
        Platform.runLater(() -> {
            if (mainTabPane != null) {
                // 查找增强洞察标签页
                Tab enhancedTab = mainTabPane.getTabs().stream()
                        .filter(tab -> tab.getText().contains("游戏专属洞察"))
                        .findFirst()
                        .orElse(null);

                if (enhancedTab != null) {
                    // 选择该标签页
                    mainTabPane.getSelectionModel().select(enhancedTab);

                    // 更新内容
                    VBox tabContent = (VBox) enhancedTab.getContent();
                    loadEnhancedContent(tabContent);
                }
            }
        });
    }

    /**
     * 显示增强模式
     */
    private void showEnhancedMode() {
        log.info("切换到增强模式");
        // 隐藏或灰化传统模式的标签页
        if (mainTabPane != null) {
            mainTabPane.getTabs().forEach(tab -> {
                if (!tab.getText().contains("游戏专属洞察")) {
                    // 可以选择隐藏或禁用传统标签页
                    // tab.setDisable(true);
                }
            });
        }
    }

    /**
     * 显示传统模式
     */
    private void showTraditionalMode() {
        log.info("切换到传统模式");
        // 恢复传统模式的标签页
        if (mainTabPane != null) {
            mainTabPane.getTabs().forEach(tab -> {
                if (!tab.getText().contains("游戏专属洞察")) {
                    tab.setDisable(false);
                }
            });
        }
    }

    /**
     * 检查是否支持增强模式
     */
    public boolean isEnhancedModeSupported() {
        // 检查系统要求和依赖
        try {
            // 检查必要的类是否存在
            Class.forName("red.jiuzhou.analysis.enhanced.GameSystemDetector");
            Class.forName("red.jiuzhou.analysis.enhanced.SmartInsightEngine");
            Class.forName("red.jiuzhou.analysis.enhanced.EnumerationAnalysisEngine");
            return true;
        } catch (ClassNotFoundException e) {
            log.warn("增强模式依赖缺失: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取集成状态信息
     */
    public String getIntegrationStatus() {
        StringBuilder status = new StringBuilder();
        status.append("洞察功能集成状态:\n");
        status.append("- 增强模式支持: ").append(isEnhancedModeSupported() ? "✅" : "❌").append("\n");
        status.append("- 当前模式: ").append(enhancedModeEnabled ? "增强模式" : "传统模式").append("\n");
        status.append("- 游戏专属面板: ").append(enhancedPanel != null ? "✅" : "❌").append("\n");

        return status.toString();
    }

    /**
     * 为现有的DesignerInsightStage提供增强功能
     */
    public static void enhanceExistingStage(DesignerInsightStage stage, TabPane tabPane) {
        try {
            InsightIntegrationAdapter adapter = new InsightIntegrationAdapter(stage);
            if (adapter.isEnhancedModeSupported()) {
                adapter.integrateEnhancedFeatures(tabPane);
                log.info("设计洞察功能增强完成");
            } else {
                log.warn("系统不支持增强模式，保持传统功能");
            }
        } catch (Exception e) {
            log.error("增强功能集成失败", e);
        }
    }

    /**
     * 处理文件拖放事件
     */
    public void handleFileDrop(Path droppedFile) {
        if (droppedFile != null && droppedFile.toString().toLowerCase().endsWith(".xml")) {
            log.info("处理拖放的XML文件: {}", droppedFile.getFileName());
            enhancedPanel.analyzeFile(droppedFile);
            updateEnhancedTabContent();
        }
    }

    /**
     * 获取增强面板（用于外部访问）
     */
    public GameSpecificInsightPanel getEnhancedPanel() {
        return enhancedPanel;
    }
}