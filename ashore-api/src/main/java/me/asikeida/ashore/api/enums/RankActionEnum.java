package me.asikeida.ashore.api.enums;

/**
 * 排行动作与积分映射（日/月榜 zset 的增量值）。
 * <p>取消动作为对应正动作的负分。</p>
 */
public enum RankActionEnum {

    /** 发布面经 */
    PUBLISH(10),

    /** 发表评论 */
    COMMENT(3),

    /** 点赞 */
    PRAISE(2),

    /** 取消点赞 */
    PRAISE_CANCEL(-2),

    /** 收藏 */
    COLLECT(2),

    /** 取消收藏 */
    COLLECT_CANCEL(-2),

    /** 阅读（登录用户访问详情） */
    READ(1);

    private final int score;

    RankActionEnum(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }

    /** 取消动作对应的正动作（PRAISE_CANCEL → PRAISE） */
    public RankActionEnum positiveCounterpart() {
        return switch (this) {
            case PRAISE_CANCEL -> PRAISE;
            case COLLECT_CANCEL -> COLLECT;
            default -> this;
        };
    }

    public boolean isCancel() {
        return score < 0;
    }
}
