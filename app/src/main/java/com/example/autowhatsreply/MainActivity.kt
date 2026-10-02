package com.example.autowhatsreply

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        val switchAutoReply = findViewById<Switch>(R.id.switchAutoReply)
        val etTrigger = findViewById<EditText>(R.id.etTrigger)
        val etReply = findViewById<EditText>(R.id.etReply)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnPermission = findViewById<Button>(R.id.btnPermission)

        // Cargar estado e información guardada
        switchAutoReply.isChecked = prefs.getBoolean("is_enabled", true)
        etTrigger.setText(prefs.getString("trigger_message", ""))
        etReply.setText(prefs.getString("reply_message", "Hola, bienvenido al evento."))

        btnSave.setOnClickListener {
            val isEnabled = switchAutoReply.isChecked
            val trigger = etTrigger.text.toString().trim()
            val reply = etReply.text.toString().trim()

            if (reply.isEmpty()) {
                Toast.makeText(this, "El mensaje de respuesta no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putBoolean("is_enabled", isEnabled)
                .putString("trigger_message", trigger)
                .putString("reply_message", reply)
                .apply()

            val stateText = if (isEnabled) "ACTIVADA" else "DESACTIVADA"
            Toast.makeText(this, "Guardado: Auto-respuesta $stateText", Toast.LENGTH_SHORT).show()
        }

        btnPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }
}
