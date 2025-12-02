package red.jiuzhou.util;

import com.aliyun.alimt20181012.Client;
import com.aliyun.alimt20181012.models.TranslateGeneralRequest;
import com.aliyun.alimt20181012.models.TranslateGeneralResponse;
import com.aliyun.teaopenapi.models.Config;
import org.bouncycastle.util.test.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

/**
*@BelongsProject: Starring V6 Test
*@Author: yanxq
*@CreateTime: 2025/3/25 20:53
*@Description: TODO
*@Version: 1.0
*/
public class AliyunTranslateUtil {
    // 单例客户端
    private static volatile Client client;
    private static final Object LOCK = new Object();

    // 获取阿里云翻译客户端（懒加载单例模式）
    private static Client getClient() throws Exception {
        if (client == null) {
            Properties properties = loadYamlProperties("application.yml");

            // 2. 创建 DataSource
            synchronized (LOCK) {
                // 双重检查锁，确保线程安全
                if (client == null) {
                    Config config = new Config()
                            .setAccessKeyId(properties.getProperty("ALIYUN.ACCESS_KEY_ID"))
                            .setAccessKeySecret(properties.getProperty("ALIYUN.ACCESS_KEY_SECRET"));
                    config.endpoint = properties.getProperty("ALIYUN.URL");
                    client = new Client(config);
                }
            }
        }
        return client;
    }

    /**
     * 翻译英文文本为中文
     * @param text 要翻译的英文文本
     * @return 翻译后的中文文本，如果失败返回 null
     */
    public static String translate(String text) {
        if(true){
            return text;
        }
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            // 复用单例客户端
            Client client = getClient();
            TranslateGeneralRequest request = new TranslateGeneralRequest()
                    .setFormatType("text")
                    .setSourceLanguage("en")
                    .setTargetLanguage("zh")
                    // 替换下划线为空格
                    .setSourceText(text.replace("_", " "))
                    .setScene("general");

            TranslateGeneralResponse response = client.translateGeneral(request);
            return response.body.data.translated;
        } catch (Exception e) {
            System.err.println("翻译失败：" + e.getMessage());
            return null;
        }
    }

    private static Properties loadYamlProperties(String yamlFile) {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ClassPathResource(yamlFile));
        return yamlFactory.getObject();
    }

    public static void main(String[] args) {
        String result = AliyunTranslateUtil.translate("move_speed_combat_run");
        System.out.println("翻译结果：" + result);
    }
}
