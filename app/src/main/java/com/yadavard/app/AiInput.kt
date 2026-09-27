package com.yadavard.app

import android.content.Context
import android.media.MediaRecorder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The user's key is entered on-device, encrypted with Android Keystore, and never bundled in the APK. */
class AiSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("local_ai_settings", Context.MODE_PRIVATE)
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
    var textModel: String
        get() = prefs.getString("text_model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini"
        set(value) { prefs.edit().putString("text_model", value.trim()).apply() }
    var audioModel: String
        get() = prefs.getString("audio_model", "openai/whisper-large-v3") ?: "openai/whisper-large-v3"
        set(value) { prefs.edit().putString("audio_model", value.trim()).apply() }
}

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    fun start() {
        val target = File.createTempFile("voice-", ".m4a", context.cacheDir)
        @Suppress("DEPRECATION")
        val newRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64000)
            setAudioSamplingRate(16000)
            setOutputFile(target.absolutePath)
            prepare()
            start()
        }
        file = target; recorder = newRecorder
    }
    fun stop(): File? {
        val output = file
        try { recorder?.stop() } catch (_: RuntimeException) { output?.delete(); return null }
        finally { recorder?.release(); recorder = null; file = null }
        return output?.takeIf { it.length() > 0 }
    }
    fun discard() { try { recorder?.stop() } catch (_: Exception) {} ; recorder?.release(); recorder = null; file?.delete(); file = null }
}

class OpenRouter(private val context: Context) {
    private val settings = AiSettings(context)
    data class Model(val id: String, val name: String, val free: Boolean)
    /** Model availability and prices change; fetch the live catalog instead of shipping fixed names. */
    suspend fun models(transcription: Boolean): List<Model> = withContext(Dispatchers.IO) {
        val key = settings.key() ?: error("ابتدا کلید OpenRouter را در تنظیمات ذخیره کن")
        val url = "https://openrouter.ai/api/v1/models" + if (transcription) "?output_modalities=transcription" else ""
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $key")
        }
        try {
            val entries = JSONObject(result(conn)).getJSONArray("data")
            buildList {
                for (i in 0 until entries.length()) {
                    val item = entries.getJSONObject(i)
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    val architecture = item.optJSONObject("architecture")
                    val outputs = architecture?.optJSONArray("output_modalities")
                    val inputs = architecture?.optJSONArray("input_modalities")
                    fun has(array: JSONArray?, target: String): Boolean = array != null &&
                        (0 until array.length()).any { array.optString(it) == target }
                    if (transcription && !has(outputs, "transcription")) continue
                    if (!transcription && outputs != null && !has(outputs, "text")) continue
                    if (!transcription && inputs != null && !has(inputs, "text")) continue
                    val pricing = item.optJSONObject("pricing")
                    val allZero = pricing != null && pricing.length() > 0 &&
                        pricing.keys().asSequence().all { key -> pricing.optString(key).toDoubleOrNull() == 0.0 }
                    add(Model(id, item.optString("name", id).ifBlank { id }, id.endsWith(":free") || allZero))
                }
            }.sortedWith(compareByDescending<Model> { it.free }.thenBy { it.name.lowercase() })
        } finally { conn.disconnect() }
    }
    private fun connection(endpoint: String) = (URL("https://openrouter.ai/api/v1/$endpoint").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 90_000
        setRequestProperty("Authorization", "Bearer ${settings.key() ?: error("ابتدا کلید OpenRouter را در تنظیمات وارد کنید")}")
    }
    private fun result(conn: HttpURLConnection): String {
        val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (conn.responseCode !in 200..299) throw IllegalStateException("خطای OpenRouter (${conn.responseCode}): ${body.take(250)}")
        return body
    }
    suspend fun transcribe(file: File): String = withContext(Dispatchers.IO) {
        val boundary = "Yadar${System.currentTimeMillis()}"
        val conn = connection("audio/transcriptions").apply { setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary") }
        conn.outputStream.use { output ->
            fun part(name: String, value: String) {
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            part("model", settings.audioModel)
            output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(output) }
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }
        try { JSONObject(result(conn)).getString("text") } finally { conn.disconnect(); file.delete() }
    }
    suspend fun parse(text: String): Reminder = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val prompt = """You are a Persian reminder parser. Return ONLY a JSON object. Current instant: $now.
User's time zone: $zone. User may speak Persian or English. Dates are Jalali unless explicitly Gregorian.
Fields: title (short Persian task), note (optional), first_at (ISO 8601 instant WITH offset), unit (NONE/DAYS/WEEKS/MONTHS/YEARS/AFTER_DONE_DAYS), every (integer >=1), weekdays (array of ISO weekday numbers 1=Monday..7=Sunday), month_day (Jalali 1..31 or 0), lead_minutes (integer >=0), assumption (brief Persian explanation of any inferred time).
Interpret '20th of every month' as MONTHS/month_day=20; 'every 20 days' as DAYS/every=20; 'start in 10 days, every 20 days' means first_at in 10 days. 'Every 3 months on the 5th' means first_at is the next 5th Jalali date that is at/after requested start; month_day=5, every=3. Return the first actual due date, not the anchor date.
If only a day is given, default to 10:00. Morning=09:00, afternoon=16:00, evening=18:00, night=21:00. If no date is stated, default to today when time is in the future, otherwise tomorrow. Never invent location-triggered automation. Preserve any condition in note.
User request: $text"""
        val request = JSONObject().put("model", settings.textModel).put("temperature", 0)
            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val conn = connection("chat/completions").apply { setRequestProperty("Content-Type", "application/json") }
        conn.outputStream.use { it.write(request.toString().toByteArray(StandardCharsets.UTF_8)) }
        try {
            val raw = JSONObject(result(conn)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val value = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
            val due = Instant.parse(value.getString("first_at").let { ZonedDateTime.parse(it).toInstant().toString() }).toEpochMilli()
            require(due > System.currentTimeMillis() - 60_000) { "زمان برداشت‌شده در گذشته است؛ لطفاً آن را روشن‌تر بگویید" }
            val unit = runCatching { RepeatUnit.valueOf(value.optString("unit", "NONE")) }.getOrDefault(RepeatUnit.NONE)
            val days = value.optJSONArray("weekdays")
            var mask = 0
            if (days != null) for (i in 0 until days.length()) if (days.optInt(i) in 1..7) mask = mask or (1 shl (days.getInt(i) - 1))
            val note = listOf(value.optString("note"), value.optString("assumption").takeIf { it.isNotBlank() }?.let { "برداشت زمان: $it" } ?: "")
                .filter { it.isNotBlank() }.joinToString("\n")
            Occurrences.alignFirst(Reminder(title = value.getString("title").trim().take(180).ifBlank { error("عنوان خالی است") }, note = note,
                firstAt = due, unit = unit, every = value.optInt("every", 1).coerceIn(1, 3650), weekdays = mask,
                monthDay = value.optInt("month_day", 0).coerceIn(0, 31), leadMinutes = value.optInt("lead_minutes", 0).coerceIn(0, 525600), zone = zone.id))
        } finally { conn.disconnect() }
    }
}
