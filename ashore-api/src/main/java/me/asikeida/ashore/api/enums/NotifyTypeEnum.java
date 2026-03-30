package me.asikeida.ashore.api.enums;

/**
 * 通知类型（notify_msg.type 列取值）。
 */
public enum NotifyTypeEnum {

    /** 评论了我的面经 */
    COMMENT,

    /** 回复了我的评论 */
    REPLY,

    /** 点赞了我的面经 */
    PRAISE,

    /** 收藏了我的面经 */
    COLLECT,

    /** 系统通知（审核结果、管理员广播等） */
    SYSTEM
}
