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
     * Extrai bytes hexadecimais como lista de inteiros
     */
    fun extractHexBytes(raw: String): List<Int> {
        val cleaned = cleanResponse(raw).replace(" ", "")
        if (cleaned.length < 2) return emptyList()

        val bytes = mutableListOf<Int>()
        var i = 0
        while (i + 1 < cleaned.length) {
            val hexChunk = cleaned.substring(i, i + 2)
            val byteVal = hexChunk.toIntOrNull(16)
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
     */
    fun isNegativeResponse(raw: String): Boolean {
        val upper = raw.uppercase().trim()
        if (upper.contains("NO DATA") ||
            upper.contains("ERROR") ||
            upper.contains("UNABLE") ||
            upper.contains("STOPPED") ||
            upper.contains("BUS INIT: ERROR") ||
            upper.contains("?")
        ) {
            return true
        }
        val bytes = extractHexBytes(raw)
        if (bytes.isNotEmpty() && bytes[0] == 0x7F) return true
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x7F) return true
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
        val v = (baroKpa - mapKpa) * 0.01f
        return if (v < 0f) 0f else v
    }

    /**
     * Fluxo de Ar Estimado / MAF: 01 10 -> ((A * 256) + B) / 100 g/s
     */
    fun parseMaf(raw: String): Float? {
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
        if (rpm <= 0f || mapKpa <= 0f) return 0f
        val tempKelvin = (iatC + 273.15f).coerceAtLeast(250f)
        // Eficiência volumétrica típica Família II (78% a 85%)
        val volumetricEfficiency = 0.82f
        val airMassPerStroke = (mapKpa * 1000.0f * (displacementLiters / 1000.0f) * volumetricEfficiency) / (287.05f * tempKelvin)
        // 4 tempos: 2 revoluções por ciclo completo
        val massFlowGramsPerSec = (airMassPerStroke * (rpm / 120.0f) * 1000.0f)
        return massFlowGramsPerSec.coerceIn(0f, 250f)
    }

    /**
     * Abertura da Borboleta (TPS %): 01 11 -> (A * 100) / 255 %
     */
    fun parseTpsPercent(raw: String): Float? {
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
        if (rpm <= 0f || tiMs <= 0f) return 0f
        val duty = (tiMs * rpm) / 1200.0f
        return duty.coerceIn(0f, 100f)
    }

    /**
     * Passo da Marcha Lenta (IAC): 21 19 -> 61 19 [A] -> A Passos
     */
    fun parseIacSteps(raw: String): Int? {
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
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x36 && idx + 2 < bytes.size) {
                return (bytes[idx + 2] * 100.0f) / 255.0f
            }
        }
        return null
    }

    /**
     * Retardo por Detonação Geral (Knock): 21 4A -> 61 4A [A] -> A * 0.05 Graus
     */
    fun parseKnockRetard(raw: String): Float? {
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x4A && idx + 2 < bytes.size) {
                return bytes[idx + 2] * 0.05f
            }
        }
        return null
    }

    /**
     * Retardo por Detonação por Cilindro (Cil 1: 21 4B, Cil 2: 21 4C, Cil 3: 21 4D, Cil 4: 21 4E)
     */
    fun parseKnockCylinder(raw: String, subService: Int): Float? {
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
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if ((bytes[idx] == 0x41 || bytes[idx] == 0x61) && bytes[idx + 1] == 0x2E && idx + 2 < bytes.size) {
                return (bytes[idx + 2] * 100.0f) / 255.0f
            }
        }
        return null
    }

    /**
     * Tensão da Bateria (Lida pela ECU): 21 42 -> 61 42 [A] -> A * 0.1 V
     */
    fun parseEcuBatteryVoltage(raw: String): Float? {
        val bytes = extractHexBytes(raw)
        for (idx in 0 until bytes.size - 1) {
            if (bytes[idx] == 0x61 && bytes[idx + 1] == 0x42 && idx + 2 < bytes.size) {
                return bytes[idx + 2] * 0.1f
            }
        }
        return null
    }

    /**
     * Tensão no Adaptador ELM327: AT RV -> regex <xx.xV>
     */
    fun parseElmVoltage(raw: String): Float? {
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
        if (rpm <= 0f || tiMs <= 0f) return 0f
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
        if (speedKmh <= 2f || litersPerHour <= 0.05f) return 0f
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
     * Mapeia código DTC para detalhes técnicos GM Família II / Motronic
     */
    fun mapDtcDetails(code: String, isPresent: Boolean = true): DtcCode {
        return when (code) {
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
                title = "Circuito do Sensor ECT",
                system = "Arrefecimento",
                description = "Falha de leitura do sensor de temperatura da água.",
                symptoms = "Eletroventilador disparado, motor afogado.",
                probableCauses = "Sensor ECT com resistência alterada.",
                recommendedFix = "Trocar sensor de temperatura do cavalete d'água.",
                isPresent = isPresent
            )
            "P0120" -> DtcCode(
                code = code,
                title = "Circuito do Sensor TPS",
                system = "Borboleta de Aceleração",
                description = "Mau funcionamento no potenciômetro da borboleta.",
                symptoms = "Hesitação ao acelerar, corte de giro.",
                probableCauses = "Pista resistiva gasta ou folga mecânica.",
                recommendedFix = "Calibrar ou substituir potenciômetro TPS.",
                isPresent = isPresent
            )
            "P0130" -> DtcCode(
                code = code,
                title = "Circuito da Sonda Lambda (Sensor 1)",
                system = "Combustão & Emissões",
                description = "Sinal incorreto ou sem oscilação na sonda pré-catalisador.",
                symptoms = "Luz de injeção acesa, fumaça preta no escape.",
                probableCauses = "Sensor de oxigênio contaminado ou chicote em curto.",
                recommendedFix = "Testar oscilação de 100mV a 900mV e substituir se travada.",
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
            "P0340" -> DtcCode(
                code = code,
                title = "Sensor de Fase CMP (Comando de Válvulas)",
                system = "Sincronismo / Ignição",
                description = "Falha no sensor de comando do cabeçote 16V.",
                symptoms = "Injeção em modo semi-sequencial, luz acesa.",
                probableCauses = "Sensor de fase com fiação rompida ou fora de ponto.",
                recommendedFix = "Verificar sincronismo da correia dentada e sensor CMP.",
                isPresent = isPresent
            )
            "P0505" -> DtcCode(
                code = code,
                title = "Controle de Marcha Lenta (Atuador IAC)",
                system = "Admissão / Marcha Lenta",
                description = "Erro no motor de passo de controle da marcha lenta.",
                symptoms = "Motor morrendo ao desacelerar ou oscilando.",
                probableCauses = "Carbonização no corpo TBI ou enrolamento aberto.",
                recommendedFix = "Limpeza química do atuador IAC e dutos da borboleta.",
                isPresent = isPresent
            )
            else -> DtcCode(
                code = code,
                title = "Código de Falha $code",
                system = "Injeção Eletrônica",
                description = "Falha registrada na memória da central eletrônica.",
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
                val ascii = payload.mapNotNull { if (it in 32..126) it.toChar() else null }.joinToString("").trim()
                if (ascii.isNotBlank() && !ascii.startsWith("7F")) return ascii
                return payload.joinToString("") { "%02X".format(it) }
            }
        }
        // Modo KWP2000 1A 94 / 1A 92 responde com 5A ...
        for (idx in 0 until bytes.size - 2) {
            if (bytes[idx] == 0x5A) {
                val payload = bytes.drop(idx + 2)
                val ascii = payload.mapNotNull { if (it in 32..126) it.toChar() else null }.joinToString("").trim()
                if (ascii.isNotBlank() && !ascii.startsWith("7F")) return ascii
                return payload.joinToString("") { "%02X".format(it) }
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
     * Dedução de Veículo e Motorização baseado nos Identificadores (Fingerprint Engine)
     */
    fun matchVehicleProfile(
        partNumber: String,
        hardwareNumber: String,
        softwareVersion: String,
        calId: String,
        vin: String,
        fallbackProfile: VehicleProfile = VehicleProfile.ASTRA_GSI_16V
    ): VehicleProfile {
        val sanitizedCalId = if (calId.startsWith("7F") || isNegativeResponse(calId)) "" else calId
        val combined = "$partNumber $hardwareNumber $softwareVersion $sanitizedCalId $vin".uppercase()

        // 1. Prioridade Absoluta: Astra GSi 2.0 16V (Bosch Motronic M1.5.5 / C20SEL DOHC 16V)
        if (combined.contains("0261204466") || combined.contains("0 261 204 466") ||
            combined.contains("09117377") || combined.contains("C20SEL") ||
            combined.contains("GSI") || combined.contains("16V") ||
            combined.contains("M1.5.5_16")
        ) {
            return VehicleProfile.ASTRA_GSI_16V.copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "09117377",
                hardwareNumber = "0 261 204 466",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "M1.5.5_16V",
                vin = vin
            )
        }

        // 2. Delphi Multec H / MT27E Fase 2 (Flexpower 8V) - Reconhece 94804161 e 94703803
        if (combined.contains("94804161") || combined.contains("9480") ||
            combined.contains("94703803") || combined.contains("MT27E_F2") ||
            (vin.length == 17 && (vin[9] in '9'..'B') && combined.contains("MT27E"))
        ) {
            return VehicleProfile.GM_ASTRA_FINGERPRINTS[1].copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "94804161",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MT27E_F2",
                vin = vin,
                notes = "Astra 2.0 Flexpower com injeção Delphi Multec H / MT27E detectada com sucesso."
            )
        }

        // 3. Delphi Multec H / MT27E Fase 1 (Flexpower 8V)
        if (combined.contains("93382531") || combined.contains("MT27E_F1") ||
            combined.contains("MT27E") || combined.contains("X20XE") ||
            (vin.length == 17 && (vin[9] in '4'..'8'))
        ) {
            return VehicleProfile.GM_ASTRA_FINGERPRINTS[2].copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "93382531",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "MT27E_F1",
                vin = vin
            )
        }

        // 4. Bosch Motronic M1.5.5 8V (C20NE Gasolina)
        if (combined.contains("0261206") || combined.contains("0 261 206") ||
            combined.contains("09117380") || combined.contains("C20NE") || combined.contains("M1.5.5_8")
        ) {
            return VehicleProfile.GM_ASTRA_FINGERPRINTS[3].copy(
                gmPartNumber = if (partNumber.isNotBlank()) partNumber else "09117380",
                hardwareNumber = "0 261 206 000",
                calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else "M1.5.5_8V",
                vin = vin
            )
        }

        // 5. Delphi Multec EMS C18NE 1.8 8V
        if (combined.contains("93278540") || combined.contains("C18NE") || combined.contains("MULT_18")) {
            return VehicleProfile.GM_ASTRA_FINGERPRINTS[4].copy(
                gmPartNumber = partNumber,
                calId = sanitizedCalId,
                vin = vin
            )
        }

        // 6. Delphi Multec H Multipower GNV
        if (combined.contains("93382535") || combined.contains("GNV") || combined.contains("MULTIPOWER")) {
            return VehicleProfile.GM_ASTRA_FINGERPRINTS[5].copy(
                gmPartNumber = partNumber,
                calId = sanitizedCalId,
                vin = vin
            )
        }

        // 7. Bosch Motronic M1.5.2 (Belga 8V)
        if (combined.contains("90351648") || combined.contains("0261200") || combined.contains("M1.5.2")) {
            return VehicleProfile.GM_ASTRA_FINGERPRINTS[6].copy(
                gmPartNumber = partNumber,
                calId = sanitizedCalId,
                vin = vin
            )
        }

        // 8. Siemens Simtec 56.5 (Belga 16V X20XEV)
        if (combined.contains("90464731") || combined.contains("SIMTEC") || combined.contains("X20XEV")) {
            return VehicleProfile.GM_ASTRA_FINGERPRINTS[7].copy(
                gmPartNumber = partNumber,
                calId = sanitizedCalId,
                vin = vin
            )
        }

        // Fallback: Se for qualquer central do Astra identificada (inclusive por Part Number como 94804161)
        val baseProfile = if (fallbackProfile == VehicleProfile.UNKNOWN) {
            VehicleProfile.GM_ASTRA_FINGERPRINTS[1] // Delphi Multec H Flexpower padrão Astra Brasil
        } else fallbackProfile

        return baseProfile.copy(
            gmPartNumber = if (partNumber.isNotBlank()) partNumber else if (baseProfile.gmPartNumber.isNotBlank()) baseProfile.gmPartNumber else "94804161",
            calId = if (sanitizedCalId.isNotBlank()) sanitizedCalId else baseProfile.calId,
            vin = if (vin.isNotBlank()) vin else baseProfile.vin,
            notes = "Injeção eletrônica GM Astra sincronizada com sucesso."
        )
    }
}
