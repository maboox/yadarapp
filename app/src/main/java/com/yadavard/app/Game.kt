package com.yadavard.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * The player side of the app: points, level, missions and badges (rules in [GameRules]). Worked out on the phone
 * and saved on the account; the server keeps the larger of the two, pays the starter missions' tokens once, and
 * the admin can set a level by hand.
 */
object Game {
    data class State(
        val xp: Int, val level: Int, val facts: GameFacts, val missions: List<GameGoal>, val badges: List<GameGoal>,
        val claimed: Set<String>, val earned: Set<String>,
    ) {
        val levelStart: Int get() = GameRules.threshold(level)
        val levelEnd: Int? get() = if (level >= GameRules.MAX_LEVEL) null else GameRules.threshold(level + 1)
        val progress: Float get() = levelEnd?.let { ((xp - levelStart).toFloat() / (it - levelStart)).coerceIn(0f, 1f) } ?: 1f
    }

    /** Something to cheer about: a new level (with what it opened) or a new badge. */
    data class Cheer(val level: Int?, val badge: GameGoal?, val unlocked: List<String>)

    var state by mutableStateOf<State?>(null)
        private set
    var cheer by mutableStateOf<Cheer?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pushJob: Job? = null

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("game", Context.MODE_PRIVATE)

    /** The level that counts: set by hand on the server, otherwise earned. */
    fun level(context: Context): Int {
        val override = prefs(context).getInt("override", 0)
        return if (override > 0) override else state?.level ?: prefs(context).getInt("level", 1)
    }

    // ---- counters the history cannot give ----

    private fun day() = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    /** Points for creating are kept per day with a cap; recent ones by id so a quick delete can take them back. */
    private fun addDaily(context: Context, key: String, cap: Int, points: Int, id: Long?) {
        val p = prefs(context)
        val log = runCatching { JSONObject(p.getString(key, "{}")!!) }.getOrDefault(JSONObject())
        val today = day().toString()
        val used = log.optInt(today)
        val given = minOf(points, cap - used).coerceAtLeast(0)
        if (given == 0) return
        log.put(today, used + given)
        val edit = p.edit().putString(key, log.toString()).putInt("${key}_total", p.getInt("${key}_total", 0) + given)
        if (id != null) {
            val recent = runCatching { JSONObject(p.getString("recent", "{}")!!) }.getOrDefault(JSONObject())
            val before = recent.optJSONArray(id.toString())?.optInt(1) ?: 0
            recent.put(id.toString(), JSONArray().put(today).put(before + given))
            // Keep the newest few only.
            val keys = recent.keys().asSequence().toList()
            if (keys.size > 40) keys.take(keys.size - 40).forEach { recent.remove(it) }
            edit.putString("recent", recent.toString())
        }
        edit.apply()
    }

    fun onCreated(context: Context, id: Long) {
        addDaily(context, "created", GameRules.XP_CREATE_DAY_CAP, GameRules.XP_CREATE, id)
        refreshSoon(context)
    }

    /** The reminder [id] came from a voice request: a few more points and one more for the voice missions. */
    fun markVoice(context: Context, id: Long) {
        val p = prefs(context)
        p.edit().putInt("voice", p.getInt("voice", 0) + 1).apply()
        addDaily(context, "created", GameRules.XP_CREATE_DAY_CAP, GameRules.XP_CREATE_VOICE - GameRules.XP_CREATE, id)
        refreshSoon(context)
    }

    fun onDeleted(context: Context, r: Reminder) {
        if (System.currentTimeMillis() - r.createdAt > GameRules.UNDO_MILLIS) return
        val p = prefs(context)
        val recent = runCatching { JSONObject(p.getString("recent", "{}")!!) }.getOrDefault(JSONObject())
        val entry = recent.optJSONArray(r.id.toString()) ?: return
        val log = runCatching { JSONObject(p.getString("created", "{}")!!) }.getOrDefault(JSONObject())
        val dayKey = entry.optString(0)
        val back = entry.optInt(1)
        log.put(dayKey, (log.optInt(dayKey) - back).coerceAtLeast(0))
        recent.remove(r.id.toString())
        p.edit().putString("created", log.toString()).putString("recent", recent.toString())
            .putInt("created_total", (p.getInt("created_total", 0) - back).coerceAtLeast(0)).apply()
        refreshSoon(context)
    }

    fun onNote(context: Context) { addDaily(context, "notes", GameRules.XP_NOTE_DAY_CAP, GameRules.XP_NOTE, null); refreshSoon(context) }

    // ---- computing ----

    private var refreshJob: Job? = null
    private fun refreshSoon(context: Context) {
        refreshJob?.cancel()
        refreshJob = scope.launch { delay(300); refresh(context.applicationContext) }
    }

