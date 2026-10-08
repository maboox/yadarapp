package com.yadavard.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads assistant replies aloud with the phone's text-to-speech engine, when it has the needed language. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val lang = Prefs.language(context)
    private var ready = false
    private var pending: String? = null
    var supported = true
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { supported = false; ready = true; return }
        val locale = if (lang == AppLanguage.FA) Locale("fa", "IR") else Locale.US
        supported = tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        ready = true
        pending?.let { pending = null; speak(it) }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) { pending = text; return }
        if (supported) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yadar-reply")
    }

    fun stop() { runCatching { tts.stop() } }
    fun shutdown() { runCatching { tts.stop(); tts.shutdown() } }
}
