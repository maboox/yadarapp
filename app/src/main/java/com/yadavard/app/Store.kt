package com.yadavard.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** Local SQLite storage. Version 5 replaces the older schema and migrates its rows in place. */
class ReminderStore(context: Context) : SQLiteOpenHelper(context, "reminders.db", null, 5) {
    override fun onCreate(db: SQLiteDatabase) = createTable(db)

    private fun createTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE reminders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL, note TEXT NOT NULL DEFAULT '',
            category TEXT NOT NULL DEFAULT 'GENERAL', important INTEGER NOT NULL DEFAULT 0,
            alert_style TEXT NOT NULL DEFAULT 'NOTIFICATION',
            first_at INTEGER NOT NULL, next_at INTEGER NOT NULL,
            unit TEXT NOT NULL, every_n INTEGER NOT NULL DEFAULT 1, weekdays INTEGER NOT NULL DEFAULT 0,
            month_day INTEGER NOT NULL DEFAULT 0, until_at INTEGER,
            lead_minutes INTEGER NOT NULL DEFAULT 0, nag_minutes INTEGER NOT NULL DEFAULT 0,
            zone TEXT NOT NULL, calendar_type TEXT NOT NULL DEFAULT 'PERSIAN',
            done INTEGER NOT NULL DEFAULT 0, completed_at INTEGER NOT NULL DEFAULT 0,
            completed_count INTEGER NOT NULL DEFAULT 0,
            pending_at INTEGER NOT NULL DEFAULT 0, alerted_at INTEGER NOT NULL DEFAULT 0,
            snooze_at INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL DEFAULT 0)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Older versions (1-4) all used a "reminders" table with a subset of these columns.
        val migrated = mutableListOf<Reminder>()
        runCatching {
            db.rawQuery("SELECT * FROM reminders", null).use { c ->
                fun long(name: String): Long = c.getColumnIndex(name).let { if (it < 0 || c.isNull(it)) 0L else c.getLong(it) }
                fun str(name: String): String? = c.getColumnIndex(name).let { if (it < 0 || c.isNull(it)) null else c.getString(it) }
                while (c.moveToNext()) {
                    if (long("cloud_deleted") != 0L) continue
                    val unit = runCatching { RepeatUnit.valueOf(str("unit") ?: "NONE") }.getOrDefault(RepeatUnit.NONE)
                    val firedAt = long("fired_at")
                    var nextAt = long("next_at")
                    var pendingAt = 0L
                    val done = long("done") == 1L
                    if (!done && nextAt == 0L && firedAt > 0) {
                        pendingAt = firedAt
                        if (unit == RepeatUnit.NONE || unit == RepeatUnit.AFTER_DONE_DAYS) nextAt = firedAt
                    }
                    val untilIndex = c.getColumnIndex("until_at")
                    migrated += Reminder(
                        id = long("id"), title = str("title") ?: continue, note = str("note").orEmpty(),
                        firstAt = long("first_at"), nextAt = nextAt, unit = unit,
                        every = long("every_n").toInt().coerceAtLeast(1), weekdays = long("weekdays").toInt(),
                        monthDay = long("month_day").toInt(),
                        untilAt = if (untilIndex < 0 || c.isNull(untilIndex)) null else c.getLong(untilIndex),
                        leadMinutes = long("lead_minutes").toInt(), zone = str("zone") ?: ZoneId.systemDefault().id,
                        calendar = runCatching { CalendarSystem.valueOf(str("calendar_type") ?: "PERSIAN") }.getOrDefault(CalendarSystem.PERSIAN),
                        done = done, completedAt = long("completed_at"), pendingAt = pendingAt,
                        alertedAt = if (pendingAt > 0) firedAt else 0, snoozeAt = long("snooze_at"))
                }
            }
        }
        db.execSQL("DROP TABLE IF EXISTS reminders")
        db.execSQL("DROP TABLE IF EXISTS reminders_local")
        createTable(db)
        migrated.forEach { db.insertOrThrow("reminders", null, values(it).apply { put("id", it.id) }) }
    }

    private fun values(r: Reminder) = ContentValues().apply {
        put("title", r.title); put("note", r.note); put("category", r.category.name)
        put("important", if (r.important) 1 else 0); put("alert_style", r.alertStyle.name)
        put("first_at", r.firstAt); put("next_at", r.nextAt); put("unit", r.unit.name)
        put("every_n", r.every); put("weekdays", r.weekdays); put("month_day", r.monthDay)
        if (r.untilAt == null) putNull("until_at") else put("until_at", r.untilAt)
        put("lead_minutes", r.leadMinutes); put("nag_minutes", r.nagMinutes)
        put("zone", r.zone); put("calendar_type", r.calendar.name)
        put("done", if (r.done) 1 else 0); put("completed_at", r.completedAt); put("completed_count", r.completedCount)
        put("pending_at", r.pendingAt); put("alerted_at", r.alertedAt); put("snooze_at", r.snoozeAt)
        put("created_at", r.createdAt)
    }

    @Synchronized
    fun save(r: Reminder): Reminder {
        val db = writableDatabase
        if (r.id == 0L) return r.copy(id = db.insertOrThrow("reminders", null, values(r)))
        val updated = db.update("reminders", values(r), "id=?", arrayOf(r.id.toString()))
        if (updated == 0) db.insertOrThrow("reminders", null, values(r).apply { put("id", r.id) })
        return r
    }

    @Synchronized
    fun delete(id: Long) { writableDatabase.delete("reminders", "id=?", arrayOf(id.toString())) }

    fun get(id: Long): Reminder? = readableDatabase.query("reminders", null, "id=?", arrayOf(id.toString()),
        null, null, null).use { c -> if (c.moveToFirst()) read(c) else null }

    fun all(): List<Reminder> = readableDatabase.query("reminders", null, null, null, null, null, "next_at ASC").use { c ->
        buildList { while (c.moveToNext()) add(read(c)) }
    }

    private fun read(c: Cursor): Reminder {
        fun long(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
        fun int(name: String) = c.getInt(c.getColumnIndexOrThrow(name))
        fun str(name: String) = c.getString(c.getColumnIndexOrThrow(name))
        val until = c.getColumnIndexOrThrow("until_at")
        return Reminder(
            id = long("id"), title = str("title"), note = str("note"),
            category = runCatching { Category.valueOf(str("category")) }.getOrDefault(Category.GENERAL),
            important = int("important") == 1,
            alertStyle = runCatching { AlertStyle.valueOf(str("alert_style")) }.getOrDefault(AlertStyle.NOTIFICATION),
            firstAt = long("first_at"), nextAt = long("next_at"),
            unit = runCatching { RepeatUnit.valueOf(str("unit")) }.getOrDefault(RepeatUnit.NONE),
            every = int("every_n"), weekdays = int("weekdays"), monthDay = int("month_day"),
            untilAt = if (c.isNull(until)) null else c.getLong(until),
            leadMinutes = int("lead_minutes"), nagMinutes = int("nag_minutes"), zone = str("zone"),
            calendar = runCatching { CalendarSystem.valueOf(str("calendar_type")) }.getOrDefault(CalendarSystem.PERSIAN),
            done = int("done") == 1, completedAt = long("completed_at"), completedCount = int("completed_count"),
            pendingAt = long("pending_at"), alertedAt = long("alerted_at"), snoozeAt = long("snooze_at"),
            createdAt = long("created_at"))
    }
}

