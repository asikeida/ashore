# Ashore 生产应用镜像（薄镜像路线）
# 构建流：前端 pnpm build → 后端 mvn package → scp jar 至服务器 → 服务器 docker build（仅 COPY，秒级）
# 为什么不用多阶段构建：多阶段会在服务器上现场跑 Maven（依赖下载无缓存，4C 机器分钟级）；
# 本地已有成熟构建流，薄镜像只负责"固定 JRE + 运行 jar"，职责单一。
# 运行时参数（-Xms/-Xmx/profile）写在 ENTRYPOINT，环境变量经 compose env_file 注入 ashore.env
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY ashore-web/target/ashore-web-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms512m", "-Xmx1536m", "-jar", "app.jar", "--spring.profiles.active=prod"]
