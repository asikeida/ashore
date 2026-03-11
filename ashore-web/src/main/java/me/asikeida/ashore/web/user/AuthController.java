package me.asikeida.ashore.web.user;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
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
 * 登出吊销 Redis 会话并清 Cookie；me 为受保护端点的样例（返回当前登录身份）。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** OAuth state Cookie：授权跳转时随机生成，回调时与参数比对（CSRF 防护），5 分钟有效 */
    private static final String OAUTH_STATE_COOKIE = "ashore_oauth_state";

    private final AuthService authService;

    private final SessionService sessionService;

    private final GitHubOAuthService githubOAuthService;

    public AuthController(AuthService authService, SessionService sessionService,
                          GitHubOAuthService githubOAuthService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.githubOAuthService = githubOAuthService;
    }

    @PostMapping("/register")
    public ResVo<UserVo> register(@Valid @RequestBody RegisterReq req, HttpServletResponse response) {
        UserVo vo = authService.register(req);
        issueSessionCookie(vo, response);
        return ResVo.ok(vo);
    }

    @PostMapping("/login")
    public ResVo<UserVo> login(@Valid @RequestBody LoginReq req, HttpServletResponse response) {
        UserVo vo = authService.login(req);
        issueSessionCookie(vo, response);
        return ResVo.ok(vo);
    }

    /** 当前登录身份（受保护端点样例；公开性由 @Permission 默认 LOGIN 决定） */
    @Permission
    @GetMapping("/me")
    public ResVo<MeVo> me() {
        LoginUser user = ReqContext.loginUser();
        // 边界视图：id 字符串化（雪花 ID 超 JS 2^53），不直接暴露内部 LoginUser
        return ResVo.ok(new MeVo(String.valueOf(user.userId()), user.account(), user.role()));
    }

    /** 登出：吊销 Redis 会话并清除 Cookie；未登录调用也返回成功（幂等） */
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
        Cookie cookie = new Cookie(SessionService.COOKIE_NAME, issued.token());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) issued.maxAgeSeconds());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private void clearSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(SessionService.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /** GitHub 授权入口：凭证未配置时拒绝（503001），已配置则 302 跳转并携带 state Cookie */
    @GetMapping("/oauth/github/authorize")
    public void authorize(HttpServletResponse response) throws IOException {
        if (!githubOAuthService.isConfigured()) {
            throw ExceptionUtil.of(me.asikeida.ashore.api.resp.StatusEnum.SERVICE_DISABLED);
        }
        String state = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Cookie stateCookie = new Cookie(OAUTH_STATE_COOKIE, state);
        stateCookie.setHttpOnly(true);
        stateCookie.setPath("/");
        stateCookie.setMaxAge(300);
        response.addCookie(stateCookie);
        response.sendRedirect(githubOAuthService.buildAuthorizeUrl(state));
    }

    /** GitHub 回调：校验 state → 换 token 拉用户 → 查/建本地用户 → 签发会话 → 回首页 */
    @GetMapping("/oauth/github/callback")
    public void githubCallback(
            @RequestParam String code,
            @RequestParam String state,
            @CookieValue(value = OAUTH_STATE_COOKIE, required = false) String expectedState,
            HttpServletResponse response) throws IOException {
        if (expectedState == null || !expectedState.equals(state)) {
            throw ExceptionUtil.of(me.asikeida.ashore.api.resp.StatusEnum.PARAM_ERROR);
        }
        LoginUser loginUser = githubOAuthService.loginWithGitHub(code);
        SessionService.IssuedSession issued = sessionService.issue(loginUser);

        Cookie sessionCookie = new Cookie(SessionService.COOKIE_NAME, issued.token());
        sessionCookie.setHttpOnly(true);
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge((int) issued.maxAgeSeconds());
        sessionCookie.setAttribute("SameSite", "Lax");
        response.addCookie(sessionCookie);
        Cookie clearState = new Cookie(OAUTH_STATE_COOKIE, "");
        clearState.setPath("/");
        clearState.setMaxAge(0);
        response.addCookie(clearState);
        response.sendRedirect("/");
    }
}