/**
 * Single entry point for every change, used by the UI, notification actions, the alarm receiver and widgets,
 * so storage, alarms, notifications and widgets always stay in sync.
 */
object Repo {
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes
    @Volatile private var instance: ReminderStore? = null

    fun store(context: Context): ReminderStore = instance ?: synchronized(this) {
        instance ?: ReminderStore(context.applicationContext).also { instance = it }
    }

    fun all(context: Context): List<Reminder> = store(context).all()
    fun get(context: Context, id: Long): Reminder? = store(context).get(id)

    /** Saves and reschedules; used for both user edits and alarm-state updates. */
    fun save(context: Context, r: Reminder): Reminder {
        val saved = store(context).save(r)
        Scheduler.schedule(context, saved)
        changed(context)
        return saved
    }

    fun delete(context: Context, r: Reminder) {
        Scheduler.cancel(context, r.id)
        Notifier.cancel(context, r.id)
        store(context).delete(r.id)
        changed(context)
    }

    fun complete(context: Context, id: Long): Reminder? {
        val r = get(context, id) ?: return null
        if (r.done) return r
        Notifier.cancel(context, id)
        return save(context, ReminderLogic.complete(r, System.currentTimeMillis()))
    }

    fun snooze(context: Context, id: Long, minutes: Int): Reminder? {
        val r = get(context, id) ?: return null
        if (r.done) return r
        Notifier.cancel(context, id)
        return save(context, ReminderLogic.snooze(r, System.currentTimeMillis(), minutes))
    }

    fun skip(context: Context, id: Long): Reminder? {
        val r = get(context, id) ?: return null
        Notifier.cancel(context, id)
        return save(context, ReminderLogic.skip(r))
    }

    fun changed(context: Context) {
        _changes.value = _changes.value + 1
        WidgetUpdater.update(context)
        Backup.scheduleAuto(context)
    }
}

/** JSON backup. Schema 2 stores every field; schema 1 files from older versions are still accepted. */
object Backup {
    private val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    private var pending: java.util.concurrent.ScheduledFuture<*>? = null
    const val AUTO_NAME = "yadar-auto-backup.json"

