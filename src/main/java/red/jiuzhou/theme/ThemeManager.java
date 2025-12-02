package red.jiuzhou.theme;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主题管理器
 *
 * 负责主题的加载、保存、应用和版本管理
 *
 * @author Claude
 * @version 1.0
 */
public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    private final Path themeDirectory;
    private final Map<String, Theme> loadedThemes = new ConcurrentHashMap<>();
    private final Map<String, List<ThemeVersion>> themeVersions = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper;

    public ThemeManager(Path themeDirectory) {
        this.themeDirectory = themeDirectory;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.registerModule(new JavaTimeModule());

        // 确保主题目录存在
        try {
            Files.createDirectories(themeDirectory);
        } catch (IOException e) {
            log.error("创建主题目录失败: {}", themeDirectory, e);
        }
    }

    /**
     * 加载主题
     * 首先检查内存缓存,如果未缓存则从文件系统加载
     *
     * @param themeId 主题ID
     * @return 加载的主题对象
     * @throws IOException 如果主题文件不存在或读取失败
     */
    public Theme loadTheme(String themeId) throws IOException {
        // 先检查缓存
        if (loadedThemes.containsKey(themeId)) {
            return loadedThemes.get(themeId);
        }

        Path themePath = themeDirectory.resolve(themeId).resolve("theme.yaml");
        if (!Files.exists(themePath)) {
            throw new IOException("主题不存在: " + themeId);
        }

        // 使用DTO加载
        ThemeDTO dto = yamlMapper.readValue(themePath.toFile(), ThemeDTO.class);
        Theme theme = dto.toTheme();
        loadedThemes.put(themeId, theme);

        log.info("已加载主题: {} (版本 {})", theme.getName(), theme.getVersion());
        return theme;
    }

    /**
     * 保存主题
     * 将主题序列化为YAML格式并保存到文件系统,同时更新内存缓存
     *
     * @param theme 要保存的主题对象
     * @throws IOException 如果写入文件失败
     */
    public void saveTheme(Theme theme) throws IOException {
        Path themeDir = themeDirectory.resolve(theme.getId());
        Files.createDirectories(themeDir);

        Path themePath = themeDir.resolve("theme.yaml");

        // 使用DTO保存
        ThemeDTO dto = ThemeDTO.fromTheme(theme);
        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(themePath.toFile(), dto);

        loadedThemes.put(theme.getId(), theme);

        log.info("已保存主题: {} (版本 {})", theme.getName(), theme.getVersion());
    }

    /**
     * 列出所有可用主题
     * 扫描主题目录,读取每个主题的基本信息并返回摘要列表
     *
     * @return 主题摘要列表,按名称排序
     */
    public List<ThemeSummary> listThemes() {
        List<ThemeSummary> summaries = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(themeDirectory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path themeFile = entry.resolve("theme.yaml");
                    if (Files.exists(themeFile)) {
                        try {
                            ThemeDTO dto = yamlMapper.readValue(themeFile.toFile(), ThemeDTO.class);
                            Theme theme = dto.toTheme();
                            summaries.add(new ThemeSummary(theme));
                        } catch (Exception e) {
                            log.warn("加载主题摘要失败: {}", entry, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("列举主题失败", e);
        }

        summaries.sort(Comparator.comparing(ThemeSummary::getName));
        return summaries;
    }

    /**
     * 删除主题
     * 删除主题目录及其所有文件(包括版本快照),并清除内存缓存
     *
     * @param themeId 主题ID
     * @throws IOException 如果删除文件失败
     */
    public void deleteTheme(String themeId) throws IOException {
        Path themeDir = themeDirectory.resolve(themeId);
        if (Files.exists(themeDir)) {
            deleteDirectory(themeDir);
            loadedThemes.remove(themeId);
            themeVersions.remove(themeId);
            log.info("已删除主题: {}", themeId);
        }
    }

    /**
     * 创建主题快照（版本）
     * 保存当前主题状态的完整副本,用于版本管理和回滚
     *
     * @param themeId 主题ID
     * @param versionTag 版本标签
     * @param comment 版本说明
     * @return 创建的版本信息
     * @throws IOException 如果保存快照失败
     */
    public ThemeVersion createSnapshot(String themeId, String versionTag, String comment) throws IOException {
        Theme theme = loadTheme(themeId);

        ThemeVersion version = new ThemeVersion(
                UUID.randomUUID().toString(),
                themeId,
                theme.getVersion(),
                versionTag,
                comment,
                Instant.now()
        );

        // 保存快照
        Path versionDir = themeDirectory.resolve(themeId).resolve("versions").resolve(version.getId());
        Files.createDirectories(versionDir);

        Path snapshotPath = versionDir.resolve("snapshot.yaml");
        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(snapshotPath.toFile(), ThemeDTO.fromTheme(theme));

        Path metaPath = versionDir.resolve("meta.yaml");
        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(metaPath.toFile(), version);

        // 记录版本
        List<ThemeVersion> cachedVersions = themeVersions.computeIfAbsent(themeId, k -> new ArrayList<>());
        cachedVersions.add(version);
        cachedVersions.sort(Comparator.comparing(ThemeVersion::getCreatedAt).reversed());

        log.info("已创建主题快照: {} - {}", themeId, versionTag);
        return version;
    }

    /**
     * 获取主题的所有版本
     * 扫描主题的versions目录,读取所有版本的元数据
     *
     * @param themeId 主题ID
     * @return 版本列表,按创建时间倒序排序
     */
    public List<ThemeVersion> getThemeVersions(String themeId) {
        List<ThemeVersion> versions = new ArrayList<>();
        Path versionsDir = themeDirectory.resolve(themeId).resolve("versions");

        if (!Files.exists(versionsDir)) {
            return versions;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
            for (Path versionDir : stream) {
                if (!Files.isDirectory(versionDir)) {
                    continue;
                }

                ThemeVersion version = null;
                Path metaFile = versionDir.resolve("meta.yaml");
                if (Files.exists(metaFile)) {
                    try {
                        version = yamlMapper.readValue(metaFile.toFile(), ThemeVersion.class);
                    } catch (Exception e) {
                        log.warn("加载版本元数据失败: {}", versionDir, e);
                    }
                }

                if (version == null) {
                    Path snapshotFile = versionDir.resolve("snapshot.yaml");
                    if (Files.exists(snapshotFile)) {
                        try {
                            ThemeDTO dto = yamlMapper.readValue(snapshotFile.toFile(), ThemeDTO.class);
                            String versionId = versionDir.getFileName().toString();
                            String themeVersion = dto.getVersion() != null ? dto.getVersion() : "unknown";
                            Instant createdAt = Files.getLastModifiedTime(snapshotFile).toInstant();
                            version = new ThemeVersion(
                                    versionId,
                                    themeId,
                                    themeVersion,
                                    "legacy",
                                    "从旧快照自动恢复",
                                    createdAt
                            );
                        } catch (Exception e) {
                            log.warn("从快照恢复版本信息失败: {}", versionDir, e);
                        }
                    }
                }

                if (version != null) {
                    versions.add(version);
                }
            }
        } catch (IOException e) {
            log.error("列举版本失败", e);
        }

        versions.sort(Comparator.comparing(ThemeVersion::getCreatedAt).reversed());
        themeVersions.put(themeId, new ArrayList<>(versions));
        return versions;
    }

    /**
     * 回滚到指定版本
     * 在回滚前会自动创建当前版本的备份快照
     *
     * @param themeId 主题ID
     * @param versionId 要回滚到的版本ID
     * @throws IOException 如果版本快照不存在或回滚失败
     */
    public void rollbackToVersion(String themeId, String versionId) throws IOException {
        Path snapshotPath = themeDirectory.resolve(themeId)
                .resolve("versions")
                .resolve(versionId)
                .resolve("snapshot.yaml");

        if (!Files.exists(snapshotPath)) {
            throw new IOException("版本快照不存在: " + versionId);
        }

        ThemeDTO dto = yamlMapper.readValue(snapshotPath.toFile(), ThemeDTO.class);
        Theme theme = dto.toTheme();

        // 保存当前版本作为备份
        createSnapshot(themeId, "auto-backup-before-rollback", "回滚前自动备份");

        // 应用快照
        saveTheme(theme);

        log.info("已回滚主题 {} 到版本 {}", themeId, versionId);
    }

    /**
     * 导出主题包
     * 将主题及其所有元数据打包导出到指定路径
     *
     * @param themeId 主题ID
     * @param targetPath 导出目标路径
     * @throws IOException 如果导出失败
     */
    public void exportTheme(String themeId, Path targetPath) throws IOException {
        Theme theme = loadTheme(themeId);
        Path themeDir = themeDirectory.resolve(themeId);

        // 创建临时目录用于打包
        Path tempDir = Files.createTempDirectory("theme-export-");
        try {
            // 复制主题文件
            copyDirectory(themeDir, tempDir);

            // 创建元信息文件
            ThemePackage packageInfo = new ThemePackage(
                    theme.getId(),
                    theme.getName(),
                    theme.getVersion(),
                    Instant.now()
            );

            Path packageInfoPath = tempDir.resolve("package.yaml");
            yamlMapper.writeValue(packageInfoPath.toFile(), packageInfo);

            // 打包（这里简化为复制整个目录）
            copyDirectory(tempDir, targetPath);

            log.info("已导出主题包: {} -> {}", themeId, targetPath);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    /**
     * 导入主题包
     * 从主题包中导入主题,如果主题已存在会自动创建备份
     *
     * @param packagePath 主题包路径
     * @return 导入的主题对象
     * @throws IOException 如果主题包无效或导入失败
     */
    public Theme importTheme(Path packagePath) throws IOException {
        // 读取包信息
        Path packageInfoPath = packagePath.resolve("package.yaml");
        if (!Files.exists(packageInfoPath)) {
            throw new IOException("无效的主题包: 缺少 package.yaml");
        }

        ThemePackage packageInfo = yamlMapper.readValue(packageInfoPath.toFile(), ThemePackage.class);

        // 检查主题是否已存在
        String themeId = packageInfo.getThemeId();
        Path targetDir = themeDirectory.resolve(themeId);
        if (Files.exists(targetDir)) {
            // 备份现有主题
            createSnapshot(themeId, "auto-backup-before-import", "导入前自动备份");
        }

        // 复制主题文件
        copyDirectory(packagePath, targetDir);

        // 加载主题
        Theme theme = loadTheme(themeId);

        log.info("已导入主题: {} (版本 {})", theme.getName(), theme.getVersion());
        return theme;
    }

    /**
     * 验证主题
     * 检查主题配置的完整性和正确性
     *
     * @param theme 要验证的主题
     * @return 错误列表,如果为空表示验证通过
     */
    public List<String> validateTheme(Theme theme) {
        List<String> errors = new ArrayList<>();

        if (theme.getName() == null || theme.getName().isEmpty()) {
            errors.add("主题名称不能为空");
        }

        if (theme.getRules().isEmpty()) {
            errors.add("主题必须至少包含一个转换规则");
        }

        if (theme.getSettings() == null) {
            errors.add("主题设置不能为空");
        }

        // 验证AI配置
        if (theme.getSettings() != null && theme.getSettings().isUseAiTransform()) {
            if (theme.getAiPrompts().isEmpty()) {
                errors.add("使用AI转换时必须提供提示词");
            }
        }

        return errors;
    }

    // 辅助方法

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.warn("复制文件失败: {}", src, e);
            }
        });
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("删除文件失败: {}", path, e);
                        }
                    });
        }
    }

    /**
     * 主题摘要（用于列表显示）
     */
    public static class ThemeSummary {
        private final String id;
        private final String name;
        private final String description;
        private final String version;
        private final ThemeType type;
        private final ThemeScope scope;
        private final Instant createdAt;

        public ThemeSummary(Theme theme) {
            this.id = theme.getId();
            this.name = theme.getName();
            this.description = theme.getDescription();
            this.version = theme.getVersion();
            this.type = theme.getType();
            this.scope = theme.getScope();
            this.createdAt = theme.getCreatedAt();
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getVersion() { return version; }
        public ThemeType getType() { return type; }
        public ThemeScope getScope() { return scope; }
        public Instant getCreatedAt() { return createdAt; }
    }

    /**
     * 主题版本
     */
    public static class ThemeVersion {
        private final String id;
        private final String themeId;
        private final String themeVersion;
        private final String tag;
        private final String comment;
        private final Instant createdAt;

        @JsonCreator
        public ThemeVersion(@JsonProperty("id") String id,
                            @JsonProperty("themeId") String themeId,
                            @JsonProperty("themeVersion") String themeVersion,
                            @JsonProperty("tag") String tag,
                            @JsonProperty("comment") String comment,
                            @JsonProperty("createdAt") Instant createdAt) {
            this.id = id;
            this.themeId = themeId;
            this.themeVersion = themeVersion;
            this.tag = tag;
            this.comment = comment;
            this.createdAt = createdAt;
        }

        // Getters
        public String getId() { return id; }
        public String getThemeId() { return themeId; }
        public String getThemeVersion() { return themeVersion; }
        public String getTag() { return tag; }
        public String getComment() { return comment; }
        public Instant getCreatedAt() { return createdAt; }
    }

    /**
     * 主题包信息
     */
    public static class ThemePackage {
        private final String themeId;
        private final String themeName;
        private final String version;
        private final Instant exportedAt;

        @JsonCreator
        public ThemePackage(@JsonProperty("themeId") String themeId,
                             @JsonProperty("themeName") String themeName,
                             @JsonProperty("version") String version,
                             @JsonProperty("exportedAt") Instant exportedAt) {
            this.themeId = themeId;
            this.themeName = themeName;
            this.version = version;
            this.exportedAt = exportedAt;
        }

        // Getters
        public String getThemeId() { return themeId; }
        public String getThemeName() { return themeName; }
        public String getVersion() { return version; }
        public Instant getExportedAt() { return exportedAt; }
    }
}
