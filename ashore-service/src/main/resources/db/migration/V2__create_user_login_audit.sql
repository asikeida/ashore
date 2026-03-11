-- 登录审计表：成功与失败都记录（失败供风控：账号撞库探测、异常 IP 分析）。
-- account 原样记录（含不存在的账号），user_id 仅在账号真实存在时填充。
-- request_id 关联 MDC 日志链路（同一标识可回溯完整请求上下文）。
CREATE TABLE IF NOT EXISTS `user_login_audit` (
    `id`          BIGINT       NOT NULL COMMENT '主键（应用层雪花 ID）',
    `user_id`     BIGINT       NULL     COMMENT '用户 ID；账号不存在时为空',
    `account`     VARCHAR(64)  NOT NULL COMMENT '尝试登录的账号（原样记录）',
    `success`     TINYINT      NOT NULL COMMENT '1 成功 / 0 失败',
    `fail_reason` VARCHAR(32)  NULL     COMMENT '失败原因：ACCOUNT_NOT_FOUND/PASSWORD_MISMATCH/BANNED',
    `ip`          VARCHAR(64)  NULL     COMMENT '客户端 IP（X-Forwarded-For 首段优先）',
    `user_agent`  VARCHAR(255) NULL     COMMENT 'User-Agent',
    `request_id`  VARCHAR(32)  NULL     COMMENT '请求标识（关联日志链路）',
    `create_time` DATETIME     NULL     COMMENT '创建时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_ula_user_id` ON `user_login_audit` (`user_id`);
CREATE INDEX `idx_ula_account` ON `user_login_audit` (`account`);
