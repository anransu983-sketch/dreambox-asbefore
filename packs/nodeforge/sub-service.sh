#!/bin/bash
# ============================================================
# NodeForge 订阅小服务（双引擎共用，被 deploy*.sh source 后调用）
# 作用：把一份 Clash YAML 放在随机令牌路径后用极简 HTTP 服务发出，
#       供梦盒远程导入/更新。隐私口径：
#       - 路径令牌 128 位随机（openssl rand -hex 16），落盘复用，只存
#         /usr/local/etc/nodeforge-sub/server.json（600，目录 700）
#       - 服务只认确切令牌路径（可带 .yaml 后缀），其余一律 404
#       - 不写访问日志（免令牌被记进 journal），响应禁缓存、noindex
#       - systemd 沙箱：NoNewPrivileges + ProtectSystem=strict + 空能力集
# 用法：nfsub_setup full <template> <uuid> <pub> <sid>   （sing-box）
#       nfsub_setup hy2only                             （s-ui，参数取全局变量）
# 结果变量：NF_SUB_URL / NF_SUB_PORT / NF_SUB_OK
# ============================================================

NFSUB_DIR="/usr/local/etc/nodeforge-sub"
NFSUB_CFG="$NFSUB_DIR/server.json"
NFSUB_YAML="$NFSUB_DIR/clash.yaml"
NFSUB_BIN="/usr/local/bin/nodeforge-sub.py"
NFSUB_UNIT="/etc/systemd/system/nodeforge-sub.service"

nfsub_port_busy() {
    if command -v ss >/dev/null 2>&1; then
        ss -tln 2>/dev/null | grep -q ":$1 "
    else
        netstat -tln 2>/dev/null | grep -q ":$1 "
    fi
}

nfsub_write_files() {
    cat > "$NFSUB_BIN" <<'PYEOF'
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CFG = "/usr/local/etc/nodeforge-sub/server.json"
_cfg = json.load(open(CFG))
TOKEN = _cfg["token"]
FILE = _cfg["file"]
PORT = int(_cfg["port"])

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _serve(self, head=False):
        path = self.path.split("?", 1)[0].split("#", 1)[0]
        if path not in ("/" + TOKEN, "/" + TOKEN + ".yaml"):
            body = b"not found"
            self.send_response(404)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if not head:
                self.wfile.write(body)
            return
        try:
            with open(FILE, "rb") as f:
                data = f.read()
        except OSError:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/yaml; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Robots-Tag", "noindex, nofollow")
        self.end_headers()
        if not head:
            self.wfile.write(data)

    def do_GET(self):
        self._serve(False)

    def do_HEAD(self):
        self._serve(True)

    def do_POST(self):
        self._serve(True)

    def do_PUT(self):
        self._serve(True)

    def do_DELETE(self):
        self._serve(True)

ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
PYEOF
    chmod 755 "$NFSUB_BIN"
    cat > "$NFSUB_UNIT" <<'UNITEOF'
[Unit]
Description=NodeForge subscription file server (token path only)
After=network.target

[Service]
ExecStart=/usr/bin/python3 /usr/local/bin/nodeforge-sub.py
Restart=on-failure
RestartSec=3
User=root
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadOnlyPaths=/usr/local/etc/nodeforge-sub
CapabilityBoundingSet=

[Install]
WantedBy=multi-user.target
UNITEOF
}

nfsub_render_full() { # $1=template $2=uuid $3=pub $4=sid
    python3 - "$1" "$NFSUB_YAML" "$PUBLIC_HOST" "$NODE_PORT" "$2" "$3" "$4" \
        "$SNI" "$NODE_NAME" "$SUB_ID" "$PROTOCOLS" "$HY2_PASS" "$HY2_PORT" \
        "$SS_KEY" "$SS_PORT" <<'PYEOF'
import json, sys
(tpl_path, out_path, host, node_port, uuid, pub, sid, sni,
 name, sub_id, protos, hy2_pass, hy2_port, ss_key, ss_port) = sys.argv[1:16]
with open(tpl_path) as f:
    yaml = f.read()
disp = name if name else sub_id
yaml = (yaml.replace("{{NODE_NAME}}", disp)
            .replace("{{SERVER}}", host)
            .replace("{{PORT}}", str(node_port))
            .replace("{{UUID}}", uuid)
            .replace("{{PUBLIC_KEY}}", pub)
            .replace("{{SHORT_ID}}", sid))
plist = [p for p in protos.split(",") if p]
extras = []
if "hy2" in plist:
    extras.append((disp + " Hy2",
                   "- name: %s Hy2\n  type: hysteria2\n  server: %s\n  port: %s\n  password: %s\n  sni: %s\n  skip-cert-verify: true"
                   % (disp, host, hy2_port, json.dumps(hy2_pass), sni)))
if "ss" in plist:
    extras.append((disp + " SS",
                   "- name: %s SS\n  type: ss\n  server: %s\n  port: %s\n  cipher: 2022-blake3-aes-256-gcm\n  password: %s\n  udp: true"
                   % (disp, host, ss_port, json.dumps(ss_key))))
if extras:
    block = "\n".join(lines for _, lines in extras)
    yaml = yaml.replace("\nproxy-groups:", "\n" + block + "\nproxy-groups:", 1)
    names = [n for n, _ in extras]
    for anchor in ("- Auto\n  - " + disp,
                   "  tolerance: 50\n  proxies:\n  - " + disp):
        idx = yaml.find(anchor)
        if idx >= 0:
            ins = "".join("\n  - " + n for n in names)
            yaml = yaml[:idx + len(anchor)] + ins + yaml[idx + len(anchor):]
with open(out_path, "w") as f:
    f.write(yaml)
PYEOF
}

