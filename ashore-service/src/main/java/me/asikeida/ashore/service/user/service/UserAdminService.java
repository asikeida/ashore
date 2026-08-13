package me.asikeida.ashore.service.user.service;

import me.asikeida.ashore.api.vo.auth.UserVo;

/**
 * 用户管理（仅管理员调用）。封禁与解封是会话强相关操作：
 * 封禁必须在改库的同一请求内配对吊销全部会话（见实现类注释）。
 */
public interface UserAdminService {

    /** 用户管理列表（按账号模糊可选，limit 限量，create_time 倒序） */
    java.util.List<UserVo> list(String accountKeyword, int limit);

    /**
     * 封禁用户：status → BANNED + 吊销全部存量会话（配对操作，缺一不可）。
     * 幂等：已封禁用户重复封禁直接返回。禁止封禁 ADMIN 与自己。
     */
    UserVo ban(Long userId, Long operatorId);

    /** 解封：status → ACTIVE（存量会话已在封禁时清空，无需吊销） */
    UserVo unban(Long userId);
}
