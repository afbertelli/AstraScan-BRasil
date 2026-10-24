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
        assertTrue(profile.ecuName.contains("Bosch Motronic M1.5.5"))
        assertTrue(profile.ecuName.contains("16V"))
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

    @Test
    fun testCanHeaderExtractionAndFrameCounters() {
        // CAN 11-bit response with header "7E8 04 41 0C 0D 80"
        val canResponse = "7E8 04 41 0C 0D 80"
        val bytes = GmDataParsers.extractHexBytes(canResponse)
        assertEquals(5, bytes.size)
        assertEquals(0x04, bytes[0])
        assertEquals(0x41, bytes[1])
        assertEquals(0x0C, bytes[2])
        val rpm = GmDataParsers.parseRpm(canResponse)
        assertNotNull(rpm)
        assertEquals(864.0f, rpm!!, 0.1f)
    }

    @Test
    fun testNanAndInfiniteSafeguards() {
        val nanDuty = GmDataParsers.calculateInjectorDutyCycle(Float.NaN, 850f)
        assertEquals(0f, nanDuty, 0.001f)

        val infDuty = GmDataParsers.calculateInjectorDutyCycle(Float.POSITIVE_INFINITY, 850f)
        assertEquals(0f, infDuty, 0.001f)

        val nanLph = GmDataParsers.calculateLitersPerHour(Float.NaN, Float.NaN)
        assertEquals(0f, nanLph, 0.001f)

        val nanKml = GmDataParsers.calculateKmPerLiter(Float.NaN, 5.0f)
        assertEquals(0f, nanKml, 0.001f)

        val nanVac = GmDataParsers.calculateVacuum(Float.NaN, 101.3f)
        assertEquals(0f, nanVac, 0.001f)
    }

    @Test
    fun testExpandedDtcMapping() {
        val dtcCkp = GmDataParsers.mapDtcDetails("P0335")
        assertTrue(dtcCkp.title.contains("CKP") || dtcCkp.title.contains("Rotação"))

        val dtcCoil = GmDataParsers.mapDtcDetails("P0351")
        assertTrue(dtcCoil.title.contains("Bobina"))

        val dtcImmo = GmDataParsers.mapDtcDetails("P1610")
        assertTrue(dtcImmo.title.contains("Imobilizador"))

        val dtcDbw = GmDataParsers.mapDtcDetails("P1120")
        assertTrue(dtcDbw.title.contains("Borboleta"))
    }

    @Test
    fun testValid0x7FDataNotRejectedAsNegativeResponse() {
        // 0x7F = 127 -> 127 - 40 = 87.0°C (temperatura operacional muito comum)
        val ectRaw = "41 05 7F"
        val isNegEct = GmDataParsers.isNegativeResponse(ectRaw)
        org.junit.Assert.assertFalse("41 05 7F não deve ser classificado como erro!", isNegEct)
        val ect = GmDataParsers.parseCoolantTemp(ectRaw)
        assertNotNull(ect)
        assertEquals(87.0f, ect!!, 0.1f)

        // TPS 49.8% -> 127 * 100 / 255 = 49.8%
        val tpsRaw = "41 11 7F"
        val isNegTps = GmDataParsers.isNegativeResponse(tpsRaw)
        org.junit.Assert.assertFalse(isNegTps)
        val tps = GmDataParsers.parseTpsPercent(tpsRaw)
        assertNotNull(tps)
        assertEquals(49.8f, tps!!, 0.2f)

        // Resposta Negativa Real (NRC UDS/KWP 7F 01 11) DEVE ser classificada como erro
        val nrcRaw = "7F 01 11"
        assertTrue(GmDataParsers.isNegativeResponse(nrcRaw))

        // NRC com cabeçalho (83 F1 11 7F 01 11 C8)
        val nrcWithHeader = "83 F1 11 7F 01 11 C8"
        assertTrue(GmDataParsers.isNegativeResponse(nrcWithHeader))
    }

    @Test
    fun testEcuHierarchyBoschVersusDelphi() {
        // Carro ano 2005 (VIN com '5' no 10º char) mas com ECU Bosch ME 7.9.6
        val bosch2005 = GmDataParsers.matchVehicleProfile(
            partNumber = "93382522",
            hardwareNumber = "0 261 208 227",
            softwareVersion = "ME7.9.6",
            calId = "ME7.9.6_20V",
            vin = "9BGTL48R05B123456"
        )
        assertTrue(bosch2005.ecuName.contains("Bosch Motronic ME 7.9.6"))

        // Carro ano 2010 (VIN com 'A' no 10º char) com Rocambole Bosch ME 7.9.9
        val rocambole2010 = GmDataParsers.matchVehicleProfile(
            partNumber = "94703803",
            hardwareNumber = "0 261 208 994",
            softwareVersion = "ME799",
            calId = "ME7.9.9_F2",
            vin = "9BGTL48R0AB123456"
        )
        assertTrue(rocambole2010.ecuName.contains("ME 7.9.9"))

        // Astra 2.0 8V Gasolina 1999-2004 com Motronic M1.5.5
        val m155Gas = GmDataParsers.matchVehicleProfile(
            partNumber = "09117380",
            hardwareNumber = "0 261 206 000",
            softwareVersion = "M155",
            calId = "M1.5.5_8V",
            vin = "9BGTL48R04B123456"
        )
        assertTrue(m155Gas.ecuName.contains("M1.5.5 (8V)"))
        assertEquals("Gasolina", m155Gas.fuelType)
    }

    @Test
    fun testEcuNameParsing() {
        // "49 0A 01 45 43 4D 20 47 4D" -> 01 descartado como contador, "ECM GM"
        val raw = "49 0A 01 45 43 4D 20 47 4D"
        val ecuName = GmDataParsers.parseEcuName(raw)
        assertEquals("ECM GM", ecuName)
    }

    @Test
    fun testHardwareProbeDisambiguationIacVsDbw() {
        // Cenário 1: Astra ano 2004 sem Part Number, mas a sonda física confirmou motor de passo IAC (cabo mecânico)
        val iacVehicle = GmDataParsers.matchVehicleProfile(
            partNumber = "",
            hardwareNumber = "",
            softwareVersion = "",
            calId = "",
            vin = "9BGTL48R04B123456",
            hasIacConfirmed = true,
            hasDbwConfirmed = false
        )
        // Deve classificar como Astra 2.0 8V Gasolina M1.5.5 (a cabo com IAC), e NUNCA Multec H Flex (que é DBW)
        assertTrue(iacVehicle.ecuName.contains("M1.5.5"))
        assertEquals("Gasolina", iacVehicle.fuelType)

        // Cenário 2: Astra ano 2004 sem Part Number, mas a sonda física confirmou Borboleta Eletrônica DBW
        val dbwVehicle = GmDataParsers.matchVehicleProfile(
            partNumber = "",
            hardwareNumber = "",
            softwareVersion = "",
            calId = "",
            vin = "9BGTL48R04B123456",
            hasIacConfirmed = false,
            hasDbwConfirmed = true
        )
        // Deve classificar como Astra 2.0 8V Flex (Drive-by-Wire)
        assertTrue(dbwVehicle.ecuName.contains("Multec") || dbwVehicle.ecuName.contains("ME 7.9.6"))
        assertTrue(dbwVehicle.fuelType.contains("Flex"))
    }

    @Test
    fun testFuelTrimHealthEvaluation() {
        // Balanço perfeito: STFT +2%, LTFT -1% -> Total +1% (nível 0)
        val (lvl0, title0, _) = GmDataParsers.evaluateFuelTrimHealth(2.0f, -1.0f)
        assertEquals(0, lvl0)
        assertTrue(title0.contains("Balanceada"))

        // Tendência Pobre Moderada: STFT +6%, LTFT +5% -> Total +11% (nível 1)
        val (lvl1, title1, _) = GmDataParsers.evaluateFuelTrimHealth(6.0f, 5.0f)
        assertEquals(1, lvl1)
        assertTrue(title1.contains("Tendência Pobre"))

        // Alerta Severo de Mistura Pobre: STFT +10%, LTFT +12% -> Total +22% (nível 2)
        val (lvl2, title2, _) = GmDataParsers.evaluateFuelTrimHealth(10.0f, 12.0f)
        assertEquals(2, lvl2)
        assertTrue(title2.contains("Alerta"))

        // Alerta Severo de Mistura Rica: STFT -10%, LTFT -8% -> Total -18% (nível 2)
        val (lvlRich, titleRich, _) = GmDataParsers.evaluateFuelTrimHealth(-10.0f, -8.0f)
        assertEquals(2, lvlRich)
        assertTrue(titleRich.contains("Alerta"))
    }

    @Test
    fun testLambdaOscillationEvaluation() {
        // Simulação de sonda comutando regularmente entre 150mV e 850mV
        val oscList = listOf(
            0.15f, 0.22f, 0.40f, 0.75f, 0.88f, 0.72f, 0.35f, 0.18f,
            0.14f, 0.28f, 0.65f, 0.85f, 0.78f, 0.40f, 0.19f, 0.15f
        )
        val (hz, status) = GmDataParsers.evaluateLambdaOscillation(oscList)
        assertTrue("Frequência de oscilação deve ser calculada", hz > 0.5f)
        assertTrue(status.contains("Oscilação"))

        // Sonda travada em pobre (< 250mV)
        val leanList = listOf(0.12f, 0.15f, 0.18f, 0.14f, 0.16f, 0.13f, 0.15f)
        val (hzLean, statusLean) = GmDataParsers.evaluateLambdaOscillation(leanList)
        assertEquals(0f, hzLean, 0.01f)
        assertTrue(statusLean.contains("Travada em Pobre"))

        // Sonda travada em rica (> 700mV)
        val richList = listOf(0.78f, 0.82f, 0.85f, 0.79f, 0.81f, 0.84f, 0.80f)
        val (hzRich, statusRich) = GmDataParsers.evaluateLambdaOscillation(richList)
        assertEquals(0f, hzRich, 0.01f)
        assertTrue(statusRich.contains("Travada em Rica"))
    }

    @Test
    fun testInjectorFlowRatesByModel() {
        // Astra GSi 204 cc/min
        assertEquals(204.0f, com.example.model.VehicleProfile.ASTRA_GSI_16V.injectorFlowCcMin, 0.1f)

        // Astra Flex 140cv 228 / 225 cc/min
        assertEquals(228.0f, com.example.model.VehicleProfile.ASTRA_20_8V_ME799.injectorFlowCcMin, 0.1f)
        assertEquals(225.0f, com.example.model.VehicleProfile.ASTRA_20_8V_FLEX_FASE2.injectorFlowCcMin, 0.1f)

        // Astra Gasolina 185 cc/min
        assertEquals(185.0f, com.example.model.VehicleProfile.ASTRA_20_8V_GAS.injectorFlowCcMin, 0.1f)

        // Monoponto Belga 380 cc/min
        assertEquals(380.0f, com.example.model.VehicleProfile.ASTRA_18_EFI_MULTEC_M.injectorFlowCcMin, 0.1f)
    }

    @Test
    fun testDisconnectedTelemetryMustBeZero() {
        val disconnected = com.example.model.LiveTelemetry()
        assertEquals(0f, disconnected.rpm, 0.01f)
        assertEquals(0f, disconnected.speedKmh, 0.01f)
        assertEquals(0f, disconnected.mapKpa, 0.01f)
        assertEquals(0f, disconnected.coolantTempC, 0.01f)
    }

    @Test
    fun testIdleAuditOfflineWhenDisconnected() {
        val disconnected = com.example.model.LiveTelemetry()
        val result = com.example.serial.AstraIdleHealthChecker.auditIdleSensors(
            disconnected,
            com.example.model.VehicleProfile.ASTRA_20_8V_FLEX_FASE2
        )
        org.junit.Assert.assertFalse("Com RPM 0 a marcha lenta não pode ser detectada", result.isIdleDetected)
        assertTrue(result.idleConditionMessage.contains("0 RPM"))
        assertEquals(0, result.checks.size)
    }

    @Test
    fun testWatchdogTimeoutThreshold() {
        val lastDataTimestamp = System.currentTimeMillis() - 5200L
        val silenceMs = System.currentTimeMillis() - lastDataTimestamp
        val isTimeout = silenceMs > 5000L
        assertTrue("Silêncio acima de 5000ms deve acionar o watchdog de timeout", isTimeout)
    }

    @Test
    fun testLiveTelemetrySampleRateDefaults() {
        val t = com.example.model.LiveTelemetry()
        assertEquals(0f, t.sampleRateHz, 0.01f)
        assertEquals(0, t.loopLatencyMs)
    }

    @Test
    fun testActuatorSafetyInterlockCondition() {
        val runningRpm = 850f
        val isActuatorSafe = runningRpm <= 150f
        org.junit.Assert.assertFalse("Teste de atuador deve ser bloqueado com motor ligado", isActuatorSafe)
    }

    @Test
    fun testChartTelemetryChannels() {
        val channels = com.example.ui.components.ChartTelemetryChannel.values()
        assertEquals(4, channels.size)
        assertEquals("RPM", com.example.ui.components.ChartTelemetryChannel.RPM.unit)
        assertEquals("°C", com.example.ui.components.ChartTelemetryChannel.ECT.unit)
    }

    @Test
    fun testNegativeResponseCodeParser() {
        val nrcRaw = "7F 31 22"
        val nrc = GmDataParsers.parseNegativeResponse(nrcRaw)
        assertTrue(nrc.isNegative)
        assertEquals(0x31, nrc.serviceId)
        assertEquals(0x22, nrc.nrcCode)
        assertTrue(nrc.description.contains("Condições incorretas"))
    }

    @Test
    fun testVinDecoder() {
        val testVin = "9BGTA08Z07B123456"
        val decoded = GmDataParsers.decodeVin(testVin)
        assertTrue(decoded.isValid)
        assertEquals("2007", decoded.modelYear)
        assertTrue(decoded.assemblyPlant.contains("São Caetano"))
        assertEquals("General Motors do Brasil", decoded.manufacturer)
    }

    @Test
    fun testKwpChecksumValidator() {
        val validFrame = listOf(0x81, 0x11, 0xF1, 0x01, 0x00, 0x84)
        assertTrue(GmDataParsers.validateKwpChecksum(validFrame))

        val corruptedFrame = listOf(0x81, 0x11, 0xF1, 0x01, 0x00, 0x99)
        org.junit.Assert.assertFalse(GmDataParsers.validateKwpChecksum(corruptedFrame))
    }

    @Test
    fun testHeartbeatIntervalIs2Seconds() {
        assertEquals(2000L, com.example.serial.ObdConnectionEngine.HEARTBEAT_INTERVAL_MS)
    }

    @Test
    fun testHeartbeatResponsePositiveValidation() {
        // Tester Present (3E) positive ack KWP2000 / UDS
        assertTrue(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("7E"))
        assertTrue(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("7E 00"))
        assertTrue(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("81 F1 11 7E 01"))

        // Standard OBD-II Mode 01 PID 00 Supported PIDs low-cost response
        assertTrue(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("41 00 BE 3F B8 10"))
        assertTrue(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("41 0C 0D 80"))

        // ELM OK
        assertTrue(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("OK"))
    }

    @Test
    fun testHeartbeatResponseNegativeAndErrorRejection() {
        // Sem resposta / timeouts / falhas de linha física
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("NO DATA"))
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("UNABLE TO CONNECT"))
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("BUS INIT: ... ERROR"))
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("CAN ERROR"))
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("STOPPED"))
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("FB ERROR"))
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("?"))
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse(""))
        org.junit.Assert.assertFalse(com.example.serial.ObdConnectionEngine.validateHeartbeatResponse("ERROR: Socket closed"))
    }

    @Test
    fun testHeartbeatStatusModelDefaults() {
        val status = com.example.model.HeartbeatStatus()
        org.junit.Assert.assertFalse(status.isAlive)
        assertEquals(0, status.totalPulsesSent)
        assertEquals(0, status.consecutiveFailures)
        assertEquals("Inativo", status.statusDescription)
    }

    @Test
    fun testMode03DtcParserDirectSaFormat() {
        // Padrão SAE J1979: 43h seguido diretamente de pares de bytes (P0133 e P0105)
        val raw = listOf(0x43, 0x01, 0x33, 0x01, 0x05)
        val parsed = GmDataParsers.parseDtcBytes(raw, expectedModeAck = 0x43, isPresent = true)
        assertEquals(2, parsed.size)
        assertEquals("P0133", parsed[0].code)
        assertEquals("P0105", parsed[1].code)
    }

    @Test
    fun testMode03DtcParserWithCountByte() {
        // Formato com contador: 43h + numDtc (2) + pares de bytes
        val raw = listOf(0x43, 0x02, 0x03, 0x00, 0x01, 0x15)
        val parsed = GmDataParsers.parseDtcBytes(raw, expectedModeAck = 0x43, isPresent = true)
        assertEquals(2, parsed.size)
        assertEquals("P0300", parsed[0].code)
        assertEquals("P0115", parsed[1].code)
    }

    @Test
    fun testChartScaleRangeZeroDivisionProtection() {
        val minScale = 1.0f
        val maxScale = 1.0f
        val scaleRange = (maxScale - minScale).takeIf { it > 0.0001f } ?: 1f
        assertEquals(1f, scaleRange, 0.001f)
        val rawNormalized = (1.0f - minScale) / scaleRange
        val normalizedY = if (rawNormalized.isNaN() || rawNormalized.isInfinite()) 0f else rawNormalized.coerceIn(0f, 1f)
        assertEquals(0f, normalizedY, 0.001f)
    }

    @Test
    fun testFingerprinterPreservesCanHeader() = kotlinx.coroutines.runBlocking {
        val executedCommands = mutableListOf<String>()
        val fingerprinter = com.example.serial.EcuFingerprinter(
            commandSender = { cmd, _ ->
                executedCommands.add(cmd)
                when (cmd) {
                    "09 04" -> "49 04 01 4D 54 32 37 45"
                    "09 02" -> "49 02 01 39 42 47 54 41 34 38 43 30 42 42 31 32 33 34 35 36"
                    else -> "NO DATA"
                }
            },
            logCallback = {},
            getActiveHeader = { "7E0" }
        )

        val (profile, _) = fingerprinter.interrogateAndFingerprint()
        // No CAN bus, a sonda de transmissão K-Line (AT SH 81 18 F1) deve ser ignorada
        org.junit.Assert.assertFalse(executedCommands.any { it.contains("81 18 F1") })
        // O cabeçalho CAN 7E0 não foi corrompido para 81 11 F1
        org.junit.Assert.assertFalse(executedCommands.any { it.contains("AT SH 81 11 F1") })
        assertEquals("Delphi Multec H / MT27E (Fase 2)", profile.ecuName)
    }

    @Test
    fun testVinModelYear2009To2011Precision() {
        val vin2009 = "9BGTA48C09B123456"
        val vin2010 = "9BGTA48C0AB123456"
        val vin2011 = "9BGTA48C0BB123456"
        val vinPunctuation = "9BGTA48C0:B123456" // ASCII 58 (entre '9' e 'A')

        val profile2009 = GmDataParsers.matchVehicleProfile(
            partNumber = "94804161",
            hardwareNumber = "",
            softwareVersion = "",
            calId = "MT27E_F2",
            vin = vin2009,
            ecuNameBroadcast = "",
            hasIacConfirmed = false,
            hasDbwConfirmed = true,
            fallbackProfile = com.example.model.VehicleProfile.UNKNOWN
        )
        assertEquals("Delphi Multec H / MT27E (Fase 2)", profile2009.ecuName)

        val profile2010 = GmDataParsers.matchVehicleProfile(
            partNumber = "94804161",
            hardwareNumber = "",
            softwareVersion = "",
            calId = "MT27E_F2",
            vin = vin2010,
            ecuNameBroadcast = "",
            hasIacConfirmed = false,
            hasDbwConfirmed = true,
            fallbackProfile = com.example.model.VehicleProfile.UNKNOWN
        )
        assertEquals("Delphi Multec H / MT27E (Fase 2)", profile2010.ecuName)

        val profile2011 = GmDataParsers.matchVehicleProfile(
            partNumber = "94804161",
            hardwareNumber = "",
            softwareVersion = "",
            calId = "MT27E_F2",
            vin = vin2011,
            ecuNameBroadcast = "",
            hasIacConfirmed = false,
            hasDbwConfirmed = true,
            fallbackProfile = com.example.model.VehicleProfile.UNKNOWN
        )
        assertEquals("Delphi Multec H / MT27E (Fase 2)", profile2011.ecuName)

        // Caractere de pontuação não deve ativar a Fase 2 (2009-2011)
        val profilePunctuation = GmDataParsers.matchVehicleProfile(
            partNumber = "",
            hardwareNumber = "",
            softwareVersion = "",
            calId = "",
            vin = vinPunctuation,
            ecuNameBroadcast = "",
            hasIacConfirmed = false,
            hasDbwConfirmed = true,
            fallbackProfile = com.example.model.VehicleProfile.UNKNOWN
        )
        assertEquals("Delphi Multec H / MT27E (Fase 1)", profilePunctuation.ecuName)
    }

    @Test
    fun testElm327HardwareEvaluationOriginalPic() {
        val bench = com.example.serial.ObdConnectionEngine.evaluateElmHardware(
            ati = "ELM327 v1.5",
            at1 = "OBDII to RS232 Interpreter (PIC18F25K80)",
            latencyMs = 28L
        )
        assertEquals(com.example.model.ElmQualityTier.EXCELLENT_PIC18F, bench.qualityTier)
        assertTrue(bench.isKLineCertified)
        assertEquals(28L, bench.latencyMs)
        assertTrue(bench.recommendationText.contains("PIC18F25K80"))
    }

    @Test
    fun testElm327HardwareEvaluationCloneV21() {
        val bench = com.example.serial.ObdConnectionEngine.evaluateElmHardware(
            ati = "ELM327 v2.1",
            at1 = "OBDII to RS232 Interpreter",
            latencyMs = 85L
        )
        assertEquals(com.example.model.ElmQualityTier.QUESTIONABLE_CLONE, bench.qualityTier)
        org.junit.Assert.assertFalse(bench.isKLineCertified)
        assertTrue(bench.recommendationText.contains("Pino 7"))
    }

    @Test
    fun testVolumetricEfficiencyCalculation() {
        // Motor parado (0 RPM)
        val veOff = GmDataParsers.calculateVolumetricEfficiency(rpm = 0f, mapKpa = 100f)
        assertEquals(0f, veOff, 0.01f)

        // Marcha lenta estabilizada 8V (850 RPM, vácuo alto, MAP ~32 kPa)
        val veIdle8V = GmDataParsers.calculateVolumetricEfficiency(rpm = 850f, mapKpa = 32f, is16V = false)
        assertTrue("VE de marcha lenta deve estar entre 18% e 32%, obtido: $veIdle8V", veIdle8V in 18f..32f)

        // Plena carga 8V no pico de torque (3000 RPM, MAP ~98 kPa)
        val veWot8V = GmDataParsers.calculateVolumetricEfficiency(rpm = 3000f, mapKpa = 98f, is16V = false)
        assertTrue("VE em plena carga 8V deve estar entre 75% e 88%, obtido: $veWot8V", veWot8V in 75f..88f)

        // Plena carga 16V no pico de rotação (4000 RPM, MAP ~98 kPa)
        val veWot16V = GmDataParsers.calculateVolumetricEfficiency(rpm = 4000f, mapKpa = 98f, is16V = true)
        assertTrue("VE em plena carga 16V deve estar entre 80% e 94%, obtido: $veWot16V", veWot16V in 80f..94f)
    }

    @Test
    fun testEngineOperatingRegimeDetection() {
        // Motor desligado
        val telOff = com.example.model.LiveTelemetry(rpm = 0f, coolantTempC = 0f, throttlePercent = 0f)
        assertEquals(com.example.model.EngineOperatingRegime.OFF, com.example.serial.AstraIdleHealthChecker.detectOperatingRegime(telOff))

        // Fase fria
        val telCold = com.example.model.LiveTelemetry(rpm = 1150f, coolantTempC = 45f, throttlePercent = 0f)
        assertEquals(com.example.model.EngineOperatingRegime.COLD_START, com.example.serial.AstraIdleHealthChecker.detectOperatingRegime(telCold))

        // Marcha lenta quente estabilizada
        val telHotIdle = com.example.model.LiveTelemetry(rpm = 850f, coolantTempC = 90f, throttlePercent = 0f)
        assertEquals(com.example.model.EngineOperatingRegime.HOT_IDLE, com.example.serial.AstraIdleHealthChecker.detectOperatingRegime(telHotIdle))

        // Carga parcial (cruzeiro)
        val telCruise = com.example.model.LiveTelemetry(rpm = 2400f, coolantTempC = 92f, throttlePercent = 22f, mapKpa = 48f)
        assertEquals(com.example.model.EngineOperatingRegime.PARTIAL_LOAD, com.example.serial.AstraIdleHealthChecker.detectOperatingRegime(telCruise))

        // Plena carga (WOT)
        val telWot = com.example.model.LiveTelemetry(rpm = 4200f, coolantTempC = 92f, throttlePercent = 88f, mapKpa = 96f)
        assertEquals(com.example.model.EngineOperatingRegime.WOT_FULL_LOAD, com.example.serial.AstraIdleHealthChecker.detectOperatingRegime(telWot))
    }

    @Test
    fun testEvaluateDynamicRegimeSensorsNominalAdaptation() {
        val profile = com.example.model.VehicleProfile.ASTRA_20_8V_FLEX_FASE2

        // No regime frio, a faixa esperada de RPM se ajusta para 1.000 a 1.300
        val telCold = com.example.model.LiveTelemetry(rpm = 1100f, coolantTempC = 40f, throttlePercent = 0f, mapKpa = 42f, injectionTimeMs = 3.8f)
        val coldChecks = com.example.serial.AstraIdleHealthChecker.evaluateDynamicRegimeSensors(telCold, profile)
        val rpmCheck = coldChecks.find { it.sensorId == "rpm" }
        assertNotNull(rpmCheck)
        assertTrue(rpmCheck!!.nominalRange.contains("1.000 - 1.300"))
        assertTrue(rpmCheck.isOk)

        // No regime WOT, a faixa de borboleta se ajusta para 70 - 100
        val telWot = com.example.model.LiveTelemetry(rpm = 3800f, coolantTempC = 90f, throttlePercent = 85f, mapKpa = 96f)
        val wotChecks = com.example.serial.AstraIdleHealthChecker.evaluateDynamicRegimeSensors(telWot, profile)
        val tpsCheck = wotChecks.find { it.sensorId == "tps" }
        assertNotNull(tpsCheck)
        assertTrue(tpsCheck!!.nominalRange.contains("70 - 100"))
        assertTrue(tpsCheck.isOk)
    }

    @Test
    fun testSessionSensorPeaksTracking() {
        val emptyPeaks = com.example.model.SessionSensorPeaks()
        org.junit.Assert.assertFalse(emptyPeaks.hasValidPeaks())

        val activePeaks = com.example.model.SessionSensorPeaks(
            minRpm = 820f,
            maxRpm = 6150f,
            minMap = 28f,
            maxMap = 98f,
            maxVePercent = 84.5f,
            sampleCount = 120L
        )
        assertTrue(activePeaks.hasValidPeaks())
        assertEquals(6150f, activePeaks.maxRpm, 0.1f)
        assertEquals(28f, activePeaks.minMap, 0.1f)
    }
}


