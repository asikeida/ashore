# AGENTS.md

本文是 Ashore 仓库内所有 AI Agent 和开发者的首要协作约定。

## 1. 项目定位

Ashore（上岸）是一个面经分享与 AI 模拟面试的技术社区，也是可公开展示的求职作品。每次改动都应当能说明：解决了什么问题、怎样验证、是否可以安全合并。

技术栈：Java 21、Spring Boot 3.5.6、MyBatis-Plus 3.5.18、Maven。数据库迁移使用 Flyway（M1 引入），Redis 与搜索按规划在后续里程碑引入。本地开发模式：不使用 Issue、Pull Request、CI/CD。

> 版本说明：曾试验 Spring Boot 4.1.1，但 MyBatis-Plus 3.5.18 的自动配置在 SB4 下不生效（已实测验证），按既定决策回退 SB 3.5.x 并保留 MyBatis-Plus。详见 `docs/开发规划/01-工程约定与工程迁移方案.md`。

## 2. 开始任务前必读

按顺序阅读：

1. 本文件 `AGENTS.md`
2. `README.md`
3. `docs/README.md`
4. 与当前任务有关的 `docs/开发规划/` 文档
5. `.agents/rules/README.md` 以及与任务类型对应的规则文件
6. `pom.xml`、`src/main/resources/application*.yaml` 和本次直接相关的源码

按任务类型补充阅读：

| 任务 | 必读规则 |
| --- | --- |
| 所有改动 | `01-通用工作规则.md`、`04-Git与提交规范.md` |
| 后端、数据库、异步任务 | `02-后端与数据规范.md` |
| 接口、异常、日志、安全 | `03-接口日志与安全规范.md` |
| Java 命名、注释、公开 API | `05-注释与命名规范.md` |
| 合并自查或编写审核记录 | `06-代码审查规范.md` |
| 接口开发、接口变更与交付 | `07-审核记录与Apifox协作.md` |

## 3. 目录与模块结构

Maven 多模块，依赖方向由构建工具强制（上层想 import 下层以外的类，编译直接报错）：

```text
ashore（父 POM）
├─ ashore-api      # 纯数据模型：VO/枚举/事件/异常/上下文（me.asikeida.ashore.api，零依赖）
├─ ashore-core     # 技术底座：缓存封装、ID、工具（me.asikeida.ashore.core，依赖 api）
├─ ashore-service  # 业务域：MyBatis-Plus 落库、领域事件（me.asikeida.ashore.service.<域>，依赖 core+api）
└─ ashore-web      # 启动与入口层：Controller/配置/异常（唯一可执行模块，依赖 service）
```

- `ashore-web` 的启动类 `AshoreApplication` 位于包根 `me.asikeida.ashore`，组件扫描天然覆盖全部模块。
- 业务域包（`service.user`、`service.article`、`service.comment`、`service.notify`、`service.rank`、`service.search`、`service.chatai`）在到达对应里程碑时才创建，**不预建空包**；域内部按复杂度增加 `service`、`repository/mapper`、`dto`。
- M6 管理后台前端为独立 `frontend/` 目录（Vue 3），不是 Maven 模块。
- 项目文档 `docs/` 为本地工作文档，**不提交 Git**（用户决策 2026-11-03）。
- AI 的行为规则放在 `.agents/rules/` 并提交 Git；根目录 `AGENTS.md` 是跨 Agent 的统一入口。
- AI 草稿、临时排查记录和逐轮审核材料放在 `.local/`，不提交。

## 4. 不可违反的规则

- 不提交密码、令牌、私钥或其他敏感数据。
- 使用环境变量或未追踪的本地文件保存私密配置；示例文件只能使用占位值。
- **未经用户在当前任务明确要求，不得执行任何 Git 写操作（init、commit、merge、push）或改写历史。**
- 不因无关任务重排、格式化或重构大量代码。
- 不创建无用途的公共类、配置类、空包或"以后可能会用"的中间件配置。
- 修改接口、鉴权或数据行为时，必须做与风险匹配的验证。
- 日志不得记录密码、令牌、完整授权头或个人敏感数据。

## 5. 文档规则

`docs/` 为本地工作文档，不提交 Git（含规划与迁移决策类内容）；对外可见的工程信息由 README 与本文件承载。目录约定：

- `docs/开发规划/`：里程碑、任务条目、范围与验收目标（承担任务追踪职责，本项目无 Issue）。
- `docs/架构设计/`：架构、数据模型、模块边界。
- `docs/接口说明/`：接口约定、鉴权和集成说明。
- `docs/部署运维/`：本地环境、启动与故障处理。
- `docs/决策记录/`：重要技术选择及其理由。
- `.agents/rules/`：所有 AI Agent 必须遵守的开发规范，随代码提交。

文档必须描述已经确认的事实或明确标注为规划。原始对话、临时调试输出和未整理草稿留在 `.local/`。

## 6. Git 工作方式

本地短分支模型（详见 `.agents/rules/04-Git与提交规范.md`）：

- `main` 必须始终可构建、测试通过。
- 每个任务从最新 `main` 创建短期分支（`feat/`、`fix/`、`infra/`、`docs/`、`refactor/`、`test/`、`chore/`）。
- 提交表达一个完整、可审核的意图，例如 `infra(db): 添加本地 MySQL 与 Flyway 基线`、`feat(auth): 新增账号密码注册登录`。
- 验证通过后 `merge --no-ff` 回 `main` 并删除分支。
- Git 提交日期反映真实开发时间，不为履历伪造历史。

## 7. 常用验证

根据改动选择最小且有意义的验证，不为了形式重复全量构建。合并回 main 前必须全量测试通过。

**本机注意**：默认 PATH 上的 Java 是 8，本项目需要 JDK 21，构建前必须显式指定：

```bash
export JAVA_HOME=/c/Users/guowe/scoop/apps/temurin21-jdk/current
```

```bash
./mvnw test                          # 全模块测试（MP 装配冒烟测试依赖 H2，无需本地数据库）
./mvnw package                       # 构建可执行 JAR（ashore-web）
./mvnw test -pl ashore-service -am   # 只测 service 模块及其上游
```

新增或修改 API 时，还要验证参数校验、错误响应、权限边界和 OpenAPI 文档。启动验证：`./mvnw spring-boot:run` 后访问 `http://127.0.0.1:8080/api/ping`（统一响应）、`/actuator/health`（应含 db/redis 组件明细）、`/swagger-ui/index.html`（接口文档，`/v3/api-docs` 为 Apifox 同步数据源）。

## 8. 本地审核与接口同步

- 每轮开发完成后，在 `.local/代码审核/` 新建一份中文 Markdown，覆盖本轮所有改动：逐文件的新增、修改、删除、目的、关键方法与调用链、数据库与配置影响、实际验证和限制。
- 接口新增、修改或删除后，通过 Apifox CLI 同步到个人团队的 Ashore 项目；同步前检查差异，完成后回读验证。权限受限必须明确报告，不能宣称完成。
- 执行步骤见 `.agents/rules/07-审核记录与Apifox协作.md`。
