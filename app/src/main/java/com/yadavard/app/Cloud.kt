package com.yadavard.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cloud setup and sign-in are optional; no network request is made without an account. */
class CloudSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("cloud_settings", Context.MODE_PRIVATE)
    var url: String
        get() = prefs.getString("url", "").orEmpty()
        set(value) { prefs.edit().putString("url", value.trim().trimEnd('/')).apply() }
    var publishableKey: String
        get() = prefs.getString("publishable_key", "").orEmpty()
        set(value) { prefs.edit().putString("publishable_key", value.trim()).apply() }
    var botUsername: String
        get() = prefs.getString("bot", "").orEmpty()
        set(value) { prefs.edit().putString("bot", value.trim().removePrefix("@")).apply() }
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("yadar_cloud_session_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("yadar_cloud_session_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun session(): JSONObject? {
        val saved = prefs.getString("session", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(saved, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8))
        }.getOrNull()
    }
    fun saveSession(session: JSONObject?) {
        if (session == null) { prefs.edit().remove("session").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val bytes = cipher.iv + cipher.doFinal(session.toString().toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("session", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }
    fun lastUserId(): String = prefs.getString("last_user_id", "").orEmpty()
    fun setLastUserId(id: String) { prefs.edit().putString("last_user_id", id).apply() }
}

class CloudApi(private val context: Context) {
    companion object { private val syncMutex = Mutex() }
    val settings = CloudSettings(context)
    private fun endpoint(path: String): String {
        val base = settings.url
        require(base.startsWith("https://") && base.removePrefix("https://").isNotBlank() &&
            !base.contains('?') && !base.contains('#')) { "نشانی HTTPS پروژهٔ Supabase را وارد کن" }
        require(settings.publishableKey.isNotBlank()) { "کلید عمومی پروژهٔ Supabase را وارد کن" }
        return "$base$path"
    }
    private fun call(path: String, method: String = "GET", body: String? = null, token: String? = null,
                     preference: String? = null): String {
        val connection = (URL(endpoint(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 35_000
            setRequestProperty("apikey", settings.publishableKey)
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            if (preference != null) setRequestProperty("Prefer", preference)
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) } }
        }
        return try {
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("Supabase ($status): ${text.take(200)}")
            text
        } finally { connection.disconnect() }
    }
    suspend fun signIn(email: String, password: String, signUp: Boolean): Boolean = withContext(Dispatchers.IO) {
        val data = JSONObject(call(if (signUp) "/auth/v1/signup" else "/auth/v1/token?grant_type=password",
            "POST", JSONObject().put("email", email.trim()).put("password", password).toString()))
        if (!data.has("access_token")) return@withContext false // Email confirmation is enabled.
        val userId = data.getJSONObject("user").getString("id")
        val previous = settings.lastUserId()
        if (previous.isNotEmpty() && previous != userId) ReminderStore(context).resetCloudBinding()
        settings.setLastUserId(userId)
        settings.saveSession(data)
        true
    }
    private fun token(): String {
        var session = settings.session() ?: error("ابتدا وارد حساب شو")
        val expires = session.optLong("expires_at", 0)
        if (expires <= System.currentTimeMillis() / 1000 + 90) {
            val refresh = session.optString("refresh_token")
            require(refresh.isNotBlank()) { "نشست منقضی شده؛ دوباره وارد شو" }
            session = JSONObject(call("/auth/v1/token?grant_type=refresh_token", "POST",
                JSONObject().put("refresh_token", refresh).toString()))
            settings.saveSession(session)
        }
        return session.getString("access_token")
    }
    suspend fun linkCode(): String = withContext(Dispatchers.IO) {
        JSONObject(call("/functions/v1/link-code", "POST",
            JSONObject().put("zone", ZoneId.systemDefault().id).toString(), token())).getString("code")
    }
    suspend fun sync(onChanged: () -> Unit): Int = withContext(Dispatchers.IO) { syncMutex.withLock {
        val access = token()
        val userId = settings.session()!!.getJSONObject("user").getString("id")
        val store = ReminderStore(context)
        var changes = 0
        fun page(offset: Int): JSONArray = JSONArray(call(
            "/rest/v1/reminders?select=*&order=updated_at.asc&limit=500&offset=$offset", token = access))
        val unbound = store.allForSync().filter { it.cloudDirty && it.cloudId == null && !it.cloudDeleted }
        val matchingRemote = mutableMapOf<Pair<String, Long>, String>()
        if (unbound.isNotEmpty()) {
            var at = 0
            while (true) {
                val batch = page(at)
                for (i in 0 until batch.length()) {
                    val row = batch.getJSONObject(i)
                    if (!row.optBoolean("deleted")) {
                        val data = row.getJSONObject("data")
                        matchingRemote[data.optString("title") to data.optLong("firstAt")] = row.getString("id")
                    }
                }
                if (batch.length() < 500) break
                at += 500
            }
        }
        store.allForSync().filter { it.cloudDirty }.forEach { local ->
            val id = local.cloudId ?: matchingRemote.remove(local.title to local.firstAt) ?: UUID.randomUUID().toString()
            val payload = JSONObject().put("id", id).put("user_id", userId).put("data", encode(local))
                .put("next_at", if (local.cloudDeleted) 0 else local.nextAt).put("deleted", local.cloudDeleted)
            call("/rest/v1/reminders?on_conflict=id", "POST", JSONArray().put(payload).toString(), access,
                "resolution=merge-duplicates,return=minimal")
            store.markSynced(local.id, id, local.modifiedAt)
            changes++
        }
        var offset = 0
        while (true) {
            val batch = page(offset)
            for (i in 0 until batch.length()) {
                val row = batch.getJSONObject(i)
                val id = row.getString("id")
                val current = store.byCloudId(id)
                if (current?.cloudDirty == true) continue
                val incoming = decode(row.getJSONObject("data"), current?.id ?: 0).copy(
                    cloudId = id, cloudDeleted = row.optBoolean("deleted"), cloudDirty = false,
                    nextAt = row.optLong("next_at", 0), modifiedAt = System.currentTimeMillis())
                if (current != null && !incoming.cloudDeleted && current.firstAt == incoming.firstAt &&
                    current.title == incoming.title && current.unit == incoming.unit &&
                    current.nextAt > incoming.nextAt && incoming.nextAt > 0) continue // Server has not advanced this alarm yet.
                if (current != null && current.title == incoming.title && current.note == incoming.note &&
                    current.firstAt == incoming.firstAt && current.nextAt == incoming.nextAt &&
                    current.unit == incoming.unit && current.every == incoming.every &&
                    current.weekdays == incoming.weekdays && current.monthDay == incoming.monthDay &&
                    current.leadMinutes == incoming.leadMinutes && current.untilAt == incoming.untilAt &&
                    current.zone == incoming.zone && current.done == incoming.done &&
                    current.cloudDeleted == incoming.cloudDeleted && current.lastCompletedAt == incoming.lastCompletedAt) continue
                if (current != null) ReminderAlarms.cancel(context, current)
                val saved = store.saveFromCloud(incoming)
                if (!saved.cloudDeleted) ReminderAlarms.schedule(context, saved)
                changes++
            }
            if (batch.length() < 500) break
            offset += 500
        }
        withContext(Dispatchers.Main) { onChanged() }
        changes
    } }
    private fun encode(r: Reminder) = JSONObject().put("title", r.title).put("note", r.note)
        .put("firstAt", r.firstAt).put("nextAt", r.nextAt).put("unit", r.unit.name)
        .put("every", r.every).put("weekdays", r.weekdays).put("monthDay", r.monthDay)
        .put("leadMinutes", r.leadMinutes).put("untilAt", r.untilAt ?: JSONObject.NULL)
        .put("zone", r.zone).put("done", r.done).put("lastCompletedAt", r.lastCompletedAt)
    private fun decode(data: JSONObject, id: Long): Reminder = Reminder(id = id,
        title = data.optString("title").take(180), note = data.optString("note").take(2000),
        firstAt = data.optLong("firstAt"), nextAt = data.optLong("nextAt"),
        unit = runCatching { RepeatUnit.valueOf(data.optString("unit")) }.getOrDefault(RepeatUnit.NONE),
        every = data.optInt("every", 1).coerceIn(1, 3650), weekdays = data.optInt("weekdays"),
        monthDay = data.optInt("monthDay"), leadMinutes = data.optInt("leadMinutes"),
        untilAt = if (data.isNull("untilAt")) null else data.optLong("untilAt"),
        zone = data.optString("zone", "Asia/Tehran"), done = data.optBoolean("done"),
        lastCompletedAt = data.optLong("lastCompletedAt"))
}
