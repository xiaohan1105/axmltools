package red.jiuzhou.ui.components;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 智能通知系统
 * 为设计师提供非阻塞式的状态通知和消息提醒
 *
 * 功能特性：
 * - 多种通知类型（成功、警告、错误、信息）
 * - 自动消失和手动关闭
 * - 通知历史记录
 * - 通知分组和优先级
 * - 动画效果和视觉反馈
 * - 通知中心面板
 */
public class NotificationSystem {

    private static final Logger log = LoggerFactory.getLogger(NotificationSystem.class);

    // 单例实例
    private static NotificationSystem instance;

    private final Stage primaryStage;
    private VBox notificationContainer;
    private final ObservableList<NotificationItem> notifications = FXCollections.observableArrayList();
    private final ConcurrentLinkedQueue<NotificationItem> pendingNotifications = new ConcurrentLinkedQueue<>();

    // 通知配置
    private static final int MAX_VISIBLE_NOTIFICATIONS = 5;
    private static final Duration DEFAULT_DURATION = Duration.seconds(5);
    private static final Duration ANIMATION_DURATION = Duration.millis(300);

    // 通知历史
    private final List<NotificationItem> notificationHistory = new ArrayList<>();
    private NotificationCenter notificationCenter;

    private NotificationSystem(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initializeNotificationContainer();
    }

    /**
     * 获取单例实例
     */
    public static NotificationSystem getInstance(Stage primaryStage) {
        if (instance == null) {
            instance = new NotificationSystem(primaryStage);
        }
        return instance;
    }

    /**
     * 获取已存在的实例
     */
    public static NotificationSystem getInstance() {
        if (instance == null) {
            throw new IllegalStateException("NotificationSystem must be initialized with a Stage first");
        }
        return instance;
    }

    /**
     * 初始化通知容器
     */
    private void initializeNotificationContainer() {
        notificationContainer = new VBox();
        notificationContainer.getStyleClass().add("notification-container");
        notificationContainer.setSpacing(8);
        notificationContainer.setPadding(new Insets(20));
        notificationContainer.setAlignment(Pos.TOP_RIGHT);
        notificationContainer.setMouseTransparent(true);

        // 设置容器位置（右上角）
        if (primaryStage.getScene() != null && primaryStage.getScene().getRoot() instanceof Pane) {
            Pane root = (Pane) primaryStage.getScene().getRoot();
            root.getChildren().add(notificationContainer);

            // 绑定位置到窗口右上角
            notificationContainer.layoutXProperty().bind(
                root.widthProperty().subtract(notificationContainer.widthProperty()).subtract(20)
            );
            notificationContainer.setLayoutY(20);
        }
    }

    // ========== 公共API - 显示通知 ==========

    /**
     * 显示成功通知
     */
    public void showSuccess(String title, String message) {
        showNotification(NotificationType.SUCCESS, title, message, DEFAULT_DURATION);
    }

    /**
     * 显示信息通知
     */
    public void showInfo(String title, String message) {
        showNotification(NotificationType.INFO, title, message, DEFAULT_DURATION);
    }

    /**
     * 显示警告通知
     */
    public void showWarning(String title, String message) {
        showNotification(NotificationType.WARNING, title, message, Duration.seconds(8));
    }

    /**
     * 显示错误通知
     */
    public void showError(String title, String message) {
        showNotification(NotificationType.ERROR, title, message, Duration.seconds(10));
    }

    /**
     * 显示持久通知（不自动消失）
     */
    public void showPersistent(NotificationType type, String title, String message) {
        showNotification(type, title, message, Duration.INDEFINITE);
    }

    /**
     * 显示自定义通知
     */
    public void showNotification(NotificationType type, String title, String message, Duration duration) {
        NotificationItem notification = new NotificationItem(type, title, message, duration);
        addNotification(notification);
    }

    /**
     * 显示操作结果通知
     */
    public void showOperationResult(String operation, boolean success, String details) {
        if (success) {
            showSuccess("操作成功", operation + " 已完成");
        } else {
            showError("操作失败", operation + " 失败: " + details);
        }
    }

    /**
     * 显示进度通知
     */
    public ProgressNotification showProgress(String title, String message) {
        ProgressNotification notification = new ProgressNotification(title, message);
        addNotification(notification);
        return notification;
    }

    // ========== 通知管理 ==========

    /**
     * 添加通知
     */
    private void addNotification(NotificationItem notification) {
        Platform.runLater(() -> {
            // 添加到历史记录
            notificationHistory.add(notification);

            // 如果当前显示的通知太多，先移除最旧的
            while (notifications.size() >= MAX_VISIBLE_NOTIFICATIONS) {
                removeOldestNotification();
            }

            // 添加新通知
            notifications.add(notification);
            displayNotification(notification);

            // 更新通知中心
            if (notificationCenter != null) {
                notificationCenter.updateNotifications();
            }

            log.debug("显示通知: {} - {}", notification.getTitle(), notification.getMessage());
        });
    }

