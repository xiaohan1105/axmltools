package red.jiuzhou.dbxml;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.YmlConfigUtil;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dream
 */
public class DirectoryManagerDialog {

    private final ObservableList<String> directoryList = FXCollections.observableArrayList();
    private final ListView<CheckBox> listView = new ListView<>();

    private final Runnable refreshCallback;

    public DirectoryManagerDialog(Runnable refreshCallback) {
        this.refreshCallback = refreshCallback;
        loadDirectories();
    }

    public void show(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("目录管理器");

        // 添加目录按钮
        Button addButton = new Button("添加目录");
        addButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择一个目录");
            File selectedDir = chooser.showDialog(dialog);
            if (selectedDir != null && !directoryList.contains(selectedDir.getAbsolutePath())) {
                directoryList.add(selectedDir.getAbsolutePath());
                saveDirectories();
                refreshListView();
                refreshCallback.run(); // 通知主页面刷新 TreeView
            }
        });

        // 删除目录按钮
        Button deleteButton = new Button("删除选中");
        deleteButton.setOnAction(e -> {
            List<String> toRemove = listView.getItems().stream()
                    .filter(CheckBox::isSelected)
                    .map(CheckBox::getText)
                    .collect(Collectors.toList());
            directoryList.removeAll(toRemove);
            saveDirectories();
            refreshListView();
            refreshCallback.run();
        });

        // 关闭按钮
        Button closeButton = new Button("关闭");
        closeButton.setOnAction(e -> dialog.close());

        // 列表展示
        refreshListView();

        HBox buttonBar = new HBox(10, addButton, deleteButton, closeButton);
        buttonBar.setPadding(new Insets(10));

        VBox layout = new VBox(10, new Label("目录列表:"), listView, buttonBar);
        layout.setPadding(new Insets(10));
        Scene scene = new Scene(layout, 500, 400);
        dialog.setScene(scene);
        dialog.initOwner(owner);
        dialog.showAndWait();
    }

    /** 从文件加载目录 */
    private void loadDirectories() {
        String xmlPath = YamlUtils.getProperty("xmlPath." + DatabaseUtil.getDbName());
        String xmlPathStr = xmlPath ==  null ? "" : xmlPath;
        if(StringUtils.hasLength(xmlPathStr)){
            directoryList.setAll(Arrays.asList(xmlPathStr.split(",")));
        }
    }

    /** 刷新 UI 列表 */
    private void refreshListView() {
        listView.getItems().clear();
        for (String path : directoryList) {
            CheckBox checkBox = new CheckBox(path);
            listView.getItems().add(checkBox);
        }
    }

    /** 保存目录列表 */
    private void saveDirectories(){
        YmlConfigUtil.updateResourcesYml("xmlPath." + DatabaseUtil.getDbName(), String.join(",", directoryList));
    }

    /** 外部可调用获取目录列表 */
    public List<String> getDirectories() {

        return directoryList;
    }
}
