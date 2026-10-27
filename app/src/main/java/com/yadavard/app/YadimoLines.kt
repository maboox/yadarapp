package com.yadavard.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// What Yadimo says: the morning hello and the short notes during the day. Pure Kotlin (covered by JVM tests);
// the words come only from the reminders on the phone, so nothing needs the network or the assistant.

/** A city for the weather; picked from a list, so no location permission is needed. */
data class YadimoCity(val id: String, val fa: String, val en: String, val lat: Double, val lon: Double)

/** Today's weather as the server (Open-Meteo) gives it; [code] is a WMO weather code. */
data class YadimoWeather(val temp: Double, val code: Int, val max: Double, val min: Double, val rain: Double)

/** One thing Yadimo can say during the day; [kind] keeps it from saying the same kind twice in a day. */
data class YadimoLine(val kind: String, val text: String)

object YadimoLines {
    val cities = listOf(
        YadimoCity("tehran", "تهران", "Tehran", 35.69, 51.39), YadimoCity("mashhad", "مشهد", "Mashhad", 36.30, 59.60),
        YadimoCity("isfahan", "اصفهان", "Isfahan", 32.65, 51.67), YadimoCity("karaj", "کرج", "Karaj", 35.84, 50.94),
        YadimoCity("shiraz", "شیراز", "Shiraz", 29.59, 52.58), YadimoCity("tabriz", "تبریز", "Tabriz", 38.08, 46.29),
        YadimoCity("qom", "قم", "Qom", 34.64, 50.88), YadimoCity("ahvaz", "اهواز", "Ahvaz", 31.32, 48.67),
        YadimoCity("kermanshah", "کرمانشاه", "Kermanshah", 34.31, 47.07), YadimoCity("urmia", "ارومیه", "Urmia", 37.55, 45.08),
        YadimoCity("rasht", "رشت", "Rasht", 37.28, 49.58), YadimoCity("zahedan", "زاهدان", "Zahedan", 29.50, 60.86),
        YadimoCity("kerman", "کرمان", "Kerman", 30.28, 57.08), YadimoCity("hamedan", "همدان", "Hamedan", 34.80, 48.51),
        YadimoCity("yazd", "یزد", "Yazd", 31.90, 54.37), YadimoCity("ardabil", "اردبیل", "Ardabil", 38.25, 48.29),
        YadimoCity("bandarabbas", "بندرعباس", "Bandar Abbas", 27.18, 56.27), YadimoCity("arak", "اراک", "Arak", 34.09, 49.69),
        YadimoCity("zanjan", "زنجان", "Zanjan", 36.67, 48.48), YadimoCity("sanandaj", "سنندج", "Sanandaj", 35.31, 47.00),
        YadimoCity("qazvin", "قزوین", "Qazvin", 36.27, 50.00), YadimoCity("khorramabad", "خرم‌آباد", "Khorramabad", 33.49, 48.36),
        YadimoCity("gorgan", "گرگان", "Gorgan", 36.84, 54.43), YadimoCity("sari", "ساری", "Sari", 36.56, 53.06),
        YadimoCity("babol", "بابل", "Babol", 36.54, 52.68), YadimoCity("amol", "آمل", "Amol", 36.47, 52.35),
        YadimoCity("bushehr", "بوشهر", "Bushehr", 28.97, 50.84), YadimoCity("birjand", "بیرجند", "Birjand", 32.87, 59.22),
        YadimoCity("bojnurd", "بجنورد", "Bojnurd", 37.47, 57.33), YadimoCity("ilam", "ایلام", "Ilam", 33.64, 46.42),
        YadimoCity("shahrekord", "شهرکرد", "Shahrekord", 32.33, 50.86), YadimoCity("yasuj", "یاسوج", "Yasuj", 30.67, 51.59),
        YadimoCity("semnan", "سمنان", "Semnan", 35.58, 53.39), YadimoCity("kashan", "کاشان", "Kashan", 33.98, 51.43),
        YadimoCity("dezful", "دزفول", "Dezful", 32.38, 48.40), YadimoCity("kish", "کیش", "Kish", 26.53, 53.98),
    )

    fun city(id: String): YadimoCity? = cities.firstOrNull { it.id == id }

