package me.asikeida.ashore.service.ai.service;

import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.vo.interview.InterviewMessageVo;
import me.asikeida.ashore.api.vo.interview.InterviewReplyVo;

/**
 * AI 面试官：基于面经内容出题 → 用户作答 → 点评+追问 的多轮闭环。
 * <p>每轮消耗一次 AI 调用（受每日限流）；会话仅归属人可访问。</p>
 */
public interface InterviewService {

    /** 开局：读面经构造上下文，AI 出第一题，建会话（同一面经同人复用未关闭会话） */
    InterviewReplyVo start(Long articleId, LoginUser candidate);

    /** 作答：存答案 → 携历史请 AI 点评并追问 */
    InterviewReplyVo answer(Long sessionId, String answer, LoginUser candidate);

    /**
     * 流式作答：AI 点评+追问的增量片段实时回调 onDelta（SSE 透传）；
     * 回复完成后统一落库（答案 + 完整点评 + 轮次+1）。
     * <p>失败语义：流中途异常时不落任何消息（重试无损），异常上抛给控制器收尾。</p>
     *
     * @return 完整点评文本
     */
    String answerStream(Long sessionId, String answer, LoginUser candidate,
                        java.util.function.Consumer<String> onDelta);

    /** 历史消息（仅本人） */
    List<InterviewMessageVo> history(Long sessionId, LoginUser candidate);
}
