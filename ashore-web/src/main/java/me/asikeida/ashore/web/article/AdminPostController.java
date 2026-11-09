package me.asikeida.ashore.web.article;

import java.util.List;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.post.AdminPostItemVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.core.permission.UserRole;
import me.asikeida.ashore.service.article.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理台文章端点（仅 ADMIN）：按状态筛选的文章列表。
 * 上线/下线复用 PostController 的 /{id}/online|offline。
 */
@Tag(name = "管理台", description = "管理员：面经审核与状态管理")
@RestController
@RequestMapping("/api/v1/admin/posts")
public class AdminPostController {

    private final ArticleService articleService;

    public AdminPostController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(summary = "文章管理列表", description = "仅管理员；status 可选（REVIEW/ONLINE/OFFLINE），缺省返回全部状态")
    @Permission(UserRole.ADMIN)
    @GetMapping
    public ResVo<List<AdminPostItemVo>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") Integer limit) {
        return ResVo.ok(articleService.adminList(status, limit));
    }

    @Operation(summary = "置顶/取消置顶", description = "仅管理员；topped=true 置顶 / false 取消；写列表排序第一键，即时生效")
    @Permission(UserRole.ADMIN)
    @PostMapping("/{id}/topping")
    public ResVo<Void> setTopping(@PathVariable Long id, @RequestParam boolean topped) {
        articleService.setTopping(id, topped, me.asikeida.ashore.api.context.ReqContext.loginUser());
        return ResVo.ok();
    }

    @Operation(summary = "删除文章", description = "仅管理员；软删除文章并从公开列表、详情及搜索索引移除")
    @Permission(UserRole.ADMIN)
    @DeleteMapping("/{id}")
    public ResVo<Void> delete(@PathVariable Long id) {
        articleService.delete(id, me.asikeida.ashore.api.context.ReqContext.loginUser());
        return ResVo.ok();
    }
}
