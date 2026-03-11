package me.asikeida.ashore.web.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.user.service.GitHubOAuthService;
import me.asikeida.ashore.service.user.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * GitHub OAuth 端点测试（凭证走测试配置；HTTP 换 token/拉用户段以 @MockitoBean 桩替换）。
 * 覆盖：authorize 跳转与 state Cookie、回调 state 校验、成功签发会话并回首页。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:githuboauth;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.oauth.github.client-id=test-client",
        "ashore.oauth.github.client-secret=test-secret"
})
@AutoConfigureMockMvc
class GitHubOAuthControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GitHubOAuthService githubOAuthService;

    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void stub() {
        when(githubOAuthService.isConfigured()).thenReturn(true);
        when(githubOAuthService.buildAuthorizeUrl(anyString())).thenAnswer(
                inv -> "https://github.com/login/oauth/authorize?client_id=test-client&state=" + inv.getArgument(0));
        when(githubOAuthService.loginWithGitHub("good-code")).thenReturn(new LoginUser(9L, "gh999", "USER"));
        when(sessionService.issue(any())).thenReturn(new SessionService.IssuedSession("stub-token", 3600));
    }

    @Test
    void authorizeRedirectsToGithubWithStateCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/oauth/github/authorize"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("client_id=test-client&state=")))
                .andExpect(cookie().exists("ashore_oauth_state"))
                .andReturn();
        // 跳转 URL 中的 state 与 Cookie 值一致（回调时比对）
        String location = result.getResponse().getHeader("Location");
        String cookieValue = result.getResponse().getCookie("ashore_oauth_state").getValue();
        org.assertj.core.api.Assertions.assertThat(location).endsWith("state=" + cookieValue);
    }

    @Test
    void callbackRejectsStateMismatch() throws Exception {
        mvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .param("code", "good-code")
                        .param("state", "tampered")
                        .cookie(new Cookie("ashore_oauth_state", "expected-state")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400001));
    }

    @Test
    void callbackIssuesSessionAndRedirectsHome() throws Exception {
        mvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .param("code", "good-code")
                        .param("state", "expected-state")
                        .cookie(new Cookie("ashore_oauth_state", "expected-state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("ashore_session=stub-token")))
                // state Cookie 使用后即清除
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("ashore_oauth_state=;"))));
    }
}
