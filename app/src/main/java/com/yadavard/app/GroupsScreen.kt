package com.yadavard.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Groups: the list of my groups and invitations, and a group's page with its members and shared reminders.
 * Opened from the "Groups" button on the home screen (when groups are turned on in the hidden settings).
 */
@Composable
fun GroupsScreen(onClose: () -> Unit, onEdit: (EditorRequest) -> Unit, actions: ReminderActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Groups.state.collectAsState()
    val profile by Account.profile.collectAsState()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pickUsername by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Groups.load(context); Groups.sync(context) }
    BackHandler { if (open != null) open = null else onClose() }
    fun act(block: suspend () -> Unit) { scope.launch { message = runCatching { block() }.exceptionOrNull()?.message?.lineSequence()?.firstOrNull().orEmpty() } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            val group = state.groups.firstOrNull { it.id == open }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (open != null) open = null else onClose() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("بازگشت", "Back")) }
                Text(group?.name ?: t("گروه‌ها", "Groups"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { act { Groups.sync(context) } }) { Icon(Icons.Rounded.Refresh, t("به‌روز کردن", "Refresh")) }
                if (group != null) GroupMenu(group, onDone = { open = null }, act = ::act)
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp))
            when {
                profile == null -> Notice("👋", t("برای گروه‌ها اول وارد حسابت شو.", "Sign in first to use groups."), t("ورود", "Sign in")) {
                    Account.loginRequested.value = true
                }
                profile?.username.isNullOrBlank() -> Notice("🆔", t("دیگران با «نام کاربری» پیدایت می‌کنند و به گروه دعوتت می‌کنند. اول یکی انتخاب کن.",
                    "Others find you and invite you by your username. Pick one first."), t("انتخاب نام کاربری", "Pick a username")) { pickUsername = true }
                group != null -> GroupPage(group, state.me, onEdit, actions, act = ::act)
                else -> GroupList(state, onOpen = { open = it }, onCreate = { creating = true }, act = ::act)
            }
        }
    }
    if (creating) GroupDialog(null) { name, all -> creating = false; if (name != null) act { Groups.create(context, name, all) } }
    if (pickUsername) UsernameDialog { pickUsername = false }
}

