package com.suanran.dreambox.feature.home.presentation.screen

/*
 * 五位居民的配置：路线 / 岗位 / 步态 / 资源清单。全是数据，没有逻辑。
 *
 * ── 坐标说明 ──
 * 全部是图比例（1120×2240 夜景图）。下面的数是在 forge-backdrop-reference.webp 上量的（网格叠图，±0.005），
 * 不是照简报里的约值抄的——简报里有两处和真图对不上，见 residents-notes.md §0。
 * 上机后用 debug=true 的叠层（画出地面线/站点/脚点/点击区）逐条拖到脚底真正踩在草上为止。
 *
 * ── 美术资源清单（assets/residents/，统一前缀 home_，避免和 proxy 模块的 npc_*.webp 在合并 assets 时撞名）──
 *   规格：webp 带 alpha；所有帧用「同一画布比例」导出（别每张各自拉到同高）；脸朝右；
 *         站姿/走帧的脚底 = 图片最底一行（空边会被自动裁，但别依赖它）；两脚都要踩在最底行上；
 *         建议导出高度 ≈ 128px（屏幕上约 30dp，3x 屏 ≈ 90px，留余量）。
 *   键位：
 *     守树人   keeper_stand · keeper_walk_a · keeper_walk_b · keeper_sweep_1 · keeper_sweep_2
 *     背书包的 courier_stand · courier_walk_a · courier_walk_b
 *     铁匠     smith_stand · smith_walk_a · smith_walk_b · smith_forge_1(举锤) · smith_forge_2(落锤)
 *     提灯姑娘 lantern_stand · lantern_walk_a · lantern_walk_b            （腰以下会被栏杆遮，见 occludeBelowY）
 *     瞭望手   lookout_stand · lookout_walk_a · lookout_walk_b · lookout_scope_1(举望远镜)
 *   走帧 A/B：A = 左脚在前的接触姿势，B = 右脚在前的接触姿势；两脚前后分开的距离 ≈ 一步（0.4 倍身高）以内。
 *   想要更顺：每人改 4 帧（A接触/A过渡/B接触/B过渡），把 GaitSpec.walkFrames 改 4、文件列表补两张即可，引擎不用动。
 */
internal object ResidentRoster {
    // ── 可调：铁匠岗位。铁匠铺定稿后只改这两个数（现在是在候选图上量的：铁砧墩子的底 y≈0.385，砧子在 x≈0.60~0.645） ──
    private const val SMITH_POST_X = 0.660f
    private const val SMITH_FLOOR_Y = 0.386f

    private fun p(x: Float, y: Float) = RPoint(x, y)

    private fun art(name: String) = "residents/home_$name.webp"

    private val keeper =
        ResidentSpec(
            id = ResidentId.Keeper,
            label = "守树人",
            ground = listOf(p(0.200f, 0.349f), p(0.232f, 0.347f), p(0.262f, 0.346f)), // 树下草径（未量准，需现场校）
            stops =
                listOf(
                    StopSpec(p(0.205f, 0.349f), 4f..9f, action = "sweep", face = Face.Left),
                    StopSpec(p(0.232f, 0.347f), 4f..9f, action = "sweep"),
                    StopSpec(p(0.262f, 0.346f), 4f..9f, action = "sweep", face = Face.Right),
                ),
            order = RouteOrder.Shuffle,
            gait = GaitSpec(heightFrac = 0.034f, speedBh = 0.50f, strideBh = 0.38f, bobBh = 0.025f),
            actions = listOf(ActionSpec("sweep", listOf(ActionFrame(0, 0.45f), ActionFrame(1, 0.45f)))),
            sprites =
                SpriteManifest(
                    stand = art("keeper_stand"),
                    walk = listOf(art("keeper_walk_a"), art("keeper_walk_b")),
                    actions = mapOf("sweep" to listOf(art("keeper_sweep_1"), art("keeper_sweep_2"))),
                ),
        )

    private val courier =
        ResidentSpec(
            id = ResidentId.Courier,
            label = "背书包的",
            // 树↔齿轮之间那片亮草地（x 0.30~0.40，y≈0.36）；白花灌木和石头在它上方/下方，路线夹在中间
            ground = listOf(p(0.300f, 0.360f), p(0.345f, 0.362f), p(0.395f, 0.362f)),
            stops =
                listOf(
                    StopSpec(p(0.300f, 0.360f), 2f..5f),
                    StopSpec(p(0.395f, 0.362f), 2f..5f),
                ),
            order = RouteOrder.PingPong,
            gait = GaitSpec(heightFrac = 0.030f, speedBh = 0.90f, strideBh = 0.42f, bobBh = 0.035f),
            sprites =
                SpriteManifest(
                    stand = art("courier_stand"),
                    walk = listOf(art("courier_walk_a"), art("courier_walk_b")),
                ),
        )

