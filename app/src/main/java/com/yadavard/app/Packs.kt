package com.yadavard.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.ZoneId

/**
 * Reminder packs: a few chosen reminders (for example family birthdays) saved as a small file that others
 * can import into their own Yadar. The sender picks which of their own settings travel with the pack;
 * everything left out uses the receiver's defaults.
 */
object Packs {
    data class Options(
        val alertStyle: Boolean = false,
        val lead: Boolean = false,
        val nag: Boolean = false,
        val important: Boolean = true,
        val notes: Boolean = true,
        val category: Boolean = true,
    )

    /** One reminder of a pack as received; null fields mean "use my defaults". */
    data class Item(
        val title: String, val note: String, val category: String?, val important: Boolean?,
        val alertStyle: AlertStyle?, val leadMinutes: Int?, val nagMinutes: Int?,
        val firstAt: Long, val unit: RepeatUnit, val every: Int, val weekdays: Int, val monthDay: Int,
        val untilAt: Long?, val calendar: CalendarSystem, val zone: String,
    )

    data class Pack(val name: String, val items: List<Item>, val categories: JSONArray?)

    fun build(name: String, reminders: List<Reminder>, o: Options): String {
        val usedCustom = reminders.map { it.category }.toSet()
        return JSONObject().put("app", "yadar").put("type", "pack").put("version", 1)
            .put("name", name.trim().take(80))
            .apply {
                if (o.category) put("categories", JSONArray().apply {
                    Categories.all.filter { !it.builtIn && it.key in usedCustom }.forEach {
                        put(JSONObject().put("key", it.key).put("name", it.nameFa).put("nameEn", it.nameEn).put("color", it.color).put("icon", it.icon))
                    }
                })
            }
            .put("items", JSONArray().apply {
                reminders.forEach { r ->
                    put(JSONObject().apply {
                        put("title", r.title)
                        if (o.notes && r.note.isNotBlank()) put("note", r.note)
                        if (o.category) put("category", r.category)
                        if (o.important) put("important", r.important)
                        if (o.alertStyle) put("alertStyle", r.alertStyle.name)
                        if (o.lead) put("leadMinutes", r.leadMinutes)
                        if (o.nag) put("nagMinutes", r.nagMinutes)
                        // The series anchor plus its next date, so the receiver continues from the same point.
                        put("firstAt", r.firstAt); put("nextAt", r.nextAt)
                        put("unit", r.unit.name); put("every", r.every); put("weekdays", r.weekdays); put("monthDay", r.monthDay)
                        put("untilAt", r.untilAt ?: JSONObject.NULL); put("calendar", r.calendar.name); put("zone", r.zone)
                    })
                }
            }).toString(1)
    }

    fun isPack(text: String): Boolean = runCatching { JSONObject(text).optString("type") == "pack" }.getOrDefault(false)

