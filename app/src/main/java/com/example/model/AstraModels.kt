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
    val vin: String = "",
    val supportsFlexPids: Boolean = false,
    val redlineRpm: Float = 6400f,
    val maxRpm: Float = 7000f,
    val displacementLiters: Float = 2.0f,
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
            engineCode = "C20SEL (DOHC 16V)",
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
            notes = "Astra GSi 2.0 16V C20SEL. Cabeçote DOHC 16V com coletor de admissão variável VIS e sensor de fase CMP."
        )

        // Banco de Fingerprints GM Brasil (Astra GSi 16V como prioridade)
        val GM_ASTRA_FINGERPRINTS = listOf(
            ASTRA_GSI_16V,
            VehicleProfile(
                ecuName = "Delphi Multec H / MT27E (Fase 2)",
                engineCode = "X20XE Flex Atualizado",
                engineDisplacement = "2.0 Litros (Família II)",
                valveCount = "8 Válvulas (SOHC)",
                horsepower = "133 cv (Gas) / 140 cv (Etanol)",
                fuelType = "Flexpower (Bicombustível)",
                years = "2009 - 2011",
                trimLevels = "Advantage, Elegance",
                gmPartNumber = "94703803",
                calId = "MT27E_F2",
                supportsFlexPids = true,
                redlineRpm = 6400f,
                displacementLiters = 2.0f,
                notes = "Coletor de admissão em plástico preto. Sensor de etanol estimado via lambda."
            ),
            VehicleProfile(
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
                displacementLiters = 2.0f,
                notes = "Coletor de admissão em alumínio fundido."
            ),
            VehicleProfile(
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
                displacementLiters = 2.0f,
                notes = "Injeção sequencial multiponto com acelerador a cabo."
            ),
            VehicleProfile(
                ecuName = "Delphi Multec EMS / Multec F",
                engineCode = "C18NE / 1.8",
                engineDisplacement = "1.8 Litros (Família II)",
                valveCount = "8 Válvulas (SOHC)",
                horsepower = "110 cv",
                fuelType = "Gasolina",
                years = "1998 - 2001",
                trimLevels = "GL Hatch / Sedan",
                gmPartNumber = "93278540",
                calId = "MULT_18",
                supportsFlexPids = false,
                redlineRpm = 6200f,
                displacementLiters = 1.8f,
                notes = "ECU Delphi com sensor MAP no coletor e IAC 4 passos."
            ),
            VehicleProfile(
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
                displacementLiters = 2.0f,
                notes = "Primeiro veículo do Brasil com GNV homologado de fábrica."
            ),
            VehicleProfile(
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
                displacementLiters = 2.0f,
                notes = "Linha Astra Belga importada. Linha K ISO 9141."
            ),
            VehicleProfile(
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
                displacementLiters = 2.0f,
                notes = "Astra F Station Wagon com ECU Siemens Simtec 56.5."
            )
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
