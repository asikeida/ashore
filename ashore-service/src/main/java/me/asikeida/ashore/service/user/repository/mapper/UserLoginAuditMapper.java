package me.asikeida.ashore.service.user.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.user.repository.entity.UserLoginAuditDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录审计 Mapper。
 */
@Mapper
public interface UserLoginAuditMapper extends BaseMapper<UserLoginAuditDO> {
}
