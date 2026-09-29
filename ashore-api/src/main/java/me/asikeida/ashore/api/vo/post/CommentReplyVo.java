package me.asikeida.ashore.api.vo.post;

/**
 * 单条评论视图（顶级/子回复通用）。id/userId/replyUserId 字符串化原因见 UserVo。
 * likeCount/liked：点赞数与当前用户是否已赞（未登录 liked 恒 false）。
 */
public record CommentReplyVo(String id, String userId, String nickname, String content,
                             String replyUserId, String replyNickname, String createTime,
                             long likeCount, boolean liked) {
}
