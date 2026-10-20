package com.example.serial

import android.util.Log
import com.example.model.EcuSensorProfile
import com.example.model.SensorDescriptor
import com.example.model.VehicleProfile

/**
 * MÓDULO DE IDENTIFICAÇÃO E FINGERPRINTING DE ECU (ECUFingerprinter)
 *
 * Responsabilidades:
 * 1. Executar rotinas padronizadas de leitura de ID de hardware, software, calibração e chassi (VIN).
 * 2. Filtrar e rejeitar respostas negativas da ECU (NRCs como 7F 1A 11, 7F 09 11, NO DATA, ERROR).
 * 3. Classificar o modelo exato do Astra (Bosch Motronic M1.5.5, Motronic ME 7.9.6, Delphi Multec H Fase 1/2, etc.).
 * 4. Mapear automaticamente a matriz completa de sensores e parâmetros compatíveis conforme a arquitetura do motor.
 */
class EcuFingerprinter(
    private val commandSender: suspend (cmd: String, timeoutMs: Long) -> String,
    private val logCallback: (String) -> Unit
) {
    companion object {
        private const val TAG = "EcuFingerprinter"
    }

    private fun log(msg: String) {
        logCallback("[FINGERPRINTER] $msg")
        Log.d(TAG, msg)
    }

    /**
     * Executa a rotina completa de identificação física e mapeamento de sensores.
     */
    suspend fun interrogateAndFingerprint(): Pair<VehicleProfile, EcuSensorProfile> {
        log("Iniciando rotina de leitura de descritores de hardware e software...")

        // 1. Part Number GM (Modo KWP 1A 90 ou SAE OBD-II)
        val partNumberRaw = try {
            commandSender("1A 90", 1500L)
        } catch (e: Exception) {
            ""
        }
        log("< 1A 90 (Part Number GM): $partNumberRaw")

        // 2. Hardware Number / Fornecedor (1A 91)
        val hwNumberRaw = try {
            commandSender("1A 91", 1500L)
        } catch (e: Exception) {
            ""
        }
        log("< 1A 91 (Hardware Number): $hwNumberRaw")

        // 3. Software Version / Calibração da ECU (1A 92)
        val swVerRaw = try {
            commandSender("1A 92", 1500L)
        } catch (e: Exception) {
            ""
        }
        log("< 1A 92 (Software Version): $swVerRaw")

        // 4. Identificador de Fornecedor / Diagnóstico GM (1A 97 / 1A 80)
        val supplierRaw = try {
            commandSender("1A 97", 1200L)
        } catch (e: Exception) {
            ""
        }
        log("< 1A 97 (Supplier/Diag ID): $supplierRaw")

        // 5. Número de Chassi / VIN (09 02 ou 1A 90)
        val vinRaw = try {
            commandSender("09 02", 1500L)
        } catch (e: Exception) {
            ""
        }
        log("< 09 02 (VIN / Chassi): $vinRaw")

        // 6. Calibration ID / Versão de Calibração (09 04 ou 1A 94)
        val calIdRaw = try {
            commandSender("09 04", 1500L)
        } catch (e: Exception) {
            ""
        }
        log("< 09 04 (CalID): $calIdRaw")

        // Decodificação e sanitização rigorosa contra NRCs
        val partNumber = GmDataParsers.parsePartNumber(partNumberRaw)
        val hwNumber = GmDataParsers.cleanResponse(hwNumberRaw)
        val swVer = GmDataParsers.cleanResponse(swVerRaw)
        val calId = GmDataParsers.parseCalId(calIdRaw)
        val vin = GmDataParsers.parseVin(vinRaw)

        log("Identificadores processados: PN='$partNumber', HW='$hwNumber', SW='$swVer', CalID='$calId', VIN='$vin'")

        // Pareamento com a base de Fingerprints GM Brasil
        val matchedProfile = GmDataParsers.matchVehicleProfile(
            partNumber = partNumber,
            hardwareNumber = hwNumber,
            softwareVersion = swVer,
            calId = calId,
            vin = vin,
            fallbackProfile = VehicleProfile.UNKNOWN
        )

        log("Central Mapeada: ${matchedProfile.ecuName} | Motor: ${matchedProfile.engineCode}")

        // Geração da matriz de sensores e parâmetros compatíveis
        val sensorProfile = mapSensorCapabilities(matchedProfile)
        log("Matriz de Sensores Gerada: ${sensorProfile.compatibleSensors.size} sensores mapeados (Borboleta: ${if (sensorProfile.hasDriveByWire) "Eletrônica (Drive-by-Wire)" else "Mecânica a Cabo"})")

        return Pair(matchedProfile, sensorProfile)
    }

    /**
     * Mapeia automaticamente os sensores, atuadores e grandezas compatíveis conforme a ECU identificada.
     */
    fun mapSensorCapabilities(profile: VehicleProfile): EcuSensorProfile {
        val ecuUpper = profile.ecuName.uppercase()
        val engineUpper = profile.engineCode.uppercase()

        // Determinação de arquitetura de injeção e hardware
        val isMotronicM155 = ecuUpper.contains("M1.5.5") || ecuUpper.contains("MOTRONIC 1.5.5")
        val isMultecHFlex = ecuUpper.contains("MULTEC") || ecuUpper.contains("MT27E") || profile.supportsFlexPids
        val is16vDohc = profile.valveCount.contains("16") || engineUpper.contains("C20SEL") || engineUpper.contains("X20XEV")
        val isBelga = ecuUpper.contains("M1.5.2") || ecuUpper.contains("BELGA") || ecuUpper.contains("SIMTEC")

        // Acelerador: Se for Delphi Multec H Fase 2 ou Motronic ME7.9.6 possui Drive-by-Wire (sem motor de passo IAC a cabo)
        val hasDriveByWire = isMultecHFlex && (profile.years.contains("2009") || profile.years.contains("2011") || profile.calId.contains("F2"))
        val hasMechanicalIac = !hasDriveByWire // M1.5.5 e Multec Fase 1 usam atuador IAC de 4 passos
        val hasFlexPids = profile.supportsFlexPids || isMultecHFlex
        val hasIndividualKnock = isMotronicM155 || is16vDohc // Motronic M1.5.5 possui duplo sensor de detonação com retardo por cilindro
        val hasPostCatLambda = profile.supportsFlexPids || profile.years.contains("2005") || profile.years.contains("2009")
        val hasPhysicalMaf = isBelga // Apenas Astra Belga com M1.5.2 / Simtec possui medidor de fluxo de ar MAF; Astra G nacional usa MAP

        val sensors = mutableListOf<SensorDescriptor>()

        // 1. DINÂMICA DO MOTOR
        sensors.add(
            SensorDescriptor(
                id = "RPM",
                name = "Rotação do Motor",
                unit = "RPM",
                pidCommand = "01 0C",
                mode = "Mode 01 (SAE) / 21 02 (GM)",
                normalRange = "750 - 900 (Lenta) / até ${profile.redlineRpm.toInt()} RPM",
                description = "Velocidade angular do virabrequim medida pelo sensor de rotação inductivo na roda fônica 60-2 dentes."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "SPEED",
                name = "Velocidade do Veículo",
                unit = "km/h",
                pidCommand = "01 0D",
                mode = "Mode 01 (SAE)",
                normalRange = "0 - 240 km/h",
                description = "Sensor de velocidade VSS no câmbio F17/F18/F23 ou sinal processado pelo módulo de ABS."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "MAP",
                name = "Pressão no Coletor (MAP)",
                unit = "kPa",
                pidCommand = "01 0B",
                mode = "Mode 01 (SAE) / 21 0A (GM)",
                normalRange = "28 - 42 kPa (Lenta aquecida)",
                description = "Sensor piezoresistivo de pressão absoluta no coletor de admissão (Carga fundamental na injeção GM Speed-Density)."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "VACUUM",
                name = "Vácuo do Coletor",
                unit = "bar",
                pidCommand = "DERIVED",
                mode = "Derivado Físico (Baro - MAP)",
                normalRange = "0.55 - 0.70 bar",
                description = "Depressão gerada pela descida dos pistões contra a borboleta fechada."
            )
        )

        // 2. BORBOLETA, ACELERADOR E MARCHA LENTA
        sensors.add(
            SensorDescriptor(
                id = "TPS",
                name = "Abertura da Borboleta (TPS)",
                unit = "%",
                pidCommand = "01 11",
                mode = "Mode 01 (SAE)",
                normalRange = "0.0% (Lenta) a 100.0% (WOT)",
                description = "Sensor de posição angular da borboleta de aceleração."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "TPS_VOLT1",
                name = "Tensão TPS Pista 1",
                unit = "V",
                pidCommand = "21 12",
                mode = "Mode 21 (GM Local)",
                normalRange = "0.40V - 0.90V (Fechada) / > 4.20V (WOT)",
                description = "Tensão direta do potenciômetro principal da borboleta."
            )
        )

        if (hasDriveByWire) {
            sensors.add(
                SensorDescriptor(
                    id = "TPS_VOLT2",
                    name = "Tensão TPS Pista 2 (Redundância)",
                    unit = "V",
                    pidCommand = "21 13",
                    mode = "Mode 21 (GM Local)",
                    normalRange = "4.20V - 4.60V (Fechada) / < 0.90V (WOT)",
                    description = "Pista inversa de segurança do corpo de borboleta eletrônico Drive-by-Wire."
                )
            )
            sensors.add(
                SensorDescriptor(
                    id = "PEDAL",
                    name = "Posição do Pedal do Acelerador",
                    unit = "%",
                    pidCommand = "01 49",
                    mode = "Mode 01 (SAE)",
                    normalRange = "0.0% a 100.0%",
                    description = "Sensor duplo de efeito Hall no pedal do acelerador."
                )
            )
        }

        if (hasMechanicalIac) {
            sensors.add(
                SensorDescriptor(
                    id = "IAC",
                    name = "Atuador de Marcha Lenta (IAC)",
                    unit = "Passos",
                    pidCommand = "21 19",
                    mode = "Mode 21 (GM Local)",
                    normalRange = "10 - 45 passos (Lenta estabilizada a 92°C)",
                    description = "Motor de passo da válvula de controle de ar suplementar na borboleta a cabo."
                )
            )
        }

        // 3. TEMPERATURAS
        sensors.add(
            SensorDescriptor(
                id = "ECT",
                name = "Temperatura do Arrefecimento (ECT)",
                unit = "°C",
                pidCommand = "01 05",
                mode = "Mode 01 (SAE) / 21 01 (GM)",
                normalRange = "88°C - 98°C (Temperatura operacional)",
                description = "Sensor termistor NTC localizado no cavalete da válvula termostática."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "IAT",
                name = "Temperatura do Ar de Admissão (IAT)",
                unit = "°C",
                pidCommand = "01 0F",
                mode = "Mode 01 (SAE) / 21 03 (GM)",
                normalRange = "20°C - 55°C",
                description = "Termistor NTC na mangueira de ar ou integrado ao sensor MAP."
            )
        )

        // 4. INJEÇÃO E ALIMENTAÇÃO
        sensors.add(
            SensorDescriptor(
                id = "INJ_TIME",
                name = "Tempo de Injeção (T.I.)",
                unit = "ms",
                pidCommand = "21 15",
                mode = "Mode 21 (GM Local)",
                normalRange = "1.80 ms - 3.20 ms (Lenta aquecida)",
                description = "Largura de pulso elétrico aplicada nos bicos injetores pela ECU."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "DUTY_CYCLE",
                name = "Duty Cycle dos Bicos",
                unit = "%",
                pidCommand = "DERIVED",
                mode = "Derivado Físico ((T.I. * RPM) / 1200)",
                normalRange = "1.2% - 3.5% (Lenta) / < 85% (Carga máxima)",
                description = "Percentual de abertura dos eletroinjetores em relação ao tempo de ciclo do motor."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "STFT",
                name = "Ajuste Curto Prazo (STFT)",
                unit = "%",
                pidCommand = "01 06",
                mode = "Mode 01 (SAE)",
                normalRange = "-10% a +10%",
                description = "Correção instantânea da mistura ar/combustível com base na sonda lambda."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "LTFT",
                name = "Ajuste Longo Prazo (LTFT)",
                unit = "%",
                pidCommand = "01 07",
                mode = "Mode 01 (SAE)",
                normalRange = "-10% a +10%",
                description = "Tabela de aprendizado e adaptação acumulada da mistura na ECU."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "LAMBDA1",
                name = "Sonda Lambda Pré-Catalisador",
                unit = "mV",
                pidCommand = "01 14",
                mode = "Mode 01 (SAE)",
                normalRange = "100 mV - 900 mV (Oscilação constante 1-2 Hz)",
                description = "Sensor de oxigênio de zircônia no coletor de escape antes do catalisador."
            )
        )

        if (hasPostCatLambda) {
            sensors.add(
                SensorDescriptor(
                    id = "LAMBDA2",
                    name = "Sonda Lambda Pós-Catalisador",
                    unit = "mV",
                    pidCommand = "01 15",
                    mode = "Mode 01 (SAE)",
                    normalRange = "450 mV - 750 mV (Sinal estabilizado)",
                    description = "Monitoramento da eficiência de conversão catalítica (Euro 3/4 OBD-II)."
                )
            )
        }

        // 5. FLEXPOWER & BI-COMBUSTÍVEL
        if (hasFlexPids) {
            sensors.add(
                SensorDescriptor(
                    id = "ETHANOL",
                    name = "Teor de Etanol Estimado",
                    unit = "%",
                    pidCommand = "01 52",
                    mode = "Mode 01 (SAE) / Algoritmo GM",
                    normalRange = "22% - 27% (Gasolina C) ou 85% - 100% (Etanol)",
                    description = "Cálculo virtual do software da ECU baseado no sensor de oxigênio pós-reabastecimento."
                )
            )
            sensors.add(
                SensorDescriptor(
                    id = "AFR",
                    name = "Relação Ar/Combustível Alvo",
                    unit = ":1",
                    pidCommand = "DERIVED",
                    mode = "Derivado Físico",
                    normalRange = "9.0:1 (Etanol) a 13.2:1 (Gasolina C E27)",
                    description = "Relação estequiométrica alvo programada no mapa da ECU."
                )
            )
        }

        // 6. IGNIÇÃO E DETONAÇÃO
        sensors.add(
            SensorDescriptor(
                id = "SPARK",
                name = "Avanço de Ignição",
                unit = "° APMS",
                pidCommand = "01 0E",
                mode = "Mode 01 (SAE)",
                normalRange = "6° - 15° APMS (Lenta aquecida)",
                description = "Ponto de centelha calculado pela ECU antes do Ponto Morto Superior."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "KNOCK_GLOBAL",
                name = "Retardo de Detonação Global",
                unit = "°",
                pidCommand = "21 17",
                mode = "Mode 21 (GM Local)",
                normalRange = "0.0° (Sem detonação perceptível)",
                description = "Graus de atraso aplicados no ponto pelo sensor piezoelétrico de detonação (KS)."
            )
        )

        if (hasIndividualKnock) {
            sensors.add(
                SensorDescriptor(
                    id = "KNOCK_CYL",
                    name = "Detonação Individual por Cilindro",
                    unit = "° (1-4)",
                    pidCommand = "21 18",
                    mode = "Mode 21 (GM Bosch M1.5.5)",
                    normalRange = "0.0° em todos os cilindros",
                    description = "Monitoramento individual de batida de pino por janela de combustão."
                )
            )
        }

        // 7. CONSUMO E FLUXO DE MASSA DE AR
        sensors.add(
            SensorDescriptor(
                id = "MAF_EST",
                name = if (hasPhysicalMaf) "Fluxo de Massa de Ar (MAF Físico)" else "Fluxo de Ar Estimado (Speed-Density)",
                unit = "g/s",
                pidCommand = if (hasPhysicalMaf) "01 10" else "DERIVED",
                mode = if (hasPhysicalMaf) "Mode 01 (SAE)" else "Derivado (RPM, MAP, IAT e Cilindrada)",
                normalRange = "2.0 - 4.5 g/s (Lenta) / até 120 g/s (WOT)",
                description = "Vazão mássica de ar admitida pelo motor para cálculo de combustível."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "FUEL_L_H",
                name = "Consumo Instantâneo (l/h)",
                unit = "l/h",
                pidCommand = "DERIVED",
                mode = "Derivado Físico (T.I. * RPM)",
                normalRange = "0.8 - 1.4 l/h (Lenta sem A/C)",
                description = "Volume horário de combustível consumido pelos bicos."
            )
        )

        // 8. ELÉTRICA E SISTEMAS AUXILIARES
        sensors.add(
            SensorDescriptor(
                id = "ECU_VOLT",
                name = "Tensão de Alimentação da ECU",
                unit = "V",
                pidCommand = "01 42",
                mode = "Mode 01 (SAE)",
                normalRange = "13.6V - 14.4V (Com alternador em carga)",
                description = "Tensão elétrica contínua na linha 30/15 medida pelo conversor A/D da central."
            )
        )

        sensors.add(
            SensorDescriptor(
                id = "ELM_VOLT",
                name = "Tensão no Conector DLC (ELM327)",
                unit = "V",
                pidCommand = "AT RV",
                mode = "Comando AT ELM327 (Pino 16)",
                normalRange = "12.0V - 14.6V",
                description = "Tensão medida diretamente no pino 16 da tomada OBD-II sob o freio de mão."
            )
        )

        // Comandos de leitura prioritários (Fast Polling) e secundários (Slow Polling)
        val fastPolling = listOf("01 0C", "01 0D", "01 0B", "01 11", "01 0E", "01 14", "21 15")
        val slowPolling = listOf("01 05", "01 0F", "01 04", "01 06", "01 07", "01 03", "21 12", "21 19", "01 33", "21 17", "01 42", "AT RV")

        return EcuSensorProfile(
            ecuFamily = profile.ecuName,
            engineFamily = profile.engineCode,
            hasDriveByWire = hasDriveByWire,
            hasMechanicalIac = hasMechanicalIac,
            hasFlexPids = hasFlexPids,
            hasIndividualKnock = hasIndividualKnock,
            hasPostCatLambda = hasPostCatLambda,
            hasPhysicalMaf = hasPhysicalMaf,
            intakeType = if (hasPhysicalMaf) "Massa de Ar (MAF)" else "Speed-Density (Sensor MAP)",
            fuelDeliveryType = if (is16vDohc || isMultecHFlex) "Injeção Sequencial Multiponto" else "Injeção Semi-Sequencial",
            compatibleSensors = sensors,
            fastPollingCommands = fastPolling,
            slowPollingCommands = slowPolling
        )
    }
}
