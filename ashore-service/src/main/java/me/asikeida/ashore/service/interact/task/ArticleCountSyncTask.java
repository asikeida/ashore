package me.asikeida.ashore.service.interact.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import me.asikeida.ashore.api.enums.FootTypeEnum;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.ArticleCountDO;
import me.asikeida.ashore.service.interact.repository.entity.CommentDO;
import me.asikeida.ashore.service.interact.repository.entity.UserFootDO;
import me.asikeida.ashore.service.interact.repository.mapper.ArticleCountMapper;
import me.asikeida.ashore.service.interact.repository.mapper.CommentMapper;
import me.asikeida.ashore.service.interact.repository.mapper.UserFootMapper;
import me.asikeida.ashore.service.interact.service.CountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 面经计数定时回写：Redis 实时计数 → article_count 表（覆盖写幂等）。
 * <p>对照 paicoding CountServiceImpl 的 5 分钟调度同款节奏。praise/collect/comment
 * 的事实源是足迹/评论表（可重建聚合），read 的事实源在 Redis hash——读取失败时
 * 保留 DB 现值（绝不因 Redis 故障把持久化阅读数清零）。</p>
 */
@Component
public class ArticleCountSyncTask {

    private static final Logger log = LoggerFactory.getLogger(ArticleCountSyncTask.class);

    private final ArticleMapper articleMapper;

    private final UserFootMapper footMapper;

    private final CommentMapper commentMapper;

    private final ArticleCountMapper countMapper;

    private final CountService countService;

    public ArticleCountSyncTask(ArticleMapper articleMapper, UserFootMapper footMapper,
                                CommentMapper commentMapper, ArticleCountMapper countMapper,
                                CountService countService) {
        this.articleMapper = articleMapper;
        this.footMapper = footMapper;
        this.commentMapper = commentMapper;
        this.countMapper = countMapper;
        this.countService = countService;
    }

    /** 每 5 分钟回写一次（对照 paicoding 同款节奏） */
    @Scheduled(cron = "0 */5 * * * ?")
    public void syncAll() {
        List<ArticleDO> articles = articleMapper.selectList(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getDeleted, 0));
        for (ArticleDO article : articles) {
            try {
                syncOne(article.getId());
            } catch (Exception e) {
                log.warn("计数回写失败 article={}: {}", article.getId(), e.getMessage());
            }
        }
        if (!articles.isEmpty()) {
            log.info("计数回写完成，共 {} 篇", articles.size());
        }
    }

    /** 单篇回写（M4-5 测试直调入口） */
    public void syncOne(Long articleId) {
        long praise = countFoot(articleId, FootTypeEnum.PRAISE);
        long collect = countFoot(articleId, FootTypeEnum.COLLECT);
        long comment = commentMapper.selectCount(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getArticleId, articleId)
                .eq(CommentDO::getDeleted, 0));
        long read = readCount(articleId);

        ArticleCountDO existing = countMapper.selectById(articleId);
        ArticleCountDO row = new ArticleCountDO();
        row.setArticleId(articleId);
        row.setPraiseCount((int) praise);
        row.setCollectCount((int) collect);
        row.setCommentCount((int) comment);
        // Redis 不可达时保留 DB 现值，不清零
        row.setReadCount(existing == null ? (int) read : Math.max((int) read, existing.getReadCount()));
        row.setUpdateTime(LocalDateTime.now());
        if (existing == null) {
            countMapper.insert(row);
        } else {
            countMapper.updateById(row);
        }
    }

    private long countFoot(Long articleId, FootTypeEnum type) {
        return footMapper.selectCount(new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getArticleId, articleId)
                .eq(UserFootDO::getType, type.name())
                .eq(UserFootDO::getState, 1));
    }

    private long readCount(Long articleId) {
        try {
            Object value = countService.incrRead(articleId, 0);
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            ArticleCountDO existing = countMapper.selectById(articleId);
            return existing == null ? 0 : existing.getReadCount();
        }
    }
}
