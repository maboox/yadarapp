package com.yadavard.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Profile: Yadimo with the level ring, missions, badges, what the next levels open, and Yadimo's settings. */
@Composable
fun ProfileScreen(padding: PaddingValues) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Game.sync(context) } }
    val s = Game.state
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp,
        padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LevelHeader(s) }
        item { RoomCard() }
        if (s != null) {
            item { MissionsCard(s) }
            item { BadgesCard(s) }
            item { UnlocksCard(s) }
        }
        item { YadimoSettingsCard() }
        item {
            Text(t("امتیاز از کجا می‌آید: هر کار انجام‌شده ۱۰ (دیرتر از دو ساعت ۵)، هر روزی که کاری انجام بدهی ۵، ساختن یادآوری ۵ و با صدا ۱۰ (تا ۴۰ امتیاز در روز)، هر یادداشت ۲، هر نشان ۵۰ و مأموریت‌ها.",
                "Points: 10 for each thing done (5 if over two hours late), 5 for each active day, 5 for creating a reminder or 10 by voice (up to 40 a day), 2 per note, 50 per badge, plus missions."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** Yadimo's room: open it in the app, and how smooth the home-screen widget should be. */
@Composable
private fun RoomCard() {
    val context = LocalContext.current
    var fps by remember { mutableIntStateOf(RoomWidgets.fps(context)) }
    SettingsCard(t("خانهٔ یادیمو", "Yadimo's home"), Icons.Rounded.Home) {
        Text(t("یادیمو اینجا زندگی می‌کند: غذا بده، توپ‌بازی کن، شارژش کن، گل‌ها را آب بده، بلندش کن و نازش کن. با هر کاری که انجام می‌دهی هم شادتر می‌شود.",
            "Yadimo lives here: feed it, play ball, charge it, water the plant, pick it up and pat it. It gets happier with every reminder you get done."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { context.startActivity(android.content.Intent(context, YadimoRoomActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
            Text(t("رفتن به خانهٔ یادیمو 🏠", "Visit Yadimo's home 🏠"))
        }
        Text(t("ویجت «خانهٔ یادیمو» را هم می‌توانی از ویجت‌های گوشی روی صفحهٔ اصلی بگذاری؛ هر چه بزرگ‌تر باشد، بیشتر از خانه دیده می‌شود. دکمه‌هایش همین‌جا را باز می‌کنند.",
            "Put the “Yadimo's home” widget on your home screen too; the bigger it is, the more of the home it shows. Its buttons open this screen."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingLabel(t("روانی یادیمو در ویجت", "Widget smoothness"))
        SettingSegmented(listOf(5 to t("کم (۵)", "Low (5)"), 15 to t("معمولی (۱۵)", "Normal (15)"), 30 to t("روان (۳۰)", "Smooth (30)")), fps) {
            fps = it; RoomWidgets.setFps(context, it)
        }
        Text(t("اگر ویجت روی گوشی‌ات کند یا ناقص حرکت کرد، «معمولی» یا «کم» را انتخاب کن.",
            "If the widget stutters on your phone, pick Normal or Low."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Yadimo (or a face) inside a ring that fills up towards the next level. */
@Composable
fun LevelRing(progress: Float, level: Int, size: Dp, content: @Composable () -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.tertiary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension * 0.06f
            val box = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), box, style = Stroke(w))
            drawArc(Brush.sweepGradient(listOf(start, end, start)), -90f, 360f * progress.coerceIn(0.02f, 1f), false, Offset(w / 2, w / 2), box,
                style = Stroke(w, cap = StrokeCap.Round))
        }
        Box(Modifier.padding(size * 0.08f).clip(CircleShape).fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomCenter)) {
            Text(t("سطح ${n(level)}", "Lv $level"), Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LevelHeader(s: Game.State?) {
    val context = LocalContext.current
    val fa = AppDisplay.language == AppLanguage.FA
    val level = s?.level ?: 1
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LevelRing(s?.progress ?: 0f, level, 168.dp) {
                if (rememberYadimoEnabled()) YadimoView(YadimoMood.IDLE, size = 140.dp)
                else Icon(Icons.Rounded.Face, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            val name = remember { Prefs.userName(context) }
            Text(name.ifBlank { t("دوست یادیمو", "Yadimo friend") }, style = MaterialTheme.typography.titleLarge)
            Text(GameRules.title(level, fa), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (s != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator({ s.progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                Spacer(Modifier.height(6.dp))
                Text(s.levelEnd?.let { end -> t("${n(s.xp)} امتیاز • ${n(end - s.xp)} امتیاز تا سطح ${n(level + 1)}", "${s.xp} points • ${end - s.xp} to level ${level + 1}") }
                    ?: t("${n(s.xp)} امتیاز • بالاترین سطح! 🏆", "${s.xp} points • top level! 🏆"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("🔥", n(s.facts.streak), t("روز پیاپی", "day streak"))
                    Stat("✅", n(s.facts.totalDone), t("کار انجام‌شده", "done"))
                    Stat("🏅", n(s.earned.size), t("نشان", "badges"))
                }
            }
        }
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$emoji $value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MissionsCard(s: Game.State) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    SettingsCard(t("مأموریت‌ها", "Missions"), Icons.Rounded.Flag) {
        s.missions.forEach { m ->
            val claimed = m.key in s.claimed
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.emoji, fontSize = 24.sp, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(t(m.fa, m.en), style = MaterialTheme.typography.bodyLarge)
                    Text((if (m.tokens > 0) t("${n(m.tokens)} توکن + ", "${m.tokens} tokens + ") else "") + t("${n(m.xp)} امتیاز", "${m.xp} points"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (!m.complete && m.target > 1) {
                        LinearProgressIndicator({ m.progress.toFloat() / m.target }, Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(CircleShape))
                        Text("${n(m.progress)}/${n(m.target)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                when {
                    claimed -> Icon(Icons.Rounded.CheckCircle, t("گرفته شد", "Claimed"), tint = MaterialTheme.colorScheme.secondary)
                    m.complete -> Button(onClick = {
                        busy = m.key; message = null
                        scope.launch { message = Game.claim(context, m); busy = null }
                    }, enabled = busy == null, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        if (busy == m.key) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(t("دریافت", "Claim"))
                    }
                    else -> Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Text(t("جایزهٔ هر مأموریت فقط یک بار داده می‌شود. توکن‌ها به حساب بازارت اضافه می‌شوند.",
            "Each mission pays once. Tokens go to your Bazaar account."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BadgesCard(s: Game.State) {
    SettingsCard(t("نشان‌ها", "Badges"), Icons.Rounded.MilitaryTech) {
        s.badges.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { b ->
                    val got = b.key in s.earned
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(56.dp).clip(CircleShape)
                            .background(if (got) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center) {
                            Text(b.emoji, fontSize = 26.sp, modifier = Modifier.alpha(if (got) 1f else 0.3f))
                        }
                        Text(t(b.fa, b.en), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
                            color = if (got) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!got) Text("${n(b.progress)}/${n(b.target)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun UnlocksCard(s: Game.State) {
    SettingsCard(t("جایزهٔ سطح‌ها", "Level rewards"), Icons.Rounded.EmojiEvents) {
        Text(t("این وسیله‌ها فروشی نیستند و فقط با رسیدن به سطحشان در کمد یادیمو باز می‌شوند.",
            "These can't be bought; they open in Yadimo's wardrobe when you reach their level."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        YadimoShop.levels.entries.sortedBy { it.value }.forEach { (id, need) ->
            val open = s.level >= need
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t("سطح ${n(need)}", "Level $need"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(72.dp))
                Text(YadimoShop.partName(id), Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, null,
                    tint = if (open) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Celebrates a new level or a new badge, with Yadimo jumping for joy. */
@Composable
fun GameCheerDialog() {
    val cheer = Game.cheer ?: return
    Dialog(onDismissRequest = { Game.cheer = null }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (rememberYadimoEnabled()) YadimoView(YadimoMood.CELEBRATE, size = 150.dp)
                else Text(cheer.badge?.emoji ?: "🎉", fontSize = 64.sp)
                Spacer(Modifier.height(8.dp))
                if (cheer.level != null) {
                    Text(t("سطح ${n(cheer.level)}! 🎉", "Level ${cheer.level}! 🎉"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(GameRules.title(cheer.level, AppDisplay.language == AppLanguage.FA), color = MaterialTheme.colorScheme.primary)
                    if (cheer.unlocked.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(t("باز شد: ", "Unlocked: ") + cheer.unlocked.joinToString(t("، ", ", ")), textAlign = TextAlign.Center)
                    }
                } else cheer.badge?.let { b ->
                    Text(t("نشان تازه: ${b.fa}", "New badge: ${b.en}") + " " + b.emoji, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(t("+${n(b.xp)} امتیاز", "+${b.xp} points"), color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { Game.cheer = null }, modifier = Modifier.fillMaxWidth()) { Text(t("عالیه!", "Great!")) }
            }
        }
    }
}