    /**
     * 显示通知
     */
    private void displayNotification(NotificationItem notification) {
        NotificationCard card = new NotificationCard(notification);
        notificationContainer.getChildren().add(0, card); // 添加到顶部

        // 设置鼠标透明度，但允许卡片本身接收鼠标事件
        card.setMouseTransparent(false);

        // 入场动画
        playEnterAnimation(card);

        // 设置自动消失
        if (notification.getDuration() != Duration.INDEFINITE) {
            Timeline timeline = new Timeline(new KeyFrame(notification.getDuration(), e -> {
                removeNotification(notification);
            }));
            timeline.play();
            notification.setTimeline(timeline);
        }
    }

    /**
     * 移除通知
     */
    private void removeNotification(NotificationItem notification) {
        Platform.runLater(() -> {
            NotificationCard cardToRemove = null;

            // 找到对应的卡片
            for (javafx.scene.Node node : notificationContainer.getChildren()) {
                if (node instanceof NotificationCard) {
                    NotificationCard card = (NotificationCard) node;
                    if (card.getNotification().equals(notification)) {
                        cardToRemove = card;
                        break;
                    }
                }
            }

            if (cardToRemove != null) {
                final NotificationCard finalCardToRemove = cardToRemove;
                final NotificationItem finalNotification = notification;
                playExitAnimation(finalCardToRemove, () -> {
                    notificationContainer.getChildren().remove(finalCardToRemove);
                    notifications.remove(finalNotification);
                });
            }
        });
    }

    /**
     * 移除最旧的通知
     */
    private void removeOldestNotification() {
        if (!notifications.isEmpty()) {
            NotificationItem oldest = notifications.get(notifications.size() - 1);
            removeNotification(oldest);
        }
    }

    /**
     * 清除所有通知
     */
    public void clearAll() {
        Platform.runLater(() -> {
            // 停止所有定时器
            for (NotificationItem notification : notifications) {
                if (notification.getTimeline() != null) {
                    notification.getTimeline().stop();
                }
            }

            // 清除显示的通知
            notificationContainer.getChildren().clear();
            notifications.clear();
        });
    }

    // ========== 动画效果 ==========

    /**
     * 入场动画
     */
    private void playEnterAnimation(NotificationCard card) {
        // 初始状态
        card.setTranslateX(300);
        card.setOpacity(0);

        // 滑入动画
        TranslateTransition slide = new TranslateTransition(ANIMATION_DURATION, card);
        slide.setToX(0);

        FadeTransition fade = new FadeTransition(ANIMATION_DURATION, card);
        fade.setToValue(1.0);

        ParallelTransition animation = new ParallelTransition(slide, fade);
        animation.setInterpolator(Interpolator.EASE_OUT);
        animation.play();
    }

    /**
     * 退场动画
     */
    private void playExitAnimation(NotificationCard card, Runnable onComplete) {
        TranslateTransition slide = new TranslateTransition(ANIMATION_DURATION, card);
        slide.setToX(300);

        FadeTransition fade = new FadeTransition(ANIMATION_DURATION, card);
        fade.setToValue(0);

        ParallelTransition animation = new ParallelTransition(slide, fade);
        animation.setInterpolator(Interpolator.EASE_IN);
        animation.setOnFinished(e -> onComplete.run());
        animation.play();
    }

    // ========== 通知中心 ==========

    /**
     * 显示通知中心
     */
    public void showNotificationCenter() {
        if (notificationCenter == null) {
            notificationCenter = new NotificationCenter();
        }
        notificationCenter.show();
    }

    /**
     * 获取通知历史
     */
    public List<NotificationItem> getNotificationHistory() {
        return new ArrayList<>(notificationHistory);
    }

    // ========== 内部类 ==========

    /**
     * 通知类型
     */
    public enum NotificationType {
        SUCCESS("[成功]", "notification-success"),
        INFO("[信息]", "notification-info"),
        WARNING("[警告]", "notification-warning"),
        ERROR("[错误]", "notification-error");

        private final String icon;
        private final String styleClass;

        NotificationType(String icon, String styleClass) {
            this.icon = icon;
            this.styleClass = styleClass;
        }

        public String getIcon() { return icon; }
        public String getStyleClass() { return styleClass; }
    }

    /**
     * 通知项
     */
    public static class NotificationItem {
        private final NotificationType type;
        private final String title;
        private final String message;
        private final Duration duration;
        private final LocalDateTime timestamp;
        private Timeline timeline;

