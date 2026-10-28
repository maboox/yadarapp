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
 * The "Yadimo's home" widget: a window into the same room as the in-app game. It shows a still picture of the room
 * around Yadimo and a short breathing-and-blinking loop of Yadimo standing on the rug, always in the middle and on
 * the floor whatever the widget's size. The buttons open the game with that action, where it plays out fully.
 * The pictures are made once by the game's own drawing code (assets/yadimo/widgetframes.html) and kept on disk;
 * new ones replace the old only when they are complete, so the widget is never empty or half-made.
 */

/** What a widget's pictures depend on; when any of it changes they are made again. */
data class RoomSpec(val wDp: Int, val hDp: Int, val density: Float, val look: String, val night: Boolean, val fps: Int, val budget: Long) {
    val key: String get() = "v2|$wDp|$hDp|$density|${look.hashCode()}|$night|$fps"
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

    /** Keeps the process alive a little while the pictures are being made. */
    private fun keep(block: () -> Unit) {
        val pending = goAsync()
        block()
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { pending.finish() } }, 40_000)
    }
}

object RoomWidgets {
    private val main = Handler(Looper.getMainLooper())
    private val buttons = mapOf(R.id.room_b_feed to "feed", R.id.room_b_play to "play", R.id.room_b_charge to "charge",
        R.id.room_b_pet to "pet", R.id.room_b_sleep to "sleep")

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
        show(context, id)
        if (RoomFrames.meta(context, id).optString("key") != spec.key) RoomFrames.render(context, id, spec) { show(context, id) }
    }

    private fun open(context: Context, id: Int, act: String?): PendingIntent =
        PendingIntent.getActivity(context, 71_300 + id * 8 + (act?.let { buttons.values.indexOf(it) + 1 } ?: 0),
            Intent(context, YadimoRoomActivity::class.java).setData(Uri.parse("yadar://home/$id/${act ?: "open"}"))
                .putExtra("act", act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Puts the room, Yadimo's loop, a line and the buttons on the widget. */
    fun show(context: Context, id: Int) {
        val (w, h) = size(context, id)
        val rv = RemoteViews(context.packageName, R.layout.widget_room)
        val d = RoomFrames.dir(context, id)
        val meta = RoomFrames.meta(context, id)
        val bg = runCatching { BitmapFactory.decodeFile(File(d, "bg.png").path) }.getOrNull()
        if (bg != null) rv.setImageViewBitmap(R.id.room_bg, bg)
        rv.removeAllViews(R.id.room_flipper)
        val idle = meta.optJSONObject("idle")
        if (bg != null && idle != null) {
            val dpi = context.resources.displayMetrics.densityDpi
            val scale = idle.optDouble("scale", 1.0)
            val frames = (0 until idle.optInt("count")).mapNotNull { i ->
                runCatching { BitmapFactory.decodeFile(File(d, "idle_$i.png").path) }.getOrNull()
                    // drawn at full size although shrunk to fit the widget's memory
                    ?.also { it.density = (dpi * scale).toInt().coerceAtLeast(1) }
            }
            // forward and back, so the loop never jumps; the same pictures are reused, so it costs nothing more
            val order = if (frames.size > 2) frames + frames.subList(1, frames.size - 1).reversed() else frames
            for (b in order) RemoteViews(context.packageName, R.layout.widget_room_frame).also { it.setImageViewBitmap(R.id.room_frame, b); rv.addView(R.id.room_flipper, it) }
            rv.setInt(R.id.room_flipper, "setFlipInterval", 1000 / idle.optInt("fps", 15).coerceIn(1, 60))
            // Yadimo's feet on the rug: the pictures' bottom edge sits this far above the widget's bottom
            val bottom = (idle.optDouble("bottom", .1) * h * context.resources.displayMetrics.density).toInt().coerceAtLeast(0)
            rv.setViewPadding(R.id.room_char_box, 0, 0, 0, bottom)
        }
        rv.setTextViewText(R.id.room_bubble, if (bg == null) t("یادیمو داره خونه‌اش رو مرتب می‌کنه… 🏠", "Yadimo is tidying up… 🏠") else YadimoPet.line(context))
        rv.setViewVisibility(R.id.room_buttons, if (h < 165 || w < 150) View.GONE else View.VISIBLE)
        for ((view, act) in buttons) rv.setOnClickPendingIntent(view, open(context, id, act))
        rv.setOnClickPendingIntent(R.id.room_root, open(context, id, null))
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(id, rv) }.onFailure { android.util.Log.e("YadimoHome", "update failed", it) }
    }
}
