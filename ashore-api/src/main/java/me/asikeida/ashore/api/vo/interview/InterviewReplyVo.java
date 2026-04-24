package me.asikeida.ashore.api.vo.interview;

/**
 * AI 面试官回复视图：sessionId 字符串化原因见 UserVo。
 */
public record InterviewReplyVo(String sessionId, Integer questionCount, String content) {
}
