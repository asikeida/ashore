package me.asikeida.ashore.api.context;

/**
 * 请求级上下文：由 web 层过滤器在请求入口写入，请求结束时清理。
 * <p>requestId 由 RequestIdFilter 写入；loginUser 由鉴权拦截器在校验会话后补充。</p>
 * <p>实现为普通 ThreadLocal，仅覆盖同步请求路径；异步线程池透传在 M4 升级为
 * TransmittableThreadLocal（见 docs/开发规划/90-待办清单.md T4）。</p>
 */
public final class ReqContext {

    private static final ThreadLocal<ReqContext> HOLDER = new ThreadLocal<>();

    private final String requestId;

    private final LoginUser loginUser;

    private ReqContext(String requestId, LoginUser loginUser) {
        this.requestId = requestId;
        this.loginUser = loginUser;
    }

    public static void hold(String requestId) {
        HOLDER.set(new ReqContext(requestId, null));
    }

    /** 鉴权拦截器校验通过后补充登录身份（保持 requestId 不变，整体替换上下文） */
    public static void setLoginUser(LoginUser user) {
        ReqContext current = HOLDER.get();
        if (current != null) {
            HOLDER.set(new ReqContext(current.requestId, user));
        }
    }

    /** 当前请求标识；非 Web 线程（定时任务、启动期）返回 null */
    public static String requestId() {
        ReqContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.requestId;
    }

    /** 当前登录用户；公开端点或未登录时返回 null */
    public static LoginUser loginUser() {
        ReqContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.loginUser;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
