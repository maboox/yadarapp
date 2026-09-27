package com.yadavard.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CalendarSystem { PERSIAN, GREGORIAN }
enum class AppLanguage { FA, EN }

/** Display choices are separate from each reminder's calendar-based repeat rule. */
object AppDisplay {
    var language by mutableStateOf(AppLanguage.FA)
        private set
    var calendar by mutableStateOf(CalendarSystem.PERSIAN)
        private set

    fun storedLanguage(context: Context): AppLanguage = runCatching {
        AppLanguage.valueOf(context.getSharedPreferences("display", Context.MODE_PRIVATE)
            .getString("language", "FA") ?: "FA")
    }.getOrDefault(AppLanguage.FA)

    fun storedCalendar(context: Context): CalendarSystem = runCatching {
        CalendarSystem.valueOf(context.getSharedPreferences("display", Context.MODE_PRIVATE)
            .getString("calendar", "PERSIAN") ?: "PERSIAN")
    }.getOrDefault(CalendarSystem.PERSIAN)

    fun load(context: Context) {
        language = storedLanguage(context)
        calendar = storedCalendar(context)
    }
    fun setLanguage(context: Context, value: AppLanguage) {
        context.getSharedPreferences("display", Context.MODE_PRIVATE).edit().putString("language", value.name).apply()
        language = value
    }
    fun setCalendar(context: Context, value: CalendarSystem) {
        context.getSharedPreferences("display", Context.MODE_PRIVATE).edit().putString("calendar", value.name).apply()
        calendar = value
    }
    fun text(fa: String, en: String, lang: AppLanguage = language): String = if (lang == AppLanguage.FA) fa else en
    fun number(value: Any, lang: AppLanguage = language): String =
        if (lang == AppLanguage.FA) PersianDates.digits(value) else value.toString()
    fun numericInput(value: String, maxLength: Int): String = value.mapNotNull { char ->
        when (char) {
            in '0'..'9' -> char
            in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
            in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
            else -> null
        }
    }.take(maxLength).joinToString("")

    private val englishPersianMonths = arrayOf("Farvardin", "Ordibehesht", "Khordad", "Tir",
        "Mordad", "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
    fun date(time: Long, zone: ZoneId = ZoneId.systemDefault(),
             system: CalendarSystem = calendar, lang: AppLanguage = language): String {
        if (system == CalendarSystem.PERSIAN) {
            val d = PersianDates.fromMillis(time, zone)
            val month = if (lang == AppLanguage.EN) englishPersianMonths[d.month - 1] else PersianDates.monthName(d.month)
            return "${number(d.day, lang)} $month ${number(d.year, lang)}"
        }
        val local = Instant.ofEpochMilli(time).atZone(zone)
        val month = local.format(DateTimeFormatter.ofPattern("MMMM",
            if (lang == AppLanguage.FA) Locale.forLanguageTag("fa") else Locale.ENGLISH))
        return "${number(local.dayOfMonth, lang)} $month ${number(local.year, lang)}"
    }
    fun dateTime(time: Long, zone: ZoneId = ZoneId.systemDefault(),
                 system: CalendarSystem = calendar, lang: AppLanguage = language): String {
        val local = Instant.ofEpochMilli(time).atZone(zone)
        return "${date(time, zone, system, lang)} • ${number("%02d:%02d".format(Locale.ROOT, local.hour, local.minute), lang)}"
    }

    data class DateParts(val year: Int, val month: Int, val day: Int)
    fun parts(time: Long, zone: ZoneId, system: CalendarSystem): DateParts =
        if (system == CalendarSystem.PERSIAN) PersianDates.fromMillis(time, zone).let { DateParts(it.year, it.month, it.day) }
        else Instant.ofEpochMilli(time).atZone(zone).let { DateParts(it.year, it.monthValue, it.dayOfMonth) }
    fun monthLength(year: Int, month: Int, zone: ZoneId, system: CalendarSystem): Int =
        if (system == CalendarSystem.PERSIAN) PersianDates.monthLength(year, month, zone)
        else LocalDate.of(year, month, 1).lengthOfMonth()
    fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId, system: CalendarSystem): Long =
        if (system == CalendarSystem.PERSIAN) PersianDates.at(year, month, day, hour, minute, zone)
        else LocalDate.of(year, month, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    fun firstDayOfWeek(system: CalendarSystem): Int = if (system == CalendarSystem.PERSIAN) 6 else 1
    fun weekStart(date: LocalDate, system: CalendarSystem): LocalDate {
        val offset = (date.dayOfWeek.value - firstDayOfWeek(system) + 7) % 7
        return date.minusDays(offset.toLong())
    }
    fun shortWeekday(date: LocalDate, lang: AppLanguage = language): String {
        val en = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val fa = arrayOf("د", "س", "چ", "پ", "ج", "ش", "ی")
        return (if (lang == AppLanguage.EN) en else fa)[date.dayOfWeek.value - 1]
    }
}

internal fun t(fa: String, en: String): String = AppDisplay.text(fa, en)
