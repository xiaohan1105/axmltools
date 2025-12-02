package red.jiuzhou.xmltosql;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.XmlUtil;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @className XmlAllNode
 * @description: xmlallnode
 * @author yanxq
 * @date 2025/03/29  10:09
 * @version V1.0
**/
public class XmlAllNode {
    private static final Logger log = LoggerFactory.getLogger(XmlProcess.class);
    public static String getAllNodeXmlStr(String filePath) {
        try {
            // 1. 解析 XML 文件
            File xmlFile = new File(filePath);
            SAXReader reader = new SAXReader();
            Document document = reader.read(xmlFile);

            // 2. 获取根节点
            Element root = document.getRootElement();

            // 树结构存储合并数据
            Map<String, Object> mergedTree = new LinkedHashMap<>();
            // 存储所有属性
            for (Attribute attribute : root.attributes()) {
                mergedTree.put("_attr_" + attribute.getName(), attribute.getValue());
            }

            // 3. 遍历根节点下的所有第一层子节点
            for (Element element : root.elements()) {
                mergeTree(mergedTree, element);
            }
            // 4. 创建新的 Document 并构造合并的节点
            Document newDocument = DocumentHelper.createDocument();
            Element newRoot = newDocument.addElement(root.getName());

            buildXmlFromTree(newRoot, mergedTree);
            return XmlUtil.formatXml(newDocument);

        } catch (Exception e) {
            log.error("解析XML{}文件获取全节点XML失败", filePath, e);
        }
        return null;
    }

    /**
     * 递归合并 XML 结构到树中
     */
    private static void mergeTree(Map<String, Object> tree, Element element) {
        String tagName = element.getName();
        // 处理标签名
        if (!tree.containsKey(tagName)) {
            // 如果当前标签不存在，则直接存储
            Map<String, Object> subTree = new LinkedHashMap<>();
            tree.put(tagName, subTree);
            List<Attribute> attributes = element.attributes();
            if(!attributes.isEmpty()){
                attributes.forEach(attribute -> {
                    subTree.put("_attr_"+attribute.getName(), attribute.getValue());
                });
            }

            for (Element child : element.elements()) {
                mergeTree(subTree, child);
            }
        } else {
            // 如果标签已存在，则递归合并子节点
            Object existing = tree.get(tagName);
            if (existing instanceof Map) {
                Map<String, Object> existingSubTree = (Map<String, Object>) existing;
                for (Element child : element.elements()) {
                    mergeTree(existingSubTree, child);
                }
                element.attributes().forEach(attribute -> {
                    if(!existingSubTree.containsKey("_attr_"+attribute.getName())){
                        existingSubTree.put("_attr_"+attribute.getName(), attribute.getValue());
                    }
                });
            }
        }
    }

    /**
     * 递归将树结构转换为 XML
     */
    private static void buildXmlFromTree(Element parentElement, Map<String, Object> tree) {
        for (Map.Entry<String, Object> entry : tree.entrySet()) {
            if (entry.getKey().startsWith("_attr_")) {
                // 处理属性// 去掉"_attr_"前缀
                String attrName = entry.getKey().substring(6);
                parentElement.addAttribute(attrName, entry.getValue().toString());
            } else {
                // 处理子元素
                Element newElement = parentElement.addElement(entry.getKey());
                if (entry.getValue() instanceof Map) {
                    // 递归构造 XML 子节点
                    buildXmlFromTree(newElement, (Map<String, Object>) entry.getValue());
                }
            }
        }
    }

    public static void main(String[] args) {
        String dataFilePath = "D:\\workspace\\dbxmlTool\\data\\DATA\\SVR_DATA\\Worlds\\df1a\\world.xml";
            try {
                String xmlStr = getAllNodeXmlStr(dataFilePath);
                System.out.println(xmlStr);
            }catch (Exception e){
            }


    }
}