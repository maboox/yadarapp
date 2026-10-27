package com.yadavard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GameRulesTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private fun at(d: Int, h: Int) = LocalDateTime.of(2026, 10, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun done(d: Int, h: Int, lateHours: Int = 0) = HistoryEntry(1, "x", "GENERAL", false, at(d, h), true, at(d, h) + lateHours * 3_600_000L)

    @Test fun levels() {
        assertEquals(1, GameRules.levelOf(0))
        assertEquals(1, GameRules.levelOf(49))
        assertEquals(2, GameRules.levelOf(50))
        assertEquals(5, GameRules.levelOf(500))
        assertEquals(20, GameRules.levelOf(1_000_000))
        assertEquals(9500, GameRules.threshold(20))
    }

    @Test fun streaksAndTiming() {
        val history = listOf(done(20, 7), done(21, 9), done(22, 23), done(24, 10, lateHours = 5), done(25, 6), done(26, 12), done(27, 9))
        val f = GameRules.facts(history, zone, at(27, 20))
        assertEquals(7, f.totalDone)
        assertEquals(6, f.onTime)
        assertEquals(1, f.late)
        assertEquals(4, f.streak)       // 24..27
        assertEquals(4, f.bestStreak)
        assertEquals(2, f.early)        // 7:00 and 6:00
        assertEquals(1, f.night)
        assertEquals(7, f.activeDays)
        assertEquals(6 * 10 + 5 + 7 * 5, GameRules.xp(f, emptySet(), emptySet()))
    }

    @Test fun streakEndsWhenADayIsSkipped() {
        val f = GameRules.facts(listOf(done(20, 9), done(21, 9)), zone, at(27, 9))
        assertEquals(0, f.streak)
        assertEquals(2, f.bestStreak)
    }

    @Test fun missionsGiveTenTokensAtMost() {
        val all = GameRules.missions(GameFacts())
        assertEquals(10, all.sumOf { it.tokens })
        assertFalse(all.any { it.complete })
        val f = GameFacts(voiceCreated = 5, totalDone = 12, bestStreak = 3, hasReminder = true)
        val m = GameRules.missions(f).associateBy { it.key }
        assertTrue(m.getValue("voice3").complete && m.getValue("done10").complete && m.getValue("first_reminder").complete)
        assertFalse(m.getValue("streak7").complete)
        assertEquals(30 + 20, GameRules.xp(GameFacts(), setOf("voice3", "name"), emptySet()))
    }
}
