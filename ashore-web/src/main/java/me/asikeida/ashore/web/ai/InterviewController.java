package me.asikeida.ashore.web.ai;

import java.util.List;
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

/**
 * AI 面试官端点：开局出题 / 作答追问 / 历史。会话仅归属人可访问。
 * 未配置 AI 供应商时返回 503001（同 OAuth 模式）。
 */
@Tag(name = "AI 面试官", description = "基于面经的 AI 出题、点评与追问")
@RestController
@RequestMapping("/api/v1")
public class InterviewController {

    /** 作答请求 */
    public record AnswerReq(@NotBlank(message = "回答不能为空") @Size(max = 5000) String content) {
    }

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
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
