package me.asikeida.ashore.web.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import me.asikeida.ashore.service.user.service.SessionService;

/**
 * 会话 Cookie 统一写入口：签发 / 清除的唯一实现（审查 R5，消除 AuthController 内 5 处重复）。
 * <p>Cookie 属性（HttpOnly / SameSite=Lax / Path=/）在此集中维护。</p>
 */
public final class SessionCookieWriter {

    private SessionCookieWriter() {
    }

    /** 签发会话 Cookie（登录 / 注册 / OAuth 回调共用） */
    public static void issue(HttpServletResponse response, String token, long maxAgeSeconds) {
        Cookie cookie = new Cookie(SessionService.COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /** 清除会话 Cookie（登出；OAuth 失败路径复用） */
    public static void clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(SessionService.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /** 短效 state Cookie（OAuth CSRF 防护） */
    public static void issueState(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);
    }

    /** 清除任意 Cookie（state 等） */
    public static void clearNamed(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
