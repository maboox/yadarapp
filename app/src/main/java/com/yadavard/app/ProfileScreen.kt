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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.clickable
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

/**
 * Profile: you and Yadimo. Your name and username, the level ring, missions (tap one to go and do it), Yadimo's
 * home and settings, badges and level rewards. The app's settings open from the gear at the top.
 * [onMission] takes the missions that are done elsewhere in the app; [settingsAlert] puts a dot on the gear.
 */
@Composable
fun ProfileScreen(padding: PaddingValues, settingsAlert: Boolean, onSettings: () -> Unit, onMission: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Game.sync(context) } }
    val s = Game.state
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    var editName by remember { mutableStateOf(false) }
    var editUsername by remember { mutableStateOf(false) }
    var openYadimo by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp,
        padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(t("پروفایل", "Profile"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = onSettings) {
                    BadgedBox(badge = { if (settingsAlert) Badge() }) { Icon(Icons.Rounded.Settings, t("تنظیمات", "Settings")) }
                }
            }
        }
        item { LevelHeader(s, onName = { editName = true }, onUsername = { editUsername = true }) }
        // Yadimo, its home and its settings in one card
        item { YadimoSettingsCard(openYadimo) { RoomSection() } }
        if (s != null) item {
            MissionsCard(s) { key ->
                when (key) {
                    "name" -> editName = true
                    // a username needs an account: sign in first
                    "username" -> if (Account.isLoggedIn(context)) editUsername = true else Account.loginRequested.value = true
                    // the morning hello is in Yadimo's card below: open it and scroll to it
                    "hello" -> { openYadimo++; scope.launch { list.animateScrollToItem(2) } }
                    else -> onMission(key)
                }
            }
        }
        if (s != null) item { UnlocksCard(s) }
        item {
            Text(t("امتیاز از کجا می‌آید: هر کار انجام‌شده ۱۰ (دیرتر از دو ساعت ۵)، هر روزی که کاری انجام بدهی ۵، ساختن یادآوری ۵ و با صدا ۱۰ (تا ۴۰ امتیاز در روز)، هر یادداشت ۲، هر نشان ۵۰ و مأموریت‌ها.",
                "Points: 10 for each thing done (5 if over two hours late), 5 for each active day, 5 for creating a reminder or 10 by voice (up to 40 a day), 2 per note, 50 per badge, plus missions."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
    if (editName) NameDialog { editName = false }
    if (editUsername) UsernameDialog { editUsername = false }
}

/** Lets the profile header show a name changed elsewhere (onboarding, this dialog). */
object Names { var tick by mutableIntStateOf(0) }

/** Your name: what Yadimo calls you. Signed in, it is also the name others see (in groups, later). */
@Composable
private fun NameDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Prefs.userName(context)) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDone() },
        title = { Text(t("چی صدات کنیم؟", "What should we call you?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(30); error = "" }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(t("اسم یا لقب", "Name or nickname")) }, isError = error.isNotBlank(), modifier = Modifier.fillMaxWidth())
                Text(error.ifBlank { t("یادیمو با همین اسم صدایت می‌کند.", "Yadimo calls you by this name.") },
                    style = MaterialTheme.typography.bodySmall, color = if (error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                // signed in, the account keeps the same name; a name the server refuses (e.g. "admin") is not saved
                val failed = if (Account.isLoggedIn(context)) runCatching { Account.setName(context, name.trim()) }.exceptionOrNull() else null
                busy = false
                if (failed != null && failed.message?.contains("رزرو") == true) { error = failed.message.orEmpty(); return@launch }
                Prefs.setUserName(context, name); Names.tick++
                withContext(Dispatchers.IO) { Game.sync(context) }
                onDone()
            }
        }) { Text(t("ذخیره", "Save")) } },
        dismissButton = { TextButton(onClick = onDone) { Text(t("انصراف", "Cancel")) } })
}

