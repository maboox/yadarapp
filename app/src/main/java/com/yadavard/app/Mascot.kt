package com.yadavard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * The Yadar mascot: a little living bell. Every part (body, legs, arms, face, hat, glasses, item, outfit) is a small
 * SVG layer file in assets (made by tools/export-mascot.js from docs/mascot-lab.html). The layers are drawn on top of
 * each other and moved by a few formulas of time, so the same parts give every mood and every combination.
 */

/** Which parts the user picked. Only ids are stored. */
data class MascotConfig(
    val face: String = "happy", val head: String = "none", val glasses: String = "none",
    val item: String = "none", val outfit: String = "none", val color: String = "lav",
) {
    fun toJson(): String = JSONObject().put("face", face).put("head", head).put("glasses", glasses)
        .put("item", item).put("outfit", outfit).put("color", color).toString()

    companion object {
        fun fromJson(raw: String?): MascotConfig = runCatching {
            val o = JSONObject(raw ?: return MascotConfig())
            MascotConfig(o.optString("face", "happy"), o.optString("head", "none"), o.optString("glasses", "none"),
                o.optString("item", "none"), o.optString("outfit", "none"), o.optString("color", "lav"))
        }.getOrDefault(MascotConfig())
    }
}

enum class MascotState { IDLE, WAVE, THINK, TALK, RING, CELEBRATE, SLEEP }

/** The mascot the whole app shows; changing it here updates every screen. */
object MascotStore {
    var config by mutableStateOf(MascotConfig())
        private set
    private var loaded = false

    fun ensure(context: Context) {
        if (!loaded) { config = MascotConfig.fromJson(Prefs.mascotJson(context)); loaded = true }
    }

    fun set(context: Context, value: MascotConfig) {
        config = value; loaded = true
        Prefs.setMascotJson(context, value.toJson())
    }
}

@Composable
fun rememberMascotConfig(): MascotConfig {
    MascotStore.ensure(LocalContext.current)
    return MascotStore.config
}

class MascotPart(val id: String, val name: String, val test: Boolean = false, val back: Boolean = false, val front: Boolean = false)
class MascotFace(val id: String, val name: String, val cy: Float, val hasEyes: Boolean, val hasMouth: Boolean)
class MascotColor(val id: String, val name: String, val swatch: Color)

/** What parts exist (read from catalog.json; the test catalog only exists in the test builds). */
object MascotCatalog {
    var colors: List<MascotColor> = emptyList(); private set
    var faces: List<MascotFace> = emptyList(); private set
    var heads: List<MascotPart> = emptyList(); private set
    var glasses: List<MascotPart> = emptyList(); private set
    var items: List<MascotPart> = emptyList(); private set
    var outfits: List<MascotPart> = emptyList(); private set
    private var loaded = false

    fun hasTest() = listOf(heads, glasses, items, outfits).any { l -> l.any { it.test } }

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val am = context.applicationContext.assets
        fun read(path: String): JSONObject? = runCatching { JSONObject(am.open(path).bufferedReader().use { it.readText() }) }.getOrNull()
        fun parts(o: JSONObject?, key: String): List<MascotPart> {
            val a = o?.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                MascotPart(p.getString("id"), p.optString("n", p.getString("id")), p.optBoolean("test"), p.optBoolean("back"), p.optBoolean("front"))
            }
        }
        val main = read("mascot/catalog.json"); val test = read("mascot_test/catalog_test.json")
        colors = main?.optJSONArray("palettes")?.let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                MascotColor(p.getString("id"), p.optString("n"), Color(android.graphics.Color.parseColor(p.optString("sw", "#b8a6ff"))))
            }
        }.orEmpty()
        faces = main?.optJSONArray("faces")?.let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                MascotFace(p.getString("id"), p.optString("n"), p.optDouble("cy", 158.0).toFloat(), p.optBoolean("eyes"), p.optBoolean("mouth"))
            }
        }.orEmpty()
        heads = parts(main, "heads") + parts(test, "heads")
        glasses = parts(main, "glasses") + parts(test, "glasses")
        items = parts(main, "items") + parts(test, "items")
        outfits = parts(main, "outfits") + parts(test, "outfits")
        loaded = faces.isNotEmpty()
    }

    private fun dir(list: List<MascotPart>, id: String) = if (list.any { it.id == id && it.test }) "mascot_test/" else "mascot/"
    fun headPath(id: String) = "${dir(heads, id)}head_$id.svg"
    fun glassesPath(id: String) = "${dir(glasses, id)}glasses_$id.svg"
    fun itemPath(id: String) = "${dir(items, id)}item_$id.svg"
    fun outfit(id: String) = outfits.firstOrNull { it.id == id }
    fun outfitPath(id: String, side: String) = "${dir(outfits, id)}out_${id}_$side.svg"
    fun face(id: String) = faces.firstOrNull { it.id == id } ?: faces.firstOrNull()
}

