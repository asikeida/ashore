package me.asikeida.ashore.service.ai.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import me.asikeida.ashore.api.exception.BizException;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.resp.Status;
import me.asikeida.ashore.service.ai.AiChatClient;
import me.asikeida.ashore.service.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 兼容网关实现：按配置顺序尝试供应商，单个失败（超时/限流/网络）切下一个——
 * paicoding 多模型故障降级精髓的轻量版。全失败抛 503001。
 * <p>限流：每用户每日调用数 Redis hash 计数（超过 dailyLimit 抛 400004），
 * 成本控制——AI 调用是真金白银。</p>
 */
@Service
public class AiChatClientImpl implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(AiChatClientImpl.class);

    private static final String DAILY_KEY_PREFIX = "ashore:ai:daily:";

    private final AiProperties properties;

    private final StringRedisTemplate redisTemplate;

    private final RestClient restClient;

    /** 流式专用客户端（HTTP/2 可选；独立于 RestClient——流式需要 ofLines 逐行语义） */
    private final HttpClient streamHttpClient;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public AiChatClientImpl(AiProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory())
                .build();
        this.streamHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String chat(List<Message> messages) {
        List<AiProperties.Provider> providers = properties.getProviders().stream()
                .filter(AiProperties.Provider::isConfigured).toList();
        if (providers.isEmpty()) {
            throw ExceptionUtil.of(StatusEnum.SERVICE_DISABLED);
        }
        // 限流在供应商尝试之前：任何一家可用都不该绕过成本闸门
        checkDailyLimit();

        List<String> errors = new java.util.ArrayList<>();
        for (AiProperties.Provider provider : providers) {
            try {
                return callProvider(provider, messages);
            } catch (Exception e) {
                log.warn("AI 供应商 {} 调用失败，尝试下一个：{}", provider.getName(), e.getMessage());
                errors.add(provider.getName() + ": " + e.getMessage());
            }
        }
        log.error("全部 AI 供应商失败：{}", errors);
        throw ExceptionUtil.of(StatusEnum.SERVICE_DISABLED);
    }

    private String callProvider(AiProperties.Provider provider, List<Message> messages) {
        ChatCompletionResponse response = restClient.post()
                .uri(provider.getBaseUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", provider.getModel(), "messages", messages))
                .retrieve()
                .body(ChatCompletionResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("供应商返回空响应");
        }
        return response.choices().get(0).message().content();
    }

    @Override
    public String streamChat(List<Message> messages, Consumer<String> onDelta) {
        List<AiProperties.Provider> providers = properties.getProviders().stream()
                .filter(AiProperties.Provider::isConfigured).toList();
        if (providers.isEmpty()) {
            throw ExceptionUtil.of(StatusEnum.SERVICE_DISABLED);
        }
        checkDailyLimit();

        List<String> errors = new java.util.ArrayList<>();
        for (AiProperties.Provider provider : providers) {
            try {
                return streamProvider(provider, messages, onDelta);
            } catch (Exception e) {
                log.warn("AI 供应商 {} 流式调用失败，尝试下一个：{}", provider.getName(), e.getMessage());
                errors.add(provider.getName() + ": " + e.getMessage());
            }
        }
        log.error("全部 AI 供应商流式失败：{}", errors);
        throw ExceptionUtil.of(StatusEnum.SERVICE_DISABLED);
    }

    /**
     * 单供应商流式调用：JDK HttpClient BodyHandlers.ofLines 逐行读 SSE。
     * 建立 HTTP 连接与读取首行前的异常触发上层降级；读到流之后失败直接上抛
     * （已吐出增量无法撤回，切换会导致重复输出）。
     */
    private String streamProvider(AiProperties.Provider provider, List<Message> messages,
                                  Consumer<String> onDelta) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "model", provider.getModel(),
                "messages", messages,
                "stream", true));
        HttpRequest request = HttpRequest.newBuilder(URI.create(provider.getBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + provider.getApiKey())
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(180))
                .build();
        HttpResponse<java.util.stream.Stream<String>> response =
                streamHttpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("供应商流式响应码 " + response.statusCode());
        }

        StringBuilder full = new StringBuilder();
        AtomicReference<RuntimeException> streamFailure = new AtomicReference<>();
        response.body().forEach(line -> {
            if (!line.startsWith("data:")) {
                return;
            }
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) {
                return;
            }
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(data);
                com.fasterxml.jackson.databind.JsonNode deltaNode =
                        node.path("choices").path(0).path("delta").path("content");
                if (!deltaNode.isMissingNode() && !deltaNode.isNull()) {
                    String delta = deltaNode.asText();
                    if (!delta.isEmpty()) {
                        full.append(delta);
                        onDelta.accept(delta);
                    }
                }
            } catch (Exception e) {
                streamFailure.set(new IllegalStateException("流式分片解析失败：" + e.getMessage()));
            }
        });
        if (streamFailure.get() != null) {
            throw streamFailure.get();
        }
        if (full.length() == 0) {
            throw new IllegalStateException("供应商流式返回为空");
        }
        return full.toString();
    }

    private void checkDailyLimit() {
        String key = DAILY_KEY_PREFIX + LocalDate.now();
        Long count = redisTemplate.opsForHash().increment(key, "total", 1);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        if (count != null && count > properties.getDailyLimit()) {
            throw new BizException(Status.of(400004, "今日 AI 使用次数已达上限"));
        }
    }

    /** /chat/completions 响应（OpenAI 兼容） */
    record ChatCompletionResponse(List<Choice> choices) {
        record Choice(ReplyMessage message) {
        }

        record ReplyMessage(String content) {
        }
    }
}
