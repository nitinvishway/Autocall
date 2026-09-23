package com.example.autocallanswer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(Prefs.ENABLED, false)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!enabled) return
                startService(context, CallAnswerService.ACTION_START_RINGING)
            }
            TelephonyManager.EXTRA_STATE_IDLE,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call ended, or was answered another way (e.g. manually) —
                // stop watching so we don't act on the next unrelated ring.
                startService(context, CallAnswerService.ACTION_STOP)
            }
        }
    }

    private fun startService(context: Context, action: String) {
        val serviceIntent = Intent(context, CallAnswerService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
