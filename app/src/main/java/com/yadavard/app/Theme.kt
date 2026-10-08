package com.yadavard.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Observable display settings; Compose recomposes when these change. */
object AppDisplay {
    var language by mutableStateOf(AppLanguage.FA)
    var calendar by mutableStateOf(CalendarSystem.PERSIAN)
    var theme by mutableStateOf(ThemeMode.SYSTEM)

    fun load(context: Context) {
        language = Prefs.language(context)
        calendar = Prefs.calendar(context)
        theme = Prefs.theme(context)
        Categories.ensure(context)
    }
}

fun t(fa: String, en: String): String = if (AppDisplay.language == AppLanguage.FA) fa else en
fun n(value: Any): String = Dates.digits(value.toString(), AppDisplay.language)

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.v() = copy(fontFamily = Vazirmatn)
private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.v(), displayMedium = base.displayMedium.v(), displaySmall = base.displaySmall.v(),
    headlineLarge = base.headlineLarge.v(), headlineMedium = base.headlineMedium.v().copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.v().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.v().copy(fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.v().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.v().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.v(), bodyMedium = base.bodyMedium.v(), bodySmall = base.bodySmall.v(),
    labelLarge = base.labelLarge.v().copy(fontWeight = FontWeight.SemiBold), labelMedium = base.labelMedium.v(), labelSmall = base.labelSmall.v(),
)

val Brand = Color(0xFF5B4BDB)

private val LightColors = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF), onPrimaryContainer = Color(0xFF1C1073),
    secondary = Color(0xFF0F9D8A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F4EE), onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFE07A2E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5D1), onTertiaryContainer = Color(0xFF4A2200),
    error = Color(0xFFD93F3F), onError = Color.White, errorContainer = Color(0xFFFFE1DE), onErrorContainer = Color(0xFF5C0A0A),
    background = Color(0xFFF6F5FB), onBackground = Color(0xFF1B1A26),
    surface = Color(0xFFF6F5FB), onSurface = Color(0xFF1B1A26),
    surfaceVariant = Color(0xFFEAE7F4), onSurfaceVariant = Color(0xFF656277),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0EEF8), surfaceContainerHigh = Color(0xFFEAE7F4), surfaceContainerHighest = Color(0xFFE3E0EF),
    outline = Color(0xFFC9C5D9), outlineVariant = Color(0xFFE4E1EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B0FF), onPrimary = Color(0xFF241690),
    primaryContainer = Color(0xFF3D30B5), onPrimaryContainer = Color(0xFFE6E1FF),
    secondary = Color(0xFF5FD9C4), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005045), onSecondaryContainer = Color(0xFFD2F4EE),
    tertiary = Color(0xFFFFB783), onTertiary = Color(0xFF4A2200),
    tertiaryContainer = Color(0xFF6B3500), onTertiaryContainer = Color(0xFFFFE5D1),
    error = Color(0xFFFF8A80), onError = Color(0xFF5C0A0A), errorContainer = Color(0xFF7A1C1C), onErrorContainer = Color(0xFFFFE1DE),
    background = Color(0xFF110F1A), onBackground = Color(0xFFE7E4F2),
    surface = Color(0xFF110F1A), onSurface = Color(0xFFE7E4F2),
    surfaceVariant = Color(0xFF2A2738), onSurfaceVariant = Color(0xFFB4B0C6),
    surfaceContainerLowest = Color(0xFF0C0B13), surfaceContainerLow = Color(0xFF1B1926),
    surfaceContainer = Color(0xFF1F1D2B), surfaceContainerHigh = Color(0xFF292636), surfaceContainerHighest = Color(0xFF343142),
    outline = Color(0xFF4C4860), outlineVariant = Color(0xFF353246),
)

@Composable
fun isDarkTheme(): Boolean = when (AppDisplay.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun YadarTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)),
        content = content)
}

/** Human description of a repeat rule, e.g. "Every 2 weeks · Sat, Tue". */
fun repeatLabel(r: Reminder): String {
    val every = r.every
    val many = every > 1
    val num = n(every)
    val calName = if (r.calendar == CalendarSystem.PERSIAN) t("شمسی", "Persian") else t("میلادی", "Gregorian")
    return when (r.unit) {
        RepeatUnit.NONE -> t("یک‌باره", "Once")
        RepeatUnit.HOURS -> if (many) t("هر $num ساعت", "Every $every hours") else t("هر ساعت", "Hourly")
        RepeatUnit.DAYS -> if (many) t("هر $num روز", "Every $every days") else t("هر روز", "Daily")
        RepeatUnit.WEEKS -> {
            val days = weekdayList(r.weekdays)
            (if (many) t("هر $num هفته", "Every $every weeks") else t("هر هفته", "Weekly")) +
                if (days.isNotEmpty()) " · " + days.joinToString(t("، ", ", ")) else ""
        }
        RepeatUnit.MONTHS -> {
            val day = when (r.monthDay) { -1 -> t("روز آخر", "last day"); 0 -> null; else -> t("روز ${n(r.monthDay)}", "day ${r.monthDay}") }
            (if (many) t("هر $num ماه", "Every $every months") else t("هر ماه", "Monthly")) + " $calName" + (day?.let { " · $it" } ?: "")
        }
        RepeatUnit.YEARS -> (if (many) t("هر $num سال", "Every $every years") else t("هر سال", "Yearly")) + " $calName"
        RepeatUnit.AFTER_DONE_DAYS -> t("$num روز پس از انجام", "$every days after done")
    }
}

fun weekdayList(mask: Int): List<String> {
    val order = generateSequence(Dates.firstDayOfWeek(AppDisplay.calendar)) { it.plus(1) }.take(7).toList()
    return order.filter { mask and (1 shl (it.value - 1)) != 0 }.map { Dates.weekdayName(it, AppDisplay.language, short = AppDisplay.language == AppLanguage.EN) }
}

fun leadLabel(minutes: Int): String = when {
    minutes <= 0 -> t("بدون", "None")
    minutes < 60 -> t("${n(minutes)} دقیقه", "$minutes min")
    minutes < 1440 -> t("${n(minutes / 60)} ساعت", "${minutes / 60} h")
    else -> t("${n(minutes / 1440)} روز", "${minutes / 1440} d")
}
