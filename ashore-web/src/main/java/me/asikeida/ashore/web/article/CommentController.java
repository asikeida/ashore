package me.asikeida.ashore.web.article;

import jakarta.validation.Valid;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.post.CommentPageVo;
import me.asikeida.ashore.api.vo.post.CommentReplyVo;
import me.asikeida.ashore.api.vo.post.CreateCommentReq;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.article.service.CommentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论端点：发表（登录）、两级折叠列表（公开）、软删除（作者或管理员）。
 */
@Tag(name = "评论", description = "面经评论：发表、两级折叠列表与软删除")
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "发表评论", description = "需登录；parentCommentId 为空 = 顶级评论")
    @Permission
    @PostMapping("/posts/{id}/comments")
    public ResVo<CommentReplyVo> create(@PathVariable Long id, @Valid @RequestBody CreateCommentReq req) {
        return ResVo.ok(commentService.create(id, req, current()));
    }

    @Operation(summary = "评论列表", description = "公开；顶级评论游标分页，子回复折叠返回；登录时附带各评论是否已赞")
    @GetMapping("/posts/{id}/comments")
    public ResVo<CommentPageVo> list(
            @PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize) {
        return ResVo.ok(commentService.list(id, cursor, pageSize, current()));
    }

    @Operation(summary = "评论点赞/取消", description = "需登录；幂等 toggle，返回操作后状态与最新计数")
    @Permission
    @PostMapping("/comments/{id}/like")
    public ResVo<CommentService.LikeResult> toggleLike(@PathVariable Long id) {
        LoginUser user = current();
        // 评论必须属于当前文章路径（路径中的文章 id 由前端传入；此处从评论反查校验一致性）
        return ResVo.ok(commentService.toggleLike(id, user));
    }

    @Operation(summary = "删除评论", description = "仅作者或管理员；软删除")
    @Permission
    @DeleteMapping("/comments/{id}")
    public ResVo<Void> delete(@PathVariable Long id) {
        commentService.delete(id, current());
        return ResVo.ok();
    }

    private static LoginUser current() {
        return ReqContext.loginUser();
    }
}
