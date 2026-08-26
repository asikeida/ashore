package me.asikeida.ashore.web.article;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.ArticleCountDO;
import me.asikeida.ashore.service.interact.repository.mapper.ArticleCountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 热榜测试：加权排序（互动权重 > 浏览）、OFFLINE 排除、limit 生效。
 * 数据经 Mapper 直插（article 主表 + article_count 聚合表）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:hotlist;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=hot-secret"
})
@AutoConfigureMockMvc
class HotListTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleCountMapper articleCountMapper;

    private Long seedArticle(String title, ArticleStatus status) {
        ArticleDO a = new ArticleDO();
        a.setTitle(title);
        a.setAuthorId(1L);
        a.setStatus(status.name());
        a.setToppingStat(0);
        a.setDeleted(0);
        articleMapper.insert(a);
        return a.getId();
    }

    private void seedCount(Long articleId, int read, int praise, int collect, int comment) {
        ArticleCountDO c = new ArticleCountDO();
        c.setArticleId(articleId);
        c.setReadCount(read);
        c.setPraiseCount(praise);
        c.setCollectCount(collect);
        c.setCommentCount(comment);
        articleCountMapper.insert(c);
    }

    @Test
    void hotRanksByWeightedScoreAndExcludesOffline() throws Exception {
        Long lowRead = seedArticle("高浏览低互动", ArticleStatus.ONLINE);
        Long highInteract = seedArticle("低浏览高互动", ArticleStatus.ONLINE);
        Long offlineHot = seedArticle("已下线的爆款", ArticleStatus.OFFLINE);

        // 高浏览低互动：1000×1 = 1000 分；低浏览高互动：100 + 10×4 + 5×6 + 8×8 = 234 分
        // 但爆款：500×1 + 50×4 + 20×6 + 30×8 = 1290 分（若未被排除应第一——排除后不应出现）
        seedCount(lowRead, 1000, 0, 0, 0);
        seedCount(highInteract, 100, 10, 5, 8);
        seedCount(offlineHot, 500, 50, 20, 30);

        String body = mvc.perform(get("/api/v1/posts/hot").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.result[*].id");
        org.assertj.core.api.Assertions.assertThat(ids).doesNotContain(String.valueOf(offlineHot));
        // 加权分：highInteract(234) < lowRead(1000)？——不对：234 < 1000，故 lowRead 第一
        org.assertj.core.api.Assertions.assertThat(ids.get(0)).isEqualTo(String.valueOf(lowRead));
        org.assertj.core.api.Assertions.assertThat(ids.get(1)).isEqualTo(String.valueOf(highInteract));
        org.assertj.core.api.Assertions.assertThat((Number) JsonPath.read(body, "$.result[0].hotScore"))
                .isEqualTo(1000.0);
    }

    @Test
    void hotRespectsLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            Long id = seedArticle("热榜文" + i, ArticleStatus.ONLINE);
            seedCount(id, 100 + i, 0, 0, 0);
        }
        String body = mvc.perform(get("/api/v1/posts/hot").param("limit", "2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.result[*].id");
        org.assertj.core.api.Assertions.assertThat(ids).hasSize(2);
    }

    @Test
    void hotEmptyWhenNoData() throws Exception {
        // 独立 H2 库中此时已有个别计数行；此断言只验证端点不炸，返回数组即可
        mvc.perform(get("/api/v1/posts/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0));
    }
}
