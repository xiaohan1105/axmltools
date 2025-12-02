package red.jiuzhou.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import red.jiuzhou.util.YamlUtils;

import java.util.Arrays;
import java.util.List;

/**
 * DeepSeek 客户端 - 实现 AiModelClient 接口，基于 DashScope SDK v2.18.2+
 */
@Slf4j
public class DeepSeekClient implements AiModelClient {

    private static final String MODEL = YamlUtils.getProperty("ai.deepseek.model");
    private static final String API_KEY = YamlUtils.getProperty("ai.deepseek.apikey");

    @Override
    public String chat(String prompt) throws RuntimeException {
        if (API_KEY == null || API_KEY.isEmpty()) {
            throw new RuntimeException("未配置 DeepSeek 模型的 API KEY，请检查配置项 ai.deepseek.apikey");
        }
        log.info("向模型发送消息：{}", prompt);
        try {
            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt)
                    .build();
            GenerationParam param = GenerationParam.builder()
                    .apiKey(API_KEY)
                    .model(MODEL)
                    .messages(Arrays.asList(userMsg))
                    // 不可以设置为"text"
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .enableThinking(false)
                    .build();
            Generation generation = new Generation();

            String content = generation.call(param).getOutput().getChoices().get(0).getMessage().getContent();
            log.info("模型返回结果：{}", content);
            return content;
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("调用 DeepSeek 模型出错：{}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}