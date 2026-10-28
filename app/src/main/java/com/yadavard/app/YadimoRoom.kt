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
 * The "Yadimo's home" widget: a little window to watch, not a remote control. Yadimo lives its own life there
 * (waters the plant, reads, looks outside, plays with its ball, eats, sleeps at night) and talks to itself now
 * and then; its needs show at the top, and tapping anywhere opens the game.
 * The pictures are made once by the game's own drawing code (assets/yadimo/widgetframes.html) and kept on disk:
 * a still picture of the room and small pictures ("sprites") of Yadimo, plus episodes saying which sprite goes
 * where in each frame. New pictures replace the old only when complete, so the widget is never empty or half-made.
 */

/** What a widget's pictures depend on; when any of it changes they are made again. */
data class RoomSpec(val wDp: Int, val hDp: Int, val density: Float, val look: String, val lang: String, val fps: Int, val budget: Long) {
    val key: String get() = "v4|$wDp|$hDp|$density|${look.hashCode()}|$lang|$fps"
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
            .put("look", JSONObject(job.spec.look)).put("lang", job.spec.lang).put("fps", job.spec.fps).put("budget", job.spec.budget)
        web?.evaluateJavascript("widgetFrames.make(${JSONObject.quote(o.toString())})", null)
    }

    private fun finish(job: Job, ok: Boolean) {
        main.removeCallbacks(timeout)
        if (current === job) current = null
        if (ok) {
            // only complete sets of pictures replace the shown ones
            val t = tmp(job.context, job.id)
            File(t, "meta.json").writeText(job.meta.put("key", job.spec.key).put("density", job.spec.density.toDouble()).toString())
            val d = dir(job.context, job.id); d.deleteRecursively(); t.renameTo(d)
        } else tmp(job.context, job.id).deleteRecursively()
        job.done(ok)
        next(job.context)
    }

    private fun png(data: String): ByteArray = Base64.decode(data.substringAfter(','), Base64.DEFAULT)

    /** Called by the page, on a background thread. */
    private class Host {
        private fun job() = current
        @JavascriptInterface fun file(name: String, data: String) { job()?.let { File(tmp(it.context, it.id), "$name.png").writeBytes(png(data)) } }
        @JavascriptInterface fun finished(json: String) { job()?.let { j -> j.meta = JSONObject(json); main.post { finish(j, true) } } }
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

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("room_widgets", Context.MODE_PRIVATE)

    /** How smooth Yadimo moves on the widget: the setting (5, 15 or 30) and the frames per second it gives. */
    fun fps(context: Context) = prefs(context).getInt("fps", 15)
    fun setFps(context: Context, v: Int) { prefs(context).edit().putInt("fps", v).apply(); refreshAll(context) }
    private fun frameRate(setting: Int) = when { setting <= 5 -> 5; setting <= 15 -> 8; else -> 12 }

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
        AppDisplay.load(context)
        return RoomSpec(w, h, density, YadimoStore.config.toJson(), if (AppDisplay.language == AppLanguage.FA) "fa" else "en", frameRate(fps(context)), budget)
    }

    /** Shows the pictures that are ready, and makes new ones when the size, look, language or speed changed. */
    fun refresh(context: Context, id: Int) {
        AppDisplay.load(context)
        File(context.filesDir, "room").takeIf { it.exists() }?.deleteRecursively()   // pictures of the old room widget
        val spec = spec(context, id)
        val stale = RoomFrames.meta(context, id).optString("key") != spec.key
        show(context, id, busy = stale)
        if (stale) RoomFrames.render(context, id, spec) { show(context, id) }
    }

    private fun open(context: Context, id: Int): PendingIntent =
        PendingIntent.getActivity(context, 71_300 + id,
            Intent(context, YadimoRoomActivity::class.java).setData(Uri.parse("yadar://home/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Which episode plays now: asleep at night, eating when hungry, otherwise a different one every 20 minutes. */
    private fun episode(context: Context, episodes: JSONObject): String {
        val s = YadimoPet.stats(context)
        val day = listOf("garden", "bored", "play").filter { episodes.has(it) }
        return when {
            YadimoPet.night() && episodes.has("night") -> "night"
            s.hunger < 40 && episodes.has("hungry") -> "hungry"
            day.isNotEmpty() -> day[((System.currentTimeMillis() / 1_200_000) % day.size).toInt()]
            else -> episodes.keys().asSequence().firstOrNull() ?: ""
        }
    }

    /** Puts the room, the episode playing in it and Yadimo's needs on the widget. */
    fun show(context: Context, id: Int, busy: Boolean = RoomFrames.busy(id)) {
        AppDisplay.load(context)
        val (_, h) = size(context, id)
        val rv = RemoteViews(context.packageName, R.layout.widget_room)
        val d = RoomFrames.dir(context, id)
        val meta = RoomFrames.meta(context, id)
        val episodes = meta.optJSONObject("episodes")
        rv.removeAllViews(R.id.room_flipper)
        if (episodes != null) runCatching {
            val name = episode(context, episodes)
            val ep = episodes.getJSONObject(name)
            BitmapFactory.decodeFile(File(d, "bg_${ep.optString("bg", "day")}.png").path)?.let { rv.setImageViewBitmap(R.id.room_bg, it) }
            val dm = context.resources.displayMetrics
            // the pictures were made for at most 3x screens; on denser ones everything is stretched by this much
            val k = dm.density / meta.optDouble("density", dm.density.toDouble()).toFloat()
            val sprites = meta.getJSONArray("sprites")
            val cache = HashMap<String, Bitmap?>()
            fun picture(file: String, shownW: Int): Bitmap? = cache.getOrPut(file) {
                BitmapFactory.decodeFile(File(d, "$file.png").path)?.also { b ->
                    // drawn at full size although made smaller to fit the widget's memory
                    b.density = (dm.densityDpi * b.width / (shownW * k)).toInt().coerceAtLeast(1)
                }
            }
            val pxW = meta.optInt("w"); val pxH = meta.optInt("h")
            val frames = ep.getJSONArray("frames")
            for (i in 0 until frames.length()) {
                val f = frames.getJSONObject(i)
                val sp = sprites.getJSONObject(f.getInt("s"))
                val img = picture("s${f.getInt("s")}", sp.getInt("w")) ?: continue
                val fr = RemoteViews(context.packageName, R.layout.widget_room_frame)
                fr.setImageViewBitmap(R.id.f_img, img)
                fr.setViewPadding(R.id.f_char, (f.getInt("x") * k).toInt(), (f.getInt("y") * k).toInt(), 0, 0)
                if (f.has("ball")) picture("ball${f.getInt("ball")}", meta.optInt("ball"))?.let { b ->
                    fr.setImageViewBitmap(R.id.f_ball_img, b); fr.setViewVisibility(R.id.f_ball, View.VISIBLE)
                    fr.setViewPadding(R.id.f_ball, (f.getInt("bx") * k).toInt(), (f.getInt("by") * k).toInt(), 0, 0)
                }
                if (f.has("say")) {
                    // centred over Yadimo's head: the free space on one side moves the centre to it
                    // …but never over the needs at the top
                    val hx = f.getInt("hx"); val top = ((if (h >= 110) 54 else 32) * dm.density / k).toInt()
                    val hy = f.getInt("hy").coerceAtLeast(top)
                    fr.setTextViewText(R.id.f_text, f.getString("say")); fr.setViewVisibility(R.id.f_say, View.VISIBLE)
                    fr.setViewPadding(R.id.f_say, (maxOf(0, 2 * hx - pxW) * k).toInt(), 0, (maxOf(0, pxW - 2 * hx) * k).toInt(), (maxOf(0, pxH - hy) * k).toInt())
                }
                rv.addView(R.id.room_flipper, fr)
            }
            rv.setInt(R.id.room_flipper, "setFlipInterval", 1000 / meta.optInt("fps", 8).coerceIn(1, 30))
        }.onFailure { android.util.Log.e("YadimoHome", "episode", it) }
        // while new pictures are being made, the old ones keep playing with a note over them
        rv.setTextViewText(R.id.room_status, t("یادیمو داره اتاقش رو مرتب می‌کنه… 🏠", "Yadimo is tidying up… 🏠"))
        rv.setViewVisibility(R.id.room_status, if (busy || episodes == null) View.VISIBLE else View.GONE)
        val s = YadimoPet.stats(context)
        rv.setProgressBar(R.id.room_hunger, 100, s.hunger, false)
        rv.setProgressBar(R.id.room_energy, 100, s.energy, false)
        rv.setProgressBar(R.id.room_joy, 100, s.joy, false)
        rv.setViewVisibility(R.id.room_stats, if (h >= 110) View.VISIBLE else View.GONE)
        rv.setOnClickPendingIntent(R.id.room_root, open(context, id))
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(id, rv) }.onFailure { android.util.Log.e("YadimoHome", "update failed", it) }
    }
}
