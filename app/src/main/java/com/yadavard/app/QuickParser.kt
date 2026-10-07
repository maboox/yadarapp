package com.yadavard.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.regex.MatchResult
import java.util.regex.Pattern

/** Result of offline natural-language parsing; [at] is null when no date or time was recognised. */
data class QuickResult(
    val title: String,
    val at: Long?,
    val unit: RepeatUnit = RepeatUnit.NONE,
    val every: Int = 1,
    val weekdays: Int = 0,
    val monthDay: Int = 0,
    val understood: Boolean = false,
) {
    fun toReminder(zone: ZoneId, calendar: CalendarSystem, now: Long): Reminder? {
        val time = at ?: return null
        var r = Recurrence.align(Reminder(title = title, firstAt = time, unit = unit, every = every,
            weekdays = if (unit == RepeatUnit.WEEKS) weekdays else 0,
            monthDay = if (unit == RepeatUnit.MONTHS || unit == RepeatUnit.YEARS) monthDay else 0,
            zone = zone.id, calendar = calendar))
        if (r.repeating && r.nextAt <= now) {
            val next = Recurrence.nextAfter(r, now) ?: return null
            r = r.copy(firstAt = next, nextAt = next)
        }
        return r
    }
}

/**
 * Offline parser for phrases such as "فردا ساعت ۸ صبح تماس با مامان", "هر ۲۰ روز قسط",
 * "۲۰ هر ماه اجاره", "remind me to call mom tomorrow at 5pm" or "every monday 9:30 standup".
 */
object QuickParser {
    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS
    private fun rx(source: String): Pattern = Pattern.compile(source, FLAGS)
    private const val S = "[\\s\\u200c]*"

    private const val WEEKDAY_FA = "(یک${S}شنبه|دو${S}شنبه|سه${S}شنبه|چهار${S}شنبه|پنج${S}شنبه|جمعه|شنبه)"
    private const val WEEKDAY_EN = "(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)"
    private const val FA_UNITS = "(دقیقه|ساعت|روز|هفته|ماه|سال)"
    private const val EN_UNITS = "(minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)"
    private const val FA_PART = "(صبح|ظهر|بعد${S}از${S}ظهر|بعدازظهر|عصر|شب|بامداد)"
    private const val EN_PART = "(am|pm|a\\.m\\.|p\\.m\\.)"

    private val faNumberWords = mapOf("یک" to 1, "یه" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5,
        "شش" to 6, "شیش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10, "یازده" to 11, "دوازده" to 12,
        "پانزده" to 15, "پونزده" to 15, "بیست" to 20, "سی" to 30, "چهل" to 40, "پنجاه" to 50)
    private val enNumberWords = mapOf("one" to 1, "a" to 1, "an" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50)
    private val faNumberAlt = faNumberWords.keys.sortedByDescending { it.length }.joinToString("|")
    private val enNumberAlt = enNumberWords.keys.sortedByDescending { it.length }.joinToString("|")

    private val persianMonths = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    private val enMonths = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val pFaNumberBeforeUnit = rx("\\b($faNumberAlt)\\b(?=$S$FA_UNITS)")
    private val pFaHourWord = rx("(ساعت$S)($faNumberAlt)\\b")
    private val pEnNumberBeforeUnit = rx("\\b($enNumberAlt)\\b(?=\\s*$EN_UNITS\\b)")
    private val pFaHalfHour = rx("نیم${S}ساعت")
    private val pFaQuarter = rx("(?:یه|یک)${S}ربع")
    private val pEnHalfHour = rx("half\\s+an?\\s+hour")

    private val pMonthDayFa1 = rx("(?:روز$S)?(\\d{1,2})${S}(?:ام|م)?${S}(?:هر|همه)${S}ماه")
    private val pMonthDayFa2 = rx("(?:هر|همه)${S}ماه${S}(?:روز$S)?(\\d{1,2})(?:${S}(?:ام|م))?(?!\\d)(?!$S(?::|دقیقه|ساعت|صبح|ظهر|عصر|شب))")
    private val pMonthDayEn1 = rx("(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(?:every|each)\\s+month")
    private val pMonthDayEn2 = rx("(?:every|each)\\s+month\\s+on\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?")
    private val pMonthDayEn3 = rx("monthly\\s+on\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?")
    private val pLastDayOfMonth = rx("(?:آخر|اخر)${S}(?:هر|همه)${S}ماه|(?:at\\s+the\\s+)?(?:end|last\\s+day)\\s+of\\s+(?:every|each|the)\\s+month")

