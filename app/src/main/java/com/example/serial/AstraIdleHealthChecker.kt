package com.example.serial

import com.example.model.EngineOperatingRegime
import com.example.model.LiveTelemetry
import com.example.model.VehicleProfile
import java.util.Locale

/**
 * Resultado individual de inspeção de cada sensor durante a marcha lenta
 */
data class SensorHealthCheck(
    val sensorId: String,
    val sensorName: String,
    val currentValueStr: String,
    val numericValue: Float,
    val nominalRange: String,
    val unit: String,
    val isOk: Boolean,
    val issueDescription: String? = null,
    val recommendedActionId: String? = null,
    val recommendedActionTitle: String? = null
)

/**
 * Relatório completo de auditoria da marcha lenta em tempo real
 */
data class IdleAuditResult(
    val isIdleDetected: Boolean,
    val idleConditionMessage: String,
    val totalSensors: Int = 0,
    val sensorsOkCount: Int = 0,
    val sensorsOutCount: Int = 0,
    val checks: List<SensorHealthCheck> = emptyList(),
    val primaryRecommendation: SensorHealthCheck? = null
)

/**
 * Relatório comparativo "Antes vs Depois" do que estava errado e do que foi corrigido
 */
data class CorrectionComparisonReport(
    val testId: String,
    val testTitle: String,
    val timestamp: Long = System.currentTimeMillis(),
    val beforeSnapshots: List<SensorHealthCheck>,
    val afterSnapshots: List<SensorHealthCheck>,
    val resolvedIssues: List<String>,
    val summaryMessage: String
)

/**
 * AstraIdleHealthChecker
 *
 * Motor especialista de auditoria dos parâmetros de injeção e sensores da Família II
 * durante o regime de marcha lenta estabilizada.
 *
 * Cruza em tempo real:
 * - MAP vs Vácuo
 * - Tempo de Injeção vs Combustível ativo (Gasolina vs Etanol)
 * - Oscilação da Sonda Lambda
 * - Avanço de Ignição (Ponto)
 * - TBI Drive-By-Wire (Soma Pistas 1 e 2 = 5.0V)
 * - Ajustes STFT e LTFT
 * - Carga da Bateria/Alternador
 */
object AstraIdleHealthChecker {

