#!/bin/bash
# ============================================================
# NodeForge 一键部署 · sing-box 引擎（无面板）
# 目标：全新机器 → sing-box + VLESS+Reality（主）/ Hysteria2 /
#       Shadowsocks 2022 入站，systemd 托管；客户端链接与配置
#       直接打印在部署终端里，复制即用。
# v20 起另起一个极简订阅服务（sub-service.sh）：只认 128 位随机
#       令牌路径、其余 404、不记日志，供梦盒远程导入/更新。
# 输入（环境变量）：NF_NODE_PORT / NF_SSH_PORT / NF_PUBLIC_HOST /
#       NF_NODE_NAME / NF_PROTOCOLS / NF_SUB_ID
# 输出：进度行 "== ..."；成功最后打印 "NF_RESULT {json}"；
#       失败打印 "NF_ERROR: ..." 并非零退出。
# 密钥落盘 /usr/local/etc/sing-box/nf-secrets.env（600），重跑复用。
# ============================================================
set -u
export DEBIAN_FRONTEND=noninteractive

SB_FALLBACK_VER="1.12.0"
SNI="www.microsoft.com"
NODE_PORT="${NF_NODE_PORT:-443}"
SSH_PORT="${NF_SSH_PORT:-22}"
SUB_ID="${NF_SUB_ID:-node1}"
PUBLIC_HOST="${NF_PUBLIC_HOST:-}"
NODE_NAME="${NF_NODE_NAME:-}"
PROTOCOLS="${NF_PROTOCOLS:-reality}"
has_proto() { case ",$PROTOCOLS," in *",$1,"*) return 0 ;; *) return 1 ;; esac }
HY2_PORT=$((NODE_PORT + 1))
SS_PORT=$((NODE_PORT + 3))
if [ "$NODE_PORT" -lt 1 ] || [ $((NODE_PORT + 3)) -gt 65535 ]; then
    echo "NF_ERROR: 节点端口不合法（多协议顺延需要主端口+3 不超过 65535）" >&2
    exit 1
fi

log() { echo "== $*"; }
fail() { echo "NF_ERROR: $*" >&2; exit 1; }

[ "$(id -u)" = "0" ] || fail "需要 root 执行"
[ -n "$PUBLIC_HOST" ] || fail "缺少服务器地址"

CFG_DIR="/usr/local/etc/sing-box"
CFG_FILE="$CFG_DIR/config.json"
SECRETS_FILE="$CFG_DIR/nf-secrets.env"
SB_BIN="/usr/local/bin/sing-box"
mkdir -p "$CFG_DIR"

# ── 0. 基础工具 ────────────────────────────────────────────
need_pkgs=""
command -v curl    >/dev/null 2>&1 || need_pkgs="$need_pkgs curl"
command -v openssl >/dev/null 2>&1 || need_pkgs="$need_pkgs openssl"
command -v python3 >/dev/null 2>&1 || need_pkgs="$need_pkgs python3"
if [ -n "$need_pkgs" ]; then
    log "安装基础工具：$need_pkgs"
    if command -v apt-get >/dev/null 2>&1; then
        APT_OPTS="-o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20 -o Acquire::Retries=1"
        apt-get update -qq $APT_OPTS || apt-get update -qq $APT_OPTS || fail "apt-get update 失败"
        apt-get install -y -qq $need_pkgs >/dev/null 2>&1 || fail "基础工具安装失败"
    elif command -v dnf >/dev/null 2>&1; then
        dnf install -y -q $need_pkgs >/dev/null || fail "基础工具安装失败"
    elif command -v yum >/dev/null 2>&1; then
        yum install -y -q $need_pkgs >/dev/null || fail "基础工具安装失败"
    else
        fail "不支持的系统（没有 apt/dnf/yum）"
    fi
fi
command -v systemctl >/dev/null 2>&1 || fail "没有 systemd，sing-box 引擎需要 systemd 发行版"

# ── 1. 安装 sing-box ───────────────────────────────────────
if [ -x "$SB_BIN" ]; then
    SB_VER="$("$SB_BIN" version 2>/dev/null | head -1 | awk '{print $3}')"
    log "sing-box 已安装（版本 ${SB_VER:-未知}），跳过安装"
