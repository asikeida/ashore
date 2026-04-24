#!/usr/bin/env bash
# 云端开发库 SSH 隧道：本地端口 -> 京东云（mine-asikeida）127.0.0.1
#   13306 -> 3306 (MySQL，/opt/projects-infra/mysql 共享实例)
#   16379 -> 6379 (Redis，/opt/projects-infra/redis，有密码)
#    9200 -> 9200 (Elasticsearch，/opt/projects-infra/elasticsearch)
# 用法：./scripts/dev-tunnel.sh （保持前台运行；应用用 --spring.profiles.active=server 连接）
# 依赖：~/.ssh/config 中的 mine-asikeida 主机别名（免密登录）
set -euo pipefail

echo "建立隧道：本地 13306/16379/9200 -> mine-asikeida（Ctrl+C 断开）"
exec ssh -N \
  -L 13306:127.0.0.1:3306 \
  -L 16379:127.0.0.1:6379 \
  -L 9200:127.0.0.1:9200 \
  mine-asikeida
