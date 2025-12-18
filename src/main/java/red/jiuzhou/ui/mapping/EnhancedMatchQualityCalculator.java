package red.jiuzhou.ui.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 增强的匹配质量计算器 v3.0
 *
 * 针对游戏数据表的智能匹配算法
 *
 * 算法设计（v3.0 重新调整）：
 * - 表名相似度权重：60%（提高，因为表名是最重要的匹配依据）
 * - 字段匹配度权重：40%（降低，避免误匹配）
 *
 * 匹配优先级：
 * 1. 精确匹配：client_xxx → xxx（100%置信度）
 * 2. 语义匹配：处理单复数、版本号、编号后缀（95%置信度）
 * 3. 模糊匹配：基于Levenshtein距离 + 字段验证
 *
 * 字段匹配度包括：
 * - 核心字段匹配（50%）- id、name等关键字段
 * - 共同字段数量占比（30%）
 * - 主键匹配（20%）
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 3.0
 */
public class EnhancedMatchQualityCalculator {

    private static final Logger log = LoggerFactory.getLogger(EnhancedMatchQualityCalculator.class);

    // 权重配置（v3.0 调整）
    private static final double WEIGHT_TABLE_NAME = 0.60;    // 表名相似度权重60%（提高）
    private static final double WEIGHT_FIELD_MATCH = 0.40;   // 字段匹配度权重40%（降低）

    // 字段匹配子权重（v3.0 调整）
    private static final double WEIGHT_CORE_FIELDS = 0.50;   // 核心字段匹配50%（新增）
    private static final double WEIGHT_FIELD_COUNT = 0.30;   // 字段数量匹配30%（降低）
    private static final double WEIGHT_PRIMARY_KEY = 0.20;   // 主键匹配20%（降低）

    // 匹配阈值（v3.0 调整）
    private static final double MIN_QUALITY_THRESHOLD = 0.45;  // 最低匹配质量阈值45%（降低）
    private static final double MIN_FIELD_MATCH_RATIO = 0.20;  // 最低字段匹配率20%（降低）

    // 核心字段列表（游戏表常见的关键字段）
    private static final Set<String> CORE_FIELDS = new HashSet<>(Arrays.asList(
        "id", "name", "desc", "description", "name_id", "type", "level", "grade"
    ));

    /**
     * 匹配质量结果
     */
    public static class MatchQuality {
        public double overallQuality;        // 综合质量 0-1
        public double tableNameSimilarity;   // 表名相似度 0-1
        public double fieldMatchScore;       // 字段匹配分数 0-1

        // 字段匹配详情
        public int totalFieldsClient;        // 客户端总字段数
        public int totalFieldsServer;        // 服务端总字段数
        public int commonFields;             // 共同字段数
        public int typeMatchedFields;        // 类型匹配的字段数
        public boolean primaryKeyMatched;    // 主键是否匹配

        // 核心字段匹配（v3.0新增）
        public int coreFieldsClient;         // 客户端核心字段数
        public int coreFieldsServer;         // 服务端核心字段数
        public int coreFieldsMatched;        // 匹配的核心字段数
        public double coreFieldScore;        // 核心字段匹配分数

        // 匹配方式（v3.0新增）
        public String matchType;             // 精确/语义/模糊

        // 匹配质量评级
        public String qualityLevel;          // 优秀/良好/中等/低/极低

        public MatchQuality() {
        }

        /**
         * 是否达到匹配标准（v3.0 放宽条件）
         */
        public boolean isAcceptable() {
            // 精确匹配或语义匹配直接通过
            if ("精确".equals(matchType) || "语义".equals(matchType)) {
                return true;
            }
            // 模糊匹配需要满足阈值
            return overallQuality >= MIN_QUALITY_THRESHOLD &&
                   getFieldMatchRatio() >= MIN_FIELD_MATCH_RATIO;
        }

