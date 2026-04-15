package me.asikeida.ashore.service.article.service;

import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.vo.post.CommentPageVo;
import me.asikeida.ashore.api.vo.post.CommentReplyVo;
import me.asikeida.ashore.api.vo.post.CreateCommentReq;

/**
 * 评论域：两级折叠（顶级分页 + 子回复折叠）、软删除。
 * <p>内容审核（敏感词/AI 审核）延后 M6，见待办 T13。</p>
 */
public interface CommentService {

    /** 发表评论：parentCommentId 为空 = 顶级；否则挂到对应顶级下（自动推导被回复人） */
    CommentReplyVo create(Long articleId, CreateCommentReq req, LoginUser commenter);

    /**
     * 评论列表（公开）：顶级评论按游标（最后一条 id）分页，子回复整体折叠返回。
     *
     * @param cursor  上一页最后一条顶级评论 id；null 表示第一页
     * @param pageSize 页大小，缺省 10，上限 50
     */
    CommentPageVo list(Long articleId, String cursor, Integer pageSize);

    /** 软删除评论：仅作者或管理员 */
    void delete(Long commentId, LoginUser operator);
}
