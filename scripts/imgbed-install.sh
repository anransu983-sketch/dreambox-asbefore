#!/bin/bash
# 自建图床一键安装（英国 9929 VPS，Debian 12，root 下跑）
# 用途：给 NodeSeek 测评帖传图，替代 nodeimage（它现在要 NodeSeek 授权＋云端过不了 CF 验证）
# 成品：https://img.suanran9527.com/ —— 手机浏览器打开就能传图，传完直接给 Markdown 链接
# 前置（先做，1 分钟）：Cloudflare 后台 suanran9527.com → DNS → Add record：
#   类型 A，名称 img，IPv4 地址 212.135.122.19，Proxy status 选 Proxied（橙云），Save。
#   不用 token、不写 /root/.cf.ini；证书走 HTTP-01，脚本自己签。
# 用法（梦盒终端里，在英国 9929 那台机上）：
#   curl -sSL https://raw.githubusercontent.com/anransu983-sketch/dreambox-asbefore/main/scripts/imgbed-install.sh | sudo bash
# 重跑安全：token、证书、DNS 都只建一次，已有的不重建。
set -e
DOMAIN="img.suanran9527.com"
APP_DIR="/opt/imgbed"
STORE="/var/www/img"
PORT="17235"

say() { echo "[图床] $*"; }

[ "$(id -u)" = "0" ] || { echo "请用 root 跑（sudo bash）"; exit 1; }

# ---------- 1. 依赖 ----------
say "装依赖（nginx / python3-flask / certbot）……"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq nginx python3 python3-flask certbot curl openssl > /dev/null
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

# ---------- 5. DNS 检查（解析必须已指到本机，否则先去 CF 后台加记录再重跑） ----------
say "查 DNS（$DOMAIN 应该指到本机）……"
PUBIP=$(curl -s --max-time 10 https://api.ipify.org || hostname -I | awk '{print $1}')
RESOLVED=""
for i in $(seq 1 12); do
  RESOLVED=$(getent hosts "$DOMAIN" | awk '{print $1}' | head -1)
  [ -z "$RESOLVED" ] && RESOLVED=$(curl -s --max-time 10 "https://dns.google/resolve?name=$DOMAIN&type=A" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['Answer'][0]['data'] if d.get('Answer') else '')" 2>/dev/null)
  [ "$RESOLVED" = "$PUBIP" ] && break
  sleep 10
done
if [ "$RESOLVED" != "$PUBIP" ]; then
  echo "DNS 还没指过来：$DOMAIN 现在解析到 ${RESOLVED:-（查不到）}，本机是 $PUBIP。"
  echo "去 Cloudflare 后台 suanran9527.com → DNS 加一条 A 记录：名称 img，内容 $PUBIP，Proxied，保存后重跑这个脚本。"
  exit 1
fi
say "DNS 已指到本机（$RESOLVED）。"

# ---------- 6. 证书（HTTP-01，已有就跳过） ----------
# 先立一个纯 HTTP 站，让 certbot 能放验证文件；签完再换成完整配置。
cat > /etc/nginx/sites-available/imgbed <<'NXEOF'
server {
    listen 80;
    server_name img.suanran9527.com;
    root /var/www/img;
    index index.html;
    location / {
        try_files $uri $uri/ =404;
    }
}
NXEOF
ln -sf /etc/nginx/sites-available/imgbed /etc/nginx/sites-enabled/imgbed
nginx -t && systemctl reload nginx
if [ -d "/etc/letsencrypt/live/$DOMAIN" ]; then
  say "证书已存在，跳过签发。"
else
  say "签证书（HTTP-01）……"
  certbot certonly --webroot -w /var/www/img -d "$DOMAIN" \
    --non-interactive --agree-tos --register-unsafely-without-email
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
