#!/bin/bash
# ============================================================
# VPS 终端功能包（sshtty）打包脚本
# 产物：sshtty-pack-v4.zip
#   web/（index.html + vendor/xterm）+ native-bridge.js
#   + pack.json + plugins.dex（包 Java + JSch 一并 dex 化）
# 约束沿用 nodeforge/芷湘包 CI 实测教训：
#   - javac 不用 android.jar 做 bootclasspath，只放 -cp
#   - d8 先把 classes 打成 jar 再喂
#   - stubs 只是编译桩（宿主 core 契约镜像），不许进 dex
#   - JSch 宿主没有，必须作为 d8 程序输入打进 dex
# 环境：需要 JDK 17（javac/jar）、Android SDK（android.jar 与 d8）。
# ============================================================
set -euo pipefail

PACK_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PACK_DIR/.build"
TOOLS_DIR="$PACK_DIR/.tools"
PACK_VER="$(sed -n 's/.*"version":"\([0-9]*\)".*/\1/p' "$PACK_DIR/pack.json" | head -1)"
ZIP_NAME="sshtty-pack-v${PACK_VER}.zip"
JSCH_VERSION="0.2.23"
KOTLIN_VERSION="2.0.21"

JAVAC="${JAVAC:-javac}"
if [ -z "${ANDROID_JAR:-}" ]; then
    SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    ANDROID_JAR="$(ls "$SDK_ROOT"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
fi
if [ -z "${D8:-}" ]; then
    SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    D8="$(ls "$SDK_ROOT"/build-tools/*/d8 2>/dev/null | sort -V | tail -1)"
fi
[ -n "$ANDROID_JAR" ] && [ -f "$ANDROID_JAR" ] || { echo "ERROR: 找不到 android.jar" >&2; exit 1; }
[ -n "$D8" ] && [ -f "$D8" ] || { echo "ERROR: 找不到 d8" >&2; exit 1; }

mkdir -p "$TOOLS_DIR" "$BUILD_DIR"
fetch() { # $1=url $2=dest
    if [ ! -f "$2" ]; then
        echo ">> 下载 $1"
        curl -fLs -o "$2" "$1"
    fi
}
JSCH_JAR="$TOOLS_DIR/jsch-$JSCH_VERSION.jar"
KOTLIN_JAR="$TOOLS_DIR/kotlin-stdlib-$KOTLIN_VERSION.jar"
fetch "https://repo1.maven.org/maven2/com/github/mwiede/jsch/$JSCH_VERSION/jsch-$JSCH_VERSION.jar" "$JSCH_JAR"
fetch "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN_VERSION/kotlin-stdlib-$KOTLIN_VERSION.jar" "$KOTLIN_JAR"
# 依赖完整性钉死：两个 jar 校验 sha256，不符拒绝打包（防下载被劫持/损坏）
JSCH_SHA256="c45ff978dd4c1d4890e2524f0cb382cbad9477a87dab0e9c5a9e019d9353ea48"
KOTLIN_SHA256="f31cc53f105a7e48c093683bbd5437561d1233920513774b470805641bedbc09"
verify_sha() { # $1=file $2=expected
    actual="$(sha256sum "$1" | awk '{print $1}')"
    if [ "$actual" != "$2" ]; then
        echo "ERROR: $1 sha256 不符，拒绝继续（期望 $2，实际 $actual）" >&2
        exit 1
    fi
}
verify_sha "$JSCH_JAR" "$JSCH_SHA256"
verify_sha "$KOTLIN_JAR" "$KOTLIN_SHA256"

echo ">> 编译 stubs"
rm -rf "$BUILD_DIR/stub-classes" "$BUILD_DIR/pack-classes" "$BUILD_DIR/dex"
mkdir -p "$BUILD_DIR/stub-classes" "$BUILD_DIR/pack-classes" "$BUILD_DIR/dex"
find "$PACK_DIR/stubs" -name '*.java' > "$BUILD_DIR/stub-sources.txt"
"$JAVAC" -source 11 -target 11 -encoding UTF-8 \
    -cp "$ANDROID_JAR:$KOTLIN_JAR" \
    -d "$BUILD_DIR/stub-classes" \
    @"$BUILD_DIR/stub-sources.txt"

echo ">> 编译 pack java"
find "$PACK_DIR/java" -name '*.java' > "$BUILD_DIR/pack-sources.txt"
"$JAVAC" -source 11 -target 11 -encoding UTF-8 \
    -cp "$ANDROID_JAR:$BUILD_DIR/stub-classes:$JSCH_JAR:$KOTLIN_JAR" \
    -d "$BUILD_DIR/pack-classes" \
    @"$BUILD_DIR/pack-sources.txt"

echo ">> d8 生成 plugins.dex（JSch 一并打入）"
(cd "$BUILD_DIR/pack-classes" && jar cf "$BUILD_DIR/pack-classes.jar" .)
"$D8" --min-api 26 \
    --lib "$ANDROID_JAR" \
    --classpath "$BUILD_DIR/stub-classes" \
    --classpath "$KOTLIN_JAR" \
    --output "$BUILD_DIR/dex" \
    "$BUILD_DIR/pack-classes.jar" \
    "$JSCH_JAR"

if find "$BUILD_DIR/pack-classes" -path "*com/suanran/dreambox*" | grep -q .; then
    echo "ERROR: pack-classes 里混入了 stub 类，隔离失败" >&2
    exit 1
fi

echo ">> 组装 $ZIP_NAME"
ASSEMBLE_DIR="$BUILD_DIR/assemble"
rm -rf "$ASSEMBLE_DIR"
mkdir -p "$ASSEMBLE_DIR"
cp -r "$PACK_DIR/web/." "$ASSEMBLE_DIR/"
cp "$PACK_DIR/native-bridge.js" "$ASSEMBLE_DIR/native-bridge.js"
cp "$PACK_DIR/pack.json" "$ASSEMBLE_DIR/pack.json"
cp "$BUILD_DIR/dex/classes.dex" "$ASSEMBLE_DIR/plugins.dex"
OUT_ZIP="${OUT_ZIP:-$PACK_DIR/$ZIP_NAME}"
rm -f "$OUT_ZIP"
(cd "$ASSEMBLE_DIR" && zip -r -q "$OUT_ZIP" .)
ls -lh "$OUT_ZIP"
sha256sum "$OUT_ZIP" || true
