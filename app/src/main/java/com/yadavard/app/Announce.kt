package com.yadavard.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Messages to every user, sent by the admin (a new version, holiday greetings…). The app asks the server for new
 * ones when it opens and about every six hours; each message is shown once, in its style: a window in the app,
 * a notification, or a full screen with Yadimo.
 */
object Announce {
    data class Message(val id: String, val title: String, val body: String, val style: String, val buttonText: String,
                       val buttonUrl: String, val belowVersion: Int, val created: Long, val target: JSONObject? = null) {
        fun json(): JSONObject = JSONObject().put("id", id).put("title", title).put("body", body).put("style", style)
            .put("button_text", buttonText).put("button_url", buttonUrl).put("below_version", belowVersion).put("created", created)
            .put("target", target ?: JSONObject.NULL)
        companion object {
            fun of(o: JSONObject) = Message(o.optString("id"), o.optString("title"), o.optString("body"), o.optString("style", "popup"),
                o.optString("button_text"), o.optString("button_url"), o.optInt("below_version"), o.optLong("created"), o.optJSONObject("target"))
        }
    }

    /** Whether a message is meant for this phone: its version, account and how much the app is used. */
    fun matches(context: Context, m: Message): Boolean {
        val v = version(context)
        if (m.belowVersion > 0 && v >= m.belowVersion) return false
        val t = m.target ?: return true
        Account.load(context)
        t.optJSONArray("users")?.takeIf { it.length() > 0 }?.let { list ->
            val me = Account.profile.value?.id ?: return false
            return (0 until list.length()).any { list.optString(it) == me }
        }
        when (t.optString("logged")) { "in" -> if (!Account.isLoggedIn(context)) return false; "out" -> if (Account.isLoggedIn(context)) return false }
        t.optInt("min_version").let { if (it > 0 && v < it) return false }
        t.optInt("max_version").let { if (it > 0 && v >= it) return false }
        val days = Engagement.daysUsed(context)
        val last = Engagement.lastDay(context)
        val today = java.time.LocalDate.now()
        return when (t.optString("usage")) {
            "active" -> days >= 7 && last != null && !last.isBefore(today.minusDays(7))
            "light" -> days < 7
            "inactive" -> last == null || last.isBefore(today.minusDays(14))
            else -> true
        }
    }

    private const val NOTIFICATION = 71_201
    private const val ACTION = "com.yadavard.app.announce.CHECK"

    /** The window to show in the app, if one is waiting. */
    val pending = MutableStateFlow<Message?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("announce", Context.MODE_PRIVATE)
    private fun version(context: Context): Int = runCatching {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }.getOrDefault(0)

