package com.yadavard.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

/**
 * Yadimo peeking in at the bottom corner of the screen over any app, saying one short thing and leaving again.
 * Only the small corner is a window; the rest of the screen keeps working as usual. A tap on Yadimo sends it
 * away, a tap on the bubble opens the app.
 */
object YadimoOverlay {
    private class Owner : LifecycleOwner, SavedStateRegistryOwner {
        val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this).apply { performAttach(); performRestore(null) }
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    }

    private var showing = false

    fun show(context: Context, text: String, onGone: () -> Unit) {
        if (showing || !Phone.canOverlay(context)) { onGone(); return }
        val app = context.applicationContext
        val wm = app.getSystemService(WindowManager::class.java)
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var view: ComposeView? = null
        var removed = false
        fun remove() {
            if (removed) return
            removed = true; showing = false
            owner.registry.currentState = Lifecycle.State.DESTROYED
            view?.let { v -> runCatching { wm.removeView(v) } }
            onGone()
        }
        val compose = ComposeView(app).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                YadarTheme {
                    // Laid out left-to-right so "end" is always the right edge, where Yadimo comes in from.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Visit(text, onOpen = {
                            runCatching { app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            remove()
                        }, onGone = { remove() })
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            y = (app.resources.displayMetrics.density * 130).toInt()
        }
        if (runCatching { wm.addView(compose, params) }.isSuccess) { view = compose; showing = true } else onGone()
    }

    @Composable
    private fun Visit(text: String, onOpen: () -> Unit, onGone: () -> Unit) {
        var inside by remember { mutableStateOf(false) }
        var talking by remember { mutableStateOf(false) }
        var leaving by remember { mutableStateOf(false) }
        LaunchedEffect(leaving) {
            if (!leaving) {
                delay(400); inside = true
                delay(1000); talking = true
                delay((3500L + text.length * 60L).coerceAtMost(9000L))
            }
            talking = false
            delay(300); inside = false
            delay(1100); onGone()
        }
        Column(Modifier.padding(start = 16.dp), horizontalAlignment = Alignment.End) {
            AnimatedVisibility(talking, enter = scaleIn(transformOrigin = TransformOrigin(0.8f, 1f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                YadimoSpeech(text, Modifier.clickable(remember { MutableInteractionSource() }, null) { onOpen() }, tailEnd = 40.dp)
            }
            Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { leaving = true }) {
                YadimoView(if (talking) YadimoMood.TALK else YadimoMood.WAVE, size = 110.dp, peekIn = inside, tappable = false)
            }
        }
    }
}
