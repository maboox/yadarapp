package com.yadavard.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    LaunchedEffect(Unit) { Account.refresh(context) }

    SettingsCard(t("حساب کاربری", "Account"), Icons.Rounded.AccountCircle) {
        val p = profile
        if (p == null) {
            Text(t("همهٔ امکانات یادار بدون حساب کار می‌کنند. برای دستیار هوشمند (صدا و متن با هوش مصنوعی) با شمارهٔ موبایل وارد شو؛ هدیهٔ ثبت‌نام هم توکن رایگان است.",
                "Everything works without an account. Sign in with your phone number to use the smart assistant; new accounts get free tokens."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { loggingIn = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Login, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(t("ورود با شمارهٔ موبایل", "Sign in with phone"))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name.ifBlank { t("بدون نام", "No name") }, style = MaterialTheme.typography.titleSmall)
                    Text(n(p.phone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(t("هر درخواست هوش مصنوعی معمولاً حدود ۱ تا ۳ توکن مصرف می‌کند. خرید بسته به‌زودی از همین‌جا ممکن می‌شود.",
                "An AI request usually costs about 1–3 tokens. Buying packs will be available here soon."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirmLogout = true }) {
                Icon(Icons.Rounded.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("خروج از حساب", "Sign out"))
            }
        }
    }
    if (loggingIn) LoginDialog { loggingIn = false }
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

/** Two steps: phone number, then the texted code. */
@Composable
fun LoginDialog(onDone: () -> Unit) {
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
        dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text(t("انصراف", "Cancel")) } })
}
