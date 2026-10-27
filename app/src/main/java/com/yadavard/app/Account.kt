package com.yadavard.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/** Thrown when the account has no tokens left (HTTP 402). */
class NoBalanceException(message: String) : Exception(message)

/** Thrown when the session is no longer valid (HTTP 401); the user has to log in again. */
class LoggedOutException(message: String) : Exception(message)

/**
 * The Yadimo account: Bazaar (or phone) login, profile and token balance. AI requests go through the Yadimo server, which
 * holds the provider keys and deducts tokens. Everything else in the app works without an account.
 */
object Account {
    data class Profile(
        val phone: String, val name: String, val balance: Double, val unlimited: Boolean,
        val plan: String, val textModel: String, val audioModel: String, val aiEnabled: Boolean,
        val id: String = "", val bazaar: Boolean = false,
    )

    /** A token pack sold through Cafe Bazaar; prices are set in the Bazaar panel, [price] is only shown. */
    data class Pack(val sku: String, val tokens: Double, val title: String, val price: String, val note: String)

    private val _profile = MutableStateFlow<Profile?>(null)
    /** The signed-in profile, or null when signed out. Observed by the UI. */
    val profile: StateFlow<Profile?> = _profile

    /** Balance at or below which the app warns that tokens are running out. */
    const val LOW_BALANCE = 5.0

    /** Set to open the token packs (from the low-balance notice or the home card). */
    val buyRequested = MutableStateFlow(false)

    /** Tokens charged by the server since the app started; the usage log diffs it per assistant request. */
    @Volatile var chargedTotal = 0.0
        private set

    /** Set when an AI feature needs the user to sign in; the main screen shows the login dialog. */
    val loginRequested = MutableStateFlow(false)