        public NotificationItem(NotificationType type, String title, String message, Duration duration) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.duration = duration;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public NotificationType getType() { return type; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public Duration getDuration() { return duration; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Timeline getTimeline() { return timeline; }

        public void setTimeline(Timeline timeline) { this.timeline = timeline; }

        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }

    /**
     * 进度通知
     */
    public static class ProgressNotification extends NotificationItem {
        private double progress = 0.0;

        public ProgressNotification(String title, String message) {
            super(NotificationType.INFO, title, message, Duration.INDEFINITE);
        }

        public double getProgress() { return progress; }

        public void updateProgress(double progress, String message) {
            this.progress = Math.max(0, Math.min(1, progress));
            // TODO: 更新UI显示
        }

        public void complete(String message) {
            this.progress = 1.0;
            // TODO: 转换为成功通知
        }
    }

    /**
     * 通知卡片
     */
    private class NotificationCard extends HBox {
        private final NotificationItem notification;

        public NotificationCard(NotificationItem notification) {
            this.notification = notification;
            createCard();
        }

        private void createCard() {
            getStyleClass().addAll("notification-card", notification.getType().getStyleClass());
            setSpacing(12);
            setPadding(new Insets(16));
            setAlignment(Pos.CENTER_LEFT);
            setMaxWidth(350);

            // 图标
            Label iconLabel = new Label(notification.getType().getIcon());
            iconLabel.getStyleClass().add("notification-icon");

            // 内容区域
            VBox contentBox = new VBox(4);
            HBox.setHgrow(contentBox, Priority.ALWAYS);

            Label titleLabel = new Label(notification.getTitle());
            titleLabel.getStyleClass().add("notification-title");

            Label messageLabel = new Label(notification.getMessage());
            messageLabel.getStyleClass().add("notification-message");
            messageLabel.setWrapText(true);

            contentBox.getChildren().addAll(titleLabel, messageLabel);

            // 关闭按钮
            Button closeButton = new Button("✕");
            closeButton.getStyleClass().add("notification-close");
            closeButton.setOnAction(e -> removeNotification(notification));

            getChildren().addAll(iconLabel, contentBox, closeButton);

            // 添加悬停效果
            setOnMouseEntered(e -> getStyleClass().add("notification-hover"));
            setOnMouseExited(e -> getStyleClass().remove("notification-hover"));

            // 点击暂停自动消失
            setOnMouseClicked(e -> {
                if (notification.getTimeline() != null) {
                    notification.getTimeline().pause();
                }
            });
        }

        public NotificationItem getNotification() {
            return notification;
        }
    }

    /**
     * 通知中心
     */
    private class NotificationCenter {
        private Stage centerStage;
        private ListView<NotificationItem> historyList;

        public void show() {
            if (centerStage == null) {
                createCenterWindow();
            }
            updateNotifications();
            centerStage.show();
            centerStage.toFront();
        }

        private void createCenterWindow() {
            centerStage = new Stage();
            centerStage.setTitle("通知中心");
            centerStage.initOwner(primaryStage);

            VBox container = new VBox();
            container.getStyleClass().add("notification-center");
            container.setSpacing(16);
            container.setPadding(new Insets(20));

            // 标题栏
            HBox header = new HBox();
            header.setAlignment(Pos.CENTER_LEFT);
            header.setSpacing(12);

            Label titleLabel = new Label("通知历史");
            titleLabel.getStyleClass().add("center-title");

            Button clearButton = new Button("清除历史");
            clearButton.getStyleClass().addAll("clear-button", "secondary");
            clearButton.setOnAction(e -> clearHistory());

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            header.getChildren().addAll(titleLabel, spacer, clearButton);

            // 通知列表
            historyList = new ListView<>();
            historyList.getStyleClass().add("history-list");
            historyList.setPrefHeight(400);
            historyList.setCellFactory(listView -> new NotificationHistoryCell());

            container.getChildren().addAll(header, historyList);

            javafx.scene.Scene scene = new javafx.scene.Scene(container, 500, 600);
            scene.getStylesheets().add("/modern-theme.css");
            centerStage.setScene(scene);
        }

        public void updateNotifications() {
            if (historyList != null) {
                ObservableList<NotificationItem> items = FXCollections.observableArrayList(notificationHistory);
                items.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())); // 最新的在前
                historyList.setItems(items);
            }
        }

        private void clearHistory() {
            notificationHistory.clear();
            updateNotifications();
        }
    }

    /**
     * 通知历史单元格
     */
    private static class NotificationHistoryCell extends ListCell<NotificationItem> {
        @Override
        protected void updateItem(NotificationItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox content = new HBox(12);
                content.setAlignment(Pos.CENTER_LEFT);

                Label iconLabel = new Label(item.getType().getIcon());
                iconLabel.getStyleClass().add("history-icon");

                VBox textBox = new VBox(2);
                Label titleLabel = new Label(item.getTitle());
                titleLabel.getStyleClass().add("history-title");

                Label messageLabel = new Label(item.getMessage());
                messageLabel.getStyleClass().add("history-message");

                Label timeLabel = new Label(item.getFormattedTimestamp());
                timeLabel.getStyleClass().add("history-time");

                textBox.getChildren().addAll(titleLabel, messageLabel, timeLabel);
                HBox.setHgrow(textBox, Priority.ALWAYS);

                content.getChildren().addAll(iconLabel, textBox);
                setGraphic(content);

                getStyleClass().removeAll("notification-success", "notification-info",
                                        "notification-warning", "notification-error");
                getStyleClass().add(item.getType().getStyleClass());
            }
        }
    }
}