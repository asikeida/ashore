package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 列表项视图：含作者昵称与标签（批量查询组装，避免 N+1）。
 */
public record PostItemVo(String id, String title, List<String> tags, String authorNickname, String createTime) {
}
