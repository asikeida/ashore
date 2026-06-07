#!/usr/bin/env bash
# 京东云（mine-asikeida）HTTPS 入口一键初始化：Nginx + Let's Encrypt IP 证书（acme.sh 签发）
# 前置：安全组已放行 22/80/443/8443；仓库已上传至 /opt/apps/ashore（含 deploy/nginx 与 scripts）
# 产物：80(ACME 验证+301) / 8443(Ashore TLS 站点)；证书 6 天有效期由每小时 cron 自动续期
# 用法：scp 上传后 ssh mine-asikeida 'sudo bash /opt/apps/ashore/scripts/prod-https-setup.sh'
# 注意：服务器 asikeida 登录 shell 为 fish——本脚本必须以 `sudo bash 本脚本` 运行
#
# 为什么是 acme.sh 而不是 certbot（真机结论，勿回退）：
#   certbot 至 5.9.0（PyPI 最新）的 util.enforce_domain_sanity 仍无条件拒绝 IP 标识符；
#   LE 生产目录已 GA IP 证书（2026-01，仅 shortlived profile，约 6 天有效期）；
#   acme.sh（github master）用 --certificate-profile shortlived --days 3 签发与排期。
#   注意：gitee 镜像停在 v3.1.5 无 profile 支持，git clone github 在境内常被掐断——
#   安装走 codeload tarball。
set -euo pipefail

IP=111.228.13.15
ASHORE_DIR=/opt/apps/ashore
CONF_SRC=$ASHORE_DIR/deploy/nginx
WEBROOT=/var/www/acme-webroot
CERT_DIR=/etc/ashore-certs
ACME_HOME=/root/.acme.sh

command -v apt-get >/dev/null || { echo "仅支持 apt 系发行版（Ubuntu/Debian）"; exit 1; }
[ "$(id -u)" -eq 0 ] || { echo "请以 root / sudo 运行"; exit 1; }

echo "==> 0/8 剥离 Windows 换行（scp 上传的工作副本可能带 CRLF，CRLF 会毒化 bash 与 nginx）"
sed -i 's/\r$//' "$CONF_SRC"/*.conf "$ASHORE_DIR"/scripts/*.sh

echo "==> 1/8 安装 nginx；清理 certbot 残留"
apt-get update -qq && apt-get install -y -qq nginx git curl
# apt certbot 2.x 客户端拒绝 IP 证书且 timer 续期必败；venv certbot 5.9 同样拒签——整体退役
apt-get remove -y certbot >/dev/null 2>&1 || true
rm -f /usr/local/bin/certbot /etc/cron.d/ashore-certbot-renew
rm -rf /opt/certbot-venv

echo "==> 2/8 安装 acme.sh（codeload tarball；git clone 在境内常被掐断，gitee 镜像过旧）"
if [ ! -f "$ACME_HOME/acme.sh" ]; then
  rm -rf /tmp/acme-master /tmp/acme.tar.gz
  curl -sL --max-time 120 -o /tmp/acme.tar.gz \
    https://codeload.github.com/acmesh-official/acme.sh/tar.gz/refs/heads/master
  [ -s /tmp/acme.tar.gz ] || { echo "FATAL: acme.sh 下载失败"; exit 1; }
  tar xzf /tmp/acme.tar.gz -C /tmp && mv /tmp/acme.sh-* /tmp/acme-master
  (cd /tmp/acme-master && ./acme.sh --install --home "$ACME_HOME" >/dev/null)
  rm -rf /tmp/acme-master /tmp/acme.tar.gz
fi
"$ACME_HOME/acme.sh" --set-default-ca --server letsencrypt

echo "==> 3/8 准备 ACME webroot 与前端目录"
mkdir -p "$WEBROOT" "$ASHORE_DIR/frontend-dist"

echo "==> 4/8 部署 80 站点（先于证书存在，ACME 验证依赖它）"
cp "$CONF_SRC/ashore-http-80.conf" /etc/nginx/sites-available/
ln -sf /etc/nginx/sites-available/ashore-http-80.conf /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default   # 移除默认站点，避免 80 冲突
nginx -t && systemctl reload nginx

echo "==> 5/8 签发 IP 证书（shortlived profile，约 6 天有效期）"
# LE 政策：IP 证书仅经 shortlived profile 签发，缺 profile 支持的客户端直接被服务端拒。
# --days 3：160 小时证书必须在 3 天时排续期（默认 60 天排期对短效证书等于永不续期）
if [ ! -f "$CERT_DIR/fullchain.pem" ]; then
  if ! "$ACME_HOME/acme.sh" --help 2>&1 | grep -qE "cert(ificate)?-profile"; then
    echo "FATAL: acme.sh 缺少 profile 支持（--certificate-profile），请用 github master 版"; exit 1
  fi
  "$ACME_HOME/acme.sh" --issue --server letsencrypt \
    -d "$IP" -w "$WEBROOT" \
    --certificate-profile shortlived --days 3
fi

echo "==> 6/8 安装证书到固定路径（acme.sh 续期后自动回填并 reload nginx）"
mkdir -p "$CERT_DIR"
"$ACME_HOME/acme.sh" --install-cert --server letsencrypt -d "$IP" \
  --fullchain-file "$CERT_DIR/fullchain.pem" \
  --key-file "$CERT_DIR/privkey.pem" \
  --reloadcmd "systemctl reload nginx"
chmod 600 "$CERT_DIR/privkey.pem"
chmod 644 "$CERT_DIR/fullchain.pem"

echo "==> 7/8 部署 8443 Ashore TLS 站点"
cp "$CONF_SRC/ashore-https-8443.conf" /etc/nginx/sites-available/
ln -sf /etc/nginx/sites-available/ashore-https-8443.conf /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
systemctl enable --now nginx

echo "==> 8/8 自动续期（每小时 cron）与告警"
# acme.sh --install 自带每日 cron；6 天证书需要更密——每小时幂等兜底（未到期为 no-op）
echo "23 * * * * root $ACME_HOME/acme.sh --cron --home $ACME_HOME > /dev/null" \
  > /etc/cron.d/ashore-acme-renew
chmod 644 /etc/cron.d/ashore-acme-renew
# 每日 09:00 自检证书剩余天数：<3 天写 syslog 告警（个人服务器无监控平台，日志兜底）
cat > /etc/cron.d/ashore-cert-check <<EOF
0 9 * * * root $ASHORE_DIR/scripts/cert-expiry-check.sh
EOF
chmod 644 /etc/cron.d/ashore-cert-check

echo ""
echo "续期排期核对（6 天证书应显示约 4 天后续期）："
"$ACME_HOME/acme.sh" --info -d "$IP" | grep -E "Le_NextRenewTime" || true
echo ""
echo "完成。后续手动步骤（切流量）："
echo "  1. scp frontend/dist/* -> $ASHORE_DIR/frontend-dist/"
echo "  2. $ASHORE_DIR/ashore.env 追加 ASHORE_COOKIE_SECURE=true 并重启应用"
echo "  3. 验证：curl -sI https://$IP:8443 | head -1"
