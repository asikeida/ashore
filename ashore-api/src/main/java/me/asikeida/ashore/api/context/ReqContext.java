package me.asikeida.ashore.api.context;

/**
 * 请求级上下文：由 web 层过滤器在请求入口写入，请求结束时清理。
 * <p>当前仅携带请求标识（日志关联与错误定位用）；登录用户等身份信息在 M2 鉴权落地后加入。</p>
 * <p>实现为普通 ThreadLocal，仅覆盖同步请求路径；异步线程池透传在 M4 升级为
 * TransmittableThreadLocal（见 docs/开发规划/90-待办清单.md T4）。</p>
 */
public final class ReqContext {

    private static final ThreadLocal<ReqContext> HOLDER = new ThreadLocal<>();

    private final String requestId;

    private ReqContext(String requestId) {
        this.requestId = requestId;
    }

    public static void hold(String requestId) {
        HOLDER.set(new ReqContext(requestId));
    }

    /** 当前请求标识；非 Web 线程（定时任务、启动期）返回 null */
    public static String requestId() {
        ReqContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.requestId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
