package com.suanran.dreambox.feature.settings.presentation.screen.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.suanran.dreambox.presentation.component.misc.BocchiPink
import com.suanran.dreambox.presentation.component.misc.BocchiStar
import top.yukonga.miuix.kmp.basic.Text

/**
 * 「一张大卡」版式（2026-11-03 用户点单：设置页不要一节一张卡、太割裂）：
 * 整页一张 BocchiCard，分节标题收进卡内（粉色小字），节与节之间一条细分隔线。
 */
@Composable
fun OneCardSectionHeader(title: String, first: Boolean = false) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = BocchiStar,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = if (first) 14.dp else 10.dp,
            bottom = 2.dp,
        ),
    )
}

/** 卡内分节细线：与卡描边同色系、低透明，不抢眼。 */
@Composable
fun OneCardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(1.dp)
            .background(BocchiPink.copy(alpha = 0.18f)),
    )
}
