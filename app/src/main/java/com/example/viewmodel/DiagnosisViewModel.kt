package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AstraModule
import com.example.model.CommunicationBusHealth
import com.example.model.DtcCode
import com.example.model.EcuSensorProfile
import com.example.model.ElmChipBenchmark
import com.example.model.HeartbeatStatus
import com.example.model.LiveTelemetry
import com.example.model.ProtocolType
import com.example.model.ScanState
import com.example.model.SessionSensorPeaks
import com.example.model.TelemetrySamplingMode
import com.example.model.TimeoutCalibration
import com.example.model.VehicleProfile
import com.example.serial.AutoScannerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Status de alto nível da conexão OBD-II física
 */
enum class ConnectionStatus(val label: String) {
    DISCONNECTED("Desconectado"),
    CONNECTING_BLUETOOTH("Conectando socket Bluetooth SPP..."),
    CONFIGURING_ELM("Configurando buffer e parâmetros do ELM327..."),
    TESTING_PROTOCOLS("Negociando protocolo físico (Linha K / CAN)..."),
    IDENTIFYING_ECU("Identificando ECU física e sensores GM..."),
    CONNECTED("Conectado em Tempo Real"),
    ERROR("Falha na comunicação")
}

/**
 * Dados básicos e cadastrais da ECU detectada na comunicação real
 */
data class DetectedEcuData(
    val ecuName: String = "ECU Não Detectada",
    val engineCode: String = "Desconhecido",
    val protocol: String = "Desconhecido",
    val gmPartNumber: String = "",
    val hardwareNumber: String = "",
    val softwareVersion: String = "",
    val calId: String = "",
    val vin: String = "",
    val horsepower: String = "-",
    val fuelType: String = "-",
    val years: String = "-",
    val displacementLiters: Float = 2.0f,
    val batteryVoltage: Float = 12.6f,
    val isDetected: Boolean = false
)

/**
 * DiagnosisViewModel
 *
 * Gerencia o estado da conexão OBD2 real:
 * - Armazena o status atual da conexão e mensagens de progresso
 * - Armazena histórico e logs em tempo real das tentativas de conexão
 * - Armazena os dados básicos e avançados da ECU detectada fisicamente
 * - Executa a comunicação real com adaptadores ELM327 via Bluetooth RFCOMM/SPP
 * - Orquestra a telemetria ao vivo, leituras de PIDs e varredura de DTCs
 */
class DiagnosisViewModel(application: Application) : AndroidViewModel(application) {

    // Orquestrador de comunicação física real OBD-II
    val realScannerManager = AutoScannerManager(viewModelScope)

    // Status da conexão
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Pronto para iniciar comunicação real com o adaptador OBD2.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Logs de tentativa de conexão e tráfego serial em tempo real
    private val _connectionLogs = MutableStateFlow<List<String>>(emptyList())
    val connectionLogs: StateFlow<List<String>> = _connectionLogs.asStateFlow()

    // Dados básicos da ECU detectada
    private val _detectedEcuData = MutableStateFlow(DetectedEcuData())
    val detectedEcuData: StateFlow<DetectedEcuData> = _detectedEcuData.asStateFlow()

    // Dispositivos Bluetooth
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice.asStateFlow()

    // Estados expostos diretamente do motor de comunicação real
    val scanState: StateFlow<ScanState> = realScannerManager.scanState
    val vehicleProfile: StateFlow<VehicleProfile> = realScannerManager.vehicleProfile
    val sensorProfile: StateFlow<EcuSensorProfile?> = realScannerManager.sensorProfile
    val isInjectionDetected: StateFlow<Boolean> = realScannerManager.isInjectionDetected
    val telemetry: StateFlow<LiveTelemetry> = realScannerManager.telemetry
    val activeDtcs: StateFlow<List<DtcCode>> = realScannerManager.activeDtcs
    val historyDtcs: StateFlow<List<DtcCode>> = realScannerManager.historyDtcs
    val detectedProtocol: StateFlow<ProtocolType> = realScannerManager.detectedProtocol
    val isConnected: StateFlow<Boolean> = realScannerManager.isConnected
    val isPhysicalEcuConnected: StateFlow<Boolean> = realScannerManager.isPhysicalEcuConnected
    val isBluetoothConnected: StateFlow<Boolean> = realScannerManager.isBluetoothConnected
    val isManualProfileConfigured: StateFlow<Boolean> = realScannerManager.isManualProfileConfigured
    val heartbeatStatus: StateFlow<HeartbeatStatus> = realScannerManager.heartbeatStatus
    val elmBenchmark: StateFlow<ElmChipBenchmark> = realScannerManager.elmBenchmark
    val samplingMode: MutableStateFlow<TelemetrySamplingMode> = realScannerManager.samplingMode
    val batteryVoltage: StateFlow<Float> = realScannerManager.batteryVoltage
    val busHealth: StateFlow<CommunicationBusHealth> = realScannerManager.busHealth
    val sessionPeaks: StateFlow<SessionSensorPeaks> = realScannerManager.sessionPeaks

