#!/bin/bash
# ============================================================
# 废土家园插件（wasteland）打包脚本
# 产物：wasteland-pack-v<version>.zip（版本号从 pack.json 派生）
#   index.html + native-bridge.js + pack.json（zip 根目录）
# 纯网页包：无 plugins.dex、无原生入口（PackStore entryClass=""，
# 宿主 PackHostActivity 对空 entryClass 直接放行，页面靠
# localStorage 存档）。不需要 JDK/Android SDK。
# ============================================================
set -euo pipefail
PACK_DIR="$(cd "$(dirname "$0")" && pwd)"
PACK_VER="$(sed -n 's/.*"version":"\([0-9]*\)".*/\1/p' "$PACK_DIR/pack.json" | head -1)"
ZIP_NAME="wasteland-pack-v${PACK_VER}.zip"
OUT_ZIP="${OUT_ZIP:-$PACK_DIR/$ZIP_NAME}"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp "$PACK_DIR/web/index.html" "$STAGE/index.html"
cp "$PACK_DIR/native-bridge.js" "$STAGE/native-bridge.js"
cp "$PACK_DIR/pack.json" "$STAGE/pack.json"
rm -f "$OUT_ZIP"
(cd "$STAGE" && zip -q -r "$OUT_ZIP" .)
ls -lh "$OUT_ZIP"
sha256sum "$OUT_ZIP" || true
