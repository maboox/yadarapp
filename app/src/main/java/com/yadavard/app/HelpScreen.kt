@file:OptIn(ExperimentalLayoutApi::class)

package com.yadavard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private class Topic(val icon: ImageVector, val title: String, val text: String, val examples: List<String> = emptyList())

private fun topics() = listOf(
    Topic(Icons.Rounded.Mic, t("دستیار هوشمند", "Smart assistant"),
        t("دکمهٔ میکروفون را بزن و عادی حرف بزن، یا بنویس. دستیار یادآوری و یادداشت می‌سازد، عوض می‌کند، تیک می‌زند و به سؤال‌هایت جواب می‌دهد. قبل از انجام هر کاری، پیشنهادش را نشان می‌دهد تا تأیید کنی.",
            "Tap the mic and talk normally, or type. The assistant creates, changes and ticks reminders and notes and answers questions. It shows what it will do before doing it.")),
    Topic(Icons.Rounded.AddAlert, t("ساختن یادآوری", "Create reminders"),
        t("زمان، تکرار، دسته و نوع یادآوری را از جمله‌ات می‌فهمد. چند کار را هم با هم بگو.",
            "It understands time, repeat, category and alert type. You can say several things at once."),
        listOf(t("فردا ساعت ۱۰ جلسه کاری", "work meeting tomorrow 10am"), t("هر شب ساعت ۱۰ قرص، با زنگ", "pills every night 10pm, as an alarm"),
            t("۲۰ هر ماه قسط وام", "loan payment on the 20th every month"), t("تولد مامان ۱۲ مهر، دو روز قبلش یادم بنداز", "mom's birthday Oct 4, remind me two days before"),
            t("هر شنبه و سه‌شنبه ساعت ۶ عصر باشگاه", "gym every Saturday and Tuesday 6pm"))),
    Topic(Icons.Rounded.EditCalendar, t("عوض کردن و جابه‌جا کردن", "Change and move"),
        t("لازم نیست اسم دقیق را بگویی؛ از معنی پیدایش می‌کند. اگر چند مورد شبیه هم باشد، می‌پرسد کدام.",
            "No need for the exact name; it finds it by meaning and asks if several match."),
        listOf(t("جلسهٔ فردا رو ببر ساعت ۱۱", "move tomorrow's meeting to 11"), t("قرص رو هر ۸ ساعت کن", "make the pills every 8 hours"),
            t("یادآوری باشگاه رو زنگی کن", "make the gym reminder an alarm"), t("قسط رو یک ساعت دیگه دوباره یادم بنداز", "remind me about the loan again in an hour"))),
    Topic(Icons.Rounded.TaskAlt, t("انجام شد، انجام نشد، حذف", "Done, missed, delete"),
        t("«انجام نشد» با حذف فرق دارد: در آمار ثبت می‌شود و در بخش جدا دیده می‌شود. در صفحهٔ اصلی هم با تیک «انجام شد» و با کشیدن «انجام نشد» یا حذف کن.",
            "“Missed” is not delete: it is kept in stats and shown separately. On the home screen, tick for done, swipe for missed or delete."),
        listOf(t("قرص رو خوردم", "I took the pills"), t("باشگاه امروز نرفتم", "I skipped the gym today"), t("جلسه فردا کنسل شد", "tomorrow's meeting is cancelled"))),
    Topic(Icons.Rounded.QuestionAnswer, t("پرسیدن از برنامه‌ات", "Ask about your plans"),
        t("جواب کوتاه می‌دهد و خود کارها را به‌صورت کارت نشان می‌دهد؛ با لمس هر کارت بازش کن. از ویجت دستیار روی صفحهٔ اصلی گوشی هم کار می‌کند.",
            "It answers briefly and shows the items as cards you can open, also from the assistant widget."),
        listOf(t("هفتهٔ بعد چه کارهایی دارم؟", "what do I have next week?"), t("فردا جلسهٔ کاری دارم؟", "do I have a work meeting tomorrow?"),
            t("نزدیک‌ترین تولد کیه؟", "whose birthday is next?"), t("تو لیست خرید چی نوشتم؟", "what's on my shopping list?"))),
    Topic(Icons.Rounded.StickyNote2, t("یادداشت و لیست", "Notes and lists"),
        t("یادداشت‌ها رنگ، سنجاق، فهرست، شماره و چک‌لیست دارند. در کارت یادداشت روی مربع چک‌لیست بزن تا تیک بخورد. دستیار هم لیست‌ها را می‌فهمد.",
            "Notes have colours, pins, bullets, numbers and checklists; tap a box on the card to tick it. The assistant understands lists too."),
        listOf(t("یه لیست خرید بذار: شیر، نان، تخم‌مرغ", "make a shopping list: milk, bread, eggs"), t("به لیست خرید پنیر هم اضافه کن", "add cheese to the shopping list"),
            t("نان رو تیک بزن", "tick the bread"), t("یادداشت ایده‌ها رو پاک کن", "delete the ideas note"))),
    Topic(Icons.Rounded.Widgets, t("ویجت‌ها", "Widgets"),
        t("روی صفحهٔ اصلی گوشی انگشت را نگه دار ← ابزارک‌ها ← یادیمو. ثبت سریع، دستیار صوتی، امروز، هفته، پیش رو، نزدیک‌ترین یادآوری و یک یادداشت دلخواه را می‌توانی روی صفحه بگذاری. ویجت یادداشت با هر تغییر، حتی با دستیار، به‌روز می‌شود.",
            "Long-press the home screen → Widgets → Yadimo: quick add, voice assistant, today, week, coming up, next reminder and any note. The note widget updates with every change, even from the assistant.")),
    Topic(Icons.Rounded.Home, t("خانهٔ یادیمو و ویجتش", "Yadimo's home and its widget"),
        t("در «پروفایل ← خانهٔ یادیمو» یادیمو در اتاق خودش زندگی می‌کند: غذا بده، توپ‌بازی کن، شارژش کن، به گلش آب بده، بخوابانش، نازش کن یا بلندش کن و جای دیگری بگذار. سیری، انرژی و خوشحالی‌اش با زمان کم می‌شود و با کارهایی که انجام می‌دهی بالا می‌رود. ویجت «خانهٔ یادیمو» فقط برای تماشاست: یادیمو آن‌جا خودش زندگی می‌کند و با خودش حرف می‌زند؛ با زدن رویش به خانه‌اش می‌روی. روانی حرکتش را از همان بخش تنظیم کن.",
            "In Profile → Yadimo's home, Yadimo lives in its own room: feed it, play ball, charge it, water its plant, put it to bed, pat it, or pick it up and put it somewhere else. Its food, energy and joy go down with time and up with what you get done. The Yadimo's home widget is just to watch: Yadimo lives its life and talks to itself there; tap it to visit. Set how smooth it moves in the same place.")),
    Topic(Icons.Rounded.EmojiEvents, t("سطح، مأموریت و نشان", "Levels, missions and badges"),
        t("با هر کار انجام‌شده، روزهای فعال و ساختن یادآوری امتیاز می‌گیری و سطحت بالا می‌رود؛ هر سطح لباس و لوازم تازه‌ای برای یادیمو باز می‌کند. مأموریت‌ها در «پروفایل» هستند؛ روی هر کدام بزن تا به جای انجامش بروی و بعد جایزه‌اش (امتیاز و گاهی توکن) را بگیر. نشان‌ها هم با کارهای خاص مثل ۷ روز پیاپی یا صد کار به دست می‌آیند.",
            "You earn points for things done, active days and creating reminders, and your level goes up; each level opens new outfits for Yadimo. Missions are in Profile: tap one to go and do it, then claim its reward (points, sometimes tokens). Badges come with special feats like a 7-day streak or a hundred things done.")),
    Topic(Icons.Rounded.AccountCircle, t("پروفایل، اسم و نام کاربری", "Profile, name and username"),
        t("در «پروفایل» روی اسمت بزن تا عوضش کنی؛ یادیمو با همین اسم صدایت می‌کند. اگر وارد حساب شده باشی، یک «نام کاربری» هم داری (مثلاً @sara_k) که یکتاست و دیگران با آن پیدایت می‌کنند؛ فقط حروف انگلیسی، عدد و _ . تنظیمات برنامه هم از چرخ‌دندهٔ بالای پروفایل باز می‌شود؛ اگر نقطهٔ قرمز دیدی، یعنی چیزی (مثلاً اجازهٔ اعلان) نیاز به توجه دارد.",
            "On Profile, tap your name to change it; Yadimo calls you by it. Signed in, you also have a unique username (e.g. @sara_k) others find you by: English letters, digits and _ only. The app's settings open from the gear at the top of Profile; a red dot means something (e.g. notification permission) needs attention.")),
    Topic(Icons.Rounded.Toll, t("توکن و حساب", "Tokens and account"),
        t("همهٔ امکانات بدون حساب کار می‌کنند؛ فقط دستیار هوشمند با هوش مصنوعی کار می‌کند و توکن می‌خواهد. با «ورود با بازار» ۱۰ توکن هدیه می‌گیری. هر دستور معمولاً کمتر از ۱ توکن است. اول با هدیه امتحان کن، بعد اگر به کارت آمد بسته بخر.",
            "Everything works without an account; only the smart assistant uses AI and tokens. Sign in with Bazaar for 10 free tokens. A command usually costs under 1 token. Try it with the gift first, then buy a pack if it helps.")),
    Topic(Icons.Rounded.Backup, t("پشتیبان‌گیری و همگام‌سازی ابری (مهم)", "Backup and cloud sync (important)"),
        t("یادآوری‌ها و یادداشت‌ها روی همین گوشی‌اند. اگر وارد حساب باشی، «همگام‌سازی ابری» را در «پروفایل ← ⚙️ تنظیمات ← پشتیبان‌گیری» روشن کن: یک نسخهٔ رمزنگاری‌شده روی سرور یادیمو نگه داشته می‌شود و روی گوشی جدید با ورود به همان حساب همه‌چیز برمی‌گردد؛ روی دو گوشی هم هماهنگ می‌ماند. اگر روشنش نکنی، اطلاعات فقط روی گوشی است و با عوض شدن یا ریست شدن گوشی فقط با نسخهٔ پشتیبان برمی‌گردد. یادیمو بعد از هر تغییر یک پشتیبان خودکار در Download/Yadar هم می‌گذارد؛ در گوشی جدید «بازیابی» را بزن.",
            "Reminders and notes live on this phone. Signed in, turn on Cloud sync in Profile → ⚙️ Settings → Backup: an encrypted copy is kept on the Yadimo server, and a new phone gets everything back by signing in to the same account; two phones stay in step. Without it, data is only on the phone and only a backup brings it back after a change or reset. Yadimo also saves an automatic backup in Download/Yadar after every change; on a new phone, tap Restore.")),
    Topic(Icons.Rounded.Groups, t("گروه‌ها (آزمایشی)", "Groups (preview)"),
        t("یادآوری مشترک با خانواده یا دوستان. فعلاً از تنظیمات پنهان روشن می‌شود (آیکن «پشتیبان‌گیری» را ۳ ثانیه نگه دار ← «گروه‌ها»). بعد بالای صفحهٔ خانه دکمهٔ 👥 می‌آید: گروه بساز، با نام کاربری دعوت کن و یادآوری مشترک بساز. هر یادآوری می‌تواند برای همه یا یک نفر باشد، با «یک نفر کافیه» یا «همه باید انجام بدن»، و اگر بخواهی وقتی کسی انجامش داد به بقیه خبر می‌دهد. کارهای گروه در آمار و امتیاز خودت حساب نمی‌شوند.",
            "Shared reminders with family or friends. For now they are turned on in the hidden settings (hold the Backup icon for 3 seconds → Groups). Then a 👥 button shows at the top of home: make a group, invite by username and add shared reminders, each for everyone or one person, with “one is enough” or “everyone does it”, and optionally a heads-up to the others when someone does it. Group reminders do not count in your own stats or points.")),
    Topic(Icons.Rounded.Inventory2, t("پک یادآوری", "Reminder packs"),
        t("چند یادآوری (مثلاً تولدهای خانواده) را به‌صورت «پک» برای دیگران بفرست تا با یک لمس به یادیمو خودشان اضافه کنند.",
            "Send reminders (e.g. family birthdays) as a pack others can add with one tap.")),
    Topic(Icons.Rounded.Insights, t("آمار", "Stats"),
        t("در «آمار» می‌بینی چقدر از کارهایت را انجام داده‌ای، کدام دسته‌ها خوب پیش می‌روند و کدام کارها بیشتر جا می‌مانند. اگر یادآوری‌ای را حذف کنی، «انجام نشد»هایش هم از آمار پاک می‌شود؛ ولی کارهایی که انجام داده‌ای می‌مانند.",
            "Stats show how much you got done, which categories go well and what tends to slip. Deleting a reminder also removes its missed entries from the stats; what you did stays counted.")),
)

/** Everything Yadimo can do, with sentences to try. Opened from Settings. */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    // Edge to edge, so the insets below are measured once and the last card is never hidden behind the nav bar.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, t("بستن", "Close")) }
                    Text(t("راهنمای یادیمو", "Yadimo guide"), style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 64.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    topics().forEach { topic ->
                        item {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center) {
                                            Icon(topic.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(topic.title, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(topic.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (topic.examples.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
    }
}
