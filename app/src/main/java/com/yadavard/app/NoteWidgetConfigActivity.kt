package com.yadavard.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Lets the user pick which note a note widget shows (also when the widget is reconfigured). */
class NoteWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDisplay.load(this)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Leaving without a choice cancels adding the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val notes = Repo.notes(this).sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

        fun choose(noteId: Long) {
            NoteWidgets.set(this, widgetId, noteId)
            WidgetUpdater.update(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }

        setContent {
            YadarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().systemBarsPadding()) {
                            Text(t("کدام یادداشت روی صفحه باشد؟", "Which note should the widget show?"),
                                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
                            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                item {
                                    Choice(Icons.Rounded.PushPin, MaterialTheme.colorScheme.primary,
                                        t("خودکار: آخرین یادداشت سنجاق‌شده یا ویرایش‌شده", "Automatic: latest pinned or edited note"),
                                        t("همیشه تازه‌ترین یادداشتت را نشان می‌دهد", "Always shows your newest note")) { choose(-1L) }
                                }
                                items(notes, key = { it.id }) { note ->
                                    Choice(Icons.Rounded.StickyNote2, noteColor(note.color), note.title.ifBlank { t("بدون عنوان", "Untitled") },
                                        note.body.replace('\n', ' ').take(80)) { choose(note.id) }
                                }
                                if (notes.isEmpty()) item {
                                    Text(t("هنوز یادداشتی نداری. «خودکار» را بزن؛ هر یادداشتی که بسازی همین‌جا دیده می‌شود.",
                                        "No notes yet. Pick “Automatic” and any note you write will show here."),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color,
                   title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
