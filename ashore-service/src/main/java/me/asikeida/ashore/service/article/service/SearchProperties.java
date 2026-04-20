package me.asikeida.ashore.service.article.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 搜索配置：enabled=false 或 ES 连接失败时自动降级 MySQL LIKE。
 */
@Component
@ConfigurationProperties(prefix = "ashore.search")
public class SearchProperties {

    /** ES 主路径开关 */
    private boolean enabled = false;

    /** ES 地址（经隧道或容器网络） */
    private String url = "http://127.0.0.1:9200";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
