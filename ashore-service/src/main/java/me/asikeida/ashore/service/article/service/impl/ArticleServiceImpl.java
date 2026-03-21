package me.asikeida.ashore.service.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.vo.post.EditPostReq;
import me.asikeida.ashore.api.vo.post.PostDetailVo;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;

    private final ArticleDetailMapper detailMapper;

    private final TagMapper tagMapper;

    private final ArticleTagMapper articleTagMapper;

    public ArticleServiceImpl(ArticleMapper articleMapper, ArticleDetailMapper detailMapper,
                              TagMapper tagMapper, ArticleTagMapper articleTagMapper) {
        this.articleMapper = articleMapper;
        this.detailMapper = detailMapper;
        this.tagMapper = tagMapper;
        this.articleTagMapper = articleTagMapper;
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
        return toVo(article, tagNames(articleId));
    }

    @Override
    public void online(Long articleId, LoginUser operator) {
        ArticleDO article = requireManaged(articleId, operator);
        transition(article, ArticleStatus.ONLINE);
    }

    @Override
    public void offline(Long articleId, LoginUser operator) {
        ArticleDO article = requireManaged(articleId, operator);
        transition(article, ArticleStatus.OFFLINE);
    }

    @Override
    public void submit(Long articleId, LoginUser operator) {
        ArticleDO article = requireManaged(articleId, operator);
        if (!article.getAuthorId().equals(operator.userId())) {
            throw ExceptionUtil.of(StatusEnum.FORBIDDEN);
        }
        transition(article, ArticleStatus.REVIEW);
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
        return new PostDetailVo(String.valueOf(article.getId()), article.getTitle(), article.getStatus(),
                tagNames(articleId), latest == null ? null : latest.getContent(),
                latest == null ? null : latest.getVersion(),
                String.valueOf(article.getAuthorId()),
                article.getCreateTime() == null ? null : article.getCreateTime().toString());
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
}