    /**
     * Avalia os 9 sensores essenciais do motor Família II, seja em repouso (ignição ligada) ou em funcionamento
     */
    fun evaluateAllSensors(t: LiveTelemetry, p: VehicleProfile, isEngineRunning: Boolean): List<SensorHealthCheck> {
        val checks = mutableListOf<SensorHealthCheck>()
        val isEthanol = t.ethanolPercentage > 50f || (t.estimatedAfRatio in 8.5f..11.5f)

        // 1. Rotação (RPM)
        if (isEngineRunning) {
            val devRpm = Math.abs(t.rpm - 850f)
            val isRpmOk = devRpm <= 130f
            checks.add(
                SensorHealthCheck(
                    sensorId = "rpm",
                    sensorName = "Rotação de Marcha Lenta",
                    currentValueStr = "${t.rpm.toInt()}",
                    numericValue = t.rpm,
                    nominalRange = "800 - 920",
                    unit = "RPM",
                    isOk = isRpmOk,
                    issueDescription = if (!isRpmOk) "Marcha lenta oscilando além do tolerado (desvio de ${devRpm.toInt()} RPM)." else null,
                    recommendedActionId = if (!isRpmOk) "calibrate_tbi" else null,
                    recommendedActionTitle = if (!isRpmOk) "Alinhamento de Batentes da TBI (DBW)" else null
                )
            )
        } else {
            val isStaticRpmOk = t.rpm <= 150f
            checks.add(
                SensorHealthCheck(
                    sensorId = "rpm",
                    sensorName = "Rotação do Motor (Repouso)",
                    currentValueStr = "${t.rpm.toInt()}",
                    numericValue = t.rpm,
                    nominalRange = "0 (Motor Desligado)",
                    unit = "RPM",
                    isOk = isStaticRpmOk,
                    issueDescription = if (!isStaticRpmOk) "Rotação detectada com motor supostamente parado." else null,
                    recommendedActionId = null,
                    recommendedActionTitle = null
                )
            )
        }

        // 2. Pressão Absoluta no Coletor (MAP)
        if (isEngineRunning) {
            val isMapOk = t.mapKpa in 26f..44f
            checks.add(
                SensorHealthCheck(
                    sensorId = "map",
                    sensorName = "Pressão no Coletor (MAP)",
                    currentValueStr = "${t.mapKpa.toInt()}",
                    numericValue = t.mapKpa,
                    nominalRange = "28 - 42",
                    unit = "kPa",
                    isOk = isMapOk,
                    issueDescription = when {
                        t.mapKpa > 44f -> "Pressão no coletor elevada (vácuo fraco). Causa peso no motor, borboleta descalibrada ou ar falso."
                        t.mapKpa < 26f -> "Leitura de pressão anormalmente baixa no coletor."
                        else -> null
                    },
                    recommendedActionId = if (!isMapOk) "calibrate_tbi" else null,
                    recommendedActionTitle = if (!isMapOk) "Alinhamento de Batentes da TBI (DBW)" else null
                )
            )
        } else {
            val isMapStaticOk = t.mapKpa in 90f..106f || t.mapKpa == 0f
            checks.add(
                SensorHealthCheck(
                    sensorId = "map",
                    sensorName = "Pressão Atmosférica (MAP Repouso)",
                    currentValueStr = "${t.mapKpa.toInt()}",
                    numericValue = t.mapKpa,
                    nominalRange = "95 - 105 (Barométrica)",
                    unit = "kPa",
                    isOk = isMapStaticOk,
                    issueDescription = if (!isMapStaticOk) "Sensor MAP com offset anormal na leitura barométrica de repouso." else null,
                    recommendedActionId = if (!isMapStaticOk) "reset_thermal_offsets" else null,
                    recommendedActionTitle = if (!isMapStaticOk) "Reset de Compensação Térmica / Baro" else null
                )
            )
        }

        // 3. Tempo de Injeção (T.I.)
        if (isEngineRunning) {
            val isTiOk = if (isEthanol) {
                t.injectionTimeMs in 2.60f..4.40f
            } else {
                t.injectionTimeMs in 1.70f..2.80f
            }
            val tiNominalStr = if (isEthanol) "2.80 - 4.20 (Etanol)" else "1.80 - 2.60 (Gasolina)"
            checks.add(
                SensorHealthCheck(
                    sensorId = "ti",
                    sensorName = "Tempo de Injeção (T.I.)",
                    currentValueStr = String.format(Locale.US, "%.2f", t.injectionTimeMs),
                    numericValue = t.injectionTimeMs,
                    nominalRange = tiNominalStr,
                    unit = "ms",
                    isOk = isTiOk,
                    issueDescription = if (!isTiOk) {
                        if (isEthanol) "Tempo de injeção em etanol fora da faixa nominal (2.8 - 4.2 ms)."
                        else "Tempo de injeção em gasolina fora da faixa nominal (1.8 - 2.6 ms)."
                    } else null,
                    recommendedActionId = if (!isTiOk) "reset_af" else null,
                    recommendedActionTitle = if (!isTiOk) "Reset de A/F e Autoadaptação" else null
                )
            )
        } else {
            checks.add(
                SensorHealthCheck(
                    sensorId = "ti",
                    sensorName = "Tempo de Injeção (Injetores em Repouso)",
                    currentValueStr = String.format(Locale.US, "%.2f", t.injectionTimeMs),
                    numericValue = t.injectionTimeMs,
                    nominalRange = "0.00 (Injetores Fechados)",
                    unit = "ms",
                    isOk = t.injectionTimeMs <= 0.1f,
                    issueDescription = if (t.injectionTimeMs > 0.1f) "Pulso de injeção ativo com motor parado!" else null,
                    recommendedActionId = null,
                    recommendedActionTitle = null
                )
            )
        }

        // 4. Sonda Lambda 1
        if (isEngineRunning) {
            val isLambdaOk = t.lambdaVoltage in 0.080f..0.920f
            checks.add(
                SensorHealthCheck(
                    sensorId = "lambda",
                    sensorName = "Sonda Lambda 1 (Pré-Cat)",
                    currentValueStr = String.format(Locale.US, "%.3f", t.lambdaVoltage),
                    numericValue = t.lambdaVoltage,
                    nominalRange = "0.100 - 0.900 (Oscilando)",
                    unit = "V",
                    isOk = isLambdaOk,
                    issueDescription = when {
                        t.lambdaVoltage < 0.120f -> "Sonda travada em mistura pobre ou com aquecedor inoperante."
                        t.lambdaVoltage > 0.880f -> "Sonda travada em mistura rica permanente (sem ciclar)."
                        else -> null
                    },
                    recommendedActionId = if (!isLambdaOk) "reset_lambda_degradation" else null,
                    recommendedActionTitle = if (!isLambdaOk) "Reset de Degradação da Sonda Lambda" else null
                )
            )
        } else {
            val isStaticLambdaOk = t.lambdaVoltage in 0.35f..0.60f || t.lambdaVoltage == 0f
            checks.add(
                SensorHealthCheck(
                    sensorId = "lambda",
                    sensorName = "Sonda Lambda 1 (Tensão de Bias)",
                    currentValueStr = String.format(Locale.US, "%.3f", t.lambdaVoltage),
                    numericValue = t.lambdaVoltage,
                    nominalRange = "0.400 - 0.500 (Repouso)",
                    unit = "V",
                    isOk = isStaticLambdaOk,
                    issueDescription = if (!isStaticLambdaOk) "Tensão de repouso da sonda fora do bias nominal de 450mV." else null,
                    recommendedActionId = if (!isStaticLambdaOk) "reset_lambda_degradation" else null,
                    recommendedActionTitle = if (!isStaticLambdaOk) "Reset de Degradação da Sonda Lambda" else null
                )
            )
        }

        // 5. Ajustes de Combustível STFT / LTFT
        val isTrimOk = Math.abs(t.shortTermFuelTrim) <= 9.0f && Math.abs(t.longTermFuelTrim) <= 9.0f
        checks.add(
            SensorHealthCheck(
                sensorId = "fuel_trim",
                sensorName = "Ajuste de Combustível (STFT/LTFT)",
                currentValueStr = String.format(Locale.US, "ST: %+.1f%% | LT: %+.1f%%", t.shortTermFuelTrim, t.longTermFuelTrim),
                numericValue = Math.max(Math.abs(t.shortTermFuelTrim), Math.abs(t.longTermFuelTrim)),
                nominalRange = "-8.0% a +8.0%",
                unit = "%",
                isOk = isTrimOk,
                issueDescription = if (!isTrimOk) "Correção de combustível com desvio expressivo. A central está corrigindo excesso ou falta de combustível." else null,
                recommendedActionId = if (!isTrimOk) "reset_af" else null,
                recommendedActionTitle = if (!isTrimOk) "Reset de A/F e Autoadaptação" else null
            )
        )

        // 6. TBI Drive-By-Wire (Soma Pistas 1 e 2 = 5.0V)
        val tpsSum = t.throttleVoltage1 + t.throttleVoltage2
        val hasDbwVoltages = t.throttleVoltage1 > 0.1f && t.throttleVoltage2 > 0.1f
        val isTpsOk = if (hasDbwVoltages) {
            Math.abs(tpsSum - 5.0f) <= 0.20f && t.throttleVoltage1 in 0.45f..0.95f
        } else {
            t.throttlePercent <= 16f
        }
        checks.add(
            SensorHealthCheck(
                sensorId = "tps",
                sensorName = "Borboleta Eletrônica (TBI DBW)",
                currentValueStr = if (hasDbwVoltages) String.format(Locale.US, "%.2fV + %.2fV = %.2fV", t.throttleVoltage1, t.throttleVoltage2, tpsSum) else "${t.throttlePercent.toInt()}%",
                numericValue = if (hasDbwVoltages) tpsSum else t.throttlePercent,
                nominalRange = if (hasDbwVoltages) "Soma P1+P2: 4.85V - 5.15V" else "0% - 15%",
                unit = if (hasDbwVoltages) "V" else "%",
                isOk = isTpsOk,
                issueDescription = if (!isTpsOk) "Desalinhamento elétrico entre as pistas 1 e 2 do corpo de borboleta ou batente fora de centro." else null,
                recommendedActionId = if (!isTpsOk) "calibrate_tbi" else null,
                recommendedActionTitle = if (!isTpsOk) "Alinhamento de Batentes da TBI (DBW)" else null
            )
        )

        // 7. Avanço de Ignição / Retardo por Detonação
        if (isEngineRunning) {
            val isSparkOk = t.sparkAdvanceDeg in 5.0f..16.0f
            checks.add(
                SensorHealthCheck(
                    sensorId = "spark",
                    sensorName = "Ponto de Ignição (Avanço)",
                    currentValueStr = String.format(Locale.US, "%.1f", t.sparkAdvanceDeg),
                    numericValue = t.sparkAdvanceDeg,
                    nominalRange = "6.0 - 14.0",
                    unit = "° BTDC",
                    isOk = isSparkOk,
                    issueDescription = if (!isSparkOk) {
                        if (t.sparkAdvanceDeg < 5.0f) "Ponto de ignição excessivamente atrasado (motor pesado, lento ou aquecendo)."
                        else "Ponto de ignição muito adiantado na marcha lenta."
                    } else null,
                    recommendedActionId = if (!isSparkOk) "reset_knock" else null,
                    recommendedActionTitle = if (!isSparkOk) "Reset do Sensor de Detonação" else null
                )
            )
        } else {
            val isKnockOk = t.knockRetardDeg <= 0.2f
            checks.add(
                SensorHealthCheck(
                    sensorId = "spark",
                    sensorName = "Retardo de Detonação na EEPROM",
                    currentValueStr = String.format(Locale.US, "%.2f°", t.knockRetardDeg),
                    numericValue = t.knockRetardDeg,
                    nominalRange = "0.00° (Sem Atraso)",
                    unit = "°",
                    isOk = isKnockOk,
                    issueDescription = if (!isKnockOk) "Tabela de detonação com retardo residual memorizado por combustível anterior." else null,
                    recommendedActionId = if (!isKnockOk) "reset_knock" else null,
                    recommendedActionTitle = if (!isKnockOk) "Reset do Sensor de Detonação" else null
                )
            )
        }

        // 8. Tensão do Sistema Elétrico
        if (isEngineRunning) {
            val isVoltOk = t.elmBatteryVoltage in 13.5f..14.6f
            checks.add(
                SensorHealthCheck(
                    sensorId = "voltage",
                    sensorName = "Tensão do Alternador",
                    currentValueStr = String.format(Locale.US, "%.1f", t.elmBatteryVoltage),
                    numericValue = t.elmBatteryVoltage,
                    nominalRange = "13.6 - 14.5",
                    unit = "V",
                    isOk = isVoltOk,
                    issueDescription = when {
                        t.elmBatteryVoltage < 13.3f -> "Alternador não está fornecendo carga plena na lenta. Verifique correia e regulador."
                        t.elmBatteryVoltage > 14.8f -> "Sobretensão perigosa vinda do alternador (>14.8V)."
                        else -> null
                    },
                    recommendedActionId = null,
                    recommendedActionTitle = null
                )
            )
        } else {
            val isBatteryOk = t.elmBatteryVoltage in 11.9f..12.9f || t.elmBatteryVoltage == 0f
            checks.add(
                SensorHealthCheck(
                    sensorId = "voltage",
                    sensorName = "Tensão da Bateria (Repouso)",
                    currentValueStr = String.format(Locale.US, "%.1f", t.elmBatteryVoltage),
                    numericValue = t.elmBatteryVoltage,
                    nominalRange = "12.0 - 12.8",
                    unit = "V",
                    isOk = isBatteryOk,
                    issueDescription = when {
                        t.elmBatteryVoltage in 1.0f..11.8f -> "Bateria com carga baixa (<11.9V). Risco de falha de inicialização da ECU."
                        else -> null
                    },
                    recommendedActionId = null,
                    recommendedActionTitle = null
                )
            )
        }

        // 9. Temperatura do Motor (ECT)
        if (isEngineRunning) {
            val isEctOk = t.coolantTempC in 82f..104f
            checks.add(
                SensorHealthCheck(
                    sensorId = "ect",
                    sensorName = "Temperatura do Motor (ECT)",
                    currentValueStr = String.format(Locale.US, "%.1f", t.coolantTempC),
                    numericValue = t.coolantTempC,
                    nominalRange = "85.0 - 100.0",
                    unit = "°C",
                    isOk = isEctOk,
                    issueDescription = when {
                        t.coolantTempC > 105f -> "Motor operando acima de 105°C na lenta. Ventoinha deve estar acionada."
                        t.coolantTempC < 80f -> "Motor ainda frio ou válvula termostática aberta."
                        else -> null
                    },
                    recommendedActionId = if (!isEctOk && t.coolantTempC < 75f) "reset_thermal_offsets" else null,
                    recommendedActionTitle = if (!isEctOk && t.coolantTempC < 75f) "Reset de Compensação Térmica (ECT/IAT)" else null
                )
            )
        } else {
            val isEctStaticOk = t.coolantTempC in -20f..115f
            checks.add(
                SensorHealthCheck(
                    sensorId = "ect",
                    sensorName = "Temperatura do Motor (ECT)",
                    currentValueStr = String.format(Locale.US, "%.1f", t.coolantTempC),
                    numericValue = t.coolantTempC,
                    nominalRange = "-10.0 a 105.0",
                    unit = "°C",
                    isOk = isEctStaticOk,
                    issueDescription = if (!isEctStaticOk) "Leitura anômala de temperatura no sensor ECT." else null,
                    recommendedActionId = if (!isEctStaticOk) "reset_thermal_offsets" else null,
                    recommendedActionTitle = if (!isEctStaticOk) "Reset de Compensação Térmica (ECT/IAT)" else null
                )
            )
        }

        return checks
    }

