package red.jiuzhou.ui.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

/**
 * 布局状态管理器
 * 负责UI布局状态的持久化、恢复和版本管理
 *
 * @author Claude Code Enhanced
 * @version 2.0
 */
public class LayoutStateManager {

    private static final Logger log = LoggerFactory.getLogger(LayoutStateManager.class);

    // 配置常量
    private static final String LAYOUT_STATE_FILE = "layout-state.json";
    private static final String LAYOUT_BACKUP_DIR = "layout-backups";
    private static final int MAX_BACKUPS = 10;
    private static final String PREF_NODE = "dbxmltool_layout";

    // 文件路径
    private final Path layoutStatePath;
    private final Path backupDirPath;

    // 当前布局状态
    private final ObjectProperty<LayoutState> currentState = new SimpleObjectProperty<>();

    // 状态变更监听器
    private final List<LayoutStateChangeListener> listeners = new ArrayList<>();

    // 自动保存配置
    private final BooleanProperty autoSave = new SimpleBooleanProperty(true);
    private final IntegerProperty autoSaveInterval = new SimpleIntegerProperty(5000); // 5秒
    private final BooleanProperty enableBackup = new SimpleBooleanProperty(true);

    // 自动保存定时器
    private Timer autoSaveTimer;

    // JSON映射器
    private final ObjectMapper objectMapper;

    // 状态缓存
    private final Map<String, Object> stateCache = new ConcurrentHashMap<>();

    public LayoutStateManager(String configDir) {
        this.layoutStatePath = Paths.get(configDir, LAYOUT_STATE_FILE);
        this.backupDirPath = Paths.get(configDir, LAYOUT_BACKUP_DIR);

        // 配置ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // 初始化
        initializeStateManager();
    }

    /**
     * 初始化状态管理器
     */
    private void initializeStateManager() {
        try {
            // 创建必要的目录
            Files.createDirectories(layoutStatePath.getParent());
            Files.createDirectories(backupDirPath);

            // 加载布局状态
            loadLayoutState();

            // 设置自动保存
            setupAutoSave();

            log.info("布局状态管理器初始化完成");

        } catch (Exception e) {
            log.error("布局状态管理器初始化失败", e);
            createDefaultLayoutState();
        }
    }

