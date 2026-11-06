package com.suanran.dreambox.feature.proxy.presentation.screen

import kotlin.random.Random

/** 港口五人的身份。 */
internal enum class HarborNpc(val label: String) {
    Lantern("提灯人"),
    Crate("扛货人"),
    Rope("盘绳人"),
    Lookout("瞭望手"),
    Boat("船夫"),
}

/** 说话时能引用的真实状态：当前节点、延迟、组、时辰、是否在测速、是否已连接。 */
internal data class HarborTalkState(
    val running: Boolean = false,
    val nodeName: String? = null,
    val pingMs: Int = -1,
    val groupName: String? = null,
    val groupCount: Int = 0,
    val nodeCount: Int = 0,
    val hour: Int = 22,
    val testing: Boolean = false,
)

/**
 * 港口闲话引擎：不是背稿，是零件组装。每人有自己的开头、话题、收尾，
 * 话题里留了 {node}{ping}{group} 等空位，填的是 App 的真实状态，
 * 再加数字与时辰的变化，组合量级过万；近 80 句整句不重说。
 */
internal class HarborTalker {
    private val recent = ArrayDeque<String>()

    private fun fill(template: String, s: HarborTalkState): String {
        var out = template
        out = out.replace("{node}", s.nodeName ?: "那条线")
        out = out.replace("{ping}", if (s.pingMs > 0) s.pingMs.toString() else "几十")
        out = out.replace("{group}", s.groupName ?: "主港")
        out = out.replace("{groups}", s.groupCount.toString())
        out = out.replace("{nodes}", s.nodeCount.toString())
        return out
    }

    private fun compose(npc: HarborNpc, s: HarborTalkState, rnd: Random): String {
        val voice = HarborLines.voices.getValue(npc)
        val parts = mutableListOf<String>()
        if (rnd.nextFloat() < 0.75f) parts += voice.openers.random(rnd)
        val topicPool = buildList {
            addAll(voice.topics)
            addAll(HarborLines.sharedTopics)
            when {
                s.testing -> addAll(HarborLines.testingLines)
                !s.running -> addAll(HarborLines.offlineLines)
                s.pingMs in 1..150 -> addAll(HarborLines.goodPingLines)
                s.pingMs > 400 -> addAll(HarborLines.badPingLines)
            }
            if (s.hour in 0..5) addAll(HarborLines.nightLines)
            if (s.hour in 6..11) addAll(HarborLines.morningLines)
        }
        parts += topicPool.random(rnd)
        if (rnd.nextFloat() < 0.6f) parts += voice.closers.random(rnd)
        return fill(parts.joinToString(""), s)
    }

    /** 点谁谁说话：一句完整的、短期内不重复的话。 */
    fun speak(npc: HarborNpc, s: HarborTalkState): String {
        val rnd = Random(System.nanoTime())
        var line = compose(npc, s, rnd)
        var guard = 0
        while (line in recent && guard++ < 12) {
            line = compose(npc, s, rnd)
        }
        recent.addLast(line)
        while (recent.size > 80) recent.removeFirst()
        return line
    }

    /** 两人碰头时的对答：返回先后两句（说话人 -> 台词）。 */
    fun chat(a: HarborNpc, b: HarborNpc, s: HarborTalkState): Pair<Pair<HarborNpc, String>, Pair<HarborNpc, String>> {
        val rnd = Random(System.nanoTime())
        HarborLines.pairChats[a to b]?.let { lines ->
            val (first, second) = lines.random(rnd)
            val l1 = fill(first, s)
            val l2 = fill(second, s)
            recent.addLast(l1)
            recent.addLast(l2)
            return (a to l1) to (b to l2)
        }
        HarborLines.pairChats[b to a]?.let { lines ->
            val (first, second) = lines.random(rnd)
            val l1 = fill(first, s)
            val l2 = fill(second, s)
            recent.addLast(l1)
            recent.addLast(l2)
            return (b to l1) to (a to l2)
        }
        return (a to speak(a, s)) to (b to speak(b, s))
    }
}

private object HarborLines {

    class Voice(val openers: List<String>, val topics: List<String>, val closers: List<String>)

