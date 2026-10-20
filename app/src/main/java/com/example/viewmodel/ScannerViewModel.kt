package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiDiagnosticEngine
import com.example.ai.AiDiagnosticReport
import com.example.ai.ComponentAudit
import com.example.model.DtcCode
import com.example.model.EcuSensorProfile
import com.example.model.LiveTelemetry
import com.example.model.ProtocolType
import com.example.model.ScanState
import com.example.model.VehicleProfile
import com.example.serial.AutoScannerManager
import com.example.service.TelemetryForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen(val title: String) {
    CONNECTION("Conexão"),
    DASHBOARD("Cockpit"),
    AI_DIAGNOSTIC("IA Diagnóstico"),
    DIAGNOSTICS("DTC"),
    DATALOGGER("Datalog")
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    val scannerManager = AutoScannerManager(viewModelScope)

    private val _currentScreen = MutableStateFlow(AppScreen.CONNECTION)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice.asStateFlow()

    private val _clearDtcMessage = MutableStateFlow<String?>(null)
    val clearDtcMessage: StateFlow<String?> = _clearDtcMessage.asStateFlow()

    private val _isClearing = MutableStateFlow(false)
    val isClearing: StateFlow<Boolean> = _isClearing.asStateFlow()

    // Estados da Ferramenta de Diagnóstico por IA
    private val _aiReport = MutableStateFlow<AiDiagnosticReport?>(null)
    val aiReport: StateFlow<AiDiagnosticReport?> = _aiReport.asStateFlow()

    private val _isAnalyzingAi = MutableStateFlow(false)
    val isAnalyzingAi: StateFlow<Boolean> = _isAnalyzingAi.asStateFlow()

    private val _componentAudits = MutableStateFlow<List<ComponentAudit>>(emptyList())
    val componentAudits: StateFlow<List<ComponentAudit>> = _componentAudits.asStateFlow()

    val isInjectionDetected: StateFlow<Boolean> = scannerManager.isInjectionDetected
    val sensorProfile: StateFlow<EcuSensorProfile?> = scannerManager.sensorProfile

    // -------------------------------------------------------------
    // GESTÃO DO DATALOGGER, ARQUIVOS CSV E EXPORTAÇÃO
    // -------------------------------------------------------------
    private val _isRecordingDatalogger = MutableStateFlow(false)
    val isRecordingDatalogger: StateFlow<Boolean> = _isRecordingDatalogger.asStateFlow()

    private val _recordedDataloggerCount = MutableStateFlow(0)
    val recordedDataloggerCount: StateFlow<Int> = _recordedDataloggerCount.asStateFlow()

    private val _lastExportedFilePath = MutableStateFlow<String?>(null)
    val lastExportedFilePath: StateFlow<String?> = _lastExportedFilePath.asStateFlow()

    private val _savedDatalogsList = MutableStateFlow<List<File>>(emptyList())
    val savedDatalogsList: StateFlow<List<File>> = _savedDatalogsList.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    // Buffer de telemetria em memória da sessão
    private val sessionBuffer = mutableListOf<LiveTelemetry>()
    private var activeRecordingFile: File? = null
    private var activeFileWriter: FileWriter? = null

    // -------------------------------------------------------------
    // MOTOR E FERRAMENTA INTERNA DE AUTO-RESIZE (CALIBRAÇÃO DE TELA)
    // -------------------------------------------------------------
    private val prefs = application.getSharedPreferences("astra_autoresize_prefs", Context.MODE_PRIVATE)

    private val _isAutoResizeEnabled = MutableStateFlow(prefs.getBoolean("key_autoresize_enabled", true))
    val isAutoResizeEnabled: StateFlow<Boolean> = _isAutoResizeEnabled.asStateFlow()

    private val _autoResizeFineTune = MutableStateFlow(prefs.getFloat("key_autoresize_fine_tune", 1.0f))
    val autoResizeFineTune: StateFlow<Float> = _autoResizeFineTune.asStateFlow()

    private val _autoResizeBaseWidth = MutableStateFlow(prefs.getFloat("key_autoresize_base_width", 392.7f))
    val autoResizeBaseWidth: StateFlow<Float> = _autoResizeBaseWidth.asStateFlow()

    fun setAutoResizeEnabled(enabled: Boolean) {
        _isAutoResizeEnabled.value = enabled
        prefs.edit().putBoolean("key_autoresize_enabled", enabled).apply()
    }

    fun setAutoResizeFineTune(factor: Float) {
        _autoResizeFineTune.value = factor
        prefs.edit().putFloat("key_autoresize_fine_tune", factor).apply()
    }

    fun setAutoResizeBaseWidth(widthDp: Float) {
        _autoResizeBaseWidth.value = widthDp
        prefs.edit().putFloat("key_autoresize_base_width", widthDp).apply()
    }

    fun resetAutoResizeDefaults() {
        setAutoResizeEnabled(true)
        setAutoResizeFineTune(1.0f)
        setAutoResizeBaseWidth(392.7f)
    }

    // Service binding para gravação em primeiro plano
    private var telemetryService: TelemetryForegroundService? = null
    private val _isServiceBound = MutableStateFlow(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TelemetryForegroundService.LocalBinder
            telemetryService = binder?.getService()
            _isServiceBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            telemetryService = null
            _isServiceBound.value = false
        }
    }

    init {
        loadPairedDevices()
        bindTelemetryService()
        loadSavedDatalogs()

        // Encaminha amostras de telemetria para o gravador de serviço e buffer local
        viewModelScope.launch {
            scannerManager.telemetry.collect { telemetry ->
                onNewTelemetrySample(telemetry)
            }
        }
    }

    private fun onNewTelemetrySample(telemetry: LiveTelemetry) {
        synchronized(sessionBuffer) {
            if (sessionBuffer.size > 20000) {
                sessionBuffer.removeAt(0)
            }
            sessionBuffer.add(telemetry)
        }

        telemetryService?.logSample(telemetry)

        if (_isRecordingDatalogger.value && activeFileWriter != null) {
            try {
                val csvLine = formatTelemetryCsvLine(telemetry)
                activeFileWriter?.write(csvLine)
                activeFileWriter?.flush()
                _recordedDataloggerCount.value = _recordedDataloggerCount.value + 1
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        try {
            val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                val devices = adapter.bondedDevices?.toList() ?: emptyList()
                _pairedDevices.value = devices
                if (_selectedDevice.value == null && devices.isNotEmpty()) {
                    val obd = devices.find {
                        val name = (it.name ?: "").uppercase()
                        name.contains("OBD") || name.contains("ELM") || name.contains("SCAN")
                    }
                    _selectedDevice.value = obd ?: devices.first()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun selectDevice(device: BluetoothDevice) {
        _selectedDevice.value = device
    }

    fun startAutoScan() {
        scannerManager.startAutoScan(_selectedDevice.value)
    }

    fun disconnect() {
        scannerManager.disconnect()
        stopDataloggerRecording()
        telemetryService?.stopLoggingSession()
    }

    fun scanDtcs() {
        viewModelScope.launch {
            scannerManager.scanDtcs()
        }
    }

    fun clearDtcs() {
        viewModelScope.launch {
            _isClearing.value = true
            _clearDtcMessage.value = null
            val result = scannerManager.clearDtcs()
            result.onSuccess {
                _clearDtcMessage.value = "SUCESSO: $it"
            }.onFailure {
                _clearDtcMessage.value = "ERRO: ${it.message}"
            }
            _isClearing.value = false
        }
    }

    fun dismissClearMessage() {
        _clearDtcMessage.value = null
    }

    fun runAiDiagnosis() {
        viewModelScope.launch {
            _isAnalyzingAi.value = true
            try {
                val currentTelemetry = scannerManager.telemetry.value
                val profile = scannerManager.vehicleProfile.value
                val active = scannerManager.activeDtcs.value
                val history = scannerManager.historyDtcs.value

                val audits = AiDiagnosticEngine.auditComponents(currentTelemetry, profile)
                _componentAudits.value = audits

                val report = AiDiagnosticEngine.runDiagnosis(currentTelemetry, profile, active, history)
                _aiReport.value = report
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnalyzingAi.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // GRAVAÇÃO CONTÍNUA & EXPORTAÇÃO CSV DE DATALOGGER
    // -------------------------------------------------------------

    fun toggleDataloggerRecording() {
        if (_isRecordingDatalogger.value) {
            stopDataloggerRecording()
        } else {
            startDataloggerRecording()
        }
    }

    fun startDataloggerRecording() {
        if (_isRecordingDatalogger.value) return
        try {
            val logsDir = getDatalogsDirectory()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val file = File(logsDir, "AstraScan_Log_$timestamp.csv")

            activeRecordingFile = file
            activeFileWriter = FileWriter(file, true).apply {
                write(getCsvHeader())
                flush()
            }

            _recordedDataloggerCount.value = 0
            _isRecordingDatalogger.value = true
            _lastExportedFilePath.value = file.absolutePath
            _exportMessage.value = "Gravação iniciada: ${file.name}"

            telemetryService?.startLoggingSession()
        } catch (e: Exception) {
            _exportMessage.value = "Erro ao iniciar gravação: ${e.message}"
            e.printStackTrace()
        }
    }

    fun stopDataloggerRecording() {
        if (!_isRecordingDatalogger.value) return
        try {
            activeFileWriter?.flush()
            activeFileWriter?.close()
        } catch (_: Exception) {}
        activeFileWriter = null
        _isRecordingDatalogger.value = false

        val savedPath = activeRecordingFile?.absolutePath
        _lastExportedFilePath.value = savedPath
        _exportMessage.value = "Gravação finalizada e salva: ${activeRecordingFile?.name ?: "datalog.csv"}"

        telemetryService?.stopLoggingSession()
        loadSavedDatalogs()
    }

    /**
     * Salva a sessão atual completa como um arquivo .CSV no armazenamento do dispositivo com carimbo de data/hora
     */
    fun exportCurrentSessionCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logsDir = getDatalogsDirectory()
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val file = File(logsDir, "AstraScan_Sessao_$timestamp.csv")

                val samplesToExport: List<LiveTelemetry> = synchronized(sessionBuffer) {
                    if (sessionBuffer.isNotEmpty()) {
                        sessionBuffer.toList()
                    } else {
                        listOf(scannerManager.telemetry.value)
                    }
                }

                FileWriter(file, false).use { writer ->
                    writer.write(getCsvHeader())
                    for (sample in samplesToExport) {
                        writer.write(formatTelemetryCsvLine(sample))
                    }
                    writer.flush()
                }

                _lastExportedFilePath.value = file.absolutePath
                _exportMessage.value = "Sessão salva com sucesso: ${file.name} (${samplesToExport.size} registros)"
                loadSavedDatalogs()
            } catch (e: Exception) {
                _exportMessage.value = "Falha ao salvar sessão: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    fun loadSavedDatalogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logsDir = getDatalogsDirectory()
                val files = logsDir.listFiles { f -> f.extension.equals("csv", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                _savedDatalogsList.value = files
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteSavedDatalog(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    file.delete()
                    if (_lastExportedFilePath.value == file.absolutePath) {
                        _lastExportedFilePath.value = null
                    }
                    loadSavedDatalogs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissExportMessage() {
        _exportMessage.value = null
    }

    private fun getDatalogsDirectory(): File {
        val dir = File(getApplication<Application>().getExternalFilesDir(null), "datalogs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getCsvHeader(): String {
        return "Timestamp_ms,Data_Hora,RPM,Alvo_Lenta_RPM,Velocidade_kmh,Carga_Motor_pct,ECT_C,IAT_C,MAP_kPa,BARO_kPa,Vacuo_bar,MAF_gs,TPS_pct,TPS1_V,TPS2_V,Pedal_pct,TI_ms,DutyCycle_pct,Passos_IAC,Avanco_deg,Retardo_Knock_deg,Knock_Cil1_deg,Knock_Cil2_deg,Knock_Cil3_deg,Knock_Cil4_deg,Lambda1_V,Lambda2_V,Fator_Lambda,Status_Malha,STFT_pct,LTFT_pct,Razao_AF,Etanol_pct,Consumo_Lh,Consumo_kmL,EVAP_pct,Tensao_ECU_V,Tensao_ELM_V\n"
    }

    private fun formatTelemetryCsvLine(t: LiveTelemetry): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(t.timestampMs))
        return String.format(
            Locale.US,
            "%d,%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f,%.2f,%.1f,%.2f,%.2f,%.1f,%.3f,%.1f,%d,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%.3f,%.3f,%.3f,\"%s\",%.1f,%.1f,%.2f,%.1f,%.2f,%.1f,%.1f,%.1f,%.1f\n",
            t.timestampMs,
            dateStr,
            t.rpm,
            t.targetIdleRpm,
            t.speedKmh,
            t.engineLoadPercent,
            t.coolantTempC,
            t.intakeAirTempC,
            t.mapKpa,
            t.baroKpa,
            t.manifoldVacuumBar,
            t.estimatedMafGramsSec,
            t.throttlePercent,
            t.throttleVoltage1,
            t.throttleVoltage2,
            t.acceleratorPedalPercent,
            t.injectionTimeMs,
            t.injectorDutyCyclePercent,
            t.iacSteps,
            t.sparkAdvanceDeg,
            t.knockRetardDeg,
            t.knockCylinder1Deg,
            t.knockCylinder2Deg,
            t.knockCylinder3Deg,
            t.knockCylinder4Deg,
            t.lambdaVoltage,
            t.lambdaPostCatVoltage,
            t.equivalenceRatioLambda,
            t.fuelLoopStatus,
            t.shortTermFuelTrim,
            t.longTermFuelTrim,
            t.estimatedAfRatio,
            t.ethanolPercentage,
            t.instantConsumptionLitersHour,
            t.instantConsumptionKmPerLiter,
            t.evapPurgePercent,
            t.ecuBatteryVoltage,
            t.elmBatteryVoltage
        )
    }

    private fun bindTelemetryService() {
        val intent = Intent(getApplication(), TelemetryForegroundService::class.java)
        try {
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        stopDataloggerRecording()
        try {
            if (_isServiceBound.value) {
                getApplication<Application>().unbindService(serviceConnection)
            }
        } catch (_: Exception) {}
        scannerManager.disconnect()
        super.onCleared()
    }
}
