package com.yadavard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private enum class Period(val days: Int?) { WEEK(7), MONTH(30), QUARTER(90), ALL(null) }

private class Tally(var done: Int = 0, var missed: Int = 0) {
    val total get() = done + missed
    val rate get() = if (total == 0) null else done * 100 / total
}

private class TaskRow(val id: Long, val title: String, val category: String, val reminder: Reminder?, val tally: Tally)

/** Statistics: how many occurrences were done or not done, by category and per reminder. */
@Composable
fun StatsScreen(items: List<Reminder>, now: Long, padding: PaddingValues) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    val today = LocalDate.now(zone)
    val since = period.days?.let { Dates.startOfDay(today.minusDays(it - 1L), zone) } ?: 0L
    val history by produceState(emptyList<HistoryEntry>(), items, period) {
        value = withContext(Dispatchers.IO) { Repo.history(context, since) }
    }

    // Per reminder. "All time" also counts what was done before outcomes were recorded (the reminder's own counters).
    val rows = remember(history, items, period) {
        val byId = items.associateBy { it.id }
        val map = linkedMapOf<Long, TaskRow>()
        history.forEach { h ->
            val row = map.getOrPut(h.reminderId) { TaskRow(h.reminderId, byId[h.reminderId]?.title ?: h.title,
                byId[h.reminderId]?.category ?: h.category, byId[h.reminderId], Tally()) }
            if (h.done) row.tally.done++ else row.tally.missed++
        }
        if (period == Period.ALL) items.forEach { r ->
            val row = map.getOrPut(r.id) { TaskRow(r.id, r.title, r.category, r, Tally()) }
            row.tally.done = maxOf(row.tally.done, r.completedCount)
            row.tally.missed = maxOf(row.tally.missed, r.missedCount)
        }
        map.values.filter { it.tally.total > 0 }
    }
    val total = remember(rows) { Tally(rows.sumOf { it.tally.done }, rows.sumOf { it.tally.missed }) }
    val active = items.count { !it.done }
    val waiting = items.count { it.needsAttention(now) }
    val byCategory = remember(rows, items) {
        val map = linkedMapOf<String, Pair<Tally, Int>>()
        rows.forEach { row -> val e = map.getOrPut(row.category) { Tally() to 0 }; e.first.done += row.tally.done; e.first.missed += row.tally.missed }
        items.filter { !it.done }.forEach { r -> val e = map.getOrPut(r.category) { Tally() to 0 }; map[r.category] = e.first to e.second + 1 }
        map.entries.sortedByDescending { it.value.first.total * 1000 + it.value.second }
    }
    val recurring = remember(rows) {
        rows.filter { it.reminder?.unit != RepeatUnit.NONE || it.tally.total > 1 }
            .sortedWith(compareByDescending<TaskRow> { it.tally.missed }.thenByDescending { it.tally.total })
    }
    var showAllTasks by rememberSaveable { mutableStateOf(false) }

    val doneColor = MaterialTheme.colorScheme.secondary
    val missedColor = MaterialTheme.colorScheme.error

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
        top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(t("آمار", "Statistics"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Period.entries) { p ->
                    FilterChip(period == p, { period = p }, { Text(when (p) {
                        Period.WEEK -> t("۷ روز", "7 days"); Period.MONTH -> t("۳۰ روز", "30 days")
                        Period.QUARTER -> t("۳ ماه", "3 months"); Period.ALL -> t("همه", "All time")
                    }) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(t("انجام‌شده", "Done"), n(total.done), Icons.Rounded.CheckCircle, doneColor, Modifier.weight(1f))
                Tile(t("انجام‌نشده", "Not done"), n(total.missed), Icons.Rounded.EventBusy, missedColor, Modifier.weight(1f))
                Tile(t("نرخ انجام", "Done rate"), total.rate?.let(::pct) ?: "—",
                    Icons.Rounded.Insights, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(t("یادآوری فعال", "Active"), n(active), Icons.Rounded.NotificationsActive, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                Tile(t("در انتظار انجام", "Waiting"), n(waiting), Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }
        item { Legend(doneColor, missedColor) }
        if (period != Period.ALL || history.isNotEmpty()) item {
            StatsCard(t("روند", "Trend"), Icons.Rounded.BarChart) {
                TrendChart(history, period, today, zone, doneColor, missedColor)
            }
        }
        if (total.total == 0 && active == 0) item {
            EmptyState(Icons.Rounded.Insights, t("هنوز آماری نیست", "No statistics yet"),
                t("هر بار «انجام شد» یا «انجام نشد» بزنی، اینجا ثبت می‌شود.", "Every “done” or “not done” is counted here."))
        }
        if (byCategory.isNotEmpty()) item {
            StatsCard(t("بر اساس دسته", "By category"), Icons.Rounded.Category) {
                byCategory.forEach { (key, value) ->
                    val (tally, open) = value
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(key.catIcon(), null, tint = key.catColor(), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(key.catLabel(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Counts(tally, open)
                        }
                        Spacer(Modifier.height(5.dp))
                        SplitBar(tally, doneColor, missedColor)
                    }
                }
            }
        }
        if (recurring.isNotEmpty()) item {
            StatsCard(t("کارهای تکراری و سابقه", "Repeating tasks"), Icons.Rounded.Repeat) {
                Text(t("مرتب بر اساس بیشترین «انجام‌نشده»", "Sorted by most “not done”"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                (if (showAllTasks) recurring else recurring.take(12)).forEach { row ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(row.category.catIcon(), null, tint = row.category.catColor(), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(row.reminder?.let { if (it.unit != RepeatUnit.NONE) repeatLabel(it) else null }
                                    ?: if (row.reminder == null) t("حذف‌شده", "Deleted") else t("یک‌باره", "Once"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Counts(row.tally, null)
                        }
                        Spacer(Modifier.height(5.dp))
                        SplitBar(row.tally, doneColor, missedColor)
                    }
                }
                if (recurring.size > 12) TextButton(onClick = { showAllTasks = !showAllTasks }) {
                    Text(if (showAllTasks) t("کمتر", "Show less") else t("همه (${n(recurring.size)})", "All (${recurring.size})"))
                }
            }
        }
    }
}

private fun pct(value: Int) = n(value) + if (AppDisplay.language == AppLanguage.FA) "٪" else "%"

@Composable
private fun Tile(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun StatsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) = SettingsCard(title, icon, content)

@Composable
private fun Legend(doneColor: Color, missedColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(doneColor, Icons.Rounded.CheckCircle, t("انجام شد", "Done"))
        LegendItem(missedColor, Icons.Rounded.EventBusy, t("انجام نشد", "Not done"))
    }
}

@Composable
private fun LegendItem(color: Color, icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "✓ 12  ✗ 3  · 4 active" with icons, so meaning never depends on colour alone. */
@Composable
private fun Counts(tally: Tally, open: Int?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
        Text(n(tally.done), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
        Text(n(tally.missed), style = MaterialTheme.typography.labelLarge)
        tally.rate?.let { Spacer(Modifier.width(8.dp)); Text(pct(it), style = MaterialTheme.typography.labelMedium, color = muted) }
        if (open != null && open > 0) {
            Spacer(Modifier.width(8.dp))
            Text(t("${n(open)} فعال", "$open active"), style = MaterialTheme.typography.labelMedium, color = muted)
        }
    }
}

/** Thin done/not-done bar with a small gap between the two parts. */
@Composable
private fun SplitBar(tally: Tally, doneColor: Color, missedColor: Color) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (tally.total == 0) track else Color.Transparent),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (tally.done > 0) Box(Modifier.weight(tally.done.toFloat()).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(doneColor))
        if (tally.missed > 0) Box(Modifier.weight(tally.missed.toFloat()).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(missedColor))
    }
}

/** Columns per day (or per week for longer periods); tap a column to read its numbers. */
@Composable
private fun TrendChart(history: List<HistoryEntry>, period: Period, today: LocalDate, zone: ZoneId, doneColor: Color, missedColor: Color) {
    val weekly = period == Period.QUARTER || period == Period.ALL
    val count = when (period) { Period.WEEK -> 7; Period.MONTH -> 30; else -> 12 }
    val buckets = remember(history, period, today) {
        val starts = (count - 1 downTo 0).map { i -> if (weekly) today.minusWeeks(i.toLong()).minusDays(6) else today.minusDays(i.toLong()) }
        starts.map { start ->
            val from = Dates.startOfDay(start, zone)
            val to = Dates.startOfDay(if (weekly) start.plusDays(7) else start.plusDays(1), zone)
            val inside = history.filter { it.recordedAt in from until to }
            Triple(start, inside.count { it.done }, inside.count { !it.done })
        }
    }
    val max = (buckets.maxOfOrNull { it.second + it.third } ?: 0).coerceAtLeast(1)
    var selected by remember(period) { mutableIntStateOf(buckets.indexOfLast { it.second + it.third > 0 }) }
    val sel = buckets.getOrNull(selected)
    Text(sel?.let { (start, d, m) ->
        val label = if (weekly) t("هفتهٔ ", "Week of ") + Dates.formatDate(start, AppDisplay.calendar, AppDisplay.language, withYear = false)
            else Dates.friendlyDay(start, today, AppDisplay.calendar, AppDisplay.language)
        "$label: " + t("${n(d)} انجام شد، ${n(m)} انجام نشد", "$d done, $m not done")
    } ?: t("روی ستون‌ها بزن تا عددشان را ببینی", "Tap a column to see its numbers"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(if (count > 12) 2.dp else 6.dp),
        verticalAlignment = Alignment.Bottom) {
        buckets.forEachIndexed { i, (_, d, m) ->
            val highlight = i == selected
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                .background(if (highlight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
                .clickable { selected = i }, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)) {
                if (m > 0) Box(Modifier.fillMaxWidth().height((110f * m / max).coerceAtLeast(3f).dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(missedColor))
                if (d > 0) Box(Modifier.fillMaxWidth().height((110f * d / max).coerceAtLeast(3f).dp)
                    .clip(RoundedCornerShape(topStart = if (m > 0) 0.dp else 4.dp, topEnd = if (m > 0) 0.dp else 4.dp)).background(doneColor))
                if (d + m == 0) Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}
