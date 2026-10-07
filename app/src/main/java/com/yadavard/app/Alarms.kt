package com.yadavard.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** What a scheduled alarm does when it fires. Each kind has its own PendingIntent per reminder. */
enum class AlarmKind { DUE, LEAD, SNOOZE, NAG, QUIET }

object Scheduler {
    private const val EXTRA_ID = "id"
    private const val EXTRA_AT = "at"
    private const val ACTION_PREFIX = "com.yadavard.app.alarm."
    const val ACTION_TEST = "com.yadavard.app.alarm.TEST"

    private fun intent(context: Context, id: Long, kind: AlarmKind) = Intent(context, AlarmReceiver::class.java).apply {
        action = ACTION_PREFIX + kind.name
        data = Uri.parse("yadar://alarm/$id/${kind.name}")
    }

    private fun pending(context: Context, id: Long, kind: AlarmKind, at: Long): PendingIntent =
        PendingIntent.getBroadcast(context, 0, intent(context, id, kind).putExtra(EXTRA_ID, id).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun kindOf(intent: Intent): AlarmKind? = intent.action?.removePrefix(ACTION_PREFIX)
        ?.let { name -> AlarmKind.entries.firstOrNull { it.name == name } }
    fun idOf(intent: Intent) = intent.getLongExtra(EXTRA_ID, 0)
    fun atOf(intent: Intent) = intent.getLongExtra(EXTRA_AT, 0)

    fun canExact(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        AlarmKind.entries.forEach { kind ->
            PendingIntent.getBroadcast(context, 0, intent(context, id, kind),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { manager.cancel(it); it.cancel() }
        }
    }

    private fun set(context: Context, id: Long, kind: AlarmKind, triggerAt: Long, token: Long, clock: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, id, kind, token)
        val time = triggerAt.coerceAtLeast(System.currentTimeMillis() + 1_000)
        try {
            when {
                !canExact(context) -> manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                clock && Prefs.reliableMode(context) -> {
                    val show = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pi)
                }
                else -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    /** Recomputes every alarm a reminder needs from its stored state. Safe to call any number of times. */
    fun schedule(context: Context, r: Reminder) {
        cancel(context, r.id)
        if (r.done || r.id == 0L) return
        val now = System.currentTimeMillis()
        if (r.nextAt > 0 && r.nextAt != r.pendingAt) {
            set(context, r.id, AlarmKind.DUE, r.nextAt, r.nextAt, clock = true)
            val leadAt = r.nextAt - r.leadMinutes * 60_000L
            if (r.leadMinutes > 0 && leadAt > now) set(context, r.id, AlarmKind.LEAD, leadAt, r.nextAt, clock = false)
        }
        if (r.snoozeAt > 0) {
            set(context, r.id, AlarmKind.SNOOZE, r.snoozeAt, r.snoozeAt, clock = true)
        } else if (r.pendingAt > 0 && r.alertedAt > 0) {
            if (r.nagMinutes > 0) {
                val nagAt = r.alertedAt + r.nagMinutes * 60_000L
                set(context, r.id, AlarmKind.NAG, nagAt, nagAt, clock = true)
            }
            if (r.alertStyle == AlertStyle.ALARM) {
                val quietAt = r.alertedAt + Prefs.ringMinutes(context) * 60_000L
                if (quietAt > now) set(context, r.id, AlarmKind.QUIET, quietAt, r.alertedAt, clock = false)
            }
        }
    }

    fun rescheduleAll(context: Context) = Repo.all(context).forEach { schedule(context, it) }

    fun scheduleTest(context: Context, delayMillis: Long) {
        val pi = PendingIntent.getBroadcast(context, 0, Intent(context, AlarmReceiver::class.java).setAction(ACTION_TEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(AlarmManager::class.java)
        val time = System.currentTimeMillis() + delayMillis
        try {
            if (canExact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi) }
    }
}

object Notifier {
    const val CHANNEL_REMINDERS = "yadar_reminders_v2"
    const val CHANNEL_ALARMS = "yadar_alarms_v2"
    const val CHANNEL_UPCOMING = "yadar_upcoming_v2"
    const val CHANNEL_QUIET = "yadar_quiet_v2"
    const val ACTION_DONE = "com.yadavard.app.action.DONE"
    const val ACTION_SNOOZE = "com.yadavard.app.action.SNOOZE"
    const val ACTION_OPEN = "com.yadavard.app.action.OPEN"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_MINUTES = "minutes"
    private const val TEST_ID = 999_999_001

    fun mainId(id: Long) = (id * 2).toInt()
    private fun leadId(id: Long) = (id * 2 + 1).toInt()

    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canFullScreen(context: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun ignoringBattery(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val fa = Prefs.language(context) == AppLanguage.FA
        fun label(f: String, e: String) = if (fa) f else e
        manager.deleteNotificationChannel("reminders") // channel of older versions
        manager.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, label("یادآوری‌ها", "Reminders"),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = label("اعلان موعد یادآوری‌ها", "Alerts when a reminder is due")
            enableVibration(true); vibrationPattern = longArrayOf(0, 250, 150, 250)
            enableLights(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ALARMS, label("هشدار تمام‌صفحه", "Alarms"),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = label("یادآوری‌های مهم با صدای زنگ و صفحهٔ کامل", "Important reminders that ring with a full-screen alert")
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_UPCOMING, label("اعلان پیش از موعد", "Advance notices"),
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = label("چند دقیقه یا چند روز پیش از موعد", "Heads-up before a reminder is due")
        })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_QUIET, label("یادآوری‌های بی‌صدا", "Silent reminders"),
            NotificationManager.IMPORTANCE_LOW).apply {
            description = label("یادآوری‌های ازدست‌رفته و بی‌صدا", "Missed and silent reminders")
        })
    }

    private fun broadcast(context: Context, action: String, id: Long, minutes: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, ActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("yadar://action/$id/$action/$minutes")
            putExtra(EXTRA_ID, id); putExtra(EXTRA_MINUTES, minutes)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun open(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            data = Uri.parse("yadar://open/$id")
            putExtra(EXTRA_ID, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun fullScreen(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, AlarmActivity::class.java).apply {
            data = Uri.parse("yadar://alarm-screen/$id")
            putExtra(EXTRA_ID, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (!allowed(context)) return
        try { context.getSystemService(NotificationManager::class.java).notify(id, notification) }
        catch (_: SecurityException) { }
    }

    private fun label(context: Context, fa: String, en: String) =
        if (Prefs.language(context) == AppLanguage.FA) fa else en

    /**
     * Posts the alert for a due reminder. [ring] selects the full-screen ringing style;
     * [quiet] reposts silently (for example after the ringing period ended).
     */
    fun showAlert(context: Context, r: Reminder, occurrence: Long, ring: Boolean, quiet: Boolean = false, repeat: Boolean = false) {
        ensureChannels(context)
        val lang = Prefs.language(context)
        val cal = Prefs.calendar(context)
        val zone = r.zoneId
        val now = System.currentTimeMillis()
        val whenText = Dates.formatDateTime(occurrence, zone, cal, lang, withYear = false)
        val late = now - occurrence > 15 * 60_000L
        val subtitle = buildString {
            append(whenText)
            if (late) append(" • ").append(Dates.relative(occurrence, now, lang))
        }
        val body = if (r.note.isBlank()) subtitle else "${r.note}\n$subtitle"
        val channel = when { quiet -> CHANNEL_QUIET; ring -> CHANNEL_ALARMS; else -> CHANNEL_REMINDERS }
        val snooze = Prefs.snoozeMinutes(context)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(0xFF5B4BDB.toInt())
            .setContentTitle((if (r.important) "❗ " else "") + r.title)
            .setContentText(if (r.note.isBlank()) subtitle else r.note)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(categoryLabel(r.category, lang))
            .setWhen(occurrence).setShowWhen(true)
            .setContentIntent(open(context, r.id))
            .setCategory(if (ring) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(!ring)
            .setOnlyAlertOnce(quiet)
            .addAction(R.drawable.ic_action_done, label(context, "انجام شد", "Done"), broadcast(context, ACTION_DONE, r.id))
            .addAction(R.drawable.ic_action_snooze, label(context, "${Dates.digits(snooze.toString(), lang)} دقیقه بعد", "Snooze $snooze min"),
                broadcast(context, ACTION_SNOOZE, r.id, snooze))
        if (snooze != 60) builder.addAction(R.drawable.ic_action_snooze, label(context, "یک ساعت بعد", "1 hour"),
            broadcast(context, ACTION_SNOOZE, r.id, 60))
        if (repeat) builder.setTicker(r.title)
        if (ring && !quiet) {
            builder.setFullScreenIntent(fullScreen(context, r.id), true).setOngoing(true)
        }
        val notification = builder.build()
        if (ring && !quiet) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        context.getSystemService(NotificationManager::class.java).cancel(leadId(r.id))
        notify(context, mainId(r.id), notification)
    }

    fun showLead(context: Context, r: Reminder) {
        ensureChannels(context)
        val lang = Prefs.language(context)
        val text = Dates.formatDateTime(r.nextAt, r.zoneId, Prefs.calendar(context), lang, withYear = false) +
            " • " + Dates.relative(r.nextAt, System.currentTimeMillis(), lang)
        notify(context, leadId(r.id), NotificationCompat.Builder(context, CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_reminder).setColor(0xFF5B4BDB.toInt())
            .setContentTitle(label(context, "به‌زودی: ", "Coming up: ") + r.title)
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(
                if (r.note.isBlank()) text else "$text\n${r.note}"))
            .setContentIntent(open(context, r.id)).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_action_done, label(context, "انجام شد", "Done"), broadcast(context, ACTION_DONE, r.id))
            .build())
    }

    fun showTest(context: Context) {
        ensureChannels(context)
        notify(context, TEST_ID, NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder).setColor(0xFF5B4BDB.toInt())
            .setContentTitle(label(context, "اعلان آزمایشی یادار ✓", "Yadar test notification ✓"))
            .setContentText(label(context, "اعلان‌ها درست کار می‌کنند.", "Notifications are working."))
            .setPriority(NotificationCompat.PRIORITY_MAX).setAutoCancel(true).build())
    }

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(mainId(id)); manager.cancel(leadId(id))
    }

    fun categoryLabel(c: Category, lang: AppLanguage): String {
        val fa = lang == AppLanguage.FA
        return when (c) {
            Category.GENERAL -> if (fa) "عمومی" else "General"
            Category.PERSONAL -> if (fa) "شخصی" else "Personal"
            Category.WORK -> if (fa) "کار" else "Work"
            Category.HEALTH -> if (fa) "سلامت" else "Health"
            Category.BILLS -> if (fa) "قبض و قسط" else "Bills"
            Category.BIRTHDAY -> if (fa) "تولد و مناسبت" else "Occasions"
            Category.SHOPPING -> if (fa) "خرید" else "Shopping"
            Category.STUDY -> if (fa) "درس" else "Study"
        }
    }
}

/** Receives scheduled alarms. Every alarm carries a token that must still match the stored state. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Scheduler.ACTION_TEST) {
            Notifier.showTest(context)
            return
        }
        val kind = Scheduler.kindOf(intent) ?: return
        val r = Repo.get(context, Scheduler.idOf(intent)) ?: return
        val at = Scheduler.atOf(intent)
        val now = System.currentTimeMillis()
        if (r.done) return
        when (kind) {
            AlarmKind.DUE -> {
                if (r.nextAt != at || r.pendingAt == at) return
                val updated = Repo.save(context, ReminderLogic.onDue(r, at, now))
                // An alarm that was missed long ago (phone off) does not start ringing.
                val ring = r.alertStyle == AlertStyle.ALARM && now - at < 30 * 60_000L
                Notifier.showAlert(context, updated, at, ring = ring)
            }
            AlarmKind.LEAD -> if (r.nextAt == at && r.pendingAt != at) Notifier.showLead(context, r)
            AlarmKind.SNOOZE -> {
                if (r.snoozeAt != at) return
                val occurrence = if (r.pendingAt > 0) r.pendingAt else at
                val updated = Repo.save(context, r.copy(snoozeAt = 0, alertedAt = now, pendingAt = occurrence))
                Notifier.showAlert(context, updated, occurrence, ring = r.alertStyle == AlertStyle.ALARM)
            }
            AlarmKind.NAG -> {
                if (r.pendingAt == 0L || r.snoozeAt > 0 || r.alertedAt + r.nagMinutes * 60_000L != at) return
                val updated = Repo.save(context, r.copy(alertedAt = now))
                Notifier.showAlert(context, updated, r.pendingAt, ring = r.alertStyle == AlertStyle.ALARM, repeat = true)
            }
            AlarmKind.QUIET -> if (r.pendingAt > 0 && r.alertedAt == at && r.snoozeAt == 0L)
                Notifier.showAlert(context, r, r.pendingAt, ring = true, quiet = true)
        }
    }
}

/** Handles "Done" and "Snooze" from notifications, the alarm screen and widgets. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Notifier.EXTRA_ID, 0)
        when (intent.action) {
            Notifier.ACTION_DONE -> Repo.complete(context, id)
            Notifier.ACTION_SNOOZE -> Repo.snooze(context, id,
                intent.getIntExtra(Notifier.EXTRA_MINUTES, Prefs.snoozeMinutes(context)).takeIf { it > 0 } ?: Prefs.snoozeMinutes(context))
        }
    }
}

/** Restores alarms after reboot, app update, clock or time-zone changes and permission grants. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                Notifier.ensureChannels(context)
                Scheduler.rescheduleAll(context)
                WidgetUpdater.update(context)
            } finally { pending.finish() }
        }.start()
    }
}
