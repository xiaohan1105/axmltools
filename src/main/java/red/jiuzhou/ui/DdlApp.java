package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author dream
 */
public class DdlApp {

    private static final Logger log = LoggerFactory.getLogger(DdlApp.class);

    private ComboBox<String> xmlFileCombo;
    private TextArea resultArea;

    public void show(Stage primaryStage) {
        Stage stage = new Stage();
        stage.setTitle("DDL生成器");
        stage.initOwner(primaryStage);

        xmlFileCombo = new ComboBox<>();
        xmlFileCombo.setPrefWidth(840);
        xmlFileCombo.setPromptText("请选择 XML 文件");

        resultArea = new TextArea();
        resultArea.setPrefHeight(300);
        resultArea.setWrapText(true);

        // 布局设置
        GridPane controlRow = new GridPane();
        controlRow.setHgap(10);
        controlRow.setVgap(5);
        controlRow.setPadding(new Insets(10));
        controlRow.add(new Label("xml文件:"), 0, 0);
        controlRow.add(xmlFileCombo, 1, 0);

        Button convertButton = new Button("生成");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        HBox buttonBox = new HBox(10, convertButton, spinner);
        buttonBox.setPadding(new Insets(0, 0, 10, 10));

        // 点击生成
        convertButton.setOnAction(e -> {
            spinner.setVisible(true);
            resultArea.clear();

            new Thread(() -> {
                try {
                    String selectedFile = xmlFileCombo.getValue();
                    if ( !StringUtils.hasLength(selectedFile)){
                        Platform.runLater(() -> resultArea.setText("请选择 XML 文件！"));
                        return;
                    }
                    log.info("选择文件：{}", selectedFile);
                    String sqlFilePath = XmlProcess.parseOneXml(selectedFile);
                    //执行sql文件
                    log.info("执行sql文件：{}", sqlFilePath);
                    DatabaseUtil.executeSqlScript(sqlFilePath);
                    log.info("生成DDL成功");
                    Platform.runLater(() -> resultArea.setText(FileUtil.readUtf8String(sqlFilePath)));
                } catch (Exception ex) {
                    log.error("生成DDL出错: {}", JSONRecord.getErrorMsg(ex));
                    Platform.runLater(() -> resultArea.setText("生成DDL失败，请检查日志！"));
                } finally {
                    Platform.runLater(() -> spinner.setVisible(false));
                }
            }).start();
        });

        VBox root = new VBox(10, controlRow, buttonBox, resultArea);
        Scene scene = new Scene(root, 1300, 550);
        stage.setScene(scene);
        stage.show();

        // 异步加载 XML 文件列表
        new Thread(this::loadTableNames).start();
    }

    private void loadTableNames() {
        try {
            String cltDataPath = YamlUtils.getProperty("file.cltDataPath");
            String svrDataPath = YamlUtils.getProperty("file.svrDataPath");

            List<String> dataPaths = Arrays.asList(cltDataPath, svrDataPath);
            List<String> xmlFiles = new ArrayList<>();

            for (String path : dataPaths) {
                List<String> files = FileUtil.loopFiles(path).stream()
                        .filter(file -> file.getName().endsWith(".xml"))
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());
                xmlFiles.addAll(files);
            }

            Platform.runLater(() -> {
                if (!xmlFiles.isEmpty()) {
                    xmlFileCombo.getItems().setAll(xmlFiles);
                    enableComboBoxFilter(xmlFileCombo, xmlFiles);
                } else {
                    log.warn("未找到任何 XML 文件");
                }
            });

        } catch (Exception e) {
            log.error("加载文件失败: {}", JSONRecord.getErrorMsg(e));
        }
    }

    private void enableComboBoxFilter(ComboBox<String> comboBox, List<String> allItems) {
        if (allItems == null || allItems.isEmpty()) {
            log.warn("ComboBox items list is empty, skipping filter initialization.");
            return;
        }

        comboBox.setEditable(true);
        TextField editor = comboBox.getEditor();

        Platform.runLater(() -> comboBox.getItems().setAll(allItems));

        editor.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                Platform.runLater(() -> comboBox.getItems().setAll(allItems));
            } else {
                List<String> filtered = allItems.stream()
                        .filter(item -> item.toLowerCase().contains(newVal.toLowerCase()))
                        .collect(Collectors.toList());

                Platform.runLater(() -> comboBox.getItems().setAll(filtered));
            }
            comboBox.show();
        });
    }
}
