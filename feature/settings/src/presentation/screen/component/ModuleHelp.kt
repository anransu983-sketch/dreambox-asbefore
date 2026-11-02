package com.suanran.dreambox.feature.settings.presentation.screen.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.component.dialog.AppDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 功能说明（2026-11-03 用户点单：不要碍眼、又便于查看，方便新手上手）：
 * 每个模块页顶栏右上角一个小 ⓘ，点开是本页的新手向说明；
 * 「关于」页另有一行低调的「功能说明」总览，内容与这里共用一份数据。
 */
data class ModuleHelpEntry(
    val key: String,
    val title: String,
    val intro: String,
    val tips: List<String>,
)

object ModuleHelp {
    val ENTRIES: List<ModuleHelpEntry> = listOf(
        ModuleHelpEntry(
            key = "app",
            title = "应用设置",
            intro = "管梦盒自己的样子和行为：主题、语言、首页长什么样、隐私和后台行为都在这里。",
            tips = listOf(
                "新手先看：第一节「主题」换模式和配色，效果立刻可见。",
                "「隐藏应用图标」打开后桌面找不到梦盒，用拨号盘 *#*#0721#*#* 打开。",
                "流量通知、日志级别在最下面的「服务」一节。",
            ),
        ),
        ModuleHelpEntry(
            key = "network",
            title = "网络设置",
            intro = "管代理怎么跑：运行模式、服务配置、访问控制，以及当前用的是哪个内核。",
            tips = listOf(
                "没有 Root 的手机选 Vpn Service 就够了，Tun / eBPF 需要 Root。",
                "某个 App 不想走代理：进「访问控制」把它设成直连。",
                "切换、升级内核点最下面的「当前内核」，在内核管家插件里操作。",
            ),
        ),
        ModuleHelpEntry(
            key = "override",
            title = "覆写配置",
            intro = "覆写 = 在订阅配置之上再叠一层修改（加规则、改 DNS、防泄露），不用动订阅本身。",
            tips = listOf(
                "「自带」页是内置的 4 个常用覆写；「导入」页放你自己加的和快速规则。",
                "点「应用」是把覆写挂到当前配置上，不是打开开关就完事。",
                "番茄小说去广告这类现成覆写，导入后应用即可。",
            ),
        ),
        ModuleHelpEntry(
            key = "meta",
            title = "Meta 功能",
            intro = "和 mihomo 内核运行时直接相关的功能：规则临时开关、自定义分流、Geo 数据库更新。",
            tips = listOf(
                "某条规则想临时停用：进「规则」点开关，不用改配置。",
                "GeoX 是 IP 归属地数据库，定期点一次「更新 GeoX」保持新鲜。",
            ),
        ),
        ModuleHelpEntry(
            key = "theme",
            title = "主题",
            intro = "换主题、装主题，以及个性化：应用图标、导航栏和模块图标、返回按钮、首页面板都在这一页。",
            tips = listOf(
                "主题模式和配色在「应用设置」第一节，这里管主题包本身的安装与切换。",
                "想把桌面图标换成自己的图：看下面「个性化」里的应用图标工坊。",
            ),
        ),
        ModuleHelpEntry(
            key = "lab",
            title = "实验室",
            intro = "放插件、模块开关和 Age 密钥这类进阶/小众工具，稳定后会移到外面。",
            tips = listOf(
                "插件是按需下载的功能包（节点部署、SSH 终端、云备份等），不用可以删掉腾空间。",
                "「模块」里可以关掉不用的页面功能，设置页立刻变清爽。",
            ),
        ),
        ModuleHelpEntry(
            key = "packs",
            title = "插件",
            intro = "重型功能做成按需下载的插件包：装上才能用，删掉不影响本体。",
            tips = listOf(
                "一键节点：有 VPS 时填 IP 和密码，自动部署节点并给出订阅。",
                "VPS 终端：把服务器存一次，之后点一下就进 SSH 终端。",
                "WebDAV 云备份、流媒体解锁、内核管家也都在这里。",
            ),
        ),
        ModuleHelpEntry(
            key = "log",
            title = "日志",
            intro = "梦盒自己记的运行日志，出问题时靠它定位原因。",
            tips = listOf(
                "反馈问题时把 app-diagnostic.log 导出发出来最有用。",
                "0 字节的空日志会自动隐藏，不用手动清。",
            ),
        ),
        ModuleHelpEntry(
            key = "about",
            title = "关于",
            intro = "版本信息、检查更新、项目链接和开源许可证。",
            tips = listOf(
                "应用内更新下载走本机代理口，卡住会自动换源，不用干等。",
            ),
        ),
    )

    fun find(key: String): ModuleHelpEntry? = ENTRIES.firstOrNull { it.key == key }
}

/** 顶栏右上角的小 ⓘ：点开本页说明。不占版面，不打扰。 */
@Composable
fun ModuleHelpAction(helpKey: String) {
    val entry = remember(helpKey) { ModuleHelp.find(helpKey) } ?: return
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }) {
        Icon(
            imageVector = MiuixIcons.Info,
            contentDescription = "功能说明",
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(20.dp),
        )
    }
    AppDialog(
        show = show,
        title = entry.title + " · 说明",
        onDismissRequest = { show = false },
    ) {
        Column {
            Text(
                text = entry.intro,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            entry.tips.forEach { tip ->
                Text(
                    text = "· $tip",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}
