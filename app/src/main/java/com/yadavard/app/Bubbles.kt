package com.yadavard.app

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate

/** Small recording pill: live level, timer, Cancel and Send. Starts recording as soon as it appears. */
@Composable
fun VoiceCapture(onCancel: () -> Unit, onSend: (File) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var seconds by remember { mutableIntStateOf(0) }
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.discard() } }
    LaunchedEffect(Unit) {
        try { recorder.start() } catch (e: Exception) {
            error = t("میکروفون در دسترس نیست", "Microphone unavailable"); return@LaunchedEffect
        }
        val started = System.currentTimeMillis()
        while (!sending && seconds < 90) {
            delay(80)
            level = recorder.level
            seconds = ((System.currentTimeMillis() - started) / 1000).toInt()
        }
        if (!sending) { sending = true; recorder.stop()?.let(onSend) ?: onCancel() }
    }
    val scale by animateFloatAsState(1f + level * 0.7f, label = "level")
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(40.dp), color = scheme.surfaceContainerHigh, shadowElevation = 10.dp, modifier = modifier) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { recorder.discard(); onCancel() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.Close, t("لغو", "Cancel"))
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).scale(scale).clip(CircleShape).background(scheme.error.copy(alpha = 0.25f)))
                Box(Modifier.size(14.dp).clip(CircleShape).background(scheme.error))
            }
            Spacer(Modifier.width(6.dp))
            Text(if (error.isNotBlank()) error else n("%d:%02d".format(seconds / 60, seconds % 60)),
                style = MaterialTheme.typography.titleMedium, color = if (error.isNotBlank()) scheme.error else scheme.onSurface)
            Spacer(Modifier.width(12.dp))
            FilledIconButton(onClick = {
                if (!sending) { sending = true; val f = recorder.stop(); if (f != null) onSend(f) else onCancel() }
            }, enabled = error.isBlank(), modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Send, t("ارسال", "Send"))
            }
        }
    }
}

/**
 * The assistant's floating bubbles: what was heard, progress, the reply, and one bubble per proposed
 * action with Edit / Confirm. Actions that are left alone are applied after a short countdown.
 */
@Composable
fun AssistantBubbles(state: AssistantSession.State, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (state.stage == AssistantSession.Stage.IDLE) return
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.stage) {
            AssistantSession.Stage.TRANSCRIBING -> StatusBubble(t("دارم صدایت را به متن تبدیل می‌کنم…", "Turning your voice into text…"), busy = true)
            AssistantSession.Stage.THINKING -> {
                if (state.heard.isNotBlank()) UserBubble(state.heard)
                StatusBubble(t("دارم فکر می‌کنم…", "Thinking…"), busy = true)
            }
            AssistantSession.Stage.RESULT -> {
                if (state.heard.isNotBlank()) UserBubble(state.heard)
                if (state.reply.isNotBlank()) ReplyBubble(state.reply, closable = state.actions.isEmpty())
                if (state.actions.isNotEmpty()) ActionBubbles(state.actions)
            }
            AssistantSession.Stage.DONE -> StatusBubble(state.message, busy = false, icon = Icons.Rounded.CheckCircle)
            AssistantSession.Stage.ERROR -> ErrorBubble(state)
            AssistantSession.Stage.IDLE -> Unit
        }
    }
}

