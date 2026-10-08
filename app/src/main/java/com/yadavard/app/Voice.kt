package com.yadavard.app

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Speech-to-text intent that prefers Google's recognizer, so phones whose default handler is a
 * vendor assistant (for example Mi AI on Xiaomi) still get a plain dictation screen.
 */
fun speechIntent(context: Context): Intent {
    val base = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val lang = if (AppDisplay.language == AppLanguage.FA) "fa-IR" else "en-US"
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        putExtra(RecognizerIntent.EXTRA_PROMPT, t("بگو چی رو و کی یادت بندازم", "Say what and when"))
    }
    val google = Intent(base).setPackage("com.google.android.googlequicksearchbox")
    return if (google.resolveActivity(context.packageManager) != null) google else base
}

private enum class Stage { RECORDING, CONVERTING, THINKING, RESULT, ERROR }

/**
 * The Yadar assistant. Starts by listening (or with [initialText] typed by the user), sends the request to
 * OpenRouter with the user's reminders as context, speaks/shows the reply and confirms proposed actions.
 * The conversation can continue with the mic button; earlier turns are kept as context.
 */
@Composable
fun AssistantDialog(initialText: String?, onDismiss: () -> Unit, onApply: (List<AssistantAction>) -> Unit,
                    onEdit: (AssistantAction, List<AssistantAction>) -> Unit, onText: (String) -> Unit, onGoogle: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    val speaker = remember { Speaker(context) }
    var stage by remember { mutableStateOf(if (initialText != null) Stage.THINKING else Stage.RECORDING) }
    var seconds by remember { mutableIntStateOf(0) }
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf(initialText.orEmpty()) }
    var result by remember { mutableStateOf<AssistantResult?>(null) }
    var turn by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf(initialText) }
    var stopRequested by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    val speak = remember { Prefs.speakReplies(context) }

    DisposableEffect(Unit) { onDispose { recorder.discard(); speaker.shutdown() } }

    LaunchedEffect(turn) {
        val ai = OpenRouter(context)
        val text: String
        val pendingTyped = typed
        if (pendingTyped != null) {
            typed = null
            text = pendingTyped
        } else {
            stage = Stage.RECORDING; seconds = 0; stopRequested = false; speaker.stop()
            try { recorder.start() } catch (e: Exception) {
                error = t("میکروفون در دسترس نیست: ", "Microphone unavailable: ") + (e.message ?: ""); stage = Stage.ERROR; return@LaunchedEffect
            }
            val started = System.currentTimeMillis()
            while (!stopRequested && seconds < 90) {
                delay(80)
                level = recorder.level
                seconds = ((System.currentTimeMillis() - started) / 1000).toInt()
            }
            val file = recorder.stop()
            if (file == null) { error = t("صدا خیلی کوتاه بود؛ دوباره امتحان کن.", "Too short — try again."); stage = Stage.ERROR; return@LaunchedEffect }
            stage = Stage.CONVERTING
            text = try { ai.transcribe(file) } catch (e: Exception) {
                error = t("تبدیل صدا انجام نشد. کلید را در تنظیمات با «آزمایش کلید» بررسی کن.\n", "Could not transcribe. Check the key with “Test key” in settings.\n") + (e.message ?: "")
                stage = Stage.ERROR; return@LaunchedEffect
            }
        }
        heard = text
        stage = Stage.THINKING
        val r = try { ai.assist(text, history.toList()) } catch (e: Exception) {
            error = t("دستیار پاسخ نداد: ", "The assistant did not answer: ") + (e.message ?: ""); stage = Stage.ERROR; return@LaunchedEffect
        }
        history.add(text to r.reply)
        result = r
        stage = Stage.RESULT
        if (speak) speaker.speak(r.reply)
    }

    val scale by animateFloatAsState(1f + level * 0.6f, label = "level")
    Dialog(onDismissRequest = { if (stage != Stage.CONVERTING && stage != Stage.THINKING) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(16.dp).fillMaxWidth().widthIn(max = 480.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(when (stage) {
                        Stage.RECORDING -> t("در حال گوش دادن…", "Listening…")
                        Stage.CONVERTING -> t("در حال تبدیل صدا به متن…", "Transcribing…")
                        Stage.THINKING -> t("در حال فکر کردن…", "Thinking…")
                        Stage.RESULT -> t("دستیار یادار", "Yadar assistant")
                        Stage.ERROR -> t("مشکلی پیش آمد", "Something went wrong")
                    }, style = MaterialTheme.typography.titleMedium)
                }
                if (stage != Stage.RESULT) Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    if (stage == Stage.RECORDING) Box(Modifier.size(104.dp).scale(scale).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
                    if (stage == Stage.CONVERTING || stage == Stage.THINKING)
                        CircularProgressIndicator(Modifier.size(104.dp), strokeWidth = 4.dp)
                    Box(Modifier.size(78.dp).clip(CircleShape).background(
                        if (stage == Stage.ERROR) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center) {
                        Icon(if (stage == Stage.THINKING) Icons.Rounded.AutoAwesome else Icons.Rounded.Mic, null, Modifier.size(38.dp),
                            tint = if (stage == Stage.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary)
                    }
                }
                if (heard.isNotBlank() && stage != Stage.RECORDING) Bubble(heard, mine = true)
                when (stage) {
                    Stage.RECORDING -> {
                        Text(n("%d:%02d".format(seconds / 60, seconds % 60)), style = MaterialTheme.typography.titleLarge)
                        Text(t("مثلاً: «فردا ساعت ۸ دکتر» • «جلسه رو ببر جمعه ساعت ۵» • «قسط رو حذف کن» • «این هفته چی دارم؟»",
                            "e.g. “doctor tomorrow 8am” • “move the meeting to Friday 5pm” • “delete the rent one” • “what do I have this week?”"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { recorder.discard(); onDismiss() }) {
                                Icon(Icons.Rounded.Close, null); Spacer(Modifier.width(4.dp)); Text(t("لغو", "Cancel"))
                            }
                            Button(onClick = { stopRequested = true }) {
                                Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(4.dp)); Text(t("تمام شد", "Done"))
                            }
                        }
                    }
                    Stage.CONVERTING, Stage.THINKING -> Unit
                    Stage.RESULT -> {
                        val r = result
                        if (r != null) {
                            if (r.reply.isNotBlank()) Bubble(r.reply, mine = false, onSpeak = if (speak && speaker.supported) ({ speaker.speak(r.reply) }) else null)
                            if (r.actions.isNotEmpty()) ActionsConfirmContent(r.actions, onApply, onEdit, onCancel = onDismiss)
                            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = onDismiss) { Text(t("بستن", "Close")) }
                                Button(onClick = { turn++ }) {
                                    Icon(Icons.Rounded.Mic, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("ادامهٔ گفتگو", "Continue"))
                                }
                            }
                        }
                    }
                    Stage.ERROR -> {
                        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss) { Text(t("بستن", "Close")) }
                            Button(onClick = { if (heard.isNotBlank() && error.startsWith(t("دستیار", "The assistant"))) typed = heard; turn++ }) { Text(t("دوباره", "Retry")) }
                        }
                        if (heard.isNotBlank()) TextButton(onClick = { onText(heard) }) { Text(t("ثبت متن به‌صورت یادآوری ساده", "Use the text as a simple reminder")) }
                        else TextButton(onClick = onGoogle) { Text(t("استفاده از تشخیص گفتار Google", "Use Google dictation instead")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean, onSpeak: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
        Row(Modifier.widthIn(max = 380.dp).clip(RoundedCornerShape(18.dp))
            .background(if (mine) scheme.surfaceVariant else scheme.primaryContainer).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (mine) Icons.Rounded.RecordVoiceOver else Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp),
                tint = if (mine) scheme.onSurfaceVariant else scheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (mine) scheme.onSurfaceVariant else scheme.onPrimaryContainer,
                modifier = Modifier.weight(1f, fill = false))
            if (onSpeak != null) IconButton(onClick = onSpeak, modifier = Modifier.size(30.dp)) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, t("پخش", "Play"), Modifier.size(18.dp), tint = scheme.primary)
            }
        }
    }
}
