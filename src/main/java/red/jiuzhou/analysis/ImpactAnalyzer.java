package red.jiuzhou.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.relationship.XmlRelationshipAnalyzer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 影响分析器
 *
 * <p>分析删除或修改数据时的级联影响，帮助设计师识别潜在的数据一致性问题。
 *
 * <p><b>核心功能:</b>
 * <ul>
 *   <li>删除影响分析 - 识别删除某条记录会影响哪些表</li>
 *   <li>修改影响分析 - 识别修改某字段值的影响范围</li>
 *   <li>依赖关系图 - 构建表间依赖关系图</li>
 *   <li>级联操作建议 - 提供删除/修改的建议操作步骤</li>
 * </ul>
 *
 * <p><b>使用示例:</b>
 * <pre>{@code
 * ImpactAnalyzer analyzer = new ImpactAnalyzer(relationships);
 * ImpactReport report = analyzer.analyzeDeleteImpact("items", "id", "1001");
 * // 输出：删除物品1001会影响：npc_drop表3处、shop_items表2处、quest_reward表1处
 * }</pre>
 *
 * @author Claude
 * @version 1.0
 */
public class ImpactAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ImpactAnalyzer.class);

    /**
     * 重复字符串（Java 8兼容）
     */
    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    // 表间关系索引：targetTable -> sourceTable -> relationships
    private final Map<String, Map<String, List<Relationship>>> reverseIndex;

    // 正向索引：sourceTable -> targetTable -> relationships
    private final Map<String, Map<String, List<Relationship>>> forwardIndex;

    public ImpactAnalyzer(List<XmlRelationshipAnalyzer.Relationship> xmlRelationships) {
        this.reverseIndex = new HashMap<>();
        this.forwardIndex = new HashMap<>();
        buildIndexes(xmlRelationships);
    }

    private void buildIndexes(List<XmlRelationshipAnalyzer.Relationship> xmlRelationships) {
        for (XmlRelationshipAnalyzer.Relationship rel : xmlRelationships) {
            String sourceTable = extractTableName(rel.getSourceFileKey());
            String targetTable = extractTableName(rel.getTargetFileKey());
            String sourceField = rel.getSourceColumnName();
            String targetField = rel.getTargetColumnName();

            Relationship relationship = new Relationship(
                sourceTable, sourceField, rel.getSourceColumnPath(),
                targetTable, targetField, rel.getTargetColumnPath(),
                rel.getConfidence(), rel.getMatchCount()
            );

            // 反向索引：targetTable -> sourceTable
            reverseIndex.computeIfAbsent(targetTable, k -> new HashMap<>())
                       .computeIfAbsent(sourceTable, k -> new ArrayList<>())
                       .add(relationship);

            // 正向索引：sourceTable -> targetTable
            forwardIndex.computeIfAbsent(sourceTable, k -> new HashMap<>())
                       .computeIfAbsent(targetTable, k -> new ArrayList<>())
                       .add(relationship);
        }
    }

    /**
     * 从文件路径提取表名
     */
    private String extractTableName(String fileKey) {
        String fileName = fileKey;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        if (fileName.endsWith(".xml")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        // 移除client_前缀
        return fileName.replaceFirst("^client_", "");
    }

    /**
     * 分析删除影响
     *
     * @param tableName 要删除数据的表名
     * @param fieldName 主键字段名
     * @param value 要删除的值
     * @return 影响报告
     */
    public ImpactReport analyzeDeleteImpact(String tableName, String fieldName, String value) {
        ImpactReport report = new ImpactReport(
            ImpactType.DELETE, tableName, fieldName, value
        );

        // 查找哪些表引用了当前表的该字段
        Map<String, List<Relationship>> references = reverseIndex.get(tableName);
        if (references == null || references.isEmpty()) {
            report.setSummary("未发现其他表引用此数据，可以安全删除");
            return report;
        }

        // 统计影响
        int totalImpactedTables = 0;
        int totalReferences = 0;

        for (Map.Entry<String, List<Relationship>> entry : references.entrySet()) {
            String referencingTable = entry.getKey();
            List<Relationship> rels = entry.getValue();

            // 过滤出相关字段的关系
            List<Relationship> relevantRels = rels.stream()
                .filter(r -> r.targetField.toLowerCase().contains(fieldName.toLowerCase()))
                .collect(Collectors.toList());

            if (!relevantRels.isEmpty()) {
                totalImpactedTables++;
                totalReferences += relevantRels.size();

                for (Relationship rel : relevantRels) {
                    ImpactedReference impacted = new ImpactedReference(
                        referencingTable,
                        rel.sourceField,
                        rel.confidence,
                        "需要删除或清空此字段的值"
                    );
                    report.addImpactedReference(impacted);
                }
            }
        }

        // 生成摘要
        if (totalImpactedTables == 0) {
            report.setSummary("未发现其他表引用此数据，可以安全删除");
            report.setSeverity(Severity.SAFE);
        } else if (totalImpactedTables <= 3) {
            report.setSummary(String.format("警告：删除此数据会影响 %d 张表，共 %d 处引用",
                totalImpactedTables, totalReferences));
            report.setSeverity(Severity.WARNING);
        } else {
            report.setSummary(String.format("严重：删除此数据会影响 %d 张表，共 %d 处引用，可能导致数据不一致",
                totalImpactedTables, totalReferences));
            report.setSeverity(Severity.CRITICAL);
        }

        // 生成级联操作建议
        report.generateCascadeActions();

        return report;
    }

    /**
     * 分析修改影响
     *
     * @param tableName 要修改数据的表名
     * @param fieldName 字段名
     * @param oldValue 旧值
     * @param newValue 新值
     * @return 影响报告
     */
    public ImpactReport analyzeUpdateImpact(String tableName, String fieldName,
                                           String oldValue, String newValue) {
        ImpactReport report = new ImpactReport(
            ImpactType.UPDATE, tableName, fieldName, oldValue
        );
        report.setNewValue(newValue);

        Map<String, List<Relationship>> references = reverseIndex.get(tableName);
        if (references == null || references.isEmpty()) {
            report.setSummary("未发现其他表引用此字段，可以安全修改");
            report.setSeverity(Severity.SAFE);
            return report;
        }

        int totalImpactedTables = 0;
        int totalReferences = 0;

        for (Map.Entry<String, List<Relationship>> entry : references.entrySet()) {
            String referencingTable = entry.getKey();
            List<Relationship> rels = entry.getValue();

            List<Relationship> relevantRels = rels.stream()
                .filter(r -> r.targetField.toLowerCase().contains(fieldName.toLowerCase()))
                .collect(Collectors.toList());

            if (!relevantRels.isEmpty()) {
                totalImpactedTables++;
                totalReferences += relevantRels.size();

                for (Relationship rel : relevantRels) {
                    ImpactedReference impacted = new ImpactedReference(
                        referencingTable,
                        rel.sourceField,
                        rel.confidence,
                        String.format("需要将 %s 从 '%s' 更新为 '%s'",
                            rel.sourceField, oldValue, newValue)
                    );
                    report.addImpactedReference(impacted);
                }
            }
        }

        if (totalImpactedTables == 0) {
            report.setSummary("未发现其他表引用此字段，可以安全修改");
            report.setSeverity(Severity.SAFE);
        } else {
            report.setSummary(String.format("修改此字段会影响 %d 张表，共 %d 处引用，需要同步更新",
                totalImpactedTables, totalReferences));
            report.setSeverity(totalImpactedTables <= 3 ? Severity.WARNING : Severity.CRITICAL);
        }

        report.generateCascadeActions();
        return report;
    }

    /**
     * 构建依赖关系图
     *
     * @param tableName 目标表名
     * @param maxDepth 最大深度
     * @return 依赖图
     */
    public DependencyGraph buildDependencyGraph(String tableName, int maxDepth) {
        DependencyGraph graph = new DependencyGraph(tableName);
        Set<String> visited = new HashSet<>();
        buildDependencyGraphRecursive(tableName, graph, visited, 0, maxDepth);
        return graph;
    }

    private void buildDependencyGraphRecursive(String tableName, DependencyGraph graph,
                                              Set<String> visited, int depth, int maxDepth) {
        if (depth >= maxDepth || visited.contains(tableName)) {
            return;
        }
        visited.add(tableName);

        // 查找依赖当前表的表
        Map<String, List<Relationship>> references = reverseIndex.get(tableName);
        if (references != null) {
            for (Map.Entry<String, List<Relationship>> entry : references.entrySet()) {
                String dependentTable = entry.getKey();
                graph.addDependency(tableName, dependentTable, entry.getValue());
                buildDependencyGraphRecursive(dependentTable, graph, visited, depth + 1, maxDepth);
            }
        }

        // 查找当前表依赖的表
        Map<String, List<Relationship>> dependencies = forwardIndex.get(tableName);
        if (dependencies != null) {
            for (Map.Entry<String, List<Relationship>> entry : dependencies.entrySet()) {
                String dependedTable = entry.getKey();
                graph.addDependency(dependedTable, tableName, entry.getValue());
                buildDependencyGraphRecursive(dependedTable, graph, visited, depth + 1, maxDepth);
            }
        }
    }

    /**
     * 关系定义（简化版）
     */
    public static class Relationship {
        public final String sourceTable;
        public final String sourceField;
        public final String sourceFieldPath;
        public final String targetTable;
        public final String targetField;
        public final String targetFieldPath;
        public final double confidence;
        public final int matchCount;

        public Relationship(String sourceTable, String sourceField, String sourceFieldPath,
                          String targetTable, String targetField, String targetFieldPath,
                          double confidence, int matchCount) {
            this.sourceTable = sourceTable;
            this.sourceField = sourceField;
            this.sourceFieldPath = sourceFieldPath;
            this.targetTable = targetTable;
            this.targetField = targetField;
            this.targetFieldPath = targetFieldPath;
            this.confidence = confidence;
            this.matchCount = matchCount;
        }
    }

    /**
     * 影响报告
     */
    public static class ImpactReport {
        private final ImpactType type;
        private final String tableName;
        private final String fieldName;
        private final String value;
        private String newValue;
        private String summary;
        private Severity severity;
        private final List<ImpactedReference> impactedReferences;
        private final List<CascadeAction> cascadeActions;

        public ImpactReport(ImpactType type, String tableName, String fieldName, String value) {
            this.type = type;
            this.tableName = tableName;
            this.fieldName = fieldName;
            this.value = value;
            this.impactedReferences = new ArrayList<>();
            this.cascadeActions = new ArrayList<>();
            this.severity = Severity.SAFE;
        }

        public void addImpactedReference(ImpactedReference ref) {
            impactedReferences.add(ref);
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public void setSeverity(Severity severity) {
            this.severity = severity;
        }

        public void setNewValue(String newValue) {
            this.newValue = newValue;
        }

        /**
         * 生成级联操作建议
         */
        public void generateCascadeActions() {
            cascadeActions.clear();

            // 按表分组
            Map<String, List<ImpactedReference>> byTable = impactedReferences.stream()
                .collect(Collectors.groupingBy(r -> r.tableName));

            int step = 1;
            for (Map.Entry<String, List<ImpactedReference>> entry : byTable.entrySet()) {
                String table = entry.getKey();
                List<ImpactedReference> refs = entry.getValue();

                String action;
                if (type == ImpactType.DELETE) {
                    action = String.format("删除或清空 %s 表中引用此数据的 %d 条记录",
                        table, refs.size());
                } else {
                    action = String.format("将 %s 表中的 %s 字段从 '%s' 更新为 '%s'",
                        table, refs.get(0).fieldName, value, newValue);
                }

                cascadeActions.add(new CascadeAction(step++, table, refs, action));
            }
        }

        public ImpactType getType() { return type; }
        public String getTableName() { return tableName; }
        public String getFieldName() { return fieldName; }
        public String getValue() { return value; }
        public String getNewValue() { return newValue; }
        public String getSummary() { return summary; }
        public Severity getSeverity() { return severity; }
        public List<ImpactedReference> getImpactedReferences() {
            return Collections.unmodifiableList(impactedReferences);
        }
        public List<CascadeAction> getCascadeActions() {
            return Collections.unmodifiableList(cascadeActions);
        }

        /**
         * 生成格式化报告
         */
        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append(repeatString("═", 60)).append("\n");
            sb.append(type == ImpactType.DELETE ? "删除影响分析报告" : "修改影响分析报告").append("\n");
            sb.append(repeatString("═", 60)).append("\n\n");

            sb.append(String.format("表名：%s\n", tableName));
            sb.append(String.format("字段：%s\n", fieldName));
            sb.append(String.format("值：%s\n", value));
            if (newValue != null) {
                sb.append(String.format("新值：%s\n", newValue));
            }
            sb.append(String.format("严重程度：%s\n\n", severity.getDisplayName()));

            sb.append("摘要：\n");
            sb.append(summary).append("\n\n");

            if (!impactedReferences.isEmpty()) {
                sb.append("受影响的引用（按表分组）：\n");
                sb.append(repeatString("─", 60)).append("\n");

                Map<String, List<ImpactedReference>> byTable = impactedReferences.stream()
                    .collect(Collectors.groupingBy(r -> r.tableName));

                for (Map.Entry<String, List<ImpactedReference>> entry : byTable.entrySet()) {
                    sb.append(String.format("\n【%s】%d 处引用\n", entry.getKey(), entry.getValue().size()));
                    for (ImpactedReference ref : entry.getValue()) {
                        sb.append(String.format("  • 字段: %s (置信度: %.1f%%)\n",
                            ref.fieldName, ref.confidence * 100));
                        sb.append(String.format("    → %s\n", ref.suggestion));
                    }
                }
            }

            if (!cascadeActions.isEmpty()) {
                sb.append("\n级联操作建议：\n");
                sb.append(repeatString("─", 60)).append("\n");
                for (CascadeAction action : cascadeActions) {
                    sb.append(String.format("%d. %s\n", action.step, action.description));
                }
            }

            sb.append("\n").append(repeatString("═", 60)).append("\n");
            return sb.toString();
        }
    }

    /**
     * 受影响的引用
     */
    public static class ImpactedReference {
        public final String tableName;
        public final String fieldName;
        public final double confidence;
        public final String suggestion;

        public ImpactedReference(String tableName, String fieldName,
                               double confidence, String suggestion) {
            this.tableName = tableName;
            this.fieldName = fieldName;
            this.confidence = confidence;
            this.suggestion = suggestion;
        }

        public String getTableName() { return tableName; }
        public String getFieldName() { return fieldName; }
        public double getConfidence() { return confidence; }
        public String getSuggestion() { return suggestion; }
    }

    /**
     * 级联操作
     */
    public static class CascadeAction {
        public final int step;
        public final String tableName;
        public final List<ImpactedReference> references;
        public final String description;

        public CascadeAction(int step, String tableName,
                           List<ImpactedReference> references, String description) {
            this.step = step;
            this.tableName = tableName;
            this.references = references;
            this.description = description;
        }

        public int getStep() { return step; }
        public String getTableName() { return tableName; }
        public List<ImpactedReference> getReferences() { return references; }
        public String getDescription() { return description; }
    }

    /**
     * 依赖关系图
     */
    public static class DependencyGraph {
        public final String rootTable;
        private final Map<String, Set<String>> dependencies; // table -> dependent tables
        private final Map<String, Map<String, List<Relationship>>> relationships;

        public DependencyGraph(String rootTable) {
            this.rootTable = rootTable;
            this.dependencies = new LinkedHashMap<>();
            this.relationships = new HashMap<>();
        }

        public void addDependency(String fromTable, String toTable, List<Relationship> rels) {
            dependencies.computeIfAbsent(fromTable, k -> new LinkedHashSet<>()).add(toTable);
            relationships.computeIfAbsent(fromTable, k -> new HashMap<>()).put(toTable, rels);
        }

        public Set<String> getAllTables() {
            Set<String> all = new HashSet<>(dependencies.keySet());
            dependencies.values().forEach(all::addAll);
            return all;
        }

        public Set<String> getDependentTables(String tableName) {
            return dependencies.getOrDefault(tableName, Collections.emptySet());
        }

        public List<Relationship> getRelationships(String fromTable, String toTable) {
            return relationships.getOrDefault(fromTable, Collections.emptyMap())
                               .getOrDefault(toTable, Collections.emptyList());
        }

        public String getRootTable() { return rootTable; }
        public Map<String, Set<String>> getDependencies() {
            return Collections.unmodifiableMap(dependencies);
        }
    }

    /**
     * 影响类型
     */
    public enum ImpactType {
        DELETE("删除"), UPDATE("修改");

        private final String displayName;

        ImpactType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /**
     * 严重程度
     */
    public enum Severity {
        SAFE("安全", "#4CAF50"),
        WARNING("警告", "#FF9800"),
        CRITICAL("严重", "#F44336");

        private final String displayName;
        private final String color;

        Severity(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
}
