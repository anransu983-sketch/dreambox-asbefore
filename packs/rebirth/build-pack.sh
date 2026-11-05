#!/bin/bash
# ============================================================
# 轮回插件（rebirth）打包脚本
# 产物：rebirth-pack-v<version>.zip（版本号从 pack.json 派生）
#   web/index.html + native-bridge.js + pack.json + plugins.dex
# 页面是纯链接清单，入口类只满足宿主契约，无第三方依赖；
# 仍按 sshtty/nodeforge 的成熟配方：javac 不用 android.jar 做
# bootclasspath、d8 先 jar 再喂、stubs 只编译不进 dex。
# 环境：JDK 17、Android SDK（android.jar 与 d8）。
# ============================================================
set -euo pipefail

PACK_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PACK_DIR/.build"
TOOLS_DIR="$PACK_DIR/.tools"
PACK_VER="$(sed -n 's/.*"version":"\([0-9]*\)".*/\1/p' "$PACK_DIR/pack.json" | head -1)"
ZIP_NAME="rebirth-pack-v${PACK_VER}.zip"
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
fetch() {
    if [ ! -f "$2" ]; then
        echo ">> 下载 $1"
        curl -fLs -o "$2" "$1"
    fi
}
KOTLIN_JAR="$TOOLS_DIR/kotlin-stdlib-$KOTLIN_VERSION.jar"
fetch "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN_VERSION/kotlin-stdlib-$KOTLIN_VERSION.jar" "$KOTLIN_JAR"

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
    -cp "$ANDROID_JAR:$BUILD_DIR/stub-classes:$KOTLIN_JAR" \
    -d "$BUILD_DIR/pack-classes" \
    @"$BUILD_DIR/pack-sources.txt"

echo ">> d8 生成 plugins.dex"
(cd "$BUILD_DIR/pack-classes" && jar cf "$BUILD_DIR/pack-classes.jar" .)
"$D8" --min-api 26 \
    --lib "$ANDROID_JAR" \
    --classpath "$BUILD_DIR/stub-classes" \
    --classpath "$KOTLIN_JAR" \
    --output "$BUILD_DIR/dex" \
    "$BUILD_DIR/pack-classes.jar"

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
