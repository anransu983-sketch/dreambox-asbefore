# 芷湘功能包（zhixiang-pack v1）移植说明

把独立芷湘 App（Capacitor + Web + Java 插件）做成梦盒功能包：
Web 本体（芷湘仓库 `dist/`）+ 官方 Capacitor `native-bridge.js` 原样进包；
原生插件不重写业务逻辑，而是给包配了一层最小的 Capacitor 兼容层
（`java/com/getcapacitor/`），让芷湘的原插件源码基本原样编译运行，
最终由 `plugins.dex` 承载、宿主 `PackHostActivity` 用 DexClassLoader 加载。

入口类：`com.suanran.pack.zhixiang.ZhixiangPackEntry`
（实现宿主 core 的 `PackBridgeDispatcher`，显式路由表分发，
覆盖面以该表 + 本文件对照表为准。）

## 兼容层（java/com/getcapacitor/）

新写，不是官方 Capacitor，只实现芷湘插件实际用到的子集：

- `JSObject` / `JSArray`：以 org.json 为底。特别处理了官方语义里的一条：
  `put(name, List)` 转 JSONArray —— HttpBridge 回传的 `setCookies` /
  `requestHeaderNames` 等 ArrayList 字段靠它在 JS 侧才是真数组，
  否则会像旧坑一样变成 `"[a, b]"` 字符串被误切。
- `PluginCall`：包住宿主的 options JSON + `PackCallback`。
  `resolve/reject` 与官方同形；`reject` 的 code 不单独过桥
  （宿主错误信封只有 message，芷湘 Web 层按 message 文案展示/判断）。
  `setKeepAlive` 只记标志：宿主桥是长连接回调，异步回调不会被回收。
- `Plugin`：`getContext()` 给应用 Context；`getActivity()` **恒为 null**
  （包内没有 Activity），依赖 Activity 的原代码逐个改走 Context 路线
  （见下面适配清单）；`notifyListeners` 直接转宿主 emit —— 宿主在桥上
  拦截 `addListener` 登记 JS 回调，包只管发事件，不维护监听表；
  `requestPermissionForAlias` 无法再弹窗（宿主打开包时已按 pack.json
  的 permissions 统一申请），实现为直接回调 `@PermissionCallback`
  方法复查权限，与官方「结果回到回调」的形状一致。
- 注解 `PluginMethod` / `annotation.CapacitorPlugin` /
  `annotation.Permission` / `annotation.PermissionCallback`：
  仅作标记让原源码原样编译，入口用显式路由表，不靠反射扫描。

## 原样移植（业务代码零改动）

| 文件 | 说明 |
|---|---|
| `HttpBridgePlugin.java` | OkHttp 通用 HTTP 桥，整文件原样（含 load() 清 CookieHandler、发头顺序、10MB 上限、setCookies 双份回传） |
| `StreamProxyServer.java` | 本机流代理（ServerSocket + OkHttp），整文件原样 |
| `EmbeddedLyrics.java` | 纯 Java 内嵌歌词解析（USLT/FLAC/M4A），整文件原样 |
| `LocalMusicPlugin.java` 的扫库/封面/sidecar/下载/存下载目录逻辑 | 逻辑原样，仅下面一条 import 级适配 |

## 适配清单（改了什么、为什么）

1. **LocalMusicPlugin**：`ContextCompat.checkSelfPermission` 改为
   `Context.checkSelfPermission`（API 23+，梦盒 minSdk 26 恒可用），
   免引 androidx.core。语义不变。其余方法原样。
2. **ExternalOpenPlugin.openUrl**：`getActivity()` 改 `getContext()` +
   原有的 `FLAG_ACTIVITY_NEW_TASK`（包内无 Activity）。结果结构
   `{handled}` 不变。
3. **PlaybackPlugin**（重写实现、保留方法面与事件约定）：
   原版 start/stop/setMedia/setPlaying/clearMedia 全是给
   `PlaybackService`（真 Service 组件）发 Intent —— 包内 dex 类不能被
   系统实例化成 Service，此路不通（`PlaybackService.java`、
   `LyricWallpaperService.java` 因此**不进包**）。包版改为进程内实现：
   `android.media.session.MediaSession`（系统播放卡；回调动作名
   play/pause/next/previous 与原版服务逐字一致，经 `mediaAction`
   事件回 Web 层）+ 一条普通通知展示曲目。`ensureProxy/stopProxy`
   与原版逐字一致、真实现（Web 层拿到端口才肯交 googlevideo 地址，
   绝不假造）。
4. **YtmSignInPlugin.signIn**（重写）：原版起 `YtmSignInActivity`
   （不进包）。包版走宿主 `PackAuthBus`：宿主打开
   `https://music.youtube.com/`（标题「YouTube Music 登录」），
   完成时回该页 Cookie，包按原版同一结构回 `{ok, cookie, userAgent}`。
   差异：原版检测到 SAPISID 自动交接、合并 youtube 两域 Cookie；
   包版需用户点宿主的「完成」、Cookie 只取 music.youtube.com 一域
   （SAPISID 是 .youtube.com 域 Cookie，在其中）；userAgent 原版取
   登录 WebView 的 UA，包版取同引擎默认 WebView UA（宿主授权壳未改
   UA，两者一致）。取消/无凭证 resolve `{ok:false}`，与原版一致。
