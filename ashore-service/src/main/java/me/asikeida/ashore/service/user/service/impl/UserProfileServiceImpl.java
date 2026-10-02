package me.asikeida.ashore.service.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.vo.user.UserProfileVo;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.article.service.ArticleService;
import me.asikeida.ashore.service.interact.repository.entity.ArticleCountDO;
import me.asikeida.ashore.service.interact.repository.mapper.ArticleCountMapper;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import me.asikeida.ashore.service.user.service.UserProfileService;
import org.springframework.stereotype.Service;

/**
 * 他人主页实现：一次用户查询 + 一次文章查询 + 一次计数批量查询。
 * 封禁用户主页照常可访问（内容只有 ONLINE 的），封禁语义体现在其本人无法登录。
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final int ARTICLE_LIMIT = 50;

    private final UserMapper userMapper;

    private final ArticleMapper articleMapper;

    private final ArticleCountMapper articleCountMapper;

    private final ArticleService articleService;

    public UserProfileServiceImpl(UserMapper userMapper, ArticleMapper articleMapper,
                                  ArticleCountMapper articleCountMapper, ArticleService articleService) {
        this.userMapper = userMapper;
        this.articleMapper = articleMapper;
        this.articleCountMapper = articleCountMapper;
        this.articleService = articleService;
    }

    @Override
    public UserProfileVo profile(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND, "用户不存在");
        }

        List<ArticleDO> articles = articleMapper.selectList(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getAuthorId, userId)
                .eq(ArticleDO::getStatus, "ONLINE")
                .eq(ArticleDO::getDeleted, 0)
                .orderByDesc(ArticleDO::getToppingStat)
                .orderByDesc(ArticleDO::getCreateTime)
                .last("LIMIT " + ARTICLE_LIMIT));

        List<Long> ids = articles.stream().map(ArticleDO::getId).toList();
        Map<Long, List<String>> tagsByArticle = articleService.tagsFor(ids);

        // 计数批量取（article_count 聚合表，一次 IN 查询）并汇总
        long totalRead = 0;
        long totalPraise = 0;
        Map<Long, ArticleCountDO> countById = ids.isEmpty() ? Map.of()
                : articleCountMapper.selectList(new LambdaQueryWrapper<ArticleCountDO>()
                        .in(ArticleCountDO::getArticleId, ids))
                .stream()
                .collect(java.util.stream.Collectors.toMap(ArticleCountDO::getArticleId, c -> c, (a, b) -> a));
        for (Long id : ids) {
            ArticleCountDO c = countById.get(id);
            if (c != null) {
                totalRead += c.getReadCount() == null ? 0 : c.getReadCount();
                totalPraise += c.getPraiseCount() == null ? 0 : c.getPraiseCount();
            }
        }

        List<UserProfileVo.PostCard> cards = articles.stream()
                .map(a -> new UserProfileVo.PostCard(
                        String.valueOf(a.getId()), a.getTitle(),
                        tagsByArticle.getOrDefault(a.getId(), List.of()),
                        a.getCreateTime() == null ? null : a.getCreateTime().toString(),
                        a.getToppingStat() != null && a.getToppingStat() == 1))
                .toList();

        return new UserProfileVo(
                String.valueOf(user.getId()),
                user.getNickname() == null ? "岸友" : user.getNickname(),
                user.getAvatar(),
                user.getRole(),
                user.getCreateTime() == null ? null : user.getCreateTime().toString(),
                cards.size(), totalRead, totalPraise, cards);
    }
}
