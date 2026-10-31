package com.yadavard.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Groups: shared reminders with family or friends (turned on from the hidden settings, still being tried out).
 *
 * The groups, their members and invitations come from the server; the group's reminders are kept here in the same
 * table as your own ones but marked with their group, so they ring like any reminder yet never show in your home
 * list, stats or points. Changes go to the server right away (or as soon as there is internet), and the phone asks
 * for what the others changed when the app opens and every so often in the background.
 */
object Groups {
    data class Member(val id: String, val name: String, val username: String, val role: String, val active: Boolean,
                      val level: Int = 1, val badges: List<String> = emptyList(), val streak: Int = 0, val done: Int = 0,
                      val purchased: Double = 0.0, val admin: Boolean = false) {
        val tier: Tier? get() = Tier.of(purchased, admin)
    }
    data class Group(val id: String, val name: String, val owner: String, val ownerName: String, val editMode: String, val role: String,
                     val members: List<Member>, val invitedBy: String = "") {
        val isOwner: Boolean get() = role == "owner"
        /** May add, change and delete reminders and invite people. */
        val canEdit: Boolean get() = isOwner || editMode == "all"
        val activeMembers: List<Member> get() = members.filter { it.active }
    }
    data class State(val groups: List<Group> = emptyList(), val invites: List<Group> = emptyList(), val me: String = "",
                     val syncing: Boolean = false, val error: String = "", val tick: Int = 0)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var pending: ScheduledFuture<*>? = null
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("groups", Context.MODE_PRIVATE)

    /** Turned on in the hidden settings; then a "Groups" button shows on the home screen. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("on", false)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        _state.value = _state.value.copy(tick = _state.value.tick + 1)
        if (on) soon(context, 0)
        SyncWork.ensure(context)
    }

    fun me(context: Context): String = Account.profile.value?.id?.takeIf { it.isNotEmpty() } ?: prefs(context).getString("me", "").orEmpty()

    /** Rings on this phone: meant for everyone, or for me. */
    fun forMe(context: Context, r: Reminder): Boolean = r.assignee.isEmpty() || r.assignee == me(context)

    fun load(context: Context) {
        if (_state.value.groups.isNotEmpty() || _state.value.invites.isNotEmpty()) return
        val p = prefs(context)
        runCatching {
            val o = JSONObject(p.getString("cache", "{}")!!)
            _state.value = _state.value.copy(groups = parseGroups(o.optJSONArray("groups")), invites = parseGroups(o.optJSONArray("invites")),
                me = p.getString("me", "").orEmpty())
        }
    }

    fun items(context: Context, groupId: String): List<Reminder> = Repo.store(context).groupItems(groupId)

    /** Who did the occurrence the reminder is at now. */
    fun doneBy(r: Reminder): List<String> = runCatching {
        JSONObject(r.doneBy).optJSONArray(ReminderLogic.occurrence(r).toString())?.let { a -> List(a.length()) { a.getString(it) } }
    }.getOrNull() ?: lastDone(r)

    /** A finished one-off reminder: who did its (only) occurrence. */
    private fun lastDone(r: Reminder): List<String> = runCatching {
        val o = JSONObject(r.doneBy)
        if (!r.done) return@runCatching emptyList<String>()
        o.optJSONArray(r.firstAt.toString())?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
    }.getOrDefault(emptyList())

    // ---------------- changes made here ----------------

    /** The fields everyone in the group shares (alarm state stays on each phone). */
    private fun shared(r: Reminder): JSONObject = Backup.toJson(r).apply {
        listOf("id", "nextAt", "done", "completedAt", "completedCount", "pendingAt", "alertedAt", "snoozeAt", "missedCount", "missed").forEach { remove(it) }
    }

    /** Saves a group reminder made or changed here and sends it to the group. */
    fun saveItem(context: Context, r: Reminder): Reminder {
        val saved = Repo.store(context).save(r)
        Scheduler.schedule(context, saved)
        Repo.changedQuietly(context)
        send(context, "/api/yadar/groups/${r.groupId}/items", JSONObject().put("uid", saved.uid).put("data", shared(saved).toString())
            .put("assignee", saved.assignee).put("mode", saved.groupMode).put("notify", saved.notifyOthers).put("updated", saved.updatedAt))
        return saved
    }

