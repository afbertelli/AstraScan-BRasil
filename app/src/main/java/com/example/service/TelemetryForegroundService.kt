package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.LiveTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelemetryForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "astra_scan_telemetry_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_LOGGING = "ACTION_START_LOGGING"
        const val ACTION_STOP_LOGGING = "ACTION_STOP_LOGGING"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedCount = MutableStateFlow(0)
    val recordedCount: StateFlow<Int> = _recordedCount.asStateFlow()

    private val _lastExportedFilePath = MutableStateFlow<String?>(null)
    val lastExportedFilePath: StateFlow<String?> = _lastExportedFilePath.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): TelemetryForegroundService = this@TelemetryForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LOGGING -> startLoggingSession()
            ACTION_STOP_LOGGING -> stopLoggingSession()
        }
        val notification = buildNotification("AstraScan Conectado", "Telemetria e monitoramento de barramento ativos.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    fun startLoggingSession(fileName: String = "datalog.csv") {
        _isRecording.value = true
        _recordedCount.value = 0
        updateNotification("Datalogger Gravando", "Arquivo: $fileName")
    }

    fun updateSampleCount(count: Int, rpm: Float) {
        _recordedCount.value = count
        if (count % 20 == 0) {
            updateNotification("Datalogger Ativo", "$count amostras gravadas | RPM: ${rpm.toInt()}")
        }
    }

    fun logSample(telemetry: LiveTelemetry) {
        // Notificação de telemetria em segundo plano
        if (_isRecording.value) {
            val current = _recordedCount.value + 1
            _recordedCount.value = current
            if (current % 25 == 0) {
                updateNotification("Datalogger Ativo", "$current amostras | RPM: ${telemetry.rpm.toInt()}")
            }
        }
    }

    fun stopLoggingSession(): String? {
        val wasRecording = _isRecording.value
        _isRecording.value = false
        if (wasRecording) {
            updateNotification("Datalogger Salvo", "Sessão concluída: ${_recordedCount.value} amostras.")
        } else {
            updateNotification("AstraScan Conectado", "Telemetria e monitoramento de barramento ativos.")
        }
        return _lastExportedFilePath.value
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AstraScan Telemetria",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoramento e Datalogger contínuo do veículo"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        stopLoggingSession()
        serviceScope.cancel()
        super.onDestroy()
    }
}
