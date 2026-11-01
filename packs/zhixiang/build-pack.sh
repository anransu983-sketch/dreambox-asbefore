#!/usr/bin/env bash
# 芷湘功能包构建脚本：把芷湘的 Web 本体 + 官方 Capacitor native-bridge.js +
# 包内原生插件（plugins.dex）打成 zhixiang-pack-v1.zip。
#
# 用法（本地有 SDK 时与 CI 里同一条路）：
#   ZHIXIANG_DIR=/path/to/zhixiang-checkout bash packs/zhixiang/build-pack.sh
#
# 输入（环境变量，均可省略走自动探测）：
#   ZHIXIANG_DIR  芷湘仓库检出目录（默认 <repo>/zhixiang-src；dist 须已构建，
#                 没有 dist 时若 node_modules 在场则自动 npm run build）
#   ANDROID_JAR   android.jar 路径（默认 $ANDROID_HOME/platforms/android-36）
#   D8            d8 可执行文件（默认 $ANDROID_HOME/build-tools/*/d8 取最高版）
#   JAVAC         javac（默认 PATH 上的 javac）
#
# 产物：packs/zhixiang/out/zhixiang-pack-v1.zip
# 依赖 jar（okhttp / okio / kotlin-stdlib / annotations）从 Maven Central
# 拉到 packs/zhixiang/.tools，已在场则不重复下载。
#
# 关键约束：stubs/ 只是编译桩（宿主 core 契约的 Java 镜像），只进 javac 的
# classpath，绝不进 d8 的程序输入 —— 运行时这些类由宿主本体提供
# （DexClassLoader 父优先加载）。okhttp / kotlin-stdlib 同理只作 library
# 引用，宿主本体自带，不打进 dex。
set -euo pipefail

PACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$PACK_DIR/../.." && pwd)"
TOOLS_DIR="$PACK_DIR/.tools"
BUILD_DIR="$PACK_DIR/.build"
OUT_DIR="$PACK_DIR/out"
ZIP_NAME="zhixiang-pack-v1.zip"

ZHIXIANG_DIR="${ZHIXIANG_DIR:-$REPO_ROOT/zhixiang-src}"
JAVAC="${JAVAC:-javac}"

# ── Android 工具探测 ──────────────────────────────────────
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "${ANDROID_JAR:-}" ]; then
    if [ -n "$SDK_ROOT" ] && [ -f "$SDK_ROOT/platforms/android-36/android.jar" ]; then
        ANDROID_JAR="$SDK_ROOT/platforms/android-36/android.jar"
    elif [ -n "$SDK_ROOT" ]; then
        ANDROID_JAR="$(ls "$SDK_ROOT"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)"
    fi
