package me.asikeida.ashore.api.vo.auth;

/**
 * 当前登录用户视图（/api/v1/auth/me）。id 为字符串原因见 {@link UserVo}。
 */
public record MeVo(String id, String account, String role, String avatar) {
}
