#!/bin/bash
# Zouter 香港 155.117.*.* Emby 反代一键脚本（含 Cloudflare 一键配置）
# 一条命令做完：CF 橙云 DNS + SSL 严格 + 关 Rocket Loader + 缓存绕过
#               + nginx 反代 + Let's Encrypt 证书 + 验证
#               + 节点：Xray VLESS+WS 走 CF、与 Emby 共用 443（子域名 node-hk），
#                 跑完打印节点订阅（Clash YAML，供梦盒一键导入）和 vless 链接
# 用法：bash emby-hk-cf-oneclick.sh
# 节点说明：CF 只代理 80/443，直连回国又烂，所以节点只能走 VLESS+WS 经 CF；
#   电话 -> CF 边缘 -> 香港机 nginx 443（SNI 分流：emby-hk 去墨云阁，
#   node-hk 的 /vless 去本机 Xray）。UUID 与订阅令牌首次生成后落盘保存，
#   重跑脚本不会换，梦盒里已导入的订阅不用重导。
# 前提：本机 /root/.cf.ini 已放好 Cloudflare API token（600 权限），格式：
#   dns_cloudflare_api_token = <token>
#   注意：以前那枚 token 只有“区域 DNS 编辑”权限，只能做 DNS 和证书，
#   改 SSL / Rocket Loader / 缓存规则会报 403，脚本会跳过并在最后列出手动项。
#   想全程一键：在 CF 后台新建一枚 token，权限给
#   Zone:DNS Edit + Zone:Zone Settings Edit + Zone:Cache Rules Edit，
#   区域只选 suanran9527.com，替换 /root/.cf.ini 里的 token 再跑。
#   token 只存在本机文件里，脚本不会打印它。
set -e
# 不开 set -x，避免 token 进日志

DOMAIN="emby-hk.suanran9527.com"
NODE_DOMAIN="node-hk.suanran9527.com"
WS_PATH="/vless"
XRAY_PORT=10086
ZONE="suanran9527.com"
ORIGIN_HOST="p3mc4.mobaiemby.site"
ACME_EMAIL="iloveyou@suanran9527.com"
CF_INI="/root/.cf.ini"
CF_API="https://api.cloudflare.com/client/v4"
BROWSER_UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
MANUAL=""

need() { command -v "$1" >/dev/null 2>&1 || { echo "缺少命令 $1，请先安装。"; exit 1; }; }
need curl; need python3

if [ ! -f "$CF_INI" ]; then
  echo "找不到 $CF_INI，请先把 Cloudflare API token 放进去（600 权限）再跑。"
  echo "格式一行：dns_cloudflare_api_token = <token>"
  exit 1
fi
CF_TOKEN=$(grep -E '^\s*dns_cloudflare_api_token\s*=' "$CF_INI" | head -1 | sed 's/^[^=]*=\s*//;s/\s*$//')
if [ -z "$CF_TOKEN" ]; then
  echo "$CF_INI 里没有 dns_cloudflare_api_token，请先补上再跑。"
  exit 1
fi
export CF_TOKEN

