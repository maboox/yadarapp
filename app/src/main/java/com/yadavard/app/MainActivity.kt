@file:OptIn(ExperimentalMaterial3Api::class)

package com.yadavard.app

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val command = mutableStateOf<Intent?>(null)
    private val onboarded = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppDisplay.load(this)
        Notifier.ensureChannels(this)
        onboarded.value = Prefs.onboarded(this)
        command.value = intent?.takeIf { it.action != Intent.ACTION_MAIN }
        setContent {
            YadarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    if (onboarded.value) AppRoot(command.value) { command.value = null }
                    else OnboardingScreen { Prefs.setOnboarded(this); onboarded.value = true }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If an alarm is still ringing, always give the user its screen so it can be stopped.
        val ringing = AlertService.ringingId()
        if (ringing != 0L) runCatching {
            startActivity(Intent(this, AlarmActivity::class.java).setData(android.net.Uri.parse("yadar://alarm-screen/$ringing"))
                .putExtra(Notifier.EXTRA_ID, ringing).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        command.value = intent
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val _items = MutableStateFlow<List<Reminder>>(emptyList())
    val items: StateFlow<List<Reminder>> = _items
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded
    private val context get() = getApplication<Application>()

    init {
        viewModelScope.launch { Repo.changes.collect { reload() } }
        viewModelScope.launch {
            while (true) {
                // Tick on the minute so relative times and overdue states stay fresh.
                delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
                _now.value = System.currentTimeMillis()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            Scheduler.rescheduleAll(context)
            WidgetUpdater.update(context)
        }
    }

    private suspend fun reload() {
        _items.value = withContext(Dispatchers.IO) { Repo.all(context) }
        _now.value = System.currentTimeMillis()
        _loaded.value = true
    }

    fun refresh() { viewModelScope.launch { reload() } }

    // One writer thread keeps user actions in order (for example "done" followed by "undo").
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private fun io(block: () -> Unit) { viewModelScope.launch(writer) { block() } }
    fun save(r: Reminder, done: (Reminder) -> Unit = {}) = viewModelScope.launch {
        val saved = withContext(writer) { Repo.save(context, r) }
        done(saved)
    }
    fun delete(r: Reminder) = io { Repo.delete(context, r) }
    fun complete(r: Reminder) = io { Repo.complete(context, r.id) }
    fun snooze(r: Reminder, minutes: Int) = io { Repo.snooze(context, r.id, minutes) }
    fun skip(r: Reminder) = io { Repo.skip(context, r.id) }
    /** Restores an exact earlier state, used by "Undo". */
    fun restore(r: Reminder) = io { Repo.save(context, r) }
}

enum class Tab { HOME, CALENDAR, SETTINGS }

/** What the editor is opened with: an existing reminder or a pre-filled draft for a new one. */
data class EditorRequest(val original: Reminder?, val draft: Reminder?, val key: Long = System.nanoTime())

@Composable
fun AppRoot(command: Intent?, consumed: () -> Unit) {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel()
    val items by vm.items.collectAsState()
    val now by vm.now.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var calendarDay by remember { mutableStateOf(LocalDate.now()) }
    var quickText by rememberSaveable { mutableStateOf("") }
    var permissionTick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++; vm.refresh() }

    var capturing by remember { mutableStateOf(false) }
    val session by AssistantSession.state.collectAsState()
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) { quickText = text; tab = Tab.HOME }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capturing = true
        else Toast.makeText(context, t("برای ضبط صدا، اجازهٔ میکروفون لازم است.", "Microphone permission is needed to record."), Toast.LENGTH_LONG).show()
    }
    fun startVoice() {
        // With an OpenRouter key, record inside the app and let AI transcribe and understand it;
        // otherwise use Google's dictation (avoiding vendor assistants such as Mi AI).
        if (AiSettings(context).hasKey()) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) capturing = true
            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        try { speech.launch(speechIntent(context)) } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, t("تشخیص گفتار در دسترس نیست. اپ Google را نصب کن یا در تنظیمات کلید OpenRouter را وارد کن تا صدا با هوش مصنوعی تبدیل شود.",
                "Speech recognition is not available. Install the Google app or add an OpenRouter key in settings for AI voice."), Toast.LENGTH_LONG).show()
        }
    }

    // Commands from notifications and widgets.
    LaunchedEffect(command) {
        val c = command ?: return@LaunchedEffect
        when (c.action) {
            WidgetCommands.ADD -> { tab = Tab.HOME; editor = EditorRequest(null, null) }
            WidgetCommands.VOICE -> { tab = Tab.HOME; startVoice() }
            WidgetCommands.HOME -> tab = Tab.HOME
            WidgetCommands.CALENDAR -> tab = Tab.CALENDAR
            WidgetCommands.DAY -> {
                calendarDay = LocalDate.ofEpochDay(c.getLongExtra(WidgetCommands.EXTRA_DAY, LocalDate.now().toEpochDay()))
                tab = Tab.CALENDAR
            }
            WidgetCommands.EDIT_DRAFT -> {
                val draft = c.getStringExtra(WidgetCommands.EXTRA_DRAFT)?.let { runCatching { Backup.fromJson(org.json.JSONObject(it)) }.getOrNull() }
                val original = c.getLongExtra(WidgetCommands.EXTRA_ID, 0).takeIf { it != 0L }?.let { withContext(Dispatchers.IO) { Repo.get(context, it) } }
                if (draft != null) editor = EditorRequest(original, draft)
            }
            WidgetCommands.EDIT, Notifier.ACTION_OPEN -> {
                val id = c.getLongExtra(WidgetCommands.EXTRA_ID, 0).takeIf { it != 0L } ?: c.getLongExtra(Notifier.EXTRA_ID, 0)
                val r = withContext(Dispatchers.IO) { Repo.get(context, id) }
                if (r != null) editor = EditorRequest(r, null)
            }
        }
        consumed()
    }

    val showSaved: (Reminder) -> Unit = { r ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val when_ = if (r.nextAt > 0) Dates.formatDateTime(r.nextAt, r.zoneId, AppDisplay.calendar, AppDisplay.language, withYear = false) else ""
            val res = snackbar.showSnackbar(t("ثبت شد: ${r.title} • $when_", "Saved: ${r.title} • $when_"), t("ویرایش", "Edit"),
                duration = SnackbarDuration.Long)
            if (res == SnackbarResult.ActionPerformed) editor = EditorRequest(r, null)
        }
    }

    val actions = remember(vm) {
        ReminderActions(
            open = { editor = EditorRequest(it, null) },
            create = { draft -> editor = EditorRequest(null, draft) },
            complete = { r ->
                vm.complete(r)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val res = snackbar.showSnackbar(t("«${r.title}» انجام شد ✓", "“${r.title}” done ✓"), t("برگرداندن", "Undo"),
                        duration = SnackbarDuration.Long)
                    if (res == SnackbarResult.ActionPerformed) vm.restore(r)
                }
            },
            delete = { r ->
                vm.delete(r)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val res = snackbar.showSnackbar(t("«${r.title}» حذف شد", "“${r.title}” deleted"), t("برگرداندن", "Undo"),
                        duration = SnackbarDuration.Long)
                    if (res == SnackbarResult.ActionPerformed) vm.restore(r)
                }
            },
            snooze = { r, m -> vm.snooze(r, m) },
            skip = { r -> vm.skip(r) },
            saved = showSaved,
            voice = ::startVoice,
            saveDirect = { r -> vm.save(r) { saved -> showSaved(saved) } },
            assistant = { text -> AssistantSession.startText(context, text, BubbleHost.APP) },
        )
    }

    BackHandler(enabled = editor == null && tab != Tab.HOME) { tab = Tab.HOME }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = tab == Tab.HOME, onClick = { tab = Tab.HOME },
                        icon = { Icon(if (tab == Tab.HOME) Icons.Rounded.Home else Icons.Outlined.OutlinedHome, null) }, label = { Text(t("خانه", "Home")) })
                    NavigationBarItem(selected = tab == Tab.CALENDAR, onClick = { tab = Tab.CALENDAR },
                        icon = { Icon(Icons.Rounded.CalendarMonth, null) }, label = { Text(t("تقویم", "Calendar")) })
                    NavigationBarItem(selected = tab == Tab.SETTINGS, onClick = { tab = Tab.SETTINGS },
                        icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text(t("تنظیمات", "Settings")) })
                }
            },
            floatingActionButton = {
                if (tab != Tab.SETTINGS) ExtendedFloatingActionButton(
                    onClick = {
                        val draft = if (tab == Tab.CALENDAR && calendarDay != LocalDate.now()) draftOn(calendarDay) else null
                        editor = EditorRequest(null, draft)
                    },
                    icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(t("یادآوری جدید", "New reminder")) },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            },
        ) { padding ->
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { current ->
                when (current) {
                    Tab.HOME -> HomeScreen(items, now, padding, actions, quickText, { quickText = it }, permissionTick,
                        onFixPermissions = { tab = Tab.SETTINGS })
                    Tab.CALENDAR -> CalendarScreen(items, now, padding, calendarDay, { calendarDay = it }, actions)
                    Tab.SETTINGS -> SettingsScreen(padding, items.size, permissionTick, onPermissionChanged = { permissionTick++ })
                }
            }
        }
        // Assistant: small recording pill at the bottom, floating bubbles at the top.
        if (session.host == BubbleHost.APP) AssistantBubbles(session, Modifier.align(Alignment.TopCenter).statusBarsPadding())
        if (capturing) VoiceCapture(
            onCancel = { capturing = false },
            onSend = { file -> capturing = false; AssistantSession.startAudio(context, file, BubbleHost.APP) },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
        var shown by remember { mutableStateOf<EditorRequest?>(null) }
        if (editor != null) shown = editor
        BackHandler(enabled = editor != null) { editor = null }
        AnimatedVisibility(editor != null, enter = slideInVertically { it / 3 } + fadeIn(), exit = slideOutVertically { it / 3 } + fadeOut()) {
            val request = shown
            if (request != null) {
                key(request.key) {
                    EditorScreen(request,
                        onClose = { editor = null },
                        onSave = { r ->
                            editor = null
                            vm.save(r) { saved -> if (request.original == null) actions.saved(saved) }
                        },
                        onDelete = { r -> editor = null; actions.delete(r) })
                }
            }
        }
    }
}

class ReminderActions(
    val open: (Reminder) -> Unit,
    val create: (Reminder?) -> Unit,
    val complete: (Reminder) -> Unit,
    val delete: (Reminder) -> Unit,
    val snooze: (Reminder, Int) -> Unit,
    val skip: (Reminder) -> Unit,
    val saved: (Reminder) -> Unit,
    val voice: () -> Unit,
    val saveDirect: (Reminder) -> Unit,
    val assistant: (String) -> Unit,
)

/** A draft reminder at 09:00 on [day], used when adding from a calendar day. */
fun draftOn(day: LocalDate): Reminder {
    val zone = java.time.ZoneId.systemDefault()
    return Reminder(title = "", firstAt = Dates.at(day, 9, 0, zone), zone = zone.id, calendar = AppDisplay.calendar)
}
