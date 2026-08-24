package me.asikeida.ashore.web.user;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.vo.profile.MeArticleItemVo;
import me.asikeida.ashore.service.profile.service.MeService;
import me.asikeida.ashore.service.user.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 个人中心端点测试（@MockitoBean 桩替换 MeService/SessionService，聚焦鉴权与契约）：
 * 未登录 401 / 登录后 myArticles 契约透传（状态+四项计数字段齐全）。
 * 数据组装的正确性（批量标签/计数）由 MeServiceImpl 数据路径与远端 E2E 覆盖。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mecontroller;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=me-secret"
})
@AutoConfigureMockMvc
class MeControllerTests {

    private static final String COOKIE = SessionService.COOKIE_NAME;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private MeService meService;

    @Test
    void myArticlesWithoutSessionIsUnauthorized() throws Exception {
        when(sessionService.validate("bad")).thenReturn(null);

        mvc.perform(get("/api/v1/me/articles").cookie(new Cookie(COOKIE, "bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(401001));
    }

    @Test
    void myArticlesReturnsStatusAndCountsContract() throws Exception {
        when(sessionService.validate("good")).thenReturn(new LoginUser(7L, "author", "USER"));
        when(meService.myArticles(7L)).thenReturn(List.of(
                new MeArticleItemVo("101", "我的面经A", List.of("MySQL"), "REVIEW",
                        "2026-11-09T10:00:00", 12, 3, 1, 2),
                new MeArticleItemVo("102", "已下线的面经B", List.of(), "OFFLINE",
                        "2026-11-08T09:00:00", 50, 9, 4, 6)));

        mvc.perform(get("/api/v1/me/articles").cookie(new Cookie(COOKIE, "good")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].articleId").value("101"))
                .andExpect(jsonPath("$.result[0].status").value("REVIEW"))
                .andExpect(jsonPath("$.result[0].read").value(12))
                .andExpect(jsonPath("$.result[0].praise").value(3))
                .andExpect(jsonPath("$.result[0].collect").value(1))
                .andExpect(jsonPath("$.result[0].comment").value(2))
                .andExpect(jsonPath("$.result[1].status").value("OFFLINE"));
    }

    @Test
    void myArticlesEmptyForNewAuthor() throws Exception {
        when(sessionService.validate("good")).thenReturn(new LoginUser(7L, "author", "USER"));
        when(meService.myArticles(7L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/me/articles").cookie(new Cookie(COOKIE, "good")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(0));
    }
}
