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

    var audioModel: String
        get() = prefs.getString("audio_model", null)?.takeIf { it.isNotBlank() } ?: FALLBACK_AUDIO_MODEL
        set(value) { prefs.edit().putString("audio_model", value.trim()).apply() }
}

/**
 * Records the user's voice as 16 kHz mono WAV inside the app, so no system assistant (such as Mi AI) is involved.
 * WAV works both with transcription models and as audio input for multimodal chat models.
 */
class VoiceRecorder(private val context: Context) {
    private val rate = 16_000
    private var record: android.media.AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = java.io.ByteArrayOutputStream()
    @Volatile private var running = false
    @Volatile var level = 0f
        private set
    val active get() = running

    @android.annotation.SuppressLint("MissingPermission")
    fun start() {
        discard()
        val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT)
        val r = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6_400) * 2)
        if (r.state != android.media.AudioRecord.STATE_INITIALIZED) { r.release(); error("microphone unavailable") }
        synchronized(pcm) { pcm.reset() }
        r.startRecording()
        record = r
        running = true
        thread = Thread {
            val buffer = ByteArray(3_200)
            while (running) {
                val n = r.read(buffer, 0, buffer.size)
                if (n <= 0) continue
                synchronized(pcm) { if (pcm.size() < rate * 2 * 120) pcm.write(buffer, 0, n) }
                var peak = 0
                var i = 0
                while (i + 1 < n) { val v = kotlin.math.abs((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)); if (v > peak) peak = v; i += 2 }
                level = (peak / 32768f).coerceIn(0f, 1f)
            }
        }.also { it.start() }
    }

    /** Stops and returns a WAV file, or null if the recording was too short. */
    fun stop(): java.io.File? {
        if (!running) return null
        running = false
        thread?.join(500); thread = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        if (data.size < rate) return null // shorter than half a second
        val file = java.io.File.createTempFile("voice-", ".wav", context.cacheDir)
        file.outputStream().use { out ->
            fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun short(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.write("RIFF".toByteArray()); int(36 + data.size); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
            out.write("data".toByteArray()); int(data.size); out.write(data)
        }
        return file
    }

    fun discard() {
        running = false
        thread?.join(300); thread = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
        synchronized(pcm) { pcm.reset() }
    }
}

/** Multimodal model that accepts audio input on OpenRouter; used by default for voice. */
const val FALLBACK_AUDIO_MODEL = "google/gemini-2.5-flash"

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

    suspend fun models(transcription: Boolean = false): List<Model> = withContext(Dispatchers.IO) {
        val url = "https://openrouter.ai/api/v1/models" + if (transcription) "?output_modalities=all" else ""
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
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
                    val inputs = item.optJSONObject("architecture")?.optJSONArray("input_modalities")
                    fun has(a: JSONArray?, v: String) = a != null && (0 until a.length()).any { a.optString(it) == v }
                    if (transcription) { if (!has(inputs, "audio") && !has(outputs, "transcription")) continue }
                    else if (outputs != null && !has(outputs, "text")) continue
                    val pricing = item.optJSONObject("pricing")
                    val free = id.endsWith(":free") || (pricing != null && pricing.length() > 0 &&
                        pricing.keys().asSequence().all { pricing.optString(it).toDoubleOrNull() == 0.0 })
                    add(Model(id, item.optString("name", id).ifBlank { id }, free))
                }
            }.sortedWith(compareByDescending<Model> { it.free }.thenBy { it.name.lowercase() })
        } finally { conn.disconnect() }
    }

    /**
     * Turns a WAV recording into text. Multimodal chat models get the audio directly; transcription models
     * (for example Whisper) use the transcription endpoint, falling back to a multimodal model if that fails.
     */
    suspend fun transcribe(file: java.io.File): String = withContext(Dispatchers.IO) {
        try {
            val model = settings.audioModel
            val speechOnly = listOf("whisper", "transcribe", "speech").any { model.contains(it, ignoreCase = true) }
            val text = if (speechOnly) runCatching { transcriptionEndpoint(file, model) }
                .getOrElse { chatTranscribe(file, FALLBACK_AUDIO_MODEL) }
            else chatTranscribe(file, model)
            text.trim().ifBlank { error(if (fa()) "صدایی تشخیص داده نشد" else "No speech recognized") }
        } finally { file.delete() }
    }

    private fun transcriptionEndpoint(file: java.io.File, model: String): String {
        val boundary = "Yadar${System.currentTimeMillis()}"
        val conn = (URL("https://openrouter.ai/api/v1/audio/transcriptions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer ${requireKey()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("X-Title", "Yadar")
        }
        try {
            conn.outputStream.use { out ->
                fun field(name: String, value: String) =
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                field("model", model)
                field("language", if (fa()) "fa" else "en")
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            return JSONObject(read(conn)).optString("text")
        } finally { conn.disconnect() }
    }

    private fun chatTranscribe(file: java.io.File, model: String): String {
        val audio = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val instruction = "Transcribe this voice note exactly as spoken, in its original language (usually Persian). " +
            "Reply with the transcript only, no quotes or explanations."
        val body = JSONObject().put("model", model).put("temperature", 0)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray()
                .put(JSONObject().put("type", "text").put("text", instruction))
                .put(JSONObject().put("type", "input_audio").put("input_audio", JSONObject().put("data", audio).put("format", "wav"))))))
        val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer ${requireKey()}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Title", "Yadar")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            return JSONObject(read(conn)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
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