else
    case "$(uname -m)" in
        x86_64|amd64) SB_ARCH="amd64" ;;
        aarch64|arm64) SB_ARCH="arm64" ;;
        *) fail "不支持的 CPU 架构：$(uname -m)" ;;
    esac
    SB_VER="$(curl -fsSL --max-time 20 https://api.github.com/repos/SagerNet/sing-box/releases/latest 2>/dev/null \
        | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tag_name","").lstrip("v"))' 2>/dev/null || true)"
    if [ -z "$SB_VER" ]; then
        SB_VER="$SB_FALLBACK_VER"
        log "取最新版本失败，改用固定版本 v$SB_VER"
    fi
    log "下载 sing-box v$SB_VER（$SB_ARCH）"
    SB_TGZ="/tmp/sing-box-$SB_VER-linux-$SB_ARCH.tar.gz"
    curl -fLs --max-time 300 -o "$SB_TGZ" \
        "https://github.com/SagerNet/sing-box/releases/download/v$SB_VER/sing-box-$SB_VER-linux-$SB_ARCH.tar.gz" \
        || fail "sing-box 下载失败（检查服务器能否访问 GitHub）"
    rm -rf "/tmp/sing-box-$SB_VER-linux-$SB_ARCH"
    tar -xzf "$SB_TGZ" -C /tmp || fail "sing-box 解压失败"
    install -m 755 "/tmp/sing-box-$SB_VER-linux-$SB_ARCH/sing-box" "$SB_BIN" || fail "sing-box 安装失败"
    rm -f "$SB_TGZ"
    log "sing-box v$SB_VER 安装完成"
fi

# ── 2. 密钥（落盘复用） ────────────────────────────────────
UUID=""; REALITY_PRIV=""; REALITY_PUB=""; SHORT_ID=""; HY2_PASS=""; SS_KEY=""
if [ -f "$SECRETS_FILE" ]; then . "$SECRETS_FILE" || true; fi
GEN_NEW=""
if [ -z "$UUID" ]; then UUID="$(cat /proc/sys/kernel/random/uuid)"; GEN_NEW=1; fi
if [ -z "$REALITY_PRIV" ] || [ -z "$REALITY_PUB" ]; then
    KP="$("$SB_BIN" generate reality-keypair)" || fail "生成 Reality 密钥失败"
    REALITY_PRIV="$(printf '%s' "$KP" | sed -n 's/.*PrivateKey:[[:space:]]*//p' | head -1)"
    REALITY_PUB="$(printf '%s' "$KP" | sed -n 's/.*PublicKey:[[:space:]]*//p' | head -1)"
    [ -n "$REALITY_PRIV" ] && [ -n "$REALITY_PUB" ] || fail "Reality 密钥解析失败"
    GEN_NEW=1
fi
if [ -z "$SHORT_ID" ]; then SHORT_ID="$(openssl rand -hex 8)"; GEN_NEW=1; fi
if has_proto hy2 && [ -z "$HY2_PASS" ]; then HY2_PASS="$(openssl rand -hex 12)"; GEN_NEW=1; fi
if has_proto ss && [ -z "$SS_KEY" ]; then SS_KEY="$(openssl rand -base64 32 | tr -d '\n')"; GEN_NEW=1; fi
if [ -n "$GEN_NEW" ]; then
    umask 077
    { echo "UUID=$UUID"; echo "REALITY_PRIV=$REALITY_PRIV"; echo "REALITY_PUB=$REALITY_PUB"
      echo "SHORT_ID=$SHORT_ID"; echo "HY2_PASS=$HY2_PASS"; echo "SS_KEY=$SS_KEY"
    } > "$SECRETS_FILE"
    chmod 600 "$SECRETS_FILE" 2>/dev/null || true
    log "密钥已就绪（重跑复用同一套）"
fi

# ── 3. Hy2 自签证书 ────────────────────────────────────────
HY2_CERT="$CFG_DIR/hy2-cert.pem"
HY2_KEY="$CFG_DIR/hy2-key.pem"
if has_proto hy2 && [ ! -f "$HY2_CERT" ]; then
    openssl req -x509 -newkey rsa:2048 -keyout "$HY2_KEY" -out "$HY2_CERT" \
        -days 3650 -nodes -subj "/CN=$SNI" >/dev/null 2>&1 || fail "Hy2 证书生成失败"
    chmod 600 "$HY2_KEY" 2>/dev/null || true
    log "Hy2 自签证书已生成"
fi

# ── 4. 写配置并校验 ────────────────────────────────────────
if [ -n "$NODE_NAME" ]; then VLESS_TAG="$NODE_NAME"; else VLESS_TAG="nf-vless"; fi
python3 - "$CFG_FILE" "$VLESS_TAG" "$SUB_ID" "$UUID" "$NODE_PORT" "$SNI" \
    "$REALITY_PRIV" "$SHORT_ID" "$PROTOCOLS" "$HY2_PORT" "$HY2_PASS" \
    "$HY2_CERT" "$HY2_KEY" "$SS_PORT" "$SS_KEY" <<'PYEOF'
