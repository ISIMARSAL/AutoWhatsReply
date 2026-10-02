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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        val switchAutoReply = findViewById<Switch>(R.id.switchAutoReply)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val etTrigger = findViewById<EditText>(R.id.etTrigger)
        val etReply = findViewById<EditText>(R.id.etReply)
        val tvSimilarityLabel = findViewById<TextView>(R.id.tvSimilarityLabel)
        val sbSimilarity = findViewById<SeekBar>(R.id.sbSimilarity)

        val switchIgnoreGroups = findViewById<Switch>(R.id.switchIgnoreGroups)
        val switchSchedule = findViewById<Switch>(R.id.switchSchedule)
        val etStartHour = findViewById<EditText>(R.id.etStartHour)
        val etEndHour = findViewById<EditText>(R.id.etEndHour)

        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTestMatch = findViewById<Button>(R.id.btnTestMatch)
        val btnPermission = findViewById<Button>(R.id.btnPermission)

        // Cargar datos
        val isEnabled = prefs.getBoolean("is_enabled", false)
        switchAutoReply.isChecked = isEnabled
        updateStatusText(tvStatus, isEnabled)

        etTrigger.setText(prefs.getString("trigger_message", ""))
        etReply.setText(prefs.getString("reply_message", ""))

        val currentSimilarity = prefs.getInt("similarity_percent", 90)
        sbSimilarity.progress = currentSimilarity
        tvSimilarityLabel.text = "Precisión requerida: $currentSimilarity%"

        switchIgnoreGroups.isChecked = prefs.getBoolean("ignore_groups", true)
        switchSchedule.isChecked = prefs.getBoolean("schedule_enabled", false)
        etStartHour.setText(prefs.getString("start_hour", ""))
        etEndHour.setText(prefs.getString("end_hour", ""))

        // Switch ON/OFF instantáneo
        switchAutoReply.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_enabled", isChecked).apply()
            updateStatusText(tvStatus, isChecked)
        }

        sbSimilarity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSimilarityLabel.text = "Precisión requerida: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val reply = etReply.text.toString().trim()
            if (reply.isEmpty()) {
                Toast.makeText(this, "Por favor introduce una respuesta válida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("trigger_message", etTrigger.text.toString().trim())
                .putString("reply_message", reply)
                .putInt("similarity_percent", sbSimilarity.progress)
                .putBoolean("ignore_groups", switchIgnoreGroups.isChecked)
                .putBoolean("schedule_enabled", switchSchedule.isChecked)
                .putString("start_hour", etStartHour.text.toString().trim())
                .putString("end_hour", etEndHour.text.toString().trim())
                .apply()

            Toast.makeText(this, "Ajustes guardados correctamente", Toast.LENGTH_SHORT).show()
        }

        // Función para probar mensajes de prueba sin necesitar otro teléfono
        btnTestMatch.setOnClickListener {
            val trigger = etTrigger.text.toString().trim()
            val requiredPercent = sbSimilarity.progress

            if (trigger.isEmpty()) {
                Toast.makeText(this, "Escribe primero un mensaje disparador arriba", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val input = EditText(this)
            input.hint = "Escribe un mensaje de prueba (ej: hola!)"

            AlertDialog.Builder(this)
                .setTitle("Probar Coincidencia")
                .setMessage("Ingresa la frase de simulación:")
                .setView(input)
                .setPositiveButton("Evaluar") { _, _ ->
                    val testText = input.text.toString()
                    val isMatch = WhatsAppResponderService.evaluateSimilarity(testText, trigger, requiredPercent)
                    val resultMessage = if (isMatch) {
                        "¡ÉXITO! La frase '$testText' SÍ activa la auto-respuesta."
                    } else {
                        "RECHAZADO: La frase '$testText' NO alcanza el $requiredPercent% de similitud exigido."
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Resultado de la Prueba")
                        .setMessage(resultMessage)
                        .setPositiveButton("OK", null)
                        .show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        btnPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    private fun updateStatusText(tv: TextView, isEnabled: Boolean) {
        if (isEnabled) {
            tv.text = "Estado: ACTIVADO"
            tv.setTextColor(0xFF10B981.toInt())
        } else {
            tv.text = "Estado: DESACTIVADO"
            tv.setTextColor(0xFF6B7280.toInt())
        }
    }
}
