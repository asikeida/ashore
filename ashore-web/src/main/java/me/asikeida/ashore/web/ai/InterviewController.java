package me.asikeida.ashore.web.ai;

import java.util.List;
import java.util.concurrent.ExecutorService;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.interview.InterviewMessageVo;
import me.asikeida.ashore.api.vo.interview.InterviewReplyVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.ai.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 面试官端点：开局出题 / 作答追问 / 历史。会话仅归属人可访问。
 * 未配置 AI 供应商时返回 503001（同 OAuth 模式）。
 */
@Tag(name = "AI 面试官", description = "基于面经的 AI 出题、点评与追问")
@RestController
@RequestMapping("/api/v1")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    private static final String ROLE_INTERVIEWER = "INTERVIEWER";

    /** 作答请求 */
    public record AnswerReq(@NotBlank(message = "回答不能为空") @Size(max = 5000) String content) {
    }

    private final InterviewService interviewService;

    /** AI 流式推送线程池（AI 调用以分钟计，不能占 Tomcat 工作线程） */
    private final ExecutorService aiStreamExecutor;

    public InterviewController(InterviewService interviewService,
                               ExecutorService aiStreamExecutor) {
        this.interviewService = interviewService;
        this.aiStreamExecutor = aiStreamExecutor;
    }

    @Operation(summary = "开始面试", description = "需登录；基于面经内容 AI 出第一题；同面经同人复用未关闭会话")
    @Permission
    @PostMapping("/posts/{id}/interview")
    public ResVo<InterviewReplyVo> start(@PathVariable Long id) {
        return ResVo.ok(interviewService.start(id, current()));
    }

    @Operation(summary = "作答", description = "需登录；AI 点评该回答并追问下一题")
    @Permission
    @PostMapping("/interviews/{sessionId}/answer")
    public ResVo<InterviewReplyVo> answer(@PathVariable Long sessionId,
                                          @Valid @RequestBody AnswerReq req) {
        return ResVo.ok(interviewService.answer(sessionId, req.content(), current()));
    }

    /**
     * 流式作答（SSE）：增量片段实时推送。事件协议：
     * <ul>
     *   <li>{@code {"d":"增量片段"}}——AI 增量（JSON 编码防换行破坏 SSE 帧）；</li>
     *   <li>{@code {"done":true}}——结束标记（点评已落库）；</li>
     *   <li>{@code {"err":"原因"}}——失败（本次不落库，可安全重试）。</li>
     * </ul>
     * AI 调用挪到专用线程池，Tomcat 工作线程只负责 SSE 写出。
     */
    @Operation(summary = "流式作答", description = "需登录；SSE 增量推送点评与追问，结束后落库")
    @Permission
    @PostMapping("/interviews/{sessionId}/answer/stream")
    public SseEmitter answerStream(@PathVariable Long sessionId,
                                   @Valid @RequestBody AnswerReq req) {
        LoginUser candidate = current();
        SseEmitter emitter = new SseEmitter(180_000L);
        aiStreamExecutor.execute(() -> {
            StringBuilder pending = new StringBuilder();
            try {
                String full = interviewService.answerStream(sessionId, req.content(), candidate,
                        delta -> {
                            try {
                                emitter.send(SseEmitter.event().data(
                                        MAPPER.writeValueAsString(java.util.Map.of("d", delta))));
                            } catch (Exception e) {
                                throw new IllegalStateException("SSE 推送中断", e);
                            }
                        });
                emitter.send(SseEmitter.event().data(
                        MAPPER.writeValueAsString(java.util.Map.of("done", true, "total", full.length()))));
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式作答失败 sessionId={}: {}", sessionId, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data(
                            MAPPER.writeValueAsString(java.util.Map.of("err",
                                    e.getMessage() == null ? "AI 服务暂不可用" : e.getMessage()))));
                    emitter.complete();
                } catch (Exception ignore) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Operation(summary = "面试历史", description = "需登录；仅会话归属人")
    @Permission
    @GetMapping("/interviews/{sessionId}")
    public ResVo<List<InterviewMessageVo>> history(@PathVariable Long sessionId) {
        return ResVo.ok(interviewService.history(sessionId, current()));
    }

    private static LoginUser current() {
        return ReqContext.loginUser();
    }
}
