package red.jiuzhou.ui.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 表层级结构辅助类
 *
 * 表的层级规则：
 * - 主表：不包含双下划线 __ 的表
 * - 一级子表：包含一个双下划线 __ 的表 (如 parent__child)
 * - 二级子表：包含两个双下划线 __ 的表 (如 parent__child1__child2)
 *
 * 匹配规则：
 * - 主表只能与主表匹配
 * - 一级子表只能与一级子表匹配
 * - 二级子表只能与二级子表匹配
 * - 不允许跨层级匹配
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 2.0
 */
public class TableHierarchyHelper {

    private static final Logger log = LoggerFactory.getLogger(TableHierarchyHelper.class);

    /**
     * 表层级枚举
     */
    public enum TableLevel {
        MAIN(0, "主表", "🏠"),
        LEVEL_1(1, "一级子表", "📁"),
        LEVEL_2(2, "二级子表", "📄");

        private final int level;
        private final String description;
        private final String icon;

        TableLevel(int level, String description, String icon) {
            this.level = level;
            this.description = description;
            this.icon = icon;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }

        public String getDisplayName() {
            return icon + " " + description;
        }
    }

    /**
     * 表层级信息
     */
    public static class TableHierarchy {
        private String tableName;           // 完整表名
        private TableLevel level;           // 层级
        private String parentTableName;     // 父表名（如果是子表）
        private String childTableName;      // 子表部分名称

        public TableHierarchy(String tableName) {
            this.tableName = tableName;
            parseHierarchy();
        }

        /**
         * 解析表的层级结构
         */
        private void parseHierarchy() {
            // 对于客户端表，需要先去除 "client_" 前缀再计算层级
            // 这样 client_item__details 和 item__details 会被识别为相同层级
            String nameForLevelCalc = tableName;
            if (tableName.startsWith("client_")) {
                nameForLevelCalc = tableName.substring("client_".length());
            }

            // 计算双下划线的数量（基于去除前缀后的表名）
            int doubleUnderscoreCount = countDoubleUnderscores(nameForLevelCalc);

            switch (doubleUnderscoreCount) {
                case 0:
                    // 主表
                    level = TableLevel.MAIN;
                    parentTableName = null;
                    childTableName = null;
                    break;

                case 1:
                    // 一级子表
                    level = TableLevel.LEVEL_1;
                    String[] parts1 = nameForLevelCalc.split("__", 2);
                    // 如果是客户端表，parentTableName 应该包含 client_ 前缀
                    parentTableName = tableName.startsWith("client_") ?
                        "client_" + parts1[0] : parts1[0];
                    childTableName = parts1[1];
                    break;

                case 2:
                    // 二级子表
                    level = TableLevel.LEVEL_2;
                    String[] parts2 = nameForLevelCalc.split("__", 3);
                    // 如果是客户端表，parentTableName 应该包含 client_ 前缀
                    String parentBase = parts2[0] + "__" + parts2[1];
                    parentTableName = tableName.startsWith("client_") ?
                        "client_" + parentBase : parentBase;
                    childTableName = parts2[2];
                    break;

                default:
                    // 超过二级的子表，记录警告
                    log.warn("表 {} 包含 {} 个双下划线，超过支持的最大层级（2级）",
                            tableName, doubleUnderscoreCount);
                    level = TableLevel.LEVEL_2;  // 默认作为二级子表处理
                    break;
            }
        }

        /**
         * 计算字符串中双下划线的数量
         */
        private int countDoubleUnderscores(String str) {
            int count = 0;
            int index = 0;
            while ((index = str.indexOf("__", index)) != -1) {
                count++;
                index += 2;  // 跳过这个双下划线
            }
            return count;
        }

        // Getters
        public String getTableName() {
            return tableName;
        }

        public TableLevel getLevel() {
            return level;
        }

        public String getParentTableName() {
            return parentTableName;
        }

        public String getChildTableName() {
            return childTableName;
        }

        public boolean isMainTable() {
            return level == TableLevel.MAIN;
        }

        public boolean isSubTable() {
            return level != TableLevel.MAIN;
        }

        public String getLevelDisplayName() {
            return level.getDisplayName();
        }

        /**
         * 获取主表名（递归到顶层）
         */
        public String getRootTableName() {
            if (isMainTable()) {
                return tableName;
            }

            // 递归获取最顶层的主表名
            String current = parentTableName;
            while (current != null && current.contains("__")) {
                int lastIndex = current.lastIndexOf("__");
                current = current.substring(0, lastIndex);
            }
            return current != null ? current : tableName;
        }

