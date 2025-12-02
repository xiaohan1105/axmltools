package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.YmlConfigUtil;
import red.jiuzhou.xmltosql.XmlProcess;
import javafx.geometry.Insets;
import java.io.File;
/**
 * @className: red.jiuzhou.ui.InitDialog.java
 * @description: 初始化窗口
 * @author: yanxq
 * @date:  2025-04-15 20:43
 * @version V1.0
 */
public class InitDialog {

    private static String clientPath  = YamlUtils.getProperty("file.cltDataPath");
    private static String serverPath = YamlUtils.getProperty("file.svrDataPath");

    private static String exportPath = YamlUtils.getProperty("file.exportDataPath");

    private static String worldPath = YamlUtils.getProperty("file.worldSvrDataPath");
    /**
     * 显示对话框
     *
     * @param ownerStage 主窗口的 Stage
     */
    public static void showDialog(Stage ownerStage) {

        // 创建对话框窗口
        Stage dialogStage = new Stage();
        // 设置主窗口为父窗口
        dialogStage.initOwner(ownerStage);
        // 设置为模态对话框
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle("数据初始化");

        // 创建按钮
        Button cltBtn = new Button("选择客户端路径   ");
        Label cltPathLabel = new Label("当前路径: " + clientPath);

        Button svrBtn = new Button("选择服务端路径   ");
        Label svrPathLabel = new Label("当前路径: " + serverPath);

        Button exportBtn = new Button("选择数据导出路径");
        Label exportPathLabel = new Label("当前路径: " + exportPath);

        Button worldBtn = new Button("选择world路径");
        Label worldPathLabel = new Label("当前路径: " + worldPath);

        Button initBtn = new Button("项目配置初始化");

        // 默认禁用初始化按钮
        initBtn.setDisable(true);

        // 添加 Tooltip 提示
        cltBtn.setTooltip(new Tooltip("点击选择客户端的工作目录"));
        svrBtn.setTooltip(new Tooltip("点击选择服务端的工作目录"));
        exportBtn.setTooltip(new Tooltip("点击选择xml导出工作目录"));
        worldBtn.setTooltip(new Tooltip("点击选择world工作目录"));
        initBtn.setTooltip(new Tooltip("点击初始化路径"));

        // 客户端路径选择
        cltBtn.setOnAction(e -> {
            File selectedDirectory = showDirectoryChooser(dialogStage, clientPath);
            if (selectedDirectory != null) {
                clientPath = selectedDirectory.getAbsolutePath();
                cltPathLabel.setText("当前路径: " + clientPath);
                YmlConfigUtil.updateResourcesYml("file.cltDataPath", clientPath + File.separator);
                checkAndEnableInitButton(initBtn);
            } else {
                System.out.println("客户端路径未选择！");
            }
        });

        // 服务端路径选择
        svrBtn.setOnAction(e -> {
            File selectedDirectory = showDirectoryChooser(dialogStage, serverPath);
            if (selectedDirectory != null) {
                serverPath = selectedDirectory.getAbsolutePath();
                System.out.println("服务端路径: " + serverPath);
                svrPathLabel.setText("当前路径: " + serverPath);
                YmlConfigUtil.updateResourcesYml("file.svrDataPath", serverPath + File.separator);
                checkAndEnableInitButton(initBtn);
            } else {
                System.out.println("服务端路径未选择！");
            }
        });

        // xml导出端路径选择
        exportBtn.setOnAction(e -> {
            File selectedDirectory = showDirectoryChooser(dialogStage, exportPath);
            if (selectedDirectory != null) {
                exportPath = selectedDirectory.getAbsolutePath();
                exportPathLabel.setText("当前路径: " + exportPath);
                YmlConfigUtil.updateResourcesYml("file.exportDataPath", exportPath + File.separator);
                checkAndEnableInitButton(initBtn);
            } else {
                System.out.println("xml导出路径未选择！");
            }
        });

        // xml导出端路径选择
        worldBtn.setOnAction(e -> {
            File selectedDirectory = showDirectoryChooser(dialogStage, worldPath);
            if (selectedDirectory != null) {
                worldPath = selectedDirectory.getAbsolutePath();
                worldPathLabel.setText("当前路径: " + worldPath);
                YmlConfigUtil.updateResourcesYml("file.worldSvrDataPath", worldPath + File.separator);
                checkAndEnableInitButton(initBtn);
            } else {
                System.out.println("xml导出路径未选择！");
            }
        });

        // 初始化按钮逻辑
        initBtn.setOnAction(e -> {
            YmlConfigUtil.updateResourcesYml("file.homePath", System.getProperty("user.dir") + File.separator + "src"+ File.separator +
                    "main"+ File.separator + "resources" + File.separator);
            YmlConfigUtil.updateResourcesYml("file.confPath", System.getProperty("user.dir") + File.separator + "src"+ File.separator +
                            "main"+ File.separator + "resources" + File.separator  + "CONF" + File.separator);
            showProgressDialog(dialogStage);
            initBtn.setDisable(true); // 禁用初始化按钮
        });

        // 使用 GridPane 布局，将按钮和标签对齐
        // 使用 GridPane 布局，将按钮和标签对齐
        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20));

        gridPane.add(new Label("客户端路径:"), 0, 0);
        gridPane.add(cltBtn, 1, 0);
        gridPane.add(cltPathLabel, 2, 0);

        gridPane.add(new Label("服务端路径:"), 0, 1);
        gridPane.add(svrBtn, 1, 1);
        gridPane.add(svrPathLabel, 2, 1);

        gridPane.add(new Label("导出路径:"), 0, 2);
        gridPane.add(exportBtn, 1, 2);
        gridPane.add(exportPathLabel, 2, 2);

        gridPane.add(new Label("world路径:"), 0, 3);
        gridPane.add(worldBtn, 1, 3);
        gridPane.add(worldPathLabel, 2, 3);

        // 使用 VBox 布局，将按钮上下排列
        VBox vbox = new VBox(20);
        vbox.setAlignment(Pos.CENTER);
        vbox.getChildren().addAll(gridPane, initBtn);

        // 创建场景并设置到对话框窗口
        Scene scene = new Scene(vbox, 1000, 500);
        dialogStage.setScene(scene);
        dialogStage.showAndWait(); // 阻塞主窗口，直到对话框关闭
    }

    /**
     * 显示目录选择器，并设置默认目录
     *
     * @param ownerStage 主窗口
     * @param filePath 默认目录
     * @return 用户选择的目录
     */
    private static File showDirectoryChooser(Stage ownerStage, String filePath) {

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择目录");
        // 设置默认目录
        if (FileUtil.exist(filePath) && new File(filePath).isDirectory()) {
            directoryChooser.setInitialDirectory(new File(filePath));
        } else {
            // 如果默认目录不存在，则使用用户主目录
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }

        // 显示目录选择器
        return directoryChooser.showDialog(ownerStage);
    }

    /**
     * 检查是否满足启用初始化按钮的条件
     *
     * @param initBtn 初始化按钮
     */
    private static void checkAndEnableInitButton(Button initBtn) {
//        if (clientPath != null && serverPath != null) {
//            // 启用初始化按钮
//            initBtn.setDisable(false);
//        } else {
//            // 禁用初始化按钮
//            initBtn.setDisable(true);
//        }
    }

    /**
     * 显示进度框
     *
     * @param ownerStage 父窗口
     */
    private static void showProgressDialog(Stage ownerStage) {
        // 创建进度框窗口
        Stage progressDialog = new Stage();
        progressDialog.initOwner(ownerStage);
        progressDialog.initModality(Modality.APPLICATION_MODAL);
        progressDialog.setTitle("初始化进度");

        // 创建进度条和描述标签
        ProgressBar progressBar = new ProgressBar(0);
        // 设置进度条的首选宽度
        progressBar.setPrefWidth(250);
        // 如果需要，也可以设置最大宽度// 允许进度条根据容器自动扩展
        progressBar.setMaxWidth(Double.MAX_VALUE);
        Label progressLabel = new Label("正在初始化...");

        // 使用 VBox 布局
        VBox vbox = new VBox(10);
        vbox.setAlignment(Pos.CENTER);
        vbox.getChildren().addAll(progressBar, progressLabel);

        // 创建场景并设置到进度框窗口
        Scene progressScene = new Scene(vbox, 900, 250);
        progressDialog.setScene(progressScene);
        progressDialog.show();

        // 后台任务线程
        new Thread(() -> {
            // 在新线程中执行 XML 导入
            Thread importThread = new Thread(XmlProcess::init);
            importThread.start();
            while (true) {
                try {
                    Thread.sleep(200);
                    double progress = XmlProcess.getProgress();
                    // 使用 Platform.runLater 更新 UI
                    javafx.application.Platform.runLater(() -> {
                        progressBar.setProgress(progress);
                        progressLabel.setText(String.format("进度: %.1f%% - %s", progress * 100, XmlProcess.msg));
                        //progressLabel.setText(XmlProcess.msg);
                    });
                    if (progress == 1) {
                        break;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            // 初始化完成后关闭进度框
            javafx.application.Platform.runLater(progressDialog::close);
            System.out.println("初始化完成！");
        }).start();


    }
}