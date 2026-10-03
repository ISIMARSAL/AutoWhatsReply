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

        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnPermission = findViewById<Button>(R.id.btnPermission)

        val isEnabled = prefs.getBoolean("is_enabled", false)
        switchAutoReply.isChecked = isEnabled
        updateStatusAndNotification(isEnabled, tvStatus)

        etTrigger.setText(prefs.getString("trigger_message", ""))
        etReply.setText(prefs.getString("reply_message", ""))

        val currentSimilarity = prefs.getInt("similarity_percent", 50)
        sbSimilarity.progress = currentSimilarity
        tvSimilarityLabel.text = "Sensibilidad de similitud: $currentSimilarity%"

        switchIgnoreGroups.isChecked = prefs.getBoolean("ignore_groups", true)

        switchAutoReply.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_enabled", isChecked).apply()
            updateStatusAndNotification(isChecked, tvStatus)
        }

        sbSimilarity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSimilarityLabel.text = "Sensibilidad de similitud: $progress%"
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
                .apply()

            Toast.makeText(this, "Configuración guardada para Eventos Salinas", Toast.LENGTH_SHORT).show()
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
                .setContentText("Escuchando notificaciones para Eventos Salinas")
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
