package red.jiuzhou.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * 智能状态栏组件
 * 提供状态信息、进度显示和消息提醒功能
 */
public class StatusBar {

    private final HBox panel;
    private final Label statusLabel;
    private final Label messageLabel;
    private final ProgressBar progressBar;

    // 消息自动隐藏定时器
    private Timeline messageTimer;

    public StatusBar() {
        this.panel = new HBox();
        this.statusLabel = new Label("就绪");
        this.messageLabel = new Label();
        this.progressBar = new ProgressBar();

        initializeComponents();
        applyStyles();
    }

    private void initializeComponents() {
        panel.getStyleClass().add("status-bar");
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setSpacing(12);
        panel.setPadding(new Insets(8, 16, 8, 16));

        // 状态标签
        statusLabel.getStyleClass().add("status-text");

        // 弹性空间
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 消息标签
        messageLabel.getStyleClass().add("message-text");
        messageLabel.setVisible(false);

        // 进度条
        progressBar.getStyleClass().add("status-progress");
        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);

        panel.getChildren().addAll(
            statusLabel,
            spacer,
            messageLabel,
            progressBar
        );
    }

    private void applyStyles() {
        panel.getStylesheets().add("/modern-theme.css");
    }

    /**
     * 设置状态文本
     */
    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    /**
     * 显示消息（带样式）
     */
    public void showMessage(String message, String type) {
        showMessage(message, type, 3000); // 默认3秒后自动隐藏
    }

    /**
     * 显示消息（指定显示时间）
     */
    public void showMessage(String message, String type, int durationMs) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeIf(cls -> cls.startsWith("message-"));
        messageLabel.getStyleClass().add("message-" + type);
        messageLabel.setVisible(true);

        // 淡入动画
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), messageLabel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // 自动隐藏
        if (messageTimer != null) {
            messageTimer.stop();
        }

        messageTimer = new Timeline(new KeyFrame(Duration.millis(durationMs), e -> hideMessage()));
        messageTimer.play();
    }

    /**
     * 隐藏消息
     */
    public void hideMessage() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), messageLabel);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> messageLabel.setVisible(false));
        fadeOut.play();
    }

    /**
     * 显示进度条
     */
    public void showProgress() {
        progressBar.setVisible(true);
        progressBar.setProgress(0);
    }

    /**
     * 更新进度
     */
    public void updateProgress(double progress) {
        progressBar.setProgress(progress);
    }

    /**
     * 隐藏进度条
     */
    public void hideProgress() {
        progressBar.setVisible(false);
    }

    /**
     * 显示成功消息
     */
    public void showSuccess(String message) {
        showMessage("[成功] " + message, "success");
    }

    /**
     * 显示警告消息
     */
    public void showWarning(String message) {
        showMessage("[警告] " + message, "warning");
    }

    /**
     * 显示错误消息
     */
    public void showError(String message) {
        showMessage("[错误] " + message, "error");
    }

    /**
     * 显示信息消息
     */
    public void showInfo(String message) {
        showMessage("[信息] " + message, "info");
    }

    public HBox getPanel() {
        return panel;
    }
}