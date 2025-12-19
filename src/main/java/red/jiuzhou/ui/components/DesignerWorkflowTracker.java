package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismFileMapper;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * 设计师工作流跟踪器
 *
 * 跟踪设计师的文件访问历史和操作序列，支持：
 * - 最近访问文件记录
 * - 按机制分组的访问统计
 * - 工作会话跟踪
 * - 关联文件推荐
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class DesignerWorkflowTracker {

    /** 单例实例 */
    private static DesignerWorkflowTracker instance;

    /** 最近访问记录 */
    private final ConcurrentLinkedDeque<FileAccessRecord> recentAccess = new ConcurrentLinkedDeque<>();

    /** 机制访问计数 */
    private final Map<AionMechanismCategory, Integer> mechanismAccessCount = new EnumMap<>(AionMechanismCategory.class);

    /** 文件访问计数 */
    private final Map<String, Integer> fileAccessCount = new LinkedHashMap<>();

    /** 会话开始时间 */
    private final LocalDateTime sessionStart = LocalDateTime.now();

    /** 最大历史记录数 */
    private static final int MAX_HISTORY = 50;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 历史变更监听器 */
    private final List<Consumer<List<FileAccessRecord>>> historyListeners = new ArrayList<>();

    /** 可观察的最近文件列表（用于UI绑定） */
    private final ObservableList<FileAccessRecord> observableRecentFiles = FXCollections.observableArrayList();

    private DesignerWorkflowTracker() {
        // 初始化机制计数
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            mechanismAccessCount.put(category, 0);
        }
    }

    /**
     * 获取单例实例
     */
    public static synchronized DesignerWorkflowTracker getInstance() {
        if (instance == null) {
            instance = new DesignerWorkflowTracker();
        }
        return instance;
    }

    /**
     * 记录文件访问
     */
    public void recordAccess(String filePath) {
        if (filePath == null || filePath.isEmpty()) return;

        // 检测文件机制
        AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(filePath);

        // 创建访问记录
        FileAccessRecord record = new FileAccessRecord(
            filePath,
            mechanism,
            LocalDateTime.now()
        );

        // 添加到历史
        recentAccess.addFirst(record);

        // 限制历史大小
        while (recentAccess.size() > MAX_HISTORY) {
            recentAccess.removeLast();
        }

        // 更新计数
        mechanismAccessCount.merge(mechanism, 1, Integer::sum);
        fileAccessCount.merge(filePath.toLowerCase(), 1, Integer::sum);

        // 更新可观察列表
        Platform.runLater(() -> {
            observableRecentFiles.clear();
            observableRecentFiles.addAll(new ArrayList<>(recentAccess));
        });

        // 通知监听器
        notifyListeners();
    }

    /**
     * 获取最近访问的文件列表
     */
    public List<FileAccessRecord> getRecentFiles() {
        return new ArrayList<>(recentAccess);
    }

    /**
     * 获取最近访问的文件列表（限制数量）
     */
    public List<FileAccessRecord> getRecentFiles(int limit) {
        List<FileAccessRecord> result = new ArrayList<>();
        int count = 0;
        for (FileAccessRecord record : recentAccess) {
            if (count >= limit) break;
            result.add(record);
            count++;
        }
        return result;
    }

    /**
     * 获取可观察的最近文件列表
     */
    public ObservableList<FileAccessRecord> getObservableRecentFiles() {
        return observableRecentFiles;
    }

    /**
     * 获取按机制分组的最近文件
     */
    public Map<AionMechanismCategory, List<FileAccessRecord>> getRecentFilesByMechanism() {
        Map<AionMechanismCategory, List<FileAccessRecord>> result = new EnumMap<>(AionMechanismCategory.class);

        for (FileAccessRecord record : recentAccess) {
            result.computeIfAbsent(record.getMechanism(), k -> new ArrayList<>())
                  .add(record);
        }

        return result;
    }

    /**
     * 获取机制访问统计
     */
    public Map<AionMechanismCategory, Integer> getMechanismAccessStats() {
        return new EnumMap<>(mechanismAccessCount);
    }

    /**
     * 获取最常访问的机制
     */
    public List<AionMechanismCategory> getMostAccessedMechanisms(int limit) {
        List<Map.Entry<AionMechanismCategory, Integer>> sorted = new ArrayList<>(mechanismAccessCount.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<AionMechanismCategory> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            if (sorted.get(i).getValue() > 0) {
                result.add(sorted.get(i).getKey());
            }
        }
        return result;
    }

    /**
     * 获取最常访问的文件
     */
    public List<String> getMostAccessedFiles(int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(fileAccessCount.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            result.add(sorted.get(i).getKey());
        }
        return result;
    }

    /**
     * 获取关联文件推荐（基于当前文件的机制）
     */
    public List<String> getRelatedFileRecommendations(String currentFile, int limit) {
        if (currentFile == null) return Collections.emptyList();

        AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(currentFile);
        List<String> result = new ArrayList<>();

        // 找出同机制的最近访问文件
        for (FileAccessRecord record : recentAccess) {
            if (record.getMechanism() == mechanism &&
                !record.getFilePath().equalsIgnoreCase(currentFile)) {
                if (!result.contains(record.getFilePath())) {
                    result.add(record.getFilePath());
                }
                if (result.size() >= limit) break;
            }
        }

        return result;
    }

    /**
     * 获取工作会话统计
     */
    public WorkflowStats getSessionStats() {
        return new WorkflowStats(
            sessionStart,
            LocalDateTime.now(),
            recentAccess.size(),
            fileAccessCount.size(),
            getMostAccessedMechanisms(3)
        );
    }

    /**
     * 清除历史记录
     */
    public void clearHistory() {
        recentAccess.clear();
        mechanismAccessCount.replaceAll((k, v) -> 0);
        fileAccessCount.clear();
        Platform.runLater(observableRecentFiles::clear);
        notifyListeners();
    }

    /**
     * 添加历史变更监听器
     */
    public void addHistoryListener(Consumer<List<FileAccessRecord>> listener) {
        historyListeners.add(listener);
    }

    /**
     * 移除历史变更监听器
     */
    public void removeHistoryListener(Consumer<List<FileAccessRecord>> listener) {
        historyListeners.remove(listener);
    }

    /**
     * 通知监听器
     */
    private void notifyListeners() {
        List<FileAccessRecord> snapshot = new ArrayList<>(recentAccess);
        Platform.runLater(() -> {
            for (Consumer<List<FileAccessRecord>> listener : historyListeners) {
                listener.accept(snapshot);
            }
        });
    }

    /**
     * 文件访问记录
     */
    public static class FileAccessRecord {
        private final String filePath;
        private final String fileName;
        private final AionMechanismCategory mechanism;
        private final LocalDateTime accessTime;

        public FileAccessRecord(String filePath, AionMechanismCategory mechanism, LocalDateTime accessTime) {
            this.filePath = filePath;
            this.fileName = new File(filePath).getName();
            this.mechanism = mechanism;
            this.accessTime = accessTime;
        }

        public String getFilePath() { return filePath; }
        public String getFileName() { return fileName; }
        public AionMechanismCategory getMechanism() { return mechanism; }
        public LocalDateTime getAccessTime() { return accessTime; }

        public String getFormattedTime() {
            return accessTime.format(TIME_FORMAT);
        }

        public String getMechanismIcon() {
            return mechanism.getIcon();
        }

        public String getMechanismColor() {
            return mechanism.getColor();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s %s",
                getFormattedTime(),
                mechanism.getIcon(),
                fileName);
        }
    }

    /**
     * 工作流统计
     */
    public static class WorkflowStats {
        private final LocalDateTime sessionStart;
        private final LocalDateTime currentTime;
        private final int totalAccesses;
        private final int uniqueFiles;
        private final List<AionMechanismCategory> topMechanisms;

        public WorkflowStats(LocalDateTime sessionStart, LocalDateTime currentTime,
                           int totalAccesses, int uniqueFiles,
                           List<AionMechanismCategory> topMechanisms) {
            this.sessionStart = sessionStart;
            this.currentTime = currentTime;
            this.totalAccesses = totalAccesses;
            this.uniqueFiles = uniqueFiles;
            this.topMechanisms = topMechanisms;
        }

        public LocalDateTime getSessionStart() { return sessionStart; }
        public LocalDateTime getCurrentTime() { return currentTime; }
        public int getTotalAccesses() { return totalAccesses; }
        public int getUniqueFiles() { return uniqueFiles; }
        public List<AionMechanismCategory> getTopMechanisms() { return topMechanisms; }

        public String getSessionDuration() {
            long minutes = java.time.Duration.between(sessionStart, currentTime).toMinutes();
            if (minutes < 60) {
                return minutes + " 分钟";
            } else {
                return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
            }
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("会话时长: ").append(getSessionDuration()).append("\n");
            sb.append("访问次数: ").append(totalAccesses).append("\n");
            sb.append("访问文件: ").append(uniqueFiles).append(" 个\n");
            if (!topMechanisms.isEmpty()) {
                sb.append("主要关注: ");
                for (int i = 0; i < topMechanisms.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(topMechanisms.get(i).getIcon())
                      .append(" ")
                      .append(topMechanisms.get(i).getDisplayName());
                }
            }
            return sb.toString();
        }
    }
}
