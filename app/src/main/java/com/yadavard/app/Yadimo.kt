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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
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

    fun set(context: Context, value: YadimoConfig) { ensure(context); config = value; Prefs.setMascotJson(context, value.toJson()) }
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
               peekIn: Boolean? = null, replay: Int = 0, tappable: Boolean = true, onCatalog: ((String) -> Unit)? = null) {
    val context = LocalContext.current
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
    LaunchedEffect(ready, config) { if (ready) js("yadimo.config(${JSONObject.quote(config.toJson())})") }
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
    Box(modifier.size(size)) {
        AndroidView({ web }, Modifier.fillMaxSize())
        // A layer on top takes the touches, so lists still scroll over Yadimo and taps reach us, not the page.
        Box(Modifier.matchParentSize().pointerInput(tappable) {
            if (tappable) detectTapGestures(onTap = { js("yadimo.poke()") })
        })
    }
}

/** A small speech bubble next to Yadimo. */
@Composable
fun YadimoBubble(text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), color = Color.White, shadowElevation = 6.dp, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color(0xFF231C47),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/** Above the voice recording pill: Yadimo flies up from behind it and says hello. */
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
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(talk, enter = scaleIn(transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            YadimoBubble(if (name.isBlank()) t("سلام! بگو چی یادت بندازم 🙂", "Hi! What should I remind you of? 🙂")
                else t("سلام $name! بگو چی یادت بندازم 🙂", "Hi $name! What should I remind you of? 🙂"))
        }
        YadimoView(mood, size = 110.dp)
    }
}

/**
 * Every now and then Yadimo peeks in from the right edge, says something short and slides away again.
 * [trigger] is what may start a visit (a tab change, opening the app); at most once every two minutes.
 */
@Composable
fun YadimoPeekHost(trigger: Any, message: () -> String, modifier: Modifier = Modifier) {
    if (!rememberYadimoEnabled()) return
    var msg by remember { mutableStateOf<String?>(null) }
    var inside by remember { mutableStateOf(false) }
    var talking by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        val now = System.currentTimeMillis()
        if (YadimoStore.lastPeek != 0L && (now - YadimoStore.lastPeek < 120_000 || Math.random() > .4)) return@LaunchedEffect
        YadimoStore.lastPeek = now
        delay(1500)
        msg = message(); inside = false
        delay(400); inside = true
        delay(900); talking = true
        delay(2800); talking = false
        delay(400); inside = false
        delay(1000); msg = null
    }
    val text = msg ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(talking, enter = scaleIn(transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            YadimoBubble(text)
        }
        YadimoView(if (talking) YadimoMood.TALK else YadimoMood.WAVE, size = 120.dp, peekIn = inside)
    }
}

private fun moodLabel(m: YadimoMood) = when (m) {
    YadimoMood.IDLE -> t("ایستاده", "Idle"); YadimoMood.WAVE -> t("سلام", "Wave"); YadimoMood.TALK -> t("حرف زدن", "Talk")
    YadimoMood.THINK -> t("فکر", "Think"); YadimoMood.RING -> t("زنگ", "Ring"); YadimoMood.CELEBRATE -> t("جشن", "Party")
    YadimoMood.LOVE -> t("ذوق", "Love"); YadimoMood.SLEEP -> t("خواب", "Sleep"); YadimoMood.FLY -> t("پرواز", "Fly")
}

private class Option(val id: String, val name: String, val color: Color? = null)

/** Settings: a live preview and a row of choices for every part (the list comes from the engine itself). */
@Composable
fun YadimoCustomizer() {
    val context = LocalContext.current
    val cfg = rememberYadimoConfig()
    var mood by remember { mutableStateOf(YadimoMood.IDLE) }
    var replay by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<JSONObject?>(null) }
    fun update(c: YadimoConfig) = YadimoStore.set(context, c)
    fun options(key: String): List<Option> = catalog?.optJSONArray(key)?.let { a ->
        (0 until a.length()).map { i -> val o = a.getJSONObject(i)
            Option(o.getString("id"), o.optString("n"), o.optString("c").takeIf { it.isNotBlank() }?.let { Color(android.graphics.Color.parseColor(it)) }) }
    }.orEmpty()

    @Composable
    fun Choices(title: String, key: String, selected: String, warn: Boolean = false, pick: (String) -> Unit) {
        val list = options(key)
        if (list.isEmpty()) return
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { o -> FilterChip(selected == o.id, { pick(o.id) }, { Text(o.name) }) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))),
            contentAlignment = Alignment.Center) {
            YadimoView(mood, Modifier.padding(vertical = 4.dp), size = 190.dp, config = cfg, replay = replay, onCatalog = { catalog = runCatching { JSONObject(it) }.getOrNull() })
        }
        Text(t("حال‌وهوا (فقط برای دیدن)", "Mood (preview only)"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(YadimoMood.entries.toList()) { m -> FilterChip(mood == m, { mood = m; replay++ }, { Text(moodLabel(m)) }) }
        }
        Choices(t("شخصیت", "Character"), "variants", cfg.variant) { update(cfg.copy(variant = it)) }
        val colors = options("colors")
        if (colors.isNotEmpty()) {
            Text(t("رنگ", "Colour"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(colors, key = { it.id }) { o ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(o.color ?: Color.Gray)
                        .border(if (cfg.color == o.id) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { update(cfg.copy(color = o.id)) })
                }
            }
        }
        Choices(t("چهره", "Face"), "faces", cfg.face) { update(cfg.copy(face = it)); mood = YadimoMood.IDLE }
        Choices(t("کلاه و سر", "Hat"), "hats", cfg.hat) { update(cfg.copy(hat = it, costume = "none")) }
        Choices(t("عینک", "Glasses"), "glasses", cfg.glass) { update(cfg.copy(glass = it, costume = "none")) }
        Choices(t("گردن", "Neck"), "neck", cfg.neck) { update(cfg.copy(neck = it, costume = "none")) }
        Choices(t("در دست", "In hand"), "items", cfg.item) { update(cfg.copy(item = it)) }
        Choices(t("لباس شخصیت‌ها", "Character costumes"), "costumes", cfg.costume) {
            update(cfg.copy(costume = if (cfg.costume == it) "none" else it, hat = "none", glass = "none", neck = "none"))
        }
        Choices(t("آیتم‌های شخصیت‌ها", "Character items"), "testItems", cfg.item) { update(cfg.copy(item = it)) }
    }
}
