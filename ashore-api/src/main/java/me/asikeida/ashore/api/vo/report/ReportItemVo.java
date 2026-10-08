package me.asikeida.ashore.api.vo.report;

/**
 * 管理台举报条目：含目标摘要（标题/评论内容截断）与举报人昵称，处理队列直接可读。
 */
public record ReportItemVo(
        String id,
        String targetType,
        String targetId,
        String targetPreview,
        String reason,
        String reporterNickname,
        String status,
        String createTime
) {
}