    val voices: Map<HarborNpc, Voice> =
        mapOf(
            HarborNpc.Lantern to
                Voice(
                    openers = listOf("哟，", "嘿，", "跟你说，", "我这灯啊，", "刚巡完一圈，", "你瞧，", "嘘，", "今晚，"),
                    topics =
                        listOf(
                            "这排灯我天天擦，擦得比脸还亮。",
                            "{group}那盏灯今晚最稳，火苗都不带抖的。",
                            "灯油又见底了，明儿得去工坊讨一罐。",
                            "雾大的时候，灯就是船的眼睛。",
                            "我数过，港里一共{groups}盏大灯，盏盏都有名有姓。",
                            "走夜路别慌，跟着灯走准没错。",
                            "灯芯剪短些，火才旺，这跟过日子一个理。",
                            "刚有个生面孔问路，我给他指了最亮的那条道。",
                            "你看{node}那盏，今晚格外精神。",
                            "灯罩上的灰，是海风写的字。",
                        ),
                    closers = listOf("我再去添点油。", "你也早点歇着。", "灯在，港就在。", "回头聊。", "我继续巡了。"),
                ),
            HarborNpc.Crate to
                Voice(
                    openers = listOf("呼……", "让让，", "这箱，", "别看我，", "嘿咻，", "今儿这批货，"),
                    topics =
                        listOf(
                            "死沉死沉的，也不知道装了啥宝贝。",
                            "楼梯我一天上下八十趟，腿肚子比桅杆还硬。",
                            "货这东西，轻的怕碰，重的怕摔，都得供着。",
                            "刚那箱是给飞艇的，催得急。",
                            "肩膀垫块布，能多扛十年。",
                            "你别伸手，我这平衡找好了，你一碰全完。",
                            "港里的货，十件有九件是赶船期的。",
                            "等这批卸完，我得去吃两大碗面。",
                            "绳桥那头风大，扛货得猫着腰走。",
                            "箱底有潮气，垫高些，管货的就怕这个。",
                        ),
                    closers = listOf("不说了，赶趟。", "让一让啊。", "走了走了。", "回头请你喝口水。"),
                ),
            HarborNpc.Rope to
                Voice(
                    openers = listOf("你看这绳，", "慢慢来，", "绳结啊，", "我跟你说，", "这活儿，"),
                    topics =
                        listOf(
                            "盘绳子急不得，一圈压一圈，心稳手才稳。",
                            "飞艇的缆绳比命金贵，我每天都要摸一遍毛刺。",
                            "死结活结都有用处，看系在哪儿。",
                            "这根绳跟了我七年，比我还可靠。",
                            "绳子怕晒怕潮，收的时候要唱着号子收。",
                            "{group}的系泊绳今早刚换过，放心。",
                            "打结这手艺，我爷爷的爷爷就会。",
                            "绳头散了要烧一烧，毛头扎进肉里可疼。",
                            "港里风一大，先紧的是绳，先松的也是绳。",
                            "你看这盘，圆不圆？圆就是顺。",
                        ),
                    closers = listOf("我接着盘了。", "手上有活，心里不慌。", "你忙你的。"),
                ),
            HarborNpc.Lookout to
                Voice(
                    openers = listOf("报告，", "我这镜里，", "瞭望台上，", "刚看见，", "听我报，"),
                    topics =
                        listOf(
                            "东边云开了一线，今晚星路通畅。",
                            "{node}那条线，我盯了一晚上，稳。",
                            "远处有片雨云，绕着咱们港走，不碍事。",
                            "飞艇的影子在云海上拉得老长，美得很。",
                            "今晚的风从西北来，三级，不大不小刚好行船。",
                            "我这镜子能看见三十里外的灯火。",
                            "云层下头有鸟群夜飞，往南去了。",
                            "月亮今晚躲在塔后头，害羞。",
                            "港外三里有雾墙，船进来得打灯语。",
                            "星图我背得滚瓜烂熟，闭着眼都能指北。",
                        ),
                    closers = listOf("继续瞭望。", "有动静我喊你们。", "塔上风大，我裹紧点。"),
                ),
            HarborNpc.Boat to
                Voice(
                    openers = listOf("慢船，", "我这船，", "渡你一程？", "水上的事，", "老朽这桨，"),
                    topics =
                        listOf(
                            "不赶时间，云海渡人，渡的都是有缘人。",
                            "船底昨儿补过，滴水不漏。",
                            "货郎、信件、走失的猫，都是我渡的。",
                            "雾里行船靠耳朵，水声会告诉你岸在哪。",
                            "这桨跟了我半辈子，包浆比镜子还亮。",
                            "急的人坐快艇，我这儿只载慢心情。",
                            "今晚水温顺，鱼都睡了，船过无声。",
                            "对岸工坊的灯一亮，我就知道该收工了。",
                            "你若赶时间，先去塔上打个灯语，我随后就到。",
                            "船小，能装的东西不多，能装的故事不少。",
                        ),
                    closers = listOf("坐稳了。", "我慢慢摇。", "下回再会。"),
                ),
        )

