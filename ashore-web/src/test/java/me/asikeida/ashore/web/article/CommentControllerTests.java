package me.asikeida.ashore.web.article;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.CommentDO;
import me.asikeida.ashore.service.interact.repository.mapper.CommentMapper;
import me.asikeida.ashore.service.interact.service.CountService;
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
 * 评论两级折叠测试：发表顶级/回复、列表折叠结构、软删除与权限、计数联动。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:commentctx;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=review-p0-secret",
        "ashore.session.expire-days=30"
})
@AutoConfigureMockMvc
class CommentControllerTests {

    private static final String COOKIE = SessionService.COOKIE_NAME;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    /** 评论计数依赖 Redis，以桩替换并据此断言联动调用 */
    @MockitoBean
    private CountService countService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    private Long articleId;

    private Long commenterId;

    private Long adminId;

    @BeforeEach
    void setUp() {
        commenterId = insertUser("commenter");
        Long strangerId = insertUser("stranger");
        adminId = insertUser("comment-admin");
        UserDO admin = userMapper.selectById(adminId);
        admin.setRole("ADMIN");
        userMapper.updateById(admin);

        when(sessionService.validate("commenter-token"))
                .thenReturn(new LoginUser(commenterId, "commenter", "USER"));
        when(sessionService.validate("stranger-token"))
                .thenReturn(new LoginUser(strangerId, "stranger", "USER"));
        when(sessionService.validate("admin-token"))
                .thenReturn(new LoginUser(adminId, "admin", "ADMIN"));

        ArticleDO article = new ArticleDO();
        article.setTitle("评论测试面经");
        article.setAuthorId(999L);
        article.setStatus("ONLINE");
        article.setToppingStat(0);
        article.setDeleted(0);
        articleMapper.insert(article);
        articleId = article.getId();
    }

    private Long insertUser(String name) {
        UserDO u = new UserDO();
        u.setAccount("comment-" + name + "-" + System.nanoTime());
        u.setPasswordHash("x");
        u.setNickname(name + "的昵称");
        u.setRole("USER");
        u.setStatus("ACTIVE");
        userMapper.insert(u);
        return u.getId();
    }

    private String createComment(String token, String content, Long parentCommentId) throws Exception {
        String parentJson = parentCommentId == null ? "" : ",\"parentCommentId\":" + parentCommentId;
        MvcResult result = mvc.perform(post("/api/v1/posts/" + articleId + "/comments")
                        .cookie(new Cookie(COOKIE, token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"" + parentJson + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");
    }

    @Test
    void topLevelAndReplyCollapseStructure() throws Exception {
        String topId = createComment("commenter-token", "这是一条顶级评论", null);
        String replyId = createComment("commenter-token", "这是一条子回复", Long.valueOf(topId));

        MvcResult list = mvc.perform(get("/api/v1/posts/" + articleId + "/comments"))
                .andExpect(status().isOk())
                .andReturn();

        String body = list.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(JsonPath.<List<String>>read(body, "$.result.items[*].id"))
                .containsExactly(topId);
        org.assertj.core.api.Assertions.assertThat(
                JsonPath.<List<String>>read(body, "$.result.items[0].replies[*].id")).containsExactly(replyId);
        org.assertj.core.api.Assertions.assertThat(JsonPath.<Integer>read(body, "$.result.total")).isEqualTo(1);
    }

    @Test
    void replyToChildReplyLiftsToSameTopLevel() throws Exception {
        String topId = createComment("commenter-token", "顶级", null);
        String childId = createComment("commenter-token", "子回复", Long.valueOf(topId));
        // 回复"子回复"：仍应挂在同一顶级下
        createComment("commenter-token", "孙回复", Long.valueOf(childId));

        List<CommentDO> all = commentMapper.selectList(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getArticleId, articleId));
        org.assertj.core.api.Assertions.assertThat(all).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(all.stream()
                .filter(c -> c.getContent().equals("孙回复"))
                .findFirst().orElseThrow().getTopCommentId()).isEqualTo(Long.valueOf(topId));
    }

    @Test
    void deleteByAuthorSoftDeletesAndStrangerForbidden() throws Exception {
        String topId = createComment("commenter-token", "待删除评论", null);

        // 陌生人删除 → 403
        mvc.perform(delete("/api/v1/comments/" + topId).cookie(new Cookie(COOKIE, "stranger-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(403001));

        // 作者删除 → 软删除，列表消失，计数递减
        mvc.perform(delete("/api/v1/comments/" + topId).cookie(new Cookie(COOKIE, "commenter-token")))
                .andExpect(status().isOk());
        verify(countService).incrComment(articleId, -1);

        mvc.perform(get("/api/v1/posts/" + articleId + "/comments"))
                .andExpect(jsonPath("$.result.total").value(0));
    }

    @Test
    void blankContentRejected() throws Exception {
        mvc.perform(post("/api/v1/posts/" + articleId + "/comments")
                        .cookie(new Cookie(COOKIE, "commenter-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(400001));
    }
}
