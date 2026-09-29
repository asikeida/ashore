package me.asikeida.ashore.service.interact.repository.mapper;

import me.asikeida.ashore.service.interact.repository.entity.CommentLikeDO;
import org.apache.ibatis.annotations.Mapper;

/** 评论点赞 Mapper（@MapperScan 覆盖 **.mapper 包） */
@Mapper
public interface CommentLikeMapper
        extends com.baomidou.mybatisplus.core.mapper.BaseMapper<CommentLikeDO> {
}