nfsub_render_hy2only() {
    python3 - "$NFSUB_YAML" "$PUBLIC_HOST" "$HY2_PORT" "$HY2_PASS" "$SNI" \
        "$NODE_NAME" "$SUB_ID" <<'PYEOF'
import json, sys
(out_path, host, hy2_port, hy2_pass, sni, name, sub_id) = sys.argv[1:8]
disp = (name if name else sub_id) + " Hy2"
lines = [
    "mixed-port: 7890",
    "mode: rule",
    "log-level: info",
    "proxies:",
    "- name: " + disp,
    "  type: hysteria2",
    "  server: " + host,
    "  port: " + str(hy2_port),
    "  password: " + json.dumps(hy2_pass),
    "  sni: " + sni,
    "  skip-cert-verify: true",
    "proxy-groups:",
    "- name: Proxy",
    "  type: select",
    "  proxies:",
    "  - " + disp,
    "rules:",
    "- MATCH,Proxy",
    "",
]
with open(out_path, "w") as f:
    f.write("\n".join(lines))
PYEOF
}

nfsub_setup() {
    NF_SUB_URL=""; NF_SUB_PORT=0; NF_SUB_OK="fail"
    local mode="$1"
    mkdir -p "$NFSUB_DIR"
    chmod 700 "$NFSUB_DIR" 2>/dev/null || true
    local token="" port=""
    if [ -f "$NFSUB_CFG" ]; then
        token="$(python3 -c 'import json;print(json.load(open("/usr/local/etc/nodeforge-sub/server.json")).get("token",""))' 2>/dev/null || true)"
        port="$(python3 -c 'import json;print(json.load(open("/usr/local/etc/nodeforge-sub/server.json")).get("port",""))' 2>/dev/null || true)"
    fi
    if [ -z "$token" ]; then token="$(openssl rand -hex 16)" || return 0; fi
    if [ -z "$port" ]; then
        local cand=$((NODE_PORT + 5)) tries=0
        while [ $tries -lt 8 ]; do
            if ! nfsub_port_busy "$cand"; then port="$cand"; break; fi
            cand=$((cand + 1)); tries=$((tries + 1))
        done
        [ -n "$port" ] || return 0
    fi
    if [ "$mode" = "full" ]; then
        [ -f "$2" ] || { log "订阅模板缺失，跳过订阅服务（本地导入仍可用）"; return 0; }
        nfsub_render_full "$2" "$3" "$4" "$5" || return 0
    else
        nfsub_render_hy2only || return 0
    fi
    chmod 600 "$NFSUB_YAML" 2>/dev/null || true
    python3 - "$NFSUB_CFG" "$token" "$port" "$NFSUB_YAML" <<'PYEOF'
import json, sys
cfg = {"token": sys.argv[2], "port": int(sys.argv[3]), "file": sys.argv[4]}
with open(sys.argv[1], "w") as f:
    json.dump(cfg, f)
PYEOF
    chmod 600 "$NFSUB_CFG" 2>/dev/null || true
    nfsub_write_files
    systemctl daemon-reload >/dev/null 2>&1 || true
    systemctl enable nodeforge-sub >/dev/null 2>&1 || true
    systemctl restart nodeforge-sub >/dev/null 2>&1 || return 0
    sleep 1
    systemctl is-active --quiet nodeforge-sub || { log "订阅服务未启动（节点不受影响）"; return 0; }
    if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -qi 'status: active'; then
        ufw allow "$port"/tcp >/dev/null 2>&1 || true
    elif command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
        firewall-cmd --permanent --add-port="$port"/tcp >/dev/null 2>&1 || true
        firewall-cmd --reload >/dev/null 2>&1 || true
    fi
    local code_ok code_bad
    code_ok="$(curl -s -o /tmp/nfsub-test.yaml -w '%{http_code}' --max-time 8 "http://127.0.0.1:$port/$token" 2>/dev/null || true)"
    code_bad="$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://127.0.0.1:$port/" 2>/dev/null || true)"
    if [ "$code_ok" = "200" ] && [ "$code_bad" = "404" ] && grep -q 'proxies:' /tmp/nfsub-test.yaml 2>/dev/null; then
        NF_SUB_PORT="$port"
        NF_SUB_URL="http://${PUBLIC_HOST}:${port}/${token}"
        NF_SUB_OK="ok"
        log "订阅服务就绪（端口 $port，令牌路径，错路径 404 已验）"
    else
        log "订阅服务自检未过（ok=$code_ok bad=$code_bad，节点不受影响）"
    fi
    rm -f /tmp/nfsub-test.yaml
    return 0
}
