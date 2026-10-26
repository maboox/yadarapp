@file:OptIn(ExperimentalFoundationApi::class)

package com.yadavard.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.ZoneId

private val notePalette = listOf(0xFF8F7BFF, 0xFF3D8BFD, 0xFF0F9D8A, 0xFF2EAD5B, 0xFFF2B705, 0xFFE07A2E, 0xFFE5484D, 0xFFD6409F, 0xFF8D6E63, 0xFF78909C)

/** Accent colour of a note (index into the palette). */
fun noteColor(index: Int): Color = Color(notePalette[index.mod(notePalette.size)])

private fun noteDate(time: Long): String {
    val zone = ZoneId.systemDefault()
    return Dates.friendlyDay(Dates.localDate(time, zone), LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language) +
        " • " + Dates.formatTime(time, zone, AppDisplay.language)
}

/** Notes: free text with a title, colour and pin. The assistant can create, edit and search them too. */
@Composable
fun NotesScreen(notes: List<Note>, padding: PaddingValues, onOpen: (Note?) -> Unit, onPin: (Note) -> Unit, onDelete: (Note) -> Unit,
                onSave: (Note) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(notes, query) {
        notes.filter { query.isBlank() || it.title.contains(query.trim(), true) || it.body.contains(query.trim(), true) }
    }
    LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("یادداشت‌ها", "Notes"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(t("جست‌وجو در یادداشت‌ها", "Search notes")) },
                    shape = RoundedCornerShape(16.dp))
            }
        }
        if (notes.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
            EmptyState(Icons.Rounded.StickyNote2, t("هنوز یادداشتی نداری", "No notes yet"),
                t("با دکمهٔ «یادداشت جدید» یا به دستیار بگو: «یه یادداشت بذار برای خرید: شیر، نان، تخم‌مرغ».",
                    "Tap “New note”, or tell the assistant: “make a shopping note: milk, bread, eggs”."))
        }
        items(shown, key = { it.id }) { note ->
            NoteCard(note, { onOpen(note) }, { onPin(note) }, { onDelete(note) }) { line ->
                onSave(note.copy(body = NoteText.toggle(note.body, line), updatedAt = System.currentTimeMillis()))
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onOpen: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit, onToggle: (Int) -> Unit) {
    val accent = noteColor(note.color)
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.35f), MaterialTheme.shapes.large)) {
        Column(Modifier.background(accent.copy(alpha = 0.12f)).combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (note.title.isNotBlank()) Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                if (note.pinned) Icon(Icons.Rounded.PushPin, null, tint = accent, modifier = Modifier.size(16.dp))
            }
            if (note.body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                NoteBody(note.body, accent, maxLines = 10, onToggle = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Text(noteDate(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(if (note.pinned) t("برداشتن سنجاق", "Unpin") else t("سنجاق کردن", "Pin")) },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) }, onClick = { menu = false; onPin() })
                DropdownMenuItem(text = { Text(t("حذف", "Delete")) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                    onClick = { menu = false; confirm = true })
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(t("یادداشت حذف شود؟", "Delete note?")) },
        confirmButton = { TextButton({ confirm = false; onDelete() }) { Text(t("حذف", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirm = false }) { Text(t("انصراف", "Cancel")) } })
}

