package com.yadavard.app

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random

/**
 * Yadimo talking on its own: the morning hello at a fixed time (a screen like the alarm screen) and a few short
 * notes during the day, shown in a corner over whatever app is open.
 *
 * The daytime notes use alarms that do NOT wake the phone: if the phone is asleep when one is due, Android
 * delivers it when the screen is next turned on. Yadimo then waits until the phone is unlocked and still in use
 * ten seconds later, so it only ever talks while someone is actually using the phone, and costs no battery.
 */
object YadimoTalk {
    const val CHANNEL = "yadimo_hello"
    private const val HELLO_NOTIFICATION = 71_101
    private const val ACTION_HELLO = "com.yadavard.app.yadimo.HELLO"
    private const val ACTION_NUDGE = "com.yadavard.app.yadimo.NUDGE"
    private const val ACTION_BEFORE = "com.yadavard.app.yadimo.BEFORE"
    /** Minutes before a reminder for the "coming up" note. */
    const val BEFORE_MINUTES = 20
    private const val QUIET_FROM = 22 * 60

    // ---- settings ----

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("yadimo_talk", Context.MODE_PRIVATE)

    /** 0 = never talks on its own, 1 = rarely, 2 = normal, 3 = chatty. */
    fun level(context: Context) = prefs(context).getInt("level", 2)
    fun setLevel(context: Context, v: Int) { prefs(context).edit().putInt("level", v).apply(); schedule(context) }
    fun hello(context: Context) = prefs(context).getBoolean("hello", true)
    fun setHello(context: Context, v: Boolean) { prefs(context).edit().putBoolean("hello", v).apply(); schedule(context) }
    fun helloTime(context: Context): Pair<Int, Int> = prefs(context).getInt("hello_h", 8) to prefs(context).getInt("hello_m", 0)
    fun setHelloTime(context: Context, h: Int, m: Int) {
        prefs(context).edit().putInt("hello_h", h).putInt("hello_m", m).apply(); schedule(context)
    }
    fun helloPart(context: Context, part: String) = prefs(context).getBoolean("hello_$part", part != "sound")
    fun setHelloPart(context: Context, part: String, v: Boolean) = prefs(context).edit().putBoolean("hello_$part", v).apply()
    fun cityId(context: Context): String = prefs(context).getString("city", "").orEmpty()
    fun setCity(context: Context, id: String) = prefs(context).edit().putString("city", id).remove("weather").apply()

    private fun on(context: Context): Boolean { YadimoStore.ensure(context); return YadimoStore.enabled }
    private fun fa(context: Context) = Prefs.language(context) == AppLanguage.FA

    // ---- when ----

    /** Start times (minutes of the day) of the daytime notes for each level. */
    private fun slots(level: Int): List<Int> = when (level) {
        1 -> listOf(15 * 60)
        2 -> listOf(11 * 60, 17 * 60 + 30)
        3 -> listOf(10 * 60, 13 * 60, 16 * 60 + 30, 20 * 60)
        else -> emptyList()
    }

    /** At most this many "coming up" notes a day. */
    private fun beforeLimit(level: Int) = when (level) { 2 -> 3; 3 -> 6; else -> 0 }

    private fun today(zone: ZoneId) = LocalDate.now(zone)
    private fun millis(day: LocalDate, minute: Int, zone: ZoneId) = day.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli()

