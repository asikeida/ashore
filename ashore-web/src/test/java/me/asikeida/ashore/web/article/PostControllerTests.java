package me.asikeida.ashore.web.article;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.Cookie;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.service.CountService;
import me.asikeida.ashore.service.interact.service.FootService;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import me.asikeida.ashore.service.user.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 面经发布/编辑/状态流转测试：状态机全路径 + 权限矩阵 + 版本化。
 * 会话以 @MockitoBean 桩替换（validate 按 token 返回预置身份），用户经 UserMapper 直插。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:articlectx;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=review-p0-secret",
        "ashore.session.expire-days=30"
})
@AutoConfigureMockMvc
class PostControllerTests {

    private static final String COOKIE = SessionService.COOKIE_NAME;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    /** 计数依赖 Redis，web 测试环境以桩替换（计数真实行为由 M4-5 远端 E2E 覆盖） */
    @MockitoBean
    private CountService countService;

    @MockitoBean
    private FootService footService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleDetailMapper detailMapper;

    private Long authorId;

    private Long strangerId;

    private Long adminId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpUsers() {
        authorId = insertUser("author");
        strangerId = insertUser("stranger");
        adminId = insertUser("admin");
        userMapper.updateById(promoteToAdmin(adminId));

        when(sessionService.validate("author-token")).thenReturn(new LoginUser(authorId, "author", "USER"));
        when(sessionService.validate("stranger-token")).thenReturn(new LoginUser(strangerId, "stranger", "USER"));
        when(sessionService.validate("admin-token")).thenReturn(new LoginUser(adminId, "admin", "ADMIN"));
        // 详情读取会经 CountService 懒加载计数（依赖 Redis），桩替换为固定值
        when(countService.counts(any())).thenReturn(new CountService.Counts(5, 2, 3, 10));
        when(footService.toggle(any(), any(), any()))
                .thenReturn(new FootService.ToggleResult(true, 6));
    }

    private Long insertUser(String name) {
        UserDO u = new UserDO();
        u.setAccount("post-" + name + "-" + System.nanoTime());
        u.setPasswordHash("x");
        u.setNickname(name);
        u.setRole("USER");
        u.setStatus("ACTIVE");
        userMapper.insert(u);
        return u.getId();
    }

    private UserDO promoteToAdmin(Long id) {
        UserDO u = new UserDO();
        u.setId(id);
        u.setRole("ADMIN");
        return u;
    }

    private String publishBody(String title) {
        return "{\"title\":\"" + title + "\",\"content\":\"一面：Redis 持久化机制\",\"tags\":[\"字节跳动\",\"后端\"]}";
    }

    private String publishAsAuthor(String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/posts")
                        .cookie(new Cookie(COOKIE, "author-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andExpect(jsonPath("$.result.status").value("REVIEW"))
                .andExpect(jsonPath("$.result.tags.length()").value(2))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");
    }

    @Test
    void publishCreatesReviewArticleWithVersionOne() throws Exception {
        String id = publishAsAuthor("发布即审核测试" + System.nanoTime());

        ArticleDO article = articleMapper.selectById(Long.valueOf(id));
        assertThatStatus(article, "REVIEW");
        ArticleDetailDO detail = detailMapper.selectOne(new LambdaQueryWrapper<ArticleDetailDO>()
                .eq(ArticleDetailDO::getArticleId, Long.valueOf(id)));
        org.assertj.core.api.Assertions.assertThat(detail.getVersion()).isEqualTo(1);
    }

    @Test
    void adminDeleteHidesArticleAndRejectsOtherUsers() throws Exception {
        String id = publishAsAuthor("管理员删除测试" + System.nanoTime());
        mvc.perform(post("/api/v1/posts/" + id + "/online").cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/posts/" + id))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/admin/posts/" + id).cookie(new Cookie(COOKIE, "author-token")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/posts/" + id).cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(articleMapper.selectById(Long.valueOf(id)).getDeleted()).isEqualTo(1);
        mvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/posts/" + id).cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void editByAuthorCreatesNewVersionAndRebindsTags() throws Exception {
        String id = publishAsAuthor("编辑版本测试" + System.nanoTime());

        mvc.perform(put("/api/v1/posts/" + id)
                        .cookie(new Cookie(COOKIE, "author-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"编辑后的标题\",\"content\":\"更新后的正文\",\"tags\":[\"后端\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REVIEW"))
                .andExpect(jsonPath("$.result.tags.length()").value(1));

        org.assertj.core.api.Assertions.assertThat(detailMapper.selectCount(
                        new LambdaQueryWrapper<ArticleDetailDO>().eq(ArticleDetailDO::getArticleId, Long.valueOf(id))))
                .isEqualTo(2);
    }

    @Test
    void editByStrangerForbidden() throws Exception {
        String id = publishAsAuthor("越权编辑测试" + System.nanoTime());

        mvc.perform(put("/api/v1/posts/" + id)
                        .cookie(new Cookie(COOKIE, "stranger-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权标题过长可以\",\"content\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(403001));
    }

    @Test
    void fullStateFlowWithAdminOnline() throws Exception {
        String id = publishAsAuthor("状态机全流程" + System.nanoTime());

        // REVIEW → ONLINE（非管理员被拦截器 403；管理员通过）
        mvc.perform(post("/api/v1/posts/" + id + "/online").cookie(new Cookie(COOKIE, "author-token")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/posts/" + id + "/online").cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isOk());

        // ONLINE 后公开可见；作者编辑 → 回 REVIEW 且产生新版本
        mvc.perform(get("/api/v1/posts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ONLINE"))
                .andExpect(jsonPath("$.result.version").value(1));

        mvc.perform(put("/api/v1/posts/" + id)
                        .cookie(new Cookie(COOKIE, "author-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"状态机全流程更新版\",\"content\":\"第二版正文\",\"tags\":[]}"))
                .andExpect(jsonPath("$.result.status").value("REVIEW"));

        // 非上线状态陌生访客不可见（防枚举返回 404）
        mvc.perform(get("/api/v1/posts/" + id))
                .andExpect(status().isNotFound());

        // 管理员再上线 → 作者下线 → 重新提交 → 非法流转（OFFLINE 下重复 submit 或直接 online 语义）
        mvc.perform(post("/api/v1/posts/" + id + "/online").cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/posts/" + id + "/offline").cookie(new Cookie(COOKIE, "author-token")))
                .andExpect(status().isOk());
        // OFFLINE → ONLINE 非法（未审核不得上线）
        mvc.perform(post("/api/v1/posts/" + id + "/online").cookie(new Cookie(COOKIE, "admin-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400003));
        // OFFLINE → REVIEW（作者重新提交）合法
        mvc.perform(post("/api/v1/posts/" + id + "/submit").cookie(new Cookie(COOKIE, "author-token")))
                .andExpect(status().isOk());
    }

    @Test
    void nonNumericIdReturnsBadRequest() throws Exception {
        // 路径参数类型不匹配：专项 400（审查 R3 同族缺陷的防御）
        mvc.perform(get("/api/v1/posts/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400001));
    }

    @Test
    void praiseEndpointReturnsToggleState() throws Exception {
        String id = publishAsAuthor("点赞端点测试" + System.nanoTime());

        mvc.perform(post("/api/v1/posts/" + id + "/praise").cookie(new Cookie(COOKIE, "author-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andExpect(jsonPath("$.result").value(true));
    }

    private void assertThatStatus(ArticleDO article, String expected) {
        org.assertj.core.api.Assertions.assertThat(article.getStatus()).isEqualTo(expected);
    }
}
