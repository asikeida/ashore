package me.asikeida.ashore.web.article;

import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.post.PostPageVo;
import me.asikeida.ashore.api.vo.post.PostVersionVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.article.service.ArticleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面经读端点：公开列表（游标分页/标签筛选）与版本历史（作者/管理员）。
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostQueryController {

    private final ArticleService articleService;

    public PostQueryController(ArticleService articleService) {
        this.articleService = articleService;
    }

    /** 公开列表：仅 ONLINE；cursor 原样回传即取下一页 */
    @GetMapping
    public ResVo<PostPageVo> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String tag) {
        return ResVo.ok(articleService.list(cursor, limit, tag));
    }

    /** 正文版本历史（作者/管理员，新版本在前） */
    @Permission
    @GetMapping("/{id}/versions")
    public ResVo<List<PostVersionVo>> versions(@PathVariable Long id) {
        LoginUser operator = ReqContext.loginUser();
        return ResVo.ok(articleService.versions(id, operator));
    }
}
