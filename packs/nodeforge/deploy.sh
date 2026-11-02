#!/bin/bash
# ============================================================
# NodeForge 一键部署（梦盒「一键节点」功能包通过 SSH 喂入执行）
# 目标：全新机器 → s-ui v1.6.7 + VLESS+Reality 入站 + 客户端，
#       面板只绑 127.0.0.1（公网打不开，管理走 SSH 隧道），
#       防火墙放行 节点端口/订阅端口/SSH，面板端口不放行。
# 输入（环境变量，密码不进 argv、不落盘）：
#   NF_ADMIN_USER  面板管理员用户名
#   NF_ADMIN_PASS  面板管理员密码
#   NF_SUB_ID      客户端名（= 订阅 ID，只能字母数字与 -_）
#   NF_NODE_PORT   节点监听端口（默认 443）
#   NF_SSH_PORT    SSH 端口（防火墙放行用，默认 22）
# 输出：每步 echo "== ..." 进度行；成功最后打印一行
#   NF_RESULT {json}（不含管理员密码，密码在调用方手里）
# 失败：打印 "NF_ERROR: ..." 并非零退出。
# 依据：alireza0/s-ui v1.6.7 源码核实（install.sh 提问顺序、
#   sui CLI、/app/api 的 login/keypairs/save、webListen 设置）。
# ============================================================
set -u
export SUI_LANG=en
export DEBIAN_FRONTEND=noninteractive

SUI_VER="v1.6.7"
PANEL_PORT=2095
PANEL_PATH="/app/"
SUB_PORT=2096
SUB_PATH="/sub/"
SNI="www.microsoft.com"
NODE_PORT="${NF_NODE_PORT:-443}"
SSH_PORT="${NF_SSH_PORT:-22}"
SUB_ID="${NF_SUB_ID:-node1}"
PUBLIC_HOST="${NF_PUBLIC_HOST:-}"
QUOTA_GB="${NF_QUOTA_GB:-}"
EXPIRE_DATE="${NF_EXPIRE_DATE:-}"
ADMIN_USER="${NF_ADMIN_USER:-}"
ADMIN_PASS="${NF_ADMIN_PASS:-}"
TLS_NAME="nf-reality"
INBOUND_TAG="nf-vless"

log() { echo "== $*"; }
fail() { echo "NF_ERROR: $*" >&2; exit 1; }

[ "$(id -u)" = "0" ] || fail "需要 root 执行"
[ -n "$ADMIN_USER" ] && [ -n "$ADMIN_PASS" ] || fail "缺少管理员用户名或密码"
case "$SUB_ID" in *[!A-Za-z0-9_-]*) fail "订阅 ID 只能用字母数字与 -_";; esac

# ── 0. 基础工具（curl / openssl / python3 / uuidgen） ──────
need_pkgs=""
command -v curl    >/dev/null 2>&1 || need_pkgs="$need_pkgs curl"
command -v openssl >/dev/null 2>&1 || need_pkgs="$need_pkgs openssl"
command -v python3 >/dev/null 2>&1 || need_pkgs="$need_pkgs python3"
command -v uuidgen >/dev/null 2>&1 || need_pkgs="$need_pkgs uuid-runtime"
if [ -n "$need_pkgs" ]; then
    log "安装基础工具：$need_pkgs"
    if command -v apt-get >/dev/null 2>&1; then
        # uuid-runtime 只在 Debian 系有，别的包名在 apt 里同名
        APT_OPTS="-o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20 -o Acquire::Retries=1"
        apt-get update -qq $APT_OPTS || apt-get update -qq $APT_OPTS || fail "apt-get update 失败"
        apt-get install -y -qq $need_pkgs >/dev/null 2>&1 || \
        apt-get install -y -qq curl openssl python3 util-linux >/dev/null || fail "基础工具安装失败"
    elif command -v dnf >/dev/null 2>&1; then
        dnf install -y -q curl openssl python3 util-linux >/dev/null || fail "基础工具安装失败"
    elif command -v yum >/dev/null 2>&1; then
        yum install -y -q curl openssl python3 util-linux >/dev/null || fail "基础工具安装失败"
    else
        fail "不支持的系统（无 apt/dnf/yum），请手动装 curl/openssl/python3 后重试"
    fi
fi
command -v python3 >/dev/null 2>&1 || fail "python3 不可用，无法继续"

gen_uuid() {
    if command -v uuidgen >/dev/null 2>&1; then uuidgen; else cat /proc/sys/kernel/random/uuid; fi
}

# ── 1. 安装 s-ui（已装则跳过装机，只校准设置） ────────────
if [ -x /usr/local/s-ui/sui ]; then
    log "检测到已安装 s-ui，跳过装机（将按新给的管理员账号校准）"