    /** Recomputes everything from the phone's data. Call off the main thread. */
    suspend fun refresh(context: Context) {
        val p = prefs(context)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        YadimoStore.ensure(context); YadimoShop.ensure(context)
        val history = Repo.history(context)
        val reminders = runCatching { Repo.all(context) }.getOrDefault(emptyList())
        val other = GameFacts(
            voiceCreated = p.getInt("voice", 0), createdXp = p.getInt("created_total", 0), noteXp = p.getInt("notes_total", 0),
            hasReminder = reminders.isNotEmpty() || history.isNotEmpty(), hasNote = Repo.notes(context).isNotEmpty(),
            nameSet = Prefs.userName(context).isNotBlank(),
            usernameSet = Account.run { load(context); profile.value?.username?.isNotBlank() == true }, customized = YadimoStore.config != YadimoConfig(),
            helloOn = YadimoStore.enabled && YadimoTalk.hello(context), owned = YadimoShop.owned.size)
        val facts = GameRules.facts(history, zone, now, other)
        val badges = GameRules.badges(facts)
        val earned = (p.getStringSet("badges", emptySet()).orEmpty() + badges.filter { it.complete }.map { it.key }).toSet()
        val claimed = p.getStringSet("claimed", emptySet()).orEmpty()
        val xp = GameRules.xp(facts, claimed, earned) + p.getInt("baseline", 0)
        val override = p.getInt("override", 0)
        val level = if (override > 0) override else GameRules.levelOf(xp)
        val s = State(xp, level, facts, GameRules.missions(facts), badges, claimed, earned)

        // Cheer only for what is new since last time; the very first time just remembers where the user is.
        val seenLevel = p.getInt("seen_level", 0)
        val seenBadges = p.getStringSet("seen_badges", null)
        var news: Cheer? = null
        if (seenLevel > 0 && level > seenLevel) {
            val unlocked = YadimoShop.levels.filter { (_, need) -> need in (seenLevel + 1)..level }.keys.map { YadimoShop.partName(it) }
            news = Cheer(level, null, unlocked)
        } else if (seenBadges != null) {
            badges.firstOrNull { it.key in earned && it.key !in seenBadges }?.let { news = Cheer(null, it, emptyList()) }
        }
        p.edit().putStringSet("badges", earned).putInt("level", level).putInt("seen_level", maxOf(seenLevel, level))
            .putStringSet("seen_badges", earned).apply()
        withContext(Dispatchers.Main) {
            state = s
            if (news != null) cheer = news
        }
        schedulePush(context, s)
    }

    // ---- account ----

    private fun body(s: State) = JSONObject().put("xp", s.xp).put("level", s.level).put("streak", s.facts.bestStreak)
        .put("done", s.facts.totalDone).put("badges", JSONArray(s.earned.toList())).put("missions", JSONArray(s.claimed.toList()))

    private fun schedulePush(context: Context, s: State) {
        if (!Account.isLoggedIn(context)) return
        val raw = body(s).toString()
        if (prefs(context).getString("pushed", null) == raw) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(3000)
            runCatching { Account.gameSave(context, JSONObject(raw)) }.onSuccess { res ->
                prefs(context).edit().putString("pushed", raw).apply(); take(context, res)
            }
        }
    }

    /** What the server says: missions paid, level set by hand, and progress from another phone. */
    private fun take(context: Context, res: JSONObject) {
        val p = prefs(context)
        val edit = p.edit().putInt("override", res.optInt("level_override", 0))
        val game = res.optJSONObject("game")
        val local = state
        val claimed = p.getStringSet("claimed", emptySet()).orEmpty().toMutableSet()
        res.optJSONArray("claimed")?.let { a -> (0 until a.length()).forEach { claimed += a.optString(it) } }
        if (game != null) {
            val serverXp = game.optInt("xp")
            // Progress from another phone: the difference to what this phone can work out is kept as a baseline.
            // Worked out from the phone's own points, so taking the same answer twice changes nothing.
            if (local != null) {
                val baseline = p.getInt("baseline", 0)
                edit.putInt("baseline", maxOf(baseline, serverXp - (local.xp - baseline)))
            }
            val badges = game.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            game.optJSONArray("missions")?.let { a -> (0 until a.length()).forEach { claimed += a.optString(it) } }
            edit.putStringSet("badges", p.getStringSet("badges", emptySet()).orEmpty() + badges)
                .putStringSet("seen_badges", p.getStringSet("seen_badges", emptySet()).orEmpty() + badges)
        }
        edit.putStringSet("claimed", claimed)
        edit.apply()
    }

    /** Fetches progress from the account (after signing in or at start). */
    suspend fun sync(context: Context) {
        if (!Account.isLoggedIn(context)) { refresh(context); return }
        if (state == null) refresh(context)
        runCatching { Account.game(context) }.onSuccess { take(context, it) }
        refresh(context)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove("override").remove("pushed").apply()
        scope.launch { refresh(context.applicationContext) }
    }

    /**
     * Takes a finished mission's reward: points right away, and the tokens of the starter missions from the server.
     * Returns null when it worked, otherwise what to show.
     */
    suspend fun claim(context: Context, goal: GameGoal): String? {
        if (!goal.complete) return t("این مأموریت هنوز کامل نشده", "Not finished yet")
        val p = prefs(context)
        if (goal.tokens > 0) {
            if (!Account.isLoggedIn(context)) { Account.loginRequested.value = true; return t("برای گرفتن توکن، وارد حساب بازارت شو", "Sign in to get the tokens") }
            state?.let { s -> runCatching { Account.gameSave(context, body(s)) } }
            @android.annotation.SuppressLint("HardwareIds")
            val device = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
            val res = try { Account.gameClaim(context, goal.key, device) } catch (e: Exception) {
                val msg = e.message?.lineSequence()?.firstOrNull().orEmpty()
                // Already paid (on this account or this phone): the points still count.
                if (msg.contains("قبلاً") || msg.contains("already")) {
                    p.edit().putStringSet("claimed", p.getStringSet("claimed", emptySet()).orEmpty() + goal.key).apply(); refresh(context)
                }
                return msg.ifBlank { t("دریافت انجام نشد", "Could not claim") }
            }
            take(context, res)
        }
        p.edit().putStringSet("claimed", p.getStringSet("claimed", emptySet()).orEmpty() + goal.key).apply()
        refresh(context)
        return null
    }
}
