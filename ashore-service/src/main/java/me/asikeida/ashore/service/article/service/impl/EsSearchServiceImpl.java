package me.asikeida.ashore.service.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.vo.post.SearchItemVo;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleTagDO;
import me.asikeida.ashore.service.article.repository.entity.TagDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleTagMapper;
import me.asikeida.ashore.service.article.repository.mapper.TagMapper;
import me.asikeida.ashore.service.article.service.EsSearchService;
import me.asikeida.ashore.service.article.service.SearchProperties;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ES 8.x 搜索实现（elasticsearch-java，懒建连接与索引）。
 * <p>仅 ONLINE 文档入索引；同步/搜索失败由门面捕获降级 LIKE。</p>
 * <p>分词设计（索引 v2）：title/content 主字段 standard 整词精确匹配，另挂
 * {@code .ngram} 子字段（内置 ngram filter 2~8，免装 ik 插件）——解决「mys 搜不到
 * mysql」类前缀/部分词检索：索引侧 ngram 切出 mys 词项，搜索侧保持 standard。
 * 中文整词仍走主字段（T14 ik 分词待办保留）。</p>
 */
@Service
public class EsSearchServiceImpl implements EsSearchService {

    private static final Logger log = LoggerFactory.getLogger(EsSearchServiceImpl.class);

    /** v2：相比 v1 新增 ngram 子字段与自定义分析器；索引名带版本便于切换与回溯 */
    private static final String INDEX = "ashore_post_v2";

    private static final String NGRAM_ANALYZER = "ashore_ngram_analyzer";
    private static final String NGRAM_FILTER = "ashore_ngram_filter";

    private final SearchProperties properties;

    private final ArticleMapper articleMapper;

    private final ArticleTagMapper articleTagMapper;

    private final TagMapper tagMapper;

    private final UserMapper userMapper;

    private volatile ElasticsearchClient client;

    public EsSearchServiceImpl(SearchProperties properties, ArticleMapper articleMapper,
                               ArticleTagMapper articleTagMapper, TagMapper tagMapper, UserMapper userMapper) {
        this.properties = properties;
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.tagMapper = tagMapper;
        this.userMapper = userMapper;
    }

    /** 懒建客户端与索引：search.enabled=false 或 ES 未就绪时不产生连接 */
    private ElasticsearchClient client() throws IOException {
        ElasticsearchClient local = client;
        if (local == null) {
            synchronized (this) {
                if (client == null) {
                    RestClient restClient = RestClient.builder(HttpHost.create(properties.getUrl())).build();
                    client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
                    ensureIndex();
                }
                local = client;
            }
        }
        return local;
    }

    private void ensureIndex() throws IOException {
        if (client.indices().exists(e -> e.index(INDEX)).value()) {
            return;
        }
        client.indices().create(c -> c.index(INDEX)
                .settings(s -> s
                        // ES 默认限制 max_gram - min_gram <= 1（max_ngram_diff=1），2~8 差 6 必须放开
                        .maxNgramDiff(20)
                        .analysis(a -> a
                                .analyzer(NGRAM_ANALYZER, an -> an.custom(cu -> cu
                                        .tokenizer("standard")
                                        .filter("lowercase", NGRAM_FILTER)))
                                .filter(NGRAM_FILTER, tf -> tf
                                        .definition(d -> d.ngram(n -> n.minGram(2).maxGram(8))))))
                .mappings(m -> m
                        .properties("articleId", p -> p.keyword(k -> k))
                        // 主字段 standard 整词；.ngram 子字段：索引侧切 2~8 gram，搜索侧 standard 整词
                        .properties("title", p -> p.text(t -> t
                                .analyzer("standard")
                                .fields("ngram", f -> f.text(nt -> nt
                                        .analyzer(NGRAM_ANALYZER).searchAnalyzer("standard")))))
                        .properties("content", p -> p.text(t -> t
                                .analyzer("standard")
                                .fields("ngram", f -> f.text(nt -> nt
                                        .analyzer(NGRAM_ANALYZER).searchAnalyzer("standard")))))
                        .properties("status", p -> p.keyword(k -> k))));
        log.info("ES 索引 {} 已创建（含 ngram 子字段）", INDEX);
    }

