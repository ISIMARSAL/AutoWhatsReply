package com.example.autowhatsreply

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

        val switchAutoReply = findViewById<Switch>(R.id.switchAutoReply)
        val etTrigger = findViewById<EditText>(R.id.etTrigger)
        val etReply = findViewById<EditText>(R.id.etReply)
        val tvSimilarityLabel = findViewById<TextView>(R.id.tvSimilarityLabel)
        val sbSimilarity = findViewById<SeekBar>(R.id.sbSimilarity)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnPermission = findViewById<Button>(R.id.btnPermission)

        // Cargar estado guardado
        val currentSimilarity = prefs.getInt("similarity_percent", 65)
        sbSimilarity.progress = currentSimilarity
        tvSimilarityLabel.text = "Nivel de similitud requerido: $currentSimilarity%"

        switchAutoReply.isChecked = prefs.getBoolean("is_enabled", true)
        etTrigger.setText(prefs.getString("trigger_message", ""))
        etReply.setText(prefs.getString("reply_message", "Hola, bienvenido al evento."))

        // Evento al mover la barra de porcentaje
        sbSimilarity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSimilarityLabel.text = "Nivel de similitud requerido: $progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val isEnabled = switchAutoReply.isChecked
            val trigger = etTrigger.text.toString().trim()
            val reply = etReply.text.toString().trim()
            val similarity = sbSimilarity.progress

            if (reply.isEmpty()) {
                Toast.makeText(this, "El mensaje de respuesta no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putBoolean("is_enabled", isEnabled)
                .putString("trigger_message", trigger)
                .putString("reply_message", reply)
                .putInt("similarity_percent", similarity)
                .apply()

            val stateText = if (isEnabled) "ACTIVADA" else "DESACTIVADA"
            Toast.makeText(this, "Guardado ($similarity% similitud) - Auto-respuesta $stateText", Toast.LENGTH_SHORT).show()
        }

        btnPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }
}
