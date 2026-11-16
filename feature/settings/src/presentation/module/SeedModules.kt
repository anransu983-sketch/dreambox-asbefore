package com.suanran.dreambox.feature.settings.presentation.module

import org.json.JSONArray
import org.json.JSONObject

/**
 * 三个种子模块（内联小名单，域名都是公开周知的广告/追踪服务，
 * 不含任何编造的规则源地址）。
 *
 * 注意：这里的种子内容必须和 modules.json 远程索引里的三条保持一致，
 * 改一边就要改另一边；种子是远程索引拉不到时的兜底，市场打开就有东西可装。
 */
object SeedModules {
    val AD_BLOCK = ModuleDef(
        id = "adblock",
        name = "去广告",
        desc = "拦截常见广告联盟域名，页面和 App 里的横幅、弹窗广告直接不加载",
        author = "梦盒内置",
        homepage = "",
        version = "1",
        category = "去广告",
        sourceUrl = "",
        rules = listOf(
            "DOMAIN-SUFFIX,doubleclick.net,REJECT",
            "DOMAIN-SUFFIX,googleadservices.com,REJECT",
            "DOMAIN-SUFFIX,googlesyndication.com,REJECT",
            "DOMAIN-SUFFIX,googleads.g.doubleclick.net,REJECT",
            "DOMAIN-SUFFIX,pagead2.googlesyndication.com,REJECT",
            "DOMAIN-SUFFIX,adservice.google.com,REJECT",
            "DOMAIN-SUFFIX,ads.yahoo.com,REJECT",
            "DOMAIN-SUFFIX,adnxs.com,REJECT",
            "DOMAIN-SUFFIX,adsrvr.org,REJECT",
            "DOMAIN-SUFFIX,criteo.com,REJECT",
            "DOMAIN-SUFFIX,moatads.com,REJECT",
            "DOMAIN-SUFFIX,scorecardresearch.com,REJECT",
            "DOMAIN-SUFFIX,ads-twitter.com,REJECT",
            "DOMAIN-SUFFIX,amazon-adsystem.com,REJECT",
            "DOMAIN-SUFFIX,taboola.com,REJECT",
            "DOMAIN-SUFFIX,outbrain.com,REJECT",
        ),
    )

    val TRACKING = ModuleDef(
        id = "tracking",
        name = "去追踪",
        desc = "拦截统计与行为追踪域名，不向广告商回传浏览行为",
        author = "梦盒内置",
        homepage = "",
        version = "1",
        category = "隐私",
        sourceUrl = "",
        rules = listOf(
            "DOMAIN-SUFFIX,google-analytics.com,REJECT",
            "DOMAIN-SUFFIX,googletagmanager.com,REJECT",
            "DOMAIN-SUFFIX,analytics.google.com,REJECT",
            "DOMAIN-SUFFIX,stats.g.doubleclick.net,REJECT",
            "DOMAIN-SUFFIX,hotjar.com,REJECT",
            "DOMAIN-SUFFIX,static.hotjar.com,REJECT",
            "DOMAIN-SUFFIX,mixpanel.com,REJECT",
            "DOMAIN-SUFFIX,amplitude.com,REJECT",
            "DOMAIN-SUFFIX,segment.com,REJECT",
            "DOMAIN-SUFFIX,segment.io,REJECT",
            "DOMAIN-SUFFIX,fullstory.com,REJECT",
            "DOMAIN-SUFFIX,clarity.microsoft.com,REJECT",
        ),
    )

    val SPLASH = ModuleDef(
        id = "splash",
        name = "开屏广告",
        desc = "拦截国内常见开屏与信息流广告 SDK 的域名，开屏广告拉不到素材就直接跳过",
        author = "梦盒内置",
        homepage = "",
        version = "1",
        category = "去广告",
        sourceUrl = "",
        rules = listOf(
            "DOMAIN-SUFFIX,gdt.qq.com,REJECT",
            "DOMAIN-SUFFIX,ad.qq.com,REJECT",
            "DOMAIN-SUFFIX,cpro.baidu.com,REJECT",
            "DOMAIN-SUFFIX,ads.baidu.com,REJECT",
            "DOMAIN-SUFFIX,pos.baidu.com,REJECT",
            "DOMAIN-SUFFIX,alog.umeng.com,REJECT",
            "DOMAIN-SUFFIX,ad.xiaomi.com,REJECT",
            "DOMAIN-SUFFIX,api.ad.xiaomi.com,REJECT",
            "DOMAIN-SUFFIX,union.bytedance.com,REJECT",
        ),
    )

