package me.asikeida.ashore.service.interact.repository.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 面试官会话 DO。
 */
@Getter
@Setter
@TableName("interview_session")
public class InterviewSessionDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long articleId;

    /** 候选人（会话归属人） */
    private Long userId;

    /** 已出题轮数 */
    private Integer questionCount;

    /** ACTIVE / CLOSED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