else
    log "下载 s-ui $SUI_VER 安装脚本"
    curl -fLs --max-time 120 -o /tmp/nf-sui-install.sh \
        "https://raw.githubusercontent.com/alireza0/s-ui/$SUI_VER/install.sh" \
        || fail "安装脚本下载失败（机器需能访问 GitHub）"
    bash -n /tmp/nf-sui-install.sh || fail "安装脚本语法校验失败"
    log "安装 s-ui（向导全自动应答）"
    # 提问顺序（v1.6.7 config_after_install 源码核实）：
    # 确认 / 面板端口 / 面板路径 / 订阅端口 / 订阅路径 / 改账号确认 / 用户名 / 密码
    printf '%s\n' 'y' "$PANEL_PORT" "$PANEL_PATH" "$SUB_PORT" "$SUB_PATH" 'y' "$ADMIN_USER" "$ADMIN_PASS" \
        | bash /tmp/nf-sui-install.sh "$SUI_VER" || fail "s-ui 安装失败"
    rm -f /tmp/nf-sui-install.sh
fi
# 双保险：CLI 直接写库（与向导等价，装后/已装都跑一遍）
/usr/local/s-ui/sui setting -port "$PANEL_PORT" -path "$PANEL_PATH" -subPort "$SUB_PORT" -subPath "$SUB_PATH" >/dev/null 2>&1
/usr/local/s-ui/sui admin -username "$ADMIN_USER" -password "$ADMIN_PASS" >/dev/null 2>&1 \
    || fail "设置管理员失败"
systemctl enable s-ui --now >/dev/null 2>&1 || true

log "等待面板就绪"
ok=0
for _ in $(seq 1 30); do
    if /usr/local/s-ui/sui healthcheck >/dev/null 2>&1 || curl -s -o /dev/null --max-time 3 "http://127.0.0.1:${PANEL_PORT}${PANEL_PATH}"; then ok=1; break; fi
    sleep 1
done
[ "$ok" = "1" ] || fail "面板 30 秒内未就绪"

API="http://127.0.0.1:${PANEL_PORT}${PANEL_PATH}api"
CJ="$(mktemp)"
trap 'rm -f "$CJ"' EXIT
HDR='X-Requested-With: XMLHttpRequest'

api_post_ok() {
    # $1=action 其余为 curl 参数；回包 success 不为 true 即失败并打印 msg
    local action="$1"; shift
    local resp
    resp="$(curl -s --max-time 25 -b "$CJ" -H "$HDR" "$@" "$API/$action")" || fail "调用 $action 网络失败"
    if ! printf '%s' "$resp" | grep -q '"success": *true'; then
        echo "NF_ERROR: $action 返回失败：$(printf '%s' "$resp" | head -c 300)" >&2
        exit 1
    fi
    printf '%s' "$resp"
}

# ── 2. 登录 ────────────────────────────────────────────────
log "登录面板 API"
login_resp="$(curl -s --max-time 25 -c "$CJ" -H "$HDR" -d "user=$ADMIN_USER" -d "pass=$ADMIN_PASS" "$API/login")"
printf '%s' "$login_resp" | grep -q '"success": *true' \
    || fail "面板登录失败（已装 s-ui 且密码与现管理员不一致时也会这样）：$(printf '%s' "$login_resp" | head -c 200)"

# ── 3. TLS 记录：已有同名则整套复用（含密钥），重跑幂等 ──
# s-ui 列表接口的 obj 形态不稳（数组/对象/JSON 字符串都见过），解析统一归一化：
# obj 与 server/client/config 子字段都可能是 JSON 字符串，先 norm 再取。
log "检查已有 TLS 记录"
TLS_LIST="$(curl -s --max-time 25 -b "$CJ" "$API/tls")"
TLS_FOUND="$(printf '%s' "$TLS_LIST" | python3 -c '
import json, sys
def norm(x):
    while isinstance(x, str):
        try: x = json.loads(x)
        except Exception: return None
    return x
try: d = json.load(sys.stdin)
except Exception: d = {}
o = norm(d.get("obj"))
if isinstance(o, dict):
    cand = [x for x in o.values() if isinstance(x, list)]
    o = cand[0] if cand else [x for x in o.values() if isinstance(x, dict)]
items = [x for x in (o or []) if isinstance(x, dict)]
hits = [t for t in items if t.get("name") == sys.argv[1]]
if hits:
    t = hits[-1]
    srv = norm(t.get("server")) or {}
    cli = norm(t.get("client")) or {}
    sreal = srv.get("reality") or {}
    creal = cli.get("reality") or {}
    sid = creal.get("short_id") or ""
    if isinstance(sid, list): sid = sid[0] if sid else ""
    print(t.get("id"), sreal.get("private_key", ""), creal.get("public_key", ""), sid)
