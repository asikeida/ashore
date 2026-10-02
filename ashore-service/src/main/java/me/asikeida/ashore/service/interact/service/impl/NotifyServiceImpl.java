package me.asikeida.ashore.service.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.api.vo.notice.NoticeItemVo;
import me.asikeida.ashore.service.interact.repository.entity.NotifyMsgDO;
import me.asikeida.ashore.service.interact.repository.mapper.NotifyMsgMapper;
import me.asikeida.ashore.service.interact.service.NotifyService;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 通知查询实现：只能查/标记当前登录用户自己的通知（归属过滤硬约束）。
 */
@Service
public class NotifyServiceImpl implements NotifyService {

    private final NotifyMsgMapper notifyMapper;

    private final UserMapper userMapper;

    public NotifyServiceImpl(NotifyMsgMapper notifyMapper, UserMapper userMapper) {
        this.notifyMapper = notifyMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<NoticeItemVo> list(Long userId, NotifyTypeEnum type, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (Math.max(page, 1) - 1) * safeSize;

        LambdaQueryWrapper<NotifyMsgDO> wrapper = new LambdaQueryWrapper<NotifyMsgDO>()
                .eq(NotifyMsgDO::getUserId, userId);
        if (type != null) {
            wrapper.eq(NotifyMsgDO::getType, type.name());
        }
        wrapper.orderByDesc(NotifyMsgDO::getId)
                .last("LIMIT " + safeSize + " OFFSET " + offset);

        List<NotifyMsgDO> rows = notifyMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nicknames = userMapper.selectBatchIds(
                        rows.stream().map(NotifyMsgDO::getOperatorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserDO::getId,
                        u -> u.getNickname() == null ? "岸友" : u.getNickname(),
                        (a, b) -> a, HashMap::new));

        return rows.stream().map(row -> new NoticeItemVo(
                        String.valueOf(row.getId()), row.getType(),
                        row.getArticleId() == null ? null : String.valueOf(row.getArticleId()),
                        row.getCommentId() == null ? null : String.valueOf(row.getCommentId()),
                        nicknames.getOrDefault(row.getOperatorId(), "岸友"), row.getContent(),
                        row.getReadStat() == 1,
                        row.getCreateTime() == null ? null : row.getCreateTime().toString()))
                .toList();
    }

    @Override
    public long unreadCount(Long userId) {
        return notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMsgDO>()
                .eq(NotifyMsgDO::getUserId, userId)
                .eq(NotifyMsgDO::getReadStat, 0));
    }

    @Override
    public void markAllRead(Long userId) {
        notifyMapper.update(null, new LambdaUpdateWrapper<NotifyMsgDO>()
                .eq(NotifyMsgDO::getUserId, userId)
                .eq(NotifyMsgDO::getReadStat, 0)
                .set(NotifyMsgDO::getReadStat, 1));
    }
}
