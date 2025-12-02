package red.jiuzhou.ui.layout;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 布局状态数据模型
 * 存储完整的UI布局状态信息
 *
 * @author Claude Code Enhanced
 * @version 2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LayoutState {

    private String version = "2.0.0";
    private String applicationId = "dbxmlTool";

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime created;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastModified;

    private WindowState windowState = new WindowState();
    private UIState uiState = new UIState();

    // 组件状态存储
    private Map<String, ComponentState> components = new HashMap<>();

    // 组件的通用状态（向后兼容）
    private Map<String, Map<String, Object>> componentStates = new HashMap<>();

    // 用户偏好设置
    private Map<String, String> userPreferences = new HashMap<>();

    // 元数据
    private Map<String, Object> metadata = new HashMap<>();

    public LayoutState() {
        this.created = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
    }

    // Getters and Setters

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public WindowState getWindowState() {
        return windowState;
    }

    public void setWindowState(WindowState windowState) {
        this.windowState = windowState;
    }

    public UIState getUiState() {
        return uiState;
    }

    public void setUiState(UIState uiState) {
        this.uiState = uiState;
    }

    public Map<String, ComponentState> getComponents() {
        return components;
    }

    public void setComponents(Map<String, ComponentState> components) {
        this.components = components;
    }

    @Deprecated
    public Map<String, Map<String, Object>> getComponentStates() {
        return componentStates;
    }

    @Deprecated
    public void setComponentStates(Map<String, Map<String, Object>> componentStates) {
        this.componentStates = componentStates;
    }

    public Map<String, String> getUserPreferences() {
        return userPreferences;
    }

    public void setUserPreferences(Map<String, String> userPreferences) {
        this.userPreferences = userPreferences;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * 添加组件状态
     */
    public void addComponentState(String componentId, ComponentState state) {
        this.components.put(componentId, state);
    }

    /**
     * 获取组件状态
     */
    public ComponentState getComponentState(String componentId) {
        return this.components.get(componentId);
    }

    /**
     * 移除组件状态
     */
    public void removeComponentState(String componentId) {
        this.components.remove(componentId);
        this.componentStates.remove(componentId);
    }

    /**
     * 添加用户偏好
     */
    public void addUserPreference(String key, String value) {
        this.userPreferences.put(key, value);
    }

    /**
     * 获取用户偏好
     */
    public String getUserPreference(String key) {
        return this.userPreferences.get(key);
    }

    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }

    /**
     * 创建副本
     */
    public LayoutState copy() {
        LayoutState copy = new LayoutState();

        copy.version = this.version;
        copy.applicationId = this.applicationId;
        copy.created = this.created;
        copy.lastModified = LocalDateTime.now();

        // 深拷贝窗口状态
        copy.windowState = this.windowState.copy();

        // 深拷贝UI状态
        copy.uiState = this.uiState.copy();

        // 深拷贝组件状态
        for (Map.Entry<String, ComponentState> entry : this.components.entrySet()) {
            copy.components.put(entry.getKey(), entry.getValue().copy());
        }

        // 深拷贝通用组件状态（向后兼容）
        for (Map.Entry<String, Map<String, Object>> entry : this.componentStates.entrySet()) {
            copy.componentStates.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        // 深拷贝用户偏好
        copy.userPreferences = new HashMap<>(this.userPreferences);

        // 深拷贝元数据
        copy.metadata = new HashMap<>(this.metadata);

        return copy;
    }

    /**
     * 合并另一个布局状态
     */
    public void merge(LayoutState other) {
        if (other == null) {
            return;
        }

        // 合并窗口状态（取other的值）
        this.windowState.merge(other.windowState);

        // 合并UI状态（取other的值）
        this.uiState.merge(other.uiState);

        // 合并组件状态
        for (Map.Entry<String, ComponentState> entry : other.components.entrySet()) {
            String componentId = entry.getKey();
            ComponentState existingState = this.components.get(componentId);

            if (existingState != null) {
                existingState.merge(entry.getValue());
            } else {
                this.components.put(componentId, entry.getValue().copy());
            }
        }

        // 合并通用组件状态（向后兼容）
        for (Map.Entry<String, Map<String, Object>> entry : other.componentStates.entrySet()) {
            String componentId = entry.getKey();
            Map<String, Object> existingState = this.componentStates.get(componentId);

            if (existingState != null) {
                existingState.putAll(entry.getValue());
            } else {
                this.componentStates.put(componentId, new HashMap<>(entry.getValue()));
            }
        }

        // 合并用户偏好
        this.userPreferences.putAll(other.userPreferences);

        // 合并元数据
        this.metadata.putAll(other.metadata);

        // 更新修改时间
        this.lastModified = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "LayoutState{" +
                "version='" + version + '\'' +
                ", applicationId='" + applicationId + '\'' +
                ", created=" + created +
                ", lastModified=" + lastModified +
                ", componentsCount=" + components.size() +
                ", userPreferencesCount=" + userPreferences.size() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LayoutState that = (LayoutState) o;

        if (!applicationId.equals(that.applicationId)) return false;
        if (!version.equals(that.version)) return false;
        return windowState.equals(that.windowState) && uiState.equals(that.uiState);
    }

    @Override
    public int hashCode() {
        int result = applicationId.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + windowState.hashCode();
        result = 31 * result + uiState.hashCode();
        return result;
    }

    /**
     * 窗口状态内部类
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WindowState {
        private double x = -1;
        private double y = -1;
        private double width = 1400;
        private double height = 800;
        private boolean maximized = false;
        private double dividerPosition = 0.3;
        private boolean fullScreen = false;

        // 窗口透明度
        private double opacity = 1.0;

        // 窗口状态（normal, minimized, maximized）
        private String state = "normal";

        public WindowState() {}

        public WindowState(double x, double y, double width, double height, boolean maximized, double dividerPosition) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.maximized = maximized;
            this.dividerPosition = dividerPosition;
        }

        // Getters and Setters
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }

        public double getY() { return y; }
        public void setY(double y) { this.y = y; }

        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }

        public double getHeight() { return height; }
        public void setHeight(double height) { this.height = height; }

        public boolean isMaximized() { return maximized; }
        public void setMaximized(boolean maximized) { this.maximized = maximized; }

        public double getDividerPosition() { return dividerPosition; }
        public void setDividerPosition(double dividerPosition) { this.dividerPosition = dividerPosition; }

        public boolean isFullScreen() { return fullScreen; }
        public void setFullScreen(boolean fullScreen) { this.fullScreen = fullScreen; }

        public double getOpacity() { return opacity; }
        public void setOpacity(double opacity) { this.opacity = opacity; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        /**
         * 合并窗口状态
         */
        public void merge(WindowState other) {
            if (other == null) return;

            if (other.x >= 0) this.x = other.x;
            if (other.y >= 0) this.y = other.y;
            if (other.width > 0) this.width = other.width;
            if (other.height > 0) this.height = other.height;
            this.maximized = other.maximized;
            this.dividerPosition = other.dividerPosition;
            this.fullScreen = other.fullScreen;
            this.opacity = other.opacity;
            this.state = other.state;
        }

        /**
         * 创建副本
         */
        public WindowState copy() {
            WindowState copy = new WindowState();
            copy.x = this.x;
            copy.y = this.y;
            copy.width = this.width;
            copy.height = this.height;
            copy.maximized = this.maximized;
            copy.dividerPosition = this.dividerPosition;
            copy.fullScreen = this.fullScreen;
            copy.opacity = this.opacity;
            copy.state = this.state;
            return copy;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WindowState that = (WindowState) o;

            return Double.compare(that.x, x) == 0 &&
                   Double.compare(that.y, y) == 0 &&
                   Double.compare(that.width, width) == 0 &&
                   Double.compare(that.height, height) == 0 &&
                   maximized == that.maximized &&
                   Double.compare(that.dividerPosition, dividerPosition) == 0 &&
                   fullScreen == that.fullScreen &&
                   Double.compare(that.opacity, opacity) == 0 &&
                   state.equals(that.state);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(x);
            result = (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(y);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(width);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(height);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + (maximized ? 1 : 0);
            temp = Double.doubleToLongBits(dividerPosition);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + (fullScreen ? 1 : 0);
            temp = Double.doubleToLongBits(opacity);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + state.hashCode();
            return result;
        }
    }

    /**
     * UI状态内部类
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UIState {
        private String theme = "light";
        private double fontSize = 14.0;
        private String fontFamily = "System";
        private boolean showLeftPanel = true;
        private boolean showToolBar = true;
        private boolean showStatusBar = true;
        private double leftPanelWidth = 350;
        private double toolBarHeight = 40;
        private double statusBarHeight = 25;

        // 布局模式
        private String layoutMode = "responsive";

        // 主题变体
        private String themeVariant = "default";

        // 动画设置
        private boolean enableAnimations = true;
        private double animationSpeed = 1.0;

        // 语言设置
        private String language = "zh-CN";

        public UIState() {}

        // Getters and Setters
        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }

        public double getFontSize() { return fontSize; }
        public void setFontSize(double fontSize) { this.fontSize = fontSize; }

        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

        public boolean isShowLeftPanel() { return showLeftPanel; }
        public void setShowLeftPanel(boolean showLeftPanel) { this.showLeftPanel = showLeftPanel; }

        public boolean isShowToolBar() { return showToolBar; }
        public void setShowToolBar(boolean showToolBar) { this.showToolBar = showToolBar; }

        public boolean isShowStatusBar() { return showStatusBar; }
        public void setShowStatusBar(boolean showStatusBar) { this.showStatusBar = showStatusBar; }

        public double getLeftPanelWidth() { return leftPanelWidth; }
        public void setLeftPanelWidth(double leftPanelWidth) { this.leftPanelWidth = leftPanelWidth; }

        public double getToolBarHeight() { return toolBarHeight; }
        public void setToolBarHeight(double toolBarHeight) { this.toolBarHeight = toolBarHeight; }

        public double getStatusBarHeight() { return statusBarHeight; }
        public void setStatusBarHeight(double statusBarHeight) { this.statusBarHeight = statusBarHeight; }

        public String getLayoutMode() { return layoutMode; }
        public void setLayoutMode(String layoutMode) { this.layoutMode = layoutMode; }

        public String getThemeVariant() { return themeVariant; }
        public void setThemeVariant(String themeVariant) { this.themeVariant = themeVariant; }

        public boolean isEnableAnimations() { return enableAnimations; }
        public void setEnableAnimations(boolean enableAnimations) { this.enableAnimations = enableAnimations; }

        public double getAnimationSpeed() { return animationSpeed; }
        public void setAnimationSpeed(double animationSpeed) { this.animationSpeed = animationSpeed; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        /**
         * 合并UI状态
         */
        public void merge(UIState other) {
            if (other == null) return;

            if (other.theme != null) this.theme = other.theme;
            if (other.fontSize > 0) this.fontSize = other.fontSize;
            if (other.fontFamily != null) this.fontFamily = other.fontFamily;
            this.showLeftPanel = other.showLeftPanel;
            this.showToolBar = other.showToolBar;
            this.showStatusBar = other.showStatusBar;
            if (other.leftPanelWidth > 0) this.leftPanelWidth = other.leftPanelWidth;
            if (other.toolBarHeight > 0) this.toolBarHeight = other.toolBarHeight;
            if (other.statusBarHeight > 0) this.statusBarHeight = other.statusBarHeight;
            if (other.layoutMode != null) this.layoutMode = other.layoutMode;
            if (other.themeVariant != null) this.themeVariant = other.themeVariant;
            this.enableAnimations = other.enableAnimations;
            if (other.animationSpeed > 0) this.animationSpeed = other.animationSpeed;
            if (other.language != null) this.language = other.language;
        }

        /**
         * 创建副本
         */
        public UIState copy() {
            UIState copy = new UIState();
            copy.theme = this.theme;
            copy.fontSize = this.fontSize;
            copy.fontFamily = this.fontFamily;
            copy.showLeftPanel = this.showLeftPanel;
            copy.showToolBar = this.showToolBar;
            copy.showStatusBar = this.showStatusBar;
            copy.leftPanelWidth = this.leftPanelWidth;
            copy.toolBarHeight = this.toolBarHeight;
            copy.statusBarHeight = this.statusBarHeight;
            copy.layoutMode = this.layoutMode;
            copy.themeVariant = this.themeVariant;
            copy.enableAnimations = this.enableAnimations;
            copy.animationSpeed = this.animationSpeed;
            copy.language = this.language;
            return copy;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UIState uiState = (UIState) o;

            if (Double.compare(uiState.fontSize, fontSize) != 0) return false;
            if (showLeftPanel != uiState.showLeftPanel) return false;
            if (showToolBar != uiState.showToolBar) return false;
            if (showStatusBar != uiState.showStatusBar) return false;
            if (Double.compare(uiState.leftPanelWidth, leftPanelWidth) != 0) return false;
            if (Double.compare(uiState.toolBarHeight, toolBarHeight) != 0) return false;
            if (Double.compare(uiState.statusBarHeight, statusBarHeight) != 0) return false;
            if (enableAnimations != uiState.enableAnimations) return false;
            if (Double.compare(uiState.animationSpeed, animationSpeed) != 0) return false;
            if (!theme.equals(uiState.theme)) return false;
            if (!fontFamily.equals(uiState.fontFamily)) return false;
            if (!layoutMode.equals(uiState.layoutMode)) return false;
            if (!themeVariant.equals(uiState.themeVariant)) return false;
            return language.equals(uiState.language);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = theme.hashCode();
            temp = Double.doubleToLongBits(fontSize);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + fontFamily.hashCode();
            result = 31 * result + (showLeftPanel ? 1 : 0);
            result = 31 * result + (showToolBar ? 1 : 0);
            result = 31 * result + (showStatusBar ? 1 : 0);
            temp = Double.doubleToLongBits(leftPanelWidth);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(toolBarHeight);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(statusBarHeight);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + layoutMode.hashCode();
            result = 31 * result + themeVariant.hashCode();
            result = 31 * result + (enableAnimations ? 1 : 0);
            temp = Double.doubleToLongBits(animationSpeed);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + language.hashCode();
            return result;
        }
    }

    /**
     * 组件状态内部类
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentState {
        private String componentType;
        private Map<String, Object> properties = new HashMap<>();
        private Map<String, Object> customData = new HashMap<>();

        public ComponentState() {}

        public ComponentState(String componentType) {
            this.componentType = componentType;
        }

        // Getters and Setters
        public String getComponentType() { return componentType; }
        public void setComponentType(String componentType) { this.componentType = componentType; }

        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) { this.properties = properties; }

        public Map<String, Object> getCustomData() { return customData; }
        public void setCustomData(Map<String, Object> customData) { this.customData = customData; }

        /**
         * 设置属性
         */
        public void setProperty(String key, Object value) {
            this.properties.put(key, value);
        }

        /**
         * 获取属性
         */
        public Object getProperty(String key) {
            return this.properties.get(key);
        }

        /**
         * 设置自定义数据
         */
        public void setCustomData(String key, Object value) {
            this.customData.put(key, value);
        }

        /**
         * 获取自定义数据
         */
        public Object getCustomData(String key) {
            return this.customData.get(key);
        }

        /**
         * 合并组件状态
         */
        public void merge(ComponentState other) {
            if (other == null) return;

            if (other.componentType != null) this.componentType = other.componentType;
            if (other.properties != null) this.properties.putAll(other.properties);
            if (other.customData != null) this.customData.putAll(other.customData);
        }

        /**
         * 创建副本
         */
        public ComponentState copy() {
            ComponentState copy = new ComponentState();
            copy.componentType = this.componentType;
            copy.properties = new HashMap<>(this.properties);
            copy.customData = new HashMap<>(this.customData);
            return copy;
        }
    }
}