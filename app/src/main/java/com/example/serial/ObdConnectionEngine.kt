package com.example.serial

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.model.ProtocolType
import com.example.model.ScanState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * MÓDULO 1: GERENCIADOR DE CONEXÃO FÍSICA E ESTABILIDADE SERIAL
 * Responsabilidade única:
 * - Abertura resiliente de sockets Bluetooth RFCOMM/SPP com triplo fallback.
 * - Reset e parametrização segura de buffers do chip ELM327.
 * - Negociação progressiva de protocolos na Linha K e CAN (KWP2000 Fast/Slow, ISO 9141-2, CAN).
 * - Monitoramento contínuo (Watchdog Keep-Alive) para impedir timeout da ECU física.
 * - Fila thread-safe de comandos seriais com recuperação contra ruídos e quedas.
 */
class ObdConnectionEngine(
    private val scope: CoroutineScope
) {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "ObdConnectionEngine"
    }

    private val serialQueue = SerialCommunicationQueue()

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Pronto para conectar ao adaptador ELM327.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _detectedProtocol = MutableStateFlow(ProtocolType.UNKNOWN)
    val detectedProtocol: StateFlow<ProtocolType> = _detectedProtocol.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _batteryVoltage = MutableStateFlow(12.6f)
    val batteryVoltage: StateFlow<Float> = _batteryVoltage.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    private var activeSocket: BluetoothSocket? = null
    private var keepAliveJob: Job? = null
    private var lastTrafficTimestamp: Long = 0L

    fun logTerminal(msg: String) {
        val formatted = "[${System.currentTimeMillis() % 100000}] $msg"
        val current = _terminalLogs.value.toMutableList()
        if (current.size > 200) current.removeAt(0)
        current.add(formatted)
        _terminalLogs.value = current
        Log.d(TAG, msg)
    }

    fun updateScanState(state: ScanState, message: String) {
        _scanState.value = state
        _statusMessage.value = message
    }

    suspend fun sendCommand(cmd: String, timeoutMs: Long = 1800L): String {
        lastTrafficTimestamp = System.currentTimeMillis()
        return serialQueue.sendCommand(cmd, timeoutMs = timeoutMs)
    }

    /**
     * Executa a rotina completa de conexão física e handshake de protocolo.
     * Retorna true se a conexão física e o protocolo da ECU responderam com sucesso.
     */
    @SuppressLint("MissingPermission")
    suspend fun connectAndHandshake(device: BluetoothDevice): Boolean {
        updateScanState(ScanState.CONNECTING_BLUETOOTH, "Conectando ao adaptador ${device.name ?: device.address}...")
        logTerminal("Iniciando conexão física com: ${device.name} [${device.address}]")

        var connectedSocket: BluetoothSocket? = null
        try {
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            connectedSocket = s
            logTerminal("Socket Bluetooth SPP conectado via UUID padrão.")
        } catch (e1: Exception) {
            logTerminal("Tentativa 1 falhou (${e1.message}). Tentando canal RFCOMM 1 direto...")
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val s = method.invoke(device, 1) as BluetoothSocket
                s.connect()
                connectedSocket = s
                logTerminal("Socket conectado com sucesso via RFCOMM canal direto.")
            } catch (e2: Exception) {
                logTerminal("Tentativa 2 falhou (${e2.message}). Tentando socket inseguro...")
                try {
                    val s = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    s.connect()
                    connectedSocket = s
                    logTerminal("Socket conectado via Insecure SPP.")
                } catch (e3: Exception) {
                    throw IOException("Não foi possível conectar ao ELM327: ${e3.message}")
                }
            }
        }

        activeSocket = connectedSocket
        serialQueue.updateSocket(connectedSocket)
        _isConnected.value = true

        updateScanState(ScanState.RESETTING_ELM327, "Inicializando ELM327 e testando tensão...")
        logTerminal("Enviando reset AT Z e configurando parâmetros base...")

        val zResp = sendCommand("AT Z", timeoutMs = 2500L)
        logTerminal("< AT Z: $zResp")
        delay(400)

        sendCommand("AT D")
        sendCommand("AT E0")
        sendCommand("AT L0")
        sendCommand("AT S0")
        sendCommand("AT AT 1")
        sendCommand("AT CAF 1")

        val voltRaw = sendCommand("AT RV")
        logTerminal("< Tensão Bateria ELM (AT RV): $voltRaw")
        val parsedVolt = GmDataParsers.parseElmVoltage(voltRaw) ?: 12.6f
        _batteryVoltage.value = parsedVolt

        val protocolFound = runProtocolCascade()
        if (!protocolFound) {
            updateScanState(ScanState.ERROR, "A central não respondeu na Linha K / OBD-II. Verifique a ignição.")
            logTerminal("ERRO: Nenhum protocolo GM respondeu na linha física.")
            return false
        }

        startKeepAliveWorker()
        return true
    }

    /**
     * Cascata de protocolos especializada GM Brasil
     */
    private suspend fun runProtocolCascade(): Boolean {
        // Tentativa 1: Auto-detecção nativa ELM327 (AT SP 0)
        updateScanState(ScanState.TESTING_ISO_9141, "Testando auto-detecção de protocolo OBD-II...")
        logTerminal("Testando auto-detecção nativa ELM327 (AT SP 0)")

        sendCommand("AT SP 0")
        sendCommand("AT AR")
        sendCommand("AT CAF 1")
        val autoResp = sendCommand("01 00", timeoutMs = 3500L)
        logTerminal("< 01 00 (Auto SP 0): $autoResp")

        if (autoResp.contains("41 00")) {
            val dpResp = sendCommand("AT DP")
            logTerminal("< Protocolo detectado pelo ELM (AT DP): $dpResp")
            _detectedProtocol.value = when {
                dpResp.contains("CAN", true) -> ProtocolType.CAN_500K
                dpResp.contains("KWP", true) -> ProtocolType.KWP_FAST
                else -> ProtocolType.ISO_9141
            }
            return true
        }

        // Tentativa 2: KWP2000 Fast Init (AT SP 5) - Padrão Astra Linha K (Bosch / Delphi)
        updateScanState(ScanState.TESTING_KWP_FAST, "Testando KWP2000 Fast Init Linha K (Bosch / Delphi)...")
        logTerminal("Testando KWP2000 Fast Init (AT SP 5)")

        sendCommand("AT SP 5")
        sendCommand("AT IB 10")
        sendCommand("AT ST 64")
        sendCommand("AT SW 20")

        // 1. Inicia com header de 1 byte 81 11 F1 para o StartCommunication (81)
        sendCommand("AT SH 81 11 F1")
        var fastInitResp = sendCommand("81", timeoutMs = 2500L)
        logTerminal("< Init 81 (Header 11): $fastInitResp")

        if (fastInitResp.contains("C1") || (fastInitResp.contains("BUS INIT: OK") && !fastInitResp.contains("ERROR"))) {
            // Após inicialização 81, configura header para 2 bytes (82 11 F1)
            sendCommand("AT SH 82 11 F1")
            val verifyResp = sendCommand("01 00", timeoutMs = 2000L)
            logTerminal("< Verificação 01 00 (Header 11): $verifyResp")
            _detectedProtocol.value = ProtocolType.KWP_FAST
            logTerminal("SUCESSO: KWP2000 Fast Init confirmado (Header 11).")
            return true
        }

        // Tenta Header 10 alternativo
        sendCommand("AT SH 81 10 F1")
        fastInitResp = sendCommand("81", timeoutMs = 2500L)
        logTerminal("< Init 81 (Header 10): $fastInitResp")
        if (fastInitResp.contains("C1") || (fastInitResp.contains("BUS INIT: OK") && !fastInitResp.contains("ERROR"))) {
            sendCommand("AT SH 82 10 F1")
            _detectedProtocol.value = ProtocolType.KWP_FAST
            logTerminal("SUCESSO: KWP2000 Fast Init confirmado (Header 10).")
            return true
        }

        // Tentativa 3: KWP2000 5-Baud Slow Init (AT SP 4)
        updateScanState(ScanState.TESTING_KWP_SLOW, "Tentando KWP2000 Slow Init 5-Baud...")
        logTerminal("Testando KWP2000 Slow Init 5-Baud (AT SP 4)")

        sendCommand("AT SP 4")
        sendCommand("AT IIA 11")
        sendCommand("AT SH 81 11 F1")
        val slowInitResp = sendCommand("81", timeoutMs = 2500L)
        logTerminal("< Init 81 (Slow Header 11): $slowInitResp")

        if (slowInitResp.contains("C1") || (slowInitResp.contains("BUS INIT: OK") && !slowInitResp.contains("ERROR"))) {
            sendCommand("AT SH 82 11 F1")
            _detectedProtocol.value = ProtocolType.KWP_SLOW
            logTerminal("SUCESSO: KWP2000 Slow Init confirmado.")
            return true
        }

        // Tentativa 4: ISO 9141-2 GM (AT SP 3)
        sendCommand("AT SP 3")
        sendCommand("AT IIA 11")
        sendCommand("AT SH 68 6A F1")
        val iso9141Resp = sendCommand("01 00", timeoutMs = 2500L)
        logTerminal("< 01 00 (ISO 9141): $iso9141Resp")

        if (iso9141Resp.contains("41 00")) {
            _detectedProtocol.value = ProtocolType.ISO_9141
            logTerminal("SUCESSO: ISO 9141-2 confirmado.")
            return true
        }

        // Tentativa 5: CAN 11-bit 500k (Astra finais)
        updateScanState(ScanState.TESTING_CAN, "Testando CAN 11-bit 500k...")
        sendCommand("AT SP 6")
        sendCommand("AT SH 7E0")
        val canResp = sendCommand("01 00", timeoutMs = 2000L)
        logTerminal("< 01 00 (CAN): $canResp")

        if (canResp.contains("41 00")) {
            _detectedProtocol.value = ProtocolType.CAN_500K
            return true
        }

        return false
    }

    private fun startKeepAliveWorker() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(2000)
                val timeSinceLast = System.currentTimeMillis() - lastTrafficTimestamp
                if (timeSinceLast >= 2000 && _isConnected.value) {
                    try {
                        sendCommand("AT", timeoutMs = 500L)
                    } catch (e: Exception) {
                        logTerminal("Aviso: Falha no keep-alive serial.")
                    }
                }
            }
        }
    }

    fun disconnect() {
        keepAliveJob?.cancel()
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeSocket = null
        serialQueue.updateSocket(null)
        _isConnected.value = false
        _scanState.value = ScanState.IDLE
        _statusMessage.value = "Desconectado do adaptador."
        logTerminal("Conexão serial física encerrada.")
    }
}
