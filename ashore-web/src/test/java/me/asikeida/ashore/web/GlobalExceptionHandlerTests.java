package me.asikeida.ashore.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.resp.StatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局异常处理测试：验证各异常路径的 HTTP 状态码与响应体形态（standalone 模式，不依赖完整上下文）。
 */
class GlobalExceptionHandlerTests {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DemoController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void bizExceptionUnauthorizedMapsTo401() throws Exception {
        mvc.perform(get("/demo/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(401001))
                .andExpect(jsonPath("$.status.msg").value("未登录或登录已失效"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void bizExceptionNotFoundMapsTo404() throws Exception {
        mvc.perform(get("/demo/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status.code").value(404001))
                .andExpect(jsonPath("$.status.msg").value("资源不存在"));
    }

    @Test
    void bodyValidationFailureReturnsFieldViolations() throws Exception {
        mvc.perform(post("/demo/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400001))
                .andExpect(jsonPath("$.result[0].field").value("title"));
    }

    @Test
    void unreadableBodyReturnsBadRequest() throws Exception {
        mvc.perform(post("/demo/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400001));
    }

    @Test
    void noResourceFoundMapsTo404() throws Exception {
        mvc.perform(get("/demo/static-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status.code").value(404001));
    }

    @Test
    void methodNotSupportedMapsTo405() throws Exception {
        mvc.perform(post("/demo/unauthorized"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status.code").value(405001))
                .andExpect(jsonPath("$.status.msg").value("不支持的请求方法"));
    }

    @Test
    void unexpectedExceptionReturnsGenericMessageWithoutLeakingDetail() throws Exception {
        mvc.perform(get("/demo/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status.code").value(500000))
                .andExpect(jsonPath("$.status.msg").value("服务异常，请稍后重试"))
                // 内部细节不得出现在响应中
                .andExpect(jsonPath("$.status.msg").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-detail"))));
    }

    /** 仅测试用的演示控制器，模拟各类异常路径 */
    @RestController
    static class DemoController {

        record ArticleReq(@NotBlank(message = "标题不能为空") String title) {
        }

        @GetMapping("/demo/unauthorized")
        public ResVo<Void> unauthorized() {
            throw ExceptionUtil.of(StatusEnum.UNAUTHORIZED);
        }

        @GetMapping("/demo/missing")
        public ResVo<Void> missing() {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }

        @PostMapping("/demo/article")
        public ResVo<String> create(@Valid @RequestBody ArticleReq req) {
            return ResVo.ok("created");
        }

        @GetMapping("/demo/static-missing")
        public ResVo<Void> staticMissing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "static/path.png");
        }

        @GetMapping("/demo/boom")
        public ResVo<Void> boom() {
            throw new IllegalStateException("secret-detail should never leak");
        }
    }
}
