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
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File

/*
 * The "Yadimo's home" widget: a window into the same room as the in-app game. It shows a still picture of the room
 * around Yadimo and a short breathing-and-blinking loop of Yadimo standing on the rug, always in the middle and on
 * the floor whatever the widget's size. Tapping a thing in the room (the bowl, the ball, the pad, the bed, Yadimo)
 * plays a short scene right on the widget; the plant and the rest of the room open the game.
 * The pictures are made once by the game's own drawing code (assets/yadimo/widgetframes.html) and kept on disk;
 * new ones replace the old only when they are complete, so the widget is never empty or half-made.
 */

/** What a widget's pictures depend on; when any of it changes they are made again. */
data class RoomSpec(val wDp: Int, val hDp: Int, val density: Float, val look: String, val night: Boolean, val fps: Int, val budget: Long) {
    val key: String get() = "v3|$wDp|$hDp|$density|${look.hashCode()}|$night|$fps"
}

object RoomFrames {
    fun dir(context: Context, id: Int) = File(context.filesDir, "home/w$id")
    private fun tmp(context: Context, id: Int) = File(context.filesDir, "home/w$id.new")

    fun meta(context: Context, id: Int): JSONObject =
        runCatching { JSONObject(File(dir(context, id), "meta.json").readText()) }.getOrDefault(JSONObject())

    // ---------------- making the pictures ----------------

    private class Job(val context: Context, val id: Int, val spec: RoomSpec, val done: (Boolean) -> Unit) { var meta = JSONObject() }

    private var web: WebView? = null
    private var ready = false
    private var current: Job? = null
    private val queue = ArrayDeque<Job>()
    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { current?.let { finish(it, false) }; web?.destroy(); web = null; ready = false }

    /** Whether widget [id]'s pictures are being made right now. */
    fun busy(id: Int) = current?.id == id || queue.any { it.id == id }

    /** Makes widget [id]'s pictures for [spec] unless that is already being done; [done] runs on the main thread. */
    fun render(context: Context, id: Int, spec: RoomSpec, done: (Boolean) -> Unit) {
        main.post {
            if (current?.id == id && current?.spec == spec || queue.any { it.id == id && it.spec == spec }) return@post
            queue.removeAll { it.id == id }
            queue.addLast(Job(context.applicationContext, id, spec, done)); next(context.applicationContext)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun next(context: Context) {
        if (current != null) return
        val job = queue.removeFirstOrNull() ?: return
        current = job
        if (web == null) web = WebView(context).also { v ->
            v.settings.javaScriptEnabled = true
            v.setBackgroundColor(0)
            v.addJavascriptInterface(Host(), "RoomHost")
            v.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { ready = true; current?.let { start(it) } }
            }
            v.layout(0, 0, 400, 720)
            v.loadUrl("file:///android_asset/yadimo/widgetframes.html")
        }
        else if (ready) start(job)
        main.removeCallbacks(timeout); main.postDelayed(timeout, 60_000)
    }

    private fun start(job: Job) {
        tmp(job.context, job.id).apply { deleteRecursively(); mkdirs() }
        val o = JSONObject().put("w", job.spec.wDp).put("h", job.spec.hDp).put("density", job.spec.density.toDouble())
            .put("look", JSONObject(job.spec.look)).put("night", job.spec.night).put("fps", job.spec.fps)
            .put("frames", job.spec.fps.coerceIn(5, 30)).put("budget", job.spec.budget)
        web?.evaluateJavascript("widgetFrames.make(${JSONObject.quote(o.toString())})", null)
    }

    private fun finish(job: Job, ok: Boolean) {
        main.removeCallbacks(timeout)
        if (current === job) current = null
        if (ok) {
            // only complete sets of pictures replace the shown ones
            val t = tmp(job.context, job.id)
            File(t, "meta.json").writeText(job.meta.put("key", job.spec.key).toString())
            val d = dir(job.context, job.id); d.deleteRecursively(); t.renameTo(d)
        } else tmp(job.context, job.id).deleteRecursively()
        job.done(ok)
        next(job.context)
    }

    private fun png(data: String): ByteArray = Base64.decode(data.substringAfter(','), Base64.DEFAULT)

    /** Called by the page, on a background thread. */
    private class Host {
        private fun job() = current
        @JavascriptInterface fun background(data: String) { job()?.let { File(tmp(it.context, it.id), "bg.png").writeBytes(png(data)) } }
        @JavascriptInterface fun frame(kind: String, i: Int, data: String) { job()?.let { File(tmp(it.context, it.id), "${kind}_$i.png").writeBytes(png(data)) } }
        @JavascriptInterface fun done(kind: String, json: String) { job()?.let { it.meta.put(kind, JSONObject(json)) } }
        @JavascriptInterface fun finished() { job()?.let { j -> main.post { finish(j, true) } } }
        @JavascriptInterface fun error(message: String) { android.util.Log.e("YadimoHome", message); job()?.let { j -> main.post { finish(j, false) } } }
    }
}

class YadimoRoomWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = keep { ids.forEach { RoomWidgets.refresh(context, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = keep { RoomWidgets.refresh(context, id) }
    override fun onDeleted(context: Context, ids: IntArray) = ids.forEach { RoomFrames.dir(context, it).deleteRecursively() }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RoomWidgets.TAP) { super.onReceive(context, intent); return }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1); val act = intent.getStringExtra("act") ?: return
        val pending = goAsync()
        RoomWidgets.tap(context, id, act) { runCatching { pending.finish() } }
    }

