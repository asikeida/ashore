package me.asikeida.ashore.api.enums;

/**
 * 足迹行为类型（user_foot.type 列取值）。
 */
public enum FootTypeEnum {

    /** 点赞（可切换） */
    PRAISE,

    /** 收藏（可切换） */
    COLLECT,

    /** 评论（产生即有效） */
    COMMENT,

    /** 阅读（详情访问即有效） */
    READ;

    /** 是否支持取消语义（切换型行为） */
    public boolean isToggleable() {
        return this == PRAISE || this == COLLECT;
    }
}
