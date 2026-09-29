-- 面经 AI 助手对话记录：面板级轻量会话（与 interview 会话不同：无状态机、无轮数概念）
-- 按 (user_id, article_id) 聚合查询（个人中心"AI 助手记录"tab 的分组键）
CREATE TABLE IF NOT EXISTS `assistant_message` (
    `id`          BIGINT      NOT NULL COMMENT '主键（应用层雪花 ID）',
    `user_id`     BIGINT      NOT NULL COMMENT '用户 ID',
    `article_id`  BIGINT      NOT NULL COMMENT '面经 ID',
    `role`        VARCHAR(20) NOT NULL COMMENT 'user / assistant',
    `content`     TEXT        NOT NULL COMMENT '消息内容（可含 Markdown 图片语法）',
    `create_time` DATETIME    NULL     COMMENT '创建时间（应用层填充）',
    PRIMARY KEY (`id`)
);
CREATE INDEX `idx_am_user_article` ON `assistant_message` (`user_id`, `article_id`);