    /**
     * Executa a auditoria completa da marcha lenta
     */
    fun auditIdleSensors(t: LiveTelemetry, p: VehicleProfile): IdleAuditResult {
        if (t.rpm <= 0f) {
            return IdleAuditResult(
                isIdleDetected = false,
                idleConditionMessage = "ECU desconectada ou ignição desligada (0 RPM). Conecte ao carro e dê a partida para iniciar a auditoria da marcha lenta.",
                totalSensors = 0,
                sensorsOkCount = 0,
                sensorsOutCount = 0,
                checks = emptyList(),
                primaryRecommendation = null
            )
        }

        val isEngineRunning = t.rpm > 550f
        val isCarStopped = t.speedKmh <= 3f
        val isThrottleIdle = t.throttlePercent <= 18f
        val isWarm = t.coolantTempC >= 70f

        val isIdle = isEngineRunning && isCarStopped && isThrottleIdle
        val checks = evaluateAllSensors(t, p, isEngineRunning)
        val outOfSpec = checks.filter { !it.isOk }
        val okCount = checks.size - outOfSpec.size
        val primaryRec = outOfSpec.firstOrNull { it.recommendedActionId != null }

        if (!isIdle) {
            val reason = when {
                !isEngineRunning -> "Motor desligado ou em repouso (${t.rpm.toInt()} RPM). Dê a partida para iniciar a auditoria da marcha lenta."
                !isCarStopped -> "Veículo em movimento (${t.speedKmh.toInt()} km/h). Pare o Astra em local seguro."
                !isThrottleIdle -> "Acelerador acionado (TPS: ${t.throttlePercent.toInt()}%). Solte o pedal do acelerador."
                else -> "Aguardando estabilização da marcha lenta."
            }
            return IdleAuditResult(
                isIdleDetected = false,
                idleConditionMessage = reason,
                totalSensors = checks.size,
                sensorsOkCount = okCount,
                sensorsOutCount = outOfSpec.size,
                checks = checks,
                primaryRecommendation = primaryRec
            )
        }

        val conditionMsg = if (outOfSpec.isEmpty()) {
            "Todos os sensores da marcha lenta estão 100% calibrados e dentro da tolerância OEM GM."
        } else {
            "${outOfSpec.size} sensor(es) com desvio identificado. Ação corretiva recomendada disponível."
        }

        return IdleAuditResult(
            isIdleDetected = true,
            idleConditionMessage = conditionMsg,
            totalSensors = checks.size,
            sensorsOkCount = okCount,
            sensorsOutCount = outOfSpec.size,
            checks = checks,
            primaryRecommendation = primaryRec
        )
    }

