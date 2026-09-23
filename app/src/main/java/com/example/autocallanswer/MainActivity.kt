package com.example.autocallanswer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var switchEnabled: Switch
    private lateinit var switchProximity: Switch
    private lateinit var switchDelay: Switch
    private lateinit var editDelay: EditText

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Phone permissions are required for auto-answer to work",
                Toast.LENGTH_LONG
            ).show()
            switchEnabled.isChecked = false
            savePref(Prefs.ENABLED, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchEnabled = findViewById(R.id.switchEnabled)
        switchProximity = findViewById(R.id.switchProximity)
        switchDelay = findViewById(R.id.switchDelay)
        editDelay = findViewById(R.id.editDelay)

        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        switchEnabled.isChecked = prefs.getBoolean(Prefs.ENABLED, false)
        // Default proximity to on for first-time users since it's the more reliable method.
        switchProximity.isChecked = prefs.getBoolean(Prefs.PROXIMITY_ENABLED, true)
        switchDelay.isChecked = prefs.getBoolean(Prefs.DELAY_ENABLED, false)
        editDelay.setText(prefs.getInt(Prefs.DELAY_SECONDS, 2).toString())

        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasPermissions()) {
                requestPermissions()
            }
            savePref(Prefs.ENABLED, isChecked)
        }

        switchProximity.setOnCheckedChangeListener { _, isChecked ->
            savePref(Prefs.PROXIMITY_ENABLED, isChecked)
        }

        switchDelay.setOnCheckedChangeListener { _, isChecked ->
            savePref(Prefs.DELAY_ENABLED, isChecked)
        }

        editDelay.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val seconds = editDelay.text.toString().toIntOrNull() ?: 2
                getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(Prefs.DELAY_SECONDS, seconds)
                    .apply()
            }
        }
    }

    private fun savePref(key: String, value: Boolean) {
        getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    private fun hasPermissions(): Boolean {
        val readPhoneState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val answerCalls = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return readPhoneState && answerCalls && notifications
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }
}
