#!/usr/bin/env bash
# 京东云（mine-asikeida）HTTPS 入口一键初始化：Nginx + Let's Encrypt IP 证书
# 前置：安全组已放行 22/80/443/8443；仓库已上传至 /opt/apps/ashore（含 deploy/nginx 与 scripts）
# 产物：80(ACME 验证+301) / 8443(Ashore TLS 站点)；证书 6 天有效期由 certbot.timer 自动续期
# 用法：scp 上传后 ssh mine-asikeida 'sudo bash /opt/apps/ashore/scripts/prod-https-setup.sh'
set -euo pipefail

IP=111.228.13.15
ASHORE_DIR=/opt/apps/ashore
CONF_SRC=$ASHORE_DIR/deploy/nginx
LIVE=/etc/letsencrypt/live/$IP

command -v apt-get >/dev/null || { echo "仅支持 apt 系发行版（Ubuntu/Debian）"; exit 1; }
[ "$(id -u)" -eq 0 ] || { echo "请以 root / sudo 运行"; exit 1; }

echo "==> 1/6 安装 nginx + certbot"
apt-get update -qq && apt-get install -y -qq nginx certbot

echo "==> 2/6 准备 ACME webroot 与前端目录"
mkdir -p /var/www/certbot "$ASHORE_DIR/frontend-dist"

echo "==> 3/6 部署 80 站点（先于证书存在，ACME 验证依赖它）"
cp "$CONF_SRC/ashore-http-80.conf" /etc/nginx/sites-available/
ln -sf /etc/nginx/sites-available/ashore-http-80.conf /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default   # 移除默认站点，避免 80 冲突
nginx -t && systemctl reload nginx

echo "==> 4/6 申请 IP 证书（HTTP-01 / webroot，有效期约 6 天）"
if [ ! -f "$LIVE/fullchain.pem" ]; then
  certbot certonly --webroot -w /var/www/certbot -d "$IP" \
    --non-interactive --agree-tos --register-unsafely-without-email
fi
[ -f "$LIVE/fullchain.pem" ] || { echo "证书未生成——检查上方 certbot 输出（80 端口是否公网可达）"; exit 1; }

echo "==> 5/6 部署 8443 Ashore TLS 站点"
cp "$CONF_SRC/ashore-https-8443.conf" /etc/nginx/sites-available/
ln -sf /etc/nginx/sites-available/ashore-https-8443.conf /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
systemctl enable --now nginx

echo "==> 6/6 自动续期与告警"
systemctl enable --now certbot.timer
systemctl list-timers certbot.timer --no-pager | head -3

# certbot 只负责获取证书，装进 web server 的部署钩子需自配（EFF 文档：IP 证书 install 支持开发中）
mkdir -p /etc/letsencrypt/renewal-hooks/deploy
cat > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh <<'EOF'
#!/bin/sh
systemctl reload nginx
logger -t ashore-cert "IP 证书已续期，nginx 已 reload"
EOF
chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

# 每日 09:00 自检证书剩余天数：<3 天写 syslog 告警（个人服务器无监控平台，日志兜底）
cat > /etc/cron.d/ashore-cert-check <<EOF
0 9 * * * root $ASHORE_DIR/scripts/cert-expiry-check.sh
EOF
chmod 644 /etc/cron.d/ashore-cert-check

echo ""
echo "完成。后续手动步骤（切流量）："
echo "  1. scp frontend/dist/* -> $ASHORE_DIR/frontend-dist/"
echo "  2. $ASHORE_DIR/ashore.env 追加 ASHORE_COOKIE_SECURE=true 并重启应用（旧 jar 陷阱：改了 env 必须 package 后用新 jar）"
echo "  3. 验证：curl -sI https://$IP:8443 | head -1"
