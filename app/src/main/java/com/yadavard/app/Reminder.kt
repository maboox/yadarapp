package com.yadavard.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.icu.util.Calendar as IcuCalendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

enum class RepeatUnit { NONE, DAYS, WEEKS, MONTHS, YEARS, AFTER_DONE_DAYS }

data class Reminder(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val firstAt: Long,
    val nextAt: Long = firstAt,
    val unit: RepeatUnit = RepeatUnit.NONE,
    val every: Int = 1,
    val weekdays: Int = 0, // ISO Monday=1, bit 0; Sunday=7, bit 6
    val monthDay: Int = 0, // Persian day; 0 means day of first occurrence
    val persianMonth: Int = 0, // 1..12; 0 means month of first occurrence
    val leadMinutes: Int = 0,
    val untilAt: Long? = null,
    val zone: String = ZoneId.systemDefault().id,
    val done: Boolean = false,
    val lastCompletedAt: Long = 0,
    val lastFiredAt: Long = 0,
    val snoozeAt: Long = 0
)

object PersianDates {
    data class Jalali(val year: Int, val month: Int, val day: Int)
    private val monthNames = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    fun monthName(month: Int): String = monthNames[month - 1]
    fun digits(value: Any): String = value.toString().map { char ->
        if (char in '0'..'9') ('۰'.code + char.code - '0'.code).toChar() else char
    }.joinToString("")
    private fun calendar(zone: ZoneId) = IcuCalendar.getInstance(
        IcuTimeZone.getTimeZone(zone.id), ULocale("fa_IR@calendar=persian")
    ).apply {
        isLenient = false
    }
    fun fromMillis(time: Long, zone: ZoneId): Jalali = calendar(zone).apply { timeInMillis = time }.let {
        Jalali(it.get(IcuCalendar.YEAR), it.get(IcuCalendar.MONTH) + 1, it.get(IcuCalendar.DAY_OF_MONTH))
    }
    fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId): Long {
        require(month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59)
        return calendar(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(IcuCalendar.MILLISECOND, 0)
        }.timeInMillis
    }
    fun monthLength(year: Int, month: Int, zone: ZoneId): Int = calendar(zone).apply {
        clear(); set(year, month - 1, 1)
    }.getActualMaximum(IcuCalendar.DAY_OF_MONTH)
    fun formatDate(time: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = fromMillis(time, zone)
        return "${digits(d.day)} ${monthName(d.month)} ${digits(d.year)}"
    }
    fun format(time: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = Instant.ofEpochMilli(time).atZone(zone)
        return "${formatDate(time, zone)} • ${digits("%02d:%02d".format(local.hour, local.minute))}"
    }
}

/** Computes scheduled occurrences independently of when a notification was delivered or dismissed. */
object Occurrences {
    /** Moves the first due date to a selected weekday or Jalali month day, if needed. */
    fun alignFirst(r: Reminder): Reminder {
        val zone = ZoneId.of(r.zone)
        val start = Instant.ofEpochMilli(r.firstAt).atZone(zone)
        val aligned = when (r.unit) {
            RepeatUnit.WEEKS -> if (r.weekdays == 0) r.firstAt else {
                val offset = (0..6).first { n -> r.weekdays and (1 shl ((start.dayOfWeek.value + n - 1) % 7)) != 0 }
                start.plusDays(offset.toLong()).toInstant().toEpochMilli()
            }
            RepeatUnit.MONTHS, RepeatUnit.YEARS -> if (r.monthDay !in 1..31) r.firstAt else {
                val p = PersianDates.fromMillis(r.firstAt, zone)
                var y = p.year; var m = p.month
                if (p.day > r.monthDay) {
                    if (r.unit == RepeatUnit.YEARS) y++
                    else { m++; if (m == 13) { m = 1; y++ } }
                }
                val day = r.monthDay.coerceAtMost(PersianDates.monthLength(y, m, zone))
                PersianDates.at(y, m, day, start.hour, start.minute, zone)
            }
            else -> r.firstAt
        }
        return r.copy(firstAt = aligned, nextAt = aligned)
    }

    fun nextAfter(r: Reminder, after: Long): Long? {
        if (r.unit == RepeatUnit.NONE || r.unit == RepeatUnit.AFTER_DONE_DAYS) return null
        val step = r.every.coerceIn(1, 3650)
        val zone = runCatching { ZoneId.of(r.zone) }.getOrDefault(ZoneId.systemDefault())
        val first = Instant.ofEpochMilli(r.firstAt).atZone(zone)
        val threshold = max(after, r.firstAt - 1)
        val thresholdLocal = Instant.ofEpochMilli(threshold).atZone(zone)
        val hour = first.hour; val minute = first.minute
        fun accepted(candidate: Long): Boolean = candidate > threshold && (r.untilAt == null || candidate <= r.untilAt)
        when (r.unit) {
            RepeatUnit.DAYS -> {
                val elapsed = ChronoUnit.DAYS.between(first.toLocalDate(), thresholdLocal.toLocalDate())
                var index = max(0L, elapsed / step)
                repeat(4) {
                    val date = first.toLocalDate().plusDays(index * step)
                    val instant = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                    if (accepted(instant)) return instant
                    index++
                }
            }
            RepeatUnit.WEEKS -> {
                val firstMonday = first.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val afterMonday = thresholdLocal.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = max(0L, ChronoUnit.WEEKS.between(firstMonday, afterMonday))
                val start = max(0L, weeks / step - 1)
                val mask = if (r.weekdays != 0) r.weekdays else 1 shl (first.dayOfWeek.value - 1)
                for (week in start..start + 3) for (day in 0..6) {
                    if ((mask and (1 shl day)) == 0) continue
                    val date = firstMonday.plusWeeks(week * step).plusDays(day.toLong())
                    val instant = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                    if (instant < r.firstAt) continue
                    if (accepted(instant)) return instant
                }
            }
            RepeatUnit.MONTHS, RepeatUnit.YEARS -> {
                val base = PersianDates.fromMillis(r.firstAt, zone)
                val current = PersianDates.fromMillis(threshold, zone)
                val baseMonthIndex = base.year * 12L + base.month - 1L
                val delta = (current.year * 12L + current.month - 1L - baseMonthIndex).coerceAtLeast(0)
                val period = if (r.unit == RepeatUnit.YEARS) step * 12L else step.toLong()
                var index = max(0L, delta / period - 1)
                repeat(5) {
                    val monthIndex = baseMonthIndex + index * period
                    val year = (monthIndex / 12).toInt()
                    val month = (monthIndex % 12 + 1).toInt()
                    val desired = if (r.monthDay in 1..31) r.monthDay else base.day
                    val day = desired.coerceAtMost(PersianDates.monthLength(year, month, zone))
                    val instant = PersianDates.at(year, month, day, hour, minute, zone)
                    if (instant >= r.firstAt && accepted(instant)) return instant
                    index++
                }
            }
            else -> Unit
        }
        return null
    }