    /** Keeps the process alive a little while the pictures are being made. */
    private fun keep(block: () -> Unit) {
        val pending = goAsync()
        block()
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { pending.finish() } }, 40_000)
    }
}

object RoomWidgets {
    const val TAP = "com.yadavard.app.ROOM_TAP"
    private val main = Handler(Looper.getMainLooper())
    private const val COLS = 6
    private const val ROWS = 4
    /** What a tap does, in the order they win when things overlap; water opens the game (the plant needs Yadimo to walk). */
    private val ACTS = listOf("pet", "play", "feed", "charge", "sleep", "water")
    private val CLIPS = setOf("pet", "play", "feed", "charge", "sleep")
    private val TAP_CELLS = intArrayOf(
        R.id.room_t0, R.id.room_t1, R.id.room_t2, R.id.room_t3, R.id.room_t4, R.id.room_t5, R.id.room_t6, R.id.room_t7, R.id.room_t8, R.id.room_t9, R.id.room_t10, R.id.room_t11, R.id.room_t12,
        R.id.room_t13, R.id.room_t14, R.id.room_t15, R.id.room_t16, R.id.room_t17, R.id.room_t18, R.id.room_t19, R.id.room_t20, R.id.room_t21, R.id.room_t22, R.id.room_t23)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("room_widgets", Context.MODE_PRIVATE)

    /** Frames per second of Yadimo's loop on the widget. */
    fun fps(context: Context) = prefs(context).getInt("fps", 15)
    fun setFps(context: Context, v: Int) { prefs(context).edit().putInt("fps", v).apply(); refreshAll(context) }

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

