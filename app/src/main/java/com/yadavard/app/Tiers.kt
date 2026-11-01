package com.yadavard.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What token purchases earn: bronze (under 200 tokens bought), silver (200–500), gold (500–1500), diamond (over
 * 1500); the admin has a frame of its own, with a crown, and a galaxy Yadimo. Nothing bought: no frame.
 * [metal] is dark, mid, highlight, mid, deep; [gem] is the inside of the frame; each tier adds to the one before
 * (a second rim, stars, a crest, wings, a crown) and so do its effects (glow, sparkles, rays, orbiting stars).
 */
enum class Tier(val fa: String, val en: String, val emoji: String, val metal: List<Long>, val gem: List<Long>, val glow: Long,
                val stars: Int, val double: Boolean = false, val crest: Boolean = false, val wings: Boolean = false,
                val crown: Boolean = false, val sparkles: Int = 0, val rays: Boolean = false) {
    BRONZE("برنزی", "Bronze", "🥉", listOf(0xFF5A2E12, 0xFFB8723C, 0xFFF6C79A, 0xFFA8602C, 0xFF4A240C), listOf(0xFF3A1A0B, 0xFF120703), 0xFFFF9A4A, 0),
    SILVER("نقره‌ای", "Silver", "🥈", listOf(0xFF5D6678, 0xFFC9D2E0, 0xFFFFFFFF, 0xFFAAB4C6, 0xFF4B5466), listOf(0xFF1D2A4D, 0xFF0A1022), 0xFFCFE0FF, 1,
        double = true, sparkles = 2),
    GOLD("طلایی", "Gold", "🥇", listOf(0xFF7A4E00, 0xFFE2A92A, 0xFFFFF2B0, 0xFFD69A1A, 0xFF6A4200), listOf(0xFF3D2300, 0xFF140B00), 0xFFFFC93D, 2,
        double = true, crest = true, sparkles = 5),
    DIAMOND("الماسی", "Diamond", "💎", listOf(0xFF2B6FB8, 0xFF8FD6FF, 0xFFFFFFFF, 0xFF5FB4FF, 0xFF1D4F8F), listOf(0xFF0D2A5C, 0xFF050C22), 0xFF8FE4FF, 3,
        double = true, crest = true, wings = true, sparkles = 7, rays = true),
    ADMIN("ادمین", "Admin", "👑", listOf(0xFF3B1680, 0xFFA067FF, 0xFFFFE08A, 0xFF7B3CFF, 0xFF2A0C5C), listOf(0xFF1A0838, 0xFF05010F), 0xFFB98CFF, 3,
        double = true, wings = true, crown = true, sparkles = 6, rays = true);

    companion object {
        fun of(purchased: Double, admin: Boolean): Tier? = when {
            admin -> ADMIN
            purchased > 1500 -> DIAMOND
            purchased >= 500 -> GOLD
            purchased >= 200 -> SILVER
            purchased > 0 -> BRONZE
            else -> null
        }
    }
}

// The frame is drawn in a 100×100 box (the hexagon's corners at radius 44), placed with room around it for the
// wings and the crown: the canvas covers -12…112 in both directions.
private const val BOX = 124f
private const val PAD = 12f

