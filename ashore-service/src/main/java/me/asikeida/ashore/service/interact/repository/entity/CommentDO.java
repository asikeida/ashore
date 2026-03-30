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
 * 评论 DO：顶级/父级双 ID 两级折叠模型。
 * <p>顶级评论 top_comment_id=0；子回复 top_comment_id=所属顶级、parent_comment_id=直接父级。</p>
 */
@Getter
@Setter
@TableName("`comment`")
public class CommentDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long articleId;

    private Long userId;

    private String content;

    /** 顶级评论 ID（自身为顶级时 0） */
    private Long topCommentId;

    /** 直接父评论 ID（顶级时 0） */
    private Long parentCommentId;

    /** 被回复人（子回复时有值） */
    private Long replyUserId;

    /** 软删除：1 已删 */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
