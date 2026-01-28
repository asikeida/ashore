package me.asikeida.ashore.api.resp;

/**
 * 全局错误码枚举。
 * <p>分段规则：HTTP 语义段（前 3 位）+ 业务序号（后 3 位）。</p>
 * <ul>
 *   <li>0 —— 成功</li>
 *   <li>400xxx —— 参数与客户端错误</li>
 *   <li>401xxx —— 未认证（未登录 / 会话失效）</li>
 *   <li>403xxx —— 已认证但无权限</li>
 *   <li>404xxx —— 资源不存在</li>
 *   <li>500xxx —— 服务器内部错误（对外统一模糊文案，不泄漏细节）</li>
 * </ul>
 * <p>约定：已发布的错误码值不可更改；新错误码按业务域追加序号（如 401002 会话过期）。</p>
 */
public enum StatusEnum implements Status {

    /** 成功 */
    SUCCESS(0, "成功"),

    /** 参数校验失败（字段级错误由全局异常处理器补充明细） */
    PARAM_ERROR(400001, "请求参数校验失败"),

    /** 未登录或登录态失效 */
    UNAUTHORIZED(401001, "未登录或登录已失效"),

    /** 已登录但角色/权限不足 */
    FORBIDDEN(403001, "无权限执行该操作"),

    /** 目标资源不存在或已删除 */
    NOT_FOUND(404001, "资源不存在"),

    /** 未预期异常的对外统一文案 */
    ERROR(500000, "服务异常，请稍后重试");

    private final int code;

    private final String msg;

    StatusEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