    fun previews(r: Reminder, count: Int = 3): List<Long> {
        val times = mutableListOf(r.nextAt)
        repeat(count - 1) {
            val next = nextAfter(r, times.last()) ?: return times
            times += next
        }
        return times
    }
}

class ReminderStore(context: Context) : SQLiteOpenHelper(context, "reminders.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        createTable(db, "reminders")
    }
    private fun createTable(db: SQLiteDatabase, name: String) {
        db.execSQL("""CREATE TABLE $name (
            id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT NOT NULL,
            first_at INTEGER NOT NULL, next_at INTEGER NOT NULL, unit TEXT NOT NULL,
            every_n INTEGER NOT NULL, weekdays INTEGER NOT NULL, month_day INTEGER NOT NULL,
            persian_month INTEGER NOT NULL, lead_minutes INTEGER NOT NULL, until_at INTEGER,
            zone TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, completed_at INTEGER NOT NULL DEFAULT 0,
            fired_at INTEGER NOT NULL DEFAULT 0, snooze_at INTEGER NOT NULL DEFAULT 0)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            createTable(db, "reminders_local")
            val columns = "id,title,note,first_at,next_at,unit,every_n,weekdays,month_day,persian_month," +
                "lead_minutes,until_at,zone,done,completed_at,fired_at,snooze_at"
            val active = if (oldVersion == 2) " WHERE cloud_deleted=0" else ""
            db.execSQL("INSERT INTO reminders_local ($columns) SELECT $columns FROM reminders$active")
            db.execSQL("DROP TABLE reminders")
            db.execSQL("ALTER TABLE reminders_local RENAME TO reminders")
        }
    }
    private fun values(r: Reminder) = ContentValues().apply {
        put("title", r.title); put("note", r.note); put("first_at", r.firstAt); put("next_at", r.nextAt)
        put("unit", r.unit.name); put("every_n", r.every); put("weekdays", r.weekdays)
        put("month_day", r.monthDay); put("persian_month", r.persianMonth)
        put("lead_minutes", r.leadMinutes); if (r.untilAt == null) putNull("until_at") else put("until_at", r.untilAt)
        put("zone", r.zone); put("done", if (r.done) 1 else 0); put("completed_at", r.lastCompletedAt)
        put("fired_at", r.lastFiredAt); put("snooze_at", r.snoozeAt)
    }
    fun save(r: Reminder): Reminder {
        val id = if (r.id == 0L) writableDatabase.insertOrThrow("reminders", null, values(r)) else {
            writableDatabase.update("reminders", values(r), "id=?", arrayOf(r.id.toString())); r.id
        }
        return r.copy(id = id)
    }
    fun saveAlarmState(r: Reminder) {
        val values = ContentValues().apply {
            put("next_at", r.nextAt); put("fired_at", r.lastFiredAt); put("snooze_at", r.snoozeAt)
        }
        writableDatabase.update("reminders", values, "id=?", arrayOf(r.id.toString()))
    }
    fun delete(id: Long) { writableDatabase.delete("reminders", "id=?", arrayOf(id.toString())) }
    fun get(id: Long): Reminder? = readableDatabase.query("reminders", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) fromCursor(c) else null
    }
    fun all(): List<Reminder> = readableDatabase.query("reminders", null, null, null, null, null, "next_at ASC").use { c ->
        buildList { while (c.moveToNext()) add(fromCursor(c)) }
    }
    private fun fromCursor(c: android.database.Cursor): Reminder {
        fun number(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
        fun integer(name: String) = c.getInt(c.getColumnIndexOrThrow(name))
        fun string(name: String) = c.getString(c.getColumnIndexOrThrow(name))
        val untilIndex = c.getColumnIndexOrThrow("until_at")
        return Reminder(number("id"), string("title"), string("note"), number("first_at"), number("next_at"),
            runCatching { RepeatUnit.valueOf(string("unit")) }.getOrDefault(RepeatUnit.NONE), integer("every_n"),
            integer("weekdays"), integer("month_day"), integer("persian_month"), integer("lead_minutes"),
            if (c.isNull(untilIndex)) null else c.getLong(untilIndex), string("zone"), integer("done") == 1,
            number("completed_at"), number("fired_at"), number("snooze_at"))
    }
}
