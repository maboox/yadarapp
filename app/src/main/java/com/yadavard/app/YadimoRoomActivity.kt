package com.yadavard.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * Yadimo's home: the little game where Yadimo lives (assets/yadimo/game-app.html). Tap the floor, Yadimo, or the
 * things in the room; the buttons below do the same. The widget's buttons open this screen with their action.
 */
class YadimoRoomActivity : ComponentActivity() {
    private var web: WebView? = null
    private var loaded = false
    private var pendingAct: String? = null
    private val stats = mutableStateOf(YadimoPet.Stats(80, 80, 80))

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppDisplay.load(this)
        YadimoStore.ensure(this)
        stats.value = YadimoPet.stats(this)
        pendingAct = intent.getStringExtra("act")
        val w = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    evaluateJavascript("yadimoGame.look(${JSONObject.quote(YadimoStore.config.toJson())}); " +
                        "yadimoGame.load(${JSONObject.quote(YadimoPet.gameState(this@YadimoRoomActivity))}); " +
                        "yadimoGame.night(${YadimoPet.night()}); yadimoGame.say(${JSONObject.quote(YadimoPet.line(this@YadimoRoomActivity))}, 3);", null)
                    loaded = true
                    pendingAct?.let { a -> postDelayed({ act(a) }, 700) }; pendingAct = null
                }
            }
            loadUrl("file:///android_asset/yadimo/game-app.html")
        }
        web = w
        setContent {
            YadarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    // the game keeps the needs while it is open; they are saved every couple of seconds
                    LaunchedEffect(Unit) { while (true) { delay(2000); save() } }
                    val s by stats
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().systemBarsPadding()) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("بازگشت", "Back")) }
                                Text(t("خانهٔ یادیمو", "Yadimo's home"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Stat("🍪", s.hunger); Stat("⚡", s.energy); Stat("😊", s.joy)
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(24.dp))) {
                                AndroidView({ w }, Modifier.fillMaxSize())
                            }
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("feed" to ("🍪" to t("غذا", "Food")), "play" to ("⚽" to t("بازی", "Play")), "charge" to ("⚡" to t("شارژ", "Charge")),
                                    "pet" to ("🤚" to t("ناز", "Pat")), "water" to ("🌱" to t("گل", "Plant")), "sleep" to ("😴" to t("خواب", "Sleep"))).forEach { (k, v) ->
                                    FilledTonalButton(onClick = { act(k) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp, 8.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(v.first, fontSize = 20.sp); Text(v.second, fontSize = 11.sp, maxLines = 1) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun act(k: String) {
        if (!loaded) { pendingAct = k; return }
        web?.evaluateJavascript("yadimoGame.act('$k')", null)
    }

    private fun save() {
        web?.evaluateJavascript("yadimoGame.save()") { raw ->
            val json = runCatching { JSONArray("[$raw]").getString(0) }.getOrNull() ?: return@evaluateJavascript
            YadimoPet.fromGame(this, json); stats.value = YadimoPet.stats(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("act")?.let { act(it) }
    }

    override fun onPause() { save(); RoomWidgets.ids(this).forEach { RoomWidgets.show(this, it) }; super.onPause() }
    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }
}

@Composable
private fun Stat(emoji: String, value: Int) {
    Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 13.sp)
        LinearProgressIndicator({ value / 100f }, Modifier.width(34.dp).height(5.dp).clip(CircleShape))
    }
}
