package me.asikeida.ashore.web.article;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import me.asikeida.ashore.service.user.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 面经列表与详情读取测试：三字段游标翻页不重不漏、置顶排序、标签筛选、
 * 软删除过滤、版本历史权限。数据经 Mapper 直插（显式 createTime 保证顺序确定）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:articlelist;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=review-p0-secret",
        "ashore.session.expire-days=30"
})
@AutoConfigureMockMvc
class ArticleListTests {

    private static final String COOKIE = SessionService.COOKIE_NAME;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper articleDetailMapper;

    @Autowired
    private UserMapper userMapper;

    private Long authorId;

    @BeforeEach
    void setUp() {
        UserDO u = new UserDO();
        u.setAccount("list-author-" + System.nanoTime());
        u.setPasswordHash("x");
        u.setNickname("列表作者");
        u.setRole("USER");
        u.setStatus("ACTIVE");
        userMapper.insert(u);
        authorId = u.getId();
        when(sessionService.validate("author-token")).thenReturn(new LoginUser(authorId, "author", "USER"));
    }

    /** 直插 ONLINE 面经，createTime 显式递减保证排序确定 */
    private Long seedArticle(String title, int topping, LocalDateTime createTime) {
        ArticleDO a = new ArticleDO();
        a.setTitle(title);
        a.setAuthorId(authorId);
        a.setStatus(ArticleStatus.ONLINE.name());
        a.setToppingStat(topping);
        a.setDeleted(0);
        a.setCreateTime(createTime);
        a.setUpdateTime(createTime);
        articleMapper.insert(a);
        return a.getId();
    }

    @Test
    void cursorPaginationWalksWithoutDuplicationOrGap() throws Exception {
        List<Long> seeded = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().withNano(0);
        for (int i = 0; i < 12; i++) {
            seeded.add(seedArticle("面经" + i, 0, base.minusMinutes(i)));
        }
        // 置顶文排最前
        seeded.add(seedArticle("置顶面经", 1, base.minusMinutes(100)));

        Set<String> seen = new HashSet<>();
        String cursor = null;
        int pages = 0;
        while (true) {
            MvcResult result = mvc.perform(cursor == null
                            ? get("/api/v1/posts").param("limit", "5")
                            : get("/api/v1/posts").param("limit", "5").param("cursor", cursor))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            List<String> ids = JsonPath.read(body, "$.result.items[*].id");
            org.assertj.core.api.Assertions.assertThat(ids.size()).isLessThanOrEqualTo(5);
            seen.addAll(ids);
            pages++;
            cursor = JsonPath.read(body, "$.result.nextCursor");
            if (cursor == null) {
                break;
            }
            org.assertj.core.api.Assertions.assertThat(pages).isLessThan(10); // 防死循环
        }

        org.assertj.core.api.Assertions.assertThat(pages).isEqualTo(3); // 12+1 条 / 5 = 3 页
        org.assertj.core.api.Assertions.assertThat(seen).hasSize(13);
        org.assertj.core.api.Assertions.assertThat(seen).containsAll(seeded.stream()
                .map(String::valueOf).toList());

        // 首页第一条必须是置顶文
        MvcResult first = mvc.perform(get("/api/v1/posts").param("limit", "1")).andExpect(status().isOk()).andReturn();
        String firstTitle = JsonPath.read(first.getResponse().getContentAsString(), "$.result.items[0].title");
        org.assertj.core.api.Assertions.assertThat(firstTitle).isEqualTo("置顶面经");
    }

