package me.asikeida.ashore.api.exception;

import me.asikeida.ashore.api.resp.Status;

/**
 * 业务异常：携带 {@link Status}（错误码 + 消息），在业务规则处抛出，
 * 由 web 模块的全局异常处理器统一转换为 {@code ResVo} 响应。
 */
public class BizException extends RuntimeException {

    private final Status status;

    public BizException(Status status) {
        super(status.getMsg());
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }
}
