package com.yadavard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CoreLogicTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun local(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDateTime()

    @Test fun jalaliKnownDates() {
        assertEquals(Jalali.Date(1403, 1, 1), Jalali.fromEpochDay(LocalDate.of(2024, 3, 20).toEpochDay()))
        assertEquals(Jalali.Date(1404, 1, 1), Jalali.fromEpochDay(LocalDate.of(2025, 3, 21).toEpochDay()))
        assertEquals(Jalali.Date(1403, 12, 30), Jalali.fromEpochDay(LocalDate.of(2025, 3, 20).toEpochDay()))
        assertEquals(Jalali.Date(1405, 7, 15), Jalali.fromEpochDay(LocalDate.of(2026, 10, 7).toEpochDay()))
        assertEquals(Jalali.Date(1357, 11, 22), Jalali.fromEpochDay(LocalDate.of(1979, 2, 11).toEpochDay()))
        assertTrue(Jalali.isLeap(1403))
        assertFalse(Jalali.isLeap(1404))
        assertEquals(29, Jalali.monthLength(1404, 12))
    }

    @Test fun jalaliRoundTrip() {
        var day = LocalDate.of(1990, 1, 1).toEpochDay()
        val end = LocalDate.of(2060, 1, 1).toEpochDay()
        while (day < end) {
            val j = Jalali.fromEpochDay(day)
            assertEquals(day, Jalali.toEpochDay(j.year, j.month, j.day))
            assertTrue(j.day in 1..Jalali.monthLength(j.year, j.month))
            day++
        }
    }

    @Test fun dailyAndHourly() {
        val r = Reminder(title = "x", firstAt = at(2026, 10, 7, 9), unit = RepeatUnit.DAYS, every = 3, zone = zone.id)
        assertEquals(at(2026, 10, 10, 9), Recurrence.nextAfter(r, at(2026, 10, 7, 9)))
        assertEquals(at(2026, 10, 7, 9), Recurrence.nextAfter(r, at(2026, 10, 1)))
        assertEquals(at(2026, 11, 6, 9), Recurrence.nextAfter(r, at(2026, 11, 5, 10)))
        val h = Reminder(title = "x", firstAt = at(2026, 10, 7, 8), unit = RepeatUnit.HOURS, every = 8, zone = zone.id)
        assertEquals(at(2026, 10, 7, 16), Recurrence.nextAfter(h, at(2026, 10, 7, 8)))
        assertEquals(at(2026, 10, 8, 0), Recurrence.nextAfter(h, at(2026, 10, 7, 17)))
    }

    @Test fun weeklyWithDays() {
        // Saturday and Tuesday at 18:00, starting Wednesday 2026-10-07.
        val mask = (1 shl (DayOfWeek.SATURDAY.value - 1)) or (1 shl (DayOfWeek.TUESDAY.value - 1))
        val r = Recurrence.align(Reminder(title = "x", firstAt = at(2026, 10, 7, 18), unit = RepeatUnit.WEEKS,
            weekdays = mask, zone = zone.id))
        assertEquals(at(2026, 10, 10, 18), r.nextAt)
        assertEquals(listOf(at(2026, 10, 10, 18), at(2026, 10, 13, 18), at(2026, 10, 17, 18)), Recurrence.preview(r, 3))
        val biweekly = Recurrence.align(Reminder(title = "x", firstAt = at(2026, 10, 5, 9), unit = RepeatUnit.WEEKS,
            every = 2, zone = zone.id))
        assertEquals(at(2026, 10, 19, 9), Recurrence.nextAfter(biweekly, biweekly.nextAt))
    }

    @Test fun monthlyPersianAndGregorian() {
        // The 31st of each Persian month falls back to the 30th/29th in shorter months.
        val p = Recurrence.align(Reminder(title = "x", firstAt = at(2026, 10, 7, 10), unit = RepeatUnit.MONTHS,
            monthDay = 31, calendar = CalendarSystem.PERSIAN, zone = zone.id))
        val first = Dates.parts(Dates.localDate(p.nextAt, zone), CalendarSystem.PERSIAN)
        assertEquals(Dates.Parts(1405, 7, 30), first)
        val g = Recurrence.align(Reminder(title = "x", firstAt = at(2026, 1, 31, 10), unit = RepeatUnit.MONTHS,
            calendar = CalendarSystem.GREGORIAN, zone = zone.id))
        assertEquals(at(2026, 2, 28, 10), Recurrence.nextAfter(g, g.nextAt))
        assertEquals(at(2026, 3, 31, 10), Recurrence.nextAfter(g, at(2026, 2, 28, 10)))
        val last = Recurrence.align(Reminder(title = "x", firstAt = at(2026, 2, 1, 10), unit = RepeatUnit.MONTHS,
            monthDay = -1, calendar = CalendarSystem.GREGORIAN, zone = zone.id))
        assertEquals(at(2026, 2, 28, 10), last.nextAt)
        // 20th of every Persian month, starting after the 20th → next month.
        val rent = Recurrence.align(Reminder(title = "x", firstAt = at(2026, 10, 13, 9), unit = RepeatUnit.MONTHS,
            monthDay = 20, calendar = CalendarSystem.PERSIAN, zone = zone.id))
        assertEquals(Dates.Parts(1405, 8, 20), Dates.parts(Dates.localDate(rent.nextAt, zone), CalendarSystem.PERSIAN))
    }

    @Test fun yearlyAndUntil() {
        val y = Reminder(title = "x", firstAt = at(2026, 10, 7, 9), unit = RepeatUnit.YEARS, zone = zone.id,
            calendar = CalendarSystem.PERSIAN)
        val next = Recurrence.nextAfter(y, y.firstAt)!!
        assertEquals(Dates.Parts(1406, 7, 15), Dates.parts(Dates.localDate(next, zone), CalendarSystem.PERSIAN))
        val limited = Reminder(title = "x", firstAt = at(2026, 10, 7, 9), unit = RepeatUnit.DAYS, zone = zone.id,
            untilAt = at(2026, 10, 8, 23, 59))
        assertEquals(at(2026, 10, 8, 9), Recurrence.nextAfter(limited, limited.firstAt))
        assertNull(Recurrence.nextAfter(limited, at(2026, 10, 8, 9)))
    }

    @Test fun lifecycle() {
        val now = at(2026, 10, 7, 9)
        val once = Reminder(title = "x", firstAt = now, zone = zone.id)
        val fired = ReminderLogic.onDue(once, now, now)
        assertTrue(fired.needsAttention(now + 1))
        assertEquals(now, fired.pendingAt)
        val done = ReminderLogic.complete(fired, now + 5)
        assertTrue(done.done)
        assertFalse(done.needsAttention(now + 10))

        val daily = Reminder(title = "x", firstAt = now, unit = RepeatUnit.DAYS, zone = zone.id)
        val firedDaily = ReminderLogic.onDue(daily, now, now)
        assertEquals(at(2026, 10, 8, 9), firedDaily.nextAt)
        val ack = ReminderLogic.complete(firedDaily, now + 1)
        assertEquals(0L, ack.pendingAt)
        assertEquals(at(2026, 10, 8, 9), ack.nextAt)
        assertFalse(ack.done)
        val early = ReminderLogic.complete(ack, now + 2)
        assertEquals(at(2026, 10, 9, 9), early.nextAt)

        val afterDone = Reminder(title = "x", firstAt = now, unit = RepeatUnit.AFTER_DONE_DAYS, every = 10, zone = zone.id)
        val c = ReminderLogic.complete(ReminderLogic.onDue(afterDone, now, now), at(2026, 10, 9, 15))
        assertEquals(at(2026, 10, 19, 9), c.nextAt)
        assertFalse(c.done)
    }

    @Test fun dailyDetection() {
        val base = Reminder(title = "x", firstAt = at(2026, 10, 7, 9), zone = zone.id)
        assertTrue(base.copy(unit = RepeatUnit.DAYS).isDaily)
        assertTrue(base.copy(unit = RepeatUnit.HOURS, every = 8).isDaily)
        assertTrue(base.copy(unit = RepeatUnit.WEEKS, weekdays = 0x7F).isDaily)
        assertFalse(base.copy(unit = RepeatUnit.DAYS, every = 2).isDaily)
        assertFalse(base.copy(unit = RepeatUnit.WEEKS, weekdays = 1).isDaily)
        assertFalse(base.isDaily)
    }

    // ---- Quick parser ----
    private val now = at(2026, 10, 7, 10, 30) // Wednesday
    private fun parse(s: String, cal: CalendarSystem = CalendarSystem.PERSIAN) = QuickParser.parse(s, now, zone, cal)

    @Test fun parserPersian() {
        parse("فردا ساعت ۸ صبح تماس با مامان").let {
            assertEquals("تماس با مامان", it.title); assertEquals(at(2026, 10, 8, 8), it.at)
        }
        parse("یادم بنداز ۲ ساعت دیگه قرص بخورم").let {
            assertEquals("قرص بخورم", it.title); assertEquals(at(2026, 10, 7, 12, 30), it.at)
        }
        parse("نیم ساعت دیگه زنگ بزنم به علی").let { assertEquals(at(2026, 10, 7, 11, 0), it.at) }
        parse("هر روز ساعت ۹ شب قرص").let {
            assertEquals(RepeatUnit.DAYS, it.unit); assertEquals("قرص", it.title); assertEquals(21, local(it.at!!).hour)
        }
        parse("۲۰ هر ماه پرداخت اجاره").let {
            assertEquals(RepeatUnit.MONTHS, it.unit); assertEquals(20, it.monthDay); assertEquals("پرداخت اجاره", it.title)
        }
        parse("هر ۲۰ روز قسط وام").let {
            assertEquals(RepeatUnit.DAYS, it.unit); assertEquals(20, it.every); assertEquals("قسط وام", it.title)
        }
        parse("پنج‌شنبه ساعت ۵ عصر باشگاه").let {
            assertEquals(at(2026, 10, 8, 17), it.at); assertEquals("باشگاه", it.title)
        }
        parse("هر شنبه و سه‌شنبه ساعت ۱۸:۳۰ کلاس زبان").let {
            assertEquals(RepeatUnit.WEEKS, it.unit)
            assertEquals((1 shl 5) or (1 shl 1), it.weekdays)
            assertEquals("کلاس زبان", it.title)
            val r = it.toReminder(zone, CalendarSystem.PERSIAN, now)!!
            assertEquals(at(2026, 10, 10, 18, 30), r.nextAt)
        }
        parse("۱۵ آبان تولد سارا").let {
            assertEquals(Dates.date(1405, 8, 15, CalendarSystem.PERSIAN), local(it.at!!).toLocalDate())
            assertEquals("تولد سارا", it.title)
        }
        parse("ساعت ۵ جلسه").let { assertEquals(at(2026, 10, 7, 17), it.at) }
        parse("خرید نان").let { assertFalse(it.understood); assertNull(it.at); assertEquals("خرید نان", it.title) }
        parse("فردا ساعت ۷ با آلارم بیدار شدن، مهم").let {
            assertTrue(it.alarm); assertTrue(it.important); assertEquals("بیدار شدن", it.title)
            assertEquals(AlertStyle.ALARM, it.toReminder(zone, CalendarSystem.PERSIAN, now)!!.alertStyle)
        }
        parse("ساعت ۵ زنگ بزنم به علی").let { assertFalse(it.alarm); assertEquals("زنگ بزنم به علی", it.title) }
        parse("امشب فیلم").let { assertEquals(at(2026, 10, 7, 21), it.at); assertEquals("فیلم", it.title) }
    }

    @Test fun parserEnglish() {
        parse("remind me to call mom tomorrow at 5pm", CalendarSystem.GREGORIAN).let {
            assertEquals("call mom", it.title); assertEquals(at(2026, 10, 8, 17), it.at)
        }
        parse("in 15 minutes check oven").let { assertEquals(at(2026, 10, 7, 10, 45), it.at); assertEquals("check oven", it.title) }
        parse("every monday 9:30 standup").let {
            assertEquals(RepeatUnit.WEEKS, it.unit); assertEquals(1, it.weekdays); assertEquals("standup", it.title)
            assertEquals(at(2026, 10, 12, 9, 30), it.toReminder(zone, CalendarSystem.GREGORIAN, now)!!.nextAt)
        }
        parse("pay rent on the 1st of every month").let {
            assertEquals(RepeatUnit.MONTHS, it.unit); assertEquals(1, it.monthDay); assertEquals("pay rent", it.title)
        }
        parse("dentist 12 november 3pm").let {
            assertEquals(at(2026, 11, 12, 15), it.at); assertEquals("dentist", it.title)
        }
        parse("water plants every 3 days").let {
            assertEquals(RepeatUnit.DAYS, it.unit); assertEquals(3, it.every); assertEquals("water plants", it.title)
            assertNotNull(it.at)
        }
    }
}
