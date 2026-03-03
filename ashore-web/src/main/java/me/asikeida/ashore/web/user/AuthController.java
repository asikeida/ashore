package me.asikeida.ashore.web.user;

import jakarta.servlet.http.Cookie;
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
 * 登出吊销 Redis 会话并清 Cookie；me 为受保护端点的样例（返回当前登录身份）。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    private final SessionService sessionService;

    public AuthController(AuthService authService, SessionService sessionService) {
        this.authService = authService;
        this.sessionService = sessionService;
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
}
