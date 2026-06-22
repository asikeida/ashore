package me.asikeida.ashore.service.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.enums.FootTypeEnum;
import me.asikeida.ashore.api.vo.profile.MeFootItemVo;
import me.asikeida.ashore.api.vo.profile.MeInterviewItemVo;
import me.asikeida.ashore.api.vo.post.PostItemVo;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.InterviewSessionDO;
import me.asikeida.ashore.service.interact.repository.mapper.InterviewSessionMapper;
import me.asikeida.ashore.service.article.service.ArticleService;
import me.asikeida.ashore.service.interact.repository.entity.UserFootDO;
import me.asikeida.ashore.service.interact.repository.mapper.UserFootMapper;
import me.asikeida.ashore.service.profile.service.MeService;
import org.springframework.stereotype.Service;

/**
 * 个人中心实现：foot/session 两次批量查询取文章卡片，杜绝逐条 N+1。
 * 足迹按动作时间（update_time）倒序——最近复习的动作排最前。
 */
@Service
public class MeServiceImpl implements MeService {

    private static final int FOOT_LIMIT = 50;
    private static final int INTERVIEW_LIMIT = 20;

    private final UserFootMapper footMapper;

    private final ArticleMapper articleMapper;

    private final InterviewSessionMapper interviewSessionMapper;

    private final ArticleService articleService;

    public MeServiceImpl(UserFootMapper footMapper, ArticleMapper articleMapper,
                         InterviewSessionMapper interviewSessionMapper, ArticleService articleService) {
        this.footMapper = footMapper;
        this.articleMapper = articleMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.articleService = articleService;
    }

    @Override
    public List<MeFootItemVo> myCollects(Long userId) {
        return myFoots(userId, FootTypeEnum.COLLECT);
    }

    @Override
    public List<MeFootItemVo> myPraises(Long userId) {
        return myFoots(userId, FootTypeEnum.PRAISE);
    }

    private List<MeFootItemVo> myFoots(Long userId, FootTypeEnum type) {
        List<UserFootDO> foots = footMapper.selectList(new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getUserId, userId)
                .eq(UserFootDO::getType, type.name())
                .eq(UserFootDO::getState, 1)
                .orderByDesc(UserFootDO::getUpdateTime)
                .last("LIMIT " + FOOT_LIMIT));
        if (foots.isEmpty()) {
            return List.of();
        }
        Map<Long, PostItemVo> cardById = articleService.listByIds(
                        foots.stream().map(UserFootDO::getArticleId).toList()).stream()
                .collect(Collectors.toMap(v -> Long.parseLong(v.id()), Function.identity()));
        return foots.stream()
                .filter(f -> cardById.containsKey(f.getArticleId()))
                .map(f -> {
                    PostItemVo card = cardById.get(f.getArticleId());
                    return new MeFootItemVo(card.id(), card.title(), card.tags(),
                            card.authorNickname(),
                            f.getUpdateTime() == null ? null : f.getUpdateTime().toString());
                })
                .toList();
    }

    @Override
    public List<MeInterviewItemVo> myInterviews(Long userId) {
        List<InterviewSessionDO> sessions = interviewSessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSessionDO>()
                        .eq(InterviewSessionDO::getUserId, userId)
                        .orderByDesc(InterviewSessionDO::getUpdateTime)
                        .last("LIMIT " + INTERVIEW_LIMIT));
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleDO> articleById = articleMapper.selectBatchIds(
                        sessions.stream().map(InterviewSessionDO::getArticleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ArticleDO::getId, a -> a, (a, b) -> a));
        return sessions.stream()
                .map(s -> new MeInterviewItemVo(
                        String.valueOf(s.getId()),
                        String.valueOf(s.getArticleId()),
                        articleById.containsKey(s.getArticleId())
                                ? articleById.get(s.getArticleId()).getTitle() : "（面经已删除）",
                        s.getQuestionCount(),
                        s.getStatus(),
                        s.getCreateTime() == null ? null : s.getCreateTime().toString(),
                        s.getUpdateTime() == null ? null : s.getUpdateTime().toString()))
                .toList();
    }
}