    const val DEFAULT_SERVER = "https://yadar.pish.run"

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
        o.optBoolean("ai_enabled", true), o.optString("id"), o.optBoolean("bazaar"))

    private fun store(context: Context, profile: JSONObject) {
        prefs(context).edit().putString("profile", profile.toString()).apply()
        val before = _profile.value?.balance ?: Double.MAX_VALUE
        val parsed = parse(profile)
        _profile.value = parsed
        LowBalance.check(context, before, parsed)
    }

    fun logout(context: Context) {
        prefs(context).edit().remove("token").remove("profile").remove("bazaar_verifier").apply()
        _profile.value = null
        YadimoShop.clear(context)
    }

    private fun fa() = AppDisplay.language == AppLanguage.FA

    /** Calls the Yadimo server and returns the JSON answer; maps 401/402 to dedicated exceptions. */
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
            // Name the server and the underlying cause so a wrong address or a certificate problem is visible.
            val cause = e.javaClass.simpleName + (e.message?.let { ": " + it.take(120) } ?: "")
            error((if (fa()) "اتصال به سرور یادیمو برقرار نشد؛ اینترنت را چک کن" else "Could not reach the Yadimo server; check your connection") +
                "\n" + server(context) + "\n" + cause)
        } finally { conn.disconnect() }
    }

    private fun signedIn(context: Context, res: JSONObject) {
        prefs(context).edit().putString("token", res.getString("token")).putLong("token_at", System.currentTimeMillis())
            .remove("bazaar_verifier").apply()
        store(context, res.getJSONObject("profile"))
        // Signing in is how people opt into the assistant, so typed quick add uses it too from now on.
        Prefs.setUseAi(context, true)
    }

    // ---- Bazaar login: the browser signs in on Bazaar's site, then the app claims the login with a secret only it knows.

    private val claimLock = Mutex()

    /** Opens Bazaar's sign-in page in the browser. */
    fun startBazaarLogin(context: Context) {
        val verifier = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
            .joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString("bazaar_verifier", verifier).putLong("bazaar_at", System.currentTimeMillis()).apply()
        val url = server(context) + "/api/yadar/bazaar/start?challenge=" + challenge + "&app=" + Uri.encode(context.packageName)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** True while a Bazaar sign-in started in the last 15 minutes is waiting to be claimed. */
    fun bazaarPending(context: Context): Boolean = !isLoggedIn(context) &&
        prefs(context).getString("bazaar_verifier", null) != null &&
        System.currentTimeMillis() - prefs(context).getLong("bazaar_at", 0) < 15 * 60_000

    /** Signs in with the account id the Bazaar app returned (in-app login). */
    suspend fun bazaarAppLogin(context: Context, accountId: String) = withContext(Dispatchers.IO) {
        @android.annotation.SuppressLint("HardwareIds")
        val device = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        signedIn(context, call(context, "POST", "/api/yadar/bazaar/app-login",
            JSONObject().put("account_id", accountId).put("device", device), auth = false))
    }

    fun cancelBazaarLogin(context: Context) { prefs(context).edit().remove("bazaar_verifier").apply() }

    /** Asks the server whether the Bazaar sign-in finished; true once signed in, false while still waiting. */
    suspend fun claimBazaar(context: Context): Boolean = withContext(Dispatchers.IO) {
        claimLock.withLock {
            if (isLoggedIn(context)) return@withLock true
            val verifier = prefs(context).getString("bazaar_verifier", null) ?: return@withLock false
            val res = try {
                call(context, "POST", "/api/yadar/bazaar/claim", JSONObject().put("verifier", verifier), auth = false)
            } catch (e: IllegalStateException) {
                if (e.message?.contains(server(context)) != true) cancelBazaarLogin(context) // keep it only when offline
                throw e
            }
            if (res.optBoolean("pending")) return@withLock false
            signedIn(context, res)
            true
        }
    }

    // ---- token packs ----

    /** The packs on sale and whether the server can verify purchases yet. */
    suspend fun packs(context: Context): Pair<List<Pack>, Boolean> = withContext(Dispatchers.IO) {
        val res = call(context, "GET", "/api/yadar/packs")
        val items = res.optJSONArray("items")
        val list = (0 until (items?.length() ?: 0)).map { i ->
            val o = items!!.getJSONObject(i)
            Pack(o.optString("sku"), o.optDouble("tokens", 0.0), o.optString("title"), o.optString("price"), o.optString("note"))
        }
        list to res.optBoolean("ready")
    }

    /** What the server did with a purchase: tokens added now, and whether the app should consume it on Bazaar. */
    data class Redeemed(val added: Double, val consume: Boolean)

    /**
     * Sends a Bazaar purchase to the server, which checks Bazaar's signature (or asks Bazaar) and adds the tokens
     * once. Bazaar's signed purchase data travels with it, so nothing here can be faked by the app.
     */
    suspend fun redeem(context: Context, packageName: String, sku: String, purchaseToken: String, orderId: String,
                       originalJson: String, signature: String): Redeemed = withContext(Dispatchers.IO) {
        val res = call(context, "POST", "/api/yadar/purchase", JSONObject().put("package", packageName).put("sku", sku)
            .put("token", purchaseToken).put("order_id", orderId).put("original_json", originalJson).put("signature", signature))
        res.optJSONObject("profile")?.let { store(context, it) }
        Redeemed(res.optDouble("added", 0.0), res.optBoolean("consume"))
    }

    /** Tells the server the app consumed the purchase on Bazaar. */
    suspend fun consumed(context: Context, purchaseToken: String) = withContext(Dispatchers.IO) {
        runCatching { call(context, "POST", "/api/yadar/purchase/consumed", JSONObject().put("token", purchaseToken)) }
    }

    /** Asks the server to text a login code; returns the code length. */
    suspend fun requestCode(context: Context, phone: String): Int = withContext(Dispatchers.IO) {
        call(context, "POST", "/api/yadar/otp/request", JSONObject().put("phone", phone), auth = false).optInt("length", 5)
    }

    suspend fun verify(context: Context, phone: String, code: String) = withContext(Dispatchers.IO) {
        signedIn(context, call(context, "POST", "/api/yadar/otp/verify", JSONObject().put("phone", phone).put("code", code), auth = false))
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

    /** Totals only (see the server's user_stats); never reminder text. */
    suspend fun sendStats(context: Context, body: JSONObject) = withContext(Dispatchers.IO) {
        call(context, "POST", "/api/yadar/stats", body)
    }

    suspend fun setName(context: Context, name: String) = withContext(Dispatchers.IO) {
        store(context, call(context, "POST", "/api/yadar/me", JSONObject().put("name", name)))
    }

    // ---- Yadimo's wardrobe: owned parts and the chosen look live on the account ----

    /** The wardrobe state (tiers, prices, owned parts, saved look and balance). */
    suspend fun yadimo(context: Context): JSONObject = withContext(Dispatchers.IO) { call(context, "GET", "/api/yadar/yadimo") }

    /** Buys one part ("hat:crown") with tokens; the server checks the balance and keeps it on the account. */
    suspend fun yadimoBuy(context: Context, item: String): JSONObject = withContext(Dispatchers.IO) {
        call(context, "POST", "/api/yadar/yadimo/buy", JSONObject().put("item", item)).also { res ->
            val current = _profile.value
            if (current != null && res.has("balance")) {
                val updated = current.copy(balance = res.optDouble("balance", current.balance))
                _profile.value = updated
                prefs(context).getString("profile", null)?.let { raw ->
                    runCatching { prefs(context).edit().putString("profile", JSONObject(raw).put("balance", updated.balance).toString()).apply() }
                }
                LowBalance.check(context, current.balance, updated)
            }
        }
    }

    suspend fun yadimoLook(context: Context, look: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        call(context, "POST", "/api/yadar/yadimo/look", look)
    }

    /** Today's weather near a city, cached by the server; works without signing in. */
    suspend fun weather(context: Context, lat: Double, lon: Double): JSONObject = withContext(Dispatchers.IO) {
        call(context, "GET", "/api/yadar/weather?lat=$lat&lon=$lon", timeout = 15_000, auth = false)
    }

    private fun afterCall(context: Context, res: JSONObject) {
        chargedTotal += res.optDouble("charged", 0.0)
        val current = _profile.value ?: return
        val updated = current.copy(balance = res.optDouble("balance", current.balance), unlimited = res.optBoolean("unlimited", current.unlimited))
        _profile.value = updated
        LowBalance.check(context, current.balance, updated)
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
        afterCall(context, res)
        return res.optString("content")
    }

    fun transcribe(context: Context, file: java.io.File): String {
        requireLogin(context)
        val req = JSONObject().put("audio", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            .put("language", if (fa()) "fa" else "en")
        val res = call(context, "POST", "/api/yadar/ai/transcribe", req, timeout = 120_000)
        afterCall(context, res)
        return res.optString("text")
    }
}

/** Whether AI features can run now: signed in, and with an own key when "use my own AI" is on. */
object Ai {
    fun ready(context: Context): Boolean =
        Account.isLoggedIn(context) && (!AiSettings(context).personal || AiSettings(context).hasKey())
}
