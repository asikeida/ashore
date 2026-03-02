package me.asikeida.ashore.service.user.service;

import me.asikeida.ashore.api.vo.auth.LoginReq;
import me.asikeida.ashore.api.vo.auth.RegisterReq;
import me.asikeida.ashore.api.vo.auth.UserVo;

/**
 * 账密注册与登录。会话签发（Cookie/JWT）由 M2-3 的 SessionService 负责，本接口只做身份校验。
 */
public interface AuthService {

    /** 注册：账号唯一校验（先查后插 + 唯一索引兜底）、BCrypt 哈希、昵称缺省生成 */
    UserVo register(RegisterReq req);

    /** 登录：校验凭证，失败统一抛 LOGIN_FAILED（模糊文案防账号枚举） */
    UserVo login(LoginReq req);
}
