package com.suanran.dreambox.feature.home.presentation.screen

import kotlin.math.abs
import kotlin.random.Random

/*
 * 居民台词引擎，思路同 HarborTalk：零件拼句 + 状态池 + 最近 80 句整句去重。
 * 和港口版的两处不同：
 *   1. 「点一下」的汇报句也走拼句法，但汇报核心句里的 {空位} 必须是真数据；某个数据是 null，
 *      含这个空位的句子整条不参选（绝不出现「几十毫秒」这种假数字），全空就走 noData 池。
 *   2. 一句话要塞进两行光字（每行 ≤ 13 字），所以拼出来超长会重拼，实在拼不短就丢掉开头/收尾。
 */

/** 说话能引用的真实状态。全部由 SkyHomeState 映射而来，见 [SkyHomeState.toTalk]。 */
internal data class ResidentTalkState(
    val connected: Boolean,
    val nodeName: String? = null,
    val uptimeText: String? = null,
    val latencyMs: Int? = null,
    val downText: String? = null,
    val upText: String? = null,
    val todayTrafficText: String? = null,
    val profileName: String? = null,
    val quotaLeftText: String? = null,
    val hour: Int = 22,
)

private class Tpl(val text: String) {
    val keys: List<String> = SLOT.findAll(text).map { it.groupValues[1] }.toList()

    companion object {
        private val SLOT = Regex("\\{(\\w+)\\}")
    }
}

private class Voice(
    val openers: List<String>,
    val topics: List<String>,
    val closers: List<String>,
    /** 点一下的汇报核心句，含真数据空位。 */
    val reports: List<String>,
    /** 汇报所需数据全空时说的话。 */
    val noData: List<String>,
) {
    val reportTpls = reports.map(::Tpl)
    val topicTpls = topics.map(::Tpl)
}

internal class ResidentTalker {
    private val recent = ArrayDeque<String>()

    /** 点人：说一句带真数据的汇报。 */
    fun report(id: ResidentId, s: ResidentTalkState): String {
        val voice = Lines.voices.getValue(id)
        val slots = slotsOf(s)
        val rnd = Random(System.nanoTime())
        val usable = voice.reportTpls.filter { t -> t.keys.all { !slots[it].isNullOrBlank() } }
        return pickFresh(rnd) {
            val core =
                if (usable.isEmpty()) voice.noData.random(rnd) else fill(usable.random(rnd).text, slots)
            fit(
                opener = if (rnd.nextFloat() < 0.45f) voice.openers.random(rnd) else "",
                core = core,
                closer = if (rnd.nextFloat() < 0.3f) voice.closers.random(rnd) else "",
            )
        }
    }

    /** 平时自发冒泡：闲话 + 共用话题 + 状态池。 */
    fun chatter(id: ResidentId, s: ResidentTalkState): String {
        val voice = Lines.voices.getValue(id)
        val slots = slotsOf(s)
        val rnd = Random(System.nanoTime())
        val pool = ArrayList<String>()
        voice.topicTpls.forEach { t -> if (t.keys.all { !slots[it].isNullOrBlank() }) pool += t.text }
        pool += Lines.shared
        when {
            !s.connected -> pool += Lines.offline
            s.latencyMs != null && s.latencyMs <= 150 -> pool += Lines.goodLine
            s.latencyMs != null && s.latencyMs > 400 -> pool += Lines.badLine
        }
        if (s.hour in 0..5) pool += Lines.deepNight
        return pickFresh(rnd) {
            fit(
                opener = if (rnd.nextFloat() < 0.5f) voice.openers.random(rnd) else "",
                core = fill(pool.random(rnd), slots),
                closer = if (rnd.nextFloat() < 0.35f) voice.closers.random(rnd) else "",
            )
        }
    }

    private inline fun pickFresh(rnd: Random, make: () -> String): String {
        var line = make()
        var guard = 0
        while (line in recent && guard++ < 12) line = make()
        recent.addLast(line)
        while (recent.size > 80) recent.removeFirst()
        return line
    }

    private fun slotsOf(s: ResidentTalkState): Map<String, String?> =
        mapOf(
            "node" to s.nodeName?.let { clipText(it, 12) },
            "uptime" to s.uptimeText,
            "ping" to s.latencyMs?.let { "$it 毫秒" },
            "feel" to s.latencyMs?.let(::pingFeel),
            "down" to s.downText,
            "up" to s.upText,
            "today" to s.todayTrafficText,
            "profile" to s.profileName?.let { clipText(it, 10) },
            "quota" to s.quotaLeftText,
        )

