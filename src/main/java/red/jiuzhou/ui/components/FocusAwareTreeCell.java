package red.jiuzhou.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismFileMapper;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 焦点感知的树节点单元格
 *
 * 当用户选择某个机制时，树节点会根据焦点状态调整视觉效果：
 * - 匹配机制的文件：高亮显示，带机制颜色标记
 * - 不匹配的文件：淡化显示
 * - 文件夹：显示包含匹配文件的统计徽章
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class FocusAwareTreeCell<T> extends TreeCell<T> {

    /** 路径解析器 */
    private final Function<TreeItem<T>, String> pathResolver;

    /** 机制过滤回调 */
    private Consumer<AionMechanismCategory> onFilterByMechanism;

    /** 打开机制浏览器回调 */
    private Consumer<AionMechanismCategory> onOpenMechanismExplorer;

    /** 当前焦点机制 */
    private AionMechanismCategory focusedMechanism = null;

    /** 是否启用焦点模式 */
    private boolean focusModeEnabled = true;

    /** 是否启用机制着色模式（文件名始终显示机制颜色） */
    private boolean mechanismColoringEnabled = true;

    /** 文件访问回调（用于跟踪工作流） */
    private Consumer<String> onFileAccessed;

    /** 关联文件操作回调 */
    private Consumer<AionMechanismCategory> onFindRelatedFiles;

    /** 机制标记圆点大小 */
    private static final double MARKER_SIZE = 7;

    /** 动画持续时间 */
    private static final Duration ANIMATION_DURATION = Duration.millis(200);

    /** 淡化透明度 */
    private static final double DIMMED_OPACITY = 0.45;

    /** 高亮透明度 */
    private static final double HIGHLIGHTED_OPACITY = 1.0;

    public FocusAwareTreeCell(Function<TreeItem<T>, String> pathResolver) {
        this.pathResolver = pathResolver;
        setupContextMenu();

        // 设置基础样式
        setStyle("-fx-padding: 2 4;");
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setOpacity(1.0);
            setEffect(null);
            return;
        }

        String displayText = item.toString();
        TreeItem<T> treeItem = getTreeItem();

        // 判断是否为文件（叶子节点）
        boolean isFile = treeItem != null && treeItem.isLeaf();

        if (isFile && pathResolver != null) {
            String filePath = pathResolver.apply(treeItem);
            if (filePath != null && filePath.toLowerCase().endsWith(".xml")) {
                // 获取文件机制
                AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(filePath);

                // 创建文件节点布局
                HBox container = createFileContent(displayText, mechanism, filePath);
                setGraphic(container);
                setText(null);

                // 设置悬停提示
                setTooltip(createMechanismTooltip(mechanism, filePath));

                // 应用焦点样式
                applyFocusStyle(mechanism);
                return;
            }
        }

        // 文件夹节点
        if (!isFile && treeItem != null) {
            HBox container = createFolderContent(displayText, treeItem);
            setGraphic(container);
            setText(null);
            setTooltip(null);

            // 文件夹焦点样式
            applyFolderFocusStyle(treeItem);
            return;
        }

        // 普通显示
        setText(displayText);
        setGraphic(null);
        setTooltip(null);
        setOpacity(1.0);
        setEffect(null);
    }

    /**
     * 创建文件节点内容
     *
     * 支持两种着色模式：
     * 1. 机制着色模式（mechanismColoringEnabled）：文件名始终显示对应机制的颜色
     * 2. 焦点模式（focusModeEnabled）：焦点机制高亮，其他淡化
     */
    private HBox createFileContent(String text, AionMechanismCategory mechanism, String filePath) {
        HBox container = new HBox(6);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(1, 0, 1, 0));

        String mechColor = mechanism.getColor();

        // 机制颜色标记（左侧竖条）
        Region colorBar = new Region();
        colorBar.setMinWidth(3);
        colorBar.setMaxWidth(3);
        colorBar.setMinHeight(16);
        try {
            colorBar.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 2;",
                mechColor
            ));

            // 如果是焦点机制，添加发光效果
            if (mechanism == focusedMechanism && focusModeEnabled) {
                DropShadow glow = new DropShadow();
                glow.setColor(Color.web(mechColor, 0.6));
                glow.setRadius(4);
                glow.setSpread(0.3);
                colorBar.setEffect(glow);
            }
        } catch (Exception e) {
            colorBar.setStyle("-fx-background-color: #6c757d; -fx-background-radius: 2;");
        }

        // 文件图标（根据机制类型选择）
        Label fileIcon = new Label(getFileIconForMechanism(mechanism));
        fileIcon.setStyle("-fx-font-size: 11px;");

        // 文件名标签 - 根据机制着色
        Label nameLabel = new Label(text);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        // 应用着色样式
        applyFileNameStyle(nameLabel, mechanism, mechColor);

        // 机制标签（只为非OTHER显示）
        if (mechanism != AionMechanismCategory.OTHER) {
            Label mechanismLabel = createMechanismBadge(mechanism);
            container.getChildren().addAll(colorBar, fileIcon, nameLabel, mechanismLabel);
        } else {
            container.getChildren().addAll(colorBar, fileIcon, nameLabel);
        }

        return container;
    }

    /**
     * 应用文件名样式
     */
    private void applyFileNameStyle(Label nameLabel, AionMechanismCategory mechanism, String mechColor) {
        StringBuilder style = new StringBuilder();

        // 机制着色模式：始终使用机制颜色
        if (mechanismColoringEnabled && mechanism != AionMechanismCategory.OTHER) {
            String textColor = darkenColor(mechColor, 0.15);
            style.append("-fx-text-fill: ").append(textColor).append("; ");
        } else {
            style.append("-fx-text-fill: #212529; ");
        }

        // 焦点模式叠加效果
        if (focusModeEnabled && focusedMechanism != null) {
            if (mechanism == focusedMechanism) {
                // 匹配焦点：加粗 + 强调色
                style.append("-fx-font-weight: bold; ");
                style.append("-fx-text-fill: ").append(darkenColor(mechColor, 0.1)).append("; ");
            } else {
                // 不匹配：淡化（但保留机制色调）
                if (mechanismColoringEnabled && mechanism != AionMechanismCategory.OTHER) {
                    style.append("-fx-text-fill: ").append(lightenColor(mechColor, 0.5)).append("; ");
                } else {
                    style.append("-fx-text-fill: #adb5bd; ");
                }
            }
        }

        nameLabel.setStyle(style.toString());
    }

    /**
     * 根据机制类型获取文件图标
     */
    private String getFileIconForMechanism(AionMechanismCategory mechanism) {
        switch (mechanism) {
            case ITEM:
                return "🎁";
            case NPC:
                return "👾";
            case SKILL:
                return "⚔";
            case QUEST:
                return "📜";
            case DROP:
                return "💎";
            case INSTANCE:
                return "🏰";
            case SHOP:
                return "🛒";
            case CRAFT:
                return "🔨";
            case ABYSS:
                return "⚡";
            case PET:
                return "🐾";
            case ENCHANT:
                return "✨";
            case TITLE:
                return "🏅";
            case PORTAL:
                return "🚪";
            case CLIENT_STRINGS:
                return "📝";
            case GOTCHA:
                return "🎰";
            case LEGION:
                return "🏴";
            case HOUSING:
                return "🏠";
            case LUNA:
                return "🌙";
            case STIGMA_TRANSFORM:
                return "💠";
            case NPC_AI:
                return "🤖";
            case PLAYER_GROWTH:
                return "📈";
            case PVP_RANKING:
                return "🏆";
            case TIME_EVENT:
                return "⏰";
            case ANIMATION:
                return "🎬";
            case ANIMATION_MARKERS:
                return "📌";
            case CHARACTER_PRESET:
                return "👤";
            case SUBZONE:
                return "🗺";
            case ID_MAPPING:
                return "🔢";
            case GAME_CONFIG:
                return "⚙";
            default:
                return "📄";
        }
    }

    /**
     * 创建文件夹节点内容
     */
    private HBox createFolderContent(String text, TreeItem<T> folderItem) {
        HBox container = new HBox(6);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(1, 0, 1, 0));

        // 文件夹图标
        Label folderIcon = new Label(folderItem.isExpanded() ? "📂" : "📁");
        folderIcon.setStyle("-fx-font-size: 12px;");

        // 文件夹名称
        Label nameLabel = new Label(text);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        // 根据焦点状态设置文件夹样式
        if (focusModeEnabled && focusedMechanism != null) {
            int matchCount = countMatchingFiles(folderItem, focusedMechanism);
            if (matchCount > 0) {
                // 包含匹配文件：正常显示 + 徽章
                nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #212529;");

                // 匹配数量徽章
                Label badge = new Label(String.valueOf(matchCount));
                badge.setStyle(String.format(
                    "-fx-font-size: 9px; " +
                    "-fx-text-fill: white; " +
                    "-fx-background-color: %s; " +
                    "-fx-background-radius: 8; " +
                    "-fx-padding: 1 5; " +
                    "-fx-min-width: 16;",
                    focusedMechanism.getColor()
                ));
                badge.setAlignment(Pos.CENTER);

                container.getChildren().addAll(folderIcon, nameLabel, badge);
            } else {
                // 不包含匹配文件：淡化显示
                nameLabel.setStyle("-fx-text-fill: #adb5bd;");
                container.getChildren().addAll(folderIcon, nameLabel);
            }
        } else {
            nameLabel.setStyle("-fx-text-fill: #212529;");
            container.getChildren().addAll(folderIcon, nameLabel);
        }

        return container;
    }

    /**
     * 创建机制徽章
     */
    private Label createMechanismBadge(AionMechanismCategory mechanism) {
        Label badge = new Label(mechanism.getIcon());

        // 根据焦点状态设置徽章样式
        if (focusModeEnabled && focusedMechanism != null && mechanism == focusedMechanism) {
            // 焦点机制：突出显示
            badge.setStyle(String.format(
                "-fx-font-size: 10px; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 1 6; " +
                "-fx-background-color: %s; " +
                "-fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, %s, 4, 0.3, 0, 1);",
                mechanism.getColor(),
                mechanism.getColor()
            ));
        } else {
            // 非焦点：普通样式
            badge.setStyle(String.format(
                "-fx-font-size: 9px; " +
                "-fx-text-fill: %s; " +
                "-fx-padding: 0 4; " +
                "-fx-background-color: %s; " +
                "-fx-background-radius: 8;",
                mechanism.getColor(),
                lightenColor(mechanism.getColor(), 0.88)
            ));
        }

        return badge;
    }

    /**
     * 应用焦点样式（文件节点）
     */
    private void applyFocusStyle(AionMechanismCategory mechanism) {
        if (!focusModeEnabled || focusedMechanism == null) {
            setOpacity(1.0);
            setEffect(null);
            return;
        }

        if (mechanism == focusedMechanism) {
            // 匹配：高亮
            setOpacity(HIGHLIGHTED_OPACITY);

            // 添加微妙的发光效果
            DropShadow shadow = new DropShadow();
            shadow.setColor(Color.web(mechanism.getColor(), 0.3));
            shadow.setRadius(3);
            shadow.setSpread(0.1);
            setEffect(shadow);
        } else {
            // 不匹配：淡化
            setOpacity(DIMMED_OPACITY);
            setEffect(null);
        }
    }

    /**
     * 应用焦点样式（文件夹节点）
     */
    private void applyFolderFocusStyle(TreeItem<T> folderItem) {
        if (!focusModeEnabled || focusedMechanism == null) {
            setOpacity(1.0);
            setEffect(null);
            return;
        }

        int matchCount = countMatchingFiles(folderItem, focusedMechanism);
        if (matchCount > 0) {
            setOpacity(1.0);
            setEffect(null);
        } else {
            setOpacity(DIMMED_OPACITY);
            setEffect(null);
        }
    }

    /**
     * 计算文件夹下匹配指定机制的文件数量
     */
    private int countMatchingFiles(TreeItem<T> item, AionMechanismCategory mechanism) {
        if (item == null || mechanism == null) return 0;

        int count = 0;

        if (item.isLeaf()) {
            // 文件节点
            if (pathResolver != null) {
                String path = pathResolver.apply(item);
                if (path != null && path.toLowerCase().endsWith(".xml")) {
                    AionMechanismCategory fileMech = MechanismFileMapper.detectMechanismStatic(path);
                    if (fileMech == mechanism) {
                        count = 1;
                    }
                }
            }
        } else {
            // 文件夹：递归计算
            for (TreeItem<T> child : item.getChildren()) {
                count += countMatchingFiles(child, mechanism);
            }
        }

        return count;
    }

    /**
     * 创建机制提示
     */
    private Tooltip createMechanismTooltip(AionMechanismCategory mechanism, String filePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("📁 ").append(new File(filePath).getName()).append("\n");
        sb.append("🎮 ").append(mechanism.getDisplayName()).append("\n");
        sb.append("📝 ").append(mechanism.getDescription());

        if (focusModeEnabled && focusedMechanism != null) {
            sb.append("\n\n");
            if (mechanism == focusedMechanism) {
                sb.append("✓ 匹配当前焦点");
            } else {
                sb.append("○ 不在当前焦点范围");
            }
        }

        sb.append("\n\n💡 右键可快速过滤此机制的所有文件");

        Tooltip tooltip = new Tooltip(sb.toString());
        tooltip.setStyle("-fx-font-size: 11px; -fx-background-radius: 4;");
        return tooltip;
    }

    /**
     * 设置右键菜单
     */
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        // ========== 文件操作组 ==========
        // 查看文件机制
        MenuItem viewMechanismItem = new MenuItem("🎮 查看文件机制");
        viewMechanismItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    showMechanismInfo(mechanism, path);
                    // 记录访问
                    notifyFileAccessed(path);
                }
            }
        });

        // ========== 焦点操作组 ==========
        // 聚焦此机制
        MenuItem focusMechanismItem = new MenuItem("🎯 聚焦此机制");
        focusMechanismItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null && onFilterByMechanism != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    onFilterByMechanism.accept(mechanism);
                    notifyFileAccessed(path);
                }
            }
        });

        // 清除焦点
        MenuItem clearFocusItem = new MenuItem("✕ 清除焦点");
        clearFocusItem.setOnAction(e -> {
            if (onFilterByMechanism != null) {
                onFilterByMechanism.accept(null);
            }
        });

        // ========== 关联文件组 ==========
        // 查找关联文件（同机制）
        MenuItem findRelatedItem = new MenuItem("🔗 查找同类型文件");
        findRelatedItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    if (onFindRelatedFiles != null) {
                        onFindRelatedFiles.accept(mechanism);
                    } else if (onFilterByMechanism != null) {
                        // 回退到聚焦机制
                        onFilterByMechanism.accept(mechanism);
                    }
                    notifyFileAccessed(path);
                }
            }
        });

        // 在机制浏览器中查看
        MenuItem openExplorerItem = new MenuItem("📊 在机制浏览器中打开");
        openExplorerItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null && onOpenMechanismExplorer != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    onOpenMechanismExplorer.accept(mechanism);
                    notifyFileAccessed(path);
                }
            }
        });

        // ========== 复制操作组 ==========
        // 复制机制名称
        MenuItem copyMechanismItem = new MenuItem("📋 复制机制名称");
        copyMechanismItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    ContextMenuFactory.copyToClipboard(mechanism.getDisplayName());
                }
            }
        });

        // 复制文件路径
        MenuItem copyPathItem = new MenuItem("📁 复制文件路径");
        copyPathItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    ContextMenuFactory.copyToClipboard(path);
                }
            }
        });

        // 分隔符
        SeparatorMenuItem separator1 = new SeparatorMenuItem();
        SeparatorMenuItem separator2 = new SeparatorMenuItem();
        SeparatorMenuItem separator3 = new SeparatorMenuItem();

        contextMenu.getItems().addAll(
            viewMechanismItem,
            separator1,
            focusMechanismItem,
            clearFocusItem,
            separator2,
            findRelatedItem,
            openExplorerItem,
            separator3,
            copyMechanismItem,
            copyPathItem
        );

        // 动态显示菜单项
        contextMenu.setOnShowing(e -> {
            TreeItem<T> selected = getTreeItem();
            boolean isFile = selected != null && selected.isLeaf();
            boolean hasPath = isFile && pathResolver != null;
            boolean hasFocus = focusedMechanism != null;

            viewMechanismItem.setDisable(!hasPath);
            focusMechanismItem.setDisable(!hasPath || onFilterByMechanism == null);
            clearFocusItem.setDisable(!hasFocus || onFilterByMechanism == null);
            findRelatedItem.setDisable(!hasPath);
            openExplorerItem.setDisable(!hasPath || onOpenMechanismExplorer == null);
            copyMechanismItem.setDisable(!hasPath);
            copyPathItem.setDisable(!hasPath);
        });

        // 只为文件节点设置右键菜单
        setOnContextMenuRequested(event -> {
            TreeItem<T> item = getTreeItem();
            if (item != null && item.isLeaf()) {
                contextMenu.show(this, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });
    }

    /**
     * 通知文件被访问（用于工作流跟踪）
     */
    private void notifyFileAccessed(String filePath) {
        if (onFileAccessed != null && filePath != null) {
            onFileAccessed.accept(filePath);
        }
    }

    /**
     * 显示机制信息对话框
     */
    private void showMechanismInfo(AionMechanismCategory mechanism, String filePath) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("文件机制信息");
        alert.setHeaderText(new File(filePath).getName());

        StringBuilder content = new StringBuilder();
        content.append("机制分类: ").append(mechanism.getDisplayName()).append("\n");
        content.append("机制图标: ").append(mechanism.getIcon()).append("\n");
        content.append("机制颜色: ").append(mechanism.getColor()).append("\n");
        content.append("机制描述: ").append(mechanism.getDescription()).append("\n");
        content.append("优先级: ").append(mechanism.getPriority()).append("\n");
        content.append("\n文件路径:\n").append(filePath);

        alert.setContentText(content.toString());
        alert.showAndWait();
    }

    /**
     * 颜色变浅
     */
    private String lightenColor(String hexColor, double factor) {
        try {
            Color color = Color.web(hexColor);
            double r = color.getRed() + (1 - color.getRed()) * factor;
            double g = color.getGreen() + (1 - color.getGreen()) * factor;
            double b = color.getBlue() + (1 - color.getBlue()) * factor;
            return String.format("#%02X%02X%02X",
                (int)(r * 255), (int)(g * 255), (int)(b * 255));
        } catch (Exception e) {
            return "#f8f9fa";
        }
    }

    /**
     * 颜色变深
     */
    private String darkenColor(String hexColor, double factor) {
        try {
            Color color = Color.web(hexColor);
            double r = color.getRed() * (1 - factor);
            double g = color.getGreen() * (1 - factor);
            double b = color.getBlue() * (1 - factor);
            return String.format("#%02X%02X%02X",
                (int)(r * 255), (int)(g * 255), (int)(b * 255));
        } catch (Exception e) {
            return "#212529";
        }
    }

    // ==================== Setters ====================

    /**
     * 设置焦点机制
     */
    public void setFocusedMechanism(AionMechanismCategory mechanism) {
        this.focusedMechanism = mechanism;
    }

    /**
     * 获取焦点机制
     */
    public AionMechanismCategory getFocusedMechanism() {
        return focusedMechanism;
    }

    /**
     * 设置是否启用焦点模式
     */
    public void setFocusModeEnabled(boolean enabled) {
        this.focusModeEnabled = enabled;
    }

    /**
     * 设置机制过滤回调
     */
    public void setOnFilterByMechanism(Consumer<AionMechanismCategory> callback) {
        this.onFilterByMechanism = callback;
    }

    /**
     * 设置打开机制浏览器回调
     */
    public void setOnOpenMechanismExplorer(Consumer<AionMechanismCategory> callback) {
        this.onOpenMechanismExplorer = callback;
    }

    /**
     * 设置文件访问回调（用于工作流跟踪）
     */
    public void setOnFileAccessed(Consumer<String> callback) {
        this.onFileAccessed = callback;
    }

    /**
     * 设置关联文件操作回调
     */
    public void setOnFindRelatedFiles(Consumer<AionMechanismCategory> callback) {
        this.onFindRelatedFiles = callback;
    }

    /**
     * 设置是否启用机制着色模式
     */
    public void setMechanismColoringEnabled(boolean enabled) {
        this.mechanismColoringEnabled = enabled;
    }

    /**
     * 获取是否启用机制着色模式
     */
    public boolean isMechanismColoringEnabled() {
        return mechanismColoringEnabled;
    }

    /**
     * 创建工厂方法
     */
    public static <T> javafx.util.Callback<TreeView<T>, TreeCell<T>> createFactory(
            Function<TreeItem<T>, String> pathResolver,
            Consumer<AionMechanismCategory> onFilterByMechanism,
            Consumer<AionMechanismCategory> onOpenMechanismExplorer,
            AionMechanismCategory focusedMechanism) {

        return treeView -> {
            FocusAwareTreeCell<T> cell = new FocusAwareTreeCell<>(pathResolver);
            cell.setOnFilterByMechanism(onFilterByMechanism);
            cell.setOnOpenMechanismExplorer(onOpenMechanismExplorer);
            cell.setFocusedMechanism(focusedMechanism);
            return cell;
        };
    }

    /**
     * 创建带工作流跟踪的工厂方法
     */
    public static <T> javafx.util.Callback<TreeView<T>, TreeCell<T>> createFactoryWithTracking(
            Function<TreeItem<T>, String> pathResolver,
            Consumer<AionMechanismCategory> onFilterByMechanism,
            Consumer<AionMechanismCategory> onOpenMechanismExplorer,
            AionMechanismCategory focusedMechanism,
            Consumer<String> onFileAccessed) {

        return treeView -> {
            FocusAwareTreeCell<T> cell = new FocusAwareTreeCell<>(pathResolver);
            cell.setOnFilterByMechanism(onFilterByMechanism);
            cell.setOnOpenMechanismExplorer(onOpenMechanismExplorer);
            cell.setFocusedMechanism(focusedMechanism);
            cell.setOnFileAccessed(onFileAccessed);
            return cell;
        };
    }
}