/** Turns SVG layer files into bitmaps once and keeps them. */
object MascotArt {
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    /** Resolution steps, so a few sizes share the same bitmaps. */
    fun bucket(pxPerUnit: Float): Float = listOf(0.3f, 0.5f, 0.75f, 1f, 1.5f, 2f).firstOrNull { it >= pxPerUnit * 0.85f } ?: 2f

    /** Only what is already loaded; drawing never decodes anything on the main thread. */
    fun cached(path: String, scale: Float): Bitmap? = cache.get("$path@$scale")

    @Synchronized
    fun bitmap(context: Context, path: String, scale: Float): Bitmap? {
        val key = "$path@$scale"
        cache.get(key)?.let { return it }
        if (key in missing) return null
        return try {
            val svg = SVG.getFromAsset(context.applicationContext.assets, path)
            val box = svg.documentViewBox
            val w = (box.width() * scale).toInt().coerceAtLeast(1)
            val h = (box.height() * scale).toInt().coerceAtLeast(1)
            svg.documentWidth = w.toFloat(); svg.documentHeight = h.toFloat()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            cache.put(key, bmp); bmp
        } catch (_: Exception) { missing.add(key); null }
    }
}

/** The motion: every mood is a small formula of time. This mirrors the formulas of docs/mascot-lab.html. */
class MascotAnimator {
    var state = MascotState.IDLE
    var base = Long.MIN_VALUE
    private var t0 = Float.NaN
    var dy = 0f; var sx = 1f; var sy = 1f; var wob = 0f; var aL = 36f; var aR = -36f
    var mouth = 1f; var bulb = 0f; var lx = 0f; var ly = 0f; var blink = 1f
    private var last = 0f
    private var nextBlink = 2.5f
    private var blinkStart = -9f

    fun moveTo(s: MascotState) { if (s != state) { state = s; t0 = Float.NaN } }

    fun tick(t: Float, hasItem: Boolean) {
        if (t0.isNaN()) t0 = t
        val dt = (t - last).coerceIn(0f, 0.05f); last = t
        val s = t - t0
        var tdy = 0f; var tsx = 1f; var tsy = 1f; var twob = 0f; var taL = 36f; var taR = -36f; var tmouth = 1f; var tbulb = 0f
        when (state) {
            MascotState.IDLE -> { tdy = sin(t * 2) * 2.2f; tsy = 1 + sin(t * 2) * .012f; taL = 36 + sin(t * 2 + 1) * 3; taR = -36 - sin(t * 2 + 2) * 3 }
            MascotState.WAVE -> { tdy = sin(t * 2) * 2; twob = sin(t * 3) * 1.6f; taL = 120 + sin(t * 9) * 14 }
            MascotState.THINK -> { tdy = sin(t * 1.6f) * 1.5f; twob = -5 + sin(t * 1.4f); tbulb = ((s - 1.6f) * 3).coerceIn(0f, 1f) }
            MascotState.TALK -> { tdy = sin(t * 2) * 2; taR = -60 + sin(t * 4) * 16; twob = sin(t * 3.2f) * 1.4f
                tmouth = .25f + .75f * abs(sin(t * 11) * sin(t * 3.1f + 1)) }
            MascotState.RING -> { tdy = -2f; twob = sin(t * 34) * 7; taL = 75f; taR = -75f; tmouth = .8f + .2f * sin(t * 18) }
            MascotState.CELEBRATE -> {
                val b = abs(sin(t * 4.4f)); tdy = -b * 38
                if (b < .12f) { tsy = 1 - .1f * (1 - b / .12f); tsx = 1 + .07f * (1 - b / .12f) }
                taL = 135 + sin(t * 12) * 10; taR = -135 - sin(t * 12 + 1) * 10; twob = sin(t * 4.4f) * 3
            }
            MascotState.SLEEP -> { tdy = sin(t * 1.2f) * 1.4f; tsy = 1 + sin(t * 1.2f) * .022f; twob = 4f; taL = 24f; taR = -24f; tmouth = .55f + .35f * sin(t * 1.2f) }
        }
        // with an item in hand the right arm holds it out
        if (hasItem && (state == MascotState.IDLE || state == MascotState.THINK || state == MascotState.TALK)) taR = -82 + sin(t * 2.2f) * 3
        val a = 1 - exp(-dt * 12)
        dy += (tdy - dy) * a; sx += (tsx - sx) * a; sy += (tsy - sy) * a; wob += (twob - wob) * a
        aL += (taL - aL) * a; aR += (taR - aR) * a; mouth += (tmouth - mouth) * a; bulb += (tbulb - bulb) * a
        // the face drifts a little by itself, as if it were looking around
        val g = 1 - exp(-dt * 8)
        lx += (sin(t * .6f) * .5f - lx) * g; ly += (sin(t * .45f) * .25f - ly) * g
        if (t > nextBlink) { blinkStart = t; nextBlink = t + 2 + (Math.random() * 3).toFloat() }
        val bp = (t - blinkStart) / .16f
        blink = if (bp in 0f..1f) 1 - .92f * sin(PI.toFloat() * bp) else 1f
        if (state == MascotState.SLEEP) blink = 1f
    }
}

