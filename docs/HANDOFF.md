# یادداشت ادامهٔ کار (برای Claude در گفتگوی بعدی)

## کاربر و قواعد
- کاربر فارسی‌زبان است؛ پاسخ‌ها فارسی، روشن و کوتاه.
- **فقط یک APK بده:** `Yadimo-bazaar-<sha7>.apk` (امضای بازار). نسخهٔ تستی جدا نده.
- هرگز کلید، رمز یا Secret را در چت نخواه و نشان نده. Secretها فقط در GitHub Secrets، پنل ادمین سرور یا Coolify.
- شناسهٔ مدل در commit/PR نیاید. پایان commit: خطوط Co-Authored-By و Claude-Session که سیستم می‌دهد.
- PR نساز مگر کاربر بخواهد.

## پروژه
- مخزن `maboox/yadarapp`؛ شاخهٔ کار: `claude/yadar-accounts` (نسخهٔ بازار). شاخهٔ `claude/epic-archimedes-1fxiqb` نسخهٔ آفلاین قدیمی است و به‌روز نمی‌شود.
- اسم برنامه: **یادیمو / Yadimo** (به‌خاطر شکایت مالکیت فکری «یادآر» عوض شد). بسته: `com.yadavard.app.plus` (تغییرناپذیر). پوشهٔ پشتیبان روی گوشی هنوز `Download/Yadar`.
- نسخهٔ فعلی: 3.9.1 (versionCode 55).
- **مهم:** CI با هر push، release قبلی APK را پاک می‌کند؛ لینک را فقط بعد از آخرین push بده و بعدش push نکن. هر نسخه versionCode را بالا ببر.
- CI: `.github/workflows/android.yml` تست‌ها، release، debug و store (با Secrets: KEYSTORE_BASE64، KEYSTORE_PASSWORD، KEY_ALIAS، KEY_PASSWORD) را می‌سازد و در release `apk-claude-yadar-accounts` با نام‌های `Yadimo-<sha7>.apk` و `Yadimo-bazaar-<sha7>.apk` می‌گذارد. هر push بیلد قبلی همان شاخه را لغو می‌کند.
- تست‌های JVM محلی: `/tmp/claude-0/jvmtest/run.sh` (ممکن است در محیط جدید نباشد؛ CI تست‌ها را اجرا می‌کند).
- دانلود asset از sandbox: با API (`Accept: application/octet-stream` روی `/releases/assets/<id>`)؛ لینک مستقیم گاهی 404 می‌دهد.

## سرور
- Go + PocketBase در https://yadar.pish.run (Coolify دوست کاربر، سرور در ایران).
- به‌روزرسانی: در Coolify متغیرهای ساخت `PKG_URL` = zip `yadar-server-selfhosted-<sha7>.zip` از release `server-claude-yadar-accounts` و `PKG_SHA256` (sha256 همان zip) را عوض و Redeploy کنند.
- آخرین سرور: `85aed49` (SHA256 `cf89bd178f2397d1140c86bbf3b71d574557d3e82ad481e9871bf01c63cb7b03`). شامل `user_stats` و view `stats_summary`.
- ورود: ورود درون‌برنامه‌ای بازار (Intent `ir.cafebazaar.intent.action.IAL`)؛ خرید با Poolakey و تأیید امضا روی سرور.

## یادیمو (شخصیت)
- منبع طراحی: `docs/yadimo/` (art.js و volume.js از فایل کاربر؛ acc.js لوازم؛ costumes.js لباس شخصیت‌ها؛ warp.js پختن شکل‌ها و پیچاندن ۲.۵بعدی؛ engine.js کلاس Yadimo؛ studio.js صفحهٔ استودیو؛ bridge.js و yadimo.html برای برنامه).
- `python3 docs/yadimo/bundle.py` → `docs/yadimo-studio.html` (یک فایل برای فرستادن به کاربر).
- `tools/sync-yadimo.sh` → کپی موتور در `app/src/main/assets/yadimo/` (همهٔ نسخه‌ها، شامل لباس شخصیت‌ها).
- برنامه: `Yadimo.kt` (WebView شفاف با `layoutParams` MATCH_PARENT؛ YadimoView، YadimoGreeting روی نوار ضبط، YadimoPeekHost سرک کشیدن، YadimoCustomizer). پیش‌فرض خاموش؛ روشن کردن: تنظیمات ← آیکون پشتیبان‌گیری ۳ ثانیه ← «یادیمو (آزمایشی)». کلیدهای Prefs: `mascot_enabled`، `mascot`.
- بررسی بصری: playwright پایتون با `executable_path='/opt/pw-browsers/chromium'`.
- 3.5.2 رفع «هیچ جا دیده نمی‌شود» (WebView بدون layoutParams). هنوز روی گوشی تأیید نشده.

