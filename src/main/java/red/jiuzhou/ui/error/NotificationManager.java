package red.jiuzhou.ui.error;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通知管理器
 * 提供优雅的UI通知显示功能
 *
 * @author Claude Code Enhanced
 * @version 2.0
 */
public class NotificationManager {

    private final Stage ownerStage;
    private final Stage notificationStage;
    private final VBox notificationContainer;

    // 通知ID生成器
    private final AtomicInteger notificationIdGenerator = new AtomicInteger(0);

    // 活跃通知映射
    private final Map<Integer, Notification> activeNotifications = new ConcurrentHashMap<>();

    // 最大同时显示的通知数量
    private static final int MAX_VISIBLE_NOTIFICATIONS = 5;

    // 通知类型样式
    private static final Map<NotificationType, String> NOTIFICATION_STYLES = new HashMap<>();
    static {
        NOTIFICATION_STYLES.put(NotificationType.SUCCESS,
            "-fx-background: linear-gradient(to right, #10b981, #059669); -fx-text-fill: white;");
        NOTIFICATION_STYLES.put(NotificationType.ERROR,
            "-fx-background: linear-gradient(to right, #ef4444, #dc2626); -fx-text-fill: white;");
        NOTIFICATION_STYLES.put(NotificationType.WARNING,
            "-fx-background: linear-gradient(to right, #f59e0b, #d97706); -fx-text-fill: white;");
        NOTIFICATION_STYLES.put(NotificationType.INFO,
            "-fx-background: linear-gradient(to right, #3b82f6, #2563eb); -fx-text-fill: white;");
    }

    public NotificationManager(Stage ownerStage) {
        this.ownerStage = ownerStage;
        this.notificationStage = createNotificationStage();
        this.notificationContainer = new VBox(8);
        setupNotificationContainer();
    }

