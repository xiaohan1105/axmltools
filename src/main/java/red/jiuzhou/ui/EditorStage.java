package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @className: red.jiuzhou.ui.EditorStage.java
 * @description: JSON 编辑器窗口
 * @author: yanxq
 * @date:  2025-04-15 20:42
 * @version V1.0
 */
public class EditorStage {

    private static final Logger log = LoggerFactory.getLogger(EditorStage.class);
    // 打开 JSON 编辑窗口
    public static void openJsonEditorWindow(String filePath) {
        if(!FileUtil.exist(filePath)){
            FileUtil.touch(filePath);
        }
        // 读取 JSON 文件
        String content = FileUtil.readUtf8String(filePath);

        // 创建新窗口
        Stage editorStage = new Stage();
        editorStage.setTitle("编辑" + filePath);

        // 创建一个 TextArea 来显示和编辑 JSON 内容
        TextArea textArea = new TextArea();
        textArea.setText(content);
        textArea.setWrapText(true);

        // 创建保存按钮
        Button formatButton = new Button("格式化");
        Button saveButton = new Button("保存");
        saveButton.setOnAction(e -> {
            FileUtil.writeUtf8String(textArea.getText(), filePath);
            // 保存后关闭窗口
            editorStage.close();
        });

        formatButton.setOnAction(e -> {
            try {
                String text = textArea.getText();

                String formattedJson = JSON.toJSONString(JSON.parse(text), SerializerFeature.PrettyFormat);

                textArea.setText(formattedJson);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // 创建一个 HBox 来将按钮放到右侧
        HBox buttonBox = new HBox(10, formatButton, saveButton);
        // 将按钮对齐到右侧
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // 布局
        // 设置 TextArea 占满剩余空间
        VBox editorLayout = new VBox(10, textArea, buttonBox);
        // 设置 TextArea 占满剩余空间
        VBox.setVgrow(textArea, Priority.ALWAYS);
        Scene editorScene = new Scene(editorLayout, 960, 660);
        editorStage.setScene(editorScene);
        editorStage.show();
    }

}