/** Your username: unique, English letters, digits and "_"; checked with the server while you type. */
@Composable
fun UsernameDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = Account.profile.collectAsState().value?.username.orEmpty()
    var name by remember { mutableStateOf(current) }
    var verdict by remember { mutableStateOf<String?>(null) }   // null: not checked yet, "": free, otherwise why not
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(name) {
        verdict = null
        val u = name.trim().removePrefix("@").lowercase()
        if (u.isEmpty() || u == current) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        verdict = runCatching { Account.checkUsername(context, u) ?: "" }.getOrElse { it.message ?: t("بررسی نشد", "Could not check") }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDone() },
        title = { Text(t("نام کاربری", "Username")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    OutlinedTextField(name, { name = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '@' }.take(21) }, singleLine = true,
                        shape = RoundedCornerShape(14.dp), prefix = { Text("@") }, modifier = Modifier.fillMaxWidth(),
                        isError = !verdict.isNullOrEmpty(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                            autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii))
                }
                Text(when (verdict) {
                    null -> t("با این نام دیگران پیدایت می‌کنند (مثلاً برای گروه‌ها). فقط حروف انگلیسی، عدد و _ .", "Others find you by this (e.g. for groups). English letters, digits and _ only.")
                    "" -> t("آزاد است ✓", "Available ✓")
                    else -> verdict!!
                }, style = MaterialTheme.typography.bodySmall,
                    color = when (verdict) { "" -> MaterialTheme.colorScheme.primary; null -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.error })
            }
        },
        confirmButton = { TextButton(enabled = !busy && verdict == "", onClick = {
            busy = true
            scope.launch {
                runCatching { Account.setUsername(context, name.trim().removePrefix("@").lowercase()) }
                    .onSuccess { withContext(Dispatchers.IO) { Game.sync(context) }; onDone() }.onFailure { verdict = it.message ?: t("ذخیره نشد", "Not saved") }
                busy = false
            }
        }) { Text(t("ذخیره", "Save")) } },
        dismissButton = { TextButton(onClick = onDone) { Text(t("انصراف", "Cancel")) } })
}

