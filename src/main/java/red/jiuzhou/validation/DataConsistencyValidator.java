package red.jiuzhou.validation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 数据一致性验证器
 * 用于检查游戏配置文件之间的关联关系和数据一致性
 *
 * 功能：
 * - 检查装备与掉落表的一致性
 * - 检查NPC等级与经验表一致性
 * - 检查技能与学习配置一致性
 * - 检测孤立配置（未被引用的数据）
 * - 检测数值平衡性问题
 * - 生成详细的验证报告
 */
@Slf4j
public class DataConsistencyValidator {

    private final ExecutorService executorService = Executors.newFixedThreadPool(8);
    private final Map<String, Document> documentCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> referenceMap = new ConcurrentHashMap<>();

    // 验证规则定义
    private final List<ValidationRule> validationRules = new ArrayList<>();

    // 验证结果
    @Data
    public static class ValidationResult {
        private String category;           // 类别（错误、警告、信息）
        private String type;               // 类型（一致性、平衡性、孤立数据等）
        private String message;            // 消息
        private String file;              // 相关文件
        private String elementPath;        // 元素路径
        private Map<String, String> details; // 详细信息
        private List<String> suggestions; // 修复建议
        private ValidationSeverity severity; // 严重程度

        public enum ValidationSeverity {
            ERROR,      // 错误 - 必须修复
            WARNING,    // 警告 - 建议修复
            INFO        // 信息 - 可选优化
        }
    }

    // 验证报告
    @Data
    public static class ValidationReport {
        private int totalErrors = 0;
        private int totalWarnings = 0;
        private int totalInfo = 0;
        private List<ValidationResult> results = new ArrayList<>();
        private Map<String, List<ValidationResult>> resultsByType = new HashMap<>();
        private Map<String, List<ValidationResult>> resultsByFile = new HashMap<>();
        private long validationTime;
        private Date validationDate = new Date();

        public void addResult(ValidationResult result) {
            results.add(result);

            // 统计
            switch (result.severity) {
                case ERROR:
                    totalErrors++;
                    break;
                case WARNING:
                    totalWarnings++;
                    break;
                case INFO:
                    totalInfo++;
                    break;
            }

            // 按类型分组
            resultsByType.computeIfAbsent(result.type, k -> new ArrayList<>()).add(result);

            // 按文件分组
            if (result.file != null) {
                resultsByFile.computeIfAbsent(result.file, k -> new ArrayList<>()).add(result);
            }
        }

        public String getSummary() {
            return String.format("验证完成: %d 个错误, %d 个警告, %d 个信息",
                               totalErrors, totalWarnings, totalInfo);
        }
    }

    // 验证规则接口
    public interface ValidationRule {
        String getName();
        String getDescription();
        List<ValidationResult> validate(Map<String, Document> documents);
    }

    /**
     * 初始化验证规则
     */
    public void initializeRules() {
        // 装备与掉落表一致性
        validationRules.add(new ItemDropConsistencyRule());

        // NPC等级与经验表一致性
        validationRules.add(new NPCLevelConsistencyRule());

        // 技能与学习配置一致性
        validationRules.add(new SkillLearnConsistencyRule());

        // 任务与奖励一致性
        validationRules.add(new QuestRewardConsistencyRule());

        // 孤立数据检测
        validationRules.add(new OrphanedDataRule());

        // 数值平衡性检查
        validationRules.add(new BalanceCheckRule());

        // 引用完整性检查
        validationRules.add(new ReferenceIntegrityRule());
    }