private val DEST = RectF(-30f, -50f, 330f, 348f)
private val ITEM_DEST = RectF(-70f, -110f, 70f, 30f)
private val BULB_DEST = RectF(-50f, -50f, 50f, 50f)
private const val VB_W = 360f
private const val VB_H = 398f

private fun faceFor(state: MascotState, chosen: String) = when (state) {
    MascotState.IDLE -> chosen
    MascotState.WAVE, MascotState.TALK -> "happy"
    MascotState.THINK -> "think"
    MascotState.RING -> "wow"
    MascotState.CELEBRATE -> "joy"
    MascotState.SLEEP -> "sleepy"
}

/** Every asset the mascot needs right now (used to load them in the background before drawing). */
private fun assetPaths(c: MascotConfig, faceId: String): List<String> = buildList {
    val out = MascotCatalog.outfit(c.outfit)
    if (out != null && out.back) add(MascotCatalog.outfitPath(c.outfit, "back"))
    add("mascot/legs_${c.color}.svg"); add("mascot/armL_${c.color}.svg"); add("mascot/armR_${c.color}.svg")
    if (c.head == "none") add("mascot/handle_${c.color}.svg")
    add("mascot/body_${c.color}.svg")
    if (out != null && out.front) add(MascotCatalog.outfitPath(c.outfit, "front"))
    val f = MascotCatalog.face(faceId)
    if (f != null) {
        add("mascot/face_${f.id}_rest.svg")
        if (f.hasEyes) add("mascot/face_${f.id}_eyes.svg")
        if (f.hasMouth) add("mascot/face_${f.id}_mouth.svg")
    }
    if (c.glasses != "none") add(MascotCatalog.glassesPath(c.glasses))
    if (c.head != "none") add(MascotCatalog.headPath(c.head))
    if (c.item != "none") add(MascotCatalog.itemPath(c.item))
    add("mascot/fx_waves.svg"); add("mascot/fx_bulb.svg")
}

private val CONFETTI = intArrayOf(0xFFFFD94A.toInt(), 0xFFFF8FB4.toInt(), 0xFF6FD1A8.toInt(), 0xFF78B4F2.toInt(), 0xFF9A82EE.toInt())

