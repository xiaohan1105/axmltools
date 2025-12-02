package red.jiuzhou.theme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件依赖分析器
 *
 * 分析XML文件之间的依赖关系，包括：
 * - 外键引用（item_id, npc_id等）
 * - 字符串引用（string_id）
 * - 文件名模式关联
 *
 * @author Claude
 * @version 1.0
 */
public class FileDependencyAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(FileDependencyAnalyzer.class);

    // ID字段模式
    private static final Set<String> ID_FIELD_PATTERNS = new HashSet<>(Arrays.asList(
            "id", "item_id", "npc_id", "skill_id", "quest_id", "map_id",
            "string_id", "text_id", "icon_id", "model_id", "effect_id"
    ));

    // 引用字段模式（指向其他文件的字段）
    private static final Set<String> REFERENCE_FIELD_PATTERNS = new HashSet<>(Arrays.asList(
            "item_ref", "npc_ref", "skill_ref", "quest_ref",
            "required_item", "reward_item", "drop_item",
            "target_npc", "required_quest", "next_quest"
    ));

    /**
     * 分析目录中所有XML文件的依赖关系
     */
    public DependencyGraph analyzeDependencies(Path directory) {
        log.info("开始分析目录: {}", directory);

        DependencyGraph graph = new DependencyGraph();

        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .collect(Collectors.toList());

            log.info("找到 {} 个XML文件", xmlFiles.size());

            // 第一遍：收集所有ID
            Map<String, Set<String>> fileIdMap = new HashMap<>();
            for (Path file : xmlFiles) {
                Set<String> ids = extractIds(file);
                fileIdMap.put(file.toString(), ids);
                graph.addFile(file);
            }

            // 第二遍：分析引用关系
            for (Path file : xmlFiles) {
                analyzeFileReferences(file, graph, fileIdMap);
            }

            log.info("依赖分析完成: {} 个文件, {} 条依赖关系",
                    graph.getFileCount(), graph.getDependencyCount());

        } catch (Exception e) {
            log.error("分析依赖关系失败", e);
        }

        return graph;
    }

    /**
     * 分析单个文件会影响哪些其他文件
     */
    public Set<Path> analyzeImpact(Path targetFile, DependencyGraph graph) {
        Set<Path> impacted = new HashSet<>();
        Set<Path> visited = new HashSet<>();
        Queue<Path> queue = new LinkedList<>();

        queue.offer(targetFile);
        visited.add(targetFile);

        while (!queue.isEmpty()) {
            Path current = queue.poll();
            Set<Path> dependents = graph.getDependents(current);

            for (Path dependent : dependents) {
                if (!visited.contains(dependent)) {
                    visited.add(dependent);
                    impacted.add(dependent);
                    queue.offer(dependent);
                }
            }
        }

        return impacted;
    }

    /**
     * 提取文件中的所有ID
     */
    private Set<String> extractIds(Path file) {
        Set<String> ids = new HashSet<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file.toFile());

            NodeList elements = doc.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                NamedNodeMap attributes = element.getAttributes();

                for (int j = 0; j < attributes.getLength(); j++) {
                    Attr attr = (Attr) attributes.item(j);
                    String attrName = attr.getName().toLowerCase();

                    if (ID_FIELD_PATTERNS.contains(attrName)) {
                        ids.add(attr.getValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取ID失败: {}", file, e);
        }

        return ids;
    }

    /**
     * 分析文件的引用关系
     */
    private void analyzeFileReferences(Path file, DependencyGraph graph,
                                       Map<String, Set<String>> fileIdMap) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file.toFile());

            NodeList elements = doc.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                NamedNodeMap attributes = element.getAttributes();

                for (int j = 0; j < attributes.getLength(); j++) {
                    Attr attr = (Attr) attributes.item(j);
                    String attrName = attr.getName().toLowerCase();
                    String attrValue = attr.getValue();

                    // 检查是否为引用字段
                    if (isReferenceField(attrName)) {
                        // 查找包含此ID的文件
                        for (Map.Entry<String, Set<String>> entry : fileIdMap.entrySet()) {
                            if (entry.getValue().contains(attrValue)) {
                                Path referencedFile = new File(entry.getKey()).toPath();
                                if (!referencedFile.equals(file)) {
                                    graph.addDependency(file, referencedFile,
                                            new Dependency(attrName, attrValue, DependencyType.ID_REFERENCE));
                                }
                            }
                        }
                    }
                }
            }

            // 分析文件名关联（如 client_items.xml 和 server_items.xml）
            analyzeFileNameRelations(file, graph, fileIdMap.keySet());

        } catch (Exception e) {
            log.warn("分析文件引用失败: {}", file, e);
        }
    }

    /**
     * 分析基于文件名的关联
     */
    private void analyzeFileNameRelations(Path file, DependencyGraph graph, Set<String> allFiles) {
        String fileName = file.getFileName().toString().toLowerCase();
        String baseName = extractBaseName(fileName);

        for (String otherFilePath : allFiles) {
            Path otherFile = new File(otherFilePath).toPath();
            if (otherFile.equals(file)) {
                continue;
            }

            String otherFileName = otherFile.getFileName().toString().toLowerCase();
            String otherBaseName = extractBaseName(otherFileName);

            // 如果基础名称相同，说明可能是关联文件
            if (baseName.equals(otherBaseName)) {
                graph.addDependency(file, otherFile,
                        new Dependency("file_name", baseName, DependencyType.FILE_NAME_PATTERN));
            }
        }
    }

    /**
     * 提取文件的基础名称（去除前缀和后缀）
     */
    private String extractBaseName(String fileName) {
        String base = fileName.replace(".xml", "");

        // 去除常见前缀
        base = base.replaceAll("^(client_|server_|svr_|clt_)", "");

        // 去除常见后缀
        base = base.replaceAll("_(config|data|info)$", "");

        return base;
    }

    /**
     * 检查字段是否为引用字段
     */
    private boolean isReferenceField(String fieldName) {
        String lower = fieldName.toLowerCase();

        // 直接匹配
        if (REFERENCE_FIELD_PATTERNS.contains(lower)) {
            return true;
        }

        // 模式匹配
        return lower.matches(".*_(id|ref)$") && !lower.equals("id");
    }

    /**
     * 依赖图
     */
    public static class DependencyGraph {
        // 文件 -> 它依赖的文件列表
        private final Map<Path, Set<DependencyEdge>> dependencies = new HashMap<>();

        // 文件 -> 依赖它的文件列表（反向索引）
        private final Map<Path, Set<Path>> dependents = new HashMap<>();

        public void addFile(Path file) {
            dependencies.putIfAbsent(file, new HashSet<>());
            dependents.putIfAbsent(file, new HashSet<>());
        }

        public void addDependency(Path from, Path to, Dependency dependency) {
            dependencies.computeIfAbsent(from, k -> new HashSet<>()).add(
                    new DependencyEdge(to, dependency));
            dependents.computeIfAbsent(to, k -> new HashSet<>()).add(from);
        }

        public Set<DependencyEdge> getDependencies(Path file) {
            return dependencies.getOrDefault(file, Collections.emptySet());
        }

        public Set<Path> getDependents(Path file) {
            return dependents.getOrDefault(file, Collections.emptySet());
        }

        public int getFileCount() {
            return dependencies.size();
        }

        public int getDependencyCount() {
            return dependencies.values().stream()
                    .mapToInt(Set::size)
                    .sum();
        }

        public Set<Path> getAllFiles() {
            return new HashSet<>(dependencies.keySet());
        }

        /**
         * 生成依赖报告
         */
        public String generateReport(Path targetFile) {
            StringBuilder report = new StringBuilder();
            report.append("文件: ").append(targetFile.getFileName()).append("\n\n");

            Set<DependencyEdge> deps = getDependencies(targetFile);
            if (!deps.isEmpty()) {
                report.append("直接依赖 (").append(deps.size()).append("):\n");
                for (DependencyEdge edge : deps) {
                    report.append("  → ").append(edge.getTarget().getFileName())
                            .append(" (").append(edge.getDependency().getType().getDisplayName()).append(")\n");
                }
                report.append("\n");
            }

            Set<Path> dependentFiles = getDependents(targetFile);
            if (!dependentFiles.isEmpty()) {
                report.append("被依赖 (").append(dependentFiles.size()).append("):\n");
                for (Path dependent : dependentFiles) {
                    report.append("  ← ").append(dependent.getFileName()).append("\n");
                }
                report.append("\n");
            }

            if (deps.isEmpty() && dependentFiles.isEmpty()) {
                report.append("无依赖关系\n");
            }

            return report.toString();
        }
    }

    /**
     * 依赖边
     */
    public static class DependencyEdge {
        private final Path target;
        private final Dependency dependency;

        public DependencyEdge(Path target, Dependency dependency) {
            this.target = target;
            this.dependency = dependency;
        }

        public Path getTarget() {
            return target;
        }

        public Dependency getDependency() {
            return dependency;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DependencyEdge that = (DependencyEdge) o;
            return Objects.equals(target, that.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(target);
        }
    }

    /**
     * 依赖详情
     */
    public static class Dependency {
        private final String fieldName;
        private final String value;
        private final DependencyType type;

        public Dependency(String fieldName, String value, DependencyType type) {
            this.fieldName = fieldName;
            this.value = value;
            this.type = type;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getValue() {
            return value;
        }

        public DependencyType getType() {
            return type;
        }

        @Override
        public String toString() {
            return String.format("%s: %s (%s)", fieldName, value, type.getDisplayName());
        }
    }

    /**
     * 依赖类型
     */
    public enum DependencyType {
        ID_REFERENCE("ID引用", "通过ID字段引用其他文件中的记录"),
        STRING_REFERENCE("文本引用", "引用字符串表中的文本"),
        FILE_NAME_PATTERN("文件名关联", "基于文件命名模式的关联关系");

        private final String displayName;
        private final String description;

        DependencyType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }
}