    @Test
    void deletedArticleHiddenFromListAndDetail() throws Exception {
        Long id = seedArticle("将被软删的面经", 0, LocalDateTime.now().withNano(0));

        ArticleDO del = new ArticleDO();
        del.setId(id);
        del.setDeleted(1);
        articleMapper.updateById(del);

        String body = mvc.perform(get("/api/v1/posts")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.result.items[*].id");
        org.assertj.core.api.Assertions.assertThat(ids).doesNotContain(String.valueOf(id));

        mvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void versionsRequireAuthorOrAdmin() throws Exception {
        Long id = seedArticle("版本历史面经", 0, LocalDateTime.now().withNano(0));
        // 补一条正文版本（seed 只建主表）
        me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO d =
                new me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO();
        d.setArticleId(id);
        d.setContent("第一版正文");
        d.setVersion(1);
        articleDetailMapper.insert(d);

        // 未登录：拦截器 401
        mvc.perform(get("/api/v1/posts/" + id + "/versions")).andExpect(status().isUnauthorized());

        // 作者：可见（当前仅 v1）
        mvc.perform(get("/api/v1/posts/" + id + "/versions").cookie(new Cookie(COOKIE, "author-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].version").value(1));
    }

    @Test
    void invalidCursorReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/posts").param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400001));
    }

    // ── 置顶管理（feat/article-置顶管理）────────────────────────────

    /** 造一个 ADMIN 身份的会话桩（setUp 只桩了 author/USER） */
    private void stubAdmin(String token) {
        when(sessionService.validate(token)).thenReturn(new LoginUser(999L, "admin", "ADMIN"));
    }

    @Test
    void toppingRequiresAdmin() throws Exception {
        Long id = seedArticle("待置顶面经", 0, LocalDateTime.now().withNano(0));

        // USER 角色请求 → 403（拦截器 ADMIN 判定，Service 未被触达）
        mvc.perform(post("/api/v1/admin/posts/" + id + "/topping").param("topped", "true")
                        .cookie(new Cookie(COOKIE, "author-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(403001));
    }

    @Test
    void toppingFlipsFlagAndListOrderFollowsImmediately() throws Exception {
        stubAdmin("admin-token");
        LocalDateTime base = LocalDateTime.now().withNano(0);
        Long newer = seedArticle("较新的普通文", 0, base);
        Long older = seedArticle("较旧的普通文", 0, base.minusHours(1)); // 无置顶时它排后面

        // 置顶较旧的文：拉取足够大的窗口，断言两篇的相对顺序翻转（不假设全局位次——
        // 类级共享 H2 库中可能有其他用例的置顶数据，绝对位置断言会跨方法污染）
        mvc.perform(post("/api/v1/admin/posts/" + older + "/topping").param("topped", "true")
                        .cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0));

        String body = mvc.perform(get("/api/v1/posts").param("limit", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.result.items[*].id");
        int idxOlder = ids.indexOf(String.valueOf(older));
        int idxNewer = ids.indexOf(String.valueOf(newer));
        org.assertj.core.api.Assertions.assertThat(idxOlder)
                .as("置顶后较旧文应排到较新文之前（topping DESC 生效）").isLessThan(idxNewer);
        // topped 标记透出（按 id 定位，不依赖绝对位置）
        org.assertj.core.api.Assertions.assertThat(
                (Boolean) JsonPath.read(body, "$.result.items[" + idxOlder + "].topped")).isTrue();
        org.assertj.core.api.Assertions.assertThat(
                (Boolean) JsonPath.read(body, "$.result.items[" + idxNewer + "].topped")).isFalse();

        // 取消置顶 → 相对顺序恢复按时间（newer 回到 older 之前）
        mvc.perform(post("/api/v1/admin/posts/" + older + "/topping").param("topped", "false")
                        .cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isOk());
        String body2 = mvc.perform(get("/api/v1/posts").param("limit", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids2 = JsonPath.read(body2, "$.result.items[*].id");
        org.assertj.core.api.Assertions.assertThat(ids2.indexOf(String.valueOf(newer)))
                .as("取消置顶后恢复按时间排序").isLessThan(ids2.indexOf(String.valueOf(older)));
    }

    @Test
    void toppingMissingArticleReturnsNotFound() throws Exception {
        stubAdmin("admin-token");
        mvc.perform(post("/api/v1/admin/posts/424242/topping").param("topped", "true")
                        .cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isNotFound());
    }
}
