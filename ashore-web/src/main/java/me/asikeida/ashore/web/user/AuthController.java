package me.asikeida.ashore.web.user;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.auth.LoginReq;
import me.asikeida.ashore.api.vo.auth.MeVo;
import me.asikeida.ashore.api.vo.auth.RegisterReq;
import me.asikeida.ashore.api.vo.auth.UserVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.user.service.AuthService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册 / 登录 / 登出 / 当前用户。
 * <p>登录与注册成功即签发会话（种 ashore_session Cookie，HttpOnly）；
 * 登出吊销 Redis 会话并清 Cookie；me 为受保护端点的样例（返回当前登录身份）。
 */
@Tag(name = "认证与登录", description = "账密注册登录、JWT 会话签发与吊销")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    private final SessionService sessionService;

    private final SessionCookieWriter cookieWriter;

    private final me.asikeida.ashore.service.profile.service.MeService meService;

    public AuthController(AuthService authService, SessionService sessionService,
                          SessionCookieWriter cookieWriter,
                          me.asikeida.ashore.service.profile.service.MeService meService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.cookieWriter = cookieWriter;
        this.meService = meService;
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
        // 边界视图：id 字符串化（雪花 ID 超 JS 2^53）；avatar 查库（改头像后即时生效，会话快照不含）
        return ResVo.ok(new MeVo(String.valueOf(user.userId()), user.account(), user.role(),
                meService.avatarOf(user.userId())));
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
}
