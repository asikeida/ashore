package me.asikeida.ashore.web.article;

import java.util.List;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.post.SearchItemVo;
import me.asikeida.ashore.service.article.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面经搜索端点（公开）：ES 主路径（带高亮）+ MySQL LIKE 降级（调用方无感）。
 */
@Tag(name = "面经", description = "面经发布、编辑、审核状态流转与详情")
@RestController
@RequestMapping("/api/v1/posts")
public class PostSearchController {

    private final SearchService searchService;

    public PostSearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "搜索面经", description = "公开；标题+正文关键词；ES 不可用时自动降级 LIKE")
    @GetMapping("/search")
    public ResVo<List<SearchItemVo>> search(
            @RequestParam("q") String q,
            @RequestParam(required = false) Integer limit) {
        return ResVo.ok(searchService.search(q, limit));
    }
}
