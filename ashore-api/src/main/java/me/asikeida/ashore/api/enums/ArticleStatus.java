package me.asikeida.ashore.api.enums;

/**
 * 面经状态机：OFFLINE（下线）⇄ REVIEW（审核中）→ ONLINE（上线）。
 * <p>流转合法性内聚在 {@link #canTransitionTo}（对照 paicoding 分散在 Service 中的 if 链，
 * 状态机自描述、新增状态只改一处）。</p>
 * <p>库列取值 = name()；字符串列避免 TINYINT 魔数，可读性优先。</p>
 */
public enum ArticleStatus {

    OFFLINE,

    REVIEW,

    ONLINE;

    /**
     * 流转矩阵：
     * <pre>
     * OFFLINE → REVIEW      （作者重新提交审核）
     * REVIEW  → ONLINE      （管理员审核通过）
     * REVIEW  → OFFLINE     （作者撤回 / 管理员驳回）
     * ONLINE  → OFFLINE     （作者下线 / 管理员下架）
     * ONLINE  → REVIEW      （编辑已上线内容，重新审核）
     * </pre>
     * 不可达：OFFLINE → ONLINE（未审核不得上线）。
     */
    public boolean canTransitionTo(ArticleStatus target) {
        return switch (this) {
            case OFFLINE -> target == REVIEW;
            case REVIEW -> target == ONLINE || target == OFFLINE;
            case ONLINE -> target == OFFLINE || target == REVIEW;
        };
    }

    /** 字符串列值转枚举；未知值抛业务异常由全局处理器兜底 */
    public static ArticleStatus of(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("未知文章状态: " + value);
        }
    }
}
