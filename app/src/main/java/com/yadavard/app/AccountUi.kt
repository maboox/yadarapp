@file:OptIn(ExperimentalFoundationApi::class)

package com.yadavard.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Token balance with one decimal at most, in the app's digits. */
fun tokensLabel(value: Double): String {
    val text = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.ROOT, "%.1f", value)
    return n(text)
}

/** Settings card: sign in, or the profile with the token balance. */
@Composable
fun AccountCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by Account.profile.collectAsState()
    var loggingIn by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf(false) }
    val signIn = rememberBazaarSignIn()
    var usage by remember { mutableStateOf(false) }
    val buyRequested by Account.buyRequested.collectAsState()
    LaunchedEffect(buyRequested, profile != null) {
        if (buyRequested) { Account.buyRequested.value = false; if (profile != null) buying = true }
    }
    LaunchedEffect(Unit) { Account.refresh(context) }

    SettingsCard(t("حساب کاربری", "Account"), Icons.Rounded.AccountCircle) {
        val p = profile
        if (p == null) {
            Text(t("یادآور، یادداشت، تقویم و ویجت‌ها بدون حساب کار می‌کنند. با ورود، دستیار هوشمند (هوش مصنوعی) هم برایت باز می‌شود:",
                "Reminders, notes, calendar and widgets work without an account. Sign in to unlock the smart assistant (AI):"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AssistantPerks()
            Box(Modifier.combinedClickable(onClick = {}, onLongClick = { loggingIn = true })) {
                BazaarButton(enabled = !signIn.busy) { signIn.start() }
            }
            if (signIn.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (signIn.error.isNotBlank()) Text(signIn.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                // the name and username are edited on the profile; here only who is signed in
                Column(Modifier.weight(1f)) {
                    Text(p.name.ifBlank { t("حساب یادیمو", "Yadimo account") } + (if (p.username.isNotBlank()) "  @${p.username}" else ""),
                        style = MaterialTheme.typography.titleSmall)
                    Text(if (p.phone.isNotBlank()) n(p.phone) else if (p.bazaar) t("حساب کافه‌بازار", "Cafe Bazaar account") else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TokenCard(p, onUsage = { usage = true }, onBuy = { buying = true })
            if (AiSettings(context).personal) Text(t("الان از هوش مصنوعی خودت (کلید شخصی) استفاده می‌کنی و توکن یادیمو کم نمی‌شود.",
                "You are using your own AI key, so no Yadimo tokens are used."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t("توکن برای دستیار هوشمند و لوازم یادیمو خرج می‌شود. هر دستور به دستیار معمولاً کمتر از ۱ توکن است؛ کارهای ساده کمتر و کارهای سنگین کمی بیشتر.",
                "Tokens pay for the smart assistant and Yadimo's outfits. A command usually costs under 1 token; simple ones less, heavy ones a bit more."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirmLogout = true }) {
                Icon(Icons.Rounded.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("خروج از حساب", "Sign out"))
            }
        }
    }
    if (loggingIn) LoginDialog(startWithPhone = true) { loggingIn = false }
    if (buying) PacksDialog { buying = false }
    if (usage) UsageDialog { usage = false }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text(t("خروج از حساب؟", "Sign out?")) },
        text = { Text(t("یادآوری‌هایت روی گوشی می‌مانند؛ فقط دستیار هوشمند تا ورود دوباره غیرفعال می‌شود.",
            "Your reminders stay on this phone; only the smart assistant is off until you sign in again.")) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; Account.logout(context) }) { Text(t("خروج", "Sign out")) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(t("انصراف", "Cancel")) } })
}

/** Bazaar's sign-in button: their green, their wording. */
@Composable
fun BazaarButton(modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA960), contentColor = Color.White)) {
        Icon(Icons.Rounded.ShoppingBag, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
        Text(t("ورود با بازار", "Sign in with Bazaar"), fontWeight = FontWeight.Bold)
    }
}

/** One-tap Bazaar sign-in: the installed Bazaar app asks the user once and hands Yadimo their account id. */
class BazaarSignIn {
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
    internal var action: () -> Unit = {}
    fun start() = action()
}

