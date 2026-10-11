package com.yadavard.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** Persian (Jalali) calendar conversion based on the jalaali-js algorithm (valid for years 1178..3177). */
object Jalali {
    data class Date(val year: Int, val month: Int, val day: Int)

    private val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635,
        2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)

    private class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun cal(jy: Int): Cal {
        require(jy >= breaks.first() && jy < breaks.last()) { "Jalali year out of range: $jy" }
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += jump / 33 * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += n / 33 * 8 + (n % 33 + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - (gy / 100 + 1) * 3 / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + (jump + 4) / 33 * 33
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4
        return Cal(leap, gy, march)
    }

    fun isLeap(year: Int): Boolean = cal(year).leap == 0

    fun monthLength(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        isLeap(year) -> 30
        else -> 29
    }

    fun toEpochDay(year: Int, month: Int, day: Int): Long {
        val c = cal(year)
        return LocalDate.of(c.gy, 3, c.march).toEpochDay() + (month - 1) * 31 - month / 7 * (month - 7) + day - 1
    }

    fun fromEpochDay(epochDay: Long): Date {
        val gy = LocalDate.ofEpochDay(epochDay).year
        var jy = gy - 621
        val c = cal(jy)
        var k = (epochDay - LocalDate.of(gy, 3, c.march).toEpochDay()).toInt()
        if (k >= 0) {
            if (k <= 185) return Date(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (c.leap == 1) k += 1
        }
        return Date(jy, 7 + k / 30, k % 30 + 1)
    }
}

/** Calendar-aware date math and formatting shared by the app, notifications and widgets. */
object Dates {
    data class Parts(val year: Int, val month: Int, val day: Int)

    private val persianMonths = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    private val persianMonthsLatin = arrayOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad",
        "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
    private val gregorianMonthsFa = arrayOf("ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
    private val gregorianMonthsEn = arrayOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")
    // Indexed by ISO day of week - 1 (Monday first).
    private val weekdaysFa = arrayOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")
    private val weekdaysFaShort = arrayOf("د", "س", "چ", "پ", "ج", "ش", "ی")
    private val weekdaysEn = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val weekdaysEnShort = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun localDate(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun parts(date: LocalDate, cal: CalendarSystem): Parts = if (cal == CalendarSystem.PERSIAN)
        Jalali.fromEpochDay(date.toEpochDay()).let { Parts(it.year, it.month, it.day) }
    else Parts(date.year, date.monthValue, date.dayOfMonth)

    fun parts(time: Long, zone: ZoneId, cal: CalendarSystem): Parts = parts(localDate(time, zone), cal)

    fun date(year: Int, month: Int, day: Int, cal: CalendarSystem): LocalDate = if (cal == CalendarSystem.PERSIAN)
        LocalDate.ofEpochDay(Jalali.toEpochDay(year, month, day)) else LocalDate.of(year, month, day)

    fun monthLength(year: Int, month: Int, cal: CalendarSystem): Int =
        if (cal == CalendarSystem.PERSIAN) Jalali.monthLength(year, month) else YearMonth.of(year, month).lengthOfMonth()

    fun at(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    fun startOfDay(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun firstDayOfWeek(cal: CalendarSystem): DayOfWeek =
        if (cal == CalendarSystem.PERSIAN) DayOfWeek.SATURDAY else DayOfWeek.MONDAY

    fun weekStart(date: LocalDate, cal: CalendarSystem): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek(cal)))

    /** First day of the month containing [date] in [cal]. */
    fun monthStart(date: LocalDate, cal: CalendarSystem): LocalDate {
        val p = parts(date, cal)
        return date(p.year, p.month, 1, cal)
    }

    fun shiftMonth(date: LocalDate, delta: Int, cal: CalendarSystem): LocalDate {
        val p = parts(date, cal)
        val index = p.year * 12 + (p.month - 1) + delta
        val year = Math.floorDiv(index, 12)
        val month = Math.floorMod(index, 12) + 1
        return date(year, month, minOf(p.day, monthLength(year, month, cal)), cal)
    }

    fun digits(text: String, lang: AppLanguage): String = if (lang == AppLanguage.EN) text else buildString {
        text.forEach { c -> append(if (c in '0'..'9') ('۰' + (c - '0')) else c) }
    }

    /** Converts Persian/Arabic-Indic digits to ASCII and drops everything else. */
    fun asciiDigits(text: String, maxLength: Int = 10): String = text.mapNotNull { c ->
        when (c) {
            in '0'..'9' -> c
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            else -> null
        }
    }.take(maxLength).joinToString("")

    fun monthName(month: Int, cal: CalendarSystem, lang: AppLanguage): String = when {
        cal == CalendarSystem.PERSIAN && lang == AppLanguage.FA -> persianMonths[month - 1]
        cal == CalendarSystem.PERSIAN -> persianMonthsLatin[month - 1]
        lang == AppLanguage.FA -> gregorianMonthsFa[month - 1]
        else -> gregorianMonthsEn[month - 1]
    }

    fun weekdayName(day: DayOfWeek, lang: AppLanguage, short: Boolean = false): String {
        val i = day.value - 1
        return when {
            lang == AppLanguage.FA && short -> weekdaysFaShort[i]
            lang == AppLanguage.FA -> weekdaysFa[i]
            short -> weekdaysEnShort[i]
            else -> weekdaysEn[i]
        }
    }

    fun monthTitle(date: LocalDate, cal: CalendarSystem, lang: AppLanguage): String {
        val p = parts(date, cal)
        return "${monthName(p.month, cal, lang)} ${digits(p.year.toString(), lang)}"
    }

    fun formatDate(date: LocalDate, cal: CalendarSystem, lang: AppLanguage,
                   withYear: Boolean = true, withWeekday: Boolean = false): String {
        val p = parts(date, cal)
        val body = buildString {
            append(digits(p.day.toString(), lang)).append(' ').append(monthName(p.month, cal, lang))
            if (withYear) append(' ').append(digits(p.year.toString(), lang))
        }
        if (!withWeekday) return body
        return weekdayName(date.dayOfWeek, lang) + (if (lang == AppLanguage.FA) "، " else ", ") + body
    }

    fun formatTime(time: Long, zone: ZoneId, lang: AppLanguage): String {
        val local = Instant.ofEpochMilli(time).atZone(zone)
        return digits("%02d:%02d".format(java.util.Locale.ROOT, local.hour, local.minute), lang)
    }

    fun formatTime(hour: Int, minute: Int, lang: AppLanguage): String =
        digits("%02d:%02d".format(java.util.Locale.ROOT, hour, minute), lang)

    fun formatDateTime(time: Long, zone: ZoneId, cal: CalendarSystem, lang: AppLanguage, withYear: Boolean = true): String =
        formatDate(localDate(time, zone), cal, lang, withYear) + " • " + formatTime(time, zone, lang)

    /** "Today", "Tomorrow", "Yesterday" or a weekday with date. */
    fun friendlyDay(date: LocalDate, today: LocalDate, cal: CalendarSystem, lang: AppLanguage): String {
        val fa = lang == AppLanguage.FA
        return when (date) {
            today -> if (fa) "امروز" else "Today"
            today.plusDays(1) -> if (fa) "فردا" else "Tomorrow"
            today.minusDays(1) -> if (fa) "دیروز" else "Yesterday"
            else -> formatDate(date, cal, lang, withYear = date.year != today.year || abs(date.toEpochDay() - today.toEpochDay()) > 300,
                withWeekday = abs(date.toEpochDay() - today.toEpochDay()) < 7)
        }
    }

    /** Short relative description such as "in 2 hours" or "۵ دقیقه پیش". */
    fun relative(target: Long, now: Long, lang: AppLanguage): String {
        val fa = lang == AppLanguage.FA
        val diff = target - now
        val minutes = abs(diff) / 60_000L
        if (minutes < 1) return if (fa) "همین الان" else "now"
        val (value, faUnit, enUnit) = when {
            minutes < 60 -> Triple(minutes, "دقیقه", "min")
            minutes < 60 * 24 -> Triple(minutes / 60, "ساعت", if (minutes / 60 == 1L) "hour" else "hours")
            minutes < 60 * 24 * 30 -> Triple(minutes / (60 * 24), "روز", if (minutes / (60 * 24) == 1L) "day" else "days")
            minutes < 60 * 24 * 365 -> Triple(minutes / (60 * 24 * 30), "ماه", if (minutes / (60 * 24 * 30) == 1L) "month" else "months")
            else -> Triple(minutes / (60 * 24 * 365), "سال", if (minutes / (60 * 24 * 365) == 1L) "year" else "years")
        }
        val number = digits(value.toString(), lang)
        return if (diff > 0) { if (fa) "$number $faUnit دیگر" else "in $number $enUnit" }
        else { if (fa) "$number $faUnit پیش" else "$number $enUnit ago" }
    }
}
