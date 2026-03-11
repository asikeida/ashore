package me.asikeida.ashore.service.user.repository.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录审计 DO：登录成功与失败都记录（失败供风控）。
 */
@Getter
@Setter
@TableName("user_login_audit")
public class UserLoginAuditDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID；账号不存在时为空 */
    private Long userId;

    /** 尝试登录的账号（原样记录） */
    private String account;

    /** 1 成功 / 0 失败 */
    private Boolean success;

    /** 失败原因（LoginAuditService.REASON_* 常量）；成功为空 */
    private String failReason;

    private String ip;

    private String userAgent;

    /** 请求标识，关联 MDC 日志链路 */
    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
