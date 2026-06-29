package me.asikeida.ashore.service.ai;

import java.util.List;

/**
 * AI 对话客户端：OpenAI 兼容协议（chat/completions），多供应商顺序降级。
 * <p>paicoding 七家供应商各自的实现裁剪为单一兼容实现——DeepSeek/智谱/OpenAI
 * 等主流 API 均兼容该协议，仅 baseURL/model/key 不同；降级精髓保留（首个失败切下一个）。</p>
 */
public interface AiChatClient {

    /**
     * 对话（同步，阻塞至返回）。
     *
     * @param messages 多轮消息（role: system/user/assistant）
     * @return 助手回复文本
     * @throws me.asikeida.ashore.api.exception.BizException 无可用供应商（503001）或限流（400004）
     */
    String chat(List<Message> messages);

    /**
     * 流式对话（OpenAI 兼容 stream=true）：AI 产出的每个增量片段实时回调 onDelta，
     * 方法阻塞至流结束并返回拼接后的完整回复（供持久化）。
     * <p>供应商降级语义：流建立前失败切下一家；流建立后中断不切换（已吐出的
     * 增量无法撤回），抛出异常由上层决定如何收尾。</p>
     *
     * @param onDelta 增量片段回调（可为空片段防护由实现保证，回调只收非空片段）
     * @return 完整回复文本
     * @throws me.asikeida.ashore.api.exception.BizException 无可用供应商（503001）或限流（400004）
     */
    String streamChat(List<Message> messages, java.util.function.Consumer<String> onDelta);

    record Message(String role, String content) {
    }
}