    fun deleteItem(context: Context, r: Reminder) {
        Scheduler.cancel(context, r.id); Notifier.cancel(context, r.id)
        Repo.store(context).delete(r.id)
        Repo.changedQuietly(context)
        send(context, "/api/yadar/groups/${r.groupId}/items", JSONObject().put("uid", r.uid).put("deleted", true).put("updated", System.currentTimeMillis()))
    }

    /** I did it: my copy moves on, and the group hears about it. Nothing goes into my own stats or points. */
    fun markDone(context: Context, r: Reminder): Reminder {
        Notifier.cancel(context, r.id)
        val occurrence = ReminderLogic.occurrence(r).takeIf { it > 0 } ?: r.firstAt
        val next = ReminderLogic.complete(r, System.currentTimeMillis()).copy(doneBy = addDone(r.doneBy, occurrence, me(context), true))
        val saved = Repo.store(context).save(next, touch = false)
        Scheduler.schedule(context, saved)
        Repo.changedQuietly(context)
        send(context, "/api/yadar/groups/${r.groupId}/done", JSONObject().put("uid", r.uid).put("occurrence", occurrence)
            .put("finished", saved.done && r.groupMode == "one"))
        return saved
    }

    /** "Undo" of a done group reminder. */
    fun undoDone(context: Context, previous: Reminder) {
        val occurrence = ReminderLogic.occurrence(previous).takeIf { it > 0 } ?: previous.firstAt
        val saved = Repo.store(context).save(previous.copy(doneBy = addDone(previous.doneBy, occurrence, me(context), false)), touch = false)
        Scheduler.schedule(context, saved)
        Repo.changedQuietly(context)
        send(context, "/api/yadar/groups/${previous.groupId}/done", JSONObject().put("uid", previous.uid).put("occurrence", occurrence).put("undo", true))
    }

    private fun addDone(json: String, occurrence: Long, user: String, add: Boolean): String {
        val o = runCatching { JSONObject(json) }.getOrDefault(JSONObject())
        val key = occurrence.toString()
        val list = o.optJSONArray(key)?.let { a -> MutableList(a.length()) { a.getString(it) } } ?: mutableListOf()
        if (add && user !in list) list += user
        if (!add) list.remove(user)
        if (list.isEmpty()) o.remove(key) else o.put(key, JSONArray(list))
        // only the last few occurrences matter
        val keys = o.keys().asSequence().toList().sortedBy { it.toLongOrNull() ?: 0 }
        keys.dropLast(6).forEach { o.remove(it) }
        return o.toString()
    }

    /** Requests waiting for internet, sent in order before the next sync. */
    private fun send(context: Context, path: String, body: JSONObject) {
        val p = prefs(context)
        synchronized(this) {
            val out = runCatching { JSONArray(p.getString("outbox", "[]")) }.getOrDefault(JSONArray())
            out.put(JSONObject().put("path", path).put("body", body))
            p.edit().putString("outbox", out.toString()).apply()
        }
        soon(context, 1)
    }

    private fun flush(context: Context) {
        val p = prefs(context)
        while (true) {
            val first = synchronized(this) { runCatching { JSONArray(p.getString("outbox", "[]")) }.getOrDefault(JSONArray()).optJSONObject(0) } ?: return
            try {
                Account.api(context, "POST", first.getString("path"), first.getJSONObject("body"))
            } catch (e: IllegalStateException) {
                // the server said no (e.g. not allowed): drop it so the queue never gets stuck; no internet: try later
                if (e.message?.contains("\n") == true) throw e
                _state.value = _state.value.copy(error = e.message.orEmpty())
            }
            synchronized(this) {
                val out = runCatching { JSONArray(p.getString("outbox", "[]")) }.getOrDefault(JSONArray())
                if (out.length() > 0) out.remove(0)
                p.edit().putString("outbox", out.toString()).apply()
            }
        }
    }

    // ---------------- asking the server ----------------

    fun soon(context: Context, delaySeconds: Long = 5) {
        if (!enabled(context) || !Account.isLoggedIn(context)) return
        val app = context.applicationContext
        synchronized(this) {
            pending?.cancel(false)
            pending = executor.schedule({ runCatching { syncNow(app) } }, delaySeconds, TimeUnit.SECONDS)
        }
    }

    suspend fun sync(context: Context) = withContext(Dispatchers.IO) { syncNow(context) }

