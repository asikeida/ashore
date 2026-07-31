package me.asikeida.ashore.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.RedisTunnelProbe;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.user.service.SessionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 会话体系并发回归（审查 R1 修复的深化验证）。
 * <p>需要真实 Redis（SSH 隧道 16379），不可用时自动跳过。场景：</p>
 * <ol>
 *   <li>同一用户 20 线程并发登录签发：user_sessions:{uid} 反查集合恰好 20、
 *       每个独立会话都可用（多端登录语义不互相踢）；</li>
 *   <li>签发后 revokeAllForUser：集合清空、全部 token 即刻失效
 *       （封禁强下线契约——R1 修复点：没有反查集合时封禁只对"之后"生效）。</li>
 * </ol>
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:sessconc;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=test-secret",
        "ashore.session.expire-days=30",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=16379",
        "spring.data.redis.password=${ASHORE_REDIS_PASSWORD:}"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionConcurrencyTests {

    private static final long USER_A = 993_100L;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterAll
    void clean() {
        if (!tunnelUp()) {
            return;
        }
        sessionService.revokeAllForUser(USER_A);
    }

    private boolean tunnelUp() {
        // 门控升级为完整 AUTH+PING 握手：端口在但转发死/密码缺失时优雅跳过而非 NOAUTH 报错
        return RedisTunnelProbe.available();
    }

    @Test
    void concurrentLoginIssuesIndependentSessions() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tunnelUp(), "需要 SSH 隧道（redis 16379）");
        int threads = 20;
        LoginUser user = new LoginUser(USER_A, "sess-conc", "USER");
        List<String> tokens = runConcurrently(threads, i ->
                sessionService.issue(user).token());

        Long setSize = redisTemplate.opsForSet().size("ashore:user_sessions:" + USER_A);
        assertThat(setSize).as("反查集合恰好 20（多端登录互不踢）").isEqualTo((long) threads);
        // 每个独立 token 都应可用（签发即登记）
        long valid = tokens.stream().filter(t -> sessionService.validate(t) != null).count();
        assertThat(valid).as("20 个会话全部可校验").isEqualTo((long) threads);
    }

    @Test
    void revokeAllForUserKillsEveryConcurrentSession() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tunnelUp(), "需要 SSH 隧道（redis 16379）");
        int threads = 10;
        LoginUser user = new LoginUser(USER_A, "sess-revoke", "USER");
        List<String> tokens = runConcurrently(threads, i ->
                sessionService.issue(user).token());
        assertThat(redisTemplate.opsForSet().size("ashore:user_sessions:" + USER_A))
                .isGreaterThanOrEqualTo((long) threads);

        sessionService.revokeAllForUser(USER_A);

        Long setSize = redisTemplate.opsForSet().size("ashore:user_sessions:" + USER_A);
        assertThat(setSize).as("封禁后反查集合清空").isEqualTo(0L);
        long valid = tokens.stream().filter(t -> sessionService.validate(t) != null).count();
        assertThat(valid).as("R1 契约：封禁后全部存量会话即刻失效").isEqualTo(0L);
    }

    private List<String> runConcurrently(int threads, java.util.function.IntFunction<String> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit((Callable<String>) () -> {
                ready.countDown();
                start.await();
                return task.apply(index);
            }));
        }
        ready.await();
        start.countDown();
        List<String> results = new CopyOnWriteArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        return results;
    }
}
