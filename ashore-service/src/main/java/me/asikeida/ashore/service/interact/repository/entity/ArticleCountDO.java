package me.asikeida.ashore.service.interact.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 面经计数 DO：Redis 实时计数的定时回写目标（覆盖写，无自增语义）。
 */
@Getter
@Setter
@TableName("article_count")
public class ArticleCountDO {

    /** 面经 ID（与 article.id 一致，非雪花） */
    @TableId(type = IdType.INPUT)
    private Long articleId;

    private Integer praiseCount;

    private Integer collectCount;

    private Integer commentCount;

    private Integer readCount;

    private LocalDateTime updateTime;
}
