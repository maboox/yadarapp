package com.yadavard.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate
import java.time.ZoneId

// Provider class names are kept from earlier versions so widgets already on the home screen keep working.
class QuickWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}
class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}
class NextWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}
class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}

class VoiceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}
class AddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}

object WidgetCommands {
    const val ADD = "com.yadavard.app.widget.ADD"
    const val VOICE = "com.yadavard.app.widget.VOICE"
    const val HOME = "com.yadavard.app.widget.HOME"
    const val DAY = "com.yadavard.app.widget.DAY"
    const val CALENDAR = "com.yadavard.app.widget.WEEK"
    const val EDIT = "com.yadavard.app.widget.EDIT"
    const val EDIT_DRAFT = "com.yadavard.app.widget.EDIT_DRAFT"
    const val EXTRA_DRAFT = "draft_json"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_DAY = "day_epoch"
}

object WidgetUpdater {
    private const val MIDNIGHT = "com.yadavard.app.widget.MIDNIGHT"
    private val todayRows = intArrayOf(R.id.today_row_0, R.id.today_row_1, R.id.today_row_2, R.id.today_row_3)
    private val nextRows = intArrayOf(R.id.next_row_0, R.id.next_row_1, R.id.next_row_2, R.id.next_row_3, R.id.next_row_4)
    private val weekDays = intArrayOf(R.id.week_day_0, R.id.week_day_1, R.id.week_day_2, R.id.week_day_3,
        R.id.week_day_4, R.id.week_day_5, R.id.week_day_6)
    private val providers = listOf(QuickWidgetProvider::class.java, TodayWidgetProvider::class.java,
        WeekWidgetProvider::class.java, NextWidgetProvider::class.java, FocusWidgetProvider::class.java,
        VoiceWidgetProvider::class.java, AddWidgetProvider::class.java)

