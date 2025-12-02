package red.jiuzhou.xmltosql;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.MultiXMLMerger;
import red.jiuzhou.util.PathUtil;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;



/**
 * @className: red.jiuzhou.xmltosql.XmlProcess
 * @description: XML文件处理的主流程控制类。
 *               <p>
 *               该类负责编排整个从XML文件到MySQL建表语句的转换过程。它从配置文件中读取
 *               数据目录和配置输出目录，然后扫描指定的目录以查找所有XML文件。
 *               <p>
 *               主要工作流程包括：
 *               <ul>
 *                   <li><b>初始化 (init):</b> 清理旧的配置文件，扫描数据目录，识别出同名XML文件，并对每个文件执行解析。</li>
 *                   <li><b>文件解析 (parseXml / parseOneXml):</b> 对单个XML文件执行一系列处理步骤：
 *                       <ol>
 *                           <li>使用 {@link XmlAllNode} 生成一个包含所有唯一节点的“全节点”XML。</li>
 *                           <li>使用 {@link XmlFieldLen} 分析字段的最大长度。</li>
 *                           <li>使用 {@link XMLToConf} 根据XML结构生成JSON格式的表映射配置。</li>
 *                           <li>使用 {@link XMLToMySQLGenerator} 生成最终的MySQL DDL（CREATE TABLE）语句。</li>
 *                           <li>将生成的中间文件和最终的SQL文件写入到配置目录中。</li>
 *                       </ol>
 *                   </li>
 *                   <li><b>进度跟踪:</b> 记录已处理的文件数和总文件数，以计算处理进度。</li>
 *                   <li><b>同名文件处理:</b> 当在不同目录下发现同名XML文件时，会根据其路径缩写生成唯一的新表名，以避免冲突。</li>
 *               </ul>
 * @author: yanxq
 * @date:  2025/03/24 22:04
 * @version V1.0
 */
public class XmlProcess {
    private static int totNum = 0;
    private static int processed = 0;
    public static String msg;
    static Map<String, List<String>> duplicateFiles = new HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(XmlProcess.class);
    static JSONRecord filedValNumJson = new JSONRecord();

    /**
     * 对 SVR_DATA 目录下的所有 `worlds/world.xml` 文件进行特殊的初始化处理。
     * <p>
     * 这个方法专门用于处理所有 `world.xml` 文件，它会将所有找到的 `world.xml`
     * 合并成一个单一的、包含所有节点的全节点XML，然后基于这个合并后的文件生成
     * 数据库建表语句。
     */
    @Test
    public void initSvrWorlds() {
        String confPath = YamlUtils.getProperty("file.confPath");
        String svrDataPath = YamlUtils.getProperty("file.svrDataPath");
        if (svrDataPath == null) {
            throw new RuntimeException("配置文件中未找到file.svrDataPath");
        }

        List<File> files = FileUtil.loopFiles(svrDataPath).stream()
                .filter(xmlfile -> "world.xml".equals(xmlfile.getName()) &&
                        xmlfile.getParent().toLowerCase().contains("worlds"))
                .collect(Collectors.toList());
        //获取文件路径
        String fPath = confPath +  FileUtil.getParent(files.get(0).getParent(), 1).replace(FileUtil.getParent(svrDataPath, 1), "");
        String allNodeXmlFileName = File.separator + "allNodeXml" + File.separator + "world.xml";
        MultiXMLMerger.merger(files, fPath + allNodeXmlFileName);

        //String tabConf = XMLToConf.generateMySQLTables(FileUtil.getParent(files.get(0).getParent(), 1) + File.separator + "world.xml",
        //        FileUtil.readUtf8String(fPath + allNodeXmlFileName), null);
        //FileUtil.writeUtf8String(tabConf, fPath + File.separator + "world.json");
        JSONRecord filedLenJson = XmlFieldLen.getFiledLenJson(files);

        String sql = XMLToMySQLGenerator.generateMysqlTables("world", FileUtil.readUtf8String(fPath + allNodeXmlFileName), filedLenJson, "");
        FileUtil.writeUtf8String(sql, fPath + File.separator + "sql" + File.separator + "world.sql");

        //XmlFiledValNum.getFiledLenJson(filedValNumJson, filePath, newFileName);
        //log.info("文件处理完成：{}", filePath);
    }

    /**
     * 解析单个XML文件，并生成所有相关的配置文件和SQL脚本。
     * <p>
     * 此方法适用于从UI或其他外部调用触发的、对单个文件的处理。
     *
     * @param filePath 要处理的XML文件的绝对路径。
     * @return 生成的SQL文件的绝对路径。
     */
    public static String parseOneXml(String filePath){
        // 验证输入参数
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new RuntimeException("文件路径不能为空");
        }

        String cltDataPath = YamlUtils.getProperty("file.cltDataPath");
        String svrDataPath = YamlUtils.getProperty("file.svrDataPath");
        String confPath = YamlUtils.getProperty("file.confPath");

        // 验证配置项
        if (confPath == null || confPath.trim().isEmpty()) {
            throw new RuntimeException("配置项 file.confPath 未设置或为空，请检查 application.yml 配置文件");
        }

