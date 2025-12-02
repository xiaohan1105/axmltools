package red.jiuzhou.search;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import red.jiuzhou.safety.DataSafetyManager;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * 全局智能搜索引擎
 * 支持跨文件XML搜索、条件替换、关联提示等功能
 *
 * 特性：
 * - 支持正则表达式搜索
 * - 支持XPath搜索
 * - 支持条件替换（如等级范围、属性条件）
 * - 自动检测关联配置
 * - 多线程并行搜索
 */
@Slf4j
public class GlobalSearchEngine {

    private static final int THREAD_POOL_SIZE = 8;
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private final Map<String, List<String>> relationshipMap = new HashMap<>();

    // 搜索模式枚举
    public enum SearchMode {
        TEXT,           // 文本搜索
        REGEX,          // 正则表达式
        XPATH,          // XPath表达式
        ID,             // ID搜索
        ATTRIBUTE,      // 属性搜索
        SMART           // 智能搜索（自动识别）
    }

    // 搜索结果
    @Data
    public static class SearchResult {
        private String filePath;
        private int lineNumber;
        private String elementPath;      // XML路径
        private String matchedText;      // 匹配的文本
        private String contextBefore;    // 前文
        private String contextAfter;     // 后文
        private Element element;         // DOM元素
        private List<String> relatedFiles; // 关联文件
        private Map<String, String> attributes; // 元素属性

        public String getDisplayText() {
            return String.format("[%s:%d] %s\n%s",
                new File(filePath).getName(), lineNumber, elementPath, matchedText);
        }
    }

    // 替换选项
    @Data
    public static class ReplaceOptions {
        private boolean caseSensitive = true;
        private boolean wholeWord = false;
        private boolean useRegex = false;
        private boolean preview = true;
        private String condition;  // 条件表达式，如 "level > 50"
        private boolean backup = true;

        // 条件替换的处理器
        public interface ConditionEvaluator {
            boolean evaluate(Element element);
        }

        private ConditionEvaluator conditionEvaluator;

        public void parseCondition() {
            if (condition == null || condition.isEmpty()) {
                conditionEvaluator = element -> true;
                return;
            }

            // 解析条件表达式，支持简单的比较操作
            // 例如: "level > 50", "type = 'weapon'", "attack_power >= 100"
            String[] parts = condition.split("\\s+");
            if (parts.length == 3) {
                String attribute = parts[0];
                String operator = parts[1];
                String value = parts[2].replace("'", "").replace("\"", "");

                conditionEvaluator = element -> {
                    String attrValue = element.getAttribute(attribute);
                    if (attrValue.isEmpty()) {
                        // 尝试从子元素获取
                        NodeList children = element.getElementsByTagName(attribute);
                        if (children.getLength() > 0) {
                            attrValue = children.item(0).getTextContent();
                        }
                    }

                    if (attrValue.isEmpty()) return false;

                    try {
                        switch (operator) {
                            case "=":
                            case "==":
                                return attrValue.equals(value);
                            case "!=":
                                return !attrValue.equals(value);
                            case ">":
                                return Double.parseDouble(attrValue) > Double.parseDouble(value);
                            case ">=":
                                return Double.parseDouble(attrValue) >= Double.parseDouble(value);
                            case "<":
                                return Double.parseDouble(attrValue) < Double.parseDouble(value);
                            case "<=":
                                return Double.parseDouble(attrValue) <= Double.parseDouble(value);
                            case "contains":
                                return attrValue.contains(value);
                            default:
                                return true;
                        }
                    } catch (NumberFormatException e) {
                        // 非数字比较，使用字符串比较
                        return attrValue.compareTo(value) > 0;
                    }
                };
            } else {
                conditionEvaluator = element -> true;
            }
        }
    }

