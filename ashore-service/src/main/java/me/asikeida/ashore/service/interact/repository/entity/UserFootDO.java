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
 * 统一足迹 DO：点赞/收藏/评论/阅读一行承载，state 表达取消。
 * <p>并发幂等：应用层 Redisson 锁（M4-2）+ {@code uk_foot_user_article_type} 唯一索引最终防线。</p>
 */
@Getter
@Setter
@TableName("user_foot")
public class UserFootDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long articleId;

    /** 类型列值 = {@link me.asikeida.ashore.api.enums.FootTypeEnum} name() */
    private String type;

    /** 1 有效 / 0 已取消 */
    private Integer state;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
