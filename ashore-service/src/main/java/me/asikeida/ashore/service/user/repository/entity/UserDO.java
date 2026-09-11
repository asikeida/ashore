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
 * 用户表 DO：登录凭证与基础展示信息。
 * <p>主键为应用层雪花 ID（{@link IdType#ASSIGN_ID}）；时间字段由
 * {@code AuditMetaObjectHandler} 在插入/更新时填充，不依赖数据库默认值（保证迁移脚本可移植）。</p>
 * <p>刻意不用 {@code @Data}：避免自动生成的 toString 将 passwordHash 写进日志。</p>
 */
@Getter
@Setter
@TableName("`user`")
public class UserDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色常量：与 role 列取值对应（取值稳定后可升级为枚举） */
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    /** 状态常量：与 status 列取值对应 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_BANNED = "BANNED";

    /** 登录账号：用户名或邮箱，全局唯一 */
    private String account;

    /** BCrypt 哈希；OAuth 用户为空 */
    private String passwordHash;

    private String nickname;

    /** 头像 URL（/images/...）；NULL=未设置 */
    private String avatar;

    /** 角色：USER / ADMIN */
    private String role;

    /** 状态：ACTIVE / BANNED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
