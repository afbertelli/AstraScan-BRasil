package com.example.serial

import android.bluetooth.BluetoothDevice
import com.example.model.DtcCode
import com.example.model.LiveTelemetry
import com.example.model.ProtocolType
import com.example.model.EcuSensorProfile
import com.example.model.ScanState
import com.example.model.VehicleProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ORQUESTRADOR CENTRAL DE COMUNICAÇÃO OBD-II E DIAGNÓSTICO GM ASTRA
 *
 * Integra e coordena os dois módulos independentes e desacoplados:
 * - Módulo 1 (ObdConnectionEngine): Gerenciamento da conexão física, estabilidade serial,
 *   chip ELM327, cascata de protocolos e watchdog keep-alive em tempo real.
 * - Módulo 2 (EcuIdentificationEngine): Identificação assertiva de ECU física,
 *   leitura de descritores GM (PN/CalID/VIN), mapeamento de sensores, telemetria contínua e DTCs.
 */
class AutoScannerManager(
    private val scope: CoroutineScope
) {
    // MÓDULO 1: Conexão e Estabilidade Serial
    val connectionEngine = ObdConnectionEngine(scope)

    // MÓDULO 2: Identificação de ECU e Mapeamento de Sensores
    val identificationEngine = EcuIdentificationEngine(
        scope = scope,
        commandSender = { cmd, timeoutMs -> connectionEngine.sendCommand(cmd, timeoutMs) },
        logCallback = { msg -> connectionEngine.logTerminal(msg) }
    )

    // Delegação reativa de estados para compatibilidade direta com a UI e ViewModels
    val scanState: StateFlow<ScanState> = connectionEngine.scanState
    val statusMessage: StateFlow<String> = connectionEngine.statusMessage
    val detectedProtocol: StateFlow<ProtocolType> = connectionEngine.detectedProtocol
    val isConnected: StateFlow<Boolean> = connectionEngine.isConnected
    val terminalLogs: StateFlow<List<String>> = connectionEngine.terminalLogs

    val vehicleProfile: StateFlow<VehicleProfile> = identificationEngine.vehicleProfile
    val sensorProfile: StateFlow<EcuSensorProfile?> = identificationEngine.sensorProfile
    val isInjectionDetected: StateFlow<Boolean> = identificationEngine.isInjectionDetected
    val telemetry: StateFlow<LiveTelemetry> = identificationEngine.telemetry
    val activeDtcs: StateFlow<List<DtcCode>> = identificationEngine.activeDtcs
    val historyDtcs: StateFlow<List<DtcCode>> = identificationEngine.historyDtcs

    private var scanJob: Job? = null

    fun logTerminal(msg: String) {
        connectionEngine.logTerminal(msg)
    }

    /**
     * Executa o fluxo automatizado:
     * 1. Estabelece a conexão física e negocia o protocolo via Módulo 1.
     * 2. Se a central responder, aciona o Módulo 2 para identificar a ECU e iniciar a telemetria.
     */
    fun startAutoScan(device: BluetoothDevice?) {
        scanJob?.cancel()

        if (device == null) {
            connectionEngine.updateScanState(
                ScanState.ERROR,
                "Nenhum adaptador selecionado. Escolha seu ELM327 Bluetooth na lista."
            )
            logTerminal("ERRO: Nenhum dispositivo Bluetooth selecionado.")
            return
        }

        scanJob = scope.launch(Dispatchers.IO) {
            try {
                // Etapa 1: Módulo 1 - Conexão Física e Protocol Cascade
                val connected = connectionEngine.connectAndHandshake(device)
                if (!connected) {
                    return@launch
                }

                // Etapa 2: Módulo 2 - Identificação Assertiva da ECU e Sensores
                connectionEngine.updateScanState(
                    ScanState.READING_IDENTIFIERS,
                    "Lendo identificação da ECU do Astra..."
                )

                val matchedProfile = identificationEngine.identifyEcuAndSensors()

                connectionEngine.updateScanState(
                    ScanState.CONNECTED,
                    "Conectado em Tempo Real com a ECU (${matchedProfile.engineCode})!"
                )

                // Etapa 3: Módulo 2 - Início da Telemetria Contínua
                identificationEngine.startTelemetry(matchedProfile)

            } catch (e: Exception) {
                connectionEngine.updateScanState(
                    ScanState.ERROR,
                    "Falha no processo: ${e.message}"
                )
                logTerminal("Exceção no auto scanner: ${e.message}")
            }
        }
    }

    suspend fun scanDtcs(): Result<List<DtcCode>> {
        return identificationEngine.scanDtcs()
    }

    suspend fun clearDtcs(): Result<String> {
        return identificationEngine.clearDtcs()
    }

    suspend fun sendCommand(cmd: String, timeoutMs: Long = 1800L): String {
        return connectionEngine.sendCommand(cmd, timeoutMs)
    }

    fun disconnect() {
        scanJob?.cancel()
        identificationEngine.reset()
        connectionEngine.disconnect()
    }
}
