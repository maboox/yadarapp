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
    const val EXTRA_ALARM = "alarm"

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

    fun scheduleTest(context: Context, delayMillis: Long, alarm: Boolean) {
        val pi = PendingIntent.getBroadcast(context, if (alarm) 2 else 1,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_TEST).putExtra(EXTRA_ALARM, alarm),
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
    // v3 channels are silent: Yadar plays sound and vibration itself (see AlertSound / AlertService),
    // because some phones mute channel sounds of newly installed apps.
    const val CHANNEL_REMINDERS = "yadar_reminders_v3"
    const val CHANNEL_ALARMS = "yadar_alarms_v3"
    const val CHANNEL_UPCOMING = "yadar_upcoming_v2"
    const val CHANNEL_QUIET = "yadar_quiet_v2"
    const val ACTION_DONE = "com.yadavard.app.action.DONE"
    const val ACTION_SNOOZE = "com.yadavard.app.action.SNOOZE"
    const val ACTION_OPEN = "com.yadavard.app.action.OPEN"
    const val ACTION_SILENCE = "com.yadavard.app.action.SILENCE"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_MINUTES = "minutes"
    private const val TEST_NOTIFICATION_ID = 999_999_001
    const val SERVICE_ID = 999_999_002

    fun mainId(id: Long) = if (id == AlertService.TEST_ID) 999_999_003 else (id * 2).toInt()
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
        listOf("reminders", "yadar_reminders_v2", "yadar_alarms_v2").forEach { manager.deleteNotificationChannel(it) }
        manager.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, label("یادآوری‌ها", "Reminders"),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = label("اعلان موعد یادآوری‌ها (صدا را خود یادار پخش می‌کند)", "Due reminders (Yadar plays the sound itself)")
            setSound(null, null); enableVibration(false)
            enableLights(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ALARMS, label("زنگ تمام‌صفحه", "Alarms"),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = label("یادآوری‌هایی که مانند ساعت زنگ‌دار زنگ می‌زنند", "Reminders that ring like an alarm clock")
            setSound(null, null); enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
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

    fun fullScreen(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, AlarmActivity::class.java).apply {
            data = Uri.parse("yadar://alarm-screen/$id")
            putExtra(EXTRA_ID, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun post(context: Context, id: Int, notification: Notification) {
        if (!allowed(context)) return
        try { context.getSystemService(NotificationManager::class.java).notify(id, notification) }
        catch (_: SecurityException) { }
    }

    private fun label(context: Context, fa: String, en: String) =
        if (Prefs.language(context) == AppLanguage.FA) fa else en

    fun testReminder(context: Context): Reminder {
        val now = System.currentTimeMillis()
        return Reminder(id = AlertService.TEST_ID, title = label(context, "آزمون زنگ یادار", "Yadar alarm test"),
            note = label(context, "اگر صدا را می‌شنوی، زنگ‌ها درست کار می‌کنند.", "If you hear this, alarms work."),
            firstAt = now, pendingAt = now, alertedAt = now, alertStyle = AlertStyle.ALARM)
    }

    fun serviceNotification(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_QUIET).setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(label(context, "یادار", "Yadar")).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    /** Builds the alert notification for a due occurrence. */
    fun buildAlert(context: Context, r: Reminder, occurrence: Long, ring: Boolean, quiet: Boolean = false): Notification {
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
            .setSubText(Categories.of(context, r.category).name(lang))
            .setWhen(occurrence).setShowWhen(true)
            .setContentIntent(if (ring && !quiet) fullScreen(context, r.id) else open(context, r.id))
            .setCategory(if (ring) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(!ring)
            .setSilent(true)
            .addAction(R.drawable.ic_action_done, label(context, "انجام شد", "Done"), broadcast(context, ACTION_DONE, r.id))
            .addAction(R.drawable.ic_action_snooze, label(context, "${Dates.digits(snooze.toString(), lang)} دقیقه بعد", "Snooze $snooze min"),
                broadcast(context, ACTION_SNOOZE, r.id, snooze))
        if (snooze != 60 && !(ring && !quiet)) builder.addAction(R.drawable.ic_action_snooze, label(context, "یک ساعت بعد", "1 hour"),
            broadcast(context, ACTION_SNOOZE, r.id, 60))
        if (ring && !quiet) {
            builder.setFullScreenIntent(fullScreen(context, r.id), true).setOngoing(true)
            builder.addAction(R.drawable.ic_action_snooze, label(context, "قطع زنگ", "Silence"), broadcast(context, ACTION_SILENCE, r.id))
        }
        return builder.build()
    }

    /**
     * Shows the alert for a due reminder and makes it audible: alarm-style reminders ring through
     * [AlertService] until handled; regular ones play one notification sound.
     */
    fun showAlert(context: Context, r: Reminder, occurrence: Long, ring: Boolean, quiet: Boolean = false) {
        context.getSystemService(NotificationManager::class.java).cancel(leadId(r.id))
        if (quiet) {
            AlertService.stop(r.id, remove = false)
            post(context, mainId(r.id), buildAlert(context, r, occurrence, ring = false, quiet = true))
            return
        }
        if (ring && AlertService.ring(context, r.id)) return
        post(context, mainId(r.id), buildAlert(context, r, occurrence, ring))
        if (ring) { AlertSound.vibrate(context, false); AlertSound.playOnce(context, alarm = true) }
        else AlertSound.notifyOnce(context)
    }

    fun showLead(context: Context, r: Reminder) {
        ensureChannels(context)
        val lang = Prefs.language(context)
        val text = Dates.formatDateTime(r.nextAt, r.zoneId, Prefs.calendar(context), lang, withYear = false) +
            " • " + Dates.relative(r.nextAt, System.currentTimeMillis(), lang)
        post(context, leadId(r.id), NotificationCompat.Builder(context, CHANNEL_UPCOMING)
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
        post(context, TEST_NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder).setColor(0xFF5B4BDB.toInt())
            .setContentTitle(label(context, "اعلان آزمایشی یادار ✓", "Yadar test notification ✓"))
            .setContentText(label(context, "اگر صدا را شنیدی، اعلان‌ها درست کار می‌کنند.", "If you heard a sound, notifications work."))
            .setPriority(NotificationCompat.PRIORITY_MAX).setSilent(true).setAutoCancel(true).build())
        AlertSound.vibrate(context, false)
        AlertSound.playOnce(context, alarm = false)
    }

    fun cancel(context: Context, id: Long) {
        AlertService.stop(id, remove = true)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(mainId(id)); manager.cancel(leadId(id))
    }
}

/** Receives scheduled alarms. Every alarm carries a token that must still match the stored state. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Scheduler.ACTION_TEST) {
            if (intent.getBooleanExtra(Scheduler.EXTRA_ALARM, false)) {
                if (!AlertService.ring(context, AlertService.TEST_ID)) Notifier.showTest(context)
            } else Notifier.showTest(context)
            return
        }
        val kind = Scheduler.kindOf(intent) ?: return
        // Keep the process alive for a few seconds so the alert sound can finish playing.
        val pending = goAsync()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { pending.finish() } }, 6_000)
        val r = Repo.get(context, Scheduler.idOf(intent)) ?: return
        val at = Scheduler.atOf(intent)
        val now = System.currentTimeMillis()
        if (r.done) return
        when (kind) {
            AlarmKind.DUE -> {
                if (r.nextAt != at || r.pendingAt == at) return
                Repo.recordSuperseded(context, r, at)
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
                Notifier.showAlert(context, updated, r.pendingAt, ring = r.alertStyle == AlertStyle.ALARM)
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
            Notifier.ACTION_DONE -> if (id == AlertService.TEST_ID) Notifier.cancel(context, id) else Repo.complete(context, id)
            Notifier.ACTION_SILENCE -> if (id == AlertService.TEST_ID) Notifier.cancel(context, id)
                else AlertService.stop(id, remove = false, quietRepost = true)
            Notifier.ACTION_SNOOZE -> if (id == AlertService.TEST_ID) Notifier.cancel(context, id) else Repo.snooze(context, id,
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
