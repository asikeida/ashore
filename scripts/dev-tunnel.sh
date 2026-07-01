#!/usr/bin/env bash
# 云端开发库 SSH 隧道：本地端口 -> 京东云（mine-asikeida）127.0.0.1
#   13306 -> 3306 (MySQL，/opt/projects-infra/mysql 共享实例)
#   16379 -> 6379 (Redis，/opt/projects-infra/redis，有密码)
#    9200 -> 9200 (Elasticsearch，/opt/projects-infra/elasticsearch)
# 用法：./scripts/dev-tunnel.sh （保持前台运行；应用用 --spring.profiles.active=server 连接）
# 依赖：~/.ssh/config 中的 mine-asikeida 主机别名（免密登录）
#
# 自动重连：空闲连接会被中间设备 RST（2026-11-06/07 实测多次，keepalive 也拦不住），
# 外层循环断开后 5s 退避重连；Ctrl+C 一次性退出整个循环。
set -uo pipefail

PORTS=(13306 16379 9200)

while true; do
  echo "建立隧道：本地 13306/16379/9200 -> mine-asikeida（Ctrl+C 断开）"
  # keepalive：30s 心跳；转发端口被占则直接失败而非静默半可用
  ssh -N \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -o ExitOnForwardFailure=yes \
    -L 13306:127.0.0.1:3306 \
    -L 16379:127.0.0.1:6379 \
    -L 9200:127.0.0.1:9200 \
    mine-asikeida
  code=$?
  # Ctrl+C（SIGINT → 130）/ 正常退出不做重连
  if [ "$code" -eq 0 ] || [ "$code" -eq 130 ]; then
    echo "隧道已退出（code=$code）"
    exit "$code"
  fi
  echo "隧道意外断开（code=$code），5s 后自动重连…"
  sleep 5
done
