package me.asikeida.ashore.service.user.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import me.asikeida.ashore.api.context.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 会话服务单元测试（Mock Redis，聚焦反查索引与吊销逻辑的接线；
 * 真实 Redis 行为由远端 E2E 覆盖）。审查 R1 的回归防线。
 */
class SessionServiceImplTests {

    private static final String SESSION_PREFIX = "ashore:session:";
    private static final String USER_SET_PREFIX = "ashore:user_sessions:";

    private StringRedisTemplate redisTemplate;

    private ValueOperations<String, String> valueOps;

    private SetOperations<String, String> setOps;

    private SessionServiceImpl sessionService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        sessionService = new SessionServiceImpl(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(sessionService, "jwtSecret", "test-secret");
        ReflectionTestUtils.setField(sessionService, "expireDays", 30);
        sessionService.initAlgorithm();
    }

    @Test
    void issueWritesSessionKeyAndUserReverseIndex() {
        sessionService.issue(new LoginUser(42L, "u42", "USER"));

        // 会话键：带 TTL 写入
        verify(valueOps).set(anyString(), anyString(), eq(30L), eq(TimeUnit.DAYS));
        // 反查索引：SADD + 集合过期（比会话长 1 天）
        verify(setOps).add(eq(USER_SET_PREFIX + 42L), anyString());
        verify(redisTemplate).expire(eq(USER_SET_PREFIX + 42L), eq(31L), eq(TimeUnit.DAYS));
    }

    @Test
    void revokeAllForUserDeletesEverySessionKeyAndIndex() {
        when(setOps.members(USER_SET_PREFIX + 42L)).thenReturn(Set.of("sid-a", "sid-b"));

        sessionService.revokeAllForUser(42L);

        // Set.of 迭代顺序不确定，按内容断言（不依赖 List 顺序）
        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.argThat(
                (java.util.Collection<String> c) -> c.size() == 2
                        && c.contains(SESSION_PREFIX + "sid-a")
                        && c.contains(SESSION_PREFIX + "sid-b")));
        verify(redisTemplate).delete(USER_SET_PREFIX + 42L);
    }

    @Test
    void validateReturnsNullAfterRevocation() {
        LoginUser user = new LoginUser(7L, "u7", "USER");
        String token = sessionService.issue(user).token();
        // 模拟吊销：Redis 会话键不存在（值被删）
        when(valueOps.get(anyString())).thenReturn(null);

        org.assertj.core.api.Assertions.assertThat(sessionService.validate(token)).isNull();
    }

    @Test
    void issueRejectsUserWithoutId() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> sessionService.issue(new LoginUser(null, "anon", "USER")))
                .isInstanceOf(IllegalArgumentException.class);
        // 契约：uid 为空不产生任何会话写入（避免无法反查的孤儿会话）
        verify(valueOps, org.mockito.Mockito.never())
                .set(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void issueTokenCarriesExpiry() {
        // 区间断言容忍签发耗时（整点相减会被毫秒差拖到 29 天）
        java.time.Instant before = java.time.Instant.now();
        String token = sessionService.issue(new LoginUser(1L, "u1", "USER")).token();
        java.time.Instant exp = com.auth0.jwt.JWT.decode(token).getExpiresAt().toInstant();

        long seconds = java.time.Duration.between(before, exp).getSeconds();
        org.assertj.core.api.Assertions.assertThat(seconds)
                .isBetween(TimeUnit.DAYS.toSeconds(30) - 5, TimeUnit.DAYS.toSeconds(30));
        org.assertj.core.api.Assertions.assertThat(
                com.auth0.jwt.JWT.decode(token).getClaim("uid").asLong()).isEqualTo(1L);
    }
}
