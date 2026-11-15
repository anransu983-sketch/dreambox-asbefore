#!/bin/bash
# 自建图床一键安装（英国 9929 VPS，Debian 12，root 下跑）
# 用途：给 NodeSeek 测评帖传图，替代 nodeimage（它现在要 NodeSeek 授权＋云端过不了 CF 验证）
# 成品：https://img.suanran9527.com/ —— 手机浏览器打开就能传图，传完直接给 Markdown 链接
# 依赖：本机 /root/.cf.ini 已放好 Cloudflare API token（600 权限），格式一行：
#   dns_cloudflare_api_token = <token>
#   （只要 DNS 编辑权就够：改 img 子域解析＋DNS-01 签证书）
# 用法（梦盒终端里，在英国 9929 那台机上）：
#   curl -sSL https://raw.githubusercontent.com/anransu983-sketch/dreambox-asbefore/main/scripts/imgbed-install.sh | sudo bash
# 重跑安全：token、证书、DNS 都只建一次，已有的不重建。
set -e
DOMAIN="img.suanran9527.com"
ZONE="suanran9527.com"
APP_DIR="/opt/imgbed"
STORE="/var/www/img"
PORT="17235"
CF_INI="/root/.cf.ini"
CF_API="https://api.cloudflare.com/client/v4"

say() { echo "[图床] $*"; }

[ "$(id -u)" = "0" ] || { echo "请用 root 跑（sudo bash）"; exit 1; }
[ -f "$CF_INI" ] || { echo "$CF_INI 不存在：先把 CF token 写进去再跑（格式 dns_cloudflare_api_token = ...，权限 600）。"; exit 1; }
CF_TOKEN=$(grep -E '^dns_cloudflare_api_token' "$CF_INI" | cut -d= -f2 | tr -d ' "')
[ -n "$CF_TOKEN" ] || { echo "$CF_INI 里没有 dns_cloudflare_api_token，请先补上再跑。"; exit 1; }

# ---------- 1. 依赖 ----------
say "装依赖（nginx / python3-flask / certbot）……"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq nginx python3 python3-flask certbot python3-certbot-dns-cloudflare curl openssl > /dev/null
python3 -c "import flask" || { echo "flask 没装上"; exit 1; }

# ---------- 2. 上传 token（只生成一次，重跑不变） ----------
mkdir -p "$APP_DIR" "$STORE"
chown www-data:www-data "$STORE"
chmod 755 "$STORE"
if [ -f "$APP_DIR/token" ]; then
  TOKEN=$(cat "$APP_DIR/token")
  say "上传 token 已存在，沿用（不重建）。"
else
  TOKEN=$(openssl rand -hex 16)
  printf '%s' "$TOKEN" > "$APP_DIR/token"
  chown www-data:www-data "$APP_DIR/token"
  chmod 600 "$APP_DIR/token"
  say "上传 token 已生成（只在最后打印一次，记得保存）。"
fi

# ---------- 3. Flask 上传服务 ----------
cat > "$APP_DIR/app.py" <<'PYEOF'
import os, secrets, datetime
from flask import Flask, request, jsonify

BASE = '/var/www/img'
DOMAIN = 'img.suanran9527.com'
ALLOWED = {'png', 'jpg', 'jpeg', 'gif', 'webp'}
MAX_BYTES = 20 * 1024 * 1024

app = Flask(__name__)
with open('/opt/imgbed/token') as f:
    TOKEN = f.read().strip()

def token_ok(req):
    t = req.form.get('token') or req.headers.get('X-Upload-Token') or ''
    return secrets.compare_digest(t, TOKEN)

@app.route('/health')
def health():
    return 'ok'

