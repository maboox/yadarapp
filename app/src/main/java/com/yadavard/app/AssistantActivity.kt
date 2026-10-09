package com.yadavard.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Transparent screen opened by the voice widget, the quick widget's mic and the app shortcut. It shows only a
 * small recording pill; after Send the request continues in floating bubbles over whatever app is open
 * (or here, when "display over other apps" is not allowed).
 */
class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppDisplay.load(this)
        setContent {
            YadarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) { Host() }
            }
        }
    }

    @Composable
    private fun Host() {
        val hasKey = remember { Ai.ready(this) }
        var recording by remember { mutableStateOf(false) }
        var showingBubbles by remember { mutableStateOf(false) }
        val session by AssistantSession.state.collectAsState()

        val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) AssistantSession.saveSimple(this, text)
            finish()
        }
        val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) recording = true
            else { Toast.makeText(this, t("اجازهٔ میکروفون لازم است.", "Microphone permission is needed."), Toast.LENGTH_LONG).show(); finish() }
        }
        LaunchedEffect(Unit) {
            when {
                !hasKey -> runCatching { speech.launch(speechIntent(this@AssistantActivity)) }.onFailure {
                    Toast.makeText(this@AssistantActivity, t("تشخیص گفتار در دسترس نیست. برای دستیار هوشمند در تنظیمات یادار وارد حسابت شو.",
                        "Speech recognition is unavailable. Sign in in Yadar settings to use the smart assistant."), Toast.LENGTH_LONG).show()
                    finish()
                }
                ContextCompat.checkSelfPermission(this@AssistantActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> recording = true
                else -> mic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        // When the bubbles are shown here (no overlay permission), close once the session ends.
        LaunchedEffect(showingBubbles, session.stage) {
            if (showingBubbles && session.stage == AssistantSession.Stage.IDLE) finish()
        }

        Box(Modifier.fillMaxSize()) {
            if (showingBubbles) AssistantBubbles(session, Modifier.align(Alignment.TopCenter).statusBarsPadding())
            if (recording) VoiceCapture(
                onCancel = { finish() },
                onSend = { file ->
                    recording = false
                    if (Settings.canDrawOverlays(this@AssistantActivity)) {
                        AssistantSession.startAudio(this@AssistantActivity, file, BubbleHost.OVERLAY)
                        AssistantBubbleService.start(this@AssistantActivity)
                        finish()
                    } else {
                        AssistantSession.startAudio(this@AssistantActivity, file, BubbleHost.ACTIVITY)
                        showingBubbles = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp))
        }
    }
}
