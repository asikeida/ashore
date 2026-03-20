package me.asikeida.ashore.service.article.repository.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 面经正文版本 DO：同一面经每次审核期编辑插入新行（version 递增），保留完整编辑历史。
 */
@Getter
@Setter
@TableName("article_detail")
public class ArticleDetailDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long articleId;

    /** 正文（Markdown） */
    private String content;

    /** 版本号，同一面经内从 1 递增 */
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
