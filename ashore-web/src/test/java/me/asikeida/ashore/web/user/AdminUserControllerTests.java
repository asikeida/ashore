package me.asikeida.ashore.web.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.vo.auth.UserVo;
import me.asikeida.ashore.service.user.service.SessionService;
import me.asikeida.ashore.service.user.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 管理台用户端点测试（@MockitoBean 桩替换 UserAdminService/SessionService，
 * 聚焦 Controller 的鉴权与契约传达；业务规则的单元级验证见 UserAdminServiceImpl 层测试）：
 * <ol>
 *   <li>USER 角色访问 → 403（拦截器 ADMIN 判定）；</li>
 *   <li>ADMIN 正常封禁 → 200 且透传 operatorId（自身防线在 Service 层用同参数断言）；</li>
 *   <li>列表端点鉴权与透传。</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminuser;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=admin-user-secret"
})
@AutoConfigureMockMvc
class AdminUserControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private UserAdminService userAdminService;

    private static final UserVo SAMPLE = new UserVo("998", "target", "目标用户", "USER", null);

    @Test
    void listForbiddenForUserRole() throws Exception {
        when(sessionService.validate("user-token")).thenReturn(new LoginUser(2L, "u2", "USER"));

        mvc.perform(get("/api/v1/admin/users").cookie(new Cookie(SessionService.COOKIE_NAME, "user-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(403001));
    }

    @Test
    void banByAdminPassesOperatorId() throws Exception {
        when(sessionService.validate("admin-token")).thenReturn(new LoginUser(3L, "root", "ADMIN"));
        when(userAdminService.ban(998L, 3L)).thenReturn(SAMPLE);

        mvc.perform(post("/api/v1/admin/users/998/ban")
                        .cookie(new Cookie(SessionService.COOKIE_NAME, "admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andExpect(jsonPath("$.result.id").value("998"))
                .andExpect(jsonPath("$.result.account").value("target"));
        verify(userAdminService).ban(998L, 3L);
    }

    @Test
    void banWithoutSessionIsUnauthorized() throws Exception {
        when(sessionService.validate(any())).thenReturn(null);

        mvc.perform(post("/api/v1/admin/users/998/ban"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(401001));
        verify(userAdminService, never()).ban(eq(998L), any());
    }

    @Test
    void unbanByAdminDelegates() throws Exception {
        when(sessionService.validate("admin-token")).thenReturn(new LoginUser(3L, "root", "ADMIN"));
        when(userAdminService.unban(998L)).thenReturn(SAMPLE);

        mvc.perform(post("/api/v1/admin/users/998/unban")
                        .cookie(new Cookie(SessionService.COOKIE_NAME, "admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.account").value("target"));
        verify(userAdminService).unban(998L);
    }

    @Test
    void listByAdminDelegatesKeywordAndLimit() throws Exception {
        when(sessionService.validate("admin-token")).thenReturn(new LoginUser(3L, "root", "ADMIN"));
        when(userAdminService.list("target", 10)).thenReturn(List.of(SAMPLE));

        mvc.perform(get("/api/v1/admin/users")
                        .param("accountKeyword", "target")
                        .param("limit", "10")
                        .cookie(new Cookie(SessionService.COOKIE_NAME, "admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1));
        verify(userAdminService).list("target", 10);
    }
}
