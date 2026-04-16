package me.asikeida.ashore.web.notice;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.Cookie;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.service.interact.repository.entity.NotifyMsgDO;
import me.asikeida.ashore.service.interact.repository.mapper.NotifyMsgMapper;
import me.asikeida.ashore.service.user.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 通知端点测试：列表/类型筛选/未读数/全部已读（归属仅本人）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notice;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=review-p0-secret",
        "ashore.session.expire-days=30"
})
@AutoConfigureMockMvc
class NoticeControllerTests {

    private static final String COOKIE = SessionService.COOKIE_NAME;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    @Autowired
    private NotifyMsgMapper notifyMapper;

    private Long me;

    @BeforeEach
    void setUp() {
        // H2 mem 库跨测试方法存活：先清表保证未读数等断言确定性
        notifyMapper.delete(null);

        me = 8001L;
        Long other = 8002L;
        when(sessionService.validate("me-token")).thenReturn(new LoginUser(me, "me", "USER"));

        notifyMapper.insert(notify(me, other, NotifyTypeEnum.PRAISE, 101L, true));
        notifyMapper.insert(notify(me, other, NotifyTypeEnum.COMMENT, 102L, false));
        notifyMapper.insert(notify(me, other, NotifyTypeEnum.SYSTEM, null, false));
        // 他人的通知：不应出现在我的列表
        notifyMapper.insert(notify(other, me, NotifyTypeEnum.PRAISE, 103L, false));
    }

    private NotifyMsgDO notify(Long receiver, Long operator, NotifyTypeEnum type, Long articleId, boolean read) {
        NotifyMsgDO n = new NotifyMsgDO();
        n.setUserId(receiver);
        n.setOperatorId(operator);
        n.setType(type.name());
        n.setArticleId(articleId);
        n.setReadStat(read ? 1 : 0);
        return n;
    }

    @Test
    void listOnlyOwnNotificationsWithTypeFilter() throws Exception {
        MvcResult praise = mvc.perform(get("/api/v1/notices")
                        .param("type", "PRAISE")
                        .cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(praise.getResponse().getContentAsString())
                .contains("PRAISE").doesNotContain("COMMENT");

        MvcResult all = mvc.perform(get("/api/v1/notices").cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk())
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(
                JsonPath.<List<String>>read(all.getResponse().getContentAsString(),
                        "$.result[*].type")).hasSize(3);
    }

    @Test
    void unreadCountThenMarkAllRead() throws Exception {
        mvc.perform(get("/api/v1/notices/unread-count").cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(2));

        mvc.perform(put("/api/v1/notices/read").cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/notices/unread-count").cookie(new Cookie(COOKIE, "me-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(0));

        Long remaining = notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMsgDO>()
                .eq(NotifyMsgDO::getUserId, me)
                .eq(NotifyMsgDO::getReadStat, 0));
        org.assertj.core.api.Assertions.assertThat(remaining).isZero();
    }

    @Test
    void endpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/v1/notices")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notices/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/notices/read")).andExpect(status().isUnauthorized());
    }
}
