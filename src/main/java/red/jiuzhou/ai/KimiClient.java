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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.YamlUtils;

import java.util.Arrays;

@Slf4j
/**
 * Moonshot-Kimi-K2-Instruct 模型客户端，支持流式输出
 */
public class KimiClient implements AiModelClient {
    private static final String MODEL = YamlUtils.getProperty("ai.kimi.model");
    private static final String API_KEY = YamlUtils.getProperty("ai.kimi.apikey");

    @Override
    public String chat(String prompt) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            throw new RuntimeException("未设置环境变量 DASHSCOPE_API_KEY，请设置你的阿里云 API Key。");
        }
        log.info("向模型发送消息：{}", prompt);
        try {
            Generation gen = new Generation();
            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt)
                    .build();
            GenerationParam param = GenerationParam.builder()
                    .apiKey(API_KEY)
                    .model(MODEL)
                    .messages(Arrays.asList(userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .enableThinking(false)
                    .build();
            String content = gen.call(param).getOutput().getChoices().get(0).getMessage().getContent();
            log.info("模型返回：{}", content);
            return content;
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("调用 Kimi 模型出错：{}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

}