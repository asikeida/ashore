package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 顶级评论视图（两级折叠：自身 + 子回复列表）。
 * likeCount/liked：点赞数与当前用户是否已赞（未登录 liked 恒 false）。
 */
public record CommentVo(String id, String userId, String nickname, String content,
                        String createTime, long likeCount, boolean liked,
                        List<CommentReplyVo> replies) {
}
