package me.asikeida.ashore.web.article;

import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.post.PostPageVo;
import me.asikeida.ashore.api.vo.post.PostVersionVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.article.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面经读端点：公开列表（游标分页/标签筛选）与版本历史（作者/管理员）。
 */
@Tag(name = "面经", description = "面经发布、编辑、审核状态流转与详情")
@RestController
@RequestMapping("/api/v1/posts")
public class PostQueryController {

    private final ArticleService articleService;

    public PostQueryController(ArticleService articleService) {
        this.articleService = articleService;
    }

    /** 公开列表：仅 ONLINE；cursor 原样回传即取下一页 */
    @Operation(summary = "面经列表", description = "公开；仅已上线；三字段游标分页（nextCursor 回传即下一页）；支持标签筛选")
    @GetMapping
    public ResVo<PostPageVo> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String tag) {
        return ResVo.ok(articleService.list(cursor, limit, tag));
    }

    /** 正文版本历史（作者/管理员，新版本在前） */
    @Operation(summary = "版本历史", description = "仅作者/管理员；正文各版本按新到旧排列")
    @Permission
    @GetMapping("/{id}/versions")
    public ResVo<List<PostVersionVo>> versions(@PathVariable Long id) {
        LoginUser operator = ReqContext.loginUser();
        return ResVo.ok(articleService.versions(id, operator));
    }
}