        @Override
        public String toString() {
            return String.format("TableHierarchy{name='%s', level=%s, parent='%s', child='%s'}",
                    tableName, level.getDescription(), parentTableName, childTableName);
        }
    }

    /**
     * 解析表的层级结构
     *
     * @param tableName 表名
     * @return 层级信息
     */
    public static TableHierarchy parseTableHierarchy(String tableName) {
        return new TableHierarchy(tableName);
    }

    /**
     * 判断两个表是否可以匹配（同层级）
     *
     * @param clientTable 客户端表名
     * @param serverTable 服务端表名
     * @return 是否可以匹配
     */
    public static boolean canMatch(String clientTable, String serverTable) {
        TableHierarchy clientHierarchy = parseTableHierarchy(clientTable);
        TableHierarchy serverHierarchy = parseTableHierarchy(serverTable);

        // 只有同层级的表才能匹配
        boolean canMatch = clientHierarchy.getLevel() == serverHierarchy.getLevel();

        if (!canMatch) {
            log.debug("表 {} ({}) 和 {} ({}) 层级不同，不能匹配",
                    clientTable, clientHierarchy.getLevel().getDescription(),
                    serverTable, serverHierarchy.getLevel().getDescription());
        }

        return canMatch;
    }

    /**
     * 获取表的层级
     *
     * @param tableName 表名
     * @return 层级
     */
    public static TableLevel getTableLevel(String tableName) {
        return parseTableHierarchy(tableName).getLevel();
    }

    /**
     * 从完整表名中提取主表名（去除client_前缀和子表后缀）
     *
     * @param fullTableName 完整表名
     * @return 主表名
     */
    public static String extractMainTableName(String fullTableName) {
        // 去除 client_ 前缀
        String tableName = fullTableName;
        if (tableName.startsWith("client_")) {
            tableName = tableName.substring("client_".length());
        }

        // 提取主表部分（第一个__之前的部分）
        int firstDoubleUnderscore = tableName.indexOf("__");
        if (firstDoubleUnderscore > 0) {
            return tableName.substring(0, firstDoubleUnderscore);
        }

        return tableName;
    }

    /**
     * 从完整表名中提取子表路径
     *
     * @param fullTableName 完整表名
     * @return 子表路径（如果是主表返回null）
     */
    public static String extractSubTablePath(String fullTableName) {
        // 去除 client_ 前缀
        String tableName = fullTableName;
        if (tableName.startsWith("client_")) {
            tableName = tableName.substring("client_".length());
        }

        // 提取子表路径（第一个__之后的部分）
        int firstDoubleUnderscore = tableName.indexOf("__");
        if (firstDoubleUnderscore > 0) {
            return tableName.substring(firstDoubleUnderscore + 2);
        }

        return null;
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        System.out.println("=== 表层级结构测试 ===\n");

        String[] testTables = {
            "item",                          // 主表
            "client_npc",                    // 客户端主表
            "item__etc",                     // 一级子表
            "client_item__misc",             // 客户端一级子表
            "npc__template__data",           // 二级子表
            "client_skill__active__combat"  // 客户端二级子表
        };

        for (String tableName : testTables) {
            TableHierarchy hierarchy = parseTableHierarchy(tableName);
            System.out.printf("表名: %s\n", tableName);
            System.out.printf("  层级: %s\n", hierarchy.getLevelDisplayName());
            System.out.printf("  父表: %s\n", hierarchy.getParentTableName());
            System.out.printf("  子表: %s\n", hierarchy.getChildTableName());
            System.out.printf("  根表: %s\n", hierarchy.getRootTableName());
            System.out.println();
        }

        // 测试匹配规则
        System.out.println("=== 匹配规则测试 ===\n");

        String[][] matchTests = {
            {"client_item", "item"},                        // 主表 vs 主表 - 应该可以
            {"client_item__etc", "item__etc"},              // 一级 vs 一级 - 应该可以
            {"client_item", "item__etc"},                   // 主表 vs 一级 - 不应该
            {"client_item__misc__data", "item__misc__data"} // 二级 vs 二级 - 应该可以
        };

        for (String[] pair : matchTests) {
            boolean canMatch = canMatch(pair[0], pair[1]);
            System.out.printf("%s vs %s: %s\n",
                    pair[0], pair[1],
                    canMatch ? "✅ 可以匹配" : "❌ 不能匹配");
        }
    }
}
