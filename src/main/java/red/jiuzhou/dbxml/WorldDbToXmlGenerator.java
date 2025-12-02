package red.jiuzhou.dbxml;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.*;

import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @className: red.jiuzhou.dbxml.WorldDbToXmlGenerator.java
 * @description: 生成World数据库的XML文件
 * @author: yanxq
 * @date:  2025-04-15 20:41
 * @version V1.0
 */
public class WorldDbToXmlGenerator {
    private static final Logger log = LoggerFactory.getLogger(WorldDbToXmlGenerator.class);
    private double progress;
    private static TableConf table;
    // 根据CPU核心数调整
    private static final int THREAD_POOL_SIZE = 16;
    // 每页数据量
    private static final int PAGE_SIZE = 1000;
    // 临时文件存放目录
    private static final String TEMP_DIR = "temp_xml/";
    private int total;
    private CounterUtil counterUtil = new CounterUtil();
    private static String mapType;

    static List<String> worldSpecialTabNames = Arrays.asList(YamlUtils.getProperty("world.specialTabName").split(","));

    private static final ExecutorService executorService =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    public WorldDbToXmlGenerator(String tabName, String mapType, String tabFilePath) {
        this.mapType = mapType;
        TableConf table = TabConfLoad.getTale(tabName, tabFilePath);
        if (table == null) {
            throw new RuntimeException("找不到表配置信息：" + tabName);
        }
        table.chk();
        this.table = table;
    }

    public void processAndMerge() {
        try {
            // 1. 获取总数据量
            int totalRecords = DatabaseUtil.getTotalRowCount(table.getTableName());
            this.total = totalRecords;
            int totalPages = (totalRecords + PAGE_SIZE - 1) / PAGE_SIZE;

            // 2. 初始化线程池
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            List<Future<?>> futures = new ArrayList<>();
            FileUtil.del(YamlUtils.getProperty("file.exportDataPath") + File.separator + TEMP_DIR);
            // 3. 分页提交任务
            for (int page = 0; page < totalPages; page++) {
                int offset = page * PAGE_SIZE;
                int finalPage = page;
                Callable<Void> task = () -> {
                    String fileName = TEMP_DIR + "part_" + finalPage + ".xml";
                    log.info("开始处理分页：{}", finalPage);
                    generateXmlPart(table, offset,  PAGE_SIZE, fileName);
                    return null;
                };
                futures.add(executor.submit(task));
            }

            // 4. 等待所有线程完成
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            // 5. 合并所有临时文件
            List<File> tempFileList = FileUtil.loopFiles(YamlUtils.getProperty("file.exportDataPath") + File.separator + TEMP_DIR).stream()
                    .filter(file -> file.getName().endsWith(".xml"))
                    .collect(Collectors.toList());
            tempFileList.sort(Comparator.comparing(File::getName));
            mergeXmlFiles(tempFileList);

            // 6. 清理临时文件
            FileUtil.del(YamlUtils.getProperty("file.exportDataPath") + File.separator + TEMP_DIR);


        } catch (Exception e) {
            throw new RuntimeException("处理失败", e);
        }
    }

