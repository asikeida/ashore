package me.asikeida.ashore.api.vo.post;

/**
 * 热榜条目：热度分 + 核心计数（公开数据，仅 ONLINE 面经参与）。
 * 热度 = read*1 + praise*4 + collect*6 + comment*8（互动权重高于浏览）。
 */
public record HotPostItemVo(
        String id,
        String title,
        String authorNickname,
        long read,
        long praise,
        long comment,
        double hotScore
) {
}
