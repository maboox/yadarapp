package com.yadavard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class YadimoLinesTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private fun at(h: Int, min: Int = 0, d: Int = 27) = LocalDateTime.of(2026, 10, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun agendaListsWhatIsLeftToday() {
        val now = at(8)
        val items = listOf(
            Reminder(id = 1, title = "Gym", firstAt = at(18), zone = zone.id),
            Reminder(id = 2, title = "Call mom", firstAt = at(10), zone = zone.id),
            Reminder(id = 3, title = "Tomorrow", firstAt = at(9, d = 28), zone = zone.id),
            Reminder(id = 4, title = "Old", firstAt = at(7, d = 26), zone = zone.id, pendingAt = at(7, d = 26)),
        )
        val day = YadimoLines.day(items, now, zone)
        assertEquals(listOf(2L, 1L), day.upcoming.map { it.second.id })
        assertEquals(1, day.overdue)
        val text = YadimoLines.agenda(day, zone, fa = false)
        assertTrue(text, text.startsWith("You have 2 things today; first at"))
        assertTrue(text, text.contains("Call mom") && text.contains("1 left over"))
    }

    @Test fun nudgesDoNotRepeatAKind() {
        val now = at(9, 40)
        val day = YadimoLines.day(listOf(Reminder(id = 1, title = "Meeting", firstAt = at(10), zone = zone.id)), now, zone)
        val first = YadimoLines.nudge(day, 90, null, emptySet(), now, zone, fa = false, seed = 0)!!
        assertEquals("soon", first.kind)
        assertTrue(first.text, first.text.contains("20 min"))
        val second = YadimoLines.nudge(day, 90, null, setOf("soon"), now, zone, fa = false, seed = 0)!!
        assertEquals("week", second.kind)
        assertNull(YadimoLines.nudge(day, null, null, setOf("soon", "cheer"), now, zone, fa = false, seed = 0))
    }

    @Test fun weatherAdvice() {
        val rainy = YadimoLines.weatherLine(YadimoWeather(12.0, 61, 15.4, 9.6, 80.0), "Tehran", fa = false)
        assertTrue(rainy, rainy.contains("rainy") && rainy.contains("10° to 15°") && rainy.contains("umbrella"))
        val hot = YadimoLines.weatherLine(YadimoWeather(30.0, 0, 41.0, 28.0, 0.0), "اهواز", fa = true)
        assertTrue(hot, hot.contains("آفتابی") && hot.contains("آب زیاد"))
    }

    @Test fun weekRateNeedsEnoughData() {
        val now = at(12)
        fun e(done: Boolean) = HistoryEntry(1, "x", "GENERAL", false, now - 3_600_000, done, now - 3_600_000)
        assertNull(YadimoLines.weekRate(listOf(e(true), e(true)), now))
        assertEquals(80, YadimoLines.weekRate(listOf(e(true), e(true), e(true), e(true), e(false)), now))
    }
}
