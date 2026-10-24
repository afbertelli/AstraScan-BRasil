package com.example.serial

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.model.AstraModule
import com.example.model.ElmChipBenchmark
import com.example.model.ElmQualityTier
import com.example.model.HeartbeatStatus
import com.example.model.ProtocolType
import com.example.model.ScanState
import com.example.model.TimeoutCalibration
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
 * Responsabilidade:
 * - Abertura resiliente de sockets Bluetooth RFCOMM/SPP com triplo fallback.
 * - Reset e parametrização segura de buffers do chip ELM327.
 * - Negociação progressiva de protocolos na Linha K e CAN (KWP2000 Fast/Slow, ISO 9141-2, CAN).
 * - Monitoramento contínuo Heartbeat (verificação de vida a cada 2s com desconexão automática).
 * - Resiliência a queda de tensão no arranque do motor (cranking drop).
 * - Suporte a múltiplos módulos Astra (Injeção, Painel MID, Câmbio Aisin, ABS, Airbag, Imobilizador).
 * - Atalho de conexão rápida (Bypass do último perfil de sucesso).
 * - Calibração de timeout adaptativo do chip ELM327 (AT ST).
 */
class ObdConnectionEngine(
    private val scope: CoroutineScope
) {
    companion object {
        const val HEARTBEAT_INTERVAL_MS = 2000L
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "ObdConnectionEngine"

        /**
         * Valida se a resposta serial da ECU ao comando de baixo custo confirma que ela está ativa.
         */
        fun validateHeartbeatResponse(resp: String): Boolean {
            val clean = resp.uppercase().trim()
            if (clean.isEmpty()) return false
            if (clean.contains("NO DATA") ||
                clean.contains("UNABLE TO CONNECT") ||
                clean.contains("BUS INIT: ... ERROR") ||
                clean.contains("CAN ERROR") ||
                clean.contains("STOPPED") ||
                clean.contains("FB ERROR") ||
                clean.contains("ERROR") ||
                clean == "?" ||
                clean.contains("?\r")
            ) {
                return false
            }
            return clean.contains("7E") ||
                   clean.contains("41 00") ||
                   clean.contains("41 0C") ||
                   clean.contains("61 02") ||
                   clean.contains("61 01") ||
                   clean.contains("41 ") ||
                   clean.contains("OK")
        }

        fun evaluateElmHardware(ati: String, at1: String, latencyMs: Long): ElmChipBenchmark {
            val upperAti = ati.uppercase()
            val upperAt1 = at1.uppercase()
            val isCloneV21 = upperAti.contains("V2.1") || upperAti.contains("2.1")
            val isV15 = upperAti.contains("V1.5") || upperAti.contains("1.5") || upperAti.contains("V1.4") || upperAti.contains("1.4")
            val hasPicSignature = upperAt1.contains("OBD") || upperAt1.contains("PIC") || upperAt1.contains("SCAN")

            val tier = when {
                isV15 && hasPicSignature -> ElmQualityTier.EXCELLENT_PIC18F
                isV15 -> ElmQualityTier.GOOD_COMPATIBLE
                isCloneV21 -> ElmQualityTier.QUESTIONABLE_CLONE
                upperAti.contains("ELM") -> ElmQualityTier.GOOD_COMPATIBLE
                else -> ElmQualityTier.UNSTABLE
            }

            val desc = when (tier) {
                ElmQualityTier.EXCELLENT_PIC18F -> "Chipset PIC18F25K80 de alta fidelidade com suporte completo à Linha K (ISO 14230/9141)."
                ElmQualityTier.GOOD_COMPATIBLE -> "Adaptador v1.5 compatível com os protocolos do GM Astra."
                ElmQualityTier.QUESTIONABLE_CLONE -> "Clone v2.1 de baixo custo. Pode apresentar perda de pacotes na Linha K (Pino 7)."
                ElmQualityTier.UNSTABLE -> "Resposta atípica do chip. Recomenda-se adaptador com chip PIC18F25K80."
                ElmQualityTier.UNKNOWN -> "Aguardando teste de hardware."
            }

            return ElmChipBenchmark(
                versionString = if (ati.isNotBlank()) ati else "ELM327",
                deviceDescription = if (at1.isNotBlank()) at1 else "Dispositivo Bluetooth",
                qualityTier = tier,
                isKLineCertified = tier != ElmQualityTier.UNSTABLE && tier != ElmQualityTier.QUESTIONABLE_CLONE,
                latencyMs = latencyMs,
                recommendationText = desc
            )
        }
    }

    private val serialQueue = SerialCommunicationQueue()

    private val _elmBenchmark = MutableStateFlow(ElmChipBenchmark())
    val elmBenchmark: StateFlow<ElmChipBenchmark> = _elmBenchmark.asStateFlow()

    private val _heartbeatStatus = MutableStateFlow(HeartbeatStatus())
    val heartbeatStatus: StateFlow<HeartbeatStatus> = _heartbeatStatus.asStateFlow()

    val isHeartbeatPaused = MutableStateFlow(false)

    fun pauseHeartbeat() {
        isHeartbeatPaused.value = true
    }

    fun resumeHeartbeat() {
        isHeartbeatPaused.value = false
    }

    var onHeartbeatFailed: (() -> Unit)? = null

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Pronto para conectar ao adaptador ELM327.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _detectedProtocol = MutableStateFlow(ProtocolType.UNKNOWN)
    val detectedProtocol: StateFlow<ProtocolType> = _detectedProtocol.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val _isPhysicalEcuConnected = MutableStateFlow(false)
    val isPhysicalEcuConnected: StateFlow<Boolean> = _isPhysicalEcuConnected.asStateFlow()

    private val _isManualProfileConfigured = MutableStateFlow(false)
    val isManualProfileConfigured: StateFlow<Boolean> = _isManualProfileConfigured.asStateFlow()

    private val _batteryVoltage = MutableStateFlow(12.6f)
    val batteryVoltage: StateFlow<Float> = _batteryVoltage.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    // Configurações Avançadas e Opções da Conexão
    val selectedModule = MutableStateFlow(AstraModule.ENGINE_PRIMARY)
    val timeoutCalibration = MutableStateFlow(TimeoutCalibration.BALANCED)
    val isFastBypassEnabled = MutableStateFlow(true)
    val isAutoReconnectOnDropEnabled = MutableStateFlow(true)
    val lastSuccessfulProfile = MutableStateFlow<String?>("PERFIL_1_KWP_FAST_11")

    private var activeSocket: BluetoothSocket? = null
    private var activeDevice: BluetoothDevice? = null
    private var keepAliveJob: Job? = null
    private var lastTrafficTimestamp: Long = 0L
    var activeHeader: String = "81 11 F1"
        private set
    var lastPhysicalEcuResponseTimestamp: Long = 0L
        private set

    fun notifyPhysicalDataReceived() {
        lastPhysicalEcuResponseTimestamp = System.currentTimeMillis()
        if (!_isPhysicalEcuConnected.value) {
            setPhysicalEcuConnected(true)
        }
    }

    fun logTerminal(msg: String) {
        val formatted = "[${System.currentTimeMillis() % 100000}] $msg"
        val current = _terminalLogs.value.toMutableList()
        if (current.size > 250) current.removeAt(0)
        current.add(formatted)
        _terminalLogs.value = current
        Log.d(TAG, msg)
    }

    fun updateScanState(state: ScanState, message: String) {
        _scanState.value = state
        _statusMessage.value = message
        if (state == ScanState.CONNECTED) {
            _isConnected.value = true
        } else if (state == ScanState.IDLE || state == ScanState.ERROR) {
            if (activeSocket == null || !activeSocket!!.isConnected) {
                _isConnected.value = false
                _isPhysicalEcuConnected.value = false
            }
        }
    }

    fun setPhysicalEcuConnected(connected: Boolean) {
        _isPhysicalEcuConnected.value = connected
        _isConnected.value = connected
        if (connected) {
            lastPhysicalEcuResponseTimestamp = System.currentTimeMillis()
            _isBluetoothConnected.value = true
        }
    }

    fun setManualProfileConfigured(profile: com.example.model.VehicleProfile) {
        _isManualProfileConfigured.value = true
        val hasBt = activeSocket != null && activeSocket!!.isConnected
        _isBluetoothConnected.value = hasBt
        _isPhysicalEcuConnected.value = false
        _isConnected.value = false
        if (hasBt) {
            updateScanState(
                ScanState.IDLE,
                "Perfil ${profile.ecuName} configurado. Adaptador Bluetooth pronto. Inicie a conexão para ler a ECU."
            )
        } else {
            updateScanState(
                ScanState.IDLE,
                "Perfil ${profile.ecuName} configurado. Adaptador Bluetooth desconectado (conecte ao carro para telemetria real)."
            )
        }
    }

    suspend fun sendCommand(cmd: String, timeoutMs: Long = 1800L): String {
        lastTrafficTimestamp = System.currentTimeMillis()
        return serialQueue.sendCommand(cmd, timeoutMs = timeoutMs)
    }

    /**
     * Executa a rotina completa de conexão física e handshake de protocolo.
     */
    @SuppressLint("MissingPermission")
    suspend fun connectAndHandshake(device: BluetoothDevice): Boolean {
        activeDevice = device
        updateScanState(ScanState.CONNECTING_BLUETOOTH, "Conectando ao adaptador ${device.name ?: device.address}...")
        logTerminal("Iniciando conexão física com: ${device.name} [${device.address}]")

        var connectedSocket: BluetoothSocket? = null
        try {
            @Suppress("DEPRECATION")
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
        _isBluetoothConnected.value = true
        _isPhysicalEcuConnected.value = false
        _isConnected.value = false

        updateScanState(ScanState.RESETTING_ELM327, "Adaptador conectado. Inicializando chip e testando bateria...")
        val stCmd = timeoutCalibration.value.atCommand
        logTerminal("Configurando parâmetros base ELM327 (AT Z, AT E0, AT L0, AT S1, AT H0, AT AT 1, AT KW 0, $stCmd)...")

        val zResp = sendCommand("AT Z", timeoutMs = 2500L)
        logTerminal("< AT Z: $zResp")
        delay(350)

        // Benchmark de Hardware e Certificação de Chipset ELM327 (PIC18F25K80 vs Clones)
        val atiResp = sendCommand("ATI", timeoutMs = 800L).trim()
        val at1Resp = sendCommand("AT @1", timeoutMs = 600L).trim()
        val benchStart = System.currentTimeMillis()
        sendCommand("AT I", timeoutMs = 600L)
        val benchLatency = (System.currentTimeMillis() - benchStart).coerceAtLeast(1L)
        val benchmark = evaluateElmHardware(atiResp, at1Resp, benchLatency)
        _elmBenchmark.value = benchmark
        logTerminal("AUDITORIA ELM327: ${benchmark.versionString} [${benchmark.qualityTier.label}] - Latência: ${benchLatency}ms")

        sendCommand("AT E0", timeoutMs = 600L)
        sendCommand("AT L0", timeoutMs = 600L)
        sendCommand("AT S1", timeoutMs = 600L)
        sendCommand("AT H0", timeoutMs = 600L) // Headers OFF: respostas 41 XX limpas para parse direto
        sendCommand("AT AT 1", timeoutMs = 600L) // Timing Adaptativo
        sendCommand("AT KW 0", timeoutMs = 600L) // Desativa checagem estrita de keywords para clones
        sendCommand(stCmd, timeoutMs = 600L)

        val voltRaw = sendCommand("AT RV", timeoutMs = 800L)
        logTerminal("< Tensão Bateria ELM (AT RV): $voltRaw")
        val parsedVolt = GmDataParsers.parseElmVoltage(voltRaw) ?: 12.6f
        _batteryVoltage.value = parsedVolt

        val protocolFound = runProtocolCascade()
        if (!protocolFound) {
            _isPhysicalEcuConnected.value = false
            _isConnected.value = false
            updateScanState(ScanState.ERROR, "Adaptador conectado, mas a ECU física não respondeu. Ligue a ignição do carro.")
            logTerminal("ERRO: Nenhum perfil de protocolo GM respondeu na linha física.")
            return false
        }

        _isPhysicalEcuConnected.value = true
        _isConnected.value = true
        updateScanState(ScanState.CONNECTED, "Conectado em tempo real com a ECU física do veículo!")
        startKeepAliveWorker()
        return true
    }

    /**
     * Aplica rotina de recuperação e cooldown da Linha K física (Pino 7).
     * Fecha protocolo anterior (AT PC) sem reiniciar o chip ELM para evitar travamentos.
     */
    private suspend fun aplicarRecuperacaoLinhaK() {
        logTerminal("Recuperação Linha K: Fechando sessão anterior (AT PC)...")
        try {
            sendCommand("AT PC", timeoutMs = 800L)
            delay(150)
            sendCommand("AT E0", timeoutMs = 400L)
            sendCommand("AT L0", timeoutMs = 400L)
            sendCommand("AT H0", timeoutMs = 400L)
        } catch (e: Exception) {
            logTerminal("Aviso na rotina de recuperação: ${e.message}")
        }
    }

    /**
     * Diagnostica erros típicos de hardware do chip ELM327
     */
    private fun diagnosticarRespostaELM(resposta: String) {
        when {
            resposta.contains("FB ERROR", ignoreCase = true) -> {
                logTerminal("ALERTA DE HARDWARE: 'FB ERROR' detectado! Linha K (Pino 7) com ruído, curto ou ignição do carro desligada.")
            }
            resposta.contains("BUS INIT: ERROR", ignoreCase = true) -> {
                logTerminal("DIAGNÓSTICO: 'BUS INIT: ERROR' - A central não aceitou a inicialização neste endereço.")
            }
            resposta.contains("UNABLE TO CONNECT", ignoreCase = true) -> {
                logTerminal("DIAGNÓSTICO: 'UNABLE TO CONNECT' - Impossível conectar com a taxa de baud/protocolo selecionado.")
            }
            resposta.contains("?", ignoreCase = true) || resposta.contains("ERROR", ignoreCase = true) -> {
                logTerminal("AVISO DE CHIP: Comando rejeitado ('?' ou 'ERROR'). Verifique se o adaptador é um clone ELM327 v2.1 instável.")
            }
        }
    }

    /**
     * Valida se a central física respondeu com dados reais após a inicialização do barramento
     */
    /**
     * Valida se a central física respondeu com dados reais após a inicialização do barramento.
     * Suporta múltiplas camadas de detecção: 01 00 (PIDs suportados), 01 0C (RPM direto),
     * 01 05 (ECT temperatura), 21 01 (KWP GM) e 1A 90 (ID física GM) para garantir conexão com ECUs antigas.
     */
    private suspend fun validarRespostaCentral(): Boolean {
        // Camada 1: Modo SAE J1979 padrão OBD2 (01 00)
        val resp00 = sendCommand("01 00", timeoutMs = 2000L)
        logTerminal("< Teste 01 00 (SAE J1979): $resp00")
        if (resp00.contains("41 00") || resp00.contains("41 0C") || resp00.contains("41 05")) {
            logTerminal("SUCESSO: Central física respondeu no modo OBD-II (01 00).")
            return true
        }

        // Camada 2: Consulta direta de RPM (01 0C) - muitas centrais antigas respondem 01 0C mesmo ignorando 01 00
        val resp0C = sendCommand("01 0C", timeoutMs = 1500L)
        logTerminal("< Teste 01 0C (RPM direto): $resp0C")
        if (resp0C.contains("41 0C")) {
            logTerminal("SUCESSO: Central física respondeu com RPM ativo (01 0C).")
            return true
        }

        // Camada 3: Consulta direta de Temperatura de Água (01 05) - universal em injeções GM
        val resp05 = sendCommand("01 05", timeoutMs = 1500L)
        logTerminal("< Teste 01 05 (ECT Temperatura): $resp05")
        if (resp05.contains("41 05")) {
            logTerminal("SUCESSO: Central física respondeu com Temperatura (01 05).")
            return true
        }

        // Camada 4: Modo GM KWP proprietário (21 01)
        val respKwp = sendCommand("21 01", timeoutMs = 1500L)
        logTerminal("< Teste 21 01 (GM KWP): $respKwp")
        if (respKwp.contains("61 01") || (respKwp.length > 5 && !GmDataParsers.isNegativeResponse(respKwp))) {
            logTerminal("SUCESSO: Central física respondeu no modo KWP proprietário GM (21 01).")
            return true
        }

        // Camada 5: Leitura de Identificador de Hardware GM (1A 90)
        val respId = sendCommand("1A 90", timeoutMs = 1500L)
        logTerminal("< Teste 1A 90 (ID Central GM): $respId")
        if (respId.contains("5A 90") || (respId.contains("5A") && !GmDataParsers.isNegativeResponse(respId))) {
            logTerminal("SUCESSO: Central física respondeu com identificador GM (1A 90).")
            return true
        }

        return false
    }

    /**
     * Camada 1: KWP2000 Fast Init - Target 0x11 (Delphi Multec H / MT27E Flexpower)
     */
    private suspend fun testProfile1(stCmd: String): Boolean {
        updateScanState(ScanState.TESTING_KWP_FAST, "Camada 1: KWP2000 Fast Init (Target 0x11 - Delphi Multec H)...")
        logTerminal("Iniciando Camada 1: KWP2000 Fast Init (AT SP 5, AT SH 81 11 F1, AT WM 81 11 F1 3E, AT IB 10)")
        sendCommand("AT SP 5", timeoutMs = 600L)
        sendCommand("AT SH 81 11 F1", timeoutMs = 600L)
        sendCommand("AT WM 81 11 F1 3E", timeoutMs = 400L)
        sendCommand("AT IB 10", timeoutMs = 400L)
        sendCommand("AT AT 1", timeoutMs = 400L)
        sendCommand("AT KW 0", timeoutMs = 400L)
        sendCommand(stCmd, timeoutMs = 400L)
        
        val resp01 = sendCommand("01 00", timeoutMs = 2800L)
        logTerminal("< 01 00 (Camada 1 KWP Fast 11): $resp01")
        diagnosticarRespostaELM(resp01)
        if (resp01.contains("41 00") || resp01.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "81 11 F1"
            _detectedProtocol.value = ProtocolType.KWP_FAST
            logTerminal("SUCESSO: Camada 1 conectada (KWP Fast Init - Delphi Multec H).")
            return true
        }
        return false
    }

    /**
     * Camada 2: KWP2000 Fast Init - Target 0x10 (Bosch Motronic ME 7.9.6 / ME 7.9.9 Flexpower)
     */
    private suspend fun testProfile2(stCmd: String): Boolean {
        updateScanState(ScanState.TESTING_KWP_FAST, "Camada 2: KWP2000 Fast Init (Target 0x10 - Bosch ME 7.9.6)...")
        logTerminal("Iniciando Camada 2: KWP2000 Fast Init (AT SP 5, AT SH 81 10 F1, AT WM 81 10 F1 3E, AT IB 10)")
        sendCommand("AT SP 5", timeoutMs = 600L)
        sendCommand("AT SH 81 10 F1", timeoutMs = 600L)
        sendCommand("AT WM 81 10 F1 3E", timeoutMs = 400L)
        sendCommand("AT IB 10", timeoutMs = 400L)
        sendCommand("AT AT 1", timeoutMs = 400L)
        sendCommand("AT KW 0", timeoutMs = 400L)
        sendCommand(stCmd, timeoutMs = 400L)
        
        val resp = sendCommand("01 00", timeoutMs = 2800L)
        logTerminal("< 01 00 (Camada 2 KWP Fast 10): $resp")
        diagnosticarRespostaELM(resp)
        if (resp.contains("41 00") || resp.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "81 10 F1"
            _detectedProtocol.value = ProtocolType.KWP_FAST
            logTerminal("SUCESSO: Camada 2 conectada (KWP Fast Init - Bosch ME 7.9.6).")
            return true
        }
        return false
    }

    /**
     * Camada 3: KWP2000 Fast Init - Funcional Padrão 0x33 (ISO 14230-4 Broadcast)
     */
    private suspend fun testProfile3(stCmd: String): Boolean {
        updateScanState(ScanState.TESTING_KWP_FAST, "Camada 3: KWP2000 Fast Init (Padrão ISO 14230-4 0x33)...")
        logTerminal("Iniciando Camada 3: KWP2000 Fast Init (AT SP 5, AT SH 81 33 F1, AT IB 10)")
        sendCommand("AT SP 5", timeoutMs = 600L)
        sendCommand("AT SH 81 33 F1", timeoutMs = 600L)
        sendCommand("AT IB 10", timeoutMs = 400L)
        sendCommand("AT AT 1", timeoutMs = 400L)
        sendCommand(stCmd, timeoutMs = 400L)
        val resp = sendCommand("01 00", timeoutMs = 2800L)
        logTerminal("< 01 00 (Camada 3 KWP Fast 33): $resp")
        diagnosticarRespostaELM(resp)
        if (resp.contains("41 00") || resp.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "81 33 F1"
            _detectedProtocol.value = ProtocolType.KWP_FAST
            logTerminal("SUCESSO: Camada 3 conectada (KWP Fast Init - Padrão ISO 14230-4).")
            return true
        }
        return false
    }

    /**
     * Camada 4: KWP2000 Slow Init 5-Baud - Target 0x11 (Astra Delphi Multec F / Multec M)
     */
    private suspend fun testProfile4(stCmd: String): Boolean {
        updateScanState(ScanState.TESTING_KWP_SLOW, "Camada 4: KWP2000 Slow Init 5-Baud (Target 0x11 - Multec Antiga)...")
        logTerminal("Iniciando Camada 4: KWP2000 Slow Init 5-Baud (AT SP 4, AT IIA 11, AT SH 81 11 F1, AT IB 10)")
        sendCommand("AT SP 4", timeoutMs = 600L)
        sendCommand("AT IIA 11", timeoutMs = 600L)
        sendCommand("AT SH 81 11 F1", timeoutMs = 600L)
        sendCommand("AT IB 10", timeoutMs = 400L)
        sendCommand("AT AT 1", timeoutMs = 400L)
        sendCommand(stCmd, timeoutMs = 400L)
        val resp = sendCommand("01 00", timeoutMs = 3200L)
        logTerminal("< 01 00 (Camada 4 KWP Slow 11): $resp")
        diagnosticarRespostaELM(resp)
        if (resp.contains("41 00") || resp.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "81 11 F1"
            _detectedProtocol.value = ProtocolType.KWP_SLOW
            logTerminal("SUCESSO: Camada 4 conectada (KWP Slow Init - Multec Antiga).")
            return true
        }
        return false
    }

    /**
     * Camada 5: KWP2000 Slow Init 5-Baud - Target 0x10 (Astra Bosch ME 7.9.6 Gasolina 2003-2004)
     */
    private suspend fun testProfile5(stCmd: String): Boolean {
        updateScanState(ScanState.TESTING_KWP_SLOW, "Camada 5: KWP2000 Slow Init 5-Baud (Target 0x10 - Bosch Gasolina)...")
        logTerminal("Iniciando Camada 5: KWP2000 Slow Init 5-Baud (AT SP 4, AT IIA 10, AT SH 81 10 F1, AT IB 10)")
        sendCommand("AT SP 4", timeoutMs = 600L)
        sendCommand("AT IIA 10", timeoutMs = 600L)
        sendCommand("AT SH 81 10 F1", timeoutMs = 600L)
        sendCommand("AT IB 10", timeoutMs = 400L)
        sendCommand("AT AT 1", timeoutMs = 400L)
        sendCommand(stCmd, timeoutMs = 400L)
        val resp = sendCommand("01 00", timeoutMs = 3200L)
        logTerminal("< 01 00 (Camada 5 KWP Slow 10): $resp")
        diagnosticarRespostaELM(resp)
        if (resp.contains("41 00") || resp.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "81 10 F1"
            _detectedProtocol.value = ProtocolType.KWP_SLOW
            logTerminal("SUCESSO: Camada 5 conectada (KWP Slow Init - Bosch Gasolina).")
            return true
        }
        return false
    }

    /**
     * Camada 6: KWP2000 Slow Init 5-Baud - Target 0x01 (Astra 2.0 16V Bosch Motronic M 1.5.5)
     */
    private suspend fun testProfile6(stCmd: String): Boolean {
        updateScanState(ScanState.TESTING_KWP_SLOW, "Camada 6: KWP2000 Slow Init 5-Baud (Target 0x01 - Bosch M 1.5.5 16V)...")
        logTerminal("Iniciando Camada 6: KWP2000 Slow Init 5-Baud (AT SP 4, AT IIA 01, AT SH 81 01 F1, AT IB 10)")
        sendCommand("AT SP 4", timeoutMs = 600L)
        sendCommand("AT IIA 01", timeoutMs = 600L)
        sendCommand("AT SH 81 01 F1", timeoutMs = 600L)
        sendCommand("AT IB 10", timeoutMs = 400L)
        sendCommand("AT AT 1", timeoutMs = 400L)
        sendCommand(stCmd, timeoutMs = 400L)
        val resp = sendCommand("01 00", timeoutMs = 3200L)
        logTerminal("< 01 00 (Camada 6 KWP Slow 01): $resp")
        diagnosticarRespostaELM(resp)
        if (resp.contains("41 00") || resp.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "81 01 F1"
            _detectedProtocol.value = ProtocolType.KWP_SLOW
            logTerminal("SUCESSO: Camada 6 conectada (KWP Slow Init - Bosch Motronic M 1.5.5 16V).")
            return true
        }
        return false
    }

    /**
     * Camada 7: ISO 9141-2 Standard GM SAE J1979 (Astra 1998-2003 Gasolina OBD-II)
     */
    private suspend fun testProfile7(): Boolean {
        updateScanState(ScanState.TESTING_ISO_9141, "Camada 7: ISO 9141-2 Padrão SAE (Header 68 6A F1)...")
        logTerminal("Iniciando Camada 7: ISO 9141-2 Padrão SAE (AT SP 3, AT SH 68 6A F1, AT IIA 33, AT IB 10)")
        sendCommand("AT SP 3", timeoutMs = 600L)
        sendCommand("AT SH 68 6A F1", timeoutMs = 600L)
        sendCommand("AT IIA 33", timeoutMs = 400L)
        sendCommand("AT IB 10", timeoutMs = 400L)
        val resp = sendCommand("01 00", timeoutMs = 3200L)
        logTerminal("< 01 00 (Camada 7 ISO 9141 SAE): $resp")
        diagnosticarRespostaELM(resp)
        if (resp.contains("41 00") || resp.contains("41 0C") || resp.contains("41 05") || validarRespostaCentral()) {
            activeHeader = "68 6A F1"
            _detectedProtocol.value = ProtocolType.ISO_9141
            logTerminal("SUCESSO: Camada 7 conectada (ISO 9141-2 SAE).")
            return true
        }
        return false
    }

    /**
     * Camada 8: ISO 9141-2 Direto GM Engine 0x11 (Astra Primeiras Séries e Astra Belga)
     */
    private suspend fun testProfile8(): Boolean {
        updateScanState(ScanState.TESTING_ISO_9141, "Camada 8: ISO 9141-2 Direto GM (Target 0x11)...")
        logTerminal("Iniciando Camada 8: ISO 9141-2 Direto GM (AT SP 3, AT SH 68 11 F1, AT IIA 11)")
        sendCommand("AT SP 3", timeoutMs = 600L)
        sendCommand("AT SH 68 11 F1", timeoutMs = 600L)
        sendCommand("AT IIA 11", timeoutMs = 400L)
        val resp = sendCommand("01 00", timeoutMs = 3200L)
        logTerminal("< 01 00 (Camada 8 ISO 9141 Direto): $resp")
        diagnosticarRespostaELM(resp)
        if (resp.contains("41 00") || resp.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "68 11 F1"
            _detectedProtocol.value = ProtocolType.ISO_9141
            logTerminal("SUCESSO: Camada 8 conectada (ISO 9141-2 Direto GM).")
            return true
        }
        return false
    }

    /**
     * Camada 9: CAN 11-bit 500k (Astra Flexpower 2009-2011 "Locomotiva" 140cv)
     */
    private suspend fun testProfile9(): Boolean {
        updateScanState(ScanState.TESTING_CAN, "Camada 9: CAN 11-bit 500k (Astra 2009-2011)...")
        logTerminal("Iniciando Camada 9: CAN 11-bit 500k (AT SP 6, AT SH 7E0, AT CRA 7E8)")
        sendCommand("AT SP 6", timeoutMs = 600L)
        sendCommand("AT SH 7E0", timeoutMs = 600L)
        sendCommand("AT CRA 7E8", timeoutMs = 400L)
        val canResp = sendCommand("01 00", timeoutMs = 2000L)
        logTerminal("< 01 00 (Camada 9 CAN 11-bit): $canResp")
        if (canResp.contains("41 00") || canResp.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "7E0"
            _detectedProtocol.value = ProtocolType.CAN_500K
            logTerminal("SUCESSO: Camada 9 conectada (CAN 11-bit 500k).")
            return true
        }
        return false
    }

    /**
     * Camada 10: CAN 29-bit 500k (Barramento GM Global)
     */
    private suspend fun testProfile10(): Boolean {
        updateScanState(ScanState.TESTING_CAN, "Camada 10: CAN 29-bit 500k (Padrão GM Estendido)...")
        logTerminal("Iniciando Camada 10: CAN 29-bit 500k (AT SP 7, AT SH 18DB33F1)")
        sendCommand("AT SP 7", timeoutMs = 600L)
        sendCommand("AT SH 18DB33F1", timeoutMs = 600L)
        val canResp = sendCommand("01 00", timeoutMs = 2000L)
        logTerminal("< 01 00 (Camada 10 CAN 29-bit): $canResp")
        if (canResp.contains("41 00") || canResp.contains("41 0C") || validarRespostaCentral()) {
            activeHeader = "18DB33F1"
            _detectedProtocol.value = ProtocolType.CAN_500K
            logTerminal("SUCESSO: Camada 10 conectada (CAN 29-bit 500k).")
            return true
        }
        return false
    }

    /**
     * Cascata de protocolos inteligente GM Astra Brasil (10 Camadas de Compatibilidade):
     * 1. Atalho de perfil memorizado
     * 2. Auto-busca universal ELM327 (AT SP 0)
     * 3 a 10. Camadas progressivas cobrindo todos os anos (1998 a 2011) e motores (8V, 16V, Flexpower e Gasolina)
     */
    private suspend fun runProtocolCascade(): Boolean {
        val stCmd = timeoutCalibration.value.atCommand

        // 1. ATALHO DE CONEXÃO RÁPIDA (BYPASS): Tenta primeiro o último perfil memorizado
        if (isFastBypassEnabled.value && lastSuccessfulProfile.value != null) {
            val saved = lastSuccessfulProfile.value
            logTerminal("CONEXÃO RÁPIDA: Testando perfil memorizado ($saved)...")
            val quickSuccess = when (saved) {
                "PERFIL_1_KWP_FAST_11" -> testProfile1(stCmd)
                "PERFIL_2_KWP_FAST_10" -> testProfile2(stCmd)
                "PERFIL_3_KWP_FAST_33" -> testProfile3(stCmd)
                "PERFIL_4_KWP_SLOW_11" -> testProfile4(stCmd)
                "PERFIL_5_KWP_SLOW_10" -> testProfile5(stCmd)
                "PERFIL_6_KWP_SLOW_01" -> testProfile6(stCmd)
                "PERFIL_7_ISO_9141_SAE" -> testProfile7()
                "PERFIL_8_ISO_9141_GM" -> testProfile8()
                "PERFIL_9_CAN_500K_11" -> testProfile9()
                "PERFIL_10_CAN_500K_29" -> testProfile10()
                else -> false
            }
            if (quickSuccess) {
                logTerminal("CONEXÃO RÁPIDA BEM-SUCEDIDA! Tempo de conexão minimizado.")
                return true
            } else {
                logTerminal("Atalho falhou. Aplicando recuperação e iniciando varredura de 10 camadas...")
                aplicarRecuperacaoLinhaK()
            }
        }

        // 2. BUSCA AUTOMÁTICA UNIVERSAL DO ELM327 (AT SP 0)
        updateScanState(ScanState.TESTING_KWP_FAST, "Executando auto-busca de protocolo no adaptador...")
        logTerminal("Tentando Auto Protocol Search (AT SP 0 com 01 00)...")
        sendCommand("AT SP 0", timeoutMs = 600L)
        val autoResp = sendCommand("01 00", timeoutMs = 3800L)
        logTerminal("< 01 00 (AT SP 0 Auto): $autoResp")
        if (autoResp.contains("41 00") || autoResp.contains("41 0C") || autoResp.contains("61 01") || validarRespostaCentral()) {
            val dpResp = sendCommand("AT DP", timeoutMs = 800L).uppercase()
            logTerminal("< Protocolo Detectado pelo ELM327: $dpResp")
            _detectedProtocol.value = when {
                dpResp.contains("CAN") -> {
                    activeHeader = "7E0"
                    ProtocolType.CAN_500K
                }
                dpResp.contains("FAST") || dpResp.contains("14230") -> {
                    activeHeader = "81 11 F1"
                    ProtocolType.KWP_FAST
                }
                dpResp.contains("9141") -> {
                    activeHeader = "68 6A F1"
                    ProtocolType.ISO_9141
                }
                else -> {
                    activeHeader = "81 11 F1"
                    ProtocolType.KWP_SLOW
                }
            }
            lastSuccessfulProfile.value = "PERFIL_1_KWP_FAST_11"
            return true
        }

        aplicarRecuperacaoLinhaK()

        // 3. CASCATA ESPECIALIZADA DE 10 CAMADAS GM ASTRA BRASIL
        if (testProfile1(stCmd)) {
            lastSuccessfulProfile.value = "PERFIL_1_KWP_FAST_11"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile2(stCmd)) {
            lastSuccessfulProfile.value = "PERFIL_2_KWP_FAST_10"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile3(stCmd)) {
            lastSuccessfulProfile.value = "PERFIL_3_KWP_FAST_33"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile4(stCmd)) {
            lastSuccessfulProfile.value = "PERFIL_4_KWP_SLOW_11"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile5(stCmd)) {
            lastSuccessfulProfile.value = "PERFIL_5_KWP_SLOW_10"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile6(stCmd)) {
            lastSuccessfulProfile.value = "PERFIL_6_KWP_SLOW_01"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile7()) {
            lastSuccessfulProfile.value = "PERFIL_7_ISO_9141_SAE"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile8()) {
            lastSuccessfulProfile.value = "PERFIL_8_ISO_9141_GM"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile9()) {
            lastSuccessfulProfile.value = "PERFIL_9_CAN_500K_11"
            return true
        }
        aplicarRecuperacaoLinhaK()

        if (testProfile10()) {
            lastSuccessfulProfile.value = "PERFIL_10_CAN_500K_29"
            return true
        }

        return false
    }

    /**
     * Comuta o endereçamento da Linha K para comunicar com outro módulo do Astra (ABS, Airbag, MID, Câmbio)
     */
    suspend fun switchModule(module: AstraModule): Boolean {
        selectedModule.value = module
        logTerminal("Comutando barramento de diagnóstico para: ${module.title} (Target 0x${module.targetHex})")
        sendCommand("AT SH ${module.headerKwp}")
        val resp = sendCommand("1A 90", timeoutMs = 1500L)
        logTerminal("< Resposta do módulo: $resp")
        return !resp.contains("ERROR") && !resp.contains("NO DATA")
    }

    /**
     * Executa um pulso de 'Heartbeat' (verificação de vida) na ECU a cada 2 segundos.
     * Envia um comando de baixíssimo custo (Tester Present "3E" ou Supported PIDs "01 00")
     * para garantir que a ECU física ainda está ativa e responsiva.
     * Retorna true se a central responder com confirmação positiva; false caso falhe.
     */
    suspend fun sendHeartbeatPulse(): Boolean {
        val socket = activeSocket
        if (socket == null || !socket.isConnected) {
            _heartbeatStatus.value = _heartbeatStatus.value.copy(
                isAlive = false,
                statusDescription = "Socket Bluetooth desconectado"
            )
            return false
        }

        // Seleciona o comando de baixo custo ideal conforme o protocolo ativo:
        // - KWP2000 / UDS / CAN: "3E" (Tester Present ISO 14230/15765)
        // - OBD2 SAE: "01 00" (Read Supported PIDs)
        val lowCostCmd = when (detectedProtocol.value) {
            ProtocolType.KWP_FAST, ProtocolType.KWP_SLOW, ProtocolType.CAN_500K, ProtocolType.CAN_250K -> "3E"
            else -> "01 00"
        }

        val pulseStartMs = System.currentTimeMillis()
        var response = ""
        try {
            response = sendCommand(lowCostCmd, timeoutMs = 800L)
            // Se o clone ELM retornar '?' para 3E puro sem header, faz fallback imediato para 01 00
            if (response.contains("?") && lowCostCmd == "3E") {
                response = sendCommand("01 00", timeoutMs = 800L)
            }
        } catch (e: Exception) {
            logTerminal("💔 [HEARTBEAT] Exceção durante envio de pulso: ${e.message}")
            response = "ERROR: ${e.message}"
        }

        val pulseLatency = System.currentTimeMillis() - pulseStartMs
        val isPulseAlive = validateHeartbeatResponse(response)
        val current = _heartbeatStatus.value

        if (isPulseAlive) {
            notifyPhysicalDataReceived()
            _heartbeatStatus.value = current.copy(
                isAlive = true,
                lastPulseTimestamp = System.currentTimeMillis(),
                pulseLatencyMs = pulseLatency,
                totalPulsesSent = current.totalPulsesSent + 1,
                consecutiveFailures = 0,
                lastCommandSent = lowCostCmd,
                lastResponse = response.trim(),
                statusDescription = "ECU Ativa (${pulseLatency}ms)"
            )
            return true
        } else {
            val failures = current.consecutiveFailures + 1
            _heartbeatStatus.value = current.copy(
                isAlive = false,
                pulseLatencyMs = pulseLatency,
                totalPulsesSent = current.totalPulsesSent + 1,
                consecutiveFailures = failures,
                lastCommandSent = lowCostCmd,
                lastResponse = response.trim(),
                statusDescription = "Falha no pulso ($response)"
            )
            logTerminal("💔 [HEARTBEAT] Pulso falhou! Resposta inválida ou ausente da ECU: '$response' (${pulseLatency}ms)")
            return false
        }
    }

    /**
     * Executa a desconexão automática imediata em caso de falha no pulso de vida (Heartbeat).
     */
    fun handleHeartbeatFailure(reason: String) {
        logTerminal("💔 [HEARTBEAT] FALHA NO PULSO DE VIDA DA ECU! $reason")
        logTerminal("⚠️ Executando DESCONEXÃO AUTOMÁTICA de segurança.")

        _heartbeatStatus.value = _heartbeatStatus.value.copy(
            isAlive = false,
            statusDescription = "Desconectado por falha no pulso"
        )

        disconnect()

        updateScanState(
            ScanState.ERROR,
            "Desconexão automática: Heartbeat da ECU falhou (sem pulso de resposta em 2s). Verifique a ignição ou conector OBD2."
        )

        onHeartbeatFailed?.invoke()
    }

    /**
     * Monitor Heartbeat (Verificação de vida da ECU a cada 2 segundos):
     * - Envia pulso de verificação de vida a cada 2000ms com comando de baixo custo ("3E" ou "01 00").
     * - Garante que a ECU ainda está ativa e pronta para receber consultas.
     * - Desconecta automaticamente e alerta o usuário caso o pulso falhe.
     * - Inclui watchdog de silêncio de 5s e monitoramento periódico de tensão da bateria (AT RV).
     */
    private fun startKeepAliveWorker() {
        keepAliveJob?.cancel()
        _heartbeatStatus.value = HeartbeatStatus(
            isAlive = true,
            statusDescription = "Iniciando monitoramento de pulso (2s)..."
        )
        keepAliveJob = scope.launch(Dispatchers.IO) {
            logTerminal("❤️ [HEARTBEAT] Monitor de vida ativo: pulso de baixo custo a cada 2s.")
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)

                if (!_isPhysicalEcuConnected.value || !_isConnected.value) {
                    break
                }

                if (isHeartbeatPaused.value) {
                    delay(500)
                    continue
                }

                // Watchdog de silêncio: se > 5000ms sem qualquer byte da ECU física
                if (lastPhysicalEcuResponseTimestamp > 0L) {
                    val silenceMs = System.currentTimeMillis() - lastPhysicalEcuResponseTimestamp
                    if (silenceMs > 5000L) {
                        handleHeartbeatFailure("Watchdog: Central permaneceu ${silenceMs / 1000}s sem responder.")
                        break
                    }
                }

                // Envia o pulso de vida (Heartbeat) de baixo custo
                val pulseOk = sendHeartbeatPulse()
                if (!pulseOk) {
                    handleHeartbeatFailure("Central ECU parou de responder ao comando de vida a cada 2s.")
                    break
                }

                // Verificação periódica de tensão a cada ~10s
                if (System.currentTimeMillis() % 10000 < 2200) {
                    try {
                        val voltRaw = sendCommand("AT RV", timeoutMs = 500L)
                        val parsed = GmDataParsers.parseElmVoltage(voltRaw)
                        if (parsed != null) {
                            _batteryVoltage.value = parsed
                            if (parsed < 10.5f) {
                                logTerminal("Aviso de Tensão: ${parsed}V (queda de partida/arranque detectada).")
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun disconnect() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeSocket = null
        serialQueue.updateSocket(null)
        _isBluetoothConnected.value = false
        _isPhysicalEcuConnected.value = false
        _isConnected.value = false
        _heartbeatStatus.value = _heartbeatStatus.value.copy(
            isAlive = false,
            statusDescription = "Desconectado"
        )
        _elmBenchmark.value = ElmChipBenchmark()
        _scanState.value = ScanState.IDLE
        _statusMessage.value = "Desconectado do adaptador."
        logTerminal("Conexão serial física encerrada.")
    }
}
