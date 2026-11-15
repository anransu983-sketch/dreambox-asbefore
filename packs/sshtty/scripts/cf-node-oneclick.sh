#!/bin/bash
# Zouter 香港 155.117.*.* 一键 CF 自动导出节点（纯节点版，不做反代）
# 一条命令做完：CF 橙云 DNS（只 node-hk）+ SSL 严格 + 强制 HTTPS
#               + nginx + Let's Encrypt 证书
#               + 节点：Xray VLESS+WS 走 CF、443（子域名 node-hk），
#                 跑完打印节点订阅（Clash YAML，供梦盒一键导入）和 vless 链接
#               + 朋友节点：单独 UUID，只走 CF（不含直连 v6，不暴露源站 IP），
#                 每月 20GB 限额，1 号清零，超了自动停用，你的节点不受影响
# 用法：bash cf-node-oneclick.sh
# 节点说明：CF 只代理 80/443，直连回国又烂，所以节点只能走 VLESS+WS 经 CF；
#   电话 -> CF 边缘 -> 香港机 nginx 443（node-hk 的 /vless 去本机 Xray）。
#   UUID 与订阅令牌首次生成后落盘保存，重跑脚本不会换，梦盒里已导入的订阅不用重导。
#   朋友 UUID/令牌同样落盘保存（/var/www/node-sub/.friend-uuid / .friend-token）。
#   限额靠 Xray 自带 stats API 按用户（email）计数，看守脚本每 5 分钟累计一次，
#   Xray 重启会清零计数，所以累计数存在本机 /var/lib/xray-friend-quota/state.json。
# 前提：本机 /root/.cf.ini 已放好 Cloudflare API token（600 权限），格式：
#   dns_cloudflare_api_token = <token>
#   注意：纯节点版不需要 Cache Rules 权限，token 只要
#   Zone:DNS Edit + Zone:Zone Settings Edit，
#   区域只选 suanran9527.com，替换 /root/.cf.ini 里的 token 再跑。
#   token 只存在本机文件里，脚本不会打印它。
set -e
# 不开 set -x，避免 token 进日志

NODE_DOMAIN="node-hk.suanran9527.com"
WS_PATH="/vless"
XRAY_PORT=10086
XRAY_API_PORT=10085
ZONE="suanran9527.com"
ACME_EMAIL="iloveyou@suanran9527.com"
CF_INI="/root/.cf.ini"
CF_API="https://api.cloudflare.com/client/v4"
MANUAL=""
# 订阅流量头（让梦盒配置卡显示流量/日期，双向流量：上下行合计）
# 总流量 2048GB（Zouter 后台 2026-11-16 04:42：已用 19.91GB，剩余 2028GB，重置 2026-12-15 21:49:26）
SUB_TOTAL_BYTES=2199023255552
SUB_EXPIRE=1797342566
SUB_USED_SNAPSHOT=21378199715
# 朋友节点限额：每月 20 GiB（上下行合计），1 号清零
FRIEND_LIMIT_BYTES=21474836480
FRIEND_SUB_DIR="/var/www/node-sub-friend"
QUOTA_DIR="/var/lib/xray-friend-quota"
QUOTA_STATE="$QUOTA_DIR/state.json"

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

# CF DNS AAAA 记录 upsert：$1=子域名全名，橙云指向本机 IPv6（已存在就更新）
cf_dns_upsert_aaaa() {
  local name="$1" rec_json rec_id rec_body resp
  rec_json=$(cf_get "/zones/$ZONE_ID/dns_records?type=AAAA&name=$name&per_page=1")
  rec_id=$(echo "$rec_json" | python3 -c 'import sys,json; r=json.load(sys.stdin).get("result") or []; print(r[0]["id"] if r else "")' 2>/dev/null)
  rec_body=$(python3 -c 'import json,sys; print(json.dumps({"type":"AAAA","name":sys.argv[1],"content":sys.argv[2],"proxied":True,"ttl":1}))' "$name" "$VPS_IPv6")
  if [ -n "$rec_id" ]; then
    resp=$(cf_put "/zones/$ZONE_ID/dns_records/$rec_id" "$rec_body")
    cf_ok "$resp" && echo "DNS 记录 $name 已更新为橙云指向本机 IPv6。" || { echo "DNS 更新失败（$name）：$resp"; exit 1; }
  else
    resp=$(cf_post "/zones/$ZONE_ID/dns_records" "$rec_body")
    cf_ok "$resp" && echo "DNS 记录 $name 已新建（橙云，IPv6）。" || { echo "DNS 新建失败（$name）：$resp"; exit 1; }
  fi
}

