package com.example.autowhatsreply

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        val etTrigger = findViewById<EditText>(R.id.etTrigger)
        val etReply = findViewById<EditText>(R.id.etReply)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnPermission = findViewById<Button>(R.id.btnPermission)

        // Cargar valores guardados previamente
        etTrigger.setText(prefs.getString("trigger_message", ""))
        etReply.setText(prefs.getString("reply_message", "Hola, estoy ocupado ahora mismo."))

        btnSave.setOnClickListener {
            val trigger = etTrigger.text.toString().trim()
            val reply = etReply.text.toString().trim()

            if (reply.isEmpty()) {
                Toast.makeText(this, "El mensaje de respuesta no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("trigger_message", trigger)
                .putString("reply_message", reply)
                .apply()

            Toast.makeText(this, "Configuración guardada correctamente", Toast.LENGTH_SHORT).show()
        }

        btnPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }
}
