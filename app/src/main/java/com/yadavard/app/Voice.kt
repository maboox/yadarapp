package com.yadavard.app

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/**
 * Speech-to-text intent that prefers Google's recognizer, so phones whose default handler is a
 * vendor assistant (for example Mi AI on Xiaomi) still get a plain dictation screen.
 */
fun speechIntent(context: Context): Intent {
    val base = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val lang = if (AppDisplay.language == AppLanguage.FA) "fa-IR" else "en-US"
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        putExtra(RecognizerIntent.EXTRA_PROMPT, t("بگو چی رو و کی یادت بندازم", "Say what and when"))
    }
    val google = Intent(base).setPackage("com.google.android.googlequicksearchbox")
    return if (google.resolveActivity(context.packageManager) != null) google else base
}

