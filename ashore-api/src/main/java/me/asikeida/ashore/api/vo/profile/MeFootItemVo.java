package me.asikeida.ashore.api.vo.profile;

import java.util.List;

/**
 * 个人中心足迹项视图：我收藏/点赞的面经卡片（含作者昵称与标签，footTime 为动作时间）。
 */
public record MeFootItemVo(
        String articleId,
        String title,
        List<String> tags,
        String authorNickname,
        String footTime
) {
}
