package me.asikeida.ashore.service.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.vo.post.CommentPageVo;
import me.asikeida.ashore.api.vo.post.CommentReplyVo;
import me.asikeida.ashore.api.vo.post.CommentVo;
import me.asikeida.ashore.api.vo.post.CreateCommentReq;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.CommentDO;
import me.asikeida.ashore.service.interact.repository.mapper.CommentMapper;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.api.model.event.NotifyMsgEvent;
import me.asikeida.ashore.api.enums.RankActionEnum;
import me.asikeida.ashore.service.interact.service.CountService;
import me.asikeida.ashore.service.interact.service.RankService;
import me.asikeida.ashore.service.article.service.CommentService;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentServiceImpl implements CommentService {

    private static final long TOP_LEVEL = 0L;

    private final CommentMapper commentMapper;

    private final ArticleMapper articleMapper;

    private final UserMapper userMapper;

    private final CountService countService;

    private final ApplicationEventPublisher eventPublisher;

    private final RankService rankService;

    public CommentServiceImpl(CommentMapper commentMapper, ArticleMapper articleMapper,
                              UserMapper userMapper, CountService countService,
                              ApplicationEventPublisher eventPublisher, RankService rankService) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.countService = countService;
        this.eventPublisher = eventPublisher;
        this.rankService = rankService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyVo create(Long articleId, CreateCommentReq req, LoginUser commenter) {
        ArticleDO article = requireArticle(articleId);

        CommentDO comment = new CommentDO();
        comment.setArticleId(articleId);
        comment.setUserId(commenter.userId());
        comment.setContent(req.content().trim());
        comment.setDeleted(0);

        if (req.parentCommentId() == null) {
            comment.setTopCommentId(TOP_LEVEL);
            comment.setParentCommentId(TOP_LEVEL);
        } else {
            CommentDO parent = requireComment(req.parentCommentId(), articleId);
            comment.setTopCommentId(parent.getTopCommentId() == TOP_LEVEL
                    ? parent.getId() : parent.getTopCommentId());
            comment.setParentCommentId(parent.getId());
            comment.setReplyUserId(parent.getUserId());
        }
        commentMapper.insert(comment);
        countService.incrComment(articleId, 1);
        rankService.record(comment.getUserId(), RankActionEnum.COMMENT, articleId);

        // 通知发布（旁路，异步消费）：回复→被回复人；评论→面经作者（自己操作自己不通知）
        if (comment.getReplyUserId() != null && !comment.getReplyUserId().equals(comment.getUserId())) {
            eventPublisher.publishEvent(new NotifyMsgEvent(comment.getReplyUserId(),
                    comment.getUserId(), NotifyTypeEnum.REPLY, articleId, comment.getId(),
                    comment.getContent()));
        }
        if (!article.getAuthorId().equals(comment.getUserId())) {
            eventPublisher.publishEvent(new NotifyMsgEvent(article.getAuthorId(),
                    comment.getUserId(), NotifyTypeEnum.COMMENT, articleId, comment.getId(),
                    comment.getContent()));
        }

        // 昵称从库补齐（LoginUser 不携带展示字段）
        UserDO commenterRow = userMapper.selectById(comment.getUserId());
        String nickname = commenterRow == null || commenterRow.getNickname() == null
                ? "岸友" : commenterRow.getNickname();
        return toReplyVo(comment, nickname);
    }

    @Override
    public CommentPageVo list(Long articleId, String cursor, Integer pageSize) {
        requireArticle(articleId);
        int size = pageSize == null ? 10 : Math.min(Math.max(pageSize, 1), 50);

        LambdaQueryWrapper<CommentDO> topWrapper = new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getArticleId, articleId)
                .eq(CommentDO::getTopCommentId, TOP_LEVEL)
                .eq(CommentDO::getDeleted, 0);
        if (cursor != null && !cursor.isBlank()) {
            topWrapper.lt(CommentDO::getId, parseCursor(cursor));
        }
        topWrapper.orderByDesc(CommentDO::getId).last("LIMIT " + size);

        List<CommentDO> tops = commentMapper.selectList(topWrapper);
        if (tops.isEmpty()) {
            return new CommentPageVo(null, countTop(articleId), List.of());
        }

        List<Long> topIds = tops.stream().map(CommentDO::getId).toList();
        Map<Long, List<CommentDO>> repliesByTop = repliesByTop(topIds);
        Map<Long, UserDO> usersById = usersById(collectUserIds(tops, repliesByTop));

        List<CommentVo> items = tops.stream().map(top -> new CommentVo(
                String.valueOf(top.getId()),
                String.valueOf(top.getUserId()),
                nickname(usersById, top.getUserId()),
                top.getContent(),
                top.getCreateTime() == null ? null : top.getCreateTime().toString(),
                repliesByTop.getOrDefault(top.getId(), List.of()).stream()
                        .map(reply -> new CommentReplyVo(
                                String.valueOf(reply.getId()),
                                String.valueOf(reply.getUserId()),
                                nickname(usersById, reply.getUserId()),
                                reply.getContent(),
                                reply.getReplyUserId() == null ? null : String.valueOf(reply.getReplyUserId()),
                                reply.getReplyUserId() == null ? null : nickname(usersById, reply.getReplyUserId()),
                                reply.getCreateTime() == null ? null : reply.getCreateTime().toString()))
                        .toList())).toList();

        String nextCursor = tops.size() == size ? String.valueOf(tops.get(tops.size() - 1).getId()) : null;
        return new CommentPageVo(nextCursor, countTop(articleId), items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long commentId, LoginUser operator) {
        CommentDO comment = commentMapper.selectOne(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getId, commentId)
                .eq(CommentDO::getDeleted, 0));
        if (comment == null) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }
        if (!comment.getUserId().equals(operator.userId()) && !operator.isAdmin()) {
            throw ExceptionUtil.of(StatusEnum.FORBIDDEN);
        }
        comment.setDeleted(1);
        commentMapper.updateById(comment);
        countService.incrComment(comment.getArticleId(), -1);
    }

    // ---------- 内部 ----------

    private ArticleDO requireArticle(Long articleId) {
        ArticleDO article = articleMapper.selectOne(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getId, articleId)
                .eq(ArticleDO::getDeleted, 0));
        if (article == null) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }
        return article;
    }

    private CommentDO requireComment(Long commentId, Long articleId) {
        CommentDO comment = commentMapper.selectOne(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getId, commentId)
                .eq(CommentDO::getArticleId, articleId)
                .eq(CommentDO::getDeleted, 0));
        if (comment == null) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }
        return comment;
    }

    private Long parseCursor(String cursor) {
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw ExceptionUtil.of(StatusEnum.PARAM_ERROR);
        }
    }

    private long countTop(Long articleId) {
        return commentMapper.selectCount(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getArticleId, articleId)
                .eq(CommentDO::getTopCommentId, TOP_LEVEL)
                .eq(CommentDO::getDeleted, 0));
    }

    private Map<Long, List<CommentDO>> repliesByTop(List<Long> topIds) {
        return commentMapper.selectList(new LambdaQueryWrapper<CommentDO>()
                        .in(CommentDO::getTopCommentId, topIds)
                        .ne(CommentDO::getParentCommentId, TOP_LEVEL)
                        .eq(CommentDO::getDeleted, 0)
                        .orderByAsc(CommentDO::getId))
                .stream()
                .collect(Collectors.groupingBy(CommentDO::getTopCommentId));
    }

    private Set<Long> collectUserIds(List<CommentDO> tops, Map<Long, List<CommentDO>> repliesByTop) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CommentDO top : tops) {
            ids.add(top.getUserId());
            for (CommentDO reply : repliesByTop.getOrDefault(top.getId(), List.of())) {
                ids.add(reply.getUserId());
                if (reply.getReplyUserId() != null) {
                    ids.add(reply.getReplyUserId());
                }
            }
        }
        return ids;
    }

    private String nickname(Map<Long, UserDO> usersById, Long userId) {
        UserDO user = usersById.get(userId);
        return user == null || user.getNickname() == null ? "岸友" : user.getNickname();
    }

    private Map<Long, UserDO> usersById(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity()));
    }

    private CommentReplyVo toReplyVo(CommentDO comment, String nickname) {
        return new CommentReplyVo(String.valueOf(comment.getId()), String.valueOf(comment.getUserId()),
                nickname, comment.getContent(), null, null,
                comment.getCreateTime() == null ? null : comment.getCreateTime().toString());
    }
}
