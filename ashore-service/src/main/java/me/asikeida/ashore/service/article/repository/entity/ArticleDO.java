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
 * 面经主表 DO：元信息与状态；正文在 {@link ArticleDetailDO}（版本化多行）。
 */
@Getter
@Setter
@TableName("`article`")
public class ArticleDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private Long authorId;

    /** 状态列值 = {@link me.asikeida.ashore.api.enums.ArticleStatus} name() */
    private String status;

    /** 纯文本摘要（发布/编辑时生成，列表卡片展示）；NULL = 旧数据未生成 */
    private String excerpt;

    /** 置顶：1 是 / 0 否（列表排序第一键） */
    private Integer toppingStat;

    /** 软删除：1 已删 */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