    private fun fill(template: String, slots: Map<String, String?>): String {
        var out = template
        for ((k, v) in slots) if (v != null) out = out.replace("{$k}", v)
        return out
    }

    /** 两行光字塞得下：总长 ≤ 26；超了先丢收尾、再丢开头。核心句本身超长由 [splitBubble] 兜底截断。 */
    private fun fit(opener: String, core: String, closer: String): String {
        val full = opener + core + closer
        if (full.length <= MAX_TOTAL) return full
        val noCloser = opener + core
        if (noCloser.length <= MAX_TOTAL) return noCloser
        return core
    }

    private fun pingFeel(ms: Int): String =
        when {
            ms <= 80 -> "快如流星"
            ms <= 150 -> "顺风顺水"
            ms <= 300 -> "还算稳当"
            ms <= 600 -> "有点堵"
            else -> "云太厚了"
        }

    companion object {
        const val MAX_TOTAL = 26
    }
}

internal fun clipText(s: String, max: Int): String = if (s.length <= max) s else s.take(max - 1) + "…"

/** 把一句话拆成光字的两行（每行 ≤ maxPerLine 个字）。优先在标点后、最靠近中点处切。 */
internal fun splitBubble(text: String, maxPerLine: Int = 13): Pair<String, String?> {
    if (text.length <= maxPerLine) return text to null
    val mid = text.length / 2
    var best = -1
    for (i in 2 until text.length - 2) {
        if (text[i] in "，、；：,。！？…") {
            if (best == -1 || abs(i - mid) < abs(best - mid)) best = i
        }
    }
    val cut = if (best >= 0) best + 1 else mid
    val a = text.substring(0, cut).trimEnd('，', '、', '；', '：', ',')
    val b = text.substring(cut)
    return clipText(a, maxPerLine + 1) to clipText(b, maxPerLine + 1)
}

/** 气泡该停多久：基础 2.4s + 每字 0.13s。 */
internal fun bubbleHoldMs(text: String): Long = 2400L + text.length * 130L

// ───────────────────────────── 词库 ─────────────────────────────