@app.route('/upload', methods=['POST'])
def upload():
    if not token_ok(request):
        return jsonify({'error': 'bad token'}), 403
    f = request.files.get('file')
    if f is None or f.filename == '':
        return jsonify({'error': 'no file'}), 400
    ext = f.filename.rsplit('.', 1)[-1].lower() if '.' in f.filename else ''
    if ext not in ALLOWED:
        return jsonify({'error': 'bad type, only png/jpg/gif/webp'}), 400
    data = f.read()
    if len(data) > MAX_BYTES:
        return jsonify({'error': 'too large, max 20MB'}), 400
    sub = datetime.datetime.now().strftime('%Y/%m')
    name = secrets.token_hex(8) + '.' + ext
    d = os.path.join(BASE, sub)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, name), 'wb') as fh:
        fh.write(data)
    url = 'https://%s/%s/%s' % (DOMAIN, sub, name)
    return jsonify({'url': url, 'markdown': '![](%s)' % url})

if __name__ == '__main__':
    app.run(host='127.0.0.1', port=17235, threaded=True)
PYEOF
chown www-data:www-data "$APP_DIR/app.py"

cat > /etc/systemd/system/imgbed.service <<'SVCEOF'
[Unit]
Description=imgbed upload service
After=network.target

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/imgbed
ExecStart=/usr/bin/python3 /opt/imgbed/app.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
SVCEOF
systemctl daemon-reload
systemctl enable --now imgbed.service
sleep 2
curl -s --max-time 5 http://127.0.0.1:17235/health | grep -q ok || { echo "imgbed 服务没起来，看日志：journalctl -u imgbed -n 50"; exit 1; }
say "上传服务已起（127.0.0.1:17235）。"

