package me.asikeida.ashore.web.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.user.repository.entity.UserLoginAuditDO;
import me.asikeida.ashore.service.user.repository.mapper.UserLoginAuditMapper;
import me.asikeida.ashore.service.user.service.LoginAuditService;
import me.asikeida.ashore.service.user.service.SessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 账密注册登录接口测试：走完整上下文（含 @Valid 校验、BCrypt、H2 数据库）。
 * 会话签发依赖 Redis，测试环境以 @MockitoBean 桩替换 SessionService
 * （签发/校验/吊销的集成行为由 AuthInterceptorTests 与远端 E2E 覆盖）。
 * 各用例用时间戳账号隔离，互不依赖执行顺序。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=review-p0-secret"
})
@AutoConfigureMockMvc
class AuthControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    @Autowired
    private UserLoginAuditMapper auditMapper;

    @BeforeEach
    void stubSessionIssuance() {
        when(sessionService.issue(any())).thenReturn(new SessionService.IssuedSession("stub-token", 3600));
    }

    private String uniqueAccount() {
        // 账号需符合 RegisterReq 的 @Pattern（字母数字_@.），不能用连字符
        return "auth" + System.currentTimeMillis();
    }

    @Test
    void registerThenLoginSuccess() throws Exception {
        String account = uniqueAccount();
        String body = "{\"account\":\"" + account + "\",\"password\":\"test123456\",\"nickname\":\"测试岸友\"}";

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andExpect(jsonPath("$.result.account").value(account))
                .andExpect(jsonPath("$.result.nickname").value("测试岸友"))
                .andExpect(jsonPath("$.result.id").isString());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andExpect(jsonPath("$.result.nickname").value("测试岸友"))
                // 登录成功即签发会话 Cookie（HttpOnly）
                .andExpect(cookie().exists(SessionService.COOKIE_NAME))
                .andExpect(cookie().value(SessionService.COOKIE_NAME, "stub-token"))
                .andExpect(cookie().httpOnly(SessionService.COOKIE_NAME, true));

        // 登录成功产生审计：IP 可追溯（MockMvc 直连为 127.0.0.1）
        UserLoginAuditDO audit = auditMapper.selectOne(new LambdaQueryWrapper<UserLoginAuditDO>()
                .eq(UserLoginAuditDO::getAccount, account));
        org.assertj.core.api.Assertions.assertThat(audit.getSuccess()).isTrue();
        org.assertj.core.api.Assertions.assertThat(audit.getUserId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(audit.getIp()).isEqualTo("127.0.0.1");
        org.assertj.core.api.Assertions.assertThat(audit.getRequestId()).isNotBlank();
    }

    @Test
    void guestLoginReusesPersistentAccountAndReservesName() throws Exception {
        String firstId = mvc.perform(post("/api/v1/auth/guest-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.account").value("guest"))
                .andExpect(cookie().httpOnly(SessionService.COOKIE_NAME, true))
                .andReturn().getResponse().getContentAsString();
        String secondId = mvc.perform(post("/api/v1/auth/guest-login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(firstId).at("/result/id").asText())
                .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree(secondId).at("/result/id").asText());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"guest\",\"password\":\"test123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400002));
    }

    @Test
    void logoutClearsCookieAndSucceedsWithoutSession() throws Exception {
        mvc.perform(delete("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                // 清除 Cookie 体现在 Set-Cookie 响应头（Max-Age=0），不在响应体
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void registerGeneratesDefaultNicknameWhenBlank() throws Exception {
        String account = uniqueAccount();
        String body = "{\"account\":\"" + account + "\",\"password\":\"test123456\"}";

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.nickname").value(
                        org.hamcrest.Matchers.matchesPattern("岸友\\d{4}")));
    }

    @Test
    void registerDuplicateAccountReturns400002() throws Exception {
        String account = uniqueAccount();
        String body = "{\"account\":\"" + account + "\",\"password\":\"test123456\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400002));
    }

    @Test
    void registerValidationFailureReturnsFieldViolation() throws Exception {
        String body = "{\"account\":\"" + uniqueAccount() + "\",\"password\":\"123\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400001))
                .andExpect(jsonPath("$.result[0].field").value("password"));
    }

    @Test
    void loginWrongPasswordAndUnknownAccountShareIdenticalMessage() throws Exception {
        String account = uniqueAccount();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"test123456\"}"))
                .andExpect(status().isOk());

        String wrongPassword = "{\"account\":\"" + account + "\",\"password\":\"wrong-password\"}";
        String unknownAccount = "{\"account\":\"ghost" + uniqueAccount() + "\",\"password\":\"whatever\"}";

        // 错误密码与不存在的账号必须返回完全一致的错误，防账号枚举
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongPassword))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(401002))
                .andExpect(jsonPath("$.status.msg").value("账号或密码错误"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(unknownAccount))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(401002))
                .andExpect(jsonPath("$.status.msg").value("账号或密码错误"));

        // 两次失败各有审计：密码错误（关联到真实用户）与账号不存在（user_id 为空）
        var wrongPwdAudit = auditMapper.selectOne(new LambdaQueryWrapper<UserLoginAuditDO>()
                .eq(UserLoginAuditDO::getAccount, account)
                .eq(UserLoginAuditDO::getSuccess, false)
                .eq(UserLoginAuditDO::getFailReason, LoginAuditService.REASON_PASSWORD_MISMATCH));
        org.assertj.core.api.Assertions.assertThat(wrongPwdAudit).isNotNull();
        org.assertj.core.api.Assertions.assertThat(wrongPwdAudit.getUserId()).isNotNull();

        var ghostAudit = auditMapper.selectOne(new LambdaQueryWrapper<UserLoginAuditDO>()
                .eq(UserLoginAuditDO::getSuccess, false)
                .eq(UserLoginAuditDO::getFailReason, LoginAuditService.REASON_ACCOUNT_NOT_FOUND));
        org.assertj.core.api.Assertions.assertThat(ghostAudit).isNotNull();
        org.assertj.core.api.Assertions.assertThat(ghostAudit.getUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(ghostAudit.getRequestId()).isNotBlank();
    }
}
