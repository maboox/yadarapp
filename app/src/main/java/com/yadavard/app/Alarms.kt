package com.yadavard.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId

private const val CHANNEL = "reminders"
private const val ACTION_FIRE = "com.yadavard.app.FIRE"
private const val ACTION_LEAD = "com.yadavard.app.LEAD"
private const val ACTION_SNOOZE_FIRE = "com.yadavard.app.SNOOZE_FIRE"
private const val ACTION_DONE = "com.yadavard.app.DONE"
private const val ACTION_SNOOZE = "com.yadavard.app.SNOOZE"
private const val ACTION_TEST = "com.yadavard.app.TEST_ALARM"
private const val EXTRA_ID = "id"
private const val EXTRA_DUE = "due"

object ReminderAlarms {
    private fun requestCode(id: Long, kind: Int) = (id * 4 + kind).toInt()
    private fun pending(context: Context, r: Reminder, kind: Int, action: String, due: Long = r.nextAt): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ID, r.id)
            putExtra(EXTRA_DUE, due)
        }
        return PendingIntent.getBroadcast(context, requestCode(r.id, kind), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun cancel(context: Context, r: Reminder) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pending(context, r, 0, ACTION_FIRE))
        manager.cancel(pending(context, r, 1, ACTION_LEAD))
        manager.cancel(pending(context, r, 2, ACTION_SNOOZE_FIRE))
        context.getSystemService(NotificationManager::class.java).cancel(r.id.toInt())
    }
    private fun set(context: Context, at: Long, intent: PendingIntent) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val whenMillis = at.coerceAtLeast(System.currentTimeMillis() + 500)
        try {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, intent)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, intent)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, intent)
        }
    }
    fun schedule(context: Context, r: Reminder) {
        if (r.done) return
        if (r.nextAt > 0) {
            set(context, r.nextAt, pending(context, r, 0, ACTION_FIRE))
            if (r.leadMinutes > 0 && r.nextAt - r.leadMinutes * 60_000L > System.currentTimeMillis())
                set(context, r.nextAt - r.leadMinutes * 60_000L, pending(context, r, 1, ACTION_LEAD))
        }
        if (r.snoozeAt > 0)
            set(context, r.snoozeAt, pending(context, r, 2, ACTION_SNOOZE_FIRE, r.snoozeAt))
    }
    fun scheduleAll(context: Context) = ReminderStore(context).all().forEach { schedule(context, it) }
    fun scheduleTest(context: Context) {
        val intent = PendingIntent.getBroadcast(context, 123456789, Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TEST
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        set(context, System.currentTimeMillis() + 2 * 60_000L, intent)
    }
    fun showTest(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val lang = AppDisplay.storedLanguage(context)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, AppDisplay.text("یادآوری‌ها", "Reminders", lang), NotificationManager.IMPORTANCE_HIGH))
        manager.notify(123456789, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(AppDisplay.text("آزمون اعلان یادار", "Yadar notification test", lang))
            .setContentText(AppDisplay.text("این اعلان باید دو دقیقه بعد از بستن برنامه برسد.", "This alert should arrive two minutes after leaving the app.", lang))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }
    fun show(context: Context, r: Reminder, leading: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val lang = AppDisplay.storedLanguage(context)
        val calendar = AppDisplay.storedCalendar(context)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, AppDisplay.text("یادآوری‌ها", "Reminders", lang), NotificationManager.IMPORTANCE_HIGH))
        val launch = PendingIntent.getActivity(context, r.id.toInt(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done = pending(context, r, 3, ACTION_DONE)
        val snooze = PendingIntent.getBroadcast(context, requestCode(r.id, 2), Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE; putExtra(EXTRA_ID, r.id)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (leading) AppDisplay.text("به‌زودی: ", "Upcoming: ", lang) + r.title else r.title)
            .setContentText(r.note.ifBlank { AppDisplay.dateTime(r.nextAt, ZoneId.of(r.zone), calendar, lang) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.note.ifBlank { AppDisplay.dateTime(r.nextAt, ZoneId.of(r.zone), calendar, lang) }))
            .setContentIntent(launch).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.checkbox_on_background, AppDisplay.text("انجام شد", "Done", lang), done)
            .addAction(android.R.drawable.ic_popup_reminder, AppDisplay.text("۱۰ دقیقه بعد", "In 10 minutes", lang), snooze)
            .build()
        manager.notify(r.id.toInt(), notification)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TEST) { ReminderAlarms.showTest(context); return }
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val store = ReminderStore(context)
        val r = store.get(id) ?: return
        val expected = intent.getLongExtra(EXTRA_DUE, 0)
        when (intent.action) {
            ACTION_LEAD -> if (!r.done && r.nextAt == expected) ReminderAlarms.show(context, r, true)
            ACTION_FIRE -> if (!r.done && r.nextAt == expected && r.lastFiredAt != expected) {
                ReminderAlarms.show(context, r)
                val next = Occurrences.nextAfter(r, maxOf(System.currentTimeMillis(), expected))
                val changed = r.copy(nextAt = next ?: 0, lastFiredAt = expected)
                store.saveAlarmState(changed)
                if (next != null) ReminderAlarms.schedule(context, changed)
            }
            ACTION_SNOOZE_FIRE -> if (r.snoozeAt == expected && expected != 0L) {
                ReminderAlarms.show(context, r)
                store.saveAlarmState(r.copy(snoozeAt = 0))
            }
            ACTION_SNOOZE -> {
                val changed = store.save(r.copy(snoozeAt = System.currentTimeMillis() + 10 * 60_000L))
                ReminderAlarms.schedule(context, changed)
                context.getSystemService(NotificationManager::class.java).cancel(r.id.toInt())
            }
            ACTION_DONE -> {
                val now = System.currentTimeMillis()
                val next = if (r.unit == RepeatUnit.AFTER_DONE_DAYS) {
                    val zone = ZoneId.of(r.zone)
                    Instant.ofEpochMilli(now).atZone(zone).plusDays(r.every.toLong()).toInstant().toEpochMilli()
                } else r.nextAt
                val changed = store.save(r.copy(nextAt = next, done = r.unit == RepeatUnit.NONE,
                    lastCompletedAt = now, snoozeAt = 0))
                ReminderAlarms.cancel(context, r)
                if (changed.nextAt > 0 && !changed.done) ReminderAlarms.schedule(context, changed)
            }
        }
        if (intent.action != ACTION_LEAD) WidgetUpdater.update(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderAlarms.scheduleAll(context)
        WidgetUpdater.update(context)
    }
}
