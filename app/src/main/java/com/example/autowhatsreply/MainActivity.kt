package com.example.autowhatsreply

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val switchEnable = findViewById<SwitchCompat>(R.id.switchEnable)
        val btnPermission = findViewById<Button>(R.id.btnPermission)
        val etTriggerText = findViewById<TextInputEditText>(R.id.etTriggerText)
        val etReplyText = findViewById<TextInputEditText>(R.id.etReplyText)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        switchEnable.isChecked = prefs.getBoolean("is_enabled", false)
        etTriggerText.setText(prefs.getString("trigger_phrase", "hola"))
        etReplyText.setText(prefs.getString("reply_message", "Hola, estoy ocupado ahora mismo."))

        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnSave.setOnClickListener {
            prefs.edit().apply {
                putBoolean("is_enabled", switchEnable.isChecked)
                putString("trigger_phrase", etTriggerText.text.toString().trim())
                putString("reply_message", etReplyText.text.toString().trim())
                apply()
            }
            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        }
    }
}
