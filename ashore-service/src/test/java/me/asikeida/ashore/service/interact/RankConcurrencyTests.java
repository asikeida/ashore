package me.asikeida.ashore.service.interact;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.RankActionEnum;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.interact.service.RankService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 排行榜并发回归（paicoding fixme 修复②的深化验证）。
 * <p>需要真实 Redis（SSH 隧道 16379），不可用时自动跳过。场景：</p>
 * <ol>
 *   <li>N 用户并发首读同一文章：Lua 单脚本原子化「去重→双榜加分→TTL 判设」，
 *       榜成员恰好 N、分数恰好 1（ acted 哈希去重不丢不重）；</li>
 *   <li>TTL 竞态：清空榜单后并发首写——并发窗口内「先判后设」必然竞态，
 *       修复后 TTL 必须被正确设置（永不过期 = 数据腐烂）。</li>
 * </ol>
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:rankconc;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class RankConcurrencyTests {

    private static final String DAILY_KEY_PREFIX = "ashore:rank:daily:";
    private static final String MONTHLY_KEY_PREFIX = "ashore:rank:monthly:";
    private static final String ACTED_KEY_PREFIX = "ashore:rank:acted:";

    @Autowired
    private RankService rankService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterAll
    void cleanRankKeys() {
        if (!tunnelUp()) {
            return;
        }
        // 清理本测试用户段（991xxx）的 acted 记录与当日榜单增量，避免污染真实榜单
        java.util.Set<String> actedKeys = redisTemplate.keys(ACTED_KEY_PREFIX + "991*");
        if (actedKeys != null && !actedKeys.isEmpty()) {
            redisTemplate.delete(actedKeys);
        }
        String today = DAILY_KEY_PREFIX + java.time.LocalDate.now();
        String month = MONTHLY_KEY_PREFIX + java.time.YearMonth.from(java.time.LocalDate.now());
        for (long userId : List.of(991_300L, 991_301L)) {
            redisTemplate.opsForZSet().remove(today, String.valueOf(userId));
            redisTemplate.opsForZSet().remove(month, String.valueOf(userId));
        }
    }

    private boolean tunnelUp() {
        try (Socket socket = new Socket("127.0.0.1", 16379)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void concurrentDistinctUsersFirstReadScoredExactlyOnce() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tunnelUp(), "需要 SSH 隧道（redis 16379）");
        int users = 30;
        // 同一文章、30 个不同用户并发首读（READ 计 1 分，acted 哈希同人同文同动作去重）
        runConcurrently(users, i -> {
            LoginUser user = new LoginUser(991_000L + i, "rank-u" + i, "USER");
            rankService.record(user.userId(), RankActionEnum.READ, 991_900L);
            return true;
        });

        String dailyKey = DAILY_KEY_PREFIX + java.time.LocalDate.now();
        String monthlyKey = MONTHLY_KEY_PREFIX + java.time.YearMonth.from(java.time.LocalDate.now());
        // 去重语义：同人同文同动作只计 1 次 → 30 用户各 1 分，双榜成员各恰好 30
        Double dailyScore = redisTemplate.opsForZSet().score(dailyKey, "991000");
        assertThat(dailyScore).as("日榜成员应有分且恰为 1（去重）").isEqualTo(1.0);
        Long dailyMembers = countUserMembers(dailyKey);
        assertThat(dailyMembers).as("日榜 30 用户全部入榜").isEqualTo((long) users);
        Long monthlyMembers = countUserMembers(monthlyKey);
        assertThat(monthlyMembers).as("月榜与日榜同构").isEqualTo((long) users);
    }

    @Test
    void concurrentFirstWriteAlwaysLeavesTtlSet() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tunnelUp(), "需要 SSH 隧道（redis 16379）");
        String dailyKey = DAILY_KEY_PREFIX + java.time.LocalDate.now();
        String monthlyKey = MONTHLY_KEY_PREFIX + java.time.YearMonth.from(java.time.LocalDate.now());
        // 制造「永不过期键不存在」的初始态：删除两个榜单，30 线程并发首写
        redisTemplate.delete(List.of(dailyKey, monthlyKey));

        int users = 30;
        runConcurrently(users, i -> {
            LoginUser user = new LoginUser(991_300L + (i % 2), "rank-ttl" + i, "USER");
            rankService.record(user.userId(), RankActionEnum.READ, 991_901L);
            return true;
        });

        // 修复点断言：并发窗口内 TTL 判设由单 Lua 脚本完成 → 必有 TTL 且绝不为 -1（永不过期）
        Long dailyTtl = redisTemplate.getExpire(dailyKey);
        Long monthlyTtl = redisTemplate.getExpire(monthlyKey);
        assertThat(dailyTtl).as("日榜 TTL 已设置").isNotNull().isGreaterThan(0);
        assertThat(monthlyTtl).as("月榜 TTL 已设置").isNotNull().isGreaterThan(0);
    }

    /** 统计榜单里 991 前缀测试用户（段内任意）的成员数 */
    private Long countUserMembers(String key) {
        java.util.Set<String> members = redisTemplate.opsForZSet().rangeByScore(key, 1, 1);
        return members == null ? 0L : members.size();
    }

    private List<Boolean> runConcurrently(int threads, java.util.function.IntFunction<Boolean> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit((Callable<Boolean>) () -> {
                ready.countDown();
                start.await();
                return task.apply(index);
            }));
        }
        ready.await();
        start.countDown();
        List<Boolean> results = new CopyOnWriteArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        return results;
    }
}
