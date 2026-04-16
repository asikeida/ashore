package me.asikeida.ashore.api.model.event;

import me.asikeida.ashore.api.enums.NotifyTypeEnum;

/**
 * 通知事件：用户行为发生后由业务点发布，{@code NotifyMsgListener} 异步落库。
 * <p>作为 payload 事件发布（Spring 4.2+ 支持任意对象），监听方按类型解耦消费。</p>
 *
 * @param receiverId 接收人（被评论/被点赞/被收藏/被审核的面经作者）
 * @param operatorId 触发者（操作的用户；系统操作为 0）
 * @param type       通知类型
 * @param articleId  关联面经
 * @param commentId  关联评论（可空）
 * @param content    摘要内容（可空）
 */
public record NotifyMsgEvent(Long receiverId, Long operatorId, NotifyTypeEnum type,
                             Long articleId, Long commentId, String content) {
}
