package com.yadavard.app

import java.time.Instant
import java.time.ZoneId

// Pure Kotlin domain model: no Android imports so the logic is covered by JVM unit tests.

enum class RepeatUnit { NONE, HOURS, DAYS, WEEKS, MONTHS, YEARS, AFTER_DONE_DAYS }
enum class CalendarSystem { PERSIAN, GREGORIAN }
enum class AppLanguage { FA, EN }
enum class AlertStyle { NOTIFICATION, ALARM }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Reminder(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    /** Category key: a built-in name such as "WORK" or the id of a user-made category. */
    val category: String = CategoryGuess.GENERAL,
    val important: Boolean = false,
    val alertStyle: AlertStyle = AlertStyle.NOTIFICATION,
    /** Anchor of the repeat rule: the first occurrence. */
    val firstAt: Long,
    /** Next occurrence that has not fired yet; 0 when a finished series has nothing left. */
    val nextAt: Long = firstAt,
    val unit: RepeatUnit = RepeatUnit.NONE,
    val every: Int = 1,
    /** ISO weekdays as bits: Monday = bit 0 ... Sunday = bit 6. */
    val weekdays: Int = 0,
    /** Day of month in [calendar]; 0 = same day as [firstAt], -1 = last day of the month. */
    val monthDay: Int = 0,
    val untilAt: Long? = null,
    val leadMinutes: Int = 0,
    /** Re-alert every N minutes until marked done; 0 = off. */
    val nagMinutes: Int = 0,
    val zone: String = ZoneId.systemDefault().id,
    val calendar: CalendarSystem = CalendarSystem.PERSIAN,
    val done: Boolean = false,
    val completedAt: Long = 0,
    val completedCount: Int = 0,
    /** Occurrence that has alerted and waits for "done"; 0 = nothing pending. */
    val pendingAt: Long = 0,
    /** When the last alert for [pendingAt] was shown. */
    val alertedAt: Long = 0,
    val snoozeAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val repeating: Boolean get() = unit != RepeatUnit.NONE && unit != RepeatUnit.AFTER_DONE_DAYS
    /** Repeats at least once every day (hourly, daily or every weekday); such routines are not "events" on a calendar. */
    val isDaily: Boolean get() = unit == RepeatUnit.HOURS || (unit == RepeatUnit.DAYS && every == 1) ||
        (unit == RepeatUnit.WEEKS && every == 1 && (weekdays and 0x7F) == 0x7F)
    val zoneId: ZoneId get() = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())

    /** True when the reminder has alerted (or should have) and still needs the user's attention. */
    fun needsAttention(now: Long): Boolean = !done &&
        (pendingAt > 0 || (!repeating && nextAt in 1 until now))

    /** The time shown for the reminder: the pending occurrence first, otherwise the next one. */
    fun displayAt(now: Long): Long = when {
        pendingAt > 0 -> pendingAt
        else -> nextAt
    }
}

/** State transitions shared by the app UI, notification actions and widgets. */
object ReminderLogic {
    fun complete(r: Reminder, now: Long): Reminder {
        val base = r.copy(completedAt = now, completedCount = r.completedCount + 1,
            snoozeAt = 0, pendingAt = 0, alertedAt = 0)
        return when {
            r.unit == RepeatUnit.NONE -> base.copy(done = true)
            r.unit == RepeatUnit.AFTER_DONE_DAYS -> base.copy(nextAt = afterDone(r, now))
            r.nextAt == 0L -> base.copy(done = true)
            r.pendingAt > 0 -> base
            else -> {
                // Completing an upcoming occurrence early (for example a bill paid ahead of time).
                val next = Recurrence.nextAfter(r, r.nextAt)
                if (next == null) base.copy(done = true) else base.copy(nextAt = next)
            }
        }
    }

    /** Skips the upcoming occurrence of a repeating reminder without counting it as done. */
    fun skip(r: Reminder): Reminder {
        if (!r.repeating) return r
        val next = Recurrence.nextAfter(r, maxOf(r.nextAt, r.pendingAt))
        return if (next == null) r.copy(done = true, pendingAt = 0, snoozeAt = 0)
        else r.copy(nextAt = next, pendingAt = 0, snoozeAt = 0, alertedAt = 0)
    }

    fun snooze(r: Reminder, now: Long, minutes: Int): Reminder {
        val until = now + minutes.coerceIn(1, 24 * 60) * 60_000L
        return r.copy(snoozeAt = until, pendingAt = if (r.pendingAt > 0) r.pendingAt else r.displayAt(now).takeIf { it > 0 } ?: now)
    }

    /** Called when the occurrence at [at] fires. Repeating series move straight to their next occurrence. */
    fun onDue(r: Reminder, at: Long, now: Long): Reminder {
        val next = if (r.repeating) Recurrence.nextAfter(r, maxOf(now, at)) ?: 0L else r.nextAt
        return r.copy(nextAt = next, pendingAt = at, alertedAt = now, snoozeAt = 0)
    }

    fun afterDone(r: Reminder, now: Long): Long {
        val zone = r.zoneId
        val time = Instant.ofEpochMilli(r.firstAt).atZone(zone).toLocalTime().withSecond(0).withNano(0)
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .plusDays(r.every.coerceIn(1, 10_000).toLong()).atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