    /**
     * 执行完整验证
     */
    public ValidationReport validateAll(String dataDirectory) throws Exception {
        long startTime = System.currentTimeMillis();
        ValidationReport report = new ValidationReport();

        log.info("Starting data consistency validation for: " + dataDirectory);

        // 加载所有XML文档
        loadAllDocuments(dataDirectory);

        // 构建引用关系图
        buildReferenceMap();

        // 执行所有验证规则
        List<Future<List<ValidationResult>>> futures = new ArrayList<>();
        for (ValidationRule rule : validationRules) {
            Future<List<ValidationResult>> future = executorService.submit(() -> {
                log.info("Executing rule: " + rule.getName());
                try {
                    return rule.validate(documentCache);
                } catch (Exception e) {
                    log.error("Rule execution failed: " + rule.getName(), e);
                    return Collections.emptyList();
                }
            });
            futures.add(future);
        }

        // 收集结果
        for (Future<List<ValidationResult>> future : futures) {
            try {
                List<ValidationResult> results = future.get(60, TimeUnit.SECONDS);
                results.forEach(report::addResult);
            } catch (TimeoutException e) {
                log.warn("Validation rule timeout");
            }
        }

        report.setValidationTime(System.currentTimeMillis() - startTime);
        log.info("Validation completed: " + report.getSummary());

        return report;
    }

    /**
     * 验证单个文件修改的影响
     */
    public ValidationReport validateFileChange(String filePath, String newContent) throws Exception {
        ValidationReport report = new ValidationReport();

        // 解析新内容
        Document newDoc = parseXmlContent(newContent);

        // 获取文件名
        String fileName = new File(filePath).getName();

        // 检查哪些文件引用了这个文件
        Set<String> referencingFiles = findReferencingFiles(fileName);

        // 验证引用一致性
        for (String refFile : referencingFiles) {
            Document refDoc = documentCache.get(refFile);
            if (refDoc != null) {
                List<ValidationResult> results = validateReferences(newDoc, refDoc, fileName, refFile);
                results.forEach(report::addResult);
            }
        }

        // 检查平衡性影响
        List<ValidationResult> balanceResults = checkBalanceImpact(fileName, newDoc);
        balanceResults.forEach(report::addResult);

        return report;
    }

    /**
     * 装备与掉落表一致性规则
     */
    private class ItemDropConsistencyRule implements ValidationRule {
        @Override
        public String getName() {
            return "装备掉落一致性检查";
        }

        @Override
        public String getDescription() {
            return "检查装备配置与NPC掉落表的一致性";
        }

        @Override
        public List<ValidationResult> validate(Map<String, Document> documents) {
            List<ValidationResult> results = new ArrayList<>();

            // 获取所有装备ID
            Set<String> itemIds = new HashSet<>();
            documents.entrySet().stream()
                .filter(e -> e.getKey().contains("items"))
                .forEach(e -> {
                    NodeList items = e.getValue().getElementsByTagName("item");
                    for (int i = 0; i < items.getLength(); i++) {
                        Element item = (Element) items.item(i);
                        String id = item.getAttribute("id");
                        if (!id.isEmpty()) {
                            itemIds.add(id);
                        }
                    }
                });

            // 检查掉落表中的装备引用
            documents.entrySet().stream()
                .filter(e -> e.getKey().contains("drop"))
                .forEach(e -> {
                    NodeList drops = e.getValue().getElementsByTagName("drop");
                    for (int i = 0; i < drops.getLength(); i++) {
                        Element drop = (Element) drops.item(i);
                        String itemId = drop.getAttribute("item_id");
                        if (!itemId.isEmpty() && !itemIds.contains(itemId)) {
                            ValidationResult result = new ValidationResult();
                            result.setSeverity(ValidationResult.ValidationSeverity.ERROR);
                            result.setType("装备引用错误");
                            result.setMessage("掉落表引用了不存在的装备ID: " + itemId);
                            result.setFile(e.getKey());
                            result.setElementPath(getElementPath(drop));

                            Map<String, String> details = new HashMap<>();
                            details.put("item_id", itemId);
                            details.put("drop_table", e.getKey());
                            result.setDetails(details);

                            result.setSuggestions(Arrays.asList(
                                "检查装备ID是否正确",
                                "确认装备配置文件是否已加载",
                                "考虑移除无效的掉落配置"
                            ));

                            results.add(result);
                        }
                    }
                });

            return results;
        }
    }

