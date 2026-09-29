package me.asikeida.ashore.api.vo.profile;

/**
 * 个人中心"AI 助手记录"分组项：一个面经一组对话。
 * turns = 消息条数（user+assistant 都算）；lastContent 为最近一条消息摘要。
 */
public record AssistantRecordVo(
        String articleId,
        String articleTitle,
        long turns,
        String lastTime,
        String lastContent
) {
}
