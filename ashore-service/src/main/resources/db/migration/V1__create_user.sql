-- 用户表：登录凭证与基础展示信息（对照 paicoding 的 user + user_info 双表，
-- 早期场景合并为单表，字段膨胀时再拆；决策见 docs/开发规划/04-M2用户与登录实施计划.md）
-- 可移植 SQL：不使用 ENGINE/ON UPDATE 等 MySQL 专属子句，保证测试期可在 H2(MySQL 模式) 上执行
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL COMMENT '主键（应用层雪花 ID）',
    `account`       VARCHAR(64)  NOT NULL COMMENT '登录账号：用户名或邮箱，全局唯一',
    `password_hash` VARCHAR(100) NULL     COMMENT '密码哈希（BCrypt）；OAuth 用户为空',
    `nickname`      VARCHAR(64)  NULL     COMMENT '昵称',
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/BANNED',
    `create_time`   DATETIME     NULL     COMMENT '创建时间（应用层填充）',
    `update_time`   DATETIME     NULL     COMMENT '更新时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX `uk_user_account` ON `user` (`account`);
