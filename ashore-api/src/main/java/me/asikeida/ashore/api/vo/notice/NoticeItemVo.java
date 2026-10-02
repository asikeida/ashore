package me.asikeida.ashore.api.vo.notice;

/**
 * 通知条目视图。articleId/commentId 为字符串化雪花 ID——Long 直出 JSON 数字会
 * 被 JS Number 2^53 精度截断（事故：通知跳转文章 ID 尾位被改写 → 404），决策 D4 补漏。
 */
public record NoticeItemVo(String id, String type, String articleId, String commentId,
                           String operatorNickname, String content, boolean read, String createTime) {
}