    /**
     * NPC等级与经验表一致性规则
     */
    private class NPCLevelConsistencyRule implements ValidationRule {
        @Override
        public String getName() {
            return "NPC等级一致性检查";
        }

        @Override
        public String getDescription() {
            return "检查NPC等级与经验表的一致性";
        }

        @Override
        public List<ValidationResult> validate(Map<String, Document> documents) {
            List<ValidationResult> results = new ArrayList<>();

            // 加载经验表
            Map<Integer, Long> expTable = loadExpTable(documents);

            // 检查NPC等级
            documents.entrySet().stream()
                .filter(e -> e.getKey().contains("npc"))
                .forEach(e -> {
                    NodeList npcs = e.getValue().getElementsByTagName("npc");
                    for (int i = 0; i < npcs.getLength(); i++) {
                        Element npc = (Element) npcs.item(i);
                        String levelStr = npc.getAttribute("level");
                        String expStr = npc.getAttribute("exp");

                        if (!levelStr.isEmpty() && !expStr.isEmpty()) {
                            try {
                                int level = Integer.parseInt(levelStr);
                                long exp = Long.parseLong(expStr);
                                Long expectedExp = expTable.get(level);

                                if (expectedExp != null && Math.abs(exp - expectedExp) > expectedExp * 0.1) {
                                    ValidationResult result = new ValidationResult();
                                    result.setSeverity(ValidationResult.ValidationSeverity.WARNING);
                                    result.setType("经验值不匹配");
                                    result.setMessage(String.format("NPC等级%d的经验值(%d)与经验表(%d)不匹配",
                                                                   level, exp, expectedExp));
                                    result.setFile(e.getKey());
                                    result.setElementPath(getElementPath(npc));

                                    Map<String, String> details = new HashMap<>();
                                    details.put("npc_id", npc.getAttribute("id"));
                                    details.put("level", levelStr);
                                    details.put("actual_exp", expStr);
                                    details.put("expected_exp", String.valueOf(expectedExp));
                                    result.setDetails(details);

                                    result.setSuggestions(Arrays.asList(
                                        "更新NPC经验值为: " + expectedExp,
                                        "或检查经验表配置是否正确"
                                    ));

                                    results.add(result);
                                }
                            } catch (NumberFormatException ex) {
                                // 忽略解析错误
                            }
                        }
                    }
                });

            return results;
        }

        private Map<Integer, Long> loadExpTable(Map<String, Document> documents) {
            Map<Integer, Long> expTable = new HashMap<>();

            documents.entrySet().stream()
                .filter(e -> e.getKey().contains("exp") || e.getKey().contains("level"))
                .forEach(e -> {
                    NodeList levels = e.getValue().getElementsByTagName("level");
                    for (int i = 0; i < levels.getLength(); i++) {
                        Element level = (Element) levels.item(i);
                        String numStr = level.getAttribute("num");
                        String expStr = level.getAttribute("exp");
                        if (!numStr.isEmpty() && !expStr.isEmpty()) {
                            try {
                                expTable.put(Integer.parseInt(numStr), Long.parseLong(expStr));
                            } catch (NumberFormatException ex) {
                                // 忽略
                            }
                        }
                    }
                });

            return expTable;
        }
    }

    /**
     * 技能与学习配置一致性规则
     */
    private class SkillLearnConsistencyRule implements ValidationRule {
        @Override
        public String getName() {
            return "技能学习一致性检查";
        }

        @Override
        public String getDescription() {
            return "检查技能配置与学习配置的一致性";
        }