    /** The widget's size in dp as the launcher reports it (portrait: the narrow width and the tall height). */
    private fun size(context: Context, id: Int): Pair<Int, Int> {
        val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val portrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val w = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)).takeIf { it > 0 } ?: 320
        val h = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)).takeIf { it > 0 } ?: 160
        return w to h
    }

    fun spec(context: Context, id: Int): RoomSpec {
        val (w, h) = size(context, id)
        val dm = context.resources.displayMetrics
        val density = dm.density.coerceAtMost(3f)
        // a widget may use about 1.5 screens' worth of pictures; the room picture takes its share first
        val limit = dm.widthPixels.toLong() * dm.heightPixels * 4 * 3 / 2
        val budget = ((limit - (w * density).toLong() * (h * density).toLong() * 4) * 0.7).toLong().coerceIn(2_000_000L, 9_000_000L)
        YadimoStore.ensure(context)
        return RoomSpec(w, h, density, YadimoStore.config.toJson(), YadimoPet.night(), fps(context), budget)
    }

    /** Shows the pictures that are ready, and makes new ones when the size, look, time of day or speed changed. */
    fun refresh(context: Context, id: Int) {
        AppDisplay.load(context)
        File(context.filesDir, "room").takeIf { it.exists() }?.deleteRecursively()   // pictures of the old room widget
        val spec = spec(context, id)
        val stale = RoomFrames.meta(context, id).optString("key") != spec.key
        show(context, id, busy = stale)
        if (stale) RoomFrames.render(context, id, spec) { show(context, id) }
    }

    private fun open(context: Context, id: Int, act: String?): PendingIntent =
        PendingIntent.getActivity(context, 71_300 + id * 8 + ACTS.indexOf(act) + 1,
            Intent(context, YadimoRoomActivity::class.java).setData(Uri.parse("yadar://home/$id/${act ?: "open"}"))
                .putExtra("act", act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun tapIntent(context: Context, id: Int, act: String): PendingIntent =
        PendingIntent.getBroadcast(context, 72_300 + id * 8 + ACTS.indexOf(act),
            Intent(context, YadimoRoomWidget::class.java).setAction(TAP).setData(Uri.parse("yadar://home/$id/tap/$act"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("act", act),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** What is under each tap cell, from where the page said the things are drawn (null: open the game). */
    private fun cells(meta: JSONObject): List<String?> {
        val taps = meta.optJSONObject("taps") ?: return List(COLS * ROWS) { null }
        return List(COLS * ROWS) { i ->
            val x = (i % COLS + .5) / COLS; val y = (i / COLS + .5) / ROWS
            ACTS.firstOrNull { a -> taps.optJSONArray(a)?.let { r -> x >= r.optDouble(0) - .04 && x <= r.optDouble(2) + .04 && y >= r.optDouble(1) - .04 && y <= r.optDouble(3) + .04 } == true }
        }
    }

    /** What Yadimo says while a scene plays. */
    private fun reaction(act: String) = when (act) {
        "feed" -> t("نوش جان! 😋", "Yum! 😋")
        "play" -> t("گل! ⚽😄", "Goal! ⚽😄")
        "charge" -> t("شارژ شدم! 💯", "Fully charged! 💯")
        "sleep" -> t("یه چرت کوچولو… 😴", "A little nap… 😴")
        else -> t("آخ جون! 🥰", "Yay! 🥰")
    }

    /** The scene playing on each widget, and what to do when it ends. */
    private val playing = HashMap<Int, Pair<Runnable, () -> Unit>>()

    /** Something in the room was tapped: Yadimo's needs go up, and the scene plays on the widget, then the loop again. */
    fun tap(context: Context, id: Int, act: String, finished: () -> Unit) {
        AppDisplay.load(context)
        prefs(context).edit().putBoolean("tapped", true).apply()
        YadimoPet.act(context, act)
        playing.remove(id)?.let { (r, f) -> main.removeCallbacks(r); f() }
        val clip = RoomFrames.meta(context, id).optJSONObject(act)
        if (act !in CLIPS || clip == null) { show(context, id); finished(); return }
        show(context, id, act)
        val back = Runnable { playing.remove(id); show(context, id); finished() }
        playing[id] = back to finished
        main.postDelayed(back, clip.optInt("count") * 1000L / clip.optInt("fps", 10).coerceIn(1, 60) + 350)
    }

    /** Puts the room, Yadimo's loop (or the scene [clip]), a line and the places to tap on the widget. */
    fun show(context: Context, id: Int, clip: String? = null, busy: Boolean = RoomFrames.busy(id)) {
        val (_, h) = size(context, id)
        val rv = RemoteViews(context.packageName, R.layout.widget_room)
        val d = RoomFrames.dir(context, id)
        val meta = RoomFrames.meta(context, id)
        val bg = runCatching { BitmapFactory.decodeFile(File(d, "bg.png").path) }.getOrNull()
        if (bg != null) rv.setImageViewBitmap(R.id.room_bg, bg)
        rv.removeAllViews(R.id.room_flipper)
        val kind = clip ?: "idle"
        val m = meta.optJSONObject(kind)
        if (bg != null && m != null) {
            val dpi = context.resources.displayMetrics.densityDpi
            val scale = m.optDouble("scale", 1.0)
            val frames = (0 until m.optInt("count")).mapNotNull { i ->
                runCatching { BitmapFactory.decodeFile(File(d, "${kind}_$i.png").path) }.getOrNull()
                    // drawn at full size although shrunk to fit the widget's memory
                    ?.also { it.density = (dpi * scale).toInt().coerceAtLeast(1) }
            }
            // the loop goes forward and back, so it never jumps; a scene plays once and holds its last picture.
            // The same pictures are reused, so neither costs anything more
            val order = when {
                clip != null && frames.isNotEmpty() -> frames + List(8) { frames.last() }
                frames.size > 2 -> frames + frames.subList(1, frames.size - 1).reversed()
                else -> frames
            }
            for (b in order) RemoteViews(context.packageName, R.layout.widget_room_frame).also { it.setImageViewBitmap(R.id.room_frame, b); rv.addView(R.id.room_flipper, it) }
            rv.setInt(R.id.room_flipper, "setFlipInterval", 1000 / m.optInt("fps", 15).coerceIn(1, 60))
            // Yadimo's feet on the rug: the pictures' bottom edge sits this far above the widget's bottom
            val bottom = (m.optDouble("bottom", .1) * h * context.resources.displayMetrics.density).toInt().coerceAtLeast(0)
            rv.setViewPadding(R.id.room_char_box, 0, 0, 0, bottom)
        }
        rv.setTextViewText(R.id.room_bubble, when {
            clip != null -> reaction(clip)
            bg == null || busy -> t("یادیمو داره اتاقش رو مرتب می‌کنه… 🏠", "Yadimo is tidying up… 🏠")
            !prefs(context).getBoolean("tapped", false) -> t("به وسایل اتاق دست بزن! 👆", "Tap things in the room! 👆")
            else -> YadimoPet.line(context)
        })
        cells(meta).forEachIndexed { i, act ->
            rv.setOnClickPendingIntent(TAP_CELLS[i], if (act != null && act in CLIPS && bg != null) tapIntent(context, id, act) else open(context, id, act))
        }
        rv.setOnClickPendingIntent(R.id.room_root, open(context, id, null))
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(id, rv) }.onFailure { android.util.Log.e("YadimoHome", "update failed", it) }
    }
}
