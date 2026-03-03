package me.asikeida.ashore.core.permission;

/**
 * 端点访问级别。role 字符串取值与 user 表 role 列一致（"USER"/"ADMIN"）。
 */
public enum UserRole {

    /** 公开端点（未标注 @Permission 时无需本枚举，仅占语义） */
    ALL,

    /** 已登录即可 */
    LOGIN,

    /** 管理员 */
    ADMIN;

    /**
     * 判断当前用户角色是否满足本访问级别。
     *
     * @param userRole 用户角色字符串（来自会话快照），未登录时为 null
     */
    public boolean allowed(String userRole) {
        return switch (this) {
            case ALL -> true;
            case LOGIN -> userRole != null;
            case ADMIN -> "ADMIN".equals(userRole);
        };
    }
}
