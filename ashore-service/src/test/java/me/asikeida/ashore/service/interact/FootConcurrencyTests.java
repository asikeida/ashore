package me.asikeida.ashore.service.interact;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.enums.FootTypeEnum;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.UserFootDO;
import me.asikeida.ashore.service.interact.repository.mapper.UserFootMapper;
import me.asikeida.ashore.service.interact.service.CountService;
import me.asikeida.ashore.service.interact.service.FootService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 足迹并发幂等回归测试（paicoding fixme 修复①的验证，审查 R1 同源）。
 * <p>需要真实 Redis：经 SSH 隧道（本地 16379）访问云端库；隧道不可用时<b>自动跳过</b>。
 * 并发场景：① 同一用户多线程连续切换（锁串行化，无丢失更新）；
 * ② 多用户并发首赞（计数原子累加恰好 N）。</p>
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:footconc;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=test-secret",
        "ashore.session.expire-days=30",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=16379"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FootConcurrencyTests {

    private static final long ARTICLE_A = 990_001L;
    private static final long ARTICLE_B = 990_002L;

    @Autowired
    private FootService footService;

    @Autowired
    private CountService countService;

    @Autowired
    private UserFootMapper footMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    void seedArticles() {
        articleMapper.insert(article(ARTICLE_A));
        articleMapper.insert(article(ARTICLE_B));
    }

    @AfterAll
    void cleanRedisFields() {
        // 隧道不可用时（跳过场景）不做清理，避免误报
        if (!tunnelUp()) {
            return;
        }
        for (long articleId : List.of(ARTICLE_A, ARTICLE_B)) {
            redisTemplate.opsForHash().delete("ashore:count:article",
                    "praise:" + articleId, "collect:" + articleId,
                    "comment:" + articleId, "read:" + articleId);
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
    void concurrentSameUserTogglesProduceSingleRowWithoutLostUpdate() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tunnelUp(), "需要 SSH 隧道（redis 16379）");
        int threads = 21; // 奇数：最终应为有效状态且计数 1
        LoginUser user = new LoginUser(990_100L, "conc-same", "USER");
        runConcurrently(threads, index -> {
            footService.toggle(ARTICLE_A, FootTypeEnum.PRAISE, user);
            return true;
        });

        List<UserFootDO> rows = footMapper.selectList(new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getUserId, user.userId())
                .eq(UserFootDO::getArticleId, ARTICLE_A)
                .eq(UserFootDO::getType, FootTypeEnum.PRAISE.name()));
        // 修复点断言 1：并发切换只产生一行（无重复插入）
        assertThat(rows).hasSize(1);
        // 修复点断言 2：21 次奇数切换 → 最终有效、计数无丢失
        assertThat(rows.get(0).getState()).isEqualTo(1);
        assertThat(countService.counts(ARTICLE_A).praise()).isEqualTo(1);
    }

    @Test
    void concurrentDistinctUsersPraiseCountsExactlyN() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tunnelUp(), "需要 SSH 隧道（redis 16379）");
        int users = 20;
        List<Boolean> results = runConcurrently(users, i -> {
            LoginUser user = new LoginUser(990_200L + i, "conc-u" + i, "USER");
            return footService.toggle(ARTICLE_B, FootTypeEnum.PRAISE, user).active();
        });

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(users);

        List<UserFootDO> rows = footMapper.selectList(new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getArticleId, ARTICLE_B)
                .eq(UserFootDO::getType, FootTypeEnum.PRAISE.name()));
        assertThat(rows).hasSize(users);
        assertThat(countService.counts(ARTICLE_B).praise()).isEqualTo(users);
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
            results.add(future.get(30, TimeUnit.SECONDS)); // 任何线程异常都会让测试失败
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        return results;
    }

    private ArticleDO article(long id) {
        ArticleDO a = new ArticleDO();
        a.setId(id);
        a.setTitle("并发测试面经");
        a.setAuthorId(1L);
        a.setStatus(ArticleStatus.ONLINE.name());
        a.setToppingStat(0);
        a.setDeleted(0);
        return a;
    }
}