5. **YtmSignInPlugin.probeWatchPage / extractViaPage**（适配）：
   JS、轮询节奏、双级捕获（音频轨优先、任意 videoplayback 兜底）、
   返回结构全部照原版；差别是 WebView 挂载方式：原版挂 Activity
   内容视图，包版用应用 Context 建 WebView + 手动 measure/layout
   驱动（点按中心坐标按 720x400 布局相应调整）。
6. **WallpaperPlugin.openLiveWallpaperPicker**：明确 reject
   （`UNSUPPORTED_IN_PACK`，中文说明）。动态壁纸服务必须由系统按
   Service 组件实例化，包做不到，宿主 v1 也未预声明。
   `setWallpaperState` 原样：照旧写 `zhixiang_wallpaper` 偏好，
   数据不丢（独立芷湘 App 仍可消费同一份）。

## 方法覆盖表（原版 @PluginMethod 共 23 个）

| 插件 | 方法 | 状态 |
|---|---|---|
| HttpBridge | request | ✅ 真实现（原样） |
| Playback | ensureProxy | ✅ 真实现（原样） |
| Playback | stopProxy | ✅ 真实现（原样） |
| Playback | start | ⚠️ 降级实现（进程内会话+普通通知，无前台服务） |
| Playback | stop | ⚠️ 降级实现（同上） |
| Playback | setMedia | ⚠️ 降级实现（会话元数据同步；不加载封面） |
| Playback | setPlaying | ⚠️ 降级实现（会话播放态同步） |
| Playback | clearMedia | ⚠️ 降级实现（清会话+撤通知） |
| LocalMusic | scan | ✅ 真实现（权限走宿主前置申请+兼容层回调复查） |
| LocalMusic | coverArt | ✅ 真实现 |
| LocalMusic | coverFile | ✅ 真实现 |
| LocalMusic | checkAllFilesAccess | ✅ 真实现 |
| LocalMusic | requestAllFilesAccess | ✅ 真实现（应用 Context + NEW_TASK 开设置页） |
| LocalMusic | writeSidecar | ✅ 真实现 |
| LocalMusic | downloadCover | ✅ 真实现 |
| LocalMusic | embeddedLyrics | ✅ 真实现 |
| LocalMusic | saveToDownloads | ✅ 真实现 |
| YtmSignIn | signIn | 🔧 适配实现（PackAuthBus，见适配 4） |
| YtmSignIn | probeWatchPage | 🔧 适配实现（离屏 WebView，见适配 5） |
| YtmSignIn | extractViaPage | 🔧 适配实现（离屏 WebView，见适配 5） |
| Wallpaper | setWallpaperState | ✅ 真实现（原样） |
| Wallpaper | openLiveWallpaperPicker | ❌ 明确拒绝（UNSUPPORTED_IN_PACK） |
| ExternalOpen | openUrl | ✅ 真实现（Context 路线） |

宿主桥拦截的 `addListener` / `removeListener` 不在原版 @PluginMethod
清单里（只在 PluginHeaders 上报），入口不处理、宿主直接登记/摘除。

## 已知 v1 降级（与独立芷湘 App 的差别）

- **后台保活变弱**：没有前台服务。梦盒进程活着时播放与系统播放卡
  正常；进程被系统回收，包与播放一起结束。长时间后台听歌的保活
  强度不如独立 App 的 PlaybackService。
- **系统播放卡无封面**：原版服务会下载封面图填会话/通知，包版只填
  标题/歌手/专辑文本。
- **通知是普通通知**：可被用户划掉；且依赖梦盒本体已授予的通知
  权限，未授予时静默不显示（不影响会话与播放）。
- **动态壁纸不可设**：`openLiveWallpaperPicker` 拒绝；壁纸状态数据
  仍照常存储。
- **YTM 登录多一步**：需在宿主授权页点「完成」，不像原版检测到
  SAPISID 自动交接；Cookie 只取一域（见适配 4）。
- **YTM 探针/页面抽链成功率可能下降**：离屏 WebView 未真正挂载到
  窗口，页面媒体管线可能被节流。Web 层对这两方法本来就按
  「失败回 null、走其它兜底」设计，不会因此断主流程，但撞
  LOGIN_REQUIRED 时的兜底能力弱于独立 App。

## 构建

- 脚本：`build-pack.sh`（本地与 CI 同一路）。流程：取芷湘 dist →
  取官方 native-bridge.js → javac 编译 stubs（仅 classpath）→
  javac 编译包内 Java（classpath = android.jar + okhttp 4.12.0 +
  okio + kotlin-stdlib + stubs）→ d8 出 plugins.dex（**程序输入只有
  包类**；stubs/okhttp/kotlin-stdlib 只作 `--classpath`/`--lib` 引用，
  运行时由宿主本体提供，父优先加载）→ 组装 dist + native-bridge.js
  + plugins.dex + pack.json → `out/zhixiang-pack-v1.zip`。
  脚本末尾有防呆：dex 字符串池里出现 stub 类描述符即失败退出。
- CI：`.github/workflows/build-zhixiang-pack.yml`（workflow_dispatch）：
  检出本仓库 + 公开仓库 anransu983-sketch/zhixiang 到 zhixiang-src，
  Node 20 构建 dist，JDK 17 + Android SDK 36 跑脚本，产物传 artifact
  并挂到本仓库 `packs` Release（与本体 PackStore 注册表的下载地址
  一一对应）。
- 本机（开发 VM）没有 JDK/Android SDK，脚本只能静态检查
  （`bash -n` + 方法覆盖自动对照），真编译以 CI 首跑为准。
