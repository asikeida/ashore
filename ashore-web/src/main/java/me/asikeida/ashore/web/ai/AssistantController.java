package me.asikeida.ashore.web.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.post.PostDetailVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.ai.AiChatClient;
import me.asikeida.ashore.service.ai.service.ArticleAssistantService;
import me.asikeida.ashore.service.article.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 面经 AI 助手端点：常驻详情页右侧，系统提示词自动携带文章全文。
 * SSE 管道与 AI 面试官同款（事件协议 {"d"} / {"done"} / {"err"}）；
 * 对话不落库（历史由前端携带，面板级轻量交互）。
 */
@Tag(name = "AI 助手", description = "面经详情页常驻 AI 助手：阅读当前文章并实时对话")
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ArticleAssistantService assistantService;

    private final ArticleService articleService;

    private final ExecutorService aiStreamExecutor;

    public AssistantController(ArticleAssistantService assistantService, ArticleService articleService,
                               ExecutorService aiStreamExecutor) {
        this.assistantService = assistantService;
        this.articleService = articleService;
        this.aiStreamExecutor = aiStreamExecutor;
    }

    /** 历史摘要（前端进入面板时取文章全文做上下文展示用——返回标题+前 N 字） */
    @Operation(summary = "助手上下文预览", description = "需登录；返回文章标题与正文摘要（供面板展示\"已阅读本文\"）")
    @Permission
    @GetMapping("/{articleId}/context")
    public ResVo<String> context(@PathVariable Long articleId) {
        PostDetailVo detail = articleService.detail(articleId, ReqContext.loginUser());
        String excerpt = detail.content() == null ? "" :
                detail.content().substring(0, Math.min(160, detail.content().length()));
        return me.asikeida.ashore.api.resp.ResVo.ok(detail.title() + "\n" + excerpt + "…");
    }

    @Operation(summary = "流式对话", description = "需登录；SSE 增量推送（事件协议与面试官一致），历史由请求体携带")
    @Permission
    @PostMapping("/{articleId}/stream")
    public SseEmitter stream(@PathVariable Long articleId, @RequestBody AssistantReq req) {
        LoginUser user = ReqContext.loginUser();
        SseEmitter emitter = new SseEmitter(180_000L);
        aiStreamExecutor.execute(() -> {
            try {
                String full = assistantService.answerStream(articleId, req.message(), req.history(),
                        delta -> {
                            try {
                                emitter.send(SseEmitter.event().data(
                                        MAPPER.writeValueAsString(Map.of("d", delta))));
                            } catch (Exception e) {
                                throw new IllegalStateException("SSE 推送中断", e);
                            }
                        });
                emitter.send(SseEmitter.event().data(
                        MAPPER.writeValueAsString(Map.of("done", true, "total", full.length()))));
                emitter.complete();
            } catch (Exception e) {
                log.warn("助手流式回答失败 articleId={}: {}", articleId, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data(
                            MAPPER.writeValueAsString(Map.of("err",
                                    e.getMessage() == null ? "AI 服务暂不可用" : e.getMessage()))));
                    emitter.complete();
                } catch (Exception ignore) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    /** 助手对话请求体：本轮消息 + 前端持有的历史（user/assistant 交替） */
    public record AssistantReq(String message, List<AiChatClient.Message> history) {
    }
}
