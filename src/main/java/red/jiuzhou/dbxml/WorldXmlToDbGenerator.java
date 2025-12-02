package red.jiuzhou.dbxml;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import red.jiuzhou.util.DatabaseUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @className: red.jiuzhou.dbxml.WorldXmlToDbGenerator.java
 * @description: xml转db
 * @author: yanxq
 * @date:  2025-04-15 20:42
 * @version V1.0
 */
public class WorldXmlToDbGenerator {

    private static final Logger log = LoggerFactory.getLogger(WorldXmlToDbGenerator.class);

    private final TableConf table;
    private final Document document;
    private double progress;
    private String mapType;

    private final List<Map<String, String>> mainTabList = new ArrayList<>();
    //private final Map<String, List<Map<String, String>>> subTabList = new HashMap<>();
    private final  Map<String, List<Map<String, String>>> subTabList = new TreeMap<>(
            Comparator.comparingInt(String::length).thenComparing(String::compareTo)
    );
    public WorldXmlToDbGenerator(String tabName, String mapType) {
        this.mapType = mapType;
        try {
            TableConf table = TabConfLoad.getTale(tabName, null);
            if (table == null) {
                throw new RuntimeException("找不到表配置信息：" + tabName);
            }
            table.chk();
            this.table = table;
            String xmlFilePath = table.getFilePath();
            if(mapType != null){
                String parent = FileUtil.getParent(xmlFilePath, 1);
                xmlFilePath = parent + File.separator + mapType + File.separator + FileUtil.getName(xmlFilePath);
            }
            String fileContent = FileUtil.readString( xmlFilePath, StandardCharsets.UTF_16);
            this.document = DocumentHelper.parseText(fileContent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void xmlTodb() {
        xmlToDb(table, document);
        List<String> allTableNameList = table.getAllTableNameList();
        // 按字符串长度倒序排序
        allTableNameList.sort(Comparator.comparingInt(String::length).reversed());
        allTableNameList.forEach(DatabaseUtil::delTable);
        try {
            // 计算总数据量
            int totalMain = mainTabList.size();
            int totalSub = subTabList.values().stream().mapToInt(List::size).sum();
            int totalRecords = totalMain + totalSub;
            int processedRecords = 0;

            System.out.printf("开始数据导入，总记录数: %d (主表: %d, 子表: %d)\n", totalRecords, totalMain, totalSub);

            // 处理主表数据
            List<List<Map<String, String>>> mainBatches = splitList(mainTabList, 1000);
            for (List<Map<String, String>> batch : mainBatches) {
                TransactionStatus transactionStatus = DatabaseUtil.beginTransaction();
                DatabaseUtil.batchInsert(table.getTableName(), batch);
                DatabaseUtil.commitTransaction(transactionStatus);
                processedRecords += batch.size();
                printProgress(processedRecords, totalRecords);
            }

            // 处理子表数据
            for (Map.Entry<String, List<Map<String, String>>> entry : subTabList.entrySet()) {
                String tableName = entry.getKey();
                List<Map<String, String>> list = entry.getValue();
                //FileUtil.writeUtf8String(JSON.toJSONString(list), "D:\\workspace\\xmlToDb\\data\\服务端xml\\XML\\China\\test" + File.separator + tableName + ".json");
                //System.out.println("处理子表数据：" + tableName + "，总记录数: " + list.size() + " ..." + list.toString());
                for (List<Map<String, String>> batch : splitList(list, 1000)) {
                    TransactionStatus transactionStatus = DatabaseUtil.beginTransaction();
                    DatabaseUtil.batchInsert(tableName, batch);
                    DatabaseUtil.commitTransaction(transactionStatus);

                    processedRecords += batch.size();
                    printProgress(processedRecords, totalRecords);
                }
            }

            System.out.println("数据导入完成！");
        } catch (Exception e) {
            allTableNameList.forEach(DatabaseUtil::delTable);
            throw new RuntimeException(e);
        }

    }

    public double getProgress() {
        return progress;
    }

    /**
     * 计算并打印进度
     */
    private void printProgress(int processed, int total) {
        progress = (double) processed / total;
        //System.out.printf("进度: %d/%d (%.2f%%)\n", processed, total, progress);
    }

    private void xmlToDb(TableConf table, Document document) {
        try {
            List<Element> elements = null;
            if(table.getXmlItemTag() == null || table.getXmlItemTag().isEmpty()){
                Element rootElement = document.getRootElement();
                elements = new ArrayList<>();
                elements.add(rootElement);
            }else{
                elements = document.getRootElement().elements(table.getXmlItemTag());
            }


            for (Element element : elements) {

                Iterator<Element> subEle = element.elementIterator();
                Map<String, String> mainMap = new HashMap<>();
                while (subEle.hasNext()) {
                    Element subElement = subEle.next();
                    if (!subElement.elements().isEmpty()) {
                        generateSubSql(element, subElement, table.getColumnMappingByXmlTag(subElement.getName()), mainMap);
                    } else {
                        mainMap.put(subElement.getName(), subElement.getText());
                    }
                }
                if(!element.attributes().isEmpty()){
                    Attribute attribute = element.attributes().get(0);
                    mainMap.put("_attr_"+attribute.getName(), attribute.getValue());
                }
                mainTabList.add(mainMap);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void generateSubSql(Element parentElement, Element element, ColumnMapping columnMaping, Map<String, String> parentMap) {
        if(columnMaping.getAddDataNode().contains(":")){
            String[] splitNodes = columnMaping.getAddDataNode().split(":");
            for (int i = 1; i < splitNodes.length; i++) {
                element = element.element(splitNodes[i]);
            }
        }
        if (!columnMaping.getAddDataNode().trim().isEmpty()) {

            List<Element> elements = element.elements(columnMaping.getXmlTag());
            elements.forEach(oneEle -> {
                Map<String, String> subMap = new HashMap<>();
                List<Map<String, String>> subList = subTabList.getOrDefault(columnMaping.getTableName(), new ArrayList<>());
                Iterator<Element> subEle = oneEle.elementIterator();
                if(parentMap.get(columnMaping.getAssociatedFiled()) == null && mapType != null){
                    subMap.put(columnMaping.getAssociatedFiled(), mapType);

                }else{
                    subMap.put(columnMaping.getAssociatedFiled(), parentMap.get(columnMaping.getAssociatedFiled()));

                }

                while (subEle.hasNext()) {
                    Element subElement = subEle.next();
                    if (!subElement.elements().isEmpty()) {
                        generateSubSql(oneEle, subElement, columnMaping.getColumnMappingByXmlTag(subElement.getName()), subMap);
                    } else {
                        subMap.put(subElement.getName(), subElement.getText());
                    }
                }
                subList.add(subMap);
                subTabList.put(columnMaping.getTableName(), subList);
            });
        }else{
            Map<String, String> subMap = new HashMap<>();
            List<Map<String, String>> subList = subTabList.getOrDefault(columnMaping.getTableName(), new ArrayList<>());
            Iterator<Element> subEle = element.elementIterator();
            if(parentMap.get(columnMaping.getAssociatedFiled()) == null && mapType != null){
                subMap.put(columnMaping.getAssociatedFiled(), mapType);
            }else{
                subMap.put(columnMaping.getAssociatedFiled(), parentMap.get(columnMaping.getAssociatedFiled()));
            }
            while (subEle.hasNext()) {
                Element subElement = subEle.next();
                if (!subElement.elements().isEmpty()) {
                    generateSubSql(element, subElement, columnMaping.getColumnMappingByXmlTag(subElement.getName()), subMap);
                } else {
                    subMap.put(subElement.getName(), subElement.getText());
                }
            }
            subList.add(subMap);
            subTabList.put(columnMaping.getTableName(), subList);
        }
    }

    public static <T> List<List<T>> splitList(List<T> list, int chunkSize) {
        List<List<T>> result = new ArrayList<>();
        Iterator<T> iterator = list.iterator();

        while (iterator.hasNext()) {
            List<T> chunk = new ArrayList<>(chunkSize);
            for (int i = 0; i < chunkSize && iterator.hasNext(); i++) {
                chunk.add(iterator.next());
            }
            result.add(chunk);
        }
        return result;
    }
}
