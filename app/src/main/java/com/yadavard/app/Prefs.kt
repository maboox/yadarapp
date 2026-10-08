package com.yadavard.app

import android.content.Context
import android.content.SharedPreferences

/** App-wide settings. Older versions stored language and calendar in the "display" file; those are read as fallbacks. */
object Prefs {
    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun legacy(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("display", Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        runCatching { enumValueOf<T>(raw ?: return fallback) }.getOrDefault(fallback)

    fun language(context: Context): AppLanguage =
        enumValue(prefs(context).getString("language", null) ?: legacy(context).getString("language", null), AppLanguage.FA)
    fun setLanguage(context: Context, value: AppLanguage) = prefs(context).edit().putString("language", value.name).apply()

    fun calendar(context: Context): CalendarSystem =
        enumValue(prefs(context).getString("calendar", null) ?: legacy(context).getString("calendar", null), CalendarSystem.PERSIAN)
    fun setCalendar(context: Context, value: CalendarSystem) = prefs(context).edit().putString("calendar", value.name).apply()

    fun theme(context: Context): ThemeMode = enumValue(prefs(context).getString("theme", null), ThemeMode.SYSTEM)
    fun setTheme(context: Context, value: ThemeMode) = prefs(context).edit().putString("theme", value.name).apply()

    fun snoozeMinutes(context: Context): Int = prefs(context).getInt("snooze_minutes", 10)
    fun setSnoozeMinutes(context: Context, value: Int) = prefs(context).edit().putInt("snooze_minutes", value).apply()

    fun ringMinutes(context: Context): Int = prefs(context).getInt("ring_minutes", 5)
    fun setRingMinutes(context: Context, value: Int) = prefs(context).edit().putInt("ring_minutes", value).apply()

    fun defaultAlert(context: Context): AlertStyle = enumValue(prefs(context).getString("default_alert", null), AlertStyle.NOTIFICATION)
    fun setDefaultAlert(context: Context, value: AlertStyle) = prefs(context).edit().putString("default_alert", value.name).apply()

    fun defaultLead(context: Context): Int = prefs(context).getInt("default_lead", 0)
    fun setDefaultLead(context: Context, value: Int) = prefs(context).edit().putInt("default_lead", value).apply()

    /** Uses AlarmManager.setAlarmClock, which Android treats like an alarm clock and does not delay. */
    fun reliableMode(context: Context): Boolean = prefs(context).getBoolean("reliable_mode", true)
    fun setReliableMode(context: Context, value: Boolean) = prefs(context).edit().putBoolean("reliable_mode", value).apply()

    fun useAi(context: Context): Boolean = prefs(context).getBoolean("use_ai", false)
    fun setUseAi(context: Context, value: Boolean) = prefs(context).edit().putBoolean("use_ai", value).apply()

    /** Custom sound URIs; null means the phone's default notification / alarm sound. */
    fun reminderSound(context: Context): String? = prefs(context).getString("reminder_sound", null)
    fun setReminderSound(context: Context, value: String?) = prefs(context).edit().putString("reminder_sound", value).apply()
    fun alarmSound(context: Context): String? = prefs(context).getString("alarm_sound", null)
    fun setAlarmSound(context: Context, value: String?) = prefs(context).edit().putString("alarm_sound", value).apply()

    fun vibrate(context: Context): Boolean = prefs(context).getBoolean("vibrate", true)
    fun setVibrate(context: Context, value: Boolean) = prefs(context).edit().putBoolean("vibrate", value).apply()

    fun speakReplies(context: Context): Boolean = prefs(context).getBoolean("speak_replies", true)
    fun setSpeakReplies(context: Context, value: Boolean) = prefs(context).edit().putBoolean("speak_replies", value).apply()

    fun onboarded(context: Context): Boolean = prefs(context).getBoolean("onboarded", false)
    fun setOnboarded(context: Context) = prefs(context).edit().putBoolean("onboarded", true).apply()

    fun askedNotifications(context: Context): Boolean = prefs(context).getBoolean("asked_notifications", false)
    fun setAskedNotifications(context: Context) = prefs(context).edit().putBoolean("asked_notifications", true).apply()
}
