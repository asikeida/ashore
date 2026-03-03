package me.asikeida.ashore.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.user.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：解析 Cookie 会话 → 校验 → 写入 {@link ReqContext} → 按
 * {@link Permission} 注解判定访问级别。
 * <p>未标注 @Permission 的端点视为公开；REST 端点拒绝时直接写 JSON（与全局异常
 * 处理器的响应形态一致）。仅拦截 /api/**（见 WebMvcConfig 注册）。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;

    private final ObjectMapper objectMapper;

    public AuthInterceptor(SessionService sessionService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Permission permission = handlerMethod.getMethodAnnotation(Permission.class);
        if (permission == null) {
            permission = handlerMethod.getBeanType().getAnnotation(Permission.class);
        }
        if (permission == null) {
            return true;
        }

        LoginUser user = resolveLoginUser(request);
        if (user == null) {
            return reject(response, HttpStatus.UNAUTHORIZED.value(), StatusEnum.UNAUTHORIZED);
        }
        if (!permission.value().allowed(user.role())) {
            return reject(response, HttpStatus.FORBIDDEN.value(), StatusEnum.FORBIDDEN);
        }
        ReqContext.setLoginUser(user);
        return true;
    }

    private LoginUser resolveLoginUser(HttpServletRequest request) {
        Cookie cookie = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(c -> SessionService.COOKIE_NAME.equals(c.getName()))
                .findFirst()
                .orElse(null);
        return cookie == null ? null : sessionService.validate(cookie.getValue());
    }

    private boolean reject(HttpServletResponse response, int status, StatusEnum statusEnum) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ResVo.fail(statusEnum)));
        return false;
    }
}
