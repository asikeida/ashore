#!/usr/bin/env bash
# IP 证书剩余有效期自检（certbot 自动续期失败的兜底告警）：
# 剩余 <3 天 => 写 syslog(user.warning) 并 exit 1；cron.d/ashore-cert-check 每日 09:00 调用
set -euo pipefail

CERT=/etc/letsencrypt/live/111.228.13.15/fullchain.pem
[ -f "$CERT" ] || { logger -t ashore-cert -p user.warning "证书文件缺失：$CERT"; exit 1; }

END=$(openssl x509 -enddate -noout -in "$CERT" | cut -d= -f2)
DAYS=$(( ($(date -d "$END" +%s) - $(date +%s)) / 86400 ))

if [ "$DAYS" -lt 3 ]; then
  logger -t ashore-cert -p user.warning "IP 证书剩余 ${DAYS} 天，自动续期疑似失败，需人工介入"
  echo "WARN: 证书剩余 ${DAYS} 天" >&2
  exit 1
fi
echo "OK: 证书剩余 ${DAYS} 天"
