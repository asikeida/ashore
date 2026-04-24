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

    /** 历史消息（仅本人） */
    List<InterviewMessageVo> history(Long sessionId, LoginUser candidate);
}