    fun resetSessionPeaks() {
        realScannerManager.resetSessionPeaks()
    }

    // Configurações Avançadas e Opções de Conexão
    val selectedModule: StateFlow<AstraModule> = realScannerManager.connectionEngine.selectedModule
    val timeoutCalibration: StateFlow<TimeoutCalibration> = realScannerManager.connectionEngine.timeoutCalibration
    val isFastBypassEnabled: StateFlow<Boolean> = realScannerManager.connectionEngine.isFastBypassEnabled
    val isAutoReconnectOnDropEnabled: StateFlow<Boolean> = realScannerManager.connectionEngine.isAutoReconnectOnDropEnabled
    val lastSuccessfulProfile: StateFlow<String?> = realScannerManager.connectionEngine.lastSuccessfulProfile

    init {
        // Observa alterações no ScanState real para atualizar ConnectionStatus e Logs
        viewModelScope.launch {
            realScannerManager.scanState.collect { state ->
                val newStatus = when (state) {
                    ScanState.IDLE -> ConnectionStatus.DISCONNECTED
                    ScanState.CONNECTING_BLUETOOTH -> ConnectionStatus.CONNECTING_BLUETOOTH
                    ScanState.RESETTING_ELM327 -> ConnectionStatus.CONFIGURING_ELM
                    ScanState.TESTING_KWP_FAST,
                    ScanState.TESTING_KWP_SLOW,
                    ScanState.TESTING_ISO_9141,
                    ScanState.TESTING_CAN -> ConnectionStatus.TESTING_PROTOCOLS
                    ScanState.READING_IDENTIFIERS,
                    ScanState.FINGERPRINTING -> ConnectionStatus.IDENTIFYING_ECU
                    ScanState.CONNECTED -> ConnectionStatus.CONNECTED
                    ScanState.ERROR -> ConnectionStatus.ERROR
                }
                _connectionStatus.value = newStatus
                _statusMessage.value = state.description
                addLog("[STATUS] ${state.description}")
            }
        }

        // Observa os logs seriais do motor de conexão física
        viewModelScope.launch {
            realScannerManager.terminalLogs.collect { logs ->
                _connectionLogs.value = logs
            }
        }

        // Observa o perfil do veículo e dados da ECU quando identificada
        viewModelScope.launch {
            combine(
                realScannerManager.vehicleProfile,
                realScannerManager.detectedProtocol,
                realScannerManager.isInjectionDetected,
                realScannerManager.connectionEngine.batteryVoltage
            ) { profile, protocol, isDetected, batteryVolt ->
                DetectedEcuData(
                    ecuName = profile.ecuName,
                    engineCode = profile.engineCode,
                    protocol = protocol.label,
                    gmPartNumber = profile.gmPartNumber,
                    hardwareNumber = profile.hardwareNumber,
                    softwareVersion = profile.softwareVersion,
                    calId = profile.calId,
                    vin = profile.vin,
                    horsepower = profile.horsepower,
                    fuelType = profile.fuelType,
                    years = profile.years,
                    displacementLiters = profile.displacementLiters,
                    batteryVoltage = batteryVolt,
                    isDetected = isDetected
                )
            }.collect { detectedData ->
                _detectedEcuData.value = detectedData
                if (detectedData.isDetected) {
                    addLog("[ECU DETECTADA] ${detectedData.ecuName} | Motor: ${detectedData.engineCode} | Protocolo: ${detectedData.protocol}")
                }
            }
        }

        loadPairedDevices()
    }

