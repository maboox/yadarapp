package com.yadavard.app

import android.content.Context
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Yadimo as a little pet: how full, rested and happy it is (0–100). The numbers slowly go down with time and go up
 * with the room's buttons and with every reminder you get done. It never gets sick or leaves; when nobody looks
 * after it, it just gets a bit sleepy and misses you.
 */
object YadimoPet {
    data class Stats(val hunger: Int, val energy: Int, val joy: Int)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("yadimo_pet", Context.MODE_PRIVATE)

    /** Points lost per hour. */
    private const val HUNGER_H = 4f
    private const val ENERGY_H = 3f
    private const val JOY_H = 3f

    fun stats(context: Context): Stats {
        val p = prefs(context)
        val hours = ((System.currentTimeMillis() - p.getLong("at", System.currentTimeMillis())) / 3_600_000f).coerceAtLeast(0f)
        fun v(k: String, rate: Float) = (p.getFloat(k, 80f) - hours * rate).coerceIn(0f, 100f).roundToInt()
        return Stats(v("hunger", HUNGER_H), v("energy", if (night()) -ENERGY_H else ENERGY_H), v("joy", JOY_H))
    }

    private fun save(context: Context, s: Stats) {
        prefs(context).edit().putFloat("hunger", s.hunger.toFloat().coerceIn(0f, 100f)).putFloat("energy", s.energy.toFloat().coerceIn(0f, 100f))
            .putFloat("joy", s.joy.toFloat().coerceIn(0f, 100f)).putLong("at", System.currentTimeMillis()).apply()
    }

    /** What the game starts from: the needs as they are now, and how far the plant has grown. */
    fun gameState(context: Context): String {
        val s = stats(context)
        return org.json.JSONObject().put("needs", org.json.JSONObject().put("hunger", s.hunger).put("energy", s.energy).put("joy", s.joy))
            .put("plant", prefs(context).getInt("plant", 0)).toString()
    }

    /** The game reports back while it is open: the needs it worked out and the plant. */
    fun fromGame(context: Context, json: String) = runCatching {
        val o = org.json.JSONObject(json); val n = o.getJSONObject("needs")
        save(context, Stats(n.optDouble("hunger", 80.0).roundToInt(), n.optDouble("energy", 80.0).roundToInt(), n.optDouble("joy", 80.0).roundToInt()))
        prefs(context).edit().putInt("plant", o.optInt("plant")).apply()
    }

    /** A button was pressed in the room or on the widget. */
    fun act(context: Context, kind: String) {
        val s = stats(context)
        save(context, when (kind) {
            "feed" -> s.copy(hunger = 100, joy = s.joy + 5)
            "play" -> s.copy(joy = s.joy + 20, energy = s.energy - 10, hunger = s.hunger - 5)
            "charge" -> s.copy(energy = 100)
            "pet" -> s.copy(joy = s.joy + 15)
            "sleep", "bed" -> s.copy(energy = s.energy + 30)
            else -> s
        })
    }

    /** Every reminder done makes Yadimo happier, and a little fuller. */
    fun onDone(context: Context) { val s = stats(context); save(context, s.copy(joy = s.joy + 8, hunger = s.hunger + 4)) }

    fun night(): Boolean = LocalTime.now(ZoneId.systemDefault()).let { it.hour >= 22 || it.hour < 7 }

    /** One short line for the room: what Yadimo needs, otherwise something about your day. */
    fun line(context: Context): String {
        val s = stats(context)
        val fa = Prefs.language(context) == AppLanguage.FA
        fun l(f: String, e: String) = if (fa) f else e
        return when {
            night() -> l("شب بخیر… 😴", "Good night… 😴")
            s.hunger < 25 -> l("گشنمه! 🍪", "I'm hungry! 🍪")
            s.energy < 25 -> l("شارژم کمه ⚡", "Low battery ⚡")
            s.joy < 25 -> l("دلم برات تنگ شده بود 🥺", "I missed you 🥺")
            else -> runCatching {
                val day = YadimoLines.day(Repo.all(context), System.currentTimeMillis(), ZoneId.systemDefault())
                when {
                    day.overdue > 0 -> l("${Dates.digits(day.overdue.toString(), AppLanguage.FA)} تا کار از قبل مونده 👀", "${day.overdue} left over 👀")
                    day.upcoming.isNotEmpty() -> l("امروز ${Dates.digits(day.upcoming.size.toString(), AppLanguage.FA)} تا کار داری 💪", "${day.upcoming.size} things today 💪")
                    else -> l("امروز خلوته؛ بیا بازی! ⚽", "A quiet day; let's play! ⚽")
                }
            }.getOrDefault(l("سلام! 👋", "Hi! 👋"))
        }
    }
}
