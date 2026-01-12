package me.asikeida.ashore;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 仅用于数据库装配冒烟测试的测试源 Mapper，不属于业务代码。
 */
@Mapper
public interface WiringMapper {

    @Select("SELECT 1")
    Integer one();
}