    private val quotesFa = listOf(
        "کارهای بزرگ از قدم‌های کوچک شروع می‌شوند؛ امروز فقط قدم اول را بردار.",
        "لازم نیست همه‌چیز عالی باشد؛ کافی است شروع کنی.",
        "یک کار کوچکِ انجام‌شده از ده کارِ بزرگِ فکرشده بهتر است.",
        "امروز همان روزی است که دیروز منتظرش بودی.",
        "هر تیکی که امروز می‌زنی، فردای سبک‌تری برایت می‌سازد.",
        "آرام ولی پیوسته؛ لاک‌پشت هم بالاخره رسید!",
        "به خودت افتخار کن؛ تا اینجا خیلی خوب آمده‌ای.",
        "اول سخت‌ترین کار را تمام کن، بقیهٔ روز مال خودت.",
        "یک لیوان آب، یک نفس عمیق، و بزن بریم!",
        "هر روز یک فرصت تازه است؛ قرار نیست دیروز را تکرار کنی.",
        "کمی برنامه، کلی آرامش.",
        "امروز با خودت مهربان باش؛ قدم‌به‌قدم جلو می‌رویم.",
        "رؤیاها با یادآوری‌های کوچک به واقعیت نزدیک می‌شوند.",
        "لبخند بزن؛ حتی کارهای سخت هم با لبخند سبک‌ترند.",
        "تو از پسش برمی‌آیی؛ من هم کنارت هستم.",
    )
    private val quotesEn = listOf(
        "Big things start with small steps; just take the first one today.",
        "It doesn't have to be perfect; it just has to start.",
        "One small thing done beats ten big things planned.",
        "Today is the day you were waiting for yesterday.",
        "Every tick today makes tomorrow lighter.",
        "Slow and steady; the tortoise got there too!",
        "Be proud of yourself; you've come a long way.",
        "Do the hardest thing first, then the day is yours.",
        "A glass of water, a deep breath, and let's go!",
        "Be kind to yourself today; one step at a time.",
        "You've got this, and I'm right here with you.",
    )
    private val cheersFa = listOf(
        "یادت نره کمی آب بخوری 💧", "یک کش و قوس کوچک به بدنت بده 🙆", "داری عالی پیش می‌ری، ادامه بده ✨",
        "یه نفس عمیق بکش؛ همه‌چی تحت کنترله 🌿", "من حواسم به یادآوری‌هات هست، تو راحت باش 😌",
    )
    private val cheersEn = listOf(
        "Don't forget to drink some water 💧", "Give yourself a little stretch 🙆", "You're doing great, keep going ✨",
        "Take a deep breath; everything's under control 🌿", "I'm keeping an eye on your reminders, relax 😌",
    )

    fun quote(day: LocalDate, fa: Boolean): String = (if (fa) quotesFa else quotesEn).let { it[Math.floorMod(day.toEpochDay(), it.size.toLong()).toInt()] }

    private fun num(v: Int, fa: Boolean) = Dates.digits(v.toString(), if (fa) AppLanguage.FA else AppLanguage.EN)

    /** A sky word and emoji for a WMO weather code. */
    fun sky(code: Int, fa: Boolean): String = when (code) {
        0 -> if (fa) "صاف و آفتابی ☀️" else "clear and sunny ☀️"
        1, 2 -> if (fa) "کمی ابری 🌤️" else "partly cloudy 🌤️"
        3 -> if (fa) "ابری ☁️" else "cloudy ☁️"
        45, 48 -> if (fa) "مه‌آلود 🌫️" else "foggy 🌫️"
        in 51..57 -> if (fa) "نم‌نم باران 🌦️" else "drizzle 🌦️"
        in 61..67, in 80..82 -> if (fa) "بارانی 🌧️" else "rainy 🌧️"
        in 71..77, 85, 86 -> if (fa) "برفی ❄️" else "snowy ❄️"
        in 95..99 -> if (fa) "رعدوبرق ⛈️" else "thunderstorms ⛈️"
        else -> if (fa) "معمولی 🙂" else "mild 🙂"
    }

    fun weatherLine(w: YadimoWeather, cityName: String, fa: Boolean): String {
        val hi = num(Math.round(w.max).toInt(), fa); val lo = num(Math.round(w.min).toInt(), fa)
        val base = if (fa) "هوای $cityName امروز ${sky(w.code, fa)}، بین $lo تا $hi درجه."
            else "$cityName today: ${sky(w.code, fa)}, $lo° to $hi°."
        val tip = when {
            w.rain >= 50 || w.code in 61..67 || w.code in 80..82 || w.code in 95..99 -> if (fa) " چتر یادت نره ☂️" else " Take an umbrella ☂️"
            w.code in 71..77 || w.min <= 0 -> if (fa) " لباس گرم بپوش 🧣" else " Dress warm 🧣"
            w.max >= 35 -> if (fa) " آب زیاد بخور 💧" else " Drink plenty of water 💧"
            else -> ""
        }
        return base + tip
    }

