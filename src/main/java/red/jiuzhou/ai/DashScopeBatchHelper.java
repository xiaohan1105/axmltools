package red.jiuzhou.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.YamlUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
@Slf4j
public class DashScopeBatchHelper {

    private static final int BATCH_SIZE = 100;
    private static final int THREAD_COUNT = 10;
    private static final int MAX_RETRY = 3;
    private static final String DELIMITER = "!@#";

    // AI 调用缓存，key 为 prompt 内容，value 为响应结果
    private static final ConcurrentHashMap<String, String> aiResponseCache = new ConcurrentHashMap<>();

    public static String buildBatchPrompt(List<String> inputs, String promptKey) {
        String property = Optional.ofNullable(YamlUtils.getProperty("ai.promptKey." + promptKey)).orElse("");
        return property + "：\n" + String.join(DELIMITER, inputs);
    }

    public static List<String> parseBatchResult(String response, int expectedCount) {
        String[] parts = response.split(Pattern.quote(DELIMITER));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            result.add(i < parts.length ? parts[i].trim() : "");
        }
        return result;
    }

    public static void rewriteField(List<Map<String, String>> dataList, String tabName, String fieldName, String aiModelName) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int start = 0; start < dataList.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, dataList.size());
            List<Map<String, String>> subList = dataList.subList(start, end);

            int finalStart = start;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    List<String> originalTexts = subList.stream()
                            .map(m -> m.getOrDefault(fieldName, ""))
                            .collect(Collectors.toList());

                    String promptKey = tabName + "@" + fieldName;
                    String prompt = buildBatchPrompt(originalTexts, promptKey);

                    // 尝试使用缓存
                    String response = aiResponseCache.computeIfAbsent(prompt, p -> {
                        for (int i = 1; i <= MAX_RETRY; i++) {
                            try {
                                long startTime = System.currentTimeMillis();
                                AiModelClient client = AiModelFactory.getClient(aiModelName);
                                String res = client.chat(p);
                                log.info("调用ai耗时 {}ms", System.currentTimeMillis() - startTime);
                                if (res != null && res.length() > 0) {
                                    return res;
                                } else {
                                    log.error("第 {} 次调用 AI 返回为空，重试中...", i);
                                }
                            } catch (Exception ex) {
                                log.error("第 {} 次调用 AI 错误：{}", i, ex.getMessage());
                                log.error("批次 {} ~ {} 批次异常：{}", finalStart, end - 1, JSONRecord.getErrorMsg(ex));
                            }
                            try {
                                Thread.sleep(1000L * i); // 增加退避时间
                            } catch (InterruptedException ignored) {}
                        }

                        log.error("多次调用 AI 失败，prompt 被跳过：{}", p);
                        return ""; // 最终失败也要写缓存，避免再次触发
                    });

                    if (!StringUtils.hasLength(response)) {
                        log.error("AI 批次最终返回为空，跳过批次：{} ~ {}", finalStart, end - 1);
                        return;
                    }

                    List<String> newTexts = parseBatchResult(response, subList.size());
                    for (int i = 0; i < subList.size(); i++) {
                        subList.get(i).put(fieldName, newTexts.get(i));
                    }

                } catch (Exception e) {
                    log.error("处理批次 {} ~ {} 批次异常：{}", finalStart, end - 1, e.getMessage());
                    log.error("批次 {} ~ {} 批次异常：{}", finalStart, end - 1, JSONRecord.getErrorMsg(e));
                }
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
    }
}