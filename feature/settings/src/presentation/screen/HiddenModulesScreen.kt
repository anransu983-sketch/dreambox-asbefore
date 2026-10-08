package com.suanran.dreambox.feature.settings.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suanran.dreambox.feature.settings.presentation.custommodule.CustomModuleStore
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.suanran.dreambox.presentation.component.sortable.saveHiddenSections
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class HiddenGroup(
    val pageKey: String,
    val pageTitle: String,
    val titles: Map<String, String>,
)

/**
 * 统一的已隐藏模块管理页：所有页面隐藏的分节/模块都在这里恢复。
 */
@Composable
fun HiddenModulesScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = AppTheme.spacing
    val mmkv: com.tencent.mmkv.MMKV = koinInject(named("settings"))
    val customStore = remember { CustomModuleStore(mmkv) }

    // 各页面的隐藏配置
    val groups = listOf(
        HiddenGroup(
            pageKey = "main",
            pageTitle = "设置主页",
            titles = mapOf(
                "app" to "应用", "network" to "网络", "override" to "覆写",
                "meta" to "Meta 功能", "lab" to "实验室", "log" to "日志", "about" to "关于",
            ),
        ),
        HiddenGroup("app_settings", "应用设置", mapOf(
            "behavior" to "行为", "interface" to "界面", "navigation" to "导航",
            "privacy" to "隐私", "service" to "服务", "network" to "网络",
        )),
        HiddenGroup("network_settings", "网络设置", mapOf(
            "runmode" to "运行模式", "advanced" to "高级选项", "access" to "访问控制", "kernel" to "内核管理",
        )),
        HiddenGroup("meta_feature", "Meta 功能", mapOf(
            "conn" to "连接与流量", "routing" to "分流相关", "panel" to "面板",
            "backup" to "备份恢复", "webdav" to "WebDAV", "agekey" to "Age 密钥",
        )),
        HiddenGroup("about", "关于", mapOf(
            "info" to "应用信息", "links" to "项目链接", "license" to "许可证",
        )),
        HiddenGroup("lab", "实验室", mapOf(
            "theme" to "主题",
            "iptest" to "IP 测试", "music" to "音乐库",
            "hidden" to "已隐藏的模块",
        )),
    )

    var refreshTick by remember { mutableStateOf(0) }

    // 读取各组隐藏项
    fun loadGroupHidden(group: HiddenGroup): List<String> {
        return if (group.pageKey == "main") {
            mmkv.decodeString("settings_module_hidden", "").orEmpty()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            loadHiddenSections(mmkv, group.pageKey).toList()
        }
    }

    fun restore(group: HiddenGroup, key: String) {
        if (group.pageKey == "main") {
            val cur = mmkv.decodeString("settings_module_hidden", "").orEmpty()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            cur.remove(key)
            mmkv.encode("settings_module_hidden", cur.joinToString(","))
        } else {
            val cur = loadHiddenSections(mmkv, group.pageKey).toMutableSet()
            cur.remove(key)
            saveHiddenSections(mmkv, group.pageKey, cur)
        }
        refreshTick++
    }

    // 自定义模块的隐藏项
    var customHidden by remember(refreshTick) {
        mutableStateOf(customStore.load().filter { it.hidden })
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "已隐藏的模块",
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
            var hasAny = false
            groups.forEach { group ->
                val hidden = loadGroupHidden(group)
                if (hidden.isNotEmpty()) {
                    hasAny = true
                    item(key = "header_${group.pageKey}") {
                        Text(
                            text = group.pageTitle,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(hidden.size) { i ->
                        val key = hidden[i]
                        BocchiCard(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { restore(group, key) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "恢复",
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = group.titles[key] ?: key,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            // 自定义模块的隐藏项
            if (customHidden.isNotEmpty()) {
                hasAny = true
                item(key = "header_custom") {
                    Text(
                        text = "自定义模块",
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(customHidden.size) { i ->
                    val m = customHidden[i]
                    BocchiCard(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                val all = customStore.load()
                                customStore.save(all.map { if (it.id == m.id) it.copy(hidden = false) else it })
                                customHidden = customStore.load().filter { it.hidden }
                                refreshTick++
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "恢复",
                                tint = MiuixTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = m.name,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            if (!hasAny) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(48.dp))
                        Text(
                            text = "没有隐藏的模块",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
