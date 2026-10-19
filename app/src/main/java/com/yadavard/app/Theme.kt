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
import androidx.compose.ui.graphics.luminance
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
    var color by mutableStateOf(ThemeColor.VIOLET)

    fun load(context: Context) {
        language = Prefs.language(context)
        calendar = Prefs.calendar(context)
        theme = Prefs.theme(context)
        color = Prefs.themeColor(context)
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
fun isDarkTheme(): Boolean = if (themeSpec(AppDisplay.color).alwaysDark) true else when (AppDisplay.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Everything that makes a theme: palette, light and dark backgrounds, header gradient and corner style. */
class ThemeSpec(
    val id: ThemeColor, val fa: String, val en: String,
    val primary: Long, val darkPrimary: Long, val secondary: Long, val tertiary: Long,
    /** Page background and card colour in light mode. */
    val lightBg: Long, val lightCard: Long,
    /** Page background and card colour in dark mode. */
    val darkBg: Long, val darkCard: Long,
    /** Quick-add header and alarm screen gradient (dark → light). */
    val hero: List<Long>,
    val corner: Int = 24,
    /** Always dark, whatever the light/dark setting says. */
    val alwaysDark: Boolean = false,
)

val themes = listOf(
    ThemeSpec(ThemeColor.VIOLET, "یادار", "Yadar", 0xFF5B4BDB, 0xFFB9B0FF, 0xFF0F9D8A, 0xFFE07A2E,
        0xFFF6F5FB, 0xFFFFFFFF, 0xFF110F1A, 0xFF1B1926, listOf(0xFF3B2DB0, 0xFF5B4BDB, 0xFF8F7BFF)),
    ThemeSpec(ThemeColor.OCEAN, "اقیانوس", "Ocean", 0xFF1667C7, 0xFF8EC2FF, 0xFF00897B, 0xFF7B5CD6,
        0xFFEFF5FB, 0xFFFFFFFF, 0xFF0B1220, 0xFF132033, listOf(0xFF0B3D91, 0xFF1667C7, 0xFF35B6E5), corner = 26),
    ThemeSpec(ThemeColor.FOREST, "جنگل", "Forest", 0xFF2E7D4F, 0xFF8BD7A8, 0xFF8D6E2F, 0xFFC0573A,
        0xFFF2F6EF, 0xFFFDFEFB, 0xFF0E150F, 0xFF18221A, listOf(0xFF1D5133, 0xFF2E7D4F, 0xFF7DBF6A), corner = 22),
    ThemeSpec(ThemeColor.SUNSET, "غروب", "Sunset", 0xFFD2512A, 0xFFFFB59C, 0xFF9C3D6E, 0xFFE0A100,
        0xFFFCF4EE, 0xFFFFFCFA, 0xFF1A1210, 0xFF261A17, listOf(0xFF8E2F5A, 0xFFD2512A, 0xFFF4A340), corner = 28),
    ThemeSpec(ThemeColor.BLOSSOM, "شکوفه", "Blossom", 0xFFC2407E, 0xFFFFB0D2, 0xFF6A5ACD, 0xFF2E9C8A,
        0xFFFDF2F6, 0xFFFFFFFF, 0xFF1A0F15, 0xFF271820, listOf(0xFF8E2C63, 0xFFC2407E, 0xFFF29BC2), corner = 30),
    ThemeSpec(ThemeColor.PAPER, "کاغذی", "Paper", 0xFF2B2B2B, 0xFFE6E1D6, 0xFF5E6F64, 0xFFB5562F,
        0xFFF5F1E8, 0xFFFFFDF7, 0xFF151412, 0xFF201F1C, listOf(0xFF2B2B2B, 0xFF4A4740, 0xFF8A8270), corner = 12),
    ThemeSpec(ThemeColor.MIDNIGHT, "نیمه‌شب", "Midnight", 0xFF00BFA6, 0xFF4DE8CF, 0xFF7C6CFF, 0xFFFFB454,
        0xFF000000, 0xFF0E0E10, 0xFF000000, 0xFF0E0E10, listOf(0xFF001E1A, 0xFF00796B, 0xFF00BFA6), corner = 20, alwaysDark = true),
)

fun themeSpec(id: ThemeColor): ThemeSpec = themes.firstOrNull { it.id == id } ?: themes.first()

private fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

private fun onColor(c: Color) = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White

/** The full light or dark colour scheme of a theme; the original Yadar theme keeps its hand-tuned colours. */
fun appColors(dark: Boolean, id: ThemeColor): ColorScheme {
    val spec = themeSpec(id)
    if (id == ThemeColor.VIOLET) return if (dark) DarkColors else LightColors
    val base = if (dark) DarkColors else LightColors
    return if (dark) {
        val p = Color(spec.darkPrimary)
        val bg = Color(spec.darkBg)
        val card = Color(spec.darkCard)
        val sec = mix(Color(spec.secondary), Color.White, 0.35f)
        val ter = mix(Color(spec.tertiary), Color.White, 0.35f)
        base.copy(
            primary = p, onPrimary = onColor(p),
            primaryContainer = mix(Color(spec.primary), bg, 0.35f), onPrimaryContainer = mix(p, Color.White, 0.6f),
            secondary = sec, onSecondary = onColor(sec), secondaryContainer = mix(Color(spec.secondary), bg, 0.55f),
            onSecondaryContainer = mix(sec, Color.White, 0.6f),
            tertiary = ter, onTertiary = onColor(ter), tertiaryContainer = mix(Color(spec.tertiary), bg, 0.55f),
            onTertiaryContainer = mix(ter, Color.White, 0.6f),
            background = bg, surface = bg, onBackground = Color(0xFFE9E6EF), onSurface = Color(0xFFE9E6EF),
            surfaceVariant = mix(card, p, 0.12f), onSurfaceVariant = mix(Color(0xFFB8B4C4), p, 0.15f),
            surfaceContainerLowest = bg, surfaceContainerLow = card, surfaceContainer = mix(card, p, 0.05f),
            surfaceContainerHigh = mix(card, p, 0.09f), surfaceContainerHighest = mix(card, p, 0.14f),
            outline = mix(card, p, 0.35f), outlineVariant = mix(card, p, 0.18f))
    } else {
        val p = Color(spec.primary)
        val bg = Color(spec.lightBg)
        val card = Color(spec.lightCard)
        base.copy(
            primary = p, onPrimary = onColor(p),
            primaryContainer = mix(p, Color.White, 0.84f), onPrimaryContainer = mix(p, Color.Black, 0.55f),
            secondary = Color(spec.secondary), onSecondary = onColor(Color(spec.secondary)),
            secondaryContainer = mix(Color(spec.secondary), Color.White, 0.84f), onSecondaryContainer = mix(Color(spec.secondary), Color.Black, 0.6f),
            tertiary = Color(spec.tertiary), onTertiary = onColor(Color(spec.tertiary)),
            tertiaryContainer = mix(Color(spec.tertiary), Color.White, 0.84f), onTertiaryContainer = mix(Color(spec.tertiary), Color.Black, 0.6f),
            background = bg, surface = bg, onBackground = Color(0xFF1C1B1F), onSurface = Color(0xFF1C1B1F),
            surfaceVariant = mix(bg, p, 0.08f), onSurfaceVariant = mix(Color(0xFF5F5C6B), p, 0.15f),
            surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = mix(bg, p, 0.03f),
            surfaceContainerHigh = mix(bg, p, 0.07f), surfaceContainerHighest = mix(bg, p, 0.11f),
            outline = mix(bg, p, 0.3f), outlineVariant = mix(bg, p, 0.14f))
    }
}

/** Gradient of the quick-add header and the alarm screen in the current theme. */
fun heroColors(): List<Color> = themeSpec(AppDisplay.color).hero.map { Color(it) }

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
    minutes % 10080 == 0 -> t("${n(minutes / 10080)} هفته", "${minutes / 10080} wk")
    else -> t("${n(minutes / 1440)} روز", "${minutes / 1440} d")
}