    private fun pending(context: Context, action: String, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(context, action.hashCode(), Intent(context, YadimoReceiver::class.java).setAction(action)
            .setData(Uri.parse("yadar://yadimo/$action")).apply(extras), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancel(context: Context, action: String) {
        PendingIntent.getBroadcast(context, action.hashCode(), Intent(context, YadimoReceiver::class.java).setAction(action)
            .setData(Uri.parse("yadar://yadimo/$action")), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { context.getSystemService(AlarmManager::class.java).cancel(it); it.cancel() }
    }

    /** An alarm that waits for the phone to be awake instead of waking it. */
    private fun setSleepy(context: Context, at: Long, pi: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java)
        try { if (Scheduler.canExact(context)) manager.setExact(AlarmManager.RTC, at, pi) else manager.set(AlarmManager.RTC, at, pi) }
        catch (_: SecurityException) { manager.set(AlarmManager.RTC, at, pi) }
    }

    /** Plans the next hello and notes from the settings and the reminders. Safe to call any time. */
    fun schedule(context: Context) {
        runCatching { scheduleNow(context.applicationContext) }
    }

    private fun scheduleNow(context: Context) {
        if (!on(context)) { listOf(ACTION_HELLO, ACTION_NUDGE, ACTION_BEFORE).forEach { cancel(context, it) }; return }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val day = today(zone)
        val p = prefs(context)

        // Morning hello. If it was missed today (phone off, alarm late), it still comes within two hours.
        if (hello(context)) {
            val (h, m) = helloTime(context)
            val todayAt = millis(day, h * 60 + m, zone)
            val shown = p.getLong("hello_day", -1) == day.toEpochDay()
            val at = when {
                !shown && now < todayAt -> todayAt
                !shown && now < todayAt + 2 * 3_600_000L -> now + 3_000
                else -> millis(day.plusDays(1), h * 60 + m, zone)
            }
            val manager = context.getSystemService(AlarmManager::class.java)
            val pi = pending(context, ACTION_HELLO)
            try { if (Scheduler.canExact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                  else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        } else cancel(context, ACTION_HELLO)

        val level = level(context)
        if (level == 0 || !Phone.canOverlay(context)) { cancel(context, ACTION_NUDGE); cancel(context, ACTION_BEFORE); return }

        // Daytime notes: the next slot that is not used yet. Each has a window; a note that could not be said in
        // time is dropped. A random few minutes make it feel less like clockwork.
        val doneSlots = if (p.getLong("slots_day", -1) == day.toEpochDay()) p.getStringSet("slots", emptySet()).orEmpty() else emptySet()
        val list = slots(level)
        val random = Random(day.toEpochDay())
        val next = list.indices.map { i ->
            val start = list[i] + random.nextInt(30)
            val end = minOf(start + 180, list.getOrNull(i + 1) ?: QUIET_FROM, QUIET_FROM)
            Triple(i, millis(day, start, zone), millis(day, end, zone))
        }.firstOrNull { (i, _, end) -> i.toString() !in doneSlots && end > now + 60_000 }
        if (next != null) {
            val (i, start, end) = next
            setSleepy(context, maxOf(start, now + 60_000), pending(context, ACTION_NUDGE) {
                putExtra("slot", i); putExtra("day", day.toEpochDay()); putExtra("until", end)
            })
        } else {
            val tomorrow = day.plusDays(1)
            list.firstOrNull()?.let { first ->
                val start = first + Random(tomorrow.toEpochDay()).nextInt(30)
                setSleepy(context, millis(tomorrow, start, zone), pending(context, ACTION_NUDGE) {
                    putExtra("slot", 0); putExtra("day", tomorrow.toEpochDay()); putExtra("until", millis(tomorrow, minOf(start + 180, list.getOrNull(1) ?: QUIET_FROM), zone))
                })
            }
        }

        // "Coming up" note about BEFORE_MINUTES before the next reminder.
        val said = saidToday(context, zone)
        if (beforeLimit(level) > said.count { it.startsWith("before") }) {
            val soon = Repo.all(context).filter { !it.done }
                .mapNotNull { r -> Recurrence.occurrencesIn(r, now + 5 * 60_000L, now + 3 * 86_400_000L, 2).firstOrNull()?.let { it to r } }
                .filter { (at, _) -> "before:$at" !in said }
                .minByOrNull { it.first }
            if (soon != null) {
                val (at, r) = soon
                setSleepy(context, maxOf(at - BEFORE_MINUTES * 60_000L, now + 30_000), pending(context, ACTION_BEFORE) {
                    putExtra("id", r.id); putExtra("at", at)
                })
            } else cancel(context, ACTION_BEFORE)
        } else cancel(context, ACTION_BEFORE)
    }

    // ---- what was said today ----

    private fun saidToday(context: Context, zone: ZoneId): Set<String> {
        val p = prefs(context)
        return if (p.getLong("said_day", -1) == today(zone).toEpochDay()) p.getStringSet("said", emptySet()).orEmpty() else emptySet()
    }

    private fun markSaid(context: Context, kind: String) {
        val zone = ZoneId.systemDefault()
        prefs(context).edit().putLong("said_day", today(zone).toEpochDay()).putStringSet("said", saidToday(context, zone) + kind).apply()
    }

    private fun markSlot(context: Context, slot: Int, day: Long) {
        val p = prefs(context)
        val current = if (p.getLong("slots_day", -1) == day) p.getStringSet("slots", emptySet()).orEmpty() else emptySet()
        p.edit().putLong("slots_day", day).putStringSet("slots", current + slot.toString()).apply()
    }

    // ---- the phone's state ----

    /** Screen on and unlocked, and not on a call. */
    private fun inUse(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        return power.isInteractive && !keyguard.isKeyguardLocked &&
            audio.mode != AudioManager.MODE_IN_CALL && audio.mode != AudioManager.MODE_IN_COMMUNICATION
    }

    private fun quiet(now: Long, context: Context): Boolean {
        val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
        val minute = time.hour * 60 + time.minute
        val (h, m) = helloTime(context)
        return minute >= QUIET_FROM || minute < minOf(h * 60 + m, 9 * 60)
    }

    // ---- receiving ----

    internal fun onAlarm(context: Context, intent: Intent, finish: () -> Unit) {
        AppDisplay.load(context)
        when (intent.action) {
            ACTION_HELLO -> { showHello(context); schedule(context); finish() }
            ACTION_NUDGE, ACTION_BEFORE -> talk(context, intent, finish)
            else -> finish()
        }
    }

    private fun talk(context: Context, intent: Intent, finish: () -> Unit) {
        val now = System.currentTimeMillis()
        val before = intent.action == ACTION_BEFORE
        val zone = ZoneId.systemDefault()
        fun drop() { if (!before) markSlot(context, intent.getIntExtra("slot", 0), intent.getLongExtra("day", 0)); schedule(context); finish() }
        fun retry(delay: Long) {
            setSleepy(context, now + delay, if (before) pending(context, ACTION_BEFORE) { putExtras(intent) } else pending(context, ACTION_NUDGE) { putExtras(intent) })
            finish()
        }
        if (!on(context) || level(context) == 0 || !Phone.canOverlay(context)) { finish(); return }
        val at = intent.getLongExtra("at", 0)
        val until = if (before) at - 5 * 60_000L else intent.getLongExtra("until", 0)
        if (now > until || (!before && quiet(now, context))) { drop(); return }
        val power = context.getSystemService(PowerManager::class.java)
        when {
            // Asleep: this kind of alarm comes back by itself as soon as the screen is turned on.
            !power.isInteractive -> { retry(2 * 60_000L); return }
            !inUse(context) -> { retry(30_000L); return }
        }
        // Still in use ten seconds later: only then say something.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!inUse(context)) { retry(30_000L); return@postDelayed }
            val fa = fa(context)
            val line: YadimoLine? = if (before) {
                val r = Repo.get(context, intent.getLongExtra("id", 0))
                val minutes = ((at - System.currentTimeMillis()) / 60_000L).toInt()
                if (r == null || r.done || minutes < 3) null else YadimoLine("before:$at", YadimoLines.before(r, minutes, fa))
            } else {
                val items = Repo.all(context)
                val day = YadimoLines.day(items, System.currentTimeMillis(), zone)
                val rate = YadimoLines.weekRate(Repo.history(context, System.currentTimeMillis() - 7 * 86_400_000L), System.currentTimeMillis())
                YadimoLines.nudge(day, rate, cachedWeatherLine(context), saidToday(context, zone), System.currentTimeMillis(), zone, fa,
                    (System.currentTimeMillis() / 3_600_000L).toInt())
            }
            if (line == null) { drop(); return@postDelayed }
            markSaid(context, line.kind)
            if (!before) markSlot(context, intent.getIntExtra("slot", 0), intent.getLongExtra("day", 0))
            YadimoOverlay.show(context, line.text) { schedule(context); finish() }
        }, 10_000)
    }

    // ---- morning hello ----

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, if (fa(context)) "سلام صبح یادیمو" else "Yadimo's morning hello", NotificationManager.IMPORTANCE_HIGH).apply {
                description = if (fa(context)) "خلاصهٔ کارهای روز، هر صبح سر ساعتی که انتخاب کرده‌ای" else "Your day at a glance, every morning at the time you chose"
                setSound(null, null); enableVibration(false)
            })
    }

    fun cancelHelloNotification(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(HELLO_NOTIFICATION)

    private fun showHello(context: Context) {
        if (!on(context) || !hello(context)) return
        val zone = ZoneId.systemDefault()
        val day = today(zone)
        val p = prefs(context)
        if (p.getLong("hello_day", -1) == day.toEpochDay()) return
        p.edit().putLong("hello_day", day.toEpochDay()).apply()
        ensureChannel(context)
        val fa = fa(context)
        val open = Intent(context, YadimoHelloActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pi = PendingIntent.getActivity(context, HELLO_NOTIFICATION, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(YadimoLines.greeting(Prefs.userName(context), fa))
            .setContentText(if (fa) "یادیمو برنامهٔ امروزت رو آورده" else "Yadimo brought your plan for today")
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi).setAutoCancel(true)
            .apply { if (Notifier.canFullScreen(context)) setFullScreenIntent(pi, true) }
            .build()
        Notifier.post(context, HELLO_NOTIFICATION, notification)
        // In use: a full-screen intent would only be a small banner, so open the screen directly (allowed with
        // the "display over other apps" permission).
        if (inUse(context) && Phone.canOverlay(context)) runCatching { context.startActivity(open) }
    }

    // ---- weather ----

    fun city(context: Context): YadimoCity? = YadimoLines.city(cityId(context))

    /** Today's weather for the chosen city, from the cache when it is from today. */
    suspend fun weather(context: Context): YadimoWeather? {
        val city = city(context) ?: return null
        val day = today(ZoneId.systemDefault()).toEpochDay()
        cached(context)?.let { (d, w) -> if (d == day) return w }
        val o = runCatching { Account.weather(context, city.lat, city.lon) }.getOrNull()
            ?: runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { direct(city) } }.getOrNull()
            ?: return null
        val w = YadimoWeather(o.optDouble("temp"), o.optInt("code"), o.optDouble("max"), o.optDouble("min"), o.optDouble("rain", 0.0))
        prefs(context).edit().putString("weather", JSONObject(o.toString()).put("day", day).toString()).apply()
        return w
    }

    private fun cached(context: Context): Pair<Long, YadimoWeather>? = prefs(context).getString("weather", null)?.let { raw ->
        runCatching { JSONObject(raw).let { o -> o.getLong("day") to YadimoWeather(o.optDouble("temp"), o.optInt("code"), o.optDouble("max"), o.optDouble("min"), o.optDouble("rain", 0.0)) } }.getOrNull()
    }

    private fun cachedWeatherLine(context: Context): String? {
        val city = city(context) ?: return null
        val (day, w) = cached(context) ?: return null
        if (day != today(ZoneId.systemDefault()).toEpochDay()) return null
        val fa = fa(context)
        return YadimoLines.weatherLine(w, if (fa) city.fa else city.en, fa)
    }

    /** Straight from Open-Meteo, for when the Yadimo server cannot be reached. */
    private fun direct(city: YadimoCity): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&timezone=Asia%2FTehran&forecast_days=1"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 10_000 }
        try {
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val daily = o.getJSONObject("daily"); val current = o.optJSONObject("current")
            return JSONObject().put("temp", current?.optDouble("temperature_2m") ?: 0.0)
                .put("code", daily.optJSONArray("weather_code")?.optInt(0) ?: current?.optInt("weather_code") ?: 0)
                .put("max", daily.getJSONArray("temperature_2m_max").getDouble(0)).put("min", daily.getJSONArray("temperature_2m_min").getDouble(0))
                .put("rain", daily.optJSONArray("precipitation_probability_max")?.optDouble(0) ?: 0.0)
        } finally { conn.disconnect() }
    }

    /** Lets the settings show Yadimo's morning screen right away. */
    fun preview(context: Context) {
        runCatching { context.startActivity(Intent(context, YadimoHelloActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Alarms for Yadimo's hello and notes. */
class YadimoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        var finished = false
        val finish = { if (!finished) { finished = true; runCatching { pending.finish() } } }
        // Never hold the broadcast longer than Android allows.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 50_000)
        runCatching { YadimoTalk.onAlarm(context.applicationContext, intent, finish) }.onFailure { finish() }
    }
}
