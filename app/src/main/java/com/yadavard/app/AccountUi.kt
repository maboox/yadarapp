package com.yadavard.app

import android.widget.Toast
import androidx.compose.foundation.background
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
    var editingName by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Account.refresh(context) }

    SettingsCard(t("حساب کاربری", "Account"), Icons.Rounded.AccountCircle) {
        val p = profile
        if (p == null) {
            Text(t("همهٔ امکانات یادار بدون حساب کار می‌کنند. برای دستیار هوشمند (صدا و متن با هوش مصنوعی) با حساب کافه‌بازارت وارد شو؛ هدیهٔ ثبت‌نام هم توکن رایگان است.",
                "Everything works without an account. Sign in with Cafe Bazaar to use the smart assistant; new accounts get free tokens."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BazaarButton { loggingIn = true }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name.ifBlank { t("بدون نام", "No name") }, style = MaterialTheme.typography.titleSmall)
                    Text(if (p.phone.isNotBlank()) n(p.phone) else if (p.bazaar) t("حساب کافه‌بازار", "Cafe Bazaar account") else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editingName = true }) { Icon(Icons.Rounded.Edit, t("ویرایش نام", "Edit name")) }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Toll, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("موجودی توکن", "Token balance"), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if (p.unlimited) t("نامحدود", "Unlimited") else tokensLabel(p.balance),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (p.plan.isNotBlank()) Text(p.plan, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (AiSettings(context).personal) Text(t("الان از هوش مصنوعی خودت (کلید شخصی) استفاده می‌کنی و توکن یادار کم نمی‌شود.",
                "You are using your own AI key, so no Yadar tokens are used."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!p.unlimited) Button(onClick = { buying = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AddShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(t("خرید توکن", "Buy tokens"))
            }
            Text(t("هر کار با دستیار معمولاً حدود ۱ توکن یا کمتر است؛ کارهای ساده کمتر و کارهای سنگین کمی بیشتر.",
                "A task usually costs about 1 token or less; simple ones less, heavy ones a bit more."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirmLogout = true }) {
                Icon(Icons.Rounded.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("خروج از حساب", "Sign out"))
            }
        }
    }
    if (loggingIn) LoginDialog { loggingIn = false }
    if (buying) PacksDialog { buying = false }
    if (editingName) {
        var name by remember { mutableStateOf(profile?.name.orEmpty()) }
        AlertDialog(onDismissRequest = { editingName = false }, title = { Text(t("نام", "Name")) },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
            confirmButton = { TextButton(onClick = {
                editingName = false
                scope.launch { runCatching { Account.setName(context, name) }.onFailure {
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() } }
            }) { Text(t("ذخیره", "Save")) } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text(t("انصراف", "Cancel")) } })
    }
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

/**
 * Sign in with Cafe Bazaar: Bazaar's page opens in the browser and the app finishes the login when the user
 * comes back. Phone sign-in stays as a second option (it needs an SMS provider on the server, or the review number).
 */
@Composable
fun LoginDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf(Account.bazaarPending(context)) }
    var usePhone by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val profile by Account.profile.collectAsState()
    LaunchedEffect(profile) { if (profile != null) onDone() }
    // While the browser is open, check every few seconds whether Bazaar has confirmed the sign-in.
    LaunchedEffect(waiting) {
        while (waiting && Account.bazaarPending(context)) {
            delay(3000)
            runCatching { Account.claimBazaar(context) }
                .onSuccess { if (it) { Toast.makeText(context, t("خوش آمدی!", "Welcome!"), Toast.LENGTH_SHORT).show(); onDone() } }
                .onFailure { if (!Account.bazaarPending(context)) { waiting = false; error = it.message.orEmpty() } }
        }
    }
    if (usePhone) { PhoneLoginDialog(onDone = onDone, onBack = { usePhone = false }); return }

    AlertDialog(onDismissRequest = onDone,
        icon = { Icon(Icons.Rounded.AccountCircle, null) },
        title = { Text(t("ورود به یادار", "Sign in to Yadar")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!waiting) {
                    Text(t("با حساب کافه‌بازارت وارد شو؛ پیامک و رمز لازم نیست. خرید توکن هم با همین حساب از بازار انجام می‌شود.",
                        "Sign in with your Cafe Bazaar account; no SMS or password needed. Tokens are bought through Bazaar too."),
                        style = MaterialTheme.typography.bodyMedium)
                    BazaarButton {
                        error = ""
                        runCatching { Account.startBazaarLogin(context); waiting = true }
                            .onFailure { error = t("مرورگری برای باز کردن صفحهٔ بازار پیدا نشد.", "No browser found to open Bazaar.") }
                    }
                } else {
                    Text(t("صفحهٔ بازار در مرورگر باز شد. وارد حسابت شو، اجازه بده و بعد به یادار برگرد؛ ورود خودش کامل می‌شود.",
                        "Bazaar opened in your browser. Sign in, allow access, then come back to Yadar; it finishes by itself."),
                        style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = { runCatching { Account.startBazaarLogin(context) } }) { Text(t("باز کردن دوبارهٔ صفحه", "Open again")) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { Account.cancelBazaarLogin(context); waiting = false }) { Text(t("از اول", "Start over")) }
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { usePhone = true }) {
                    Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(t("ورود با شمارهٔ موبایل", "Sign in with phone number"))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(t("بستن", "Close")) } })
}

/** Token packs sold through Bazaar; the server checks each payment with Bazaar before adding the tokens. */
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
                    runCatching { Account.redeem(context, info.packageName, info.productId, info.purchaseToken, info.orderId) }
                        .onSuccess { added ->
                            Toast.makeText(context, t("${tokensLabel(added)} توکن به حسابت اضافه شد. ممنون!", "${tokensLabel(added)} tokens added. Thank you!"),
                                Toast.LENGTH_LONG).show()
                            onDone()
                        }
                        .onFailure {
                            error = (it.message ?: "") + "\n" + t("پرداختت ثبت شده و دفعهٔ بعد که یادار را باز کنی دوباره بررسی می‌شود.",
                                "Your payment is saved and will be checked again next time you open Yadar.")
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
        title = { Text(t("ورود به یادار", "Sign in to Yadar")) },
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
