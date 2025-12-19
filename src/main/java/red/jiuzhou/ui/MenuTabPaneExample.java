package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ui.components.SearchableTreeView;
import red.jiuzhou.util.AIAssistant;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @className: red.jiuzhou.ui.MenuTabPaneExample.java
 * @description: 菜单栏
 * @author: yanxq
 * @date:  2025-04-15 20:43
 * @version V1.0
 */
public class MenuTabPaneExample {

    private static final Logger log = LoggerFactory.getLogger(MenuTabPaneExample.class);

    private AIAssistant aiAssistant;
    private FeatureGateway featureGateway;

    /**
     * 功能网关接口 - 用于扩展功能集成
     */
    public interface FeatureGateway {
        boolean supportsDesignerInsight();
        void openDesignerInsight(Path path);
    }

    public void setAiAssistant(AIAssistant aiAssistant) {
        this.aiAssistant = aiAssistant;
    }

    public AIAssistant getAiAssistant() {
        return aiAssistant;
    }

    public void setFeatureGateway(FeatureGateway featureGateway) {
        this.featureGateway = featureGateway;
    }

    public FeatureGateway getFeatureGateway() {
        return featureGateway;
    }

    // 创建 TabPane
    public TabPane createTopPane() {
        TabPane tabPane = new TabPane();

        // 右键菜单
        ContextMenu contextMenu = new ContextMenu();

        // 关闭当前
        MenuItem closeCurrent = new MenuItem("关闭当前");
        closeCurrent.setOnAction(event -> {
            Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != null) {
                tabPane.getTabs().remove(selectedTab);
            }
        });

        // 关闭所有
        MenuItem closeAll = new MenuItem("关闭所有");
        closeAll.setOnAction(event -> {
            tabPane.getTabs().clear();
        });

