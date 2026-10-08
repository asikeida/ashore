package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 列表项视图：含作者昵称与标签（批量查询组装，避免 N+1）。
 * topped 为置顶标记（列表排序第一键，管理台可设置）——库内 0/1 转布尔语义化。
 * excerpt 为发布/编辑时生成的纯文本摘要（无 Markdown 符号）；
 * read/praise/comment 来自 article_count 聚合（批量取，卡片信息密度对齐主流社区）。
 */
public record PostItemVo(String id, String title, List<String> tags, String authorNickname,
                         String createTime, boolean topped, String excerpt,
                         long read, long praise, long comment) {
}