/** Full-screen note editor; changes are saved when it closes. */
@Composable
fun NoteEditor(note: Note?, onClose: (Note?) -> Unit, onDelete: (Note) -> Unit) {
    var title by remember { mutableStateOf(note?.title.orEmpty()) }
    var field by remember { mutableStateOf(TextFieldValue(note?.body.orEmpty())) }
    val body = field.text
    var color by remember { mutableIntStateOf(note?.color ?: 0) }
    var pinned by remember { mutableStateOf(note?.pinned ?: false) }
    var confirm by remember { mutableStateOf(false) }
    fun result(): Note? {
        if (title.isBlank() && body.isBlank()) return null
        val base = note ?: Note(title = "")
        val changed = base.title != title.trim() || base.body != body || base.color != color || base.pinned != pinned
        if (note != null && !changed) return note
        return base.copy(title = title.trim(), body = body, color = color, pinned = pinned, updatedAt = System.currentTimeMillis())
    }
    val accent = noteColor(color)
    Dialog(onDismissRequest = { onClose(result()) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().background(accent.copy(alpha = 0.08f)).systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onClose(result()) }) { Icon(Icons.Rounded.Check, t("ذخیره", "Save")) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(Icons.Rounded.PushPin, t("سنجاق", "Pin"), tint = if (pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (note != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.DeleteOutline, t("حذف", "Delete")) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    notePalette.indices.forEach { i ->
                        Box(Modifier.size(26.dp).clip(CircleShape).background(noteColor(i))
                            .border(if (i == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = i })
                    }
                }
                TextField(title, { title = it.take(200) }, Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    placeholder = { Text(t("عنوان", "Title")) }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                if (note != null) Text(t("آخرین ویرایش: ", "Edited ") + noteDate(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp))
                ListToolbar(accent) { kind ->
                    val line = NoteText.lineAt(field.text, field.selection.start)
                    val text = NoteText.setKind(field.text, line, kind)
                    val end = text.split('\n').take(line + 1).sumOf { it.length + 1 } - 1
                    field = TextFieldValue(text, TextRange(end.coerceIn(0, text.length)))
                }
                TextField(field, { value ->
                    val limited = if (value.text.length > 20_000) value.copy(text = value.text.take(20_000)) else value
                    // A tap on the ☐ / ☑ at the start of a line ticks or unticks it.
                    val tapped = if (limited.text == field.text && limited.selection.collapsed && limited.selection != field.selection)
                        NoteText.boxTappedAt(limited.text, limited.selection.start) else null
                    field = if (tapped != null) {
                        val line = NoteText.lineAt(limited.text, limited.selection.start)
                        val (text, newLine) = NoteText.toggleMove(limited.text, line)
                        val start = text.split('\n').take(newLine).sumOf { it.length + 1 }
                        TextFieldValue(text, TextRange((start + 2).coerceAtMost(text.length)))
                    } else NoteText.continueList(field.text, limited.text, limited.selection.start)
                        ?.let { (text, cursor) -> TextFieldValue(text, TextRange(cursor)) } ?: limited
                }, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp),
                    placeholder = { Text(t("بنویس…", "Write…")) },
                    visualTransformation = remember(accent) { CheckGlyphs(accent) },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            }
        }
    }
    if (confirm && note != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(t("یادداشت حذف شود؟", "Delete note?")) },
        confirmButton = { TextButton({ confirm = false; onDelete(note) }) { Text(t("حذف", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirm = false }) { Text(t("انصراف", "Cancel")) } })
}

/** Bullet, checklist and numbered-list buttons for the note editor. */
@Composable
private fun ListToolbar(accent: Color, onKind: (NoteText.Kind) -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple(NoteText.Kind.TODO, Icons.Rounded.Checklist, t("چک‌لیست", "Checklist")),
            Triple(NoteText.Kind.BULLET, Icons.Rounded.FormatListBulleted, t("فهرست", "Bullets")),
            Triple(NoteText.Kind.NUMBER, Icons.Rounded.FormatListNumbered, t("شماره‌دار", "Numbered")),
        ).forEach { (kind, icon, label) ->
            AssistChip(onClick = { onKind(kind) }, label = { Text(label) },
                leadingIcon = { Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)) })
        }
    }
}

/** Note text with lists drawn as bullets and tappable checkboxes. */
@Composable
fun NoteBody(body: String, accent: Color, maxLines: Int = Int.MAX_VALUE, onToggle: ((Int) -> Unit)? = null) {
    val lines = body.split('\n')
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.take(maxLines).forEachIndexed { index, raw ->
            val line = NoteText.parse(raw)
            Row(verticalAlignment = if (NoteText.Kind.TODO == line.kind || NoteText.Kind.DONE == line.kind) Alignment.CenterVertically else Alignment.Top) {
                when (line.kind) {
                    NoteText.Kind.TODO, NoteText.Kind.DONE -> Box(Modifier.size(38.dp).clip(CircleShape)
                        .then(if (onToggle != null) Modifier.clickable { onToggle(index) } else Modifier), contentAlignment = Alignment.Center) {
                        Icon(if (line.kind == NoteText.Kind.DONE) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                            tint = accent, modifier = Modifier.size(28.dp))
                    }
                    NoteText.Kind.BULLET -> Text("•", color = accent, modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    NoteText.Kind.NUMBER -> Text(n(line.number) + ".", color = accent, modifier = Modifier.padding(end = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    NoteText.Kind.TEXT -> {}
                }
                Text(line.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (line.kind == NoteText.Kind.DONE) TextDecoration.LineThrough else null,
                    color = if (line.kind == NoteText.Kind.DONE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
        if (lines.size > maxLines) Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Makes the ☐ / ☑ at the start of a checklist line large and coloured, so it is easy to see and to tap. */
private class CheckGlyphs(private val accent: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val b = AnnotatedString.Builder(text)
        var i = 0
        while (i < s.length) {
            val end = s.indexOf('\n', i).let { if (it < 0) s.length else it }
            if (end > i && (s[i] == '☐' || s[i] == '☑')) b.addStyle(SpanStyle(fontSize = 28.sp, color = accent), i, i + 1)
            i = end + 1
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}
