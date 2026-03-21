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

    /** 注册时账号已被占用 */
    ACCOUNT_EXISTS(400002, "账号已被注册"),

    /** 未登录或登录态失效 */
    UNAUTHORIZED(401001, "未登录或登录已失效"),

    /** 登录失败：统一文案防账号枚举（不区分账号不存在与密码错误） */
    LOGIN_FAILED(401002, "账号或密码错误"),

    /** 已登录但角色/权限不足 */
    FORBIDDEN(403001, "无权限执行该操作"),

    /** 账号处于封禁状态 */
    ACCOUNT_BANNED(403002, "账号已被封禁，请联系管理员"),

    /** 目标资源不存在或已删除 */
    NOT_FOUND(404001, "资源不存在"),

    /** 当前状态不允许该操作（状态机非法流转） */
    ILLEGAL_STATE(400003, "当前状态不允许该操作"),

    /** 未预期异常的对外统一文案 */
    ERROR(500000, "服务异常，请稍后重试"),

    /** 功能存在但服务端未启用（如 OAuth 凭证未配置） */
    SERVICE_DISABLED(503001, "该登录方式暂未启用");

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
