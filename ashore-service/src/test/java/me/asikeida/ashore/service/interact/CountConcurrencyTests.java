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
import java.util.concurrent.atomic.AtomicLong;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.interact.repository.entity.ArticleCountDO;
import me.asikeida.ashore.service.interact.repository.mapper.ArticleCountMapper;
import me.asikeida.ashore.service.interact.service.CountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 计数并发回归：阅读数原子累加 + 懒加载自愈的并发一致性。
 * <p>需要真实 Redis（SSH 隧道 16379），不可用时自动跳过。场景：</p>
 * <ol>
 *   <li>30 线程并发 incrRead：hash HINCRBY 原子累加 → 恰好 30（无丢失更新）；</li>
 *   <li>懒加载自愈并发：Redis 字段丢失后 N 线程并发 counts() 触发回填——
 *       全部线程读到同一基准值（DB article_count），自愈不产生重复累加或脏值。</li>
 * </ol>
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:countconc;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class CountConcurrencyTests {

    private static final String COUNT_KEY = "ashore:count:article";
    private static final long ARTICLE_A = 992_001L;
    private static final long ARTICLE_B = 992_002L;
    private static final long SEEDED_READ_COUNT = 42L;

    @Autowired
    private CountService countService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ArticleCountMapper articleCountMapper;

    @BeforeAll
    void seed() {
        // 懒加载场景的 DB 基准：article_count 持久化值 42（回写表兜底语义）
        ArticleCountDO row = new ArticleCountDO();
        row.setArticleId(ARTICLE_B);
        row.setReadCount((int) SEEDED_READ_COUNT);
        row.setPraiseCount(0);
        row.setCollectCount(0);
        row.setCommentCount(0);
        articleCountMapper.insert(row);
    }

    @AfterAll
    void clean() {
        if (!tunnelUp()) {
            return;
        }
        redisTemplate.opsForHash().delete(COUNT_KEY,
                "read:" + ARTICLE_A, "read:" + ARTICLE_B,
                "praise:" + ARTICLE_A, "collect:" + ARTICLE_A, "comment:" + ARTICLE_A);
        articleCountMapper.deleteById(ARTICLE_B);
    }

    private boolean tunnelUp() {
        try (Socket socket = new Socket("127.0.0.1", 16379)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void concurrentReadIncrSumsExactly() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tunnelUp(), "需要 SSH 隧道（redis 16379）");
        int threads = 30;
        runConcurrently(threads, i -> {
            countService.incrRead(ARTICLE_A, 1);
            return true;
        });
        assertThat(countService.counts(ARTICLE_A).read())
                .as("30 并发累加恰好 30（hash HINCRBY 原子）").isEqualTo(threads);
    }

    @Test
    void lazyLoadSelfHealIsConcurrentlyConsistent() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tunnelUp(), "需要 SSH 隧道（redis 16379）");
        // 删掉 Redis 字段制造「数据丢失」：所有线程的 counts() 都必须触发懒加载兜底
        redisTemplate.opsForHash().delete(COUNT_KEY, "read:" + ARTICLE_B);
        AtomicLong consistent = new AtomicLong();
        int threads = 20;
        runConcurrently(threads, i -> {
            long read = countService.counts(ARTICLE_B).read();
            if (read == SEEDED_READ_COUNT) {
                consistent.incrementAndGet();
            }
            return true;
        });
        assertThat(consistent.get())
                .as("20 线程全部读到同一 DB 基准值（懒加载自愈无脏值）").isEqualTo((long) threads);
        // 自愈后 Redis 已回填：再次读取不查库也应是该值
        assertThat(countService.counts(ARTICLE_B).read()).isEqualTo(SEEDED_READ_COUNT);
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
