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
import java.time.LocalDate
import java.time.ZoneId

/** Each widget has its own entry in Android's widget picker, but shares one local data source. */
class QuickWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
    override fun onDisabled(context: Context) = WidgetUpdater.scheduleMidnight(context)
}
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
    override fun onDisabled(context: Context) = WidgetUpdater.scheduleMidnight(context)
}
class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
    override fun onDisabled(context: Context) = WidgetUpdater.scheduleMidnight(context)
}
class NextWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
    override fun onDisabled(context: Context) = WidgetUpdater.scheduleMidnight(context)
}
class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
    override fun onDisabled(context: Context) = WidgetUpdater.scheduleMidnight(context)
}

object WidgetCommands {
    const val ADD = "com.yadavard.app.widget.ADD"
    const val VOICE = "com.yadavard.app.widget.VOICE"
    const val HOME = "com.yadavard.app.widget.HOME"
    const val DAY = "com.yadavard.app.widget.DAY"
    const val WEEK = "com.yadavard.app.widget.WEEK"
    const val EDIT = "com.yadavard.app.widget.EDIT"
    const val DONE = "com.yadavard.app.widget.DONE"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_DAY = "day_epoch"
}

object WidgetUpdater {
    private const val MIDNIGHT = "com.yadavard.app.widget.MIDNIGHT"
    private val todayRows = intArrayOf(R.id.today_row_0, R.id.today_row_1, R.id.today_row_2)
    private val nextRows = intArrayOf(R.id.next_row_0, R.id.next_row_1, R.id.next_row_2, R.id.next_row_3, R.id.next_row_4)
    private val weekDays = intArrayOf(R.id.week_day_0, R.id.week_day_1, R.id.week_day_2,
        R.id.week_day_3, R.id.week_day_4, R.id.week_day_5, R.id.week_day_6)
    private val providers = arrayOf(QuickWidgetProvider::class.java, TodayWidgetProvider::class.java,
        WeekWidgetProvider::class.java, NextWidgetProvider::class.java, FocusWidgetProvider::class.java)