    private val smith =
        ResidentSpec(
            id = ResidentId.Smith,
            label = "铁匠",
            // 地面线：从铁砧旁的岗位，贴着铁砧墩子前面的草地，走到齿轮工坊右沿。y 比岗位略低 = 从墩子前面绕过去
            ground =
                listOf(
                    p(SMITH_POST_X, SMITH_FLOOR_Y),
                    p(SMITH_POST_X - 0.018f, SMITH_FLOOR_Y + 0.004f),
                    p(0.600f, 0.392f),
                    p(0.572f, 0.391f),
                ),
            stops =
                listOf(
                    // 0 = 岗位：打铁，一站就是 18~40 秒
                    StopSpec(p(SMITH_POST_X, SMITH_FLOOR_Y), 18f..40f, action = "forge", face = Face.Left),
                    // 1 = 齿轮边看看
                    StopSpec(p(0.572f, 0.391f), 3f..6f, face = Face.Left),
                ),
            order = RouteOrder.Home(0),
            gait = GaitSpec(heightFrac = 0.036f, speedBh = 0.62f, strideBh = 0.40f, bobBh = 0.03f),
            // 举锤久、落锤短：锤子是「举——落」不是均匀摆
            actions = listOf(ActionSpec("forge", listOf(ActionFrame(0, 0.60f), ActionFrame(1, 0.16f)))),
            sprites =
                SpriteManifest(
                    stand = art("smith_stand"),
                    walk = listOf(art("smith_walk_a"), art("smith_walk_b")),
                    actions = mapOf("forge" to listOf(art("smith_forge_1"), art("smith_forge_2"))),
                ),
        )

    private val lantern =
        ResidentSpec(
            id = ResidentId.Lantern,
            label = "提灯姑娘",
            // 飞艇甲板：船体在 x 0.71~0.86、y 0.243~0.28，船舷上沿 y≈0.250。人站在舷墙后面：
            // 脚线取 0.26（在船舷里面），舷线 0.2505 以下全遮 → 看到的是舷墙后面的上半身
            ground = listOf(p(0.750f, 0.296f), p(0.800f, 0.293f), p(0.850f, 0.289f)),
            stops =
                listOf(
                    StopSpec(p(0.750f, 0.296f), 3f..6f, face = Face.Left),
                    StopSpec(p(0.850f, 0.289f), 3f..6f, face = Face.Right),
                ),
            order = RouteOrder.PingPong,
            occludeBelowY = 0.2845f,
            // 脚被遮住了，只剩躯干：加一点随步的重心左右摆，让「在走」靠身体读出来
            gait = GaitSpec(heightFrac = 0.032f, speedBh = 0.45f, strideBh = 0.38f, bobBh = 0.03f, swayBh = 0.012f),
            sprites =
                SpriteManifest(
                    stand = art("lantern_stand"),
                    walk = listOf(art("lantern_walk_a"), art("lantern_walk_b")),
                ),
        )

    private val lookout =
        ResidentSpec(
            id = ResidentId.Lookout,
            label = "瞭望手",
            // 右崖上沿的草（x 0.88~0.94、y≈0.36）。远端收在 0.918：再往右在窄屏上会贴屏幕边
            ground = listOf(p(0.880f, 0.363f), p(0.900f, 0.366f), p(0.918f, 0.368f)),
            stops =
                listOf(
                    StopSpec(p(0.880f, 0.363f), 4f..8f, action = "scope", face = Face.Left),
                    StopSpec(p(0.918f, 0.368f), 4f..8f, action = "scope", face = Face.Right),
                ),
            order = RouteOrder.PingPong,
            gait = GaitSpec(heightFrac = 0.033f, speedBh = 0.50f, strideBh = 0.38f, bobBh = 0.025f),
            actions = listOf(ActionSpec("scope", listOf(ActionFrame(0, 1f)))),
            sprites =
                SpriteManifest(
                    stand = art("lookout_stand"),
                    walk = listOf(art("lookout_walk_a"), art("lookout_walk_b")),
                    actions = mapOf("scope" to listOf(art("lookout_scope_1"))),
                ),
        )

    /** 夜景图（1120×2240）的居民表。白天图以后另起一份，别共用数字。 */
    val night: List<ResidentSpec> = listOf(keeper, courier, smith, lantern, lookout)
}
