package me.asikeida.ashore.service.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.vo.post.SearchItemVo;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.article.service.ArticleService;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import org.springframework.stereotype.Component;

/**
 * LIKE 降级路径：标题 LIKE 或 正文版本表 LIKE（in 子查询命中面经）。
 * 与 ES 路径结果形态一致；高亮片段为 null。
 */
@Component
public class LikeSearchDelegate {

    private final ArticleMapper articleMapper;

    private final ArticleDetailMapper detailMapper;

    private final UserMapper userMapper;

    private final ArticleService articleService;

    public LikeSearchDelegate(ArticleMapper articleMapper, ArticleDetailMapper detailMapper,
                              UserMapper userMapper, ArticleService articleService) {
        this.articleMapper = articleMapper;
        this.detailMapper = detailMapper;
        this.userMapper = userMapper;
        this.articleService = articleService;
    }

    public List<SearchItemVo> search(String keyword, int limit) {
        // 正文命中：版本表 LIKE → 面经 id 集合
        List<Long> contentMatchIds = detailMapper.selectList(new LambdaQueryWrapper<ArticleDetailDO>()
                        .like(ArticleDetailDO::getContent, keyword))
                .stream().map(ArticleDetailDO::getArticleId).distinct().toList();

        LambdaQueryWrapper<ArticleDO> wrapper = new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getStatus, ArticleStatus.ONLINE.name())
                .eq(ArticleDO::getDeleted, 0)
                .and(w -> {
                    w.like(ArticleDO::getTitle, keyword);
                    if (!contentMatchIds.isEmpty()) {
                        w.or().in(ArticleDO::getId, contentMatchIds);
                    }
                })
                .orderByDesc(ArticleDO::getCreateTime)
                .last("LIMIT " + limit);

        List<ArticleDO> articles = articleMapper.selectList(wrapper);
        if (articles.isEmpty()) {
            return List.of();
        }

        List<Long> articleIds = articles.stream().map(ArticleDO::getId).toList();
        Map<Long, List<String>> tagsByArticle = articleService.tagsFor(articleIds);
        Map<Long, String> nicknames = userMapper.selectBatchIds(
                        articles.stream().map(ArticleDO::getAuthorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserDO::getId,
                        u -> u.getNickname() == null ? "岸友" : u.getNickname()));

        return articles.stream().map(a -> new SearchItemVo(
                String.valueOf(a.getId()), a.getTitle(),
                tagsByArticle.getOrDefault(a.getId(), List.of()),
                nicknames.getOrDefault(a.getAuthorId(), "岸友"),
                a.getCreateTime() == null ? null : a.getCreateTime().toString(),
                null, null)).toList();
    }
}
