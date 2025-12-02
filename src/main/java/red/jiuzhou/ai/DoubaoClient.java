package red.jiuzhou.ai;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionContentPart;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import red.jiuzhou.util.YamlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DoubaoClient implements AiModelClient {

    private static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final String MODEL_ID = YamlUtils.getProperty("ai.doubao.model");

    private static ArkService service;

    private static synchronized void initService() {
        if (service == null) {
            String apiKey = YamlUtils.getProperty("ai.doubao.apikey");
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("❌ 请配置 ai.doubao.apikey 的值（豆包 API_KEY）");
            }
            service = ArkService.builder()
                    .dispatcher(new Dispatcher())
                    .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                    .baseUrl(BASE_URL)
                    .apiKey(apiKey)
                    .build();
        }
    }

    @Override
    public String chat(String prompt) {
        log.info("开始调用豆包模型，输入：{}", prompt);
        try {
            initService(); // 延迟初始化，确保 API_KEY 读取成功

            List<ChatMessage> messages = new ArrayList<>();
            List<ChatCompletionContentPart> parts = new ArrayList<>();
            parts.add(ChatCompletionContentPart.builder().type("text").text(prompt).build());

            messages.add(ChatMessage.builder().role(ChatMessageRole.USER).multiContent(parts).build());

            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                    .model(MODEL_ID)
                    .messages(messages)
                    .thinking(new ChatCompletionRequest.ChatCompletionRequestThinking("disabled"))
                    .build();

            StringBuilder responseBuilder = new StringBuilder();
            service.createChatCompletion(chatCompletionRequest)
                    .getChoices()
                    .forEach(choice -> {
                        String content = (String) choice.getMessage().getContent();
                        log.info("豆包模型返回内容：{}", content);
                        responseBuilder.append(content).append("\n");
                    });
            log.info("豆包模型返回结果：{}", responseBuilder.toString());
            return responseBuilder.toString().trim();

        } catch (Exception e) {
            log.error("豆包模型调用失败", e);
            throw new RuntimeException(e);
        }
    }
}