    /**
     * 设置自动保存
     */
    private void setupAutoSave() {
        autoSaveTimer = new Timer("LayoutStateAutoSave", true);
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (autoSave.get() && currentState.get() != null) {
                    saveLayoutStateAsync();
                }
            }
        }, autoSaveInterval.get(), autoSaveInterval.get());

        // 监听自动保存间隔变化
        autoSaveInterval.addListener((obs, oldVal, newVal) -> {
            autoSaveTimer.cancel();
            setupAutoSave();
        });
    }

    /**
     * 加载布局状态
     */
    public void loadLayoutState() {
        try {
            if (Files.exists(layoutStatePath)) {
                String jsonContent = new String(Files.readAllBytes(layoutStatePath));
                LayoutState layoutState = objectMapper.readValue(jsonContent, LayoutState.class);

                // 验证版本兼容性
                if (isVersionCompatible(layoutState.getVersion())) {
                    currentState.set(layoutState);
                    log.info("布局状态加载成功，版本: {}", layoutState.getVersion());
                } else {
                    log.warn("布局状态版本不兼容，使用默认状态");
                    createDefaultLayoutState();
                }
            } else {
                log.info("布局状态文件不存在，创建默认状态");
                createDefaultLayoutState();
            }

        } catch (Exception e) {
            log.error("加载布局状态失败，使用默认状态", e);
            createDefaultLayoutState();
        }
    }

    /**
     * 保存布局状态
     */
    public void saveLayoutState() {
        LayoutState state = currentState.get();
        if (state == null) {
            log.warn("没有布局状态可保存");
            return;
        }

        try {
            // 更新时间戳
            state.setLastModified(LocalDateTime.now());
            state.setVersion(getCurrentVersion());

            // 创建备份
            if (enableBackup.get()) {
                createBackup();
            }

            // 保存到文件
            String jsonContent = objectMapper.writeValueAsString(state);
            Files.write(layoutStatePath, jsonContent.getBytes());

            log.debug("布局状态保存成功");

        } catch (Exception e) {
            log.error("保存布局状态失败", e);
        }
    }

    /**
     * 异步保存布局状态
     */
    public void saveLayoutStateAsync() {
        CompletableFuture.runAsync(this::saveLayoutState);
    }

    /**
     * 创建默认布局状态
     */
    private void createDefaultLayoutState() {
        LayoutState defaultState = new LayoutState();
        defaultState.setVersion(getCurrentVersion());
        defaultState.setCreated(LocalDateTime.now());
        defaultState.setLastModified(LocalDateTime.now());

        // 设置默认的窗口状态
        LayoutState.WindowState windowState = new LayoutState.WindowState();
        windowState.setWidth(1400);
        windowState.setHeight(800);
        windowState.setMaximized(false);
        windowState.setDividerPosition(0.3);

        defaultState.setWindowState(windowState);

        // 设置默认的UI状态
        LayoutState.UIState uiState = new LayoutState.UIState();
        uiState.setTheme("light");
        uiState.setFontSize(14);
        uiState.setShowLeftPanel(true);
        uiState.setShowToolBar(true);
        uiState.setLeftPanelWidth(350);

        defaultState.setUiState(uiState);

        currentState.set(defaultState);

        // 保存默认状态
        saveLayoutState();
    }

    /**
     * 创建备份
     */
    private void createBackup() {
        try {
            if (!Files.exists(layoutStatePath)) {
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "layout-state-backup-" + timestamp + ".json";
            Path backupPath = backupDirPath.resolve(backupFileName);

            Files.copy(layoutStatePath, backupPath);

            // 清理旧备份
            cleanupOldBackups();

            log.debug("布局状态备份创建成功: {}", backupFileName);

        } catch (Exception e) {
            log.error("创建布局状态备份失败", e);
        }
    }

    /**
     * 清理旧备份
     */
    private void cleanupOldBackups() {
        try {
            File backupDir = backupDirPath.toFile();
            if (!backupDir.exists()) {
                return;
            }

            File[] backupFiles = backupDir.listFiles((dir, name) ->
                name.startsWith("layout-state-backup-") && name.endsWith(".json"));

            if (backupFiles != null && backupFiles.length > MAX_BACKUPS) {
                // 按修改时间排序，删除最旧的
                Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));

                for (int i = 0; i < backupFiles.length - MAX_BACKUPS; i++) {
                    Files.delete(backupFiles[i].toPath());
                    log.debug("删除旧备份文件: {}", backupFiles[i].getName());
                }
            }

        } catch (Exception e) {
            log.error("清理旧备份失败", e);
        }
    }

    /**
     * 恢复备份
     */
    public boolean restoreFromBackup(String backupFileName) {
        try {
            Path backupPath = backupDirPath.resolve(backupFileName);
            if (!Files.exists(backupPath)) {
                log.warn("备份文件不存在: {}", backupFileName);
                return false;
            }

            // 备份当前状态
            if (Files.exists(layoutStatePath)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String currentBackup = "layout-state-before-restore-" + timestamp + ".json";
                Files.copy(layoutStatePath, backupDirPath.resolve(currentBackup));
            }

            // 恢复备份
            Files.copy(backupPath, layoutStatePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 重新加载
            loadLayoutState();

            log.info("从备份恢复布局状态成功: {}", backupFileName);
            return true;

        } catch (Exception e) {
            log.error("从备份恢复布局状态失败: {}", backupFileName, e);
            return false;
        }
    }

    /**
     * 更新窗口状态
     */
    public void updateWindowState(Stage stage, double dividerPosition) {
        LayoutState.WindowState windowState = currentState.get().getWindowState();

        windowState.setX((double) stage.getX());
        windowState.setY((double) stage.getY());
        windowState.setWidth(stage.getWidth());
        windowState.setHeight(stage.getHeight());
        windowState.setMaximized(stage.isMaximized());
        windowState.setDividerPosition(dividerPosition);

        // 触发状态变更
        notifyStateChange();

        // 自动保存
        if (autoSave.get()) {
            saveLayoutStateAsync();
        }
    }

    /**
     * 更新UI状态
     */
    public void updateUIState(LayoutState.UIState uiState) {
        currentState.get().setUiState(uiState);

        // 触发状态变更
        notifyStateChange();

        // 自动保存
        if (autoSave.get()) {
            saveLayoutStateAsync();
        }
    }

    /**
     * 更新UI状态
     */
    public void updateUIState(String theme, double fontSize, boolean showLeftPanel,
                             boolean showToolBar, double leftPanelWidth) {
        LayoutState.UIState uiState = currentState.get().getUiState();

        uiState.setTheme(theme);
        uiState.setFontSize(fontSize);
        uiState.setShowLeftPanel(showLeftPanel);
        uiState.setShowToolBar(showToolBar);
        uiState.setLeftPanelWidth(leftPanelWidth);

        // 触发状态变更
        notifyStateChange();

        // 自动保存
        if (autoSave.get()) {
            saveLayoutStateAsync();
        }
    }

    /**
     * 保存组件状态
     */
    public void saveComponentState(String componentId, Map<String, Object> state) {
        currentState.get().getComponents().put(componentId, new LayoutState.ComponentState(componentId));
        currentState.get().getComponents().get(componentId).setProperties(new HashMap<>(state));

        // 触发状态变更
        notifyStateChange();

        // 自动保存
        if (autoSave.get()) {
            saveLayoutStateAsync();
        }
    }

    /**
     * 加载布局状态 - 兼容方法
     */
    public LayoutState loadState() {
        loadLayoutState();
        return getCurrentState();
    }

    /**
     * 获取组件状态
     */
    public Map<String, Object> getComponentState(String componentId) {
        LayoutState.ComponentState componentState = currentState.get().getComponents().get(componentId);
        return componentState != null ? componentState.getProperties() : new HashMap<>();
    }

    /**
     * 保存用户偏好
     */
    public void saveUserPreference(String key, String value) {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        prefs.put(key, value);
    }

    /**
     * 获取用户偏好
     */
    public String getUserPreference(String key, String defaultValue) {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        return prefs.get(key, defaultValue);
    }

    /**
     * 通知状态变更
     */
    private void notifyStateChange() {
        for (LayoutStateChangeListener listener : listeners) {
            try {
                listener.onStateChanged(currentState.get());
            } catch (Exception e) {
                log.error("通知状态变更失败", e);
            }
        }
    }

    /**
     * 检查版本兼容性
     */
    private boolean isVersionCompatible(String stateVersion) {
        String currentVersion = getCurrentVersion();
        // 简单的版本比较，实际项目中可能需要更复杂的逻辑
        return currentVersion.equals(stateVersion) || isCompatibleVersion(stateVersion, currentVersion);
    }

    /**
     * 检查两个版本是否兼容
     */
    private boolean isCompatibleVersion(String version1, String version2) {
        // 这里可以实现更复杂的版本兼容性检查逻辑
        // 例如：主版本号相同，次版本号向后兼容等
        try {
            String[] v1Parts = version1.split("\\.");
            String[] v2Parts = version2.split("\\.");

            // 比较主版本号
            int v1Major = Integer.parseInt(v1Parts[0]);
            int v2Major = Integer.parseInt(v2Parts[0]);

            return v1Major == v2Major;

        } catch (Exception e) {
            log.warn("版本比较失败，假设不兼容", e);
            return false;
        }
    }

    /**
     * 获取当前版本
     */
    private String getCurrentVersion() {
        return "2.0.0"; // 可以从配置文件或构建信息中读取
    }

    /**
     * 获取所有备份列表
     */
    public List<String> getBackupList() {
        List<String> backups = new ArrayList<>();

        try {
            File backupDir = backupDirPath.toFile();
            if (!backupDir.exists()) {
                return backups;
            }

            File[] backupFiles = backupDir.listFiles((dir, name) ->
                name.startsWith("layout-state-backup-") && name.endsWith(".json"));

            if (backupFiles != null) {
                Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified).reversed());

                for (File backupFile : backupFiles) {
                    backups.add(backupFile.getName());
                }
            }

        } catch (Exception e) {
            log.error("获取备份列表失败", e);
        }

        return backups;
    }

    /**
     * 重置布局状态
     */
    public void resetLayoutState() {
        // 创建当前状态的备份
        if (enableBackup.get()) {
            createBackup();
        }

        // 创建新的默认状态
        createDefaultLayoutState();

        log.info("布局状态已重置为默认值");
    }

    /**
     * 导出布局状态
     */
    public boolean exportLayoutState(Path exportPath) {
        try {
            String jsonContent = objectMapper.writeValueAsString(currentState.get());
            Files.write(exportPath, jsonContent.getBytes());

            log.info("布局状态导出成功: {}", exportPath);
            return true;

        } catch (Exception e) {
            log.error("导出布局状态失败", e);
            return false;
        }
    }

    /**
     * 导入布局状态
     */
    public boolean importLayoutState(Path importPath) {
        try {
            if (!Files.exists(importPath)) {
                log.warn("导入文件不存在: {}", importPath);
                return false;
            }

            String jsonContent = new String(Files.readAllBytes(importPath));
            LayoutState importedState = objectMapper.readValue(jsonContent, LayoutState.class);

            // 验证导入的状态
            if (isVersionCompatible(importedState.getVersion())) {
                // 备份当前状态
                if (enableBackup.get()) {
                    createBackup();
                }

                // 设置导入的状态
                currentState.set(importedState);

                // 保存导入的状态
                saveLayoutState();

                log.info("布局状态导入成功: {}", importPath);
                return true;

            } else {
                log.warn("导入的布局状态版本不兼容: {}", importedState.getVersion());
                return false;
            }

        } catch (Exception e) {
            log.error("导入布局状态失败", e);
            return false;
        }
    }

    // Getters and Setters

    public LayoutState getCurrentState() {
        return currentState.get();
    }

    public ObjectProperty<LayoutState> currentStateProperty() {
        return currentState;
    }

    public boolean isAutoSave() {
        return autoSave.get();
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave.set(autoSave);
    }

    public BooleanProperty autoSaveProperty() {
        return autoSave;
    }

    public int getAutoSaveInterval() {
        return autoSaveInterval.get();
    }

    public void setAutoSaveInterval(int autoSaveInterval) {
        this.autoSaveInterval.set(autoSaveInterval);
    }

    public IntegerProperty autoSaveIntervalProperty() {
        return autoSaveInterval;
    }

    public boolean isEnableBackup() {
        return enableBackup.get();
    }

    public void setEnableBackup(boolean enableBackup) {
        this.enableBackup.set(enableBackup);
    }

    public BooleanProperty enableBackupProperty() {
        return enableBackup;
    }

    /**
     * 添加状态变更监听器
     */
    public void addStateChangeListener(LayoutStateChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除状态变更监听器
     */
    public void removeStateChangeListener(LayoutStateChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 关闭状态管理器
     */
    public void close() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
        }

        // 最后一次保存
        saveLayoutState();

        listeners.clear();
        stateCache.clear();

        log.info("布局状态管理器已关闭");
    }

    /**
     * 状态变更监听器接口
     */
    public interface LayoutStateChangeListener {
        void onStateChanged(LayoutState newState);
    }
}