package com.yadavard.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CalendarScreen(items: List<Reminder>, now: Long, padding: PaddingValues, selected: LocalDate,
                   onSelect: (LocalDate) -> Unit, actions: ReminderActions) {
    val zone = ZoneId.systemDefault()
    var month by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { month = selected }
    val cal = AppDisplay.calendar

    // Dots per day for the visible month (and a little around it). Daily/hourly routines add no dots;
    // days with an important reminder get a red ring.
    val marks = remember(items, month, cal) {
        val start = Dates.monthStart(month, cal).minusDays(7)
        val end = start.plusDays(50)
        val s = Dates.startOfDay(start, zone)
        val e = Dates.startOfDay(end, zone)
        val map = HashMap<LocalDate, MutableList<Color>>()
        val important = HashSet<LocalDate>()
        items.filter { !it.isDaily }.forEach { r ->
            Recurrence.occurrencesIn(r, s, e, 400).forEach { at ->
                val day = Dates.localDate(at, zone)
                map.getOrPut(day) { mutableListOf() }
                    .let { list -> if (list.size < 3) list.add(if (r.done) Color.Gray else r.category.catColor()) }
                if (r.important && !r.done) important += day
            }
        }
        map.mapValues { it.value.toList() } to important.toSet()
    }
    val dayEntries = remember(items, selected, now) {
        val s = Dates.startOfDay(selected, zone)
        val e = Dates.startOfDay(selected.plusDays(1), zone)
        items.flatMap { r -> Recurrence.occurrencesIn(r, s, e, 48).map { at -> Entry(r, at, !r.done && r.pendingAt == at && r.needsAttention(now)) } }
            .sortedWith(compareBy<Entry> { it.reminder.done }.thenBy { it.at })
    }

    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(t("تقویم", "Calendar"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (selected != LocalDate.now(zone)) TextButton(onClick = { onSelect(LocalDate.now(zone)) }) { Text(t("امروز", "Today")) }
            }
        }
        item(key = "grid") {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 1.dp) {
                Box(Modifier.padding(10.dp)) {
                    MonthGrid(month, selected, onSelect, { month = it }, marks.first, importantDays = marks.second)
                }
            }
        }
        item(key = "dayTitle") {
            SectionHeader(Dates.friendlyDay(selected, LocalDate.now(zone), cal, AppDisplay.language).let {
                if (selected == LocalDate.now(zone) || selected == LocalDate.now(zone).plusDays(1))
                    it + " • " + Dates.formatDate(selected, cal, AppDisplay.language, withYear = false) else it
            }, dayEntries.size)
        }
        if (dayEntries.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Rounded.EventAvailable, t("برای این روز چیزی نیست", "Nothing on this day"),
                t("با دکمهٔ «یادآوری جدید» برای این روز یادآوری بساز.", "Use “New reminder” to add one for this day."))
        }
        items(dayEntries, key = { "${it.reminder.id}_${it.at}" }) { e ->
            ReminderCard(e, now, actions, Modifier.animateItem(), showDate = false)
        }
    }
}
