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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "面经", description = "面经发布、编辑、审核状态流转与详情")
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final ArticleService articleService;

    public PostController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(summary = "发布面经", description = "需登录；所有人发文进 REVIEW 审核；标签不存在自动创建")
    @Permission
    @PostMapping
    public ResVo<PostVo> publish(@Valid @RequestBody PublishPostReq req) {
        return ResVo.ok(articleService.publish(req, current()));
    }

    @Operation(summary = "编辑面经", description = "仅作者或管理员；每次编辑产生新正文版本；已上线面经编辑后回审核")
    @Permission
    @PutMapping("/{id}")
    public ResVo<PostVo> edit(@PathVariable Long id, @Valid @RequestBody EditPostReq req) {
        return ResVo.ok(articleService.edit(id, req, current()));
    }

    /** 审核通过上线（仅管理员） */
    @Operation(summary = "审核上线", description = "仅管理员；REVIEW → ONLINE")
    @Permission(UserRole.ADMIN)
    @PostMapping("/{id}/online")
    public ResVo<Void> online(@PathVariable Long id) {
        articleService.online(id, current());
        return ResVo.ok();
    }

    /** 下线/撤回（作者或管理员） */
    @Operation(summary = "下线/撤回", description = "作者或管理员；REVIEW/ONLINE → OFFLINE")
    @Permission
    @PostMapping("/{id}/offline")
    public ResVo<Void> offline(@PathVariable Long id) {
        articleService.offline(id, current());
        return ResVo.ok();
    }

    /** 重新提交审核（仅作者，OFFLINE → REVIEW） */
    @Operation(summary = "重新提交审核", description = "仅作者；OFFLINE → REVIEW")
    @Permission
    @PostMapping("/{id}/submit")
    public ResVo<Void> submit(@PathVariable Long id) {
        articleService.submit(id, current());
        return ResVo.ok();
    }

    /** 详情（公开；非上线状态仅作者/管理员可见） */
    @Operation(summary = "面经详情", description = "公开；非上线状态仅作者/管理员可见（其他返回 404 防枚举）；返回最新版本正文")
    @GetMapping("/{id}")
    public ResVo<PostDetailVo> detail(@PathVariable Long id) {
        // 公开端点未登录时 ReqContext.loginUser() 为 null，由 service 决定可见性
        return ResVo.ok(articleService.detail(id, current()));
    }

    private static LoginUser current() {
        return ReqContext.loginUser();
    }
}
