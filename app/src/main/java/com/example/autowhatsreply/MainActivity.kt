package com.example.autowhatsreply

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        val btnNotificationPermission = findViewById<Button>(R.id.btnNotificationPermission)
        val btnBatteryOptimization = findViewById<Button>(R.id.btnBatteryOptimization)
        val switchEnable = findViewById<Switch>(R.id.switchEnable)
        val switchIgnoreGroups = findViewById<Switch>(R.id.switchIgnoreGroups)
        val etTriggerMessage = findViewById<EditText>(R.id.etTriggerMessage)
        val sbSimilarity = findViewById<SeekBar>(R.id.sbSimilarity)
        val tvSimilarityValue = findViewById<TextView>(R.id.tvSimilarityValue)
        val etReplyMessage = findViewById<EditText>(R.id.etReplyMessage)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnNotificationPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnBatteryOptimization.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Tu versión de Android no requiere esta configuración", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                    startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "Abre Ajustes > Batería en tu teléfono", Toast.LENGTH_LONG).show()
                }
            }
        }

        switchEnable.isChecked = prefs.getBoolean("is_enabled", false)
        switchIgnoreGroups.isChecked = prefs.getBoolean("ignore_groups", true)
        etTriggerMessage.setText(prefs.getString("trigger_message", ""))
        etReplyMessage.setText(prefs.getString("reply_message", ""))

        val savedSimilarity = prefs.getInt("similarity_percent", 50)
        sbSimilarity.progress = savedSimilarity
        tvSimilarityValue.text = "$savedSimilarity%"

        sbSimilarity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSimilarityValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            prefs.edit().apply {
                putBoolean("is_enabled", switchEnable.isChecked)
                putBoolean("ignore_groups", switchIgnoreGroups.isChecked)
                putString("trigger_message", etTriggerMessage.text.toString().trim())
                putInt("similarity_percent", sbSimilarity.progress)
                putString("reply_message", etReplyMessage.text.toString().trim())
                apply()
            }
            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        }
    }
}
