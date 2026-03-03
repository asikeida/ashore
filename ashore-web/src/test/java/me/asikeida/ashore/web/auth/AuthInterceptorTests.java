package me.asikeida.ashore.web.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.user.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 鉴权拦截器测试（@MockitoBean 桩替换 SessionService，聚焦拦截器判定逻辑）：
 * 未登录 401 / 会话吊销 401 / LOGIN 通过 / ADMIN 角色判定 403 与 200。
 * 会话签发-校验-吊销的真实 Redis 行为由远端 E2E 覆盖。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authinterceptor;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class AuthInterceptorTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    @Test
    void meWithoutCookieIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(401001));
    }

    @Test
    void meWithValidSessionReturnsLoginUser() throws Exception {
        when(sessionService.validate("good-token")).thenReturn(new LoginUser(1L, "u1", "USER"));

        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie(SessionService.COOKIE_NAME, "good-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andExpect(jsonPath("$.result.account").value("u1"))
                .andExpect(jsonPath("$.result.role").value("USER"));
    }

    @Test
    void revokedOrExpiredSessionIsUnauthorized() throws Exception {
        // 已登出 / Redis 键已过期：签名有效但会话不存在 → 拦截器同样拒绝
        when(sessionService.validate(anyString())).thenReturn(null);

        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie(SessionService.COOKIE_NAME, "revoked-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(401001));
    }

    @Test
    void adminEndpointForbiddenForUserRole() throws Exception {
        when(sessionService.validate("user-token")).thenReturn(new LoginUser(2L, "u2", "USER"));

        mvc.perform(get("/api/test/admin-only").cookie(new Cookie(SessionService.COOKIE_NAME, "user-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(403001));
    }

    @Test
    void adminEndpointAllowedForAdminRole() throws Exception {
        when(sessionService.validate("admin-token")).thenReturn(new LoginUser(3L, "root", "ADMIN"));

        mvc.perform(get("/api/test/admin-only").cookie(new Cookie(SessionService.COOKIE_NAME, "admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("admin-ok"));
    }
}
