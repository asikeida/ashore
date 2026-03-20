package me.asikeida.ashore.service.article.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面经主表 Mapper。
 */
@Mapper
public interface ArticleMapper extends BaseMapper<ArticleDO> {
}
