/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.suanran.dreambox.presentation.webview

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * 简易广告拦截器：WebView 内置，无需浏览器扩展。
 *
 * 在 [android.webkit.WebViewClient.shouldInterceptRequest] 中对命中
 * 广告/追踪域名的请求直接返回空响应，IP 测试页里的广告位就加载不出来了。
 */
object AdBlocker {

    private val blockedHosts = setOf(
        // Google 广告系
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "ads.google.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "googleads.g.doubleclick.net",
        "cm.g.doubleclick.net",
        "stats.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "pubads.g.doubleclick.net",
        "fundingchoicesmessages.google.com",
        // 百度联盟
        "cpro.baidustatic.com",
        "dup.baidustatic.com",
        "pos.baidu.com",
        "ubmcmm.baidustatic.com",
        "afd.baidu.com",
        "als.baidu.com",
        // 阿里 Tanx
        "tanx.com",
        "atanx.alicdn.com",
        "atanx2.alicdn.com",
        "p.tanx.com",
        // 360 / 腾讯 / 头条系广告
        "s.360.cn",
        "union.360.cn",
        "l.qq.com",
        "qzonestyle.gtimg.cn",
        "adsmind.toutiao.com",
        "pangolin-sdk-toutiao.com",
        "pangolin.snssdk.com",
        // 国际广告网络
        "amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "moatads.com",
        "adsrvr.org",
        "outbrain.com",
        "taboola.com",
        "mgid.com",
        "revcontent.com",
        "adblade.com",
        "zergnet.com",
        "triplelift.com",
        "indexww.com",
        "casalemedia.com",
        "contextweb.com",
        "spotxchange.com",
        "springserve.com",
        "tremorhub.com",
        "sharethrough.com",
        "nativo.com",
        "zedo.com",
        "advertising.com",
        "ads.yahoo.com",
        "ads.bing.com",
        "bat.bing.com",
        // 移动广告 SDK
        "vungle.com",
        "applovin.com",
        "mopub.com",
        "inmobi.com",
        "smaato.com",
        "fyber.com",
        "chartboost.com",
        "unityads.unity3d.com",
        "ironsrc.com",
        "adcolony.com",
        "tapjoy.com",
        "flurry.com",
        // 数据追踪（常随广告一起加载）
        "hotjar.com",
        "fullstory.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "matomo.cloud",
        "clicky.com",
        "statcounter.com",
        "histats.com",
        "51.la",
        "cnzz.com",
        "umeng.com",
        "umtrack.com",
    )

    /** 请求 URL 的 host 是否命中广告/追踪域名（子域名同样命中）。 */
    fun isBlocked(url: String): Boolean {
        val host = try {
            Uri.parse(url).host ?: return false
        } catch (_: Exception) {
            return false
        }
        val h = host.lowercase()
        return blockedHosts.any { blocked -> h == blocked || h.endsWith(".$blocked") }
    }

    /** 命中时返回的空响应：让 WebView 认为请求成功但无内容。 */
    fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
    }
}