    /**
     * 初始化关联关系映射
     */
    public void initializeRelationships() {
        // 装备与掉落表的关联
        relationshipMap.put("client_items_", Arrays.asList(
            "npc_drop.xml", "quest_reward.xml", "combine_recipe.xml"));

        // NPC与技能的关联
        relationshipMap.put("npcs_", Arrays.asList(
            "skill_templates.xml", "npc_drop.xml", "spawn_maps.xml"));

        // 技能与学习配置的关联
        relationshipMap.put("skill_", Arrays.asList(
            "skill_learn.xml", "class_skills.xml", "skill_tree.xml"));

        // 任务与奖励的关联
        relationshipMap.put("quest_", Arrays.asList(
            "quest_reward.xml", "quest_items.xml", "npc_dialog.xml"));
    }

    /**
     * 在指定目录下搜索XML文件
     */
    public List<SearchResult> searchInDirectory(String directory, String searchText,
                                                SearchMode mode) throws Exception {
        Path dir = Paths.get(directory);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Invalid directory: " + directory);
        }

        List<Path> xmlFiles = Files.walk(dir)
            .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
            .collect(Collectors.toList());

        return searchInFiles(xmlFiles, searchText, mode);
    }

    /**
     * 在多个文件中搜索
     */
    public List<SearchResult> searchInFiles(List<Path> files, String searchText,
                                           SearchMode mode) throws Exception {
        List<Future<List<SearchResult>>> futures = new ArrayList<>();

        for (Path file : files) {
            Future<List<SearchResult>> future = executorService.submit(() ->
                searchInFile(file, searchText, mode));
            futures.add(future);
        }

        List<SearchResult> allResults = new ArrayList<>();
        for (Future<List<SearchResult>> future : futures) {
            try {
                allResults.addAll(future.get(30, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                log.warn("Search timeout for file");
            }
        }

        // 检测关联文件
        detectRelatedFiles(allResults);

        return allResults;
    }

    /**
     * 在单个文件中搜索
     */
    private List<SearchResult> searchInFile(Path file, String searchText,
                                           SearchMode mode) throws Exception {
        List<SearchResult> results = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // 处理UTF-16编码
            Document doc;
            try (InputStream is = Files.newInputStream(file)) {
                // 检测编码
                byte[] bytes = readAllBytesCompat(is);
                String content = new String(bytes, StandardCharsets.UTF_16);
                if (!content.startsWith("<?xml")) {
                    content = new String(bytes, StandardCharsets.UTF_8);
                }
                doc = builder.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            }

            doc.getDocumentElement().normalize();

            switch (mode) {
                case XPATH:
                    results = searchByXPath(doc, file, searchText);
                    break;
                case REGEX:
                    results = searchByRegex(doc, file, searchText);
                    break;
                case ID:
                    results = searchById(doc, file, searchText);
                    break;
                case ATTRIBUTE:
                    results = searchByAttribute(doc, file, searchText);
                    break;
                case SMART:
                    results = smartSearch(doc, file, searchText);
                    break;
                default:
                    results = searchByText(doc, file, searchText);
            }

        } catch (Exception e) {
            log.error("Error searching in file: " + file, e);
        }

        return results;
    }

    /**
     * XPath搜索
     */
    private List<SearchResult> searchByXPath(Document doc, Path file, String xpath)
                                            throws Exception {
        List<SearchResult> results = new ArrayList<>();
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        XPathExpression expr = xPath.compile(xpath);

        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                results.add(createSearchResult(file, (Element) node));
            }
        }

        return results;
    }

    /**
     * 正则表达式搜索
     */
    private List<SearchResult> searchByRegex(Document doc, Path file, String regex) {
        List<SearchResult> results = new ArrayList<>();
        Pattern pattern = Pattern.compile(regex);

        searchNodesRecursively(doc.getDocumentElement(), element -> {
            // 搜索文本内容
            String text = element.getTextContent();
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                SearchResult result = createSearchResult(file, element);
                result.setMatchedText(matcher.group());
                results.add(result);
            }

            // 搜索属性值
            NamedNodeMap attrs = element.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr attr = (Attr) attrs.item(i);
                matcher = pattern.matcher(attr.getValue());
                if (matcher.find()) {
                    SearchResult result = createSearchResult(file, element);
                    result.setMatchedText(attr.getName() + "=\"" + matcher.group() + "\"");
                    results.add(result);
                }
            }
        });

        return results;
    }

    /**
     * ID搜索
     */
    private List<SearchResult> searchById(Document doc, Path file, String id) {
        List<SearchResult> results = new ArrayList<>();

        // 搜索id属性
        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String idValue = element.getAttribute("id");
            if (idValue.equals(id)) {
                results.add(createSearchResult(file, element));
            }
        }

        return results;
    }

    /**
     * 属性搜索
     */
    private List<SearchResult> searchByAttribute(Document doc, Path file, String search) {
        List<SearchResult> results = new ArrayList<>();
        String[] parts = search.split("=");
        String attrName = parts[0].trim();
        String attrValue = parts.length > 1 ? parts[1].trim() : "";

        searchNodesRecursively(doc.getDocumentElement(), element -> {
            if (element.hasAttribute(attrName)) {
                if (attrValue.isEmpty() || element.getAttribute(attrName).contains(attrValue)) {
                    results.add(createSearchResult(file, element));
                }
            }
        });

        return results;
    }

    /**
     * 智能搜索 - 自动识别搜索模式
     */
    private List<SearchResult> smartSearch(Document doc, Path file, String searchText) {
        // 判断搜索类型
        if (searchText.startsWith("//") || searchText.startsWith("/")) {
            try {
                return searchByXPath(doc, file, searchText);
            } catch (Exception e) {
                log.debug("Not a valid XPath, trying other methods");
            }
        }

        if (searchText.contains("=")) {
            return searchByAttribute(doc, file, searchText);
        }

        if (searchText.matches("^\\d+$")) {
            return searchById(doc, file, searchText);
        }

        // 默认文本搜索
        return searchByText(doc, file, searchText);
    }

    /**
     * 文本搜索
     */
    private List<SearchResult> searchByText(Document doc, Path file, String searchText) {
        List<SearchResult> results = new ArrayList<>();
        String lowerSearch = searchText.toLowerCase();

        searchNodesRecursively(doc.getDocumentElement(), element -> {
            // 搜索文本内容
            String text = element.getTextContent();
            if (text != null && text.toLowerCase().contains(lowerSearch)) {
                SearchResult result = createSearchResult(file, element);
                result.setMatchedText(extractContext(text, searchText));
                results.add(result);
            }

            // 搜索属性
            NamedNodeMap attrs = element.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr attr = (Attr) attrs.item(i);
                if (attr.getValue().toLowerCase().contains(lowerSearch)) {
                    SearchResult result = createSearchResult(file, element);
                    result.setMatchedText(attr.getName() + "=\"" + attr.getValue() + "\"");
                    results.add(result);
                }
            }
        });

        return results;
    }

    /**
     * 递归遍历节点
     */
    private void searchNodesRecursively(Element element, NodeProcessor processor) {
        processor.process(element);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                searchNodesRecursively((Element) child, processor);
            }
        }
    }

    /**
     * 创建搜索结果
     */
    private SearchResult createSearchResult(Path file, Element element) {
        SearchResult result = new SearchResult();
        result.setFilePath(file.toString());
        result.setElement(element);
        result.setElementPath(getElementPath(element));

        // 获取所有属性
        Map<String, String> attributes = new HashMap<>();
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            attributes.put(attr.getName(), attr.getValue());
        }
        result.setAttributes(attributes);

        // 获取行号（近似）
        result.setLineNumber(getLineNumber(element));

        return result;
    }

    /**
     * 获取元素的XPath路径
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
     * 获取行号（近似值）
     */
    private int getLineNumber(Element element) {
        // DOM不直接提供行号，这里返回一个近似值
        int line = 1;
        Node current = element;
        while (current.getPreviousSibling() != null) {
            current = current.getPreviousSibling();
            line++;
        }
        return line;
    }

    /**
     * 提取上下文
     */
    private String extractContext(String text, String searchText) {
        int index = text.toLowerCase().indexOf(searchText.toLowerCase());
        if (index == -1) return text;

        int start = Math.max(0, index - 50);
        int end = Math.min(text.length(), index + searchText.length() + 50);

        String context = text.substring(start, end);
        if (start > 0) context = "..." + context;
        if (end < text.length()) context = context + "...";

        return context;
    }

    /**
     * 检测关联文件
     */
    private void detectRelatedFiles(List<SearchResult> results) {
        for (SearchResult result : results) {
            List<String> related = new ArrayList<>();
            String fileName = new File(result.getFilePath()).getName();

            for (Map.Entry<String, List<String>> entry : relationshipMap.entrySet()) {
                if (fileName.startsWith(entry.getKey())) {
                    related.addAll(entry.getValue());
                }
            }

            result.setRelatedFiles(related);
        }
    }

    /**
     * 批量替换功能
     */
    public Map<String, Integer> replaceAll(List<SearchResult> searchResults,
                                          String replaceText, ReplaceOptions options) throws Exception {
        Map<String, Integer> replaceCount = new HashMap<>();
        Map<String, Document> documentCache = new HashMap<>();

        // 解析条件
        options.parseCondition();

        for (SearchResult result : searchResults) {
            String filePath = result.getFilePath();

            // 检查条件
            if (options.getConditionEvaluator() != null &&
                !options.getConditionEvaluator().evaluate(result.getElement())) {
                continue;
            }

            // 加载或获取文档
            Document doc = documentCache.get(filePath);
            if (doc == null) {
                doc = loadDocument(filePath);
                documentCache.put(filePath, doc);
            }

            // 执行替换
            Element element = result.getElement();
            String originalText = element.getTextContent();
            String newText = performReplace(originalText, result.getMatchedText(),
                                           replaceText, options);

            if (!originalText.equals(newText)) {
                element.setTextContent(newText);
                replaceCount.merge(filePath, 1, Integer::sum);
            }
        }

        // 保存修改的文档
        if (!options.isPreview()) {
            for (Map.Entry<String, Document> entry : documentCache.entrySet()) {
                String filePath = entry.getKey();
                Document doc = entry.getValue();

                if (replaceCount.containsKey(filePath)) {
                    if (options.isBackup()) {
                        backupFile(filePath);
                    }
                    saveDocument(doc, filePath);
                }
            }
        }

        return replaceCount;
    }

    /**
     * 执行替换操作
     */
    private String performReplace(String text, String searchText,
                                 String replaceText, ReplaceOptions options) {
        if (options.isUseRegex()) {
            Pattern pattern = Pattern.compile(searchText,
                options.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE);
            return pattern.matcher(text).replaceAll(replaceText);
        } else {
            if (options.isWholeWord()) {
                String regex = "\\b" + Pattern.quote(searchText) + "\\b";
                Pattern pattern = Pattern.compile(regex,
                    options.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE);
                return pattern.matcher(text).replaceAll(replaceText);
            } else {
                if (options.isCaseSensitive()) {
                    return text.replace(searchText, replaceText);
                } else {
                    // 不区分大小写的替换
                    String regex = Pattern.quote(searchText);
                    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    return pattern.matcher(text).replaceAll(replaceText);
                }
            }
        }
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
     * 保存XML文档
     */
    private void saveDocument(Document doc, String filePath) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filePath));
        transformer.transform(source, result);
    }

    /**
     * 备份文件
     */
    private void backupFile(String filePath) throws IOException {
        Path source = Paths.get(filePath);
        Path backup = Paths.get(filePath + ".bak");
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 关闭搜索引擎
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

    // 内部接口
    private interface NodeProcessor {
        void process(Element element);
    }
}