# ---------- 4. 手机上传页 ----------
cat > "$STORE/index.html" <<'HTMLEOF'
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<title>图床</title>
<style>
body{background:#111;color:#eee;font-family:sans-serif;margin:0;padding:20px}
h1{font-size:20px;margin:0 0 16px}
input,button{font-size:16px;width:100%;box-sizing:border-box;margin:8px 0;padding:14px;border-radius:10px;border:1px solid #444;background:#222;color:#eee}
button{background:#2e7d32;border:none;font-weight:bold}
button:active{background:#388e3c}
#out{word-break:break-all;margin-top:12px}
#out img{max-width:100%;border-radius:8px;margin-top:8px}
code{background:#222;padding:4px 8px;border-radius:6px;display:block;margin-top:8px}
</style>
</head>
<body>
<h1>传图</h1>
<input type="file" id="file" accept="image/png,image/jpeg,image/gif,image/webp">
<input type="password" id="token" placeholder="上传 token（只输一次，自动记住）">
<button id="up">上传</button>
<div id="out"></div>
<script>
var t = document.getElementById('token');
t.value = localStorage.getItem('imgbed_token') || '';
document.getElementById('up').onclick = async function(){
  var f = document.getElementById('file').files[0];
  if(!f){ alert('先选一张图'); return; }
  localStorage.setItem('imgbed_token', t.value);
  var fd = new FormData();
  fd.append('file', f);
  fd.append('token', t.value);
  var out = document.getElementById('out');
  out.textContent = '上传中……';
  try{
    var r = await fetch('/upload', {method:'POST', body:fd});
    var j = await r.json();
    if(!r.ok){ out.textContent = '失败：' + (j.error || r.status); return; }
    out.innerHTML = '<img src="' + j.url + '"><code>' + j.markdown + '</code><button id="cp">复制 Markdown</button>';
    document.getElementById('cp').onclick = function(){ navigator.clipboard.writeText(j.markdown); this.textContent = '已复制'; };
  }catch(e){ out.textContent = '失败：' + e; }
};
</script>
</body>
</html>
HTMLEOF

# ---------- 5. Cloudflare DNS（img 子域 → 本机，橙云） ----------
say "配 DNS（$DOMAIN → 本机，橙云）……"
PUBIP=$(curl -s --max-time 10 https://api.ipify.org || hostname -I | awk '{print $1}')
cf_get()  { curl -s -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" "$CF_API$1"; }
cf_post() { curl -s -X POST -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" -d "$2" "$CF_API$1"; }
cf_put()  { curl -s -X PUT -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" -d "$2" "$CF_API$1"; }
ZONE_ID=$(cf_get "/zones?name=$ZONE" | python3 -c "import json,sys; print(json.load(sys.stdin)['result'][0]['id'])")
REC=$(cf_get "/zones/$ZONE_ID/dns_records?name=$DOMAIN&type=A")
REC_ID=$(echo "$REC" | python3 -c "import json,sys; r=json.load(sys.stdin)['result']; print(r[0]['id'] if r else '')")
BODY=$(python3 -c "import json; print(json.dumps({'type':'A','name':'$DOMAIN','content':'$PUBIP','ttl':1,'proxied':True}))")
if [ -n "$REC_ID" ]; then
  cf_put "/zones/$ZONE_ID/dns_records/$REC_ID" "$BODY" > /dev/null
  say "DNS 记录已更新（$DOMAIN → $PUBIP，橙云）。"
else
  cf_post "/zones/$ZONE_ID/dns_records" "$BODY" > /dev/null
  say "DNS 记录已建（$DOMAIN → $PUBIP，橙云）。"
fi

# ---------- 6. 证书（DNS-01，已有就跳过） ----------
if [ -d "/etc/letsencrypt/live/$DOMAIN" ]; then
  say "证书已存在，跳过签发。"
else
  say "签证书（DNS-01）……"
  certbot certonly --dns-cloudflare --dns-cloudflare-credentials "$CF_INI" \
    -d "$DOMAIN" --non-interactive --agree-tos --register-unsafely-without-email
fi

# ---------- 7. nginx ----------
cat > /etc/nginx/sites-available/imgbed <<'NXEOF'
server {
    listen 80;
    server_name img.suanran9527.com;
    return 301 https://$host$request_uri;
}
server {
    listen 443 ssl;
    server_name img.suanran9527.com;
    ssl_certificate /etc/letsencrypt/live/img.suanran9527.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/img.suanran9527.com/privkey.pem;
    root /var/www/img;
    index index.html;
    client_max_body_size 20m;
    location / {
        try_files $uri $uri/ =404;
    }
    location = /upload {
        proxy_pass http://127.0.0.1:17235;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        client_max_body_size 20m;
    }
    location = /health {
        proxy_pass http://127.0.0.1:17235;
    }
}
NXEOF
ln -sf /etc/nginx/sites-available/imgbed /etc/nginx/sites-enabled/imgbed
nginx -t
systemctl reload nginx
ufw allow 443/tcp > /dev/null 2>&1 || true
say "nginx 已配好。"

# ---------- 8. 自测：真传一张 1x1 图再删掉 ----------
say "自测上传……"
printf 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' | base64 -d > /tmp/imgbed-test.png
RESP=$(curl -s -F "file=@/tmp/imgbed-test.png" -F "token=$TOKEN" "https://$DOMAIN/upload")
echo "$RESP" | grep -q '"url"' || { echo "自测失败，上传接口返回：$RESP"; exit 1; }
TEST_URL=$(echo "$RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['url'])")
CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "$TEST_URL")
[ "$CODE" = "200" ] || { echo "自测失败，图片直链回 $CODE：$TEST_URL"; exit 1; }
rm -f "/var/www/img/${TEST_URL#https://$DOMAIN/}" /tmp/imgbed-test.png
say "自测通过（传图＋直链 200 都通，测试图已删）。"
echo ""
echo "================ 图床好了 ================"
echo "图床地址：https://$DOMAIN/"
echo "上传 Token：$TOKEN"
echo "（Token 只在这里打印一次，存在服务器 /opt/imgbed/token，丢了从那看；想换就删了它重跑脚本）"
echo "用法：手机浏览器打开图床地址，选图、填 token、点上传，出来就是 Markdown 链接，直接贴 NodeSeek。"
echo "curl 用法：curl -F \"file=@图.png\" -F \"token=$TOKEN\" https://$DOMAIN/upload"
echo "只收 png/jpg/gif/webp，单张最大 20MB；图片公开可见（有链接就能看），上传要 token。"
echo "=============================================="
