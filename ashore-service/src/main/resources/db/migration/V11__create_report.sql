-- 举报：用户对文章/评论的违规反馈，管理员在管理台处理。
-- 处理动作二选一：DISMISSED（驳回=误报/无问题）或 RESOLVED（已处置：文章下线/评论软删）。
-- 目标对象不设外键：评论可能已被作者自删，举报记录仍需保留（审计流水）。
CREATE TABLE IF NOT EXISTS `report` (
    `id`          BIGINT       NOT NULL COMMENT '主键（应用层雪花 ID）',
    `reporter_id` BIGINT       NOT NULL COMMENT '举报人用户 ID',
    `target_type` VARCHAR(20)  NOT NULL COMMENT '目标类型：ARTICLE/COMMENT',
    `target_id`   BIGINT       NOT NULL COMMENT '目标对象 ID',
    `reason`      VARCHAR(500) NOT NULL COMMENT '举报理由',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RESOLVED/DISMISSED',
    `handled_by`  BIGINT       NULL COMMENT '处理管理员用户 ID',
    `handle_time` DATETIME     NULL COMMENT '处理时间',
    `create_time` DATETIME     NULL COMMENT '创建时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_report_status` ON `report` (`status`, `create_time`);
CREATE INDEX `idx_report_reporter` ON `report` (`reporter_id`);