@Composable
fun rememberBazaarSignIn(onSignedIn: () -> Unit = {}): BazaarSignIn {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { BazaarSignIn() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val accountId = BazaarLogin.accountId(result.data)
        if (accountId == null) {
            state.busy = false
            state.error = t("ورود انجام نشد؛ اگر در بازار اجازه ندادی، دوباره امتحان کن.", "Sign-in was not completed; try again.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching { Account.bazaarAppLogin(context, accountId) }
                .onSuccess {
                    val balance = Account.profile.value?.balance ?: 0.0
                    Toast.makeText(context, if (balance > 0) t("خوش آمدی! ${tokensLabel(balance)} توکن داری؛ دستیار را امتحان کن.",
                        "Welcome! You have ${tokensLabel(balance)} tokens; try the assistant.") else t("خوش آمدی!", "Welcome!"),
                        Toast.LENGTH_LONG).show()
                    onSignedIn()
                }
                .onFailure { state.error = it.message.orEmpty() }
            state.busy = false
        }
    }
    state.action = {
        run {
            state.error = ""
            if (!BazaarLogin.installed(context)) {
                state.error = t("برای ورود، برنامهٔ «بازار» باید روی گوشی نصب و به‌روز باشد.", "Cafe Bazaar must be installed to sign in.")
            } else {
                state.busy = true
                runCatching { launcher.launch(BazaarLogin.intent(context)) }.onFailure {
                    state.busy = false
                    state.error = t("بازار ورود درون‌برنامه‌ای را باز نکرد؛ بازار را به‌روز کن و دوباره امتحان کن.",
                        "Bazaar could not open its sign-in; update Bazaar and try again.")
                }
            }
        }
    }
    return state
}

/** What signing in gives: shown before the user signs in. */
@Composable
fun AssistantPerks() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Icons.Rounded.Mic to t("با صدا یا متن یادآوری بساز، عوض کن، تیک بزن یا لغو کن", "Create, change, tick or cancel reminders by voice or text"),
            Icons.Rounded.QuestionAnswer to t("بپرس «هفتهٔ بعد چی دارم؟» و کارها را به‌صورت کارت ببین", "Ask “what do I have next week?” and see the items as cards"),
            Icons.Rounded.Checklist to t("یادداشت و لیست خرید بساز، اضافه کن و تیک بزن", "Make notes and shopping lists, add to them and tick items"),
            Icons.Rounded.CardGiftcard to t("۱۰ توکن هدیه برای امتحان؛ هر دستور معمولاً کمتر از ۱ توکن", "10 free tokens to try; a command usually costs under 1"),
        ).forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Shown when an AI feature needs an account. Phone sign-in is kept for the admin's review number only:
 * long-press the icon to reach it.
 */
