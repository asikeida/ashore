package me.asikeida.ashore.service.ai.service;

import java.util.List;
import java.util.function.Consumer;
import me.asikeida.ashore.service.ai.AiChatClient;

/**
 * 面经 AI 助手：常驻详情页右侧的轻量对话。与 AI 面试官的区别——
 * 无会话状态机、不落库（历史由前端携带），系统提示词自动携带当前文章全文，
 * 使助手"读过这篇文章"。
 */
public interface ArticleAssistantService {

    /**
     * 流式回答（SSE 增量回调）。
     *
     * @param articleId 当前阅读的面经
     * @param message   本轮用户消息
     * @param history   既有对话（前端携带，原样拼入上下文）
     * @param onDelta   增量回调
     * @return 完整回复
     */
    String answerStream(Long articleId, String message, List<AiChatClient.Message> history,
                        Consumer<String> onDelta);
}