## نسخهٔ 3.6.0 (چه ساخته شد)
- یادیمو از منوی مخفی بیرون آمد: تنظیمات ← کارت «یادیمو» (`YadimoSettings.kt`).
- کمد و فروشگاه (`YadimoShop.kt` + `YadimoCustomizer` در `Yadimo.kt`): سطح‌ها رایگان/ساده ۵/ویژه ۱۰/شخصیت ۲۰ توکن.
  قیمت سطح‌ها در سرور: settings → `yadimo_price_basic|special|costume`. نقشهٔ سطح هر قطعه در `server/yadimo.go` (و کپی در YadimoShop).
  خریدها در `users.yadimo_owned`، ظاهر در `users.yadimo_look`. API: GET /api/yadar/yadimo، POST /yadimo/buy، POST /yadimo/look.
- سلام صبح سر ساعت (`YadimoHelloActivity`، هشدار exact)، شامل کارهای امروز، آب‌وهوا (GET /api/yadar/weather?lat&lon → Open-Meteo، کش یک‌ساعته) و جملهٔ انگیزشی.
- پیام‌های طول روز (`YadimoTalk.kt` + `YadimoOverlay.kt`): هشدار RTC غیربیدارکننده؛ فقط وقتی صفحه روشن و قفل باز و ۱۰ ثانیه بعد هنوز در استفاده. متن‌ها در `YadimoLines.kt` (تست JVM دارد).
- حباب نوار ضبط: `Modifier.floatAboveEnd` تا جا نگیرد؛ حباب بالای سر با دُم (`YadimoSpeech`).

## نسخهٔ 3.7.0 (گیمیفیکشن)
- زبانهٔ «پروفایل» (`ProfileScreen.kt`): حلقهٔ سطح، مأموریت‌ها، نشان‌ها، جایزهٔ سطح‌ها و تنظیمات یادیمو (از تنظیمات منتقل شد).
- قوانین در `GameRules.kt` (تست JVM)، وضعیت و همگام‌سازی در `Game.kt`. ۲۰ سطح، آستانه 25·(L−1)·L.
- ۸ مأموریت؛ فقط voice3 (۳)، done10 (۲)، streak7 (۵) توکن دارند، یک بار برای هر حساب و هر گوشی (جدول `rewards` در سرور).
- وسیله‌های سطحی (فروشی نیستند): cap۲، sun۳، scarf۴، crown۵، wizard۷، ninja۱۰، pirate۱۵، color:galaxy۲۰.
- سرور: `users.game` (پیشرفت، بیشینه نگه داشته می‌شود)، `users.level_override` (سطح دستی از پنل؛ ۰ = خودکار). API: GET/POST /api/yadar/game، POST /game/claim.
- سرور این نسخه: `yadar-server-selfhosted-5f3caa3.zip`، SHA256 `5f20fcd9995956867d1e02967a37ec9dfc6c6b87d597bcadb78a147743748eca`.
- ترتیب انتشار به خواست کاربر: اول برنامه را تست می‌کند، بعد سرور، بعد بخش سرور را دوباره تست، بعد بازار.

