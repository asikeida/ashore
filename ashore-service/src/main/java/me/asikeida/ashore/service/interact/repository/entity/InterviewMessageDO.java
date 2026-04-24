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
 * AI 面试官消息 DO：INTERVIEWER（AI）/ USER（候选人）。
 */
@Getter
@Setter
@TableName("interview_message")
public class InterviewMessageDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    /** INTERVIEWER / USER */
    private String role;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