    @Override
    public List<SearchItemVo> search(String keyword, int limit) throws IOException {
        ElasticsearchClient es = client();
        var response = es.search(s -> s
                        .index(INDEX)
                        .size(limit)
                        .query(Query.of(q -> q.bool(b -> b
                                .must(m -> m.multiMatch(mm -> mm
                                        // ngram 子字段权重低一档：整词命中优先，前缀/部分词兜底
                                        .fields("title^3", "title.ngram", "content", "content.ngram")
                                        .query(keyword)))
                                .filter(f -> f.term(t -> t.field("status").value(FieldValue.of(
                                        ArticleStatus.ONLINE.name())))))))
                        .highlight(h -> h
                                .fields("title", hf -> hf.preTags("<em>").postTags("</em>"))
                                .fields("title.ngram", hf -> hf.preTags("<em>").postTags("</em>"))
                                .fields("content", hf -> hf.preTags("<em>").postTags("</em>")
                                        .fragmentSize(80).numberOfFragments(1))),
                Map.class);

        List<Long> articleIds = response.hits().hits().stream()
                .map(hit -> Long.parseLong(hit.id()))
                .toList();
        if (articleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleDO> articles = articleMapper.selectBatchIds(articleIds).stream()
                .collect(Collectors.toMap(ArticleDO::getId, a -> a));
        Map<Long, List<String>> tagsByArticle = tagsByArticle(articleIds);
        Map<Long, String> nicknames = userMapper.selectBatchIds(
                        articles.values().stream().map(ArticleDO::getAuthorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserDO::getId,
                        u -> u.getNickname() == null ? "岸友" : u.getNickname(),
                        (a, b) -> a, HashMap::new));

        return response.hits().hits().stream()
                .filter(hit -> articles.containsKey(Long.parseLong(hit.id())))
                .map(hit -> {
                    ArticleDO article = articles.get(Long.parseLong(hit.id()));
                    List<String> titleHits = hit.highlight() == null
                            ? List.of() : hit.highlight().getOrDefault("title", List.of());
                    if (titleHits.isEmpty() && hit.highlight() != null) {
                        titleHits = hit.highlight().getOrDefault("title.ngram", List.of());
                    }
                    List<String> contentHits = hit.highlight() == null
                            ? List.of() : hit.highlight().getOrDefault("content", List.of());
                    return new SearchItemVo(
                            String.valueOf(article.getId()), article.getTitle(),
                            tagsByArticle.getOrDefault(article.getId(), List.of()),
                            nicknames.getOrDefault(article.getAuthorId(), "岸友"),
                            article.getCreateTime() == null ? null : article.getCreateTime().toString(),
                            titleHits.isEmpty() ? null : titleHits.get(0),
                            contentHits.isEmpty() ? null : contentHits.get(0));
                })
                .toList();
    }

    @Override
    public void upsert(Long articleId, String title, String content, ArticleStatus status) {
        try {
            ElasticsearchClient es = client();
            if (status != ArticleStatus.ONLINE) {
                es.delete(d -> d.index(INDEX).id(String.valueOf(articleId)));
                return;
            }
            es.index(i -> i.index(INDEX).id(String.valueOf(articleId))
                    .document(Map.of("articleId", String.valueOf(articleId),
                            "title", title, "content", content,
                            "status", status.name())));
        } catch (Exception e) {
            // 索引同步是旁路：失败仅告警（ES 故障时搜索自动降级 LIKE）
            log.warn("ES 文档同步失败 articleId={}: {}", articleId, e.getMessage());
        }
    }

    @Override
    public void delete(Long articleId) {
        try {
            client().delete(d -> d.index(INDEX).id(String.valueOf(articleId)));
        } catch (Exception e) {
            log.warn("ES 文档删除失败 articleId={}: {}", articleId, e.getMessage());
        }
    }

    /** 标签批量组装（两次批量查询防 N+1） */
    private Map<Long, List<String>> tagsByArticle(List<Long> articleIds) {
        List<ArticleTagDO> links = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTagDO>()
                .in(ArticleTagDO::getArticleId, articleIds));
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> tagNames = tagMapper.selectBatchIds(
                        links.stream().map(ArticleTagDO::getTagId).distinct().toList())
                .stream().collect(Collectors.toMap(TagDO::getId, TagDO::getName));
        Map<Long, List<String>> result = new HashMap<>();
        for (ArticleTagDO link : links) {
            String name = tagNames.get(link.getTagId());
            if (name != null) {
                result.computeIfAbsent(link.getArticleId(), k -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }
}
