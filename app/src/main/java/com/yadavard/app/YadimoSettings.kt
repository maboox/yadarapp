package com.yadavard.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Settings → Yadimo: on/off, the wardrobe, how chatty it is, the morning hello and the city for the weather. */
@Composable
fun YadimoSettingsCard() {
    val context = LocalContext.current
    val on = rememberYadimoEnabled()
    var level by remember { mutableIntStateOf(YadimoTalk.level(context)) }
    var hello by remember { mutableStateOf(YadimoTalk.hello(context)) }
    var time by remember { mutableStateOf(YadimoTalk.helloTime(context)) }
    var parts by remember { mutableStateOf(listOf("tasks", "weather", "quote", "sound").associateWith { YadimoTalk.helloPart(context, it) }) }
    var cityId by remember { mutableStateOf(YadimoTalk.cityId(context)) }
    var wardrobe by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickCity by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { tick++; YadimoTalk.schedule(context) }
    @Suppress("UNUSED_VARIABLE") val read = tick

    SettingsCard(t("یادیمو", "Yadimo"), Icons.Rounded.Face,
        summary = t("دوست زندهٔ برنامه: لباس و لوازم، سلام صبح و پیام‌های کوتاه", "The app's living buddy: outfits, morning hello and short notes"),
        collapsible = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("یادیمو روشن باشد", "Show Yadimo"), style = MaterialTheme.typography.bodyLarge)
                Text(t("در صفحه‌ها، روی نوار ضبط صدا و صفحهٔ زنگ دیده می‌شود.", "Appears around the app, on the voice pill and the alarm screen."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(on, { YadimoStore.setEnabled(context, it); YadimoTalk.schedule(context) })
        }
        if (on) {

        FilledTonalButton(onClick = { wardrobe = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Checkroom, null); Spacer(Modifier.width(8.dp)); Text(t("کمد یادیمو: ظاهر، لباس و لوازم", "Yadimo's wardrobe"))
        }

        SettingLabel(t("چقدر خودش حرف بزند", "How chatty"))
        SettingSegmented(listOf(0 to t("ساکت", "Off"), 1 to t("کم‌حرف", "Rarely"), 2 to t("معمولی", "Normal"), 3 to t("پرحرف", "Chatty")), level) {
            level = it; YadimoTalk.setLevel(context, it)
        }
        Text(when (level) {
            0 -> t("فقط داخل برنامه دیده می‌شود.", "Only shows inside the app.")
            1 -> t("روزی یک بار، وقتی با گوشی کار می‌کنی، گوشهٔ صفحه سر می‌زند.", "Once a day, while you use the phone, it pops in at the corner.")
            2 -> t("روزی دو بار سر می‌زند و ۲۰ دقیقه قبل از بعضی یادآوری‌ها خبرت می‌کند.", "Twice a day, plus a heads-up 20 minutes before some reminders.")
            else -> t("روزی چند بار سر می‌زند و قبل از یادآوری‌ها خبرت می‌کند.", "A few times a day, plus heads-ups before reminders.")
        } + t(" فقط وقتی گوشی دستت است می‌آید و چند ثانیه بعد می‌رود؛ شب‌ها ساکت است.", " It only comes while you're using the phone, leaves after a few seconds, and stays quiet at night."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (level > 0 && !Phone.canOverlay(context)) Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("برای اینکه یادیمو روی برنامه‌های دیگر هم بیاید، اجازهٔ «نمایش روی برنامه‌ها» لازم است.",
                    "To pop in over other apps, Yadimo needs “display over other apps”."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    Phone.open(context, Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }) { Text(t("اجازه", "Allow")) }
            }
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WbSunny, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t("سلام صبح", "Morning hello"), style = MaterialTheme.typography.bodyLarge)
                Text(t("هر روز سر ساعت، صفحه‌ای مثل زنگ با برنامهٔ روز", "Every day at a set time, a screen like the alarm with your day"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(hello, { hello = it; YadimoTalk.setHello(context, it) })
        }
        if (hello) {
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Schedule, null); Spacer(Modifier.width(8.dp))
                Text(t("ساعت: ", "Time: ") + Dates.formatTime(time.first, time.second, AppDisplay.language))
            }
            listOf("tasks" to t("کارهای امروز", "Today's tasks"), "weather" to t("آب‌وهوا", "Weather"),
                "quote" to t("یک جملهٔ انگیزشی", "A kind word"), "sound" to t("صدای آرام", "Soft sound")).forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clickable {
                    val v = !(parts[key] ?: true); parts = parts + (key to v); YadimoTalk.setHelloPart(context, key, v)
                }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(parts[key] ?: true, { v -> parts = parts + (key to v); YadimoTalk.setHelloPart(context, key, v) })
                    Text(label)
                }
            }
            TextButton(onClick = { YadimoTalk.preview(context) }) { Text(t("نمایش نمونه", "Show a preview")) }
        }

        HorizontalDivider()
        val city = YadimoLines.city(cityId)
        OutlinedButton(onClick = { pickCity = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.LocationCity, null); Spacer(Modifier.width(8.dp))
            Text(t("شهر برای آب‌وهوا: ", "City for weather: ") + (city?.let { t(it.fa, it.en) } ?: t("انتخاب نشده", "not set")))
        }
        Text(t("بدون اجازهٔ موقعیت؛ فقط شهر را انتخاب کن.", "No location permission; just pick a city."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (pickTime) TimePickerSheet(time.first, time.second, { pickTime = false }) { h, m ->
        time = h to m; pickTime = false; YadimoTalk.setHelloTime(context, h, m)
    }
    if (pickCity) AlertDialog(onDismissRequest = { pickCity = false },
        title = { Text(t("شهر", "City")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(t("هیچ‌کدام", "None"), Modifier.fillMaxWidth().clickable { cityId = ""; YadimoTalk.setCity(context, ""); pickCity = false }.padding(12.dp))
                }
                items(YadimoLines.cities, key = { it.id }) { c ->
                    Text(t(c.fa, c.en), Modifier.fillMaxWidth().clickable { cityId = c.id; YadimoTalk.setCity(context, c.id); pickCity = false }.padding(12.dp),
                        color = if (c.id == cityId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickCity = false }) { Text(t("بستن", "Close")) } })

    if (wardrobe) Dialog(onDismissRequest = { wardrobe = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { wardrobe = false }) { Icon(Icons.Rounded.Close, t("بستن", "Close")) }
                    Text(t("کمد یادیمو", "Yadimo's wardrobe"), style = MaterialTheme.typography.titleLarge)
                }
                YadimoCustomizer(Modifier.weight(1f), onLeave = { wardrobe = false })
            }
        }
    }
}
