package com.yadavard.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/*
 * Yadimo, the app's living character. It is drawn by the same 2.5D vector engine that is designed in docs/yadimo
 * (copied into assets/yadimo by tools/sync-yadimo.sh) and runs in a small transparent WebView, so the app shows
 * exactly what the studio shows: turning head, springs, accessories and costumes that follow the head.
 * It is off until switched on in the hidden settings.
 */

/** Which character and parts the user picked. Only ids are stored. */
data class YadimoConfig(
    val variant: String = "bell", val color: String = "lilac", val face: String = "happy", val hat: String = "none",
    val glass: String = "none", val neck: String = "none", val item: String = "none", val costume: String = "none",
) {
    fun toJson(): String = JSONObject().put("variant", variant).put("color", color).put("face", face).put("hat", hat)
        .put("glass", glass).put("neck", neck).put("item", item).put("costume", costume).toString()

    companion object {
        fun fromJson(raw: String?): YadimoConfig = runCatching {
            val o = JSONObject(raw ?: return YadimoConfig())
            YadimoConfig(o.optString("variant", "bell"), o.optString("color", "lilac"), o.optString("face", "happy"),
                o.optString("hat", "none"), o.optString("glass", "none"), o.optString("neck", "none"),
                o.optString("item", "none"), o.optString("costume", "none"))
        }.getOrDefault(YadimoConfig())
    }
}

enum class YadimoMood(val js: String) { IDLE("idle"), WAVE("wave"), TALK("talk"), THINK("think"), RING("ring"),
    CELEBRATE("celebrate"), LOVE("love"), SLEEP("sleep"), FLY("fly") }

object YadimoStore {
    var config by mutableStateOf(YadimoConfig())
        private set
    var enabled by mutableStateOf(false)
        private set
    /** When Yadimo last peeked in from the side, so it does not do it too often. */
    var lastPeek = 0L
    private var loaded = false

    fun ensure(context: Context) {
        if (!loaded) { config = YadimoConfig.fromJson(Prefs.mascotJson(context)); enabled = Prefs.mascotEnabled(context); loaded = true }
    }

    fun set(context: Context, value: YadimoConfig) {
        ensure(context); config = value; Prefs.setMascotJson(context, value.toJson())
        YadimoShop.lookChanged(context, value)
        RoomWidgets.lookChanged(context)
    }

    /** The look saved on the account replaces the one on the phone (signing in on a new phone). */
    fun applyFromAccount(context: Context, value: YadimoConfig) {
        ensure(context)
        if (value == config) return
        config = value; Prefs.setMascotJson(context, value.toJson())
    }
    fun setEnabled(context: Context, value: Boolean) { ensure(context); enabled = value; Prefs.setMascotEnabled(context, value) }
}

@Composable
fun rememberYadimoEnabled(): Boolean { YadimoStore.ensure(LocalContext.current); return YadimoStore.enabled }

@Composable
fun rememberYadimoConfig(): YadimoConfig { YadimoStore.ensure(LocalContext.current); return YadimoStore.config }

