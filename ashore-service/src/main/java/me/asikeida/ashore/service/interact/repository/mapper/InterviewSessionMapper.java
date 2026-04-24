package me.asikeida.ashore.service.interact.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.interact.repository.entity.InterviewSessionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 面试官会话 Mapper。
 */
@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSessionDO> {
}
