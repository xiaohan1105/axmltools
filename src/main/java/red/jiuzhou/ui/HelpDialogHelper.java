package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import red.jiuzhou.util.YamlUtils;
/**
 * @className: red.jiuzhou.ui.HelpDialogHelper.java
 * @description: 帮助对话框
 * @author: yanxq
 * @date:  2025-04-15 20:43
 * @version V1.0
 */
public class HelpDialogHelper {

    // 创建一个带有帮助功能的按钮
    public static Button createHelpButton() {
        Button helpButton = new Button("帮助");
        String filePath = YamlUtils.getProperty("file.homePath") + "README";
        String helpMessage = "";
        if (!FileUtil.exist(filePath)) {
            helpMessage = "帮助文件不存在: " + filePath;
        }else{
            // 定义帮助步骤内容
            helpMessage = FileUtil.readUtf8String(filePath);
        }


        // 添加按钮点击事件监听器
        String finalHelpMessage = helpMessage;
        helpButton.setOnAction(event -> {
            // 创建 Alert 对话框
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("帮助");
            alert.setHeaderText("使用说明");
            alert.setContentText(finalHelpMessage);

            // 获取 DialogPane 并设置首选宽高
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.setPrefWidth(1000); // 设置宽度为 600 像素
            dialogPane.setPrefHeight(800); // 设置高度为 400 像素

            // 显示对话框
            alert.showAndWait();
        });

        return helpButton;
    }
}