package com.yadavard.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File

/*
 * The "Yadimo's room" home-screen widget. A home-screen widget cannot run an animation itself, so it works in
 * three layers: a still picture of the room, Yadimo on top as a short loop of pictures that the widget flips
 * through, and for each button a short scene (food, ball, charge, pat, bed) that plays once before the loop
 * comes back. The pictures are made once by the same 2.5D room code as the app's room screen
 * (assets/yadimo/roomframes.html) and kept on disk, so pressing a button plays at once.
 */

/** What a widget's pictures depend on; when any of it changes they are made again. */
data class RoomSpec(val cols: Int, val rows: Int, val wDp: Int, val hDp: Int, val density: Float, val look: String,
                    val night: Boolean, val fps: Int, val budget: Long) {
    val key: String get() = "v1|$cols|$rows|$wDp|$hDp|$density|${look.hashCode()}|$night|$fps"
    val wPx: Int get() = (wDp * density).toInt()
    val hPx: Int get() = (hDp * density).toInt()
}

/** One scene on disk: [count] pictures cropped to the box at [x], [y] (widget pixels), shrunk by [scale]. */
data class RoomSeq(val kind: String, val count: Int, val fps: Int, val loop: Boolean, val x: Int, val y: Int,
                   val w: Int, val h: Int, val scale: Double, val seconds: Double)

object RoomFrames {
    /** Every scene a widget needs; the loops come first so the widget comes alive as soon as possible. */
    val ALL = listOf("idle", "sleep", "pet", "feed", "play", "charge", "bed", "wake")

    fun dir(context: Context, id: Int) = File(context.filesDir, "room/w$id")

    fun readMeta(context: Context, id: Int): JSONObject =
        runCatching { JSONObject(File(dir(context, id), "meta.json").readText()) }.getOrDefault(JSONObject())

    fun seq(context: Context, id: Int, kind: String): RoomSeq? {
        val o = readMeta(context, id).optJSONObject("seqs")?.optJSONObject(kind) ?: return null
        return RoomSeq(kind, o.optInt("count"), o.optInt("fps", 15), o.optBoolean("loop"), o.optInt("x"), o.optInt("y"),
            o.optInt("w"), o.optInt("h"), o.optDouble("scale", 1.0), o.optDouble("seconds", 2.0))
    }

    fun missing(context: Context, id: Int, spec: RoomSpec): List<String> {
        val meta = readMeta(context, id)
        if (meta.optString("key") != spec.key) return ALL
        val have = meta.optJSONObject("seqs")
        return ALL.filter { have?.has(it) != true } + (if (File(dir(context, id), "bg.png").exists()) emptyList() else listOf("bg"))
    }

    // ---------------- making the pictures ----------------

    private class Job(val context: Context, val id: Int, val spec: RoomSpec, val kinds: List<String>, val done: (Boolean) -> Unit)

    private var web: WebView? = null
    private var ready = false
    private var current: Job? = null
    private val queue = ArrayDeque<Job>()
    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { current?.let { finish(it, false) }; web?.destroy(); web = null; ready = false }

    /** Makes the [kinds] scenes for widget [id]; [done] is called on the main thread. */
    fun render(context: Context, id: Int, spec: RoomSpec, kinds: List<String>, done: (Boolean) -> Unit) = main.post {
        if (current?.id == id || queue.any { it.id == id }) { done(false); return@post }
        queue.addLast(Job(context.applicationContext, id, spec, kinds, done)); next(context.applicationContext)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun next(context: Context) {
        if (current != null) return
        val job = queue.removeFirstOrNull() ?: return
        current = job
        val w = web ?: WebView(context).also { v ->
            v.settings.javaScriptEnabled = true
            v.setBackgroundColor(0)
            v.addJavascriptInterface(Host(), "RoomHost")
            v.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { ready = true; current?.let { start(it) } }
            }
            v.layout(0, 0, 600, 600)
            v.loadUrl("file:///android_asset/yadimo/roomframes.html")
            web = v
        }
        if (ready) start(job)
        main.removeCallbacks(timeout); main.postDelayed(timeout, 90_000)
        @Suppress("UNUSED_VARIABLE") val keep = w
    }