' "$TLS_NAME")"
TLS_ID=""; PRIV=""; PUB=""; SID=""
if [ -n "$TLS_FOUND" ]; then
    read -r TLS_ID PRIV PUB SID <<< "$TLS_FOUND"
fi
if [ -n "$TLS_ID" ] && [ -n "$PRIV" ] && [ -n "$PUB" ]; then
    log "复用已有 TLS 记录（含原密钥对）"
else
    TLS_ID=""
    log "生成 Reality 密钥对"
    kp="$(curl -s --max-time 25 -b "$CJ" "$API/keypairs?k=reality")"
    PRIV="$(printf '%s' "$kp" | grep -o 'PrivateKey: [^"]*' | head -1 | cut -d' ' -f2)"
    PUB="$(printf '%s' "$kp" | grep -o 'PublicKey: [^"]*' | head -1 | cut -d' ' -f2)"
    [ -n "$PRIV" ] && [ -n "$PUB" ] || fail "密钥对生成失败：$(printf '%s' "$kp" | head -c 200)"
    SID="$(openssl rand -hex 4)"
fi
UUID="$(gen_uuid)"
log "UUID 与 Short ID 已就绪"

# ── 4. TLS 记录（新建时才写） ─────────────────────────────
if [ -z "$TLS_ID" ]; then
    log "创建 TLS 记录"
    tls_data="$(python3 - "$SNI" "$PRIV" "$PUB" "$SID" "$TLS_NAME" <<'PYEOF2'
import json, sys
sni, priv, pub, sid, name = sys.argv[1:6]
data = {
    "name": name,
    "server": {
        "enabled": True,
        "server_name": sni,
        "reality": {
            "enabled": True,
            "handshake": {"server": sni, "server_port": 443},
            "private_key": priv,
            "short_id": [sid],
        },
    },
    "client": {
        "enabled": True,
        "server_name": sni,
        "utls": {"enabled": True, "fingerprint": "chrome"},
        "reality": {"enabled": True, "public_key": pub, "short_id": sid},
    },
}
print(json.dumps(data))
PYEOF2
)" || fail "TLS 数据组装失败"
    api_post_ok save --data-urlencode object=tls --data-urlencode action=new \
        --data-urlencode "data=$tls_data" >/dev/null
    TLS_LIST="$(curl -s --max-time 25 -b "$CJ" "$API/tls")"
    TLS_ID="$(printf '%s' "$TLS_LIST" | python3 -c '
import json, sys
def norm(x):
    while isinstance(x, str):
        try: x = json.loads(x)
        except Exception: return None
    return x
try: d = json.load(sys.stdin)
except Exception: d = {}
o = norm(d.get("obj"))
if isinstance(o, dict):
    cand = [x for x in o.values() if isinstance(x, list)]
    o = cand[0] if cand else [x for x in o.values() if isinstance(x, dict)]
items = [x for x in (o or []) if isinstance(x, dict)]
hits = [t for t in items if t.get("name") == sys.argv[1]]
print(hits[-1].get("id") if hits else "")
' "$TLS_NAME")"
    [ -n "$TLS_ID" ] || fail "TLS 记录创建后未找到"
fi

# ── 5. 入站（VLESS + Reality）：已有同 tag 则复用 ─────────
# 订阅链接里的节点地址取自入站 addrs；不写 addrs 时 s-ui 会用「保存请求的
# 主机名」——本脚本在本机用 127.0.0.1 调 API，链接就会被写成 127.0.0.1。
if [ -z "$PUBLIC_HOST" ]; then
    PUBLIC_HOST="$(curl -s --max-time 8 https://api.ipify.org 2>/dev/null || true)"
    [ -n "$PUBLIC_HOST" ] && log "公网地址自动探测为 $PUBLIC_HOST（订阅链接用）"
fi
log "检查已有入站"
INB_LIST="$(curl -s --max-time 25 -b "$CJ" "$API/inbounds")"
INB_FOUND="$(printf '%s' "$INB_LIST" | python3 -c '
import json, sys
def norm(x):
    while isinstance(x, str):
        try: x = json.loads(x)
        except Exception: return None
    return x
try: d = json.load(sys.stdin)
except Exception: d = {}
o = norm(d.get("obj"))
if isinstance(o, dict):
    cand = [x for x in o.values() if isinstance(x, list)]
    o = cand[0] if cand else [x for x in o.values() if isinstance(x, dict)]
