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

private enum class VoiceStage { RECORDING, CONVERTING, UNDERSTANDING, CONFIRM, ERROR }

/**
 * Records inside the app and uses OpenRouter to transcribe and understand the voice note.
 * [onDraft] receives a ready reminder draft; [onText] gets the transcript when only text is available.
 */
@Composable
fun VoiceDialog(onDismiss: () -> Unit, onSave: (List<Reminder>) -> Unit, onEdit: (Reminder, List<Reminder>) -> Unit,
                onText: (String) -> Unit, onGoogle: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var stage by remember { mutableStateOf(VoiceStage.RECORDING) }
    var seconds by remember { mutableIntStateOf(0) }
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var stopRequested by remember { mutableStateOf(false) }
    var drafts by remember { mutableStateOf<List<Reminder>>(emptyList()) }

    DisposableEffect(Unit) { onDispose { recorder.discard() } }

    LaunchedEffect(attempt) {
        stage = VoiceStage.RECORDING; seconds = 0; stopRequested = false
        try { recorder.start() } catch (e: Exception) {
            error = t("میکروفون در دسترس نیست: ", "Microphone unavailable: ") + (e.message ?: ""); stage = VoiceStage.ERROR; return@LaunchedEffect
        }
        val started = System.currentTimeMillis()
        while (!stopRequested && seconds < 90) {
            delay(80)
            level = recorder.level
            seconds = ((System.currentTimeMillis() - started) / 1000).toInt()
        }
        val file = recorder.stop()
        if (file == null) { error = t("صدا خیلی کوتاه بود؛ دوباره امتحان کن.", "Too short — try again."); stage = VoiceStage.ERROR; return@LaunchedEffect }
        stage = VoiceStage.CONVERTING
        val ai = OpenRouter(context)
        val text = try { ai.transcribe(file) } catch (e: Exception) {
            error = t("تبدیل صدا انجام نشد. کلید را در تنظیمات با «آزمایش کلید» بررسی کن.\n", "Could not transcribe. Check the key with “Test key” in settings.\n") + (e.message ?: ""); stage = VoiceStage.ERROR; return@LaunchedEffect
        }
        transcript = text
        stage = VoiceStage.UNDERSTANDING
        val found = try { ai.parseMany(text) } catch (_: Exception) { emptyList() }
        if (found.isEmpty()) onText(text) else { drafts = found; stage = VoiceStage.CONFIRM }
    }

    val scale by animateFloatAsState(1f + level * 0.6f, label = "level")
    Dialog(onDismissRequest = { if (stage != VoiceStage.CONVERTING && stage != VoiceStage.UNDERSTANDING) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(16.dp).fillMaxWidth().widthIn(max = 480.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(when (stage) {
                    VoiceStage.RECORDING -> t("در حال گوش دادن…", "Listening…")
                    VoiceStage.CONVERTING -> t("در حال تبدیل صدا به متن…", "Transcribing…")
                    VoiceStage.UNDERSTANDING -> t("در حال فهمیدن یادآوری…", "Understanding…")
                    VoiceStage.CONFIRM -> t("این را فهمیدم ✓", "Here's what I understood ✓")
                    VoiceStage.ERROR -> t("مشکلی پیش آمد", "Something went wrong")
                }, style = MaterialTheme.typography.titleMedium)
                if (stage != VoiceStage.CONFIRM) Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    if (stage == VoiceStage.RECORDING) Box(Modifier.size(110.dp).scale(scale).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
                    if (stage == VoiceStage.CONVERTING || stage == VoiceStage.UNDERSTANDING)
                        CircularProgressIndicator(Modifier.size(110.dp), strokeWidth = 4.dp)
                    Box(Modifier.size(82.dp).clip(CircleShape).background(
                        if (stage == VoiceStage.ERROR) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Mic, null, Modifier.size(40.dp),
                            tint = if (stage == VoiceStage.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary)
                    }
                }
                when (stage) {
                    VoiceStage.RECORDING -> {
                        Text(n("%d:%02d".format(seconds / 60, seconds % 60)), style = MaterialTheme.typography.titleLarge)
                        Text(t("مثلاً: «فردا ساعت ۸ صبح به دکتر زنگ بزنم»", "e.g. “call the doctor tomorrow at 8am”"),
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
                    VoiceStage.CONVERTING, VoiceStage.UNDERSTANDING -> {
                        if (transcript.isNotBlank()) Text("«$transcript»", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                    VoiceStage.CONFIRM -> DraftConfirmContent(transcript, drafts, onSave, onEdit, onDismiss)
                    VoiceStage.ERROR -> {
                        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss) { Text(t("بستن", "Close")) }
                            Button(onClick = { attempt++ }) { Text(t("دوباره", "Retry")) }
                        }
                        TextButton(onClick = onGoogle) { Text(t("استفاده از تشخیص گفتار Google", "Use Google dictation instead")) }
                    }
                }
            }
        }
    }
}
