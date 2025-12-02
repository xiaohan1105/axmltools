package red.jiuzhou.ai;

public interface AiModelClient {
    /**
     * 向大模型发送对话输入并返回输出
     */
    String chat(String prompt) throws RuntimeException;
}