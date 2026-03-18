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
 * 标签 DO。
 */
@Getter
@Setter
@TableName("`tag`")
public class TagDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