cf_get()  { curl -s -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" "$CF_API$1"; }
cf_post() { curl -s -X POST -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" -d "$2" "$CF_API$1"; }
cf_put()  { curl -s -X PUT -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" -d "$2" "$CF_API$1"; }
cf_patch(){ curl -s -X PATCH -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" -d "$2" "$CF_API$1"; }
cf_ok() { echo "$1" | python3 -c 'import sys,json; sys.exit(0 if json.load(sys.stdin).get("success") else 1)' 2>/dev/null; }

# CF DNS A 记录 upsert：$1=子域名全名，橙云指向本机 IP（已存在就更新）
cf_dns_upsert() {
  local name="$1" rec_json rec_id rec_body resp
  rec_json=$(cf_get "/zones/$ZONE_ID/dns_records?type=A&name=$name&per_page=1")
  rec_id=$(echo "$rec_json" | python3 -c 'import sys,json; r=json.load(sys.stdin).get("result") or []; print(r[0]["id"] if r else "")' 2>/dev/null)
  rec_body=$(python3 -c 'import json,sys; print(json.dumps({"type":"A","name":sys.argv[1],"content":sys.argv[2],"proxied":True,"ttl":1}))' "$name" "$VPS_IP")
  if [ -n "$rec_id" ]; then
    resp=$(cf_put "/zones/$ZONE_ID/dns_records/$rec_id" "$rec_body")
    cf_ok "$resp" && echo "DNS 记录 $name 已更新为橙云指向本机。" || { echo "DNS 更新失败（$name）：$resp"; exit 1; }
  else
    resp=$(cf_post "/zones/$ZONE_ID/dns_records" "$rec_body")
    cf_ok "$resp" && echo "DNS 记录 $name 已新建（橙云）。" || { echo "DNS 新建失败（$name）：$resp"; exit 1; }
  fi
}

echo ">>> [1/8] 本机 IP 与 token 校验"
VPS_IP=$(curl -sS -m 10 https://api.ipify.org || curl -sS -m 10 https://ifconfig.me)
echo "本机公网 IP: $VPS_IP"
if ! cf_ok "$(cf_get /user/tokens/verify)"; then
  echo "token 校验失败（/user/tokens/verify 没通过），请检查 /root/.cf.ini 里的 token 是否有效。"
  exit 1
fi
echo "token 有效。"

echo ">>> [2/8] 取 Zone ID（$ZONE）"
ZONE_ID=$(cf_get "/zones?name=$ZONE&per_page=1" | python3 -c 'import sys,json; r=json.load(sys.stdin).get("result") or []; print(r[0]["id"] if r else "")' 2>/dev/null)
if [ -z "$ZONE_ID" ]; then echo "取不到 Zone ID，请确认 token 有该区域权限。"; exit 1; fi
echo "Zone ID: $ZONE_ID"

echo ">>> [3/9] CF DNS：$DOMAIN 与 $NODE_DOMAIN 的 A 记录 -> $VPS_IP，橙云（proxied=true）"
cf_dns_upsert "$DOMAIN"
cf_dns_upsert "$NODE_DOMAIN"

echo ">>> [4/9] CF 设置：SSL 严格 + 强制 HTTPS + 关 Rocket Loader"
if cf_ok "$(cf_patch "/zones/$ZONE_ID/settings/ssl" '{"value":"strict"}')"; then echo "SSL 已设为 完全（严格）。"; else echo "SSL 设置失败（多半是 token 缺 Zone Settings 权限）。"; MANUAL="$MANUAL
- CF 面板 SSL/TLS -> 概述 -> 设为 完全（严格）"; fi
if cf_ok "$(cf_patch "/zones/$ZONE_ID/settings/always_use_https" '{"value":"on"}')"; then echo "强制 HTTPS 已开。"; else echo "强制 HTTPS 设置失败，跳过（不影响看片）。"; fi
if cf_ok "$(cf_patch "/zones/$ZONE_ID/settings/rocket_loader" '{"value":"off"}')"; then echo "Rocket Loader 已关。"; else echo "Rocket Loader 设置失败（多半是 token 缺 Zone Settings 权限）。"; MANUAL="$MANUAL
- CF 面板 速度 -> 优化 -> Rocket Loader 关掉"; fi

echo ">>> [5/9] CF 缓存规则：$DOMAIN 全站绕过缓存（视频不走边缘缓存）"
export ZONE_ID DOMAIN
CACHE_RES=$(python3 - "$ZONE_ID" "$DOMAIN" <<'PYEOF'
import json, os, sys, urllib.request
token = os.environ["CF_TOKEN"]
zone = sys.argv[1]; host = sys.argv[2]
base = "https://api.cloudflare.com/client/v4"
def call(method, path, body=None):
    req = urllib.request.Request(base + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.load(r)
    except Exception as e:
        try:
            return json.load(e)  # type: ignore
        except Exception:
            return {"success": False, "errors": [{"message": str(e)}]}
rule = {"action": "set_cache_settings",
        "action_parameters": {"cache": False},
        "expression": f'(http.host eq "{host}")',
        "description": "emby-hk-bypass-cache",
        "enabled": True}
ep = f"/zones/{zone}/rulesets/phases/http_request_cache_settings/entrypoint"
cur = call("GET", ep)
if not cur.get("success"):
    # 规则集不存在就新建
    res = call("POST", f"/zones/{zone}/rulesets",
               {"kind": "zone", "phase": "http_request_cache_settings",
                "name": "emby-hk cache rules", "rules": [rule]})
    print("OK" if res.get("success") else "FAIL " + json.dumps(res.get("errors", []), ensure_ascii=False))
    sys.exit(0)
rules = (cur.get("result") or {}).get("rules") or []
if any(r.get("description") == "emby-hk-bypass-cache" for r in rules):
    print("OK exists")
    sys.exit(0)
rules.append(rule)
res = call("PUT", ep, {"rules": rules})
print("OK" if res.get("success") else "FAIL " + json.dumps(res.get("errors", []), ensure_ascii=False))
PYEOF
)
case "$CACHE_RES" in
  OK*) echo "缓存绕过规则已生效（$CACHE_RES）。" ;;
  *) echo "缓存规则设置失败：$CACHE_RES（多半是 token 缺 Cache Rules 权限）。"; MANUAL="$MANUAL
- CF 面板 缓存 -> 缓存规则 -> 新建规则：主机名等于 $DOMAIN 时绕过缓存" ;;
esac

echo ">>> [6/9] 安装 nginx + certbot，测试源站"
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nginx certbot python3-certbot-dns-cloudflare curl unzip
ORIGIN_URL=""
CODE443=$(curl -sk -A "$BROWSER_UA" -o /dev/null -m 20 -w "%{http_code}" "https://${ORIGIN_HOST}:443/emby/System/Info/Public" || echo "000")
echo "源站 https :443 返回 HTTP $CODE443"
if [ "$CODE443" = "200" ]; then ORIGIN_URL="https://${ORIGIN_HOST}:443";
else
  CODE80=$(curl -s -A "$BROWSER_UA" -o /dev/null -m 20 -w "%{http_code}" "http://${ORIGIN_HOST}:80/emby/System/Info/Public" || echo "000")
  echo "源站 http :80 返回 HTTP $CODE80"
  [ "$CODE80" = "200" ] && ORIGIN_URL="http://${ORIGIN_HOST}:80"
fi
if [ -z "$ORIGIN_URL" ]; then echo "源站两个端口都连不上，退出。"; exit 1; fi
echo "选用上游：$ORIGIN_URL"

for P in 80 443; do
  if ss -tlnp 2>/dev/null | grep ":${P} " | grep -qv nginx; then echo "错误：端口 $P 已被 nginx 以外的程序占用。"; exit 1; fi
done

echo ">>> [7/9] 申请证书（DNS-01）并写 nginx 反代"
certbot certonly --dns-cloudflare --dns-cloudflare-credentials "$CF_INI" \
  --non-interactive --agree-tos --email "$ACME_EMAIL" -d "$DOMAIN" -d "$NODE_DOMAIN"
cat > "/etc/nginx/sites-available/${DOMAIN}" <<NGINXEOF
server {
    listen 80;
    server_name ${DOMAIN};
    return 301 https://\$host\$request_uri;
}
server {
    listen 443 ssl;
    server_name ${DOMAIN};

    ssl_certificate /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;

    client_max_body_size 0;
    proxy_buffering off;
    proxy_cache off;

    location / {
        proxy_pass ${ORIGIN_URL};
        proxy_ssl_server_name on;
        proxy_http_version 1.1;
        proxy_set_header Host ${ORIGIN_HOST};
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
NGINXEOF
ln -sf "/etc/nginx/sites-available/${DOMAIN}" "/etc/nginx/sites-enabled/${DOMAIN}"
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
ufw allow 80/tcp >/dev/null 2>&1 || true
ufw allow 443/tcp >/dev/null 2>&1 || true

echo ">>> [8/9] 验证反代（走橙云域名测）"
sleep 3
VCODE=$(curl -sk -A "$BROWSER_UA" -o /dev/null -m 20 -w "%{http_code}" "https://${DOMAIN}/emby/System/Info/Public" || echo "000")
echo "反代地址返回 HTTP $VCODE"
[ "$VCODE" != "200" ] && echo "警告：没返回 200，可能是橙云刚生效（等几分钟再测）或 SSL 模式不对。"

echo ">>> [9/9] 节点：Xray VLESS+WS 走 CF，与 Emby 共用 443"
# UUID 与订阅令牌：已存在就复用，重跑脚本不换（已导入的订阅不用重导）
mkdir -p /var/www/node-sub
NODE_UUID=""
if [ -f /usr/local/etc/xray/config.json ]; then
  NODE_UUID=$(python3 -c 'import json; print(json.load(open("/usr/local/etc/xray/config.json"))["inbounds"][0]["settings"]["clients"][0]["id"])' 2>/dev/null || echo "")
fi
[ -z "$NODE_UUID" ] && NODE_UUID=$(cat /proc/sys/kernel/random/uuid)
if [ -f /var/www/node-sub/.token ]; then
  SUB_TOKEN=$(cat /var/www/node-sub/.token)
else
  SUB_TOKEN=$(head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n')
  echo "$SUB_TOKEN" > /var/www/node-sub/.token
  chmod 600 /var/www/node-sub/.token
fi
NODE_SUB_URL="https://${NODE_DOMAIN}/sub/${SUB_TOKEN}.yaml"
NODE_VLESS="vless://${NODE_UUID}@${NODE_DOMAIN}:443?encryption=none&security=tls&sni=${NODE_DOMAIN}&type=ws&path=%2Fvless#HK-CF-VLESS"

# 装 Xray（官方安装脚本，systemd 服务 xray）
if ! command -v /usr/local/bin/xray >/dev/null 2>&1; then
  bash -c "$(curl -L https://github.com/XTLS/Xray-install/raw/main/install-release.sh)" install
fi
mkdir -p /usr/local/etc/xray
cat > /usr/local/etc/xray/config.json <<XRAYEOF
{
  "log": {"loglevel": "warning"},
  "inbounds": [
    {
      "port": ${XRAY_PORT},
      "listen": "127.0.0.1",
      "protocol": "vless",
      "settings": {"clients": [{"id": "${NODE_UUID}", "flow": ""}], "decryption": "none"},
      "streamSettings": {"network": "ws", "wsSettings": {"path": "${WS_PATH}"}}
    }
  ],
  "outbounds": [{"protocol": "freedom", "settings": {}}]
}
XRAYEOF
systemctl enable --now xray
systemctl restart xray
sleep 2
systemctl is-active --quiet xray || { echo "Xray 没起来，退出。"; systemctl status xray --no-pager | head -20; exit 1; }
echo "Xray 已启动（VLESS+WS，127.0.0.1:${XRAY_PORT}${WS_PATH}）。"

# 订阅 YAML（Clash 格式，梦盒 importSubscription 用 clash:// 深链导入）
cat > "/var/www/node-sub/${SUB_TOKEN}.yaml" <<YAMLEOF
proxies:
- name: HK-CF-VLESS
  type: vless
  server: ${NODE_DOMAIN}
  port: 443
  uuid: ${NODE_UUID}
  udp: true
  tls: true
  servername: ${NODE_DOMAIN}
  network: ws
  ws-opts:
    path: ${WS_PATH}
    headers:
      Host: ${NODE_DOMAIN}
YAMLEOF
chmod 644 "/var/www/node-sub/${SUB_TOKEN}.yaml"

# nginx：node-hk 域名——/vless 转 Xray，/sub/ 出订阅文件，其余 404
cat > "/etc/nginx/sites-available/${NODE_DOMAIN}" <<NGINXEOF
server {
    listen 80;
    server_name ${NODE_DOMAIN};
    return 301 https://\$host\$request_uri;
}
server {
    listen 443 ssl;
    server_name ${NODE_DOMAIN};

    ssl_certificate /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;

    client_max_body_size 0;

    location ${WS_PATH} {
        proxy_pass http://127.0.0.1:${XRAY_PORT};
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    location /sub/ {
        alias /var/www/node-sub/;
        default_type text/plain;
    }

    location / {
        return 404;
    }
}
NGINXEOF
ln -sf "/etc/nginx/sites-available/${NODE_DOMAIN}" "/etc/nginx/sites-enabled/${NODE_DOMAIN}"
nginx -t && systemctl reload nginx

# 验证订阅走 CF 能拉到
sleep 3
SUB_CODE=$(curl -sk -o /dev/null -m 20 -w "%{http_code}" "$NODE_SUB_URL" || echo "000")
echo "节点订阅走 CF 拉取返回 HTTP $SUB_CODE"
[ "$SUB_CODE" != "200" ] && echo "警告：订阅没返回 200，可能是橙云刚生效（等几分钟再测）。"

echo ""
echo "=================================================="
echo "完成！反代地址：https://${DOMAIN}（443，无需带端口）"
echo "CapyPlayer / 琳：地址 ${DOMAIN}，端口 443，HTTPS 开，路径留空，墨云阁账号登录。"
echo "手机实测：先播泰坦尼克号 144.9Mbps 那版，CF 免费版大码率直出可能 524/限流，卡就只看中小码率。"
echo "节点订阅：$NODE_SUB_URL"
echo "节点链接：$NODE_VLESS"
echo "梦盒里点「导入到梦盒」走订阅导入（以后节点有变，更新订阅即可）；单条链接也可复制手动导入。"
if [ -n "$MANUAL" ]; then
  echo ""
  echo "以下几项因 token 权限不足没自动改上，请去 CF 面板手动点一下：$MANUAL"
  echo "想下次全自动：在 CF 后台新建 token，权限加 Zone Settings Edit + Cache Rules Edit（区域只选 suanran9527.com），替换 /root/.cf.ini 再跑一遍即可。"
fi
echo "=================================================="
