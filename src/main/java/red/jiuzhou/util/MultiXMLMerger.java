package red.jiuzhou.util;

import cn.hutool.core.io.FileUtil;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.xmltosql.XmlAllNode;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MultiXMLMerger {

    private static final Logger log = LoggerFactory.getLogger(MultiXMLMerger.class);
    public static void merger(List<File> files, String xmlfilePath) {
        // 读取第一个 XML 作为基础
        File firstFile = files.get(0);
        String parent = firstFile.getParent();

        Document mergedDoc = null;
        try {
            String allNodeXmlStr = XmlAllNode.getAllNodeXmlStr(firstFile.getAbsolutePath());

            mergedDoc = DocumentHelper.parseText(allNodeXmlStr);
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        }
        files.remove(0);
        // 依次合并后续 XML
        Document finalMergedDoc = mergedDoc;
        files.forEach(file -> {
            String filePath = file.getAbsolutePath();
            String allNodeXmlStr = XmlAllNode.getAllNodeXmlStr(filePath);
            assert allNodeXmlStr != null;
            try {
                assert allNodeXmlStr != null;
                Document currentDoc = DocumentHelper.parseText(allNodeXmlStr);
                mergeElements(finalMergedDoc.getRootElement(), currentDoc.getRootElement());
            } catch (DocumentException e) {
                throw new RuntimeException(e);
            }
        });
        FileUtil.touch(xmlfilePath);
        // 保存最终合并的 XML
         saveDocument(mergedDoc, xmlfilePath);
    }

    /**
     * 递归合并两个 XML 节点，考虑属性但不考虑值
     */
    /**
     * 递归合并两个 XML 节点：
     * 1. 只要 XPath 相同就合并
     * 2. 属性合并（相同属性保留，不同属性追加）
     * 3. 递归合并子节点
     */
    public static void mergeElements(Element base, Element toMerge) {
        // 先合并属性
        if(!toMerge.attributes().isEmpty()){
            mergeAttributes(base, toMerge);
        }


        // 遍历 toMerge 的子节点
        for (Element mergingChild : toMerge.elements()) {
            String nodeName = mergingChild.getName();
            Element baseChild = findElementByXPath(base, nodeName);

            if (baseChild != null) {
                // 递归合并子节点
                mergeElements(baseChild, mergingChild);
            } else {
                // 直接追加
                base.add(mergingChild.createCopy());
            }
        }
    }

    /**
     * 合并两个节点的属性：
     * 1. 如果 base 节点已有某个属性，则保持原值
     * 2. 如果 base 没有某个属性，则从 toMerge 复制过来
     */
    private static void mergeAttributes(Element base, Element toMerge) {
        for (Attribute attr : toMerge.attributes()) {
            if (base.attribute(attr.getName()) == null) {
                base.addAttribute(attr.getName(), attr.getValue());
            }
        }
    }

    /**
     * 在父节点下查找指定名称的子节点，仅基于 XPath 结构匹配（忽略属性）
     */
    private static Element findElementByXPath(Element parent, String nodeName) {
        List<Element> children = parent.elements(nodeName);
        return children.isEmpty() ? null : children.get(0); // 只取第一个匹配的
    }

    /**
     * 在父节点下查找指定名称的子节点，要求属性名匹配，但不考虑值
     */
    private static Element findElementWithAttributes(Element parent, String nodeName, Element reference) {
        List<Element> children = parent.elements(nodeName);
        for (Element child : children) {
            if (hasSameAttributesIgnoreValue(child, reference)) {
                return child;
            }
        }
        return null;
    }

    /**
     * 判断两个节点的属性是否相同（仅考虑属性名，不考虑值）
     */
    private static boolean hasSameAttributesIgnoreValue(Element e1, Element e2) {
        Map<String, String> attrMap1 = getAttributeKeys(e1);
        Map<String, String> attrMap2 = getAttributeKeys(e2);
        return attrMap1.equals(attrMap2);
    }

    /**
     * 获取元素的属性映射（仅包含属性名）
     */
    private static Map<String, String> getAttributeKeys(Element element) {
        Map<String, String> attrMap = new HashMap<>();
        for (Attribute attr : element.attributes()) {
            attrMap.put(attr.getName(), ""); // 只存储属性名，忽略值
        }
        return attrMap;
    }

    /**
     * 保存 XML 到文件
     */
    public static void saveDocument(Document document, String filePath) {
        OutputFormat format = OutputFormat.createPrettyPrint();
        XMLWriter writer = null;
        try {
            writer = new XMLWriter(new FileWriter(filePath), format);
            writer.write(document);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