private fun hexPath(cx: Float, cy: Float, r: Float, at: (Float, Float) -> Offset) = Path().apply {
    for (k in 0 until 6) {
        val a = (PI / 180 * (60 * k - 90)).toFloat()
        val p = at(cx + r * cos(a), cy + r * sin(a))
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun starPath(cx: Float, cy: Float, r: Float, at: (Float, Float) -> Offset) = Path().apply {
    for (k in 0 until 10) {
        val a = (PI / 5 * k - PI / 2).toFloat(); val rr = if (k % 2 == 1) r * .45f else r
        val p = at(cx + rr * cos(a), cy + rr * sin(a))
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun poly(at: (Float, Float) -> Offset, vararg xy: Float) = Path().apply {
    for (i in xy.indices step 2) { val p = at(xy[i], xy[i + 1]); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

/** The tier's frame: metal hexagon (and rims, stars, crest, wings, crown), with a shine running over it. */
private fun DrawScope.frame(t: Tier, shine: Float) {
    val u = size.minDimension / BOX
    val at = { x: Float, y: Float -> Offset((x + PAD) * u, (y + PAD) * u) }
    val c = t.metal.map { Color(it) }
    val (d1, m1, hl, m2, d2) = c
    val metal = Brush.linearGradient(listOf(m1, hl, m2, d2), start = at(0f, 0f), end = at(100f, 100f))
    val edge = Brush.linearGradient(listOf(hl, d1, m1), start = at(100f, 0f), end = at(0f, 100f))
    val line = Stroke(.9f * u, join = StrokeJoin.Round)
    if (t.wings) for (side in listOf(-1f, 1f)) {
        val w = { x: Float, y: Float -> at(50 + side * x, y) }
        val wing = poly(w, 30f, 24f, 56f, 10f, 50f, 30f, 60f, 26f, 48f, 44f, 54f, 44f, 38f, 58f)
        drawPath(wing, metal); drawPath(wing, d1, style = line)
        drawLine(hl.copy(alpha = .8f), w(36f, 28f), w(50f, 20f), 1.2f * u)
        drawLine(hl.copy(alpha = .6f), w(38f, 40f), w(50f, 34f), 1.2f * u)
    }
    if (t.crest && !t.crown) { val crest = poly(at, 40f, 9f, 50f, 1f, 60f, 9f, 56f, 12f, 44f, 12f); drawPath(crest, metal); drawPath(crest, d1, style = line) }
    val outer = hexPath(50f, 50f, 44f, at)
    drawPath(outer, edge); drawPath(outer, d2, style = line)
    drawPath(hexPath(50f, 50f, 40.5f, at), metal)
    if (t.double) { drawPath(hexPath(50f, 50f, 37.5f, at), d2); drawPath(hexPath(50f, 50f, 36.2f, at), edge) }
    val inner = if (t.double) 33.5f else 35.5f
    drawPath(hexPath(50f, 50f, inner, at), Brush.radialGradient(t.gem.map { Color(it) }, center = at(50f, 42f), radius = inner * 1.4f * u))
    drawPath(hexPath(50f, 50f, inner, at), d2, style = Stroke(u))
    if (t.crown) {
        val crown = poly(at, 33f, 13f, 35f, -1f, 42f, 6f, 50f, -7f, 58f, 6f, 65f, -1f, 67f, 13f)
        drawPath(crown, metal); drawPath(crown, d2, style = line)
        val band = poly(at, 32f, 10f, 68f, 10f, 68f, 16f, 32f, 16f)
        drawPath(band, edge); drawPath(band, d2, style = line)
        drawCircle(Color(0xFFFF6FB1), 2f * u, at(50f, 13f)); drawCircle(Color(0xFF7FE6FF), 1.4f * u, at(41f, 13f)); drawCircle(Color(0xFF7FE6FF), 1.4f * u, at(59f, 13f))
        for ((x, y, r) in listOf(Triple(35f, -1f, 1.6f), Triple(50f, -7f, 1.9f), Triple(65f, -1f, 1.6f))) drawCircle(hl, r * u, at(x, y))
    }
    // the shine: a soft band of light sweeping across the frame
    clipPath(outer) {
        val x = -60f + shine * 190f
        rotate(20f, at(50f, 50f)) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .5f), Color.Transparent),
                startX = at(x, 0f).x, endX = at(x + 40f, 0f).x), topLeft = at(x, -10f), size = androidx.compose.ui.geometry.Size(40f * u, 120f * u))
        }
    }
    val xs = when (t.stars) { 1 -> listOf(50f); 2 -> listOf(43f, 57f); 3 -> listOf(37f, 50f, 63f); else -> emptyList() }
    xs.forEachIndexed { i, x ->
        val big = t.stars == 3 && i == 1
        val star = starPath(x, if (big) 94f else 91f, if (big) 6.5f else 5.2f, at)
        drawPath(star, metal); drawPath(star, d2, style = Stroke(.7f * u))
    }
}

/** Glow, rays, sparkles and orbiting stars around Yadimo, more of them the higher the tier. */
private fun DrawScope.effects(t: Tier, breathe: Float, turn: Float, twinkle: Float) {
    val glow = Color(t.glow)
    val r = size.minDimension * .39f
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .55f * (.6f + .4f * breathe)), Color.Transparent), center, r * (1f + .12f * breathe)),
        r * (1f + .12f * breathe))
    if (t.rays) rotate(turn) {
        for (i in 0 until 12) rotate(i * 30f) {
            drawLine(Brush.linearGradient(listOf(Color(0x55C8F0FF), Color.Transparent), start = center, end = center + Offset(0f, -r * 1.25f)),
                center, center + Offset(0f, -r * 1.25f), size.minDimension * .025f)
        }
    }
    val sparkColor = when (t) { Tier.SILVER -> Color.White; Tier.GOLD -> Color(0xFFFFE27A); Tier.DIAMOND -> Color(0xFFD7F6FF); else -> Color(0xFFFFE08A) }
    for (i in 0 until t.sparkles) {
        val a = i.toFloat() / t.sparkles * 2 * PI.toFloat() + .4f
        val k = ((sin((twinkle + i * .37f) * 2 * PI) + 1) / 2).toFloat()
        val p = center + Offset(cos(a) * r * .78f, sin(a) * r * .78f)
        val s = size.minDimension * .03f * (.3f + .7f * k)
        rotate(45f * k, p) {
            drawPath(Path().apply {
                moveTo(p.x, p.y - s); lineTo(p.x + s * .2f, p.y - s * .2f); lineTo(p.x + s, p.y); lineTo(p.x + s * .2f, p.y + s * .2f)
                lineTo(p.x, p.y + s); lineTo(p.x - s * .2f, p.y + s * .2f); lineTo(p.x - s, p.y); lineTo(p.x - s * .2f, p.y - s * .2f); close()
            }, sparkColor.copy(alpha = k))
        }
    }
    if (t == Tier.ADMIN) for (i in 0 until 4) {
        val a = Math.toRadians((turn * 1.3 + i * 90).toDouble()).toFloat()
        val p = center + Offset(cos(a) * r * .95f, sin(a) * r * .95f)
        drawCircle(Color(0xFFB69CFF).copy(alpha = .5f), size.minDimension * .02f, p)
        drawCircle(Color.White, size.minDimension * .009f, p)
    }
}

