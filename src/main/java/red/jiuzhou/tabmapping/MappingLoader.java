package red.jiuzhou.tabmapping;


import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

public class MappingLoader {
    public static List<TableMapping> loadMappings() {
        ClassPathResource classPathResource = new ClassPathResource("tabMapping.json");
        String content = FileUtil.readUtf8String(classPathResource.getPath());
        return JSON.parseObject(content, new TypeReference<List<TableMapping>>() {});
    }
}
