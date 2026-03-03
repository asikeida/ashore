package me.asikeida.ashore.api.context;

/**
 * 当前登录用户：会话校验通过后的身份快照，由鉴权拦截器写入 {@link ReqContext}。
 *
 * @param role 角色字符串，取值与 user 表 role 列一致（"USER"/"ADMIN"）
 */
public record LoginUser(Long userId, String account, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