items = [x for x in (o or []) if isinstance(x, dict)]
hits = [t for t in items if t.get("tag") == sys.argv[1]]
if hits:
    t = hits[-1]
    print(t.get("id"), t.get("listen_port", ""))
' "$INBOUND_TAG")"
INBOUND_ID=""; INB_PORT=""
if [ -n "$INB_FOUND" ]; then
    read -r INBOUND_ID INB_PORT <<< "$INB_FOUND"
fi
if [ -n "$INBOUND_ID" ]; then
    log "复用已有入站（端口 $INB_PORT）"
    if [ -n "$INB_PORT" ] && [ "$INB_PORT" != "$NODE_PORT" ]; then
        log "注意：已有入站端口是 $INB_PORT，与本次填写的 $NODE_PORT 不同，沿用 $INB_PORT"
        NODE_PORT="$INB_PORT"
    fi
    # 复用入站：addrs 为空/不对则整对象 edit 回写（edit 会连带重生成客户端链接）
    if [ -n "$PUBLIC_HOST" ]; then
        EDIT_DATA="$(printf '%s' "$INB_LIST" | python3 -c '
import json, sys
def norm(x):
    while isinstance(x, str):
        try: x = json.loads(x)
        except Exception: return None
    return x
try: d = json.load(sys.stdin)
except Exception: d = {}
o = norm(d.get("obj"))
if isinstance(o, dict):
    cand = [x for x in o.values() if isinstance(x, list)]
    o = cand[0] if cand else [x for x in o.values() if isinstance(x, dict)]
items = [x for x in (o or []) if isinstance(x, dict)]
hits = [t for t in items if t.get("tag") == sys.argv[1]]
host, port = sys.argv[2], int(sys.argv[3])
if hits:
    t = dict(hits[-1])
    addrs = t.get("addrs") or []
    cur = addrs[0].get("server") if addrs and isinstance(addrs[0], dict) else ""
    if cur != host:
        t["addrs"] = [{"server": host, "server_port": port}]
        print(json.dumps(t))
' "$INBOUND_TAG" "$PUBLIC_HOST" "$NODE_PORT")"
        if [ -n "$EDIT_DATA" ]; then
            if api_post_ok save --data-urlencode object=inbounds --data-urlencode action=edit \
                --data-urlencode "data=$EDIT_DATA" >/dev/null; then
                log "已把入站地址校正为公网地址 $PUBLIC_HOST（订阅链接用）"
            else
                log "入站地址校正失败（订阅地址可能不对，文末校验会确认）"
            fi
        else
            log "入站地址已是公网地址 $PUBLIC_HOST"
        fi
    fi
else
    log "创建 VLESS 入站（端口 $NODE_PORT）"
    inbound_data="$(python3 -c '
import json, sys
d = {"type": "vless", "tag": "nf-vless", "listen": "::",
     "listen_port": int(sys.argv[1]), "tls_id": int(sys.argv[2])}
host = sys.argv[3].strip()
if host:
    d["addrs"] = [{"server": host, "server_port": int(sys.argv[1])}]
print(json.dumps(d))
' "$NODE_PORT" "$TLS_ID" "$PUBLIC_HOST")"
    api_post_ok save --data-urlencode object=inbounds --data-urlencode action=new \
        --data-urlencode "data=$inbound_data" >/dev/null
    INB_LIST="$(curl -s --max-time 25 -b "$CJ" "$API/inbounds")"
    INBOUND_ID="$(printf '%s' "$INB_LIST" | python3 -c '
import json, sys
def norm(x):
    while isinstance(x, str):
        try: x = json.loads(x)
        except Exception: return None
    return x
try: d = json.load(sys.stdin)
except Exception: d = {}
o = norm(d.get("obj"))
if isinstance(o, dict):
    cand = [x for x in o.values() if isinstance(x, list)]
    o = cand[0] if cand else [x for x in o.values() if isinstance(x, dict)]
items = [x for x in (o or []) if isinstance(x, dict)]
hits = [t for t in items if t.get("tag") == sys.argv[1]]
print(hits[-1].get("id") if hits else "")
' "$INBOUND_TAG")"
    [ -n "$INBOUND_ID" ] || fail "入站创建后未找到"
fi

# ── 5.9 流量/到期换算（订阅卡显示用，写进客户端 volume/expiry）──
VOL_BYTES=0
if [ -n "$QUOTA_GB" ]; then
    VOL_BYTES="$(python3 -c '
