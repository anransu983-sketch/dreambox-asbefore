# DreamBox（梦盒）

基于 **FlyCat / YumeBox** 的换皮 fork —— Mihomo 内核的 Android 代理客户端。

- **上游原作者**：YumeYucca（GitHub [@lm-firefly](https://github.com/lm-firefly)，原仓库 <https://github.com/lm-firefly/yumebox>）
- **许可证**：AGPL-3.0（见 [LICENSE](LICENSE)、[LICENSE-F2DL](LICENSE)；许可证文件与原作者署名均保留未动）
- **本 fork 改动**：换皮（包名、应用名、图标、文案、更新地址、签名）+ 新增 sing-box JSON → Mihomo 转换器；**未改动内核/代理逻辑**

> 源码文件头部的原作者版权注释全部保留，另追加 "Modified for DreamBox" 说明。

## 换皮清单

| 项目 | 上游 | 本 fork |
|---|---|---|
| 应用名 | FlyCat | 梦盒 / DreamBox |
| applicationId | `com.github.lmfirefly.flycat` | `com.suanran.dreambox` |
| 各模块 namespace | `com.github.lmfirefly.flycat.*` | `com.suanran.dreambox.*` |
| rootProject.name | FlyCat | DreamBox |
| compileSdk / targetSdk | 37 | 36（`android.compileSdkMinor` 已删除，默认为 0） |
| 更新检查仓库 | `LM-Firefly/FlyCat`（GitHub Releases） | `suanran/dreambox`（`app/build.gradle.kts` 里 `update.repository` 的 `?:` 兜底默认值已直接改掉；更新资产名前缀 `flycat-*` → `dreambox-*`，UA 标识 FlyCat → DreamBox） |
| 应用图标 | 上游 logo | 新生成的二次元纸箱图标（`app/res/mipmap-*/ic_launcher*` 全密度 + adaptive-icon；关于页 `R.drawable.dreambox`） |
| Deep link scheme | `flycat://` | `dreambox://` |
| 前台服务 subtype | `flycat_vpn` / `flycat_proxy` / `flycat_root_tun` | `dreambox_*` |
| 主题名 | `Theme.FlyCat` | `Theme.DreamBox` |
| 下载目录 | `Download/FlyCat` | `Download/DreamBox` |
| 界面文案 | FlyCat | 中文语境「梦盒」，英文/日文/俄文语境 DreamBox（`locale/lang/*`） |
| Release 签名 | 上游 `release.keystore`（已删除） | 新生成的 `dreambox.keystore`（见下） |

未改：`pack` 模块的 loader 包名（`dev.flycat.loader`，构建工具内部名）、buildSrc 的 `dev.flycat.packer`、LICENSE 文件头注释。

## 新增功能：sing-box JSON → Mihomo 转换器

Mihomo 原生不支持 sing-box JSON 配置。本 fork 在 `core/src/core/util/SingBoxConverter.kt` 实现了一个 best-effort 转换器：

- **识别**：内容为 JSON 且含 `outbounds` 数组时判定为 sing-box 配置
- **覆盖的 outbound**：`vless`、`vmess`、`trojan`、`shadowsocks`、`hysteria`（v1）、`hysteria2`、`tuic`、`wireguard`（含 transport：`ws` / `grpc` / `reality` / `tcp`(http伪装) / `http` 常用项）
- **跳过**：`selector`、`urltest`、`direct`、`block`、`dns`、`anytls`（无 Mihomo 对应）
- **容错**：未知字段忽略并记 warning（logcat + 生成 YAML 头部注释），单条 outbound 转换失败不影响其它，永不抛异常

**接入点**（两处，全自动）：
1. **文件导入**：`feature/profiles/.../ProfileImportFiles.kt` 的 `copyProfileImport` —— 导入文件后若检测到 sing-box JSON，原地转换为 `proxies:` YAML 再走正常流程
2. **订阅更新**：`runtime/service/.../ProfileProcessor.kt` —— 订阅返回内容经 Go 核心 `FetchAndValid` 校验失败后，若暂存的 `config.yaml` 是 sing-box JSON，则原地转换后**重试一次校验**；转换失败则抛原始错误

## 构建（实测可用的复现步骤）

> 以下是在一台 2 核 / 8GB Linux 机器上实际跑通的流程。注意 `/tmp` 只有 512MB 时 Go/Rust 构建会因空间不足失败，务必把 `TMPDIR` 指到大磁盘。

**环境要求**：JDK 17（跑 Gradle）+ JDK 26（项目 `android.jvm=26`，编译工具链用；`gradle/gradle-daemon-jvm.properties` 要求 daemon 跑在 JDK 26）、Android SDK（API 36、build-tools 36.0.0）、NDK r30（`android.ndkVersion=30.0.15729638`）、Go 1.27+、Rust nightly + `cargo-ndk`。

```bash
# 0. 环境变量（按实际路径调整）
export JAVA_HOME=/home/hatch/jdk-26            # 直接用 JDK 26 跑 Gradle，省去 toolchain 下载
export PATH=$JAVA_HOME/bin:$HOME/go/bin:$HOME/.cargo/bin:$PATH
export ANDROID_HOME=/home/hatch/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/30.0.15729638
export TMPDIR=$HOME/build-tmp                  # 关键：避开 512MB 的 /tmp
export GRADLE_USER_HOME=/home/hatch/.gradle    # 关键：JVM 的 user.home 可能是 /root，不设会读写错位置
mkdir -p $TMPDIR

# 代理（如需）：Gradle 的 Java 进程不读 shell 代理变量，需经 GRADLE_OPTS 传入
# export GRADLE_OPTS="-Dhttps.proxyHost=... -Dhttps.proxyPort=... -Dhttps.proxyUser=... -Dhttps.proxyPassword=... \
#   -Dhttp.proxyHost=... -Dhttp.proxyPort=... -Dhttp.proxyUser=... -Dhttp.proxyPassword=..."

# 1. 同步 Mihomo 内核源码
./scripts/sync-kernel.sh alpha                 # alpha | meta | smart

# 2. 原生构建（只编 arm64-v8a；--loader 必需，它是启动时的 Application 类依赖的 so）
ABI_APP_LIST=arm64-v8a python3 scripts/native-build.py --go --rust --loader --geo
# 产物：jniLibs/arm64-v8a/{libmihomo.so,liboverride.so,libloader.so}，Geo 数据进 build/generated

# 3. Gradle 构建（~/.gradle/gradle.properties 需有 org.gradle.java.installations.paths=<jdk26路径>，
#    否则 daemon 会尝试从 api.foojay.io 下载 JDK 26）
<gradle-9.8.0>/bin/gradle :app:assembleDebug    # arm64-v8a debug 包
<gradle-9.8.0>/bin/gradle :app:assembleRelease  # release 包（需 signing.properties，见下）
```

说明：
- Gradle 9.8.0 发行版若 wrapper 下不动，可用 curl 从 `https://services.gradle.org/distributions/gradle-9.8.0-bin.zip` 手动下载解压后直接用 `bin/gradle`。
- `gradle.properties` 里 `abi.app.list` 默认全 ABI，但 app 模块默认只打单 `arm64-v8a`（`splitAbiList` 逻辑），无需修改。
- `org.gradle.jvmargs` 默认 `-Xmx8g`；内存不足的机器可酌情调小（如 `-Xmx3g`）。
- **关键**：`org.gradle.jvmargs` 必须含 `-Djava.net.preferIPv4Stack=true`（否则 daemon 会绑 IPv4-mapped IPv6 socket，`isCommunicationAddress` 的 equals 检查失败，daemon 直接 RST 客户端连接，报 `Could not dispatch a message to the daemon`）。客户端侧同样需要在 `GRADLE_OPTS` 里加 `-Djava.net.preferIPv4Stack=true`。

## Release 签名

Release 签名配置由**项目根目录的 `signing.properties`** 控制（构建脚本读取它；该文件已被 `.gitignore` 忽略，不会提交）：

```properties
keystore.password=<dreambox.keystore 密码>
key.alias=dreambox
key.password=<密钥密码>
```

构建脚本（`app/build.gradle.kts`）的 `signingConfigs.release` 与 pack 任务均指向根目录的 **`dreambox.keystore`**。

### ⚠️ keystore 备份提醒

- `dreambox.keystore` + `keystore-password.txt`（密码仅存于此文件）**丢了就再也无法用同一签名更新应用**，所有已安装用户将被迫卸载重装。
- 这两个文件都已被 `.gitignore` 忽略、不会进 git，**必须手动备份到安全的地方**（加密 U 盘 / 密码管理器至少两处异地）。
- 重新生成 keystore 的命令（会覆盖旧文件，旧签名即作废）：

```bash
PASS=$(cat keystore-password.txt)  # 或重新生成随机密码并写回该文件
keytool -genkeypair -keystore dreambox.keystore -storetype PKCS12 \
  -alias dreambox -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=DreamBox, OU=DreamBox, O=suanran, C=CN"
```

## 订阅格式支持情况

| 格式 | 状态 |
|---|---|
| Clash YAML / Mihomo YAML（订阅、文件导入） | ✅ 原生支持（Go 核心 `FetchAndValid`） |
| sing-box JSON（含 `outbounds`，订阅、文件导入） | ✅ 本 fork 新增自动转换（见上） |
| vless:// 等 share-link | ❌ 上游本来就不支持，未实现 |

## 目录结构（主要模块）

```
app/            应用壳（AndroidManifest、res、启动 Activity）
core/           核心契约 / 模型 / 内核桥接（含 SingBoxConverter、YamlCodec）
data/           数据层（store、备份）
feature/        功能模块（home、proxy、profiles、settings、about、log…）
ui/             通用 UI 组件
extension/      扩展模块
runtime/        运行时（api / client / service，含 VPN Service 与 ProfileProcessor）
locale/         多语言文案（locale/lang/<语言>/*.fvv）
pack/           Release 加固打包器（内部工具，包名未改）
scripts/        构建脚本（sync-kernel.sh、native-build.py 等）
```

## 许可证

本项目基于上游 AGPL-3.0 代码修改，按许可证要求同样以 AGPL-3.0 分发。`LICENSE`、`LICENSE-F2DL` 及各源码文件头的原作者署名均予保留。