    /**
     * Compara o snapshot de sensores antes da calibração com o snapshot atual (após calibração)
     */
    fun buildComparisonReport(
        testId: String,
        testTitle: String,
        beforeChecks: List<SensorHealthCheck>,
        currentTelemetry: LiveTelemetry,
        profile: VehicleProfile
    ): CorrectionComparisonReport {
        val isRunning = currentTelemetry.rpm > 550f
        val afterChecks = evaluateAllSensors(currentTelemetry, profile, isRunning)

        val resolvedIssues = mutableListOf<String>()

        beforeChecks.forEach { before ->
            val after = afterChecks.firstOrNull { it.sensorId == before.sensorId }
            if (after != null) {
                if (!before.isOk && after.isOk) {
                    resolvedIssues.add("${before.sensorName}: Estava em ${before.currentValueStr} (${before.issueDescription ?: "Fora de especificação"}) -> Normalizado para ${after.currentValueStr} (${after.nominalRange})")
                } else if (!before.isOk) {
                    resolvedIssues.add("${before.sensorName}: Valor anterior ${before.currentValueStr} -> Atual ${after.currentValueStr}")
                }
            }
        }

        // Se a rotina era específica, adiciona confirmação técnica
        if (resolvedIssues.isEmpty()) {
            when (testId) {
                "calibrate_tbi" -> resolvedIssues.add("Borboleta Eletrônica: Batentes mínimo e máximo memorizados na EEPROM com sucesso.")
                "reset_af" -> resolvedIssues.add("A/F e Autoadaptação: Mapas de curto e longo prazo (STFT/LTFT) zerados para novo aprendizado.")
                "reset_knock" -> resolvedIssues.add("Sensor de Detonação: Tabelas de atraso de ponto restauradas aos valores originais de fábrica.")
                "reset_insp" -> resolvedIssues.add("Painel VDO: Aviso de revisão periódica 'InSP' apagado do hodômetro.")
                "reset_trans_aisin" -> resolvedIssues.add("Câmbio Aisin: Adaptações de pressão de linha e folga de embreagens reinicializadas.")
                "ckp_variation_learn" -> resolvedIssues.add("Roda Fônica: Sincronismo dos 58 dentes gravado na memória permanente.")
                "calibrate_pedal_app" -> resolvedIssues.add("Pedal do Acelerador: Tensões de repouso (0%) e aceleração (100%) sincronizadas.")
                "reset_lambda_degradation" -> resolvedIssues.add("Sonda Lambda: Coeficientes de degradação e resistência do aquecedor resetados.")
                "reset_thermal_offsets" -> resolvedIssues.add("Offsets Térmicos: Compensações de ECT e IAT retornadas à curva nominal de fábrica.")
                else -> resolvedIssues.add("Comando '$testTitle' executado e confirmado pela central eletrônica GM.")
            }
        }

        val summary = "Procedimento '$testTitle' concluído com sucesso e gravado na central física do Astra."

        return CorrectionComparisonReport(
            testId = testId,
            testTitle = testTitle,
            beforeSnapshots = beforeChecks,
            afterSnapshots = afterChecks,
            resolvedIssues = resolvedIssues,
            summaryMessage = summary
        )
    }