@Composable
private fun BubbleSurface(color: Color = MaterialTheme.colorScheme.surfaceContainerHigh, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = color, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun StatusBubble(text: String, busy: Boolean, icon: ImageVector = Icons.Rounded.AutoAwesome) {
    BubbleSurface {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
            else Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { AssistantSession.dismiss() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, t("بستن", "Close"), Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 320.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mic, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReplyBubble(text: String, closable: Boolean) {
    BubbleSurface(MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            if (closable) IconButton(onClick = { AssistantSession.dismiss() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, t("بستن", "Close"), Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ErrorBubble(state: AssistantSession.State) {
    val context = LocalContext.current
    BubbleSurface(MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.heard.isNotBlank()) Text("«${state.heard}»", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { AssistantSession.dismiss() }) { Text(t("بستن", "Close")) }
                if (state.heard.isNotBlank()) TextButton(onClick = { AssistantSession.dismiss(); AssistantSession.saveSimple(context, state.heard) }) {
                    Text(t("ثبت ساده", "Save as is"))
                }
                Button(onClick = { AssistantSession.retry(context) }) { Text(t("دوباره", "Retry")) }
            }
        }
    }
}

@Composable
private fun ActionBubbles(actions: List<AssistantAction>) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val canAuto = actions.none { it.needsFix(now) }
    val total = (6_000L + 3_000L * (actions.size - 1)).coerceAtMost(15_000L)
    var remaining by remember(actions.size) { mutableLongStateOf(total) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(actions.size, paused, canAuto) {
        if (paused || !canAuto) return@LaunchedEffect
        while (remaining > 0) { delay(50); remaining -= 50 }
        AssistantSession.apply(context, actions)
    }
    if (actions.size <= 3) actions.forEach { a -> ActionBubble(a, now, onTouched = { paused = true }) }
    else {
        // Many items (e.g. a list of birthdays): a scrollable stack plus one button for all of them.
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { a -> ActionBubble(a, now, onTouched = { paused = true }) }
        }
        val ready = actions.filter { !it.needsFix(now) }
        Button(onClick = { AssistantSession.apply(context, ready) }, enabled = ready.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(t("تأیید همه (${n(ready.size)})", "Confirm all (${ready.size})"))
        }
    }
    if (canAuto) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        LinearProgressIndicator(progress = { 1f - remaining.toFloat() / total }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(8.dp))
        Text(if (paused) t("متوقف", "Paused") else t("${n((remaining + 999) / 1000)} ثانیه", "${(remaining + 999) / 1000}s"),
            style = MaterialTheme.typography.labelMedium, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun ActionBubble(a: AssistantAction, now: Long, onTouched: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val (label, color, icon) = when (a) {
        is AssistantAction.Create -> Triple(t("یادآوری جدید", "New reminder"), scheme.primary, Icons.Rounded.AddCircle)
        is AssistantAction.Update -> Triple(t("ویرایش", "Edit"), scheme.tertiary, Icons.Rounded.Edit)
        is AssistantAction.Delete -> Triple(t("حذف", "Delete"), scheme.error, Icons.Rounded.DeleteOutline)
        is AssistantAction.Complete -> Triple(t("انجام شد", "Done"), scheme.secondary, Icons.Rounded.CheckCircle)
        is AssistantAction.Miss -> Triple(t("انجام نشد", "Not done"), scheme.error, Icons.Rounded.EventBusy)
        is AssistantAction.Postpone -> Triple(t("تعویق", "Postpone"), scheme.tertiary, Icons.Rounded.Snooze)
    }
    val r = when (a) {
        is AssistantAction.Create -> a.draft
        is AssistantAction.Update -> a.after
        is AssistantAction.Delete -> a.target
        is AssistantAction.Complete -> a.target
        is AssistantAction.Miss -> a.target
        is AssistantAction.Postpone -> a.target
    }
    val zone = r.zoneId
    val lines = buildList {
        when (a) {
            is AssistantAction.Update -> addAll(Assistant.describeChanges(a.before, a.after))
            is AssistantAction.Postpone -> add(t("${leadLabel(a.minutes)} عقب‌تر", "${leadLabel(a.minutes)} later"))
            else -> {
                val at = r.displayAt(now)
                if (at > 0) add(Dates.friendlyDay(Dates.localDate(at, zone), LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language) +
                    " • " + Dates.formatTime(at, zone, AppDisplay.language) + if (a.needsFix(now)) t(" — گذشته!", " — passed!") else "")
                if (a is AssistantAction.Create) {
                    if (r.unit != RepeatUnit.NONE) add(repeatLabel(r))
                    if (r.alertStyle == AlertStyle.ALARM) add(t("زنگ تمام‌صفحه", "Full-screen alarm"))
                    if (r.important) add(t("مهم", "Important"))
                    if (r.nagMinutes > 0) add(t("تا انجام، هر ${leadLabel(r.nagMinutes)}", "Until done, every ${leadLabel(r.nagMinutes)}"))
                    if (r.leadMinutes > 0) add(t("${leadLabel(r.leadMinutes)} زودتر خبر می‌دهد", "${leadLabel(r.leadMinutes)} early"))
                }
            }
        }
    }
    BubbleSurface(if (a is AssistantAction.Delete) scheme.errorContainer else scheme.surfaceContainerHigh) {
        Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = color)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                    Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { AssistantSession.remove(a) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, t("نادیده بگیر", "Discard"), Modifier.size(16.dp))
                }
            }
            if (lines.isNotEmpty()) Text(lines.joinToString(" • "), style = MaterialTheme.typography.bodySmall,
                color = if (a.needsFix(now)) scheme.error else scheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, top = 2.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (a is AssistantAction.Create || a is AssistantAction.Update) OutlinedButton(onClick = { onTouched(); AssistantSession.edit(context, a) },
                    contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("اصلاح", "Edit"))
                }
                Button(onClick = { AssistantSession.apply(context, listOf(a)) }, enabled = !a.needsFix(now),
                    contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(36.dp),
                    colors = if (a is AssistantAction.Delete) ButtonDefaults.buttonColors(containerColor = scheme.error) else ButtonDefaults.buttonColors()) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("تأیید", "Confirm"))
                }
            }
        }
    }
}
