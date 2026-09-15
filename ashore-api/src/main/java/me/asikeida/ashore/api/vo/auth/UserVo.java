package me.asikeida.ashore.api.vo.auth;

/**
 * 用户基础信息视图。不含密码哈希等敏感字段。
 *
 * @param id     雪花 ID 以字符串返回：约 1.9e18 超出 JS Number 安全整数范围（2^53），
 *               直接输出数字会被前端精度截断
 * @param avatar 头像 URL；null = 未设置（前端以昵称首字占位）
 */
public record UserVo(String id, String account, String nickname, String role, String avatar) {
}
