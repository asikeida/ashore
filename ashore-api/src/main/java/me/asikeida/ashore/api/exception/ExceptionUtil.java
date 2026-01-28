package me.asikeida.ashore.api.exception;

import me.asikeida.ashore.api.resp.Status;
import me.asikeida.ashore.api.resp.StatusEnum;

/**
 * 业务异常静态工厂。用法示例：
 * <pre>{@code
 * throw ExceptionUtil.of(StatusEnum.NOT_FOUND);                          // 静态消息
 * throw ExceptionUtil.of(StatusEnum.PARAM_ERROR, "标题不能为空");         // {} 占位符填参
 * }</pre>
 */
public final class ExceptionUtil {

    private ExceptionUtil() {
    }

    public static BizException of(StatusEnum status, Object... args) {
        return new BizException(Status.of(status.getCode(), format(status.getMsg(), args)));
    }

    /**
     * 将模板中的 {@code {}} 依序替换为参数。无第三方依赖的轻量实现，
     * 参数多于占位符时忽略多余参数。
     */
    public static String format(String template, Object... args) {
        if (template == null || args.length == 0) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + 32);
        int from = 0;
        int argIdx = 0;
        int slot;
        while (argIdx < args.length && (slot = template.indexOf("{}", from)) >= 0) {
            sb.append(template, from, slot).append(args[argIdx++]);
            from = slot + 2;
        }
        return sb.append(template, from, template.length()).toString();
    }
}
