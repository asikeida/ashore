package me.asikeida.ashore.service.report.repository.mapper;

import me.asikeida.ashore.service.report.repository.entity.ReportDO;
import org.apache.ibatis.annotations.Mapper;

/** 举报 Mapper（@MapperScan 覆盖 **.mapper 包） */
@Mapper
public interface ReportMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<ReportDO> {
}
