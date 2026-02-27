package me.asikeida.ashore.service.user.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。通用单表 CRUD 由 MyBatis-Plus BaseMapper 提供；
 * 需要复杂 SQL 时再追加 XML，不预建。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
