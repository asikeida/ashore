#!/usr/bin/env bash
# 云端开发库 SSH 隧道：本地端口 -> 4-yuchen 服务器 127.0.0.1
#   13306 -> 服务器 3306 (MySQL)
#   16379 -> 服务器 6379 (Redis)
# 用法：./scripts/dev-tunnel.sh （保持前台运行；应用用 --spring.profiles.active=server 连接）
# 依赖：~/.ssh/config 中的 4-yuchen 主机别名（免密登录）
set -euo pipefail

echo "建立隧道：本地 13306/16379 -> 4-yuchen 127.0.0.1:3306/6379（Ctrl+C 断开）"
exec ssh -N \
  -L 13306:127.0.0.1:3306 \
  -L 16379:127.0.0.1:6379 \
  4-yuchen
