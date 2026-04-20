package me.asikeida.ashore.service.article.service;

import java.io.IOException;
import java.util.List;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.vo.post.SearchItemVo;

/**
 * ES 搜索实现细节接口：仅供门面与索引同步组件调用，不直接暴露给 Controller。
 */
public interface EsSearchService {

    /** 关键词搜索（multi_match title^2 + content，带高亮） */
    List<SearchItemVo> search(String keyword, int limit) throws IOException;

    /** 索引/更新单篇面经文档（status=ONLINE 才入库索引，其余删除） */
    void upsert(Long articleId, String title, String content, ArticleStatus status);

    /** 从索引移除文档 */
    void delete(Long articleId);
}