    private fun activity(context: Context, widgetId: Int, slot: Int, action: String,
                         reminderId: Long = 0, day: LocalDate? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            data = Uri.parse("yadar://widget/$widgetId/$slot/$action/$reminderId/${day?.toEpochDay() ?: 0}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(WidgetCommands.EXTRA_ID, reminderId)
            if (day != null) putExtra(WidgetCommands.EXTRA_DAY, day.toEpochDay())
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun action(context: Context, widgetId: Int, id: Long): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = WidgetCommands.DONE
            data = Uri.parse("yadar://widget/$widgetId/done/$id")
            putExtra(WidgetCommands.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun ids(context: Context, provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))
    private fun alarm(context: Context) = PendingIntent.getBroadcast(context, 61751,
        Intent(context, WidgetMidnightReceiver::class.java).apply { action = MIDNIGHT },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun scheduleMidnight(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = alarm(context)
        if (providers.none { ids(context, it).isNotEmpty() }) { manager.cancel(pending); return }
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now(zone).plusDays(1).atTime(0, 1).atZone(zone).toInstant().toEpochMilli()
        manager.set(AlarmManager.RTC_WAKEUP, next, pending)
    }
    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        if (providers.none { ids(context, it).isNotEmpty() }) { scheduleMidnight(context); return }
        val reminders = ReminderStore(context).all().filterNot { it.done }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val language = AppDisplay.storedLanguage(context)
        val calendar = AppDisplay.storedCalendar(context)
        fun label(fa: String, en: String) = AppDisplay.text(fa, en, language)
        fun number(value: Any) = AppDisplay.number(value, language)
        fun date(value: Long, inZone: ZoneId = zone) = AppDisplay.date(value, inZone, calendar, language)
        fun dateTime(value: Long, inZone: ZoneId = zone) = AppDisplay.dateTime(value, inZone, calendar, language)

        for (widgetId in ids(context, QuickWidgetProvider::class.java)) {
            val view = RemoteViews(context.packageName, R.layout.widget_quick)
            view.setTextViewText(R.id.quick_header, label("✦ یادار / ثبت سریع", "✦ Yadar / Quick add"))
            view.setTextViewText(R.id.quick_description, label("همین الان بگو یا بنویس؛ یادت می‌مونه.", "Say or add it now; remember it later."))
            view.setTextViewText(R.id.quick_voice, label("●  با ویس بگو", "●  Use voice"))
            view.setTextViewText(R.id.quick_add, label("＋  یادآوری جدید", "＋  New reminder"))
            view.setOnClickPendingIntent(R.id.quick_root, activity(context, widgetId, 0, WidgetCommands.HOME))
            view.setOnClickPendingIntent(R.id.quick_voice, activity(context, widgetId, 1, WidgetCommands.VOICE))
            view.setOnClickPendingIntent(R.id.quick_add, activity(context, widgetId, 2, WidgetCommands.ADD))
            manager.updateAppWidget(widgetId, view)
        }

        val dueToday = reminders.mapNotNull { reminder ->
            occurrenceOn(reminder, today, zone)?.let { reminder to it }
        }.sortedBy { it.second }
        for (widgetId in ids(context, TodayWidgetProvider::class.java)) {
            val view = RemoteViews(context.packageName, R.layout.widget_today)
            view.setTextViewText(R.id.today_header, label("امروز • ${number(dueToday.size)} یادآوری", "Today • ${number(dueToday.size)} reminders"))
            view.setTextViewText(R.id.today_date, date(now))
            view.setOnClickPendingIntent(R.id.today_root, activity(context, widgetId, 0, WidgetCommands.HOME))
            todayRows.forEachIndexed { index, rowId ->
                val item = dueToday.getOrNull(index)
                if (item == null && index != 0) { view.setViewVisibility(rowId, View.GONE); return@forEachIndexed }
                view.setViewVisibility(rowId, View.VISIBLE)
                view.setTextViewText(rowId, if (item == null) label("امروز خیالت راحت باشه ✨", "All clear today ✨")
                    else "${if (item.second <= 0L) label("پیگیری", "Overdue") else number(java.time.Instant.ofEpochMilli(item.second).atZone(zone).toLocalTime().toString().take(5))}  •  ${item.first.title}")
                view.setOnClickPendingIntent(rowId, activity(context, widgetId, index + 1,
                    if (item == null) WidgetCommands.ADD else WidgetCommands.EDIT, item?.first?.id ?: 0))
            }
            view.setTextViewText(R.id.today_footer, if (dueToday.size > 3) label("${number(dueToday.size - 3)} مورد دیگر  ←", "${number(dueToday.size - 3)} more  →")
                else label("دیدن همه  ←", "See all  →"))
            view.setOnClickPendingIntent(R.id.today_footer, activity(context, widgetId, 4, WidgetCommands.HOME))
            manager.updateAppWidget(widgetId, view)
        }

        val weekStart = AppDisplay.weekStart(today, calendar)
        for (widgetId in ids(context, WeekWidgetProvider::class.java)) {
            val view = RemoteViews(context.packageName, R.layout.widget_week)
            val end = weekStart.plusDays(6)
            view.setTextViewText(R.id.week_header, label("هفتهٔ من  ✦", "My week  ✦"))
            view.setTextViewText(R.id.week_dates,
                "${date(weekStart.atStartOfDay(zone).toInstant().toEpochMilli())} ${label("تا", "to")} ${date(end.atStartOfDay(zone).toInstant().toEpochMilli())}")
            view.setOnClickPendingIntent(R.id.week_root, activity(context, widgetId, 0, WidgetCommands.WEEK))
            weekDays.forEachIndexed { index, dayId ->
                val day = weekStart.plusDays(index.toLong())
                val at = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayNumber = AppDisplay.parts(at, zone, calendar).day
                val count = reminders.count { occurrenceOn(it, day, zone) != null }
                view.setTextViewText(dayId, "${AppDisplay.shortWeekday(day, language)}\n${number(dayNumber)}\n${number(count)} ${label("کار", "tasks")}")
                view.setTextColor(dayId, if (day == today) 0xFF644EE9.toInt() else 0xFF4F408C.toInt())
                view.setOnClickPendingIntent(dayId, activity(context, widgetId, index + 1, WidgetCommands.DAY, day = day))
            }
            manager.updateAppWidget(widgetId, view)
        }

        val upcoming = reminders.filter { it.nextAt > 0 }.sortedBy { it.nextAt }
        for (widgetId in ids(context, NextWidgetProvider::class.java)) {
            val view = RemoteViews(context.packageName, R.layout.widget_next)
            view.setOnClickPendingIntent(R.id.next_root, activity(context, widgetId, 0, WidgetCommands.HOME))
            view.setTextViewText(R.id.next_header, label("۵ یادآوری بعدی  ✦", "Next 5 reminders  ✦"))
            view.setTextViewText(R.id.next_subtitle, if (upcoming.isEmpty()) label("یادآوری بعدی را از دکمهٔ + بساز", "Add your first reminder")
                else label("به ترتیب نزدیک‌ترین موعد", "Soonest first"))
            nextRows.forEachIndexed { index, rowId ->
                val reminder = upcoming.getOrNull(index)
                if (reminder == null && index != 0) { view.setViewVisibility(rowId, View.GONE); return@forEachIndexed }
                view.setViewVisibility(rowId, View.VISIBLE)
                view.setTextViewText(rowId, if (reminder == null) label("＋  اولین یادآوری رو اضافه کن", "＋  Add a reminder") else
                    "${number(index + 1)}. ${reminder.title}  •  ${dateTime(reminder.nextAt, ZoneId.of(reminder.zone))}")
                view.setOnClickPendingIntent(rowId, activity(context, widgetId, index + 1,
                    if (reminder == null) WidgetCommands.ADD else WidgetCommands.EDIT, reminder?.id ?: 0))
            }
            manager.updateAppWidget(widgetId, view)
        }

        val focus = upcoming.firstOrNull() ?: reminders.firstOrNull { it.nextAt == 0L }
        for (widgetId in ids(context, FocusWidgetProvider::class.java)) {
            val view = RemoteViews(context.packageName, R.layout.widget_focus)
            view.setTextViewText(R.id.focus_header, label("✦ نزدیک‌ترین یادآوری", "✦ Nearest reminder"))
            view.setTextViewText(R.id.focus_title, focus?.title ?: label("همه‌چیز رو به‌راهه ✨", "All clear ✨"))
            view.setTextViewText(R.id.focus_due, if (focus == null) label("یادآوری تازه‌ای ثبت کن", "Add a new reminder")
                else if (focus.nextAt == 0L) label("موعد گذشته • نیاز به پیگیری", "Overdue • needs attention")
                else dateTime(focus.nextAt, ZoneId.of(focus.zone)))
            view.setTextViewText(R.id.focus_open, label("باز کردن", "Open"))
            view.setTextViewText(R.id.focus_done, label("✓  انجام شد", "✓  Done"))
            view.setOnClickPendingIntent(R.id.focus_root, activity(context, widgetId, 0, WidgetCommands.HOME))
            view.setOnClickPendingIntent(R.id.focus_open, activity(context, widgetId, 1,
                if (focus == null) WidgetCommands.ADD else WidgetCommands.EDIT, focus?.id ?: 0))
            val canComplete = focus != null && focus.unit in listOf(RepeatUnit.NONE, RepeatUnit.AFTER_DONE_DAYS)
            view.setViewVisibility(R.id.focus_done, if (canComplete) View.VISIBLE else View.GONE)
            if (canComplete) view.setOnClickPendingIntent(R.id.focus_done, action(context, widgetId, focus!!.id))
            manager.updateAppWidget(widgetId, view)
        }
        scheduleMidnight(context)
    }
}

class WidgetMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { WidgetUpdater.update(context) }
}

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetCommands.DONE) return
        val store = ReminderStore(context)
        val reminder = store.get(intent.getLongExtra(WidgetCommands.EXTRA_ID, 0)) ?: return
        if (reminder.done || reminder.unit !in listOf(RepeatUnit.NONE, RepeatUnit.AFTER_DONE_DAYS)) return
        ReminderAlarms.cancel(context, reminder)
        val now = System.currentTimeMillis()
        val next = if (reminder.unit == RepeatUnit.AFTER_DONE_DAYS)
            java.time.Instant.ofEpochMilli(now).atZone(ZoneId.of(reminder.zone)).plusDays(reminder.every.toLong()).toInstant().toEpochMilli()
        else reminder.nextAt
        val changed = store.save(reminder.copy(nextAt = next, done = reminder.unit == RepeatUnit.NONE,
            lastCompletedAt = now, snoozeAt = 0))
        if (!changed.done) ReminderAlarms.schedule(context, changed)
        WidgetUpdater.update(context)
    }
}
