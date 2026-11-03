# DreamBox（梦盒）

**基于 Mihomo 内核的 Android 代理客户端 —— 不只是导入订阅，还能一键把节点部署到你自己的 VPS 上。**

- 包名：`com.suanran.dreambox2`
- 平台：Android（arm64-v8a）
- 许可证：AGPL-3.0
- 下载：见本仓库 [Releases](https://github.com/anransu983-sketch/dreambox-asbefore/releases)

## 简介

梦盒借鉴 **FlyCat / YumeBox** 定制而来，在其 Mihomo 客户端基础上重做了品牌与界面，并持续加入自研功能：功能包扩展体系、一键节点部署、SSH 终端、sing-box 配置自动转换等。界面为磁吸卡片式设计，设置项直接在卡片内操作，首页面板可长按拖拽排序。

- 上游原作者：YumeYucca（GitHub [@lm-firefly](https://github.com/lm-firefly)，原仓库 <https://github.com/lm-firefly/yumebox>）
- 源码文件头的原作者版权注释全部保留，另追加 "Modified for DreamBox" 说明；`LICENSE`、`LICENSE-F2DL` 均保留未动

## 功能

### 代理核心

- Mihomo 内核，TUN / VPN 模式，策略组、规则分流、节点测速一应俱全
- Web 控制面板三选一：Zashboard / MetaCubeXD / Yacd
- 流量统计：按节点 / 分组记录上下行，首页可见
- 覆写模板：防 DNS 泄露、防 WebRTC（STUN 拦截）、sniffer 嗅探开箱即用

### 配置导入

- 订阅链接（Clash / Mihomo YAML）、本地文件、剪贴板、扫码、单节点链接
- **sing-box JSON 自动转换**：订阅或文件是 sing-box 格式（含 `outbounds`）时自动转成 Mihomo 配置，覆盖 vless / vmess / trojan / shadowsocks / hysteria(2) / tuic / wireguard 及 ws、grpc、reality 等常见传输

### 一键节点（功能包 nodeforge）

在自己的 VPS 上一键部署节点，全程不用手动碰面板或终端：

- 双引擎：**s-ui**（带面板与订阅）或 **sing-box**（轻量无面板）
- 协议组合：**VLESS Reality + Hysteria2 + SS 2022**，可单选可全选，端口自动顺延
- SSH 密钥部署：插件自带部署钥匙（可生成 / 导入），私钥加密存储；支持把公钥写进服务器并关闭密码登录
- 部署完成自动生成订阅：完整分组 / 规则模板、流量与到期显示；**Hysteria2 与 VLESS、SS 合并在同一份订阅里**，导入一次三个节点全进来
- **实测验证**：部署后在服务器上回环真连出网验证，通了才算成功；重跑同一台机器自动复用已有入站与客户端，不重复造节点

### SSH 终端（功能包 sshtty）

- 完整的 SSH 终端，支持密码与密钥登录
- 插件内生成 / 粘贴导入私钥、导出公钥，私钥以 AES-GCM 加密保存

### 功能包系统

- 一键节点、SSH 终端等重功能做成独立功能包，在应用内单独更新，不用整包升级
- 应用本身通过 GitHub Releases 检查更新

### 个性化

- 主题与配色、设置页磁吸卡片、首页面板拖拽排序
- 节点图标 / 国旗、测速与延迟展示样式

## 构建

环境：JDK 17 + JDK 26（Gradle 工具链）、Android SDK（API 36）、NDK r30、Go 1.27+、Rust nightly + cargo-ndk。

```bash
./scripts/sync-kernel.sh alpha                 # 同步 Mihomo 内核源码
ABI_APP_LIST=arm64-v8a python3 scripts/native-build.py --go --rust --loader --geo
<gradle-9.8.0>/bin/gradle :app:assembleRelease # 需在根目录 signing.properties 配置签名
```

注意：构建机 `/tmp` 较小时把 `TMPDIR`、`GRADLE_USER_HOME` 指到大磁盘目录；`org.gradle.jvmargs` 需含 `-Djava.net.preferIPv4Stack=true`。

## 目录结构

```
app/            应用壳（Manifest、res、启动 Activity）
core/           核心契约 / 模型 / 内核桥接（含 SingBoxConverter、YamlCodec）
data/           数据层（store、备份）
feature/        功能模块（home、proxy、profiles、settings、about、log…）
packs/          功能包源码（nodeforge 一键节点、sshtty 等）
runtime/        运行时（VPN Service、订阅处理）
ui/             通用 UI 组件
locale/         多语言文案
pack/           Release 打包器（内部工具）
scripts/        构建脚本
```

## 许可证

本项目基于上游 AGPL-3.0 代码修改，按许可证要求同样以 **AGPL-3.0** 分发。`LICENSE`、`LICENSE-F2DL` 及各源码文件头的原作者署名均予保留。
