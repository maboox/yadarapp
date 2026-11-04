package com.yadavard.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Yadimo's wardrobe: which parts cost tokens, which ones this account owns, and keeping the chosen look on the
 * account. The server decides prices and owns the truth; the copy here only lets the screen show locks offline.
 * Part ids are "<kind>:<id>", e.g. "hat:crown" or "costume:bat".
 */
object YadimoShop {
    /** Parts that are earned, not sold: the level each one opens at (same as server/yadimo.go). */
    private val defaultLevels = mapOf("hat:cap" to 2, "glass:sun" to 3, "neck:scarf" to 4, "hat:crown" to 5, "hat:wizard" to 7,
        "costume:ninja" to 10, "costume:pirate" to 15, "color:galaxy" to 20)

    /** Same starting tiers as the server (server/yadimo.go); replaced by the server's answer once it is known. */
    private val defaultTiers: Map<String, String> = buildMap {
        listOf("hat:party", "glass:round").forEach { put(it, "free") }
        listOf("hat:beanie", "hat:night", "hat:bow", "hat:chef", "neck:bowtie", "neck:tie",
            "item:cup", "item:phone", "item:flag").forEach { put(it, "basic") }
        listOf("hat:phones", "hat:grad", "hat:flowers", "glass:star", "glass:heart", "neck:medal",
            "item:wand", "item:balloon", "titem:hammer", "titem:shield", "titem:ball", "titem:saber", "titem:staff").forEach { put(it, "special") }
        listOf("bat", "plumber", "spider", "iron", "straw", "ice", "thor", "elf", "graywiz", "joker", "wed", "saiyan", "hero")
            .forEach { put("costume:$it", "costume") }
        defaultLevels.keys.forEach { put(it, "level") }
    }
    private val defaultPrices = mapOf("free" to 0.0, "basic" to 5.0, "special" to 10.0, "costume" to 20.0)

