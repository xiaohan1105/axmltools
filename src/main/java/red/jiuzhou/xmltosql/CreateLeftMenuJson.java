package red.jiuzhou.xmltosql;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class MenuNode {
    private String name;
    private String path;
    private List<MenuNode> children = new ArrayList<>();

    public MenuNode(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public void addChild(MenuNode child) {
        this.children.add(child);
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    @JSONField(serialize = false) // 该方法不会被序列化
    public boolean isChildrenEmpty() {
        return children.isEmpty();
    }

    @JSONField(serialize = true) // 仅当 children 非空时才序列化
    public List<MenuNode> getChildren() {
        return isChildrenEmpty() ? null : children;
    }
}

public class CreateLeftMenuJson {
    public static MenuNode directoryToJson(File dir) {
        // 目录节点
        MenuNode node = new MenuNode(dir.getName(), dir.getPath());
        File[] files = dir.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 递归添加子目录
                    MenuNode cNode = directoryToJson(file);
                    if(cNode != null){
                        node.addChild(cNode);
                    }

                } else {
                    if(file.getName().endsWith(".json")){
                        // 去除后缀
                        String fileNameWithoutExt = StrUtil.subBefore(file.getName(), ".", true);
                        JSONRecord confJson = new JSONRecord(FileUtil.readUtf8String(file.getAbsolutePath()));
                        String tabName = confJson.getValue("table_name");
                        if(StringUtils.hasLength(tabName)){
                            // 添加文件节点
                            node.addChild(new MenuNode(tabName, file.getAbsolutePath()));
                        }

                    }

                }
            }
        }
        if(node.getChildren()== null || node.getChildren().isEmpty()){
            return null;
        }
        return node;
    }

    public static String createJson() {
        // 替换为你的目录路径
        String confPath = YamlUtils.getProperty("file.confPath");
        File rootDir = new File(confPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.out.println("目录不存在或不是一个有效的目录");
            return "{}";
        }

        MenuNode jsonTree = directoryToJson(rootDir);
        
        // 使用 FastJSON 生成 JSON 格式输出
        String jsonOutput = JSON.toJSONString(jsonTree, true);
        String homePath = YamlUtils.getProperty("file.homePath");
        FileUtil.writeUtf8String(jsonOutput, homePath + "LeftMenu.json");
        return jsonOutput;
    }
}