    @Synchronized
    fun syncNow(context: Context) {
        if (!enabled(context) || !Account.isLoggedIn(context)) return
        val p = prefs(context)
        _state.value = _state.value.copy(syncing = true)
        try {
            flush(context)
            val res = Account.api(context, "GET", "/api/yadar/groups?since=" + p.getLong("since", 0))
            val me = res.optString("me")
            val groups = parseGroups(res.optJSONArray("groups"))
            val invites = parseGroups(res.optJSONArray("invites"))
            val store = Repo.store(context)
            // groups I am no longer in: their reminders go
            val ids = groups.map { it.id }.toSet()
            store.groupItems().map { it.groupId }.distinct().filter { it !in ids }.forEach { gid ->
                store.groupItems(gid).forEach { Scheduler.cancel(context, it.id); Notifier.cancel(context, it.id) }
                store.deleteGroup(gid)
            }
            val items = res.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) runCatching { applyItem(context, store, items.getJSONObject(i)) }
            val done = res.optJSONArray("done") ?: JSONArray()
            for (i in 0 until done.length()) runCatching { applyDone(context, store, done.getJSONObject(i), me) }
            val events = res.optJSONArray("events") ?: JSONArray()
            for (i in 0 until events.length()) runCatching { notifyEvent(context, events.getJSONObject(i), groups) }
            // new invitations get a notification once
            val seen = p.getStringSet("seen_invites", emptySet()).orEmpty()
            invites.filter { it.id !in seen }.forEach { notifyInvite(context, it) }
            p.edit().putLong("since", res.optLong("now")).putString("me", me).putStringSet("seen_invites", invites.map { it.id }.toSet())
                .putString("cache", JSONObject().put("groups", res.optJSONArray("groups")).put("invites", res.optJSONArray("invites")).toString()).apply()
            _state.value = State(groups, invites, me, syncing = false, error = "", tick = _state.value.tick + 1)
            Repo.changedQuietly(context)
        } catch (e: Exception) {
            _state.value = _state.value.copy(syncing = false, error = e.message?.lineSequence()?.firstOrNull().orEmpty())
        }
    }

    private fun applyItem(context: Context, store: ReminderStore, o: JSONObject) {
        val uid = o.getString("uid")
        val local = store.byUid(uid)
        if (o.optBoolean("deleted")) {
            local?.let { Scheduler.cancel(context, it.id); Notifier.cancel(context, it.id); store.delete(it.id) }
            return
        }
        val updated = o.optLong("updated")
        val shared = Backup.fromJson(JSONObject(o.getString("data")).put("nextAt", 0))
        val now = System.currentTimeMillis()
        val ruleChanged = local == null || local.firstAt != shared.firstAt || local.unit != shared.unit || local.every != shared.every ||
            local.weekdays != shared.weekdays || local.monthDay != shared.monthDay || local.untilAt != shared.untilAt
        var r = (local ?: shared).copy(title = shared.title, note = shared.note, category = shared.category, important = shared.important,
            alertStyle = shared.alertStyle, firstAt = shared.firstAt, unit = shared.unit, every = shared.every, weekdays = shared.weekdays,
            monthDay = shared.monthDay, untilAt = shared.untilAt, leadMinutes = shared.leadMinutes, nagMinutes = shared.nagMinutes,
            zone = shared.zone, calendar = shared.calendar, createdAt = shared.createdAt,
            uid = uid, updatedAt = updated, groupId = o.getString("group"), assignee = o.optString("assignee"),
            groupMode = o.optString("mode", "one"), notifyOthers = o.optBoolean("notify"))
        if (ruleChanged) {
            // when it is due next on this phone, worked out from the shared rule
            r = Recurrence.align(r.copy(nextAt = r.firstAt, done = false, pendingAt = 0, alertedAt = 0, snoozeAt = 0))
            if (r.repeating && r.nextAt <= now) r = Recurrence.nextAfter(r, now)?.let { n -> r.copy(nextAt = n) } ?: r.copy(done = true)
        }
        val saved = store.save(r.copy(id = local?.id ?: 0), touch = false)
        Scheduler.schedule(context, saved)
    }

    private fun applyDone(context: Context, store: ReminderStore, o: JSONObject, me: String) {
        val local = store.byUid(o.getString("uid")) ?: return
        val occurrence = o.optLong("occurrence")
        val user = o.optString("user")
        val undone = o.optBoolean("undone")
        var r = local.copy(doneBy = addDone(local.doneBy, occurrence, user, !undone))
        // done by someone when one is enough, or by me on another phone: my copy moves on too
        val current = ReminderLogic.occurrence(local).takeIf { it > 0 } ?: local.firstAt
        if (!undone && !local.done && occurrence == current && (local.groupMode == "one" || user == me)) {
            Notifier.cancel(context, local.id)
            r = ReminderLogic.complete(r, System.currentTimeMillis())
        }
        val saved = store.save(r, touch = false)
        Scheduler.schedule(context, saved)
    }

    private fun parseGroups(a: JSONArray?): List<Group> {
        if (a == null) return emptyList()
        return List(a.length()) { i ->
            val g = a.getJSONObject(i)
            val m = g.optJSONArray("members") ?: JSONArray()
            Group(g.getString("id"), g.optString("name"), g.optString("owner"), g.optString("owner_name"), g.optString("edit_mode", "owner"),
                g.optString("role"), List(m.length()) { j ->
                    val x = m.getJSONObject(j)
                    val badges = x.optJSONArray("badges")
                    Member(x.getString("id"), x.optString("name"), x.optString("username"), x.optString("role"), x.optString("status") == "active",
                        x.optInt("level", 1).coerceAtLeast(1), badges?.let { b -> List(b.length()) { b.getString(it) } }.orEmpty(),
                        x.optInt("streak"), x.optInt("done"), x.optDouble("purchased", 0.0), x.optBoolean("admin"))
                }, g.optString("invited_by"))
        }
    }

    // ---------------- actions from the screens (they throw with a readable message) ----------------

    private suspend fun post(context: Context, path: String, body: JSONObject? = null) = withContext(Dispatchers.IO) {
        Account.api(context, "POST", path, body).also { syncNow(context) }
    }

    suspend fun create(context: Context, name: String, everyoneEdits: Boolean) =
        post(context, "/api/yadar/groups", JSONObject().put("name", name).put("edit_mode", if (everyoneEdits) "all" else "owner"))
    suspend fun settings(context: Context, id: String, name: String, everyoneEdits: Boolean) =
        post(context, "/api/yadar/groups/$id/settings", JSONObject().put("name", name).put("edit_mode", if (everyoneEdits) "all" else "owner"))
    suspend fun invite(context: Context, id: String, username: String) =
        post(context, "/api/yadar/groups/$id/invite", JSONObject().put("username", username))
    suspend fun answer(context: Context, id: String, accept: Boolean) =
        post(context, "/api/yadar/groups/$id/" + if (accept) "accept" else "decline")
    suspend fun leave(context: Context, id: String) = post(context, "/api/yadar/groups/$id/leave")
    suspend fun remove(context: Context, id: String, user: String) = post(context, "/api/yadar/groups/$id/remove", JSONObject().put("user", user))
    suspend fun delete(context: Context, id: String) = post(context, "/api/yadar/groups/$id/delete")

    // ---------------- notifications ----------------

    private const val CHANNEL = "yadimo_groups"

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        val fa = Prefs.language(context) == AppLanguage.FA
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, if (fa) "گروه‌ها" else "Groups", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 7300, Intent(context, MainActivity::class.java).setAction(WidgetCommands.GROUPS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            NotificationManagerCompat.from(context).notify(id, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }

    private fun notifyEvent(context: Context, e: JSONObject, groups: List<Group>) {
        val fa = Prefs.language(context) == AppLanguage.FA
        val group = groups.firstOrNull { it.id == e.optString("group") }?.name.orEmpty()
        val who = e.optString("actor"); val what = e.optString("text")
        val text = when (e.optString("kind")) {
            "done" -> if (fa) "$who «$what» را انجام داد ✓" else "$who did “$what” ✓"
            "added" -> if (fa) "$who یادآوری «$what» را اضافه کرد" else "$who added “$what”"
            "joined" -> if (fa) "$who به گروه پیوست 👋" else "$who joined 👋"
            "left" -> if (fa) "$who از گروه رفت" else "$who left"
            else -> return
        }
        notify(context, 7310 + (e.optLong("at") % 1000).toInt(), "👥 $group", text)
    }

    private fun notifyInvite(context: Context, g: Group) {
        val fa = Prefs.language(context) == AppLanguage.FA
        notify(context, 7400 + (g.id.hashCode() and 0xff), if (fa) "دعوت به گروه «${g.name}»" else "Invited to “${g.name}”",
            if (fa) "${g.invitedBy.ifBlank { g.ownerName }} تو را به گروه دعوت کرده؛ برای دیدن و قبول کردن بزن." else
                "${g.invitedBy.ifBlank { g.ownerName }} invited you; tap to see and accept.")
    }
}
