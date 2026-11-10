# Ashore 开发约定

Ashore 是一个面经分享与 AI 辅助复盘社区。改动应聚焦明确的问题，并说明验证结果和已知限制。

## 结构

- 后端使用 Java 21、Spring Boot 3.5.6、MyBatis-Plus 3.5.18 和 Maven。
- 依赖方向为 `ashore-api` → `ashore-core` → `ashore-service` → `ashore-web`；只有 `ashore-web` 启动应用。
- Vue 3 前端位于 `frontend/`；数据库结构由 Flyway 迁移维护。

## 开发与安全

- 修改前阅读相关代码、配置和测试；避免无关格式化或大范围重构。
- 不提交密码、令牌、私钥、真实用户数据或本机环境文件。敏感配置通过环境变量提供，示例文件只使用占位值。
- 不在日志中记录密码、令牌、完整授权头或个人敏感信息。
- 修改 API、鉴权或数据行为时，验证成功路径、参数错误和权限边界；数据库迁移不得修改已应用的版本。
- 文档只描述已实现的行为；未来能力明确标为规划。

## 验证与提交

- 后端使用 JDK 21 运行 `./mvnw test`；前端运行 `pnpm typecheck` 和 `pnpm build`。
- 合并到 `main` 前确保相关验证通过。提交信息使用 Conventional Commits，例如 `fix(article): 修复文章展示`。
- 提交日期记录实际提交时间。未经当前任务的明确授权，不执行 Git 提交、合并、推送或历史改写。