/**
 * One Yadimo. [mood] changes are smooth; changing [replay] restarts the mood (for example a new fly-in).
 * [peekIn] true/false slides it in from or out to the right edge of its box. A tap makes it jump with joy.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YadimoView(mood: YadimoMood, modifier: Modifier = Modifier, size: Dp = 120.dp, config: YadimoConfig = rememberYadimoConfig(),
               peekIn: Boolean? = null, replay: Int = 0, tappable: Boolean = true, onCatalog: ((String) -> Unit)? = null,
               /** "galaxy", "" or null = as for this account (the admin's own Yadimo is the galaxy one). */
               fx: String? = null) {
    val context = LocalContext.current
    val admin = Account.profile.collectAsState().value?.admin == true
    val effect = fx ?: if (admin) "galaxy" else ""
    var ready by remember { mutableStateOf(false) }
    val web = remember {
        WebView(context).apply {
            // Without explicit layout params the WebView measures its own (empty) content height and shows nothing.
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = false; isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { ready = true }
            }
            loadUrl("file:///android_asset/yadimo/yadimo.html")
        }
    }
    DisposableEffect(Unit) { onDispose { web.stopLoading(); web.destroy() } }
    fun js(code: String) = web.evaluateJavascript(code, null)
    LaunchedEffect(ready, config, effect) {
        if (ready) js("yadimo.config(${JSONObject.quote(JSONObject(config.toJson()).put("fx", effect).toString())})")
    }
    var peekStarted by remember { mutableStateOf(false) }
    LaunchedEffect(ready, peekIn) {
        if (!ready || peekIn == null) return@LaunchedEffect
        if (!peekStarted) { js("yadimo.peek(false,true)"); peekStarted = true }
        js("yadimo.peek($peekIn,false)")
    }
    LaunchedEffect(ready, mood, replay) { if (ready) js(if (replay > 0) "yadimo.replay('${mood.js}')" else "yadimo.mood('${mood.js}')") }
    LaunchedEffect(ready) {
        if (ready && onCatalog != null) web.evaluateJavascript("yadimo.catalog()") { raw ->
            runCatching { JSONArray("[$raw]").getString(0) }.getOrNull()?.let(onCatalog)
        }
    }
    // Hidden until the page has its look, mood and place: otherwise the first frame shows the default character
    // in the middle for a moment and then it jumps.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(ready) { if (ready) { delay(120); shown = true } }
    val fade by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, androidx.compose.animation.core.tween(220), label = "yadimo")
    Box(modifier.size(size)) {
        AndroidView({ web }, Modifier.fillMaxSize().graphicsLayer { alpha = fade })
        // A layer on top takes the touches, so lists still scroll over Yadimo and taps reach us, not the page.
        Box(Modifier.matchParentSize().pointerInput(tappable) {
            if (tappable) detectTapGestures(onTap = { js("yadimo.poke()") })
        })
    }
}

/**
 * A speech bubble that sits above Yadimo's head, with a little tail pointing down at it. [tailEnd] is how far the
 * tail is from the bubble's end edge, so it points at the head of a Yadimo standing at that end; null centres it.
 */
@Composable
fun YadimoSpeech(text: String, modifier: Modifier = Modifier, tailEnd: Dp? = 40.dp) {
    Column(modifier.widthIn(max = 250.dp), horizontalAlignment = if (tailEnd == null) Alignment.CenterHorizontally else Alignment.End) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 6.dp) {
            // The bubble may sit in a left-to-right layout (so Yadimo comes in from the right edge); the words keep
            // the app's own direction, or a line that starts with a number is laid out backwards.
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Color(0xFF231C47),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection =
                    if (AppDisplay.language == AppLanguage.FA) androidx.compose.ui.text.style.TextDirection.Rtl else androidx.compose.ui.text.style.TextDirection.Ltr),
                fontWeight = FontWeight.Bold)
        }
        androidx.compose.foundation.Canvas(Modifier.padding(end = tailEnd ?: 0.dp).size(18.dp, 10.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, -1f); lineTo(size.width, -1f); lineTo(size.width * (if (tailEnd == null) 0.5f else 0.55f), size.height); close()
            }
            drawPath(path, Color.White)
        }
    }
}

/**
 * Draws its content centred above the top edge of the parent without taking any room, so whatever appears there
 * (Yadimo flying in, a bubble growing) never pushes the rest of the screen around. Put it on a zero-size box at
 * the parent's top centre; [overlap] lowers it so it sits on the parent's edge.
 */
fun Modifier.floatAboveCenter(overlap: Dp = 0.dp): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints())
    // place (not placeRelative): centring is the same in both directions.
    layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height + overlap.roundToPx()) }
}

