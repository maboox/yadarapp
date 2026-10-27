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
- نسخهٔ فعلی: 3.5.2 (versionCode 47). هر نسخه versionCode را بالا ببر.
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

## کارهای باز
1. **هم‌پوشانی لوازم:** هر قطعه/لباس «جا» دارد (سر، صورت، گردن، پشت، دست)؛ انتخاب یکی، بقیهٔ همان جا را بردارد. در استودیو و برنامه.
2. اگر یادیمو در 3.5.2 دیده نشد: نمایش خطای JS داخل برنامه (WebChromeClient.onConsoleMessage) برای پیدا کردن علت.
3. اگر لگ بود: کم کردن تعداد WebViewهای هم‌زمان یا بردن موتور به Compose.
4. ایده‌های دیگر در `docs/IDEAS.md` (گروه و یادآوری مشترک، آیتم‌های قفل‌دار با توکن، مناسبتی‌ها).
