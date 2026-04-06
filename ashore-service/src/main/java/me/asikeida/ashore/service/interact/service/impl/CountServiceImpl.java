package me.asikeida.ashore.service.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.asikeida.ashore.api.enums.FootTypeEnum;
import me.asikeida.ashore.service.interact.repository.entity.ArticleCountDO;
import me.asikeida.ashore.service.interact.repository.entity.CommentDO;
import me.asikeida.ashore.service.interact.repository.entity.UserFootDO;
import me.asikeida.ashore.service.interact.repository.mapper.ArticleCountMapper;
import me.asikeida.ashore.service.interact.repository.mapper.CommentMapper;
import me.asikeida.ashore.service.interact.repository.mapper.UserFootMapper;
import me.asikeida.ashore.service.interact.service.CountService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 计数实现：单 hash（ashore:count:article，field = praise:{id} 等）承载实时计数。
 * <p>懒加载兜底：praise/collect 从足迹表聚合、comment 从评论表计数、read 从回写表取——
 * Redis 数据丢失（重启/淘汰）后首次读取即自愈。</p>
 */
@Service
public class CountServiceImpl implements CountService {

    private static final String COUNT_KEY = "ashore:count:article";

    private final StringRedisTemplate redisTemplate;

    private final UserFootMapper footMapper;

    private final CommentMapper commentMapper;

    private final ArticleCountMapper articleCountMapper;

    public CountServiceImpl(StringRedisTemplate redisTemplate, UserFootMapper footMapper,
                            CommentMapper commentMapper, ArticleCountMapper articleCountMapper) {
        this.redisTemplate = redisTemplate;
        this.footMapper = footMapper;
        this.commentMapper = commentMapper;
        this.articleCountMapper = articleCountMapper;
    }

    @Override
    public String incrPraise(Long articleId, long delta) {
        return incr(field("praise", articleId), delta);
    }

    @Override
    public String incrCollect(Long articleId, long delta) {
        return incr(field("collect", articleId), delta);
    }

    @Override
    public String incrComment(Long articleId, long delta) {
        return incr(field("comment", articleId), delta);
    }

    @Override
    public String incrRead(Long articleId, long delta) {
        return incr(field("read", articleId), delta);
    }

    @Override
    public Counts counts(Long articleId) {
        long praise = Long.parseLong(lazyLoad("praise", articleId,
                () -> footCount(articleId, FootTypeEnum.PRAISE)));
        long collect = Long.parseLong(lazyLoad("collect", articleId,
                () -> footCount(articleId, FootTypeEnum.COLLECT)));
        long comment = Long.parseLong(lazyLoad("comment", articleId,
                () -> commentMapper.selectCount(new LambdaQueryWrapper<CommentDO>()
                        .eq(CommentDO::getArticleId, articleId)
                        .eq(CommentDO::getDeleted, 0))));
        long read = Long.parseLong(lazyLoad("read", articleId, () -> readCountFromDb(articleId)));
        return new Counts(praise, collect, comment, read);
    }

    private String field(String type, Long articleId) {
        return type + ":" + articleId;
    }

    private String incr(String field, long delta) {
        return String.valueOf(redisTemplate.opsForHash().increment(COUNT_KEY, field, delta));
    }

    /** 字段缺失时从事实源计算并回填 hash（自愈） */
    private String lazyLoad(String type, Long articleId, java.util.function.Supplier<Long> fallback) {
        Object value = redisTemplate.opsForHash().get(COUNT_KEY, field(type, articleId));
        if (value != null) {
            return String.valueOf(value);
        }
        String computed = String.valueOf(fallback.get());
        redisTemplate.opsForHash().put(COUNT_KEY, field(type, articleId), computed);
        return computed;
    }

    private Long footCount(Long articleId, FootTypeEnum type) {
        return footMapper.selectCount(new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getArticleId, articleId)
                .eq(UserFootDO::getType, type.name())
                .eq(UserFootDO::getState, 1));
    }

    /** 阅读数兜底：取定时回写表的持久化值（Redis 丢失后不归零）；回写调度见 M4-5 */
    private Long readCountFromDb(Long articleId) {
        ArticleCountDO row = articleCountMapper.selectById(articleId);
        return row == null ? 0L : row.getReadCount();
    }
}