    private val pWeekdayRepeatFa = rx("(?:هر|همه)$S$WEEKDAY_FA|$WEEKDAY_FA${S}ها")
    private val pWeekdayRepeatEn = rx("(?:every|each)\\s+$WEEKDAY_EN\\b|\\b(?:on\\s+)?(mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays)\\b")
    private val pExtraWeekdayFa = rx("(?:و|،|,)$S$WEEKDAY_FA(?:${S}ها)?")
    private val pExtraWeekdayEn = rx("(?:and|,|&)\\s*$WEEKDAY_EN(?:s)?\\b")

    private val pRepeatFa = rx("(?:هر|همه)$S(\\d+)?$S(ساعت|روز|هفته|ماه|سال)(?:$S(?:یک${S}بار|یکبار))?")
    private val pRepeatOtherEn = rx("every\\s+other\\s+(day|week|month|year)")
    private val pRepeatEn = rx("(?:every|each)\\s*(\\d+)?\\s*(hours?|hrs?|days?|weeks?|months?|years?)\\b")
    private val pDaily = rx("\\b(?:روزانه|هرروز|daily|everyday)\\b")
    private val pWeekly = rx("\\b(?:هفتگی|weekly)\\b")
    private val pMonthly = rx("\\b(?:ماهانه|ماهیانه|monthly)\\b")
    private val pYearly = rx("\\b(?:سالانه|سالیانه|yearly|annually)\\b")

    private val pRelFa = rx("(?:تا$S|بعد${S}از$S)?(\\d+)$S$FA_UNITS$S(?:دیگه|دیگر|بعد)\\b")
    private val pRelEn1 = rx("\\bin\\s+(\\d+)\\s*$EN_UNITS\\b")
    private val pRelEn2 = rx("\\b(\\d+)\\s*$EN_UNITS\\s+(?:from\\s+now|later)\\b")

    private val pIsoDate = rx("\\b(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})\\b")
    private val pDateFa = rx("(\\d{1,2})${S}(?:ام|م)?$S(${persianMonths.joinToString("|")}|امرداد)\\b(?:${S}(?:ماه)?${S}(\\d{4}))?")
    private val pDateEn1 = rx("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?(?:\\s*,?\\s*(\\d{4}))?\\b")
    private val pDateEn2 = rx("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?\\b")

    private val pDayAfterTomorrow = rx("پس${S}فردا|\\bday\\s+after\\s+tomorrow\\b")
    private val pTomorrowNight = rx("فردا${S}شب|\\btomorrow\\s+night\\b")
    private val pTomorrow = rx("\\bفردا\\b|\\btomorrow\\b|\\btmrw\\b")
    private val pTonight = rx("\\bامشب\\b|\\btonight\\b")
    private val pToday = rx("\\bامروز\\b|\\btoday\\b")
    private val pNextWeek = rx("هفته$S(?:بعد|دیگه|دیگر|آینده)|\\bnext\\s+week\\b")
    private val pNextMonth = rx("ماه$S(?:بعد|دیگه|دیگر|آینده)|\\bnext\\s+month\\b")
    private val pNextYear = rx("سال$S(?:بعد|دیگه|دیگر|آینده)|\\bnext\\s+year\\b")
    private val pWeekdayFa = rx("(?:این$S)?$WEEKDAY_FA(?:$S(?:بعد|آینده|دیگه|دیگر))?")
    private val pWeekdayEn = rx("\\b(?:(?:next|this|on|coming)\\s+)?$WEEKDAY_EN\\b")

    private val pTimeFa = rx("ساعت$S(\\d{1,2})(?:[:.](\\d{1,2}))?(?:${S}و$S(نیم|ربع|(\\d{1,2})${S}دقیقه))?(?:$S$FA_PART)?")
    private val pTimeColon = rx("\\b(\\d{1,2})[:](\\d{2})\\b(?:\\s*(?:$EN_PART|$FA_PART))?")
    private val pTimeAmPm = rx("(?:\\bat\\s+)?\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*$EN_PART")
    private val pTimeAt = rx("\\bat\\s+(\\d{1,2})(?:[:.](\\d{2}))?\\b")
    private val pTimeFaPart = rx("\\b(\\d{1,2})$S$FA_PART")
    private val pPartOfDay = rx("\\b(?:$FA_PART|morning|noon|afternoon|evening|night)\\b")

