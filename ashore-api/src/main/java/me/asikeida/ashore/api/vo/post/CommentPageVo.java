package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 评论分页（顶级分页 + 每顶级折叠子回复）。nextCursor 为最后一条顶级评论 id，空 = 到底。
 */
public record CommentPageVo(String nextCursor, long total, List<CommentVo> items) {
}
