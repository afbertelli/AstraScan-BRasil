package com.example.model

/**
 * Protocolos suportados na varredura progressiva GM Brasil
 */
enum class ProtocolType(val label: String, val initCommand: String) {
    UNKNOWN("Desconhecido", ""),
    KWP_FAST("ISO 14230-4 KWP2000 Fast Init (K-Line)", "AT SP 5"),
    KWP_SLOW("ISO 14230-4 KWP2000 5-Baud Slow (K-Line)", "AT SP 4"),
    ISO_9141("ISO 9141-2 GM 5-Baud (K-Line)", "AT SP 3"),
    CAN_500K("ISO 15765-4 CAN 11-bit 500k", "AT SP 6"),
    CAN_250K("ISO 15765-4 CAN 11-bit 250k", "AT SP 8")
}

/**
 * Módulos eletrônicos do Chevrolet Astra acessíveis via Linha K (Pinos 7, 12, 8, 3)
 */
enum class AstraModule(
    val title: String,
    val targetHex: String,
    val headerKwp: String,
    val description: String
) {
    ENGINE_PRIMARY("Injeção / Motor (Primário)", "11", "81 11 F1", "Bosch Motronic / Delphi (Pino 7)"),
    ENGINE_SECONDARY("Injeção / Motor (Secundário)", "10", "81 10 F1", "ME 7.9.6 / Multipower (Pino 7)"),
    INSTRUMENT_CLUSTER("Painel / Computador MID", "60", "81 60 F1", "Velocímetro, combustível, odômetro e alertas"),
    TRANSMISSION("Câmbio Automático Aisin", "18", "81 18 F1", "Aisin 50-40LE / AF20-4"),
    BRAKES_ABS("Freios ABS (Bosch 5.3 / 8.0)", "28", "81 28 F1", "Módulo de controle antibloqueio e TC"),
    AIRBAG("Airbag (Siemens / SAB6)", "58", "81 58 F1", "Bolsas infláveis e pré-tensionadores"),
    IMMOBILIZER("Imobilizador GM (Immo 2)", "5A", "81 5A F1", "Antena da chave e transponder GM")
}

/**
 * Nível de calibração do timeout ELM327 (AT ST)
 */
enum class TimeoutCalibration(val label: String, val atCommand: String, val latencyMs: Int) {
    FAST("Rápido (~130ms)", "AT ST 20", 130),
    BALANCED("Balanceado (~200ms)", "AT ST 32", 200),
    COMPATIBLE("Alta Compatibilidade (~320ms)", "AT ST 50", 320)
}

/**
 * Estados da Máquina de Estados Finitos (FSM) de Auto-Detecção
 */
enum class ScanState(val description: String, val progress: Float) {
    IDLE("Desconectado", 0.0f),
    CONNECTING_BLUETOOTH("Conectando socket Bluetooth SPP...", 0.15f),
    RESETTING_ELM327("Resetando e configurando buffer ELM327...", 0.30f),
    TESTING_KWP_FAST("Testando Protocolo 1: KWP2000 Fast Init...", 0.45f),
    TESTING_KWP_SLOW("Testando Protocolo 2: KWP2000 Slow Init...", 0.55f),
    TESTING_ISO_9141("Testando Protocolo 3: ISO 9141-2 GM...", 0.65f),
    TESTING_CAN("Testando Protocolo 4: CAN 11-bit...", 0.75f),
    READING_IDENTIFIERS("Lendo identificadores da ECU (1A 90 / 09 02)...", 0.85f),
    FINGERPRINTING("Cruzando dados no Fingerprint DB GM Brasil...", 0.95f),
    CONNECTED("Conectado e Telemetria Ativa", 1.0f),
    ERROR("Falha na comunicação ou protocolo", 0.0f)
}

/**
 * Perfil completo de veículo e motor GM Astra deduzido via Fingerprint
 */
