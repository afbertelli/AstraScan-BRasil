package com.example

import com.example.serial.GmDataParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testRpmConversionFormula() {
        val raw = "41 0C 0D 80"
        val rpm = GmDataParsers.parseRpm(raw)
        assertNotNull(rpm)
        assertEquals(864.0f, rpm!!, 0.1f)
    }

    @Test
    fun testSpeedConversion() {
        val raw = "41 0D 50" // 0x50 = 80 km/h
        val speed = GmDataParsers.parseSpeed(raw)
        assertNotNull(speed)
        assertEquals(80.0f, speed!!, 0.1f)
    }

    @Test
    fun testCoolantTempFormula() {
        val raw = "41 05 84"
        val ect = GmDataParsers.parseCoolantTemp(raw)
        assertNotNull(ect)
        assertEquals(92.0f, ect!!, 0.1f)
    }

    @Test
    fun testMapAndVacuumFormula() {
        val raw = "41 0B 23" // 35 kPa
        val map = GmDataParsers.parseMap(raw)
        assertNotNull(map)
        assertEquals(35.0f, map!!, 0.1f)

        val vacuum = GmDataParsers.calculateVacuum(map, 101.3f)
        assertEquals(0.663f, vacuum, 0.01f)
    }

    @Test
    fun testInjectionTimeFormula() {
        val raw = "61 15 0A F0" // 2.800 ms
        val ti = GmDataParsers.parseInjectionTime(raw)
        assertNotNull(ti)
        assertEquals(2.800f, ti!!, 0.005f)
    }

    @Test
    fun testDutyCycleFormula() {
        // T.I. = 2.8 ms, RPM = 850 -> Duty Cycle = (2.8 * 850) / 1200 = 1.98%
        val duty = GmDataParsers.calculateInjectorDutyCycle(2.8f, 850f)
        assertEquals(1.98f, duty, 0.05f)

        // T.I. = 15.0 ms, RPM = 6000 -> Duty Cycle = (15.0 * 6000) / 1200 = 75.0%
        val highDuty = GmDataParsers.calculateInjectorDutyCycle(15.0f, 6000f)
        assertEquals(75.0f, highDuty, 0.1f)
    }

    @Test
    fun testConsumptionCalculations() {
        // T.I. = 2.8 ms, RPM = 850, Bicos 190 cc/min
        val lPerHour = GmDataParsers.calculateLitersPerHour(2.8f, 850f, 190f)
        assertTrue(lPerHour in 0.8f..1.5f)

        // Em rodovia: 100 km/h e gastando 8.0 L/h -> 100 / 8 = 12.5 km/L
        val kmPerL = GmDataParsers.calculateKmPerLiter(100f, 8.0f)
        assertEquals(12.5f, kmPerL, 0.1f)
    }

    @Test
    fun testEngineLoadAndBaro() {
        val loadRaw = "41 04 68" // 0x68 = 104 -> (104 * 100) / 255 = 40.78%
        val load = GmDataParsers.parseEngineLoad(loadRaw)
        assertNotNull(load)
        assertEquals(40.78f, load!!, 0.1f)

        val baroRaw = "41 33 65" // 0x65 = 101 kPa
        val baro = GmDataParsers.parseBaro(baroRaw)
        assertNotNull(baro)
        assertEquals(101.0f, baro!!, 0.1f)
    }

    @Test
    fun testKnockPerCylinder() {
        val k1Raw = "61 4B 14" // 0x14 = 20 -> 20 * 0.05 = 1.0 grau
        val k1 = GmDataParsers.parseKnockCylinder(k1Raw, 0x4B)
        assertNotNull(k1)
        assertEquals(1.0f, k1!!, 0.05f)
    }

    @Test
    fun testSparkAdvanceFormula() {
        val raw = "41 0E 94"
        val spark = GmDataParsers.parseSparkAdvance(raw)
        assertNotNull(spark)
        assertEquals(10.0f, spark!!, 0.1f)
    }

    @Test
    fun testFlexpowerAfAndEthanolFormula() {
        val afRaw = "61 35 80"
        val af = GmDataParsers.parseAfRatio(afRaw)
        assertNotNull(af)
        assertEquals(12.8f, af!!, 0.1f)

        val ethRaw = "61 36 78"
        val eth = GmDataParsers.parseEthanolPercent(ethRaw)
        assertNotNull(eth)
        assertEquals(47.05f, eth!!, 0.5f)
    }

    @Test
    fun testDtcDecoding() {
        val p0105 = GmDataParsers.decodeDtc(0x01, 0x05)
        assertEquals("P0105", p0105)

        val p0115 = GmDataParsers.decodeDtc(0x01, 0x15)
        assertEquals("P0115", p0115)

        val p0335 = GmDataParsers.decodeDtc(0x03, 0x35)
        assertEquals("P0335", p0335)
    }

    @Test
    fun testFingerprintMatchingFlexpowerFase2() {
        val profile = GmDataParsers.matchVehicleProfile(
            partNumber = "94703803",
            hardwareNumber = "MT27E",
            softwareVersion = "2010",
            calId = "MT27E_F2",
            vin = "9BGTL48R0BB123456"
        )
        assertTrue(profile.ecuName.contains("Multec H / MT27E (Fase 2)"))
        assertTrue(profile.supportsFlexPids)
        assertTrue(profile.horsepower.contains("140 cv"))
    }

    @Test
    fun testFingerprintMatchingMotronic16V() {
        val profile = GmDataParsers.matchVehicleProfile(
            partNumber = "09117377",
            hardwareNumber = "0 261 204 466",
            softwareVersion = "M155",
            calId = "M1.5.5_16V",
            vin = ""
        )
        assertTrue(profile.ecuName.contains("Bosch Motronic M1.5.5 (16V)"))
        assertEquals("C20SEL", profile.engineCode)
        assertTrue(profile.valveCount.contains("16 Válvulas"))
    }

    @Test
    fun testAiDiagnosticEngineAudit() {
        val normalTelemetry = com.example.model.LiveTelemetry(
            rpm = 850f,
            targetIdleRpm = 850f,
            speedKmh = 0f,
            coolantTempC = 92f,
            intakeAirTempC = 30f,
            mapKpa = 34f,
            baroKpa = 101.3f,
            manifoldVacuumBar = 0.67f,
            throttlePercent = 14f,
            throttleVoltage1 = 0.74f,
            injectionTimeMs = 2.6f,
            injectorDutyCyclePercent = 1.8f,
            iacSteps = 28,
            lambdaVoltage = 0.450f,
            shortTermFuelTrim = 2.0f,
            longTermFuelTrim = -1.0f,
            elmBatteryVoltage = 13.9f,
            knockRetardDeg = 0.0f
        )
        val profile = com.example.model.VehicleProfile.GM_ASTRA_FINGERPRINTS[0]
        val audits = com.example.ai.AiDiagnosticEngine.auditComponents(normalTelemetry, profile)

        assertTrue(audits.isNotEmpty())
        val abnormals = audits.filter { it.status != com.example.ai.ComponentStatus.OK }
        assertEquals(0, abnormals.size)
    }

    @Test
    fun testAiDiagnosticEngineAnomalies() {
        // Simula anomalia crônica GM: entrada falsa de ar (MAP alto na lenta + STFT positivo excessivo)
        val faultTelemetry = com.example.model.LiveTelemetry(
            rpm = 980f,
            speedKmh = 0f,
            coolantTempC = 92f,
            mapKpa = 49f, // MAP alto na lenta (>42 kPa)
            throttlePercent = 14f,
            injectionTimeMs = 3.6f,
            lambdaVoltage = 0.200f,
            shortTermFuelTrim = 16.0f, // STFT adicionando combustível
            elmBatteryVoltage = 13.8f
        )
        val profile = com.example.model.VehicleProfile.GM_ASTRA_FINGERPRINTS[0]
        val audits = com.example.ai.AiDiagnosticEngine.auditComponents(faultTelemetry, profile)

        val mapAudit = audits.find { it.name.contains("MAP") }
        assertNotNull(mapAudit)
        assertTrue(mapAudit!!.status != com.example.ai.ComponentStatus.OK)

        val stftAudit = audits.find { it.name.contains("STFT") }
        assertNotNull(stftAudit)
        assertTrue(stftAudit!!.status != com.example.ai.ComponentStatus.OK)
    }

    @Test
    fun testEcuFingerprinterMotronicM155() = kotlinx.coroutines.runBlocking {
        val fingerprinter = com.example.serial.EcuFingerprinter(
            commandSender = { cmd, _ ->
                when (cmd) {
                    "1A 90" -> "5A 90 09 11 73 77" // GM Part Number 09117377 (Astra GSi 16V)
                    "1A 91" -> "5A 91 02 61 20 44 66" // Bosch 0 261 204 466
                    "1A 92" -> "5A 92 01 02 03"
                    "09 02" -> "7F 09 11" // NRC service not supported (sem VIN no KWP antigo)
                    "09 04" -> "49 04 01 4D 31 35 35" // CalID M155
                    else -> "NO DATA"
                }
            },
            logCallback = {}
        )

        val (vehicle, sensorMap) = fingerprinter.interrogateAndFingerprint()
        assertNotNull(vehicle)
        assertTrue(vehicle.ecuName.contains("Motronic M1.5.5"))
        assertTrue(vehicle.engineCode.contains("C20SEL") || vehicle.valveCount.contains("16"))

        // Verifica mapeamento automático de sensores para M1.5.5
        assertNotNull(sensorMap)
        assertTrue(sensorMap.hasMechanicalIac)
        assertTrue(!sensorMap.hasDriveByWire)
        assertTrue(sensorMap.hasIndividualKnock)
        assertTrue(sensorMap.compatibleSensors.any { it.id == "IAC" })
        assertTrue(sensorMap.compatibleSensors.any { it.id == "MAP" })
        assertTrue(sensorMap.compatibleSensors.any { it.id == "INJ_TIME" })
    }

    @Test
    fun testEcuFingerprinterFlexpowerDriveByWire() = kotlinx.coroutines.runBlocking {
        val fingerprinter = com.example.serial.EcuFingerprinter(
            commandSender = { cmd, _ ->
                when (cmd) {
                    "1A 90" -> "5A 90 94 70 38 03" // GM Part Number 94703803 (Astra Flexpower 140cv Fase 2)
                    "1A 91" -> "5A 91 12 34 56"
                    "1A 92" -> "5A 92 56 78 90"
                    "09 02" -> "49 02 01 39 42 47 41 53 54 52 41" // VIN
                    "09 04" -> "49 04 01 4D 54 32 37 45 5F 46 32" // MT27E_F2
                    else -> "NO DATA"
                }
            },
            logCallback = {}
        )

        val (vehicle, sensorMap) = fingerprinter.interrogateAndFingerprint()
        assertNotNull(vehicle)
        assertTrue(vehicle.ecuName.contains("Multec") || vehicle.fuelType.contains("Flex"))

        // Verifica mapeamento automático de sensores para Multec H Fase 2
        assertNotNull(sensorMap)
        assertTrue(sensorMap.hasDriveByWire)
        assertTrue(sensorMap.hasFlexPids)
        assertTrue(sensorMap.compatibleSensors.any { it.id == "TPS_VOLT2" })
        assertTrue(sensorMap.compatibleSensors.any { it.id == "PEDAL" })
        assertTrue(sensorMap.compatibleSensors.any { it.id == "ETHANOL" })
    }
}

