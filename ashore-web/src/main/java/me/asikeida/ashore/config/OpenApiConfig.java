package me.asikeida.ashore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档元信息：Swagger UI（/swagger-ui/index.html）与 /v3/api-docs 共用。
 * 接口本身的描述用注解写在各 Controller 上，不在此集中维护。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Ashore API")
                .description("上岸 Ashore —— 面经分享与 AI 模拟面试社区")
                .version("0.0.1-SNAPSHOT"));
    }
}
