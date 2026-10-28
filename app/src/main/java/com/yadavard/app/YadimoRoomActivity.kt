package com.yadavard.app

import android.annotation.SuppressLint
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
import org.json.JSONObject

/** Yadimo's room in the app: the same room as the widget, live and at full speed. */
class YadimoRoomActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppDisplay.load(this)
        YadimoStore.ensure(this)
        val web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    evaluateJavascript("room.look(${JSONObject.quote(YadimoStore.config.toJson())}); room.night(${YadimoPet.night()});", null)
                }
            }
            loadUrl("file:///android_asset/yadimo/room.html")
        }
        setContent {
            YadarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    var stats by remember { mutableStateOf(YadimoPet.stats(this)) }
                    LaunchedEffect(Unit) { while (true) { delay(30_000); stats = YadimoPet.stats(this@YadimoRoomActivity) } }
                    fun act(k: String) { web.evaluateJavascript("room.act('$k')", null); YadimoPet.act(this, k); stats = YadimoPet.stats(this) }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().systemBarsPadding()) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("بازگشت", "Back")) }
                                Text(t("اتاق یادیمو", "Yadimo's room"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Stat("🍪", stats.hunger); Stat("⚡", stats.energy); Stat("😊", stats.joy)
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(24.dp))) {
                                AndroidView({ web }, Modifier.fillMaxSize())
                            }
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("feed" to ("🍪" to t("غذا", "Food")), "play" to ("⚽" to t("بازی", "Play")), "charge" to ("⚡" to t("شارژ", "Charge")),
                                    "pet" to ("🤚" to t("ناز", "Pat")), "sleep" to ("😴" to t("خواب", "Sleep"))).forEach { (k, v) ->
                                    FilledTonalButton(onClick = { act(k) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp, 8.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(v.first, fontSize = 20.sp); Text(v.second, fontSize = 11.sp, maxLines = 1) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        this.web = web
    }

    private var web: WebView? = null
    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }
}

@Composable
private fun Stat(emoji: String, value: Int) {
    Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 13.sp)
        LinearProgressIndicator({ value / 100f }, Modifier.width(34.dp).height(5.dp).clip(CircleShape))
    }
}
