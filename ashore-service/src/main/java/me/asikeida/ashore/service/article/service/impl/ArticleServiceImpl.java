package me.asikeida.ashore.service.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.enums.FootTypeEnum;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.vo.post.EditPostReq;
import me.asikeida.ashore.api.vo.post.PostDetailVo;
import me.asikeida.ashore.api.vo.post.AdminPostItemVo;
import me.asikeida.ashore.api.vo.post.PostItemVo;
import me.asikeida.ashore.api.vo.post.PostPageVo;
import me.asikeida.ashore.api.vo.post.PostVersionVo;
import me.asikeida.ashore.api.vo.post.PublishPostReq;
import me.asikeida.ashore.api.vo.post.PostVo;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleTagDO;
import me.asikeida.ashore.service.article.repository.entity.TagDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleTagMapper;
import me.asikeida.ashore.service.article.repository.mapper.TagMapper;
import me.asikeida.ashore.service.article.service.ArticleService;
import me.asikeida.ashore.service.article.service.EsSearchService;
import me.asikeida.ashore.service.interact.repository.entity.UserFootDO;
import me.asikeida.ashore.service.interact.repository.mapper.UserFootMapper;
import me.asikeida.ashore.api.model.event.NotifyMsgEvent;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.service.interact.service.CountService;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleServiceImpl implements ArticleService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ArticleServiceImpl.class);

    private final ArticleMapper articleMapper;

    private final ArticleDetailMapper detailMapper;

    private final TagMapper tagMapper;

    private final ArticleTagMapper articleTagMapper;

    private final UserMapper userMapper;

    private final CountService countService;

    private final UserFootMapper footMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final me.asikeida.ashore.service.interact.service.RankService rankService;

    private final EsSearchService esSearchService;

    public ArticleServiceImpl(ArticleMapper articleMapper, ArticleDetailMapper detailMapper,
                              TagMapper tagMapper, ArticleTagMapper articleTagMapper, UserMapper userMapper,
                              CountService countService, UserFootMapper footMapper,
                              ApplicationEventPublisher eventPublisher,
                              me.asikeida.ashore.service.interact.service.RankService rankService,
                              EsSearchService esSearchService) {
        this.articleMapper = articleMapper;
        this.detailMapper = detailMapper;
        this.tagMapper = tagMapper;
        this.articleTagMapper = articleTagMapper;
        this.userMapper = userMapper;
        this.countService = countService;
        this.footMapper = footMapper;
        this.eventPublisher = eventPublisher;
        this.rankService = rankService;
        this.esSearchService = esSearchService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVo publish(PublishPostReq req, LoginUser author) {
        ArticleDO article = new ArticleDO();
        article.setTitle(req.title().trim());
        article.setAuthorId(author.userId());
        // 审核制：所有人发文进 REVIEW（无白名单，简化 paicoding 逻辑）
        article.setStatus(ArticleStatus.REVIEW.name());
        article.setToppingStat(0);
        article.setDeleted(0);
        articleMapper.insert(article);

        insertDetail(article.getId(), req.content(), 1);
        bindTags(article.getId(), req.tags());
        rankService.record(author.userId(), me.asikeida.ashore.api.enums.RankActionEnum.PUBLISH,
                article.getId());
        syncIndex(article, req.content());

        return toVo(article, tagNames(article.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVo edit(Long articleId, EditPostReq req, LoginUser operator) {
        ArticleDO article = requireManaged(articleId, operator);

        // 版本化：每次编辑插新行，历史正文全部保留
        int nextVersion = nextVersion(articleId);
        insertDetail(articleId, req.content(), nextVersion);

        article.setTitle(req.title().trim());
        // 已上线内容编辑后回审核（状态机矩阵内聚在枚举）
        ArticleStatus current = ArticleStatus.of(article.getStatus());
        if (current.canTransitionTo(ArticleStatus.REVIEW) && current != ArticleStatus.REVIEW) {
            article.setStatus(ArticleStatus.REVIEW.name());
        }
        articleMapper.updateById(article);

        bindTags(articleId, req.tags());
        syncIndex(article, req.content());
        return toVo(article, tagNames(articleId));
    }

    @Override
    public void online(Long articleId, LoginUser operator) {
        ArticleDO article = requireManaged(articleId, operator);
        transition(article, ArticleStatus.ONLINE);
        syncIndex(article, null);
        // 审核结果通知（SYSTEM）：即使作者当前角色是管理员也照常通知
        eventPublisher.publishEvent(new NotifyMsgEvent(article.getAuthorId(), operator.userId(),
                NotifyTypeEnum.SYSTEM, articleId, null, "你发布的面经《" + article.getTitle() + "》已审核通过上线"));
    }

    @Override
    public void offline(Long articleId, LoginUser operator) {
        ArticleDO article = requireManaged(articleId, operator);
        transition(article, ArticleStatus.OFFLINE);
        syncIndex(article, null);
    }

    @Override
    public void submit(Long articleId, LoginUser operator) {
        ArticleDO article = requireManaged(articleId, operator);
        if (!article.getAuthorId().equals(operator.userId())) {
            throw ExceptionUtil.of(StatusEnum.FORBIDDEN);
        }
        transition(article, ArticleStatus.REVIEW);
        syncIndex(article, null);
    }

    @Override
    public PostDetailVo detail(Long articleId, LoginUser operator) {
        ArticleDO article = requireArticle(articleId);
        ArticleStatus status = ArticleStatus.of(article.getStatus());
        // 可见性：非上线仅作者/管理员可见（NOT_FOUND 对外，防状态枚举）
        boolean privileged = operator != null
                && (operator.isAdmin() || article.getAuthorId().equals(operator.userId()));
        if (status != ArticleStatus.ONLINE && !privileged) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }
        ArticleDetailDO latest = latestDetail(articleId);
        me.asikeida.ashore.service.interact.service.CountService.Counts counts = countService.counts(articleId);
        incrReadQuietly(articleId, operator);
        return new PostDetailVo(String.valueOf(article.getId()), article.getTitle(), article.getStatus(),
                tagNames(articleId), latest == null ? null : latest.getContent(),
                latest == null ? null : latest.getVersion(),
                String.valueOf(article.getAuthorId()),
                article.getCreateTime() == null ? null : article.getCreateTime().toString(),
                counts.praise(), counts.collect(), counts.comment(), counts.read(),
                operator != null && isActiveFoot(articleId, FootTypeEnum.PRAISE, operator.userId()),
                operator != null && isActiveFoot(articleId, FootTypeEnum.COLLECT, operator.userId()));
    }

    /** 当前用户对该面经的足迹是否有效（未登录返回 false） */
    private boolean isActiveFoot(Long articleId, FootTypeEnum type, Long userId) {
        UserFootDO row = footMapper.selectOne(new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getUserId, userId)
                .eq(UserFootDO::getArticleId, articleId)
                .eq(UserFootDO::getType, type.name()));
        return row != null && row.getState() == 1;
    }

    /** 索引同步：旁路（ES 故障时搜索自动降级 LIKE）；REVIEW/OFFLINE 从索引移除 */
    private void syncIndex(ArticleDO article, String content) {
        ArticleStatus status = ArticleStatus.of(article.getStatus());
        String body = content;
        if (status == ArticleStatus.ONLINE && body == null) {
            ArticleDetailDO latest = latestDetail(article.getId());
            body = latest == null ? "" : latest.getContent();
        }
        esSearchService.upsert(article.getId(), article.getTitle(),
                body == null ? "" : body, status);
    }

    /** 详情阅读计数与排行：仅登录用户计（防匿名刷数），旁路失败不反压 */
    private void incrReadQuietly(Long articleId, LoginUser operator) {
        if (operator == null) {
            return;
        }
        try {
            countService.incrRead(articleId, 1);
            rankService.record(operator.userId(),
                    me.asikeida.ashore.api.enums.RankActionEnum.READ, articleId);
        } catch (Exception e) {
            // 阅读计数是旁路
        }
    }

    @Override
    public PostPageVo list(String cursor, Integer limit, String tagName) {
        int pageSize = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);

        LambdaQueryWrapper<ArticleDO> wrapper = new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getStatus, ArticleStatus.ONLINE.name())
                .eq(ArticleDO::getDeleted, 0);

        if (tagName != null && !tagName.isBlank()) {
            TagDO tag = tagMapper.selectOne(new LambdaQueryWrapper<TagDO>().eq(TagDO::getName, tagName.trim()));
            if (tag == null) {
                return new PostPageVo(null, List.of());
            }
            List<Long> ids = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTagDO>()
                            .eq(ArticleTagDO::getTagId, tag.getId()))
                    .stream().map(ArticleTagDO::getArticleId).toList();
            if (ids.isEmpty()) {
                return new PostPageVo(null, List.of());
            }
            wrapper.in(ArticleDO::getId, ids);
        }

        if (cursor != null && !cursor.isBlank()) {
            CursorKey key = parseCursor(cursor);
            // 三字段游标：(topping, create_time, id) 整体小于游标，杜绝深分页 offset 扫描
            wrapper.and(w -> w
                    .lt(ArticleDO::getToppingStat, key.topping())
                    .or(x -> x.eq(ArticleDO::getToppingStat, key.topping())
                            .lt(ArticleDO::getCreateTime, key.createTime()))
                    .or(x -> x.eq(ArticleDO::getToppingStat, key.topping())
                            .eq(ArticleDO::getCreateTime, key.createTime())
                            .lt(ArticleDO::getId, key.id())));
        }

        wrapper.orderByDesc(ArticleDO::getToppingStat)
                .orderByDesc(ArticleDO::getCreateTime)
                .orderByDesc(ArticleDO::getId)
                .last("LIMIT " + pageSize);

        List<ArticleDO> articles = articleMapper.selectList(wrapper);
        if (articles.isEmpty()) {
            return new PostPageVo(null, List.of());
        }

        List<Long> articleIds = articles.stream().map(ArticleDO::getId).toList();
        Map<Long, List<String>> tagsByArticle = tagsByArticle(articleIds);
        Map<Long, String> nicknameByAuthor = nicknamesByAuthor(
                articles.stream().map(ArticleDO::getAuthorId).distinct().toList());

        List<PostItemVo> items = articles.stream()
                .map(a -> new PostItemVo(String.valueOf(a.getId()), a.getTitle(),
                        tagsByArticle.getOrDefault(a.getId(), List.of()),
                        nicknameByAuthor.get(a.getAuthorId()),
                        a.getCreateTime() == null ? null : a.getCreateTime().toString()))
                .toList();

        String nextCursor = articles.size() == pageSize ? toCursor(articles.get(articles.size() - 1)) : null;
        return new PostPageVo(nextCursor, items);
    }

    @Override
    public List<PostVersionVo> versions(Long articleId, LoginUser operator) {
        requireManaged(articleId, operator);
        return detailMapper.selectList(new LambdaQueryWrapper<ArticleDetailDO>()
                        .eq(ArticleDetailDO::getArticleId, articleId)
                        .orderByDesc(ArticleDetailDO::getVersion))
                .stream()
                .map(d -> new PostVersionVo(d.getVersion(),
                        d.getCreateTime() == null ? null : d.getCreateTime().toString(), d.getContent()))
                .toList();
    }

    // ---------- 内部 ----------

    private void transition(ArticleDO article, ArticleStatus target) {
        ArticleStatus current = ArticleStatus.of(article.getStatus());
        if (!current.canTransitionTo(target)) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_STATE);
        }
        article.setStatus(target.name());
        articleMapper.updateById(article);
    }

    /** 载入未删除面经；不存在抛 NOT_FOUND */
    private ArticleDO requireArticle(Long articleId) {
        ArticleDO article = articleMapper.selectOne(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getId, articleId)
                .eq(ArticleDO::getDeleted, 0));
        if (article == null) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }
        return article;
    }

    /** 载入并校验操作者：作者或管理员，否则 FORBIDDEN */
    private ArticleDO requireManaged(Long articleId, LoginUser operator) {
        ArticleDO article = requireArticle(articleId);
        if (!article.getAuthorId().equals(operator.userId()) && !operator.isAdmin()) {
            throw ExceptionUtil.of(StatusEnum.FORBIDDEN);
        }
        return article;
    }

    private int nextVersion(Long articleId) {
        ArticleDetailDO latest = latestDetail(articleId);
        return latest == null ? 1 : latest.getVersion() + 1;
    }

    private ArticleDetailDO latestDetail(Long articleId) {
        return detailMapper.selectOne(new LambdaQueryWrapper<ArticleDetailDO>()
                .eq(ArticleDetailDO::getArticleId, articleId)
                .orderByDesc(ArticleDetailDO::getVersion)
                .last("LIMIT 1"));
    }

    private void insertDetail(Long articleId, String content, int version) {
        ArticleDetailDO detail = new ArticleDetailDO();
        detail.setArticleId(articleId);
        detail.setContent(content);
        detail.setVersion(version);
        detailMapper.insert(detail);
    }

    /** 标签全删重插：名称去空格去重；不存在自动创建（并发重名由唯一索引兜底后重查） */
    private void bindTags(Long articleId, List<String> tagNames) {
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTagDO>()
                .eq(ArticleTagDO::getArticleId, articleId));
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String name : tagNames) {
            if (name != null && !name.isBlank()) {
                distinct.add(name.trim());
            }
        }
        for (String name : distinct) {
            TagDO tag = findOrCreateTag(name);
            ArticleTagDO link = new ArticleTagDO();
            link.setArticleId(articleId);
            link.setTagId(tag.getId());
            articleTagMapper.insert(link);
        }
    }

    private TagDO findOrCreateTag(String name) {
        TagDO existing = tagMapper.selectOne(new LambdaQueryWrapper<TagDO>().eq(TagDO::getName, name));
        if (existing != null) {
            return existing;
        }
        TagDO tag = new TagDO();
        tag.setName(name);
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException e) {
            // 并发创建同名标签：唯一索引兜底后重查
            return tagMapper.selectOne(new LambdaQueryWrapper<TagDO>().eq(TagDO::getName, name));
        }
        return tag;
    }

    private List<String> tagNames(Long articleId) {
        List<ArticleTagDO> links = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTagDO>()
                .eq(ArticleTagDO::getArticleId, articleId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = links.stream().map(ArticleTagDO::getTagId).toList();
        List<String> names = new ArrayList<>();
        for (TagDO tag : tagMapper.selectBatchIds(tagIds)) {
            names.add(tag.getName());
        }
        return names;
    }

    private PostVo toVo(ArticleDO article, List<String> tags) {
        return new PostVo(String.valueOf(article.getId()), article.getTitle(), article.getStatus(),
                tags, article.getCreateTime() == null ? null : article.getCreateTime().toString());
    }

    @Override
    public Map<Long, List<String>> tagsFor(List<Long> articleIds) {
        return tagsByArticle(articleIds);
    }

    @Override
    public List<AdminPostItemVo> adminList(String status, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), 100);
        LambdaQueryWrapper<ArticleDO> wrapper = new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getDeleted, 0)
                .orderByDesc(ArticleDO::getCreateTime)
                .last("LIMIT " + safeLimit);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ArticleDO::getStatus, status);
        }
        List<ArticleDO> articles = articleMapper.selectList(wrapper);
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> articleIds = articles.stream().map(ArticleDO::getId).toList();
        Map<Long, List<String>> tagsByArticle = tagsByArticle(articleIds);
        Map<Long, String> nicknameByAuthor = nicknamesByAuthor(
                articles.stream().map(ArticleDO::getAuthorId).distinct().toList());
        return articles.stream()
                .map(a -> AdminPostItemVo.of(new PostItemVo(String.valueOf(a.getId()), a.getTitle(),
                        tagsByArticle.getOrDefault(a.getId(), List.of()),
                        nicknameByAuthor.getOrDefault(a.getAuthorId(), "岸友"),
                        a.getCreateTime() == null ? null : a.getCreateTime().toString()), a.getStatus()))
                .toList();
    }

    @Override
    public List<PostItemVo> listByIds(java.util.Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }
        List<ArticleDO> articles = articleMapper.selectBatchIds(articleIds).stream()
                .filter(a -> a.getDeleted() == 0)
                .toList();
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articles.stream().map(ArticleDO::getId).toList();
        Map<Long, List<String>> tagsByArticle = tagsByArticle(ids);
        Map<Long, String> nicknameByAuthor = nicknamesByAuthor(
                articles.stream().map(ArticleDO::getAuthorId).distinct().toList());
        return articles.stream()
                .map(a -> new PostItemVo(String.valueOf(a.getId()), a.getTitle(),
                        tagsByArticle.getOrDefault(a.getId(), List.of()),
                        nicknameByAuthor.getOrDefault(a.getAuthorId(), "岸友"),
                        a.getCreateTime() == null ? null : a.getCreateTime().toString()))
                .toList();
    }

    @Override
    public int reindexSearch() {
        List<ArticleDO> onlineArticles = articleMapper.selectList(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getDeleted, 0)
                .eq(ArticleDO::getStatus, ArticleStatus.ONLINE.name()));
        int synced = 0;
        for (ArticleDO article : onlineArticles) {
            // syncIndex 内部会取最新正文版本；单篇失败仅告警不中断（旁路纪律）
            try {
                syncIndex(article, null);
                synced++;
            } catch (Exception e) {
                log.warn("重索引单篇失败 articleId={}: {}", article.getId(), e.getMessage());
            }
        }
        return synced;
    }

    // ---------- 游标与批量组装 ----------

    /** 游标明文格式：{topping}_{createTimeISO}_{id}（ISO 时间无下划线，可无损往返） */
    private record CursorKey(int topping, LocalDateTime createTime, long id) {
    }

    private CursorKey parseCursor(String cursor) {
        String[] parts = cursor.split("_", 3);
        if (parts.length != 3) {
            throw ExceptionUtil.of(StatusEnum.PARAM_ERROR);
        }
        try {
            return new CursorKey(Integer.parseInt(parts[0]), LocalDateTime.parse(parts[1]),
                    Long.parseLong(parts[2]));
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            throw ExceptionUtil.of(StatusEnum.PARAM_ERROR);
        }
    }

    private String toCursor(ArticleDO article) {
        return article.getToppingStat() + "_" + article.getCreateTime() + "_" + article.getId();
    }

    /** 批量取标签：两次批量查询组装（避免逐条 N+1） */
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

    /** 批量取作者昵称 */
    private Map<Long, String> nicknamesByAuthor(List<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(UserDO::getId, UserDO::getNickname, (a, b) -> a,
                        () -> new HashMap<>()));
    }
}
