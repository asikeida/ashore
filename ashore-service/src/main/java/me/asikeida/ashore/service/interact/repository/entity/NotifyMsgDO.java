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
 * 通知消息 DO：由 NotifyMsgListener 异步落库。
 */
@Getter
@Setter
@TableName("notify_msg")
public class NotifyMsgDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收人 */
    private Long userId;

    /** 触发者 */
    private Long operatorId;

    /** 类型列值 = {@link me.asikeida.ashore.api.enums.NotifyTypeEnum} name() */
    private String type;

    private Long articleId;

    private Long commentId;

    /** 摘要内容 */
    private String content;

    /** 0 未读 / 1 已读 */
    private Integer readStat;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
