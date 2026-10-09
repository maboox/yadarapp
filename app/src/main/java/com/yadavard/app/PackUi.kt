package com.yadavard.app

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen picker: choose reminders and which of your settings go with them, then share the pack. */
@Composable
fun PackExportDialog(items: List<Reminder>, preselected: Set<Long>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val active = remember(items) { items.filter { !it.done }.sortedBy { it.title.lowercase() } }
    var selected by remember { mutableStateOf(preselected) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(Packs.Options()) }
    val shown = active.filter { query.isBlank() || it.title.contains(query.trim(), true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, t("بستن", "Close")) }
                    Text(t("ساخت پک برای ارسال", "Share a reminder pack"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(t("نام پک", "Pack name")) }, placeholder = { Text(t("مثلاً تولدهای خانواده", "e.g. Family birthdays")) },
                                shape = RoundedCornerShape(14.dp))
                            Text(t("کدام تنظیمات خودت هم فرستاده شود؟ بقیه با تنظیمات پیش‌فرض گیرنده ساخته می‌شوند.",
                                "Which of your settings go along? The rest use the receiver's defaults."),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OptionRow(t("نوع یادآوری (زنگ تمام‌صفحه / اعلان)", "Alert type (alarm / notification)"), options.alertStyle) { options = options.copy(alertStyle = it) }
                            OptionRow(t("یادآوری زودتر (مثلاً ۲ روز قبل)", "Advance notice (e.g. 2 days before)"), options.lead) { options = options.copy(lead = it) }
                            OptionRow(t("تکرار تا انجام", "Repeat until done"), options.nag) { options = options.copy(nag = it) }
                            OptionRow(t("علامت «مهم»", "“Important” flag"), options.important) { options = options.copy(important = it) }
                            OptionRow(t("توضیحات", "Notes"), options.notes) { options = options.copy(notes = it) }
                            OptionRow(t("دسته‌بندی", "Category"), options.category) { options = options.copy(category = it) }
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t("یادآوری‌ها (${n(selected.size)} انتخاب)", "Reminders (${selected.size} selected)"),
                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    val ids = shown.map { it.id }.toSet()
                                    selected = if (selected.containsAll(ids)) selected - ids else selected + ids
                                }) { Text(t("همه / هیچ", "All / none")) }
                            }
                            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(t("جست‌وجو", "Search")) },
                                shape = RoundedCornerShape(14.dp))
                        }
                    }
                    items(shown, key = { it.id }) { r ->
                        val on = r.id in selected
                        Row(Modifier.fillMaxWidth().clickable { selected = if (on) selected - r.id else selected + r.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { selected = if (on) selected - r.id else selected + r.id })
                            Icon(r.category.catIcon(), null, tint = r.category.catColor(), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(describe(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
                Button(onClick = {
                    val chosen = active.filter { it.id in selected }
                    val title = name.ifBlank { t("پک یادآوری", "Reminder pack") }
                    runCatching { context.startActivity(Packs.shareIntent(context, title, Packs.build(title, chosen, options))) }
                        .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
                    Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp))
                    Text(t("ارسال پک (${n(selected.size)})", "Send pack (${selected.size})"))
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

private fun describe(r: Reminder): String {
    val zone = r.zoneId
    val at = if (r.nextAt > 0) Dates.formatDateTime(r.nextAt, zone, AppDisplay.calendar, AppDisplay.language, withYear = false) else ""
    return if (r.unit != RepeatUnit.NONE) "$at · ${repeatLabel(r)}" else at
}

/** Shows a received pack and adds the chosen reminders. */
@Composable
fun PackImportDialog(pack: Packs.Pack, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }
    val previews = remember(pack) { pack.items.map { it to Packs.toReminder(context, it, now) } }
    var selected by remember { mutableStateOf(previews.indices.filter { previews[it].second != null }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.Inventory2, null) },
        title = { Text(pack.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("${n(pack.items.size)} یادآوری در این پک است. تنظیماتی که فرستنده نفرستاده با پیش‌فرض‌های خودت ساخته می‌شوند.",
                    "${pack.items.size} reminders. Settings the sender left out use your defaults."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(previews.indices.toList()) { i ->
                        val (item, r) = previews[i]
                        val on = i in selected
                        Row(Modifier.fillMaxWidth().clickable(enabled = r != null) { selected = if (on) selected - i else selected + i },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { selected = if (on) selected - i else selected + i }, enabled = r != null)
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r?.let { describe(it) + if (it.alertStyle == AlertStyle.ALARM) t(" · زنگ", " · alarm") else "" }
                                    ?: t("زمانش گذشته است", "Already past"),
                                    style = MaterialTheme.typography.bodySmall, color = if (r == null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                busy = true
                scope.launch {
                    val added = withContext(Dispatchers.IO) { Packs.install(context, pack, selected.sorted().map { previews[it].first }) }
                    Toast.makeText(context, t("${n(added)} یادآوری اضافه شد", "$added reminders added"), Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }, enabled = selected.isNotEmpty() && !busy) { Text(t("افزودن (${n(selected.size)})", "Add (${selected.size})")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(t("انصراف", "Cancel")) } })
}