        /**
         * 获取字段匹配率
         */
        public double getFieldMatchRatio() {
            int maxFields = Math.max(totalFieldsClient, totalFieldsServer);
            return maxFields > 0 ? (double) commonFields / maxFields : 0;
        }

        /**
         * 获取类型匹配率
         */
        public double getTypeMatchRatio() {
            return commonFields > 0 ? (double) typeMatchedFields / commonFields : 0;
        }

        /**
         * 获取核心字段匹配率
         */
        public double getCoreFieldMatchRatio() {
            int maxCoreFields = Math.max(coreFieldsClient, coreFieldsServer);
            return maxCoreFields > 0 ? (double) coreFieldsMatched / maxCoreFields : 1.0;
        }

        /**
         * 计算质量等级（v3.0 调整阈值）
         */
        public void calculateQualityLevel() {
            if (overallQuality >= 0.85) {
                qualityLevel = "优秀";
            } else if (overallQuality >= 0.70) {
                qualityLevel = "良好";
            } else if (overallQuality >= 0.55) {
                qualityLevel = "中等";
            } else if (overallQuality >= 0.40) {
                qualityLevel = "低";
            } else {
                qualityLevel = "极低";
            }
        }

        @Override
        public String toString() {
            return String.format(
                "MatchQuality{综合:%.1f%%, 表名:%.1f%%, 字段:%.1f%%, " +
                "共同:%d/%d, 核心:%d/%d, 主键:%s, 类型:%s, 等级:%s}",
                overallQuality * 100,
                tableNameSimilarity * 100,
                fieldMatchScore * 100,
                commonFields,
                Math.max(totalFieldsClient, totalFieldsServer),
                coreFieldsMatched,
                Math.max(coreFieldsClient, coreFieldsServer),
                primaryKeyMatched ? "✓" : "✗",
                matchType != null ? matchType : "模糊",
                qualityLevel
            );
        }
    }

    /**
     * 计算综合匹配质量（v3.0 重新设计）
     *
     * @param clientTable 客户端表信息
     * @param serverTable 服务端表信息
     * @param tableNameSimilarity 表名相似度（来自原算法）
     * @return 匹配质量结果
     */
    public static MatchQuality calculateQuality(
            DatabaseTableScanner.TableInfo clientTable,
            DatabaseTableScanner.TableInfo serverTable,
            double tableNameSimilarity) {

        MatchQuality quality = new MatchQuality();

        // 1. 判断匹配类型（精确/语义/模糊）
        String matchType = determineMatchType(clientTable.getTableName(), serverTable.getTableName());
        quality.matchType = matchType;

        // 2. 根据匹配类型调整表名相似度
        if ("精确".equals(matchType)) {
            tableNameSimilarity = 1.0;
        } else if ("语义".equals(matchType)) {
            tableNameSimilarity = Math.max(tableNameSimilarity, 0.95);
        }

        // 3. 应用表名权重加成（如strings表的80%权重）
        double weightBonus = MappingConfigManager.getTableWeightBonus(clientTable.getTableName());
        if (weightBonus > 0) {
            tableNameSimilarity = Math.min(1.0, tableNameSimilarity + weightBonus * (1.0 - tableNameSimilarity));
            log.debug("表 {} 应用权重加成 {:.0f}%, 调整后相似度: {:.1f}%",
                clientTable.getTableName(), weightBonus * 100, tableNameSimilarity * 100);
        }

        quality.tableNameSimilarity = tableNameSimilarity;

        // 4. 获取字段对比结果
        DatabaseTableScanner.FieldCompareResult fieldCompare =
                DatabaseTableScanner.compareFields(clientTable, serverTable);

        // 5. 统计字段信息
        quality.totalFieldsClient = clientTable.getColumns().size();
        quality.totalFieldsServer = serverTable.getColumns().size();
        quality.commonFields = fieldCompare.getCommonCount();

        // 6. 统计类型匹配的字段
        quality.typeMatchedFields = 0;
        for (DatabaseTableScanner.FieldPair pair : fieldCompare.commonFields) {
            if (pair.isTypeMatched()) {
                quality.typeMatchedFields++;
            }
        }

        // 7. 统计核心字段匹配（v3.0 新增）
        calculateCoreFieldMatch(quality, clientTable, serverTable, fieldCompare);

        // 8. 检查主键匹配
        quality.primaryKeyMatched = checkPrimaryKeyMatch(clientTable, serverTable);

        // 9. 计算字段匹配分数（v3.0 改进）
        quality.fieldMatchScore = calculateFieldMatchScore(quality);

        // 10. 计算综合质量
        quality.overallQuality = WEIGHT_TABLE_NAME * quality.tableNameSimilarity +
                                WEIGHT_FIELD_MATCH * quality.fieldMatchScore;

        // 11. 计算质量等级
        quality.calculateQualityLevel();

        log.debug("匹配质量计算: {} → {} | {}",
                clientTable.getTableName(),
                serverTable.getTableName(),
                quality.toString());

        return quality;
    }