    var tiers by mutableStateOf(defaultTiers)
        private set
    var prices by mutableStateOf(defaultPrices)
        private set
    var levels by mutableStateOf(defaultLevels)
        private set
    var owned by mutableStateOf(emptySet<String>())
        private set
    var unlimited by mutableStateOf(false)
        private set
    private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("yadimo_shop", Context.MODE_PRIVATE)

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).getString("state", null)?.let { runCatching { apply(JSONObject(it)) } }
    }

    private fun apply(o: JSONObject) {
        o.optJSONObject("tiers")?.let { t -> tiers = t.keys().asSequence().associateWith { t.optString(it) } }
        o.optJSONObject("prices")?.let { p -> prices = p.keys().asSequence().associateWith { p.optDouble(it, 0.0) } }
        o.optJSONObject("levels")?.let { l -> levels = l.keys().asSequence().associateWith { l.optInt(it, 99) } }
        owned = o.optJSONArray("owned")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        unlimited = o.optBoolean("unlimited")
    }

    /** The tier of a part; parts that are not sold (characters, colours, faces, "none") are free. */
    fun tier(id: String): String = if (id.endsWith(":none")) "free" else tiers[id] ?: "free"
    fun price(id: String): Double = prices[tier(id)] ?: 0.0
    fun isFree(id: String): Boolean = tier(id) != "level" && price(id) <= 0.0
    /** The level an earned part needs, or null when it is not one of them. */
    fun needsLevel(id: String): Int? = if (tier(id) == "level") levels[id] ?: 99 else null
    fun canWear(id: String): Boolean = if (id == "color:cosmic") Account.profile.value?.admin == true else needsLevel(id)?.let { (Game.state?.level ?: 1) >= it } ?: (isFree(id) || unlimited || id in owned)

    /** Names of the earned parts, for the "level up" cheer. */
    fun partName(id: String): String = when (id) {
        "hat:cap" -> t("کلاه کپ", "Cap"); "glass:sun" -> t("عینک آفتابی", "Sunglasses"); "neck:scarf" -> t("شال", "Scarf")
        "hat:crown" -> t("تاج", "Crown"); "hat:wizard" -> t("کلاه جادوگر", "Wizard hat"); "costume:ninja" -> t("سربند نینجا", "Ninja band")
        "costume:pirate" -> t("ناخدا", "Captain"); "color:galaxy" -> t("رنگ کهکشانی", "Galaxy colour")
        else -> id.substringAfter(':')
    }

    fun tierName(tier: String): String = when (tier) {
        "basic" -> t("ساده", "Basic"); "special" -> t("ویژه", "Special"); "costume" -> t("شخصیت", "Character"); "level" -> t("با سطح", "By level"); else -> t("رایگان", "Free")
    }

    /** Every paid part the config wears, as shop ids. */
    fun partsOf(c: YadimoConfig): List<String> = listOf("hat:${c.hat}", "glass:${c.glass}", "neck:${c.neck}", "costume:${c.costume}", itemId(c.item))

    /** Items in hand come from two lists in the engine; the character items have their own prefix. */
    fun itemId(item: String): String = if (tiers.containsKey("titem:$item") && !tiers.containsKey("item:$item")) "titem:$item" else "item:$item"

    private fun store(context: Context, o: JSONObject) {
        apply(o)
        prefs(context).edit().putString("state", JSONObject().put("tiers", o.optJSONObject("tiers")).put("prices", o.optJSONObject("prices"))
            .put("levels", o.optJSONObject("levels"))
            .put("owned", o.optJSONArray("owned") ?: JSONArray()).put("unlimited", o.optBoolean("unlimited")).toString()).apply()
    }

    /** Forgets what the previous account owned (after signing out). */
    fun clear(context: Context) {
        owned = emptySet(); unlimited = false
        prefs(context).edit().remove("state").putBoolean("look_dirty", false).apply()
        tiers = defaultTiers; prices = defaultPrices; levels = defaultLevels
    }

    /**
     * Fetches prices and owned parts. When the look on the phone has not been changed since the last sync, the
     * look saved on the account wins: that is how a new phone gets the old Yadimo back after signing in.
     */
    suspend fun sync(context: Context) {
        ensure(context)
        if (!Account.isLoggedIn(context)) return
        val res = runCatching { Account.yadimo(context) }.getOrNull() ?: return
        store(context, res)
        val dirty = prefs(context).getBoolean("look_dirty", false)
        val look = res.optJSONObject("look")
        if (dirty) pushLook(context, YadimoStore.config)
        else if (look != null && look.length() > 0) YadimoStore.applyFromAccount(context, YadimoConfig.fromJson(look.toString()))
    }

    /** Buys a part; returns null when it worked, otherwise the message to show. */
    suspend fun buy(context: Context, id: String): String? = try {
        store(context, Account.yadimoBuy(context, id))
        UsageLog.add(context, UsageLog.Entry(System.currentTimeMillis(), price(id), 0, mapOf("yadimo" to 1)))
        null
    } catch (e: NoBalanceException) { e.message ?: t("موجودی کافی نیست", "Not enough tokens") }
    catch (e: Exception) { e.message?.lineSequence()?.firstOrNull() ?: t("خرید انجام نشد", "Purchase failed") }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var pushJob: kotlinx.coroutines.Job? = null

    /** Saves the look on the account a moment after the last change (or at the next sync when offline). */
    fun lookChanged(context: Context, c: YadimoConfig) {
        prefs(context).edit().putBoolean("look_dirty", true).apply()
        pushJob?.cancel()
        pushJob = scope.launch { kotlinx.coroutines.delay(1500); pushLook(context.applicationContext, c) }
    }

    suspend fun pushLook(context: Context, c: YadimoConfig) {
        if (!Account.isLoggedIn(context)) return
        runCatching { Account.yadimoLook(context, JSONObject(c.toJson())) }.onSuccess {
            prefs(context).edit().putBoolean("look_dirty", false).apply()
        }
    }
}
