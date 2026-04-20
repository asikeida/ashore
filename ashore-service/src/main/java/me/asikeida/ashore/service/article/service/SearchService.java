package me.asikeida.ashore.service.article.service;

import java.util.List;
import me.asikeida.ashore.api.vo.post.SearchItemVo;

/**
 * 面经搜索：ES 主路径 + MySQL LIKE 降级。
 * <p>降级语义：ES 未启用（search.enabled=false）或调用异常时自动回退 LIKE，
 * 调用方无感（结果形态一致；LIKE 路径无高亮片段）。</p>
 */
public interface SearchService {

    /**
     * 关键词搜索（仅 ONLINE 且未删除）。
     *
     * @param keyword 关键词（匹配标题与正文）
     * @param limit   返回条数，缺省 20，上限 50
     */
    List<SearchItemVo> search(String keyword, Integer limit);
}
