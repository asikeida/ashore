#!/usr/bin/env bash
# 云端开发库 SSH 隧道：本地端口转发到远端回环地址
#   13306 -> 3306 (MySQL)
#   16379 -> 6379 (Redis)
#    9200 -> 9200 (Elasticsearch)
# 用法：ASHORE_DEV_SSH_HOST=<SSH 主机或别名> ./scripts/dev-tunnel.sh
#
# 自动重连：空闲连接会被中间设备 RST（2026-11-06/07 实测多次，keepalive 也拦不住），
# 外层循环断开后 5s 退避重连；Ctrl+C 一次性退出整个循环。
set -uo pipefail
SSH_HOST=${ASHORE_DEV_SSH_HOST:?请设置 ASHORE_DEV_SSH_HOST 为 SSH 主机或别名}

PORTS=(13306 16379 9200)

while true; do
  echo "建立隧道：本地 13306/16379/9200 -> $SSH_HOST（Ctrl+C 断开）"
  # keepalive：30s 心跳；转发端口被占则直接失败而非静默半可用
  ssh -N \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -o ExitOnForwardFailure=yes \
    -L 13306:127.0.0.1:3306 \
    -L 16379:127.0.0.1:6379 \
    -L 9200:127.0.0.1:9200 \
    "$SSH_HOST"
  code=$?
  # Ctrl+C（SIGINT → 130）/ 正常退出不做重连
  if [ "$code" -eq 0 ] || [ "$code" -eq 130 ]; then
    echo "隧道已退出（code=$code）"
    exit "$code"
  fi
  echo "隧道意外断开（code=$code），5s 后自动重连…"
  sleep 5
done
