package me.asikeida.ashore.service.ai.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    public AiChatClientImpl(AiProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory())
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