    fun parse(text: String): Pack {
        val root = JSONObject(text)
        require(root.optString("app") == "yadar" && root.optString("type") == "pack") { t("این فایل پک یادار نیست", "This is not a Yadar pack") }
        val array = root.getJSONArray("items")
        require(array.length() <= 2000) { t("پک خیلی بزرگ است", "The pack is too large") }
        fun JSONObject.intOrNull(n: String) = if (has(n) && !isNull(n)) optInt(n) else null
        val items = (0 until array.length()).mapNotNull { i ->
            val v = array.optJSONObject(i) ?: return@mapNotNull null
            val title = v.optString("title").trim().take(200).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val unit = runCatching { RepeatUnit.valueOf(v.optString("unit", "NONE")) }.getOrDefault(RepeatUnit.NONE)
            // Repeating rules keep their anchor; single and "after done" reminders start at their next date.
            val first = (if (unit == RepeatUnit.NONE || unit == RepeatUnit.AFTER_DONE_DAYS) v.optLong("nextAt").takeIf { it > 0 } else null)
                ?: v.optLong("firstAt").takeIf { it > 0 } ?: return@mapNotNull null
            Item(title = title, note = v.optString("note").take(4000),
                category = v.optString("category").takeIf { it.isNotBlank() },
                important = if (v.has("important")) v.optBoolean("important") else null,
                alertStyle = v.optString("alertStyle").takeIf { it.isNotBlank() }?.let { runCatching { AlertStyle.valueOf(it) }.getOrNull() },
                leadMinutes = v.intOrNull("leadMinutes")?.coerceIn(0, 525_600),
                nagMinutes = v.intOrNull("nagMinutes")?.coerceIn(0, 1440),
                firstAt = first, unit = unit,
                every = v.optInt("every", 1).coerceIn(1, 10_000), weekdays = v.optInt("weekdays") and 0x7F,
                monthDay = v.optInt("monthDay").coerceIn(-1, 31),
                untilAt = if (v.isNull("untilAt") || !v.has("untilAt")) null else v.optLong("untilAt"),
                calendar = runCatching { CalendarSystem.valueOf(v.optString("calendar", "PERSIAN")) }.getOrDefault(CalendarSystem.PERSIAN),
                zone = v.optString("zone").takeIf { z -> runCatching { ZoneId.of(z) }.isSuccess } ?: ZoneId.systemDefault().id)
        }
        require(items.isNotEmpty()) { t("یادآوری‌ای در این پک نیست", "The pack has no reminders") }
        return Pack(root.optString("name").ifBlank { t("پک یادآوری", "Reminder pack") }, items, root.optJSONArray("categories"))
    }

    /** The reminder an item becomes on this phone, or null when a one-time reminder is already over. */
    fun toReminder(context: Context, item: Item, now: Long): Reminder? {
        var r = Reminder(title = item.title, note = item.note,
            category = item.category?.let { Categories.resolve(context, it) } ?: CategoryGuess.guess(item.title, Categories.customPairs(context)),
            important = item.important ?: false,
            alertStyle = item.alertStyle ?: Prefs.defaultAlert(context),
            leadMinutes = item.leadMinutes ?: Prefs.defaultLead(context), nagMinutes = item.nagMinutes ?: 0,
            firstAt = item.firstAt, unit = item.unit, every = item.every, weekdays = item.weekdays, monthDay = item.monthDay,
            untilAt = item.untilAt, calendar = item.calendar, zone = item.zone)
        r = Recurrence.align(r)
        if (r.nextAt <= now) {
            if (r.unit == RepeatUnit.AFTER_DONE_DAYS) {
                val next = ReminderLogic.afterDone(r.copy(every = 1), now)
                return r.copy(firstAt = next, nextAt = next)
            }
            if (!r.repeating) return null
            r = r.copy(nextAt = Recurrence.nextAfter(r, now) ?: return null)
        }
        return r
    }

    /** Adds the chosen items; reminders that already exist (same title and time) are skipped. */
    fun install(context: Context, pack: Pack, chosen: List<Item>): Int {
        pack.categories?.let { Categories.importJson(context, it) }
        val now = System.currentTimeMillis()
        val existing = Repo.all(context).filter { !it.done }.map { it.title.trim() to it.nextAt }.toMutableSet()
        var added = 0
        chosen.forEach { item ->
            val r = toReminder(context, item, now) ?: return@forEach
            if (existing.add(r.title.trim() to r.nextAt)) { Repo.save(context, r); added++ }
        }
        return added
    }

    /** Writes the pack to a shareable file and returns a share intent for it. */
    fun shareIntent(context: Context, name: String, json: String): Intent {
        val dir = File(context.cacheDir, "packs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safe = name.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-").trim('-').ifBlank { "yadar" }.take(40)
        val file = File(dir, "$safe.yadar.json").apply { writeText(json) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .putExtra(Intent.EXTRA_TEXT, t("پک یادآوری «$name» از یادار. برای افزودن، فایل را با یادار باز کن.",
                "Reminder pack “$name” from Yadar. Open the file with Yadar to add it."))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, t("ارسال پک", "Send pack"))
    }

    fun read(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().also { b -> require(b.size < 2_000_000) }.decodeToString() }
            ?: error(t("فایل خوانده نشد", "Could not read the file"))
}