    /** Debounced automatic backup to Download/Yadar, which survives uninstalling the app. */
    fun scheduleAuto(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            pending?.cancel(false)
            pending = executor.schedule({ runCatching { autoBackup(app) } }, 5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private fun autoBackup(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        val items = Repo.all(context)
        if (items.isEmpty()) return // never replace a good backup with an empty one
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val dir = android.os.Environment.DIRECTORY_DOWNLOADS + "/Yadar/"
        val column = android.provider.MediaStore.MediaColumns.DISPLAY_NAME
        val pathColumn = android.provider.MediaStore.MediaColumns.RELATIVE_PATH
        // Only files created by this installation are visible here, so an older backup is never overwritten.
        val existing = resolver.query(collection, arrayOf(android.provider.MediaStore.MediaColumns._ID),
            "$column=? AND $pathColumn=?", arrayOf(AUTO_NAME, dir), null)?.use { c ->
            if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }
        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(column, AUTO_NAME); put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json"); put(pathColumn, dir)
        }) ?: return
        resolver.openOutputStream(uri, "wt")?.use { it.write(export(items).toByteArray()) }
    }

    fun export(items: List<Reminder>): String = JSONObject().put("app", "yadar").put("schema", 2)
        .put("exportedAt", System.currentTimeMillis())
        .put("reminders", JSONArray().apply {
            items.forEach { r -> put(JSONObject().apply {
                put("title", r.title); put("note", r.note); put("category", r.category.name)
                put("important", r.important); put("alertStyle", r.alertStyle.name)
                put("firstAt", r.firstAt); put("nextAt", r.nextAt); put("unit", r.unit.name)
                put("every", r.every); put("weekdays", r.weekdays); put("monthDay", r.monthDay)
                put("untilAt", r.untilAt ?: JSONObject.NULL); put("leadMinutes", r.leadMinutes)
                put("nagMinutes", r.nagMinutes); put("zone", r.zone); put("calendar", r.calendar.name)
                put("done", r.done); put("lastCompletedAt", r.completedAt); put("completedCount", r.completedCount)
                put("createdAt", r.createdAt)
            }) }
        }).toString(2)

    /** Imports reminders that are not already present (same title and first date); returns the number added. */
    fun restore(context: Context, data: String, fa: Boolean): Int {
        val root = JSONObject(data)
        val schema = root.optInt("schema", 0)
        require(schema == 1 || schema == 2) { if (fa) "نسخهٔ فایل پشتیبان پشتیبانی نمی‌شود" else "Unsupported backup version" }
        val array = root.getJSONArray("reminders")
        require(array.length() <= 20_000) { if (fa) "فایل بیش از حد بزرگ است" else "Backup is too large" }
        val store = Repo.store(context)
        val existing = store.all().map { it.title to it.firstAt }.toMutableSet()
        val now = System.currentTimeMillis()
        var added = 0
        for (i in 0 until array.length()) {
            val v = array.getJSONObject(i)
            val title = v.getString("title").trim().take(200)
            val first = v.getLong("firstAt")
            if (title.isBlank() || !existing.add(title to first)) continue
            val zone = v.optString("zone", ZoneId.systemDefault().id).takeIf { runCatching { ZoneId.of(it) }.isSuccess }
                ?: ZoneId.systemDefault().id
            var r = Reminder(title = title, note = v.optString("note").take(4000),
                category = runCatching { Category.valueOf(v.optString("category", "GENERAL")) }.getOrDefault(Category.GENERAL),
                important = v.optBoolean("important"),
                alertStyle = runCatching { AlertStyle.valueOf(v.optString("alertStyle", "NOTIFICATION")) }.getOrDefault(AlertStyle.NOTIFICATION),
                firstAt = first, nextAt = v.optLong("nextAt", first),
                unit = runCatching { RepeatUnit.valueOf(v.optString("unit", "NONE")) }.getOrDefault(RepeatUnit.NONE),
                every = v.optInt("every", 1).coerceIn(1, 10_000), weekdays = v.optInt("weekdays"),
                monthDay = v.optInt("monthDay").coerceIn(-1, 31),
                untilAt = if (v.isNull("untilAt")) null else v.optLong("untilAt"),
                leadMinutes = v.optInt("leadMinutes").coerceIn(0, 525_600), nagMinutes = v.optInt("nagMinutes").coerceIn(0, 1440),
                zone = zone, calendar = runCatching { CalendarSystem.valueOf(v.optString("calendar", "PERSIAN")) }.getOrDefault(CalendarSystem.PERSIAN),
                done = v.optBoolean("done"), completedAt = v.optLong("lastCompletedAt"),
                completedCount = v.optInt("completedCount"), createdAt = v.optLong("createdAt", now))
            // A restored repeating series continues from its next future occurrence instead of replaying the past.
            if (!r.done && r.repeating && r.nextAt in 1 until now) r = r.copy(nextAt = Recurrence.nextAfter(r, now) ?: 0L,
                done = Recurrence.nextAfter(r, now) == null)
            store.save(r)
            added++
        }
        Scheduler.rescheduleAll(context)
        Repo.changed(context)
        return added
    }
}