/**
 * Someone's picture in the frame their purchases earned, with its effects; [content] (Yadimo) sits in the middle.
 * No tier: a plain round frame.
 */
@Composable
fun TierFrame(tier: Tier?, size: Dp, content: @Composable () -> Unit) {
    if (tier == null) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(size * .62f)) {}
            Box(Modifier.size(size * .5f), contentAlignment = Alignment.Center) { content() }
        }
        return
    }
    val anim = rememberInfiniteTransition(label = "tier")
    val shine by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "shine")
    val turn by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "turn")
    val breathe by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "breathe")
    val twinkle by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "twinkle")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { effects(tier, breathe, turn, twinkle) }
        Canvas(Modifier.fillMaxSize()) { frame(tier, shine) }
        // the picture fills the inside of the hexagon (a little lower than the middle, like the frame's gem)
        Box(Modifier.size(size * .5f), contentAlignment = Alignment.Center) { content() }
    }
}

/** The level number in a small hexagon, in the metal of the tier (plain steel when there is none). */
@Composable
fun LevelMedal(tier: Tier?, level: Int, size: Dp = 28.dp) {
    val metal = (tier?.metal ?: listOf(0xFF2C3A6A, 0xFF55608F, 0xFF9AA6D6, 0xFF46507C, 0xFF1D2648)).map { Color(it) }
    val gem = (tier?.gem ?: listOf(0xFF1B2446, 0xFF0C1124)).map { Color(it) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = this.size.minDimension / 40f
            val at = { x: Float, y: Float -> Offset(x * u, y * u) }
            val outer = hexPath(20f, 20f, 19f, at)
            drawPath(outer, Brush.linearGradient(listOf(metal[1], metal[2], metal[4]), at(0f, 0f), at(40f, 40f)))
            drawPath(outer, metal[4], style = Stroke(u))
            drawPath(hexPath(20f, 20f, 14.5f, at), Brush.radialGradient(gem, center = at(20f, 15f), radius = 18f * u))
        }
        Text(n(level), color = metal[2], fontWeight = FontWeight.Black,
            fontSize = (size.value * .36f).sp, style = MaterialTheme.typography.labelSmall)
    }
}

/** A small shining label with the tier's medal, e.g. "💎 الماسی". */
@Composable
fun TierChip(tier: Tier) {
    Surface(shape = CircleShape, color = Color(tier.metal[1]).copy(alpha = .28f), modifier = Modifier.clip(CircleShape).shimmer(true, .35f)) {
        Text("${tier.emoji} ${t(tier.fa, tier.en)}", Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
