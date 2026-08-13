package me.asikeida.ashore.service.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import me.asikeida.ashore.api.exception.BizException;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import me.asikeida.ashore.service.user.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 用户管理业务规则单测（纯 Mockito，无 Spring 上下文）。核心契约：
 * <ol>
 *   <li>封禁 = UPDATE(BANNED) + revokeAllForUser <b>成对发生</b>（R1 强下线契约，
 *       只改库不吊销 = 存量会话存活至自然过期）；</li>
 *   <li>防线：不能封自己、不能封 ADMIN；</li>
 *   <li>幂等：已 BANNED 重复封禁不重复吊销；ACTIVE 重复解封不写库；</li>
 *   <li>解封不吊销（封禁时已清空，无可吊销）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceImplTests {

    @Mock
    private UserMapper userMapper;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private UserAdminServiceImpl service;

    private UserDO user(long id, String role, String status) {
        UserDO u = new UserDO();
        u.setId(id);
        u.setAccount("acct-" + id);
        u.setNickname("n" + id);
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    @Test
    void banUpdatesStatusAndRevokesAllSessionsAsAPair() {
        when(userMapper.selectById(998L)).thenReturn(user(998L, UserDO.ROLE_USER, UserDO.STATUS_ACTIVE));

        service.ban(998L, 1L);

        verify(userMapper).updateById(any(UserDO.class));
        // 配对断言：改库与吊销必须同时发生，缺一即契约破坏
        verify(sessionService).revokeAllForUser(998L);
    }

    @Test
    void banSelfIsRejected() {
        assertThatThrownBy(() -> service.ban(1L, 1L))
                .isInstanceOf(BizException.class);
        verify(userMapper, never()).updateById(any(UserDO.class));
        verify(sessionService, never()).revokeAllForUser(any());
    }

    @Test
    void banAdminIsRejected() {
        when(userMapper.selectById(3L)).thenReturn(user(3L, UserDO.ROLE_ADMIN, UserDO.STATUS_ACTIVE));

        assertThatThrownBy(() -> service.ban(3L, 1L))
                .isInstanceOf(BizException.class);
        verify(sessionService, never()).revokeAllForUser(any());
    }

    @Test
    void banBannedUserIsIdempotentWithoutRevoke() {
        when(userMapper.selectById(998L)).thenReturn(user(998L, UserDO.ROLE_USER, UserDO.STATUS_BANNED));

        service.ban(998L, 1L);

        verify(userMapper, never()).updateById(any(UserDO.class));
        // 幂等关键：不重复吊销（会话已在上次封禁时清空）
        verify(sessionService, never()).revokeAllForUser(any());
    }

    @Test
    void unbanFlipsStatusWithoutRevoking() {
        when(userMapper.selectById(998L)).thenReturn(user(998L, UserDO.ROLE_USER, UserDO.STATUS_BANNED));

        service.unban(998L);

        verify(userMapper).updateById(any(UserDO.class));
        // 解封无可吊销（封禁时已清空会话），错误地吊销反而会误伤新会话
        verify(sessionService, never()).revokeAllForUser(any());
    }

    @Test
    void unbanActiveUserIsIdempotent() {
        when(userMapper.selectById(998L)).thenReturn(user(998L, UserDO.ROLE_USER, UserDO.STATUS_ACTIVE));

        service.unban(998L);

        verify(userMapper, never()).updateById(any(UserDO.class));
    }

    @Test
    void banMissingUserThrowsNotFound() {
        when(userMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.ban(404L, 1L))
                .isInstanceOf(BizException.class);
    }
}
