package red.jiuzhou.xmltosql;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.JSONRecord;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @className XmlFiledEnum
 * @description: 1
 * @author yanxq
 * @date 2025/03/29  18:53
 * @version V1.0
**/
public class XmlFiledValNum {
    private static final List<String> excludeNames = Arrays.asList("id,name,desc".split(","));

    public static void getFiledLenJson(JSONRecord filedValNumJson, String filePath, String newFileName) {
        String fileName = FileUtil.getName(filePath).split("\\.")[0];
        if(StringUtils.hasLength(newFileName)){
            fileName = newFileName;
        }

        SAXReader reader = new SAXReader();
        Document document = null;
        try {
            document = reader.read(new File(filePath));
        } catch (DocumentException e) {
            System.err.println(filePath);
            throw new RuntimeException(e);
        }

        Element root = document.getRootElement();
        show(fileName, root, filedValNumJson);
    }

    private static void show(String fileName, Element element, JSONRecord filedLenJson) {
        List<Element> children = element.elements();

        if (children.isEmpty()) {
            if(excludeNames.contains(element.getName())){
                return;
            }
            if(element.getName().endsWith("_id") || element.getName().contains("name") || element.getName().contains("desc")){
                return;
            }
            String text = element.getText();
            JSONRecord filed = filedLenJson.getOrCreateRecord(fileName).getOrCreateRecord(element.getName())
                    .getOrCreateRecord(text);
            filed.put("num", filed.getIntegerVal("num", 0) + 1);
            return;
        }

        for (Element child : children) {
            show(fileName, child, filedLenJson);
        }
    }
}