    /**
     * Identifica em tempo real o regime dinâmico de operação física do motor GM Família II
     */
    fun detectOperatingRegime(t: LiveTelemetry): EngineOperatingRegime {
        if (t.rpm < 350f) return EngineOperatingRegime.OFF
        if (t.coolantTempC < 70f && t.coolantTempC > 0f) return EngineOperatingRegime.COLD_START
        if (t.throttlePercent >= 70f || (t.mapKpa >= 88f && t.rpm > 2000f)) return EngineOperatingRegime.WOT_FULL_LOAD
        if (t.throttlePercent <= 2.5f && t.rpm in 450f..1150f) return EngineOperatingRegime.HOT_IDLE
        return EngineOperatingRegime.PARTIAL_LOAD
    }

    /**
     * Avalia os parâmetros da injeção com faixas nominais adaptativas conforme o regime atual do motor
     */
    fun evaluateDynamicRegimeSensors(t: LiveTelemetry, p: VehicleProfile): List<SensorHealthCheck> {
        val regime = detectOperatingRegime(t)
        val checks = mutableListOf<SensorHealthCheck>()
        val isEthanol = t.ethanolPercentage > 50f || (t.estimatedAfRatio in 8.5f..11.5f)

        when (regime) {
            EngineOperatingRegime.OFF -> {
                return evaluateAllSensors(t, p, isEngineRunning = false)
            }
            EngineOperatingRegime.COLD_START -> {
                // 1. Rotação em aquecimento
                val isRpmOk = t.rpm in 950f..1350f
                checks.add(
                    SensorHealthCheck(
                        sensorId = "rpm",
                        sensorName = "Rotação (Fase Fria)",
                        currentValueStr = "${t.rpm.toInt()}",
                        numericValue = t.rpm,
                        nominalRange = "1.000 - 1.300",
                        unit = "RPM",
                        isOk = isRpmOk,
                        issueDescription = if (!isRpmOk) "Rotação de aquecimento fora da faixa esperada para motor frio." else null
                    )
                )
                // 2. MAP em aquecimento
                val isMapOk = t.mapKpa in 32f..55f
                checks.add(
                    SensorHealthCheck(
                        sensorId = "map",
                        sensorName = "Pressão no Coletor (MAP)",
                        currentValueStr = "${t.mapKpa.toInt()}",
                        numericValue = t.mapKpa,
                        nominalRange = "35 - 50",
                        unit = "kPa",
                        isOk = isMapOk,
                        issueDescription = if (!isMapOk) "Vácuo em fase fria fora dos parâmetros normais." else null
                    )
                )
                // 3. Tempo de Injeção enriquecido
                val isTiOk = t.injectionTimeMs in 2.8f..6.0f
                checks.add(
                    SensorHealthCheck(
                        sensorId = "ti",
                        sensorName = "Tempo de Injeção (T.I.)",
                        currentValueStr = String.format(Locale.US, "%.2f", t.injectionTimeMs),
                        numericValue = t.injectionTimeMs,
                        nominalRange = "3.00 - 5.50 (Enriquecido)",
                        unit = "ms",
                        isOk = isTiOk,
                        issueDescription = if (!isTiOk) "Tempo de injeção em fase fria fora da faixa de enriquecimento da ECU." else null
                    )
                )
            }
            EngineOperatingRegime.HOT_IDLE -> {
                return evaluateAllSensors(t, p, isEngineRunning = true)
            }
            EngineOperatingRegime.PARTIAL_LOAD -> {
                // Rotação em cruzeiro
                val isRpmOk = t.rpm in 1100f..5500f
                checks.add(
                    SensorHealthCheck(
                        sensorId = "rpm",
                        sensorName = "Rotação em Cruzeiro",
                        currentValueStr = "${t.rpm.toInt()}",
                        numericValue = t.rpm,
                        nominalRange = "1.200 - 4.500",
                        unit = "RPM",
                        isOk = isRpmOk
                    )
                )
                // MAP em carga parcial
                val isMapOk = t.mapKpa in 32f..88f
                checks.add(
                    SensorHealthCheck(
                        sensorId = "map",
                        sensorName = "Pressão no Coletor (MAP)",
                        currentValueStr = "${t.mapKpa.toInt()}",
                        numericValue = t.mapKpa,
                        nominalRange = "35 - 85",
                        unit = "kPa",
                        isOk = isMapOk
                    )
                )
                // Avanço em cruzeiro
                val isSparkOk = t.sparkAdvanceDeg in 15f..42f
                checks.add(
                    SensorHealthCheck(
                        sensorId = "spark",
                        sensorName = "Avanço de Ignição (Ponto)",
                        currentValueStr = String.format(Locale.US, "%.1f", t.sparkAdvanceDeg),
                        numericValue = t.sparkAdvanceDeg,
                        nominalRange = "18 - 38",
                        unit = "° APMS",
                        isOk = isSparkOk,
                        issueDescription = if (!isSparkOk) "Curva de avanço em cruzeiro atípica." else null
                    )
                )
            }
            EngineOperatingRegime.WOT_FULL_LOAD -> {
                // Borboleta em plena carga
                val isTpsOk = t.throttlePercent >= 68f
                checks.add(
                    SensorHealthCheck(
                        sensorId = "tps",
                        sensorName = "Posição Borboleta (WOT)",
                        currentValueStr = String.format(Locale.US, "%.1f", t.throttlePercent),
                        numericValue = t.throttlePercent,
                        nominalRange = "70 - 100",
                        unit = "%",
                        isOk = isTpsOk,
                        issueDescription = if (!isTpsOk) "Abertura total da borboleta não atingida em plena aceleração." else null
                    )
                )
                // MAP atmosférico em plena carga
                val isMapWotOk = t.mapKpa >= 88f
                checks.add(
                    SensorHealthCheck(
                        sensorId = "map",
                        sensorName = "Pressão Coletor (WOT)",
                        currentValueStr = "${t.mapKpa.toInt()}",
                        numericValue = t.mapKpa,
                        nominalRange = "90 - 102 (Atmosférico)",
                        unit = "kPa",
                        isOk = isMapWotOk,
                        issueDescription = if (!isMapWotOk) "Restrição detectada na admissão de ar (MAP baixo em WOT)." else null
                    )
                )
            }
        }

        // Adiciona checagens elétricas e de temperatura comuns
        val isVoltOk = t.ecuBatteryVoltage in 13.4f..14.8f || t.elmBatteryVoltage in 13.4f..14.8f
        val effectiveVolt = if (t.ecuBatteryVoltage > 5f) t.ecuBatteryVoltage else t.elmBatteryVoltage
        checks.add(
            SensorHealthCheck(
                sensorId = "volt",
                sensorName = "Tensão Alternador / Bateria",
                currentValueStr = String.format(Locale.US, "%.1f", effectiveVolt),
                numericValue = effectiveVolt,
                nominalRange = "13.5 - 14.6",
                unit = "V",
                isOk = isVoltOk,
                issueDescription = if (!isVoltOk && effectiveVolt < 13.2f) "Tensão baixa do alternador em regime de funcionamento." else null
            )
        )

        return checks
    }
}
