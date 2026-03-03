package me.asikeida.ashore.service.user.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.user.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 会话双层实现：JWT（HMAC256）携带 uid/sid 声明，Redis 以
 * {@code ashore:session:{sid}} 登记身份快照（TTL 即会话有效期）。
 * <p>校验 = 签名验证 + Redis 存在性检查；登出 = 删除 Redis 键，token 即刻失效。</p>
 */
@Service
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    private static final String SESSION_KEY_PREFIX = "ashore:session:";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    @Value("${ashore.session.jwt-secret}")
    private String jwtSecret;

    @Value("${ashore.session.expire-days}")
    private int expireDays;

    private Algorithm algorithm;

    public SessionServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initAlgorithm() {
        this.algorithm = Algorithm.HMAC256(jwtSecret);
    }

    @Override
    public IssuedSession issue(LoginUser user) {
        String sessionId = UUID.randomUUID().toString();
        Date expiresAt = Date.from(Instant.now().plus(expireDays, java.time.temporal.ChronoUnit.DAYS));
        String token = JWT.create()
                .withClaim("uid", user.userId())
                .withClaim("sid", sessionId)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
        try {
            redisTemplate.opsForValue().set(
                    SESSION_KEY_PREFIX + sessionId,
                    objectMapper.writeValueAsString(user),
                    expireDays, TimeUnit.DAYS);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("会话序列化失败", e);
        }
        return new IssuedSession(token, TimeUnit.DAYS.toSeconds(expireDays));
    }

    @Override
    public LoginUser validate(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
            String json = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + jwt.getClaim("sid").asString());
            if (json == null) {
                // 签名有效但会话已吊销/清理
                return null;
            }
            return objectMapper.readValue(json, LoginUser.class);
        } catch (JWTVerificationException e) {
            log.warn("会话令牌校验失败：{}", e.getMessage());
            return null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("会话数据反序列化失败：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public void revoke(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
            redisTemplate.delete(SESSION_KEY_PREFIX + jwt.getClaim("sid").asString());
        } catch (JWTVerificationException e) {
            // token 本身已无效（过期/伪造），无需清理
            log.warn("登出时令牌已无效，忽略：{}", e.getMessage());
        }
    }
}