    /**
     * 判断匹配类型（v3.0 新增）
     *
     * @return 精确/语义/模糊
     */
    private static String determineMatchType(String clientTable, String serverTable) {
        // 标准化客户端表名
        String normalizedClient = clientTable.toLowerCase();
        if (normalizedClient.startsWith("client_")) {
            normalizedClient = normalizedClient.substring("client_".length());
        }

        String normalizedServer = serverTable.toLowerCase();

        // 1. 精确匹配：去掉client_前缀后完全相同
        if (normalizedClient.equals(normalizedServer)) {
            return "精确";
        }

        // 2. 语义匹配：处理常见变体
        String semanticClient = normalizeForSemantic(normalizedClient);
        String semanticServer = normalizeForSemantic(normalizedServer);

        if (semanticClient.equals(semanticServer)) {
            return "语义";
        }

        // 3. 模糊匹配
        return "模糊";
    }

    /**
     * 语义标准化（v3.0 新增）
     * 处理单复数、版本号、编号后缀等
     */
    private static String normalizeForSemantic(String tableName) {
        String normalized = tableName;

        // 去掉数字后缀: _1, _2, _v2, _v3
        normalized = normalized.replaceAll("_\\d+$", "");
        normalized = normalized.replaceAll("_v\\d+$", "");

        // 去掉常见后缀
        normalized = normalized.replaceAll("_(data|info|table|tab|list)$", "");

        // 单复数统一
        if (normalized.endsWith("ies") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 3) + "y";
        } else if (normalized.endsWith("ses") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 2);
        } else if (normalized.endsWith("s") && !normalized.endsWith("ss") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    /**
     * 计算核心字段匹配（v3.0 新增）
     */
    private static void calculateCoreFieldMatch(
            MatchQuality quality,
            DatabaseTableScanner.TableInfo clientTable,
            DatabaseTableScanner.TableInfo serverTable,
            DatabaseTableScanner.FieldCompareResult fieldCompare) {

        // 统计客户端核心字段
        Set<String> clientCoreFields = new HashSet<>();
        for (DatabaseTableScanner.ColumnInfo col : clientTable.getColumns()) {
            String colName = col.getColumnName().toLowerCase();
            if (CORE_FIELDS.contains(colName)) {
                clientCoreFields.add(colName);
            }
        }
        quality.coreFieldsClient = clientCoreFields.size();

        // 统计服务端核心字段
        Set<String> serverCoreFields = new HashSet<>();
        for (DatabaseTableScanner.ColumnInfo col : serverTable.getColumns()) {
            String colName = col.getColumnName().toLowerCase();
            if (CORE_FIELDS.contains(colName)) {
                serverCoreFields.add(colName);
            }
        }
        quality.coreFieldsServer = serverCoreFields.size();

        // 统计匹配的核心字段
        int matched = 0;
        for (DatabaseTableScanner.FieldPair pair : fieldCompare.commonFields) {
            String fieldName = pair.clientField.getColumnName().toLowerCase();
            if (CORE_FIELDS.contains(fieldName)) {
                matched++;
            }
        }
        quality.coreFieldsMatched = matched;

        // 计算核心字段分数
        quality.coreFieldScore = quality.getCoreFieldMatchRatio();
    }

    /**
     * 计算字段匹配分数（v3.0 改进）
     */
    private static double calculateFieldMatchScore(MatchQuality quality) {
        // 1. 核心字段匹配分数（最重要）
        double coreFieldScore = quality.coreFieldScore;

        // 2. 字段数量匹配分数
        double fieldCountScore = quality.getFieldMatchRatio();

        // 3. 主键匹配分数
        double primaryKeyScore = quality.primaryKeyMatched ? 1.0 : 0.0;

        // 综合计算字段匹配分数（v3.0 调整权重）
        double fieldMatchScore = WEIGHT_CORE_FIELDS * coreFieldScore +
                                WEIGHT_FIELD_COUNT * fieldCountScore +
                                WEIGHT_PRIMARY_KEY * primaryKeyScore;

        return fieldMatchScore;
    }

    /**
     * 检查主键是否匹配
     */
    private static boolean checkPrimaryKeyMatch(
            DatabaseTableScanner.TableInfo clientTable,
            DatabaseTableScanner.TableInfo serverTable) {

        List<String> clientPK = getPrimaryKeyColumns(clientTable);
        List<String> serverPK = getPrimaryKeyColumns(serverTable);

        if (clientPK.isEmpty() || serverPK.isEmpty()) {
            return false;
        }

        return clientPK.equals(serverPK);
    }

    /**
     * 获取主键列
     */
    private static List<String> getPrimaryKeyColumns(DatabaseTableScanner.TableInfo table) {
        List<String> pkColumns = new ArrayList<>();
        for (DatabaseTableScanner.ColumnInfo col : table.getColumns()) {
            if (col.isPrimaryKey()) {
                pkColumns.add(col.getColumnName());
            }
        }
        return pkColumns;
    }

    /**
     * 快速计算匹配质量（只基于字段信息，不需要完整的TableInfo）
     */
    public static MatchQuality calculateQuickQuality(
            String clientTableName,
            String serverTableName,
            int clientFieldCount,
            int serverFieldCount,
            int commonFieldCount,
            double tableNameSimilarity) {

        MatchQuality quality = new MatchQuality();
        quality.tableNameSimilarity = tableNameSimilarity;
        quality.totalFieldsClient = clientFieldCount;
        quality.totalFieldsServer = serverFieldCount;
        quality.commonFields = commonFieldCount;
        quality.typeMatchedFields = commonFieldCount;  // 简化假设：类型都匹配
        quality.primaryKeyMatched = true;  // 简化假设：主键匹配

        quality.fieldMatchScore = calculateFieldMatchScore(quality);
        quality.overallQuality = WEIGHT_TABLE_NAME * quality.tableNameSimilarity +
                                WEIGHT_FIELD_MATCH * quality.fieldMatchScore;

        quality.calculateQualityLevel();

        return quality;
    }

    /**
     * 匹配质量分析报告
     */
    public static String generateQualityReport(MatchQuality quality,
                                               String clientTableName,
                                               String serverTableName) {
        StringBuilder report = new StringBuilder();

        report.append("=== 匹配质量分析 ===\n\n");
        report.append(String.format("客户端表: %s\n", clientTableName));
        report.append(String.format("服务端表: %s\n\n", serverTableName));

        report.append("综合匹配质量:\n");
        report.append(String.format("  总分: %.1f%% (%s)\n",
                quality.overallQuality * 100, quality.qualityLevel));
        report.append(String.format("  是否可接受: %s\n\n",
                quality.isAcceptable() ? "✅ 是" : "❌ 否"));

        report.append("详细评分:\n");
        report.append(String.format("  表名相似度: %.1f%% (权重30%%)\n",
                quality.tableNameSimilarity * 100));
        report.append(String.format("  字段匹配度: %.1f%% (权重70%%)\n\n",
                quality.fieldMatchScore * 100));

        report.append("字段匹配详情:\n");
        report.append(String.format("  客户端字段数: %d\n", quality.totalFieldsClient));
        report.append(String.format("  服务端字段数: %d\n", quality.totalFieldsServer));
        report.append(String.format("  共同字段数: %d (占比%.1f%%)\n",
                quality.commonFields, quality.getFieldMatchRatio() * 100));
        report.append(String.format("  类型匹配字段: %d/%d (%.1f%%)\n",
                quality.typeMatchedFields, quality.commonFields,
                quality.getTypeMatchRatio() * 100));
        report.append(String.format("  主键匹配: %s\n\n",
                quality.primaryKeyMatched ? "✅ 是" : "❌ 否"));

        if (!quality.isAcceptable()) {
            report.append("⚠️ 警告: 匹配质量不足\n");
            if (quality.overallQuality < MIN_QUALITY_THRESHOLD) {
                report.append(String.format("  - 综合质量(%.1f%%)低于阈值(%.0f%%)\n",
                        quality.overallQuality * 100, MIN_QUALITY_THRESHOLD * 100));
            }
            if (quality.getFieldMatchRatio() < MIN_FIELD_MATCH_RATIO) {
                report.append(String.format("  - 字段匹配率(%.1f%%)低于阈值(%.0f%%)\n",
                        quality.getFieldMatchRatio() * 100, MIN_FIELD_MATCH_RATIO * 100));
            }
            report.append("  建议: 人工验证或手动配置映射\n");
        }

        return report.toString();
    }

    /**
     * 测试用例
     */
    public static void main(String[] args) {
        System.out.println("=== 匹配质量计算器测试 ===\n");

        // 测试案例1: string_monster vs monster（应该低质量）
        System.out.println("案例1: string_monster vs monster");
        System.out.println("  - 表名相似度: 60%");
        System.out.println("  - 共同字段: 1/20 (只有id)");

        MatchQuality q1 = calculateQuickQuality(
                "string_monster", "monster",
                20,  // 客户端20个字段
                15,  // 服务端15个字段
                1,   // 共同字段1个（id）
                0.60 // 表名相似度60%
        );

        System.out.println("  结果: " + q1.toString());
        System.out.println("  是否可接受: " + (q1.isAcceptable() ? "✅" : "❌"));
        System.out.println();

        // 测试案例2: client_item vs item（应该高质量）
        System.out.println("案例2: client_item vs item");
        System.out.println("  - 表名相似度: 100%");
        System.out.println("  - 共同字段: 15/15 (完全匹配)");

        MatchQuality q2 = calculateQuickQuality(
                "client_item", "item",
                15,  // 客户端15个字段
                15,  // 服务端15个字段
                15,  // 共同字段15个
                1.00 // 表名相似度100%
        );

        System.out.println("  结果: " + q2.toString());
        System.out.println("  是否可接受: " + (q2.isAcceptable() ? "✅" : "❌"));
        System.out.println();

        // 测试案例3: client_items_etc_1 vs item_etc（应该中等质量）
        System.out.println("案例3: client_items_etc_1 vs item_etc");
        System.out.println("  - 表名相似度: 85%");
        System.out.println("  - 共同字段: 10/12");

        MatchQuality q3 = calculateQuickQuality(
                "client_items_etc_1", "item_etc",
                12,  // 客户端12个字段
                12,  // 服务端12个字段
                10,  // 共同字段10个
                0.85 // 表名相似度85%
        );

        System.out.println("  结果: " + q3.toString());
        System.out.println("  是否可接受: " + (q3.isAcceptable() ? "✅" : "❌"));
        System.out.println();

        // 生成详细报告
        System.out.println(generateQualityReport(q1, "string_monster", "monster"));
    }
}
