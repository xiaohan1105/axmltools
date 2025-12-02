package red.jiuzhou.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.YamlUtils;

import java.time.Duration;
import java.util.Arrays;
/**
 * @author dream
 */
@Slf4j
public class TongYiClient implements AiModelClient {

    private static final String MODEL = YamlUtils.getProperty("ai.qwen.model");
    private static final String API_KEY = YamlUtils.getProperty("ai.qwen.apikey");

    @Override
    public String chat(String prompt) {
        log.info("向模型发送消息：{}", prompt);
        if (API_KEY == null || API_KEY.isEmpty()) {
            throw new RuntimeException("请设置环境变量 ai.qwen.apikey");
        }

        try {
            Generation gen = new Generation();
            Message systemMsg = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content("You are a helpful assistant.")
                    .build();
            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt)
                    .build();
            GenerationParam param = GenerationParam.builder()
                    .apiKey(API_KEY)
                    .model(MODEL)
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .enableThinking(false)
                    .build();

            GenerationResult result = gen.call(param);

            //log.info("模型返回的完整结果：{}", result);
            // 提取回答内容
            if (result.getOutput() != null && result.getOutput().getChoices() != null && !result.getOutput().getChoices().isEmpty()) {
                String content = result.getOutput().getChoices().get(0).getMessage().getContent();
                if(!StringUtils.hasLength(content)){
                    log.error("模型处理异常:::::::::{}", result.toString());
                }
                log.info("模型返回的回答：{}", content);
                return content;
            } else {
                log.error("模型处理异常::::::::{}", result.toString());
                return "";
            }
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("调用模型出错：{}", e.getMessage());
            throw new RuntimeException(e);
        }
    }


}