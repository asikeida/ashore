package me.asikeida.ashore.service.config;

import java.time.Duration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Redisson 客户端（分布式锁用，仅延迟创建）。
 * <p>声明为 @Lazy：仅在首次真正需要加锁（如足迹切换）时才创建并连接 Redis——
 * 无 Redis 的纯数据库测试上下文不会因它失败。</p>
 * <p>连接参数复用 spring.data.redis.*（与 StringRedisTemplate 同源）。</p>
 */
@Lazy
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        var server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectTimeout((int) Duration.ofSeconds(3).toMillis())
                .setTimeout((int) Duration.ofSeconds(3).toMillis())
                .setRetryAttempts(2);
        if (password != null && !password.isBlank()) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
