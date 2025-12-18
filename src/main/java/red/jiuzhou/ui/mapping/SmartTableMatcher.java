package red.jiuzhou.ui.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能表匹配器
 *
 * 解决复杂的表映射场景，例如：
 * - client_items_etc_1, client_item_etc_2 → item_etc
 * - client_npc_templates_v2 → npc_template
 * - client_skill_data → skill
 *
 * 匹配策略：
 * 1. 精确匹配 (client_item → item)
 * 2. 智能模糊匹配 (相似度算法)
 * 3. 手动配置映射
 *
 * @author yanxq
 * @date 2025-01-13
 * @version 1.0
 */
public class SmartTableMatcher {

    private static final Logger log = LoggerFactory.getLogger(SmartTableMatcher.class);

    // 手动配置的映射关系
    private static Map<String, String> manualMappings = new HashMap<>();

    /**
     * 匹配结果
     */
    public static class MatchResult {
        public String clientTable;
        public String serverTable;
        public double similarity;      // 综合质量 0-1
        public String matchMethod;     // 匹配方法

        // 增强质量详情
        public EnhancedMatchQualityCalculator.MatchQuality qualityDetail;

        public MatchResult(String clientTable, String serverTable,
                          double similarity, String matchMethod) {
            this.clientTable = clientTable;
            this.serverTable = serverTable;
            this.similarity = similarity;
            this.matchMethod = matchMethod;
        }

        public MatchResult(String clientTable, String serverTable,
                          double similarity, String matchMethod,
                          EnhancedMatchQualityCalculator.MatchQuality qualityDetail) {
            this.clientTable = clientTable;
            this.serverTable = serverTable;
            this.similarity = similarity;
            this.matchMethod = matchMethod;
            this.qualityDetail = qualityDetail;
        }
    }

    /**
     * 智能匹配客户端表到服务端表（旧版本，向后兼容）
     *
     * @param clientTable 客户端表名
     * @param allServerTables 所有服务端表名列表
     * @return 最佳匹配结果，如果没有找到返回null
     * @deprecated 使用 {@link #smartMatch(DatabaseTableScanner.TableInfo, List)} 代替
     */
    @Deprecated
    public static MatchResult smartMatch(String clientTable, List<String> allServerTables) {
        log.warn("使用了过时的 smartMatch(String, List<String>) 方法，建议升级到新版本");

        // 简化版本：只使用表名相似度，不计算字段匹配
        TableHierarchyHelper.TableHierarchy clientHierarchy =
            TableHierarchyHelper.parseTableHierarchy(clientTable);

        // 手动配置检查
        if (manualMappings.containsKey(clientTable)) {
            String serverTable = manualMappings.get(clientTable);
            if (TableHierarchyHelper.canMatch(clientTable, serverTable)) {
                return new MatchResult(clientTable, serverTable, 1.0, "手动配置");
            }
        }

        // 过滤同层级表
        List<String> sameLevelServerTables = new ArrayList<>();
        for (String serverTable : allServerTables) {
            if (TableHierarchyHelper.canMatch(clientTable, serverTable)) {
                sameLevelServerTables.add(serverTable);
            }
        }

        if (sameLevelServerTables.isEmpty()) {
            return null;
        }

        // 精确匹配
        if (clientTable.startsWith("client_")) {
            String expectedServer = clientTable.substring("client_".length());
            if (sameLevelServerTables.contains(expectedServer)) {
                return new MatchResult(clientTable, expectedServer, 1.0, "精确匹配");
            }
        }

        // 模糊匹配（只用表名）
        String normalizedClient = normalizeTableName(clientTable);
        double bestSimilarity = 0;
        String bestMatch = null;

        for (String serverTable : sameLevelServerTables) {
            String normalizedServer = normalizeTableName(serverTable);
            double similarity = calculateSimilarity(normalizedClient, normalizedServer);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = serverTable;
            }
        }

        if (bestSimilarity >= 0.6) {
            return new MatchResult(clientTable, bestMatch, bestSimilarity,
                String.format("模糊匹配 (%.0f%%)", bestSimilarity * 100));
        }

