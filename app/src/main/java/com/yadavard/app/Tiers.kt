package com.yadavard.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ring around someone's picture, earned with token purchases: bronze (under 200 tokens bought), silver
 * (200–500), gold (500–1500) and diamond (over 1500, and always for the admin). Nothing bought, no ring.
 */
enum class Tier(val fa: String, val en: String, val emoji: String, val colors: List<Long>) {
    BRONZE("برنزی", "Bronze", "🥉", listOf(0xFF6E3B17, 0xFFCD7F32, 0xFFF3C08A, 0xFFCD7F32, 0xFF6E3B17)),
    SILVER("نقره‌ای", "Silver", "🥈", listOf(0xFF6F7680, 0xFFC9D1DA, 0xFFFFFFFF, 0xFFC9D1DA, 0xFF6F7680)),
    GOLD("طلایی", "Gold", "🥇", listOf(0xFF8A6100, 0xFFFFC83D, 0xFFFFF4B0, 0xFFFFC83D, 0xFF8A6100)),
    DIAMOND("الماسی", "Diamond", "💎", listOf(0xFF66E0FF, 0xFFFFFFFF, 0xFFB69CFF, 0xFFFF9BD2, 0xFFFFFFFF, 0xFF66E0FF));

    companion object {
        fun of(purchased: Double, admin: Boolean): Tier? = when {
            admin || purchased > 1500 -> DIAMOND
            purchased >= 500 -> GOLD
            purchased >= 200 -> SILVER
            purchased > 0 -> BRONZE
            else -> null
        }
    }
}

/** A shining ring of [tier] around [content]; the colours turn, a glow breathes and (diamond) sparkles twinkle. */
@Composable
fun TierRing(tier: Tier?, size: Dp, width: Dp = 6.dp, content: @Composable () -> Unit) {
    if (tier == null) { Box(Modifier.size(size), contentAlignment = Alignment.Center) { content() }; return }
    val t = rememberInfiniteTransition(label = "tier")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (tier == Tier.DIAMOND) 2600 else 4200, easing = LinearEasing)), label = "turn")
    val glow by t.animateFloat(.35f, .9f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    val colors = tier.colors.map { Color(it) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = width.toPx()
            val r = size.toPx() / 2 - w
            // soft glow outside the ring
            drawCircle(Brush.radialGradient(listOf(colors[1].copy(alpha = glow * .55f), Color.Transparent), radius = r + w * 3), radius = r + w * 3)
            rotate(turn) { drawCircle(Brush.sweepGradient(colors), radius = r, style = Stroke(w)) }
            // a bright glint running round
            val a = Math.toRadians(turn * 1.5).toFloat()
            drawCircle(Color.White.copy(alpha = .9f), radius = w * .45f, center = center + Offset(cos(a) * r, sin(a) * r))
            if (tier == Tier.DIAMOND) for (i in 0 until 6) {
                val b = Math.toRadians(i * 60.0 + turn * .4).toFloat()
                val k = ((sin(Math.toRadians(turn * 3.0 + i * 50)).toFloat()) + 1f) / 2f
                drawCircle(Color.White.copy(alpha = k), radius = w * .35f * (0.5f + k), center = center + Offset(cos(b) * (r + w * 1.6f), sin(b) * (r + w * 1.6f)))
            }
        }
        Box(Modifier.padding(width * 1.6f), contentAlignment = Alignment.Center) { content() }
    }
}

/** A small shining label with the tier's medal, e.g. "💎 الماسی". */
@Composable
fun TierChip(tier: Tier) {
    Surface(shape = CircleShape, color = Color(tier.colors[1]).copy(alpha = .28f), modifier = Modifier.clip(CircleShape).shimmer(true, .35f)) {
        Text("${tier.emoji} ${t(tier.fa, tier.en)}", Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
