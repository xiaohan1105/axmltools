package red.jiuzhou.analysis.aion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XML字段解析器
 *
 * <p>解析Aion XML配置文件，提取字段结构和引用关系。
 *
 * @author Claude
 * @version 1.0
 */
public class XmlFieldParser {

    private static final Logger log = LoggerFactory.getLogger(XmlFieldParser.class);

    // 常见的引用字段模式
    private static final Map<Pattern, String> REFERENCE_PATTERNS = new LinkedHashMap<>();

    static {
        // 物品引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*item[_]?id.*"), "物品系统");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*item[_]?name.*"), "物品系统");

        // NPC引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*npc[_]?id.*"), "NPC系统");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*monster[_]?id.*"), "NPC系统");

        // 技能引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*skill[_]?id.*"), "技能系统");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*skill[_]?level.*"), "技能系统");

        // 任务引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*quest[_]?id.*"), "任务系统");

        // 地图/区域引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*map[_]?id.*"), "副本区域");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*world[_]?id.*"), "副本区域");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*zone[_]?id.*"), "副本区域");

        // 掉落引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*drop[_]?id.*"), "掉落系统");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*loot[_]?id.*"), "掉落系统");

        // 商店引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*shop[_]?id.*"), "商店交易");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*goods[_]?id.*"), "商店交易");

        // 称号引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*title[_]?id.*"), "称号系统");

        // 传送引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*portal[_]?id.*"), "传送系统");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*teleport.*"), "传送系统");
    }

    /**
     * 解析XML文件，提取字段信息
     */
    public static ParseResult parse(File xmlFile) {
        ParseResult result = new ParseResult(xmlFile.getName());

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            Element root = doc.getDocumentElement();
            result.setRootElement(root.getTagName());

            // 递归解析节点
            parseNode(root, "", result, 0);

        } catch (Exception e) {
            log.warn("解析XML失败: {} - {}", xmlFile.getName(), e.getMessage());
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * 递归解析节点
     */
    private static void parseNode(Node node, String path, ParseResult result, int depth) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element element = (Element) node;
        String currentPath = path.isEmpty() ? element.getTagName() : path + "/" + element.getTagName();

        // 解析属性
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String attrName = attr.getNodeName();
            String attrValue = attr.getNodeValue();

            FieldInfo field = new FieldInfo(attrName, currentPath + "@" + attrName, attrValue, true, depth);

            // 检查引用
            String refTarget = detectReference(attrName);
            if (refTarget != null) {
                field.setReferenceTarget(refTarget);
            }

            result.addField(field);
        }

        // 检查文本内容
        String textContent = getDirectTextContent(element);
        if (textContent != null && !textContent.isEmpty()) {
            FieldInfo field = new FieldInfo(element.getTagName(), currentPath, textContent, false, depth);
            result.addField(field);
        }

        // 递归子节点（限制深度避免过深）
        if (depth < 5) {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                parseNode(children.item(i), currentPath, result, depth + 1);
            }
        }
    }

    /**
     * 获取元素的直接文本内容（不包含子元素的文本）
     */
    private static String getDirectTextContent(Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 检测字段是否引用其他系统
     */
    public static String detectReference(String fieldName) {
        for (Map.Entry<Pattern, String> entry : REFERENCE_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(fieldName).matches()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 解析结果
     */
    public static class ParseResult {
        private final String fileName;
        private String rootElement;
        private final List<FieldInfo> fields = new ArrayList<>();
        private final Map<String, List<FieldInfo>> fieldsByPath = new LinkedHashMap<>();
        private final Set<String> references = new LinkedHashSet<>();
        private String error;

        public ParseResult(String fileName) {
            this.fileName = fileName;
        }

        public void setRootElement(String rootElement) {
            this.rootElement = rootElement;
        }

        public void addField(FieldInfo field) {
            fields.add(field);

            String path = field.getPath();
            if (!fieldsByPath.containsKey(path)) {
                fieldsByPath.put(path, new ArrayList<FieldInfo>());
            }
            fieldsByPath.get(path).add(field);

            if (field.getReferenceTarget() != null) {
                references.add(field.getReferenceTarget());
            }
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getFileName() {
            return fileName;
        }

        public String getRootElement() {
            return rootElement;
        }

        public List<FieldInfo> getFields() {
            return Collections.unmodifiableList(fields);
        }

        public Map<String, List<FieldInfo>> getFieldsByPath() {
            return Collections.unmodifiableMap(fieldsByPath);
        }

        public Set<String> getReferences() {
            return Collections.unmodifiableSet(references);
        }

        public String getError() {
            return error;
        }

        public boolean hasError() {
            return error != null;
        }

        /**
         * 获取唯一字段名列表（去重）
         */
        public List<String> getUniqueFieldNames() {
            Set<String> names = new LinkedHashSet<>();
            for (FieldInfo field : fields) {
                names.add(field.getName());
            }
            return new ArrayList<>(names);
        }

        /**
         * 获取引用其他系统的字段
         */
        public List<FieldInfo> getReferenceFields() {
            List<FieldInfo> result = new ArrayList<>();
            for (FieldInfo field : fields) {
                if (field.getReferenceTarget() != null) {
                    result.add(field);
                }
            }
            return result;
        }
    }

    /**
     * 字段信息
     */
    public static class FieldInfo {
        private final String name;
        private final String path;
        private final String sampleValue;
        private final boolean isAttribute;
        private final int depth;
        private String referenceTarget;

        public FieldInfo(String name, String path, String sampleValue, boolean isAttribute, int depth) {
            this.name = name;
            this.path = path;
            this.sampleValue = truncateValue(sampleValue);
            this.isAttribute = isAttribute;
            this.depth = depth;
        }

        private static String truncateValue(String value) {
            if (value == null) return "";
            if (value.length() > 100) {
                return value.substring(0, 97) + "...";
            }
            return value;
        }

        public void setReferenceTarget(String referenceTarget) {
            this.referenceTarget = referenceTarget;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public String getSampleValue() {
            return sampleValue;
        }

        public boolean isAttribute() {
            return isAttribute;
        }

        public int getDepth() {
            return depth;
        }

        public String getReferenceTarget() {
            return referenceTarget;
        }

        public boolean hasReference() {
            return referenceTarget != null;
        }

        @Override
        public String toString() {
            String ref = referenceTarget != null ? " → " + referenceTarget : "";
            return String.format("%s%s = %s%s",
                    isAttribute ? "@" : "",
                    name,
                    sampleValue,
                    ref);
        }
    }
}
