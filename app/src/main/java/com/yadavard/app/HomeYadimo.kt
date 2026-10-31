package com.yadavard.app

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Yadimo on the home screen: a little stage across the top where it lives on its own. It strolls from side to
 * side (turning the way it walks), ducks behind the bushes and peeks out, hops, thinks, waves, and now and then
 * says something useful about your day (what is next, what is left, how you are doing). At night it sleeps.
 * Tap it to cheer it up; hold it to visit its home.
 */
@Composable
fun HomeYadimo(items: List<Reminder>, now: Long) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val zone = ZoneId.systemDefault()
    val night = Instant.ofEpochMilli(now).atZone(zone).hour.let { it >= 22 || it < 7 }
    val size = 104.dp
    var width by remember { mutableIntStateOf(0) }
    val x = remember { Animatable(0.5f) }              // 0…1 across the stage
    val y = remember { Animatable(0f) }                // dp up (+) or down into hiding (-)
    var face by remember { mutableFloatStateOf(1f) }    // 1 = as drawn, -1 = mirrored (walking the other way)
    var mood by remember { mutableStateOf(YadimoMood.IDLE) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var poke by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val bushes = remember { listOf(.14f, .58f, .9f) }

    // what it might say: worked out from your reminders each time
    fun line(): String {
        val day = YadimoLines.day(items, System.currentTimeMillis(), zone)
        val pet = YadimoPet.stats(context)
        val name = Prefs.userName(context).let { if (it.isBlank()) "" else t(" $it", " $it") }
        val options = buildList {
            day.upcoming.firstOrNull()?.let { (at, r) ->
                add(t("بعدی: «${r.title}» ساعت ${Dates.formatTime(at, r.zoneId, AppDisplay.language)} ⏰", "Next: “${r.title}” at ${Dates.formatTime(at, r.zoneId, AppDisplay.language)} ⏰"))
            }
            if (day.upcoming.size > 1) add(t("امروز ${n(day.upcoming.size)} کار دیگه داری 💪", "${day.upcoming.size} more things today 💪"))
            if (day.overdue > 0) add(t("${n(day.overdue)} تا کار از قبل مونده 👀", "${day.overdue} left over 👀"))
            if (day.doneToday > 0) add(t("امروز ${n(day.doneToday)} کار انجام دادی؛ آفرین$name! ⭐", "${day.doneToday} done today; well done$name! ⭐"))
            if (day.upcoming.isEmpty() && day.overdue == 0) add(t("امروز خلوته؛ یه کار تازه بنویسیم؟ ✍️", "A quiet day; add something? ✍️"))
            if (pet.hunger < 35) add(t("گشنمه! یه سر به خونه‌ام بزن 🍪", "I'm hungry! Visit my home 🍪"))
            Game.state?.takeIf { it.facts.streak > 1 }?.let { add(t("${n(it.facts.streak)} روزه پشت سر هم کار می‌کنی 🔥", "${it.facts.streak}-day streak 🔥")) }
            add(t("یه لیوان آب بخوریم؟ 💧", "Glass of water? 💧"))
        }
        return options.random()
    }

    suspend fun say(text: String, secs: Long = 4) { bubble = text; delay(secs * 1000); bubble = null }
    suspend fun walkTo(target: Float) {
        face = if (target < x.value) -1f else 1f
        mood = YadimoMood.IDLE
        val ms = (kotlin.math.abs(target - x.value) * 4200).toInt().coerceIn(500, 4000)
        // a little bounce while walking
        scope.launch { repeat(ms / 280) { y.animateTo(5f, tween(140)); y.animateTo(0f, tween(140)) } }
        x.animateTo(target, tween(ms))
    }

    // its own life
    LaunchedEffect(night) {
        if (night) { mood = YadimoMood.SLEEP; x.animateTo(.5f, tween(800)); return@LaunchedEffect }
        delay(600)
        val name = Prefs.userName(context).let { if (it.isBlank()) "" else " $it" }
        say(t("سلام$name! 👋", "Hi$name! 👋"), 3)
        while (true) {
            delay(Random.nextLong(2500, 6000))
            when (Random.nextInt(7)) {
                0, 1 -> walkTo(Random.nextFloat() * .8f + .1f)
                2 -> {   // hides behind a bush, then peeks out somewhere else
                    val bush = bushes.random()
                    walkTo(bush)
                    y.animateTo(-size.value * .75f, tween(450))
                    delay(Random.nextLong(1200, 2600))
                    val other = bushes.filter { it != bush }.random()
                    x.snapTo(other)
                    y.animateTo(-size.value * .35f, spring(Spring.DampingRatioMediumBouncy))   // peek
                    mood = YadimoMood.THINK
                    delay(900)
                    y.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)); mood = YadimoMood.WAVE
                    delay(1200); mood = YadimoMood.IDLE
                }
                3 -> { mood = YadimoMood.CELEBRATE; repeat(2) { y.animateTo(26f, tween(220)); y.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }; delay(600); mood = YadimoMood.IDLE }
                4 -> { mood = YadimoMood.THINK; delay(2200); mood = YadimoMood.IDLE }
                else -> { mood = YadimoMood.TALK; say(line()); mood = YadimoMood.IDLE }
            }
        }
    }
    // a tap: a happy hop and a line
    LaunchedEffect(poke) {
        if (poke == 0) return@LaunchedEffect
        mood = YadimoMood.LOVE
        y.animateTo(30f, tween(200)); y.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
        say(line(), 3); mood = if (night) YadimoMood.SLEEP else YadimoMood.IDLE
    }

    Box(Modifier.fillMaxWidth().height(size + 40.dp).onSizeChanged { width = it.width }) {
        val px = with(density) { size.toPx() }
        val left = ((width - px) * x.value).roundToInt()
        // Yadimo
        Box(Modifier.offset { IntOffset(left, with(density) { (40.dp - y.value.dp).roundToPx() }) }.size(size)
            .graphicsLayer { scaleX = face }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { poke++ }, onLongPress = { context.startActivity(Intent(context, YadimoRoomActivity::class.java)) })
            }) {
            YadimoView(mood, size = size, tappable = false)
        }
        // bushes in front, so it can duck behind them
        bushes.forEach { b ->
            Text("🌿", fontSize = 38.sp, modifier = Modifier.align(Alignment.BottomStart)
                .offset { IntOffset(((width - with(density) { 44.dp.toPx() }) * b).roundToInt(), with(density) { 6.dp.roundToPx() }) })
        }
        // what it says, over its head
        AnimatedVisibility(bubble != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
                Text(bubble.orEmpty(), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}
