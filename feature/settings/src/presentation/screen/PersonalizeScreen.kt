package com.suanran.dreambox.feature.settings.presentation.screen

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.misc.BocchiPink
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import com.suanran.dreambox.presentation.component.misc.BocchiTextDark
import com.suanran.dreambox.presentation.component.misc.BocchiTextGray
import com.suanran.dreambox.presentation.component.misc.CustomIcons
import com.suanran.dreambox.presentation.component.misc.rememberCustomIconBitmap
import com.suanran.dreambox.presentation.component.navigation.BottomBarDestination
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.util.toast
import com.tencent.mmkv.MMKV
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 个性化（2026-11-03 参考琳）：应用图标预置、导航栏/模块/返回按钮自定义图片。 */
private data class AppIconVariant(
    val id: String,
    val name: String,
    val aliasSuffix: String,
    val swatch: Color,
)

private val APP_ICON_VARIANTS = listOf(
    AppIconVariant("default", "默认", ".DreamBoxLauncher", Color(0xFFF5EFE6)),
    AppIconVariant("pink", "粉樱", ".DreamBoxLauncherPink", Color(0xFFFF7BAC)),
    AppIconVariant("blue", "深蓝", ".DreamBoxLauncherBlue", Color(0xFF4A90D9)),
    AppIconVariant("dark", "曜黑", ".DreamBoxLauncherDark", Color(0xFF23232B)),
)

private const val ICON_VARIANT_KEY = "app_icon_variant"

private fun applyAppIconVariant(context: Context, variant: AppIconVariant) {
    val pm = context.packageManager
    val pkg = context.packageName
    APP_ICON_VARIANTS.forEach { v ->
        pm.setComponentEnabledSetting(
            ComponentName(pkg, pkg + v.aliasSuffix),
            if (v.id == variant.id) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}

private val MODULE_ICON_ROWS = listOf(
    "app" to "应用",
    "network" to "网络",
    "override" to "覆写",
    "meta" to "Meta 功能",
    "lab" to "实验室",
    "log" to "日志",
    "about" to "关于",
)

@Composable
fun PersonalizeScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val mmkv: MMKV = koinInject(named("settings"))
    var iconVariant by remember {
        mutableStateOf(mmkv.decodeString(ICON_VARIANT_KEY, "default").orEmpty().ifBlank { "default" })
    }

    var pendingKey by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val key = pendingKey
        pendingKey = null
        if (uri != null && key != null) {
            if (CustomIcons.saveFromUri(context, key, uri)) {
                context.toast("图标已更新")
            } else {
                context.toast("图片读取失败，换一张试试")
            }
        }
    }
    fun pick(key: String) {
        pendingKey = key
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "个性化",
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
                BocchiSectionTitle("应用图标")
                BocchiCard {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.space16, vertical = spacing.space14),
                        verticalArrangement = Arrangement.spacedBy(spacing.space10),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                        ) {
                            APP_ICON_VARIANTS.forEach { variant ->
                                val selected = iconVariant == variant.id
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(variant.swatch)
                                            .then(
                                                if (selected) {
                                                    Modifier.border(2.dp, BocchiPink, RoundedCornerShape(14.dp))
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable {
                                                iconVariant = variant.id
                                                mmkv.encode(ICON_VARIANT_KEY, variant.id)
                                                runCatching { applyAppIconVariant(context, variant) }
                                                context.toast("已切换为「${variant.name}」，桌面图标稍等片刻刷新")
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "梦",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (variant.id == "default") BocchiTextDark else Color.White,
                                        )
                                    }
                                    Text(
                                        text = variant.name,
                                        fontSize = 12.sp,
                                        color = if (selected) BocchiPink else BocchiTextGray,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        Text(
                            text = "预置款秒换；自定义图片款要云端打包，下一版补上工坊。",
                            fontSize = 11.sp,
                            color = BocchiTextGray,
                        )
                    }
                }
            }

            item {
                BocchiSectionTitle("导航栏图标")
                BocchiCard {
                    Column {
                        BottomBarDestination.entries.forEach { dest ->
                            CustomIconRow(
                                title = dest.label,
                                customKey = "tab_" + dest.name.lowercase(),
                                onPick = { pick("tab_" + dest.name.lowercase()) },
                            ) {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = null,
                                    tint = BocchiTextDark,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                BocchiSectionTitle("模块图标")
                BocchiCard {
                    Column {
                        MODULE_ICON_ROWS.forEach { (key, title) ->
                            CustomIconRow(
                                title = title,
                                customKey = "module_$key",
                                onPick = { pick("module_$key") },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(BocchiPink.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = title.take(1),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BocchiTextDark,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                BocchiSectionTitle("返回按钮")
                BocchiCard {
                    Column {
                        CustomIconRow(
                            title = "返回按钮图标",
                            customKey = "back_button",
                            onPick = { pick("back_button") },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = BocchiTextDark,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = "所有页面的返回按钮统一用这个图；系统全面屏手势返回照常有效。",
                            fontSize = 11.sp,
                            color = BocchiTextGray,
                            modifier = Modifier.padding(
                                start = spacing.space16,
                                end = spacing.space16,
                                bottom = spacing.space12,
                            ),
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CustomIconRow(
    title: String,
    customKey: String,
    onPick: () -> Unit,
    defaultPreview: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val custom = rememberCustomIconBitmap(customKey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.space16, vertical = spacing.space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (custom != null) {
                Image(
                    bitmap = custom,
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape),
                )
            } else {
                defaultPreview()
            }
        }
        Spacer(modifier = Modifier.width(spacing.space12))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = BocchiTextDark,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "换图",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.clickable { onPick() },
        )
        if (custom != null) {
            Spacer(modifier = Modifier.width(spacing.space16))
            Text(
                text = "恢复默认",
                fontSize = 13.sp,
                color = BocchiTextGray,
                modifier = Modifier.clickable { CustomIcons.clear(context, customKey) },
            )
        }
    }
}