    /**
     * 创建通知窗口
     */
    private Stage createNotificationStage() {
        Stage stage = new Stage();
        stage.initOwner(ownerStage);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                stage.toFront();
            }
        });

        // 设置通知窗口位置（屏幕右上角）
        javafx.geometry.Rectangle2D screenBounds =
            javafx.stage.Screen.getPrimary().getVisualBounds();
        stage.setX(screenBounds.getMaxX() - 350);
        stage.setY(screenBounds.getMinY() + 50);

        return stage;
    }

    /**
     * 设置通知容器
     */
    private void setupNotificationContainer() {
        notificationContainer.setAlignment(Pos.TOP_RIGHT);
        notificationContainer.setPadding(new Insets(10));
        notificationContainer.getStyleClass().add("notification-container");

        Scene scene = new Scene(notificationContainer);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add("/enhanced-responsive-theme.css");

        notificationStage.setScene(scene);
        notificationStage.show();
    }

    /**
     * 显示成功通知
     */
    public void showSuccess(String title, String message) {
        showNotification(NotificationType.SUCCESS, title, message, 3000);
    }

    /**
     * 显示错误通知
     */
    public void showError(String title, String message) {
        showNotification(NotificationType.ERROR, title, message, 5000);
    }

    /**
     * 显示警告通知
     */
    public void showWarning(String title, String message) {
        showNotification(NotificationType.WARNING, title, message, 4000);
    }

    /**
     * 显示信息通知
     */
    public void showInfo(String title, String message) {
        showNotification(NotificationType.INFO, title, message, 3000);
    }

    /**
     * 显示通知的主方法
     */
    private void showNotification(NotificationType type, String title, String message, long duration) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showNotification(type, title, message, duration));
            return;
        }

        int notificationId = notificationIdGenerator.incrementAndGet();

        // 创建通知节点
        HBox notificationNode = createNotificationNode(type, title, message, notificationId);

        // 检查是否超过最大显示数量
        if (activeNotifications.size() >= MAX_VISIBLE_NOTIFICATIONS) {
            removeOldestNotification();
        }

        // 添加到容器
        notificationContainer.getChildren().add(0, notificationNode);

        // 创建通知对象
        Notification notification = new Notification(notificationId, type, title, message,
            notificationNode, duration);

        activeNotifications.put(notificationId, notification);

        // 显示动画
        animateIn(notificationNode);

        // 设置自动消失
        if (duration > 0) {
            PauseTransition showDelay = new PauseTransition(Duration.millis(duration));
            showDelay.setOnFinished(e -> removeNotification(notificationId));
            showDelay.play();
        }

        // 确保通知窗口可见
        if (!notificationStage.isShowing()) {
            notificationStage.show();
        }
    }

    /**
     * 创建通知节点
     */
    private HBox createNotificationNode(NotificationType type, String title, String message, int notificationId) {
        HBox notificationBox = new HBox(12);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        notificationBox.setPadding(new Insets(12, 16, 12, 16));
        notificationBox.getStyleClass().add("notification");
        notificationBox.setStyle(NOTIFICATION_STYLES.get(type));
        notificationBox.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.2), 8, 0.3, 0, 2));

        // 图标
        ImageView iconView = createIcon(type);
        iconView.setFitWidth(20);
        iconView.setFitHeight(20);
        iconView.setPreserveRatio(true);

        // 内容区域
        VBox contentBox = new VBox(2);
        contentBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("notification-title");
        titleLabel.setStyle("-fx-font-weight: 600; -fx-font-size: 14px;");

        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("notification-message");
        messageLabel.setStyle("-fx-font-size: 12px; -fx-wrap-text: true;");
        messageLabel.setMaxWidth(250);

        contentBox.getChildren().addAll(titleLabel, messageLabel);
        HBox.setHgrow(contentBox, Priority.ALWAYS);

        // 关闭按钮
        Button closeButton = createCloseButton(notificationId);

        notificationBox.getChildren().addAll(iconView, contentBox, closeButton);

        return notificationBox;
    }

    /**
     * 创建图标
     */
    private ImageView createIcon(NotificationType type) {
        String iconPath;
        switch (type) {
            case SUCCESS:
                iconPath = "/icons/success.png";
                break;
            case ERROR:
                iconPath = "/icons/error.png";
                break;
            case WARNING:
                iconPath = "/icons/warning.png";
                break;
            case INFO:
            default:
                iconPath = "/icons/info.png";
                break;
        }

        try {
            Image iconImage = new Image(iconPath);
            return new ImageView(iconImage);
        } catch (Exception e) {
            // 如果图标文件不存在，使用文本符号
            Label textIcon = new Label(getIconSymbol(type));
            textIcon.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
            return new ImageView(textIcon.snapshot(null, null));
        }
    }

    /**
     * 获取图标符号
     */
    private String getIconSymbol(NotificationType type) {
        switch (type) {
            case SUCCESS: return "✓";
            case ERROR: return "✕";
            case WARNING: return "⚠";
            case INFO: return "ℹ";
            default: return "•";
        }
    }

    /**
     * 创建关闭按钮
     */
    private Button createCloseButton(int notificationId) {
        Button closeButton = new Button("✕");
        closeButton.getStyleClass().add("notification-close");
        closeButton.setStyle(
            "-fx-background: transparent; " +
            "-fx-border: none; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 16px; " +
            "-fx-cursor: hand; " +
            "-fx-min-width: 20px; " +
            "-fx-min-height: 20px; " +
            "-fx-max-width: 20px; " +
            "-fx-max-height: 20px;"
        );

        closeButton.setOnMouseEntered(e ->
            closeButton.setStyle("-fx-background: rgba(255,255,255,0.2); " +
                               "-fx-border: none; " +
                               "-fx-text-fill: white; " +
                               "-fx-font-size: 16px; " +
                               "-fx-cursor: hand;"));
        closeButton.setOnMouseExited(e ->
            closeButton.setStyle("-fx-background: transparent; " +
                               "-fx-border: none; " +
                               "-fx-text-fill: white; " +
                               "-fx-font-size: 16px; " +
                               "-fx-cursor: hand;"));

        closeButton.setOnAction(e -> removeNotification(notificationId));

        return closeButton;
    }

    /**
     * 移除通知
     */
    private void removeNotification(int notificationId) {
        Notification notification = activeNotifications.remove(notificationId);
        if (notification != null) {
            HBox node = notification.getNode();
            animateOut(node, () -> {
                Platform.runLater(() -> {
                    notificationContainer.getChildren().remove(node);

                    // 如果没有活跃通知，隐藏通知窗口
                    if (activeNotifications.isEmpty()) {
                        notificationStage.hide();
                    }
                });
            });
        }
    }

    /**
     * 移除最旧的通知
     */
    private void removeOldestNotification() {
        if (activeNotifications.isEmpty()) {
            return;
        }

        // 找到最早的通知
        int oldestId = activeNotifications.keySet().stream()
            .min(Integer::compareTo)
            .orElse(-1);

        if (oldestId != -1) {
            removeNotification(oldestId);
        }
    }

    /**
     * 显示动画
     */
    private void animateIn(HBox node) {
        // 初始状态
        node.setTranslateX(350);
        node.setOpacity(0);

        // 滑入并淡入动画
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.translateXProperty(), 350),
                new KeyValue(node.opacityProperty(), 0)
            ),
            new KeyFrame(Duration.millis(300),
                new KeyValue(node.translateXProperty(), 0),
                new KeyValue(node.opacityProperty(), 1)
            )
        );

        timeline.play();
    }

    /**
     * 隐藏动画
     */
    private void animateOut(HBox node, Runnable onFinished) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.translateXProperty(), 0),
                new KeyValue(node.opacityProperty(), 1)
            ),
            new KeyFrame(Duration.millis(300),
                new KeyValue(node.translateXProperty(), 350),
                new KeyValue(node.opacityProperty(), 0)
            )
        );

        timeline.setOnFinished(e -> onFinished.run());
        timeline.play();
    }

    /**
     * 清除所有通知
     */
    public void clearAllNotifications() {
        // 创建通知ID的副本以避免并发修改异常
        java.util.Set<Integer> notificationIds = new java.util.HashSet<>(activeNotifications.keySet());

        for (Integer notificationId : notificationIds) {
            removeNotification(notificationId);
        }
    }

    /**
     * 获取当前活跃通知数量
     */
    public int getActiveNotificationCount() {
        return activeNotifications.size();
    }

    /**
     * 通知类型枚举
     */
    public enum NotificationType {
        SUCCESS,
        ERROR,
        WARNING,
        INFO
    }

    /**
     * 通知数据类
     */
    private static class Notification {
        private final int id;
        private final NotificationType type;
        private final String title;
        private final String message;
        private final HBox node;
        private final long duration;

        public Notification(int id, NotificationType type, String title, String message,
                          HBox node, long duration) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.message = message;
            this.node = node;
            this.duration = duration;
        }

        // Getters
        public int getId() { return id; }
        public NotificationType getType() { return type; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public HBox getNode() { return node; }
        public long getDuration() { return duration; }
    }
}