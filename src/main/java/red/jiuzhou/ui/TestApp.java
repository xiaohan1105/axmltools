package red.jiuzhou.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * 简单的测试应用
 * 用于验证 Java/JavaFX 环境是否正确配置
 */
public class TestApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label label = new Label("Hello, DB_XML Tool!");
        label.setStyle("-fx-font-size: 18px; -fx-padding: 20px;");

        VBox root = new VBox(label);
        root.setStyle("-fx-alignment: center; -fx-background-color: #f0f0f0;");

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("Test App - DB_XML Tool");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}