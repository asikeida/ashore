package me.asikeida.ashore.service.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 供应商配置：列表顺序即降级顺序；apiKey 为空的供应商视为未启用。
 */
@Component
@ConfigurationProperties(prefix = "ashore.ai")
public class AiProperties {

    /** 每用户每日调用上限（成本控制，Redis hash 按天计数） */
    private int dailyLimit = 20;

    private List<Provider> providers = new ArrayList<>();

    public List<Provider> getProviders() {
        return providers;
    }

    public void setProviders(List<Provider> providers) {
        this.providers = providers;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /** 已配置（apiKey 非空）的供应商数量 */
    public long configuredCount() {
        return providers.stream().filter(Provider::isConfigured).count();
    }

    public static class Provider {

        private String name;

        /** OpenAI 兼容 baseUrl（不含 /chat/completions） */
        private String baseUrl;

        /** API key；空 = 未启用 */
        private String apiKey = "";

        private String model;

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
