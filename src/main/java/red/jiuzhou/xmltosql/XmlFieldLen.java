package red.jiuzhou.xmltosql;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import red.jiuzhou.util.JSONRecord;

import java.io.File;
import java.util.List;

public class XmlFieldLen {
    public static void main(String[] args) {
        String filePath = "D:\\workspace\\dbxmlTool\\src\\main\\resources\\DATA\\SVR_DATA\\China\\abyss.xml";
        JSONRecord filedLenJson = getFiledLenJson(filePath);
        System.out.println(filedLenJson);
    }

    public static JSONRecord getFiledLenJson(List<File> files) {
        JSONRecord filedLenJson = new JSONRecord();
        String fileName = FileUtil.getName(files.get(0).getAbsolutePath()).split("\\.")[0];

        files.forEach(file -> {
            String filePath = file.getAbsolutePath();
            SAXReader reader = new SAXReader();
            Document document = null;
            try {
                document = reader.read(filePath);
            } catch (DocumentException e) {
                System.err.println(filePath);
                throw new RuntimeException(e);
            }

            Element root = document.getRootElement();
            show(fileName, root, filedLenJson);
        });

        return filedLenJson.getOrCreateRecord(fileName);
    }
    public static JSONRecord getFiledLenJson(String filePath) {
        JSONRecord filedLenJson = new JSONRecord();
        String fileName = FileUtil.getName(filePath).split("\\.")[0];

        SAXReader reader = new SAXReader();
        Document document = null;
        try {
            document = reader.read(new File(filePath));
        } catch (DocumentException e) {
            System.err.println(filePath);
            throw new RuntimeException(e);
        }

        Element root = document.getRootElement();
        show(fileName, root, filedLenJson);

        return filedLenJson.getOrCreateRecord(fileName);
    }

    private static void show(String fileName, Element element, JSONRecord filedLenJson) {
        List<Element> children = element.elements();

        if (children.isEmpty()) {
            int len = filedLenJson.getOrCreateRecord(fileName).getIntegerVal(element.getName(), 0);

            if (element.getText().length() > len) {
                filedLenJson.getOrCreateRecord(fileName).put(element.getName(), element.getText().length());
            }
            return;
        }

        for (Element child : children) {
            show(fileName, child, filedLenJson);
        }
    }
}
