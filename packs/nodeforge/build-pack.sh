#!/bin/bash
# ============================================================
# 一键节点功能包（nodeforge）打包脚本
# 产物：nodeforge-pack-v2.zip
#   index.html / template.yaml（web/）+ deploy.sh + native-bridge.js
#   + pack.json + plugins.dex（包 Java + JSch 一并 dex 化）
# 约束沿用芷湘包 CI 实测教训：
#   - javac 不用 android.jar 做 bootclasspath（lambda metafactory 缺失），只放 -cp
#   - d8 先把 classes 打成 jar 再喂（直吃目录报 Unsupported source file type）
#   - stubs 只是编译桩（宿主 core 契约镜像），不许进 dex
#   - JSch 宿主没有，必须作为 d8 程序输入打进 dex
# 环境：需要 JDK 17（javac/jar）、Android SDK（android.jar 与 d8）。
#   ANDROID_JAR / D8 可用环境变量覆盖。
# ============================================================
set -euo pipefail

PACK_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PACK_DIR/.build"
TOOLS_DIR="$PACK_DIR/.tools"
ZIP_NAME="nodeforge-pack-v2.zip"
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
cp "$PACK_DIR/deploy.sh" "$ASSEMBLE_DIR/deploy.sh"
cp "$PACK_DIR/native-bridge.js" "$ASSEMBLE_DIR/native-bridge.js"
cp "$PACK_DIR/pack.json" "$ASSEMBLE_DIR/pack.json"
cp "$BUILD_DIR/dex/classes.dex" "$ASSEMBLE_DIR/plugins.dex"
OUT_ZIP="${OUT_ZIP:-$PACK_DIR/$ZIP_NAME}"
rm -f "$OUT_ZIP"
(cd "$ASSEMBLE_DIR" && zip -r -q "$OUT_ZIP" .)
ls -lh "$OUT_ZIP"
sha256sum "$OUT_ZIP" || true
