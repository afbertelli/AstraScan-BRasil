package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.model.DtcCode
import com.example.model.LiveTelemetry
import com.example.model.VehicleProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Status de conformidade do sensor com a especificação original de fábrica GM
 */
enum class ComponentStatus {
    OK,
    WARNING,
    ALERT
}

/**
 * Análise de conformidade de um parâmetro individual
 */
data class ComponentAudit(
    val name: String,
    val currentValue: String,
    val acceptableRange: String,
    val unit: String,
    val status: ComponentStatus,
    val diagnosisNote: String
)

/**
 * Resultado completo do diagnóstico da IA
 */
data class AiDiagnosticReport(
    val summary: String,
    val rootCause: String,
    val abnormalComponents: List<ComponentAudit>,
    val normalComponents: List<ComponentAudit>,
    val crossAnalysisDetails: String,
    val actionPlan: List<String>,
    val isGeneratedByGemini: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

object AiDiagnosticEngine {

    private const val TAG = "AiDiagnosticEngine"
    private const val GEMINI_MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Audita todos os parâmetros em tempo real contra as tolerâncias OEM da GM Brasil
     */
    fun auditComponents(t: LiveTelemetry, p: VehicleProfile): List<ComponentAudit> {
        val list = mutableListOf<ComponentAudit>()

        // 1. Rotação (RPM)
        val isIdle = t.speedKmh <= 2f && t.throttlePercent <= 16f
        val rpmStatus = if (isIdle) {
            val dev = Math.abs(t.rpm - t.targetIdleRpm)
            when {
                dev > 250f -> ComponentStatus.ALERT
                dev > 100f -> ComponentStatus.WARNING
                else -> ComponentStatus.OK
            }
        } else {
            if (t.rpm > p.redlineRpm) ComponentStatus.ALERT else ComponentStatus.OK
        }
        list.add(
            ComponentAudit(
                name = "Rotação do Motor (RPM)",
                currentValue = "${t.rpm.toInt()}",
                acceptableRange = if (isIdle) "800 - 950 (Alvo: ${t.targetIdleRpm.toInt()})" else "850 - ${p.redlineRpm.toInt()}",
                unit = "RPM",
                status = rpmStatus,
                diagnosisNote = if (isIdle && Math.abs(t.rpm - t.targetIdleRpm) > 120) "Oscilação de lenta (verificar atuador IAC e entradas de ar)" else "Rotação coerente"
            )
        )

        // 2. Temperatura do Arrefecimento (ECT)
        val ectStatus = when {
            t.coolantTempC > 102f -> ComponentStatus.ALERT
            t.coolantTempC in 98f..102f -> ComponentStatus.WARNING
            t.coolantTempC < 75f && t.speedKmh > 40f -> ComponentStatus.WARNING // Termostática travada aberta
            else -> ComponentStatus.OK
        }
        list.add(
            ComponentAudit(
                name = "Sensor de Temperatura da Água (ECT)",
                currentValue = String.format(Locale.US, "%.1f", t.coolantTempC),
                acceptableRange = "88.0 - 98.0",
                unit = "°C",
                status = ectStatus,
                diagnosisNote = when {
                    t.coolantTempC > 100f -> "Temperatura crítica: risco de queima da junta do cabeçote ou falha de acionamento do eletroventilador."
                    t.coolantTempC < 75f && t.speedKmh > 30f -> "Motor trabalhando frio em rodovia: provável válvula termostática travada aberta ou ausente."
                    else -> "Faixa térmica de operação ideal."
                }
            )
        )

        // 3. Temperatura do Ar de Admissão (IAT)
        val iatStatus = if (t.intakeAirTempC > 65f || t.intakeAirTempC < -10f) ComponentStatus.WARNING else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Sensor de Temperatura do Ar (IAT)",
                currentValue = String.format(Locale.US, "%.1f", t.intakeAirTempC),
                acceptableRange = "15.0 - 55.0",
                unit = "°C",
                status = iatStatus,
                diagnosisNote = if (iatStatus != ComponentStatus.OK) "Temperatura de admissão anormalmente alta (perda de densidade do ar)." else "Densidade de ar normal."
            )
        )

        // 4. Pressão Absoluta no Coletor (MAP)
        val mapStatus = if (isIdle) {
            when {
                t.mapKpa > 46f -> ComponentStatus.ALERT
                t.mapKpa in 41f..46f -> ComponentStatus.WARNING
                t.mapKpa < 26f -> ComponentStatus.WARNING
                else -> ComponentStatus.OK
            }
        } else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Sensor de Pressão no Coletor (MAP)",
                currentValue = "${t.mapKpa.toInt()}",
                acceptableRange = if (isIdle) "28 - 40" else "25 - 105",
                unit = "kPa",
                status = mapStatus,
                diagnosisNote = if (isIdle && t.mapKpa > 42f) "Pressão no coletor elevada na lenta: indício claro de entrada falsa de ar, mangueira de vácuo solta, ponto atrasado ou sincronismo de correia dentada fora." else "Vácuo e vedação do coletor normais."
            )
        )

        // 5. Vácuo Derivado do Coletor
        val vacStatus = if (isIdle && t.manifoldVacuumBar < 0.55f) ComponentStatus.WARNING else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Vácuo Efetivo do Coletor",
                currentValue = String.format(Locale.US, "%.2f", t.manifoldVacuumBar),
                acceptableRange = if (isIdle) "0.58 - 0.72" else "0.00 - 0.80",
                unit = "bar",
                status = vacStatus,
                diagnosisNote = if (vacStatus != ComponentStatus.OK) "Vácuo baixo no coletor de admissão." else "Depressão do coletor adequada."
            )
        )

        // 6. Tempo de Injeção (T.I.)
        val tiStatus = if (isIdle) {
            when {
                t.injectionTimeMs > 3.8f -> ComponentStatus.ALERT
                t.injectionTimeMs > 3.3f -> ComponentStatus.WARNING
                t.injectionTimeMs < 1.6f -> ComponentStatus.WARNING
                else -> ComponentStatus.OK
            }
        } else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Tempo de Injeção (T.I.)",
                currentValue = String.format(Locale.US, "%.2f", t.injectionTimeMs),
                acceptableRange = if (isIdle) "1.80 - 3.20" else "1.50 - 18.00",
                unit = "ms",
                status = tiStatus,
                diagnosisNote = if (isIdle && t.injectionTimeMs > 3.3f) "Tempo de injeção excessivo na marcha lenta (central enriquecendo para compensar falta de combustível ou leitura alta de MAP)." else "Pulso dos eletroinjetores calibrado."
            )
        )

        // 7. Duty Cycle dos Injetores
        val dutyStatus = if (t.injectorDutyCyclePercent > 85f) ComponentStatus.ALERT else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Duty Cycle dos Injetores",
                currentValue = String.format(Locale.US, "%.1f", t.injectorDutyCyclePercent),
                acceptableRange = "1.0 - 80.0",
                unit = "%",
                status = dutyStatus,
                diagnosisNote = if (dutyStatus != ComponentStatus.OK) "Injetores operando próximos da saturação contínua." else "Margem de fluxo dos bicos segura."
            )
        )

        // 8. Posição da Borboleta (TPS %)
        val tpsStatus = if (isIdle && t.throttlePercent > 18f) ComponentStatus.WARNING else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Posição da Borboleta (TPS %)",
                currentValue = String.format(Locale.US, "%.1f", t.throttlePercent),
                acceptableRange = if (isIdle) "0.0 - 16.0" else "0.0 - 100.0",
                unit = "%",
                status = tpsStatus,
                diagnosisNote = if (tpsStatus != ComponentStatus.OK) "Borboleta aberta além do normal para condição de marcha lenta." else "Posicionamento angular dentro da tolerância."
            )
        )

        // 9. Tensão da Pista 1 do TPS
        val tpsVStatus = if (isIdle && (t.throttleVoltage1 > 1.1f || t.throttleVoltage1 < 0.35f)) ComponentStatus.WARNING else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Tensão TPS Potenciômetro 1",
                currentValue = String.format(Locale.US, "%.2f", t.throttleVoltage1),
                acceptableRange = if (isIdle) "0.45 - 0.90" else "0.45 - 4.60",
                unit = "V",
                status = tpsVStatus,
                diagnosisNote = if (tpsVStatus != ComponentStatus.OK) "Tensão fora da curva de calibração OEM (desgaste na pista do sensor)." else "Sinal elétrico da pista linear."
            )
        )

        // 10. Sonda Lambda 1 (Tensão Pré-Catalisador)
        val lambdaStatus = if (t.lambdaVoltage < 0.05f || t.lambdaVoltage > 0.98f) ComponentStatus.ALERT else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Sonda Lambda 1 (Pré-Cat)",
                currentValue = String.format(Locale.US, "%.3f", t.lambdaVoltage),
                acceptableRange = "0.080 - 0.920",
                unit = "V",
                status = lambdaStatus,
                diagnosisNote = when {
                    t.lambdaVoltage < 0.12f -> "Mistura excessivamente pobre ou sonda inoperante/travada em zero."
                    t.lambdaVoltage > 0.88f -> "Mistura rica constante (sonda não está ciclando)."
                    else -> "Sonda operando e alternando entre rica e pobre."
                }
            )
        )

        // 11. Ajuste a Curto Prazo (STFT)
        val stftStatus = when {
            Math.abs(t.shortTermFuelTrim) > 18f -> ComponentStatus.ALERT
            Math.abs(t.shortTermFuelTrim) > 10f -> ComponentStatus.WARNING
            else -> ComponentStatus.OK
        }
        list.add(
            ComponentAudit(
                name = "Ajuste de Combustível a Curto Prazo (STFT)",
                currentValue = String.format(Locale.US, "%+.1f", t.shortTermFuelTrim),
                acceptableRange = "-10.0 a +10.0",
                unit = "%",
                status = stftStatus,
                diagnosisNote = when {
                    t.shortTermFuelTrim > 12f -> "ECU adicionando excesso de combustível (compensando entrada falsa de ar ou baixa pressão na linha de combustível)."
                    t.shortTermFuelTrim < -12f -> "ECU retirando combustível excessivamente (bico gotejando, pressão de combustível muito alta ou canister travado aberto)."
                    else -> "Correção estequiométrica em malha fechada normal."
                }
            )
        )

        // 12. Ajuste a Longo Prazo (LTFT)
        val ltftStatus = when {
            Math.abs(t.longTermFuelTrim) > 15f -> ComponentStatus.ALERT
            Math.abs(t.longTermFuelTrim) > 8f -> ComponentStatus.WARNING
            else -> ComponentStatus.OK
        }
        list.add(
            ComponentAudit(
                name = "Ajuste de Combustível a Longo Prazo (LTFT)",
                currentValue = String.format(Locale.US, "%+.1f", t.longTermFuelTrim),
                acceptableRange = "-8.0 a +8.0",
                unit = "%",
                status = ltftStatus,
                diagnosisNote = if (ltftStatus != ComponentStatus.OK) "Mapa de combustível autoadaptativo com desvio acentuado persistente." else "Mapa base de injeção equilibrado."
            )
        )

        // 13. Retardo por Detonação Geral (Knock Sensor)
        val knockStatus = if (t.knockRetardDeg > 1.0f) ComponentStatus.ALERT else if (t.knockRetardDeg > 0.2f) ComponentStatus.WARNING else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Retardo por Detonação (Batida de Pino)",
                currentValue = String.format(Locale.US, "%.2f", t.knockRetardDeg),
                acceptableRange = "0.00 - 0.20",
                unit = "° Retardo",
                status = knockStatus,
                diagnosisNote = if (knockStatus != ComponentStatus.OK) "Detonação ativa detectada no bloco: a ECU atrasou o avanço para proteger pistões e bielas. Combustível adulterado ou carbonização." else "Sem ocorrência de pré-ignição ou detonação."
            )
        )

        // 14. Tensão da Bateria (Lida pela ECU e ELM327)
        val voltStatus = when {
            t.elmBatteryVoltage < 12.6f -> ComponentStatus.ALERT
            t.elmBatteryVoltage < 13.4f -> ComponentStatus.WARNING
            t.elmBatteryVoltage > 15.1f -> ComponentStatus.ALERT
            else -> ComponentStatus.OK
        }
        list.add(
            ComponentAudit(
                name = "Tensão do Sistema Elétrico (Bateria / Alternador)",
                currentValue = String.format(Locale.US, "%.1f", t.elmBatteryVoltage),
                acceptableRange = "13.4 - 14.7",
                unit = "V",
                status = voltStatus,
                diagnosisNote = when {
                    t.elmBatteryVoltage < 13.0f -> "Tensão insuficiente com motor em funcionamento: falha no alternador, regulador de tensão ou correia de acessórios escorregando."
                    t.elmBatteryVoltage > 15.0f -> "Sobretensão severa: risco de queima de módulos eletrônicos e lâmpadas."
                    else -> "Carga do alternador perfeita."
                }
            )
        )

        // 15. Passo da Marcha Lenta (Atuador IAC)
        val iacStatus = if (isIdle && (t.iacSteps > 65 || t.iacSteps < 10)) ComponentStatus.WARNING else ComponentStatus.OK
        list.add(
            ComponentAudit(
                name = "Atuador da Marcha Lenta (IAC)",
                currentValue = "${t.iacSteps}",
                acceptableRange = if (isIdle) "15 - 55" else "5 - 120",
                unit = "Passos",
                status = iacStatus,
                diagnosisNote = if (iacStatus != ComponentStatus.OK) "Passos fora da faixa média da TBI GM: carbonização na sede cônica do atuador ou batente da borboleta desregulado." else "Ajuste de vazão de ar da lenta normal."
            )
        )

        // 16. Razão A/F e Teor de Etanol (se Flexpower)
        if (p.supportsFlexPids) {
            val afStatus = if (t.estimatedAfRatio !in 9.0f..14.8f) ComponentStatus.WARNING else ComponentStatus.OK
            list.add(
                ComponentAudit(
                    name = "Razão A/F Estimada (Flexpower)",
                    currentValue = String.format(Locale.US, "%.1f:1", t.estimatedAfRatio),
                    acceptableRange = "9.0:1 (100% Etanol) a 14.7:1 (Gasolina Pura)",
                    unit = ":1",
                    status = afStatus,
                    diagnosisNote = "Teor de Álcool calculado em ${t.ethanolPercentage.toInt()}%. Se abastecido recentemente e o carro falhar a frio, force o reconhecimento de A/F rodando 10 km."
                )
            )
        }

        return list
    }

    /**
     * Executa o diagnóstico cruzado inteligente usando Gemini API (gemini-3.5-flash)
     * com fallback offline caso a chave ou conexão não estejam disponíveis.
     */
    suspend fun runDiagnosis(
        telemetry: LiveTelemetry,
        profile: VehicleProfile,
        activeDtcs: List<DtcCode>,
        historyDtcs: List<DtcCode>
    ): AiDiagnosticReport = withContext(Dispatchers.IO) {
        val audits = auditComponents(telemetry, profile)
        val abnormals = audits.filter { it.status != ComponentStatus.OK }
        val normals = audits.filter { it.status == ComponentStatus.OK }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val prompt = buildAnalysisPrompt(telemetry, profile, activeDtcs, historyDtcs, abnormals, normals)
                val geminiResponse = callGeminiRest(apiKey, prompt)
                if (geminiResponse != null && geminiResponse.isNotBlank()) {
                    return@withContext parseGeminiDiagnostic(geminiResponse, abnormals, normals)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha na chamada Gemini API: ${e.message}. Acionando engine especialista offline...")
            }
        }

        // Fallback de Regras Especialistas GM Astra offline
        return@withContext generateExpertOfflineDiagnosis(telemetry, profile, activeDtcs, historyDtcs, abnormals, normals)
    }

    private fun buildAnalysisPrompt(
        t: LiveTelemetry,
        p: VehicleProfile,
        activeDtcs: List<DtcCode>,
        historyDtcs: List<DtcCode>,
        abnormals: List<ComponentAudit>,
        normals: List<ComponentAudit>
    ): String {
        return """
Você é um Engenheiro Mecânico Sênior e Especialista Chefe em Injeção Eletrônica de Motores GM Família II (Chevrolet Astra 1995-2011 Brasil).
Analise a telemetria ao vivo extraída do veículo, cruze todos os dados de sensores entre si e forneça um diagnóstico técnico aprofundado.

VEÍCULO IDENTIFICADO:
- Modelo: ${p.ecuName}
- Motor: ${p.engineCode} (${p.engineDisplacement} ${p.valveCount})
- Potência: ${p.horsepower} | Combustível: ${p.fuelType}
- Part Number: ${p.gmPartNumber} | CalID: ${p.calId}

FALHAS REGISTRADAS NA CENTRAL:
- Ativas (SAE 03 / KWP 18): ${if (activeDtcs.isEmpty()) "Nenhuma falha ativa registrada" else activeDtcs.joinToString { "${it.code} (${it.title})" }}
- Histórico (SAE 07): ${if (historyDtcs.isEmpty()) "Nenhuma" else historyDtcs.joinToString { "${it.code}" }}

TELEMETRIA EM TEMPO REAL:
- Rotação: ${t.rpm.toInt()} RPM (Alvo de Marcha Lenta: ${t.targetIdleRpm.toInt()} RPM)
- Velocidade: ${t.speedKmh.toInt()} km/h
- Temperatura Arrefecimento (ECT): ${String.format(Locale.US, "%.1f", t.coolantTempC)} °C
- Temperatura Ar Admissão (IAT): ${String.format(Locale.US, "%.1f", t.intakeAirTempC)} °C
- Pressão Coletor (MAP): ${t.mapKpa.toInt()} kPa | Vácuo: ${String.format(Locale.US, "%.2f", t.manifoldVacuumBar)} bar | BARO: ${t.baroKpa.toInt()} kPa
- Carga do Motor: ${String.format(Locale.US, "%.1f", t.engineLoadPercent)}%
- Tempo de Injeção: ${String.format(Locale.US, "%.2f", t.injectionTimeMs)} ms | Duty Cycle: ${String.format(Locale.US, "%.1f", t.injectorDutyCyclePercent)}%
- Borboleta TPS: ${String.format(Locale.US, "%.1f", t.throttlePercent)}% (Tensão TPS1: ${String.format(Locale.US, "%.2f", t.throttleVoltage1)}V)
- Avanço de Ignição: ${String.format(Locale.US, "%.1f", t.sparkAdvanceDeg)}° APMS
- Retardo Detonação (Knock): ${String.format(Locale.US, "%.2f", t.knockRetardDeg)}° (Cil 1: ${t.knockCylinder1Deg}°, Cil 2: ${t.knockCylinder2Deg}°, Cil 3: ${t.knockCylinder3Deg}°, Cil 4: ${t.knockCylinder4Deg}°)
- Sonda Lambda 1: ${String.format(Locale.US, "%.3f", t.lambdaVoltage)}V | Malha: ${t.fuelLoopStatus}
- Ajustes de Combustível: STFT ${String.format(Locale.US, "%+.1f", t.shortTermFuelTrim)}% | LTFT ${String.format(Locale.US, "%+.1f", t.longTermFuelTrim)}%
- Mistura Flexpower: Razão A/F ${String.format(Locale.US, "%.1f", t.estimatedAfRatio)}:1 | Etanol ${t.ethanolPercentage.toInt()}%
- Tensão Elétrica: ${String.format(Locale.US, "%.1f", t.elmBatteryVoltage)}V
- Atuador IAC: ${t.iacSteps} passos

SENSORES FORA DO PADRÃO ACEITÁVEL IDENTIFICADOS PELO AUDITOR:
${if (abnormals.isEmpty()) "Todos os sensores estão dentro da faixa nominal estatística." else abnormals.joinToString("\n") { "- ${it.name}: Lido ${it.currentValue} ${it.unit} (Padrão: ${it.acceptableRange}) -> ${it.diagnosisNote}" }}

INSTRUÇÕES DE RESPOSTA:
Gere uma resposta técnica estruturada em Português do Brasil com:
1. RESUMO EXECUTIVO: Avaliação geral da saúde da injeção e do motor.
2. CRUZAMENTO DE DADOS: Relações de causa e efeito entre os sensores (ex: se MAP está alto, como afeta T.I. e avanço; se ECT está baixo, se impede malha fechada; se STFT está positivo/negativo, etc.).
3. CAUSA RAIZ PROVÁVEL: O componente ou falha primária responsável pelo desvio (ex: entrada de ar falsa, sensor MAP defeituoso, bobina em fuga, termostática travada, sonda lenta, etc.).
4. PLANO DE AÇÃO: 3 a 5 testes físicos e procedimentos recomendados para o mecânico ou proprietário testar no cofre do motor.
""".trimIndent()
    }

    private fun callGeminiRest(apiKey: String, prompt: String): String? {
        val jsonPayload = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArr)
                }
                put(contentObj)
            }
            put("contents", contentsArr)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("topP", 0.95)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toString().toRequestBody(mediaType)
        val url = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini API HTTP Error: ${response.code} - ${response.message}")
            return null
        }

        val respBody = response.body?.string() ?: return null
        val respJson = JSONObject(respBody)
        val candidates = respJson.optJSONArray("candidates") ?: return null
        if (candidates.length() > 0) {
            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                return parts.getJSONObject(0).optString("text")
            }
        }
        return null
    }

    private fun parseGeminiDiagnostic(
        text: String,
        abnormals: List<ComponentAudit>,
        normals: List<ComponentAudit>
    ): AiDiagnosticReport {
        val lines = text.lines()
        val actions = mutableListOf<String>()

        var summary = "Diagnóstico inteligente gerado pelo Gemini 3.5 Flash com base nos parâmetros ao vivo da injeção."
        var rootCause = "Análise concluída com sucesso."

        // Extrai linhas de plano de ação
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || (trimmed.length > 3 && trimmed[0].isDigit() && trimmed[1] == '.')) {
                val clean = trimmed.removePrefix("-").removePrefix("*").replace(Regex("""^\d+\.\s*"""), "").trim()
                if (clean.length > 10 && actions.size < 6) {
                    actions.add(clean)
                }
            }
        }

        if (actions.isEmpty()) {
            actions.add("Conferir estanqueidade da mangueira de vácuo do sensor MAP.")
            actions.add("Inspecionar vedação dos anéis O-Ring dos bicos injetores.")
            actions.add("Verificar chicote do sensor de temperatura da água da ECU.")
        }

        return AiDiagnosticReport(
            summary = summary,
            rootCause = if (abnormals.isNotEmpty()) "Identificados ${abnormals.size} componentes fora da faixa ideal de tolerância GM." else "Todos os componentes operando na faixa nominal de fábrica.",
            abnormalComponents = abnormals,
            normalComponents = normals,
            crossAnalysisDetails = text,
            actionPlan = actions,
            isGeneratedByGemini = true
        )
    }

    /**
     * Fallback Especialista Offline baseado em regras mecânicas automotivas da Família II
     */
    private fun generateExpertOfflineDiagnosis(
        t: LiveTelemetry,
        p: VehicleProfile,
        activeDtcs: List<DtcCode>,
        historyDtcs: List<DtcCode>,
        abnormals: List<ComponentAudit>,
        normals: List<ComponentAudit>
    ): AiDiagnosticReport {
        val actions = mutableListOf<String>()
        val crossBuilder = StringBuilder()

        var rootCause = "Parâmetros normais de funcionamento."
        var summary = "Varredura completa de componentes realizada com cruzamento estequiométrico em tempo real."

        // Caso 1: Entrada falsa de ar / MAP alto com STFT positivo
        if (t.mapKpa > 42f && t.shortTermFuelTrim > 8f) {
            rootCause = "Entrada falsa de ar no coletor de admissão ou mangueira de vácuo do sensor MAP fissurada."
            summary = "Anomalia de Pressão e Mistura: A pressão de coletor está anormalmente alta (${t.mapKpa.toInt()} kPa na lenta) e a ECU está sendo forçada a injetar mais combustível (STFT ${String.format(Locale.US, "%+.1f", t.shortTermFuelTrim)}%) para compensar ar parasita não medido."
            actions.add("Testar vedação com máquina de fumaça (Smoke Test) no coletor de admissão e mangueira do servo-freio.")
            actions.add("Verificar mangueira de tomada de vácuo conectada atrás do sensor MAP.")
            actions.add("Inspecionar anéis de vedação (O-Rings) plásticos dos bicos injetores no cabeçote.")
            actions.add("Limpar o corpo de borboleta TBI e verificar se há folga no eixo da borboleta.")
        }
        // Caso 2: Temperatura baixa (válvula termostática travada aberta)
        else if (t.coolantTempC < 75f && t.speedKmh > 20f) {
            rootCause = "Válvula termostática aberta ou ausente no alojamento do cabeçote."
            summary = "Motor operando fora da temperatura de eficiência térmica: O líquido de arrefecimento está a ${t.coolantTempC.toInt()}°C. A ECU permanece em modo de aquecimento com enriquecimento forçado de mistura (T.I. prolongado), aumentando o consumo em até 30%."
            actions.add("Substituir a válvula termostática original (início de abertura em 92°C).")
            actions.add("Sangrar o sistema de arrefecimento e utilizar aditivo orgânico na proporção de 50%.")
            actions.add("Inspecionar o conector elétrico de 2 vias do sensor ECT (crônico por oxidação interna).")
        }
        // Caso 3: Superaquecimento
        else if (t.coolantTempC > 100f) {
            rootCause = "Superaquecimento: Falha de acionamento do eletroventilador, válvula termostática travada fechada ou perda de pressão no arrefecimento."
            summary = "Alerta Térmico Crítico: Líquido a ${t.coolantTempC.toInt()}°C. Risco de empenamento do cabeçote de alumínio."
            actions.add("Verificar relés de velocidade 1 e 2 do eletroventilador na caixa do vão do motor.")
            actions.add("Testar tampa do reservatório de expansão (deve suportar 1.4 bar de pressão).")
            actions.add("Conferir circulação de água e bomba de água.")
        }
        // Caso 4: Sonda Lambda travada / Mistura rica constante
        else if (t.lambdaVoltage > 0.85f && t.shortTermFuelTrim < -8f) {
            rootCause = "Enriquecimento excessivo: bicos injetores com estanqueidade comprometida ou válvula do canister travada aberta."
            summary = "A central está cortando combustível (STFT negativo), mas a sonda permanece travada em mistura rica (${String.format(Locale.US, "%.3f", t.lambdaVoltage)}V)."
            actions.add("Efetuar teste de estanqueidade e vazão dos 4 bicos injetores em bancada de ultrassom.")
            actions.add("Tampar momentaneamente a mangueira da válvula de purga do canister para isolar entrada de vapores.")
            actions.add("Medir a pressão da linha de combustível (deve manter 3.0 bar estabilizados no regulador).")
        }
        // Caso 5: Detonação ativa
        else if (t.knockRetardDeg > 0.5f) {
            rootCause = "Detonação (batida de pino) detectada no bloco do motor."
            summary = "O sensor piezoelétrico registrou ruído acústico de pré-ignição, provocando recuo dinâmico de ${String.format(Locale.US, "%.1f", t.knockRetardDeg)}° no avanço."
            actions.add("Verificar qualidade do combustível e se há mistura indevida de gasolina comum de baixa octanagem.")
            actions.add("Verificar torque do parafuso do sensor de detonação no bloco (deve ser exatamente 20 Nm estritos).")
            actions.add("Inspecionar velas de ignição (grau térmico NGK correto e folga de 0.8mm a 1.0mm).")
        }
        // Caso Normal
        else {
            summary = "Parâmetros estequiométricos e elétricos em perfeito alinhamento com a calibração de fábrica GM."
            rootCause = "Nenhuma falha crítica detectada nos componentes monitorados."
            actions.add("Sistema operando em conformidade com as normas OEM GM Brasil.")
            actions.add("Recomenda-se manter a manutenção preventiva periódica (óleo 5W30/15W40, velas e filtros).")
        }

        crossBuilder.append("Cruzamento Estequiométrico em Tempo Real:\n")
        crossBuilder.append("• Razão MAP / T.I.: ${t.mapKpa.toInt()} kPa gerando ${String.format(Locale.US, "%.2f", t.injectionTimeMs)} ms de injeção.\n")
        crossBuilder.append("• Resposta da Sonda: ${String.format(Locale.US, "%.3f", t.lambdaVoltage)} V em ${t.fuelLoopStatus}.\n")
        crossBuilder.append("• Equilíbrio da Malha: STFT (${String.format(Locale.US, "%+.1f", t.shortTermFuelTrim)}%) e LTFT (${String.format(Locale.US, "%+.1f", t.longTermFuelTrim)}%).\n")
        crossBuilder.append("• Eficiência Térmica: ECT em ${t.coolantTempC.toInt()}°C com avanço em ${String.format(Locale.US, "%.1f", t.sparkAdvanceDeg)}° APMS.\n")

        return AiDiagnosticReport(
            summary = summary,
            rootCause = rootCause,
            abnormalComponents = abnormals,
            normalComponents = normals,
            crossAnalysisDetails = crossBuilder.toString(),
            actionPlan = actions,
            isGeneratedByGemini = false
        )
    }
}
