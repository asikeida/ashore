package me.asikeida.ashore.api.vo.user;

import java.util.List;

/**
 * 他人主页视图（公开）：用户资料 + 其已上线面经 + 汇总数据。
 * <p>不含敏感字段（无 account/passwordHash/status——封禁状态对外不可见，
 * 封禁用户的主页仅返回 ONLINE 内容，与匿名视角一致）。</p>
 */
public record UserProfileVo(
        String id,
        String nickname,
        String avatar,
        String role,
        String createTime,
        long articleCount,
        long totalRead,
        long totalPraise,
        List<PostCard> articles
) {
    /** 主页文章卡：复用公开卡片字段 */
    public record PostCard(String id, String title, List<String> tags,
                           String createTime, boolean topped) {
    }
}
