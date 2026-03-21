package me.asikeida.ashore.web.article;

import jakarta.validation.Valid;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.post.EditPostReq;
import me.asikeida.ashore.api.vo.post.PostDetailVo;
import me.asikeida.ashore.api.vo.post.PublishPostReq;
import me.asikeida.ashore.api.vo.post.PostVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.core.permission.UserRole;
import me.asikeida.ashore.service.article.service.ArticleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面经写端点：发布 / 编辑 / 状态流转 / 详情。
 * 列表与分页见 PostQueryController（M3-3）。
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final ArticleService articleService;

    public PostController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Permission
    @PostMapping
    public ResVo<PostVo> publish(@Valid @RequestBody PublishPostReq req) {
        return ResVo.ok(articleService.publish(req, current()));
    }

    @Permission
    @PutMapping("/{id}")
    public ResVo<PostVo> edit(@PathVariable Long id, @Valid @RequestBody EditPostReq req) {
        return ResVo.ok(articleService.edit(id, req, current()));
    }

    /** 审核通过上线（仅管理员） */
    @Permission(UserRole.ADMIN)
    @PostMapping("/{id}/online")
    public ResVo<Void> online(@PathVariable Long id) {
        articleService.online(id, current());
        return ResVo.ok();
    }

    /** 下线/撤回（作者或管理员） */
    @Permission
    @PostMapping("/{id}/offline")
    public ResVo<Void> offline(@PathVariable Long id) {
        articleService.offline(id, current());
        return ResVo.ok();
    }

    /** 重新提交审核（仅作者，OFFLINE → REVIEW） */
    @Permission
    @PostMapping("/{id}/submit")
    public ResVo<Void> submit(@PathVariable Long id) {
        articleService.submit(id, current());
        return ResVo.ok();
    }

    /** 详情（公开；非上线状态仅作者/管理员可见） */
    @GetMapping("/{id}")
    public ResVo<PostDetailVo> detail(@PathVariable Long id) {
        // 公开端点未登录时 ReqContext.loginUser() 为 null，由 service 决定可见性
        return ResVo.ok(articleService.detail(id, current()));
    }

    private static LoginUser current() {
        return ReqContext.loginUser();
    }
}