        // 计算配置文件路径
        String fPath;
        if(cltDataPath != null && filePath.contains(cltDataPath)){
            // 标准客户端数据路径
            String dataFilePath = cltDataPath;
            fPath = confPath + FileUtil.getParent(filePath, 1).replace(FileUtil.getParent(dataFilePath, 1), "");
        }else if (svrDataPath != null && filePath.contains(svrDataPath)){
            // 标准服务端数据路径
            String dataFilePath = svrDataPath;
            fPath = confPath + FileUtil.getParent(filePath, 1).replace(FileUtil.getParent(dataFilePath, 1), "");
        }else{
            // 其他路径，使用 PathUtil.getConfPath 处理任意绝对路径
            log.info("文件不在标准数据目录中，使用通用路径处理: {}", filePath);
            fPath = PathUtil.getConfPath(FileUtil.getParent(filePath, 1));
        }

        String fileName = FileUtil.getName(filePath).split("\\.")[0];
        log.info("开始处理文件：{}", filePath);
        log.info("计算的配置路径 fPath: {}", fPath);

        // 验证必要的参数
        if (fPath == null || fPath.trim().isEmpty()) {
            throw new RuntimeException("配置路径 fPath 为空，无法继续处理文件: " + filePath);
        }

        String allNodeXmlStr = XmlAllNode.getAllNodeXmlStr(filePath);
        if (allNodeXmlStr == null) {
            throw new RuntimeException("无法从文件中提取 XML 节点信息: " + filePath);
        }

        // 确保目录存在
        String allNodeXmlDir = fPath + File.separator + "allNodeXml";
        FileUtil.mkdir(allNodeXmlDir);
        FileUtil.writeUtf8String(allNodeXmlStr, allNodeXmlDir + File.separator + FileUtil.getName(filePath));

        JSONRecord filedLenJson = XmlFieldLen.getFiledLenJson(filePath);

        String tabConf = XMLToConf.generateMySQLTables(filePath, allNodeXmlStr, null);
        if (tabConf == null) {
            throw new RuntimeException("生成表配置失败: " + filePath);
        }
        FileUtil.writeUtf8String(tabConf, fPath + File.separator + fileName + ".json");

        String sql = XMLToMySQLGenerator.generateMysqlTables(fileName, allNodeXmlStr, filedLenJson, null);
        if (sql == null || sql.trim().isEmpty()) {
            throw new RuntimeException("生成 SQL 失败（SQL为空）: " + filePath);
        }
        String sqlDir = fPath + File.separator + "sql";
        FileUtil.mkdir(sqlDir);
        String sqlFilePath = sqlDir + File.separator + fileName + ".sql";
        FileUtil.writeUtf8String(sql, sqlFilePath);