    /**
     * Adiciona um registro com carimbo de data/hora aos logs de conexão
     */
    fun addLog(entry: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formattedEntry = "[$time] $entry"
        val updated = _connectionLogs.value.toMutableList()
        if (updated.size > 300) updated.removeAt(0)
        updated.add(formattedEntry)
        _connectionLogs.value = updated
    }

    fun clearLogs() {
        _connectionLogs.value = emptyList()
    }

    /**
     * Carrega a lista de adaptadores Bluetooth pareados no sistema Android
     */
    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        try {
            val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            @Suppress("DEPRECATION")
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

            if (adapter == null || !adapter.isEnabled) {
                addLog("[AVISO] Bluetooth desligado ou indisponível no dispositivo.")
                _pairedDevices.value = emptyList()
                return
            }

            val bonded = adapter.bondedDevices?.toList() ?: emptyList()
            _pairedDevices.value = bonded
            addLog("[BLUETOOTH] ${bonded.size} dispositivo(s) pareado(s) encontrado(s).")

            // Auto-seleciona adaptador ELM327 / OBDII se ainda não selecionado
            if (_selectedDevice.value == null && bonded.isNotEmpty()) {
                val preferred = bonded.find { d ->
                    val name = (d.name ?: "").uppercase(Locale.ROOT)
                    name.contains("OBD") || name.contains("ELM") || name.contains("SCAN") || name.contains("KKL")
                } ?: bonded.first()
                selectDevice(preferred)
            }
        } catch (e: Exception) {
            addLog("[ERRO] Falha ao listar dispositivos Bluetooth: ${e.message}")
        }
    }

    /**
     * Define o adaptador Bluetooth alvo para a comunicação real
     */
    @SuppressLint("MissingPermission")
    fun selectDevice(device: BluetoothDevice) {
        _selectedDevice.value = device
        addLog("[SELEÇÃO] Adaptador selecionado: ${device.name ?: "Sem Nome"} (${device.address})")
    }

    // =========================================================================
    // COMUNICAÇÃO REAL COM O HARDWARE (ELM327 / ECU ASTRA)
    // =========================================================================

    /**
     * Inicia o ciclo de conexão real física com o adaptador e central (ECU)
     */
    fun startRealConnection(device: BluetoothDevice? = _selectedDevice.value) {
        val target = device ?: _selectedDevice.value
        if (target == null) {
            _statusMessage.value = "Nenhum adaptador selecionado. Selecione um dispositivo Bluetooth."
            addLog("[ERRO] Conexão cancelada: nenhum adaptador Bluetooth selecionado.")
            return
        }

        addLog("[COMUNICAÇÃO REAL] Iniciando conexão com ${target.name} [${target.address}]...")
        realScannerManager.startAutoScan(target)
    }

    /**
     * Encerra a conexão física com o adaptador e o socket Bluetooth
     */
    fun disconnectRealConnection() {
        addLog("[COMUNICAÇÃO REAL] Desconectando comunicação física...")
        realScannerManager.disconnect()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _statusMessage.value = "Desconectado do adaptador OBD2."
    }

    /**
     * Envia comando OBD-II ou AT diretamente pela linha física
     */
    suspend fun sendRealObdCommand(command: String, timeoutMs: Long = 1800L): String {
        return withContext(Dispatchers.IO) {
            addLog("[TX] $command")
            val resp = realScannerManager.sendCommand(command, timeoutMs)
            addLog("[RX] $resp")
            resp
        }
    }

    /**
     * Executa varredura de códigos de falha (DTC) na ECU física
     */
    fun scanRealFaultCodes() {
        viewModelScope.launch {
            addLog("[DTC] Solicitando códigos de falha físicos (Modo 03 / Modo 07)...")
            val result = realScannerManager.scanDtcs()
            result.onSuccess { codes ->
                addLog("[DTC] ${codes.size} código(s) de falha retornado(s) pela central.")
            }.onFailure { err ->
                addLog("[DTC ERRO] Falha ao ler códigos da ECU: ${err.message}")
            }
        }
    }

    /**
     * Limpa a memória de erros da central física (Modo 04 / KWP 14h)
     */
    fun clearRealFaultCodes(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            addLog("[DTC] Enviando comando de limpeza da memória de falhas da central...")
            val result = realScannerManager.clearDtcs()
            result.onSuccess { msg ->
                addLog("[DTC SUCESSO] $msg")
                onResult(true, msg)
            }.onFailure { err ->
                addLog("[DTC ERRO] Falha na limpeza: ${err.message}")
                onResult(false, err.message ?: "Erro desconhecido")
            }
        }
    }

    /**
     * Alterna o módulo alvo de diagnóstico no Astra (Motor, Painel MID, Câmbio, ABS, Airbag, Imobilizador)
     */
    fun selectModule(module: AstraModule) {
        viewModelScope.launch {
            addLog("[MÓDULO] Comutando barramento K-Line para ${module.title} (Target 0x${module.targetHex})...")
            val success = realScannerManager.connectionEngine.switchModule(module)
            if (success) {
                addLog("[MÓDULO SUCESSO] Canal estabelecido com ${module.title}.")
            } else {
                addLog("[MÓDULO AVISO] Sem resposta em 0x${module.targetHex}. Verifique se o veículo possui este opcional instalado.")
            }
        }
    }

    fun setTimeoutCalibration(timeout: TimeoutCalibration) {
        realScannerManager.connectionEngine.timeoutCalibration.value = timeout
        addLog("[CONFIG] Calibração de timeout ajustada para: ${timeout.label}")
    }

    fun setFastBypassEnabled(enabled: Boolean) {
        realScannerManager.connectionEngine.isFastBypassEnabled.value = enabled
        addLog("[CONFIG] Atalho de conexão rápida (Bypass): ${if (enabled) "Ativado" else "Desativado"}")
    }

    fun setAutoReconnectOnDropEnabled(enabled: Boolean) {
        realScannerManager.connectionEngine.isAutoReconnectOnDropEnabled.value = enabled
        addLog("[CONFIG] Auto-reconexão no arranque: ${if (enabled) "Ativada" else "Desativada"}")
    }

    /**
     * Define ou altera manualmente a central ECU alvo do Astra
     */
    fun selectEcuProfile(profile: VehicleProfile) {
        realScannerManager.selectEcuProfile(profile)
        addLog("[ECU SELEÇÃO MANUAL] Central definida para: ${profile.ecuName} | Motor: ${profile.engineCode}")
    }

    fun setSamplingMode(mode: TelemetrySamplingMode) {
        realScannerManager.samplingMode.value = mode
        addLog("[AMOSTRAGEM] Modo de leitura alterado para: ${mode.label}")
    }

    /**
     * Gera relatório técnico consolidado da oficina com diagnóstico completo do Astra
     */
    fun generateTechnicalReport(): String {
        val ecu = detectedEcuData.value
        val profile = vehicleProfile.value
        val bench = elmBenchmark.value
        val proto = detectedProtocol.value.label
        val volt = String.format(Locale.US, "%.1f V", realScannerManager.connectionEngine.batteryVoltage.value)
        val activeDtcList = activeDtcs.value
        val histDtcList = historyDtcs.value
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())

        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("   RELATÓRIO TÉCNICO - GM ASTRA BRASIL\n")
        sb.append("=========================================\n")
        sb.append("Data/Hora: $dateStr\n\n")

        sb.append("[1. IDENTIFICAÇÃO DO VEÍCULO & CENTRAL]\n")
        sb.append("- Modelo/Motor: ${profile.ecuName} (${profile.engineCode})\n")
        sb.append("- Cilindrada / Válvulas: ${profile.engineDisplacement} - ${profile.valveCount}\n")
        sb.append("- Potência / Combustível: ${profile.horsepower} | ${profile.fuelType}\n")
        sb.append("- Anos de Fabricação: ${profile.years}\n")
        sb.append("- GM Part Number: ${if (profile.gmPartNumber.isNotBlank()) profile.gmPartNumber else ecu.gmPartNumber}\n")
        sb.append("- CalID / Software: ${if (profile.calId.isNotBlank()) profile.calId else ecu.calId}\n")
        sb.append("- CVN (Checksum): ${if (profile.cvn.isNotBlank()) profile.cvn else "N/D"}\n")
        sb.append("- Chassi (VIN): ${if (profile.vin.isNotBlank()) profile.vin else ecu.vin}\n")
        if (profile.vin.length == 17) {
            val decoded = com.example.serial.GmDataParsers.decodeVin(profile.vin)
            if (decoded.isValid) {
                sb.append("  * Ano-Modelo Decodificado: ${decoded.modelYear}\n")
                sb.append("  * Planta Montadora: ${decoded.assemblyPlant}\n")
            }
        }
        sb.append("\n")

        sb.append("[2. BARRAMENTO SERIAL & HARDWARE ELM327]\n")
        sb.append("- Protocolo Ativo: $proto\n")
        sb.append("- Cabeçalho Físico Negociado: ${realScannerManager.connectionEngine.activeHeader}\n")
        sb.append("- Tensão da Bateria (Pino 16): $volt\n")
        sb.append("- Integridade do Barramento: ${busHealth.value.status} (${busHealth.value.healthPercent.toInt()}% sucesso | ${busHealth.value.totalRequests} reqs)\n")
        sb.append("- Adaptador ELM327: ${bench.versionString} [${bench.qualityTier.label}]\n")
        sb.append("- Certificação Linha K: ${if (bench.isKLineCertified) "Conforme (Pino 7)" else "Atenção (Clone)"}\n")
        sb.append("- Latência Média de Resposta: ${bench.latencyMs}ms\n\n")

        sb.append("[3. CÓDIGOS DE FALHA (DIAGNÓSTICO OBD2/KWP)]\n")
        if (activeDtcList.isEmpty() && histDtcList.isEmpty()) {
            sb.append("NENHUM CÓDIGO DE FALHA ATIVO OU GRAVADO NA ECU.\n")
        } else {
            if (activeDtcList.isNotEmpty()) {
                sb.append("FALHAS ATIVAS (PRESENTE):\n")
                activeDtcList.forEach { dtc ->
                    sb.append("  * ${dtc.code}: ${dtc.description} [${dtc.system}]\n")
                }
            }
            if (histDtcList.isNotEmpty()) {
                sb.append("FALHAS GRAVADAS NO HISTÓRICO:\n")
                histDtcList.forEach { dtc ->
                    sb.append("  * ${dtc.code}: ${dtc.description} [${dtc.system}]\n")
                }
            }
        }
        sb.append("\n")

        val currentTel = telemetry.value
        sb.append("[4. AUDITORIA DINÂMICA DA INJEÇÃO ELETRÔNICA]\n")
        sb.append("- Regime Operacional: ${currentTel.engineRegime.label}\n")
        sb.append("- Eficiência Volumétrica (VE): ${String.format(Locale.US, "%.1f", currentTel.volumetricEfficiencyPercent)}%\n")
        sb.append("- Dinâmica da Sonda Lambda: ${currentTel.lambdaHealthStatus} (${String.format(Locale.US, "%.1f", currentTel.lambdaFrequencyHz)} Hz)\n")
        val checks = com.example.serial.AstraIdleHealthChecker.evaluateDynamicRegimeSensors(currentTel, profile)
        checks.forEach { check ->
            val statusTag = if (check.isOk) "[OK]" else "[ATENÇÃO]"
            sb.append("  $statusTag ${check.sensorName}: ${check.currentValueStr} ${check.unit} (Faixa Esperada: ${check.nominalRange})\n")
            if (!check.isOk && check.issueDescription != null) {
                sb.append("    -> Causa Provável: ${check.issueDescription}\n")
            }
        }
        sb.append("\n")

        val peaks = sessionPeaks.value
        if (peaks.hasValidPeaks()) {
            sb.append("[5. EXTREMOS E PICOS REGISTRADOS NA SESSÃO]\n")
            sb.append("- Rotação Máxima: ${peaks.maxRpm.toInt()} RPM\n")
            sb.append("- Pressão MAP Mín/Máx: ${peaks.minMap.toInt()} / ${peaks.maxMap.toInt()} kPa\n")
            sb.append("- Temperatura ECT Máxima: ${peaks.maxEct.toInt()} °C\n")
            sb.append("- Abertura Máxima da Borboleta (TPS): ${String.format(Locale.US, "%.1f", peaks.maxTps)}%\n")
            sb.append("- Tensão da Bateria Mín/Máx: ${String.format(Locale.US, "%.1f", peaks.minVolt)}V / ${String.format(Locale.US, "%.1f", peaks.maxVolt)}V\n")
            sb.append("- Eficiência Volumétrica Pico: ${String.format(Locale.US, "%.1f", peaks.maxVePercent)}%\n")
            sb.append("\n")
        }

        sb.append("=========================================\n")
        sb.append("Gerado por AstraScanner OBD-II Brasil\n")
        return sb.toString()
    }
}
