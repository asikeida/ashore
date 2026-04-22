package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 搜索结果条目。title/content 高亮片段仅 ES 路径返回（LIKE 降级路径为 null）。
 */
public record SearchItemVo(String id, String title, List<String> tags, String authorNickname,
                           String createTime, String titleHighlight, String contentHighlight) {
}
