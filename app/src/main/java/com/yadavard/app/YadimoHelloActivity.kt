package com.yadavard.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/** Yadimo's morning hello: today's plan, the weather and a kind word, shown like the alarm screen. */
class YadimoHelloActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        enableEdgeToEdge()
        AppDisplay.load(this)
        YadimoTalk.cancelHelloNotification(this)
        if (YadimoTalk.helloPart(this, "sound")) runCatching { AlertSound.playOnce(this, alarm = false) }
        setContent {
            YadarTheme(darkOverride = true) {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    HelloScreen(onClose = { finish() }, onOpen = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                        finish()
                    })
                }
            }
        }
    }
}

@Composable
private fun HelloScreen(onClose: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val fa = AppDisplay.language == AppLanguage.FA
    val now = remember { System.currentTimeMillis() }
    val day = remember { YadimoLines.day(Repo.all(context), now, zone) }
    val showTasks = remember { YadimoTalk.helloPart(context, "tasks") }
    val showQuote = remember { YadimoTalk.helloPart(context, "quote") }
    val city = remember { YadimoTalk.city(context).takeIf { YadimoTalk.helloPart(context, "weather") } }
    var weather by remember { mutableStateOf<String?>(null) }
    var weatherTried by remember { mutableStateOf(city == null) }
    LaunchedEffect(Unit) {
        if (city != null) {
            weather = YadimoTalk.weather(context)?.let { YadimoLines.weatherLine(it, if (fa) city.fa else city.en, fa) }
            weatherTried = true
        }
    }
    var mood by remember { mutableStateOf(YadimoMood.FLY) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(1200); mood = YadimoMood.WAVE; shown = 1
        delay(1500); mood = YadimoMood.TALK; shown = 2
        delay(500); shown = 3
        delay(500); shown = 4
        delay(2500); mood = YadimoMood.IDLE
    }
    val yadimo = rememberYadimoEnabled()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14102A)) + heroColors().take(2)))) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            if (yadimo) YadimoView(mood, size = 190.dp)
            else Icon(Icons.Rounded.WbSunny, null, tint = Color(0xFFFFD45C), modifier = Modifier.size(110.dp))
            Spacer(Modifier.height(8.dp))
            AnimatedVisibility(shown >= 1, enter = fadeIn() + slideInVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(YadimoLines.greeting(Prefs.userName(context), fa), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                    Text(Dates.formatDate(LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language, withWeekday = true),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (showTasks) AnimatedVisibility(shown >= 2, enter = fadeIn() + slideInVertically { it / 3 }) {
                HelloCard(t("کارهای امروز", "Today")) {
                    Text(YadimoLines.agenda(day, zone, fa), color = Color.White, fontSize = 16.sp)
                    day.upcoming.take(5).forEach { (at, r) ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Dates.formatTime(at, zone, AppDisplay.language), color = Color(0xFFFFD45C), fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(64.dp))
                            Text(r.title, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                    if (day.upcoming.size > 5) Text(t("و ${n(day.upcoming.size - 5)} کار دیگر…", "and ${day.upcoming.size - 5} more…"),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (city != null) AnimatedVisibility(shown >= 3, enter = fadeIn() + slideInVertically { it / 3 }) {
                HelloCard(t("آب‌وهوا", "Weather")) {
                    Text(weather ?: if (weatherTried) t("آب‌وهوا الان در دسترس نیست.", "Weather isn't available right now.")
                        else t("در حال گرفتن آب‌وهوا…", "Getting the weather…"), color = Color.White, fontSize = 16.sp)
                }
            }
            if (showQuote) AnimatedVisibility(shown >= 4, enter = fadeIn() + slideInVertically { it / 3 }) {
                HelloCard(t("حرف امروز", "Today's thought")) {
                    Text(YadimoLines.quote(LocalDate.now(zone), fa), color = Color.White, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = heroColors()[0])) {
                Text(t("بریم! 💪", "Let's go! 💪"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onOpen) { Text(t("باز کردن یادیمو", "Open Yadimo"), color = Color.White) }
        }
    }
}

@Composable
private fun HelloCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
