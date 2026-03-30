-- 互动链路四表：评论（两级折叠）、统一足迹（点赞/收藏/评论/阅读）、通知、面经计数。
-- 可移植 SQL；足迹唯一索引是并发幂等的最终防线（应用层另有分布式锁，见 M4-2）。

-- 评论：顶级/父级双 ID 两级折叠模型
CREATE TABLE IF NOT EXISTS `comment` (
    `id`                 BIGINT        NOT NULL COMMENT '主键（应用层雪花 ID）',
    `article_id`         BIGINT        NOT NULL COMMENT '面经 ID',
    `user_id`            BIGINT        NOT NULL COMMENT '评论人',
    `content`            VARCHAR(2000) NOT NULL COMMENT '内容',
    `top_comment_id`     BIGINT        NOT NULL DEFAULT 0 COMMENT '顶级评论 ID（自身为顶级时 0）',
    `parent_comment_id`  BIGINT        NOT NULL DEFAULT 0 COMMENT '直接父评论 ID（顶级时 0）',
    `reply_user_id`      BIGINT        NULL     COMMENT '被回复人（子回复时有值）',
    `deleted`            TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除：1 已删',
    `create_time`        DATETIME      NULL     COMMENT '创建时间（应用层填充）',
    `update_time`        DATETIME      NULL     COMMENT '更新时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_comment_article` ON `comment` (`article_id`);
CREATE INDEX `idx_comment_top` ON `comment` (`top_comment_id`);

-- 统一足迹：点赞/收藏/评论/阅读四类行为一行承载，state 表达取消
CREATE TABLE IF NOT EXISTS `user_foot` (
    `id`          BIGINT      NOT NULL COMMENT '主键（应用层雪花 ID）',
    `user_id`     BIGINT      NOT NULL COMMENT '操作人',
    `article_id`  BIGINT      NOT NULL COMMENT '面经 ID',
    `type`        VARCHAR(20) NOT NULL COMMENT '类型：PRAISE/COLLECT/COMMENT/READ',
    `state`       TINYINT     NOT NULL DEFAULT 1 COMMENT '1 有效 / 0 已取消',
    `create_time` DATETIME    NULL     COMMENT '首次时间（应用层填充）',
    `update_time` DATETIME    NULL     COMMENT '最近更新（应用层填充）',
    PRIMARY KEY (`id`)
);

-- 幂等最终防线：一人一面经一类行为仅一行
CREATE UNIQUE INDEX `uk_foot_user_article_type` ON `user_foot` (`user_id`, `article_id`, `type`);
CREATE INDEX `idx_foot_user_type` ON `user_foot` (`user_id`, `type`);

-- 通知
CREATE TABLE IF NOT EXISTS `notify_msg` (
    `id`           BIGINT       NOT NULL COMMENT '主键（应用层雪花 ID）',
    `user_id`      BIGINT       NOT NULL COMMENT '接收人',
    `operator_id`  BIGINT       NOT NULL COMMENT '触发者',
    `type`         VARCHAR(20)  NOT NULL COMMENT '类型：COMMENT/REPLY/PRAISE/COLLECT/SYSTEM',
    `article_id`   BIGINT       NULL     COMMENT '关联面经',
    `comment_id`   BIGINT       NULL     COMMENT '关联评论',
    `content`      VARCHAR(500) NULL     COMMENT '摘要内容',
    `read_stat`    TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未读 / 1 已读',
    `create_time`  DATETIME     NULL     COMMENT '创建时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_notify_user` ON `notify_msg` (`user_id`, `read_stat`);

-- 面经计数：Redis 实时计数的定时回写目标（覆盖写）
CREATE TABLE IF NOT EXISTS `article_count` (
    `article_id`    BIGINT   NOT NULL COMMENT '面经 ID',
    `praise_count`  INT      NOT NULL DEFAULT 0 COMMENT '点赞数',
    `collect_count` INT      NOT NULL DEFAULT 0 COMMENT '收藏数',
    `comment_count` INT      NOT NULL DEFAULT 0 COMMENT '评论数',
    `read_count`    INT      NOT NULL DEFAULT 0 COMMENT '阅读数',
    `update_time`   DATETIME NULL     COMMENT '回写时间',
    PRIMARY KEY (`article_id`)
);