    // 生成分页XML
    private void generateXmlPart(TableConf table, int offset, int limit, String outputFileName) {
        try {
            String sql = table.getSql();
            if(mapType != null && !mapType.isEmpty()){
                sql = sql.replace("$mapType", mapType) + " LIMIT " + limit + " OFFSET " + offset;
            }else{
                sql = sql + " LIMIT " + limit + " OFFSET " + offset;
            }

            log.info("sql:{}", sql);
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            List<Map<String, Object>> itemList = jdbcTemplate.queryForList(sql);
            List<String> listDbcolumnList = table.getListDbcolumnList();
            Document document = DocumentHelper.createDocument();
            Element root = document.addElement(table.getXmlRootTag());

            for (Map<String, Object> itemMap : itemList) {
                Element element = null;
                if(table.getXmlItemTag() != null && !table.getXmlItemTag().isEmpty()){
                    element = root.addElement(table.getXmlItemTag());
                }else{
                    element = root;
                }
                Set<String> keySet = itemMap.keySet();
                if("world".equals(table.getTableName())){
                    total = keySet.size();
                    mapType = String.valueOf(itemMap.get("name"));
                }
                for (String key : keySet) {
                    if("world".equals(table.getTableName()) && "mapTp".equals(key)){
                        continue;
                    }
                    if (itemMap.get(key) != null) {
                        if(key.startsWith("_attr_")){
                            element.addAttribute(key.replace("_attr_", ""), (String) itemMap.get(key));
                        }else{
                            element.addElement(key).setText((String) itemMap.get(key));
                        }
                    }
                    if (listDbcolumnList.contains(key)) {
                        ColumnMapping columnMapping = table.getColumnMapping(key);
                        String parentVal = getParentVal(itemMap, columnMapping);
                        parseSubquery(element, columnMapping, jdbcTemplate, parentVal, 1);
                    }
                    if("world".equals(table.getTableName())){
                        counterUtil.increment();
                    }
                }
                counterUtil.increment();
                log.info("进度：" + counterUtil.getCount() + "/" + total + "，完成度：" + (counterUtil.getCount() / (double) total * 100) + "%");
            }

            // 保存为临时文件
            FileUtil.writeString(document.asXML(), YamlUtils.getProperty("file.exportDataPath") + File.separator + outputFileName, StandardCharsets.UTF_16);
        } catch (Exception e) {
            log.error("err::::::::::::" + JSONRecord.getErrorMsg(e));
            throw new RuntimeException("生成分页XML失败", e);
        }
    }
    // 合并所有XML文件
    private void mergeXmlFiles(List<File> xmlFiles) throws Exception {
        Document document = DocumentHelper.createDocument();
        Element root = document.addElement(table.getXmlRootTag());
        if(table.getXmlRootAttr() != null && !table.getXmlRootAttr().trim().isEmpty()){
            root.addAttribute(table.getXmlRootAttr().split("=")[0], table.getXmlRootAttr().split("=")[1]);
        }

        for (File file : xmlFiles) {
            Document doc = DocumentHelper.parseText(FileUtil.readString(file, StandardCharsets.UTF_16));
            Element childRoot = doc.getRootElement();
            for (Iterator<?> it = childRoot.elementIterator(); it.hasNext(); ) {
                Object obj = it.next();
                if (obj instanceof Element) {
                    Element element = (Element) obj;
                    // 先从原来的 Document 移除
                    element.detach();
                    // 然后添加到新的 Document
                    root.add(element);
                }
            }
        }
        String exportFileName = StringUtils.hasLength(table.getRealTableName()) ? table.getRealTableName() : table.getTableName();
        String xmlFile = YamlUtils.getProperty("file.exportDataPath") + File.separator + exportFileName + ".xml";
        saveFormatXml(document, xmlFile);
        if(table.getFilePath().contains("AionMap")){
            XmlStringModifier.insertStringAfterFirstLine(xmlFile);
        }
    }


