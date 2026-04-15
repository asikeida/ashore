package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 顶级评论视图（两级折叠：自身 + 子回复列表）。
 */
public record CommentVo(String id, String userId, String nickname, String content,
                        String createTime, List<CommentReplyVo> replies) {
}
