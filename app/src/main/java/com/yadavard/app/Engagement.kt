package com.yadavard.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/**
 * Light-weight engagement: when to ask for a rating, and a small daily totals report for signed-in users.
 * Nothing here leaves the phone except counts (and the rating), once a day at most.
 */
object Engagement {
    private const val DAY = 24 * 3600_000L
    private const val MIN_REMINDERS = 5
    private const val MAX_ASKS = 5
    private const val ASK_EVERY_DAYS = 10
    private const val BAZAAR = "com.farsitel.bazaar"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("engagement", Context.MODE_PRIVATE)

    fun stars(context: Context): Int = prefs(context).getInt("stars", 0)

    /** Counts a day on which the app was opened (for the "days used" figure). */
    fun noteLaunch(context: Context) {
        val today = LocalDate.now().toString()
        val p = prefs(context)
        if (p.getString("last_day", "") != today) p.edit().putString("last_day", today).putInt("days", p.getInt("days", 0) + 1).apply()
    }

    /** True on a fresh start when the user has made a few reminders and has not rated yet (and was not asked too recently). */
    fun shouldAskRating(context: Context): Boolean {
        val p = prefs(context)
        if (p.getInt("stars", 0) > 0 || p.getInt("asks", 0) >= MAX_ASKS) return false
        if (runCatching { Repo.all(context).size }.getOrDefault(0) < MIN_REMINDERS) return false
        val last = p.getLong("last_ask", 0)
        return last == 0L || System.currentTimeMillis() - last >= ASK_EVERY_DAYS * DAY
    }

    /** The dialog was shown and dismissed without a rating: ask again after a while, a few times at most. */
    fun askedLater(context: Context) {
        val p = prefs(context)
        p.edit().putInt("asks", p.getInt("asks", 0) + 1).putLong("last_ask", System.currentTimeMillis()).apply()
    }

    fun rate(context: Context, stars: Int) {
        prefs(context).edit().putInt("stars", stars.coerceIn(1, 5)).putLong("last_sent", 0).apply()
    }

    /** Opens Yadimo's page in Bazaar (its rating box), or the web page when Bazaar is missing. */
    fun openStore(context: Context) {
        val pkg = context.packageName
        val intent = Intent(Intent.ACTION_EDIT, Uri.parse("bazaar://details?id=$pkg")).setPackage(BAZAAR)
        try { context.startActivity(intent.also { if (context !is Activity) it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cafebazaar.ir/app/$pkg?l=fa"))
                    .also { if (context !is Activity) it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }
    }

    /** Sends the daily totals when signed in and at least ~20 hours passed since the last report. */
    suspend fun sendStats(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (Account.token(app) == null) return@withContext
        val p = prefs(app)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last_sent", 0) < 20 * 3600_000L) return@withContext
        val all = Repo.all(app)
        val month = now - 30 * DAY
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()
        val body = JSONObject().put("reminders", all.size).put("notes", Repo.notes(app).size)
            .put("done", all.sumOf { it.completedCount }).put("missed", all.sumOf { it.missedCount })
            .put("ai_requests", UsageLog.entries(app).count { it.time >= month })
            .put("days_used", p.getInt("days", 0)).put("stars", p.getInt("stars", 0))
            .put("version", version).put("language", Prefs.language(app).name.lowercase()).put("theme", Prefs.themeColor(app).name.lowercase())
        runCatching { Account.sendStats(app, body); p.edit().putLong("last_sent", now).apply() }
    }
}

/** "How many stars would you give Yadimo?" High ratings go to the store page, low ones just get a thank-you. */
@Composable
fun RatingDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var stars by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = { Engagement.askedLater(context); onClose() },
        title = { Text(t("یادیمو چطور بود؟", "How do you like Yadimo?")) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("چند ستاره به یادیمو می‌دهی؟ نظرت به ما کمک می‌کند آن را بهتر کنیم.",
                    "How many stars would you give Yadimo? Your opinion helps us improve it."), textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    (1..5).forEach { i ->
                        Icon(if (i <= stars) Icons.Rounded.Star else Icons.Rounded.StarBorder, "$i",
                            tint = if (i <= stars) Color(0xFFF5B301) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp).clickable { stars = i })
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = stars > 0, onClick = {
                Engagement.rate(context, stars)
                if (stars > 3) Engagement.openStore(context)
                else Toast.makeText(context, t("ممنون از نظرت 🙏 تلاش می‌کنیم یادیمو را بهتر کنیم.", "Thank you 🙏 we will keep improving Yadimo."), Toast.LENGTH_LONG).show()
                onClose()
            }) { Text(t("ثبت", "Send")) }
        },
        dismissButton = { TextButton(onClick = { Engagement.askedLater(context); onClose() }) { Text(t("بعداً", "Later")) } })
}
