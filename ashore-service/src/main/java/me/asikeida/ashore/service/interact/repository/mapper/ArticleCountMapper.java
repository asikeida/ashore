package me.asikeida.ashore.service.interact.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.interact.repository.entity.ArticleCountDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面经计数 Mapper。
 */
@Mapper
public interface ArticleCountMapper extends BaseMapper<ArticleCountDO> {
}