import sys
try: gb = float(sys.argv[1])
except Exception: gb = 0
print(int(gb * 1024**3) if gb > 0 else 0)
' "$QUOTA_GB")"
fi
EXP_EPOCH=0
if [ -n "$EXPIRE_DATE" ]; then
    EXP_EPOCH="$(date -d "$EXPIRE_DATE 23:59:59" +%s 2>/dev/null || echo 0)"
fi
NOW_EPOCH="$(date +%s)"
NEXT_RESET=$(( NOW_EPOCH + 30 * 86400 ))
if [ "$VOL_BYTES" -gt 0 ] || [ "$EXP_EPOCH" -gt 0 ]; then
    log "订阅将显示：总流量 ${QUOTA_GB:-不限} GB｜到期 ${EXPIRE_DATE:-不限}（流量按 30 天一轮自动重置）"
fi

# ── 6. 客户端（name = 订阅 ID）：已有同名则复用 ───────────
log "检查已有客户端"
CLI_LIST="$(curl -s --max-time 25 -b "$CJ" "$API/clients")"
CLI_FOUND="$(printf '%s' "$CLI_LIST" | python3 -c '
import json, sys
def norm(x):
    while isinstance(x, str):
        try: x = json.loads(x)
        except Exception: return None
    return x
try: d = json.load(sys.stdin)
except Exception: d = {}
o = norm(d.get("obj"))
if isinstance(o, dict):
    cand = [x for x in o.values() if isinstance(x, list)]
    o = cand[0] if cand else [x for x in o.values() if isinstance(x, dict)]
items = [x for x in (o or []) if isinstance(x, dict)]
hits = [c for c in items if c.get("name") == sys.argv[1]]
print("yes" if hits else "")
' "$SUB_ID")"
if [ "$CLI_FOUND" = "yes" ]; then
    log "复用已有客户端（订阅 ID：$SUB_ID）"
else
    log "创建客户端（订阅 ID：$SUB_ID）"
    client_data="$(python3 -c '
import json, sys
vol, exp, nxt = int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
print(json.dumps({"enable": True, "name": sys.argv[1],
                  "config": {"vless": {"uuid": sys.argv[2], "flow": "xtls-rprx-vision"}},
                  "inbounds": [int(sys.argv[3])], "links": [],
                  "volume": vol, "expiry": exp,
                  "autoReset": vol > 0, "resetDays": 30 if vol > 0 else 0,
                  "nextReset": nxt if vol > 0 else 0}))
' "$SUB_ID" "$UUID" "$INBOUND_ID" "$VOL_BYTES" "$EXP_EPOCH" "$NEXT_RESET")"
    api_post_ok save --data-urlencode object=clients --data-urlencode action=new \
        --data-urlencode "data=$client_data" >/dev/null
    CLI_LIST="$(curl -s --max-time 25 -b "$CJ" "$API/clients")"
fi

# 以服务端记录为准回读 UUID 与 vless 链接（复用旧客户端时 UUID 用旧的）
CLI_INFO="$(printf '%s' "$CLI_LIST" | python3 -c '
import json, sys
def norm(x):
    while isinstance(x, str):
        try: x = json.loads(x)
        except Exception: return None
    return x
try: d = json.load(sys.stdin)
except Exception: d = {}
o = norm(d.get("obj"))
if isinstance(o, dict):
    cand = [x for x in o.values() if isinstance(x, list)]
    o = cand[0] if cand else [x for x in o.values() if isinstance(x, dict)]
items = [x for x in (o or []) if isinstance(x, dict)]
uuid = ""; link = ""
for c in items:
    if c.get("name") == sys.argv[1]:
        cfg = norm(c.get("config")) or {}
        vless = cfg.get("vless") or {}
        uuid = vless.get("uuid", "") or ""
        links = c.get("links") or ""
        if isinstance(links, list):
            links = " ".join(str(x) for x in links)
        for part in str(links).replace(",", " ").split():
            if part.startswith("vless://"):
                link = part; break
        break
print(uuid, link)
' "$SUB_ID" 2>/dev/null)"
CLI_UUID=""; VLESS_LINK=""
if [ -n "$CLI_INFO" ]; then
    read -r CLI_UUID VLESS_LINK <<< "$CLI_INFO"
fi
[ -n "$CLI_UUID" ] && UUID="$CLI_UUID"