/** Above the voice recording pill: Yadimo flies up from behind it and says hello, with the bubble over its head. */
@Composable
fun YadimoGreeting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mood by remember { mutableStateOf(YadimoMood.FLY) }
    var talk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1300); mood = YadimoMood.WAVE; talk = true
        delay(2600); mood = YadimoMood.IDLE
        delay(3000); talk = false
    }
    val name = remember { Prefs.userName(context) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(talk, enter = scaleIn(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            YadimoSpeech(if (name.isBlank()) t("سلام! بگو چی یادت بندازم 🙂", "Hi! What should I remind you of? 🙂")
                else t("سلام $name! بگو چی یادت بندازم 🙂", "Hi $name! What should I remind you of? 🙂"), tailEnd = null)
        }
        YadimoView(mood, size = 110.dp, tappable = false)
    }
}

/**
 * Every now and then Yadimo peeks in from the right edge, says something short and slides away again.
 * [trigger] is what may start a visit (a tab change, opening the app); at most once every two minutes. [message]
 * gives what to say, or null when there is nothing new to say today.
 */
@Composable
fun YadimoPeekHost(trigger: Any, message: () -> String?, modifier: Modifier = Modifier) {
    if (!rememberYadimoEnabled()) return
    var msg by remember { mutableStateOf<String?>(null) }
    var inside by remember { mutableStateOf(false) }
    var talking by remember { mutableStateOf(false) }
    var leave by remember { mutableIntStateOf(0) }
    LaunchedEffect(trigger) {
        // A visit cut short (the tab changed meanwhile) must not leave Yadimo stuck on the screen.
        try {
            val now = System.currentTimeMillis()
            if (YadimoStore.lastPeek != 0L && (now - YadimoStore.lastPeek < 120_000 || Math.random() > .4)) return@LaunchedEffect
            delay(1500)
            msg = message() ?: return@LaunchedEffect
            YadimoStore.lastPeek = now
            inside = false
            delay(400); inside = true
            delay(900); talking = true
            delay(2800); talking = false
            delay(400); inside = false
            delay(1000)
        } finally { msg = null; talking = false; inside = false }
    }
    // A tap sends it away at once.
    LaunchedEffect(leave) {
        if (leave == 0) return@LaunchedEffect
        talking = false; inside = false; delay(900); msg = null
    }
    val text = msg ?: return
    // Laid out left-to-right: Yadimo always comes in from the right edge.
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Column(modifier.padding(start = 16.dp), horizontalAlignment = Alignment.End) {
            AnimatedVisibility(talking, enter = scaleIn(transformOrigin = TransformOrigin(0.8f, 1f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                YadimoSpeech(text, Modifier.padding(end = 8.dp).clickable { leave++ }, tailEnd = 36.dp)
            }
            Box(Modifier.clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { leave++ }) {
                YadimoView(if (talking) YadimoMood.TALK else YadimoMood.WAVE, size = 120.dp, peekIn = inside, tappable = false)
            }
        }
    }
}

private fun moodLabel(m: YadimoMood) = when (m) {
    YadimoMood.IDLE -> t("ایستاده", "Idle"); YadimoMood.WAVE -> t("سلام", "Wave"); YadimoMood.TALK -> t("حرف زدن", "Talk")
    YadimoMood.THINK -> t("فکر", "Think"); YadimoMood.RING -> t("زنگ", "Ring"); YadimoMood.CELEBRATE -> t("جشن", "Party")
    YadimoMood.LOVE -> t("ذوق", "Love"); YadimoMood.SLEEP -> t("خواب", "Sleep"); YadimoMood.FLY -> t("پرواز", "Fly")
}

private class Option(val id: String, val name: String, val color: Color? = null)

/** A part being tried on before buying it: its shop id, its name and the look with it on. */
private class Trial(val id: String, val name: String, val config: YadimoConfig)

/**
 * The wardrobe: a live preview and a row of choices for every part (the list comes from the engine itself).
 * Paid parts that are not owned show a lock and their price; tapping one tries it on, and the bar at the bottom
 * buys it with tokens. Bought parts are kept on the account.
 */
@Composable
fun YadimoCustomizer(modifier: Modifier = Modifier, onLeave: () -> Unit = {}, onTopUp: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = rememberYadimoConfig()
    YadimoShop.ensure(context)
    LaunchedEffect(Unit) { YadimoShop.sync(context) }
    val profile by Account.profile.collectAsState()
    var mood by remember { mutableStateOf(YadimoMood.IDLE) }
    var replay by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<JSONObject?>(null) }
    var trial by remember { mutableStateOf<Trial?>(null) }
    var buying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shown = trial?.config ?: cfg
    fun update(c: YadimoConfig) = YadimoStore.set(context, c)
    fun choose(id: String, name: String, next: YadimoConfig) {
        error = null
        if (YadimoShop.canWear(id)) { trial = null; update(next) }
        else { trial = Trial(id, name, next); mood = YadimoMood.CELEBRATE; replay++ }
    }
    fun options(key: String): List<Option> = catalog?.optJSONArray(key)?.let { a ->
        (0 until a.length()).map { i -> val o = a.getJSONObject(i)
            Option(o.getString("id"), o.optString("n"), o.optString("c").takeIf { it.isNotBlank() }?.let { Color(android.graphics.Color.parseColor(it)) }) }
    }.orEmpty()

    @Composable
    fun Choices(title: String, key: String, kind: String, selected: String, pick: (Option) -> YadimoConfig) {
        val list = options(key)
        if (list.isEmpty()) return
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { o ->
                val id = if (kind == "item") YadimoShop.itemId(o.id) else "$kind:${o.id}"
                val locked = !YadimoShop.canWear(id)
                FilterChip(selected == o.id, { choose(id, o.name, pick(o)) }, { Text(o.name) },
                    leadingIcon = if (locked) { { Icon(androidx.compose.material.icons.Icons.Rounded.Lock, null, Modifier.size(16.dp)) } } else null,
                    trailingIcon = if (locked) { { Text(YadimoShop.needsLevel(id)?.let { t("سطح ${n(it)}", "Lv $it") }
                        ?: n(YadimoShop.price(id).let { if (it % 1.0 == 0.0) it.toInt() else it }), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } } else null)
            }
        }
    }

    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))),
            contentAlignment = Alignment.Center) {
            YadimoView(mood, Modifier.padding(vertical = 4.dp), size = 190.dp, config = shown, replay = replay, onCatalog = { catalog = runCatching { JSONObject(it) }.getOrNull() })
            if (trial != null) Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                Text(t("در حال امتحان", "Trying on"), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiary)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("حال‌وهوا (فقط برای دیدن)", "Mood (preview only)"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(YadimoMood.entries.toList()) { m -> FilterChip(mood == m, { mood = m; replay++ }, { Text(moodLabel(m)) }) }
            }
            Choices(t("شخصیت", "Character"), "variants", "variant", shown.variant) { cfg.copy(variant = it.id) }
            val colors = options("colors")
            if (colors.isNotEmpty()) {
                Text(t("رنگ", "Colour"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(colors, key = { it.id }) { o ->
                        val id = "color:${o.id}"
                        Box(Modifier.size(34.dp).clip(CircleShape).background(o.color ?: Color.Gray)
                            .border(if (shown.color == o.id) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { choose(id, o.name, cfg.copy(color = o.id)) }, contentAlignment = Alignment.Center) {
                            if (!YadimoShop.canWear(id)) Icon(androidx.compose.material.icons.Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
            }
            Choices(t("چهره", "Face"), "faces", "face", shown.face) { mood = YadimoMood.IDLE; cfg.copy(face = it.id) }
            Choices(t("کلاه و سر", "Hat"), "hats", "hat", shown.hat) { cfg.copy(hat = it.id, costume = "none") }
            Choices(t("عینک", "Glasses"), "glasses", "glass", shown.glass) { cfg.copy(glass = it.id, costume = "none") }
            Choices(t("گردن", "Neck"), "neck", "neck", shown.neck) { cfg.copy(neck = it.id, costume = "none") }
            Choices(t("در دست", "In hand"), "items", "item", shown.item) { cfg.copy(item = it.id) }
            Choices(t("لباس شخصیت‌ها", "Character costumes"), "costumes", "costume", shown.costume) {
                cfg.copy(costume = if (cfg.costume == it.id) "none" else it.id, hat = "none", glass = "none", neck = "none")
            }
            Choices(t("وسیله‌های شخصیت‌ها", "Character items"), "testItems", "item", shown.item) { cfg.copy(item = it.id) }
            Text(t("شخصیت‌ها، رنگ‌ها، چهره‌ها، کلاه جشن و عینک گرد رایگان‌اند. بعضی وسیله‌ها فقط با رسیدن به سطحشان باز می‌شوند و بقیه را با توکن می‌خری؛ همه روی حسابت می‌ماند و با ورود دوباره روی هر گوشی برمی‌گردد.",
                "Characters, colours, faces, the party hat and round glasses are free. Some parts open only at a level; the rest are bought with tokens. All of it stays on your account on any phone."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trial?.let { tr -> BuyBar(tr, profile, buying, error,
            onBuy = {
                scope.launch {
                    buying = true; error = YadimoShop.buy(context, tr.id); buying = false
                    if (error == null) { update(tr.config); trial = null; mood = YadimoMood.LOVE; replay++ }
                }
            },
            onCancel = { trial = null; error = null; mood = YadimoMood.IDLE },
            onLogin = { onLeave(); Account.loginRequested.value = true },
            onTopUp = { onTopUp?.invoke() ?: run { onLeave(); Account.buyRequested.value = true } }) }
    }
}

@Composable
private fun BuyBar(tr: Trial, profile: Account.Profile?, buying: Boolean, error: String?, onBuy: () -> Unit, onCancel: () -> Unit,
                   onLogin: () -> Unit, onTopUp: () -> Unit) {
    val price = YadimoShop.price(tr.id)
    val priceText = tokensLabel(price)
    val need = YadimoShop.needsLevel(tr.id)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (need != null) {
            // Earned parts are never sold: they open when the level is reached.
            Text("«${tr.name}» • " + t("در سطح ${n(need)} باز می‌شود", "Opens at level $need"), style = MaterialTheme.typography.titleMedium)
            Text(t("الان سطح ${n(Game.state?.level ?: 1)} هستی. با انجام کارها و مأموریت‌ها در «پروفایل» امتیاز جمع کن؛ این وسیله فروشی نیست.",
                "You're level ${Game.state?.level ?: 1}. Earn points by getting things done and finishing missions in Profile; this one can't be bought."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(t("باشه", "OK")) }
          } else {
            Text("«${tr.name}» • " + YadimoShop.tierName(YadimoShop.tier(tr.id)) + " • " + t("$priceText توکن", "$priceText tokens"),
                style = MaterialTheme.typography.titleMedium)
            if (profile == null) {
                    Text(t("برای خرید وارد حساب بازارت شو؛ خریدها روی حسابت می‌ماند.", "Sign in with Bazaar to buy; purchases stay on your account."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onLogin, modifier = Modifier.weight(1f)) { Text(t("ورود و خرید", "Sign in to buy")) }
                        OutlinedButton(onClick = onCancel) { Text(t("نه، ممنون", "No thanks")) }
                    }
            } else when {
                !(profile.unlimited || profile.balance + 1e-9 >= price) -> {
                    Text(t("موجودی‌ات ${tokensLabel(profile.balance)} توکن است؛ کافی نیست.", "You have ${tokensLabel(profile.balance)} tokens; not enough."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onTopUp, modifier = Modifier.weight(1f)) { Text(t("خرید توکن", "Buy tokens")) }
                        OutlinedButton(onClick = onCancel) { Text(t("انصراف", "Cancel")) }
                    }
                }
                else -> {
                    Text(if (profile.unlimited) t("حساب نامحدود", "Unlimited account") else t("موجودی: ${tokensLabel(profile.balance)} توکن", "Balance: ${tokensLabel(profile.balance)} tokens"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBuy, enabled = !buying, modifier = Modifier.weight(1f)) {
                            if (buying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(t("خرید با $priceText توکن", "Buy for $priceText tokens"))
                        }
                        OutlinedButton(onClick = onCancel) { Text(t("انصراف", "Cancel")) }
                    }
                }
            }
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
        }
    }
}
