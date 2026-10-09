package com.yadavard.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Computes scheduled occurrences purely from the rule, independent of when alerts were shown. */
object Recurrence {
    /** The first occurrence strictly after [after] (and not before the anchor), or null when the series ended. */
    fun nextAfter(r: Reminder, after: Long): Long? {
        val step = r.every.coerceIn(1, 10_000)
        val zone = r.zoneId
        val threshold = maxOf(after, r.firstAt - 1)
        val first = Instant.ofEpochMilli(r.firstAt).atZone(zone)
        val time = first.toLocalTime().withSecond(0).withNano(0)
        fun ok(candidate: Long) = candidate > threshold && candidate >= r.firstAt
        val result: Long? = when (r.unit) {
            RepeatUnit.NONE, RepeatUnit.AFTER_DONE_DAYS -> null
            RepeatUnit.HOURS -> {
                val period = step * 3_600_000L
                if (threshold < r.firstAt) r.firstAt
                else r.firstAt + ((threshold - r.firstAt) / period + 1) * period
            }
            RepeatUnit.DAYS -> {
                val firstDate = first.toLocalDate()
                val elapsed = ChronoUnit.DAYS.between(firstDate, Dates.localDate(threshold, zone))
                val startIndex = maxOf(0L, elapsed / step - 1)
                (startIndex..startIndex + 5).asSequence()
                    .map { k -> firstDate.plusDays(k * step).atTime(time).atZone(zone).toInstant().toEpochMilli() }
                    .firstOrNull { ok(it) }
            }
            RepeatUnit.WEEKS -> {
                val mask = (r.weekdays and 0x7F).takeIf { it != 0 } ?: (1 shl (first.dayOfWeek.value - 1))
                val anchor = first.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val thresholdWeek = Dates.localDate(threshold, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = maxOf(0L, ChronoUnit.WEEKS.between(anchor, thresholdWeek))
                var block = maxOf(0L, weeks / step - 1)
                var found: Long? = null
                loop@ for (i in 0 until 4) {
                    val weekStart = anchor.plusWeeks(block * step)
                    for (d in 0..6) {
                        if (mask and (1 shl d) == 0) continue
                        val c = weekStart.plusDays(d.toLong()).atTime(time).atZone(zone).toInstant().toEpochMilli()
                        if (ok(c)) { found = c; break@loop }
                    }
                    block++
                }
                found
            }
            RepeatUnit.MONTHS, RepeatUnit.YEARS -> {
                val cal = r.calendar
                val base = Dates.parts(first.toLocalDate(), cal)
                val baseIndex = base.year * 12L + (base.month - 1)
                val cur = Dates.parts(Dates.localDate(threshold, zone), cal)
                val curIndex = cur.year * 12L + (cur.month - 1)
                val period = if (r.unit == RepeatUnit.YEARS) 12L * step else step.toLong()
                var k = maxOf(0L, (curIndex - baseIndex) / period - 1)
                var found: Long? = null
                for (i in 0 until 6) {
                    val index = baseIndex + k * period
                    val year = Math.floorDiv(index, 12L).toInt()
                    val month = Math.floorMod(index, 12L).toInt() + 1
                    val length = Dates.monthLength(year, month, cal)
                    val desired = when {
                        r.monthDay == -1 -> length
                        r.monthDay in 1..31 -> r.monthDay
                        else -> base.day
                    }
                    val c = Dates.date(year, month, minOf(desired, length), cal)
                        .atTime(time).atZone(zone).toInstant().toEpochMilli()
                    if (ok(c)) { found = c; break }
                    k++
                }
                found
            }
        }
        return result?.takeIf { r.untilAt == null || it <= r.untilAt }
    }

    /** Moves the anchor to the first real occurrence, e.g. the next selected weekday or month day. */
    fun align(r: Reminder): Reminder {
        if (!r.repeating) return r.copy(nextAt = r.firstAt)
        val first = nextAfter(r, r.firstAt - 1) ?: r.firstAt
        return r.copy(firstAt = first, nextAt = first)
    }

    /** Upcoming occurrences starting with [Reminder.nextAt]. */
    fun preview(r: Reminder, count: Int = 3): List<Long> {
        if (r.nextAt <= 0) return emptyList()
        val out = mutableListOf(r.nextAt)
        if (!r.repeating) return out
        while (out.size < count) out += nextAfter(r, out.last()) ?: break
        return out
    }

    /** Occurrences to display in [start, end): the pending one plus scheduled future ones. */
    fun occurrencesIn(r: Reminder, start: Long, end: Long, limit: Int = 48): List<Long> {
        if (r.done) return if (!r.repeating && r.nextAt in start until end) listOf(r.nextAt) else emptyList()
        val out = mutableListOf<Long>()
        if (r.pendingAt in start until end) out += r.pendingAt
        if (r.nextAt <= 0 || r.nextAt == r.pendingAt) return out
        if (!r.repeating) {
            if (r.nextAt in start until end) out += r.nextAt
            return out
        }
        var t: Long? = if (r.nextAt >= start) r.nextAt else nextAfter(r, start - 1)
        while (t != null && t < end && out.size < limit) {
            if (t >= r.nextAt && t !in out) out += t
            t = nextAfter(r, t)
        }
        return out
    }
}
