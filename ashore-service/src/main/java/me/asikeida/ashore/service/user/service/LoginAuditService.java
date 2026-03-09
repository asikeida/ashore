package me.asikeida.ashore.service.user.service;

/**
 * 登录审计：登录成功与失败都记录（失败供风控分析）。
 * <p>写入为 best-effort：审计失败仅告警，不影响登录主流程。</p>
 */
public interface LoginAuditService {

    /** 失败原因：账号不存在（未区分大小写等归一化策略，原样匹配） */
    String REASON_ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    /** 失败原因：密码错误 */
    String REASON_PASSWORD_MISMATCH = "PASSWORD_MISMATCH";

    /** 失败原因：账号被封禁 */
    String REASON_BANNED = "BANNED";

    /**
     * 记录一次登录尝试。
     *
     * @param userId     用户 ID；账号不存在时为 null
     * @param account    尝试登录的账号（原样记录）
     * @param success    是否成功
     * @param failReason 失败原因（成功时为 null）
     */
    void record(Long userId, String account, boolean success, String failReason);
}
