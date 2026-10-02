package me.asikeida.ashore.service.user.service;

import me.asikeida.ashore.api.vo.user.UserProfileVo;

/** 他人主页读域（公开）：资料 + 已上线作品 + 汇总计数 */
public interface UserProfileService {

    /** 主页数据：用户不存在抛 NOT_FOUND；文章仅 ONLINE 且未删除 */
    UserProfileVo profile(Long userId);
}
