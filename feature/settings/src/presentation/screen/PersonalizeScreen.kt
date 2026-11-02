package com.suanran.dreambox.feature.settings.presentation.screen

import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Switch
import com.suanran.dreambox.presentation.component.misc.rememberHomePanels
import com.suanran.dreambox.presentation.component.misc.HomePanels
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextField
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
    AppIconVariant("custom", "自定义", ".DreamBoxLauncherCustom", Color(0xFFB79CFF)),
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
                                                if (variant.id == "custom") {
                                                    context.toast("已切到自定义款：在下面工坊传图构建、装上定制版后生效")
                                                } else {
                                                    context.toast("已切换为「${variant.name}」，桌面图标稍等片刻刷新")
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (variant.id == "custom") "图" else "梦",
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
                            text = "预置款秒换；自定义款在下面工坊传图、云端打包后安装定制版生效。",
                            fontSize = 11.sp,
                            color = BocchiTextGray,
                        )
                        IconWorkshopBlock(mmkv = mmkv)
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
                BocchiSectionTitle("首页面板")
                BocchiCard {
                    Column {
                        val panelContext = LocalContext.current
                        val (panelOrder, panelHidden) = rememberHomePanels()
                        panelOrder.forEachIndexed { index, key ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.space16, vertical = spacing.space8),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = HomePanels.LABELS[key] ?: key,
                                    fontSize = 13.sp,
                                    color = BocchiTextDark,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    text = "↑",
                                    onClick = { HomePanels.move(panelContext, key, -1) },
                                    enabled = index > 0,
                                )
                                TextButton(
                                    text = "↓",
                                    onClick = { HomePanels.move(panelContext, key, 1) },
                                    enabled = index < panelOrder.lastIndex,
                                )
                                Switch(
                                    checked = key !in panelHidden,
                                    onCheckedChange = { show ->
                                        HomePanels.setHidden(
                                            panelContext,
                                            if (show) panelHidden - key else panelHidden + key,
                                        )
                                    },
                                )
                            }
                        }
                        Text(
                            text = "首页顶部的流量与开关区固定，其余卡片可在这里调顺序、关掉不要的。",
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

private const val GH_REPO = "anransu983-sketch/dreambox-asbefore"
private const val TOKEN_KEY = "icon_build_token"

@Composable
private fun IconWorkshopBlock(mmkv: MMKV) {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var token by remember { mutableStateOf(mmkv.decodeString(TOKEN_KEY, "").orEmpty()) }
    var status by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val tk = token.trim()
            if (tk.isEmpty()) {
                context.toast("先填构建令牌并保存")
            } else {
                status = "正在上传图片并触发构建…"
                scope.launch {
                    status = runCatching {
                        uploadIconAndDispatch(context, uri, tk)
                        "已开始构建：约 5–10 分钟后点「构建状态」确认，好了再开下载页安装"
                    }.getOrElse { "失败：" + (it.message ?: it.toString()) }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space10)) {
        Text(
            text = "自定义图片工坊",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = BocchiTextDark,
        )
        Text(
            text = "传一张图 → 云端打包（约 5–10 分钟）→ 装定制版，桌面图标就变成你的图。定制版与正式版同版本同签名，可互相覆盖装。令牌需要该仓库的 Contents 与 Actions 写权限，只存在本机。",
            fontSize = 11.sp,
            color = BocchiTextGray,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = token,
                onValueChange = { token = it },
                label = "构建令牌",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(spacing.space12))
            Text(
                text = "保存",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    mmkv.encode(TOKEN_KEY, token.trim())
                    context.toast("令牌已保存")
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.space16)) {
            Text(
                text = "上传图片并开始构建",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            Text(
                text = "构建状态",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val tk = token.trim()
                    if (tk.isEmpty()) {
                        context.toast("先填构建令牌并保存")
                    } else {
                        status = "查询中…"
                        scope.launch {
                            status = runCatching { fetchLatestRunStatus(tk) }
                                .getOrElse { "查询失败：" + (it.message ?: it.toString()) }
                        }
                    }
                },
            )
            Text(
                text = "打开定制版下载",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/$GH_REPO/releases/tag/custom-icon"),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
        if (status.isNotEmpty()) {
            Text(text = status, fontSize = 11.5.sp, color = BocchiTextGray)
        }
    }
}

private fun ghApi(
    method: String,
    path: String,
    token: String,
    body: org.json.JSONObject?,
): Pair<Int, String> {
    val conn = java.net.URL("https://api.github.com$path").openConnection() as java.net.HttpURLConnection
    conn.requestMethod = method
    conn.setRequestProperty("Authorization", "Bearer $token")
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    conn.connectTimeout = 20000
    conn.readTimeout = 30000
    if (body != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    }
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    return code to text
}

private suspend fun uploadIconAndDispatch(context: Context, uri: android.net.Uri, token: String) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("图片读取失败")
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val (getCode, getText) = ghApi("GET", "/repos/$GH_REPO/contents/custom-icon/icon.png?ref=main", token, null)
        val sha = if (getCode == 200) {
            runCatching { org.json.JSONObject(getText).optString("sha") }.getOrNull()
        } else {
            null
        }
        val putBody = org.json.JSONObject()
            .put("message", "workshop: custom app icon")
            .put("content", b64)
            .put("branch", "main")
        if (!sha.isNullOrEmpty()) putBody.put("sha", sha)
        val (putCode, putText) = ghApi("PUT", "/repos/$GH_REPO/contents/custom-icon/icon.png", token, putBody)
        if (putCode !in 200..299) {
            throw IllegalStateException("上传失败（$putCode）：${putText.take(140)}")
        }
        val dispatchBody = org.json.JSONObject()
            .put("ref", "main")
            .put("inputs", org.json.JSONObject().put("custom_icon", "true"))
        val (code, text) = ghApi(
            "POST",
            "/repos/$GH_REPO/actions/workflows/build-dreambox-apk.yml/dispatches",
            token,
            dispatchBody,
        )
        if (code !in 200..299) {
            throw IllegalStateException("触发构建失败（$code）：${text.take(140)}")
        }
    }
}

private suspend fun fetchLatestRunStatus(token: String): String {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val (code, text) = ghApi(
            "GET",
            "/repos/$GH_REPO/actions/workflows/build-dreambox-apk.yml/runs?per_page=1",
            token,
            null,
        )
        if (code != 200) throw IllegalStateException("HTTP $code")
        val run = org.json.JSONObject(text).optJSONArray("workflow_runs")?.optJSONObject(0)
            ?: return@withContext "还没有构建记录"
        val status = run.optString("status")
        val conclusion = run.optString("conclusion")
        val created = run.optString("created_at").take(16).replace("T", " ")
        when {
            status != "completed" -> "最近一次构建进行中（开始于 $created UTC）"
            conclusion == "success" -> "最近一次构建已成功（$created UTC）：若那是你的定制构建，去下载页装定制版"
            else -> "最近一次构建失败（$created UTC，结论 $conclusion）"
        }
    }
}
