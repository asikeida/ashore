package me.asikeida.ashore.web.user;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.exception.BizException;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.auth.LoginReq;
import me.asikeida.ashore.api.vo.auth.MeVo;
import me.asikeida.ashore.api.vo.auth.RegisterReq;
import me.asikeida.ashore.api.vo.auth.UserVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.user.service.AuthService;
import me.asikeida.ashore.service.user.service.GitHubOAuthService;
import me.asikeida.ashore.service.user.service.SessionService;
import me.asikeida.ashore.web.auth.SessionCookieWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册 / 登录 / 登出 / 当前用户。
 * <p>登录与注册成功即签发会话（种 ashore_session Cookie，HttpOnly）；
 * 登出吊销 Redis 会话并清 Cookie；me 为受保护端点的样例（返回当前登录身份）。
 */
@Tag(name = "认证与登录", description = "账密注册登录、JWT 会话签发与吊销、GitHub OAuth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** OAuth state Cookie：授权跳转时随机生成，回调时与参数比对（CSRF 防护），5 分钟有效 */
    private static final String OAUTH_STATE_COOKIE = "ashore_oauth_state";

    private final AuthService authService;

    private final SessionService sessionService;

    private final GitHubOAuthService githubOAuthService;

    private final SessionCookieWriter cookieWriter;

    public AuthController(AuthService authService, SessionService sessionService,
                          GitHubOAuthService githubOAuthService, SessionCookieWriter cookieWriter) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.githubOAuthService = githubOAuthService;
        this.cookieWriter = cookieWriter;
    }

    @Operation(summary = "注册", description = "账号唯一校验 + BCrypt 密码哈希；成功即签发会话 Cookie")
    @PostMapping("/register")
    public ResVo<UserVo> register(@Valid @RequestBody RegisterReq req, HttpServletResponse response) {
        UserVo vo = authService.register(req);
        issueSessionCookie(vo, response);
        return ResVo.ok(vo);
    }

    @Operation(summary = "登录", description = "账密校验（失败统一文案防账号枚举）+ 登录审计；成功即签发会话 Cookie")
    @PostMapping("/login")
    public ResVo<UserVo> login(@Valid @RequestBody LoginReq req, HttpServletResponse response) {
        UserVo vo = authService.login(req);
        issueSessionCookie(vo, response);
        return ResVo.ok(vo);
    }

    /** 当前登录身份（受保护端点样例；公开性由 @Permission 默认 LOGIN 决定） */
    @Operation(summary = "当前用户", description = "需登录（ashore_session Cookie）；返回会话身份快照")
    @Permission
    @GetMapping("/me")
    public ResVo<MeVo> me() {
        LoginUser user = ReqContext.loginUser();
        // 边界视图：id 字符串化（雪花 ID 超 JS 2^53），不直接暴露内部 LoginUser
        return ResVo.ok(new MeVo(String.valueOf(user.userId()), user.account(), user.role()));
    }

    /** 登出：吊销 Redis 会话并清除 Cookie；未登录调用也返回成功（幂等） */
    @Operation(summary = "登出", description = "吊销 Redis 会话（token 即刻失效）并清除 Cookie；幂等")
    @DeleteMapping("/logout")
    public ResVo<Void> logout(
            @CookieValue(value = SessionService.COOKIE_NAME, required = false) String token,
            HttpServletResponse response) {
        if (token != null && !token.isBlank()) {
            sessionService.revoke(token);
        }
        clearSessionCookie(response);
        return ResVo.ok();
    }

    private void issueSessionCookie(UserVo vo, HttpServletResponse response) {
        LoginUser loginUser = new LoginUser(Long.valueOf(vo.id()), vo.account(), vo.role());
        SessionService.IssuedSession issued = sessionService.issue(loginUser);
        cookieWriter.issue(response, issued.token(), issued.maxAgeSeconds());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        cookieWriter.clear(response);
    }

    /** GitHub 授权入口：凭证未配置时拒绝（503001），已配置则 302 跳转并携带 state Cookie */
    @Operation(summary = "GitHub 授权跳转", description = "302 重定向至 GitHub 授权页；凭证未配置返回 503")
    @GetMapping("/oauth/github/authorize")
    public void authorize(HttpServletResponse response) throws IOException {
        if (!githubOAuthService.isConfigured()) {
            throw ExceptionUtil.of(me.asikeida.ashore.api.resp.StatusEnum.SERVICE_DISABLED);
        }
        String state = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        cookieWriter.issueState(response, OAUTH_STATE_COOKIE, state, 300);
        response.sendRedirect(githubOAuthService.buildAuthorizeUrl(state));
    }

    /** GitHub 回调：校验 state → 换 token 拉用户 → 查/建本地用户 → 签发会话 → 回首页 */
    @Operation(summary = "GitHub 回调", description = "state CSRF 校验 → 授权码换 token → 查/建本地用户 → 签发会话 → 302 回首页")
    @GetMapping("/oauth/github/callback")
    public void githubCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String oauthError,
            @CookieValue(value = OAUTH_STATE_COOKIE, required = false) String expectedState,
            HttpServletResponse response) throws IOException {
        // 用户在 GitHub 拒绝授权：GitHub 回调携带 error 参数而非 code（审查 R3：明确 400 而非兜底 500；
        // M5 前端就绪后改为重定向回登录页并带失败提示）
        if (oauthError != null && !oauthError.isBlank()) {
            throw new BizException(me.asikeida.ashore.api.resp.Status.of(
                    me.asikeida.ashore.api.resp.StatusEnum.PARAM_ERROR.getCode(), "GitHub 授权未完成或已取消"));
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw ExceptionUtil.of(me.asikeida.ashore.api.resp.StatusEnum.PARAM_ERROR);
        }
        if (expectedState == null || !expectedState.equals(state)) {
            throw ExceptionUtil.of(me.asikeida.ashore.api.resp.StatusEnum.PARAM_ERROR);
        }
        LoginUser loginUser = githubOAuthService.loginWithGitHub(code);
        SessionService.IssuedSession issued = sessionService.issue(loginUser);

        cookieWriter.issue(response, issued.token(), issued.maxAgeSeconds());
        cookieWriter.clearNamed(response, OAUTH_STATE_COOKIE);
        response.sendRedirect("/");
    }
}
