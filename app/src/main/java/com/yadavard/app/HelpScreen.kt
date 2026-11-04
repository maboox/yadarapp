@file:OptIn(ExperimentalLayoutApi::class)

package com.yadavard.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * One page of the guide: what it is ([text], bullet points start with "•"), where to find it ([where]), sentences to
 * try ([examples]) and a picture: a real screenshot ([image]) or something drawn live ([visual]).
 */
private class Topic(val icon: ImageVector, val title: String, val text: String, val where: String = "", val examples: List<String> = emptyList(),
                    val image: Int? = null, val visual: (@Composable () -> Unit)? = null)

private class Section(val title: String, val emoji: String, val topics: List<Topic>)

@Composable
private fun TierSamples() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(Tier.BRONZE to t("زیر ۲۰۰", "under 200"), Tier.SILVER to t("۲۰۰ تا ۵۰۰", "200–500"), Tier.GOLD to t("۵۰۰ تا ۱۵۰۰", "500–1500"),
            Tier.DIAMOND to t("بیش از ۱۵۰۰", "over 1500")).forEach { (tier, range) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TierFrame(tier, 84.dp) { Text("🐣", fontSize = 18.sp) }
                Text(t(tier.fa, tier.en), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickSteps() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "➕" to t("بساز: دکمهٔ «یادآوری جدید»، یا فقط در کادر بالای خانه بنویس «فردا ساعت ۹ تماس با مامان».", "Create: the New reminder button, or just type “call mum tomorrow 9am” in the box on top."),
            "🎙️" to t("بگو: دکمهٔ میکروفون را بزن و عادی حرف بزن؛ دستیار خودش یادآوری را می‌سازد.", "Say it: tap the mic and talk normally; the assistant creates the reminder."),
            "🔔" to t("یادت می‌اندازد: سر وقت اعلان یا زنگ می‌آید؛ «انجام شد» یا «تعویق» را بزن.", "It reminds you: a notification or alarm arrives on time; tap Done or Snooze."),
            "✅" to t("انجام بده: کارت را در خانه به یک طرف بکش تا انجام شود؛ به طرف دیگر تا حذف شود (با امکان برگرداندن).", "Get it done: swipe a card one way for done, the other to delete (with undo)."),
        ).forEachIndexed { i, (emoji, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Text("${n(i + 1)}. $text", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun sections() = listOf(
    Section(t("شروع سریع", "Quick start"), "🚀", listOf(
        Topic(Icons.Rounded.AutoAwesome, t("چهار قدم تا اولین یادآوری", "Four steps to your first reminder"), "", visual = { QuickSteps() }),
        Topic(Icons.Rounded.AddAlert, t("ساختن یادآوری", "Create reminders"),
            t("زمان، تکرار، دسته و نوع یادآوری را از جمله‌ات می‌فهمد؛ یا خودت در صفحهٔ ساخت انتخاب می‌کنی.\n• زمان: تاریخ و ساعت، یا «فردا»، «۲ ساعت دیگه».\n• تکرار: یک‌باره، هر چند ساعت (برای دارو)، روزانه، هفتگی با انتخاب روزها، ماهانه، سالانه و «چند روز پس از انجام».\n• اعلان یا زنگ تمام‌صفحه (برای کارهای مهم، حتی روی صفحهٔ قفل).\n• یادآوری زودتر (مثلاً یک روز قبل) و «تکرار هشدار تا انجام».\n• دسته‌بندی رنگی و علامت «مهم».",
                "It understands time, repeat, category and alert type from your sentence, or you set them on the create page.\n• Time: date and hour, or “tomorrow”, “in 2 hours”.\n• Repeat: once, every few hours (pills), daily, weekly on chosen days, monthly, yearly and “N days after done”.\n• Notification or full-screen alarm (for important things, even on the lock screen).\n• Advance notice (e.g. a day before) and “keep reminding until done”.\n• A coloured category and an “important” mark."),
            where = t("دکمهٔ «یادآوری جدید» روی صفحهٔ خانه و تقویم", "The New reminder button on Home and Calendar"),
            examples = listOf(t("فردا ساعت ۱۰ جلسه کاری", "work meeting tomorrow 10am"), t("هر شب ساعت ۱۰ قرص، با زنگ", "pills every night 10pm, as an alarm"),
                t("۲۰ هر ماه قسط وام", "loan payment on the 20th every month"), t("هر شنبه و سه‌شنبه ساعت ۶ عصر باشگاه", "gym every Saturday and Tuesday 6pm"))),
        Topic(Icons.Rounded.TaskAlt, t("انجام شد، انجام نشد، تعویق، حذف", "Done, missed, snooze, delete"),
            t("• «انجام شد»: در آمار و امتیاز تو حساب می‌شود.\n• «انجام نشد»: در آمار ثبت می‌شود و در بخش جدا دیده می‌شود.\n• تعویق: از اعلان، ۵ تا ۶۰ دقیقه عقب می‌اندازد (مدتش در تنظیمات است).\n• حذف: یادآوری و اعلان‌های آیندهاش پاک می‌شود و «انجام‌نشده»هایش هم از آمار برداشته می‌شود؛ کارهای انجام‌شده می‌مانند.\n• اگر یادآوریِ تکرارشونده را حذف کنی، می‌پرسد: «فقط همین بار» (بقیهٔ تکرارها می‌ماند) یا «همهٔ تکرارها».\n• هر کدام را با «برگرداندن» پایین صفحه پس بگیر.",
                "• Done: counts in your stats and points.\n• Missed: recorded in stats and shown apart.\n• Snooze: from the notification, 5–60 minutes (the length is in Settings).\n• Delete: the reminder and its alerts go, and its missed entries leave the stats; what you did stays counted.\n• Deleting a repeating reminder asks: “Only this time” (the rest stays) or “All repeats”.\n• Take any of them back with Undo at the bottom."),
            examples = listOf(t("قرص رو خوردم", "I took the pills"), t("باشگاه امروز نرفتم", "I skipped the gym today"))),
        Topic(Icons.Rounded.CalendarMonth, t("تقویم، جست‌وجو و فیلتر", "Calendar, search and filters"),
            t("• تقویم ماهانه شمسی یا میلادی، با نقطه‌های رنگی روزهای دارای یادآوری؛ روی یک روز بزن تا کارهایش را ببینی و بساز.\n• خانه: بخش‌های «نیاز به توجه»، امروز، فردا، این هفته، بعدتر و انجام‌شده.\n• بالای خانه: جست‌وجو در یادآوری‌ها و فیلترهای «فقط امروز»، «همه»، «عمومی»، «شخصی» و دسته‌ها.",
                "• A monthly Persian or Gregorian calendar with coloured dots on days that have reminders; tap a day to see and add.\n• Home: Needs attention, Today, Tomorrow, This week, Later and Done.\n• At the top of Home: search and the Today only, All, General, Personal and category filters."),
            where = t("تب «تقویم» و صفحهٔ «خانه»", "The Calendar tab and Home")),
    )),
    Section(t("دستیار هوشمند", "Smart assistant"), "🤖", listOf(
        Topic(Icons.Rounded.Mic, t("دستیار هوشمند", "Smart assistant"),
            t("دکمهٔ میکروفون را بزن و عادی حرف بزن، یا بنویس. دستیار یادآوری و یادداشت می‌سازد، عوض می‌کند، تیک می‌زند و به سؤال‌ها جواب می‌دهد. قبل از انجام، نشان می‌دهد چه می‌کند. چند کار را هم می‌شود با هم گفت. برای استفاده باید وارد حساب شوی و توکن داشته باشی (با ورود، ۱۰ توکن هدیه می‌گیری).",
                "Tap the mic and talk normally, or type. The assistant creates, changes and ticks reminders and notes and answers questions, and shows what it will do first. You can say several things at once. You need to be signed in with some tokens (you get 10 as a gift when you sign in)."),
            where = t("دکمهٔ میکروفون در خانه", "The mic button on Home"),
            examples = listOf(t("هفتهٔ بعد چه کارهایی دارم؟", "what do I have next week?"), t("جلسهٔ فردا رو ببر ساعت ۱۱", "move tomorrow's meeting to 11"),
                t("قرص رو هر ۸ ساعت کن", "make the pills every 8 hours"), t("نزدیک‌ترین تولد کیه؟", "whose birthday is next?"))),
        Topic(Icons.Rounded.StickyNote2, t("یادداشت و لیست", "Notes and lists"),
            t("یادداشت‌ها رنگ، سنجاق، فهرست، شماره و چک‌لیست دارند. در کارت یادداشت روی آیتم‌های چک‌لیست بزن تا تیک بخورد. ویجت یادداشت با هر تغییر، حتی با دستیار، به‌روز می‌شود.",
                "Notes have colours, pins, bullet and numbered lists and checklists. Tap a checklist item on the note card to tick it. The note widget updates with every change, even from the assistant."),
            where = t("تب «یادداشت»", "The Notes tab"),
            examples = listOf(t("یه لیست خرید بذار: شیر، نان، تخم‌مرغ", "make a shopping list: milk, bread, eggs"), t("نان رو تیک بزن", "tick the bread"))),
    )),
    Section(t("یادیمو و بازی", "Yadimo and the game"), "🐣", listOf(
        Topic(Icons.Rounded.Face, t("یادیمو، دوست زندهٔ برنامه", "Yadimo, the app's living buddy"),
            t("• گاهی از کنار صفحه سرک می‌کشد و یک حرف کوتاه دربارهٔ روزت می‌زند.\n• سلام صبح: هر روز سر ساعتی که بگویی، صفحه‌ای مثل زنگ با برنامهٔ روز، آب‌وهوا و یک جمله.\n• کمد: رنگ، کلاه، عینک، لباس و وسیله؛ بعضی‌ها با سطح باز می‌شوند و بعضی با توکن.\n• چقدر حرف بزند را خودت تنظیم کن (ساکت تا پرحرف).",
                "• Now and then it peeks in from the side with a short word about your day.\n• Morning hello: every day at the time you pick, an alarm-like page with your day, the weather and a line.\n• Wardrobe: colour, hat, glasses, costume and item; some unlock with levels, some cost tokens.\n• Set how chatty it is (silent to chatty)."),
            where = t("پروفایل ← یادیمو و خانه‌اش", "Profile → Yadimo and its home")),
        Topic(Icons.Rounded.Home, t("خانهٔ یادیمو", "Yadimo's home"),
            t("اتاق یادیمو: غذا بده (کلوچه توی ظرف می‌افتد)، توپ‌بازی کن، روی سکو شارژش کن، به گل آب بده، روی تخت بخوابانش، نازش کن، یا بلندش کن و جای دیگری بگذار. روی هر وسیله بزنی یادیمو می‌رود سراغش. اگر تنهایش بگذاری خودش هم به فکر خودش است. سیری، انرژی و خوشحالی‌اش با زمان کم می‌شود و با کارهایی که انجام می‌دهی بالا می‌رود.",
                "Yadimo's room: feed it (cookies drop in the bowl), play ball, charge it on the pad, water the plant, put it to bed, pat it, or pick it up and put it down elsewhere. Tap anything and Yadimo goes to it; left alone it looks after itself. Its food, energy and joy go down with time and up with what you get done."),
            where = t("پروفایل ← یادیمو و خانه‌اش ← رفتن به خانه", "Profile → Yadimo and its home → Visit"), image = R.drawable.help_room),
        Topic(Icons.Rounded.Widgets, t("ویجت خانهٔ یادیمو", "Yadimo's home widget"),
            t("یک ویجت فقط برای تماشا: یادیمو آنجا خودش زندگی می‌کند؛ گل آب می‌دهد، کتاب می‌خواند، با توپ بازی می‌کند، غذا می‌خورد، شب می‌خوابد و با خودش حرف می‌زند. بالایش سیری، انرژی و خوشحالی‌اش دیده می‌شود. با زدن روی ویجت، به خانه‌اش می‌روی. روانی حرکتش را از «یادیمو و خانه‌اش» تنظیم کن.",
                "A watch-only widget: Yadimo lives its own life there; it waters the plant, reads, plays ball, eats, sleeps at night and talks to itself. Its food, energy and joy show at the top. Tap the widget to visit its home. Set how smooth it moves in “Yadimo and its home”."),
            where = t("نگه‌داشتن صفحهٔ اصلی گوشی ← ابزارک‌ها ← یادیمو", "Long-press the home screen → Widgets → Yadimo"), image = R.drawable.help_widget),
        Topic(Icons.Rounded.EmojiEvents, t("سطح، مأموریت و نشان", "Levels, missions and badges"),
            t("• امتیاز: ۱۰ برای هر کار انجام‌شده (۵ اگر دیرتر از دو ساعت)، ۵ برای هر روز فعال، ۵ برای ساختن یادآوری (۱۰ با صدا)، ۲ برای هر یادداشت، ۵۰ برای هر نشان.\n• سطح: با امتیاز بالا می‌رود؛ هر سطح لباس و وسیلهٔ تازه‌ای برای یادیمو باز می‌کند («جایزهٔ سطح‌ها»).\n• مأموریت‌ها: روی هر کدام بزن تا به جای انجامش بروی؛ بعد «دریافت» را بزن. جایزه (امتیاز و گاهی توکن) فقط یک بار داده می‌شود.\n• نشان‌ها: ۵ تا از آن‌ها زیر اسمت دیده می‌شود؛ روی ردیف بزن تا همه را ببینی و روی هر نشان بزن تا بفهمی چطور به دستش می‌آوری.",
                "• Points: 10 per thing done (5 if over two hours late), 5 per active day, 5 per reminder created (10 by voice), 2 per note, 50 per badge.\n• Level: goes up with points; each level unlocks new outfits and items for Yadimo (Level rewards).\n• Missions: tap one to go and do it, then tap Claim. A reward (points, sometimes tokens) is paid once.\n• Badges: five show under your name; tap the row to see all and tap one to learn how to earn it."),
            where = t("تب «پروفایل»", "The Profile tab")),
        Topic(Icons.Rounded.MilitaryTech, t("قاب‌های درخشان خریداران", "Shining frames for buyers"),
            t("هر کس توکن خریده باشد، دور عکس پروفایلش یک قاب شش‌ضلعی فلزی می‌گیرد که هرچه بیشتر بخرد، پرزرق‌وبرق‌تر می‌شود: برنزی، نقره‌ای (ستاره)، طلایی (ستاره و تزئین)، الماسی (بال و پرتو). اعضای گروه هم آن را می‌بینند. کنار اسم، مدالِ سطح بازی هم هست.",
                "Anyone who has bought tokens gets a metal hexagon frame round their profile picture that gets fancier the more they buy: bronze, silver (stars), gold (stars and a crest), diamond (wings and rays). Group members see it too. Next to the name is a medal with the game level."),
            visual = { TierSamples() }),
    )),
    Section(t("حساب و اطلاعات", "Account and data"), "🔐", listOf(
        Topic(Icons.Rounded.AccountCircle, t("پروفایل، اسم و نام کاربری", "Profile, name and username"),
            t("• اسم: روی اسمت بزن تا عوضش کنی؛ یادیمو با همین اسم صدایت می‌کند. اسم‌هایی مثل «ادمین» و «پشتیبانی» ثبت نمی‌شوند.\n• نام کاربری: اگر وارد حساب باشی، یک نام کاربری یکتا داری (مثلاً @sara_k) که دیگران با آن پیدایت می‌کنند و به گروه دعوتت می‌کنند. فقط حروف انگلیسی، عدد و _ ؛ هنگام نوشتن نشان می‌دهد آزاد است یا نه.\n• تنظیمات برنامه از چرخ‌دندهٔ بالای پروفایل باز می‌شود؛ نقطهٔ قرمز یعنی چیزی (مثل اجازهٔ اعلان) نیاز به توجه دارد.",
                "• Name: tap it to change it; Yadimo calls you by it. Names like “admin” and “support” cannot be taken.\n• Username: signed in, you have a unique username (e.g. @sara_k) others find and invite you by. English letters, digits and _ only; it tells you as you type whether it is free.\n• The app's settings open from the gear at the top of Profile; a red dot means something (e.g. notification permission) needs attention."),
            where = t("تب «پروفایل»", "The Profile tab")),
        Topic(Icons.Rounded.Toll, t("توکن و حساب", "Tokens and account"),
            t("همهٔ امکانات بدون حساب کار می‌کنند؛ فقط دستیار هوشمند، همگام‌سازی ابری و گروه‌ها حساب می‌خواهند و دستیار توکن هم مصرف می‌کند. با «ورود با بازار» ۱۰ توکن هدیه می‌گیری. هر دستور به دستیار معمولاً کمتر از ۱ توکن است. اول با هدیه امتحان کن، بعد اگر به کارت آمد بسته بخر؛ توکن برای لباس و لوازم یادیمو هم خرج می‌شود.",
                "Everything works without an account; only the smart assistant, cloud sync and groups need one, and the assistant also uses tokens. Sign in with Bazaar for 10 free tokens. A command usually costs under 1 token. Try the gift first, then buy a pack if it helps; tokens also pay for Yadimo's outfits and items."),
            where = t("⚙️ تنظیمات ← حساب کاربری", "⚙️ Settings → Account")),
        Topic(Icons.Rounded.CloudSync, t("پشتیبان‌گیری و همگام‌سازی ابری", "Backup and cloud sync"),
            t("یادآوری‌ها و یادداشت‌ها روی همین گوشی‌اند. دو راه برای اینکه با عوض شدن گوشی از دست نروند:\n• همگام‌سازی ابری (وارد حساب باش و روشنش کن): یک نسخهٔ رمزنگاری‌شده روی سرور یادیمو نگه داشته می‌شود. روی گوشی جدید فقط با همان حساب وارد شو و آن را روشن کن تا همه‌چیز برگردد؛ روی دو گوشی هم هماهنگ می‌ماند. هر وقت خواستی «پاک کردن نسخهٔ ابری» را بزن.\n• فایل پشتیبان: «گرفتن نسخه» و «بازیابی». یادیمو بعد از هر تغییر هم یک پشتیبان خودکار در پوشهٔ Download/Yadar می‌گذارد که با حذف برنامه هم می‌ماند.\nنسخه‌های جدید را روی همین نصب کن تا اطلاعات بماند.",
                "Reminders and notes live on this phone. Two ways to keep them across phones:\n• Cloud sync (sign in and turn it on): an encrypted copy is kept on the Yadimo server. On a new phone sign in to the same account and turn it on to get everything back; two phones stay in step. Tap Delete cloud copy any time.\n• Backup file: Export and Restore. Yadimo also saves an automatic backup in Download/Yadar after every change, which survives uninstalling.\nInstall new versions over this one to keep your data."),
            where = t("⚙️ تنظیمات ← پشتیبان‌گیری", "⚙️ Settings → Backup")),
        Topic(Icons.Rounded.Inventory2, t("پک یادآوری", "Reminder packs"),
            t("چند یادآوری (مثلاً تولدهای خانواده) را به‌صورت «پک» برای دیگران بفرست تا با یک لمس به یادیمو خودشان اضافه کنند.",
                "Send reminders (e.g. family birthdays) as a pack others can add with one tap."),
            where = t("⚙️ تنظیمات ← اشتراک‌گذاری یادآوری‌ها", "⚙️ Settings → Share reminders")),
    )),
    Section(t("گروه‌ها", "Groups"), "👥", listOf(
        Topic(Icons.Rounded.Groups, t("یادآوری مشترک با خانواده و دوستان", "Shared reminders with family and friends"),
            t("• روشن کردن: «⚙️ تنظیمات ← گروه‌ها». بعد بالای خانه دکمهٔ 👥 می‌آید. (باید وارد حساب باشی و نام کاربری داشته باشی.)\n• ساختن گروه: اسم بده و انتخاب کن چه کسی یادآوری بسازد و عضو اضافه کند: فقط سازنده، یا همه.\n• دعوت: «+ افزودن عضو» و نام کاربری او. دعوت‌شده اعلان می‌گیرد و در صفحهٔ گروه‌ها «قبول» یا «رد» می‌زند.\n• یادآوری مشترک: برای همه یا فقط یک نفر (آن‌وقت فقط گوشی همان یک نفر زنگ می‌زند). «یک نفر کافیه» (مثل پرداخت قبض) یا «همه باید انجام بدن» (مثل دارو)؛ زیرش نوشته می‌شود چه کسانی انجام داده‌اند. اگر «به بقیه خبر بده» روشن باشد، وقتی کسی انجامش داد اعلان می‌آید.\n• پروفایل اعضا: روی اسم هر عضو بزن تا یادیمو، سطح، نشان‌ها و قابش را ببینی.\n• خروج: از منوی ⋮ بالای گروه. اگر سازنده برود، گروه به قدیمی‌ترین عضو می‌رسد. سازنده می‌تواند عضوی را حذف کند (نگه داشتن اسمش) یا گروه را پاک کند.\n• هر گروه تا ۲۰ نفر و هر نفر تا ۵ گروه.\n• حریم: یادآوری‌های گروه رمزنگاری‌شده روی سرور نگه داشته می‌شوند تا به همه برسند و بعد از ۳۰ روز از پایان کار پاک می‌شوند. کارهای گروه در آمار و امتیاز تو حساب نمی‌شوند و یادآوری‌های شخصی‌ات جدا می‌مانند.\n• به‌روز شدن: وقتی برنامه باز است چند ثانیه‌ای، در پس‌زمینه تا حدود ۱۵ دقیقه؛ دکمهٔ 🔄 همان لحظه به‌روز می‌کند.",
                "• Turn on: ⚙️ Settings → Groups. Then a 👥 button shows at the top of Home. (You need to be signed in with a username.)\n• Make a group: give it a name and choose who may add reminders and invite: only the owner, or everyone.\n• Invite: “+ Add” and their username. They get a notification and tap Accept or Decline on the Groups page.\n• A shared reminder: for everyone or just one person (then only their phone rings). “One is enough” (like paying a bill) or “Everyone does it” (like pills); it shows who did it. With “Tell the others” on, a notification arrives when someone does it.\n• Member profiles: tap a member's name to see their Yadimo, level, badges and frame.\n• Leave: the ⋮ menu on top of the group. If the owner leaves, the longest-standing member takes over. The owner can remove a member (long-press the name) or delete the group.\n• Up to 20 people per group and 5 groups each.\n• Privacy: group reminders are kept encrypted on the server so they reach everyone, and are deleted 30 days after they finish. Group things do not count in your stats or points, and your own reminders stay separate.\n• Updates: a few seconds while the app is open, up to about 15 minutes in the background; the 🔄 button updates at once."),
            where = t("⚙️ تنظیمات ← گروه‌ها", "⚙️ Settings → Groups")),
    )),
    Section(t("تنظیمات و ویجت‌ها", "Settings and widgets"), "⚙️", listOf(
        Topic(Icons.Rounded.HealthAndSafety, t("سلامت اعلان‌ها (مهم)", "Notification health (important)"),
            t("برای اینکه یادآوری‌ها سر وقت برسند: اجازهٔ اعلان، آلارم دقیق، زنگ تمام‌صفحه و برداشتن محدودیت باتری را سبز کن. در گوشی‌های شیائومی، سامسونگ و هواوی «اجرای خودکار» برنامه را هم روشن کن. با «اعلان آزمایشی» امتحانش کن. اگر یادآوری نیامد، اول اینجا را ببین.",
                "So reminders arrive on time: make notification permission, exact alarms, full-screen alarms and battery unrestricted all green. On Xiaomi, Samsung and Huawei also turn on the app's Autostart. Try it with the test notification. If a reminder did not arrive, look here first."),
            where = t("⚙️ تنظیمات ← سلامت اعلان‌ها", "⚙️ Settings → Notification health")),
        Topic(Icons.Rounded.Palette, t("ظاهر، زبان و پیش‌فرض‌ها", "Look, language and defaults"),
            t("• زبان فارسی یا انگلیسی، تقویم شمسی یا میلادی، حالت روشن یا تیره و چند تم رنگی.\n• صدا و لرزش یادآوری و زنگ (قابل پیش‌شنود).\n• پیش‌فرض‌ها: مدت تعویق، اعلان یا زنگ، یادآوری زودتر، مدت زنگ.\n• «حالت حداکثر دقت»: مثل ساعت زنگ‌دار زمان‌بندی می‌کند تا سیستم عقبش نیندازد.\n• دسته‌بندی‌ها: دستهٔ خودت را بساز.",
                "• Persian or English, Persian or Gregorian calendar, light or dark and several colour themes.\n• Reminder and alarm sound and vibration (with preview).\n• Defaults: snooze length, notification or alarm, advance notice, ring time.\n• Maximum reliability: schedules like an alarm clock so the system never delays it.\n• Categories: make your own."),
            where = t("⚙️ تنظیمات", "⚙️ Settings")),
        Topic(Icons.Rounded.Widgets, t("ویجت‌ها", "Widgets"),
            t("روی صفحهٔ اصلی گوشی انگشت را نگه دار ← ابزارک‌ها ← یادیمو. ثبت سریع، دستیار صوتی، امروز، هفته، پیش رو، نزدیک‌ترین یادآوری، یک یادداشت دلخواه و خانهٔ یادیمو را می‌توانی بگذاری. ویجت‌ها با زبان برنامه راست‌به‌چپ یا چپ‌به‌راست می‌شوند.",
                "Long-press the home screen → Widgets → Yadimo: quick add, voice assistant, today, week, coming up, next reminder, any note and Yadimo's home. Widgets follow the app's language direction.")),
        Topic(Icons.Rounded.Insights, t("آمار", "Stats"),
            t("در «آمار» می‌بینی چقدر از کارهایت را انجام داده‌ای، کدام دسته‌ها خوب پیش می‌روند و کدام کارها بیشتر جا می‌مانند، در بازه‌های هفته، ماه و کل. اگر یادآوری‌ای را حذف کنی، «انجام نشد»هایش هم از آمار پاک می‌شود؛ ولی کارهایی که انجام داده‌ای می‌مانند. کارهای گروه در آمار نمی‌آیند.",
                "Stats show how much you got done, which categories go well and what tends to slip, over a week, a month or all time. Deleting a reminder also removes its missed entries; what you did stays counted. Group things are not in your stats."),
            where = t("تب «آمار»", "The Stats tab")),
    )),
    Section(t("اگر مشکلی بود", "If something is wrong"), "🛠", listOf(
        Topic(Icons.Rounded.Lightbulb, t("پرسش‌های پرتکرار", "Common questions"),
            t("• اعلان نمی‌آید: «⚙️ تنظیمات ← سلامت اعلان‌ها» را سبز کن و در شیائومی/سامسونگ «اجرای خودکار» را روشن کن.\n• گوشی عوض کردم: وارد همان حساب شو و «همگام‌سازی ابری» را روشن کن؛ یا از «بازیابی» فایل پشتیبان را برگردان.\n• دستیار می‌گوید توکن ندارم: از کارت توکن در تنظیمات بسته بخر، یا از مأموریت‌ها توکن هدیه بگیر.\n• نام کاربری را نمی‌پذیرد: فقط حروف انگلیسی، عدد و _ ؛ ۳ تا ۲۰ حرف؛ و کسی دیگر آن را نگرفته باشد.\n• گروه به‌روز نمی‌شود: دکمهٔ 🔄 را بزن و مطمئن شو اینترنت داری و وارد حسابی.\n• ویجت یادیمو کند است: در «یادیمو و خانه‌اش» روانی را «کم» یا «معمولی» کن.\n• نسخه‌های جدید را روی همین نصب کن تا اطلاعات نماند.",
                "• No notification: make ⚙️ Settings → Notification health green and turn on Autostart on Xiaomi/Samsung.\n• New phone: sign in to the same account and turn on Cloud sync, or Restore a backup file.\n• The assistant says no tokens: buy a pack on the token card in Settings, or earn some from missions.\n• The username is refused: English letters, digits and _ only, 3–20 characters, and not taken by someone else.\n• A group does not update: tap 🔄 and check you are online and signed in.\n• The Yadimo widget is slow: set smoothness to Low or Normal in “Yadimo and its home”.\n• Install new versions over this one to keep your data.")),
    )),
)

/** Everything Yadimo can do, in sections you open one at a time, with pictures and sentences to try. Opened from Settings. */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    val all = remember { sections() }
    var open by rememberSaveable { mutableStateOf("0.0") }
    // Edge to edge, so the insets below are measured once and the last card is never hidden behind the nav bar.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, t("بستن", "Close")) }
                    Text(t("راهنمای یادیمو", "Yadimo guide"), style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 64.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(t("روی هر عنوان بزن تا باز شود. هر بخش می‌گوید چیست، کجاست و چطور کار می‌کند.", "Tap a title to open it. Each part says what it is, where to find it and how it works."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    all.forEachIndexed { si, section ->
                        item { Text("${section.emoji}  ${section.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp, start = 4.dp)) }
                        section.topics.forEachIndexed { ti, topic ->
                            val id = "$si.$ti"
                            item(key = id) { TopicCard(topic, open == id) { open = if (open == id) "" else id } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicCard(topic: Topic, expanded: Boolean, onToggle: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(topic.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(topic.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(if (expanded) 180f else 0f))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (topic.where.isNotEmpty()) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Flag, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text(topic.where, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    topic.image?.let { res ->
                        Image(painterResource(res), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
                    }
                    topic.visual?.invoke()
                    if (topic.text.isNotEmpty()) Text(topic.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (topic.examples.isNotEmpty()) {
                        Text(t("امتحان کن:", "Try:"), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            topic.examples.forEach { example ->
                                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.FormatQuote, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Spacer(Modifier.width(6.dp))
                                        Text(example, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
