package me.asikeida.ashore.service.interact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.asikeida.ashore.api.enums.FootTypeEnum;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.interact.repository.entity.ArticleCountDO;
import me.asikeida.ashore.service.interact.repository.entity.CommentDO;
import me.asikeida.ashore.service.interact.repository.entity.NotifyMsgDO;
import me.asikeida.ashore.service.interact.repository.entity.UserFootDO;
import me.asikeida.ashore.service.interact.repository.mapper.ArticleCountMapper;
import me.asikeida.ashore.service.interact.repository.mapper.CommentMapper;
import me.asikeida.ashore.service.interact.repository.mapper.NotifyMsgMapper;
import me.asikeida.ashore.service.interact.repository.mapper.UserFootMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

/**
 * 互动域数据访问测试：V5 四表、足迹唯一索引幂等防线、两级折叠查询。
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:interactdao;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=test-secret",
        "ashore.session.expire-days=30"
})
class InteractDaoTests {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserFootMapper footMapper;

    @Autowired
    private ArticleCountMapper countMapper;

    @Autowired
    private NotifyMsgMapper notifyMapper;

    @Test
    void commentTwoLevelModel() {
        CommentDO top = comment(101L, 1L, "顶级评论", 0L, 0L);
        commentMapper.insert(top);
        CommentDO reply = comment(101L, 2L, "子回复", top.getId(), top.getId());
        reply.setReplyUserId(1L);
        commentMapper.insert(reply);

        // 顶级：article + top_comment_id=0
        var tops = commentMapper.selectList(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getArticleId, 101L)
                .eq(CommentDO::getTopCommentId, 0L));
        assertThat(tops).hasSize(1);

        // 子回复挂在顶级下
        var replies = commentMapper.selectList(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getTopCommentId, top.getId()));
        assertThat(replies).hasSize(1);
        assertThat(replies.get(0).getReplyUserId()).isEqualTo(1L);
    }

    @Test
    void footUniqueIndexGuardsConcurrency() {
        UserFootDO first = foot(5L, 201L, FootTypeEnum.PRAISE);
        footMapper.insert(first);
        assertThat(first.getId()).isNotNull();

        // 并发重复点赞的唯一索引防线（应用层分布式锁之前/失效时的最后屏障）
        assertThatThrownBy(() -> footMapper.insert(foot(5L, 201L, FootTypeEnum.PRAISE)))
                .isInstanceOf(DuplicateKeyException.class);

        // 同用户同面经不同类型互不冲突
        UserFootDO collect = foot(5L, 201L, FootTypeEnum.COLLECT);
        footMapper.insert(collect);
        assertThat(collect.getId()).isNotNull();
    }

    @Test
    void articleCountUpsertShape() {
        ArticleCountDO count = new ArticleCountDO();
        count.setArticleId(301L);
        count.setPraiseCount(3);
        count.setReadCount(100);
        countMapper.insert(count);

        // 覆盖写语义：主键 INPUT，直接 updateById 覆盖
        ArticleCountDO update = new ArticleCountDO();
        update.setArticleId(301L);
        update.setPraiseCount(4);
        countMapper.updateById(update);

        ArticleCountDO loaded = countMapper.selectById(301L);
        assertThat(loaded.getPraiseCount()).isEqualTo(4);
        assertThat(loaded.getReadCount()).isEqualTo(100);
    }

    @Test
    void notifyMsgDefaultsUnread() {
        NotifyMsgDO msg = new NotifyMsgDO();
        msg.setUserId(1L);
        msg.setOperatorId(2L);
        msg.setType(NotifyTypeEnum.PRAISE.name());
        msg.setArticleId(201L);
        notifyMapper.insert(msg);
        assertThat(msg.getId()).isNotNull();

        var unread = notifyMapper.selectList(new LambdaQueryWrapper<NotifyMsgDO>()
                .eq(NotifyMsgDO::getUserId, 1L)
                .eq(NotifyMsgDO::getReadStat, 0));
        assertThat(unread).hasSize(1);
    }

    private CommentDO comment(Long articleId, Long userId, String content, Long topId, Long parentId) {
        CommentDO c = new CommentDO();
        c.setArticleId(articleId);
        c.setUserId(userId);
        c.setContent(content);
        c.setTopCommentId(topId);
        c.setParentCommentId(parentId);
        c.setDeleted(0);
        return c;
    }

    private UserFootDO foot(Long userId, Long articleId, FootTypeEnum type) {
        UserFootDO f = new UserFootDO();
        f.setUserId(userId);
        f.setArticleId(articleId);
        f.setType(type.name());
        f.setState(1);
        return f;
    }
}