        XmlFiledValNum.getFiledLenJson(filedValNumJson, filePath, null);
        //重新生成菜单
        CreateLeftMenuJson.createJson();
        log.info("文件处理完成：{}", filePath);
        return sqlFilePath;
    }

    /**
     * 内部使用的文件解析方法，与 `parseOneXml` 功能类似，但路径处理方式不同。
     */
    public static String parseXmlFile(String filePath){
        String fileName = FileUtil.getName(filePath).split("\\.")[0];
        log.info("开始处理文件：{}", filePath);
        String allNodeXmlStr = XmlAllNode.getAllNodeXmlStr(filePath);
        //获取文件路径
        String fPath = PathUtil.getConfPath(FileUtil.getParent(filePath, 1));

        FileUtil.writeUtf8String(allNodeXmlStr, fPath + File.separator + "allNodeXml" + File.separator + FileUtil.getName(filePath));

        JSONRecord filedLenJson = XmlFieldLen.getFiledLenJson(filePath);

        String tabConf = XMLToConf.generateMySQLTables(filePath, allNodeXmlStr, null);
        FileUtil.writeUtf8String(tabConf, fPath + File.separator + fileName + ".json");

        String sql = XMLToMySQLGenerator.generateMysqlTables(fileName, allNodeXmlStr, filedLenJson, null);
        String sqlFilePath = fPath + File.separator + "sql" + File.separator + fileName + ".sql";

        FileUtil.writeUtf8String(sql, sqlFilePath);

        XmlFiledValNum.getFiledLenJson(filedValNumJson, filePath, null);
        log.info("文件处理完成：{}", filePath);
        return sqlFilePath;
    }
    /**
     * 初始化整个XML处理流程。
     * <p>
     * 该方法会清空旧的配置目录，扫描所有指定的数据源路径，识别出需要处理的XML文件，
     * 特别是那些在不同数据源中重名的文件，然后对它们进行逐一处理，并最终将所有
     * 生成的SQL脚本合并成一个 `all.sql` 文件。
     */
    public static void init() {

        Map<String, List<String>> fileMap = new HashMap<>();

        String cltDataPath = YamlUtils.getProperty("file.cltDataPath");
        String svrDataPath = YamlUtils.getProperty("file.svrDataPath");
        if (cltDataPath == null || svrDataPath == null) {
            throw new RuntimeException("配置文件中未找到file.cltDataPath或file.svrDataPath");
        }
        String confPath = YamlUtils.getProperty("file.confPath");
        //清理历史文件
        FileUtil.del(confPath);

        List<String> dataPath = new ArrayList<String>() {{
            add(cltDataPath);
            add(svrDataPath);
        }};

        dataPath.forEach(subDatapath -> {
            List<File> files = FileUtil.loopFiles(subDatapath).stream()
                    .filter(xmlfile -> xmlfile.getName().endsWith(".xml")
                            && !xmlfile.getParent().toLowerCase().contains("worlds"))
                    .collect(Collectors.toList());
            totNum += files.size();
            files.forEach(file -> {
                fileMap.computeIfAbsent(file.getName(), k -> new ArrayList<>()).add(file.getAbsolutePath());
            });
        });

        // 生成包含同名文件的新 Map
        fileMap.forEach((fileName, paths) -> {
            if (paths.size() > 1) { // 只保留在多个路径下都存在的文件
                duplicateFiles.put(fileName.split("\\.")[0], paths);
            }
        });

        dataPath.forEach(XmlProcess::parseXml);
        //initSvrWorlds();
        CreateLeftMenuJson.createJson();

        FileUtil.del(confPath + File.separator + "all.sql");
        FileUtil.loopFiles(confPath).forEach(file -> {
            if (file.getName().endsWith(".sql")) {
                String sql = FileUtil.readUtf8String(file.getAbsolutePath());
                FileUtil.appendUtf8String(sql, confPath + File.separator + "all.sql");
            }
        });

        FileUtil.writeUtf8String(JSON.toJSONString(filedValNumJson, SerializerFeature.PrettyFormat), confPath + File.separator + "XmlFileValNum.json");
    }

    /**
     * 对指定数据目录下的所有XML文件进行批量解析。
     *
     * @param dataFilePath 包含XML文件的数据目录路径。
     */
    public static void parseXml(String dataFilePath) {
        String confPath = YamlUtils.getProperty("file.confPath");
        List<File> files = FileUtil.loopFiles(dataFilePath).stream()
                .filter(xmlfile -> xmlfile.getName().endsWith(".xml") &&
                        !xmlfile.getParent().toLowerCase().contains("worlds"))
                .collect(Collectors.toList());
        files.forEach(xFile -> {
            String filePath = xFile.getAbsolutePath();
            String fileName = FileUtil.getName(filePath).split("\\.")[0];
            String newFileName = fileName;
            log.info("开始处理文件：{}", filePath);
            msg = "正在处理文件：" + filePath;
            processed++;
            String allNodeXmlStr = XmlAllNode.getAllNodeXmlStr(filePath);

            //获取文件路径
            String fPath = confPath + xFile.getParent().replace(FileUtil.getParent(dataFilePath, 1), "");

            FileUtil.writeUtf8String(allNodeXmlStr, fPath + File.separator + "allNodeXml" + File.separator + FileUtil.getName(filePath));

            JSONRecord filedLenJson = XmlFieldLen.getFiledLenJson(filePath);

            if(duplicateFiles.containsKey(fileName)){
                List<String> filePathList = duplicateFiles.get(fileName);
                if(filePathList.size() > 1){
                    filePathList.remove(filePath);
                    duplicateFiles.put(fileName, filePathList);
                    newFileName = getPathAbbreviation( xFile.getParent().replace(FileUtil.getParent(dataFilePath, 1), "")) + "_" + fileName;
                }
            }
            String tabConf = XMLToConf.generateMySQLTables(filePath, allNodeXmlStr, newFileName);
            FileUtil.writeUtf8String(tabConf, fPath + File.separator + fileName + ".json");

            String sql = XMLToMySQLGenerator.generateMysqlTables(fileName, allNodeXmlStr, filedLenJson, newFileName);
            FileUtil.writeUtf8String(sql, fPath + File.separator + "sql" + File.separator + fileName + ".sql");

            XmlFiledValNum.getFiledLenJson(filedValNumJson, filePath, newFileName);
            log.info("文件处理完成：{}", filePath);

        });

    }

    /**
     * 根据文件路径生成一个缩写字符串，通常用于为同名文件创建唯一标识。
     * <p>
     * 例如，路径 "a/b/c" 会被转换为 "abc"。
     *
     * @param path 文件路径字符串。
     * @return 由路径各部分首字母组成的缩写字符串。
     */
    public static String getPathAbbreviation(String path) {
        // 统一使用 File.separator 进行拆分（兼容 Windows 和 Linux）
        String separator = File.separator.equals("\\") ? "\\\\" : File.separator;
        return Arrays.stream(path.split(separator))
                .filter(segment -> !segment.isEmpty())
                .map(segment -> String.valueOf(segment.charAt(0)))
                .collect(Collectors.joining());
    }

    /**
     * 获取当前文件处理的进度。
     *
     * @return 一个表示进度的浮点数（0.0 到 1.0）。
     */
    public static double getProgress() {
        return (double) processed / totNum;
    }

}