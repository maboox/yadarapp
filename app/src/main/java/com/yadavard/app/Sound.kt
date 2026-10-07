package com.yadavard.app

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Yadar plays its own alert sounds instead of relying on notification-channel sounds,
 * which some phones (for example MIUI) silence by default or ignore.
 */
object AlertSound {
    private val main = Handler(Looper.getMainLooper())
    private var oneShot: MediaPlayer? = null

    fun soundUri(context: Context, alarm: Boolean): Uri {
        val custom = (if (alarm) Prefs.alarmSound(context) else Prefs.reminderSound(context))?.let { runCatching { Uri.parse(it) }.getOrNull() }
        return custom
            ?: RingtoneManager.getDefaultUri(if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun soundName(context: Context, alarm: Boolean): String {
        val custom = if (alarm) Prefs.alarmSound(context) else Prefs.reminderSound(context)
        if (custom == null) return if (Prefs.language(context) == AppLanguage.FA) "پیش‌فرض گوشی" else "Phone default"
        return runCatching { RingtoneManager.getRingtone(context, Uri.parse(custom))?.getTitle(context) }.getOrNull() ?: custom
    }

    fun player(context: Context, alarm: Boolean, loop: Boolean): MediaPlayer? {
        val attributes = AudioAttributes.Builder()
            .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val candidates = listOfNotNull(soundUri(context, alarm),
            RingtoneManager.getDefaultUri(if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)).distinct()
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attributes)
                mp.setDataSource(context, uri)
                mp.isLooping = loop
                mp.prepare()
                return mp
            } catch (_: Exception) { mp.release() }
        }
        return null
    }

    private fun vibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    fun vibrate(context: Context, repeat: Boolean) {
        if (!Prefs.vibrate(context)) return
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val pattern = if (repeat) longArrayOf(0, 700, 500, 700, 500) else longArrayOf(0, 250, 150, 250)
        val attrs = AudioAttributes.Builder().setUsage(if (repeat) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(pattern, if (repeat) 0 else -1), attrs)
    }

    fun stopVibration(context: Context) { vibrator(context)?.cancel() }

    /** Short sound for a regular reminder. Respects silent/vibrate mode and Do Not Disturb. */
    fun notifyOnce(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val dnd = nm.currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> return
            AudioManager.RINGER_MODE_VIBRATE -> { if (!dnd) vibrate(context, false); return }
        }
        if (dnd) return
        vibrate(context, false)
        playOnce(context, alarm = false)
    }

    /** Plays one sound to completion (also used for previews and as a fallback). */
    fun playOnce(context: Context, alarm: Boolean) {
        val app = context.applicationContext
        main.post {
            oneShot?.let { runCatching { it.stop() }; it.release() }
            val mp = player(app, alarm, loop = false) ?: return@post
            oneShot = mp
            mp.setOnCompletionListener { it.release(); if (oneShot === it) oneShot = null }
            runCatching { mp.start() }
            // Safety stop for long tracks used as notification sounds.
            main.postDelayed({ if (oneShot === mp) { runCatching { mp.stop() }; mp.release(); oneShot = null } }, 15_000)
        }
    }

    fun stopPreview() {
        main.post { oneShot?.let { runCatching { it.stop() }; it.release() }; oneShot = null }
    }

    fun volumeOk(context: Context, alarm: Boolean): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        return audio.getStreamVolume(if (alarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION) > 0
    }
}

/**
 * Foreground service that rings for "alarm" reminders until the user reacts or the ring period ends.
 * The reminder's alert notification is the service notification, so nothing extra is shown.
 */
class AlertService : Service() {
    private var player: MediaPlayer? = null
    private var currentId = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopRinging(currentId, remove = false, quietRepost = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); instance = this }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        releasePlayer()
        AlertSound.stopVibration(this)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, 0) ?: 0
        val reminder = if (id == TEST_ID) Notifier.testReminder(this) else Repo.get(this, id)
        val notification = reminder?.let { Notifier.buildAlert(this, it, it.pendingAt, ring = true) }
        if (reminder == null || notification == null || (id != TEST_ID && (reminder.done || reminder.pendingAt == 0L))) {
            // Android requires startForeground after startForegroundService, even when there is nothing to ring.
            runCatching { ServiceCompat.startForeground(this, Notifier.SERVICE_ID, Notifier.serviceNotification(this), type()) }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, Notifier.mainId(id), notification, type())
        } catch (_: Exception) {
            Notifier.post(this, Notifier.mainId(id), notification)
        }
        currentId = id
        releasePlayer()
        player = AlertSound.player(this, alarm = true, loop = true)?.also { runCatching { it.start() } }
        AlertSound.vibrate(this, repeat = true)
        handler.removeCallbacks(timeout)
        val minutes = if (id == TEST_ID) 1 else Prefs.ringMinutes(this)
        handler.postDelayed(timeout, minutes * 60_000L)
        return START_NOT_STICKY
    }

    private fun type() = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    /** Stops ringing. [remove] drops the notification (handled); otherwise it stays as a silent reminder. */
    fun stopRinging(id: Long, remove: Boolean, quietRepost: Boolean) {
        if (id != 0L && id != currentId) return
        handler.removeCallbacks(timeout)
        releasePlayer()
        AlertSound.stopVibration(this)
        val stopped = currentId
        currentId = 0
        if (quietRepost && stopped != 0L && stopped != TEST_ID) {
            Repo.get(this, stopped)?.takeIf { !it.done && it.pendingAt > 0 }?.let {
                Notifier.post(this, Notifier.mainId(stopped), Notifier.buildAlert(this, it, it.pendingAt, ring = false, quiet = true))
            }
        }
        ServiceCompat.stopForeground(this, if (remove || stopped == TEST_ID) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val TEST_ID = -1L
        @Volatile private var instance: AlertService? = null
        private val main = Handler(Looper.getMainLooper())

        fun ringing(): Boolean = instance?.currentId?.let { it != 0L } == true

        fun ring(context: Context, id: Long): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, AlertService::class.java).putExtra(EXTRA_ID, id))
            true
        } catch (_: Exception) { false }

        /** Stops ringing for [id] (0 = whatever is ringing). Safe to call when nothing rings. */
        fun stop(id: Long = 0, remove: Boolean = true, quietRepost: Boolean = false) {
            main.post { instance?.stopRinging(id, remove, quietRepost) }
        }
    }
}
