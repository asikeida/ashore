package me.asikeida.ashore.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.asikeida.ashore.api.context.ReqContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 请求标识过滤器测试：@AutoConfigureMockMvc 会装载容器中注册的 Filter Bean，
 * 因此这里跑的是真实过滤器链（含 MDC 写入与 ReqContext 清理）。
 */
@SpringBootTest(properties = "ashore.session.jwt-secret=review-p0-secret")
@AutoConfigureMockMvc
class RequestIdFilterTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void generatesRequestIdHeaderAndClearsContextAfterwards() throws Exception {
        MvcResult result = mvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andReturn();
        String requestId = result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).hasSize(16).matches("[0-9a-f]+");
        // 请求结束后 ThreadLocal 必须被清理，防止线程复用串号
        assertThat(ReqContext.requestId()).isNull();
    }

    @Test
    void passesThroughIncomingRequestId() throws Exception {
        mvc.perform(get("/api/ping").header(RequestIdFilter.REQUEST_ID_HEADER, "test-trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "test-trace-123"));
    }
}
