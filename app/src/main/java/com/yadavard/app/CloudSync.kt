package com.yadavard.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional cloud copy of your own reminders, notes and categories, for signed-in users who turn it on. Everything
 * is encrypted on the phone before it leaves (AES-GCM, with a key the server works out for this account), so the
 * stored copy cannot be read as it is. On a new phone, signing in and turning it on brings everything back.
 *
 * Each item is sent on its own and only when it changed; the phone asks only for what changed since its last
 * visit. When two phones changed the same item, the newer change wins. Group reminders are not part of this.
 */
object CloudSync {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var pending: ScheduledFuture<*>? = null

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)

    fun on(context: Context): Boolean = prefs(context).getBoolean("on", false) && Account.isLoggedIn(context)

    /** When the last sync finished (ms), 0 = never; and its error, "" when it went well. */
    fun lastSync(context: Context): Long = prefs(context).getLong("last_ok", 0)
    fun lastError(context: Context): String = prefs(context).getString("last_error", "").orEmpty()

    /** Turning it on sends everything up and brings down what the account already has (from another phone). */
    fun setOn(context: Context, on: Boolean) {
        val p = prefs(context)
        if (on) p.edit().putBoolean("on", true).putLong("pull_since", 0).putLong("pushed", 0).apply()
        else p.edit().putBoolean("on", false).apply()
        if (on) soon(context, 0)
        SyncWork.ensure(context)
    }

    /** Removes the cloud copy (the phone keeps everything) and turns sync off. */
    fun wipe(context: Context) {
        Account.api(context, "POST", "/api/yadar/sync/wipe")
        prefs(context).edit().putBoolean("on", false).remove("key").apply()
    }

    /** After a change: sync a few seconds later, once for a burst of changes. */
    fun soon(context: Context, delaySeconds: Long = 8) {
        if (!on(context)) return
        val app = context.applicationContext
        synchronized(this) {
            pending?.cancel(false)
            pending = executor.schedule({ runCatching { now(app) } }, delaySeconds, TimeUnit.SECONDS)
        }
    }

    // ---------------- the work ----------------

    @Synchronized
    fun now(context: Context) {
        if (!on(context)) return
        val p = prefs(context)
        try {
            val key = key(context)
            pull(context, key)
            push(context, key)
            p.edit().putLong("last_ok", System.currentTimeMillis()).putString("last_error", "").apply()
        } catch (e: Exception) {
            android.util.Log.w("YadimoSync", "sync failed", e)
            p.edit().putString("last_error", e.message?.lineSequence()?.firstOrNull().orEmpty()).apply()
        }
    }

    private fun key(context: Context): ByteArray {
        val p = prefs(context)
        p.getString("key", null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val k = Account.api(context, "GET", "/api/yadar/sync/key").getString("key")
        p.edit().putString("key", k).apply()
        return Base64.decode(k, Base64.NO_WRAP)
    }

    private fun pull(context: Context, key: ByteArray) {
        val p = prefs(context)
        val store = Repo.store(context)
        var changed = false
        for (round in 0 until 20) {
            val res = Account.api(context, "GET", "/api/yadar/sync?since=" + p.getLong("pull_since", 0))
            val items = res.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                runCatching { if (apply(context, store, key, it)) changed = true }
                    .onFailure { e -> android.util.Log.w("YadimoSync", "item skipped", e) }
            }
            p.edit().putLong("pull_since", res.optLong("now")).apply()
            if (!res.optBoolean("more")) break
        }
        if (changed) Repo.changedQuietly(context)
    }

    /** Takes one item from the cloud when it is newer than the copy here. Returns whether anything changed. */
    private fun apply(context: Context, store: ReminderStore, key: ByteArray, it: JSONObject): Boolean {
        val uid = it.getString("uid")
        val updated = it.optLong("updated")
        val deleted = it.optBoolean("deleted")
        when (it.optString("kind")) {
            "reminder" -> {
                val local = store.byUid(uid)
                if (local != null && local.updatedAt >= updated) return false
                if (deleted) {
                    if (local == null) return false
                    Scheduler.cancel(context, local.id); Notifier.cancel(context, local.id)
                    store.delete(local.id); store.forgetGone(listOf(uid))
                    return true
                }
                val r = Backup.fromJson(JSONObject(decrypt(key, it.getString("data"))))
                    .copy(id = local?.id ?: 0, uid = uid, updatedAt = updated, groupId = "")
                val saved = store.save(r, touch = false)
                Scheduler.schedule(context, saved)
                return true
            }
            "note" -> {
                val local = store.notes().firstOrNull { n -> n.uid == uid }
                if (local != null && local.updatedAt >= updated) return false
                if (deleted) {
                    if (local == null) return false
                    store.deleteNote(local.id); store.forgetGone(listOf(uid))
                    return true
                }
                val o = JSONObject(decrypt(key, it.getString("data")))
                store.saveNote(Note(id = local?.id ?: 0, title = o.optString("title"), body = o.optString("body"), color = o.optInt("color"),
                    pinned = o.optBoolean("pinned"), createdAt = o.optLong("createdAt", updated), updatedAt = updated, uid = uid))
                return true
            }
            "categories" -> {
                if (deleted || prefs(context).getLong("categories_at", 0) >= updated) return false
                Categories.importJson(context, JSONArray(decrypt(key, it.getString("data"))))
                prefs(context).edit().putLong("categories_at", updated).putString("categories_sent", Categories.exportJson().toString()).apply()
                return true
            }
        }
        return false
    }

    private fun push(context: Context, key: ByteArray) {
        val p = prefs(context)
        val store = Repo.store(context)
        val since = p.getLong("pushed", 0)
        val started = System.currentTimeMillis()
        val out = mutableListOf<JSONObject>()
        for (r in store.all()) if (r.updatedAt > since) out += item(r.uid, "reminder", r.updatedAt, encrypt(key, Backup.toJson(r).toString()))
        for (n in store.notes()) if (n.updatedAt > since) out += item(n.uid, "note", n.updatedAt, encrypt(key, JSONObject()
            .put("title", n.title).put("body", n.body).put("color", n.color).put("pinned", n.pinned).put("createdAt", n.createdAt).toString()))
        val cats = Categories.exportJson().toString()
        if (cats != p.getString("categories_sent", null)) {
            out += item("categories", "categories", started, encrypt(key, cats))
            p.edit().putLong("categories_at", started).apply()
        }
        val gone = store.goneList()
        for ((uid, kind) in gone) out += JSONObject().put("uid", uid).put("kind", kind).put("deleted", true).put("updated", started)
        for (chunk in out.chunked(200)) Account.api(context, "POST", "/api/yadar/sync", JSONObject().put("items", JSONArray(chunk)))
        store.forgetGone(gone.keys)
        p.edit().putLong("pushed", started).putString("categories_sent", cats).apply()
    }

    private fun item(uid: String, kind: String, updated: Long, data: String) =
        JSONObject().put("uid", uid).put("kind", kind).put("updated", updated).put("data", data)

    // ---------------- encryption ----------------

    private fun encrypt(key: ByteArray, text: String): String {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(key: ByteArray, data: String): String {
        val raw = Base64.decode(data, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw, 0, 12))
        return String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }
}
