package me.asikeida.ashore.api.vo.profile;

/**
 * 个人中心面试会话项视图：哪篇面经、聊到第几题、当前状态与时间。
 */
public record MeInterviewItemVo(
        String sessionId,
        String articleId,
        String articleTitle,
        Integer questionCount,
        String status,
        String createTime,
        String updateTime
) {
}
