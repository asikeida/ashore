package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 管理台列表项视图：在公开列表项基础上追加审核状态（REVIEW/ONLINE/OFFLINE）。
 * <p>历史缺陷记录：adminList 曾直接复用 {@link PostItemVo}（无 status 字段），
 * 导致管理台状态列自 M7 起恒为空——前端表格化后暴露，本 VO 为契约修正。</p>
 */
    public record AdminPostItemVo(
            String id,
            String title,
            List<String> tags,
            String authorNickname,
            String createTime,
            String status,
            boolean topped
    ) {
    /** 由公开列表项 + 审核状态组装（字段语义完全一致，仅追加 status） */
    public static AdminPostItemVo of(PostItemVo item, String status) {
        return new AdminPostItemVo(item.id(), item.title(), item.tags(),
                item.authorNickname(), item.createTime(), status, item.topped());
    }
}
