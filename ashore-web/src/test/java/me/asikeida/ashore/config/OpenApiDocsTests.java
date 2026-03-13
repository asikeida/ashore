package me.asikeida.ashore.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OpenAPI 文档与健康检查装配测试。
 * /v3/api-docs 是 Apifox 接口同步的数据源（.agents/rules/07），必须稳定可用。
 * 健康明细断言只验证组件存在（测试环境无 Redis，redis 组件应为 DOWN，状态不在此断言）。
 */
@SpringBootTest(properties = "ashore.session.jwt-secret=review-p0-secret")
@AutoConfigureMockMvc
class OpenApiDocsTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiDocsExposePingPath() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/ping']").exists())
                .andExpect(jsonPath("$.info.title").value("Ashore API"));
    }

    @Test
    void healthExposesDbAndRedisComponents() throws Exception {
        // 测试环境无 Redis（6379 无服务）：redis 组件 DOWN 使聚合状态 DOWN（HTTP 503）。
        // 断言组件明细存在与 redis 确为 DOWN；真实连通性在远端启动验证中覆盖。
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components.db").exists())
                .andExpect(jsonPath("$.components.redis").exists())
                .andExpect(jsonPath("$.components.redis.status").value("DOWN"));
    }
}