    private fun activity(context: Context, widgetId: Int, slot: Int, action: String, reminderId: Long = 0, day: LocalDate? = null) =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            this.action = action
            data = Uri.parse("yadar://widget/$widgetId/$slot/$action/$reminderId/${day?.toEpochDay() ?: 0}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(WidgetCommands.EXTRA_ID, reminderId)
            if (day != null) putExtra(WidgetCommands.EXTRA_DAY, day.toEpochDay())
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Opens the assistant on top of the home screen without the rest of the app. */
    private fun assistant(context: Context, widgetId: Int) =
        PendingIntent.getActivity(context, 0, Intent(context, AssistantActivity::class.java).apply {
            data = Uri.parse("yadar://widget/$widgetId/assistant")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** "●  09:30   title" with the dot in the reminder's category colour. */
    private fun styledRow(r: Reminder, prefix: String, rest: String): CharSequence {
        val text = "●  $prefix   $rest"
        return android.text.SpannableString(text).apply {
            setSpan(android.text.style.ForegroundColorSpan(r.category.color().toArgb()), 0, 1, 0)
            setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 3, 3 + prefix.length, 0)
        }
    }

    private fun done(context: Context, widgetId: Int, id: Long) =
        PendingIntent.getBroadcast(context, 0, Intent(context, ActionReceiver::class.java).apply {
            action = Notifier.ACTION_DONE
            data = Uri.parse("yadar://widget/$widgetId/done/$id")
            putExtra(Notifier.EXTRA_ID, id)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun ids(context: Context, manager: AppWidgetManager, provider: Class<*>): IntArray =
        runCatching { manager.getAppWidgetIds(ComponentName(context, provider)) }.getOrDefault(IntArray(0))

    private fun midnightIntent(context: Context) = PendingIntent.getBroadcast(context, 61751,
        Intent(context, WidgetMidnightReceiver::class.java).setAction(MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun scheduleMidnight(context: Context, any: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = midnightIntent(context)
        if (!any) { manager.cancel(pending); return }
        val zone = ZoneId.systemDefault()
        manager.set(AlarmManager.RTC, LocalDate.now(zone).plusDays(1).atTime(0, 1).atZone(zone).toInstant().toEpochMilli(), pending)
    }

    fun update(context: Context) {
        runCatching { render(context) }
    }

    private fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val all = providers.associateWith { ids(context, manager, it) }
        val any = all.values.any { it.isNotEmpty() }
        scheduleMidnight(context, any)
        if (!any) return

        val lang = Prefs.language(context)
        val cal = Prefs.calendar(context)
        val fa = lang == AppLanguage.FA
        fun l(f: String, e: String) = if (fa) f else e
        fun num(v: Any) = Dates.digits(v.toString(), lang)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val active = Repo.all(context).filter { !it.done }
        val dayStart = Dates.startOfDay(today, zone)
        val dayEnd = Dates.startOfDay(today.plusDays(1), zone)

        // Separate RTL/LTR layouts follow the app language rather than the phone locale.
        fun layout(rtl: Int, ltr: Int) = RemoteViews(context.packageName, if (fa) rtl else ltr)

        // Today: items needing attention first, then today's occurrences.
        val attention = active.filter { it.needsAttention(now) }.map { it to it.displayAt(now) }
        val todays = active.filterNot { it.needsAttention(now) }
            .flatMap { r -> Recurrence.occurrencesIn(r, dayStart, dayEnd, 24).map { r to it } }
            .sortedBy { it.second }
        val todayList = attention + todays

        for (widgetId in all.getValue(QuickWidgetProvider::class.java)) {
            val v = layout(R.layout.widget_quick, R.layout.widget_quick_ltr)
            v.setTextViewText(R.id.quick_header, l("✦ یادار", "✦ Yadar"))
            v.setTextViewText(R.id.quick_description,
                if (todayList.isEmpty()) l("امروز کاری نداری؛ چیزی اضافه کن", "Nothing today — add something")
                else l("امروز ${num(todayList.size)} یادآوری داری", "${todayList.size} reminders today"))
            v.setTextViewText(R.id.quick_voice, l("🎙  با صدا", "🎙  Voice"))
            v.setTextViewText(R.id.quick_add, l("＋  جدید", "＋  New"))
            v.setOnClickPendingIntent(R.id.quick_root, activity(context, widgetId, 0, WidgetCommands.HOME))
            v.setOnClickPendingIntent(R.id.quick_voice, assistant(context, widgetId))
            v.setOnClickPendingIntent(R.id.quick_add, activity(context, widgetId, 2, WidgetCommands.ADD))
            manager.updateAppWidget(widgetId, v)
        }

        for (widgetId in all.getValue(TodayWidgetProvider::class.java)) {
            val v = layout(R.layout.widget_today, R.layout.widget_today_ltr)
            v.setTextViewText(R.id.today_header, l("امروز", "Today") + if (todayList.isNotEmpty()) " • ${num(todayList.size)}" else "")
            v.setTextViewText(R.id.today_date, Dates.formatDate(today, cal, lang, withWeekday = true))
            v.setOnClickPendingIntent(R.id.today_root, activity(context, widgetId, 0, WidgetCommands.HOME))
            v.setOnClickPendingIntent(R.id.today_add, activity(context, widgetId, 9, WidgetCommands.ADD))
            todayRows.forEachIndexed { index, row ->
                val item = todayList.getOrNull(index)
                if (item == null && index > 0) { v.setViewVisibility(row, View.GONE); return@forEachIndexed }
                v.setViewVisibility(row, View.VISIBLE)
                val text: CharSequence = if (item == null) l("✨ امروز کاری نداری", "✨ All clear today") else {
                    val (r, at) = item
                    styledRow(r, (if (r.needsAttention(now)) "⚠ " else "") + Dates.formatTime(at, zone, lang), r.title)
                }
                v.setTextViewText(row, text)
                v.setOnClickPendingIntent(row, activity(context, widgetId, index + 1,
                    if (item == null) WidgetCommands.ADD else WidgetCommands.EDIT, item?.first?.id ?: 0))
            }
            v.setTextViewText(R.id.today_footer, if (todayList.size > todayRows.size)
                l("${num(todayList.size - todayRows.size)} مورد دیگر ←", "${todayList.size - todayRows.size} more →")
                else l("باز کردن یادار ←", "Open Yadar →"))
            v.setOnClickPendingIntent(R.id.today_footer, activity(context, widgetId, 8, WidgetCommands.HOME))
            manager.updateAppWidget(widgetId, v)
        }

        val weekStart = Dates.weekStart(today, cal)
        for (widgetId in all.getValue(WeekWidgetProvider::class.java)) {
            val v = layout(R.layout.widget_week, R.layout.widget_week_ltr)
            v.setTextViewText(R.id.week_header, l("هفتهٔ من", "My week"))
            v.setTextViewText(R.id.week_dates, Dates.formatDate(weekStart, cal, lang, withYear = false) + l(" تا ", " – ") +
                Dates.formatDate(weekStart.plusDays(6), cal, lang, withYear = false))
            v.setOnClickPendingIntent(R.id.week_root, activity(context, widgetId, 0, WidgetCommands.CALENDAR))
            weekDays.forEachIndexed { index, cell ->
                val day = weekStart.plusDays(index.toLong())
                val s = Dates.startOfDay(day, zone)
                val e = Dates.startOfDay(day.plusDays(1), zone)
                // Daily routines would make every day look the same; count the other reminders once each.
                val count = active.count { !it.isDaily && Recurrence.occurrencesIn(it, s, e, 1).isNotEmpty() }
                val dayNumber = Dates.parts(day, cal).day
                v.setTextViewText(cell, "${Dates.weekdayName(day.dayOfWeek, lang, short = true)}\n${num(dayNumber)}\n" +
                    if (count == 0) "·" else "● ${num(count)}")
                v.setTextColor(cell, if (day == today) 0xFF5B4BDB.toInt() else context.getColor(R.color.widget_text))
                v.setInt(cell, "setBackgroundResource", if (day == today) R.drawable.widget_day_today else R.drawable.widget_chip)
                v.setOnClickPendingIntent(cell, activity(context, widgetId, index + 1, WidgetCommands.DAY, day = day))
            }
            manager.updateAppWidget(widgetId, v)
        }

        val upcoming = active.filter { it.nextAt > now && it.nextAt != it.pendingAt }.sortedBy { it.nextAt }
        for (widgetId in all.getValue(NextWidgetProvider::class.java)) {
            val v = layout(R.layout.widget_next, R.layout.widget_next_ltr)
            v.setTextViewText(R.id.next_header, l("پیش رو", "Coming up"))
            v.setTextViewText(R.id.next_subtitle, if (upcoming.isEmpty()) l("یادآوری بعدی‌ای نداری", "No upcoming reminders")
                else l("به ترتیب نزدیک‌ترین", "Soonest first"))
            v.setOnClickPendingIntent(R.id.next_root, activity(context, widgetId, 0, WidgetCommands.HOME))
            nextRows.forEachIndexed { index, row ->
                val r = upcoming.getOrNull(index)
                if (r == null && index > 0) { v.setViewVisibility(row, View.GONE); return@forEachIndexed }
                v.setViewVisibility(row, View.VISIBLE)
                v.setTextViewText(row, if (r == null) l("＋  یادآوری جدید", "＋  New reminder") else
                    styledRow(r, Dates.friendlyDay(Dates.localDate(r.nextAt, zone), today, cal, lang) + " " + Dates.formatTime(r.nextAt, zone, lang), r.title))
                v.setOnClickPendingIntent(row, activity(context, widgetId, index + 1,
                    if (r == null) WidgetCommands.ADD else WidgetCommands.EDIT, r?.id ?: 0))
            }
            manager.updateAppWidget(widgetId, v)
        }

        for (widgetId in all.getValue(VoiceWidgetProvider::class.java)) {
            val v = RemoteViews(context.packageName, R.layout.widget_icon)
            v.setImageViewResource(R.id.icon_image, R.drawable.ic_widget_mic)
            v.setTextViewText(R.id.icon_label, l("دستیار", "Assistant"))
            v.setOnClickPendingIntent(R.id.icon_root, assistant(context, widgetId))
            manager.updateAppWidget(widgetId, v)
        }
        for (widgetId in all.getValue(AddWidgetProvider::class.java)) {
            val v = RemoteViews(context.packageName, R.layout.widget_icon_light)
            v.setImageViewResource(R.id.icon_image, R.drawable.ic_widget_add)
            v.setTextViewText(R.id.icon_label, l("جدید", "New"))
            v.setOnClickPendingIntent(R.id.icon_root, activity(context, widgetId, 0, WidgetCommands.ADD))
            manager.updateAppWidget(widgetId, v)
        }

        val focus = active.filter { it.needsAttention(now) }.minByOrNull { it.displayAt(now) } ?: upcoming.firstOrNull()
        for (widgetId in all.getValue(FocusWidgetProvider::class.java)) {
            val v = layout(R.layout.widget_focus, R.layout.widget_focus_ltr)
            v.setTextViewText(R.id.focus_header, if (focus?.needsAttention(now) == true) l("⚠ نیاز به توجه", "⚠ Needs attention")
                else l("✦ نزدیک‌ترین یادآوری", "✦ Up next"))
            v.setTextViewText(R.id.focus_title, focus?.title ?: l("همه‌چیز رو به‌راهه ✨", "All clear ✨"))
            v.setTextViewText(R.id.focus_due, if (focus == null) l("یک یادآوری تازه بساز", "Create a new reminder") else {
                val at = focus.displayAt(now)
                "${Dates.friendlyDay(Dates.localDate(at, zone), today, cal, lang)} • ${Dates.formatTime(at, zone, lang)} • ${Dates.relative(at, now, lang)}"
            })
            v.setTextViewText(R.id.focus_open, if (focus == null) l("＋ جدید", "＋ New") else l("باز کردن", "Open"))
            v.setTextViewText(R.id.focus_done, l("✓  انجام شد", "✓  Done"))
            v.setOnClickPendingIntent(R.id.focus_root, activity(context, widgetId, 0, WidgetCommands.HOME))
            v.setOnClickPendingIntent(R.id.focus_open, activity(context, widgetId, 1,
                if (focus == null) WidgetCommands.ADD else WidgetCommands.EDIT, focus?.id ?: 0))
            v.setViewVisibility(R.id.focus_done, if (focus != null) View.VISIBLE else View.GONE)
            if (focus != null) v.setOnClickPendingIntent(R.id.focus_done, done(context, widgetId, focus.id))
            manager.updateAppWidget(widgetId, v)
        }
    }
}

class WidgetMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = WidgetUpdater.update(context)
}
