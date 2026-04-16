package me.asikeida.ashore.api.vo.rank;

/**
 * 排行榜条目。
 */
public record RankItemVo(int rank, String userId, String nickname, double score) {
}