# 以订阅内容为准校正 UUID：clients 列表是摘要视图（config/links 不全），
# 复用旧客户端时上面的回读会落空、UUID 会误用本次新生成值；
# 订阅由面板按存量记录生成，是最终真相。
SUB_RAW="$(curl -s --max-time 25 "http://127.0.0.1:${SUB_PORT}${SUB_PATH}${SUB_ID}" || true)"
SUB_TXT="$(printf '%s' "$SUB_RAW" | base64 -d 2>/dev/null || true)"
[ -n "$SUB_TXT" ] || SUB_TXT="$SUB_RAW"
SUB_UUID="$(printf '%s' "$SUB_TXT" | grep -o 'vless://[0-9a-fA-F-]\{36\}@' | head -1 | sed 's|^vless://||; s|@$||')"
if [ -n "$SUB_UUID" ]; then
    UUID="$SUB_UUID"
    log "已按订阅内容校正 UUID"
fi

# 复用客户端补写流量/到期：全量数据从 s-ui 数据库原样读出（列表是摘要视图，
# 不能拿来回写），只改 volume/expiry/autoReset/resetDays/nextReset 五项，
# 其余字段（config/计数/时间戳）逐字保留，经 API edit 落盘。
if [ "$VOL_BYTES" -gt 0 ] || [ "$EXP_EPOCH" -gt 0 ]; then
    CLI_EDIT="$(python3 - "$SUB_ID" "$VOL_BYTES" "$EXP_EPOCH" "$NEXT_RESET" <<'PYEOF2'
import sqlite3, glob, json, sys
name, vol, exp, nxt = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
try:
    db = glob.glob('/usr/local/s-ui/**/s-ui.db', recursive=True)[0]
    con = sqlite3.connect(db)
    cur = con.execute('select * from clients where name=?', (name,))
    cols = [d[0] for d in cur.description]
    row = cur.fetchone()
except Exception:
    row = None
if not row:
    print('NOROW')
else:
    r = dict(zip(cols, row))
    def jload(v, fallback):
        try:
            x = json.loads(v) if isinstance(v, str) else v
            while isinstance(x, str):
                x = json.loads(x)
            return x
        except Exception:
            return fallback
    new_vol = vol if vol > 0 else int(r.get('volume') or 0)
    new_exp = exp if exp > 0 else int(r.get('expiry') or 0)
    if new_vol == int(r.get('volume') or 0) and new_exp == int(r.get('expiry') or 0) \
            and (vol == 0 or (r.get('auto_reset') in (1, True) and int(r.get('reset_days') or 0) == 30)):
        print('SAME')
    else:
        out = {
            'id': r.get('id'), 'enable': bool(r.get('enable')), 'name': r.get('name'),
            'config': jload(r.get('config'), {}), 'inbounds': jload(r.get('inbounds'), []),
            'links': jload(r.get('links'), []),
            'volume': new_vol, 'expiry': new_exp,
            'down': int(r.get('down') or 0), 'up': int(r.get('up') or 0),
            'desc': r.get('desc') or '', 'group': r.get('group') or '',
            'remark': r.get('remark') or '',
            'createdAt': int(r.get('created_at') or 0), 'onlineAt': int(r.get('online_at') or 0),
            'delayStart': bool(r.get('delay_start')), 'totalUp': int(r.get('total_up') or 0),
            'totalDown': int(r.get('total_down') or 0),
        }
        if vol > 0:
            out['autoReset'] = True
            out['resetDays'] = 30
            out['nextReset'] = nxt
        else:
            out['autoReset'] = bool(r.get('auto_reset'))
            out['resetDays'] = int(r.get('reset_days') or 0)
            out['nextReset'] = int(r.get('next_reset') or 0)
        print(json.dumps(out))
PYEOF2
)"
    case "$CLI_EDIT" in
        SAME) log "客户端流量/到期已是本次填写的值" ;;
        NOROW) log "未找到客户端记录，流量/到期未写入（订阅卡暂不显示）" ;;
        "" ) log "客户端流量/到期写入准备失败，已跳过（节点本身不受影响）" ;;
        * )
            if api_post_ok save --data-urlencode object=clients --data-urlencode action=edit \
                --data-urlencode "data=$CLI_EDIT" >/dev/null; then
                log "已把流量/到期写入已有客户端（订阅卡更新后可见）"
            else
                log "客户端流量/到期写入失败（节点本身不受影响）"
            fi
            ;;
    esac
fi

# ── 7. 防火墙：放行 节点/订阅/SSH；面板端口不放行 ────────
log "配置防火墙"
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -qi 'status: active'; then
    ufw allow "$SSH_PORT"/tcp >/dev/null 2>&1 || true
    ufw allow "$NODE_PORT"/tcp >/dev/null 2>&1 || true
    ufw allow "$SUB_PORT"/tcp >/dev/null 2>&1 || true
    log "ufw 已放行 $SSH_PORT/$NODE_PORT/$SUB_PORT，面板 $PANEL_PORT 未放行"