data class VehicleProfile(
    val ecuName: String,
    val engineCode: String,
    val engineDisplacement: String,
    val valveCount: String,
    val horsepower: String,
    val fuelType: String,
    val years: String,
    val trimLevels: String,
    val gmPartNumber: String = "",
    val hardwareNumber: String = "",
    val softwareVersion: String = "",
    val calId: String = "",
    val cvn: String = "",
    val vin: String = "",
    val supportsFlexPids: Boolean = false,
    val redlineRpm: Float = 6400f,
    val maxRpm: Float = 7000f,
    val displacementLiters: Float = 2.0f,
    val injectorFlowCcMin: Float = 190.0f,
    val hasAutomaticTransmission: Boolean = false,
    val notes: String = ""
) {
    companion object {
        val UNKNOWN = VehicleProfile(
            ecuName = "ECU Não Identificada",
            engineCode = "Desconhecido",
            engineDisplacement = "-",
            valveCount = "-",
            horsepower = "-",
            fuelType = "-",
            years = "-",
            trimLevels = "-"
        )

        val ASTRA_GSI_16V = VehicleProfile(
            ecuName = "Bosch Motronic M1.5.5 (16V DOHC)",
            engineCode = "C20SEL",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "16 Válvulas (DOHC)",
            horsepower = "136 cv",
            fuelType = "Gasolina",
            years = "1999 - 2005",
            trimLevels = "Astra GSi / GLS / CD",
            gmPartNumber = "09117377",
            hardwareNumber = "0 261 204 466",
            calId = "M1.5.5_16V",
            supportsFlexPids = false,
            redlineRpm = 6600f,
            maxRpm = 7200f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 204.0f,
            notes = "Astra GSi 2.0 16V C20SEL. Cabeçote DOHC 16V com coletor de admissão variável VIS e sensor de fase CMP."
        )

        val ASTRA_20_8V_ME799 = VehicleProfile(
            ecuName = "Bosch Motronic ME 7.9.9 (Rocambole)",
            engineCode = "X20XE Flex 140cv",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "133 cv (Gas) / 140 cv (Etanol)",
            fuelType = "Flexpower (Bicombustível)",
            years = "2009 - 2011",
            trimLevels = "Advantage, Elegance",
            gmPartNumber = "94703803",
            hardwareNumber = "0 261 208 994",
            calId = "ME7.9.9_F2",
            supportsFlexPids = true,
            redlineRpm = 6400f,
            maxRpm = 6800f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 228.0f,
            notes = "Astra 2.0 8V 140cv com coletor de admissão plástico 'Rocambole' e corpo de borboleta DBW Bosch."
        )

        val ASTRA_20_8V_FLEX_FASE2 = VehicleProfile(
            ecuName = "Delphi Multec H / MT27E (Fase 2)",
            engineCode = "X20XE Flex Atualizado",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "133 cv (Gas) / 140 cv (Etanol)",
            fuelType = "Flexpower (Bicombustível)",
            years = "2009 - 2011",
            trimLevels = "Advantage, Elegance",
            gmPartNumber = "94804161",
            calId = "MT27E_F2",
            supportsFlexPids = true,
            redlineRpm = 6400f,
            maxRpm = 6800f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 225.0f,
            notes = "Coletor de admissão em plástico preto. Sensor de etanol estimado via lambda."
        )

        val ASTRA_20_8V_FLEX_FASE1 = VehicleProfile(
            ecuName = "Delphi Multec H / MT27E (Fase 1)",
            engineCode = "X20XE Flexpower",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "121 cv (Gas) / 128 cv (Etanol)",
            fuelType = "Flexpower (Bicombustível)",
            years = "2004 - 2008",
            trimLevels = "Comfort, Elegance, Elite, Advantage",
            gmPartNumber = "93382531",
            calId = "MT27E_F1",
            supportsFlexPids = true,
            redlineRpm = 6200f,
            maxRpm = 6600f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 225.0f,
            notes = "Coletor de admissão em alumínio fundido. Acelerador eletrônico DBW Delphi."
        )

        val ASTRA_24_16V_ME796 = VehicleProfile(
            ecuName = "Bosch Motronic ME 7.9.6 (2.4 16V SFI)",
            engineCode = "X24XF / C24SE SFI",
            engineDisplacement = "2.4 Litros (Família II)",
            valveCount = "16 Válvulas (DOHC)",
            horsepower = "146 cv (Etanol) / 150 cv (Gasolina)",
            fuelType = "Flexpower / Gasolina",
            years = "2004 - 2009",
            trimLevels = "Astra / Vectra Elite 2.4 16V",
            gmPartNumber = "93384234",
            hardwareNumber = "0 261 208 421",
            calId = "ME7.9.6_24V",
            supportsFlexPids = true,
            redlineRpm = 6400f,
            maxRpm = 6800f,
            displacementLiters = 2.4f,
            injectorFlowCcMin = 240.0f,
            notes = "Motor 2.4 16V Família II com duplo comando de válvulas DOHC, coletor plástico e acelerador eletrônico."
        )

        val ASTRA_20_8V_ME796 = VehicleProfile(
            ecuName = "Bosch Motronic ME 7.9.6 (2.0 8V Flex)",
            engineCode = "X20XE ME796",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "121 cv (Gas) / 128 cv (Etanol)",
            fuelType = "Flexpower (Bicombustível)",
            years = "2004 - 2008",
            trimLevels = "Comfort, Elegance, Elite",
            gmPartNumber = "93382522",
            hardwareNumber = "0 261 208 227",
            calId = "ME7.9.6_20V",
            supportsFlexPids = true,
            redlineRpm = 6200f,
            maxRpm = 6600f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 225.0f,
            notes = "Injeção Bosch ME 7.9.6 com acelerador eletrônico Bosch e controle estequiométrico adaptativo."
        )

        val ASTRA_20_8V_GAS = VehicleProfile(
            ecuName = "Bosch Motronic M1.5.5 (8V)",
            engineCode = "C20NE",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "116 cv",
            fuelType = "Gasolina",
            years = "1999 - 2004",
            trimLevels = "GL, GLS, CD",
            gmPartNumber = "09117380",
            hardwareNumber = "0 261 206 000",
            calId = "M1.5.5_8V",
            supportsFlexPids = false,
            redlineRpm = 6200f,
            maxRpm = 6600f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 185.0f,
            notes = "Injeção sequencial multiponto com acelerador mecânico a cabo e atuador de lenta IAC."
        )

        val ASTRA_18_8V_GAS = VehicleProfile(
            ecuName = "Delphi Multec EMS / Multec F",
            engineCode = "C18NE / 1.8 Gasolina",
            engineDisplacement = "1.8 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "110 cv",
            fuelType = "Gasolina",
            years = "1998 - 2001",
            trimLevels = "GL Hatch / Sedan",
            gmPartNumber = "93278540",
            calId = "MULT_18_GAS",
            supportsFlexPids = false,
            redlineRpm = 6200f,
            maxRpm = 6600f,
            displacementLiters = 1.8f,
            injectorFlowCcMin = 168.0f,
            notes = "ECU Delphi com sensor MAP no coletor e IAC 4 passos a cabo."
        )

        val ASTRA_18_8V_ALCOOL = VehicleProfile(
            ecuName = "Delphi / Delco Multec EMS (1.8 Álcool)",
            engineCode = "C18NE Álcool",
            engineDisplacement = "1.8 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "102 cv",
            fuelType = "Etanol (Álcool)",
            years = "2001 - 2004",
            trimLevels = "GL 1.8 Álcool de Fábrica",
            gmPartNumber = "93317420",
            calId = "MULT_18_ALC",
            supportsFlexPids = false,
            redlineRpm = 6000f,
            maxRpm = 6400f,
            displacementLiters = 1.8f,
            injectorFlowCcMin = 220.0f,
            notes = "Astra 1.8 movido a Etanol de fábrica com bicos injetores de alta vazão e reservatório de partida a frio."
        )

        val ASTRA_18_EFI_MULTEC_M = VehicleProfile(
            ecuName = "Delco Multec M (Monoponto EFI)",
            engineCode = "B18NZ / C18NZ",
            engineDisplacement = "1.8 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "95 cv",
            fuelType = "Gasolina",
            years = "1995",
            trimLevels = "Astra F GL (Belga)",
            gmPartNumber = "16199449",
            calId = "MULTEC_M",
            supportsFlexPids = false,
            redlineRpm = 6000f,
            maxRpm = 6400f,
            displacementLiters = 1.8f,
            injectorFlowCcMin = 380.0f,
            notes = "Astra F Belga 1.8 com injeção monoponto eletrônica Delco Multec M (1 bico injetor central)."
        )

        val ASTRA_20_8V_MULTIPOWER = VehicleProfile(
            ecuName = "Delphi Multec H Multipower",
            engineCode = "X20XE Tri-Fuel",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "106 cv (GNV) / 121 cv (Gas) / 128 cv (Alc)",
            fuelType = "Tri-fuel (GNV / Etanol / Gasolina)",
            years = "2004 - 2006",
            trimLevels = "Multipower Fabrica",
            gmPartNumber = "93382535",
            calId = "MT27E_GNV",
            supportsFlexPids = true,
            redlineRpm = 6000f,
            maxRpm = 6400f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 225.0f,
            notes = "Primeiro veículo do Brasil com GNV homologado de fábrica."
        )

        val ASTRA_BELGA_20_8V = VehicleProfile(
            ecuName = "Bosch Motronic M1.5.2 (Belga)",
            engineCode = "C20NE Belga",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "8 Válvulas (SOHC)",
            horsepower = "116 cv",
            fuelType = "Gasolina",
            years = "1995",
            trimLevels = "Astra F Hatch / Sedan",
            gmPartNumber = "90351648",
            hardwareNumber = "0 261 200 700",
            calId = "M1.5.2",
            supportsFlexPids = false,
            redlineRpm = 6200f,
            maxRpm = 6600f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 185.0f,
            notes = "Linha Astra Belga importada. Linha K ISO 9141."
        )

        val ASTRA_BELGA_20_16V = VehicleProfile(
            ecuName = "Siemens Simtec 56.5",
            engineCode = "X20XEV",
            engineDisplacement = "2.0 Litros (Família II)",
            valveCount = "16 Válvulas (DOHC)",
            horsepower = "136 cv",
            fuelType = "Gasolina",
            years = "1995",
            trimLevels = "Astra F GLS Wagon (Belga)",
            gmPartNumber = "90464731",
            calId = "SIMTEC_56",
            supportsFlexPids = false,
            redlineRpm = 6600f,
            maxRpm = 7000f,
            displacementLiters = 2.0f,
            injectorFlowCcMin = 204.0f,
            notes = "Astra F Station Wagon com ECU Siemens Simtec 56.5."
        )

        // Banco de Fingerprints GM Brasil Completo
        val GM_ASTRA_FINGERPRINTS = listOf(
            ASTRA_GSI_16V,
            ASTRA_20_8V_ME799,
            ASTRA_20_8V_FLEX_FASE2,
            ASTRA_20_8V_FLEX_FASE1,
            ASTRA_24_16V_ME796,
            ASTRA_20_8V_ME796,
            ASTRA_20_8V_GAS,
            ASTRA_18_8V_GAS,
            ASTRA_18_8V_ALCOOL,
            ASTRA_18_EFI_MULTEC_M,
            ASTRA_20_8V_MULTIPOWER,
            ASTRA_BELGA_20_8V,
            ASTRA_BELGA_20_16V
        )
    }
}

