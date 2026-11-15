# DreamBox（梦盒）

**基于 Mihomo 内核的 Android 代理客户端 —— 不只是导入订阅，还能一键把节点部署到你自己的 VPS 上。**

- 包名：`com.suanran.dreambox2`
- 平台：Android（arm64-v8a）
- 许可证：AGPL-3.0
- 下载：见本仓库 [Releases](https://github.com/anransu983-sketch/dreambox-asbefore/releases)

## 简介

梦盒借鉴 **FlyCat / YumeBox** 定制而来，在其 Mihomo 客户端基础上重做了品牌与界面，并持续加入自研功能：功能包扩展体系、一键节点部署、SSH 终端、IP 测试、流媒体解锁、WebDAV 云备份、模块市场、sing-box 配置自动转换等。首页是一整幅天空之城浮岛夜景——城堡、飞艇、巨树、齿轮就是入口，岛上还住着会走动、会说话的居民，点谁报谁（节点与连接时长、延迟、今日流量、当前配置与剩余流量、实时上下行）；设置页沿用磁吸卡片式设计，设置项直接在卡片内操作。

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

服务器只输一次，之后点「连接」直接进终端；测试与调优都在服务器卡片里。

- 完整的 SSH 终端（xterm + 功能键条），支持密码与私钥登录；IP 默认打码，可切换显示
- 插件内生成 / 粘贴导入私钥、导出公钥，私钥以 AES-GCM 加密保存
- **一键测试四件套**：TcpQuality 三网回程、融合怪综合体检、IP 质量体检、NodeQuality 全家桶——都在服务器上脱离会话运行，关掉页面不中断，回来点「结果」继续看，报告链接自动提取成卡
- **一键调优**：Tcpfit 审计版（下载先验哈希）；基础调优按套餐带宽算缓冲，完整版再加实测拐点整形，调前留底、可一键回滚
- **一键 CF 节点**：在回国直连差的机器上一键走 Cloudflare 出节点（VLESS + WS），同时出直连 v6 节点，订阅自动带流量与到期显示
- **迁移备份**：全部服务器与私钥用你的口令加密导出成一段文本，换机 / 卸载重装后粘贴 + 口令即可恢复；本机存档损坏时自动隔离自愈，不挡导入
- v14 起每张服务器卡住进一只光点仓鼠：随机过日子（揉爪、洗脸、啃薯条、抱瓜子、睡觉冒 Zzz、打包绕框跑路），全部按钮图标也都是不同品种、不同姿势的仓鼠

### IP 测试（功能包 iptest）

- IP 查询与纯净度检测：IP 画像、纯净度、评分、IPv6 测试，多个查询源交叉对照

### 流媒体解锁（功能包 unlock）

- 走本机代理端口测解锁情况：Netflix、Disney+、YouTube Premium、ChatGPT 等，看跳转与地区码判定

### WebDAV 云备份（功能包 webdav）

- 备份包上传到自己的 WebDAV 网盘，账号密码加密保存在本机；换机后从云端恢复

### 模块市场（插件页）

- 轻模块是纯规则包：一键安装，开关当场生效，删掉就干净移除
- 内置种子：去广告、去追踪、开屏广告；远程索引可挂更多模块，不用整包升级

### 功能包系统

- 一键节点、VPS 终端、IP 测试、流媒体解锁、WebDAV 云备份做成独立功能包，在应用内单独更新，不用整包升级
- 应用本身通过 GitHub Releases 检查更新

### 个性化

- 首页天空之城：整幅浮岛夜景场景画，景物即控件，无底部导航栏
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
