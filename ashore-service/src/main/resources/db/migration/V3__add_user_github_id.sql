-- GitHub OAuth 登录绑定：github_id 唯一索引允许多个 NULL（非 OAuth 用户均为 NULL）。
-- account 采用 "gh" + githubId 规则生成，避免与手工注册账号冲突。
ALTER TABLE `user` ADD COLUMN `github_id` BIGINT NULL COMMENT 'GitHub 用户 ID（OAuth 登录绑定）';

CREATE UNIQUE INDEX `uk_user_github_id` ON `user` (`github_id`);
