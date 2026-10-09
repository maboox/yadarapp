package com.yadavard.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Where the assistant's bubbles are drawn: inside the app, as a floating overlay, or on the transparent screen. */
enum class BubbleHost { APP, OVERLAY, ACTIVITY }

/**
 * One assistant request at a time, independent of any screen, so the user can leave while it is transcribing
 * and thinking. Screens and the overlay service only render [state].
 */
object AssistantSession {
    enum class Stage { IDLE, TRANSCRIBING, THINKING, RESULT, DONE, ERROR }

    data class State(
        val stage: Stage = Stage.IDLE,
        val host: BubbleHost = BubbleHost.APP,
        val heard: String = "",
        val reply: String = "",
        val actions: List<AssistantAction> = emptyList(),
        val message: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val history = mutableListOf<Pair<String, String>>()
    private var lastAudio: File? = null

    val active: Boolean get() = _state.value.stage != Stage.IDLE

    fun startAudio(context: Context, file: File, host: BubbleHost) = start(context.applicationContext, file, null, host)
    fun startText(context: Context, text: String, host: BubbleHost) = start(context.applicationContext, null, text, host)

    private fun start(app: Context, file: File?, text: String?, host: BubbleHost) {
        job?.cancel()
        if (_state.value.stage == Stage.IDLE) history.clear()
        _state.value = State(stage = if (file != null) Stage.TRANSCRIBING else Stage.THINKING, host = host, heard = text.orEmpty())
        job = scope.launch {
            val ai = OpenRouter(app)
            val heard = if (file != null) {
                // Keep a copy so "retry" can resend the same recording.
                val keep = runCatching { File(app.cacheDir, "assistant-last.wav").also { file.copyTo(it, overwrite = true) } }.getOrNull()
                lastAudio = keep
                try { ai.transcribe(file) } catch (e: Exception) {
                    fail(t("تبدیل صدا انجام نشد. ", "Could not transcribe. ") + (e.message ?: "")); return@launch
                }
            } else text.orEmpty()
            _state.value = _state.value.copy(stage = Stage.THINKING, heard = heard)
            val result = try { ai.assist(heard, history.toList()) } catch (e: Exception) {
                fail(t("دستیار پاسخ نداد. ", "The assistant did not answer. ") + (e.message ?: "")); return@launch
            }
            history.add(heard to result.reply)
            _state.value = _state.value.copy(stage = Stage.RESULT, reply = result.reply, actions = result.actions)
            if (result.actions.isEmpty()) {
                // Answers to questions stay for a while, then the bubble goes away by itself.
                delay(30_000)
                if (_state.value.stage == Stage.RESULT && _state.value.actions.isEmpty()) dismiss()
            }
        }
    }

    private fun fail(message: String) { _state.value = _state.value.copy(stage = Stage.ERROR, message = message) }

    fun retry(context: Context) {
        val s = _state.value
        val audio = lastAudio
        when {
            s.heard.isNotBlank() -> start(context.applicationContext, null, s.heard, s.host)
            audio != null && audio.exists() -> {
                val copy = File(context.cacheDir, "assistant-retry.wav").also { audio.copyTo(it, overwrite = true) }
                start(context.applicationContext, copy, null, s.host)
            }
            else -> dismiss()
        }
    }

    /** Applies [list] (some or all proposed actions) and keeps the rest on screen. */
    fun apply(context: Context, list: List<AssistantAction>) {
        if (list.isEmpty()) return
        val app = context.applicationContext
        val remaining = _state.value.actions.filter { a -> list.none { it === a } }
        _state.value = _state.value.copy(actions = remaining)
        scope.launch {
            withContext(Dispatchers.IO) { Assistant.apply(app, list) }
            if (remaining.isEmpty()) finishWith(Assistant.summary(list))
        }
    }

    fun remove(action: AssistantAction) {
        val remaining = _state.value.actions.filter { it !== action }
        _state.value = _state.value.copy(actions = remaining)
        if (remaining.isEmpty()) dismiss()
    }

    /** Applies the other actions and opens the full form for [action]. */
    fun edit(context: Context, action: AssistantAction) {
        val app = context.applicationContext
        val others = _state.value.actions.filter { it !== action }
        val (original, draft) = when (action) {
            is AssistantAction.Update -> action.before to action.after
            is AssistantAction.Create -> null to action.draft
            else -> return
        }
        scope.launch {
            withContext(Dispatchers.IO) { Assistant.apply(app, others) }
            dismiss()
            openEditor(app, original, draft)
        }
    }

    private suspend fun finishWith(message: String) {
        _state.value = _state.value.copy(stage = Stage.DONE, message = message, actions = emptyList())
        delay(1_800)
        if (_state.value.stage == Stage.DONE) dismiss()
    }

    fun dismiss() {
        job?.cancel()
        _state.value = State(host = _state.value.host)
    }

    fun openEditor(context: Context, original: Reminder?, draft: Reminder) {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            action = WidgetCommands.EDIT_DRAFT
            putExtra(WidgetCommands.EXTRA_DRAFT, Backup.toJson(draft).toString())
            putExtra(WidgetCommands.EXTRA_ID, original?.id ?: 0L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
    }

    /**
     * Offline fallback for a sentence: saves it when the time is clear, otherwise opens the form.
     * Returns true when it was saved directly.
     */
    fun saveSimple(context: Context, text: String): Boolean {
        val app = context.applicationContext
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val cal = Prefs.calendar(app)
        val result = runCatching { QuickParser.parse(text, now, zone, cal, Categories.customPairs(app)) }
            .getOrElse { QuickResult(title = text.trim(), at = null) }
        val r = result.toReminder(zone, cal, now)?.let {
            it.copy(alertStyle = if (it.alertStyle == AlertStyle.ALARM) AlertStyle.ALARM else Prefs.defaultAlert(app),
                leadMinutes = Prefs.defaultLead(app))
        }
        if (r != null && result.understood && r.nextAt > now && result.title.isNotBlank()) {
            scope.launch {
                withContext(Dispatchers.IO) { Repo.save(app, r) }
                Toast.makeText(app, t("ثبت شد: ", "Saved: ") + r.title + " • " +
                    Dates.formatDateTime(r.nextAt, zone, cal, AppDisplay.language, withYear = false), Toast.LENGTH_LONG).show()
            }
            return true
        }
        openEditor(app, null, r ?: Reminder(title = result.title, firstAt = Dates.at(LocalDate.now(zone).plusDays(1), 9, 0, zone),
            zone = zone.id, calendar = cal, alertStyle = Prefs.defaultAlert(app), category = result.category))
        return false
    }
}