        @Override
        public List<ValidationResult> validate(Map<String, Document> documents) {
            List<ValidationResult> results = new ArrayList<>();

            // 获取所有技能ID
            Set<String> skillIds = new HashSet<>();
            documents.entrySet().stream()
                .filter(e -> e.getKey().contains("skill"))
                .forEach(e -> {
                    NodeList skills = e.getValue().getElementsByTagName("skill");
                    for (int i = 0; i < skills.getLength(); i++) {
                        Element skill = (Element) skills.item(i);
                        String id = skill.getAttribute("id");
                        if (!id.isEmpty()) {
                            skillIds.add(id);
                        }
                    }
                });

            // 检查学习配置中的技能引用
            documents.entrySet().stream()
                .filter(e -> e.getKey().contains("learn") || e.getKey().contains("class"))
                .forEach(e -> {
                    NodeList learns = e.getValue().getElementsByTagName("learn");
                    for (int i = 0; i < learns.getLength(); i++) {
                        Element learn = (Element) learns.item(i);
                        String skillId = learn.getAttribute("skill_id");
                        if (!skillId.isEmpty() && !skillIds.contains(skillId)) {
                            ValidationResult result = new ValidationResult();
                            result.setSeverity(ValidationResult.ValidationSeverity.ERROR);
                            result.setType("技能引用错误");
                            result.setMessage("学习配置引用了不存在的技能ID: " + skillId);
                            result.setFile(e.getKey());
                            result.setElementPath(getElementPath(learn));

                            Map<String, String> details = new HashMap<>();
                            details.put("skill_id", skillId);
                            details.put("class", learn.getAttribute("class"));
                            details.put("level", learn.getAttribute("level"));
                            result.setDetails(details);

                            result.setSuggestions(Arrays.asList(
                                "检查技能ID是否正确",
                                "确认技能配置文件是否已加载",
                                "考虑移除无效的学习配置"
                            ));

                            results.add(result);
                        }
                    }
                });

            return results;
        }
    }

    /**
     * 任务与奖励一致性规则
     */
    private class QuestRewardConsistencyRule implements ValidationRule {
        @Override
        public String getName() {
            return "任务奖励一致性检查";
        }

        @Override
        public String getDescription() {
            return "检查任务配置与奖励配置的一致性";
        }

        @Override
        public List<ValidationResult> validate(Map<String, Document> documents) {
            List<ValidationResult> results = new ArrayList<>();

            // 实现任务奖励验证逻辑
            // ...

            return results;
        }
    }

    /**
     * 孤立数据检测规则
     */
    private class OrphanedDataRule implements ValidationRule {
        @Override
        public String getName() {
            return "孤立数据检测";
        }

        @Override
        public String getDescription() {
            return "检测未被任何其他配置引用的孤立数据";
        }

        @Override
        public List<ValidationResult> validate(Map<String, Document> documents) {
            List<ValidationResult> results = new ArrayList<>();

            // 构建反向引用图
            Map<String, Set<String>> reverseReferences = buildReverseReferenceMap(documents);

            // 检测孤立的装备
            documents.entrySet().stream()
                .filter(e -> e.getKey().contains("items"))
                .forEach(e -> {
                    NodeList items = e.getValue().getElementsByTagName("item");
                    for (int i = 0; i < items.getLength(); i++) {
                        Element item = (Element) items.item(i);
                        String id = item.getAttribute("id");
                        if (!id.isEmpty() && !reverseReferences.containsKey("item:" + id)) {
                            ValidationResult result = new ValidationResult();
                            result.setSeverity(ValidationResult.ValidationSeverity.INFO);
                            result.setType("孤立数据");
                            result.setMessage("装备 " + id + " 未被任何配置引用");
                            result.setFile(e.getKey());
                            result.setElementPath(getElementPath(item));

                            Map<String, String> details = new HashMap<>();
                            details.put("item_id", id);
                            details.put("item_name", item.getAttribute("name"));
                            result.setDetails(details);

                            result.setSuggestions(Arrays.asList(
                                "考虑将此装备添加到掉落表",
                                "或将其添加到商店配置",
                                "如果确实不需要，可以删除此配置"
                            ));

                            results.add(result);
                        }
                    }
                });

            return results;
        }