    /** Asks every six hours or so; the system may batch it with other work, which is fine. */
    fun schedule(context: Context) {
        val pi = PendingIntent.getBroadcast(context, 71_202, Intent(context, AnnounceReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(AlarmManager.RTC,
            System.currentTimeMillis() + 6 * 3_600_000L, 6 * 3_600_000L, pi)
    }

    /** Fetches new messages and shows them; quiet when offline. [inApp] = the app is open now. */
    suspend fun check(context: Context, inApp: Boolean) = withContext(Dispatchers.IO) {
        val p = prefs(context)
        // A first run starts from now, so a new install doesn't get old news.
        if (!p.contains("since")) p.edit().putLong("since", System.currentTimeMillis()).apply()
        if (inApp) restorePending(context)
        if (System.currentTimeMillis() - p.getLong("checked", 0) < 20 * 60_000L) return@withContext
        val since = p.getLong("since", System.currentTimeMillis())
        val res = runCatching {
            val conn = (URL(Account.server(context) + "/api/yadar/announcements?since=$since").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 10_000; readTimeout = 10_000 }
            try { JSONObject(conn.inputStream.bufferedReader().use { it.readText() }) } finally { conn.disconnect() }
        }.getOrNull() ?: return@withContext
        val items = res.optJSONArray("items") ?: JSONArray()
        var newest = since
        val seen = p.getStringSet("seen", emptySet()).orEmpty()
        for (i in 0 until items.length()) {
            val m = Message.of(items.getJSONObject(i))
            newest = maxOf(newest, m.created)
            if (m.id in seen || !matches(context, m)) continue
            p.edit().putStringSet("seen", (p.getStringSet("seen", emptySet()).orEmpty() + m.id).toList().takeLast(50).toSet()).apply()
            deliver(context, m, inApp)
        }
        p.edit().putLong("since", newest).putLong("checked", System.currentTimeMillis()).apply()
    }

    private fun deliver(context: Context, m: Message, inApp: Boolean) {
        when {
            m.style == "fullscreen" -> { notify(context, m, fullScreen = true); if (inApp) open(context, m) }
            m.style == "notification" -> notify(context, m, fullScreen = false)
            inApp -> pending.value = m
            // a window waits until the app is opened
            else -> prefs(context).edit().putString("pending", m.json().toString()).apply()
        }
    }

    private fun restorePending(context: Context) {
        prefs(context).getString("pending", null)?.let { raw ->
            prefs(context).edit().remove("pending").apply()
            runCatching { pending.value = Message.of(JSONObject(raw)) }
        }
    }

    private fun open(context: Context, m: Message) = runCatching {
        context.startActivity(Intent(context, AnnounceActivity::class.java).putExtra("m", m.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun notify(context: Context, m: Message, fullScreen: Boolean) {
        YadimoTalk.ensureChannel(context)
        val intent = Intent(context, AnnounceActivity::class.java).putExtra("m", m.json().toString()).setData(Uri.parse("yadar://announce/${m.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, NOTIFICATION, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, if (fullScreen) YadimoTalk.CHANNEL else Notifier.CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(m.title.ifBlank { t("پیامی از یادیمو", "A message from Yadimo") }).setContentText(m.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(m.body))
            .setContentIntent(pi).setAutoCancel(true)
            .apply { if (fullScreen && Notifier.canFullScreen(context)) setFullScreenIntent(pi, true) }
            .build()
        Notifier.post(context, NOTIFICATION, n)
    }

    fun cancelNotification(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)

    /** Opens the message's button: a link, or the app's page in Bazaar. */
    fun follow(context: Context, url: String) {
        if (url.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ---- sending (admin) ----

    suspend fun send(context: Context, body: JSONObject): String? = try { Account.announce(context, body); null }
        catch (e: Exception) { e.message?.lineSequence()?.firstOrNull() ?: t("ارسال نشد", "Not sent") }
}

class AnnounceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { AppDisplay.load(context); Announce.check(context.applicationContext, inApp = false) } finally { pending.finish() }
        }
    }
}

/** The window shown in the app for a message. */
@Composable
fun AnnounceDialog() {
    val context = LocalContext.current
    val m by Announce.pending.collectAsState()
    val msg = m ?: return
    AlertDialog(onDismissRequest = { Announce.pending.value = null },
        icon = { if (rememberYadimoEnabled()) YadimoView(YadimoMood.WAVE, size = 110.dp) else Icon(Icons.Rounded.Campaign, null) },
        title = { if (msg.title.isNotBlank()) Text(msg.title, textAlign = TextAlign.Center) },
        text = { Text(msg.body) },
        confirmButton = {
            if (msg.buttonText.isNotBlank() && msg.buttonUrl.isNotBlank())
                TextButton(onClick = { Announce.follow(context, msg.buttonUrl); Announce.pending.value = null }) { Text(msg.buttonText) }
            else TextButton(onClick = { Announce.pending.value = null }) { Text(t("باشه", "OK")) }
        },
        dismissButton = { if (msg.buttonText.isNotBlank() && msg.buttonUrl.isNotBlank()) TextButton(onClick = { Announce.pending.value = null }) { Text(t("بعداً", "Later")) } })
}

/** The full-screen style (and where a tapped notification leads). */
class AnnounceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppDisplay.load(this)
        Announce.cancelNotification(this)
        val m = runCatching { Announce.Message.of(JSONObject(intent.getStringExtra("m") ?: "")) }.getOrNull() ?: run { finish(); return }
        setContent {
            YadarTheme(darkOverride = true) {
                CompositionLocalProvider(LocalLayoutDirection provides if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14102A)) + heroColors().take(2)))) {
                        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (rememberYadimoEnabled()) YadimoView(YadimoMood.WAVE, size = 200.dp)
                            else Icon(Icons.Rounded.Campaign, null, tint = Color.White, modifier = Modifier.size(96.dp))
                            Spacer(Modifier.height(16.dp))
                            if (m.title.isNotBlank()) Text(m.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                            Text(m.body, color = Color.White.copy(alpha = .9f), fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 28.sp)
                            Spacer(Modifier.height(28.dp))
                            if (m.buttonText.isNotBlank() && m.buttonUrl.isNotBlank()) Button(onClick = { Announce.follow(this@AnnounceActivity, m.buttonUrl); finish() },
                                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = heroColors()[0])) {
                                Text(m.buttonText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { finish() }) { Text(t("بستن", "Close"), color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

/** Hidden settings → send a message to everyone (admin only). */
@Composable
fun AnnounceCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("popup") }
    var update by remember { mutableStateOf(false) }
    var audience by remember { mutableStateOf(Audience()) }
    var days by remember { mutableIntStateOf(7) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val version = remember { runCatching { @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).versionCode }.getOrDefault(0) }
    SettingsCard(t("پیام به کاربران", "Message to users"), Icons.Rounded.Campaign) {
        OutlinedTextField(title, { title = it.take(80) }, Modifier.fillMaxWidth(), label = { Text(t("عنوان (اختیاری)", "Title (optional)")) }, singleLine = true)
        OutlinedTextField(body, { body = it.take(1000) }, Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text(t("متن پیام", "Message")) })
        SettingLabel(t("نحوهٔ نمایش", "Shown as"))
        SettingSegmented(listOf("popup" to t("پنجره", "Window"), "notification" to t("اعلان", "Notification"), "fullscreen" to t("صفحهٔ کامل", "Full screen")), style) { style = it }
        Row(Modifier.fillMaxWidth().clickable { update = !update }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(update, { update = it }); Text(t("دکمهٔ «به‌روزرسانی» (صفحهٔ برنامه در بازار)", "“Update” button (the app's Bazaar page)"))
        }
        AudiencePicker(audience) { audience = it }
        SettingLabel(t("تا چند روز نشان داده شود", "Show for"))
        SettingSegmented(listOf(1 to t("۱ روز", "1 day"), 3 to t("۳ روز", "3 days"), 7 to t("۷ روز", "7 days"), 30 to t("۳۰ روز", "30 days")), days) { days = it }
        Text(t("پیام ظرف چند ساعت به همه می‌رسد (هر کس برنامه را باز کند، زودتر). هر کاربر هر پیام را فقط یک بار می‌بیند.",
            "Everyone gets it within a few hours (sooner when they open the app), each person once."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { confirm = true }, enabled = body.isNotBlank() && !sending, modifier = Modifier.fillMaxWidth()) {
            Text(if (audience.everyone) t("ارسال به همه", "Send to everyone") else t("ارسال", "Send"))
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(if (audience.everyone) t("برای همهٔ کاربران فرستاده شود؟", "Send to every user?") else t("برای این کاربران فرستاده شود؟", "Send to these users?")) },
        text = { Text(audience.summary() + "\n\n" + body) },
        confirmButton = { TextButton(onClick = {
            confirm = false; sending = true
            scope.launch {
                val req = JSONObject().put("title", title).put("body", body).put("style", style).put("days", days)
                    .put("target", audience.json(version))
                if (update) req.put("button_text", t("به‌روزرسانی", "Update")).put("button_url", "bazaar://details?id=${context.packageName}")
                val err = Announce.send(context, req)
                sending = false
                result = err ?: t("فرستاده شد ✓", "Sent ✓")
                if (err == null) { title = ""; body = "" }
            }
        }) { Text(t("ارسال", "Send")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("انصراف", "Cancel")) } })
}

/** Who a message goes to. Empty choices mean everyone. */
data class Audience(val everyone: Boolean = true, val logged: String = "", val version: String = "", val usage: String = "",
                    val users: Map<String, String> = emptyMap()) {
    fun json(current: Int): JSONObject? {
        if (everyone) return null
        val o = JSONObject()
        if (users.isNotEmpty()) return o.put("users", JSONArray(users.keys.toList()))
        if (logged.isNotEmpty()) o.put("logged", logged)
        when (version) { "older" -> o.put("max_version", current); "current" -> o.put("min_version", current) }
        if (usage.isNotEmpty()) o.put("usage", usage)
        return o
    }
    fun summary(): String = when {
        everyone -> t("همهٔ کاربران", "Everyone")
        users.isNotEmpty() -> t("کاربران: ", "Users: ") + users.values.joinToString(t("، ", ", "))
        else -> listOfNotNull(
            when (logged) { "in" -> t("واردشده‌ها", "signed in"); "out" -> t("واردنشده‌ها", "not signed in"); else -> null },
            when (version) { "older" -> t("نسخهٔ قدیمی", "older versions"); "current" -> t("نسخهٔ فعلی و جدیدتر", "this version or newer"); else -> null },
            when (usage) { "active" -> t("فعال‌ها", "active"); "light" -> t("کم‌استفاده‌ها", "light users"); "inactive" -> t("مدتی سر نزده‌ها", "inactive"); else -> null },
        ).joinToString(" • ").ifBlank { t("همهٔ کاربران", "Everyone") }
    }
}

@Composable
private fun AudiencePicker(a: Audience, onChange: (Audience) -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).versionCode }.getOrDefault(0) }
    SettingLabel(t("به چه کسانی؟", "To whom?"))
    SettingSegmented(listOf(true to t("همه", "Everyone"), false to t("انتخابی", "Choose")), a.everyone) { onChange(a.copy(everyone = it)) }
    if (a.everyone) return
    var count by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(a) {
        count = null; kotlinx.coroutines.delay(500)
        count = runCatching {
            val res = Account.audience(context, a.json(version) ?: JSONObject())
            if (res.optBoolean("known")) t("حدود ${n(res.optInt("count"))} کاربرِ واردشده", "About ${res.optInt("count")} signed-in users")
            else t("کاربرانی که وارد نشده‌اند را نمی‌شود شمرد", "Users who never signed in can't be counted")
        }.getOrElse { t("تعداد معلوم نشد", "Couldn't count") }
    }
    if (a.users.isEmpty()) {
        SettingLabel(t("حساب", "Account"))
        SettingSegmented(listOf("" to t("همه", "All"), "in" to t("واردشده", "Signed in"), "out" to t("واردنشده", "Not signed in")), a.logged) { onChange(a.copy(logged = it)) }
        SettingLabel(t("نسخهٔ نصب‌شده", "Installed version"))
        SettingSegmented(listOf("" to t("همه", "All"), "older" to t("قدیمی‌تر از ${n(version)}", "Older than $version"), "current" to t("${n(version)} و جدیدتر", "$version or newer")), a.version) { onChange(a.copy(version = it)) }
        SettingLabel(t("میزان استفاده", "How much they use it"))
        SettingSegmented(listOf("" to t("همه", "All"), "active" to t("فعال", "Active"), "light" to t("کم", "Light"), "inactive" to t("سر نزده", "Away")), a.usage) { onChange(a.copy(usage = it)) }
        Text(t("فعال: بیش از ۷ روز استفاده و در هفتهٔ اخیر • کم: کمتر از ۷ روز • سر نزده: دو هفته باز نکرده", "Active: 7+ days, used this week • Light: under 7 days • Away: not opened for two weeks"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // particular users, found by name or phone
    SettingLabel(t("یا کاربران مشخص (با اسم یا شماره)", "Or particular users (by name or phone)"))
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.isBlank()) { found = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(400)
        found = runCatching { Account.adminUsers(context, q).optJSONArray("items")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } } }.getOrNull().orEmpty()
    }
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(t("جست‌وجو…", "Search…")) })
    found.take(8).forEach { u ->
        val id = u.optString("id"); val name = u.optString("name").ifBlank { u.optString("phone").ifBlank { id.take(6) } }
        Row(Modifier.fillMaxWidth().clickable {
            onChange(a.copy(users = if (id in a.users) a.users - id else a.users + (id to name)))
        }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(id in a.users, { onChange(a.copy(users = if (it) a.users + (id to name) else a.users - id)) })
            Column {
                Text(name)
                Text(listOfNotNull(u.optString("version").takeIf { it.isNotBlank() }?.let { t("نسخهٔ ${n(it)}", "v$it") },
                    u.optInt("days_used").takeIf { it > 0 }?.let { t("${n(it)} روز استفاده", "$it days") },
                    u.optInt("done").takeIf { it > 0 }?.let { t("${n(it)} کار انجام‌شده", "$it done") }).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (a.users.isNotEmpty()) Text(t("انتخاب‌شده‌ها: ", "Chosen: ") + a.users.values.joinToString(t("، ", ", ")) + t(" (فقط همین‌ها)", " (only these)"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    count?.let { Text("👥 $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
}