private fun drawMascot(c: Canvas, ctx: Context, cfg: MascotConfig, a: MascotAnimator, faceId: String, pxPerUnit: Float, bmpScale: Float, t: Float, paint: Paint, text: Paint) {
    fun layer(path: String, dest: RectF = DEST) { MascotArt.cached(path, bmpScale)?.let { c.drawBitmap(it, null, dest, paint) } }
    val out = MascotCatalog.outfit(cfg.outfit)
    val face = MascotCatalog.face(faceId)
    c.save(); c.scale(pxPerUnit, pxPerUnit); c.translate(30f, 50f)   // viewBox origin is (-30, -50)

    // ground shadow
    paint.color = 0x29241B4D; paint.style = Paint.Style.FILL
    val sw = 86f * (1 + a.dy * .012f)
    c.drawOval(RectF(150f - sw, 314f, 150f + sw, 336f), paint)
    paint.color = 0xFFFFFFFF.toInt()

    c.save(); c.translate(0f, a.dy); c.translate(150f, 324f); c.scale(a.sx, a.sy); c.translate(-150f, -324f)
    c.save(); c.rotate(a.wob, 150f, 285f)
    if (out != null && out.back) layer(MascotCatalog.outfitPath(cfg.outfit, "back"))
    layer("mascot/legs_${cfg.color}.svg")
    c.save(); c.rotate(a.aL, 76f, 196f); layer("mascot/armL_${cfg.color}.svg"); c.restore()
    c.save(); c.rotate(a.aR, 224f, 196f); layer("mascot/armR_${cfg.color}.svg"); c.restore()
    if (cfg.head == "none") layer("mascot/handle_${cfg.color}.svg")
    layer("mascot/body_${cfg.color}.svg")
    if (out != null && out.front) layer(MascotCatalog.outfitPath(cfg.outfit, "front"))
    if (face != null) {
        c.save(); c.translate(a.lx * 4, a.ly * 3)
        layer("mascot/face_${face.id}_rest.svg")
        if (face.hasEyes) {
            c.save(); c.translate(a.lx * 2, a.ly * 1.5f + face.cy * (1 - a.blink)); c.scale(1f, a.blink)
            layer("mascot/face_${face.id}_eyes.svg"); c.restore()
        }
        if (face.hasMouth) {
            c.save(); c.translate(0f, 182f); c.scale(1f, a.mouth); c.translate(0f, -182f)
            layer("mascot/face_${face.id}_mouth.svg"); c.restore()
        }
        if (cfg.glasses != "none") layer(MascotCatalog.glassesPath(cfg.glasses))
        c.restore()
    }
    if (cfg.head != "none") layer(MascotCatalog.headPath(cfg.head))
    if (a.state == MascotState.RING) {
        paint.alpha = (140 + 115 * sin(t * 12.5f)).toInt().coerceIn(40, 255)
        layer("mascot/fx_waves.svg"); paint.alpha = 255
    }
    if (a.bulb > 0.02f) { c.save(); c.translate(250f, 24f); c.scale(a.bulb, a.bulb); layer("mascot/fx_bulb.svg", BULB_DEST); c.restore() }
    c.restore()   // wob

    if (cfg.item != "none") {
        val r = Math.toRadians(a.aR.toDouble())
        val hx = (224 - sin(r) * 44).toFloat(); val hy = (196 + cos(r) * 44).toFloat()
        c.save(); c.translate(hx, hy); c.rotate(a.wob * .5f); c.scale(1.35f, 1.35f)
        layer(MascotCatalog.itemPath(cfg.item), ITEM_DEST); c.restore()
    }
    if (a.state == MascotState.SLEEP) {
        val sizes = floatArrayOf(28f, 21f, 16f); val xs = floatArrayOf(206f, 224f, 236f); val ys = floatArrayOf(44f, 22f, 2f)
        for (k in 0..2) {
            var ph = (t / 2.6f - k * .35f) % 1f; if (ph < 0) ph += 1f
            val alpha = if (ph < .2f) ph / .2f else 1 - (ph - .2f) / .8f
            text.textSize = sizes[k]; text.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            c.drawText("z", xs[k] + 16 * ph, ys[k] - 40 * ph, text)
        }
    }
    c.restore()   // bounce

    if (a.state == MascotState.CELEBRATE) {
        for (i in 0 until 16) {
            val dur = 1.8f + (i % 4) * .3f
            var p = ((t + i * .137f) / dur) % 1f; if (p < 0) p += 1f
            val dx = (if (i % 2 == 1) 1 else -1) * (10 + (i * 7) % 30) * p
            val cx = -10f + i * 21f + 4f + dx; val cy = -60f + 420f * p
            paint.color = CONFETTI[i % 5]; paint.alpha = (255 * (1 - .1f * p)).toInt()
            c.save(); c.rotate(540f * p, cx, cy); c.drawRoundRect(RectF(cx - 4f, cy - 6f, cx + 4f, cy + 6f), 2f, 2f, paint); c.restore()
        }
        paint.color = 0xFFFFFFFF.toInt(); paint.alpha = 255
    }
    c.restore()
}

/** The mascot, animated. [size] is its width; the height follows the picture's proportions. */
@Composable
fun MascotView(config: MascotConfig, state: MascotState, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val ctx = LocalContext.current.applicationContext
    MascotCatalog.load(ctx)
    val anim = remember { MascotAnimator() }
    anim.moveTo(state)
    val faceId = faceFor(state, config.face)
    val density = LocalDensity.current
    val pxPerUnit = with(density) { size.toPx() } / VB_W
    val bmpScale = MascotArt.bucket(pxPerUnit)
    var frame by remember { mutableLongStateOf(0L) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val text = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6A4FD8.toInt(); typeface = Typeface.DEFAULT_BOLD } }
    // load the pictures off the main thread first, then keep drawing frames
    LaunchedEffect(config, faceId, bmpScale) {
        withContext(Dispatchers.Default) {
            // also the faces of the other moods, so a mood change never shows an empty face
            val faces = listOf(faceId, config.face, "happy", "think", "wow", "joy", "sleepy").distinct()
            (assetPaths(config, faceId) + faces.flatMap { assetPaths(config, it).filter { p -> p.contains("/face_") } }).distinct()
                .forEach { MascotArt.bitmap(ctx, it, bmpScale) }
        }
    }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    ComposeCanvas(modifier.size(size, size * (VB_H / VB_W))) {
        val f = frame
        if (anim.base == Long.MIN_VALUE) anim.base = f
        val t = ((f - anim.base) / 1_000_000_000.0).toFloat()
        anim.tick(t, config.item != "none")
        drawIntoCanvas { drawMascot(it.nativeCanvas, ctx, config, anim, faceId, pxPerUnit, bmpScale, t, paint, text) }
    }
}

