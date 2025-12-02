package red.jiuzhou.ui.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 增强的匹配质量计算器
 *
 * 综合考虑表名相似度和字段匹配度，避免误匹配
 *
 * 算法设计：
 * - 表名相似度权重：30%
 * - 字段匹配度权重：70%
 *
 * 字段匹配度包括：
 * - 共同字段数量占比（40%）
 * - 字段类型匹配度（30%）
 * - 主键匹配（30%）
 *
 * 实例分析：
 * string_monster vs monster
 * - 表名相似度：60%
 * - 字段匹配度：5% (只有id字段)
 * - 综合质量：0.3*60% + 0.7*5% = 21.5% （低质量，不应匹配）
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 2.0
 */
public class EnhancedMatchQualityCalculator {

    private static final Logger log = LoggerFactory.getLogger(EnhancedMatchQualityCalculator.class);

    // 权重配置
    private static final double WEIGHT_TABLE_NAME = 0.30;    // 表名相似度权重30%
    private static final double WEIGHT_FIELD_MATCH = 0.70;   // 字段匹配度权重70%

    // 字段匹配子权重
    private static final double WEIGHT_FIELD_COUNT = 0.40;   // 字段数量匹配40%
    private static final double WEIGHT_FIELD_TYPE = 0.30;    // 字段类型匹配30%
    private static final double WEIGHT_PRIMARY_KEY = 0.30;   // 主键匹配30%

    // 匹配阈值
    private static final double MIN_QUALITY_THRESHOLD = 0.50;  // 最低匹配质量阈值50%
    private static final double MIN_FIELD_MATCH_RATIO = 0.30;  // 最低字段匹配率30%

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

        // 匹配质量评级
        public String qualityLevel;          // 优秀/良好/中等/低/极低

        public MatchQuality() {
        }

        /**
         * 是否达到匹配标准
         */
        public boolean isAcceptable() {
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
         * 计算质量等级
         */
        public void calculateQualityLevel() {
            if (overallQuality >= 0.90) {
                qualityLevel = "优秀";
            } else if (overallQuality >= 0.75) {
                qualityLevel = "良好";
            } else if (overallQuality >= 0.60) {
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
                "共同字段:%d/%d, 类型匹配:%d/%d, 主键:%s, 等级:%s}",
                overallQuality * 100,
                tableNameSimilarity * 100,
                fieldMatchScore * 100,
                commonFields,
                Math.max(totalFieldsClient, totalFieldsServer),
                typeMatchedFields,
                commonFields,
                primaryKeyMatched ? "✓" : "✗",
                qualityLevel
            );
        }
    }

    /**
     * 计算综合匹配质量
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
        quality.tableNameSimilarity = tableNameSimilarity;

        // 获取字段对比结果
        DatabaseTableScanner.FieldCompareResult fieldCompare =
                DatabaseTableScanner.compareFields(clientTable, serverTable);

        // 统计字段信息
        quality.totalFieldsClient = clientTable.getColumns().size();
        quality.totalFieldsServer = serverTable.getColumns().size();
        quality.commonFields = fieldCompare.getCommonCount();

        // 统计类型匹配的字段
        quality.typeMatchedFields = 0;
        for (DatabaseTableScanner.FieldPair pair : fieldCompare.commonFields) {
            if (pair.isTypeMatched()) {
                quality.typeMatchedFields++;
            }
        }

        // 检查主键匹配
        quality.primaryKeyMatched = checkPrimaryKeyMatch(clientTable, serverTable);

        // 计算字段匹配分数
        quality.fieldMatchScore = calculateFieldMatchScore(quality);

        // 计算综合质量
        quality.overallQuality = WEIGHT_TABLE_NAME * quality.tableNameSimilarity +
                                WEIGHT_FIELD_MATCH * quality.fieldMatchScore;

        // 计算质量等级
        quality.calculateQualityLevel();

        log.debug("匹配质量计算: {} → {} | {}",
                clientTable.getTableName(),
                serverTable.getTableName(),
                quality.toString());

        return quality;
    }

    /**
     * 计算字段匹配分数
     */
    private static double calculateFieldMatchScore(MatchQuality quality) {
        // 1. 字段数量匹配分数
        double fieldCountScore = quality.getFieldMatchRatio();

        // 2. 字段类型匹配分数
        double fieldTypeScore = quality.getTypeMatchRatio();

        // 3. 主键匹配分数
        double primaryKeyScore = quality.primaryKeyMatched ? 1.0 : 0.0;

        // 综合计算字段匹配分数
        double fieldMatchScore = WEIGHT_FIELD_COUNT * fieldCountScore +
                                WEIGHT_FIELD_TYPE * fieldTypeScore +
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
