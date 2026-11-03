package com.suanran.dreambox.feature.settings.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.misc.PreferenceSwitchItem
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.feature.settings.presentation.pack.PACKS_VISIBILITY_SCOPE
import com.suanran.dreambox.feature.settings.presentation.pack.PackStore
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.suanran.dreambox.presentation.component.sortable.saveHiddenSections
import com.suanran.dreambox.presentation.navigation.Navigator
import com.tencent.mmkv.MMKV
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class ModuleToggle(val key: String, val title: String, val summary: String)

private val MAIN_MODULES = listOf(
    ModuleToggle("app", "应用", "应用设置入口"),
    ModuleToggle("network", "网络", "网络设置入口"),
    ModuleToggle("override", "覆写", "覆写配置入口"),
    ModuleToggle("meta", "Meta 功能", "Meta 功能入口"),
    ModuleToggle("lab", "实验室", "插件 · 模块 · 密钥"),
    ModuleToggle("log", "日志", "日志入口"),
    ModuleToggle("about", "关于", "关于入口"),
)

private val LAB_MODULES = listOf(
    ModuleToggle("theme", "主题", "浏览、安装和管理主题"),
    ModuleToggle("packs", "插件", "重型功能按需下载，删掉腾空间"),
    ModuleToggle("hidden", "已隐藏的模块", "管理所有隐藏的功能模块"),
)

private const val MAIN_HIDDEN_KEY = "settings_module_hidden"

private fun loadMainHidden(mmkv: MMKV): Set<String> =
    mmkv.decodeString(MAIN_HIDDEN_KEY, "").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

private fun saveMainHidden(mmkv: MMKV, hidden: Set<String>) {
    mmkv.encode(MAIN_HIDDEN_KEY, hidden.joinToString(","))
}

/**
 * 模块：各页面的功能模块开关。与既有的「隐藏模块」是同一套存储：
 * 在这里关掉的模块会从对应页面隐藏，也能在「已隐藏的模块」页恢复。
 */
@Composable
fun ModulesScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val mmkv: MMKV = koinInject(named("settings"))
    var mainHidden by remember { mutableStateOf(loadMainHidden(mmkv)) }
    var labHidden by remember { mutableStateOf(loadHiddenSections(mmkv, "lab")) }
    var packHidden by remember { mutableStateOf(loadHiddenSections(mmkv, PACKS_VISIBILITY_SCOPE)) }

    Scaffold(
        topBar = {
            TopBar(
                title = "模块",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "关掉的模块会从对应页面里隐藏。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                Text(
                    text = "设置主页模块",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                BocchiCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        MAIN_MODULES.forEach { module ->
                            PreferenceSwitchItem(
                                title = module.title,
                                summary = module.summary,
                                checked = module.key !in mainHidden,
                                onCheckedChange = { on ->
                                    mainHidden = if (on) mainHidden - module.key else mainHidden + module.key
                                    saveMainHidden(mmkv, mainHidden)
                                },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "实验室模块",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                BocchiCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        LAB_MODULES.forEach { module ->
                            PreferenceSwitchItem(
                                title = module.title,
                                summary = module.summary,
                                checked = module.key !in labHidden,
                                onCheckedChange = { on ->
                                    labHidden = if (on) labHidden - module.key else labHidden + module.key
                                    saveHiddenSections(mmkv, "lab", labHidden)
                                },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "插件模块",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                BocchiCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        PackStore.REGISTRY.forEach { def ->
                            PreferenceSwitchItem(
                                title = def.name,
                                summary = "插件页里的这个包",
                                checked = def.id !in packHidden,
                                onCheckedChange = { on ->
                                    packHidden = if (on) packHidden - def.id else packHidden + def.id
                                    saveHiddenSections(mmkv, PACKS_VISIBILITY_SCOPE, packHidden)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
