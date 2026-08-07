-- GitHub OAuth 登录下线（2026-11-09 决策：应用部署在国内，GitHub 的浏览器跳转段
-- 与服务器换 token 段对无代理用户均不可达，功能形同虚设；见 docs/决策记录/03）。
-- 回滚安全性：线上 github_id 全为 NULL（0 个 OAuth 绑定用户），删除列无数据损失；
-- V3 的建列语句保留在迁移历史中，列结构变化以 V7 为准。
-- 只删列不显式 DROP INDEX：MySQL 删列时自动级联删除仅含该列的索引（uk_user_github_id
-- 只含 github_id，随之消失）；且 H2 的 MySQL 兼容模式不支持 ALTER TABLE ... DROP INDEX
-- 专有语法（测试库 H2 实测 42122）——删列语句本身则两个库通用。
ALTER TABLE `user` DROP COLUMN `github_id`;