        private Map<String, Set<String>> buildReverseReferenceMap(Map<String, Document> documents) {
            Map<String, Set<String>> reverseMap = new HashMap<>();

            // 扫描所有引用
            documents.forEach((file, doc) -> {
                // 扫描掉落表
                NodeList drops = doc.getElementsByTagName("drop");
                for (int i = 0; i < drops.getLength(); i++) {
                    Element drop = (Element) drops.item(i);
                    String itemId = drop.getAttribute("item_id");
                    if (!itemId.isEmpty()) {
                        reverseMap.computeIfAbsent("item:" + itemId, k -> new HashSet<>()).add(file);
                    }
                }

                // 扫描商店
                NodeList goods = doc.getElementsByTagName("goods");
                for (int i = 0; i < goods.getLength(); i++) {
                    Element good = (Element) goods.item(i);
                    String itemId = good.getAttribute("item_id");
                    if (!itemId.isEmpty()) {
                        reverseMap.computeIfAbsent("item:" + itemId, k -> new HashSet<>()).add(file);
                    }
                }

                // 可以继续添加其他引用类型...
            });

            return reverseMap;
        }
    }

    /**
     * 数值平衡性检查规则
     */
    private class BalanceCheckRule implements ValidationRule {
        @Override
        public String getName() {
            return "数值平衡性检查";
        }

        @Override
        public String getDescription() {
            return "检查游戏数值的平衡性问题";
        }

        @Override
        public List<ValidationResult> validate(Map<String, Document> documents) {
            List<ValidationResult> results = new ArrayList<>();

            // 检查装备属性平衡
            checkItemBalance(documents, results);

            // 检查怪物难度平衡
            checkMonsterBalance(documents, results);

            // 检查技能伤害平衡
            checkSkillBalance(documents, results);

            return results;
        }

