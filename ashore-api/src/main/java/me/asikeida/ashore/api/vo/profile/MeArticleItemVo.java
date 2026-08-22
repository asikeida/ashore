package me.asikeida.ashore.api.vo.profile;

import java.util.List;

/**
 * 个人中心"我的面经"项：卡片信息 + 审核状态 + 四项互动计数。
 * <p>status 为审核状态（REVIEW/ONLINE/OFFLINE）——作者据此看到自己作品
 * 的审核进度；计数来自 CountService（Redis hash，与详情页同一数据源）。</p>
 */
public record MeArticleItemVo(
        String articleId,
        String title,
        List<String> tags,
        String status,
        String createTime,
        long read,
        long praise,
        long collect,
        long comment
) {
}
