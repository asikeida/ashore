package me.asikeida.ashore.service.article.service.impl;

import java.util.List;
import me.asikeida.ashore.api.vo.post.SearchItemVo;
import me.asikeida.ashore.service.article.service.EsSearchService;
import me.asikeida.ashore.service.article.service.impl.LikeSearchDelegate;
import me.asikeida.ashore.service.article.service.SearchProperties;
import me.asikeida.ashore.service.article.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 搜索门面：ES 可用走 ES（含高亮），未启用/异常自动降级 MySQL LIKE（调用方无感）。
 */
@Primary
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final EsSearchService esSearchService;

    private final LikeSearchDelegate likeSearchDelegate;

    private final SearchProperties properties;

    public SearchServiceImpl(EsSearchService esSearchService, LikeSearchDelegate likeSearchDelegate,
                             SearchProperties properties) {
        this.esSearchService = esSearchService;
        this.likeSearchDelegate = likeSearchDelegate;
        this.properties = properties;
    }

    @Override
    public List<SearchItemVo> search(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        int safeLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
        if (!properties.isEnabled()) {
            return likeSearchDelegate.search(keyword, safeLimit);
        }
        try {
            return esSearchService.search(keyword, safeLimit);
        } catch (Exception e) {
            log.warn("ES 搜索失败，降级 MySQL LIKE：{}", e.getMessage());
            return likeSearchDelegate.search(keyword, safeLimit);
        }
    }
}