import json, sys
(cfg, tag, user, uuid, port, sni, priv, sid, protos,
 hy2_port, hy2_pass, cert, key, ss_port, ss_key) = sys.argv[1:16]
plist = [p for p in protos.split(",") if p]
inbounds = [{
    "type": "vless", "tag": tag, "listen": "::", "listen_port": int(port),
    "users": [{"name": user, "uuid": uuid, "flow": "xtls-rprx-vision"}],
    "tls": {"enabled": True, "server_name": sni,
            "reality": {"enabled": True,
                        "handshake": {"server": sni, "server_port": 443},
                        "private_key": priv, "short_id": [sid]}},
}]
if "hy2" in plist:
    inbounds.append({
        "type": "hysteria2", "tag": "nf-hy2", "listen": "::",
        "listen_port": int(hy2_port), "users": [{"password": hy2_pass}],
        "tls": {"enabled": True, "alpn": ["h3"],
                "certificate_path": cert, "key_path": key},
    })
if "ss" in plist:
    inbounds.append({
        "type": "shadowsocks", "tag": "nf-ss", "listen": "::",
        "listen_port": int(ss_port),
        "method": "2022-blake3-aes-256-gcm", "password": ss_key,
    })
conf = {"log": {"level": "warn"},
        "inbounds": inbounds,
        "outbounds": [{"type": "direct", "tag": "direct"}],
        "route": {"final": "direct"}}
with open(cfg, "w") as f:
    json.dump(conf, f, indent=2)
PYEOF
[ -f "$CFG_FILE" ] || fail "配置生成失败"
"$SB_BIN" check -c "$CFG_FILE" || fail "sing-box 配置校验不通过"
log "配置校验通过（协议：$PROTOCOLS）"

# ── 5. systemd 托管 ────────────────────────────────────────
cat > /etc/systemd/system/sing-box.service <<'UNITEOF'
[Unit]
Description=sing-box (NodeForge)
After=network.target nss-lookup.target

[Service]
User=root
ExecStart=/usr/local/bin/sing-box run -c /usr/local/etc/sing-box/config.json
Restart=on-failure
RestartSec=3
LimitNOFILE=1048576

[Install]
WantedBy=multi-user.target
UNITEOF
systemctl daemon-reload || fail "systemd 重载失败"
systemctl enable sing-box >/dev/null 2>&1 || true
systemctl restart sing-box || fail "sing-box 启动失败"
sleep 1
if ! systemctl is-active --quiet sing-box; then
    journalctl -u sing-box -n 15 --no-pager 2>/dev/null | tail -15
    fail "sing-box 服务未运行（上面是最近日志，常见原因：端口被别的程序占用）"
fi
log "sing-box 服务已运行"

# ── 6. 防火墙放行（只放节点端口；订阅端口在第 9 节单独放行） ──
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -qi 'status: active'; then
    ufw allow "$SSH_PORT"/tcp >/dev/null 2>&1 || true
    ufw allow "$NODE_PORT"/tcp >/dev/null 2>&1 || true
    if has_proto ss; then ufw allow "$SS_PORT"/tcp >/dev/null 2>&1 || true; ufw allow "$SS_PORT"/udp >/dev/null 2>&1 || true; fi
    if has_proto hy2; then ufw allow "$HY2_PORT"/udp >/dev/null 2>&1 || true; fi
    log "ufw 已放行节点端口"
elif command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
    firewall-cmd --permanent --add-port="$NODE_PORT"/tcp >/dev/null 2>&1 || true
    if has_proto ss; then firewall-cmd --permanent --add-port="$SS_PORT"/tcp >/dev/null 2>&1 || true; firewall-cmd --permanent --add-port="$SS_PORT"/udp >/dev/null 2>&1 || true; fi
    if has_proto hy2; then firewall-cmd --permanent --add-port="$HY2_PORT"/udp >/dev/null 2>&1 || true; fi
    firewall-cmd --reload >/dev/null 2>&1 || true
    log "firewalld 已放行节点端口"
fi

# ── 7. 监听自检 ────────────────────────────────────────────
port_listening() { # $1=port $2=tcp|udp
    if command -v ss >/dev/null 2>&1; then
        if [ "$2" = "udp" ]; then ss -uln 2>/dev/null | grep -q ":$1 "; else ss -tln 2>/dev/null | grep -q ":$1 "; fi
    else
        netstat -ln 2>/dev/null | grep -q ":$1 "
    fi
}
VERIFY="fail"
if port_listening "$NODE_PORT" tcp; then VERIFY="ok"; log "VLESS Reality 已监听 $NODE_PORT/tcp"; else log "警告：$NODE_PORT/tcp 未见监听"; fi
HY2_OK="fail"; SS_VERIFY="fail"
if has_proto hy2; then
    if port_listening "$HY2_PORT" udp; then HY2_OK="ok"; log "Hysteria2 已监听 $HY2_PORT/udp"; else log "警告：$HY2_PORT/udp 未见监听"; fi