private fun stateLabel(s: MascotState) = when (s) {
    MascotState.IDLE -> t("ایستاده", "Idle"); MascotState.WAVE -> t("سلام", "Wave"); MascotState.THINK -> t("فکر", "Think")
    MascotState.TALK -> t("حرف زدن", "Talk"); MascotState.RING -> t("زنگ", "Ring"); MascotState.CELEBRATE -> t("جشن", "Party")
    MascotState.SLEEP -> t("خواب", "Sleep")
}

/** Settings: live preview with the moods, and a row of choices for every part. */
@Composable
fun MascotCustomizer() {
    val ctx = LocalContext.current
    MascotStore.ensure(ctx); MascotCatalog.load(ctx)
    val cfg = MascotStore.config
    var state by remember { mutableStateOf(MascotState.IDLE) }
    fun update(c: MascotConfig) = MascotStore.set(ctx, c)

    @Composable
    fun PartRow(title: String, parts: List<MascotPart>, selected: String, pick: (String) -> Unit) {
        if (parts.isEmpty()) return
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(parts, key = { (if (it.test) "t:" else "m:") + it.id }) { p ->
                FilterChip(selected == p.id, { pick(p.id) }, { Text(if (p.id == "none") t("بدون", "None") else p.name) })
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))),
            contentAlignment = Alignment.Center) { MascotView(cfg, state, Modifier.padding(vertical = 8.dp), size = 200.dp) }
        Text(t("حالت (فقط برای دیدن)", "Mood (preview only)"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MascotState.entries.toList()) { s -> FilterChip(state == s, { state = s }, { Text(stateLabel(s)) }) }
        }
        Text(t("رنگ بدنه", "Body colour"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MascotCatalog.colors, key = { it.id }) { col ->
                Box(Modifier.size(34.dp).clip(CircleShape).background(col.swatch)
                    .border(if (cfg.color == col.id) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { update(cfg.copy(color = col.id)) })
            }
        }
        Text(t("چهره", "Face"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MascotCatalog.faces, key = { it.id }) { f -> FilterChip(cfg.face == f.id, { update(cfg.copy(face = f.id)); state = MascotState.IDLE }, { Text(f.name) }) }
        }
        PartRow(t("کلاه و مو", "Hat and hair"), MascotCatalog.heads.filter { !it.test }, cfg.head) { update(cfg.copy(head = it)) }
        PartRow(t("عینک", "Glasses"), MascotCatalog.glasses.filter { !it.test }, cfg.glasses) { update(cfg.copy(glasses = it)) }
        PartRow(t("آیتم دست", "Item in hand"), MascotCatalog.items.filter { !it.test }, cfg.item) { update(cfg.copy(item = it)) }
        PartRow(t("لباس و پشتی", "Outfit"), MascotCatalog.outfits.filter { !it.test }, cfg.outfit) { update(cfg.copy(outfit = it)) }
        if (MascotCatalog.hasTest()) {
            Text(t("⚠️ تست: الهام‌گرفته از شخصیت‌ها (فقط نسخهٔ آزمایشی؛ در نسخهٔ بازار نیست)",
                "⚠️ Test: inspired by well-known characters (test builds only, not in the store build)"),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            PartRow(t("کلاه و مو (تست)", "Hats (test)"), MascotCatalog.heads.filter { it.test }, cfg.head) { update(cfg.copy(head = it)) }
            PartRow(t("صورت (تست)", "Face parts (test)"), MascotCatalog.glasses.filter { it.test }, cfg.glasses) { update(cfg.copy(glasses = it)) }
            PartRow(t("آیتم دست (تست)", "Items (test)"), MascotCatalog.items.filter { it.test }, cfg.item) { update(cfg.copy(item = it)) }
            PartRow(t("لباس (تست)", "Outfits (test)"), MascotCatalog.outfits.filter { it.test }, cfg.outfit) { update(cfg.copy(outfit = it)) }
        }
    }
}