        private void checkItemBalance(Map<String, Document> documents, List<ValidationResult> results) {
            // 按等级分组装备
            Map<Integer, List<ItemStats>> itemsByLevel = new HashMap<>();

            documents.entrySet().stream()
                .filter(e -> e.getKey().contains("items"))
                .forEach(e -> {
                    NodeList items = e.getValue().getElementsByTagName("item");
                    for (int i = 0; i < items.getLength(); i++) {
                        Element item = (Element) items.item(i);
                        String levelStr = item.getAttribute("level");
                        String attackStr = item.getAttribute("attack");
                        String defenseStr = item.getAttribute("defense");

                        if (!levelStr.isEmpty()) {
                            try {
                                int level = Integer.parseInt(levelStr);
                                int attack = attackStr.isEmpty() ? 0 : Integer.parseInt(attackStr);
                                int defense = defenseStr.isEmpty() ? 0 : Integer.parseInt(defenseStr);

                                ItemStats stats = new ItemStats();
                                stats.id = item.getAttribute("id");
                                stats.name = item.getAttribute("name");
                                stats.level = level;
                                stats.attack = attack;
                                stats.defense = defense;
                                stats.file = e.getKey();
                                stats.element = item;

                                itemsByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(stats);
                            } catch (NumberFormatException ex) {
                                // 忽略
                            }
                        }
                    }
                });

            // 检查每个等级的装备平衡
            itemsByLevel.forEach((level, items) -> {
                if (items.size() >= 3) {
                    // 计算平均值和标准差
                    double avgAttack = items.stream().mapToInt(i -> i.attack).average().orElse(0);
                    double avgDefense = items.stream().mapToInt(i -> i.defense).average().orElse(0);

                    // 检测异常值（偏离平均值超过50%）
                    for (ItemStats item : items) {
                        boolean unbalanced = false;
                        String reason = "";

                        if (item.attack > avgAttack * 1.5) {
                            unbalanced = true;
                            reason = String.format("攻击力(%d)远高于同等级平均值(%.0f)", item.attack, avgAttack);
                        } else if (item.attack < avgAttack * 0.5 && item.attack > 0) {
                            unbalanced = true;
                            reason = String.format("攻击力(%d)远低于同等级平均值(%.0f)", item.attack, avgAttack);
                        }

                        if (item.defense > avgDefense * 1.5) {
                            unbalanced = true;
                            reason = String.format("防御力(%d)远高于同等级平均值(%.0f)", item.defense, avgDefense);
                        } else if (item.defense < avgDefense * 0.5 && item.defense > 0) {
                            unbalanced = true;
                            reason = String.format("防御力(%d)远低于同等级平均值(%.0f)", item.defense, avgDefense);
                        }

                        if (unbalanced) {
                            ValidationResult result = new ValidationResult();
                            result.setSeverity(ValidationResult.ValidationSeverity.WARNING);
                            result.setType("平衡性问题");
                            result.setMessage("装备 " + item.name + " 的属性可能不平衡: " + reason);
                            result.setFile(item.file);
                            result.setElementPath(getElementPath(item.element));

                            Map<String, String> details = new HashMap<>();
                            details.put("item_id", item.id);
                            details.put("item_name", item.name);
                            details.put("level", String.valueOf(item.level));
                            details.put("attack", String.valueOf(item.attack));
                            details.put("defense", String.valueOf(item.defense));
                            details.put("avg_attack", String.format("%.0f", avgAttack));
                            details.put("avg_defense", String.format("%.0f", avgDefense));
                            result.setDetails(details);

                            result.setSuggestions(Arrays.asList(
                                "调整属性值使其接近平均水平",
                                String.format("建议攻击力范围: %.0f - %.0f", avgAttack * 0.8, avgAttack * 1.2),
                                String.format("建议防御力范围: %.0f - %.0f", avgDefense * 0.8, avgDefense * 1.2)
                            ));

                            results.add(result);
                        }
                    }
                }
            });
        }

        private void checkMonsterBalance(Map<String, Document> documents, List<ValidationResult> results) {
            // 实现怪物平衡性检查
            // ...
        }

        private void checkSkillBalance(Map<String, Document> documents, List<ValidationResult> results) {
            // 实现技能平衡性检查
            // ...
        }

        @Data
        private class ItemStats {
            String id;
            String name;
            int level;
            int attack;
            int defense;
            String file;
            Element element;
        }
    }

    /**
     * 引用完整性检查规则
     */
    private class ReferenceIntegrityRule implements ValidationRule {
        @Override
        public String getName() {
            return "引用完整性检查";
        }

        @Override
        public String getDescription() {
            return "检查配置文件之间的引用完整性";
        }

        @Override
        public List<ValidationResult> validate(Map<String, Document> documents) {
            List<ValidationResult> results = new ArrayList<>();

            // 实现引用完整性检查逻辑
            // ...

            return results;
        }
    }

    /**
     * 加载所有XML文档
     */
    private void loadAllDocuments(String directory) throws Exception {
        Path dir = Paths.get(directory);
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("Directory does not exist: " + directory);
        }

        List<Path> xmlFiles = Files.walk(dir)
            .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
            .collect(Collectors.toList());

        List<Future<Void>> futures = new ArrayList<>();
        for (Path file : xmlFiles) {
            Future<Void> future = executorService.submit(() -> {
                try {
                    Document doc = loadDocument(file.toString());
                    documentCache.put(file.getFileName().toString(), doc);
                } catch (Exception e) {
                    log.error("Failed to load document: " + file, e);
                }
                return null;
            });
            futures.add(future);
        }

