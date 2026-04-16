package me.asikeida.ashore.service.interact.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.api.model.event.NotifyMsgEvent;
import me.asikeida.ashore.service.interact.repository.entity.NotifyMsgDO;
import me.asikeida.ashore.service.interact.repository.mapper.NotifyMsgMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 通知事件消费者：异步落库。
 * <p>过滤规则：① 自己触发自己不通知；② PRAISE/COLLECT 按
 * （接收人，触发者，类型，面经）去重——切换语义下重复点赞不重复通知；
 * COMMENT/REPLY 每次都通知。</p>
 */
@Component
public class NotifyMsgListener {

    private static final Logger log = LoggerFactory.getLogger(NotifyMsgListener.class);

    private final NotifyMsgMapper notifyMapper;

    public NotifyMsgListener(NotifyMsgMapper notifyMapper) {
        this.notifyMapper = notifyMapper;
    }

    @Async
    @EventListener
    public void onNotify(NotifyMsgEvent event) {
        try {
            if (event.receiverId().equals(event.operatorId())) {
                return;
            }
            if (isToggleType(event.type()) && alreadyNotified(event)) {
                return;
            }
            NotifyMsgDO msg = new NotifyMsgDO();
            msg.setUserId(event.receiverId());
            msg.setOperatorId(event.operatorId());
            msg.setType(event.type().name());
            msg.setArticleId(event.articleId());
            msg.setCommentId(event.commentId());
            msg.setContent(event.content());
            msg.setReadStat(0);
            notifyMapper.insert(msg);
        } catch (Exception e) {
            // 通知是旁路：失败仅告警，不反压业务主流程
            log.warn("通知落库失败 type={} receiver={}: {}", event.type(), event.receiverId(), e.getMessage());
        }
    }

    private boolean isToggleType(NotifyTypeEnum type) {
        return type == NotifyTypeEnum.PRAISE || type == NotifyTypeEnum.COLLECT;
    }

    private boolean alreadyNotified(NotifyMsgEvent event) {
        return notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMsgDO>()
                .eq(NotifyMsgDO::getUserId, event.receiverId())
                .eq(NotifyMsgDO::getOperatorId, event.operatorId())
                .eq(NotifyMsgDO::getType, event.type().name())
                .eq(NotifyMsgDO::getArticleId, event.articleId())) > 0;
    }
}
