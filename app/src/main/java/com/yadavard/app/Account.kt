package com.yadavard.app

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Thrown when the account has no tokens left (HTTP 402). */
class NoBalanceException(message: String) : Exception(message)

/** Thrown when the session is no longer valid (HTTP 401); the user has to log in again. */
class LoggedOutException(message: String) : Exception(message)

/**
 * The Yadar account: phone login, profile and token balance. AI requests go through the Yadar server, which
 * holds the provider keys and deducts tokens. Everything else in the app works without an account.
 */
object Account {
    data class Profile(
        val phone: String, val name: String, val balance: Double, val unlimited: Boolean,
        val plan: String, val textModel: String, val audioModel: String, val aiEnabled: Boolean,
    )

    private val _profile = MutableStateFlow<Profile?>(null)
    /** The signed-in profile, or null when signed out. Observed by the UI. */
    val profile: StateFlow<Profile?> = _profile

    /** Set when an AI feature needs the user to sign in; the main screen shows the login dialog. */
    val loginRequested = MutableStateFlow(false)

    const val DEFAULT_SERVER = "https://yadar-api.liara.run"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun token(context: Context): String? = prefs(context).getString("token", null)?.takeIf { it.isNotBlank() }
    fun isLoggedIn(context: Context): Boolean = token(context) != null

    fun server(context: Context): String =
        (prefs(context).getString("server", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER).trimEnd('/')
    fun setServer(context: Context, url: String) { prefs(context).edit().putString("server", url.trim()).apply() }

    /** Loads the cached profile so the UI has something to show before the network answers. */
    fun load(context: Context) {
        if (_profile.value != null || !isLoggedIn(context)) return
        prefs(context).getString("profile", null)?.let { runCatching { _profile.value = parse(JSONObject(it)) } }
    }

    private fun parse(o: JSONObject) = Profile(o.optString("phone"), o.optString("name"), o.optDouble("balance", 0.0),
        o.optBoolean("unlimited"), o.optString("plan"), o.optString("text_model"), o.optString("audio_model"),
        o.optBoolean("ai_enabled", true))

    private fun store(context: Context, profile: JSONObject) {
        prefs(context).edit().putString("profile", profile.toString()).apply()
        _profile.value = parse(profile)
    }

    fun logout(context: Context) {
        prefs(context).edit().remove("token").remove("profile").apply()
        _profile.value = null
    }

    private fun fa() = AppDisplay.language == AppLanguage.FA

    /** Calls the Yadar server and returns the JSON answer; maps 401/402 to dedicated exceptions. */
    private fun call(context: Context, method: String, path: String, body: JSONObject? = null, timeout: Int = 30_000,
                     auth: Boolean = true): JSONObject {
        val conn = (URL(server(context) + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = timeout
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            if (auth) token(context)?.let { setRequestProperty("Authorization", it) }
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code in 200..299) return json
            val message = json.optString("message").trim().trimEnd('.').ifBlank {
                if (fa()) "خطای سرور ($code)" else "Server error ($code)"
            }
            when (code) {
                401 -> if (auth) { logout(context); throw LoggedOutException(if (fa()) "دوباره وارد حسابت شو" else "Please sign in again") }
                402 -> throw NoBalanceException(message)
            }
            error(message)
        } catch (e: java.io.IOException) {
            error(if (fa()) "اتصال به سرور یادار برقرار نشد؛ اینترنت را چک کن" else "Could not reach the Yadar server; check your connection")
        } finally { conn.disconnect() }
    }

    /** Asks the server to text a login code; returns the code length. */
    suspend fun requestCode(context: Context, phone: String): Int = withContext(Dispatchers.IO) {
        call(context, "POST", "/api/yadar/otp/request", JSONObject().put("phone", phone), auth = false).optInt("length", 5)
    }

    suspend fun verify(context: Context, phone: String, code: String) = withContext(Dispatchers.IO) {
        val res = call(context, "POST", "/api/yadar/otp/verify", JSONObject().put("phone", phone).put("code", code), auth = false)
        prefs(context).edit().putString("token", res.getString("token")).putLong("token_at", System.currentTimeMillis()).apply()
        store(context, res.getJSONObject("profile"))
        // Signing in is how people opt into the assistant, so typed quick add uses it too from now on.
        Prefs.setUseAi(context, true)
    }

    /** Updates the profile and renews the session token about once a week. Errors are ignored (offline). */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        if (!isLoggedIn(context)) return@withContext
        runCatching {
            if (System.currentTimeMillis() - prefs(context).getLong("token_at", 0) > 7L * 24 * 3600_000) {
                val res = call(context, "POST", "/api/collections/users/auth-refresh")
                res.optString("token").takeIf { it.isNotBlank() }?.let {
                    prefs(context).edit().putString("token", it).putLong("token_at", System.currentTimeMillis()).apply()
                }
            }
            store(context, call(context, "GET", "/api/yadar/me"))
        }
    }

    suspend fun setName(context: Context, name: String) = withContext(Dispatchers.IO) {
        store(context, call(context, "POST", "/api/yadar/me", JSONObject().put("name", name)))
    }

    private fun afterCall(res: JSONObject) {
        val current = _profile.value ?: return
        _profile.value = current.copy(balance = res.optDouble("balance", current.balance), unlimited = res.optBoolean("unlimited", current.unlimited))
    }

    private fun requireLogin(context: Context) {
        if (!isLoggedIn(context)) {
            loginRequested.value = true
            throw LoggedOutException(if (fa()) "برای استفاده از هوش مصنوعی وارد حسابت شو" else "Sign in to use the assistant")
        }
    }

    /** Chat completion through the server; the server picks the model. [body] is an OpenAI-style request. */
    fun chat(context: Context, body: JSONObject): String {
        requireLogin(context)
        val req = JSONObject().put("messages", body.getJSONArray("messages"))
            .put("max_tokens", body.optInt("max_tokens", 1500)).put("temperature", body.optDouble("temperature", 0.0))
        val res = call(context, "POST", "/api/yadar/ai/chat", req, timeout = 120_000)
        afterCall(res)
        return res.optString("content")
    }

    fun transcribe(context: Context, file: java.io.File): String {
        requireLogin(context)
        val req = JSONObject().put("audio", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            .put("language", if (fa()) "fa" else "en")
        val res = call(context, "POST", "/api/yadar/ai/transcribe", req, timeout = 120_000)
        afterCall(res)
        return res.optString("text")
    }
}

/** Whether AI features can run now: signed in, and with an own key when "use my own AI" is on. */
object Ai {
    fun ready(context: Context): Boolean =
        Account.isLoggedIn(context) && (!AiSettings(context).personal || AiSettings(context).hasKey())
}
