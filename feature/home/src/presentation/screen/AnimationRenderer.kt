package com.suanran.dreambox.feature.home.presentation.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.suanran.dreambox.core.model.animation.AnimationBehavior
import com.suanran.dreambox.core.model.animation.AnimationParticleType
import com.suanran.dreambox.core.model.animation.AnimationSpawn
import com.suanran.dreambox.core.model.animation.DreamAnimation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

const val ANIMATION_MAX_PARTICLES = 150

/** 通用粒子 */
internal data class AnimParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    var sizeFactor: Float,
    var alpha: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var phase: Float,
    var phaseSpeed: Float,
    var color: Color,
    /** 0=环境粒子，1=火箭，2=火花 */
    var kind: Int,
)

private val fireworkPalette = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77),
    Color(0xFF4D96FF), Color(0xFFFF9F45), Color(0xFFB983FF),
)

/**
 * 通用粒子更新：根据 [DreamAnimation] 的 spawn/behavior 驱动。
 *
 * @param count 目标粒子数（已应用覆盖）
 * @param speedMul 速度倍率（已应用覆盖）
 */
internal fun updateAnimationParticles(
    particles: MutableList<AnimParticle>,
    anim: DreamAnimation,
    dt: Float,
    w: Float,
    h: Float,
    random: Random,
    baseColor: Color,
    count: Int,
    speedMul: Float,
) {
    val p = anim.particles
    val mainColor = p.colorArgb?.let { Color(it) } ?: baseColor
    when (p.behavior) {
        AnimationBehavior.Explode -> updateExplode(particles, anim, dt, w, h, random, mainColor, count, speedMul)
        else -> updateAmbient(particles, anim, dt, w, h, random, mainColor, count, speedMul)
    }
}

private fun updateAmbient(
    particles: MutableList<AnimParticle>,
    anim: DreamAnimation,
    dt: Float,
    w: Float,
    h: Float,
    random: Random,
    mainColor: Color,
    count: Int,
    speedMul: Float,
) {
    val p = anim.particles
    // 补足粒子
    var ambient = 0
    for (pt in particles) if (pt.kind == 0) ambient++
    while (ambient < count && particles.size < ANIMATION_MAX_PARTICLES) {
        particles.add(spawnAmbient(p, w, h, random, mainColor, speedMul))
        ambient++
    }
    val iter = particles.iterator()
    while (iter.hasNext()) {
        val pt = iter.next()
        if (pt.kind != 0) { iter.remove(); continue }
        when (p.behavior) {
            AnimationBehavior.Fall -> {
                pt.y += pt.vy * dt
                if (p.sway) {
                    pt.phase += pt.phaseSpeed * dt
                    pt.x += sin(pt.phase) * 30f * p.wind * dt
                }
                pt.x += 12f * p.wind * dt
                if (p.rotation) pt.rotation += pt.rotationSpeed * dt
                if (pt.y > h + 40f || pt.x > w + 60f || pt.x < -60f) {
                    respawnAmbient(pt, p, w, h, random, speedMul)
                }
            }
            AnimationBehavior.Rise -> {
                pt.y += pt.vy * dt
                if (p.sway) {
                    pt.phase += pt.phaseSpeed * dt
                    pt.x += sin(pt.phase) * 20f * p.wind * dt
                }
                if (p.rotation) pt.rotation += pt.rotationSpeed * dt
                if (pt.y < -40f) respawnAmbient(pt, p, w, h, random, speedMul)
            }
            AnimationBehavior.Float -> {
                pt.x += pt.vx * dt
                pt.y += pt.vy * dt
                if (p.rotation) pt.rotation += pt.rotationSpeed * dt
                // 环绕
                if (pt.x < -40f) pt.x = w + 40f
                if (pt.x > w + 40f) pt.x = -40f
                if (pt.y < -40f) pt.y = h + 40f
                if (pt.y > h + 40f) pt.y = -40f
            }
            AnimationBehavior.Explode -> { iter.remove() }
        }
    }
}

private fun spawnAmbient(
    p: com.suanran.dreambox.core.model.animation.AnimationParticles,
    w: Float, h: Float, random: Random, mainColor: Color, speedMul: Float,
): AnimParticle {
    val pt = AnimParticle(
        x = 0f, y = 0f, vx = 0f, vy = 0f,
        life = 1f, maxLife = 1f,
        sizeFactor = 0.6f + random.nextFloat() * 0.7f,
        alpha = (0.5f + random.nextFloat() * 0.5f) * p.opacity,
        rotation = random.nextFloat() * 360f,
        rotationSpeed = -90f + random.nextFloat() * 180f,
        phase = random.nextFloat() * (2 * PI).toFloat(),
        phaseSpeed = 0.5f + random.nextFloat() * 1.5f,
        color = pickParticleColor(p, mainColor, random),
        kind = 0,
    )
    respawnAmbient(pt, p, w, h, random, speedMul)
    return pt
}