    val sharedTopics =
        listOf(
            "港里的规矩：灯不灭，船不慌。",
            "今晚的云海平得像镜子。",
            "听说工坊那边又添了新卷轴，架子都快满了。",
            "飞艇睡着的时候会打鼾，你仔细听，呼噜呼噜的。",
            "食堂今晚炖了热汤，干完活都去喝一碗。",
            "老港主说过，天再黑，灯塔也不能眨眼。",
            "这风里有股甜味，是对岸花岛飘来的。",
            "星星今晚排得整齐，像在列队点名。",
            "港里的猫又睡在货箱上了，谁也不敢撵它。",
            "明儿是个好天，我的膝盖说的，它比天气预报准。",
        )

    val testingLines =
        listOf(
            "塔上正打灯语测风向呢，一盏一盏地点名。",
            "测速的时候别晃灯，数错了要重来。",
            "灯语打得越密，说明线越多，我眼都花了。",
            "刚点完一轮名，个个都应声了，齐整。",
        )

    val offlineLines =
        listOf(
            "船还没开，缆绳都松着呢。",
            "港里静悄悄的，就等一声启程锣。",
            "没启程的时候，灯也矮了三分，没精神。",
            "等船开起来，这港才算活过来。",
        )

    val goodPingLines =
        listOf(
            "{node}刚报了数，{ping}，快得像顺风船。",
            "这条线今晚手感滑溜，{ping}，一点不拖泥带水。",
            "{ping}的线，走起来脚底生风。",
            "今晚的{node}值得记一功，{ping}，稳稳当当。",
        )

    val badPingLines =
        listOf(
            "{node}今晚有点堵，{ping}，怕是云层太厚。",
            "{ping}……这线在跟雾较劲呢。",
            "慢成这样，{node}怕是绕了远路。",
            "风浪大，线也跟着晃，{ping}，将就着用吧。",
        )

    val nightLines =
        listOf(
            "夜深了，港里就剩我们几个守夜的。",
            "这个点儿，连星星都开始打盹了。",
            "守夜的人，耳朵比眼睛管用。",
        )

    val morningLines =
        listOf(
            "晨雾还没散，第一班船快到了。",
            "天蒙蒙亮，灯就该歇了，我也该歇了。",
            "早班的风最勤快，吹得旗子哗哗响。",
        )

    /** 碰头对答：前者先说，后者接话。 */
    val pairChats: Map<Pair<HarborNpc, HarborNpc>, List<Pair<String, String>>> =
        mapOf(
            (HarborNpc.Lantern to HarborNpc.Lookout) to
                listOf(
                    "塔上看得清吗？今晚雾大。" to "清着呢，你那灯一亮，雾就退三分。",
                    "我这灯油还够烧到天亮。" to "够就行，塔上的灯跟你呼应着，港就丢不了。",
                    "刚巡到东头，有盏灯火苗歪了。" to "我从塔上也瞧见了，正要喊你呢。",
                ),
            (HarborNpc.Crate to HarborNpc.Rope) to
                listOf(
                    "这箱搁哪儿？你地盘你说了算。" to "靠桶那边，绳圈旁边，别压着我的新绳。",
                    "楼梯滑，你上去的时候扶着点。" to "绳栏杆我今早刚紧过，稳当，你放心扛。",
                    "这批货赶船期，帮我喊一嗓子让路。" to "好嘞——劳驾让让，急货过港喽！",
                ),
            (HarborNpc.Rope to HarborNpc.Boat) to
                listOf(
                    "你那船的缆绳该换了，边都起毛了。" to "就等你这句话呢，明儿我把船摇过来。",
                    "今晚水静，你能多歇会儿。" to "水静船稳，我正好在船头打个盹。",
                    "新绳到了，先紧着你那船用。" to "得嘞，你这绳，比锚还让我安心。",
                ),
            (HarborNpc.Lantern to HarborNpc.Crate) to
                listOf(
                    "慢点，楼梯口我给你留着灯。" to "瞧见了，你这灯比我亲娘的眼睛还亮堂。",
                    "今晚货多，辛苦你了。" to "辛苦啥，港活着，咱就有饭吃。",
                ),
            (HarborNpc.Lookout to HarborNpc.Boat) to
                listOf(
                    "船夫，雾墙在港外三里，你回来时打灯语。" to "晓得了，老规矩，三长两短。",
                    "对岸有船影，你瞧见没？" to "瞧见了，是送信的小艇，我这就去接应。",
                ),
        )
}
