package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 面经视图（列表/写操作返回）。id 为字符串原因见 UserVo。
 */
public record PostVo(String id, String title, String status, List<String> tags, String createTime) {
}
