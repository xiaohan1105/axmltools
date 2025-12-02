package red.jiuzhou.dbxml;

import java.util.*;

public class TableForestBuilder {

    /**
     * 构建多个 TableConf 构成的树结构森林
     * @param confList 多个配置
     * @return root list 和 索引 map
     */
    public static TreeBuildResult buildForest(List<TableConf> confList) {
        Map<String, TableNode> indexMap = new HashMap<>();
        Set<String> childTableNames = new HashSet<>();

        // Step1：构建所有子树，并建立索引
        for (TableConf conf : confList) {
            buildTree(conf, null, indexMap, childTableNames);
        }

        // Step2：识别“根”节点（即不是任何其他表的子表的）
        List<TableNode> roots = new ArrayList<>();
        for (TableNode node : indexMap.values()) {
            if (!childTableNames.contains(node.tableName) && node.parent == null) {
                roots.add(node);
            }
        }

        return new TreeBuildResult(roots, indexMap);
    }

    public static TreeBuildResult buildOneForest(TableConf tableConf) {
        return buildForest(Collections.singletonList(tableConf));
    }
    private static void buildTree(TableConf conf, TableNode parent, Map<String, TableNode> indexMap, Set<String> childSet) {
        String tableName = conf.getTableName();
        if (!indexMap.containsKey(tableName)) {
            indexMap.put(tableName, new TableNode(tableName));
        }
        TableNode current = indexMap.get(tableName);

        if (parent != null) {
            parent.addChild(current);
            childSet.add(current.tableName);
        }

        for (ColumnMapping col : conf.getList()) {
            buildSubTreeRecursive(col, current, indexMap, childSet);
        }
    }

    private static void buildSubTreeRecursive(ColumnMapping col, TableNode parent, Map<String, TableNode> indexMap, Set<String> childSet) {
        if (col.getTableName() == null || col.getTableName().trim().isEmpty()) return;

        TableNode child = indexMap.computeIfAbsent(col.getTableName(), TableNode::new);
        parent.addChild(child);
        childSet.add(child.tableName);
        if(col.getList() == null){
            return;
        }
        for (ColumnMapping sub : col.getList()) {
            buildSubTreeRecursive(sub, child, indexMap, childSet);
        }
    }

    public static class TreeBuildResult {
        public List<TableNode> roots;
        public Map<String, TableNode> tableIndex;

        public TreeBuildResult(List<TableNode> roots, Map<String, TableNode> tableIndex) {
            this.roots = roots;
            this.tableIndex = tableIndex;
        }
    }
}
