package com.example.serial

import android.util.Log
import com.example.model.DtcCode
import com.example.model.EcuSensorProfile
import com.example.model.LiveTelemetry
import com.example.model.ScanState
import com.example.model.VehicleProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MÓDULO 2: IDENTIFICAÇÃO ASSERTIVA DE ECU, MAPEAMENTO DE SENSORES E TELEMETRIA EM TEMPO REAL
 *
 * Responsabilidade Única e Independente:
 * - Identificar com precisão milimétrica a ECU física (Bosch Motronic M1.5.5, Motronic ME 7.9.6, Delphi Multec H, Rochester, etc.)
 * - Interrogar Part Number GM (1A 90 / KWP ou 09 02 / 09 04), CalID (09 04 / 1A 94), VIN (09 02), HW (1A 91) e SW (1A 92)
 * - Rejeitar estritamente códigos de erro e respostas negativas (NRC 7F, NO DATA, ERROR)
 * - Mapear os dados do motor Chevrolet Astra Brasil (potência, cilindrada, válvulas, combustível, limites de injeção)
 * - Orquestrar o ciclo de telemetria contínua com prioridade de amostragem (Fast Loop vs Interleaved Loop)
 * - Mapear e calcular grandezas derivadas reais (vácuo do coletor, duty cycle dos bicos, consumo instantâneo km/l e l/h, MAF estimado por Speed-Density)
 * - Realizar varredura e limpeza de códigos de falha (DTCs) ativos e armazenados no histórico
 */