    private fun start(job: Job) {
        val d = dir(job.context, job.id).apply { mkdirs() }
        val meta = readMeta(job.context, job.id)
        if (meta.optString("key") != job.spec.key) { d.listFiles()?.forEach { it.delete() }; writeMeta(job.context, job.id, JSONObject().put("key", job.spec.key)) }
        val seqs = job.kinds.filter { it != "bg" }
        val o = JSONObject().put("w", job.spec.wDp).put("h", job.spec.hDp).put("cols", job.spec.cols).put("rows", job.spec.rows)
            .put("density", job.spec.density.toDouble()).put("look", JSONObject(job.spec.look)).put("night", job.spec.night)
            .put("fps", job.spec.fps).put("budget", job.spec.budget).put("seqs", org.json.JSONArray(seqs))
        web?.evaluateJavascript("roomFrames.make(${JSONObject.quote(o.toString())})", null)
    }

    private fun finish(job: Job, ok: Boolean) {
        main.removeCallbacks(timeout)
        if (current === job) current = null
        job.done(ok)
        next(job.context)
    }

    @Synchronized private fun writeMeta(context: Context, id: Int, meta: JSONObject) =
        File(dir(context, id), "meta.json").writeText(meta.toString())

    private fun png(data: String): ByteArray = Base64.decode(data.substringAfter(','), Base64.DEFAULT)

    /** Called by the page (on a background thread). */
    private class Host {
        private fun job() = current
        @JavascriptInterface fun background(data: String) { job()?.let { File(dir(it.context, it.id), "bg.png").writeBytes(png(data)) } }
        @JavascriptInterface fun frame(kind: String, i: Int, data: String) { job()?.let { File(dir(it.context, it.id), "${kind}_$i.png").writeBytes(png(data)) } }
        @JavascriptInterface fun done(kind: String, json: String) {
            val j = job() ?: return
            synchronized(RoomFrames) {
                val meta = readMeta(j.context, j.id)
                val seqs = meta.optJSONObject("seqs") ?: JSONObject()
                seqs.put(kind, JSONObject(json)); meta.put("seqs", seqs); writeMeta(j.context, j.id, meta)
            }
            // The widget shows its loop as soon as the loop is ready, before the other scenes are made.
            if (kind == "idle" || kind == "sleep") main.post { RoomWidgets.show(j.context, j.id) }
        }
        @JavascriptInterface fun finished() { job()?.let { j -> main.post { finish(j, true) } } }
        @JavascriptInterface fun error(message: String) { android.util.Log.e("YadimoRoom", message); job()?.let { j -> main.post { finish(j, false) } } }
    }

    fun load(context: Context, id: Int, s: RoomSeq, densityDpi: Int): List<Bitmap> = (0 until s.count).mapNotNull { i ->
        runCatching { BitmapFactory.decodeFile(File(dir(context, id), "${s.kind}_$i.png").path) }.getOrNull()
            // shown at its box size although it was shrunk to fit the widget's memory
            ?.also { it.density = (densityDpi * s.scale).toInt().coerceAtLeast(1) }
    }
}

class YadimoRoomWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = keep { ids.forEach { RoomWidgets.refresh(context, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = keep { RoomWidgets.refresh(context, id) }
    override fun onDeleted(context: Context, ids: IntArray) = ids.forEach { RoomFrames.dir(context, it).deleteRecursively(); RoomWidgets.forget(context, it) }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RoomWidgets.ACTION_ACT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            val kind = intent.getStringExtra("kind") ?: return
            val pending = goAsync()
            RoomWidgets.act(context, id, kind) { runCatching { pending.finish() } }
            return
        }
        super.onReceive(context, intent)
    }

