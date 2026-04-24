package me.asikeida.ashore.service.interact.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.interact.repository.entity.InterviewMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 面试官消息 Mapper。
 */
@Mapper
public interface InterviewMessageMapper extends BaseMapper<InterviewMessageDO> {
}