    val VIDEO_ADS = ModuleDef(
        id = "videoads",
        name = "视频去广告",
        desc = "拦截视频广告 SDK 与片前贴片域名，视频片头广告直接不加载",
        author = "梦盒内置",
        homepage = "",
        version = "1",
        category = "去广告",
        sourceUrl = "",
        rules = listOf(
            "DOMAIN-SUFFIX,imasdk.googleapis.com,REJECT",
            "DOMAIN-SUFFIX,pubads.g.doubleclick.net,REJECT",
            "DOMAIN-SUFFIX,s0.2mdn.net,REJECT",
            "DOMAIN-SUFFIX,2mdn.net,REJECT",
            "DOMAIN-SUFFIX,ad.doubleclick.net,REJECT",
            "DOMAIN-SUFFIX,g.doubleclick.net,REJECT",
        ),
    )

    val ANTI_MALWARE = ModuleDef(
        id = "antimalware",
        name = "反恶意域名",
        desc = "拦截挖矿脚本与恶意域名，防后台偷跑算力与恶意跳转",
        author = "梦盒内置",
        homepage = "",
        version = "1",
        category = "安全",
        sourceUrl = "",
        rules = listOf(
            "DOMAIN-SUFFIX,coinhive.com,REJECT",
            "DOMAIN-SUFFIX,coin-hive.com,REJECT",
            "DOMAIN-SUFFIX,cryptoloot.pro,REJECT",
            "DOMAIN-SUFFIX,minero.cc,REJECT",
            "DOMAIN-SUFFIX,coinimp.com,REJECT",
            "DOMAIN-SUFFIX,jsecoin.com,REJECT",
            "DOMAIN-SUFFIX,webminepool.com,REJECT",
        ),
    )

    val PRIVACY_PLUS = ModuleDef(
        id = "privacyplus",
        name = "隐私增强",
        desc = "拦截会话录制与指纹追踪，不让网站回放你的操作与设备指纹",
        author = "梦盒内置",
        homepage = "",
        version = "1",
        category = "隐私",
        sourceUrl = "",
        rules = listOf(
            "DOMAIN-SUFFIX,mouseflow.com,REJECT",
            "DOMAIN-SUFFIX,luckyorange.com,REJECT",
            "DOMAIN-SUFFIX,inspectlet.com,REJECT",
            "DOMAIN-SUFFIX,smartlook.com,REJECT",
            "DOMAIN-SUFFIX,logrocket.com,REJECT",
            "DOMAIN-SUFFIX,heap.io,REJECT",
            "DOMAIN-SUFFIX,pendo.io,REJECT",
            "DOMAIN-SUFFIX,api.fingerprint.com,REJECT",
            "DOMAIN-SUFFIX,fingerprint.com,REJECT",
        ),
    )

    val SHOPPING = ModuleDef(
        id = "shopping",
        name = "购物净化",
        desc = "拦截电商联盟与返利追踪，购物链接不再被挂返利与追踪参数",
        author = "梦盒内置",
        homepage = "",
        version = "1",
        category = "去广告",
        sourceUrl = "",
        rules = listOf(
            "DOMAIN-SUFFIX,awin.com,REJECT",
            "DOMAIN-SUFFIX,linksynergy.com,REJECT",
            "DOMAIN-SUFFIX,shareasale.com,REJECT",
            "DOMAIN-SUFFIX,cj.com,REJECT",
            "DOMAIN-SUFFIX,impact.com,REJECT",
            "DOMAIN-SUFFIX,skimlinks.com,REJECT",
            "DOMAIN-SUFFIX,viglink.com,REJECT",
            "DOMAIN-SUFFIX,partnerize.com,REJECT",
        ),
    )

    val ALL: List<ModuleDef> = listOf(AD_BLOCK, TRACKING, SPLASH, VIDEO_ADS, ANTI_MALWARE, PRIVACY_PLUS, SHOPPING)

    /** 生成远程索引 modules.json 的内容（和本文件三条一致）。 */
    fun toIndexJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        val arr = JSONArray()
        ALL.forEach { arr.put(it.toJson()) }
        root.put("modules", arr)
        return root.toString(2)
    }
}