@Composable
private fun Notice(emoji: String, text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(emoji, fontSize = 48.sp)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun GroupList(state: Groups.State, onOpen: (String) -> Unit, onCreate: () -> Unit, act: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.invites.isNotEmpty()) item { Text(t("دعوت‌ها", "Invitations"), style = MaterialTheme.typography.titleMedium) }
        items(state.invites, key = { "i" + it.id }) { g ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("💌 " + g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(t("${g.invitedBy.ifBlank { g.ownerName }} دعوتت کرده", "${g.invitedBy.ifBlank { g.ownerName }} invited you"),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(t("اعضا: ", "Members: ") + g.activeMembers.joinToString("، ") { it.name }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { act { Groups.answer(context, g.id, true) } }) { Text(t("قبول", "Accept")) }
                        OutlinedButton(onClick = { act { Groups.answer(context, g.id, false) } }) { Text(t("رد", "Decline")) }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("گروه‌های من", "My groups"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${n(state.groups.size)}/${n(5)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.groups.isEmpty()) item {
            Text(t("هنوز گروهی نداری. یک گروه بساز (مثلاً «خانواده») و با نام کاربری دیگران را دعوت کن؛ بعد یادآوری‌های مشترک دارید: قبض‌ها، خرید، داروی مامان…",
                "No groups yet. Make one (e.g. “Family”) and invite people by username; then you share reminders: bills, shopping, mum's pills…"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.groups, key = { it.id }) { g ->
            val count = remember(g.id, state.tick) { Groups.items(context, g.id).count { !it.done } }
            Surface(onClick = { onOpen(g.id) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("👥", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium)
                        Text(t("${n(g.activeMembers.size)} نفر • ${n(count)} یادآوری", "${g.activeMembers.size} people • $count reminders") +
                            if (g.isOwner) t(" • سازنده", " • owner") else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item {
            Button(onClick = onCreate, enabled = state.groups.size < 5, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.GroupAdd, null); Spacer(Modifier.width(8.dp)); Text(t("ساخت گروه", "New group"))
            }
            Text(t("هر گروه تا ۲۰ نفر و هر نفر تا ۵ گروه. یادآوری‌های گروه (برخلاف یادآوری‌های خودت) رمزنگاری‌شده روی سرور یادیمو نگه داشته می‌شود تا به گوشی همه برسد، و در آمار و امتیاز تو حساب نمی‌شود.",
                "Up to 20 people per group and 5 groups each. Group reminders (unlike your own) are kept encrypted on the Yadimo server so they reach everyone, and they do not count in your stats or points."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupPage(g: Groups.Group, me: String, onEdit: (EditorRequest) -> Unit, actions: ReminderActions, act: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    val changes by Repo.changes.collectAsState()
    val state by Groups.state.collectAsState()
    val items = remember(g.id, changes, state.tick) { Groups.items(context, g.id) }
    var inviting by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Groups.Member?>(null) }
    var showDone by remember { mutableStateOf(false) }
    val names = g.members.associate { it.id to it.name }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(g.members, key = { it.id }) { m ->
                        val label = m.name + when { m.role == "owner" -> t(" 👑", " 👑"); !m.active -> t(" (دعوت‌شده)", " (invited)"); else -> "" }
                        Surface(shape = CircleShape, color = if (m.active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { if (g.isOwner && m.id != me) removing = m })) {
                            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (g.canEdit) item {
                        AssistChip(onClick = { inviting = true }, label = { Text(t("+ افزودن عضو", "+ Add")) })
                    }
                }
                if (g.isOwner) Text(t("برای حذف یک عضو، اسمش را نگه دار.", "Long-press a member to remove them."), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            val open = items.filter { !it.done }
            val finished = items.filter { it.done }
            if (open.isEmpty()) item {
                Text(if (g.canEdit) t("هنوز یادآوری مشترکی نیست؛ با دکمهٔ پایین بساز.", "No shared reminders yet; add one below.")
                    else t("هنوز یادآوری مشترکی نیست.", "No shared reminders yet."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            }
            items(open, key = { it.id }) { r -> GroupItem(r, g, me, names, onClick = { if (g.canEdit) onEdit(EditorRequest(r, null)) }, onDone = { actions.complete(r) }) }
            if (finished.isNotEmpty()) item {
                TextButton(onClick = { showDone = !showDone }) { Text(t("انجام‌شده‌ها (${n(finished.size)})", "Done (${finished.size})")) }
            }
            if (showDone) items(finished, key = { it.id }) { r -> GroupItem(r, g, me, names, onClick = { if (g.canEdit) onEdit(EditorRequest(r, null)) }, onDone = null) }
        }
        if (g.canEdit) ExtendedFloatingActionButton(onClick = {
            val zone = ZoneId.systemDefault()
            val at = Instant.now().atZone(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
            onEdit(EditorRequest(null, Reminder(title = "", firstAt = at, zone = zone.id, calendar = AppDisplay.calendar, groupId = g.id)))
        }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(t("یادآوری مشترک", "Shared reminder")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
    if (inviting) InviteDialog { u -> inviting = false; if (u != null) act { Groups.invite(context, g.id, u) } }
    removing?.let { m ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(t("حذف ${m.name} از گروه؟", "Remove ${m.name}?")) },
            confirmButton = { TextButton(onClick = { removing = null; act { Groups.remove(context, g.id, m.id) } }) { Text(t("حذف", "Remove")) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(t("انصراف", "Cancel")) } })
    }
}

@Composable
private fun GroupItem(r: Reminder, g: Groups.Group, me: String, names: Map<String, String>, onClick: () -> Unit, onDone: (() -> Unit)?) {
    val did = Groups.doneBy(r)
    val forMe = r.assignee.isEmpty() || r.assignee == me
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val at = if (r.done) r.completedAt else ReminderLogic.occurrence(r)
                if (at > 0) Text(Dates.formatDateTime(at, r.zoneId, AppDisplay.calendar, AppDisplay.language, withYear = false),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text((if (r.assignee.isEmpty()) t("👥 برای همه", "👥 Everyone") else t("👤 برای ${names[r.assignee] ?: "?"}", "👤 For ${names[r.assignee] ?: "?"}")) +
                    " • " + (if (r.groupMode == "all") t("همه باید انجام بدن", "everyone does it") else t("یک نفر کافیه", "one is enough")),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (did.isNotEmpty()) {
                    val total = if (r.assignee.isEmpty()) g.activeMembers.size else 1
                    Text("✓ " + did.joinToString("، ") { names[it] ?: "?" } + if (r.groupMode == "all") t(" • ${n(did.size)} از ${n(total)}", " • ${did.size} of $total") else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (onDone != null && forMe && !r.done && me !in did) FilledTonalButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(t("انجام شد", "Done"))
            }
        }
    }
}

@Composable
private fun GroupMenu(g: Groups.Group, onDone: () -> Unit, act: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, t("بیشتر", "More")) }
        DropdownMenu(menu, { menu = false }) {
            if (g.isOwner) DropdownMenuItem({ Text(t("تنظیمات گروه", "Group settings")) }, { menu = false; editing = true }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
            DropdownMenuItem({ Text(t("خروج از گروه", "Leave group")) }, { menu = false; confirm = "leave" }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) })
            if (g.isOwner) DropdownMenuItem({ Text(t("حذف گروه", "Delete group")) }, { menu = false; confirm = "delete" }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
        }
    }
    if (editing) GroupDialog(g) { name, all -> editing = false; if (name != null) act { Groups.settings(context, g.id, name, all) } }
    confirm?.let { what ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(if (what == "leave") t("از «${g.name}» خارج می‌شوی؟", "Leave “${g.name}”?") else t("«${g.name}» برای همه پاک شود؟", "Delete “${g.name}” for everyone?")) },
            text = { Text(if (what == "leave") t("یادآوری‌های این گروه از گوشی‌ات برداشته می‌شود." + if (g.isOwner) " گروه به قدیمی‌ترین عضو می‌رسد." else "",
                "Its reminders leave your phone." + if (g.isOwner) " The longest-standing member takes over." else "")
                else t("گروه و همهٔ یادآوری‌هایش برای همهٔ اعضا پاک می‌شود.", "The group and all its reminders go for every member.")) },
            confirmButton = { TextButton(onClick = {
                confirm = null; onDone()
                act { if (what == "leave") Groups.leave(context, g.id) else Groups.delete(context, g.id) }
            }) { Text(if (what == "leave") t("خروج", "Leave") else t("حذف", "Delete")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("انصراف", "Cancel")) } })
    }
}

/** New group (g = null) or the owner's settings of one: its name and who may add reminders. */
@Composable
private fun GroupDialog(g: Groups.Group?, onDone: (String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf(g?.name.orEmpty()) }
    var all by remember { mutableStateOf(g?.editMode == "all") }
    AlertDialog(onDismissRequest = { onDone(null, all) },
        title = { Text(if (g == null) t("گروه تازه", "New group") else t("تنظیمات گروه", "Group settings")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(t("مثلاً خانواده", "e.g. Family")) }, modifier = Modifier.fillMaxWidth())
                Text(t("چه کسی یادآوری بسازد، عوض کند و عضو اضافه کند؟", "Who may add and change reminders and invite people?"),
                    style = MaterialTheme.typography.labelLarge)
                SettingSegmented(listOf(false to t("فقط سازنده", "Only the owner"), true to t("همه", "Everyone")), all) { all = it }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onDone(name.trim(), all) }) { Text(if (g == null) t("ساختن", "Create") else t("ذخیره", "Save")) } },
        dismissButton = { TextButton(onClick = { onDone(null, all) }) { Text(t("انصراف", "Cancel")) } })
}

@Composable
private fun InviteDialog(onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { onDone(null) },
        title = { Text(t("دعوت به گروه", "Invite")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    OutlinedTextField(name, { name = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '@' }.take(21) }, singleLine = true,
                        prefix = { Text("@") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                }
                Text(t("نام کاربری کسی که می‌خواهی دعوت کنی (در پروفایلش زیر اسمش هست). بعد از قبول کردنش به گروه اضافه می‌شود.",
                    "The username of the person (shown on their profile under their name). They join once they accept."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onDone(name.trim().removePrefix("@")) }) { Text(t("دعوت", "Invite")) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(t("انصراف", "Cancel")) } })
}
