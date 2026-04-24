-- AI 面试官会话两表：会话 + 消息（面试官 INTERVIEWER / 用户 USER）。
CREATE TABLE IF NOT EXISTS `interview_session` (
    `id`             BIGINT      NOT NULL COMMENT '主键（应用层雪花 ID）',
    `article_id`     BIGINT      NOT NULL COMMENT '面经 ID',
    `user_id`        BIGINT      NOT NULL COMMENT '候选人（会话归属人）',
    `question_count` INT         NOT NULL DEFAULT 0 COMMENT '已出题轮数',
    `status`         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/CLOSED',
    `create_time`    DATETIME    NULL     COMMENT '创建时间（应用层填充）',
    `update_time`    DATETIME    NULL     COMMENT '更新时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_ivs_user` ON `interview_session` (`user_id`);

CREATE TABLE IF NOT EXISTS `interview_message` (
    `id`          BIGINT        NOT NULL COMMENT '主键（应用层雪花 ID）',
    `session_id`  BIGINT        NOT NULL COMMENT '会话 ID',
    `role`        VARCHAR(20)   NOT NULL COMMENT '角色：INTERVIEWER/USER',
    `content`     TEXT          NOT NULL COMMENT '消息内容',
    `create_time` DATETIME      NULL     COMMENT '创建时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_ivm_session` ON `interview_message` (`session_id`);
