package me.asikeida.ashore.service.interact;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.api.model.event.NotifyMsgEvent;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.interact.listener.NotifyMsgListener;
import me.asikeida.ashore.service.interact.repository.entity.NotifyMsgDO;
import me.asikeida.ashore.service.interact.repository.mapper.NotifyMsgMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 通知监听器测试（同步直调，聚焦落库/去重/防自通知三条规则）；
 * @Async 异步与事件发布接线由远端 E2E 覆盖。
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:notifysvc;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=test-secret",
        "ashore.session.expire-days=30"
})
class NotifyMsgListenerTests {

    @Autowired
    private NotifyMsgListener listener;

    @Autowired
    private NotifyMsgMapper notifyMapper;

    @Test
    void praiseInsertsRowAndDuplicateIsSkipped() {
        NotifyMsgEvent event = new NotifyMsgEvent(10L, 20L, NotifyTypeEnum.PRAISE, 301L, null, null);
        listener.onNotify(event);
        listener.onNotify(event); // 重复点赞（切换语义）不重复通知

        assertThat(count(10L, NotifyTypeEnum.PRAISE)).isEqualTo(1);
    }

    @Test
    void selfActionIsNotNotified() {
        listener.onNotify(new NotifyMsgEvent(10L, 10L, NotifyTypeEnum.COMMENT, 301L, null, "自评"));

        assertThat(count(10L, NotifyTypeEnum.COMMENT)).isZero();
    }

    @Test
    void commentAndReplyAlwaysNotified() {
        listener.onNotify(new NotifyMsgEvent(10L, 20L, NotifyTypeEnum.COMMENT, 301L, 1L, "评论一"));
        listener.onNotify(new NotifyMsgEvent(10L, 20L, NotifyTypeEnum.COMMENT, 301L, 2L, "评论二"));

        assertThat(count(10L, NotifyTypeEnum.COMMENT)).isEqualTo(2);
    }

    private long count(Long receiver, NotifyTypeEnum type) {
        return notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMsgDO>()
                .eq(NotifyMsgDO::getUserId, receiver)
                .eq(NotifyMsgDO::getType, type.name()));
    }
}
