#!/usr/bin/env bash
# 京东云（mine-asikeida）HTTPS 入口一键初始化：Nginx + Let's Encrypt IP 证书
# 前置：安全组已放行 22/80/443/8443；仓库已上传至 /opt/apps/ashore（含 deploy/nginx 与 scripts）
# 产物：80(ACME 验证+301) / 8443(Ashore TLS 站点)；证书 6 天有效期由 cron 每 12h 自动续期
# 用法：scp 上传后 ssh mine-asikeida 'sudo bash /opt/apps/ashore/scripts/prod-https-setup.sh'
# 注意：服务器 asikeida 登录 shell 为 fish——本脚本必须以 `sudo bash 本脚本` 运行
set -euo pipefail

IP=111.228.13.15
ASHORE_DIR=/opt/apps/ashore
CONF_SRC=$ASHORE_DIR/deploy/nginx
LIVE=/etc/letsencrypt/live/$IP

command -v apt-get >/dev/null || { echo "仅支持 apt 系发行版（Ubuntu/Debian）"; exit 1; }
[ "$(id -u)" -eq 0 ] || { echo "请以 root / sudo 运行"; exit 1; }

echo "==> 0/7 剥离 Windows 换行（scp 上传的工作副本可能带 CRLF，CRLF 会毒化 bash 与 nginx）"
sed -i 's/\r$//' "$CONF_SRC"/*.conf "$ASHORE_DIR"/scripts/*.sh

echo "==> 1/7 安装 nginx + python3-venv；移除 apt 版 certbot"
apt-get update -qq && apt-get install -y -qq nginx python3-venv
# apt certbot（2.x）客户端拒绝 IP 证书，且自带 certbot.timer 会用老版本续期——IP 证书续期必败，必须移除
apt-get remove -y certbot >/dev/null 2>&1 || true

echo "==> 2/7 venv 安装 certbot（需 >=5.4 才支持 IP 证书；pip 走清华镜像）"
if ! command -v certbot >/dev/null \
   || [ "$(certbot --version 2>/dev/null | awk '{print $2}' | cut -d. -f1)" -lt 5 ]; then
  python3 -m venv /opt/certbot-venv
  /opt/certbot-venv/bin/pip install -q --upgrade pip
  /opt/certbot-venv/bin/pip install -q --upgrade -i https://pypi.tuna.tsinghua.edu.cn/simple certbot
  ln -sf /opt/certbot-venv/bin/certbot /usr/local/bin/certbot
fi
certbot --version

echo "==> 3/7 准备 ACME webroot 与前端目录"
mkdir -p /var/www/certbot "$ASHORE_DIR/frontend-dist"

echo "==> 4/7 部署 80 站点（先于证书存在，ACME 验证依赖它）"
cp "$CONF_SRC/ashore-http-80.conf" /etc/nginx/sites-available/
ln -sf /etc/nginx/sites-available/ashore-http-80.conf /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default   # 移除默认站点，避免 80 冲突
nginx -t && systemctl reload nginx

echo "==> 5/7 申请 IP 证书（HTTP-01 / webroot，有效期约 6 天）"
if [ ! -f "$LIVE/fullchain.pem" ]; then
  certbot certonly --webroot -w /var/www/certbot -d "$IP" \
    --non-interactive --agree-tos --register-unsafely-without-email
fi
[ -f "$LIVE/fullchain.pem" ] || { echo "证书未生成——检查上方 certbot 输出（80 端口是否公网可达）"; exit 1; }

echo "==> 6/7 部署 8443 Ashore TLS 站点"
cp "$CONF_SRC/ashore-https-8443.conf" /etc/nginx/sites-available/
ln -sf /etc/nginx/sites-available/ashore-https-8443.conf /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
systemctl enable --now nginx

echo "==> 7/7 自动续期（cron）与告警"
# venv certbot 不带 systemd timer：cron 每 12h 续期检查（certbot renew 幂等，未到期为 no-op）
echo '17 3,15 * * * root /usr/local/bin/certbot renew --quiet' > /etc/cron.d/ashore-certbot-renew
chmod 644 /etc/cron.d/ashore-certbot-renew

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
echo "  2. $ASHORE_DIR/ashore.env 追加 ASHORE_COOKIE_SECURE=true 并重启应用"
echo "  3. 验证：curl -sI https://$IP:8443 | head -1"
