@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.yadavard.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch

private class Page(val icon: ImageVector, val colors: List<Color>, val title: () -> String, val body: () -> String,
                   val extra: (@Composable () -> Unit)? = null)

/** First-run introduction; the last page walks through every permission reminders depend on. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val pages = listOf(
        Page(Icons.Rounded.NotificationsActive, listOf(Color(0xFF3B2DB0), Color(0xFF8F7BFF)),
            { t("به یادار خوش آمدی", "Welcome to Yadar") },
            { t("دستیار یادآوری فارسی برای همهٔ کارها: قرص، قسط، تولد، جلسه، خرید و هر چیز دیگر. همه‌چیز فقط روی گوشی خودت می‌ماند.",
                "A reminder assistant for everything — pills, bills, birthdays, meetings and more. Everything stays on your phone.") }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(AppLanguage.FA to "فارسی", AppLanguage.EN to "English").forEach { (lang, label) ->
                    FilterChip(AppDisplay.language == lang, {
                        AppDisplay.language = lang; Prefs.setLanguage(context, lang); Notifier.ensureChannels(context)
                    }, { Text(label) })
                }
            }
        },
        Page(Icons.Rounded.AutoAwesome, listOf(Color(0xFF0F7F70), Color(0xFF3FC9B0)),
            { t("فقط بگو یا بنویس", "Just say or type it") },
            { t("یادار جمله‌های معمولی را می‌فهمد و خودش زمان و تکرار را تشخیص می‌دهد. دکمهٔ میکروفون را بزن و حرف بزن.",
                "Yadar understands plain sentences and figures out the time and repeat by itself. Tap the mic and speak.") }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(t("فردا ساعت ۸ صبح دکتر", "doctor tomorrow 8am"), t("۲ ساعت دیگه قرص", "pills in 2 hours"),
                    t("۲۰ هر ماه اجاره", "rent on the 20th every month"), t("هر شنبه ساعت ۱۸ باشگاه", "gym every saturday 6pm"))
                    .forEach { Example(it) }
            }
        },
        Page(Icons.Rounded.Alarm, listOf(Color(0xFFB4441B), Color(0xFFF0A05A)),
            { t("یادآوری که جا نمی‌ماند", "Reminders you won't miss") },
            { t("برای کارهای مهم «زنگ تمام‌صفحه» را انتخاب کن تا مثل ساعت زنگ‌دار، حتی روی صفحهٔ قفل، زنگ بزند. می‌توانی بگویی تا انجامش ندادی هر چند دقیقه دوباره یادت بیندازد.",
                "Pick “Full-screen alarm” for important things — it rings like an alarm clock, even on the lock screen. It can also keep reminding you until you mark it done.") }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Feature(Icons.Rounded.Snooze, t("تعویق ۵ دقیقه تا ۱ ساعت از خود اعلان", "Snooze right from the notification"))
                Feature(Icons.Rounded.NotificationImportant, t("یادآوری زودتر و تکرار تا انجام", "Advance notice and repeat-until-done"))
                Feature(Icons.Rounded.Repeat, t("روزانه، هفتگی، ماهانهٔ شمسی، هر چند ساعت…", "Daily, weekly, monthly, every few hours…"))
            }
        },
        Page(Icons.Rounded.Widgets, listOf(Color(0xFF8A2D7A), Color(0xFFD6409F)),
            { t("تقویم شمسی و ویجت‌ها", "Calendar and widgets") },
            { t("همه‌چیز را در تقویم ماهانهٔ شمسی یا میلادی ببین. پنج ویجت صفحهٔ اصلی داری: ثبت سریع، امروز، هفتهٔ من، پیش رو و نزدیک‌ترین یادآوری.",
                "See everything on a Persian or Gregorian monthly calendar. Five home-screen widgets: quick add, today, my week, coming up and next reminder.") }) {
            Text(t("برای افزودن ویجت: روی صفحهٔ اصلی گوشی انگشت را نگه دار ← ابزارک‌ها ← یادار", "To add a widget: long-press the home screen → Widgets → Yadar"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        },
        Page(Icons.Rounded.HealthAndSafety, listOf(Color(0xFF1F5FBF), Color(0xFF5AA0FF)),
            { t("یک قدم آخر: دسترسی‌ها", "Last step: permissions") },
            { t("برای اینکه یادآوری‌ها حتماً سر وقت و با صدا برسند، موارد زیر را فعال کن.", "Enable these so reminders always arrive on time and with sound.") }) {
            PermissionChecklist(tick, onChanged = { tick++ }, compact = true)
        },
    )
    val pager = rememberPagerState { pages.size }
    val last = pager.currentPage == pages.lastIndex

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!last) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.lastIndex) } }) { Text(t("رد شدن", "Skip")) }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(if (index == pages.lastIndex) 110.dp else 168.dp).clip(RoundedCornerShape(48.dp))
                        .background(Brush.linearGradient(page.colors)), contentAlignment = Alignment.Center) {
                        Icon(page.icon, null, tint = Color.White, modifier = Modifier.size(if (index == pages.lastIndex) 54.dp else 80.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(page.title(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(page.body(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 26.sp)
                    Spacer(Modifier.height(20.dp))
                    page.extra?.invoke()
                    Spacer(Modifier.height(16.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    repeat(pages.size) { i ->
                        val selected = i == pager.currentPage
                        val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                        val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "dotColor")
                        Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
                    }
                }
                Button(onClick = {
                    if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp)) {
                    Text(if (last) t("شروع کنیم", "Let's start") else t("بعدی", "Next"))
                }
            }
        }
    }
}

@Composable
private fun Example(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatQuote, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