class EcuIdentificationEngine(
    private val scope: CoroutineScope,
    private val commandSender: suspend (cmd: String, timeoutMs: Long) -> String,
    private val logCallback: (String) -> Unit,
    private val onPhysicalConnectionChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "EcuIdentificationEngine"
    }

    private val fingerprinter = EcuFingerprinter(commandSender, logCallback)

    private val _vehicleProfile = MutableStateFlow(VehicleProfile.UNKNOWN)
    val vehicleProfile: StateFlow<VehicleProfile> = _vehicleProfile.asStateFlow()

    private val _sensorProfile = MutableStateFlow<EcuSensorProfile?>(null)
    val sensorProfile: StateFlow<EcuSensorProfile?> = _sensorProfile.asStateFlow()

    private val _isInjectionDetected = MutableStateFlow(false)
    val isInjectionDetected: StateFlow<Boolean> = _isInjectionDetected.asStateFlow()

    private val _telemetry = MutableStateFlow(LiveTelemetry())
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()

    private val _activeDtcs = MutableStateFlow<List<DtcCode>>(emptyList())
    val activeDtcs: StateFlow<List<DtcCode>> = _activeDtcs.asStateFlow()

    private val _historyDtcs = MutableStateFlow<List<DtcCode>>(emptyList())
    val historyDtcs: StateFlow<List<DtcCode>> = _historyDtcs.asStateFlow()

    val isTelemetryPaused = MutableStateFlow(false)

    fun pauseTelemetry() {
        isTelemetryPaused.value = true
        log("Telemetria pausada para execução exclusiva de comando especial / atuador.")
    }

    fun resumeTelemetry() {
        isTelemetryPaused.value = false
        log("Telemetria retomada com sucesso.")
    }

    private var telemetryJob: Job? = null

    private fun log(msg: String) {
        logCallback("[ECU-ID] $msg")
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            // Ignora falha de mock em testes unitários JVM
        }
    }

    /**
     * Interroga fisicamente a ECU conectada e mapeia sensores compatíveis via ECUFingerprinter.
     */
    suspend fun identifyEcuAndSensors(): VehicleProfile {
        log("Acionando rotinas do ECUFingerprinter...")
        val (matchedProfile, mappedSensors) = fingerprinter.interrogateAndFingerprint()

        _vehicleProfile.value = matchedProfile
        _sensorProfile.value = mappedSensors
        _isInjectionDetected.value = true

        log("ECU Identificada: ${matchedProfile.ecuName} | ${mappedSensors.compatibleSensors.size} sensores mapeados.")
        return matchedProfile
    }

    /**
     * Inicia o loop de aquisição contínua de telemetria em tempo real com priorização inteligente de PIDs.
     */
    /**
     * Define ou altera manualmente o perfil da central (ECU) e remapeia sensores.
     */
    fun setVehicleProfile(profile: VehicleProfile) {
        log("Definindo perfil de veículo: ${profile.ecuName} | Motor: ${profile.engineCode}")
        _vehicleProfile.value = profile
        val mappedSensors = fingerprinter.mapSensorCapabilities(profile)
        _sensorProfile.value = mappedSensors
        _isInjectionDetected.value = true
        startTelemetry(profile)
    }

    /**
     * Inicia o loop de aquisição contínua de telemetria em tempo real com priorização inteligente de PIDs.
     * Arquitetura High-Rate Interleaved Scheduler:
     * - RPM é consultado em todo ciclo (taxa máxima de atualização para o conta-giros).
     * - Sensores primários rápidos (TPS, MAP, Velocidade, Ponto, Lambda) alternam em rodízio de alta frequência.
     * - Sensores lentos (ECT, IAT, Carga, Trim, Bateria) alternam em rodízio secundário de baixa frequência.
     * - Total de apenas 2 a 3 comandos seriais por ciclo, reduzindo o tempo de volta para 100-150ms (~7-10 Hz).
     */
    fun startTelemetry(profile: VehicleProfile = _vehicleProfile.value) {
        telemetryJob?.cancel()
        telemetryJob = scope.launch(Dispatchers.IO) {
            var current = _telemetry.value
            var cycleCounter = 0
            val unsupportedPids = mutableSetOf<String>()
            var isMode21Supported: Boolean? = null
            val lambdaWindow = mutableListOf<Float>()
            var consecutiveNoSerial = 0
            var lastEcuPhysicalDataMs = System.currentTimeMillis()
            log("Loop de telemetria em tempo real ativado para perfil: ${profile.engineCode}")

            while (isActive) {
                if (isTelemetryPaused.value) {
                    kotlinx.coroutines.delay(150)
                    continue
                }
                try {
                    val loopStartMs = System.currentTimeMillis()
                    val activeProfile = _vehicleProfile.value

                    // ========================================================
                    // 1. TACHOMETER FAST LOOP: RPM A CADA CICLO (~7-10 Hz)
                    // ========================================================
                    var rpm = current.rpm
                    var isRealPhysicalSerial = false

                    if (!unsupportedPids.contains("01 0C")) {
                        val rpmRaw = try { commandSender("01 0C", 600L) } catch (_: Exception) { "" }
                        val parsedRpm = GmDataParsers.parseRpm(rpmRaw)
                        val isPositiveRpm = parsedRpm != null || rpmRaw.contains("41 0C") || rpmRaw.contains("61 02")
                        if (isPositiveRpm) {
                            if (parsedRpm != null) rpm = parsedRpm
                            isRealPhysicalSerial = true
                            consecutiveNoSerial = 0
                            lastEcuPhysicalDataMs = System.currentTimeMillis()
                            onPhysicalConnectionChanged(true)
                        } else if (!GmDataParsers.isNegativeResponse(rpmRaw) && isMode21Supported != false) {
                            val rpm21 = try { commandSender("21 02", 500L) } catch (_: Exception) { "" }
                            val parsed21 = GmDataParsers.parseRpm(rpm21)
                            if (parsed21 != null) {
                                rpm = parsed21
                                isRealPhysicalSerial = true
                                consecutiveNoSerial = 0
                                lastEcuPhysicalDataMs = System.currentTimeMillis()
                                onPhysicalConnectionChanged(true)
                                isMode21Supported = true
                            } else {
                                if (GmDataParsers.isNegativeResponse(rpm21)) unsupportedPids.add("21 02")
                                consecutiveNoSerial++
                            }
                        } else {
                            // Verifica se há resposta física em 01 05 (chave ligada, motor em repouso)
                            if (cycleCounter % 6 == 0) {
                                val ectRaw = try { commandSender("01 05", 500L) } catch (_: Exception) { "" }
                                if (ectRaw.contains("41 05") && GmDataParsers.parseCoolantTemp(ectRaw) != null) {
                                    isRealPhysicalSerial = true
                                    consecutiveNoSerial = 0
                                    lastEcuPhysicalDataMs = System.currentTimeMillis()
                                    onPhysicalConnectionChanged(true)
                                } else {
                                    consecutiveNoSerial++
                                }
                            } else {
                                consecutiveNoSerial++
                            }
                        }
                    }

                    // Watchdog de 5 segundos: se nenhum dado da ECU for recebido há mais de 5s,
                    // cancela o status físico e zera a telemetria imediatamente
                    val silenceDurationMs = System.currentTimeMillis() - lastEcuPhysicalDataMs
                    if (silenceDurationMs > 5000L || consecutiveNoSerial >= 2) {
                        onPhysicalConnectionChanged(false)
                        lambdaWindow.clear()
                        current = LiveTelemetry(
                            rpm = 0f,
                            targetIdleRpm = 0f,
                            speedKmh = 0f,
                            engineLoadPercent = 0f,
                            coolantTempC = 0f,
                            intakeAirTempC = 0f,
                            mapKpa = 0f,
                            baroKpa = 0f,
                            manifoldVacuumBar = 0f,
                            estimatedMafGramsSec = 0f,
                            throttlePercent = 0f,
                            throttleVoltage1 = 0f,
                            throttleVoltage2 = 0f,
                            acceleratorPedalPercent = 0f,
                            injectionTimeMs = 0f,
                            injectorDutyCyclePercent = 0f,
                            iacSteps = 0,
                            shortTermFuelTrim = 0f,
                            longTermFuelTrim = 0f,
                            fuelLoopStatus = "Desconectado",
                            lambdaVoltage = 0f,
                            lambdaPostCatVoltage = 0f,
                            equivalenceRatioLambda = 0f,
                            lambdaFrequencyHz = 0f,
                            lambdaHealthStatus = "Sem Sinal",
                            lambdaHistory = emptyList(),
                            fuelTrimHealthMessage = "Aguardando conexão com a ECU do Astra",
                            fuelTrimHealthLevel = 0,
                            transmissionDetected = activeProfile.hasAutomaticTransmission,
                            estimatedAfRatio = 0f,
                            ethanolPercentage = 0f,
                            sparkAdvanceDeg = 0f,
                            knockRetardDeg = 0f,
                            knockCylinder1Deg = 0f,
                            knockCylinder2Deg = 0f,
                            knockCylinder3Deg = 0f,
                            knockCylinder4Deg = 0f,
                            instantConsumptionLitersHour = 0f,
                            instantConsumptionKmPerLiter = 0f,
                            evapPurgePercent = 0f,
                            ecuBatteryVoltage = 0f,
                            elmBatteryVoltage = 0f,
                            fuelPumpRelayStatus = false,
                            timestampMs = System.currentTimeMillis()
                        )
                        _telemetry.value = current
                        delay(300)
                        continue
                    }

                    // ========================================================
                    // 2. PRIMARY FAST SENSORS (ROTAÇÃO RÁPIDA: TPS, MAP, VEL, PONTO, LAMBDA)
                    // ========================================================
                    var spd = current.speedKmh
                    var map = current.mapKpa
                    var tps = current.throttlePercent
                    var spark = current.sparkAdvanceDeg
                    var lambda = current.lambdaVoltage

                    when (cycleCounter % 4) {
                        0 -> {
                            // Borboleta (TPS %)
                            if (!unsupportedPids.contains("01 11")) {
                                val tpsRaw = try { commandSender("01 11", 500L) } catch (_: Exception) { "" }
                                val parsedTps = GmDataParsers.parseTpsPercent(tpsRaw)
                                if (parsedTps != null) {
                                    tps = parsedTps
                                } else if (GmDataParsers.isNegativeResponse(tpsRaw)) {
                                    unsupportedPids.add("01 11")
                                }
                            }
                        }
                        1 -> {
                            // Pressão no Coletor (MAP kPa)
                            if (!unsupportedPids.contains("01 0B")) {
                                val mapRaw = try { commandSender("01 0B", 500L) } catch (_: Exception) { "" }
                                var parsedMap = GmDataParsers.parseMap(mapRaw)
                                if (parsedMap == null && isMode21Supported == true && !unsupportedPids.contains("21 0A")) {
                                    val map21 = try { commandSender("21 0A", 400L) } catch (_: Exception) { "" }
                                    parsedMap = GmDataParsers.parseMap(map21)
                                }
                                if (parsedMap != null) {
                                    map = parsedMap
                                } else if (GmDataParsers.isNegativeResponse(mapRaw)) {
                                    unsupportedPids.add("01 0B")
                                }
                            }
                        }
                        2 -> {
                            // Velocidade do Veículo (km/h)
                            if (!unsupportedPids.contains("01 0D")) {
                                val spdRaw = try { commandSender("01 0D", 500L) } catch (_: Exception) { "" }
                                val parsedSpd = GmDataParsers.parseSpeed(spdRaw)
                                if (parsedSpd != null) {
                                    spd = parsedSpd
                                } else if (GmDataParsers.isNegativeResponse(spdRaw)) {
                                    unsupportedPids.add("01 0D")
                                }
                            }
                        }
                        3 -> {
                            // Avanço de Ignição e Sonda Lambda 1
                            if (!unsupportedPids.contains("01 0E")) {
                                val sparkRaw = try { commandSender("01 0E", 500L) } catch (_: Exception) { "" }
                                val parsedSpark = GmDataParsers.parseSparkAdvance(sparkRaw)
                                if (parsedSpark != null) spark = parsedSpark
                            }
                            if (!unsupportedPids.contains("01 14")) {
                                val lambdaRaw = try { commandSender("01 14", 500L) } catch (_: Exception) { "" }
                                val parsedLambda = GmDataParsers.parseLambdaVoltage(lambdaRaw)
                                if (parsedLambda != null) {
                                    lambda = parsedLambda
                                    lambdaWindow.add(parsedLambda)
                                    if (lambdaWindow.size > 30) {
                                        lambdaWindow.removeAt(0)
                                    }
                                }
                            }
                        }
                    }

                    // ========================================================
                    // 3. SECONDARY SLOW SENSORS (ROTAÇÃO EM 8 PASSOS)
                    // ========================================================
                    var ect = current.coolantTempC
                    var iat = current.intakeAirTempC
                    var engLoad = current.engineLoadPercent
                    var stft = current.shortTermFuelTrim
                    var ltft = current.longTermFuelTrim
                    var af = current.estimatedAfRatio
                    var eth = current.ethanolPercentage
                    var iac = current.iacSteps
                    var tpsVolt1 = current.throttleVoltage1
                    var tpsVolt2 = current.throttleVoltage2
                    var pedal = current.acceleratorPedalPercent
                    var baro = current.baroKpa
                    var lambda2 = current.lambdaPostCatVoltage
                    var lambdaFactor = current.equivalenceRatioLambda
                    var evap = current.evapPurgePercent
                    var targetRpm = current.targetIdleRpm
                    var ecuVolt = current.ecuBatteryVoltage
                    var elmVolt = current.elmBatteryVoltage
                    var loopStatus = current.fuelLoopStatus
                    var knockGlobal = current.knockRetardDeg
                    var ti: Float? = current.injectionTimeMs

                    when (cycleCounter % 8) {
                        0 -> {
                            // Temperatura da Água (ECT)
                            if (!unsupportedPids.contains("01 05")) {
                                val ectRaw = try { commandSender("01 05", 500L) } catch (_: Exception) { "" }
                                var parsedEct = GmDataParsers.parseCoolantTemp(ectRaw)
                                if (parsedEct == null && isMode21Supported == true) {
                                    val ect21 = try { commandSender("21 01", 400L) } catch (_: Exception) { "" }
                                    parsedEct = GmDataParsers.parseCoolantTemp(ect21)
                                }
                                if (parsedEct != null) ect = parsedEct
                            }
                        }
                        1 -> {
                            // Temperatura do Ar (IAT) e Carga do Motor
                            if (!unsupportedPids.contains("01 0F")) {
                                val iatRaw = try { commandSender("01 0F", 500L) } catch (_: Exception) { "" }
                                val parsedIat = GmDataParsers.parseIat(iatRaw)
                                if (parsedIat != null) iat = parsedIat
                            }
                            if (!unsupportedPids.contains("01 04")) {
                                val loadRaw = try { commandSender("01 04", 500L) } catch (_: Exception) { "" }
                                val parsedLoad = GmDataParsers.parseEngineLoad(loadRaw)
                                if (parsedLoad != null) engLoad = parsedLoad
                            }
                        }
                        2 -> {
                            // Ajustes de Combustível (STFT e LTFT)
                            if (!unsupportedPids.contains("01 06")) {
                                val stftRaw = try { commandSender("01 06", 500L) } catch (_: Exception) { "" }
                                val parsedStft = GmDataParsers.parseStft(stftRaw)
                                if (parsedStft != null) stft = parsedStft
                            }
                            if (!unsupportedPids.contains("01 07")) {
                                val ltftRaw = try { commandSender("01 07", 500L) } catch (_: Exception) { "" }
                                val parsedLtft = GmDataParsers.parseLtft(ltftRaw)
                                if (parsedLtft != null) ltft = parsedLtft
                            }
                        }
                        3 -> {
                            // Malha Fechada e Fator Lambda
                            if (!unsupportedPids.contains("01 03")) {
                                val loopRaw = try { commandSender("01 03", 500L) } catch (_: Exception) { "" }
                                val parsedLoop = GmDataParsers.parseFuelLoopStatus(loopRaw)
                                if (parsedLoop != null) loopStatus = parsedLoop
                            }
                            if (!unsupportedPids.contains("01 44")) {
                                val lambdaEqRaw = try { commandSender("01 44", 500L) } catch (_: Exception) { "" }
                                val parsedEq = GmDataParsers.parseEquivalenceRatioLambda(lambdaEqRaw)
                                if (parsedEq != null) lambdaFactor = parsedEq
                            }
                        }
                        4 -> {
                            // Parâmetros Flexpower GM (A/F e Etanol %)
                            if (activeProfile.supportsFlexPids && isMode21Supported != false && !unsupportedPids.contains("21 35")) {
                                val afRaw = try { commandSender("21 35", 500L) } catch (_: Exception) { "" }
                                val parsedAf = GmDataParsers.parseAfRatio(afRaw)
                                if (parsedAf != null && parsedAf in 7.0f..18.0f) {
                                    af = parsedAf
                                } else if (GmDataParsers.isNegativeResponse(afRaw)) {
                                    unsupportedPids.add("21 35")
                                }
                                val ethRaw = try { commandSender("21 36", 500L) } catch (_: Exception) { "" }
                                val parsedEth = GmDataParsers.parseEthanolPercent(ethRaw)
                                if (parsedEth != null && parsedEth in 0f..100f) {
                                    eth = parsedEth
                                }
                            }
                            if (af <= 0f) {
                                af = if (activeProfile.supportsFlexPids) {
                                    if (eth > 50f) 9.0f else 13.2f
                                } else 13.2f
                            }
                        }
                        5 -> {
                            // Tensão da Bateria (ECU e ELM327)
                            if (!unsupportedPids.contains("01 42")) {
                                val voltRaw = try { commandSender("01 42", 500L) } catch (_: Exception) { "" }
                                val parsedVolt = GmDataParsers.parseEcuBatteryVoltage(voltRaw)
                                if (parsedVolt != null) ecuVolt = parsedVolt
                            }
                            val rvRaw = try { commandSender("AT RV", 500L) } catch (_: Exception) { "" }
                            val parsedRv = GmDataParsers.parseElmVoltage(rvRaw)
                            if (parsedRv != null) elmVolt = parsedRv
                        }
                        6 -> {
                            // Tempo de Injeção real (Mode 21 15 da GM)
                            if (isMode21Supported != false && !unsupportedPids.contains("21 15")) {
                                val tiRaw = try { commandSender("21 15", 500L) } catch (_: Exception) { "" }
                                val parsedTi = GmDataParsers.parseInjectionTime(tiRaw)
                                if (parsedTi != null && parsedTi > 0f) {
                                    ti = parsedTi
                                    isMode21Supported = true
                                } else if (GmDataParsers.isNegativeResponse(tiRaw)) {
                                    unsupportedPids.add("21 15")
                                }
                            }
                            if (ti == null || ti <= 0f) {
                                ti = if (rpm > 100f && map > 10f) {
                                    val baseTi = (map / 101.3f) * (activeProfile.displacementLiters / 4f) * 4.2f
                                    baseTi.coerceIn(1.2f, 22.0f)
                                } else 0f
                            }

                            // Pressão Barométrica e Purga Canister
                            if (!unsupportedPids.contains("01 33")) {
                                val baroRaw = try { commandSender("01 33", 500L) } catch (_: Exception) { "" }
                                val parsedBaro = GmDataParsers.parseBaro(baroRaw)
                                if (parsedBaro != null) baro = parsedBaro
                            }
                        }
                        7 -> {
                            // Borboleta DBW / Atuador IAC / Pedal APP
                            if (isMode21Supported == true && !unsupportedPids.contains("21 19")) {
                                val iacRaw = try { commandSender("21 19", 500L) } catch (_: Exception) { "" }
                                val parsedIac = GmDataParsers.parseIacSteps(iacRaw)
                                if (parsedIac != null) {
                                    iac = parsedIac
                                } else if (GmDataParsers.isNegativeResponse(iacRaw)) {
                                    unsupportedPids.add("21 19")
                                }
                            }
                            if (!unsupportedPids.contains("01 49")) {
                                val pedRaw = try { commandSender("01 49", 500L) } catch (_: Exception) { "" }
                                val parsedPed = GmDataParsers.parsePedalPercent(pedRaw)
                                if (parsedPed != null) pedal = parsedPed
                            }
                            tpsVolt1 = (tps * 0.045f + 0.45f)
                            tpsVolt2 = (4.5f - (tps * 0.045f))
                        }
                    }

                    cycleCounter++

                    // Cálculos derivados físicos em tempo real atualizados a cada ciclo
                    val actualTi = ti ?: current.injectionTimeMs
                    val effectiveBaro = if (baro > 50f) baro else 101.3f
                    val vacuum = GmDataParsers.calculateVacuum(map, effectiveBaro)
                    val dutyCycle = GmDataParsers.calculateInjectorDutyCycle(actualTi, rpm)
                    val lPerHour = GmDataParsers.calculateLitersPerHour(actualTi, rpm, activeProfile.injectorFlowCcMin)
                    val kmPerL = GmDataParsers.calculateKmPerLiter(spd, lPerHour)
                    val maf = GmDataParsers.estimateSpeedDensityMaf(rpm, map, iat, activeProfile.displacementLiters)

                    val (lambdaHz, lambdaStatus) = GmDataParsers.evaluateLambdaOscillation(lambdaWindow)
                    val (trimLevel, trimTitle, trimAdvice) = GmDataParsers.evaluateFuelTrimHealth(stft, ltft)

                    current = LiveTelemetry(
                        rpm = rpm,
                        targetIdleRpm = targetRpm,
                        speedKmh = spd,
                        engineLoadPercent = engLoad,
                        coolantTempC = ect,
                        intakeAirTempC = iat,
                        mapKpa = map,
                        baroKpa = baro,
                        manifoldVacuumBar = vacuum,
                        estimatedMafGramsSec = maf,
                        throttlePercent = tps,
                        throttleVoltage1 = tpsVolt1,
                        throttleVoltage2 = tpsVolt2,
                        acceleratorPedalPercent = pedal,
                        injectionTimeMs = actualTi,
                        injectorDutyCyclePercent = dutyCycle,
                        iacSteps = iac,
                        shortTermFuelTrim = stft,
                        longTermFuelTrim = ltft,
                        fuelLoopStatus = loopStatus,
                        lambdaVoltage = lambda,
                        lambdaPostCatVoltage = lambda2,
                        equivalenceRatioLambda = lambdaFactor,
                        lambdaFrequencyHz = lambdaHz,
                        lambdaHealthStatus = lambdaStatus,
                        lambdaHistory = lambdaWindow.toList(),
                        fuelTrimHealthMessage = "$trimTitle: $trimAdvice",
                        fuelTrimHealthLevel = trimLevel,
                        transmissionDetected = activeProfile.hasAutomaticTransmission,
                        estimatedAfRatio = af,
                        ethanolPercentage = eth,
                        sparkAdvanceDeg = spark,
                        knockRetardDeg = knockGlobal,
                        knockCylinder1Deg = current.knockCylinder1Deg,
                        knockCylinder2Deg = current.knockCylinder2Deg,
                        knockCylinder3Deg = current.knockCylinder3Deg,
                        knockCylinder4Deg = current.knockCylinder4Deg,
                        instantConsumptionLitersHour = lPerHour,
                        instantConsumptionKmPerLiter = kmPerL,
                        evapPurgePercent = evap,
                        ecuBatteryVoltage = ecuVolt,
                        elmBatteryVoltage = elmVolt,
                        fuelPumpRelayStatus = (rpm > 100f || _isInjectionDetected.value),
                        sampleRateHz = ((1000f / (System.currentTimeMillis() - loopStartMs).coerceAtLeast(1))).coerceIn(0.5f, 30.0f),
                        loopLatencyMs = (System.currentTimeMillis() - loopStartMs).toInt().coerceAtLeast(1),
                        timestampMs = System.currentTimeMillis()
                    )

                    // Emissão imediata e fluida para a UI em tempo real
                    _telemetry.value = current
                    delay(20)
                } catch (e: Exception) {
                    log("Aviso no loop de telemetria: ${e.message}")
                    delay(40)
                }
            }
        }
    }

    /**
     * Varredura detalhada de códigos de falha (DTCs) ativos e armazenados.
     */
    suspend fun scanDtcs(): Result<List<DtcCode>> {
        return try {
            log("Lendo falhas ativas na ECU (Modo 03 / KWP 18h)...")
            val resp03 = commandSender("03", 3000L)
            log("< 03 DTCs Ativos: $resp03")

            val bytes = GmDataParsers.extractHexBytes(resp03)
            val codes = mutableListOf<DtcCode>()

            if (bytes.size >= 2 && bytes[0] == 0x43) {
                val numDtc = bytes[1]
                var i = 2
                while (i + 1 < bytes.size && codes.size < numDtc) {
                    val b1 = bytes[i]
                    val b2 = bytes[i + 1]
                    val codeStr = GmDataParsers.decodeDtc(b1, b2)
                    if (codeStr != null && codeStr != "P0000") {
                        val dtc = GmDataParsers.mapDtcDetails(codeStr, isPresent = true)
                        codes.add(dtc)
                    }
                    i += 2
                }
            }

            val resp07 = commandSender("07", 2500L)
            val histBytes = GmDataParsers.extractHexBytes(resp07)
            val histCodes = mutableListOf<DtcCode>()
            if (histBytes.size >= 2 && histBytes[0] == 0x47) {
                val numDtc = histBytes[1]
                var i = 2
                while (i + 1 < histBytes.size && histCodes.size < numDtc) {
                    val b1 = histBytes[i]
                    val b2 = histBytes[i + 1]
                    val codeStr = GmDataParsers.decodeDtc(b1, b2)
                    if (codeStr != null && codeStr != "P0000") {
                        val dtc = GmDataParsers.mapDtcDetails(codeStr, isPresent = false)
                        histCodes.add(dtc)
                    }
                    i += 2
                }
            }

            // Se a leitura padrão 03/07 não retornou códigos, tenta o comando KWP nativo GM (18 00 FF 00)
            if (codes.isEmpty()) {
                val respKwp18 = commandSender("18 00 FF 00", 3000L)
                log("< 18 00 FF 00 (KWP DTCs): $respKwp18")
                val kwpBytes = GmDataParsers.extractHexBytes(respKwp18)
                if (kwpBytes.size >= 2 && kwpBytes[0] == 0x58) {
                    val numDtc = kwpBytes[1]
                    var i = 2
                    while (i + 1 < kwpBytes.size && codes.size < numDtc) {
                        val b1 = kwpBytes[i]
                        val b2 = kwpBytes[i + 1]
                        val codeStr = GmDataParsers.decodeDtc(b1, b2)
                        if (codeStr != null && codeStr != "P0000") {
                            val dtc = GmDataParsers.mapDtcDetails(codeStr, isPresent = true)
                            codes.add(dtc)
                        }
                        i += 3 // No KWP 18h, o 3º byte é o status da falha
                    }
                }
            }

            _activeDtcs.value = codes
            _historyDtcs.value = histCodes
            log("Varredura de DTCs concluída: ${codes.size} ativas, ${histCodes.size} históricas.")
            Result.success(codes)
        } catch (e: Exception) {
            log("Erro ao escanear DTCs: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Limpeza segura da memória de falhas da central (Modo 04 / KWP 14h).
     */
    suspend fun clearDtcs(): Result<String> {
        return try {
            log("Enviando comando de limpeza de DTCs (04h / KWP 14h)...")
            val resp = commandSender("04", 3000L)
            log("< Resposta Clear DTC: $resp")
            if (resp.contains("44") || resp.contains("OK")) {
                _activeDtcs.value = emptyList()
                _historyDtcs.value = emptyList()
                Result.success("Memória de falhas limpa com sucesso.")
            } else {
                val respKwp = commandSender("14 FF 00", 3000L)
                log("< Resposta KWP Clear: $respKwp")
                _activeDtcs.value = emptyList()
                _historyDtcs.value = emptyList()
                Result.success("Comando de limpeza finalizado.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
        onPhysicalConnectionChanged(false)
        _telemetry.value = LiveTelemetry()
    }

    fun reset() {
        stopTelemetry()
        onPhysicalConnectionChanged(false)
        _isInjectionDetected.value = false
        _vehicleProfile.value = VehicleProfile.UNKNOWN
        _telemetry.value = LiveTelemetry()
        _activeDtcs.value = emptyList()
        _historyDtcs.value = emptyList()
    }
}
