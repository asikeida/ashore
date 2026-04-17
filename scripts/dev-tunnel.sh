#!/usr/bin/env bash
# 云端开发库 SSH 隧道：本地端口 -> 京东云（mine-asikeida）127.0.0.1
#   13306 -> 京东云 3306 (MySQL，/opt/projects-infra/mysql 共享实例的 ashore_dev)
# 用法：./scripts/dev-tunnel.sh （保持前台运行；应用用 --spring.profiles.active=server 连接）
# 依赖：~/.ssh/config 中的 mine-asikeida 主机别名（免密登录）
# 注：京东云暂未部署 Redis，会话/计数功能待共享 Redis 就绪后追加 16379 映射
set -euo pipefail

echo "建立隧道：本地 13306 -> mine-asikeida 127.0.0.1:3306（Ctrl+C 断开）"
exec ssh -N \
  -L 13306:127.0.0.1:3306 \
  mine-asikeida