private object Lines {
    val voices: Map<ResidentId, Voice> =
        mapOf(
            // 守树人：点 → 今日流量。语气：慢、老、安静
            ResidentId.Keeper to
                Voice(
                    openers = listOf("嗯，", "来啦，", "哎，", "听我说，"),
                    topics =
                        listOf(
                            "落叶扫不完，扫的是个心静。",
                            "这棵树比城堡还老。",
                            "根须底下有水声，你听。",
                            "月光落在叶子上，沙沙响。",
                            "我扫的不是叶子，是时辰。",
                            "风从崖边来，带着云的味道。",
                            "树洞里住着一窝萤火虫。",
                            "哪片叶子先落，我心里有数。",
                            "今天树下，过了 {today} 的风。",
                        ),
                    closers = listOf("树不累，我也不累。", "够用就好。", "慢慢来。", "扫完这片再说。"),
                    reports =
                        listOf(
                            "今儿一共走了 {today} 的路。",
                            "账本上写着，今天已过 {today}。",
                            "叶子数过了，今日 {today}。",
                            "到这会儿，{today}，一笔不差。",
                            "今天这岛上，流过 {today}。",
                            "{today}，我记在树皮上了。",
                        ),
                    noData = listOf("账本还在翻，晚点再问我。", "今天的数还没记上。", "树皮上还是空的。"),
                ),
            // 背书包的：点 → 实时上下行。语气：急、碎、爱喘
            ResidentId.Courier to
                Voice(
                    openers = listOf("呼——", "等下，", "别拦我，", "诶，"),
                    topics =
                        listOf(
                            "书包里装的都是别人的信。",
                            "树下到工坊，我一天跑八趟。",
                            "鞋底都磨薄了，还得走。",
                            "路边的萤火虫，比路灯靠谱。",
                            "跑得快，不如跑得稳。",
                            "背得越多，走得越轻——习惯了。",
                            "小路两边的草，都认得我的脚步。",
                            "这会儿路上，下行 {down}。",
                        ),
                    closers = listOf("不聊了，赶路。", "回头见！", "信还等着呢。"),
                    reports =
                        listOf(
                            "下行 {down}，上行 {up}。",
                            "书包里，下 {down}，上 {up}。",
                            "这会儿路上，下行 {down}。",
                            "往外送的，上行 {up}。",
                            "进来的 {down}，出去的 {up}。",
                        ),
                    noData = listOf("路上空空的，没货在跑。", "书包是空的，歇会儿。", "这会儿没风也没信。"),
                ),
            // 铁匠：点 → 当前配置名 + 订阅剩余。语气：粗、短、实在
            ResidentId.Smith to
                Voice(
                    openers = listOf("嗯。", "说。", "瞧——", "哼，"),
                    topics =
                        listOf(
                            "铁要趁热打，线要趁早通。",
                            "火候不到，锤子再响也白搭。",
                            "这砧子比我年纪还大。",
                            "淬火桶里的水，四季都凉。",
                            "好刀不怕敲，好线不怕测。",
                            "风箱一拉，整座岛都暖和。",
                            "瀑布声是我打铁的拍子。",
                            "手上这套「{profile}」，用着顺。",
                        ),
                    closers = listOf("接着打。", "别碰，烫。", "活儿多着呢。"),
                    reports =
                        listOf(
                            "「{profile}」还剩 {quota}。",
                            "用的是「{profile}」，剩 {quota}。",
                            "手上这套「{profile}」，用着顺。",
                            "订阅还剩 {quota}，省着打。",
                            "{quota}，够打一阵子。",
                        ),
                    noData = listOf("账册还没送来，等等。", "配置的数我没记，去问树下那位。"),
                ),
            // 提灯姑娘：点 → 当前节点 + 已连接时长。语气：轻、软、爱用比喻
            ResidentId.Lantern to
                Voice(
                    openers = listOf("嘘，", "你看，", "嗯……", "今晚，"),
                    topics =
                        listOf(
                            "灯油添得足，夜才长。",
                            "飞艇上的风，比地上凉些。",
                            "我提着的不是灯，是回家的路。",
                            "星星一颗颗数，数到一半就困了。",
                            "船舱里的灯，总比我的亮一点。",
                            "灯芯剪短些，火才稳。",
                            "灯照着 {node}，一整晚。",
                        ),
                    closers = listOf("灯在，路就在。", "我再添点油。", "你也早些歇着。"),
                    reports =
                        listOf(
                            "现在连着 {node}，已经 {uptime}。",
                            "{node}，亮了 {uptime}。",
                            "灯照着 {node}，{uptime} 了。",
                            "这条路是 {node}。",
                            "走了 {uptime} 了，没熄过灯。",
                        ),
                    noData = listOf("灯还没点，路还没通。", "没有要照的路。"),
                ),
            // 瞭望手：点 → 延迟。语气：干脆、报告腔
            ResidentId.Lookout to
                Voice(
                    openers = listOf("报告，", "听我报，", "镜子里，", "刚看见，"),
                    topics =
                        listOf(
                            "远处有片云在赶路。",
                            "崖边风大，镜子得攥紧。",
                            "我望的是线，不是天。",
                            "星星今晚排得整齐。",
                            "瀑布底下有彩虹，夜里也有。",
                            "天边有鸟群，往南去了。",
                            "这条线，{ping}，{feel}。",
                        ),
                    closers = listOf("继续瞭望。", "有动静我喊你。", "风大，裹紧点。"),
                    reports =
                        listOf(
                            "延迟 {ping}，{feel}。",
                            "看过去，{ping}，{feel}。",
                            "线上 {ping}，{feel}。",
                            "{ping}，{feel}，没事。",
                            "探过了，{ping}。",
                        ),
                    noData = listOf("镜子里一片雾，看不清线。", "线还没通，我没东西可报。"),
                ),
        )

    val shared =
        listOf(
            "城堡的灯火，数到天亮也数不完。",
            "石头在天上飘，不用人推。",
            "今晚的云海，平得像镜子。",
            "瀑布的声音，听久了就是安静。",
            "岛在动，是风在摇它。",
        )
    val offline = listOf("城堡还没亮灯，大家都在等你。", "线没通，岛上安静得很。", "点一下城堡，岛就醒了。")
    val goodLine = listOf("线今晚顺滑得很。", "今晚的线，手感不错。")
    val badLine = listOf("线有点堵，别急。", "云太厚，线跟着慢了。")
    val deepNight = listOf("夜这么深，还不睡？", "这个点儿，连星星都打盹。")
}
