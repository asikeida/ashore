package me.asikeida.ashore.web.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.service.ai.AiChatClient;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.InterviewMessageDO;
import me.asikeida.ashore.service.interact.repository.mapper.InterviewMessageMapper;
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
 * AI 面试官闭环测试（@MockitoBean 桩替换 AI 客户端，聚焦消息持久化与会话归属）。
 * 真实 AI 调用待凭证（DeepSeek/智谱 key）后 E2E。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:interview;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=review-p0-secret",
        "ashore.session.expire-days=30"
})
@AutoConfigureMockMvc
class InterviewControllerTests {

    private static final String COOKIE = SessionService.COOKIE_NAME;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private AiChatClient aiChatClient;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleDetailMapper detailMapper;

    @Autowired
    private InterviewMessageMapper messageMapper;

    private Long articleId;

    private Long meId;

    @BeforeEach
    void setUp() {
        meId = 8801L;
        Long strangerId = 8802L;
        when(sessionService.validate("me-token")).thenReturn(new LoginUser(meId, "me", "USER"));
        when(sessionService.validate("stranger-token")).thenReturn(new LoginUser(strangerId, "stranger", "USER"));
        when(aiChatClient.chat(anyList())).thenReturn("追问：请展开讲讲缓存雪崩的应对方案。");

        ArticleDO a = new ArticleDO();
        a.setTitle("Redis 一面面经");
        a.setAuthorId(999L);
        a.setStatus(ArticleStatus.ONLINE.name());
        a.setToppingStat(0);
        a.setDeleted(0);
        a.setCreateTime(LocalDateTime.now().withNano(0));
        articleMapper.insert(a);
        articleId = a.getId();
        ArticleDetailDO d = new ArticleDetailDO();
        d.setArticleId(articleId);
        d.setContent("面试官问了缓存穿透。");
        d.setVersion(1);
        detailMapper.insert(d);
    }

    @Test
    void startThenAnswerPersistsFullDialog() throws Exception {
        MvcResult start = mvc.perform(post("/api/v1/posts/" + articleId + "/interview")
                        .cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.questionCount").value(1))
                .andReturn();
        String sessionId = JsonPath.read(start.getResponse().getContentAsString(), "$.result.sessionId");

        mvc.perform(post("/api/v1/interviews/" + sessionId + "/answer")
                        .cookie(new Cookie(COOKIE, "me-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"雪崩可以用随机过期和多级缓存解决。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.questionCount").value(2));

        // 消息持久化：INTERVIEWER 出题 → USER 作答 → INTERVIEWER 追问
        List<InterviewMessageDO> messages = messageMapper.selectList(new LambdaQueryWrapper<InterviewMessageDO>()
                .eq(InterviewMessageDO::getSessionId, Long.valueOf(sessionId))
                .orderByAsc(InterviewMessageDO::getId));
        org.assertj.core.api.Assertions.assertThat(messages).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(messages.get(0).getRole()).isEqualTo("INTERVIEWER");
        org.assertj.core.api.Assertions.assertThat(messages.get(1).getRole()).isEqualTo("USER");
        org.assertj.core.api.Assertions.assertThat(messages.get(2).getRole()).isEqualTo("INTERVIEWER");

        // 历史（仅本人可见）
        mvc.perform(get("/api/v1/interviews/" + sessionId).cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(3));
        mvc.perform(get("/api/v1/interviews/" + sessionId).cookie(new Cookie(COOKIE, "stranger-token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void startReusesActiveSession() throws Exception {
        mvc.perform(post("/api/v1/posts/" + articleId + "/interview")
                        .cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk());
        // 第二次开局：复用会话（AI 仅在首局调用一次 + 复用不再调用 → 共 1 次）
        mvc.perform(post("/api/v1/posts/" + articleId + "/interview")
                        .cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk());

        verify(aiChatClient, times(1)).chat(anyList());
    }

    @Test
    void answerBelongsToOtherSessionNotFound() throws Exception {
        String sessionId = JsonPath.read(mvc.perform(post("/api/v1/posts/" + articleId + "/interview")
                                .cookie(new Cookie(COOKIE, "me-token")))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), "$.result.sessionId");

        mvc.perform(post("/api/v1/interviews/" + sessionId + "/answer")
                        .cookie(new Cookie(COOKIE, "stranger-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"别人的回答\"}"))
                .andExpect(status().isNotFound());
    }
}