        // 等待所有文档加载完成
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Document loading timeout");
            }
        }

        log.info("Loaded " + documentCache.size() + " documents");
    }

    /**
     * 构建引用关系图
     */
    private void buildReferenceMap() {
        // 实现引用关系构建逻辑
        // ...
    }

    /**
     * 查找引用特定文件的所有文件
     */
    private Set<String> findReferencingFiles(String fileName) {
        // 实现查找逻辑
        return referenceMap.getOrDefault(fileName, new HashSet<>());
    }

    /**
     * 验证引用
     */
    private List<ValidationResult> validateReferences(Document newDoc, Document refDoc,
                                                     String fileName, String refFile) {
        List<ValidationResult> results = new ArrayList<>();

        // 实现引用验证逻辑
        // ...

        return results;
    }

    /**
     * 检查平衡性影响
     */
    private List<ValidationResult> checkBalanceImpact(String fileName, Document doc) {
        List<ValidationResult> results = new ArrayList<>();

        // 实现平衡性影响检查
        // ...

        return results;
    }

    /**
     * 加载XML文档
     */
    private Document loadDocument(String filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // 处理UTF-16编码
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            byte[] bytes = readAllBytesCompat(is);
            String content = new String(bytes, StandardCharsets.UTF_16);
            if (!content.startsWith("<?xml")) {
                content = new String(bytes, StandardCharsets.UTF_8);
            }
            return builder.parse(new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * 解析XML内容
     */
    private Document parseXmlContent(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 获取元素路径
     */
    private String getElementPath(Element element) {
        StringBuilder path = new StringBuilder();
        Node current = element;

        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            path.insert(0, "/" + current.getNodeName());
            current = current.getParentNode();
        }

        return path.toString();
    }

    /**
     * 生成HTML报告
     */
    public String generateHtmlReport(ValidationReport report) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html><head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>数据一致性验证报告</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append(".error { color: #d32f2f; }\n");
        html.append(".warning { color: #f57c00; }\n");
        html.append(".info { color: #1976d2; }\n");
        html.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }\n");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("th { background-color: #f5f5f5; }\n");
        html.append(".summary { background: #e3f2fd; padding: 15px; border-radius: 5px; margin: 20px 0; }\n");
        html.append("</style>\n");
        html.append("</head><body>\n");

        html.append("<h1>数据一致性验证报告</h1>\n");

        // 摘要
        html.append("<div class=\"summary\">\n");
        html.append("<h2>验证摘要</h2>\n");
        html.append("<p>验证时间: ").append(report.getValidationDate()).append("</p>\n");
        html.append("<p>耗时: ").append(report.getValidationTime()).append(" ms</p>\n");
        html.append("<p>").append(report.getSummary()).append("</p>\n");
        html.append("</div>\n");

        // 按类型分组显示
        html.append("<h2>详细结果</h2>\n");
        report.getResultsByType().forEach((type, results) -> {
            html.append("<h3>").append(type).append(" (").append(results.size()).append(")</h3>\n");
            html.append("<table>\n");
            html.append("<tr><th>严重程度</th><th>文件</th><th>消息</th><th>建议</th></tr>\n");

            results.forEach(result -> {
                String severityClass = result.getSeverity().toString().toLowerCase();
                html.append("<tr class=\"").append(severityClass).append("\">\n");
                html.append("<td>").append(result.getSeverity()).append("</td>\n");
                html.append("<td>").append(result.getFile() != null ? result.getFile() : "-").append("</td>\n");
                html.append("<td>").append(result.getMessage()).append("</td>\n");
                html.append("<td>");
                if (result.getSuggestions() != null) {
                    html.append("<ul>");
                    result.getSuggestions().forEach(s ->
                        html.append("<li>").append(s).append("</li>"));
                    html.append("</ul>");
                }
                html.append("</td>\n");
                html.append("</tr>\n");
            });

            html.append("</table>\n");
        });

        html.append("</body></html>\n");

        return html.toString();
    }

    /**
     * Java 8兼容的readAllBytes方法
     */
    private byte[] readAllBytesCompat(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * 关闭验证器
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}