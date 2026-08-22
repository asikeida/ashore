package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 列表项视图：含作者昵称与标签（批量查询组装，避免 N+1）。
 * topped 为置顶标记（列表排序第一键，管理台可设置）——库内 0/1 转布尔语义化，
 * 前端按 topped 渲染置顶标识，不暴露内部 TINYINT。
 */
public record PostItemVo(String id, String title, List<String> tags, String authorNickname,
                         String createTime, boolean topped) {
}
