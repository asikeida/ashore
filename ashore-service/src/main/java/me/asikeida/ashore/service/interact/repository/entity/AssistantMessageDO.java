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
 * 面经 AI 助手消息：按 (user_id, article_id) 聚合，role = user / assistant。
 * 与 interview 消息的区别：无状态机、无轮数，面板级轻量会话。
 */
@Getter
@Setter
@TableName("assistant_message")
public class AssistantMessageDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long articleId;

    /** user / assistant */
    private String role;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