    /** Keeps the process alive a little while the pictures are being made. */
    private fun keep(block: () -> Unit) {
        val pending = goAsync()
        block()
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { pending.finish() } }, 45_000)
    }
}

object RoomWidgets {
    const val ACTION_ACT = "com.yadavard.app.room.ACT"
    private val main = Handler(Looper.getMainLooper())
    private val buttons = mapOf(R.id.room_b_feed to "feed", R.id.room_b_play to "play", R.id.room_b_charge to "charge",
        R.id.room_b_pet to "pet", R.id.room_b_sleep to "sleep")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("room_widgets", Context.MODE_PRIVATE)

    /** Frames per second of the widget's loop (actions play at up to 15). */
    fun fps(context: Context) = prefs(context).getInt("fps", 15)
    fun setFps(context: Context, v: Int) { prefs(context).edit().putInt("fps", v).apply(); refreshAll(context) }

    private fun sleeping(context: Context, id: Int) = prefs(context).getBoolean("sleep_$id", YadimoPet.night())
    private fun setSleeping(context: Context, id: Int, v: Boolean) = prefs(context).edit().putBoolean("sleep_$id", v).apply()
    fun forget(context: Context, id: Int) = prefs(context).edit().remove("sleep_$id").remove("night_$id").apply()

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, YadimoRoomWidget::class.java))

    fun refreshAll(context: Context) = ids(context).forEach { refresh(context, it) }

    /** Yadimo's look changed: make the pictures again, a moment after the last change. */
    private var again: Runnable? = null
    fun lookChanged(context: Context) {
        val app = context.applicationContext
        again?.let { main.removeCallbacks(it) }
        again = Runnable { refreshAll(app) }.also { main.postDelayed(it, 3000) }
    }

    fun spec(context: Context, id: Int): RoomSpec {
        val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val portrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val w = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)).takeIf { it > 0 } ?: 320
        val h = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)).takeIf { it > 0 } ?: 160
        val cols = when { w < 100 -> 1; w < 220 -> 2; else -> 4 }
        val rows = when { h < 100 -> 1; h < 200 -> 2; h < 290 -> 3; h < 420 -> 4; else -> 6 }
        val dm = context.resources.displayMetrics
        val density = dm.density.coerceAtMost(3f)
        // A widget may use about 1.5 screens worth of pictures; the room picture takes its share first.
        val limit = dm.widthPixels.toLong() * dm.heightPixels * 4 * 3 / 2
        val budget = ((limit - (w * density).toLong() * (h * density).toLong() * 4) * 0.8).toLong().coerceIn(2_000_000L, 12_000_000L)
        YadimoStore.ensure(context)
        return RoomSpec(maxOf(2, cols), rows, w, h, density, YadimoStore.config.toJson(), YadimoPet.night(), fps(context), budget)
    }

    /** Shows what is ready and makes whatever pictures are missing. */
    fun refresh(context: Context, id: Int) {
        AppDisplay.load(context)
        val spec = spec(context, id)
        val p = prefs(context)
        // Night falls (or morning comes): Yadimo goes to bed or gets up.
        if (p.getBoolean("night_$id", !spec.night) != spec.night) { p.edit().putBoolean("night_$id", spec.night).apply(); setSleeping(context, id, spec.night) }
        val missing = RoomFrames.missing(context, id, spec)
        show(context, id)
        if (missing.isNotEmpty()) RoomFrames.render(context, id, spec, missing) { show(context, id) }
    }

    /** Puts a scene on the widget: the room, Yadimo's pictures at their place, the line and the buttons. */
    fun show(context: Context, id: Int, kind: String? = null, line: String? = null) {
        val manager = AppWidgetManager.getInstance(context)
        val spec = spec(context, id)
        val rv = RemoteViews(context.packageName, R.layout.widget_room)
        val ready = RoomFrames.readMeta(context, id).optString("key") == spec.key
        val bg = if (ready) runCatching { BitmapFactory.decodeFile(File(RoomFrames.dir(context, id), "bg.png").path) }.getOrNull() else null
        if (bg != null) rv.setImageViewBitmap(R.id.room_bg, bg)
        val seq = if (ready) RoomFrames.seq(context, id, kind ?: if (sleeping(context, id)) "sleep" else "idle") else null
        rv.removeAllViews(R.id.room_flipper)
        if (seq != null) {
            val frames = RoomFrames.load(context, id, seq, context.resources.displayMetrics.densityDpi)
            // A loop plays forward and back, so it never jumps; the same pictures are reused, so it costs nothing more.
            val order = if (seq.loop && frames.size > 2) frames + frames.subList(1, frames.size - 1).reversed() else frames
            for (b in order) RemoteViews(context.packageName, R.layout.widget_room_frame).also { it.setImageViewBitmap(R.id.room_frame, b); rv.addView(R.id.room_flipper, it) }
            rv.setInt(R.id.room_flipper, "setFlipInterval", (1000 / seq.fps.coerceIn(1, 60)))
            rv.setViewPadding(R.id.room_char_box, seq.x, seq.y, 0, 0)
        }
        val text = when {
            bg == null -> t("یادیمو داره اتاقش رو می‌چینه… 🏠", "Yadimo is setting up the room… 🏠")
            else -> line ?: YadimoPet.line(context)
        }
        rv.setTextViewText(R.id.room_bubble, text)
        rv.setViewVisibility(R.id.room_buttons, if (spec.rows == 1 || bg == null) View.GONE else View.VISIBLE)
        for ((view, act) in buttons) {
            val pi = PendingIntent.getBroadcast(context, id * 10 + act.hashCode() % 10, Intent(context, YadimoRoomWidget::class.java)
                .setAction(ACTION_ACT).setData(Uri.parse("yadar://room/$id/$act")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("kind", act),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(view, pi)
        }
        rv.setOnClickPendingIntent(R.id.room_root, PendingIntent.getActivity(context, 71_300 + id,
            Intent(context, YadimoRoomActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        runCatching { manager.updateAppWidget(id, rv) }.onFailure { android.util.Log.e("YadimoRoom", "update failed", it) }
    }

    private val LINES = mapOf("feed" to ("ممم! خوشمزه بود 😋" to "Yum! 😋"), "play" to ("بزن بریم! ⚽" to "Let's play! ⚽"),
        "charge" to ("شارژ شدم! ⚡" to "Fully charged! ⚡"), "pet" to ("آخ جون! 🥰" to "Yay! 🥰"), "bed" to ("شب بخیر… 😴" to "Good night… 😴"),
        "wake" to ("صبح بخیر! ☀️" to "Good morning! ☀️"))

    /** A button: play its scene once, then the loop again. */
    fun act(context: Context, id: Int, button: String, finished: () -> Unit) {
        AppDisplay.load(context)
        val asleep = sleeping(context, id)
        val kind = when { button == "sleep" -> if (asleep) "wake" else "bed"; asleep -> "wake"; else -> button }
        YadimoPet.act(context, button)
        val seq = RoomFrames.seq(context, id, kind)
        if (seq == null) { refresh(context, id); finished(); return }
        val line = LINES[kind]?.let { (fa, en) -> if (Prefs.language(context) == AppLanguage.FA) fa else en }
        show(context, id, kind, line)
        setSleeping(context, id, kind == "bed")
        main.postDelayed({
            // after waking up for a button, the button's own scene plays
            if (kind == "wake" && button != "sleep") show(context, id, button.takeIf { RoomFrames.seq(context, id, it) != null }, line)
            else show(context, id)
            if (kind == "wake" && button != "sleep") main.postDelayed({ show(context, id); finished() }, ((RoomFrames.seq(context, id, button)?.seconds ?: 1.0) * 1000).toLong())
            else finished()
        }, (seq.seconds * 1000).toLong() + 120)
    }
}