    private val fillers = listOf(
        rx("(?:به${S}من$S|بهم$S)?یاد(?:م|ت)?$S(?:بنداز|بندازی|بیار|بیاری|انداز|باشه|بمونه)"),
        rx("(?:به${S}من$S|بهم$S)?یادآوری${S}(?:کن|بکن)"),
        rx("\\bremind\\s+me\\s+(?:to\\s+|about\\s+|of\\s+)?"),
        rx("\\bdon'?t\\s+(?:let\\s+me\\s+)?forget\\s+(?:to\\s+)?"),
    )
    private val edgeTokens = setOf("که", "در", "ساعت", "روز", "هر", "را", "رو", "و", "برای", "تا", "از",
        "on", "at", "in", "the", "every", "to", "and", "for", "by", "from")

    private enum class Part { AM, PM, NOON, NIGHT, EVENING }

    private fun normalize(input: String): String = buildString {
        input.forEach { c ->
            append(when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                'ي' -> 'ی'
                'ك' -> 'ک'
                else -> c
            })
        }
    }

    private fun part(word: String?): Part? {
        val w = word?.lowercase()?.replace("‌", "")?.replace(" ", "") ?: return null
        return when (w) {
            "am", "a.m.", "صبح", "بامداد" -> Part.AM
            "pm", "p.m.", "بعدازظهر" -> Part.PM
            "ظهر" -> Part.NOON
            "عصر" -> Part.EVENING
            "شب" -> Part.NIGHT
            else -> null
        }
    }

    private fun applyPart(hour: Int, p: Part): Int = when (p) {
        Part.AM -> if (hour == 12) 0 else hour
        Part.PM, Part.EVENING -> if (hour < 12) hour + 12 else hour
        Part.NOON -> if (hour in 1..5) hour + 12 else hour
        Part.NIGHT -> when (hour) { 12 -> 0; in 1..4 -> hour; in 5..11 -> hour + 12; else -> hour }
    }

    private fun faWeekday(word: String): DayOfWeek {
        val w = word.replace("‌", "").replace(" ", "")
        return when (w) {
            "شنبه" -> DayOfWeek.SATURDAY
            "یکشنبه" -> DayOfWeek.SUNDAY
            "دوشنبه" -> DayOfWeek.MONDAY
            "سهشنبه" -> DayOfWeek.TUESDAY
            "چهارشنبه" -> DayOfWeek.WEDNESDAY
            "پنجشنبه" -> DayOfWeek.THURSDAY
            else -> DayOfWeek.FRIDAY
        }
    }

    private fun enWeekday(word: String): DayOfWeek = when (word.lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun bit(day: DayOfWeek) = 1 shl (day.value - 1)

    fun parse(input: String, now: Long, zone: ZoneId, calendar: CalendarSystem): QuickResult {
        var text = " " + normalize(input) + " "
        fun take(p: Pattern): MatchResult? {
            val m = p.matcher(text)
            if (!m.find()) return null
            val result = m.toMatchResult()
            text = text.substring(0, m.start()) + " " + text.substring(m.end())
            return result
        }
        fun replaceAll(p: Pattern, transform: (MatchResult) -> String) {
            val m = p.matcher(text)
            val sb = StringBuffer()
            while (m.find()) m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(transform(m.toMatchResult())))
            m.appendTail(sb)
            text = sb.toString()
        }

        // Number words to digits.
        replaceAll(pFaHalfHour) { "30 دقیقه" }
        replaceAll(pFaQuarter) { "15 دقیقه" }
        replaceAll(pEnHalfHour) { "30 minutes" }
        replaceAll(pFaNumberBeforeUnit) { faNumberWords[it.group(1)]?.toString() ?: it.group() }
        replaceAll(pFaHourWord) { it.group(1) + (faNumberWords[it.group(2)]?.toString() ?: it.group(2)) }
        replaceAll(pEnNumberBeforeUnit) { enNumberWords[it.group(1).lowercase()]?.toString() ?: it.group() }

        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        val today = nowZ.toLocalDate()
        var understood = false
        var unit = RepeatUnit.NONE
        var every = 1
        var weekdays = 0
        var monthDay = 0
        var date: LocalDate? = null
        var relative: ZonedDateTime? = null
        var hour: Int? = null
        var minute = 0
        var ambiguousHour = false
        var defaultHour: Int? = null

        // Repeat rules.
        (take(pLastDayOfMonth))?.let { unit = RepeatUnit.MONTHS; monthDay = -1 }
        if (unit == RepeatUnit.NONE) {
            (take(pMonthDayFa1) ?: take(pMonthDayFa2) ?: take(pMonthDayEn1) ?: take(pMonthDayEn2) ?: take(pMonthDayEn3))?.let {
                val d = it.group(1).toInt()
                if (d in 1..31) { unit = RepeatUnit.MONTHS; monthDay = d }
            }
        }
        if (unit == RepeatUnit.NONE) {
            take(pWeekdayRepeatFa)?.let { m ->
                unit = RepeatUnit.WEEKS
                weekdays = bit(faWeekday(m.group(1) ?: m.group(2)))
                while (true) { val extra = take(pExtraWeekdayFa) ?: break; weekdays = weekdays or bit(faWeekday(extra.group(1))) }
            }
        }
        if (unit == RepeatUnit.NONE) {
            take(pWeekdayRepeatEn)?.let { m ->
                unit = RepeatUnit.WEEKS
                weekdays = bit(enWeekday(m.group(1) ?: m.group(2)))
                while (true) { val extra = take(pExtraWeekdayEn) ?: break; weekdays = weekdays or bit(enWeekday(extra.group(1))) }
            }
        }
        if (unit == RepeatUnit.NONE) {
            take(pRepeatFa)?.let { m ->
                every = m.group(1)?.toIntOrNull() ?: 1
                unit = when (m.group(2)) {
                    "ساعت" -> RepeatUnit.HOURS
                    "روز" -> RepeatUnit.DAYS
                    "هفته" -> RepeatUnit.WEEKS
                    "ماه" -> RepeatUnit.MONTHS
                    else -> RepeatUnit.YEARS
                }
            }
        }
        if (unit == RepeatUnit.NONE) {
            take(pRepeatOtherEn)?.let { m -> every = 2; unit = enUnit(m.group(1)) }
        }
        if (unit == RepeatUnit.NONE) {
            take(pRepeatEn)?.let { m -> every = m.group(1)?.toIntOrNull() ?: 1; unit = enUnit(m.group(2)) }
        }
        if (unit == RepeatUnit.NONE) when {
            take(pDaily) != null -> unit = RepeatUnit.DAYS
            take(pWeekly) != null -> unit = RepeatUnit.WEEKS
            take(pMonthly) != null -> unit = RepeatUnit.MONTHS
            take(pYearly) != null -> unit = RepeatUnit.YEARS
        }
        every = every.coerceIn(1, 10_000)
        if (unit != RepeatUnit.NONE) understood = true

        // Relative times such as "in 2 hours".
        (take(pRelFa))?.let { m ->
            val amount = m.group(1).toLong()
            relative = when (m.group(2)) {
                "دقیقه" -> nowZ.plusMinutes(amount)
                "ساعت" -> nowZ.plusHours(amount)
                "روز" -> nowZ.plusDays(amount)
                "هفته" -> nowZ.plusWeeks(amount)
                "ماه" -> nowZ.plusMonths(amount)
                else -> nowZ.plusYears(amount)
            }
        }
        if (relative == null) (take(pRelEn1) ?: take(pRelEn2))?.let { m ->
            val amount = m.group(1).toLong()
            val u = m.group(2).lowercase()
            relative = when {
                u.startsWith("min") -> nowZ.plusMinutes(amount)
                u.startsWith("h") -> nowZ.plusHours(amount)
                u.startsWith("d") -> nowZ.plusDays(amount)
                u.startsWith("w") -> nowZ.plusWeeks(amount)
                u.startsWith("mon") -> nowZ.plusMonths(amount)
                else -> nowZ.plusYears(amount)
            }
        }
        val rel = relative
        if (rel != null) understood = true

        // Explicit dates.
        take(pIsoDate)?.let { m ->
            val y = m.group(1).toInt(); val mo = m.group(2).toInt(); val d = m.group(3).toInt()
            date = runCatching {
                if (y < 1700) Dates.date(y, mo, d, CalendarSystem.PERSIAN) else LocalDate.of(y, mo, d)
            }.getOrNull()
        }
        if (date == null) take(pDateFa)?.let { m ->
            val d = m.group(1).toInt()
            val name = if (m.group(2) == "امرداد") "مرداد" else m.group(2)
            val mo = persianMonths.indexOf(name) + 1
            date = explicitDate(d, mo, m.group(3)?.toIntOrNull(), CalendarSystem.PERSIAN, today)
        }
        if (date == null) (take(pDateEn1))?.let { m ->
            date = explicitDate(m.group(1).toInt(), enMonths.indexOf(m.group(2).lowercase().take(3)) + 1,
                m.group(3)?.toIntOrNull(), CalendarSystem.GREGORIAN, today)
        }
        if (date == null) (take(pDateEn2))?.let { m ->
            date = explicitDate(m.group(2).toInt(), enMonths.indexOf(m.group(1).lowercase().take(3)) + 1,
                m.group(3)?.toIntOrNull(), CalendarSystem.GREGORIAN, today)
        }

        // Day words.
        if (date == null) when {
            take(pDayAfterTomorrow) != null -> date = today.plusDays(2)
            take(pTomorrowNight) != null -> { date = today.plusDays(1); defaultHour = 21 }
            take(pTomorrow) != null -> date = today.plusDays(1)
            take(pTonight) != null -> { date = today; defaultHour = 21 }
            take(pToday) != null -> date = today
            take(pNextWeek) != null -> date = today.plusWeeks(1)
            take(pNextMonth) != null -> date = today.plusMonths(1)
            take(pNextYear) != null -> date = today.plusYears(1)
        }
        var weekdayDate = false
        if (date == null) {
            val m = take(pWeekdayFa)
            val day = if (m != null) faWeekday(m.group(1)) else take(pWeekdayEn)?.let { enWeekday(it.group(1)) }
            if (day != null) {
                weekdayDate = true
                date = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(day))
                if (unit == RepeatUnit.WEEKS && weekdays == 0) weekdays = bit(day)
            }
        }
        if (date != null) understood = true

        // Times.
        take(pTimeFa)?.let { m ->
            var h = m.group(1).toInt()
            var min = m.group(2)?.toIntOrNull() ?: 0
            when (m.group(3)) {
                null -> Unit
                "نیم" -> min = 30
                "ربع" -> min = 15
                else -> min = m.group(4)?.toIntOrNull() ?: 0
            }
            val p = part(m.group(5))
            if (p != null) h = applyPart(h, p) else ambiguousHour = h in 1..11
            hour = h; minute = min
        }
        if (hour == null) take(pTimeColon)?.let { m ->
            var h = m.group(1).toInt()
            val p = part(m.group(3) ?: m.group(4))
            if (p != null) h = applyPart(h, p) else ambiguousHour = h in 1..11
            hour = h; minute = m.group(2).toInt()
        }
        if (hour == null) take(pTimeAmPm)?.let { m ->
            hour = applyPart(m.group(1).toInt(), part(m.group(3)) ?: Part.AM); minute = m.group(2)?.toIntOrNull() ?: 0
        }
        if (hour == null) take(pTimeAt)?.let { m ->
            val h = m.group(1).toInt(); hour = h; minute = m.group(2)?.toIntOrNull() ?: 0; ambiguousHour = h in 1..11
        }
        if (hour == null) take(pTimeFaPart)?.let { m ->
            hour = applyPart(m.group(1).toInt(), part(m.group(2)) ?: Part.AM)
        }
        if (hour == null) take(pPartOfDay)?.let { m ->
            defaultHour = when (m.group().lowercase().replace("‌", "").replace(" ", "")) {
                "صبح", "بامداد", "morning" -> 9
                "ظهر", "noon" -> 12
                "بعدازظهر", "afternoon" -> 15
                "عصر", "evening" -> 18
                else -> 21
            }
        }
        if (hour != null && (hour!! !in 0..23 || minute !in 0..59)) { hour = null; minute = 0; ambiguousHour = false }
        if (hour != null || defaultHour != null) understood = true

        // Title.
        fillers.forEach { p -> while (take(p) != null) Unit }
        val title = cleanTitle(text).ifBlank { input.trim() }

        // Resolve the instant.
        val at: Long? = when {
            rel != null && hour == null && defaultHour == null -> rel.withSecond(0).withNano(0).toInstant().toEpochMilli()
            !understood -> null
            else -> {
                val baseDate = date ?: rel?.toLocalDate()
                val h = hour
                if (h == null) {
                    val target = baseDate ?: today
                    val resolved = target.atTime(defaultHour ?: 9, 0).atZone(zone)
                    if (baseDate == null && unit == RepeatUnit.NONE && !resolved.toInstant().isAfter(nowZ.toInstant()))
                        resolved.plusDays(1).toInstant().toEpochMilli()
                    else resolved.toInstant().toEpochMilli()
                } else {
                    val candidates = if (ambiguousHour) listOf(h, h + 12) else listOf(h)
                    val day = baseDate
                    if (day == null && unit != RepeatUnit.NONE) {
                        // Repeat rules pick the usual daytime reading; alignment finds the next occurrence.
                        val hh = if (ambiguousHour && h < 7) h + 12 else h
                        today.atTime(hh, minute).atZone(zone).toInstant().toEpochMilli()
                    } else if (day == null || day == today) {
                        val todayHit = candidates.map { today.atTime(LocalTime.of(it, minute)).atZone(zone) }
                            .firstOrNull { it.isAfter(nowZ) }
                        when {
                            todayHit != null -> todayHit.toInstant().toEpochMilli()
                            day == today && unit == RepeatUnit.NONE -> today.atTime(candidates.last(), minute).atZone(zone).toInstant().toEpochMilli()
                            else -> {
                                val hh = if (ambiguousHour && h < 7) h + 12 else h
                                today.plusDays(if (unit == RepeatUnit.NONE) 1 else 0).atTime(hh, minute).atZone(zone).toInstant().toEpochMilli()
                            }
                        }
                    } else {
                        val hh = if (ambiguousHour && h < 7) h + 12 else h
                        var result = day.atTime(hh, minute).atZone(zone)
                        if (weekdayDate && !result.isAfter(nowZ)) result = result.plusWeeks(1)
                        result.toInstant().toEpochMilli()
                    }
                }
            }
        }
        return QuickResult(title, at, unit, every, weekdays, monthDay, understood)
    }

    private fun enUnit(word: String): RepeatUnit {
        val w = word.lowercase()
        return when {
            w.startsWith("h") -> RepeatUnit.HOURS
            w.startsWith("d") -> RepeatUnit.DAYS
            w.startsWith("w") -> RepeatUnit.WEEKS
            w.startsWith("m") -> RepeatUnit.MONTHS
            else -> RepeatUnit.YEARS
        }
    }

    private fun explicitDate(day: Int, month: Int, year: Int?, cal: CalendarSystem, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val currentYear = Dates.parts(today, cal).year
        fun build(y: Int) = runCatching { Dates.date(y, month, minOf(day, Dates.monthLength(y, month, cal)), cal) }.getOrNull()
        if (year != null) return build(year)
        val thisYear = build(currentYear) ?: return null
        return if (thisYear.isBefore(today)) build(currentYear + 1) else thisYear
    }

    private fun cleanTitle(raw: String): String {
        var tokens = raw.replace(Regex("[\\s]+"), " ").trim().split(' ').filter { it.isNotBlank() }
        fun edge(token: String) = token.trim('،', ',', '.', '!', '?', '؟', ':', '-', '؛').lowercase() in edgeTokens ||
            token.trim('،', ',', '.', '!', '?', '؟', ':', '-', '؛').isEmpty()
        while (tokens.isNotEmpty() && edge(tokens.first())) tokens = tokens.drop(1)
        while (tokens.isNotEmpty() && edge(tokens.last())) tokens = tokens.dropLast(1)
        return tokens.joinToString(" ").trim('،', ',', '.', ':', '-', '؛', ' ')
    }
}
