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

        val isEnabled = prefs.getBoolean("is_enabled", false)
        switchAutoReply.isChecked = isEnabled
        updateStatusAndNotification(isEnabled, tvStatus)

        etTrigger.setText(prefs.getString("trigger_message", ""))
        etReply.setText(prefs.getString("reply_message", ""))

        val currentSimilarity = prefs.getInt("similarity_percent", 70)
        sbSimilarity.progress = currentSimilarity
        tvSimilarityLabel.text = "Similitud requerida: $currentSimilarity%"

        switchIgnoreGroups.isChecked = prefs.getBoolean("ignore_groups", true)
        switchSchedule.isChecked = prefs.getBoolean("schedule_enabled", false)
        etStartHour.setText(prefs.getString("start_hour", ""))
        etEndHour.setText(prefs.getString("end_hour", ""))

        switchAutoReply.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_enabled", isChecked).apply()
            updateStatusAndNotification(isChecked, tvStatus)
        }

        sbSimilarity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSimilarityLabel.text = "Similitud requerida: $progress%"
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

            Toast.makeText(this, "Configuración guardada correctamente", Toast.LENGTH_SHORT).show()
        }

        btnTestMatch.setOnClickListener {
            val trigger = etTrigger.text.toString().trim()
            val requiredPercent = sbSimilarity.progress

            if (trigger.isEmpty()) {
                Toast.makeText(this, "Escribe primero un mensaje en la casilla disparadora", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val input = EditText(this)
            input.hint = "Escribe una frase de prueba"

            AlertDialog.Builder(this)
                .setTitle("Probador de Similitud")
                .setMessage("Frase configurada: '$trigger'\nExigencia: $requiredPercent%\n\nIntroduce el mensaje a evaluar:")
                .setView(input)
                .setPositiveButton("Evaluar") { _, _ ->
                    val testText = input.text.toString()
                    val score = WhatsAppResponderService.calculateMatchPercentage(testText, trigger)
                    val isMatch = score >= requiredPercent
                    val resultMessage = "Puntuación obtenida: $score%\nRequerido: $requiredPercent%\n\nResultado: " +
                            if (isMatch) "¡SÍ SE ACTIVA!" else "NO ALCANZA EL MÍNIMO"

                    AlertDialog.Builder(this)
                        .setTitle("Resultado de Similitud")
                        .setMessage(resultMessage)
                        .setPositiveButton("Aceptar", null)
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
                "Estado de AutoWhatsReply",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        if (isEnabled) {
            tv.text = "Estado: ACTIVADO (Escuchando mensajes)"
            tv.setTextColor(0xFF10B981.toInt())

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("AutoWhatsReply Activo")
                .setContentText("Escuchando notificaciones en segundo plano")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManager.notify(1001, notification)
        } else {
            tv.text = "Estado: DESACTIVADO"
            tv.setTextColor(0xFF64748B.toInt())
            notificationManager.cancel(1001)
        }
    }
}
