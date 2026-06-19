package me.asikeida.ashore.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.core.permission.UserRole;
import me.asikeida.ashore.service.article.service.ArticleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理台搜索维护端点（仅 ADMIN）：索引版本升级后的全量重建入口。
 * <p>重建把全部 ONLINE 面经重新写入 ES 当前版本索引；量大时为同步阻塞操作，
 * 个人项目规模（数百篇内）可接受。</p>
 */
@Tag(name = "管理台", description = "管理员：面经审核与状态管理")
@RestController
@RequestMapping("/api/v1/admin/search")
public class AdminSearchController {

    private final ArticleService articleService;

    public AdminSearchController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(summary = "重建搜索索引",
            description = "仅管理员；把全部 ONLINE 面经重新同步进 ES 当前版本索引，返回成功写入数")
    @Permission(UserRole.ADMIN)
    @PostMapping("/reindex")
    public ResVo<Integer> reindex() {
        return ResVo.ok(articleService.reindexSearch());
    }
}
