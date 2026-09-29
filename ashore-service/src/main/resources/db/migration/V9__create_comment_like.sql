-- 评论点赞：唯一索引 (comment_id, user_id) 兜底并发防重（与 user_foot 同款防线）。
-- 取消点赞 = 物理删除行（点赞是轻量动作，无历史保留价值）
CREATE TABLE IF NOT EXISTS `comment_like` (
    `id`          BIGINT   NOT NULL COMMENT '主键（应用层雪花 ID）',
    `comment_id`  BIGINT   NOT NULL COMMENT '被点赞的评论 ID',
    `user_id`     BIGINT   NOT NULL COMMENT '点赞用户 ID',
    `create_time` DATETIME NULL     COMMENT '点赞时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX `uk_cl_comment_user` ON `comment_like` (`comment_id`, `user_id`);
CREATE INDEX `idx_cl_comment` ON `comment_like` (`comment_id`);