private fun respawnAmbient(
    pt: AnimParticle,
    p: com.suanran.dreambox.core.model.animation.AnimationParticles,
    w: Float, h: Float, random: Random, speedMul: Float,
) {
    val speed = p.speed * speedMul
    when (p.spawn) {
        AnimationSpawn.Random -> {
            pt.x = random.nextFloat() * w
            pt.y = random.nextFloat() * h
        }
        AnimationSpawn.Bottom -> {
            pt.x = random.nextFloat() * w
            pt.y = h + 20f + random.nextFloat() * 60f
        }
        AnimationSpawn.Center -> {
            pt.x = w / 2f + (random.nextFloat() - 0.5f) * w * 0.4f
            pt.y = h / 2f + (random.nextFloat() - 0.5f) * h * 0.4f
        }
        AnimationSpawn.Top -> {
            pt.x = random.nextFloat() * w
            pt.y = -20f - random.nextFloat() * 80f
        }
    }
    when (p.behavior) {
        AnimationBehavior.Fall -> {
            pt.vy = when (p.type) {
                AnimationParticleType.Line -> (700f + random.nextFloat() * 500f) * speed
                else -> (50f + p.size * 10f + random.nextFloat() * 60f) * speed
            }
            pt.vx = 0f
        }
        AnimationBehavior.Rise -> {
            pt.vy = -(60f + random.nextFloat() * 80f) * speed
            pt.vx = 0f
        }
        AnimationBehavior.Float -> {
            val a = random.nextFloat() * 2 * PI
            val s = (15f + random.nextFloat() * 30f) * speed
            pt.vx = (cos(a) * s).toFloat()
            pt.vy = (sin(a) * s).toFloat()
        }
        AnimationBehavior.Explode -> { pt.vy = 0f; pt.vx = 0f }
    }
    pt.alpha = (0.5f + random.nextFloat() * 0.5f) * p.opacity
}

private fun pickParticleColor(
    p: com.suanran.dreambox.core.model.animation.AnimationParticles,
    mainColor: Color,
    random: Random,
): Color {
    p.colorArgb?.let { return Color(it) }
    // 未指定颜色时：爆炸行为用多彩，其余跟随主题
    return if (p.behavior == AnimationBehavior.Explode) fireworkPalette.random(random) else mainColor
}

private fun updateExplode(
    particles: MutableList<AnimParticle>,
    anim: DreamAnimation,
    dt: Float,
    w: Float,
    h: Float,
    random: Random,
    mainColor: Color,
    count: Int,
    speedMul: Float,
) {
    val p = anim.particles
    val speed = p.speed * speedMul
    // 发射火箭（限制并发数）
    val rockets = particles.count { it.kind == 1 }
    // 用 count 换算发射频率：count 越大发射越频繁
    val launchProb = (0.4f + count / 60f).coerceIn(0.3f, 2.5f)
    if (rockets < 4 && random.nextFloat() < launchProb * dt * 0.9f) {
        val startX = when (p.spawn) {
            AnimationSpawn.Center -> w / 2f + (random.nextFloat() - 0.5f) * w * 0.3f
            else -> w * (0.15f + random.nextFloat() * 0.7f)
        }
        particles.add(
            AnimParticle(
                x = startX, y = h + 10f, vx = 0f,
                vy = -(h * (0.55f + random.nextFloat() * 0.25f)) * speed,
                life = 1f, maxLife = 1f,
                sizeFactor = 1f, alpha = 1f,
                rotation = 0f, rotationSpeed = 0f, phase = 0f, phaseSpeed = 0f,
                color = pickParticleColor(p, mainColor, random),
                kind = 1,
            ).also {
                // 目标高度存到 life 字段复用（hack：用 maxLife 存 targetY）
                it.maxLife = h * (0.15f + random.nextFloat() * 0.35f)
            }
        )
    }
    val spawned = mutableListOf<AnimParticle>()
    // 爆炸模式不保留环境粒子
    particles.removeAll { it.kind == 0 }
    val it2 = particles.iterator()
    while (it2.hasNext()) {
        val pt = it2.next()
        when (pt.kind) {
            1 -> { // 火箭上升
                pt.y += pt.vy * dt
                // 拖尾
                spawned.add(
                    AnimParticle(
                        x = pt.x, y = pt.y, vx = 0f, vy = 30f,
                        life = 0.35f, maxLife = 0.35f,
                        sizeFactor = 0.7f, alpha = 0.9f,
                        rotation = 0f, rotationSpeed = 0f, phase = 0f, phaseSpeed = 0f,
                        color = pt.color, kind = 2,
                    )
                )
                if (pt.y <= pt.maxLife) { // maxLife 复用为 targetY
                    it2.remove()
                    val n = count.coerceAtMost(ANIMATION_MAX_PARTICLES / 2)
                    repeat(n) { i ->
                        val angle = (i.toFloat() / n) * 2 * PI + random.nextFloat() * 0.3f
                        val s = (90f + random.nextFloat() * 190f) * speed
                        spawned.add(
                            AnimParticle(
                                x = pt.x, y = pt.y,
                                vx = (cos(angle) * s).toFloat(),
                                vy = (sin(angle) * s).toFloat(),
                                life = 1.1f + random.nextFloat() * 0.7f,
                                maxLife = 1.8f,
                                sizeFactor = 0.7f + random.nextFloat() * 0.8f,
                                alpha = p.opacity,
                                rotation = random.nextFloat() * 360f,
                                rotationSpeed = -90f + random.nextFloat() * 180f,
                                phase = 0f, phaseSpeed = 0f,
                                color = if (p.colorArgb == null && random.nextFloat() < 0.25f) Color.White else pt.color,
                                kind = 2,
                            )
                        )
                    }
                }
            }
            2 -> { // 火花
                pt.life -= dt
                if (pt.life <= 0f) { it2.remove(); continue }
                pt.vy += p.gravity * dt
                pt.vx *= (1f - 0.6f * dt)
                pt.x += pt.vx * dt
                pt.y += pt.vy * dt
            }
            else -> it2.remove()
        }
    }
    particles.addAll(spawned)
    while (particles.size > ANIMATION_MAX_PARTICLES) particles.removeAt(0)
}

