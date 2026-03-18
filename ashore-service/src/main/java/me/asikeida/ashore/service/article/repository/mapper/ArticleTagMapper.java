package me.asikeida.ashore.service.article.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.article.repository.entity.ArticleTagDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面经-标签关联 Mapper。
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTagDO> {
}