/** Yadimo's room: open it in the app, and how smooth the home-screen widget should be. */
@Composable
private fun ColumnScope.RoomSection() {
    val context = LocalContext.current
    var fps by remember { mutableIntStateOf(RoomWidgets.fps(context)) }
    run {
        Text(t("یادیمو اینجا زندگی می‌کند: غذا بده، توپ‌بازی کن، شارژش کن، گل‌ها را آب بده، بلندش کن و نازش کن. با هر کاری که انجام می‌دهی هم شادتر می‌شود.",
            "Yadimo lives here: feed it, play ball, charge it, water the plant, pick it up and pat it. It gets happier with every reminder you get done."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { context.startActivity(android.content.Intent(context, YadimoRoomActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
            Text(t("رفتن به خانهٔ یادیمو 🏠", "Visit Yadimo's home 🏠"))
        }
        Text(t("ویجت «خانهٔ یادیمو» را هم می‌توانی از ویجت‌های گوشی روی صفحهٔ اصلی بگذاری؛ هر چه بزرگ‌تر باشد، بیشتر از خانه دیده می‌شود. آن‌جا یادیمو زندگی خودش را می‌کند و با خودش حرف می‌زند؛ با زدن رویش به همین‌جا می‌آیی.",
            "Put the “Yadimo's home” widget on your home screen too; the bigger it is, the more of the home it shows. Yadimo lives its own life there and talks to itself; tap it to come here."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingLabel(t("روانی یادیمو در ویجت", "Widget smoothness"))
        SettingSegmented(listOf(5 to t("کم", "Low"), 15 to t("معمولی", "Normal"), 30 to t("روان", "Smooth")), fps) {
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
private fun LevelHeader(s: Game.State?, onName: () -> Unit, onUsername: () -> Unit) {
    val context = LocalContext.current
    val profile by Account.profile.collectAsState()
    val fa = AppDisplay.language == AppLanguage.FA
    val level = s?.level ?: 1
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // the ring your token purchases earned goes round the level ring
            val tier = profile?.let { Tier.of(it.purchased, it.admin) }
            TierRing(tier, 196.dp, 7.dp) {
                LevelRing(s?.progress ?: 0f, level, 168.dp) {
                    if (rememberYadimoEnabled()) YadimoView(YadimoMood.IDLE, size = 140.dp)
                    else Icon(Icons.Rounded.Face, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(10.dp))
            val name = remember(Names.tick) { Prefs.userName(context) }
            // your name (what Yadimo calls you) and your username (how others find you), each edited with a tap
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onName).padding(horizontal = 8.dp)) {
                Text(name.ifBlank { t("اسمت چیه؟", "What's your name?") }, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Edit, t("ویرایش اسم", "Edit name"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            val p = profile
            if (p != null) TextButton(onClick = onUsername, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(if (p.username.isNotBlank()) "@${p.username}" else t("+ انتخاب نام کاربری", "+ Pick a username"),
                    style = MaterialTheme.typography.labelLarge)
            }
            Text(GameRules.title(level, fa), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (tier != null) { Spacer(Modifier.height(6.dp)); TierChip(tier) }
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
                Spacer(Modifier.height(12.dp))
                BadgeStrip(s)
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
private fun MissionsCard(s: Game.State, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val ready = s.missions.count { it.complete && it.key !in s.claimed }
    val finished = s.missions.count { it.key in s.claimed }
    SettingsCard(t("مأموریت‌ها", "Missions"), Icons.Rounded.Flag, collapsible = true, startExpanded = ready > 0,
        summary = t("${n(finished)} از ${n(s.missions.size)} انجام شد", "${finished} of ${s.missions.size} done") +
            if (ready > 0) t(" • ${n(ready)} جایزه آمادهٔ گرفتن 🎁", " • $ready rewards to claim 🎁") else "") {
        s.missions.forEach { m ->
            val claimed = m.key in s.claimed
            // a mission not done yet takes you to where it is done
            val open = !claimed && !m.complete
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (open) Modifier.clickable { onOpen(m.key) } else Modifier),
                verticalAlignment = Alignment.CenterVertically) {
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
                    else -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, t("برو", "Go"), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Text(t("روی هر مأموریت بزن تا به جای انجامش بروی. جایزهٔ هر مأموریت فقط یک بار داده می‌شود و توکن‌ها به حسابت اضافه می‌شوند.",
            "Tap a mission to go and do it. Each mission pays once; tokens go to your account."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Five badges under the profile: the ones you have first, then the nearest; a tap opens them all. */
@Composable
private fun BadgeStrip(s: Game.State) {
    var all by remember { mutableStateOf(false) }
    val shown = s.badges.sortedWith(compareByDescending<GameGoal> { it.key in s.earned }.thenByDescending { it.progress.toFloat() / it.target }).take(5)
    Surface(onClick = { all = true }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            shown.forEach { b ->
                val got = b.key in s.earned
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp).clip(CircleShape)
                        .background(if (got) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) { Text(b.emoji, fontSize = 20.sp, modifier = Modifier.alpha(if (got) 1f else 0.3f)) }
                }
            }
            Text(t("${n(s.earned.size)}/${n(s.badges.size)} ›", "${s.earned.size}/${s.badges.size} ›"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp))
        }
    }
    if (all) Dialog(onDismissRequest = { all = false }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("نشان‌ها", "Badges"), style = MaterialTheme.typography.titleLarge)
                BadgesGrid(s)
                TextButton(onClick = { all = false }, modifier = Modifier.align(Alignment.End)) { Text(t("بستن", "Close")) }
            }
        }
    }
}

@Composable
private fun BadgesGrid(s: Game.State) {
    var shown by remember { mutableStateOf<GameGoal?>(null) }
    shown?.let { b -> BadgeDialog(b, b.key in s.earned) { shown = null } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("روی هر نشان بزن تا ببینی چطور به دستش می‌آوری.", "Tap a badge to see how to earn it."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.badges.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { b ->
                    val got = b.key in s.earned
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { shown = b }, horizontalAlignment = Alignment.CenterHorizontally) {
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

/** What a badge is and how to earn it (also used for other people's badges in groups). */
@Composable
fun BadgeDialog(b: GameGoal, got: Boolean, onDone: () -> Unit) {
    val how = GameRules.badgeHow(b.key)
    AlertDialog(onDismissRequest = onDone,
        icon = { Text(b.emoji, fontSize = 40.sp) },
        title = { Text(t(b.fa, b.en)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t(how.first, how.second), style = MaterialTheme.typography.bodyLarge)
                Text(if (got) t("این نشان را گرفته‌ای! 🎉", "You have this badge! 🎉")
                    else t("پیشرفت: ${n(b.progress)} از ${n(b.target)} • جایزه: ${n(b.xp)} امتیاز", "Progress: ${b.progress} of ${b.target} • reward: ${b.xp} points"),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(t("باشه", "OK")) } })
}

@Composable
private fun UnlocksCard(s: Game.State) {
    SettingsCard(t("جایزهٔ سطح‌ها", "Level rewards"), Icons.Rounded.EmojiEvents, collapsible = true,
        summary = t("هر سطح چه لباس و وسیله‌ای برای یادیمو باز می‌کند", "What each level opens for Yadimo")) {
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
