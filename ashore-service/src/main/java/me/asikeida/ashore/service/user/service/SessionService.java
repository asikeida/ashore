package me.asikeida.ashore.service.user.service;

import me.asikeida.ashore.api.context.LoginUser;

/**
 * 登录会话：JWT 格式 token + Redis 登记的双层设计（对照 paicoding UserSessionHelper 重写）。
 * <p>JWT 提供无状态签名校验，Redis 登记提供**可吊销**能力——两者结合的原因：
 * 纯 JWT 无法主动踢会话，纯 Redis 又失去签名防篡改。Cookie 名与 Cookie 语义见常量。</p>
 */
public interface SessionService {

    /** 会话 Cookie 名（对照 paicoding 的 f-session） */
    String COOKIE_NAME = "ashore_session";

    /** 为登录用户签发会话：返回 JWT token（含 sessionId 声明）与 Cookie 有效期 */
    IssuedSession issue(LoginUser user);

    /**
     * 校验 token：签名与有效期验证 + Redis 会话存在性检查。
     *
     * @return 登录身份；token 无效、过期或会话已吊销时返回 null
     */
    LoginUser validate(String token);

    /** 吊销会话（登出）：删除 Redis 登记，token 即刻失效 */
    void revoke(String token);

    /** 签发结果 */
    record IssuedSession(String token, long maxAgeSeconds) {
    }
}
