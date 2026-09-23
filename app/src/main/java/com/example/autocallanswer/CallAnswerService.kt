package com.example.autocallanswer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.TelecomManager
import android.view.KeyEvent

/**
 * Runs only while the phone is ringing. Depending on what's enabled in
 * settings, it watches the proximity sensor (answers when the phone is
 * raised to the ear) and/or a fixed delay timer (answers after N seconds).
 * Whichever condition fires first wins, and the service stops itself
 * immediately after.
 */
class CallAnswerService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START_RINGING = "com.example.autocallanswer.action.START_RINGING"
        const val ACTION_STOP = "com.example.autocallanswer.action.STOP"
        private const val NOTIF_CHANNEL_ID = "auto_call_answer_channel"
        private const val NOTIF_ID = 1001
    }

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private val handler = Handler(Looper.getMainLooper())
    private var delayRunnable: Runnable? = null
    private var answered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RINGING -> startWatching()
            else -> {
                // Covers ACTION_STOP and any other case. We must call
                // startForeground() here too in case this service instance
                // was launched via startForegroundService() for this call,
                // otherwise Android throws on some versions.
                startForeground(NOTIF_ID, buildNotification())
                stopWatchingAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startWatching() {
        answered = false
        startForeground(NOTIF_ID, buildNotification())

        val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val proximityEnabled = prefs.getBoolean(Prefs.PROXIMITY_ENABLED, false)
        val delayEnabled = prefs.getBoolean(Prefs.DELAY_ENABLED, false)
        val delaySeconds = prefs.getInt(Prefs.DELAY_SECONDS, 2)

        if (proximityEnabled) {
            val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            if (sensor != null) {
                sensorManager = manager
                proximitySensor = sensor
                manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }

        if (delayEnabled) {
            val runnable = Runnable { triggerAnswer() }
            delayRunnable = runnable
            handler.postDelayed(runnable, delaySeconds * 1000L)
        }

        // Neither method enabled — nothing to do, stop right away.
        if (!proximityEnabled && !delayEnabled) {
            stopWatchingAndSelf()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val distance = event.values.getOrNull(0) ?: return
        val sensor = proximitySensor ?: return
        // Most devices report 0 (or a small value) when something is
        // covering the sensor, and the sensor's max range when it's clear.
        if (distance < sensor.maximumRange) {
            triggerAnswer()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerAnswer() {
        if (answered) return
        answered = true
        answerCall(applicationContext)
        stopWatchingAndSelf()
    }

    private fun stopWatchingAndSelf() {
        delayRunnable?.let { handler.removeCallbacks(it) }
        delayRunnable = null
        sensorManager?.unregisterListener(this)
        sensorManager = null
        proximitySensor = null
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val builder: Notification.Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Auto Call Answer",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
            builder = Notification.Builder(this, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            builder = Notification.Builder(this)
        }

        builder
            .setContentTitle("Auto Call Answer")
            .setContentText("Watching for an incoming call…")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setOngoing(true)

        return builder.build()
    }

    private fun answerCall(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val telecomManager =
                    context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.acceptRingingCall()
                return
            } catch (e: SecurityException) {
                // Permission missing — fall through to the legacy method.
            }
        }
        simulateHeadsetHook(context)
    }

    private fun simulateHeadsetHook(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = System.currentTimeMillis()
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, 0)
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK, 0)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
    }
}
