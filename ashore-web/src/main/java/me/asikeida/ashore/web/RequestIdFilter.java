package me.asikeida.ashore.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import me.asikeida.ashore.api.context.ReqContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求标识与链路日志的入口：
 * <ol>
 *   <li>解析/生成 requestId（优先透传上游网关带来的 X-Request-Id），写响应头与 MDC，
 *       使本轮所有日志行自动携带同一标识；</li>
 *   <li>写入 {@link ReqContext}，供业务代码与异常处理读取；</li>
 *   <li>请求结束输出访问日志（方法/路径/状态/耗时），并清理 MDC 与 ThreadLocal 防泄漏。</li>
 * </ol>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        ReqContext.hold(requestId, resolveClientIp(request), request.getHeader("User-Agent"));
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            // finally 中输出访问日志：即使内部抛异常被全局处理器接住，也能记录到真实耗时
            log.info("access {} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), cost);
            MDC.remove(MDC_KEY);
            ReqContext.clear();
        }
    }

    /** 上游带来的合法标识直接复用（便于跨服务串联），否则生成 16 位十六进制短 ID */
    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank() && incoming.length() <= 64) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 客户端 IP：代理场景取 X-Forwarded-For 末段（最接近本服务的代理所追加，不可被客户端预置伪造），
     * 直连场景取源地址。审查 R4：首段可被客户端任意伪造（公网部署后审计 IP 污染）。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
