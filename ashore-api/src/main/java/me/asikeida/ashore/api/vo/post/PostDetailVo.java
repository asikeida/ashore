package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 面经详情视图：含最新版本正文、标签与互动计数；非上线状态仅作者/管理员可见。
 */
public record PostDetailVo(String id, String title, String status, List<String> tags,
                           String content, Integer version, String authorId, String createTime,
                           long praiseCount, long collectCount, long commentCount, long readCount,
                           boolean praised, boolean collected) {
}
