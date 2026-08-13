package me.asikeida.ashore.service.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.vo.auth.UserVo;
import me.asikeida.ashore.service.user.converter.UserConverter;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import me.asikeida.ashore.service.user.service.SessionService;
import me.asikeida.ashore.service.user.service.UserAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理实现。
 * <p>封禁的配对语义（学习文档 06 §8）：会话校验查的是 Redis 身份快照而非数据库，
 * 只改 status 不吊销会话，被封者凭存量 token 还能用到自然过期（最长 30 天）；
 * 反过来只吊销不改库，重新登录即恢复。两步必须同请求内完成，本类用
 * {@code @Transactional} 保证 DB 写入的原子性，Redis 吊销紧随其后——
 * Redis 无事务语义，顺序执行已满足契约（吊销成功后新请求即刻被拒；
 * 若吊销中途失败，重试封禁幂等再吊销即可，无脏状态）。</p>
 */
@Service
public class UserAdminServiceImpl implements UserAdminService {

    private final UserMapper userMapper;

    private final SessionService sessionService;

    public UserAdminServiceImpl(UserMapper userMapper, SessionService sessionService) {
        this.userMapper = userMapper;
        this.sessionService = sessionService;
    }

    @Override
    public List<UserVo> list(String accountKeyword, int limit) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
                .orderByDesc(UserDO::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (accountKeyword != null && !accountKeyword.isBlank()) {
            wrapper.like(UserDO::getAccount, accountKeyword.trim());
        }
        return userMapper.selectList(wrapper).stream().map(UserConverter::toVo).toList();
    }

    @Override
    @Transactional
    public UserVo ban(Long userId, Long operatorId) {
        if (userId.equals(operatorId)) {
            // 自封禁会把操作者自己即刻踢下线，属误操作防线
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_STATE, "不能封禁自己");
        }
        UserDO user = requireUser(userId);
        if (UserDO.ROLE_ADMIN.equals(user.getRole())) {
            // ADMIN 拥有全部管理权，封禁 ADMIN 可能锁死整个管理台
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_STATE, "不能封禁管理员账号");
        }
        if (UserDO.STATUS_BANNED.equals(user.getStatus())) {
            return UserConverter.toVo(user);   // 幂等：重复封禁直接返回
        }
        user.setStatus(UserDO.STATUS_BANNED);
        userMapper.updateById(user);
        // 配对吊销（R1 契约）：不吊销则存量会话存活至自然过期，封禁形同虚设
        sessionService.revokeAllForUser(userId);
        return UserConverter.toVo(user);
    }

    @Override
    @Transactional
    public UserVo unban(Long userId) {
        UserDO user = requireUser(userId);
        if (!UserDO.STATUS_BANNED.equals(user.getStatus())) {
            return UserConverter.toVo(user);   // 幂等
        }
        user.setStatus(UserDO.STATUS_ACTIVE);
        userMapper.updateById(user);
        return UserConverter.toVo(user);
    }

    private UserDO requireUser(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