        // 关闭其他
        MenuItem closeOthers = new MenuItem("关闭其他");
        closeOthers.setOnAction(event -> {
            Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != null) {
                tabPane.getTabs().retainAll(selectedTab);
            }
        });

        // 添加菜单项
        contextMenu.getItems().addAll(closeCurrent, closeOthers, closeAll);

        // 右键点击时显示菜单
        tabPane.setOnContextMenuRequested(event -> {
            if (!tabPane.getTabs().isEmpty()) {
                contextMenu.hide(); // 先隐藏已有菜单，防止多个菜单同时存在
                contextMenu.show(tabPane, event.getScreenX(), event.getScreenY());
            }
        });
        return tabPane;
    }

    // 创建左侧菜单
    public TreeView<String> createLeftMenu(String json, TabPane tabPane) {
        JSONObject rootNode = JSONObject.parseObject(json);

        TreeItem<String> rootItem = new TreeItem<>(rootNode.getString("name"));
        TreeView<String> treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(false);
        // 递归创建菜单项
        if (rootNode.containsKey("children")) {
            createMenuItems(rootNode.getJSONArray("children"), rootItem, treeView);
        }

        // 设置选择事件
        treeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // 如果是叶子节点，才创建Tab
                if (newValue.getChildren().isEmpty()) {
                    createTab(tabPane, newValue.getValue(), getTabFullPath(newValue));
                }
            }
        });

        return treeView;
    }

    /**
     * 创建可搜索的左侧菜单（增强版）
     * 支持实时搜索过滤、高亮匹配、上下导航
     *
     * @param json    菜单JSON配置
     * @param tabPane Tab容器
     * @return 可搜索的菜单树组件
     */
    public SearchableTreeView<String> createSearchableLeftMenu(String json, TabPane tabPane) {
        JSONObject rootNode = JSONObject.parseObject(json);

        TreeItem<String> rootItem = new TreeItem<>(rootNode.getString("name"));

        // 递归创建菜单项
        if (rootNode.containsKey("children")) {
            createMenuItemsForSearchable(rootNode.getJSONArray("children"), rootItem);
        }

        // 创建可搜索树视图
        SearchableTreeView<String> searchableTree = new SearchableTreeView<>();
        searchableTree.setRoot(rootItem);
        searchableTree.setShowRoot(false);

        // 设置搜索匹配器（支持模糊搜索）
        searchableTree.setSearchMatcher((item, keyword) -> {
            if (item == null) return false;
            String itemLower = item.toLowerCase();
            String keywordLower = keyword.toLowerCase();
            // 支持拼音首字母匹配（简化版：只做包含匹配）
            return itemLower.contains(keywordLower);
        });

        // 设置选择事件
        searchableTree.setOnItemSelected(selected -> {
            if (selected != null && selected.getValue() != null) {
                // 如果是叶子节点，才创建Tab
                if (selected.getChildren().isEmpty()) {
                    createTab(tabPane, selected.getValue(), getTabFullPath(selected));
                }
            }
        });

        // 设置双击事件
        searchableTree.setOnItemDoubleClicked(item -> {
            if (item != null && item.getValue() != null && item.getChildren().isEmpty()) {
                createTab(tabPane, item.getValue(), getTabFullPath(item));
            }
        });

        // 设置路径解析器（用于右键菜单和机制检测）
        searchableTree.setPathResolver(this::getTabFullPath);

        // 启用机制过滤功能（显示机制颜色标记和增强右键菜单）
        searchableTree.enableMechanismFilter(true);

        return searchableTree;
    }

    /**
     * 递归创建菜单项（用于可搜索树）
     */
    private void createMenuItemsForSearchable(JSONArray children, TreeItem<String> parentItem) {
        for (int i = 0; i < children.size(); i++) {
            JSONObject childNode = children.getJSONObject(i);
            TreeItem<String> item = new TreeItem<>(childNode.getString("name"));
            parentItem.getChildren().add(item);
            // 递归调用
            if (childNode.containsKey("children")) {
                createMenuItemsForSearchable(childNode.getJSONArray("children"), item);
            }
        }
    }


    // 递归创建菜单项并为每个 TreeItem 添加右键菜单
    private void createMenuItems(JSONArray children, TreeItem<String> parentItem, TreeView<String> treeView) {
        for (int i = 0; i < children.size(); i++) {
            JSONObject childNode = children.getJSONObject(i);
            TreeItem<String> item = new TreeItem<>(childNode.getString("name"));
            parentItem.getChildren().add(item);
            // 递归调用
            if (childNode.containsKey("children")) {
                createMenuItems(childNode.getJSONArray("children"), item, treeView);
            }

        }
    }
    // 创建新的Tab
    private void createTab(TabPane tabPane, String menuItem, String fullPath) {
        boolean tabExists = false;
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().equals(menuItem)) {
                tabExists = true;
                tab.setUserData(fullPath);
                tabPane.getSelectionModel().select(tab);
                break;
            }
        }

        if (!tabExists) {
            Tab newTab = new Tab(menuItem, new Label(""));
            newTab.setUserData(fullPath);
            tabPane.getTabs().add(newTab);
            tabPane.getSelectionModel().select(newTab);

        }
    }
    private String getTabFullPath(TreeItem<String> treeItem) {
        return  getParetnPath(treeItem, treeItem.getValue());
    }

    private String getParetnPath(TreeItem<String> treeItem, String cpath){
        TreeItem<String> parentTreeItem = treeItem.getParent();
        if(parentTreeItem != null){
            String path = parentTreeItem.getValue();
            cpath = path + File.separator + cpath;
            return getParetnPath(parentTreeItem, cpath);

        }
        return cpath.replace("Root" + File.separator, "");
    }

    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误提示");
        alert.setHeaderText("发生异常");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 复制选中项的路径到剪贴板
     */
    public void copySelectionPath(TreeView<String> treeView) {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String path = getFullPath(selected);
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(path);
            clipboard.setContent(content);
            log.info("复制路径到剪贴板: {}", path);
        }
    }

    /**
     * 在Tab中打开选中项
     */
    public void openSelectionInTab(TreeView<String> treeView, TabPane tabPane) {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected != null && selected.isLeaf()) {
            String path = getFullPath(selected);
            log.info("在Tab中打开: {}", path);
            // TODO: 实现在Tab中打开文件
        }
    }

    /**
     * 使用系统默认程序打开选中项
     */
    public void openSelectionWithDesktop(TreeView<String> treeView) {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                String path = getFullPath(selected);
                java.awt.Desktop.getDesktop().open(new File(path));
            } catch (Exception e) {
                log.error("打开文件失败", e);
                showError("打开文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 刷新目录树
     */
    public void refreshTree(TreeView<String> treeView) {
        log.info("刷新目录树");
        // TODO: 实现刷新逻辑
    }

    /**
     * 在资源管理器中显示选中项
     */
    public void revealSelection(TreeView<String> treeView) {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                String path = getFullPath(selected);
                File file = new File(path);
                if (file.exists()) {
                    java.awt.Desktop.getDesktop().open(file.getParentFile());
                }
            } catch (Exception e) {
                log.error("打开目录失败", e);
                showError("打开目录失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取TreeItem的完整路径
     */
    private String getFullPath(TreeItem<String> treeItem) {
        return getParetnPath(treeItem, treeItem.getValue());
    }
}
