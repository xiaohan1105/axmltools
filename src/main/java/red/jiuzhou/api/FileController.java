package red.jiuzhou.api;

import cn.hutool.core.io.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import red.jiuzhou.api.common.CommonResult;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.dbxml.TableNode;
import red.jiuzhou.util.*;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static red.jiuzhou.api.common.CommonResult.error;
import static red.jiuzhou.api.common.CommonResult.success;

/**
 * @author dream
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @GetMapping("/getFileList")
    public CommonResult<List<String>> getTabInfo(@RequestParam(required = false) String filePath) {
        List<String> fileList;
        if(!StringUtils.hasLength(filePath)){
            fileList = new ArrayList<>();
            String xmlPath = YamlUtils.getProperty("xmlPath." + DatabaseUtil.getDbName());
            String xmlPathStr = xmlPath ==  null ? "" : xmlPath;
            if(StringUtils.hasLength(xmlPathStr)){
                List<String> list = Arrays.asList(xmlPathStr.split(","));

                list.forEach(path -> {
                    List<String> fList = FileUtil.loopFiles(path).stream().filter(file -> file.getName().endsWith(".xml")).map(File::getAbsolutePath).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                    fileList.addAll(fList);
                });
            }
        }else{
            if(!FileUtil.exist(filePath)){
                return error(1,"目录不存在" + filePath);
            }
            fileList = FileUtil.loopFiles(filePath).stream().filter(file -> file.getName().endsWith(".xml")).map(File::getAbsolutePath).sorted(Comparator.reverseOrder()).collect(Collectors.toList());

        }

        return success(fileList);
    }

    @GetMapping("/getSimpleFileList")
    public CommonResult<List<String>> getSimpleFileList(@RequestParam(required = false) String filePath) {
        List<String> fileList;
        if(!StringUtils.hasLength(filePath)){
            fileList = new ArrayList<>();
            String xmlPath = YamlUtils.getProperty("xmlPath." + DatabaseUtil.getDbName());
            String xmlPathStr = xmlPath ==  null ? "" : xmlPath;
            if(StringUtils.hasLength(xmlPathStr)){
                List<String> list = Arrays.asList(xmlPathStr.split(","));

                list.forEach(path -> {
                    List<String> fList = FileUtil.loopFiles(path).stream().map(File::getName).filter(name -> name.endsWith(".xml")).distinct() .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                    fileList.addAll(fList);
                });
            }
        }else{
            if(!FileUtil.exist(filePath)){
                return error(1,"目录不存在" + filePath);
            }
            fileList = FileUtil.loopFiles(filePath).stream().map(File::getName).filter(name -> name.endsWith(".xml")).distinct() .sorted(Comparator.reverseOrder()).collect(Collectors.toList());

        }

        return success(fileList);
    }
}
