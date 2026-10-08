package com.yadavard.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch

/**
 * Shows the assistant's bubbles floating at the top of the screen over any app, so the user can keep working
 * while a voice request is transcribed and understood. Stops itself when the session ends.
 */
class AssistantBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    private var view: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        Notifier.ensureChannels(this)
        val notification = NotificationCompat.Builder(this, Notifier.CHANNEL_QUIET)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(if (Prefs.language(this) == AppLanguage.FA) "دستیار یادار در حال کار…" else "Yadar assistant is working…")
            .setPriority(NotificationCompat.PRIORITY_LOW).setSilent(true).build()
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        }
        addOverlay()
        registry.currentState = Lifecycle.State.RESUMED
        lifecycleScope.launch {
            AssistantSession.state.collect { s -> if (s.stage == AssistantSession.Stage.IDLE || s.host != BubbleHost.OVERLAY) stopSelf() }
        }
    }

    private fun addOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WindowManager::class.java)
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AssistantBubbleService)
            setViewTreeSavedStateRegistryOwner(this@AssistantBubbleService)
            setContent {
                YadarTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides
                        if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        val state by AssistantSession.state.collectAsState()
                        AssistantBubbles(state, Modifier.statusBarsPadding())
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: touches outside the bubbles reach the app underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP }
        runCatching { wm.addView(compose, params); view = compose }
    }

    override fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        view?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        view = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 999_999_010
        fun start(context: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, AssistantBubbleService::class.java)); true
        }.getOrDefault(false)
    }
}