elif command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
    firewall-cmd --permanent --add-port="$NODE_PORT"/tcp >/dev/null 2>&1 || true
    firewall-cmd --permanent --add-port="$SUB_PORT"/tcp >/dev/null 2>&1 || true
    firewall-cmd --reload >/dev/null 2>&1 || true
    log "firewalld 已放行 $NODE_PORT/$SUB_PORT"
else
    # 裸 iptables：只补本节点需要的 ACCEPT 与面板 DROP，不动现有规则体系
    iptables -C INPUT -p tcp --dport "$NODE_PORT" -j ACCEPT 2>/dev/null || \
        iptables -I INPUT -p tcp --dport "$NODE_PORT" -j ACCEPT 2>/dev/null || true
    iptables -C INPUT -p tcp --dport "$SUB_PORT" -j ACCEPT 2>/dev/null || \
        iptables -I INPUT -p tcp --dport "$SUB_PORT" -j ACCEPT 2>/dev/null || true
    iptables -C INPUT -p tcp --dport "$PANEL_PORT" ! -i lo -j DROP 2>/dev/null || \
        iptables -I INPUT -p tcp --dport "$PANEL_PORT" ! -i lo -j DROP 2>/dev/null || true
    log "iptables 已补放行与面板拦截（尽力而为，以面板绑回环为主要防线）"
fi

# ── 8. 面板绑回环（公网不可达，管理走 SSH 隧道） ─────────
log "面板收回 127.0.0.1 绑定"
api_post_ok save --data-urlencode object=settings \
    --data-urlencode data='{"webListen":"127.0.0.1"}' >/dev/null
curl -s --max-time 25 -b "$CJ" -H "$HDR" -X POST "$API/restartApp" >/dev/null 2>&1 || true
sleep 2
ok=0
for _ in $(seq 1 30); do
    if /usr/local/s-ui/sui healthcheck >/dev/null 2>&1 || curl -s -o /dev/null --max-time 3 "http://127.0.0.1:${PANEL_PORT}${PANEL_PATH}"; then ok=1; break; fi
    sleep 1
done
if [ "$ok" != "1" ]; then
    systemctl restart s-ui >/dev/null 2>&1 || true
    for _ in $(seq 1 30); do
        if /usr/local/s-ui/sui healthcheck >/dev/null 2>&1 || curl -s -o /dev/null --max-time 3 "http://127.0.0.1:${PANEL_PORT}${PANEL_PATH}"; then ok=1; break; fi
        sleep 1
    done
fi
[ "$ok" = "1" ] || fail "面板收回回环后未恢复（可手动 systemctl restart s-ui）"
if command -v ss >/dev/null 2>&1; then
    ss -tln 2>/dev/null | grep ":$PANEL_PORT " | grep -q '127.0.0.1' \
        || log "提示：面板监听地址未确认是 127.0.0.1，请人工核查 ss -tln"
fi

# ── 9. 实测验证：重启核心后回环真连，过关才算部署成功 ────
log "重启核心使最终配置生效"
curl -s --max-time 25 -b "$CJ" -H "$HDR" -X POST "$API/restartSb" >/dev/null 2>&1 || true
for _ in $(seq 1 20); do
    ss -tln 2>/dev/null | grep -q ":$NODE_PORT " && break
    sleep 1
done
VERIFY="fail"
SB_DIR="/opt/nodeforge"; SB_BIN="$SB_DIR/sing-box"
mkdir -p "$SB_DIR" 2>/dev/null || true
if [ ! -x "$SB_BIN" ]; then
    log "下载实测工具（sing-box）"
    if curl -sL --max-time 180 -o "$SB_DIR/sb.tgz" "https://github.com/SagerNet/sing-box/releases/download/v1.14.4/sing-box-1.14.4-linux-amd64.tar.gz" && tar -xzf "$SB_DIR/sb.tgz" -C "$SB_DIR"; then
        cp "$SB_DIR/sing-box-1.14.4-linux-amd64/sing-box" "$SB_BIN" 2>/dev/null || true
        chmod +x "$SB_BIN" 2>/dev/null || true
    fi
fi
if [ -x "$SB_BIN" ]; then
    if python3 - "$INBOUND_TAG" "$SUB_ID" "$NODE_PORT" "$SB_DIR/verify-client.json" <<'PYEOF3'
