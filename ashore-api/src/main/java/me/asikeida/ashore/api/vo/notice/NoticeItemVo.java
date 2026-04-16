package me.asikeida.ashore.api.vo.notice;

/**
 * 通知条目视图。
 */
public record NoticeItemVo(String id, String type, Long articleId, Long commentId,
                           String operatorNickname, String content, boolean read, String createTime) {
}
