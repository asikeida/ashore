package me.asikeida.ashore.service.interact.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.interact.repository.entity.CommentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论 Mapper。
 */
@Mapper
public interface CommentMapper extends BaseMapper<CommentDO> {
}