import sqlite3, glob, json, sys
tag, cname, dport, out = sys.argv[1:5]
db = glob.glob('/usr/local/s-ui/**/s-ui.db', recursive=True)[0]
con = sqlite3.connect(db)
row = con.execute('select tls_id, options from inbounds where tag=?', (tag,)).fetchone()
tls_id, opts = row[0], json.loads(row[1] or '{}')
port = int(opts.get('listen_port') or dport)
cli = json.loads(con.execute('select client from tls where id=?', (tls_id,)).fetchone()[0])
ucfg = json.loads(con.execute('select config from clients where name=?', (cname,)).fetchone()[0])
cfg = {
  "log": {"level": "error"},
  "inbounds": [{"type": "mixed", "tag": "vin", "listen": "127.0.0.1", "listen_port": 18090}],
  "outbounds": [{
    "type": "vless", "tag": "vout", "server": "127.0.0.1", "server_port": port,
    "uuid": ucfg['vless']['uuid'], "flow": "xtls-rprx-vision",
    "tls": {"enabled": True, "server_name": cli.get('server_name', 'www.microsoft.com'),
            "utls": {"enabled": True, "fingerprint": "chrome"},
            "reality": {"enabled": True, "public_key": cli['reality']['public_key'], "short_id": cli['reality'].get('short_id', '')}}
  }]
}
open(out, 'w').write(json.dumps(cfg))
print('verify client ready, port', port)
PYEOF3
    then
        "$SB_BIN" run -c "$SB_DIR/verify-client.json" > "$SB_DIR/verify.log" 2>&1 &
        VPID=$!
        sleep 2
        VCODE="$(curl -s --max-time 12 -x socks5h://127.0.0.1:18090 -o /dev/null -w '%{http_code}' https://www.gstatic.com/generate_204 2>/dev/null || echo 000)"
        kill "$VPID" 2>/dev/null || true
        if [ "$VCODE" = "204" ]; then
            VERIFY="ok"
            log "实测验证通过（回环真连出网 204）"
        else
            log "实测验证未通过（回环返回 $VCODE），部署数据已保留待查"
        fi
    else
        log "实测配置生成失败，跳过验证"
    fi
else
    log "实测工具不可用，跳过验证（部署本身已完成）"
fi

# ── 9.5 订阅地址校验：订阅里节点的 host 必须是公网地址 ────
SUB_HOST_OK="fail"
SUB_NOW="$(curl -s --max-time 25 "http://127.0.0.1:${SUB_PORT}${SUB_PATH}${SUB_ID}" 2>/dev/null || true)"
SUB_NOW_TXT="$(printf '%s' "$SUB_NOW" | base64 -d 2>/dev/null || true)"
[ -n "$SUB_NOW_TXT" ] || SUB_NOW_TXT="$SUB_NOW"
LINK_HOST="$(printf '%s' "$SUB_NOW_TXT" | grep -o 'vless://[^@]*@[^:?#]*' | head -1 | sed 's/.*@//')"
if [ -n "$PUBLIC_HOST" ] && [ "$LINK_HOST" = "$PUBLIC_HOST" ]; then
    SUB_HOST_OK="ok"
    log "订阅地址校验通过（节点地址 $LINK_HOST）"
else
    log "订阅地址校验未通过（订阅里是 ${LINK_HOST:-空}，应为 ${PUBLIC_HOST:-未知}）——订阅导入会连不上，请用包生成的 YAML"
fi
SUB_INFO="$(curl -s -D - -o /dev/null --max-time 25 "http://127.0.0.1:${SUB_PORT}${SUB_PATH}${SUB_ID}" 2>/dev/null | grep -i '^subscription-userinfo:' | head -1 | tr -d '\r' | sed 's/^[^:]*: *//')"
[ -n "$SUB_INFO" ] && log "订阅流量信息：$SUB_INFO"

# ── 10. 结果 ───────────────────────────────────────────────
python3 - "$UUID" "$PUB" "$SID" "$SUB_ID" "$NODE_PORT" "$PANEL_PORT" "$PANEL_PATH" "$SUB_PORT" "$SUB_PATH" "$SNI" "$VLESS_LINK" "$SUI_VER" "$VERIFY" "$SUB_HOST_OK" "$SUB_INFO" <<'PYEOF'
import json, sys
(uuid, pub, sid, sub_id, node_port, panel_port, panel_path,
 sub_port, sub_path, sni, vless, ver, verify, sub_host_ok, sub_info) = sys.argv[1:16]
print("NF_RESULT " + json.dumps({
    "uuid": uuid, "publicKey": pub, "shortId": sid, "subId": sub_id,
    "nodePort": int(node_port), "panelPort": int(panel_port),
    "panelPath": panel_path, "subPort": int(sub_port), "subPath": sub_path,
    "sni": sni, "vlessLink": vless, "suiVersion": ver,
    "verified": verify == "ok", "subHostOk": sub_host_ok == "ok",
    "subInfo": sub_info,
}))
PYEOF
log "部署完成"