    public double getProgress() {
        return (double) counterUtil.getCount() / total;
    }
    // 控制异步深度，超过后改为同步
    private static final int MAX_ASYNC_DEPTH = 2;
    private static void parseSubquery(
            Element element,
            ColumnMapping columnMapping,
            JdbcTemplate jdbcTemplate,
            String id,
            int depth
    ) {
        String sql = columnMapping.getSql().replace("#associated_filed", id);
        if("world".equals(table.getTableName())){
            sql = sql.replace("$mapType", mapType);
        }

        List<Map<String, Object>> subList = jdbcTemplate.queryForList(sql);
        if(sql.contains("riding_zones")){
            log.info("sql:::::::::::::{};subList:::::::::::{}", sql, subList.size());
        }
        if (subList.isEmpty()) {
            return;
        }

        Element subEle = createElement(element, columnMapping);
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (Map<String, Object> subMap : subList) {
            Runnable task = () -> {
                try {
                    Element dataElement;
                    synchronized (subEle) {
                        dataElement = subEle.addElement(columnMapping.getXmlTag());
                    }

                    for (String subKey : subMap.keySet()) {
                        if (shouldSkipKey(subKey, columnMapping, subMap)) continue;
                        Object val = subMap.get(subKey);
                        if (val != null) {
                            synchronized (dataElement) {
                                applyAttributeOrElement(dataElement, subKey, val);
                            }
                        }
                    }

                    // 递归处理
                    if (columnMapping.getList() != null && !columnMapping.getList().isEmpty()) {
                        for (ColumnMapping subCol : columnMapping.getList()) {
                            String parentVal = getParentVal(subMap, subCol);
                            if (depth < MAX_ASYNC_DEPTH) {
                                parseSubquery(dataElement, subCol, jdbcTemplate, parentVal, depth + 1); // 异步内层继续同步
                            } else {
                                parseSubquery(dataElement, subCol, jdbcTemplate, parentVal, depth); // 超过层级：同步处理
                            }
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace(); // 捕获异常防止任务挂死
                }
            };

            if (depth < MAX_ASYNC_DEPTH) {
                //tasks.add(CompletableFuture.runAsync(task, executorService));
                CompletableFuture<Void> future = CompletableFuture
                        .runAsync(task, executorService)
                        .exceptionally(ex -> {
                            log.error("异步任务异常，id={}, columnTag={}, sql={}", id, columnMapping.getXmlTag(), columnMapping.getSql(), ex);
                            throw new CompletionException(ex); // 可选，重新抛出包装后的异常，避免任务 silently fail
                        });
                tasks.add(future);
            } else {
                task.run(); // 超过最大异步深度直接同步执行
            }
        }

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
    }


    private static boolean shouldSkipKey(String subKey, ColumnMapping columnMapping, Map<String, Object> subMap) {
        if (subKey.equals(columnMapping.getAssociatedFiled()) || columnMapping.getAssociatedFiled().contains(">" + subKey)) {
            return true;
        }
        if (worldSpecialTabNames.contains(columnMapping.getTableName()) && "world__id".equals(subKey)) {
            return true;
        }
        if ("world".equals(table.getTableName()) && "mapTp".equals(subKey)) {
            return true;
        }
        return false;
    }

    private static void applyAttributeOrElement(Element element, String key, Object value) {
        if (key.startsWith("_attr_")) {
            if (key.contains("__")) {
                String[] attrArr = key.split("__");
                element.element(attrArr[1]).addAttribute(attrArr[2], String.valueOf(value));
            } else {
                element.addAttribute(key.replace("_attr_", ""), String.valueOf(value));
            }
        } else {
            String val = String.valueOf(value);
            String[] valArr = val.split("!@#");
            for (String v : valArr) {
                element.addElement(key).setText(v);
            }
        }
    }


    private static void parseSubquery2(Element element, ColumnMapping columnMapping, JdbcTemplate jdbcTemplate, String id) {
        String sql = columnMapping.getSql().replace("#associated_filed", id);
        if("world".equals(table.getTableName())){
            sql = sql.replace("$mapType", mapType);
        }
        List<Map<String, Object>> subList = jdbcTemplate.queryForList(sql);
        if (subList.isEmpty()) {
            return;
        }
        Element subEle = createElement(element, columnMapping);

        for (Map<String, Object> subMap : subList) {
            Element dataElement = subEle.addElement(columnMapping.getXmlTag());
            Set<String> subKeySet = subMap.keySet();
            for (String subKey : subKeySet) {
                if(subKey.equals(columnMapping.getAssociatedFiled()) || columnMapping.getAssociatedFiled().contains(">" + subKey)){
                    continue;
                }
                if(worldSpecialTabNames.contains(columnMapping.getTableName()) && "world__id".equals(subKey)){
                    continue;
                }
                if("world".equals(table.getTableName()) && "mapTp".equals(subKey)){
                    continue;
                }
                if(subKey.startsWith("_attr_") && subMap.get(subKey) != null){
                    if(subKey.contains("__")){
                        String[] attrArr = subKey.split("__");
                        dataElement.element(attrArr[1]).addAttribute(attrArr[2], String.valueOf( subMap.get(subKey)));
                    }else{
                        dataElement.addAttribute(subKey.replace("_attr_", ""), String.valueOf( subMap.get(subKey)));
                    }
                }else if (subMap.get(subKey) != null) {
                    dataElement.addElement(subKey).setText(String.valueOf( subMap.get(subKey)));
                }

            }
            if (columnMapping.getList() != null && !columnMapping.getList().isEmpty()) {
                for (ColumnMapping subColumnMapping : columnMapping.getList()) {
                    String parentVal = getParentVal(subMap, subColumnMapping);
                    parseSubquery2(dataElement, subColumnMapping, jdbcTemplate, parentVal);
                }
            }

        }
    }

    private static Element createElement(Element element, ColumnMapping columnMapping) {
        String addDataNode = columnMapping.getAddDataNode();
        if(addDataNode.trim().isEmpty()){
            return element;
        }
        String[] xmlTagArr = addDataNode.split(":");
        for (String tag : xmlTagArr) {
            try {
                element = element.addElement(tag);
            }catch (Exception e){
                log.error("addDataNode {}::::::::::::tag {}" ,addDataNode, tag);
                log.error("err::::::::::::" + JSONRecord.getErrorMsg(e));
            }

        }
        //element = element.addElement(columnMapping.getXmlTag());
        return element;
    }

    public static void saveFormatXml(Document document, String filePath) throws Exception {
        // 设置格式化方式
        // 美化格式（缩进 + 换行）
        OutputFormat format = OutputFormat.createPrettyPrint();
        // 设置编码
        format.setEncoding("UTF-16");
        // 设置缩进大小（4 个空格）
        //format.setIndentSize(4);
        format.setIndent("\t");
        // 允许换行
        format.setNewlines(true);
        // **关键：避免自动去除空格**
        format.setTrimText(false);


        OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(Paths.get(filePath)), StandardCharsets.UTF_16);
        XMLWriter xmlWriter = new XMLWriter(writer, format);
        try {
            xmlWriter.write(document);
        } finally {
            xmlWriter.close();
            writer.close();
        }
    }

    private static String getParentVal(Map<String, Object> itemMap, ColumnMapping columnMapping){
        if(columnMapping.getAssociatedFiled().contains((">"))){
            String parentKey = columnMapping.getAssociatedFiled().split(">")[0];
            return itemMap.get(parentKey).toString();
        }

        return itemMap.get(columnMapping.getAssociatedFiled()).toString();
    }
}
