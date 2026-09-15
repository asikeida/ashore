package me.asikeida.ashore.service.user.converter;

import me.asikeida.ashore.api.vo.auth.UserVo;
import me.asikeida.ashore.service.user.repository.entity.UserDO;

/**
 * UserDO 与 API 视图对象的转换。域数量增多后如样板代码显著，再统一引入 MapStruct。
 */
public final class UserConverter {

    private UserConverter() {
    }

    public static UserVo toVo(UserDO user) {
        return new UserVo(
                String.valueOf(user.getId()),
                user.getAccount(),
                user.getNickname(),
                user.getRole(),
                user.getAvatar()
        );
    }
}
