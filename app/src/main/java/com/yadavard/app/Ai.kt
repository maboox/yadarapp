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
        if (clean(value).isBlank()) { prefs.edit().remove("key").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val encrypted = cipher.doFinal(clean(value).toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("key", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
    }

    fun key(): String? {
        val stored = prefs.getString("key", null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            clean(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)).ifBlank { null }
        } catch (_: Exception) { null }
    }

    /**
     * Keys are plain ASCII. Pasting from chat apps or a Persian keyboard can add invisible characters
     * (direction marks, spaces, line breaks) or a "Bearer" prefix, which break the Authorization header.
     */
    private fun clean(value: String): String =
        value.filter { it.code in 33..126 }.removePrefix("Bearer").removePrefix("bearer")

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
const val FALLBACK_TEXT_MODEL = "openai/gpt-4o-mini"

/** Models known to understand Persian speech through audio input; shown first in the voice-model picker. */
val RECOMMENDED_AUDIO_MODELS = listOf("google/gemini-2.5-flash-lite", "google/gemini-2.5-flash",
    "google/gemini-2.5-pro", "openai/gpt-4o-audio-preview")

/** Quick, inexpensive text models; reasoning and free models can take minutes. */
val RECOMMENDED_TEXT_MODELS = listOf("google/gemini-2.5-flash-lite", "openai/gpt-4o-mini", "openai/gpt-4.1-mini", "google/gemini-2.5-flash")

/** Models known to be slow for short requests (thinking models or free queues). */
fun isSlowModel(id: String): Boolean = id.endsWith(":free") || listOf("-r1", "/r1", "thinking", "/o1", "/o3", "/o4", "qwq", "-pro")
    .any { id.contains(it, ignoreCase = true) }

class OpenRouter(private val context: Context) {
    private val settings = AiSettings(context)
    data class Model(val id: String, val name: String, val free: Boolean)

    private fun fa() = Prefs.language(context) == AppLanguage.FA
    private fun requireKey(): String = settings.key()?.takeIf { it.isNotBlank() }
        ?: error(if (fa()) "ابتدا کلید OpenRouter را در تنظیمات وارد کنید" else "Enter an OpenRouter key in settings first")

    private fun read(conn: HttpURLConnection): String {
        val code = conn.responseCode
        if (code in 300..399) error((if (fa()) "OpenRouter درخواست را جای دیگری فرستاد" else "OpenRouter redirected the request") +
            " ($code → ${conn.getHeaderField("Location")}). " + (if (fa()) "اگر VPN یا پروکسی روشن است، یک‌بار بدون آن امتحان کن." else "If a VPN or proxy is on, try without it."))
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error((if (fa()) "خطای OpenRouter" else "OpenRouter error") + " ($code): " + body.take(200))
        return body
    }

    suspend fun models(transcription: Boolean = false): List<Model> = withContext(Dispatchers.IO) {
        val url = "https://openrouter.ai/api/v1/models" + if (transcription) "?output_modalities=all" else ""
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer ${requireKey()}")
            // A redirect would silently drop the Authorization header (and turn POST into GET).
            instanceFollowRedirects = false
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
                    // Voice models must take audio in and give text out (excludes video/music generators).
                    if (transcription) { if (!(has(inputs, "audio") && (outputs == null || has(outputs, "text"))) && !has(outputs, "transcription")) continue }
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
            val speechOnly = listOf("whisper", "transcribe").any { model.contains(it, ignoreCase = true) }
            val attempts = buildList<Pair<String, () -> String>> {
                add(model to { if (speechOnly) transcriptionEndpoint(file, model) else chatTranscribe(file, model) })
                if (model != FALLBACK_AUDIO_MODEL) add(FALLBACK_AUDIO_MODEL to { chatTranscribe(file, FALLBACK_AUDIO_MODEL) })
            }
            val errors = mutableListOf<String>()
            for ((name, attempt) in attempts) {
                val text = runCatching(attempt).onFailure { errors += "$name: ${it.message}" }.getOrNull()?.trim()
                if (!text.isNullOrBlank()) return@withContext text
            }
            error(errors.joinToString("\n").ifBlank { if (fa()) "صدایی تشخیص داده نشد" else "No speech recognized" })
        } finally { file.delete() }
    }

    /** Checks the saved key against OpenRouter and returns a short description of the account. */
    suspend fun testKey(): String = withContext(Dispatchers.IO) {
        val conn = (URL("https://openrouter.ai/api/v1/key").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer ${requireKey()}")
            instanceFollowRedirects = false
        }
        try {
            val data = JSONObject(read(conn)).optJSONObject("data")
            val label = data?.optString("label").orEmpty()
            val remaining = data?.opt("limit_remaining")?.takeIf { it != JSONObject.NULL }?.toString()
            (if (fa()) "کلید معتبر است ✓" else "Key is valid ✓") + (if (label.isNotBlank()) " ($label)" else "") +
                (remaining?.let { if (fa()) " • اعتبار باقی‌مانده: $it" else " • remaining: $it" } ?: "")
        } finally { conn.disconnect() }
    }

    private fun transcriptionEndpoint(file: java.io.File, model: String): String {
        val boundary = "Yadar${System.currentTimeMillis()}"
        val conn = (URL("https://openrouter.ai/api/v1/audio/transcriptions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer ${requireKey()}")
            // A redirect would silently drop the Authorization header (and turn POST into GET).
            instanceFollowRedirects = false
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
        val body = JSONObject().put("model", model).put("temperature", 0).put("max_tokens", 1000)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray()
                .put(JSONObject().put("type", "text").put("text", instruction))
                .put(JSONObject().put("type", "input_audio").put("input_audio", JSONObject().put("data", audio).put("format", "wav"))))))
        return complete(body, 60_000)
    }

    /**
     * Sends a chat completion and returns the message text. Reasoning ("thinking") is switched off because it
     * made simple requests take minutes; models that cannot turn it off are retried with their default.
     */
    private fun complete(body: JSONObject, timeout: Int): String {
        fun send(payload: JSONObject): String {
            val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = timeout
                setRequestProperty("Authorization", "Bearer ${requireKey()}")
                // A redirect would silently drop the Authorization header (and turn POST into GET).
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Title", "Yadar")
            }
            try {
                conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
                return JSONObject(read(conn)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
            } finally { conn.disconnect() }
        }
        val fast = JSONObject(body.toString())
            .put("reasoning", JSONObject().put("enabled", false).put("exclude", true))
            .put("provider", JSONObject().put("sort", "latency"))
        return try { send(fast) } catch (e: IllegalStateException) {
            // 400 = the model rejected an option (for example mandatory reasoning); try once more without them.
            if (e.message?.contains("(400)") == true) send(body) else throw e
        }
    }

    /**
     * Turns a sentence (or a voice transcript) into one or more reminder drafts with every option the user
     * mentioned. Options the user did not mention keep the app defaults.
     */
    suspend fun parseMany(text: String): List<Reminder> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val calendar = Prefs.calendar(context)
        val today = Dates.formatDate(now.toLocalDate(), CalendarSystem.PERSIAN, AppLanguage.EN)
        val prompt = """You turn what the user said into reminders. The user may mention SEVERAL separate tasks; create one reminder per task.
Return ONLY JSON: {"reminders":[ ... ]} with no prose.
Current local time: $now (time zone $zone, ${now.dayOfWeek}). Today in the Persian calendar: $today. Default calendar: $calendar.
The user usually speaks Persian (or English). Interpret dates with the default calendar; 14xx years are Persian, 20xx Gregorian.
Fields of each reminder (use null for anything the user did not mention):
- title: short task in the user's language, without date/time or option words
- note: extra details the user gave, or null
- first_at: ISO-8601 local date-time WITH offset of the first due moment, in the future
- unit: NONE | HOURS | DAYS | WEEKS | MONTHS | YEARS | AFTER_DONE_DAYS
- every: integer >= 1
- weekdays: ISO weekday numbers (1=Monday..7=Sunday) for WEEKS, else []
- month_day: 1..31 for a fixed day of month, -1 for the last day, 0 otherwise
- calendar: PERSIAN | GREGORIAN for monthly/yearly rules
- until: ISO date (yyyy-MM-dd, Gregorian) when the repetition ends, or null
- category: one key from this list, the best fit for the task (medicine/pills → MEDICINE, doctor/dentist/clinic → DOCTOR, gym/running → SPORT…); GENERAL if nothing fits: ${Categories.promptList(context)}
- important: true only if the user says it is important/urgent/مهم/فوری
- alert_style: "ALARM" if the user asks for an alarm, ringing, loud sound, full screen, wake me up, زنگ, آلارم, تمام صفحه, با صدا, بیدارم کن; "NOTIFICATION" if they explicitly ask for a normal/silent notification; otherwise null
- lead_minutes: advance notice in minutes if the user asks to be told earlier (e.g. "۱۰ دقیقه قبلش خبرم کن" = 10, "یه روز قبل" = 1440), else null
- nag_minutes: if the user asks to keep reminding until done ("تا انجامش ندادم هر ۵ دقیقه یادم بنداز"), the interval in minutes (default 10), else null
Rules: "every 20 days" = DAYS/every=20; "20th of every month" = MONTHS/month_day=20; "every 8 hours" = HOURS/every=8; "10 days after I do it" = AFTER_DONE_DAYS/every=10.
Default times: morning 09:00, noon 12:00, afternoon 16:00, evening 18:00, night 21:00; a date without a time = 09:00.
No date: today if the time is still ahead, otherwise tomorrow.
User said: $text"""
        val body = JSONObject().put("model", settings.model).put("temperature", 0).put("max_tokens", 1500)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val raw = complete(body, 60_000)
        val items = parseJson(raw)
        val result = items.mapNotNull { runCatching { toReminder(it, text, zone, calendar) }.getOrNull() }
        if (result.isEmpty()) error(if (fa()) "یادآوری‌ای از این جمله پیدا نشد" else "No reminder found in that sentence")
        result
    }

    private fun chat(prompt: String, history: List<Pair<String, String>> = emptyList()): String {
        val messages = JSONArray()
        history.takeLast(3).forEach { (q, a) ->
            messages.put(JSONObject().put("role", "user").put("content", q))
            messages.put(JSONObject().put("role", "assistant").put("content", a))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        val body = JSONObject().put("model", settings.model).put("temperature", 0).put("max_tokens", 1500).put("messages", messages)
        return try { complete(body, 45_000) } catch (e: Exception) {
            // A busy or slow model (timeouts, 429, 5xx) gets one retry on a quick default model.
            val retry = e is java.io.IOException || Regex("\\((429|5\\d\\d)\\)").containsMatchIn(e.message.orEmpty())
            if (!retry || settings.model == FALLBACK_TEXT_MODEL) throw e
            complete(JSONObject(body.toString()).put("model", FALLBACK_TEXT_MODEL), 45_000)
        }
    }

    /**
     * The Yadar assistant: understands requests to create, change, delete, complete or postpone reminders
     * and questions about the schedule. [history] holds earlier (user text, assistant reply) turns.
     */
    suspend fun assist(text: String, history: List<Pair<String, String>> = emptyList()): AssistantResult = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val calendar = Prefs.calendar(context)
        val lang = Prefs.language(context)
        val today = Dates.formatDate(now.toLocalDate(), CalendarSystem.PERSIAN, AppLanguage.FA, withWeekday = true)
        val prompt = """You are «یادار», a friendly reminder assistant inside an Android app. The user talks to you (usually in Persian).
Current local time: $now (${now.dayOfWeek}), time zone $zone. Today in the Persian calendar: $today. Default calendar: $calendar.
Reply language: ${if (lang == AppLanguage.FA) "Persian (Farsi), colloquial and short" else "English, short"}.

${Assistant.context(context)}
Decide what the user wants and return ONLY JSON, no prose:
{"reply": "<what to say back, plain text, max 3 short sentences, no markdown or emojis>", "actions": [ ... ]}
Action objects:
- {"type":"create","reminder":{...}}  new reminder
- {"type":"update","id":<id>,"changes":{...only the fields that change...}}
- {"type":"delete","id":<id>}   when the user cancels/deletes/removes something
- {"type":"complete","id":<id>} when the user says it is done
- {"type":"postpone","id":<id>,"minutes":<n>} when the user says remind me later / postpone by some time
Reminder and change fields (omit or null when not mentioned):
title, note, first_at (ISO-8601 local date-time WITH offset), unit (NONE|HOURS|DAYS|WEEKS|MONTHS|YEARS|AFTER_DONE_DAYS), every,
weekdays (ISO 1=Monday..7=Sunday), month_day (1..31, -1 = last day), calendar (PERSIAN|GREGORIAN), until (yyyy-MM-dd Gregorian or null to remove),
category (one key from: ${Categories.promptList(context)}; pick the best fit for new reminders, GENERAL if none fits), important (bool),
alert_style ("ALARM" for alarm / ringing / full screen / wake me up / زنگ / آلارم / تمام صفحه; "NOTIFICATION" for a normal notification),
lead_minutes (advance notice), nag_minutes (keep reminding every N minutes until done).
Rules:
- Find existing reminders by meaning, not exact words. Use their #id. Never invent ids.
- If the request is ambiguous (several reminders could match) or unclear, do NOT act: return no actions and ask a short question in "reply".
- For questions ("what do I have this week?", "what's tomorrow?") return no actions and answer from the agenda in "reply": group by day, mention times, be concise and natural for speech.
- The user may ask for several things at once; return one action per thing.
- Rescheduling ("move it to Friday 5pm") is an update with first_at. Changing to repeat is an update with unit/every/...
- Default times: morning 09:00, noon 12:00, afternoon 16:00, evening 18:00, night 21:00; a date without time keeps the reminder's time (or 09:00 for new ones).
- "every 20 days" = DAYS/every=20; "20th of every month" = MONTHS/month_day=20; "every 8 hours" = HOURS/every=8.
- For actions, "reply" briefly says what you will do (e.g. «باشه، قرار دندانپزشکی رو برای جمعه ساعت ۵ گذاشتم»).
User: $text"""
        val raw = chat(prompt, history)
        val start = raw.indexOf('{')
        val json = JSONObject(raw.substring(start, raw.lastIndexOf('}') + 1))
        val reply = json.optString("reply").trim()
        val all = Repo.all(context).associateBy { it.id }
        val actions = mutableListOf<AssistantAction>()
        val list = json.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until list.length()) {
            val a = list.optJSONObject(i) ?: continue
            val target = all[a.optLong("id", -1)]
            runCatching {
                when (a.optString("type").lowercase()) {
                    "create" -> actions += AssistantAction.Create(toReminder(a.optJSONObject("reminder") ?: a, text, zone, calendar))
                    "update" -> if (target != null) {
                        val after = Assistant.applyChanges(context, target, a.optJSONObject("changes") ?: JSONObject())
                        if (after != target) actions += AssistantAction.Update(target, after)
                    }
                    "delete" -> if (target != null) actions += AssistantAction.Delete(target)
                    "complete", "done" -> if (target != null && !target.done) actions += AssistantAction.Complete(target)
                    "postpone", "snooze" -> if (target != null) actions += AssistantAction.Postpone(target, a.optInt("minutes", 60).coerceIn(1, 60 * 24 * 30))
                }
            }
        }
        AssistantResult(text, reply.ifBlank { if (actions.isEmpty()) (if (fa()) "متوجه نشدم؛ دوباره بگو." else "Sorry, I didn't get that.") else "" }, actions)
    }

    suspend fun parse(text: String): Reminder = parseMany(text).first()

    private fun parseJson(raw: String): List<JSONObject> {
        val start = raw.indexOfFirst { it == '{' || it == '[' }
        require(start >= 0) { "no JSON" }
        val trimmed = raw.substring(start)
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed.substring(0, trimmed.lastIndexOf(']') + 1))
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }
        val obj = JSONObject(trimmed.substring(0, trimmed.lastIndexOf('}') + 1))
        val list = obj.optJSONArray("reminders") ?: return listOf(obj)
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
    }

    private fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name).trim().takeIf { it.isNotBlank() && it != "null" }
    private fun JSONObject.int(name: String): Int? = if (isNull(name) || !has(name)) null else optString(name).trim().toDoubleOrNull()?.toInt()

    private fun toReminder(json: JSONObject, text: String, zone: ZoneId, calendar: CalendarSystem): Reminder {
        val due = ZonedDateTime.parse(json.getString("first_at")).toInstant().toEpochMilli()
        val unit = runCatching { RepeatUnit.valueOf(json.str("unit")?.uppercase() ?: "NONE") }.getOrDefault(RepeatUnit.NONE)
        var mask = 0
        json.optJSONArray("weekdays")?.let { days -> for (i in 0 until days.length()) days.optInt(i).takeIf { it in 1..7 }?.let { mask = mask or (1 shl (it - 1)) } }
        val title = json.str("title")?.take(200) ?: text.trim().take(200)
        val alert = when (json.str("alert_style")?.uppercase()) {
            "ALARM" -> AlertStyle.ALARM
            "NOTIFICATION" -> AlertStyle.NOTIFICATION
            else -> Prefs.defaultAlert(context)
        }
        val until = json.str("until")?.let { v ->
            runCatching { java.time.LocalDate.parse(v.take(10)).atTime(23, 59).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        }
        var r = Recurrence.align(Reminder(title = title, note = json.str("note").orEmpty(),
            category = Categories.resolve(context, json.str("category")),
            important = json.optBoolean("important", false),
            firstAt = due, unit = unit, every = (json.int("every") ?: 1).coerceIn(1, 10_000),
            weekdays = mask, monthDay = (json.int("month_day") ?: 0).coerceIn(-1, 31),
            untilAt = if (unit == RepeatUnit.NONE || unit == RepeatUnit.AFTER_DONE_DAYS) null else until,
            leadMinutes = (json.int("lead_minutes") ?: Prefs.defaultLead(context)).coerceIn(0, 525_600),
            nagMinutes = (json.int("nag_minutes") ?: 0).coerceIn(0, 1440), zone = zone.id,
            calendar = runCatching { CalendarSystem.valueOf(json.str("calendar")?.uppercase() ?: calendar.name) }.getOrDefault(calendar),
            alertStyle = alert))
        // A repeating rule whose first date already passed continues from its next occurrence.
        if (r.repeating && r.nextAt <= System.currentTimeMillis())
            Recurrence.nextAfter(r, System.currentTimeMillis())?.let { r = r.copy(nextAt = it) }
        return r
    }
}
