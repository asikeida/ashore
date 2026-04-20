package me.asikeida.ashore.service.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.concurrent.TimeUnit;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.FootTypeEnum;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.UserFootDO;
import me.asikeida.ashore.service.interact.repository.mapper.UserFootMapper;
import me.asikeida.ashore.api.enums.RankActionEnum;
import me.asikeida.ashore.service.interact.service.CountService;
import me.asikeida.ashore.service.interact.service.RankService;
import me.asikeida.ashore.service.interact.service.FootService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.api.model.event.NotifyMsgEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 足迹切换实现：Redisson 锁串行化同一（用户，面经，行为）的"查-改-写"，
 * 修复 paicoding UserFootServiceImpl#compareAndUpdate 的并发覆盖缺陷（原 fixme）。
 * <p>RedissonClient 经 ObjectProvider 延迟获取（@Lazy Bean，无 Redis 的测试上下文不触发创建）。</p>
 */
@Service
public class FootServiceImpl implements FootService {

    private final ObjectProvider<RedissonClient> redissonProvider;

    private final UserFootMapper footMapper;

    private final ArticleMapper articleMapper;

    private final CountService countService;

    private final ApplicationEventPublisher eventPublisher;

    private final RankService rankService;

    public FootServiceImpl(ObjectProvider<RedissonClient> redissonProvider, UserFootMapper footMapper,
                           ArticleMapper articleMapper, CountService countService,
                           ApplicationEventPublisher eventPublisher, RankService rankService) {
        this.redissonProvider = redissonProvider;
        this.footMapper = footMapper;
        this.articleMapper = articleMapper;
        this.countService = countService;
        this.eventPublisher = eventPublisher;
        this.rankService = rankService;
    }

    @Override
    public ToggleResult toggle(Long articleId, FootTypeEnum type, LoginUser user) {
        if (!type.isToggleable()) {
            throw new IllegalArgumentException("仅切换型行为支持 toggle: " + type);
        }
        me.asikeida.ashore.service.article.repository.entity.ArticleDO article = requireArticle(articleId);

        RLock lock = redissonProvider.getObject()
                .getLock("lock:foot:" + user.userId() + ":" + articleId + ":" + type.name());
        boolean locked;
        try {
            // 单参 tryLock = 看门狗模式：持锁期间自动续期，慢操作下锁不会提前过期
            locked = lock.tryLock(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取足迹锁被中断", e);
        }
        if (!locked) {
            // 锁等待超时视为服务繁忙（500 段统一文案）；客户端可重试
            throw ExceptionUtil.of(StatusEnum.ERROR);
        }
        try {
            boolean active = doToggle(articleId, type, user.userId());
            long delta = active ? 1 : -1;
            rankService.record(user.userId(),
                    type == FootTypeEnum.PRAISE
                            ? (active ? RankActionEnum.PRAISE : RankActionEnum.PRAISE_CANCEL)
                            : (active ? RankActionEnum.COLLECT : RankActionEnum.COLLECT_CANCEL),
                    articleId);
            String newCount = switch (type) {
                case PRAISE -> countService.incrPraise(articleId, delta);
                case COLLECT -> countService.incrCollect(articleId, delta);
                default -> throw new IllegalStateException("unreachable");
            };
            // 有效点赞/收藏通知面经作者（自己不通知；重复点赞由监听器去重）
            if (active && !article.getAuthorId().equals(user.userId())) {
                NotifyTypeEnum notifyType = type == FootTypeEnum.PRAISE
                        ? NotifyTypeEnum.PRAISE : NotifyTypeEnum.COLLECT;
                eventPublisher.publishEvent(new NotifyMsgEvent(article.getAuthorId(),
                        user.userId(), notifyType, articleId, null, article.getTitle()));
            }
            return new ToggleResult(active, Long.parseLong(newCount));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isActive(Long articleId, FootTypeEnum type, Long userId) {
        UserFootDO row = footMapper.selectOne(rowQuery(articleId, type, userId));
        return row != null && row.getState() == 1;
    }

    private boolean doToggle(Long articleId, FootTypeEnum type, Long userId) {
        UserFootDO existing = footMapper.selectOne(rowQuery(articleId, type, userId));
        if (existing == null) {
            try {
                footMapper.insert(foot(articleId, type, userId, 1));
                return true;
            } catch (DuplicateKeyException e) {
                // 锁失效/跨节点竞态兜底：唯一索引拦截后翻转既有行
                UserFootDO row = footMapper.selectOne(rowQuery(articleId, type, userId));
                if (row == null) {
                    throw e;
                }
                row.setState(row.getState() == 1 ? 0 : 1);
                footMapper.updateById(row);
                return row.getState() == 1;
            }
        }
        existing.setState(existing.getState() == 1 ? 0 : 1);
        footMapper.updateById(existing);
        return existing.getState() == 1;
    }

    private LambdaQueryWrapper<UserFootDO> rowQuery(Long articleId, FootTypeEnum type, Long userId) {
        return new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getUserId, userId)
                .eq(UserFootDO::getArticleId, articleId)
                .eq(UserFootDO::getType, type.name());
    }

    private UserFootDO foot(Long articleId, FootTypeEnum type, Long userId, int state) {
        UserFootDO foot = new UserFootDO();
        foot.setUserId(userId);
        foot.setArticleId(articleId);
        foot.setType(type.name());
        foot.setState(state);
        return foot;
    }

    private ArticleDO requireArticle(Long articleId) {
        var article = articleMapper.selectOne(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getId, articleId)
                .eq(ArticleDO::getDeleted, 0));
        if (article == null) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }
        return article;
    }
}
