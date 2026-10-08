-- 文章摘要：发布/编辑时从正文提取纯文本前 120 字（列表卡片展示，免列表页拖大字段）。
-- 存量回填：取最新版本正文前 120 字（不做 Markdown 清洗——历史仅 1 篇种子文，可接受）。
-- 写法注意：不用 MySQL 的 UPDATE JOIN（H2 不支持），用关联子查询保证 H2/MySQL 双可移植。
ALTER TABLE `article` ADD COLUMN `excerpt` VARCHAR(300) NULL COMMENT '纯文本摘要（发布/编辑时生成）' AFTER `status`;

UPDATE `article`
SET `excerpt` = COALESCE((
    SELECT LEFT(d.`content`, 120) FROM `article_detail` d
    WHERE d.`article_id` = `article`.`id`
      AND d.`version` = (SELECT MAX(v2.`version`) FROM `article_detail` v2 WHERE v2.`article_id` = `article`.`id`)
), '');
