package me.asikeida.ashore.web.article;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * LIKE 降级路径搜索测试（ES 关闭时门面自动降级，降级语义回归防线）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:likesearch;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=review-p0-secret",
        "ashore.session.expire-days=30",
        "ashore.search.enabled=false"
})
@AutoConfigureMockMvc
class LikeSearchTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleDetailMapper detailMapper;

    private Long seed(String title, String content, String status) {
        ArticleDO a = new ArticleDO();
        a.setTitle(title);
        a.setAuthorId(1L);
        a.setStatus(status);
        a.setToppingStat(0);
        a.setDeleted(0);
        a.setCreateTime(LocalDateTime.now().withNano(0));
        articleMapper.insert(a);
        if (content != null) {
            ArticleDetailDO d = new ArticleDetailDO();
            d.setArticleId(a.getId());
            d.setContent(content);
            d.setVersion(1);
            detailMapper.insert(d);
        }
        return a.getId();
    }

    @Test
    void likeFallbackSearchesTitleAndContent() throws Exception {
        seed("Redis 面试高频题", "缓存穿透与雪崩", ArticleStatus.ONLINE.name());
        Long contentOnly = seed("标题无关的一面", "聊到了 Redis 持久化机制", ArticleStatus.ONLINE.name());
        seed("REVIEW 未上线不命中", "Redis 相关但未上线", ArticleStatus.REVIEW.name());

        MvcResult result = mvc.perform(get("/api/v1/posts/search").param("q", "Redis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andReturn();

        List<String> ids = JsonPath.read(result.getResponse().getContentAsString(),
                "$.result[*].id");
        org.assertj.core.api.Assertions.assertThat(ids).containsExactlyInAnyOrder(
                String.valueOf(contentOnly) /* 顺序按时间 */, ids.get(0));
        org.assertj.core.api.Assertions.assertThat(ids).hasSize(2);

        // LIKE 降级路径无高亮片段
        List<String> highlights = JsonPath.read(result.getResponse().getContentAsString(),
                "$.result[*].titleHighlight");
        org.assertj.core.api.Assertions.assertThat(highlights).allMatch(java.util.Objects::isNull);
    }

    @Test
    void blankKeywordReturnsEmpty() throws Exception {
        mvc.perform(get("/api/v1/posts/search").param("q", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(0));
    }
}
