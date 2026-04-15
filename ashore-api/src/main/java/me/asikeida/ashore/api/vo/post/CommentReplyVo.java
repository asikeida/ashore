package me.asikeida.ashore.api.vo.post;

/**
 * 单条评论视图（顶级/子回复通用）。id/userId/replyUserId 字符串化原因见 UserVo。
 */
public record CommentReplyVo(String id, String userId, String nickname, String content,
                             String replyUserId, String replyNickname, String createTime) {
}