/** 通用粒子绘制 */
internal fun DrawScope.drawAnimationParticles(
    particles: List<AnimParticle>,
    anim: DreamAnimation,
    baseColor: Color,
    opacityMul: Float,
) {
    val p = anim.particles
    for (pt in particles) {
        val a = when (pt.kind) {
            2 -> (pt.life / pt.maxLife).coerceIn(0f, 1f) * pt.alpha * p.opacity * opacityMul
            else -> (pt.alpha * p.opacity * opacityMul).coerceIn(0f, 1f)
        }
        if (a <= 0.01f) continue
        // 未指定颜色的环境粒子跟随主题色；爆炸火花用自带颜色
        val drawColor =
            if (pt.kind == 0 && p.colorArgb == null && p.behavior != AnimationBehavior.Explode) {
                baseColor.copy(alpha = a)
            } else {
                pt.color.copy(alpha = a)
            }
        when (p.type) {
            AnimationParticleType.Circle -> {
                drawCircle(
                    color = drawColor,
                    radius = p.size * pt.sizeFactor,
                    center = Offset(pt.x, pt.y),
                )
            }
            AnimationParticleType.Line -> {
                val len = p.size * pt.sizeFactor * 1.6f
                val tiltX = p.wind * 60f * (len / 20f)
                drawLine(
                    color = drawColor,
                    start = Offset(pt.x, pt.y),
                    end = Offset(pt.x - tiltX, pt.y - len),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            AnimationParticleType.Petal -> {
                val w = p.size * pt.sizeFactor * 0.75f
                val h = p.size * pt.sizeFactor * 1.35f
                if (p.rotation) {
                    rotate(pt.rotation, Offset(pt.x, pt.y)) {
                        drawOval(
                            color = drawColor,
                            topLeft = Offset(pt.x - w, pt.y - h),
                            size = Size(w * 2f, h * 2f),
                        )
                    }
                } else {
                    drawOval(
                        color = drawColor,
                        topLeft = Offset(pt.x - w, pt.y - h),
                        size = Size(w * 2f, h * 2f),
                    )
                }
            }
            AnimationParticleType.Rect -> {
                val s = p.size * pt.sizeFactor
                if (p.rotation) {
                    rotate(pt.rotation, Offset(pt.x, pt.y)) {
                        drawRect(
                            color = drawColor,
                            topLeft = Offset(pt.x - s / 2f, pt.y - s / 2f),
                            size = Size(s, s),
                        )
                    }
                } else {
                    drawRect(
                        color = drawColor,
                        topLeft = Offset(pt.x - s / 2f, pt.y - s / 2f),
                        size = Size(s, s),
                    )
                }
            }
        }
    }
}
