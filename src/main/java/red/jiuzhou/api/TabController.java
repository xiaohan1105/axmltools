package red.jiuzhou.api;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import red.jiuzhou.api.common.CommonResult;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.dbxml.TableNode;
import red.jiuzhou.util.*;

import java.io.File;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static red.jiuzhou.api.common.CommonResult.error;
import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @author dream
 */
@RestController
@RequestMapping("/api/tab")
public class TabController {

    private static final Logger log = LoggerFactory.getLogger(TabController.class);

    @GetMapping("/getTabInfo")
    public CommonResult<JSONRecord> getTabInfo(@RequestParam String tabName, @RequestParam(required = false) String fields,
                                               @RequestParam(required = false) Boolean allSqlFlg) {
        log.info("tabName::::::{}", tabName);
        log.info("fields::::::{}", fields);
        List<String> tabList = getTabList(tabName);
        if (tabList.isEmpty()){
            return error(1, "表或表配置不存在");
        }

        String longestPathByDepth = tabList.stream()
                .max(Comparator.comparingInt(p -> p.split(File.separator).length))
                .orElse(null);
        log.info("longestPathByDepth: " + longestPathByDepth);
        TableNode tableNode = TabConfLoad.getTableNodeByTabName(tabName, longestPathByDepth);
        JSONRecord resJson = new JSONRecord();
        while (tableNode != null) {
            String tableName = tableNode.tableName;
            log.info("tableName:::" + tableName);
            JSONRecord distinctValues = null;
            try {
                distinctValues = DatabaseUtil.getDistinctValuesWithCount(tabName, tableName.equals(tabName), fields);
            } catch (SQLException e) {
                return error(1,"获取表字段枚举异常" + e.getMessage());
            }
            resJson.getOrCreateRecord(tableName).put("ddl", DatabaseUtil.getTableDDL(tableName).replaceAll("\n", ""));
            resJson.getOrCreateRecord(tableName).put("distinctValues", distinctValues);
            resJson.getOrCreateRecord(tableName).put("insertTemplate", SqlGeneratorUtil.buildInsertFromRandomRow(tableName));
            if(allSqlFlg != null && allSqlFlg){
                resJson.getOrCreateRecord(tableName).put("updateTemplate", SqlGeneratorUtil.buildUpdateFromRandomRow(tableName));
                resJson.getOrCreateRecord(tableName).put("deleteTemplate", SqlGeneratorUtil.buildDeleteFromRandomRow(tableName));
                resJson.getOrCreateRecord(tableName).put("seleteTemplate", SqlGeneratorUtil.buildSelectByRandomPrimaryKey(tableName));

            }
            tableNode = tableNode.parent;

        }

        String rootTableName = TabConfLoad.getRootTableName(tabName, longestPathByDepth);

        log.info("rootTableName::::::{}", rootTableName);
        String confPath = YamlUtils.getProperty("file.confPath");
        List<File> files = FileUtil.loopFiles(confPath).stream()
                .filter(xmlfile -> (rootTableName + ".xml").equals(xmlfile.getName()) &&
                        xmlfile.getParent().contains("allNodeXml"))
                .collect(Collectors.toList());
        String xmlStr = FileUtil.readUtf8String(files.get(0));
        resJson.put("xml_file_struct", xmlStr.replaceAll("\\r?\\n", ""));
        resJson.put("tab_xml_path", longestPathByDepth);
        //log.info("resJson::::::{}", resJson.toString(true));
        return success(resJson);
    }

    @GetMapping("/getTabListByTabName")
    public CommonResult<List<String>> getTabListByTabName(@RequestParam String tabName) {
        return success(getTabList(tabName));
    }

    private static List<String> getTabList(String tabName) {
        String confPath = YamlUtils.getProperty("file.confPath");
        File baseDir = new File(confPath);
        List<String> fileList = FileUtil.loopFiles(baseDir).stream()
                .filter(f -> (tabName + ".json").equals(f.getName()))
                .map(f -> {
                    // 获取文件目录路径
                    String fileP = f.getAbsolutePath().replace(".json", ".xml");
                    // 去掉前缀
                    return PathUtil.restoreOriginalPath(fileP);
                })
                .distinct() // 可选：去重
                .collect(Collectors.toList());
        return fileList;
    }
}