@Composable
fun LoginDialog(startWithPhone: Boolean = false, onDone: () -> Unit) {
    var usePhone by remember { mutableStateOf(startWithPhone) }
    val signIn = rememberBazaarSignIn(onSignedIn = onDone)
    if (usePhone) { PhoneLoginDialog(onDone = onDone, onBack = { usePhone = false }); return }

    AlertDialog(onDismissRequest = { if (!signIn.busy) onDone() },
        icon = {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.combinedClickable(onClick = {}, onLongClick = { usePhone = true }))
        },
        title = { Text(t("دستیار هوشمند یادیمو", "Yadimo smart assistant")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AssistantPerks()
                Text(t("فقط با حساب کافه‌بازارت وارد شو؛ پیامک و رمز لازم نیست.", "Just sign in with your Cafe Bazaar account; no SMS or password."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BazaarButton(enabled = !signIn.busy) { signIn.start() }
                if (signIn.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (signIn.error.isNotBlank()) Text(signIn.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone, enabled = !signIn.busy) { Text(t("بعداً", "Later")) } })
}

/** Token packs sold through Bazaar; the server checks each payment (Bazaar's signature) before adding the tokens. */
/** The token balance as a bright card that invites a purchase: what tokens do, and a big buy button. */
@Composable
private fun TokenCard(p: Account.Profile, onUsage: () -> Unit, onBuy: () -> Unit) {
    val glow = rememberInfiniteTransition(label = "tokens")
    val shine by glow.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Restart), label = "shine")
    val low = !p.unlimited && p.balance <= Account.LOW_BALANCE
    Surface(onClick = onUsage, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF7A5CF0), Color(0xFFB45CF0), Color(0xFFFF8FB6))))
            .drawWithContent {
                drawContent()
                // a soft light sweeping over the card
                val x = size.width * (shine * 1.6f - .3f)
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .22f), Color.Transparent),
                    start = Offset(x - 120f, 0f), end = Offset(x + 120f, size.height)))
            }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙", fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("توکن‌های تو", "Your tokens"), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                        Text(if (p.unlimited) t("نامحدود ✨", "Unlimited ✨") else tokensLabel(p.balance),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (p.plan.isNotBlank()) Text(p.plan, style = MaterialTheme.typography.labelLarge, color = Color.White)
                        Text(t("جزئیات مصرف ›", "Usage ›"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .85f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(t("🤖 دستیار هوشمند", "🤖 Smart assistant"), t("🎙️ ساخت با صدا", "🎙️ Voice"), t("👕 لباس یادیمو", "👕 Outfits")).forEach {
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = .2f)) {
                            Text(it, Modifier.padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                        }
                    }
                }
                if (low) Text(t("توکنت داره تموم می‌شه! 🔋", "Running low on tokens! 🔋"), style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                if (!p.unlimited) Button(onClick = onBuy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6A40E0))) {
                    Icon(Icons.Rounded.AddShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(t("خرید توکن", "Buy tokens"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PacksDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by Account.profile.collectAsState()
    var packs by remember { mutableStateOf<List<Account.Pack>?>(null) }
    var ready by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { Account.packs(context) }
            .onSuccess { packs = it.first; ready = it.second }
            .onFailure { error = it.message.orEmpty(); packs = emptyList() }
    }
    fun buy(pack: Account.Pack) {
        val activity = context.findActivity() ?: return
        val userId = profile?.id.orEmpty()
        if (userId.isBlank()) { error = t("دوباره وارد حسابت شو.", "Please sign in again."); return }
        busy = true; error = ""
        Billing.buy(activity, pack.sku, userId) { result ->
            when (result) {
                is Billing.Result.Bought -> scope.launch {
                    val info = result.info
                    runCatching { Billing.redeem(context, info) }
                        .onSuccess { added ->
                            Toast.makeText(context, t("${tokensLabel(added)} توکن به حسابت اضافه شد. ممنون!", "${tokensLabel(added)} tokens added. Thank you!"),
                                Toast.LENGTH_LONG).show()
                            onDone()
                        }
                        .onFailure {
                            error = (it.message ?: "") + "\n" + t("پرداختت ثبت شده و دفعهٔ بعد که یادیمو را باز کنی دوباره بررسی می‌شود.",
                                "Your payment is saved and will be checked again next time you open Yadimo.")
                        }
                    busy = false
                }
                Billing.Result.Canceled -> busy = false
                is Billing.Result.Failed -> { busy = false; error = result.message }
            }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDone() },
        icon = { Icon(Icons.Rounded.Toll, null) },
        title = { Text(t("خرید توکن", "Buy tokens")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                profile?.let { Text(t("موجودی فعلی: ", "Current balance: ") + tokensLabel(it.balance), style = MaterialTheme.typography.bodyMedium) }
                when {
                    packs == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    !ready -> Text(t("خرید هنوز روی سرور راه‌اندازی نشده؛ به‌زودی فعال می‌شود.", "Purchases are not set up on the server yet."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> packs!!.forEach { pack ->
                        Surface(onClick = { if (!busy) buy(pack) }, shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(pack.title.ifBlank { tokensLabel(pack.tokens) + t(" توکن", " tokens") },
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (pack.note.isNotBlank()) Text(pack.note, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                Text(pack.price, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("توکن برای دستیار هوشمند (هوش مصنوعی) و لوازم یادیمو خرج می‌شود؛ یادآور، یادداشت، تقویم و ویجت‌ها همیشه رایگان‌اند. قبل از خرید، با همین توکن‌هایی که داری چند دستور به دستیار بده و ببین به کارت می‌آید یا نه. هر دستور معمولاً کمتر از ۱ توکن است.",
                            "Tokens pay for the smart assistant (AI) and Yadimo's outfits; reminders, notes, calendar and widgets are always free. Before buying, try a few commands with the tokens you have and see if it helps. A command usually costs under 1 token."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                Text(t("پرداخت با کافه‌بازار انجام می‌شود و توکن‌ها بلافاصله به حسابت اضافه می‌شوند.",
                    "Payment goes through Cafe Bazaar and the tokens are added right away."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text(t("بستن", "Close")) } })
}

/** Two steps: phone number, then the texted code. */
@Composable
private fun PhoneLoginDialog(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeLength by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var wait by remember { mutableIntStateOf(0) }
    LaunchedEffect(wait > 0) { while (wait > 0) { delay(1000); wait-- } }

    fun send() {
        busy = true; error = ""
        scope.launch {
            runCatching { Account.requestCode(context, phone) }
                .onSuccess { codeLength = it; code = ""; wait = 60 }
                .onFailure { error = it.message.orEmpty() }
            busy = false
        }
    }
    fun verify() {
        busy = true; error = ""
        scope.launch {
            runCatching { Account.verify(context, phone, code) }
                .onSuccess {
                    Toast.makeText(context, t("خوش آمدی!", "Welcome!"), Toast.LENGTH_SHORT).show()
                    onDone()
                }
                .onFailure { error = it.message.orEmpty() }
            busy = false
        }
    }

    AlertDialog(onDismissRequest = { if (!busy) onDone() },
        icon = { Icon(Icons.Rounded.PhoneAndroid, null) },
        title = { Text(t("ورود به یادیمو", "Sign in to Yadimo")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (codeLength == 0) {
                    Text(t("شمارهٔ موبایلت را وارد کن تا کد ورود برایت پیامک شود.", "Enter your mobile number to get a sign-in code."),
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(phone, { phone = it.take(20) }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("۰۹۱۲۳۴۵۶۷۸۹") }, shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                } else {
                    Text(t("کد ${n(codeLength)} رقمی که به ${n(phone)} پیامک شد را وارد کن.", "Enter the ${codeLength}-digit code sent to $phone."),
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(code, {
                        code = Dates.asciiDigits(it, codeLength)
                        if (code.length == codeLength && !busy) verify()
                    }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { codeLength = 0; error = "" }) { Text(t("تغییر شماره", "Change number")) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = ::send, enabled = wait == 0 && !busy) {
                            Text(if (wait > 0) t("ارسال دوباره (${n(wait)})", "Resend ($wait)") else t("ارسال دوباره", "Resend"))
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (codeLength == 0) Button(onClick = ::send, enabled = phone.isNotBlank() && !busy) { Text(t("دریافت کد", "Get code")) }
            else Button(onClick = ::verify, enabled = code.length == codeLength && !busy) { Text(t("ورود", "Sign in")) }
        },
        dismissButton = { TextButton(onClick = onBack, enabled = !busy) { Text(t("بازگشت", "Back")) } })
}

/** Where the tokens went: one row per assistant request, newest first. */
@Composable
fun UsageDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { UsageLog.entries(context) }
    val zone = java.time.ZoneId.systemDefault()
    val monthAgo = System.currentTimeMillis() - 30L * 24 * 3600_000
    val recent = entries.filter { it.time >= monthAgo }
    AlertDialog(onDismissRequest = onDone,
        icon = { Icon(Icons.Rounded.ReceiptLong, null) },
        title = { Text(t("جزئیات مصرف توکن", "Token usage")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("۳۰ روز اخیر: ${n(recent.size)} بار استفاده • ${tokensLabel(recent.sumOf { it.tokens })} توکن",
                    "Last 30 days: ${recent.size} uses • ${tokensLabel(recent.sumOf { it.tokens })} tokens"),
                    style = MaterialTheme.typography.titleSmall)
                if (entries.isEmpty()) Text(t("هنوز از دستیار استفاده نکرده‌ای.", "You haven't used the assistant yet."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries.size) { i ->
                        val e = entries[i]
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Dates.friendlyDay(Dates.localDate(e.time, zone), java.time.LocalDate.now(zone), AppDisplay.calendar, AppDisplay.language) +
                                        " • " + Dates.formatTime(e.time, zone, AppDisplay.language), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(UsageLog.describe(e), style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(tokensLabel(e.tokens), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Text(t("فقط نوع کارها ثبت می‌شود، نه متن یادآوری‌ها؛ این فهرست فقط روی همین گوشی است.",
                    "Only the kind of work is kept, not your reminders; this list stays on this phone."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(t("بستن", "Close")) } })
}
