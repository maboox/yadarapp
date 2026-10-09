package com.yadavard.app

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** One change the assistant proposes. Nothing is applied until the user confirms (or the countdown ends). */
sealed class AssistantAction {
    data class Create(val draft: Reminder) : AssistantAction()
    data class Update(val before: Reminder, val after: Reminder) : AssistantAction()
    data class Delete(val target: Reminder) : AssistantAction()
    data class Complete(val target: Reminder) : AssistantAction()
    data class Miss(val target: Reminder) : AssistantAction()
    data class Postpone(val target: Reminder, val minutes: Int) : AssistantAction()

    /** A one-time reminder whose time already passed cannot be saved as is. */
    fun needsFix(now: Long): Boolean = when (this) {
        is Create -> !draft.repeating && draft.nextAt <= now
        is Update -> !after.repeating && after.nextAt <= now && after.nextAt != before.nextAt
        else -> false
    }
}

data class AssistantResult(val heard: String, val reply: String, val actions: List<AssistantAction>)

object Assistant {
    /**
     * Describes the user's reminders and the coming week so the model can find, change and summarise them.
     * Kept short on purpose: a long context made answers slow. Daily routines are listed once, not every day.
     */
    fun context(context: Context): String {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val cal = Prefs.calendar(context)
        val all = Repo.all(context)
        val active = all.filter { !it.done }.sortedBy { it.displayAt(now) }.take(80)
        val done = all.filter { it.done }.sortedByDescending { it.completedAt }.take(8)
        fun line(r: Reminder): String {
            val at = r.displayAt(now)
            val whenText = if (at > 0) ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), zone).toLocalDateTime().toString().take(16) +
                " (" + Dates.formatDate(Dates.localDate(at, zone), cal, AppLanguage.FA, withWeekday = true) + ")" else "-"
            val flags = buildList {
                add(r.unit.name + if (r.every > 1) "x${r.every}" else "")
                if (r.alertStyle == AlertStyle.ALARM) add("ALARM")
                if (r.important) add("important")
                if (r.category != CategoryGuess.GENERAL) add("cat=" + r.category)
                if (r.needsAttention(now)) add("OVERDUE")
                if (r.leadMinutes > 0) add("lead=${r.leadMinutes}m")
                if (r.nagMinutes > 0) add("nag=${r.nagMinutes}m")
                if (r.missed) add("ENDED_NOT_DONE")
                if (r.completedCount > 0 || r.missedCount > 0) add("done_count=${r.completedCount},missed_count=${r.missedCount}")
            }.joinToString(",")
            return "#${r.id} | ${r.title} | next: $whenText | $flags" + if (r.note.isNotBlank()) " | note: ${r.note.take(60)}" else ""
        }
        val today = LocalDate.now(zone)
        val agenda = buildString {
            for (d in 0 until 8) {
                val day = today.plusDays(d.toLong())
                val s = Dates.startOfDay(day, zone)
                val e = Dates.startOfDay(day.plusDays(1), zone)
                val items = active.filter { !it.isDaily }.flatMap { r -> Recurrence.occurrencesIn(r, s, e, 6).map { r to it } }.sortedBy { it.second }
                if (items.isEmpty()) continue
                append(day).append(" ").append(Dates.formatDate(day, cal, AppLanguage.FA, withWeekday = true)).append(": ")
                append(items.joinToString("; ") { (r, at) -> Dates.formatTime(at, zone, AppLanguage.EN) + " " + r.title + " #" + r.id })
                append('\n')
            }
        }
        return buildString {
            append("ACTIVE REMINDERS (id | title | next due | flags):\n")
            if (active.isEmpty()) append("(none)\n") else active.forEach { append(line(it)).append('\n') }
            if (done.isNotEmpty()) { append("RECENTLY COMPLETED:\n"); done.forEach { append(line(it)).append('\n') } }
            append("AGENDA FOR THE NEXT 8 DAYS (daily/hourly routines are omitted here; they happen every day at their time):\n").append(agenda.ifBlank { "(empty)\n" })
        }
    }

    /** Applies the fields present in [c] to [r]; timing changes restart the schedule from the new time. */
    fun applyChanges(context: Context, r: Reminder, c: JSONObject): Reminder {
        fun str(n: String) = if (!c.has(n) || c.isNull(n)) null else c.optString(n).trim().takeIf { it.isNotBlank() && it != "null" }
        fun int(n: String) = str(n)?.toDoubleOrNull()?.toInt()
        val zone = r.zoneId
        var out = r
        str("title")?.let { out = out.copy(title = it.take(200)) }
        if (c.has("note")) out = out.copy(note = str("note").orEmpty())
        str("category")?.let { v -> out = out.copy(category = Categories.resolve(context, v)) }
        if (c.has("important") && !c.isNull("important")) out = out.copy(important = c.optBoolean("important"))
        when (str("alert_style")?.uppercase()) {
            "ALARM" -> out = out.copy(alertStyle = AlertStyle.ALARM)
            "NOTIFICATION" -> out = out.copy(alertStyle = AlertStyle.NOTIFICATION)
        }
        int("lead_minutes")?.let { out = out.copy(leadMinutes = it.coerceIn(0, 525_600)) }
        int("nag_minutes")?.let { out = out.copy(nagMinutes = it.coerceIn(0, 1440)) }

        var timing = false
        str("first_at")?.let { v -> runCatching { ZonedDateTime.parse(v).toInstant().toEpochMilli() }.getOrNull()?.let { out = out.copy(firstAt = it); timing = true } }
        str("unit")?.let { v -> runCatching { RepeatUnit.valueOf(v.uppercase()) }.getOrNull()?.let { out = out.copy(unit = it); timing = true } }
        int("every")?.let { out = out.copy(every = it.coerceIn(1, 10_000)); timing = true }
        c.optJSONArray("weekdays")?.let { days ->
            var mask = 0
            for (i in 0 until days.length()) days.optInt(i).takeIf { it in 1..7 }?.let { mask = mask or (1 shl (it - 1)) }
            if (mask != 0) { out = out.copy(weekdays = mask); timing = true }
        }
        int("month_day")?.let { out = out.copy(monthDay = it.coerceIn(-1, 31)); timing = true }
        if (c.has("until")) {
            out = out.copy(untilAt = str("until")?.let { v -> runCatching { LocalDate.parse(v.take(10)).atTime(23, 59).atZone(zone).toInstant().toEpochMilli() }.getOrNull() })
            timing = true
        }
        if (timing) {
            if (!c.has("first_at")) {
                // Keep the current next time as the new anchor when only the rule changed.
                val anchor = if (out.nextAt > 0) out.nextAt else out.firstAt
                out = out.copy(firstAt = anchor)
            }
            out = Recurrence.align(out.copy(done = false, pendingAt = 0, alertedAt = 0, snoozeAt = 0, calendar = Prefs.calendar(context)))
            if (out.repeating && out.nextAt <= System.currentTimeMillis())
                Recurrence.nextAfter(out, System.currentTimeMillis())?.let { out = out.copy(nextAt = it) }
        }
        return out
    }

    /** Short human-readable list of what differs between [a] and [b]. */
    fun describeChanges(a: Reminder, b: Reminder): List<String> = buildList {
        val zone = b.zoneId
        fun whenText(r: Reminder) = if (r.nextAt > 0) Dates.friendlyDay(Dates.localDate(r.nextAt, zone), LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language) +
            " " + Dates.formatTime(r.nextAt, zone, AppDisplay.language) else "-"
        if (a.title != b.title) add(t("عنوان جدید: ", "New title: ") + b.title)
        if (a.nextAt != b.nextAt) add(t("زمان: ", "Time: ") + whenText(a) + t(" ← ", " → ") + whenText(b))
        if (a.unit != b.unit || a.every != b.every || a.weekdays != b.weekdays || a.monthDay != b.monthDay) add(t("تکرار: ", "Repeat: ") + repeatLabel(b))
        if (a.untilAt != b.untilAt) add(b.untilAt?.let { t("پایان تکرار: ", "Ends: ") + Dates.formatDate(Dates.localDate(it, zone), AppDisplay.calendar, AppDisplay.language) }
            ?: t("بدون تاریخ پایان", "No end date"))
        if (a.alertStyle != b.alertStyle) add(if (b.alertStyle == AlertStyle.ALARM) t("نوع: زنگ تمام‌صفحه", "Type: full-screen alarm") else t("نوع: اعلان", "Type: notification"))
        if (a.important != b.important) add(if (b.important) t("مهم شد", "Marked important") else t("دیگر مهم نیست", "No longer important"))
        if (a.leadMinutes != b.leadMinutes) add(t("یادآوری زودتر: ", "Advance notice: ") + leadLabel(b.leadMinutes))
        if (a.nagMinutes != b.nagMinutes) add(t("تکرار تا انجام: ", "Repeat until done: ") + if (b.nagMinutes == 0) t("خاموش", "off") else leadLabel(b.nagMinutes))
        if (a.category != b.category) add(t("دسته: ", "Category: ") + b.category.catLabel())
        if (a.note != b.note) add(t("توضیحات: ", "Notes: ") + b.note.ifBlank { "-" })
    }

    /** Applies confirmed actions. Runs on a background thread. */
    fun apply(context: Context, actions: List<AssistantAction>) {
        val now = System.currentTimeMillis()
        actions.forEach { a ->
            when (a) {
                is AssistantAction.Create -> Repo.save(context, a.draft)
                is AssistantAction.Update -> Repo.save(context, a.after)
                is AssistantAction.Delete -> Repo.delete(context, a.target)
                is AssistantAction.Complete -> Repo.complete(context, a.target.id)
                is AssistantAction.Miss -> Repo.miss(context, a.target.id)
                is AssistantAction.Postpone -> {
                    val r = Repo.get(context, a.target.id) ?: return@forEach
                    if (r.pendingAt > 0) Repo.snooze(context, r.id, a.minutes)
                    else if (r.nextAt > 0) {
                        val shifted = maxOf(r.nextAt, now) + a.minutes * 60_000L
                        Repo.save(context, if (r.repeating) r.copy(nextAt = shifted) else r.copy(firstAt = shifted, nextAt = shifted))
                    }
                }
            }
        }
    }

    fun summary(actions: List<AssistantAction>): String {
        val created = actions.count { it is AssistantAction.Create }
        val other = actions.size - created
        return when {
            actions.isEmpty() -> ""
            other == 0 && created == 1 -> t("یادآوری ثبت شد ✓", "Reminder saved ✓")
            other == 0 -> t("${n(created)} یادآوری ثبت شد ✓", "$created reminders saved ✓")
            else -> t("${n(actions.size)} تغییر انجام شد ✓", "${actions.size} changes applied ✓")
        }
    }
}