## نسخهٔ 3.8.0
- ویجت «اتاق یادیمو» (`YadimoRoom.kt`، `YadimoRoomWidget`): سه‌لایه (عکس اتاق، حلقهٔ تصویرهای یادیمو در ViewFlipper، صحنهٔ کوتاه هر دکمه). تصویرها را WebView پنهان با `assets/yadimo/roomframes.html` می‌سازد و در `files/room/w<id>/` نگه می‌دارد. روانی: پروفایل ← اتاق یادیمو (۵/۱۵/۳۰).
- صفحهٔ اتاق در برنامه: `YadimoRoomActivity` با `room.html` (زنده). منطق مشترک اتاق: `docs/yadimo/roomscene.js`. آمار پت: `YadimoPet.kt`.
- پیام به کاربران: `Announce.kt` (مخاطب: همه، یا حساب/نسخه/میزان استفاده/کاربران خاص؛ برنامه خودش target را بررسی می‌کند)؛ سرور `announce.go` (جدول announcements، شمارش مخاطب از user_stats). منوی مخفی فقط برای `users.is_admin`.
- نمونه‌های طراحی: `docs/yadimo-sizes-demo.html` (اندازه‌ها)، `yadimo-styles-demo.html` (سه سبک).
- سرور این نسخه: `yadar-server-selfhosted-854e53d.zip`، SHA256 `d733d25abace15c7651078eb2014b93b8177bfd241053a3ee9852bcb7e9367ae`.
- بعداً: رصد استفاده از برنامه‌های دیگر (فعلاً کنار گذاشته شد)، چیدن وسایل اتاق توسط کاربر و فروش وسایل اتاق.

## نسخهٔ 3.9.0 (خانهٔ یادیمو)
- بازی خانه: `docs/yadimo/game.js` (نمونه: `docs/yadimo-game-demo.html`؛ در برنامه `assets/yadimo/game-app.html` با `GAME_MODE='app'`). عمق (x و z)، وسایل قابل استفاده، رفتار خودکار بر اساس نیازها، کشیدن و انداختن یادیمو. پل: `window.yadimoGame` (act/look/night/load/save).
- ویجت `YadimoRoomWidget` (`YadimoRoom.kt`): فقط «پنجره به خانه» = عکس اتاق (برش از همان صحنه) + حلقهٔ نفس‌کشیدن یادیمو وسط و روی فرش؛ دکمه‌ها `YadimoRoomActivity` را با act باز می‌کنند. تصویرها با `widgetframes.html` ساخته و در `files/home/w<id>` گذاشته می‌شوند؛ مجموعهٔ جدید فقط وقتی کامل شد جای قبلی را می‌گیرد.
- کدهای اتاق قبلی (roomscene/roomframes/roomlive) دیگر در برنامه نیستند (فقط در docs).

## نسخهٔ 3.9.1 (لمس داخل ویجت)
- دکمه‌های کناری ویجت حذف شدند. روی ویجت یک شبکهٔ ۶×۴ خانهٔ شفاف لمسی هست (`room_t0..23`). `widgetFrames.make` جای هر وسیله را (به نسبت اندازهٔ ویجت) در `meta.taps` می‌دهد. هر خانه به وسیلهٔ زیرش وصل می‌شود: یادیمو=pet، توپ=play، ظرف=feed، سکو=charge، تخت=sleep. گلدان و بقیهٔ اتاق برنامه را باز می‌کنند.
- برای هر کار، یک صحنهٔ کوتاه درجا ساخته می‌شود (`CLIPS` در game.js؛ ۸ تا ۱۰ فریم در ثانیه، حدود ۲.۵ ثانیه). لمس یک broadcast به `YadimoRoomWidget` می‌فرستد. `RoomWidgets.tap` صحنه را یک بار پخش می‌کند و بعد با Handler (با goAsync) به حلقهٔ عادی برمی‌گردد.
- در ویجت، ظرف و سبد و سکو کمی نزدیک‌تر به فرش هستند (`WIDGET_SPOTS`) تا در تصویر دیده شوند.
- وقتی تصویرها در حال ساخته شدن‌اند (`RoomFrames.busy`)، حباب ویجت «یادیمو داره اتاقش رو مرتب می‌کنه…» را نشان می‌دهد.

## کارهای باز
1. بازخورد کاربر از 3.6.0 (سلام صبح، پیام‌های روز، فروشگاه). سرور باید قبل از انتشار برنامه به‌روز شود.
3. اگر لگ بود: کم کردن تعداد WebViewهای هم‌زمان یا بردن موتور به Compose.
4. ایده‌های دیگر در `docs/IDEAS.md` (گروه و یادآوری مشترک، آیتم‌های قفل‌دار با توکن، مناسبتی‌ها).
