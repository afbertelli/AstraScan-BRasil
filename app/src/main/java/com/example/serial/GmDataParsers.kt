package com.example.serial

import com.example.model.DtcCode
import com.example.model.LiveTelemetry
import com.example.model.VehicleProfile
import java.util.Locale

/**
 * Módulo desacoplado de conversão puramente matemática de respostas hexadecimais GM e OBD-II.
 * Cobre todos os parâmetros de injeção, ignição, emissões, atuadores e sensores da Família II.
 */
object GmDataParsers {

    /**
     * Sanitiza a resposta bruta do ELM327
     */
    fun cleanResponse(raw: String): String {
        return raw
            .replace(">", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("SEARCHING...", "")
            .replace("BUS INIT: OK", "")
            .replace("BUS INIT: ERROR", "")
            .replace("STOPPED", "")
            .replace("UNABLE TO CONNECT", "")
            .replace("NO DATA", "")
            .trim()
    }

    /**
     * Extrai bytes hexadecimais como lista de inteiros com proteção contra cabeçalhos CAN e contadores de frame
     */
    fun extractHexBytes(raw: String): List<Int> {
        val cleaned = cleanResponse(raw)
        if (cleaned.isBlank()) return emptyList()

        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        val bytes = mutableListOf<Int>()

        val validTokens = mutableListOf<String>()
        for (token in tokens) {
            val t = token.trim()
            // Descarta identificadores CAN de 3 caracteres (ex: 7E8, 7E9, 7EA, 7DF) e índices de linha (ex: 0:, 1:, 2:)
            if (t.matches(Regex("^[0-9A-Fa-f]{3}$")) || t.matches(Regex("^[0-9A-Fa-f]:$"))) {
                continue
            }
            if (t.length == 2 && t.toIntOrNull(16) != null) {
                validTokens.add(t)
            } else if (t.length > 2 && t.length % 2 == 0 && t.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) {
                var idx = 0
                while (idx + 1 < t.length) {
                    validTokens.add(t.substring(idx, idx + 2))
                    idx += 2
                }
            } else if (t.length == 1 && t.toIntOrNull(16) != null) {
                validTokens.add("0$t")
            }
        }

        if (validTokens.isNotEmpty()) {
            for (vt in validTokens) {
                vt.toIntOrNull(16)?.let { bytes.add(it) }
            }
            return bytes
        }

        // Fallback para fluxo compacto sem espaços
        val compacted = cleaned.replace(" ", "")
        if (compacted.length < 2) return emptyList()
        var i = 0
        while (i + 1 < compacted.length) {
            val byteVal = compacted.substring(i, i + 2).toIntOrNull(16)
            if (byteVal != null) {
                bytes.add(byteVal)
            }
            i += 2
        }
        return bytes
    }

    /**
     * Verifica se a resposta é um código de resposta negativa (NRC), erro ou ausência de dados.
     * Padrão ISO 14230 / KWP2000 / UDS: 7F <ServiceID> <NRC> (ex: 7F 01 11 = ServiceNotSupported).
     * ATENÇÃO CRÍTICA: Nunca deve classificar como erro bytes de dados legítimos de valor 127 (0x7F),
     * como temperatura de 87°C (41 05 7F), TPS 50% (41 11 7F) ou combustível/MAP.
     */
    fun isNegativeResponse(raw: String): Boolean {
        val upper = raw.uppercase().trim()
        if (upper.contains("NO DATA") ||
            upper.contains("ERROR") ||
            upper.contains("UNABLE") ||
            upper.contains("STOPPED") ||
            upper.contains("BUS INIT: ERROR") ||
            upper.startsWith("?") ||
            upper == "?"
        ) {
            return true
        }
        val bytes = extractHexBytes(raw)
        if (bytes.size >= 2) {
            // Caso 1: Começa diretamente com NRC UDS/KWP (7F <ServiceId> <NRC>)
            if (bytes[0] == 0x7F) {
                return true
            }
            // Caso 2: Resposta com cabeçalho onde 7F indica NRC para um serviço específico
            val validServices = setOf(
                0x01, 0x02, 0x03, 0x04, 0x07, 0x09, 0x0A,
                0x14, 0x18, 0x1A, 0x21, 0x22, 0x30, 0x31, 0x3E
            )
            for (idx in 1 until bytes.size - 1) {
                // Se 7F for precedido por uma resposta positiva (como 41h ou 61h), 7F é apenas DADO!
                val prev = bytes[idx - 1]
                if (prev == 0x41 || prev == 0x61 || prev == 0x49 || prev == 0x5A) {
                    continue
                }
                if (bytes[idx] == 0x7F && bytes[idx + 1] in validServices) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Rotação do Motor (RPM): 01 0C ou 21 02 -> ((A * 256) + B) / 4
     */
    fun parseRpm(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 2) {
            if ((bytes[idx] == 0x41 && bytes[idx + 1] == 0x0C) ||
                (bytes[idx] == 0x61 && bytes[idx + 1] == 0x02)
            ) {
                val a = bytes[idx + 2]
                val b = if (idx + 3 < bytes.size) bytes[idx + 3] else 0
                val rpm = ((a * 256) + b) / 4.0f
                if (rpm in 0f..8500f) return rpm
            }
        }
        return null
    }

    /**
     * Rotação Alvo da Marcha Lenta (Target Idle RPM): 21 04 -> ((A * 256) + B) / 4 ou A * 10
     */
    fun parseTargetIdleRpm(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x04) {
                if (idx + 3 < bytes.size) {
                    val a = bytes[idx + 2]
                    val b = bytes[idx + 3]
                    return ((a * 256) + b) / 4.0f
                } else if (idx + 2 < bytes.size) {
                    return bytes[idx + 2] * 10.0f
                }
            }
        }
        return null
    }

    /**
     * Velocidade do Veículo: 01 0D -> 41 0D [A] -> A km/h
     */
    fun parseSpeed(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x0D && idx + 2 < bytes.size) {
                val spd = bytes[idx + 2].toFloat()
                if (spd in 0f..260f) return spd
            }
        }
        return null
    }

    /**
     * Carga Calculada do Motor (Engine Load %): 01 04 -> (A * 100) / 255 %
     */
    fun parseEngineLoad(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x04 && idx + 2 < bytes.size) {
                return (bytes[idx + 2] * 100.0f) / 255.0f
            }
        }
        return null
    }

    /**
     * Carga Absoluta (Absolute Load %): 01 43 -> ((A * 256) + B) * 100 / 255
     */
    fun parseAbsoluteLoad(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 2) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x43) {
                val a = bytes[idx + 2]
                val b = bytes[idx + 3]
                return ((a * 256) + b) * 100.0f / 255.0f
            }
        }
        return null
    }

    /**
     * Temperatura do Arrefecimento (ECT): 01 05 ou 21 01 -> A - 40 °C
     */
    fun parseCoolantTemp(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if ((bytes[idx] == 0x41 && bytes[idx + 1] == 0x05) ||
                (bytes[idx] == 0x61 && bytes[idx + 1] == 0x01)
            ) {
                if (idx + 2 < bytes.size) {
                    val temp = (bytes[idx + 2] - 40).toFloat()
                    if (temp in -40f..140f) return temp
                }
            }
        }
        return null
    }

    /**
     * Temperatura do Ar de Admissão (IAT): 01 0F -> A - 40 °C
     */
    fun parseIat(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x0F && idx + 2 < bytes.size) {
                return (bytes[idx + 2] - 40).toFloat()
            }
        }
        return null
    }

    /**
     * Pressão Absoluta do Coletor (MAP): 01 0B ou 21 0A -> A kPa
     */
    fun parseMap(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (((bytes[idx] == 0x41 && bytes[idx + 1] == 0x0B) ||
                 (bytes[idx] == 0x61 && bytes[idx + 1] == 0x0A)) && idx + 2 < bytes.size
            ) {
                val map = bytes[idx + 2].toFloat()
                if (map in 0f..255f) return map
            }
        }
        return null
    }

    /**
     * Pressão Barométrica / Atmosférica (BARO): 01 33 ou 21 0E -> A kPa
     */
    fun parseBaro(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if ((bytes[idx] == 0x41 && bytes[idx + 1] == 0x33) ||
                (bytes[idx] == 0x61 && bytes[idx + 1] == 0x0E)
            ) {
                if (idx + 2 < bytes.size) {
                    return bytes[idx + 2].toFloat()
                }
            }
        }
        return null
    }

    /**
     * Vácuo do Coletor Derivado: (BARO - MAP) * 0.01 bar
     */
    fun calculateVacuum(mapKpa: Float, baroKpa: Float = 101.3f): Float {
        if (mapKpa.isNaN() || baroKpa.isNaN() || mapKpa.isInfinite() || baroKpa.isInfinite()) return 0f
        val v = (baroKpa - mapKpa) * 0.01f
        return if (v < 0f) 0f else v
    }

    /**
     * Fluxo de Ar Estimado / MAF: 01 10 -> ((A * 256) + B) / 100 g/s
     */
    fun parseMaf(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 2) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x10) {
                val a = bytes[idx + 2]
                val b = bytes[idx + 3]
                return ((a * 256) + b) / 100.0f
            }
        }
        return null
    }

    /**
     * Estimativa de MAF via Speed-Density para motores GM sem sensor MAF físico (Delphi Multec H)
     * Vazão de ar (g/s) = (RPM * Deslocamento * Eficiência_Volumetrica * MAP) / (2 * 60 * R * (IAT + 273.15))
     */
    fun estimateSpeedDensityMaf(rpm: Float, mapKpa: Float, iatC: Float, displacementLiters: Float = 2.0f): Float {
        if (rpm.isNaN() || mapKpa.isNaN() || iatC.isNaN() || displacementLiters.isNaN() ||
            rpm.isInfinite() || mapKpa.isInfinite() || iatC.isInfinite() || displacementLiters.isInfinite() ||
            rpm <= 0f || mapKpa <= 0f) return 0f
        val tempKelvin = (iatC + 273.15f).coerceAtLeast(250f)
        // Eficiência volumétrica típica Família II (78% a 85%)
        val volumetricEfficiency = 0.82f
        val airMassPerStroke = (mapKpa * 1000.0f * (displacementLiters / 1000.0f) * volumetricEfficiency) / (287.05f * tempKelvin)
        // 4 tempos: 2 revoluções por ciclo completo
        val massFlowGramsPerSec = (airMassPerStroke * (rpm / 120.0f) * 1000.0f)
        return massFlowGramsPerSec.coerceIn(0f, 250f)
    }

    /**
     * Calcula a Eficiência Volumétrica (VE %) estimada em tempo real para os motores GM Família II (8V e 16V).
     * Cruza a pressão no coletor (MAP), barômetro, temperatura de ar (IAT) e curva de respiração mecânica do comando.
     */
    fun calculateVolumetricEfficiency(
        rpm: Float,
        mapKpa: Float,
        baroKpa: Float = 101.3f,
        iatC: Float = 25.0f,
        is16V: Boolean = false
    ): Float {
        if (rpm.isNaN() || mapKpa.isNaN() || rpm < 350f || mapKpa <= 0f) return 0f
        val effectiveBaro = if (baroKpa > 50f) baroKpa else 101.3f
        val pressureRatio = (mapKpa / effectiveBaro).coerceIn(0.10f, 1.10f)

        // Fator de respiração mecânica conforme perfil de comando de válvulas (8V SOHC vs 16V DOHC)
        val peakRpm = if (is16V) 4000f else 3000f
        val rpmDeviation = Math.abs(rpm - peakRpm)
        val camBreathingFactor = (1.0f - (rpmDeviation / 5000f).coerceIn(0f, 0.28f))

        // Densidade do ar pela temperatura admitida (compensação térmica IAT)
        val tempKelvin = (iatC + 273.15f).coerceIn(240f, 350f)
        val thermalDensityFactor = (298.15f / tempKelvin).coerceIn(0.85f, 1.15f)

        val maxTheoreticalVe = if (is16V) 92.0f else 86.0f
        val calculatedVe = maxTheoreticalVe * pressureRatio * camBreathingFactor * thermalDensityFactor
        return calculatedVe.coerceIn(0f, 105f)
    }

    /**
     * Abertura da Borboleta (TPS %): 01 11 -> (A * 100) / 255 %
     */
    fun parseTpsPercent(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x11 && idx + 2 < bytes.size) {
                return (bytes[idx + 2] * 100.0f) / 255.0f
            }
        }
        return null
    }

    /**
     * Tensão do Sensor TPS 1: 21 12 -> 61 12 [A] -> A * 0.0196 V
     */
    fun parseTpsVoltage(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x12 && idx + 2 < bytes.size) {
                return bytes[idx + 2] * 0.0196f
            }
        }
        return null
    }

    /**
     * Tensão do Sensor TPS 2 (Drive-By-Wire): 21 13 -> 61 13 [A] -> A * 0.0196 V
     */
    fun parseTpsVoltage2(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x13 && idx + 2 < bytes.size) {
                return bytes[idx + 2] * 0.0196f
            }
        }
        return null
    }

    /**
     * Posição do Pedal do Acelerador (APP %): 01 49 / 01 4A -> (A * 100) / 255 %
     */
    fun parsePedalPercent(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if ((bytes[idx] == 0x41 && (bytes[idx + 1] == 0x49 || bytes[idx + 1] == 0x4A)) && idx + 2 < bytes.size) {
                return (bytes[idx + 2] * 100.0f) / 255.0f
            }
        }
        return null
    }

    /**
     * Tempo de Injeção (T.I.): 21 15 -> 61 15 [A] [B] -> ((A * 256) + B) * 0.001 ms
     */
    fun parseInjectionTime(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 2) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x15) {
                val a = bytes[idx + 2]
                val b = if (idx + 3 < bytes.size) bytes[idx + 3] else 0
                return ((a * 256) + b) * 0.001f
            }
        }
        return null
    }

    /**
     * Duty Cycle dos Bicos Injetores (%):
     * Em motor 4 tempos sequencial, o tempo disponível por ciclo é (120000 / RPM) ms.
     * Duty Cycle (%) = (T.I. ms / (120000 / RPM)) * 100 = (T.I. ms * RPM) / 1200.0
     */
    fun calculateInjectorDutyCycle(tiMs: Float, rpm: Float): Float {
        if (rpm.isNaN() || tiMs.isNaN() || rpm.isInfinite() || tiMs.isInfinite() || rpm <= 0f || tiMs <= 0f) return 0f
        val duty = (tiMs * rpm) / 1200.0f
        return duty.coerceIn(0f, 100f)
    }

    /**
     * Passo da Marcha Lenta (IAC): 21 19 -> 61 19 [A] -> A Passos
     */
    fun parseIacSteps(raw: String): Int? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x19 && idx + 2 < bytes.size) {
                return bytes[idx + 2]
            }
        }
        return null
    }

    /**
     * Avanço de Ignição: 01 0E -> 41 0E [A] -> (A / 2.0) - 64 Graus
     */
    fun parseSparkAdvance(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x0E && idx + 2 < bytes.size) {
                return (bytes[idx + 2] / 2.0f) - 64.0f
            }
        }
        return null
    }

    /**
     * Sonda Lambda 1 (Tensão Pré-Catalisador): 01 14 -> 41 14 [A] [B] -> A * 0.005 V
     */
    fun parseLambdaVoltage(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x14 && idx + 2 < bytes.size) {
                return bytes[idx + 2] * 0.005f
            }
        }
        return null
    }

    /**
     * Sonda Lambda 2 (Tensão Pós-Catalisador): 01 15 -> 41 15 [A] [B] -> A * 0.005 V
     */
    fun parseLambdaPostCatVoltage(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x15 && idx + 2 < bytes.size) {
                return bytes[idx + 2] * 0.005f
            }
        }
        return null
    }

    /**
     * Fator Lambda / Equivalence Ratio (λ): 01 44 -> ((A * 256) + B) / 32768.0
     */
    fun parseEquivalenceRatioLambda(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 2) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x44) {
                val a = bytes[idx + 2]
                val b = bytes[idx + 3]
                return ((a * 256) + b) / 32768.0f
            }
        }
        return null
    }

    /**
     * Status da Malha de Combustível: 01 03 -> 01=Aberta, 02=Fechada
     */
    fun parseFuelLoopStatus(raw: String): String? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x03 && idx + 2 < bytes.size) {
                return when (bytes[idx + 2]) {
                    1 -> "Malha Aberta (Open Loop)"
                    2 -> "Malha Fechada (Closed Loop)"
                    4 -> "Aberta por Aceleração Total (WOT)"
                    8 -> "Aberta por Falha no Sistema"
                    else -> "Status: 0x${bytes[idx + 2].toString(16)}"
                }
            }
        }
        return null
    }

    /**
     * Short Term Fuel Trim (STFT): 01 06 -> (A - 128) * 100 / 128 %
     */
    fun parseStft(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x06 && idx + 2 < bytes.size) {
                return (bytes[idx + 2] - 128) * 100.0f / 128.0f
            }
        }
        return null
    }

    /**
     * Long Term Fuel Trim (LTFT): 01 07 -> (A - 128) * 100 / 128 %
     */
    fun parseLtft(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x07 && idx + 2 < bytes.size) {
                return (bytes[idx + 2] - 128) * 100.0f / 128.0f
            }
        }
        return null
    }

    /**
     * Ajuste em Marcha Lenta (Idle Fuel Trim): 21 08 -> (A - 128) * 100 / 128 %
     */
    fun parseIdleTrim(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x08 && idx + 2 < bytes.size) {
                return (bytes[idx + 2] - 128) * 100.0f / 128.0f
            }
        }
        return null
    }

    /**
     * Razão A/F Estimada (Flexpower): 21 35 -> 61 35 [A] -> A / 10.0 (:1)
     */
    fun parseAfRatio(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x35 && idx + 2 < bytes.size) {
                return bytes[idx + 2] / 10.0f
            }
        }
        return null
    }

    /**
     * Porcentagem de Álcool (Flexpower): 21 36 -> 61 36 [A] -> (A * 100) / 255 % Etanol
     */
    fun parseEthanolPercent(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x36 && idx + 2 < bytes.size) {
                return (bytes[idx + 2] * 100.0f) / 255.0f
            }
        }
        return null
    }

    /**
     * Retardo por Detonação Geral (Knock): 21 4A ou 21 17 -> 61 4A/17 [A] -> A * 0.05 Graus
     */
    fun parseKnockRetard(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && (bytes[idx + 1] == 0x4A || bytes[idx + 1] == 0x17) && idx + 2 < bytes.size) {
                return bytes[idx + 2] * 0.05f
            }
        }
        return null
    }

    /**
     * Retardo por Detonação por Cilindro (Cil 1: 21 4B, Cil 2: 21 4C, Cil 3: 21 4D, Cil 4: 21 4E)
     */
    fun parseKnockCylinder(raw: String, subService: Int): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == subService && idx + 2 < bytes.size) {
                return bytes[idx + 2] * 0.05f
            }
        }
        return null
    }

    /**
     * Válvula de Purga do Canister (EVAP Purge %): 01 2E ou 21 2E -> (A * 100) / 255 %
     */
    fun parseEvapPurge(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if ((bytes[idx] == 0x41 || bytes[idx] == 0x61) && bytes[idx + 1] == 0x2E && idx + 2 < bytes.size) {
                return (bytes[idx + 2] * 100.0f) / 255.0f
            }
        }
        return null
    }

    /**
     * Tensão da Bateria (Lida pela ECU):
     * Suporta Mode 01 42 (41 42 [A][B] -> ((A*256)+B)/1000 V) e Mode 21 42 (61 42 [A] -> A * 0.1 V)
     */
    fun parseEcuBatteryVoltage(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x41 && bytes[idx + 1] == 0x42 && idx + 3 < bytes.size) {
                val a = bytes[idx + 2]
                val b = bytes[idx + 3]
                val v = ((a * 256) + b) / 1000.0f
                if (v in 5f..20f) return v
            }
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x42) {
                if (idx + 3 < bytes.size) {
                    val a = bytes[idx + 2]
                    val b = bytes[idx + 3]
                    val v = ((a * 256) + b) / 1000.0f
                    if (v in 5f..20f) return v
                }
                if (idx + 2 < bytes.size) {
                    val v = bytes[idx + 2] * 0.1f
                    if (v in 5f..20f) return v
                }
            }
        }
        return null
    }

    /**
     * Tensão no Adaptador ELM327: AT RV -> regex <xx.xV>
     */
    fun parseElmVoltage(raw: String): Float? {
        if (isNegativeResponse(raw)) return null
        val regex = Regex("""(\d+\.?\d*)\s*V""", RegexOption.IGNORE_CASE)
        val match = regex.find(raw)
        if (match != null) {
            return match.groupValues[1].toFloatOrNull()
        }
        val cleaned = raw.replace("V", "").replace("v", "").trim()
        return cleaned.toFloatOrNull()
    }

    /**
     * Cálculo de Consumo Instantâneo em Litros por Hora (L/h)
     * Baseado na vazão dos injetores Família II (~190 cc/min a 3.0 bar de pressão de linha)
     * e no tempo de injeção por ciclo de 4 cilindros.
     */
    fun calculateLitersPerHour(tiMs: Float, rpm: Float, injectorFlowCcMin: Float = 190.0f): Float {
        if (rpm.isNaN() || tiMs.isNaN() || injectorFlowCcMin.isNaN() ||
            rpm.isInfinite() || tiMs.isInfinite() || injectorFlowCcMin.isInfinite() ||
            rpm <= 0f || tiMs <= 0f) return 0f
        // 4 cilindros em injeção sequencial: cada injetor pulsa 1 vez a cada 2 rotações (rpm / 2 vezes por minuto)
        // Volume em cc por minuto = 4 * (rpm / 2) * (tiMs / 60000) * injectorFlowCcMin
        val totalCcPerMin = 4.0f * (rpm / 2.0f) * (tiMs / 60000.0f) * injectorFlowCcMin
        val litersPerHour = (totalCcPerMin * 60.0f) / 1000.0f
        return litersPerHour.coerceIn(0f, 60f)
    }

    /**
     * Cálculo de Consumo Instantâneo em km/L
     */
    fun calculateKmPerLiter(speedKmh: Float, litersPerHour: Float): Float {
        if (speedKmh.isNaN() || litersPerHour.isNaN() || speedKmh.isInfinite() || litersPerHour.isInfinite() ||
            speedKmh <= 2f || litersPerHour <= 0.05f) return 0f
        val kmPerL = speedKmh / litersPerHour
        return kmPerL.coerceIn(0f, 40f)
    }

    /**
     * Decodifica código de falha DTC a partir de 2 bytes hex (ex: 01 05 -> P0105)
     */
    fun decodeDtc(byte1: Int, byte2: Int): String? {
        if (byte1 == 0 && byte2 == 0) return null

        val prefix = when ((byte1 shr 6) and 0x03) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            3 -> "U"
            else -> "P"
        }

        val digit1 = (byte1 shr 4) and 0x03
        val digit2 = byte1 and 0x0F
        val digit3 = (byte2 shr 4) and 0x0F
        val digit4 = byte2 and 0x0F

        return String.format(Locale.US, "%s%d%X%X%X", prefix, digit1, digit2, digit3, digit4)
    }

    /**
     * Decodifica lista de DTCs a partir de fluxo de bytes brutos do Modo 03 (43h) ou Modo 07 (47h).
     * Suporta tanto o formato direto SAE J1979 (43 [DTC1_H] [DTC1_L]...) quanto o formato com contador (43 [num] [DTC1_H] [DTC1_L]...).
     */
    fun parseDtcBytes(bytes: List<Int>, expectedModeAck: Int, isPresent: Boolean = true): List<DtcCode> {
        if (bytes.size < 3 || bytes[0] != expectedModeAck) return emptyList()

        val codes = mutableListOf<DtcCode>()
        val seenCodes = mutableSetOf<String>()

        // Testa se o frame possui byte de contagem (ex: 43 01 01 05 -> tamanho par e bytes[1] == (bytes.size-2)/2)
        val hasCountByte = bytes.size % 2 == 0 && bytes[1] in 1..8 && (bytes[1] * 2 + 2 <= bytes.size)
        val startIndex = if (hasCountByte) 2 else 1

        var i = startIndex
        while (i + 1 < bytes.size) {
            val b1 = bytes[i]
            val b2 = bytes[i + 1]
            val codeStr = decodeDtc(b1, b2)
            if (codeStr != null && codeStr != "P0000" && !seenCodes.contains(codeStr)) {
                seenCodes.add(codeStr)
                codes.add(mapDtcDetails(codeStr, isPresent = isPresent))
            }
            i += 2
        }

        // Se começar no startIndex não encontrou nada mas o tamanho é par, tenta o fallback pelo outro índice
        if (codes.isEmpty() && startIndex == 2 && bytes.size >= 3) {
            var j = 1
            while (j + 1 < bytes.size) {
                val b1 = bytes[j]
                val b2 = bytes[j + 1]
                val codeStr = decodeDtc(b1, b2)
                if (codeStr != null && codeStr != "P0000" && !seenCodes.contains(codeStr)) {
                    seenCodes.add(codeStr)
                    codes.add(mapDtcDetails(codeStr, isPresent = isPresent))
                }
                j += 2
            }
        }

        return codes
    }

    /**
     * Mapeia código DTC para detalhes técnicos GM Família II / Motronic
     */
    fun mapDtcDetails(code: String, isPresent: Boolean = true): DtcCode {
        return when (code) {
            "P0100" -> DtcCode(
                code = code,
                title = "Circuito do Sensor MAF (Fluxo de Ar)",
                system = "Admissão / Ar",
                description = "Falha no medidor de massa de ar.",
                symptoms = "Hesitação na aceleração, perda de rendimento.",
                probableCauses = "Filamento do MAF sujo ou conector frouxo.",
                recommendedFix = "Limpar filamento com limpa-contatos específico ou trocar sensor MAF.",
                isPresent = isPresent
            )
            "P0105" -> DtcCode(
                code = code,
                title = "Circuito do Sensor MAP",
                system = "Admissão / Ar",
                description = "Falha no sensor de pressão absoluta do coletor.",
                symptoms = "Marcha lenta irregular, alto consumo.",
                probableCauses = "Mangueira de vácuo solta ou sensor com defeito.",
                recommendedFix = "Verificar mangueira de vácuo do TBI e fiação.",
                isPresent = isPresent
            )
            "P0110" -> DtcCode(
                code = code,
                title = "Circuito do Sensor IAT",
                system = "Admissão / Ar",
                description = "Tensão fora da faixa no sensor de temperatura do ar.",
                symptoms = "Dificuldade na partida a frio.",
                probableCauses = "Plugue IAT desconectado ou sensor quebrado.",
                recommendedFix = "Conectar chicote ou substituir sensor IAT.",
                isPresent = isPresent
            )
            "P0115" -> DtcCode(
                code = code,
                title = "Circuito do Sensor ECT (Temperatura da Água)",
                system = "Arrefecimento",
                description = "Falha de leitura do sensor de temperatura da água da ECU.",
                symptoms = "Eletroventilador disparado, motor afogado, partida pesada.",
                probableCauses = "Sensor ECT com resistência alterada ou conector chicote 2 vias oxidado.",
                recommendedFix = "Trocar conector e sensor ECT do cavalete d'água.",
                isPresent = isPresent
            )
            "P0120" -> DtcCode(
                code = code,
                title = "Circuito do Sensor TPS (Posição da Borboleta)",
                system = "Borboleta de Aceleração",
                description = "Mau funcionamento no potenciômetro da borboleta.",
                symptoms = "Hesitação ao acelerar, corte de giro, trancos nas retomadas.",
                probableCauses = "Pista resistiva gasta ou folga mecânica.",
                recommendedFix = "Calibrar ou substituir potenciômetro TPS.",
                isPresent = isPresent
            )
            "P0121" -> DtcCode(
                code = code,
                title = "Sensor TPS - Faixa / Rendimento Incoerente",
                system = "Borboleta de Aceleração",
                description = "Sinal de abertura da borboleta em desacordo com rotação e vácuo do coletor.",
                symptoms = "Luz de injeção acesa, marcha lenta acelerada.",
                probableCauses = "Carbonização severa no corpo TBI ou batente mecânico desajustado.",
                recommendedFix = "Efetuar limpeza descarbonizante da TBI e reaprender batentes com o scanner.",
                isPresent = isPresent
            )
            "P0122" -> DtcCode(
                code = code,
                title = "Sensor TPS - Tensão Baixa / Curto à Massa",
                system = "Borboleta de Aceleração",
                description = "Tensão do sinal do TPS abaixo de 0.2V.",
                symptoms = "Motor sem aceleração, falha de resposta ao pedal.",
                probableCauses = "Fio de sinal partido ou sensor em curto interno.",
                recommendedFix = "Conferir alimentação 5V e continuidade da fiação até a ECU.",
                isPresent = isPresent
            )
            "P0123" -> DtcCode(
                code = code,
                title = "Sensor TPS - Tensão Alta / Circuito Aberto",
                system = "Borboleta de Aceleração",
                description = "Tensão do sinal do TPS acima de 4.8V constante.",
                symptoms = "ECU assume borboleta 100% aberta, afogando motor.",
                probableCauses = "Fio de aterramento do sensor interrompido.",
                recommendedFix = "Verificar aterramento no chicote da injeção.",
                isPresent = isPresent
            )
            "P0130" -> DtcCode(
                code = code,
                title = "Circuito da Sonda Lambda (Sensor 1 Pré-Cat)",
                system = "Combustão & Emissões",
                description = "Sinal incorreto ou sem oscilação na sonda pré-catalisador.",
                symptoms = "Luz de injeção acesa, fumaça preta no escape, consumo alto.",
                probableCauses = "Sensor de oxigênio contaminado ou chicote em curto.",
                recommendedFix = "Testar oscilação de 100mV a 900mV e substituir se travada.",
                isPresent = isPresent
            )
            "P0135" -> DtcCode(
                code = code,
                title = "Aquecedor da Sonda Lambda 1 (Heater)",
                system = "Combustão & Emissões",
                description = "Resistência de pré-aquecimento da sonda lambda rompida.",
                symptoms = "Demora em entrar em malha fechada a frio, consumo elevado.",
                probableCauses = "Resistência ôhmica interna queimada ou fusível aberto.",
                recommendedFix = "Medir resistência nos fios brancos (4 a 14 ohms); substituir sonda.",
                isPresent = isPresent
            )
            "P0136" -> DtcCode(
                code = code,
                title = "Circuito da Sonda Lambda (Sensor 2 Pós-Cat)",
                system = "Combustão & Emissões",
                description = "Falha no circuito do sensor de oxigênio pós-catalisador.",
                symptoms = "Luz de injeção acesa permanente.",
                probableCauses = "Fiação raspando no assoalho ou sonda pós-cat inoperante.",
                recommendedFix = "Inspecionar chicote inferior e substituir sensor pós-catalisador.",
                isPresent = isPresent
            )
            "P0141" -> DtcCode(
                code = code,
                title = "Aquecedor da Sonda Lambda 2 (Pós-Cat)",
                system = "Combustão & Emissões",
                description = "Circuito de aquecimento da sonda após o catalisador inoperante.",
                symptoms = "Falha registrada na memória de emissões.",
                probableCauses = "Elemento aquecedor da sonda queimado.",
                recommendedFix = "Substituir sonda lambda 2.",
                isPresent = isPresent
            )
            "P0170" -> DtcCode(
                code = code,
                title = "Ajuste de Combustível Fora da Faixa (Fuel Trim)",
                system = "Alimentação & Combustível",
                description = "Ajuste autoadaptativo atingiu limite máximo de correção.",
                symptoms = "Falha na marcha lenta, perda de potência, cheiro de combustível.",
                probableCauses = "Pressão de linha de combustível fora do padrão (3.0 bar) ou bicos sujos.",
                recommendedFix = "Medir pressão e vazão da linha com manômetro e equalizar bicos injetores.",
                isPresent = isPresent
            )
            "P0171" -> DtcCode(
                code = code,
                title = "Mistura Excessivamente Pobre (System Too Lean)",
                system = "Combustão & Injeção",
                description = "Excesso de oxigênio nos gases de escape detectado pela sonda lambda.",
                symptoms = "Estouros no coletor de admissão, engasgos ao acelerar, superaquecimento.",
                probableCauses = "Entrada falsa de ar (junta do coletor / servo-freio) ou bomba com baixa vazão.",
                recommendedFix = "Localizar ar falso com máquina de fumaça; substituir filtro e bomba de combustível.",
                isPresent = isPresent
            )
            "P0172" -> DtcCode(
                code = code,
                title = "Mistura Excessivamente Rica (System Too Rich)",
                system = "Combustão & Injeção",
                description = "Excesso de combustível na queima detectado pela sonda lambda.",
                symptoms = "Fumaça preta, cheiro forte no escape, velas carbonizadas.",
                probableCauses = "Bicos injetores gotejando, pressão de combustível alta ou sensor MAP com offset alto.",
                recommendedFix = "Equalizar bicos no ultrassom e verificar regulador de pressão da linha.",
                isPresent = isPresent
            )
            "P0200" -> DtcCode(
                code = code,
                title = "Circuito dos Injetores de Combustível",
                system = "Injeção de Combustível",
                description = "Falha genérica no chicote ou alimentação positiva dos eletroinjetores.",
                symptoms = "Motor falhando ou sem partida.",
                probableCauses = "Relé principal de injeção ou fusível com mau contato.",
                recommendedFix = "Verificar alimentação 12V comum aos bicos injetores.",
                isPresent = isPresent
            )
            "P0201" -> DtcCode(
                code = code,
                title = "Circuito do Injetor 1",
                system = "Injeção de Combustível",
                description = "Circuito aberto ou curto no eletroinjetor do cilindro 1.",
                symptoms = "Motor manco em 3 cilindros, forte vibração.",
                probableCauses = "Plugue do bico 1 frouxo ou bobina do injetor aberta.",
                recommendedFix = "Medir resistência do bico (~12 a 16 ohms) e verificar chicote.",
                isPresent = isPresent
            )
            "P0202" -> DtcCode(
                code = code,
                title = "Circuito do Injetor 2",
                system = "Injeção de Combustível",
                description = "Circuito aberto ou curto no eletroinjetor do cilindro 2.",
                symptoms = "Motor manco em 3 cilindros.",
                probableCauses = "Bobina do injetor 2 rompida.",
                recommendedFix = "Testar pulso com caneta de polaridade e trocar bico 2.",
                isPresent = isPresent
            )
            "P0203" -> DtcCode(
                code = code,
                title = "Circuito do Injetor 3",
                system = "Injeção de Combustível",
                description = "Circuito aberto ou curto no eletroinjetor do cilindro 3.",
                symptoms = "Motor falhando no cilindro 3.",
                probableCauses = "Conector oxidado ou bico queimado.",
                recommendedFix = "Testar resistência e chicote do injetor 3.",
                isPresent = isPresent
            )
            "P0204" -> DtcCode(
                code = code,
                title = "Circuito do Injetor 4",
                system = "Injeção de Combustível",
                description = "Circuito aberto ou curto no eletroinjetor do cilindro 4.",
                symptoms = "Motor falhando no cilindro 4.",
                probableCauses = "Chicote rompido próximo à flauta de combustível.",
                recommendedFix = "Substituir bico 4 ou reparar chicote.",
                isPresent = isPresent
            )
            "P0230" -> DtcCode(
                code = code,
                title = "Circuito Primário da Bomba de Combustível",
                system = "Alimentação de Combustível",
                description = "Falha no circuito de acionamento do relé da bomba.",
                symptoms = "Bomba de combustível não liga; motor não dá partida.",
                probableCauses = "Relé roxo da bomba de combustível queimado ou chicote aberto.",
                recommendedFix = "Substituir relé da bomba na caixa de relés do vão do motor.",
                isPresent = isPresent
            )
            "P0300" -> DtcCode(
                code = code,
                title = "Falha de Ignição Múltipla / Aleatória (Misfire)",
                system = "Ignição & Combustão",
                description = "Detecção de falhas de combustão em múltiplos cilindros.",
                symptoms = "Luz de injeção piscando sob carga, motor fraco e trepidando.",
                probableCauses = "Velas de ignição desgastadas, cabos com fuga de corrente ou bobina estufada.",
                recommendedFix = "Substituir jogo de velas e cabos de ignição resistivos.",
                isPresent = isPresent
            )
            "P0301" -> DtcCode(
                code = code,
                title = "Falha de Combustão - Cilindro 1",
                system = "Ignição & Combustão",
                description = "Perda de centelha ou combustão incompleta no cilindro 1.",
                symptoms = "Vibração na marcha lenta e perda de torque.",
                probableCauses = "Vela do cilindro 1 carbonizada ou cabo partido.",
                recommendedFix = "Substituir vela e cabo de vela do cilindro 1.",
                isPresent = isPresent
            )
            "P0302" -> DtcCode(
                code = code,
                title = "Falha de Combustão - Cilindro 2",
                system = "Ignição & Combustão",
                description = "Perda de centelha ou combustão incompleta no cilindro 2.",
                symptoms = "Falha perceptível em acelerações.",
                probableCauses = "Torre 2 da bobina DIS com centelha fraca.",
                recommendedFix = "Testar bobina e trocar vela do cilindro 2.",
                isPresent = isPresent
            )
            "P0303" -> DtcCode(
                code = code,
                title = "Falha de Combustão - Cilindro 3",
                system = "Ignição & Combustão",
                description = "Perda de centelha no cilindro 3.",
                symptoms = "Motor oscilando em marcha lenta.",
                probableCauses = "Cabo ou vela do cilindro 3 com fuga.",
                recommendedFix = "Trocar cabo e vela do cilindro 3.",
                isPresent = isPresent
            )
            "P0304" -> DtcCode(
                code = code,
                title = "Falha de Combustão - Cilindro 4",
                system = "Ignição & Combustão",
                description = "Perda de centelha no cilindro 4.",
                symptoms = "Motor trepidando sob carga.",
                probableCauses = "Torre 4 da bobina DIS danificada.",
                recommendedFix = "Substituir bobina DIS e velas.",
                isPresent = isPresent
            )
            "P0325" -> DtcCode(
                code = code,
                title = "Sensor de Detonação (Knock Sensor)",
                system = "Ignição",
                description = "Sinal anômalo no sensor piezoelétrico de batida de pino.",
                symptoms = "Perda de torque em alta rotação, ponto atrasado.",
                probableCauses = "Torque de aperto incorreto (20Nm) ou sensor trincado.",
                recommendedFix = "Apertar com torquímetro a 20 Nm no bloco do motor.",
                isPresent = isPresent
            )
            "P0335" -> DtcCode(
                code = code,
                title = "Sensor de Rotação da Árvore de Manivelas (CKP)",
                system = "Sincronismo / Ignição",
                description = "Sinal ausente ou ruidoso da roda fônica 60-2 dentes.",
                symptoms = "Motor gira no arranque mas não pega; corte repentino em movimento.",
                probableCauses = "Sensor CKP queimado ou cabo raspando na polia de acessórios.",
                recommendedFix = "Substituir sensor de rotação CKP original Bosch/Delphi.",
                isPresent = isPresent
            )
            "P0340" -> DtcCode(
                code = code,
                title = "Sensor de Fase CMP (Comando de Válvulas)",
                system = "Sincronismo / Ignição",
                description = "Falha no sensor de comando do cabeçote 16V.",
                symptoms = "Injeção em modo semi-sequencial, corte aos 4500 RPM.",
                probableCauses = "Sensor de fase com fiação rompida ou correia dentada fora de sincronismo.",
                recommendedFix = "Verificar sincronismo da correia dentada e substituir sensor CMP.",
                isPresent = isPresent
            )
            "P0351" -> DtcCode(
                code = code,
                title = "Bobina de Ignição DIS - Cilindros 1 e 4",
                system = "Ignição",
                description = "Falha no primário ou secundário da bobina dos cilindros 1 e 4.",
                symptoms = "Motor funcionando em apenas 2 cilindros, fortes estouros.",
                probableCauses = "Trinca na base da carcaça plástica da bobina por centelhamento.",
                recommendedFix = "Substituir bobina de ignição DIS 4 pinos GM e conferir velas.",
                isPresent = isPresent
            )
            "P0352" -> DtcCode(
                code = code,
                title = "Bobina de Ignição DIS - Cilindros 2 e 3",
                system = "Ignição",
                description = "Falha no circuito da bobina dos cilindros 2 e 3.",
                symptoms = "Motor manco em 2 cilindros, falta total de potência.",
                probableCauses = "Enrolamento interno em curto por vela gasta com abertura excessiva.",
                recommendedFix = "Trocar bobina de ignição e instalar velas novas calibradas a 0.8mm.",
                isPresent = isPresent
            )
            "P0400" -> DtcCode(
                code = code,
                title = "Sistema de Recirculação de Gases (EGR)",
                system = "Emissões / EGR",
                description = "Fluxo incorreto na válvula EGR.",
                symptoms = "Engasgos em velocidade de cruzeiro.",
                probableCauses = "Válvula EGR travada aberta ou fechada por fuligem.",
                recommendedFix = "Descarbonizar válvula EGR ou substituir solenoide.",
                isPresent = isPresent
            )
            "P0443" -> DtcCode(
                code = code,
                title = "Válvula de Purga do Canister EVAP",
                system = "Emissões / Canister",
                description = "Circuito elétrico da válvula de purga em aberto ou curto.",
                symptoms = "Pressão no tanque ao abrir a tampa, cheiro de gasolina.",
                probableCauses = "Solenoide do canister queimado ou mangueira de vácuo desconectada.",
                recommendedFix = "Testar solenoide de purga e restabelecer conexão de vácuo.",
                isPresent = isPresent
            )
            "P0480" -> DtcCode(
                code = code,
                title = "Relé do Eletroventilador 1 (Velocidade Baixa)",
                system = "Arrefecimento",
                description = "Falha de acionamento da 1ª velocidade do ventilador do radiador.",
                symptoms = "Temperatura sobe em trânsito lento; ventoinha só arma em alta emergencial.",
                probableCauses = "Resistor do eletroventilador rompido ou relé queimado.",
                recommendedFix = "Testar resistor montado no defletor do radiador e relé na caixa de relés.",
                isPresent = isPresent
            )
            "P0481" -> DtcCode(
                code = code,
                title = "Relé do Eletroventilador 2 (Velocidade Alta)",
                system = "Arrefecimento",
                description = "Falha no circuito de alta velocidade do eletroventilador.",
                symptoms = "Risco de fervura do motor sob carga pesada e com ar condicionado ligado.",
                probableCauses = "Fusível maxi ou relé da 2ª velocidade inoperante.",
                recommendedFix = "Substituir relé e fusível do eletroventilador.",
                isPresent = isPresent
            )
            "P0500" -> DtcCode(
                code = code,
                title = "Sensor de Velocidade do Veículo (VSS)",
                system = "Transmissão & Painel",
                description = "Sinal de velocidade ausente na ECU em movimento.",
                symptoms = "Motor morre ao parar em semáforos em desaceleração; velocímetro oscila.",
                probableCauses = "Sensor VSS no câmbio desconectado ou pinhão desdentado.",
                recommendedFix = "Inspecionar sensor VSS no câmbio F17 ou sinal vindo do ABS.",
                isPresent = isPresent
            )
            "P0505" -> DtcCode(
                code = code,
                title = "Controle de Marcha Lenta (Atuador IAC)",
                system = "Admissão / Marcha Lenta",
                description = "Erro no motor de passo de controle da marcha lenta.",
                symptoms = "Motor morrendo ao desacelerar ou oscilando entre 600 e 1500 RPM.",
                probableCauses = "Carbonização no corpo TBI ou enrolamento aberto no motor de passo.",
                recommendedFix = "Limpeza química do atuador IAC e dutos da borboleta com descarbonizante.",
                isPresent = isPresent
            )
            "P1120" -> DtcCode(
                code = code,
                title = "Sensor de Posição da Borboleta TBI Pista 1 (DBW)",
                system = "Borboleta Eletrônica",
                description = "Tensão da pista 1 do corpo de borboleta fora de especificação.",
                symptoms = "Veículo entra em modo de emergência (Limp Mode), sem potência.",
                probableCauses = "Pista 1 com trilha desgastada ou motor da TBI pesado.",
                recommendedFix = "Limpar engrenagens do motor da TBI e alinhar batentes com o scanner.",
                isPresent = isPresent
            )
            "P1122" -> DtcCode(
                code = code,
                title = "Sensor de Posição da Borboleta TBI Pista 2 (DBW)",
                system = "Borboleta Eletrônica",
                description = "Soma das tensões das pistas 1 e 2 diferente de 5.0V.",
                symptoms = "Aceleração limitada a 2500 RPM, luz da injeção acesa.",
                probableCauses = "Mau contato no conector de 6 vias do corpo de borboleta.",
                recommendedFix = "Substituir chicote da TBI ou corpo de borboleta eletrônica completo.",
                isPresent = isPresent
            )
            "P1220" -> DtcCode(
                code = code,
                title = "Sensor de Posição do Pedal do Acelerador (APP)",
                system = "Pedal do Acelerador",
                description = "Incoerência entre as duas pistas do potenciômetro do pedal.",
                symptoms = "Acelerador para de responder subitamente em movimento.",
                probableCauses = "Potenciômetro do pedal com desgaste mecânico.",
                recommendedFix = "Substituir conjunto do pedal do acelerador eletrônico.",
                isPresent = isPresent
            )
            "P1530" -> DtcCode(
                code = code,
                title = "Relé do Compressor do Ar Condicionado",
                system = "Ar Condicionado",
                description = "Circuito de acionamento da embreagem do compressor em corte ou curto.",
                symptoms = "Ar condicionado não gela e compressor não atraca.",
                probableCauses = "Relé queimado ou pressostato aberto por falta de gás refrigerante.",
                recommendedFix = "Testar carga de gás R134a e relé do ar condicionado.",
                isPresent = isPresent
            )
            "P1600" -> DtcCode(
                code = code,
                title = "Módulo de Controle do Motor (ECU Hardware)",
                system = "Módulo ECU",
                description = "Erro interno de memória ou checksum da central eletrônica.",
                symptoms = "Falhas aleatórias e dificuldade de comunicação.",
                probableCauses = "Queda severa de tensão por bateria fraca durante o arranque.",
                recommendedFix = "Verificar aterramentos do cabeçote/bloco e estado da bateria.",
                isPresent = isPresent
            )
            "P1610" -> DtcCode(
                code = code,
                title = "Imobilizador Megamos - Função Não Programada",
                system = "Imobilizador",
                description = "Chave transponder não reconhecida pelo módulo imobilizador.",
                symptoms = "Luz de injeção pisca rapidamente; motor pega e morre em 2 segundos.",
                probableCauses = "Transponder ID40 ausente na chave canivete ou antena despareada.",
                recommendedFix = "Verificar chip na chave e antena ao redor da ignição.",
                isPresent = isPresent
            )
            "P1614" -> DtcCode(
                code = code,
                title = "Chave Transponder Incorreta (Immo 2)",
                system = "Imobilizador",
                description = "Código de transponder recebido não coincide com o memorizado.",
                symptoms = "Partida bloqueada pela central eletrônica.",
                probableCauses = "Chave reserva não gravada no módulo.",
                recommendedFix = "Reprogramar transponder utilizando o código do Infocard do Astra.",
                isPresent = isPresent
            )
            else -> DtcCode(
                code = code,
                title = "Código de Falha $code",
                system = "Injeção Eletrônica",
                description = "Falha registrada na memória da central eletrônica GM.",
                symptoms = "Luz de injeção / anomalia no painel.",
                probableCauses = "Circuito elétrico aberto ou sensor fora do limiar OEM.",
                recommendedFix = "Consultar manual de reparação GM para o código $code.",
                isPresent = isPresent
            )
        }
    }

    /**
     * Converte resposta ASCII de VIN (Modo 09 02)
     */
    fun parseVin(raw: String): String {
        val bytes = extractHexBytes(raw)
        val sb = StringBuilder()
        for (b in bytes) {
            if (b in 32..126) {
                sb.append(b.toChar())
            }
        }
        val text = sb.toString()
        val vinRegex = Regex("""[A-HJ-NPR-Z0-9]{17}""")
        val match = vinRegex.find(text)
        if (match != null) return match.value
        return text.filter { it.isLetterOrDigit() }.takeLast(17)
    }

    /**
     * Decodifica CalID / Versão de Calibração (Modo 09 04 ou 1A 94)
     * Rejeita estritamente códigos de erro e NRCs (como 7F 09 11 = ServiceNotSupported).
     */
    fun parseCalId(raw: String): String {
        if (isNegativeResponse(raw)) return ""
        val bytes = extractHexBytes(raw)
        // Modo OBD-II 09 04 responde com 49 04 ...
        for (idx in 0 until bytes.size - 2) {
            if (bytes[idx] == 0x49 && bytes[idx + 1] == 0x04) {
                val payload = bytes.drop(idx + 2)
                // Se houver contador de mensagem (ex: 01), descarta
                val content = if (payload.isNotEmpty() && payload[0] <= 0x05) payload.drop(1) else payload
                val ascii = content.mapNotNull { if (it in 32..126) it.toChar() else null }.joinToString("").trim()
                if (ascii.isNotBlank() && !ascii.startsWith("7F")) return ascii
                return content.joinToString("") { "%02X".format(it) }
            }
        }
        // Modo KWP2000 1A 94 / 1A 92 responde com 5A ...
        for (idx in 0 until bytes.size - 2) {
            if (bytes[idx] == 0x5A) {
                val payload = bytes.drop(idx + 2)
                val content = if (payload.isNotEmpty() && payload[0] <= 0x05) payload.drop(1) else payload
                val ascii = content.mapNotNull { if (it in 32..126) it.toChar() else null }.joinToString("").trim()
                if (ascii.isNotBlank() && !ascii.startsWith("7F")) return ascii
                return content.joinToString("") { "%02X".format(it) }
            }
        }
        val cleaned = cleanResponse(raw).trim()
        if (cleaned.startsWith("7F") || cleaned.contains("NO DATA") || cleaned.isBlank()) return ""
        return cleaned
    }

    /**
     * Decodifica Nome da ECU / Descritor de Módulo a partir de 09 0A
     */
    fun parseEcuName(raw: String): String {
        if (isNegativeResponse(raw)) return ""
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 2) {
            if (bytes[idx] == 0x49 && bytes[idx + 1] == 0x0A) {
                val payload = bytes.drop(idx + 2)
                val content = if (payload.isNotEmpty() && payload[0] <= 0x05) payload.drop(1) else payload
                val ascii = content.mapNotNull { if (it in 32..126) it.toChar() else null }.joinToString("").trim()
                if (ascii.isNotBlank() && !ascii.startsWith("7F")) return ascii
            }
        }
        val cleaned = cleanResponse(raw).trim()
        if (cleaned.startsWith("7F") || cleaned.contains("NO DATA") || cleaned.isBlank()) return ""
        return cleaned
    }

    /**
     * Decodifica Part Number GM a partir de 1A 90
     */
    fun parsePartNumber(raw: String): String {
        if (isNegativeResponse(raw)) return ""
        val bytes = extractHexBytes(raw)
        val payload = if (bytes.size >= 2 && bytes[0] == 0x5A && bytes[1] == 0x90) {
            bytes.drop(2)
        } else bytes

        val ascii = payload.mapNotNull { if (it in 32..126) it.toChar() else null }.joinToString("")
        val digits = ascii.filter { it.isDigit() }
        if (digits.length >= 8) return digits.take(8)

        val hexStr = payload.joinToString("") { "%02X".format(it) }
        return if (hexStr.length >= 8) hexStr.take(8) else hexStr
    }

    /**
     * Avaliação diagnóstica da saúde da mistura (STFT + LTFT)
     * Retorna Triple(nível, título curto, orientação técnica)
     * Nível: 0 (Verde/Saudável), 1 (Amarelo/Atenção), 2 (Vermelho/Alerta)
     */
    fun evaluateFuelTrimHealth(stft: Float, ltft: Float): Triple<Int, String, String> {
        val totalTrim = stft + ltft
        return when {
            kotlin.math.abs(totalTrim) <= 8.0f -> {
                Triple(0, "Mistura Balanceada", "Operação dentro da tolerância estequiométrica ideal GM.")
            }
            totalTrim in 8.0f..15.0f -> {
                val formatted = String.format(Locale.US, "+%.1f%%", totalTrim)
                Triple(1, "Tendência Pobre ($formatted)", "ECU enriquecendo para compensar ar falso leve ou bicos com baixa vazão.")
            }
            totalTrim in -15.0f..-8.0f -> {
                val formatted = String.format(Locale.US, "%.1f%%", totalTrim)
                Triple(1, "Tendência Rica ($formatted)", "ECU empobrecendo para compensar pressão alta na linha ou vapor de combustível.")
            }
            totalTrim > 15.0f -> {
                val formatted = String.format(Locale.US, "+%.1f%%", totalTrim)
                Triple(2, "Alerta: Mistura Muito Pobre ($formatted)", "Possível entrada falsa de ar (coletor/servo freio) ou bomba com baixa pressão.")
            }
            else -> {
                val formatted = String.format(Locale.US, "%.1f%%", totalTrim)
                Triple(2, "Alerta: Mistura Muito Rica ($formatted)", "Possível gotejamento de bicos, cânister travado aberto ou MAP com offset.")
            }
        }
    }

    /**
     * Avaliação do ritmo de oscilação e saúde da Sonda Lambda pré-catalisador
     * Calcula a frequência em Hz baseada em cruzamentos do limiar de 450 mV (0.45V)
     * e identifica diagnósticos de sensor travado em rica ou pobre.
     */
    fun evaluateLambdaOscillation(history: List<Float>): Pair<Float, String> {
        if (history.size < 6) {
            return Pair(0f, "Amostrando oscilação...")
        }
        val min = history.minOrNull() ?: 0f
        val max = history.maxOrNull() ?: 0f

        // Caso de sensor travado
        if (max < 0.28f) {
            return Pair(0f, "Travada em Pobre (< 280mV)")
        }
        if (min > 0.65f) {
            return Pair(0f, "Travada em Rica (> 650mV)")
        }

        // Contagem de cruzamentos pelo limiar de 450 mV
        val threshold = 0.450f
        var transitions = 0
        for (i in 1 until history.size) {
            val prev = history[i - 1]
            val curr = history[i]
            if ((prev < threshold && curr >= threshold) || (prev >= threshold && curr < threshold)) {
                transitions++
            }
        }

        val estimatedTotalSeconds = history.size * 0.18f
        val cycles = transitions / 2.0f
        val frequencyHz = if (estimatedTotalSeconds > 0f) (cycles / estimatedTotalSeconds) else 0f
        val roundedHz = (kotlin.math.round(frequencyHz * 10f) / 10f).toFloat()

        return when {
            roundedHz >= 0.8f -> Pair(roundedHz, "Oscilação Saudável (${roundedHz} Hz)")
            roundedHz > 0.0f -> Pair(roundedHz, "Oscilação Lenta / Envelhecida (${roundedHz} Hz)")
            else -> Pair(0f, "Em Aquecimento / Sem Ciclo")
        }
    }

    /**
     * Dedução de Veículo e Motorização baseado nos Identificadores (Fingerprint Engine)
     * Utiliza matriz de prioridades com precedência estrita:
     * 1. Número de Peça GM / Bosch HW
     * 2. CalID / Nome Broadcast da ECU
     * 3. Sonda física de hardware (Borboleta Eletrônica DBW vs Cabo mecânico + IAC)
     * 4. Décimo dígito do Chassi (VIN)
     */
    fun matchVehicleProfile(
        partNumber: String,
        hardwareNumber: String,
        softwareVersion: String,
        calId: String,
        vin: String,
        ecuNameBroadcast: String = "",
        hasIacConfirmed: Boolean = false,
        hasDbwConfirmed: Boolean = false,
        fallbackProfile: VehicleProfile = VehicleProfile.ASTRA_20_8V_FLEX_FASE2
    ): VehicleProfile {
        val sanitizedCalId = if (calId.startsWith("7F") || isNegativeResponse(calId)) "" else calId
        val sanitizedEcuName = if (ecuNameBroadcast.startsWith("7F") || isNegativeResponse(ecuNameBroadcast)) "" else ecuNameBroadcast
        val combined = "$partNumber $hardwareNumber $softwareVersion $sanitizedCalId $sanitizedEcuName $vin".uppercase()

        // 1. Astra GSi 2.0 16V (Bosch Motronic M1.5.5 / C20SEL DOHC 16V)
        if (combined.contains("0261204466") || combined.contains("0 261 204 466") ||
            combined.contains("09117377") || combined.contains("C20SEL") ||
            combined.contains("GSI") || combined.contains("M1.5.5_16")
        ) {
            return VehicleProfile.ASTRA_GSI_16V.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "09117377",
                hardwareNumber = "0 261 204 466",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "M1.5.5_16V",
                vin = vin
            )
        }

        // 2. Bosch Motronic M1.5.5 8V (C20NE Gasolina 1999-2004)
        if (combined.contains("0261206") || combined.contains("0 261 206") ||
            combined.contains("09117380") || combined.contains("C20NE") || combined.contains("M1.5.5_8") ||
            (combined.contains("M1.5.5") && !combined.contains("16"))
        ) {
            return VehicleProfile.ASTRA_20_8V_GAS.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "09117380",
                hardwareNumber = "0 261 206 000",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "M1.5.5_8V",
                vin = vin
            )
        }

        // 3. Bosch Motronic ME 7.9.9 (2.0 8V 140cv "Rocambole" 2009-2011)
        if (combined.contains("0261208994") || combined.contains("0 261 208 994") ||
            combined.contains("ME7.9.9") || combined.contains("ME 7.9.9") ||
            combined.contains("ROCAMBOLE") || (combined.contains("94703803") && !combined.contains("MT27E"))
        ) {
            return VehicleProfile.ASTRA_20_8V_ME799.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "94703803",
                hardwareNumber = "0 261 208 994",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "ME7.9.9_F2",
                vin = vin,
                notes = "Astra 2.0 8V 140cv com coletor Rocambole e Bosch ME 7.9.9 detectada."
            )
        }

        // 4. Bosch Motronic ME 7.9.6 (2.4 16V SFI Astra/Vectra Elite)
        if (combined.contains("93384234") || combined.contains("0261208421") || combined.contains("0 261 208 421") ||
            combined.contains("ME7.9.6_24") || combined.contains("X24XF") || combined.contains("2.4")
        ) {
            return VehicleProfile.ASTRA_24_16V_ME796.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93384234",
                hardwareNumber = "0 261 208 421",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "ME7.9.6_24V",
                vin = vin
            )
        }

        // 5. Bosch Motronic ME 7.9.6 (2.0 8V Flex 2004-2008)
        if (combined.contains("93382522") || combined.contains("0261208227") || combined.contains("0 261 208 227") ||
            combined.contains("ME7.9.6_20") || combined.contains("ME 7.9.6") || combined.contains("ME796")
        ) {
            return VehicleProfile.ASTRA_20_8V_ME796.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93382522",
                hardwareNumber = "0 261 208 227",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "ME7.9.6_20V",
                vin = vin
            )
        }

        // 6. Delphi Multec H / MT27E Fase 2 (Flexpower 8V 2009-2011)
        if (combined.contains("MT27E_F2") || combined.contains("94804161") || combined.contains("9480") ||
            (combined.contains("94703803") && combined.contains("MT27E")) ||
            (vin.length == 17 && (vin[9] in '9'..'B') && combined.contains("MT27E"))
        ) {
            return VehicleProfile.ASTRA_20_8V_FLEX_FASE2.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "94804161",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MT27E_F2",
                vin = vin,
                notes = "Astra 2.0 Flexpower com injeção Delphi Multec H / MT27E Fase 2 detectada com sucesso."
            )
        }

        // 7. Delphi Multec H / MT27E Fase 1 (Flexpower 8V 2004-2008)
        if (combined.contains("93382531") || combined.contains("MT27E_F1") ||
            (combined.contains("MT27E") && !combined.contains("F2"))
        ) {
            return VehicleProfile.ASTRA_20_8V_FLEX_FASE1.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93382531",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MT27E_F1",
                vin = vin
            )
        }

        // 8. Delphi Multec EMS C18NE 1.8 8V Gasolina (1998-2001)
        if (combined.contains("93278540") || combined.contains("C18NE") || combined.contains("MULT_18_GAS")) {
            return VehicleProfile.ASTRA_18_8V_GAS.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93278540",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MULT_18_GAS",
                vin = vin
            )
        }

        // 9. Delphi Multec EMS 1.8 8V Álcool de Fábrica (2001-2004)
        if (combined.contains("93317420") || combined.contains("MULT_18_ALC")) {
            return VehicleProfile.ASTRA_18_8V_ALCOOL.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93317420",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MULT_18_ALC",
                vin = vin
            )
        }

        // 10. Delco Multec M (Monoponto EFI 1.8 Astra F Belga)
        if (combined.contains("16199449") || combined.contains("B18NZ") || combined.contains("MULTEC_M")) {
            return VehicleProfile.ASTRA_18_EFI_MULTEC_M.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "16199449",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MULTEC_M",
                vin = vin
            )
        }

        // 11. Delphi Multec H Multipower GNV de Fábrica (2004-2006)
        if (combined.contains("93382535") || combined.contains("GNV") || combined.contains("MULTIPOWER")) {
            return VehicleProfile.ASTRA_20_8V_MULTIPOWER.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93382535",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MT27E_GNV",
                vin = vin
            )
        }

        // 12. Bosch Motronic M1.5.2 (Astra Belga 2.0 8V 1995)
        if (combined.contains("90351648") || combined.contains("0261200") || combined.contains("M1.5.2")) {
            return VehicleProfile.ASTRA_BELGA_20_8V.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "90351648",
                hardwareNumber = "0 261 200 700",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "M1.5.2",
                vin = vin
            )
        }

        // 13. Siemens Simtec 56.5 (Astra Belga 2.0 16V X20XEV 1995)
        if (combined.contains("90464731") || combined.contains("SIMTEC") || combined.contains("X20XEV")) {
            return VehicleProfile.ASTRA_BELGA_20_16V.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "90464731",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "SIMTEC_56",
                vin = vin
            )
        }

        // =========================================================================
        // REFINAMENTO POR ASSINATURA DE HARDWARE (PROBES FÍSICOS DE ACELERADOR)
        // =========================================================================
        if (hasIacConfirmed && !hasDbwConfirmed) {
            // Central com atuador de marcha lenta IAC mecânico a cabo
            if (combined.contains("GSI") || combined.contains("16V") || combined.contains("16")) {
                return VehicleProfile.ASTRA_GSI_16V.copy(vin = vin)
            }
            if (combined.contains("1.8") || combined.contains("18")) {
                return VehicleProfile.ASTRA_18_8V_GAS.copy(vin = vin)
            }
            return VehicleProfile.ASTRA_20_8V_GAS.copy(vin = vin)
        }

        if (hasDbwConfirmed && !hasIacConfirmed) {
            // Central com borboleta eletrônica Drive-by-Wire
            if (combined.contains("BOSCH") || combined.contains("0261")) {
                return VehicleProfile.ASTRA_20_8V_ME796.copy(vin = vin)
            }
            if (vin.length == 17 && (vin[9] in charArrayOf('9', 'A', 'B'))) {
                return VehicleProfile.ASTRA_20_8V_FLEX_FASE2.copy(vin = vin)
            }
            return VehicleProfile.ASTRA_20_8V_FLEX_FASE1.copy(vin = vin)
        }

        // =========================================================================
        // RESOLUÇÃO POR DÉCIMO DÍGITO DO VIN (ANO-MODELO) COM PROTEÇÃO CONTRA FALSOS POSITIVOS
        // =========================================================================
        // Se o VIN indicar ano entre 2004 e 2008:
        if (vin.length == 17 && (vin[9] in '4'..'8')) {
            // Se houver qualquer indício de injeção Bosch
            if (combined.contains("BOSCH") || combined.contains("0261") || combined.contains("ME7")) {
                return VehicleProfile.ASTRA_20_8V_ME796.copy(
                    gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93382522",
                    hardwareNumber = "0 261 208 227",
                    calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "ME7.9.6_20V",
                    vin = vin
                )
            }
            return VehicleProfile.ASTRA_20_8V_FLEX_FASE1.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93382531",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MT27E_F1",
                vin = vin
            )
        }

        // Se o VIN indicar ano entre 2009 e 2011:
        if (vin.length == 17 && (vin[9] in charArrayOf('9', 'A', 'B'))) {
            if (combined.contains("ROCAMBOLE") || combined.contains("ME7.9.9") || combined.contains("ME799")) {
                return VehicleProfile.ASTRA_20_8V_ME799.copy(
                    gmPartNumber = if (partNumber.isNotBlank()) partNumber else "94703803",
                    calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "ME7.9.9_F2",
                    vin = vin
                )
            }
            return VehicleProfile.ASTRA_20_8V_FLEX_FASE2.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "94804161",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MT27E_F2",
                vin = vin
            )
        }

        // Fallback seguro: se perfil for desconhecido, adota o padrão mais comum (Astra 2.0 8V Flexpower)
        val baseProfile = if (fallbackProfile == VehicleProfile.UNKNOWN) {
            VehicleProfile.ASTRA_20_8V_FLEX_FASE2
        } else fallbackProfile

        return baseProfile.copy(
            gmPartNumber = if (partNumber.isNotBlank()) partNumber else if (baseProfile.gmPartNumber.isNotBlank()) baseProfile.gmPartNumber else "94804161",
            calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else baseProfile.calId,
            vin = if (vin.isNotBlank()) vin else baseProfile.vin,
            notes = "Injeção eletrônica GM Astra sincronizada com sucesso."
        )
    }

    /**
     * Resposta negativa GM KWP2000 / UDS ($7F [ServiceId] [NRC])
     */
    data class NegativeResponse(
        val isNegative: Boolean,
        val serviceId: Int = 0,
        val nrcCode: Int = 0,
        val description: String = ""
    )

    /**
     * Analisa respostas brutas em busca do código de erro negativo oficial GM ($7F)
     */
    fun parseNegativeResponse(raw: String): NegativeResponse {
        val bytes = extractHexBytes(raw)
        val idx7F = bytes.indexOf(0x7F)
        if (idx7F != -1 && idx7F + 2 < bytes.size) {
            val sid = bytes[idx7F + 1]
            val nrc = bytes[idx7F + 2]
            val desc = when (nrc) {
                0x10 -> "Rejeição geral (General Reject)"
                0x11 -> "Serviço não suportado por esta versão da ECU (Service Not Supported)"
                0x12 -> "Sub-função não suportada ou formato inválido (Subfunction Not Supported)"
                0x21 -> "Central ocupada, repita o comando (Busy Repeat Request)"
                0x22 -> "Condições incorretas para execução: motor deve estar desligado com chave ligada (Conditions Not Correct)"
                0x23 -> "Rotina em andamento incompleta (Routine Not Complete)"
                0x31 -> "Parâmetro fora da faixa nominal de calibração GM (Request Out Of Range)"
                0x33 -> "Acesso de segurança negado pela central (Security Access Denied)"
                0x35 -> "Chave de segurança inválida (Invalid Key)"
                0x36 -> "Número de tentativas de segurança excedido (Exceeded Number Of Attempts)"
                0x78 -> "Comando aceito com resposta pendente (Response Pending)"
                0x80 -> "Falha na transmissão dos dados (Transmission Failure)"
                else -> String.format(Locale.US, "Resposta Negativa GM NRC 0x%02X", nrc)
            }
            return NegativeResponse(isNegative = true, serviceId = sid, nrcCode = nrc, description = desc)
        }
        return NegativeResponse(isNegative = false)
    }

    /**
     * Valida o checksum de 8 bits em frames KWP2000 completos (Format + Target + Source + Length + Data + Checksum).
     * O checksum KWP2000 é a soma simples módulo 256 de todos os bytes anteriores.
     */
    fun validateKwpChecksum(bytes: List<Int>): Boolean {
        if (bytes.size < 4) return true // Sem cabeçalho KWP suficiente para validar
        val fmt = bytes[0]
        val isKwpHeader = (fmt in 0x80..0x8F) || (fmt in 0xC0..0xCF)
        if (!isKwpHeader) return true

        var sum = 0
        for (i in 0 until bytes.size - 1) {
            sum = (sum + bytes[i]) and 0xFF
        }
        val expectedChecksum = bytes.last()
        return sum == expectedChecksum
    }

    /**
     * Decodifica a resposta de identificação KWP2000 Modo $1A (Read ECU Identification by LID).
     * Resposta típica: 5A [LID] [Dados ASCII/Hex...]
     */
    fun parseMode1aIdentification(raw: String): Map<String, String> {
        val bytes = extractHexBytes(raw)
        val result = mutableMapOf<String, String>()
        val idx5A = bytes.indexOf(0x5A)
        if (idx5A != -1 && idx5A + 1 < bytes.size) {
            val lid = bytes[idx5A + 1]
            val dataBytes = bytes.subList(idx5A + 2, bytes.size)
            val asciiStr = dataBytes.map { if (it in 32..126) it.toChar() else ' ' }.joinToString("").trim()
            val hexStr = dataBytes.joinToString("") { String.format(Locale.US, "%02X", it) }

            when (lid) {
                0x90 -> {
                    val digits = asciiStr.filter { it.isDigit() }
                    val partNum = if (digits.length in 7..8) digits else hexStr.take(8)
                    result["partNumber"] = partNum
                }
                0x80 -> {
                    result["softwareVersion"] = if (asciiStr.isNotBlank()) asciiStr else hexStr.take(6)
                }
                0x97 -> {
                    result["systemName"] = asciiStr
                }
            }
        }
        return result
    }

    /**
     * Informações estruturadas decodificadas a partir do VIN de 17 dígitos do Astra
     */
    data class DecodedVinInfo(
        val vin: String,
        val manufacturer: String = "GM Brasil",
        val modelYear: String = "Desconhecido",
        val assemblyPlant: String = "São Caetano do Sul - SP",
        val isValid: Boolean = false
    )

    /**
     * Decodifica ano-modelo, planta montadora e fabricante do Astra pelo padrão SAE J272 / CONTRAN
     */
    fun decodeVin(vin: String): DecodedVinInfo {
        val cleanVin = vin.trim().uppercase()
        if (cleanVin.length != 17) return DecodedVinInfo(vin = cleanVin, isValid = false)

        val yearChar = cleanVin[9]
        val modelYear = when (yearChar) {
            'X' -> "1999"
            'Y' -> "2000"
            '1' -> "2001"
            '2' -> "2002"
            '3' -> "2003"
            '4' -> "2004"
            '5' -> "2005"
            '6' -> "2006"
            '7' -> "2007"
            '8' -> "2008"
            '9' -> "2009"
            'A' -> "2010"
            'B' -> "2011"
            else -> "Astra ($yearChar)"
        }

        val plantChar = cleanVin[10]
        val plant = when (plantChar) {
            'B' -> "São Caetano do Sul - SP"
            'S' -> "São José dos Campos - SP"
            'G' -> "Gravataí - RS"
            else -> "Planta GM ($plantChar)"
        }

        val manu = if (cleanVin.startsWith("9BG")) "General Motors do Brasil" else "General Motors"

        return DecodedVinInfo(
            vin = cleanVin,
            manufacturer = manu,
            modelYear = modelYear,
            assemblyPlant = plant,
            isValid = true
        )
    }
}
