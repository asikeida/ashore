package me.asikeida.ashore.service.interact.repository.mapper;

import me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO;
import org.apache.ibatis.annotations.Mapper;

/** 面经 AI 助手消息 Mapper（落 **.mapper 包：@MapperScan 后自动扫描退避，见 K3 坑④） */
@Mapper
public interface AssistantMessageMapper
        extends com.baomidou.mybatisplus.core.mapper.BaseMapper<AssistantMessageDO> {
}
