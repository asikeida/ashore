package me.asikeida.ashore.service.article.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.article.repository.entity.TagDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper。
 */
@Mapper
public interface TagMapper extends BaseMapper<TagDO> {
}
