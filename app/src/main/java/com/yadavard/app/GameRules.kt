package com.yadavard.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Points, levels, missions and badges. Pure Kotlin (covered by JVM tests): everything is worked out from the
// reminder history on the phone plus a few counters, so it also works offline. Only the token rewards of three
// starter missions are paid by the server, once.

/** What progress is computed from. */
data class GameFacts(
    val totalDone: Int = 0, val onTime: Int = 0, val late: Int = 0, val activeDays: Int = 0,
    val streak: Int = 0, val bestStreak: Int = 0, val early: Int = 0, val night: Int = 0, val perfectWeek: Boolean = false,
    val voiceCreated: Int = 0, val createdXp: Int = 0, val noteXp: Int = 0,
    val hasReminder: Boolean = false, val hasNote: Boolean = false, val nameSet: Boolean = false,
    val customized: Boolean = false, val helloOn: Boolean = false, val owned: Int = 0,
)

/** A mission or a badge: how far along it is and what it gives. */
data class GameGoal(val key: String, val emoji: String, val fa: String, val en: String, val progress: Int, val target: Int,
                    val xp: Int, val tokens: Int = 0) {
    val complete: Boolean get() = progress >= target
}

object GameRules {
    const val MAX_LEVEL = 20
    const val XP_CREATE = 5
    const val XP_CREATE_VOICE = 10
    /** Points for creating reminders stop counting after this many a day, so creating for points doesn't pay. */
    const val XP_CREATE_DAY_CAP = 40
    const val XP_NOTE = 2
    const val XP_NOTE_DAY_CAP = 10
    const val XP_BADGE = 50
    /** A reminder deleted this soon after it was created takes its points back. */
    const val UNDO_MILLIS = 10 * 60_000L

    /** Points needed to reach [level]: 0, 50, 150, 300, 500… (each level a bit longer than the one before). */
    fun threshold(level: Int): Int = 25 * (level - 1) * level

    fun levelOf(xp: Int): Int = (1..MAX_LEVEL).last { threshold(it) <= xp }

    fun title(level: Int, fa: Boolean): String = when {
        level >= 20 -> if (fa) "افسانه" else "Legend"
        level >= 16 -> if (fa) "قهرمان" else "Champion"
        level >= 12 -> if (fa) "استاد" else "Master"
        level >= 8 -> if (fa) "حرفه‌ای" else "Pro"
        level >= 5 -> if (fa) "پیگیر" else "Go-getter"
        level >= 3 -> if (fa) "منظم" else "Organised"
        else -> if (fa) "تازه‌کار" else "Newcomer"
    }

    /** Facts from the outcome history; [other] fills in what the history cannot know. */
    fun facts(history: List<HistoryEntry>, zone: ZoneId, now: Long, other: GameFacts = GameFacts()): GameFacts {
        val done = history.filter { it.done }
        val onTime = done.count { it.recordedAt - it.occurrenceAt <= 2 * 3_600_000L }
        val days = done.map { Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate() }.toSortedSet()
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in days) { run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1; best = maxOf(best, run); prev = d }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var streak = 0
        var d = if (today in days) today else today.minusDays(1)
        while (d in days) { streak++; d = d.minusDays(1) }
        val hours = done.map { Instant.ofEpochMilli(it.recordedAt).atZone(zone).hour }
        // A week (Saturday to Friday) with at least seven outcomes, all done.
        val weeks = history.groupBy { Math.floorDiv(Instant.ofEpochMilli(it.occurrenceAt).atZone(zone).toLocalDate().toEpochDay() + 5, 7L) }
        val perfect = weeks.values.any { w -> w.size >= 7 && w.all { it.done } }
        return other.copy(totalDone = done.size, onTime = onTime, late = done.size - onTime, activeDays = days.size,
            streak = streak, bestStreak = best, early = hours.count { it < 8 }, night = hours.count { it >= 23 }, perfectWeek = perfect)
    }

    fun missions(f: GameFacts): List<GameGoal> = listOf(
        GameGoal("first_reminder", "📝", "اولین یادآوری‌ات را بساز", "Create your first reminder", if (f.hasReminder) 1 else 0, 1, 20),
        GameGoal("name", "👋", "اسمت را به یادیمو بگو", "Tell Yadimo your name", if (f.nameSet) 1 else 0, 1, 20),
        GameGoal("customize", "🎨", "یادیمو را شخصی کن", "Make Yadimo your own", if (f.customized) 1 else 0, 1, 20),
        GameGoal("first_note", "🗒️", "اولین یادداشت را بنویس", "Write your first note", if (f.hasNote) 1 else 0, 1, 20),
        GameGoal("hello", "☀️", "سلام صبح را روشن کن", "Turn on the morning hello", if (f.helloOn) 1 else 0, 1, 20),
        GameGoal("voice3", "🎙️", "۳ یادآوری با صدا بساز", "Create 3 reminders by voice", minOf(f.voiceCreated, 3), 3, 30, tokens = 3),
        GameGoal("done10", "✅", "۱۰ کار را انجام بده", "Get 10 things done", minOf(f.totalDone, 10), 10, 30, tokens = 2),
        GameGoal("streak7", "🔥", "۷ روز پشت سر هم کار انجام بده", "Get something done 7 days in a row", minOf(f.bestStreak, 7), 7, 30, tokens = 5),
    )

    fun badges(f: GameFacts): List<GameGoal> = listOf(
        GameGoal("early", "🌅", "سحرخیز", "Early bird", minOf(f.early, 5), 5, XP_BADGE),
        GameGoal("night", "🦉", "شب‌زنده‌دار", "Night owl", minOf(f.night, 5), 5, XP_BADGE),
        GameGoal("streak7", "🔥", "۷ روز پیاپی", "7-day streak", minOf(f.bestStreak, 7), 7, XP_BADGE),
        GameGoal("streak30", "🏆", "۳۰ روز پیاپی", "30-day streak", minOf(f.bestStreak, 30), 30, XP_BADGE),
        GameGoal("hundred", "💯", "صد کار", "A hundred done", minOf(f.totalDone, 100), 100, XP_BADGE),
        GameGoal("voice25", "🎤", "استاد صدا", "Voice master", minOf(f.voiceCreated, 25), 25, XP_BADGE),
        GameGoal("perfect", "⭐", "هفتهٔ کامل", "Perfect week", if (f.perfectWeek) 1 else 0, 1, XP_BADGE),
        GameGoal("collector", "👑", "کلکسیونر", "Collector", minOf(f.owned, 5), 5, XP_BADGE),
    )

    /** All points: work done, active days, creating (capped per day), badges and claimed missions. */
    fun xp(f: GameFacts, claimed: Set<String>, badges: Set<String>): Int {
        val missionXp = missions(f).filter { it.key in claimed }.sumOf { it.xp }
        return f.onTime * 10 + f.late * 5 + f.activeDays * 5 + f.createdXp + f.noteXp + badges.size * XP_BADGE + missionXp
    }
}
