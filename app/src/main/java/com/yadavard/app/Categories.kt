package com.yadavard.app

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

data class CategoryDef(val key: String, val nameFa: String, val nameEn: String, val color: Long, val icon: String, val builtIn: Boolean) {
    fun name(lang: AppLanguage) = if (lang == AppLanguage.FA || nameEn.isBlank()) nameFa else nameEn
}

/** Built-in categories plus the user's own ones (stored in settings). Unknown keys fall back to "General". */
object Categories {
    val builtIns = listOf(
        CategoryDef("GENERAL", "عمومی", "General", 0xFF6C5CE7, "bell", true),
        CategoryDef("PERSONAL", "شخصی", "Personal", 0xFF3D8BFD, "person", true),
        CategoryDef("WORK", "کار", "Work", 0xFF0F9D8A, "work", true),
        CategoryDef("MEDICINE", "دارو", "Medicine", 0xFFE5484D, "pill", true),
        CategoryDef("DOCTOR", "دکتر و درمان", "Doctor", 0xFFD64545, "doctor", true),
        CategoryDef("SPORT", "ورزش", "Sport", 0xFF2E9E6B, "sport", true),
        CategoryDef("HEALTH", "سلامت", "Health", 0xFFEC5F8C, "heart", true),
        CategoryDef("BILLS", "قبض و قسط", "Bills", 0xFFE07A2E, "money", true),
        CategoryDef("BIRTHDAY", "تولد و مناسبت", "Occasions", 0xFFD6409F, "cake", true),
        CategoryDef("SHOPPING", "خرید", "Shopping", 0xFF2EAD5B, "cart", true),
        CategoryDef("STUDY", "درس", "Study", 0xFF8E5BD8, "school", true),
    )

    val palette = listOf(0xFF6C5CE7, 0xFF3D8BFD, 0xFF0F9D8A, 0xFF2EAD5B, 0xFFE5484D, 0xFFE07A2E, 0xFFD6409F,
        0xFF8E5BD8, 0xFF00A3BF, 0xFF8D6E63, 0xFF546E7A, 0xFFF2B705)

    val icons: Map<String, ImageVector> = linkedMapOf(
        "bell" to Icons.Rounded.NotificationsActive, "person" to Icons.Rounded.Person, "work" to Icons.Rounded.Work,
        "pill" to Icons.Rounded.Medication, "doctor" to Icons.Rounded.LocalHospital, "sport" to Icons.Rounded.FitnessCenter,
        "heart" to Icons.Rounded.Favorite, "money" to Icons.Rounded.Payments, "cake" to Icons.Rounded.Cake,
        "cart" to Icons.Rounded.ShoppingCart, "school" to Icons.Rounded.School, "home" to Icons.Rounded.Home,
        "car" to Icons.Rounded.DirectionsCar, "pets" to Icons.Rounded.Pets, "family" to Icons.Rounded.People,
        "food" to Icons.Rounded.Restaurant, "travel" to Icons.Rounded.Flight, "phone" to Icons.Rounded.Phone,
        "star" to Icons.Rounded.Star, "plant" to Icons.Rounded.LocalFlorist, "water" to Icons.Rounded.WaterDrop,
    )

    /** Observable list used by the UI; loaded once per process. */
    val all = mutableStateListOf<CategoryDef>().apply { addAll(builtIns) }
    @Volatile private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val custom = runCatching {
                val raw = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("categories", null) ?: "[]"
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    CategoryDef(o.getString("key"), o.getString("name"), o.optString("nameEn"), o.optLong("color", palette[0]),
                        o.optString("icon", "star"), false)
                }
            }.getOrDefault(emptyList())
            all.clear(); all.addAll(builtIns + custom)
            loaded = true
        }
    }

    private fun persist(context: Context) {
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("categories", exportJson().toString()).apply()
    }

    fun of(key: String): CategoryDef = all.firstOrNull { it.key == key } ?: builtIns.firstOrNull { it.key == key } ?: builtIns[0]
    fun of(context: Context, key: String): CategoryDef { ensure(context); return of(key) }

    fun add(context: Context, name: String, color: Long, icon: String): CategoryDef {
        ensure(context)
        val def = CategoryDef("c_" + System.currentTimeMillis().toString(36), name.trim().take(40), "", color, icon, false)
        all.add(def); persist(context)
        return def
    }

    fun update(context: Context, def: CategoryDef) {
        val i = all.indexOfFirst { it.key == def.key }
        if (i >= 0 && !all[i].builtIn) { all[i] = def; persist(context) }
    }

    /** Removes a user category; its reminders move to "General". */
    fun remove(context: Context, key: String) {
        if (all.none { it.key == key && !it.builtIn }) return
        all.removeAll { it.key == key }
        persist(context)
        val app = context.applicationContext
        Thread { Repo.all(app).filter { it.category == key }.forEach { Repo.save(app, it.copy(category = CategoryGuess.GENERAL)) } }.start()
    }

    /** The user's own categories, written into backups. */
    fun exportJson(): JSONArray = JSONArray().apply {
        all.filter { !it.builtIn }.forEach {
            put(JSONObject().put("key", it.key).put("name", it.nameFa).put("nameEn", it.nameEn).put("color", it.color).put("icon", it.icon))
        }
    }

    /** Adds categories from a backup that are not present yet (matched by key). */
    fun importJson(context: Context, array: JSONArray) {
        ensure(context)
        var changed = false
        for (i in 0 until minOf(array.length(), 200)) {
            val o = array.optJSONObject(i) ?: continue
            val key = o.optString("key").takeIf { it.isNotBlank() } ?: continue
            if (all.any { it.key == key }) continue
            all.add(CategoryDef(key, o.optString("name", key).take(40), o.optString("nameEn"), o.optLong("color", palette[0]),
                o.optString("icon", "star"), false))
            changed = true
        }
        if (changed) persist(context)
    }

    /** (key, name) pairs of the user's own categories, for keyword guessing. */
    fun customPairs(context: Context): List<Pair<String, String>> { ensure(context); return all.filter { !it.builtIn }.map { it.key to it.nameFa } }

    /** Resolves what the AI returned (a key or a name) to a known key. */
    fun resolve(context: Context, value: String?): String {
        ensure(context)
        val v = value?.trim().orEmpty()
        if (v.isBlank()) return CategoryGuess.GENERAL
        return all.firstOrNull { it.key.equals(v, true) || it.nameFa == v || it.nameEn.equals(v, true) }?.key ?: CategoryGuess.GENERAL
    }

    /** Category list for AI prompts. */
    fun promptList(context: Context): String {
        ensure(context)
        return all.joinToString(", ") { "${it.key} (${it.nameFa})" }
    }
}

fun String.catDef(): CategoryDef = Categories.of(this)
fun String.catColor(): Color = Color(Categories.of(this).color)
fun String.catIcon(): ImageVector = Categories.icons[Categories.of(this).icon] ?: Icons.Rounded.Label
fun String.catLabel(): String = Categories.of(this).name(AppDisplay.language)
