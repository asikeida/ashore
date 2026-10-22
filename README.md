# Ashore · 上岸

> Every offer begins ashore. —— 面向求职者的技术社区：面经分享 + AI 模拟面试。

## 项目定位

求职者在这里发布面试经历（面经）、按岗位/公司检索面经，并由内置的「AI 面试官」针对面经中的技术点进行出题、追问与点评，形成"分享—练习—复盘"的闭环。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / 框架 | Java 21 · Spring Boot 3.5.6 · MyBatis-Plus 3.5.18 |
| 构建 | Maven 多模块：`ashore-api / core / service / web`，依赖方向由构建强制 |
| Web | Spring MVC（REST）+ Actuator |
| 前端 | 规划中：Vue 3 + Element Plus（管理后台） |
| 存储 | 规划中：MySQL + Redis + ElasticSearch |

## 快速开始

```bash
# 要求 JDK 21（本机默认 Java 为 8，需显式指定）
export JAVA_HOME=/c/Users/guowe/scoop/apps/temurin21-jdk/current

./mvnw spring-boot:run        # 启动，默认 8080 端口
./mvnw test                   # 运行测试
curl http://127.0.0.1:8080/api/ping
```

登录页支持账号密码和共享游客登录。游客使用数据库中持久化的 `guest` 普通用户，所有游客共用消息、收藏和互动记录，请勿在游客身份下填写私人信息。

`POST /api/v1/auth/guest-login` 会为首次访问创建该账号，后续访问复用，并签发与普通登录相同的会话 Cookie。`guest` 账号名不开放注册。

## 路线图

- [x] M0 工程骨架（含 MyBatis-Plus × Spring Boot 版本决策，见 docs/开发规划/01）
- [ ] M1 基础设施（统一响应/异常、MySQL + Flyway、Redis、本地 compose）
- [ ] M2 用户与登录（账密 + JWT 会话 + GitHub OAuth）
- [ ] M3 面经（文章）域：状态机、版本化、游标分页
- [ ] M4 互动链路：评论、足迹、通知事件、活跃度排行
- [ ] M5 搜索（ES + 降级）与 AI 面试官
- [ ] M6 管理后台（Vue 3）

## 起源声明

本项目的架构设计参考并 reimplement 自开源社区项目 [技术派（paicoding）](https://gitcode.com/javabetter/paicoding)（Apache License 2.0），在其基础上按上述定位重新裁剪与实现。改造决策与源码分析文档见原仓库 `docs/` 目录。
