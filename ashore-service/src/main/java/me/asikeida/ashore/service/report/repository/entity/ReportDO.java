package me.asikeida.ashore.service.report.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 举报记录：状态机 PENDING → RESOLVED（已处置）/ DISMISSED（驳回） */
@Getter
@Setter
@TableName("report")
public class ReportDO {

    public static final String TYPE_ARTICLE = "ARTICLE";
    public static final String TYPE_COMMENT = "COMMENT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_DISMISSED = "DISMISSED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long reporterId;

    private String targetType;

    private Long targetId;

    private String reason;

    private String status;

    private Long handledBy;

    private LocalDateTime handleTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;
}