        return null;
    }

    /**
     * 智能匹配客户端表到服务端表（支持层级结构和增强质量计算）
     *
     * @param clientTableInfo 客户端表信息
     * @param allServerTables 所有服务端表信息列表
     * @return 最佳匹配结果，如果没有找到返回null
     */
    public static MatchResult smartMatch(DatabaseTableScanner.TableInfo clientTableInfo,
                                        List<DatabaseTableScanner.TableInfo> allServerTables) {

        String clientTable = clientTableInfo.getTableName();

        // 获取客户端表的层级信息
        TableHierarchyHelper.TableHierarchy clientHierarchy =
            TableHierarchyHelper.parseTableHierarchy(clientTable);

        // 1. 检查手动配置
        if (manualMappings.containsKey(clientTable)) {
            String serverTable = manualMappings.get(clientTable);

            // 验证层级匹配
            if (!TableHierarchyHelper.canMatch(clientTable, serverTable)) {
                log.warn("手动配置的映射 {} → {} 层级不匹配，跳过", clientTable, serverTable);
            } else {
                log.info("使用手动配置: {} → {}", clientTable, serverTable);
                return new MatchResult(clientTable, serverTable, 1.0, "手动配置");
            }
        }

        // 2. 过滤出同层级的服务端表
        List<DatabaseTableScanner.TableInfo> sameLevelServerTables = new ArrayList<>();
        for (DatabaseTableScanner.TableInfo serverTable : allServerTables) {
            if (TableHierarchyHelper.canMatch(clientTable, serverTable.getTableName())) {
                sameLevelServerTables.add(serverTable);
            }
        }

        if (sameLevelServerTables.isEmpty()) {
            log.warn("客户端表 {} ({}) 没有找到同层级的服务端表",
                    clientTable, clientHierarchy.getLevelDisplayName());
            return null;
        }

        log.debug("客户端表 {} ({}) 找到 {} 个同层级服务端表",
                clientTable, clientHierarchy.getLevelDisplayName(), sameLevelServerTables.size());

        // 3. 精确匹配 (去掉 client_ 前缀)
        if (clientTable.startsWith("client_")) {
            String expectedServer = clientTable.substring("client_".length());
            for (DatabaseTableScanner.TableInfo serverTable : sameLevelServerTables) {
                if (serverTable.getTableName().equals(expectedServer)) {
                    log.debug("精确匹配: {} → {}", clientTable, expectedServer);

                    // 即使精确匹配，也计算质量详情
                    EnhancedMatchQualityCalculator.MatchQuality quality =
                        EnhancedMatchQualityCalculator.calculateQuality(
                            clientTableInfo, serverTable, 1.0);

                    return new MatchResult(clientTable, expectedServer, 1.0, "精确匹配", quality);
                }
            }
        }

        // 4. 智能模糊匹配（只在同层级表中匹配，使用增强质量计算）
        return fuzzyMatch(clientTableInfo, sameLevelServerTables);
    }

    /**
     * 模糊匹配（使用增强的质量计算器 v3.0）
     *
     * 综合考虑：
     * - 表名相似度 (60%)
     * - 字段匹配度 (40%)
     *   - 核心字段匹配 (50%)
     *   - 字段数量匹配 (30%)
     *   - 主键匹配 (20%)
     */
    private static MatchResult fuzzyMatch(DatabaseTableScanner.TableInfo clientTable,
                                         List<DatabaseTableScanner.TableInfo> allServerTables) {

        String clientTableName = clientTable.getTableName();
        String normalizedClient = normalizeTableName(clientTableName);

        double bestOverallQuality = 0;
        DatabaseTableScanner.TableInfo bestMatch = null;
        EnhancedMatchQualityCalculator.MatchQuality bestQuality = null;

        for (DatabaseTableScanner.TableInfo serverTable : allServerTables) {
            String normalizedServer = normalizeTableName(serverTable.getTableName());

            // 1. 计算表名相似度（基于标准化的表名）
            double tableNameSimilarity = calculateSimilarity(normalizedClient, normalizedServer);

            // 2. 计算综合质量（表名 + 字段，v3.0使用增强算法）
            EnhancedMatchQualityCalculator.MatchQuality quality =
                EnhancedMatchQualityCalculator.calculateQuality(
                    clientTable, serverTable, tableNameSimilarity);

            log.debug("质量评估: {} → {} | {}",
                    clientTableName, serverTable.getTableName(), quality.toString());

            // 3. 选择最佳匹配
            if (quality.overallQuality > bestOverallQuality) {
                bestOverallQuality = quality.overallQuality;
                bestMatch = serverTable;
                bestQuality = quality;
            }
        }

        // 使用增强的阈值判断（v3.0 更宽松的条件）
        if (bestMatch != null && bestQuality != null && bestQuality.isAcceptable()) {
            // 根据匹配类型生成描述
            String matchMethod;
            if ("精确".equals(bestQuality.matchType)) {
                matchMethod = "精确匹配";
            } else if ("语义".equals(bestQuality.matchType)) {
                matchMethod = String.format("语义匹配 (%.0f%%)", bestOverallQuality * 100);
            } else {
                matchMethod = String.format("模糊匹配 (%.0f%%)", bestOverallQuality * 100);
            }

            log.info("匹配成功: {} → {} | {} | {}",
                    clientTableName, bestMatch.getTableName(), matchMethod, bestQuality.toString());

            return new MatchResult(
                clientTableName,
                bestMatch.getTableName(),
                bestOverallQuality,
                matchMethod,
                bestQuality
            );
        }

        // 没有找到合适的匹配
        if (bestMatch != null) {
            log.warn("未找到满足质量要求的匹配: {} (最佳: {} 质量:%.1f%%)",
                    clientTableName, bestMatch.getTableName(), bestOverallQuality * 100);
        } else {
            log.warn("未找到匹配: {}", clientTableName);
        }
        return null;
    }

    /**
     * 标准化表名
     *
     * 规则：
     * - 去掉 client_ 前缀
     * - 去掉数字后缀 (_1, _2, _v2等)
     * - 单复数统一 (items → item)
     * - 全部转小写
     */
    private static String normalizeTableName(String tableName) {
        String normalized = tableName.toLowerCase();

        // 去掉 client_ 前缀
        if (normalized.startsWith("client_")) {
            normalized = normalized.substring("client_".length());
        }

        // 去掉数字后缀: _1, _2, _v2, _v3 等
        normalized = normalized.replaceAll("_\\d+$", "");
        normalized = normalized.replaceAll("_v\\d+$", "");

        // 去掉常见后缀
        normalized = normalized.replaceAll("_(data|info|table|tab)$", "");

        // 单复数统一 (简单处理，去掉末尾的s)
        if (normalized.endsWith("ies")) {
            // items → item, entries → entry
            normalized = normalized.substring(0, normalized.length() - 3) + "y";
        } else if (normalized.endsWith("ses")) {
            // classes → class
            normalized = normalized.substring(0, normalized.length() - 2);
        } else if (normalized.endsWith("s") && !normalized.endsWith("ss")) {
            // items → item, but not class → clas
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    /**
     * 计算两个字符串的相似度
     *
     * 使用 Levenshtein 距离算法
     *
     * @return 相似度 0-1，1表示完全相同
     */
    private static double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());

        if (maxLen == 0) {
            return 1.0;
        }

        return 1.0 - (double) distance / maxLen;
    }

    /**
     * Levenshtein 距离算法
     * 计算两个字符串的编辑距离
     */
    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * 批量匹配
     *
     * @param clientTables 客户端表列表
     * @param serverTables 服务端表列表
     * @return 匹配结果列表
     */
    public static List<MatchResult> batchMatch(List<String> clientTables,
                                               List<String> serverTables) {
        List<MatchResult> results = new ArrayList<>();

        for (String clientTable : clientTables) {
            MatchResult result = smartMatch(clientTable, serverTables);
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    /**
     * 查找多对一映射
     * 多个客户端表映射到同一个服务端表
     *
     * @param allMatches 所有匹配结果
     * @return Map<服务端表, List<客户端表>>
     */
    public static Map<String, List<String>> findManyToOne(List<MatchResult> allMatches) {
        Map<String, List<String>> manyToOne = new HashMap<>();

        for (MatchResult match : allMatches) {
            manyToOne.computeIfAbsent(match.serverTable, k -> new ArrayList<>())
                     .add(match.clientTable);
        }

        // 只保留一对多的情况
        return manyToOne.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 添加手动映射配置
     *
     * @param clientTable 客户端表
     * @param serverTable 服务端表
     */
    public static void addManualMapping(String clientTable, String serverTable) {
        manualMappings.put(clientTable, serverTable);
        log.info("添加手动映射: {} → {}", clientTable, serverTable);
    }

    /**
     * 删除手动映射
     */
    public static void removeManualMapping(String clientTable) {
        String removed = manualMappings.remove(clientTable);
        if (removed != null) {
            log.info("删除手动映射: {} → {}", clientTable, removed);
        }
    }

    /**
     * 获取所有手动映射
     */
    public static Map<String, String> getManualMappings() {
        return new HashMap<>(manualMappings);
    }

    /**
     * 加载手动映射配置（从文件）
     */
    public static void loadManualMappings(Map<String, String> mappings) {
        if (mappings != null) {
            manualMappings.clear();
            manualMappings.putAll(mappings);
            log.info("加载 {} 条手动映射配置", mappings.size());
        }
    }

    /**
     * 清除所有手动映射
     */
    public static void clearManualMappings() {
        manualMappings.clear();
        log.info("清除所有手动映射");
    }

    /**
     * 生成映射建议
     *
     * 分析未匹配的表，给出可能的映射建议
     */
    public static List<String> generateSuggestions(String clientTable,
                                                   List<String> serverTables) {
        List<String> suggestions = new ArrayList<>();

        String normalized = normalizeTableName(clientTable);

        // 查找包含关键词的表
        for (String serverTable : serverTables) {
            String normalizedServer = normalizeTableName(serverTable);

            // 如果标准化后的客户端表名包含在服务端表名中，或反之
            if (normalizedServer.contains(normalized) ||
                normalized.contains(normalizedServer)) {
                suggestions.add(serverTable);
            }
        }

        return suggestions;
    }

    /**
     * 测试匹配算法
     */
    public static void main(String[] args) {
        // 测试案例
        List<String> serverTables = Arrays.asList(
            "item_etc",
            "npc_template",
            "skill",
            "world_npc"
        );

        // 测试各种客户端表名
        String[] clientTables = {
            "client_items_etc_1",
            "client_item_etc_2",
            "client_npc_templates_v2",
            "client_skill_data",
            "client_world__npc_spawn"
        };

        System.out.println("=== 智能表匹配测试 ===\n");

        for (String clientTable : clientTables) {
            MatchResult result = smartMatch(clientTable, serverTables);
            if (result != null) {
                System.out.printf("✅ %s → %s (%.0f%%, %s)\n",
                    clientTable, result.serverTable,
                    result.similarity * 100, result.matchMethod);
            } else {
                System.out.printf("❌ %s → 未找到匹配\n", clientTable);
            }
        }

        // 测试多对一检测
        System.out.println("\n=== 多对一映射检测 ===\n");
        List<MatchResult> allMatches = batchMatch(Arrays.asList(clientTables), serverTables);
        Map<String, List<String>> manyToOne = findManyToOne(allMatches);

        for (Map.Entry<String, List<String>> entry : manyToOne.entrySet()) {
            System.out.printf("服务端表: %s\n", entry.getKey());
            System.out.println("  对应客户端表:");
            for (String ct : entry.getValue()) {
                System.out.printf("    - %s\n", ct);
            }
        }
    }
}
