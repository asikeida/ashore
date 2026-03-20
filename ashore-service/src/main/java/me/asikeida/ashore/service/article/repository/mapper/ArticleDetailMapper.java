package me.asikeida.ashore.service.article.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面经正文版本 Mapper。
 */
@Mapper
public interface ArticleDetailMapper extends BaseMapper<ArticleDetailDO> {
}