fi
if has_proto ss; then
    if port_listening "$SS_PORT" tcp; then SS_VERIFY="ok"; log "SS 2022 已监听 $SS_PORT"; else log "警告：$SS_PORT 未见监听"; fi
fi

# ── 8. 客户端链接：直接打印在终端里（无网址） ──────────────
LINKS="$(python3 - "$PUBLIC_HOST" "$NODE_NAME" "$SUB_ID" "$UUID" "$NODE_PORT" \
    "$REALITY_PUB" "$SHORT_ID" "$SNI" "$PROTOCOLS" "$HY2_PASS" "$HY2_PORT" \
    "$SS_KEY" "$SS_PORT" <<'PYEOF'
import sys, base64
from urllib.parse import quote
(host, name, sub_id, uuid, port, pub, sid, sni, protos,
 hy2_pass, hy2_port, ss_key, ss_port) = sys.argv[1:14]
disp = name if name else sub_id
enc = quote(disp, safe="")
plist = [p for p in protos.split(",") if p]
out = []
out.append("vless://%s@%s:%s?security=reality&encryption=none&pbk=%s&fp=chrome&type=tcp&sni=%s&sid=%s&flow=xtls-rprx-vision#%s"
           % (uuid, host, port, pub, sni, sid, enc))
if "hy2" in plist:
    out.append("hysteria2://%s@%s:%s/?insecure=1&sni=%s#%s"
               % (hy2_pass, host, hy2_port, sni, enc))
if "ss" in plist:
    b64 = base64.urlsafe_b64encode(("2022-blake3-aes-256-gcm:" + ss_key).encode()).decode()
    out.append("ss://%s@%s:%s#%s" % (b64, host, ss_port, enc))
print("\n".join(out))
PYEOF
)"
VLESS_LINK="$(printf '%s' "$LINKS" | grep '^vless://' || true)"
HY2_LINK=""; SS_LINK=""
if has_proto hy2; then HY2_LINK="$(printf '%s' "$LINKS" | grep '^hysteria2://' || true)"; fi
if has_proto ss; then SS_LINK="$(printf '%s' "$LINKS" | grep '^ss://' || true)"; fi
log "客户端配置（sing-box 无面板无订阅，下面就是全部配置，复制保存）"
printf '%s\n' "$LINKS"

# ── 9. 订阅服务（令牌路径，供梦盒远程导入） ────────────────
NF_SUB_URL=""; NF_SUB_PORT=0; NF_SUB_OK="fail"
RUN_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$RUN_DIR/sub-service.sh" ]; then
    . "$RUN_DIR/sub-service.sh"
    nfsub_setup full "$RUN_DIR/template.yaml" "$UUID" "$REALITY_PUB" "$SHORT_ID" || true
fi

# ── 10. 结果 ───────────────────────────────────────────────
python3 - "$UUID" "$REALITY_PUB" "$SHORT_ID" "$NODE_PORT" "$SNI" "$VLESS_LINK" \
    "$VERIFY" "$PROTOCOLS" "$SS_LINK" "$SS_PORT" "$SS_VERIFY" \
    "$HY2_LINK" "$HY2_PORT" "$HY2_OK" "$SB_VER" \
    "$NF_SUB_URL" "$NF_SUB_PORT" "$NF_SUB_OK" <<'PYEOF'
import json, sys
(uuid, pub, sid, node_port, sni, vless, verify, protocols,
 ss_link, ss_port, ss_verify, hy2_link, hy2_port, hy2_ok, sb_ver,
 sub_url, sub_port, sub_ok) = sys.argv[1:19]
print("NF_RESULT " + json.dumps({
    "engine": "singbox", "sbVersion": sb_ver,
    "uuid": uuid, "publicKey": pub, "shortId": sid,
    "nodePort": int(node_port), "sni": sni, "vlessLink": vless,
    "verified": verify == "ok",
    "protocols": protocols, "ssLink": ss_link, "ssPort": int(ss_port),
    "ssVerified": ss_verify == "ok", "hy2Link": hy2_link,
    "hy2Port": int(hy2_port), "hy2Ok": hy2_ok == "ok",
    "subUrl": sub_url, "subPort": int(sub_port), "subOk": sub_ok == "ok",
}))
PYEOF
log "部署完成"
