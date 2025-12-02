package red.jiuzhou.analysis.aion;

import java.io.File;
import java.util.*;

/**
 * Aion机制视图模型
 *
 * <p>组织和管理按机制分类的XML文件，支持公共/本地化文件的对比展示。
 *
 * @author Claude
 * @version 1.0
 */
public class AionMechanismView {

    private final Map<AionMechanismCategory, MechanismGroup> groups;
    private final List<LocalizedOverride> localizedOverrides;
    private final Statistics statistics;

    public AionMechanismView() {
        this.groups = new LinkedHashMap<>();
        this.localizedOverrides = new ArrayList<>();
        this.statistics = new Statistics();

        // 初始化所有分类组
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            groups.put(category, new MechanismGroup(category));
        }
    }

    /**
     * 添加文件到视图
     */
    public void addFile(FileEntry entry) {
        MechanismGroup group = groups.get(entry.getCategory());
        if (group != null) {
            group.addFile(entry);
            statistics.incrementTotal();
            if (entry.isLocalized()) {
                statistics.incrementLocalized();
            }
        }
    }

    /**
     * 添加本地化覆盖记录
     */
    public void addLocalizedOverride(LocalizedOverride override) {
        localizedOverrides.add(override);
    }

    /**
     * 获取所有非空的机制分组
     */
    public List<MechanismGroup> getNonEmptyGroups() {
        List<MechanismGroup> result = new ArrayList<>();
        for (MechanismGroup group : groups.values()) {
            if (group.getFileCount() > 0) {
                result.add(group);
            }
        }
        return result;
    }

    /**
     * 获取指定分类的机制组
     */
    public MechanismGroup getGroup(AionMechanismCategory category) {
        return groups.get(category);
    }

    /**
     * 获取所有本地化覆盖
     */
    public List<LocalizedOverride> getLocalizedOverrides() {
        return Collections.unmodifiableList(localizedOverrides);
    }

    /**
     * 获取统计信息
     */
    public Statistics getStatistics() {
        return statistics;
    }

    /**
     * 机制分组
     */
    public static class MechanismGroup {
        private final AionMechanismCategory category;
        private final List<FileEntry> publicFiles;
        private final List<FileEntry> localizedFiles;

        public MechanismGroup(AionMechanismCategory category) {
            this.category = category;
            this.publicFiles = new ArrayList<>();
            this.localizedFiles = new ArrayList<>();
        }

        public void addFile(FileEntry entry) {
            if (entry.isLocalized()) {
                localizedFiles.add(entry);
            } else {
                publicFiles.add(entry);
            }
        }

        public AionMechanismCategory getCategory() {
            return category;
        }

        public List<FileEntry> getPublicFiles() {
            return Collections.unmodifiableList(publicFiles);
        }

        public List<FileEntry> getLocalizedFiles() {
            return Collections.unmodifiableList(localizedFiles);
        }

        public List<FileEntry> getAllFiles() {
            List<FileEntry> all = new ArrayList<>(publicFiles);
            all.addAll(localizedFiles);
            return all;
        }

        public int getFileCount() {
            return publicFiles.size() + localizedFiles.size();
        }

        public int getPublicFileCount() {
            return publicFiles.size();
        }

        public int getLocalizedFileCount() {
            return localizedFiles.size();
        }
    }

    /**
     * 文件条目
     */
    public static class FileEntry {
        private final String fileName;
        private final String relativePath;
        private final File file;
        private final AionMechanismCategory category;
        private final DetectionResult detectionResult;
        private final boolean localized;
        private final long fileSize;

        public FileEntry(String fileName, String relativePath, File file,
                        DetectionResult detectionResult, boolean localized) {
            this.fileName = fileName;
            this.relativePath = relativePath;
            this.file = file;
            this.category = detectionResult.getCategory();
            this.detectionResult = detectionResult;
            this.localized = localized;
            this.fileSize = file.length();
        }

        public String getFileName() {
            return fileName;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public File getFile() {
            return file;
        }

        public AionMechanismCategory getCategory() {
            return category;
        }

        public DetectionResult getDetectionResult() {
            return detectionResult;
        }

        public boolean isLocalized() {
            return localized;
        }

        public long getFileSize() {
            return fileSize;
        }

        /**
         * 获取文件大小的可读格式
         */
        public String getFileSizeReadable() {
            if (fileSize < 1024) {
                return fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                return String.format("%.1f KB", fileSize / 1024.0);
            } else {
                return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
            }
        }

        /**
         * 获取显示名称（带本地化标记）
         */
        public String getDisplayName() {
            if (localized) {
                return fileName + " [本地化]";
            }
            return fileName;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    /**
     * 本地化覆盖记录
     */
    public static class LocalizedOverride {
        private final String fileName;
        private final File publicFile;
        private final File localizedFile;
        private final AionMechanismCategory category;

        public LocalizedOverride(String fileName, File publicFile,
                                File localizedFile, AionMechanismCategory category) {
            this.fileName = fileName;
            this.publicFile = publicFile;
            this.localizedFile = localizedFile;
            this.category = category;
        }

        public String getFileName() {
            return fileName;
        }

        public File getPublicFile() {
            return publicFile;
        }

        public File getLocalizedFile() {
            return localizedFile;
        }

        public AionMechanismCategory getCategory() {
            return category;
        }

        /**
         * 检查两个文件大小是否相同
         */
        public boolean isSameSize() {
            return publicFile.length() == localizedFile.length();
        }

        /**
         * 获取大小差异
         */
        public long getSizeDifference() {
            return localizedFile.length() - publicFile.length();
        }
    }

    /**
     * 统计信息
     */
    public static class Statistics {
        private int totalFiles = 0;
        private int localizedFiles = 0;
        private final Map<AionMechanismCategory, Integer> categoryCount = new LinkedHashMap<>();

        public void incrementTotal() {
            totalFiles++;
        }

        public void incrementLocalized() {
            localizedFiles++;
        }

        public void incrementCategory(AionMechanismCategory category) {
            categoryCount.merge(category, 1, Integer::sum);
        }

        public int getTotalFiles() {
            return totalFiles;
        }

        public int getLocalizedFiles() {
            return localizedFiles;
        }

        public int getPublicFiles() {
            return totalFiles - localizedFiles;
        }

        public Map<AionMechanismCategory, Integer> getCategoryCount() {
            return Collections.unmodifiableMap(categoryCount);
        }

        /**
         * 获取分类数量（非空分类数）
         */
        public int getCategoryTypeCount() {
            return categoryCount.size();
        }

        /**
         * 生成统计摘要
         */
        public String getSummary() {
            return String.format("总计 %d 个文件 (公共: %d, 本地化: %d), %d 个机制分类",
                    totalFiles, getPublicFiles(), localizedFiles, getCategoryTypeCount());
        }
    }
}