fi
if [ -z "${ANDROID_JAR:-}" ] || [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: 找不到 android.jar，请设置 ANDROID_JAR 或 ANDROID_HOME" >&2
    exit 1
fi
if [ -z "${D8:-}" ]; then
    D8="$(ls "$SDK_ROOT"/build-tools/*/d8 2>/dev/null | sort -V | tail -1 || true)"
fi
if [ -z "${D8:-}" ] || [ ! -x "$D8" ]; then
    echo "ERROR: 找不到 d8，请设置 D8 或 ANDROID_HOME" >&2
    exit 1
fi
if [ ! -d "$ZHIXIANG_DIR" ]; then
    echo "ERROR: ZHIXIANG_DIR 不存在：$ZHIXIANG_DIR" >&2
    exit 1
fi

# ── 依赖 jar（版本与芷湘 android/app/build.gradle 对齐） ──
OKHTTP_VERSION="4.12.0"
OKIO_VERSION="3.6.0"
KOTLIN_VERSION="2.0.20"
JETBRAINS_ANNOTATIONS_VERSION="13.0"
MAVEN="https://repo1.maven.org/maven2"

fetch_jar() {
    local url="$1" dest="$2"
    if [ ! -f "$dest" ]; then
        echo ">> 下载依赖 $(basename "$dest")"
        curl -fSL --retry 3 -o "$dest" "$url"
    fi
}

mkdir -p "$TOOLS_DIR" "$BUILD_DIR" "$OUT_DIR"
OKHTTP_JAR="$TOOLS_DIR/okhttp-$OKHTTP_VERSION.jar"
OKIO_JAR="$TOOLS_DIR/okio-jvm-$OKIO_VERSION.jar"
KOTLIN_JAR="$TOOLS_DIR/kotlin-stdlib-$KOTLIN_VERSION.jar"
ANNOTATIONS_JAR="$TOOLS_DIR/annotations-$JETBRAINS_ANNOTATIONS_VERSION.jar"
fetch_jar "$MAVEN/com/squareup/okhttp3/okhttp/$OKHTTP_VERSION/okhttp-$OKHTTP_VERSION.jar" "$OKHTTP_JAR"
fetch_jar "$MAVEN/com/squareup/okio/okio-jvm/$OKIO_VERSION/okio-jvm-$OKIO_VERSION.jar" "$OKIO_JAR"
fetch_jar "$MAVEN/org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN_VERSION/kotlin-stdlib-$KOTLIN_VERSION.jar" "$KOTLIN_JAR"
fetch_jar "$MAVEN/org/jetbrains/annotations/$JETBRAINS_ANNOTATIONS_VERSION/annotations-$JETBRAINS_ANNOTATIONS_VERSION.jar" "$ANNOTATIONS_JAR"

DEPS_CP="$OKHTTP_JAR:$OKIO_JAR:$KOTLIN_JAR:$ANNOTATIONS_JAR"

# ── (a) Web 本体 ──────────────────────────────────────────
if [ ! -f "$ZHIXIANG_DIR/dist/index.html" ]; then
    if [ -d "$ZHIXIANG_DIR/node_modules" ]; then
        echo ">> dist 不存在，执行 npm run build"
        (cd "$ZHIXIANG_DIR" && npm run build)
    fi
fi
if [ ! -f "$ZHIXIANG_DIR/dist/index.html" ]; then
    echo "ERROR: $ZHIXIANG_DIR/dist 不存在或不完整，请先在芷湘仓库 npm ci && npm run build" >&2
    exit 1
fi

# ── (b) 官方 native-bridge.js ─────────────────────────────
BRIDGE_JS="$ZHIXIANG_DIR/node_modules/@capacitor/android/capacitor/src/main/assets/native-bridge.js"
if [ ! -f "$BRIDGE_JS" ]; then
    echo "ERROR: 找不到 Capacitor native-bridge.js：$BRIDGE_JS（先在芷湘仓库 npm ci）" >&2
    exit 1
fi

# ── (c) 编译桩 ────────────────────────────────────────────
echo ">> 编译 stubs"
rm -rf "$BUILD_DIR/stub-classes" "$BUILD_DIR/pack-classes" "$BUILD_DIR/dex"
mkdir -p "$BUILD_DIR/stub-classes" "$BUILD_DIR/pack-classes" "$BUILD_DIR/dex"
find "$PACK_DIR/stubs" -name '*.java' > "$BUILD_DIR/stub-sources.txt"
# 注意：不能用 -bootclasspath android.jar —— android.jar 里 java.lang.invoke 的
# 桩缺少 LambdaMetafactory.metafactory，lambda 编译会直接报错（CI 首跑实测）。
# android.jar 只放 -cp：JDK 平台类优先于 classpath，lambda 由 d8 在出 dex 时脱糖。
"$JAVAC" -source 11 -target 11 -encoding UTF-8 \
    -cp "$ANDROID_JAR:$KOTLIN_JAR" \
    -d "$BUILD_DIR/stub-classes" \
    @"$BUILD_DIR/stub-sources.txt"

# ── (d) 编译包内 Java（兼容层 + 插件 + 入口） ─────────────
echo ">> 编译 pack java"
find "$PACK_DIR/java" -name '*.java' > "$BUILD_DIR/pack-sources.txt"
"$JAVAC" -source 11 -target 11 -encoding UTF-8 \
    -cp "$ANDROID_JAR:$BUILD_DIR/stub-classes:$DEPS_CP" \
    -d "$BUILD_DIR/pack-classes" \
    @"$BUILD_DIR/pack-sources.txt"

# ── (e) d8 出 plugins.dex（程序输入只有包类） ─────────────
echo ">> d8 生成 plugins.dex"
"$D8" --min-api 26 \
    --lib "$ANDROID_JAR" \
    --classpath "$BUILD_DIR/stub-classes" \
    --classpath "$OKHTTP_JAR" \
    --classpath "$OKIO_JAR" \
    --classpath "$KOTLIN_JAR" \
    --output "$BUILD_DIR/dex" \
    "$BUILD_DIR/pack-classes"

# 防呆：stub 类不许被打进 dex。dex 字符串池里必然有对 core 契约的「引用」
# 描述符（入口类实现了它），所以不能靠 strings 判；在 d8 的程序输入目录里
# 查有没有 stub 的 .class 文件混入才是真判据（stub 只在 stub-classes 目录）。
if find "$BUILD_DIR/pack-classes" -path "*com/suanran/dreambox*" | grep -q .; then
    echo "ERROR: pack-classes 里混入了 stub 类，隔离失败" >&2
    exit 1
fi

# ── (f) 组装并打包 ────────────────────────────────────────
echo ">> 组装 $ZIP_NAME"
ASSEMBLE_DIR="$BUILD_DIR/assemble"
rm -rf "$ASSEMBLE_DIR"
mkdir -p "$ASSEMBLE_DIR"
cp -r "$ZHIXIANG_DIR/dist/." "$ASSEMBLE_DIR/"
cp "$BRIDGE_JS" "$ASSEMBLE_DIR/native-bridge.js"
cp "$BUILD_DIR/dex/classes.dex" "$ASSEMBLE_DIR/plugins.dex"
cp "$PACK_DIR/pack.json" "$ASSEMBLE_DIR/pack.json"

rm -f "$OUT_DIR/$ZIP_NAME"
(cd "$ASSEMBLE_DIR" && zip -r -q "$OUT_DIR/$ZIP_NAME" .)

echo "== 完成 =="
ls -la "$OUT_DIR/$ZIP_NAME"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$OUT_DIR/$ZIP_NAME"
else
    shasum -a 256 "$OUT_DIR/$ZIP_NAME"
fi
