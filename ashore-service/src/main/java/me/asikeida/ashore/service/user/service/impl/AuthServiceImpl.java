package me.asikeida.ashore.service.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.concurrent.ThreadLocalRandom;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.vo.auth.LoginReq;
import me.asikeida.ashore.api.vo.auth.RegisterReq;
import me.asikeida.ashore.api.vo.auth.UserVo;
import me.asikeida.ashore.service.user.converter.UserConverter;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import me.asikeida.ashore.service.user.service.AuthService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserVo register(RegisterReq req) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getAccount, req.account()));
        if (exists > 0) {
            throw ExceptionUtil.of(StatusEnum.ACCOUNT_EXISTS);
        }

        UserDO user = new UserDO();
        user.setAccount(req.account());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(defaultNicknameIfBlank(req.nickname()));
        user.setRole(UserDO.ROLE_USER);
        user.setStatus(UserDO.STATUS_ACTIVE);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册同名账号时先查后插会放行，由 V1 唯一索引兜底
            throw ExceptionUtil.of(StatusEnum.ACCOUNT_EXISTS);
        }
        return UserConverter.toVo(user);
    }

    @Override
    public UserVo login(LoginReq req) {
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getAccount, req.account()));
        // 账号不存在 / OAuth 无密码用户 / 密码错误：三种情况同一文案，防账号枚举
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ExceptionUtil.of(StatusEnum.LOGIN_FAILED);
        }
        if (UserDO.STATUS_BANNED.equals(user.getStatus())) {
            throw ExceptionUtil.of(StatusEnum.ACCOUNT_BANNED);
        }
        return UserConverter.toVo(user);
    }

    private String defaultNicknameIfBlank(String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return "岸友" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
