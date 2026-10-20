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

    private var logFile: File? = null
    private var fileWriter: FileWriter? = null

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
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    fun startLoggingSession() {
        if (_isRecording.value) return
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logsDir = File(getExternalFilesDir(null), "datalogs")
            if (!logsDir.exists()) logsDir.mkdirs()

            val file = File(logsDir, "AstraScan_Log_$timestamp.csv")
            logFile = file
            fileWriter = FileWriter(file, true).apply {
                write("TimestampMs,DateTime,RPM,TargetIdleRPM,Speed_kmh,EngineLoad_pct,ECT_C,IAT_C,MAP_kPa,BARO_kPa,Vacuum_bar,MAF_gs,TPS_pct,TPS1_V,TPS2_V,Pedal_pct,TI_ms,DutyCycle_pct,IAC_steps,Spark_deg,KnockGlobal_deg,KnockCil1_deg,KnockCil2_deg,KnockCil3_deg,KnockCil4_deg,Lambda1_V,Lambda2_V,LambdaFactor,Loop_Status,STFT_pct,LTFT_pct,AF_Ratio,Ethanol_pct,Consump_Lh,Consump_kmL,EVAP_pct,ECU_Volt,ELM_Volt\n")
                flush()
            }
            _recordedCount.value = 0
            _isRecording.value = true
            _lastExportedFilePath.value = file.absolutePath
            updateNotification("Datalogger Gravando", "Arquivo: ${file.name}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logSample(telemetry: LiveTelemetry) {
        if (!_isRecording.value || fileWriter == null) return
        try {
            val dateStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(telemetry.timestampMs))
            val line = String.format(
                Locale.US,
                "%d,%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f,%.2f,%.1f,%.2f,%.2f,%.1f,%.3f,%.1f,%d,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%.3f,%.3f,%.3f,\"%s\",%.1f,%.1f,%.2f,%.1f,%.2f,%.1f,%.1f,%.1f,%.1f\n",
                telemetry.timestampMs,
                dateStr,
                telemetry.rpm,
                telemetry.targetIdleRpm,
                telemetry.speedKmh,
                telemetry.engineLoadPercent,
                telemetry.coolantTempC,
                telemetry.intakeAirTempC,
                telemetry.mapKpa,
                telemetry.baroKpa,
                telemetry.manifoldVacuumBar,
                telemetry.estimatedMafGramsSec,
                telemetry.throttlePercent,
                telemetry.throttleVoltage1,
                telemetry.throttleVoltage2,
                telemetry.acceleratorPedalPercent,
                telemetry.injectionTimeMs,
                telemetry.injectorDutyCyclePercent,
                telemetry.iacSteps,
                telemetry.sparkAdvanceDeg,
                telemetry.knockRetardDeg,
                telemetry.knockCylinder1Deg,
                telemetry.knockCylinder2Deg,
                telemetry.knockCylinder3Deg,
                telemetry.knockCylinder4Deg,
                telemetry.lambdaVoltage,
                telemetry.lambdaPostCatVoltage,
                telemetry.equivalenceRatioLambda,
                telemetry.fuelLoopStatus,
                telemetry.shortTermFuelTrim,
                telemetry.longTermFuelTrim,
                telemetry.estimatedAfRatio,
                telemetry.ethanolPercentage,
                telemetry.instantConsumptionLitersHour,
                telemetry.instantConsumptionKmPerLiter,
                telemetry.evapPurgePercent,
                telemetry.ecuBatteryVoltage,
                telemetry.elmBatteryVoltage
            )
            fileWriter?.write(line)
            fileWriter?.flush()
            _recordedCount.value = _recordedCount.value + 1

            if (_recordedCount.value % 20 == 0) {
                updateNotification("Datalogger Ativo", "${_recordedCount.value} amostras gravadas | RPM: ${telemetry.rpm.toInt()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopLoggingSession(): String? {
        if (!_isRecording.value) return _lastExportedFilePath.value
        try {
            fileWriter?.flush()
            fileWriter?.close()
        } catch (_: Exception) {}
        fileWriter = null
        _isRecording.value = false
        val path = logFile?.absolutePath
        updateNotification("Datalogger Salvo", "Sessão concluída: ${_recordedCount.value} amostras.")
        return path
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
