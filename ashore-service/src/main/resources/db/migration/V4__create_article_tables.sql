-- 面经域四表（对照 paicoding 去 category：面经用标签足够）。
-- 可移植 SQL：TEXT/TINYINT/BOOLEAN 均为 H2 与 MySQL 共同子集；
-- deleted 软删除列（待办 T11 的设计落地）。

-- 面经主表：元信息与状态；正文在 article_detail（版本化多行）
CREATE TABLE IF NOT EXISTS `article` (
    `id`           BIGINT       NOT NULL COMMENT '主键（应用层雪花 ID）',
    `title`        VARCHAR(128) NOT NULL COMMENT '标题',
    `author_id`    BIGINT       NOT NULL COMMENT '作者用户 ID',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'REVIEW' COMMENT '状态：OFFLINE/REVIEW/ONLINE',
    `topping_stat` TINYINT      NOT NULL DEFAULT 0 COMMENT '置顶：1 是 / 0 否（列表排序第一键）',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除：1 已删',
    `create_time`  DATETIME     NULL     COMMENT '创建时间（应用层填充）',
    `update_time`  DATETIME     NULL     COMMENT '更新时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_article_author` ON `article` (`author_id`);
-- 列表游标分页的排序键组合（topping_stat, create_time, id）
CREATE INDEX `idx_article_page` ON `article` (`topping_stat`, `create_time`, `id`);

-- 正文版本表：每次审核期编辑插入新行（version 递增），保留完整编辑历史
CREATE TABLE IF NOT EXISTS `article_detail` (
    `id`          BIGINT   NOT NULL COMMENT '主键（应用层雪花 ID）',
    `article_id`  BIGINT   NOT NULL COMMENT '面经 ID',
    `content`     TEXT     NOT NULL COMMENT '正文（Markdown）',
    `version`     INT      NOT NULL DEFAULT 1 COMMENT '版本号，同一面经内递增',
    `create_time` DATETIME NULL     COMMENT '创建时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX `uk_detail_article_version` ON `article_detail` (`article_id`, `version`);

-- 标签
CREATE TABLE IF NOT EXISTS `tag` (
    `id`          BIGINT      NOT NULL COMMENT '主键（应用层雪花 ID）',
    `name`        VARCHAR(32) NOT NULL COMMENT '标签名（唯一）',
    `create_time` DATETIME    NULL     COMMENT '创建时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX `uk_tag_name` ON `tag` (`name`);

-- 面经-标签关联
CREATE TABLE IF NOT EXISTS `article_tag` (
    `id`          BIGINT   NOT NULL COMMENT '主键（应用层雪花 ID）',
    `article_id`  BIGINT   NOT NULL COMMENT '面经 ID',
    `tag_id`      BIGINT   NOT NULL COMMENT '标签 ID',
    `create_time` DATETIME NULL     COMMENT '创建时间（应用层填充）',
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX `uk_at_article_tag` ON `article_tag` (`article_id`, `tag_id`);
CREATE INDEX `idx_at_tag` ON `article_tag` (`tag_id`);
