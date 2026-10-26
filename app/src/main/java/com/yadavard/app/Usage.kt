package com.yadavard.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * What each assistant request cost, kept on the phone so people can see where their tokens went: when, how many
 * tokens, how long the voice was and what kind of things it did (not which reminders).
 */
object UsageLog {
    data class Entry(val time: Long, val tokens: Double, val voiceSeconds: Int, val counts: Map<String, Int>, val failed: Boolean = false)

    /** Kinds of work, in the order they are listed. */
    val kinds = listOf("create", "update", "complete", "missed", "postpone", "delete", "note", "question")

    private const val MAX = 200

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("usage_log", Context.MODE_PRIVATE)

    fun entries(context: Context): List<Entry> = runCatching {
        val arr = JSONArray(prefs(context).getString("entries", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val c = o.optJSONObject("c") ?: JSONObject()
            Entry(o.optLong("t"), o.optDouble("k", 0.0), o.optInt("v"), c.keys().asSequence().associateWith { c.optInt(it) },
                o.optBoolean("f"))
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, entry: Entry) {
        if (entry.tokens <= 0.0 && !entry.failed) return
        val list = (listOf(entry) + entries(context)).take(MAX)
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().put("t", e.time).put("k", e.tokens).put("v", e.voiceSeconds).put("f", e.failed)
                .put("c", JSONObject().apply { e.counts.forEach { (k, v) -> put(k, v) } }))
        }
        prefs(context).edit().putString("entries", arr.toString()).apply()
    }

    /** Counts the kinds of work in an assistant answer. */
    fun countsOf(actions: List<AssistantAction>, question: Boolean): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        fun inc(k: String) { counts[k] = (counts[k] ?: 0) + 1 }
        actions.forEach { a ->
            when (a) {
                is AssistantAction.Create -> inc("create")
                is AssistantAction.Update -> inc("update")
                is AssistantAction.Complete -> inc("complete")
                is AssistantAction.Miss -> inc("missed")
                is AssistantAction.Postpone -> inc("postpone")
                is AssistantAction.Delete -> inc("delete")
                is AssistantAction.NoteCreate, is AssistantAction.NoteUpdate, is AssistantAction.NoteDelete -> inc("note")
            }
        }
        if (question) inc("question")
        return counts
    }

    fun label(kind: String, count: Int): String {
        val c = n(count)
        return when (kind) {
            "create" -> t("$c یادآوری جدید", "$count new reminder" + if (count > 1) "s" else "")
            "update" -> t("$c ویرایش", "$count edit" + if (count > 1) "s" else "")
            "complete" -> t("$c انجام شد", "$count done")
            "missed" -> t("$c انجام نشد", "$count missed")
            "postpone" -> t("$c تعویق", "$count postponed")
            "delete" -> t("$c حذف", "$count deleted")
            "note" -> t("$c کار روی یادداشت", "$count note change" + if (count > 1) "s" else "")
            else -> t("پرسش و پاسخ", "question")
        }
    }

    /** One line describing an entry, e.g. "voice 40 s • 2 new reminders • 1 edit". */
    fun describe(e: Entry): String {
        val parts = mutableListOf<String>()
        if (e.voiceSeconds > 0) parts += t("صدا ${n(e.voiceSeconds)} ثانیه", "voice ${e.voiceSeconds} s")
        kinds.forEach { k -> e.counts[k]?.takeIf { it > 0 }?.let { parts += label(k, it) } }
        if (e.failed) parts += t("بدون نتیجه", "no result")
        if (parts.isEmpty()) parts += t("متن", "text")
        return parts.joinToString(" • ")
    }
}

/** Warns once when the token balance drops to [Account.LOW_BALANCE]; resets after a top-up. */
object LowBalance {
    private const val NOTIFICATION_ID = 71_001

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    @SuppressLint("MissingPermission")
    fun check(context: Context, before: Double, profile: Account.Profile) {
        if (profile.unlimited) return
        val p = prefs(context)
        if (profile.balance > Account.LOW_BALANCE) {
            if (p.getBoolean("low_warned", false)) p.edit().putBoolean("low_warned", false).apply()
            return
        }
        if (before <= Account.LOW_BALANCE && p.getBoolean("low_warned", false)) return
        p.edit().putBoolean("low_warned", true).apply()
        runCatching {
            Notifier.ensureChannels(context)
            val open = PendingIntent.getActivity(context, NOTIFICATION_ID, Intent(context, MainActivity::class.java).apply {
                action = WidgetCommands.BUY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val left = tokensLabel(profile.balance)
            val n = NotificationCompat.Builder(context, Notifier.CHANNEL_QUIET)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(t("توکن‌های یادیمو رو به اتمام است", "Your Yadimo tokens are running out"))
                .setContentText(t("$left توکن مانده؛ برای ادامهٔ دستیار هوشمند شارژ کن.", "$left tokens left; top up to keep using the assistant."))
                .setContentIntent(open).setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        }
    }
}