    /** Today's agenda: what is still to come today and what was left from before. */
    data class Day(val upcoming: List<Pair<Long, Reminder>>, val doneToday: Int, val overdue: Int)

    fun day(items: List<Reminder>, now: Long, zone: ZoneId): Day {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val upcoming = items.filter { !it.done }.flatMap { r -> Recurrence.occurrencesIn(r, now, end, 12).map { it to r } }
            .filter { (at, r) -> at >= now && at != r.pendingAt }.sortedBy { it.first }
        val doneToday = items.count { it.completedAt in start until end && !it.missed }
        val overdue = items.count { it.needsAttention(now) }
        return Day(upcoming, doneToday, overdue)
    }

    /** The share of reminders done in the last 7 days, or null when there were too few to say. */
    fun weekRate(history: List<HistoryEntry>, now: Long): Int? {
        val week = history.filter { it.recordedAt >= now - 7 * 86_400_000L }
        if (week.size < 5) return null
        return week.count { it.done } * 100 / week.size
    }

    fun greeting(name: String, fa: Boolean): String =
        if (fa) (if (name.isBlank()) "صبح بخیر! ☀️" else "صبح بخیر $name! ☀️") else (if (name.isBlank()) "Good morning! ☀️" else "Good morning, $name! ☀️")

    /** The agenda sentence of the morning hello. */
    fun agenda(day: Day, zone: ZoneId, fa: Boolean): String {
        val lang = if (fa) AppLanguage.FA else AppLanguage.EN
        val count = day.upcoming.size
        val main = when {
            count == 0 -> if (fa) "امروز کار ثبت‌شده‌ای نداری؛ یک روز آزاد! 😎" else "Nothing planned today; a free day! 😎"
            else -> {
                val first = day.upcoming.first()
                val time = Dates.formatTime(first.first, zone, lang)
                if (fa) "امروز ${num(count, fa)} کار داری؛ اولی ساعت $time: «${first.second.title}»."
                else "You have $count thing${if (count > 1) "s" else ""} today; first at $time: “${first.second.title}”."
            }
        }
        val left = if (day.overdue > 0) (if (fa) " ${num(day.overdue, fa)} تا هم از قبل مانده." else " ${day.overdue} left over from before.") else ""
        return main + left
    }

    /**
     * Something short to say during the day, most useful first; [said] are the kinds already said today.
     * Returns null when there is nothing worth saying.
     */
    fun nudge(day: Day, weekRate: Int?, weather: String?, said: Set<String>, now: Long, zone: ZoneId, fa: Boolean, seed: Int): YadimoLine? {
        val options = buildList {
            if (day.overdue > 0) add(YadimoLine("overdue",
                if (fa) "${num(day.overdue, fa)} تا یادآوری از قبل مونده‌ها! 👀 یه سر بزن." else "${day.overdue} reminder${if (day.overdue > 1) "s" else ""} left over! 👀 Take a look."))
            day.upcoming.firstOrNull()?.let { (at, r) ->
                val minutes = ((at - now) / 60_000L).toInt()
                if (minutes in 1..90) add(YadimoLine("soon",
                    if (fa) "${num(minutes, fa)} دقیقهٔ دیگه: «${r.title}» ⏰" else "In $minutes min: “${r.title}” ⏰"))
            }
            if (day.upcoming.size >= 2) add(YadimoLine("today",
                if (fa) "امروز هنوز ${num(day.upcoming.size, fa)} تا کار مونده؛ از پسش برمیای 💪" else "${day.upcoming.size} things still to go today; you've got this 💪"))
            if (day.doneToday >= 3) add(YadimoLine("doneToday",
                if (fa) "امروز ${num(day.doneToday, fa)} تا کار رو تیک زدی؛ دمت گرم! 🎉" else "You've ticked off ${day.doneToday} things today. Nice! 🎉"))
            if (weekRate != null && weekRate >= 70) add(YadimoLine("week",
                if (fa) "این هفته ${num(weekRate, fa)}٪ کارهات رو انجام دادی؛ عالی بودی! 🌟" else "You did $weekRate% of your tasks this week. Amazing! 🌟"))
            if (weather != null) add(YadimoLine("weather", weather))
            val cheers = if (fa) cheersFa else cheersEn
            add(YadimoLine("cheer", cheers[Math.floorMod(seed, cheers.size)]))
        }
        return options.firstOrNull { it.kind !in said }
    }

    /** The note Yadimo gives about 20 minutes before a reminder. */
    fun before(r: Reminder, minutes: Int, fa: Boolean): String =
        if (fa) "${num(minutes, fa)} دقیقهٔ دیگه «${r.title}» داری ⏰" else "“${r.title}” in $minutes minutes ⏰"
}