echo ">>> [1/6] 本机 IP 与 token 校验"
VPS_IP=$(curl -sS -m 10 https://api.ipify.org || curl -sS -m 10 https://ifconfig.me)
echo "本机公网 IP: $VPS_IP"
VPS_IPv6=$(curl -sS -m 10 https://api64.ipify.org || echo "")
if [ -n "$VPS_IPv6" ]; then echo "本机公网 IPv6: $VPS_IPv6"; else echo "没取到本机 IPv6，这次只配 IPv4。"; fi
if ! cf_ok "$(cf_get /user/tokens/verify)"; then
  echo "token 校验失败（/user/tokens/verify 没通过），请检查 /root/.cf.ini 里的 token 是否有效。"
  exit 1
fi
echo "token 有效。"

echo ">>> [2/6] 取 Zone ID（$ZONE）"
ZONE_ID=$(cf_get "/zones?name=$ZONE&per_page=1" | python3 -c 'import sys,json; r=json.load(sys.stdin).get("result") or []; print(r[0]["id"] if r else "")' 2>/dev/null)
if [ -z "$ZONE_ID" ]; then echo "取不到 Zone ID，请确认 token 有该区域权限。"; exit 1; fi
echo "Zone ID: $ZONE_ID"

echo ">>> [3/6] CF DNS：$NODE_DOMAIN 的 A 记录 -> $VPS_IP，橙云（proxied=true）"
cf_dns_upsert "$NODE_DOMAIN"
if [ -n "$VPS_IPv6" ]; then
  echo ">>> [3/6] CF DNS：$NODE_DOMAIN 的 AAAA 记录 -> $VPS_IPv6，橙云（让 CF 回源走 IPv6，Zouter 香港 v4 烂、v6 好）"
  cf_dns_upsert_aaaa "$NODE_DOMAIN"
fi

echo ">>> [4/6] CF 设置：SSL 严格 + 强制 HTTPS"
if cf_ok "$(cf_patch "/zones/$ZONE_ID/settings/ssl" '{"value":"strict"}')"; then echo "SSL 已设为 完全（严格）。"; else echo "SSL 设置失败（多半是 token 缺 Zone Settings 权限）。"; MANUAL="$MANUAL
- CF 面板 SSL/TLS -> 概述 -> 设为 完全（严格）"; fi
if cf_ok "$(cf_patch "/zones/$ZONE_ID/settings/always_use_https" '{"value":"on"}')"; then echo "强制 HTTPS 已开。"; else echo "强制 HTTPS 设置失败，跳过（不影响节点）。"; fi

echo ">>> [5/6] 安装 nginx + certbot，申请证书（DNS-01，只 node-hk）"
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nginx certbot python3-certbot-dns-cloudflare curl unzip cron

for P in 80 443; do
  if ss -tlnp 2>/dev/null | grep ":${P} " | grep -qv nginx; then echo "错误：端口 $P 已被 nginx 以外的程序占用。"; exit 1; fi
done

certbot certonly --dns-cloudflare --dns-cloudflare-credentials "$CF_INI" \
  --non-interactive --agree-tos --email "$ACME_EMAIL" -d "$NODE_DOMAIN"

echo ">>> [6/6] 节点：Xray VLESS+WS 走 CF，443"
# UUID 与订阅令牌：已存在就复用，重跑脚本不换（已导入的订阅不用重导）
mkdir -p /var/www/node-sub "$FRIEND_SUB_DIR" "$QUOTA_DIR"
NODE_UUID=""
if [ -f /usr/local/etc/xray/config.json ]; then
  NODE_UUID=$(python3 -c 'import json; print(json.load(open("/usr/local/etc/xray/config.json"))["inbounds"][0]["settings"]["clients"][0]["id"])' 2>/dev/null || echo "")
fi
[ -z "$NODE_UUID" ] && NODE_UUID=$(cat /proc/sys/kernel/random/uuid)
echo "$NODE_UUID" > /var/www/node-sub/.owner-uuid
chmod 600 /var/www/node-sub/.owner-uuid
if [ -f /var/www/node-sub/.token ]; then
  SUB_TOKEN=$(cat /var/www/node-sub/.token)
else
  SUB_TOKEN=$(head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n')
  echo "$SUB_TOKEN" > /var/www/node-sub/.token
  chmod 600 /var/www/node-sub/.token
fi
NODE_SUB_URL="https://${NODE_DOMAIN}/sub/${SUB_TOKEN}.yaml"
NODE_VLESS="vless://${NODE_UUID}@${NODE_DOMAIN}:443?encryption=none&security=tls&sni=${NODE_DOMAIN}&type=ws&path=%2Fvless#HK-CF-VLESS"
# 朋友 UUID 与订阅令牌：同样落盘复用（只第一次生成）
FRIEND_UUID=""
if [ -f /var/www/node-sub/.friend-uuid ]; then
  FRIEND_UUID=$(cat /var/www/node-sub/.friend-uuid)
fi
if [ -z "$FRIEND_UUID" ] && [ -f /usr/local/etc/xray/config.json ]; then
  FRIEND_UUID=$(python3 -c 'import json; cs=json.load(open("/usr/local/etc/xray/config.json"))["inbounds"][0]["settings"]["clients"]; print([c["id"] for c in cs if c.get("email")=="friend"][0])' 2>/dev/null || echo "")
fi
[ -z "$FRIEND_UUID" ] && FRIEND_UUID=$(cat /proc/sys/kernel/random/uuid)
echo "$FRIEND_UUID" > /var/www/node-sub/.friend-uuid
chmod 600 /var/www/node-sub/.friend-uuid
if [ -f /var/www/node-sub/.friend-token ]; then
  FRIEND_TOKEN=$(cat /var/www/node-sub/.friend-token)
else
  FRIEND_TOKEN=$(head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n')
  echo "$FRIEND_TOKEN" > /var/www/node-sub/.friend-token
  chmod 600 /var/www/node-sub/.friend-token
fi
FRIEND_SUB_URL="https://${NODE_DOMAIN}/friend-sub/${FRIEND_TOKEN}.yaml"
FRIEND_VLESS="vless://${FRIEND_UUID}@${NODE_DOMAIN}:443?encryption=none&security=tls&sni=${NODE_DOMAIN}&type=ws&path=%2Fvless#HK-CF-Friend"
# 本月朋友是否处于停用（上月超 20GB）：停用期内重跑脚本也不恢复，等看守脚本 1 号自动恢复
FRIEND_ENABLED=1
if [ -f "$QUOTA_STATE" ]; then
  FRIEND_ENABLED=$(python3 -c 'import json,sys,time; s=json.load(open(sys.argv[1])); print(0 if (s.get("disabled") and s.get("month")==time.strftime("%Y-%m")) else 1)' "$QUOTA_STATE" 2>/dev/null || echo 1)
fi

# 装 Xray（官方安装脚本，systemd 服务 xray）
if ! command -v /usr/local/bin/xray >/dev/null 2>&1; then
  bash -c "$(curl -L https://github.com/XTLS/Xray-install/raw/main/install-release.sh)" install
fi
mkdir -p /usr/local/etc/xray

# Xray 配置下发器：写 config.json（主人 + 朋友双用户 + stats API），
# 配置没变就不重启；先 xray run -test 校验，失败则恢复备份、不动运行中的 Xray
cat > /usr/local/bin/xray-apply-clients.py <<'PYAPPLYEOF'
#!/usr/bin/env python3
"""Write Xray config: owner + optional friend VLESS clients, stats API on.

Usage:
  xray-apply-clients.py --port 10086 --ws-path /vless \
      --owner-uuid UUID [--friend-uuid UUID] [--no-friend]
UUIDs omitted on the command line are read from /var/www/node-sub/
.owner-uuid / .friend-uuid, falling back to the current config.json.
Exits non-zero without touching the running Xray if the new config
fails `xray run -test`.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys

CFG = "/usr/local/etc/xray/config.json"
XRAY = "/usr/local/bin/xray"
OWNER_UUID_FILE = "/var/www/node-sub/.owner-uuid"
FRIEND_UUID_FILE = "/var/www/node-sub/.friend-uuid"


def read_uuid(path):
    try:
        with open(path) as f:
            v = f.read().strip()
            return v or None
    except OSError:
        return None


def uuid_from_config(email=None):
    try:
        cfg = json.load(open(CFG))
        clients = cfg["inbounds"][0]["settings"]["clients"]
        if email is None:
            return clients[0]["id"]
        for c in clients:
            if c.get("email") == email:
                return c["id"]
    except Exception:
        return None
    return None


def build_config(port, ws_path, owner_uuid, friend_uuid):
    clients = [{"id": owner_uuid, "email": "owner", "flow": ""}]
    if friend_uuid:
        clients.append({"id": friend_uuid, "email": "friend", "flow": ""})
    return {
        "log": {"loglevel": "warning"},
        "stats": {},
        "api": {"tag": "api", "services": ["StatsService"]},
        "policy": {"levels": {"0": {"statsUserUplink": True, "statsUserDownlink": True}}},
        "inbounds": [
            {
                "port": port,
                "listen": "127.0.0.1",
                "protocol": "vless",
                "settings": {"clients": clients, "decryption": "none"},
                "streamSettings": {"network": "ws", "wsSettings": {"path": ws_path}},
            },
            {
                "tag": "api",
                "port": 10085,
                "listen": "127.0.0.1",
                "protocol": "dokodemo-door",
                "settings": {"address": "127.0.0.1"},
            },
        ],
        "outbounds": [
            {"protocol": "freedom", "settings": {}},
            {"tag": "api", "protocol": "freedom", "settings": {}},
        ],
        "routing": {"rules": [{"type": "field", "inboundTag": ["api"], "outboundTag": "api"}]},
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=10086)
    ap.add_argument("--ws-path", default="/vless")
    ap.add_argument("--owner-uuid")
    ap.add_argument("--friend-uuid")
    ap.add_argument("--no-friend", action="store_true")
    args = ap.parse_args()

    owner = args.owner_uuid or read_uuid(OWNER_UUID_FILE) or uuid_from_config()
    if not owner:
        print("xray-apply-clients: no owner UUID found", file=sys.stderr)
        return 2
    friend = None
    if not args.no_friend:
        friend = args.friend_uuid or read_uuid(FRIEND_UUID_FILE) or uuid_from_config("friend")

    new_text = json.dumps(build_config(args.port, args.ws_path, owner, friend), indent=2) + "\n"
    try:
        with open(CFG) as f:
            old_text = f.read()
    except OSError:
        old_text = None
    if old_text == new_text:
        print("xray config unchanged (friend: %s)" % ("on" if friend else "off"))
        return 0

    backup = CFG + ".bak"
    if old_text is not None:
        shutil.copyfile(CFG, backup)
    with open(CFG, "w") as f:
        f.write(new_text)
    test = subprocess.run([XRAY, "run", "-test", "-config", CFG],
                          capture_output=True, text=True)
    if test.returncode != 0:
        print("xray config test failed, restoring backup", file=sys.stderr)
        print(test.stderr[-1000:], file=sys.stderr)
        if old_text is not None:
            shutil.copyfile(backup, CFG)
        else:
            os.remove(CFG)
        return 1
    subprocess.run(["systemctl", "enable", "--now", "xray"], capture_output=True)
    restart = subprocess.run(["systemctl", "restart", "xray"], capture_output=True, text=True)
    if restart.returncode != 0:
        print("xray restart failed, restoring backup", file=sys.stderr)
        if old_text is not None:
            shutil.copyfile(backup, CFG)
            subprocess.run(["systemctl", "restart", "xray"], capture_output=True)
        return 1
    import time
    time.sleep(2)
    active = subprocess.run(["systemctl", "is-active", "--quiet", "xray"])
    if active.returncode != 0:
        print("xray not active after restart, restoring backup", file=sys.stderr)
        if old_text is not None:
            shutil.copyfile(backup, CFG)
            subprocess.run(["systemctl", "restart", "xray"], capture_output=True)
        return 1
    print("xray config applied (friend: %s)" % ("on" if friend else "off"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
PYAPPLYEOF
chmod 755 /usr/local/bin/xray-apply-clients.py

# 朋友限额看守：每 5 分钟查一次 Xray stats API，累计朋友上下行，
# 到 20GB 摘掉朋友用户并删朋友订阅；每月 1 号清零并自动恢复
cat > /usr/local/bin/xray-friend-quota.py <<'PYQUOTAEOF'
#!/usr/bin/env python3
"""Enforce a monthly 20 GiB cap on the friend VLESS user.

- Accumulates user>>>friend>>>traffic uplink+downlink into state.json
  (Xray counters reset on restart, so deltas are accumulated here).
- At the cap: removes the friend client (via xray-apply-clients.py),
  deletes the friend subscription file, marks disabled.
- On month rollover: resets the counter and re-enables the friend.
- Keeps the friend subscription-userinfo header roughly live by
  rewriting the nginx snippet when displayed usage moved >= 100 MiB.
Run from cron every 5 minutes. Never touches the owner client.
"""
import calendar
import json
import os
import subprocess
import sys
import time

LIMIT = 20 * 1024 ** 3
SNIPPET_MIN_DELTA = 100 * 1024 * 1024
XRAY = "/usr/local/bin/xray"
APPLY = "/usr/local/bin/xray-apply-clients.py"
STATE = "/var/lib/xray-friend-quota/state.json"
FRIEND_UUID_FILE = "/var/www/node-sub/.friend-uuid"
FRIEND_TOKEN_FILE = "/var/www/node-sub/.friend-token"
TEMPLATE = "/usr/local/etc/xray/friend-sub-template.yaml"
FRIEND_SUB_DIR = "/var/www/node-sub-friend"
SNIPPET = "/etc/nginx/snippets/friend-userinfo.conf"


def log(msg):
    print("%s [friend-quota] %s" % (time.strftime("%Y-%m-%d %H:%M:%S"), msg), flush=True)


def load_state():
    try:
        with open(STATE) as f:
            s = json.load(f)
            return {"month": s.get("month", ""), "used": int(s.get("used", 0)),
                    "last_up": int(s.get("last_up", 0)), "last_down": int(s.get("last_down", 0)),
                    "disabled": bool(s.get("disabled", False)),
                    "last_displayed": int(s.get("last_displayed", -1))}
    except Exception:
        return {"month": "", "used": 0, "last_up": 0, "last_down": 0,
                "disabled": False, "last_displayed": -1}


def save_state(s):
    os.makedirs(os.path.dirname(STATE), exist_ok=True)
    tmp = STATE + ".tmp"
    with open(tmp, "w") as f:
        json.dump(s, f)
    os.replace(tmp, STATE)


def read_file(path):
    try:
        with open(path) as f:
            return f.read().strip() or None
    except OSError:
        return None


def month_end_epoch():
    now = time.localtime()
    last_day = calendar.monthrange(now.tm_year, now.tm_mon)[1]
    return int(time.mktime((now.tm_year, now.tm_mon, last_day, 23, 59, 59, 0, 0, -1)))


def query_stats():
    """Return (uplink, downlink) bytes for user friend, or None on error."""
    try:
        p = subprocess.run(
            [XRAY, "api", "statsquery", "--server=127.0.0.1:10085",
             "-pattern", "user>>>friend>>>traffic"],
            capture_output=True, text=True, timeout=15)
    except Exception as e:
        log("statsquery failed to run: %s" % e)
        return None
    out = p.stdout or ""
    if "not found" in out:
        return (0, 0)
    try:
        data = json.loads(out)
    except Exception:
        log("statsquery unparsable output: %r (rc=%s)" % (out[:200], p.returncode))
        return None
    up = down = 0
    for item in data.get("stat", []):
        name = item.get("name", "")
        try:
            val = int(item.get("value", 0))
        except (TypeError, ValueError):
            continue
        if name.endswith(">>>uplink"):
            up = val
        elif name.endswith(">>>downlink"):
            down = val
    return (up, down)


def render_friend_sub():
    uuid = read_file(FRIEND_UUID_FILE)
    token = read_file(FRIEND_TOKEN_FILE)
    if not uuid or not token:
        log("cannot render friend sub: missing uuid/token file")
        return False
    try:
        with open(TEMPLATE) as f:
            tpl = f.read()
    except OSError as e:
        log("cannot read friend template: %s" % e)
        return False
    os.makedirs(FRIEND_SUB_DIR, exist_ok=True)
    path = os.path.join(FRIEND_SUB_DIR, token + ".yaml")
    with open(path, "w") as f:
        f.write(tpl.replace("__FRIEND_UUID__", uuid))
    os.chmod(path, 0o644)
    return True


def update_snippet(state):
    expire = month_end_epoch()
    line = ('add_header subscription-userinfo "upload=0; download=%d; '
            'total=%d; expire=%d";\n' % (state["used"], LIMIT, expire))
    try:
        with open(SNIPPET) as f:
            if f.read() == line:
                return
    except OSError:
        pass
    if state["last_displayed"] >= 0 and not state["disabled"] and \
            abs(state["used"] - state["last_displayed"]) < SNIPPET_MIN_DELTA:
        return
    os.makedirs(os.path.dirname(SNIPPET), exist_ok=True)
    with open(SNIPPET, "w") as f:
        f.write(line)
    state["last_displayed"] = state["used"]
    r = subprocess.run(["nginx", "-t"], capture_output=True, text=True)
    if r.returncode != 0:
        log("nginx -t failed, snippet not reloaded: %s" % r.stderr[:200])
        return
    subprocess.run(["systemctl", "reload", "nginx"], capture_output=True)
    log("friend card updated: used=%d bytes" % state["used"])


def set_friend_enabled(enabled):
    cmd = ["python3", APPLY, "--no-friend"] if not enabled else ["python3", APPLY]
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    log("apply-clients (%s): rc=%s %s %s" % (
        "off" if not enabled else "on", p.returncode,
        p.stdout.strip()[:200], p.stderr.strip()[:200]))
    return p.returncode == 0


def main():
    now_month = time.strftime("%Y-%m")
    state = load_state()
    if state["month"] != now_month:
        log("month rollover %s -> %s: reset counter" % (state["month"] or "(none)", now_month))
        state = {"month": now_month, "used": 0, "last_up": 0, "last_down": 0,
                 "disabled": False, "last_displayed": -1}
        if set_friend_enabled(True):
            render_friend_sub()
        save_state(state)
        update_snippet(state)
    stats = query_stats()
    if stats is None:
        save_state(state)
        return 0
    up, down = stats
    if up < state["last_up"] or down < state["last_down"]:
        delta = up + down  # Xray restarted, counters reset
    else:
        delta = (up - state["last_up"]) + (down - state["last_down"])
    if delta:
        state["used"] += delta
    state["last_up"] = up
    state["last_down"] = down
    if state["used"] >= LIMIT and not state["disabled"]:
        log("friend over cap: used=%d >= %d, disabling" % (state["used"], LIMIT))
        if set_friend_enabled(False):
            token = read_file(FRIEND_TOKEN_FILE)
            if token:
                try:
                    os.remove(os.path.join(FRIEND_SUB_DIR, token + ".yaml"))
                except OSError:
                    pass
            state["disabled"] = True
    save_state(state)
    update_snippet(state)
    save_state(state)
    return 0


if __name__ == "__main__":
    sys.exit(main())
PYQUOTAEOF
chmod 755 /usr/local/bin/xray-friend-quota.py

# 朋友订阅模板（只含 CF 节点，不含直连 v6；__FRIEND_UUID__ 由脚本/看守渲染时替换）
cat > /usr/local/etc/xray/friend-sub-template.yaml <<'FRIENDTMPL'
mixed-port: 7890
mode: rule
log-level: info
dns:
  enable: true
  enhanced-mode: fake-ip
  fake-ip-range: 198.18.0.1/16
  nameserver:
  - https://1.1.1.1/dns-query
  - https://8.8.8.8/dns-query
proxies:
- name: 🇭🇰 香港CF
  type: vless
  server: node-hk.suanran9527.com
  port: 443
  uuid: __FRIEND_UUID__
  udp: true
  tls: true
  servername: node-hk.suanran9527.com
  client-fingerprint: chrome
  network: ws
  ws-opts:
    path: /vless
    headers:
      Host: node-hk.suanran9527.com
proxy-groups:
- name: Proxy
  type: select
  proxies:
  - Auto
  - 🇭🇰 香港CF
- name: Auto
  type: url-test
  url: http://www.gstatic.com/generate_204
  interval: 300
  tolerance: 50
  proxies:
  - 🇭🇰 香港CF
rules:
- GEOIP,Private,DIRECT
- GEOIP,CN,DIRECT
- MATCH,Proxy
FRIENDTMPL

# 下发 Xray 配置（主人 + 朋友双用户 + stats API；配置没变不重启）
if [ "$FRIEND_ENABLED" = "1" ]; then
  python3 /usr/local/bin/xray-apply-clients.py --port "$XRAY_PORT" --ws-path "$WS_PATH" --owner-uuid "$NODE_UUID" --friend-uuid "$FRIEND_UUID"
else
  echo "朋友节点本月已超 20GB，处于停用中，本次不恢复（1 号自动恢复）。"
  python3 /usr/local/bin/xray-apply-clients.py --port "$XRAY_PORT" --ws-path "$WS_PATH" --owner-uuid "$NODE_UUID" --no-friend
fi
systemctl is-active --quiet xray || { echo "Xray 没起来，退出。"; systemctl status xray --no-pager | head -20; exit 1; }
echo "Xray 已启动（VLESS+WS，127.0.0.1:${XRAY_PORT}${WS_PATH}，朋友用户：$([ "$FRIEND_ENABLED" = "1" ] && echo 已启用 || echo 已停用)）。"

# 朋友订阅文件（启用时生成，停用时删掉，订阅地址 404）
if [ "$FRIEND_ENABLED" = "1" ]; then
  sed "s/__FRIEND_UUID__/$FRIEND_UUID/" /usr/local/etc/xray/friend-sub-template.yaml > "/var/www/node-sub-friend/${FRIEND_TOKEN}.yaml"
  chmod 644 "/var/www/node-sub-friend/${FRIEND_TOKEN}.yaml"
else
  rm -f "/var/www/node-sub-friend/${FRIEND_TOKEN}.yaml"
fi

# 限额看守 cron（每 5 分钟；Xray 统计重启清零，累计数存在本机状态文件里）
systemctl enable --now cron >/dev/null 2>&1 || true
if ! crontab -l 2>/dev/null | grep -q "xray-friend-quota.py"; then
  (crontab -l 2>/dev/null; echo "*/5 * * * * /usr/bin/python3 /usr/local/bin/xray-friend-quota.py >>/var/log/xray-friend-quota.log 2>&1") | crontab -
  echo "限额看守 cron 已装（每 5 分钟）。"
fi

# 直连 v6 节点：取到本机 IPv6 才加进订阅（与 CF 节点同机同 UUID，只换连接目标为服务器 IPv6 直连 443，不经过 CF）
if [ -n "$VPS_IPv6" ]; then
  DIRECT_NODE=$(cat <<DNEOF
- name: 🇭🇰 香港直连v6
  type: vless
  server: ${VPS_IPv6}
  port: 443
  uuid: ${NODE_UUID}
  udp: true
  tls: true
  servername: ${NODE_DOMAIN}
  client-fingerprint: chrome
  network: ws
  ws-opts:
    path: ${WS_PATH}
    headers:
      Host: ${NODE_DOMAIN}
DNEOF
)
  DIRECT_GROUP="  - 🇭🇰 香港直连v6"
else
  DIRECT_NODE=""
  DIRECT_GROUP=""
fi
# 订阅 YAML：4837 v2 模板完整配置（6 组＋sniffer＋防 DNS 泄露＋STUN 拦截），节点参数由脚本变量填，梦盒 importSubscription 用 clash:// 深链导入
cat > "/var/www/node-sub/${SUB_TOKEN}.yaml" <<YAMLEOF
allow-lan: false
global-client-fingerprint: chrome
keep-alive-interval: 30
profile:
  store-selected: true
  store-fake-ip: true
sniffer:
  enable: true
  force-dns-mapping: true
  parse-pure-ip: true
  override-destination: true
  sniff:
    TLS:
      ports: [443, 8443]
    HTTP:
      ports: [80, 8080]
      override-destination: true
dns:
  default-nameserver:
  - 8.8.8.8
  - 1.1.1.1
  enable: true
  enhanced-mode: fake-ip
  fake-ip-filter:
  - '*.lan'
  - localhost
  - '*.local'
  fake-ip-range: 198.18.0.1/16
  fallback:
  - https://9.9.9.9/dns-query
  - https://149.112.112.112/dns-query
  fallback-filter:
    geoip: true
    geoip-code: CN
    ipcidr:
    - 240.0.0.0/4
  ipv6: false
  respect-rules: true
  proxy-server-nameserver:
  - https://doh.pub/dns-query
  direct-nameserver:
  - https://doh.pub/dns-query
  nameserver:
  - https://1.1.1.1/dns-query
  - https://8.8.8.8/dns-query
  nameserver-policy:
    '+.cn':
    - https://doh.pub/dns-query
external-controller: 127.0.0.1:9090
secret: '123456'
log-level: info
mixed-port: 7890
mode: rule
unified-delay: true
tcp-concurrent: true
proxies:
- name: 🇭🇰 香港CF
  type: vless
  server: ${NODE_DOMAIN}
  port: 443
  uuid: ${NODE_UUID}
  udp: true
  tls: true
  servername: ${NODE_DOMAIN}
  client-fingerprint: chrome
  network: ws
  ws-opts:
    path: ${WS_PATH}
    headers:
      Host: ${NODE_DOMAIN}
${DIRECT_NODE}
proxy-groups:
- name: Proxy
  type: select
  proxies:
  - Auto
  - 🇭🇰 香港CF
${DIRECT_GROUP}
- name: Auto
  type: url-test
  url: http://www.gstatic.com/generate_204
  interval: 300
  tolerance: 50
  proxies:
  - 🇭🇰 香港CF
${DIRECT_GROUP}
- name: 🎮 Steam
  type: select
  proxies:
  - Proxy
  - 🇭🇰 香港CF
${DIRECT_GROUP}
  - DIRECT
  - Auto
- name: 🎬 Emby
  type: select
  proxies:
  - Proxy
  - 🇭🇰 香港CF
${DIRECT_GROUP}
  - DIRECT
- name: 📺 YouTube
  type: select
  proxies:
  - Proxy
  - 🇭🇰 香港CF
${DIRECT_GROUP}
  - Auto
- name: ✈️ Telegram
  type: select
  proxies:
  - Proxy
  - 🇭🇰 香港CF
${DIRECT_GROUP}
  - Auto
- name: 🤖 AI 增强
  type: select
  proxies:
  - Proxy
  - 🇭🇰 香港CF
${DIRECT_GROUP}
  - Auto
  - DIRECT
- name: 🐟 漏网之鱼
  type: select
  proxies:
  - Proxy
  - 🇭🇰 香港CF
${DIRECT_GROUP}
  - DIRECT
rules:
- GEOIP,Private,DIRECT
- DOMAIN-SUFFIX,local,DIRECT
- IP-CIDR,192.168.0.0/16,DIRECT,no-resolve
- IP-CIDR,10.0.0.0/8,DIRECT,no-resolve
- IP-CIDR,127.0.0.0/8,DIRECT,no-resolve
- DOMAIN-KEYWORD,stun,REJECT
- DST-PORT,3478,REJECT
- DST-PORT,3479,REJECT
- DST-PORT,5349,REJECT
- DST-PORT,19302,REJECT
- DST-PORT,19305,REJECT
- DOMAIN-SUFFIX,openai.com,🤖 AI 增强
- DOMAIN-SUFFIX,chatgpt.com,🤖 AI 增强
- DOMAIN-SUFFIX,anthropic.com,🤖 AI 增强
- DOMAIN-SUFFIX,claude.ai,🤖 AI 增强
- DOMAIN-SUFFIX,gemini.google.com,🤖 AI 增强
- DOMAIN-SUFFIX,t.me,✈️ Telegram
- DOMAIN-SUFFIX,tdesktop.com,✈️ Telegram
- DOMAIN-SUFFIX,telegra.ph,✈️ Telegram
- DOMAIN-SUFFIX,telegram.me,✈️ Telegram
- DOMAIN-SUFFIX,telegram.org,✈️ Telegram
- IP-CIDR,91.108.4.0/22,✈️ Telegram,no-resolve
- IP-CIDR,91.108.8.0/22,✈️ Telegram,no-resolve
- IP-CIDR,91.108.12.0/22,✈️ Telegram,no-resolve
- IP-CIDR,91.108.16.0/22,✈️ Telegram,no-resolve
- IP-CIDR,91.108.20.0/22,✈️ Telegram,no-resolve
- IP-CIDR,91.108.56.0/22,✈️ Telegram,no-resolve
- IP-CIDR,149.154.160.0/20,✈️ Telegram,no-resolve
- DOMAIN-SUFFIX,youtube.com,📺 YouTube
- DOMAIN-SUFFIX,youtu.be,📺 YouTube
- DOMAIN-SUFFIX,googlevideo.com,📺 YouTube
- DOMAIN-SUFFIX,ytimg.com,📺 YouTube
- DOMAIN-SUFFIX,steampowered.com,🎮 Steam
- DOMAIN-SUFFIX,steamcommunity.com,🎮 Steam
- DOMAIN-SUFFIX,steamgames.com,🎮 Steam
- DOMAIN-SUFFIX,steamusercontent.com,🎮 Steam
- DOMAIN-SUFFIX,steamcontent.com,🎮 Steam
- DOMAIN-SUFFIX,steamstatic.com,🎮 Steam
- DOMAIN-SUFFIX,steamcdn-a.akamaihd.net,🎮 Steam
- DOMAIN-KEYWORD,emby,🎬 Emby
- GEOIP,CN,DIRECT
- MATCH,🐟 漏网之鱼
tun:
  auto-detect-interface: true
  auto-route: true
  dns-hijack:
  - any:53
  enable: true
  stack: system
YAMLEOF
chmod 644 "/var/www/node-sub/${SUB_TOKEN}.yaml"

# 订阅流量头：已用量取本机网卡累计收发（双向合计），低于后台快照时用快照兜底（防重启后计数清零显少）
SUB_IF=$(ip route show default 2>/dev/null | awk '{print $5}' | head -1)
SUB_RX=0; SUB_TX=0
if [ -n "$SUB_IF" ] && [ -r "/sys/class/net/$SUB_IF/statistics/rx_bytes" ]; then
  SUB_RX=$(cat "/sys/class/net/$SUB_IF/statistics/rx_bytes" 2>/dev/null || echo 0)
  SUB_TX=$(cat "/sys/class/net/$SUB_IF/statistics/tx_bytes" 2>/dev/null || echo 0)
fi
SUB_USED=$((SUB_RX + SUB_TX))
if [ "$SUB_USED" -lt "$SUB_USED_SNAPSHOT" ]; then SUB_USED=$SUB_USED_SNAPSHOT; fi
SUB_DOWNLOAD=$SUB_USED
# 朋友订阅流量头：本月已用取看守状态文件（当月），过期日为下月 1 号
mkdir -p /etc/nginx/snippets
FRIEND_USED=0
if [ -f "$QUOTA_STATE" ]; then
  FRIEND_USED=$(python3 -c 'import json,sys,time; s=json.load(open(sys.argv[1])); print(s.get("used",0) if s.get("month")==time.strftime("%Y-%m") else 0)' "$QUOTA_STATE" 2>/dev/null || echo 0)
fi
FRIEND_EXPIRE=$(date -d "$(date +%Y-%m-01) +1 month" +%s)
printf 'add_header subscription-userinfo "upload=0; download=%s; total=%s; expire=%s";\n' "$FRIEND_USED" "$FRIEND_LIMIT_BYTES" "$FRIEND_EXPIRE" > /etc/nginx/snippets/friend-userinfo.conf
# nginx：node-hk 域名——/vless 转 Xray，/sub/ 出主人订阅文件（带流量头），/friend-sub/ 出朋友订阅文件（带朋友流量头），其余 404
cat > "/etc/nginx/sites-available/${NODE_DOMAIN}" <<NGINXEOF
server {
    listen 80;
    listen [::]:80;
    server_name ${NODE_DOMAIN};
    return 301 https://\$host\$request_uri;
}
server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name ${NODE_DOMAIN};

    ssl_certificate /etc/letsencrypt/live/${NODE_DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${NODE_DOMAIN}/privkey.pem;

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
        add_header subscription-userinfo "upload=0; download=${SUB_DOWNLOAD}; total=${SUB_TOTAL_BYTES}; expire=${SUB_EXPIRE}";
    }

    location /friend-sub/ {
        alias /var/www/node-sub-friend/;
        default_type text/plain;
        include /etc/nginx/snippets/friend-userinfo.conf;
    }

    location / {
        return 404;
    }
}
NGINXEOF
ln -sf "/etc/nginx/sites-available/${NODE_DOMAIN}" "/etc/nginx/sites-enabled/${NODE_DOMAIN}"
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
ufw allow 80/tcp >/dev/null 2>&1 || true
ufw allow 443/tcp >/dev/null 2>&1 || true

# 验证订阅走 CF 能拉到
sleep 3
SUB_CODE=$(curl -sk -o /dev/null -m 20 -w "%{http_code}" "$NODE_SUB_URL" || echo "000")
echo "节点订阅走 CF 拉取返回 HTTP $SUB_CODE"
[ "$SUB_CODE" != "200" ] && echo "警告：订阅没返回 200，可能是橙云刚生效（等几分钟再测）。"
if [ "$FRIEND_ENABLED" = "1" ]; then
  FRIEND_CODE=$(curl -sk -o /dev/null -m 20 -w "%{http_code}" "$FRIEND_SUB_URL" || echo "000")
  echo "朋友订阅走 CF 拉取返回 HTTP $FRIEND_CODE"
  [ "$FRIEND_CODE" != "200" ] && echo "警告：朋友订阅没返回 200，可能是橙云刚生效（等几分钟再测）。"
fi

echo ""
echo "=================================================="
echo "完成！只出了节点，没有做反代。"
echo "节点订阅：$NODE_SUB_URL"
echo "节点链接：$NODE_VLESS"
echo "梦盒里点「导入到梦盒」走订阅导入（以后节点有变，更新订阅即可）；单条链接也可复制手动导入。"
echo "朋友订阅：$FRIEND_SUB_URL"
echo "朋友链接：$FRIEND_VLESS"
echo "朋友节点只走 CF（不含直连 v6，不暴露源站 IP），每月 20GB，1 号清零，超了自动停用；你的节点不受影响。"
echo "查朋友已用：cat $QUOTA_STATE；看守日志：tail /var/log/xray-friend-quota.log"
if [ -n "$MANUAL" ]; then
  echo ""
  echo "以下几项因 token 权限不足没自动改上，请去 CF 面板手动点一下：$MANUAL"
  echo "想下次全自动：在 CF 后台新建 token，权限加 Zone Settings Edit（区域只选 suanran9527.com），替换 /root/.cf.ini 再跑一遍即可。"
fi
echo "=================================================="
