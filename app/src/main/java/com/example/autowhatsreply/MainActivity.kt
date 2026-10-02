package com.example.autowhatsreply

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat

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
        updateStatusAndNotification(isEnabled, tvStatus)

        etTrigger.setText(prefs.getString("trigger_message", ""))
        etReply.setText(prefs.getString("reply_message", ""))

        val currentSimilarity = prefs.getInt("similarity_percent", 70)
        sbSimilarity.progress = currentSimilarity
        tvSimilarityLabel.text = "Porcentaje de similitud: $currentSimilarity%"

        switchIgnoreGroups.isChecked = prefs.getBoolean("ignore_groups", true)
        switchSchedule.isChecked = prefs.getBoolean("schedule_enabled", false)
        etStartHour.setText(prefs.getString("start_hour", ""))
        etEndHour.setText(prefs.getString("end_hour", ""))

        // Interruptor ON/OFF instantáneo y actualización de notificación fijos
        switchAutoReply.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_enabled", isChecked).apply()
            updateStatusAndNotification(isChecked, tvStatus)
        }

        sbSimilarity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSimilarityLabel.text = "Porcentaje de similitud: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val reply = etReply.text.toString().trim()
            if (reply.isEmpty()) {
                Toast.makeText(this, "Por favor escribe un mensaje de respuesta", Toast.LENGTH_SHORT).show()
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

            Toast.makeText(this, "Ajustes guardados con éxito", Toast.LENGTH_SHORT).show()
        }

        btnTestMatch.setOnClickListener {
            val trigger = etTrigger.text.toString().trim()
            val requiredPercent = sbSimilarity.progress

            if (trigger.isEmpty()) {
                Toast.makeText(this, "Escribe primero un mensaje disparador", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val input = EditText(this)
            input.hint = "Ej: hola! estoy en el evnto"

            AlertDialog.Builder(this)
                .setTitle("Probar Algoritmo de Similitud")
                .setMessage("Frase esperada: '$trigger' ($requiredPercent%)\n\nIngresa la frase a probar:")
                .setView(input)
                .setPositiveButton("Probar") { _, _ ->
                    val testText = input.text.toString()
                    val isMatch = WhatsAppResponderService.evaluateSimilarity(testText, trigger, requiredPercent)
                    val resultMessage = if (isMatch) {
                        "¡ÉXITO! La frase '$testText' SÍ activa la respuesta automática."
                    } else {
                        "RECHAZADO: La frase '$testText' NO supera el $requiredPercent% de similitud."
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Resultado")
                        .setMessage(resultMessage)
                        .setPositiveButton("Entendido", null)
                        .show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        btnPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    private fun updateStatusAndNotification(isEnabled: Boolean, tv: TextView) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "autowhatsreply_status_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Estado del servicio AutoWhatsReply",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        if (isEnabled) {
            tv.text = "Estado: ACTIVADO (Escuchando WhatsApp)"
            tv.setTextColor(0xFF10B981.toInt())

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("AutoWhatsReply Activo")
                .setContentText("El servicio de respuesta automática está funcionando.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManager.notify(1001, notification)
        } else {
            tv.text = "Estado: DESACTIVADO"
            tv.setTextColor(0xFF6B7280.toInt())
            notificationManager.cancel(1001)
        }
    }
}