/**
 * Conjunto Completo de Telemetria e Parâmetros de Injeção Eletrônica GM Astra
 */
data class LiveTelemetry(
    // 1. DINÂMICA DO MOTOR
    val rpm: Float = 0f,
    val targetIdleRpm: Float = 0f,
    val speedKmh: Float = 0f,
    val engineLoadPercent: Float = 0f,
    val absoluteLoadPercent: Float = 0f,

    // 2. TEMPERATURAS
    val coolantTempC: Float = 0f,
    val intakeAirTempC: Float = 0f,

    // 3. PRESSÕES E ADMISSÃO
    val mapKpa: Float = 0f,
    val baroKpa: Float = 0f,
    val manifoldVacuumBar: Float = 0f,
    val estimatedMafGramsSec: Float = 0f,

    // 4. BORBOLETA E PEDAL
    val throttlePercent: Float = 0f,
    val throttleVoltage1: Float = 0f,
    val throttleVoltage2: Float = 0f,
    val acceleratorPedalPercent: Float = 0f,

    // 5. INJEÇÃO E COMBUSTÍVEL
    val injectionTimeMs: Float = 0f,
    val injectorDutyCyclePercent: Float = 0f,
    val iacSteps: Int = 0,
    val shortTermFuelTrim: Float = 0f,
    val longTermFuelTrim: Float = 0f,
    val idleFuelTrim: Float = 0f,
    val fuelLoopStatus: String = "OFFLINE",
    val lambdaVoltage: Float = 0f,
    val lambdaPostCatVoltage: Float = 0f,
    val equivalenceRatioLambda: Float = 0f,
    val lambdaFrequencyHz: Float = 0f,
    val lambdaHealthStatus: String = "Aguardando sinal...",
    val lambdaHistory: List<Float> = emptyList(),
    val fuelTrimHealthMessage: String = "Em Faixa Normal",
    val fuelTrimHealthLevel: Int = 0, // 0: Ideal (Verde), 1: Atenção (Amarelo), 2: Alerta (Vermelho)

    // 6. FLEXPOWER & BIO-COMBUSTÍVEL
    val estimatedAfRatio: Float = 0f,
    val ethanolPercentage: Float = 0f,

    // 7. IGNIÇÃO E DETONAÇÃO
    val sparkAdvanceDeg: Float = 0f,
    val knockRetardDeg: Float = 0f,
    val knockCylinder1Deg: Float = 0f,
    val knockCylinder2Deg: Float = 0f,
    val knockCylinder3Deg: Float = 0f,
    val knockCylinder4Deg: Float = 0f,

    // 8. CONSUMO E EFICIÊNCIA
    val instantConsumptionLitersHour: Float = 0f,
    val instantConsumptionKmPerLiter: Float = 0f,

    // 9. EMISSÕES E CANISTER
    val evapPurgePercent: Float = 0f,

    // 10. ELÉTRICA & ATUADORES GM
    val ecuBatteryVoltage: Float = 0f,
    val elmBatteryVoltage: Float = 0f,
    val fuelPumpRelayStatus: Boolean = false,
    val acCompressorRelayStatus: Boolean = false,
    val coolingFanSpeed: String = "Desligado",
    val transmissionDetected: Boolean = false,
    val transmissionGear: String = "N/D",

    // 11. TELEMETRIA E DIAGNÓSTICO DO HARDWARE SERIAL
    val sampleRateHz: Float = 0f,
    val loopLatencyMs: Int = 0,

    // 12. EFICIÊNCIA VOLUMÉTRICA & REGIME OPERACIONAL DINÂMICO
    val volumetricEfficiencyPercent: Float = 0f,
    val engineRegime: EngineOperatingRegime = EngineOperatingRegime.OFF,
    val lambdaCrossCountPerSec: Float = 0f,
    val lambdaTransitionTimeMs: Int = 0,

    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Falha de diagnóstico (DTC)
 */
data class DtcCode(
    val code: String,
    val title: String,
    val system: String,
    val description: String,
    val symptoms: String,
    val probableCauses: String,
    val recommendedFix: String,
    val isPresent: Boolean = true
)

/**
 * Pino do Conector OBD-II (DLC) no Astra
 */
data class DlcPin(
    val pinNumber: Int,
    val designation: String,
    val system: String,
    val function: String,
    val wireColor: String,
    val isDiagnosticLine: Boolean
)

/**
 * Descritor de Sensor / Parâmetro mapeado pela ECU do Astra
 */
data class SensorDescriptor(
    val id: String,
    val name: String,
    val unit: String,
    val pidCommand: String,
    val mode: String, // "Mode 01 (SAE)", "Mode 21 (GM Local)", "Derivado Físico"
    val normalRange: String,
    val description: String,
    val isSupported: Boolean = true
)

/**
 * Mapeamento e perfil de sensores compatíveis conforme o modelo identificado da ECU Astra
 */
data class EcuSensorProfile(
    val ecuFamily: String,
    val engineFamily: String,
    val hasDriveByWire: Boolean,
    val hasMechanicalIac: Boolean,
    val hasFlexPids: Boolean,
    val hasIndividualKnock: Boolean,
    val hasPostCatLambda: Boolean,
    val hasPhysicalMaf: Boolean,
    val intakeType: String,
    val fuelDeliveryType: String,
    val compatibleSensors: List<SensorDescriptor>,
    val fastPollingCommands: List<String>,
    val slowPollingCommands: List<String>
)

/**
 * Estado e métricas do Heartbeat (Verificação de Vida a cada 2 segundos)
 */
data class HeartbeatStatus(
    val isAlive: Boolean = false,
    val lastPulseTimestamp: Long = 0L,
    val pulseLatencyMs: Long = 0L,
    val totalPulsesSent: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastCommandSent: String = "",
    val lastResponse: String = "",
    val statusDescription: String = "Inativo"
)

/**
 * Níveis de qualidade e autenticidade do chipset do adaptador ELM327 OBD-II
 */
enum class ElmQualityTier(val label: String, val badgeColorHex: Long) {
    UNKNOWN("Aguardando Análise", 0xFF888888),
    EXCELLENT_PIC18F("Original PIC18F25K80 (v1.5/v1.4)", 0xFF00E676),
    GOOD_COMPATIBLE("Compatível Estável (v1.5)", 0xFF00B0FF),
    QUESTIONABLE_CLONE("Clone v2.1 de Baixo Custo", 0xFFFF9100),
    UNSTABLE("Instável / Sem Linha K Real", 0xFFFF1744)
}

/**
 * Benchmark e auditoria de hardware do adaptador ELM327 conectado
 */
data class ElmChipBenchmark(
    val versionString: String = "ELM327",
    val deviceDescription: String = "",
    val qualityTier: ElmQualityTier = ElmQualityTier.UNKNOWN,
    val isKLineCertified: Boolean = true,
    val latencyMs: Long = 0L,
    val recommendationText: String = "Adaptador pronto para conexão."
)

/**
 * Modo de frequência e foco de amostragem da telemetria
 */
enum class TelemetrySamplingMode(val label: String, val description: String) {
    FULL_DASHBOARD("Varredura Completa", "Todos os sensores monitorados (~5 Hz)"),
    HIGH_RATE_FOCUS("Alta Resolução (Puxada)", "Foco em RPM + MAP + TPS (~18 Hz)")
}

/**
 * Regime de operação física do motor GM Astra detectado em tempo real
 */
enum class EngineOperatingRegime(val label: String, val badgeColorHex: Long) {
    OFF("Motor Parado (Chave Ligada)", 0xFF9E9E9E),
    COLD_START("Fase Fria / Aquecimento", 0xFF29B6F6),
    HOT_IDLE("Marcha Lenta Quente", 0xFF00E676),
    PARTIAL_LOAD("Carga Parcial / Cruzeiro", 0xFFAB47BC),
    WOT_FULL_LOAD("Plena Carga (WOT)", 0xFFFF7043)
}

/**
 * Picos e estatísticas de mínimo e máximo registrados durante a sessão
 */
data class SessionSensorPeaks(
    val minRpm: Float = Float.MAX_VALUE,
    val maxRpm: Float = 0f,
    val minMap: Float = Float.MAX_VALUE,
    val maxMap: Float = 0f,
    val minEct: Float = Float.MAX_VALUE,
    val maxEct: Float = 0f,
    val minVolt: Float = Float.MAX_VALUE,
    val maxVolt: Float = 0f,
    val minTps: Float = Float.MAX_VALUE,
    val maxTps: Float = 0f,
    val minLambda: Float = Float.MAX_VALUE,
    val maxLambda: Float = 0f,
    val maxVePercent: Float = 0f,
    val sampleCount: Long = 0L
) {
    fun hasValidPeaks(): Boolean = sampleCount > 0L && maxRpm > 0f
}

/**
 * Métrica de integridade e saúde da comunicação com a ECU na Linha K / CAN
 */
data class CommunicationBusHealth(
    val totalRequests: Long = 0L,
    val successfulFrames: Long = 0L,
    val invalidOrTimeoutFrames: Long = 0L,
    val healthPercent: Float = 100f,
    val averageLatencyMs: Long = 0L,
    val status: String = "Excelente (99%+)"
)

