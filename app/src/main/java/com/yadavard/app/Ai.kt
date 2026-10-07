package com.yadavard.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The OpenRouter key is typed on the phone, encrypted with Android Keystore and never leaves it except to OpenRouter. */
class AiSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("local_ai_settings", Context.MODE_PRIVATE)
    private val alias = "yadar_openrouter_key_v1"

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    fun saveKey(value: String) {
        if (value.isBlank()) { prefs.edit().remove("key").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val encrypted = cipher.doFinal(value.trim().toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("key", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
    }

    fun key(): String? {
        val stored = prefs.getString("key", null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
        } catch (_: Exception) { null }
    }

    fun hasKey(): Boolean = !key().isNullOrBlank()

    var model: String
        get() = prefs.getString("text_model", null)?.takeIf { it.isNotBlank() } ?: "openai/gpt-4o-mini"
        set(value) { prefs.edit().putString("text_model", value.trim()).apply() }
}

class OpenRouter(private val context: Context) {
    private val settings = AiSettings(context)
    data class Model(val id: String, val name: String, val free: Boolean)

    private fun fa() = Prefs.language(context) == AppLanguage.FA
    private fun requireKey(): String = settings.key()?.takeIf { it.isNotBlank() }
        ?: error(if (fa()) "ابتدا کلید OpenRouter را در تنظیمات وارد کنید" else "Enter an OpenRouter key in settings first")

    private fun read(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error((if (fa()) "خطای OpenRouter" else "OpenRouter error") + " ($code): " + body.take(200))
        return body
    }

    suspend fun models(): List<Model> = withContext(Dispatchers.IO) {
        val conn = (URL("https://openrouter.ai/api/v1/models").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer ${requireKey()}")
        }
        try {
            val data = JSONObject(read(conn)).getJSONArray("data")
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val outputs = item.optJSONObject("architecture")?.optJSONArray("output_modalities")
                    if (outputs != null && (0 until outputs.length()).none { outputs.optString(it) == "text" }) continue
                    val pricing = item.optJSONObject("pricing")
                    val free = id.endsWith(":free") || (pricing != null && pricing.length() > 0 &&
                        pricing.keys().asSequence().all { pricing.optString(it).toDoubleOrNull() == 0.0 })
                    add(Model(id, item.optString("name", id).ifBlank { id }, free))
                }
            }.sortedWith(compareByDescending<Model> { it.free }.thenBy { it.name.lowercase() })
        } finally { conn.disconnect() }
    }

    /** Parses free text into a reminder draft. The result is shown to the user before it is saved. */
    suspend fun parse(text: String): Reminder = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val calendar = Prefs.calendar(context)
        val today = Dates.formatDate(now.toLocalDate(), CalendarSystem.PERSIAN, AppLanguage.EN)
        val prompt = """You turn a user's sentence into ONE reminder. Return ONLY a JSON object, no prose.
Current local time: $now (time zone $zone). Today in the Persian calendar: $today. Default calendar: $calendar.
The user may write Persian or English. Interpret ambiguous dates with the default calendar; 14xx years are Persian, 20xx Gregorian.
Fields:
- title: short task in the user's language, without date/time words
- note: optional extra details
- first_at: ISO-8601 local date-time WITH offset of the first due moment (must be in the future)
- unit: NONE | HOURS | DAYS | WEEKS | MONTHS | YEARS | AFTER_DONE_DAYS
- every: integer >= 1
- weekdays: array of ISO weekday numbers (1=Monday..7=Sunday) for WEEKS
- month_day: 1..31 for monthly/yearly on a fixed day, -1 for the last day, 0 otherwise
- calendar: PERSIAN | GREGORIAN (calendar for monthly/yearly rules)
- category: GENERAL | PERSONAL | WORK | HEALTH | BILLS | BIRTHDAY | SHOPPING | STUDY
- important: boolean
- lead_minutes: minutes of advance notice, 0 if none
Rules: "every 20 days" = DAYS/every=20; "20th of every month" = MONTHS/month_day=20; "10 days after I do it" = AFTER_DONE_DAYS.
Default times: morning 09:00, noon 12:00, afternoon 16:00, evening 18:00, night 21:00; a date without time = 09:00.
No date: today if the time is still ahead, otherwise tomorrow.
Sentence: $text"""
        val body = JSONObject().put("model", settings.model).put("temperature", 0)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer ${requireKey()}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Title", "Yadar")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val raw = JSONObject(read(conn)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val json = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
            val due = ZonedDateTime.parse(json.getString("first_at")).toInstant().toEpochMilli()
            val unit = runCatching { RepeatUnit.valueOf(json.optString("unit", "NONE").uppercase()) }.getOrDefault(RepeatUnit.NONE)
            var mask = 0
            json.optJSONArray("weekdays")?.let { days -> for (i in 0 until days.length()) days.optInt(i).takeIf { it in 1..7 }?.let { mask = mask or (1 shl (it - 1)) } }
            val title = json.optString("title").trim().take(200).ifBlank { text.trim().take(200) }
            Recurrence.align(Reminder(title = title, note = json.optString("note").trim(),
                category = runCatching { Category.valueOf(json.optString("category", "GENERAL").uppercase()) }.getOrDefault(Category.GENERAL),
                important = json.optBoolean("important"),
                firstAt = due, unit = unit, every = json.optInt("every", 1).coerceIn(1, 10_000),
                weekdays = mask, monthDay = json.optInt("month_day", 0).coerceIn(-1, 31),
                leadMinutes = json.optInt("lead_minutes", 0).coerceIn(0, 525_600), zone = zone.id,
                calendar = runCatching { CalendarSystem.valueOf(json.optString("calendar", calendar.name).uppercase()) }.getOrDefault(calendar),
                alertStyle = Prefs.defaultAlert(context)))
        } finally { conn.disconnect() }
    }
}
