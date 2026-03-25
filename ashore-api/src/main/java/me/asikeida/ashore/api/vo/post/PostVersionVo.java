package me.asikeida.ashore.api.vo.post;

/**
 * 正文版本历史项（作者/管理员可见）。
 */
public record PostVersionVo(Integer version, String createTime, String content) {
}
