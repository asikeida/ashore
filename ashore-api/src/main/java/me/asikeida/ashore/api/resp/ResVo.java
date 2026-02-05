package me.asikeida.ashore.api.resp;

/**
 * 统一响应体。所有 HTTP 接口返回该结构：
 * <pre>{@code
 * { "status": { "code": 0, "msg": "成功" }, "result": { ... } }
 * }</pre>
 * <p>code == 0 表示成功；非 0 时 result 为 null，前端以 status.msg 展示。</p>
 *
 * @param <T> 业务数据类型
 */
public class ResVo<T> {

    private Status status;

    private T result;

    public static <T> ResVo<T> ok() {
        return build(StatusEnum.SUCCESS, null);
    }

    public static <T> ResVo<T> ok(T result) {
        return build(StatusEnum.SUCCESS, result);
    }

    public static <T> ResVo<T> fail(StatusEnum status) {
        return build(status, null);
    }

    /** 失败且携带附加明细（如参数校验的字段级错误列表） */
    public static <T> ResVo<T> fail(StatusEnum status, T result) {
        return build(status, result);
    }

    public static <T> ResVo<T> fail(Status status) {
        return build(status, null);
    }

    private static <T> ResVo<T> build(Status status, T result) {
        ResVo<T> vo = new ResVo<>();
        vo.status = normalize(status);
        vo.result = result;
        return vo;
    }

    /**
     * 枚举归一化为 {@link Status.SimpleStatus}：Jackson 对枚举运行时类型会走 EnumSerializer
     * 把 status 序列化成字符串（如 "SUCCESS"）而非 {code,msg} 对象，统一转 record 保证 JSON 形态稳定。
     */
    private static Status normalize(Status status) {
        return status instanceof StatusEnum e ? Status.of(e.getCode(), e.getMsg()) : status;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }
}
