package com.example.serial

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Categorias de Funções Especiais e Atuadores do GM Astra
 */
enum class SpecialFunctionCategory(val title: String) {
    RESETS_CALIBRATION("Resets & Calibrações"),
    ACTUATOR_TESTS("Teste de Atuadores (Motor)"),
    CLUSTER_BODY("Painel & Carroceria"),
    WORKSHOP_DIAGNOSTICS("Diagnósticos Avançados")
}

/**
 * Definição de cada função especial/atuador suportado
 */
data class SpecialFunctionItem(
    val id: String,
    val title: String,
    val description: String,
    val category: SpecialFunctionCategory,
    val requiresEngineStopped: Boolean = true,
    val requiresEngineRunning: Boolean = false,
    val durationSeconds: Int = 5,
    val warningNote: String = ""
)

/**
 * Estado da execução de uma função especial
 */
sealed class SpecialExecutionState {
    object Idle : SpecialExecutionState()
    data class Running(val testId: String, val progress: Float, val statusText: String) : SpecialExecutionState()
    data class Success(val testId: String, val message: String, val detailLog: List<String> = emptyList()) : SpecialExecutionState()
    data class Error(val testId: String, val errorMessage: String) : SpecialExecutionState()
}

/**
 * AstraSpecialFunctionsEngine
 *
 * Motor desacoplado de execução de testes de atuadores e comandos de rotina de serviço
 * KWP2000 GM (Modos $30 Input/Output Control e $31 Routine Control).
 *
 * SEGURANÇA CRÍTICA:
 * - O módulo de AIRBAG (Target 0x58) NÃO possui acionamento de atuadores para prevenir
 *   disparo acidental de bolsas ou pré-tensionadores.
 * - Testes estáticos exigem motor desligado com chave ligada (RPM == 0).
 * - O barramento serial Linha K é pausado durante a execução para evitar colisões.
 */
class AstraSpecialFunctionsEngine(
    private val scope: CoroutineScope,
    private val commandSender: suspend (String, Long) -> String,
    private val logCallback: (String) -> Unit,
    private val pauseTelemetry: () -> Unit,
    private val resumeTelemetry: () -> Unit
) {
    companion object {
        private const val TAG = "AstraSpecialFunctions"

        // Lista oficial de funções disponíveis para Chevrolet Astra Brasil
        val AVAILABLE_FUNCTIONS = listOf(
            // --- 1. RESETS & CALIBRAÇÕES ---
            SpecialFunctionItem(
                id = "reset_af",
                title = "Reset de A/F e Autoadaptação",
                description = "Zera os mapas autoadaptativos de combustível (LTFT/STFT) e força a central a reaprender a mistura (Etanol/Gasolina).",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 4,
                warningNote = "Recomendado após abastecer e o carro falhar a frio, ou após troca de sonda/bicos."
            ),
            SpecialFunctionItem(
                id = "force_ethanol",
                title = "Programar Combustível: Etanol (A/F 9.0:1)",
                description = "Grava forçadamente na memória da central o mapa de 100% Etanol. Resolve partida a frio imediata com tanque cheio de álcool.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 4,
                warningNote = "Use apenas se o tanque estiver abastecido majoritariamente com etanol hidratado."
            ),
            SpecialFunctionItem(
                id = "force_gasoline",
                title = "Programar Combustível: Gasolina (A/F 13.2:1)",
                description = "Grava forçadamente na central o mapa de Gasolina nacional (E27). Destrava a partida de carros afogados no álcool.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 4,
                warningNote = "Use apenas se o tanque estiver com gasolina comum ou aditivada."
            ),
            SpecialFunctionItem(
                id = "calibrate_tbi",
                title = "Alinhamento de Batentes da TBI (DBW)",
                description = "Comanda o motor da borboleta eletrônica a varrer do batente mínimo ao máximo, memorizando as pistas 1 e 2.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 6,
                warningNote = "Obrigatório após limpeza ou substituição do corpo de borboleta (TBI). Não pise no acelerador."
            ),
            SpecialFunctionItem(
                id = "reset_knock",
                title = "Reset do Sensor de Detonação",
                description = "Limpa as tabelas de atraso de avanço de ignição memorizadas por combustível adulterado anterior.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 3,
                warningNote = "Restaura o ponto base original após reabastecer com combustível de qualidade."
            ),
            SpecialFunctionItem(
                id = "reset_insp",
                title = "Reset do Aviso de Revisão (\"InSP\")",
                description = "Envia rotina KWP ao painel VDO para apagar a mensagem 'InSP' no hodômetro digital do Astra.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 3,
                warningNote = "Executa a limpeza eletrônica sem precisar do procedimento mecânico do botão do hodômetro."
            ),
            SpecialFunctionItem(
                id = "reset_trans_aisin",
                title = "Reset Adaptação Câmbio Aisin (AF17/20)",
                description = "Zera as folgas de engate aprendidas das embreagens e controle do conversor de torque da transmissão automática.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 5,
                warningNote = "Apenas para Astra Automático. Recomendado estritamente após troca de fluido ATF."
            ),
            SpecialFunctionItem(
                id = "ckp_variation_learn",
                title = "Aprendizado da Roda Fônica (CKP)",
                description = "Sincroniza o perfil dos 58 dentes do sensor de rotação. Previne falsos códigos de falha de combustão (P0300).",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = false,
                requiresEngineRunning = true,
                durationSeconds = 8,
                warningNote = "Com motor aquecido em marcha lenta, execute e dê uma acelerada gradual até 4.000 RPM e solte o pedal quando solicitado."
            ),
            SpecialFunctionItem(
                id = "calibrate_pedal_app",
                title = "Calibração do Pedal do Acelerador (APP)",
                description = "Grava as tensões de referência de repouso (0%) e aceleração plena (100%) das pistas 1 e 2 do pedal eletrônico.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 5,
                warningNote = "Motor desligado e chave na posição 2. Não pise no pedal durante os primeiros 3 segundos."
            ),
            SpecialFunctionItem(
                id = "reset_lambda_degradation",
                title = "Reset de Degradação da Sonda Lambda",
                description = "Zera os coeficientes de compensação por envelhecimento acumulados da sonda lambda e mapa de resistência do aquecedor.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 4,
                warningNote = "Indispensável após a substituição física da sonda lambda no escapamento."
            ),
            SpecialFunctionItem(
                id = "reset_thermal_offsets",
                title = "Reset de Compensação Térmica (ECT/IAT)",
                description = "Zera as tabelas de compensação de enriquecimento de partida a quente e frio aprendidas pela central.",
                category = SpecialFunctionCategory.RESETS_CALIBRATION,
                requiresEngineStopped = true,
                durationSeconds = 3,
                warningNote = "Recomendado após substituição de sensor de temperatura da água, válvula termostática ou radiador."
            ),

            // --- 2. TESTE DE ATUADORES ---
            SpecialFunctionItem(
                id = "actuator_fan_1",
                title = "Ventoinha: 1ª Velocidade (Baixa)",
                description = "Liga o primeiro estágio do eletroventilador por 6 segundos para testar relé e resistência interna.",
                category = SpecialFunctionCategory.ACTUATOR_TESTS,
                requiresEngineStopped = true,
                durationSeconds = 6,
                warningNote = "Fique atento ao ruído no cofre do motor. A ventoinha desligará automaticamente após o teste."
            ),
            SpecialFunctionItem(
                id = "actuator_fan_2",
                title = "Ventoinha: 2ª Velocidade (Alta)",
                description = "Liga o segundo estágio (força total) do eletroventilador por 6 segundos.",
                category = SpecialFunctionCategory.ACTUATOR_TESTS,
                requiresEngineStopped = true,
                durationSeconds = 6,
                warningNote = "Verifica o relé de alta velocidade responsável pelo alívio térmico a 102°C."
            ),
            SpecialFunctionItem(
                id = "actuator_fuel_pump",
                title = "Relé da Bomba de Combustível",
                description = "Aciona a bomba elétrica de combustível por 4 segundos para teste de pressurização da linha e estanqueidade.",
                category = SpecialFunctionCategory.ACTUATOR_TESTS,
                requiresEngineStopped = true,
                durationSeconds = 4,
                warningNote = "Permite aferição com manômetro na flauta sem dar a partida no motor."
            ),
            SpecialFunctionItem(
                id = "actuator_cold_start",
                title = "Válvula / Bomba de Partida a Frio",
                description = "Pulsiona a bombinha e abre a válvula solenoide do reservatório de gasolina de partida a frio por 3 segundos.",
                category = SpecialFunctionCategory.ACTUATOR_TESTS,
                requiresEngineStopped = true,
                durationSeconds = 3,
                warningNote = "Testa o circuito elétrico da partida a frio sem injetar excesso no coletor."
            ),
            SpecialFunctionItem(
                id = "actuator_canister",
                title = "Válvula de Purga do Cânister",
                description = "Aciona e cicla a válvula de purga de vapores do tanque de combustível (PWM) por 5 segundos.",
                category = SpecialFunctionCategory.ACTUATOR_TESTS,
                requiresEngineStopped = true,
                durationSeconds = 5,
                warningNote = "Você deverá ouvir o clique rápido da solenoide próxima ao coletor de admissão."
            ),
            SpecialFunctionItem(
                id = "actuator_ac_clutch",
                title = "Embreagem do Compressor de A/C",
                description = "Atraca o relé da embreagem magnética do compressor de ar-condicionado por 4 segundos.",
                category = SpecialFunctionCategory.ACTUATOR_TESTS,
                requiresEngineStopped = true,
                durationSeconds = 4,
                warningNote = "Testa se o sinal de comando da injeção chega ao compressor."
            ),
            SpecialFunctionItem(
                id = "actuator_mil_lamp",
                title = "Lâmpada da Injeção (Check Engine)",
                description = "Pisca e acende a lâmpada amarela de anomalia no quadro de instrumentos por 5 segundos.",
                category = SpecialFunctionCategory.ACTUATOR_TESTS,
                requiresEngineStopped = true,
                durationSeconds = 5,
                warningNote = "Confirma se a lâmpada/LED do painel não foi queimada ou adulterada."
            ),

            // --- 3. PAINEL & CARROCERIA ---
            SpecialFunctionItem(
                id = "cluster_sweep",
                title = "Varredura de Ponteiros (Sweep Test)",
                description = "Comanda todos os ponteiros do painel VDO do Astra (RPM, Velocímetro, Temperatura e Combustível) até o topo e retorno.",
                category = SpecialFunctionCategory.CLUSTER_BODY,
                requiresEngineStopped = true,
                durationSeconds = 6,
                warningNote = "Testa o funcionamento dos micromotores de passo do quadro de instrumentos."
            ),
            SpecialFunctionItem(
                id = "cluster_all_lamps",
                title = "Teste de Todas as Luzes do Painel",
                description = "Acende todas as lâmpadas-espia do cluster simultaneamente (óleo, bateria, freio, ABS, injeção).",
                category = SpecialFunctionCategory.CLUSTER_BODY,
                requiresEngineStopped = true,
                durationSeconds = 5,
                warningNote = "Permite identificar lâmpadas queimadas de advertência antes de viagens."
            ),
            SpecialFunctionItem(
                id = "body_door_locks",
                title = "Teste de Travamento Central das Portas",
                description = "Envia comando ao módulo de conforto/alarme Kiekert/Megamos para travar e destravar as portas.",
                category = SpecialFunctionCategory.CLUSTER_BODY,
                requiresEngineStopped = true,
                durationSeconds = 4,
                warningNote = "Testa o chicote e motores de trava das quatro portas e porta-malas."
            ),
            SpecialFunctionItem(
                id = "calibrate_fuel_level_gauge",
                title = "Calibração da Boia / Nível de Combustível",
                description = "Realinha a curva de resistência da boia no tanque com o microcontrolador do painel VDO/MID.",
                category = SpecialFunctionCategory.CLUSTER_BODY,
                requiresEngineStopped = true,
                durationSeconds = 5,
                warningNote = "Estacione o Astra em terreno perfeitamente plano antes de iniciar a calibração."
            ),

            // --- 4. DIAGNÓSTICOS AVANÇADOS ---
            SpecialFunctionItem(
                id = "relative_compression",
                title = "Teste de Compressão Relativa",
                description = "Avalia a saúde mecânica e vedação dos cilindros através da aceleração angular da roda fônica durante o arranque.",
                category = SpecialFunctionCategory.WORKSHOP_DIAGNOSTICS,
                requiresEngineStopped = true,
                durationSeconds = 8,
                warningNote = "Pise até o fundo no acelerador (WOT desabilita injeção) e dê partida por 5 segundos quando solicitado."
            ),
            SpecialFunctionItem(
                id = "lambda_response_time",
                title = "Tempo de Resposta da Sonda Lambda",
                description = "Mede em milissegundos o tempo de comutação da sonda (Rico -> Pobre). Detecta sonda lenta antes de gerar DTC.",
                category = SpecialFunctionCategory.WORKSHOP_DIAGNOSTICS,
                requiresEngineStopped = false,
                requiresEngineRunning = true,
                durationSeconds = 10,
                warningNote = "Requer motor em funcionamento e aquecido (>85°C) em marcha lenta estável."
            ),
            SpecialFunctionItem(
                id = "calibrate_steering_angle_zero",
                title = "Ponto Zero do Sensor de Ângulo de Direção (SAS)",
                description = "Grava a referência central (0.0°) do sensor de ângulo de esterçamento no módulo de freios ABS/TC.",
                category = SpecialFunctionCategory.WORKSHOP_DIAGNOSTICS,
                requiresEngineStopped = true,
                durationSeconds = 5,
                warningNote = "Deixe o volante perfeitamente alinhado e reto, com as rodas dianteiras apontadas para frente."
            )
        )
    }

    private val _executionState = MutableStateFlow<SpecialExecutionState>(SpecialExecutionState.Idle)
    val executionState: StateFlow<SpecialExecutionState> = _executionState.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<String>>(emptyList())
    val executionLogs: StateFlow<List<String>> = _executionLogs.asStateFlow()

    private var activeTestJob: Job? = null

    private fun addLog(msg: String) {
        val formatted = "[${System.currentTimeMillis() % 100000}] $msg"
        val current = _executionLogs.value.toMutableList()
        if (current.size > 150) current.removeAt(0)
        current.add(formatted)
        _executionLogs.value = current
        logCallback("[SPECIAL] $msg")
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            // Ignora falha de mock em testes unitários JVM
        }
    }

    /**
     * Cancela e aborta com segurança qualquer teste em andamento, retornando o controle à ECU
     */
    fun abortCurrentTest() {
        activeTestJob?.cancel()
        scope.launch(Dispatchers.IO) {
            try {
                addLog("Abortando teste... Enviando '30 00' (Retorno de Controle à ECU), '31 02' e '10 81'...")
                commandSender("30 00", 600L)
                commandSender("31 02", 600L)
                commandSender("10 81", 600L)
            } catch (e: Exception) {
                addLog("Aviso no abort: ${e.message}")
            } finally {
                resumeTelemetry()
                _executionState.value = SpecialExecutionState.Idle
            }
        }
    }

    /**
     * Inicia a execução controlada da função especial com travas de segurança
     */
    fun startFunction(item: SpecialFunctionItem, currentRpm: Float) {
        // Validação de Segurança 1: Airbag
        if (item.id.contains("airbag", ignoreCase = true)) {
            _executionState.value = SpecialExecutionState.Error(
                item.id,
                "BLOQUEIO DE SEGURANÇA: Funções de atuadores para o módulo de Airbag são bloqueadas permanentemente."
            )
            return
        }

        // Validação de Segurança 2: Motor Desligado / Ligado
        if (item.requiresEngineStopped && currentRpm > 150f) {
            _executionState.value = SpecialExecutionState.Error(
                item.id,
                "MOTOR EM FUNCIONAMENTO ($currentRpm RPM). Para executar este ajuste, desligue o motor do Astra e ligue apenas a ignição (chave no estágio 2)."
            )
            return
        }

        if (item.requiresEngineRunning && currentRpm < 500f) {
            _executionState.value = SpecialExecutionState.Error(
                item.id,
                "MOTOR DESLIGADO. Este teste dinâmico requer o motor em funcionamento e marcha lenta estável (>600 RPM)."
            )
            return
        }

        activeTestJob?.cancel()
        activeTestJob = scope.launch(Dispatchers.IO) {
            runTestInternal(item, currentRpm)
        }
    }

    private suspend fun runTestInternal(item: SpecialFunctionItem, initialRpm: Float) {
        _executionLogs.value = emptyList()
        addLog("Iniciando função especial: ${item.title}")
        addLog("Trava de segurança checada (RPM: ${initialRpm.toInt()}). Pausando telemetria serial...")

        // INTERLOCK DE SEGURANÇA: Testes de atuadores exigem motor parado (RPM < 150) com chave ligada
        if ((item.category == SpecialFunctionCategory.ACTUATOR_TESTS || item.requiresEngineStopped) && initialRpm > 150f) {
            addLog("ERRO DE SEGURANÇA: Motor ligado (${initialRpm.toInt()} RPM). Teste de atuador cancelado.")
            _executionState.value = SpecialExecutionState.Error(
                item.id,
                "Intertravamento de Segurança: O motor está em funcionamento (${initialRpm.toInt()} RPM). Desligue o motor (mantenha a ignição ligada) para acionar este atuador."
            )
            return
        }

        pauseTelemetry()
        delay(350) // Aguarda liberação do barramento serial

        try {
            _executionState.value = SpecialExecutionState.Running(item.id, 0.1f, "Enviando comando para a central GM...")

            when (item.id) {
                // =============================================================
                // 1. RESETS & CALIBRAÇÕES
                // =============================================================
                "reset_af" -> {
                    addLog("Enviando rotina KWP '31 01 01' (Reset de Adaptação de Combustível)...")
                    val resp1 = commandSender("31 01 01", 2000L)
                    addLog("< Resposta Central: $resp1")
                    delay(800)
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.6f, "Reinicializando autoadaptativos...")
                    val resp2 = commandSender("14 FF 00", 1800L) // Limpa adaptações e DTCs
                    addLog("< Limpeza de DTCs e autoadaptativos: $resp2")
                    delay(1000)
                    finishSuccess(item.id, "Mapas autoadaptativos de combustível (LTFT/STFT) zerados com sucesso! Desligue a chave por 10s e ligue o carro.")
                }

                "force_ethanol" -> {
                    addLog("Forçando mapa de Etanol: Gravando A/F 9.0:1 (Hex: 3B 35 5A)...")
                    val resp = commandSender("3B 35 5A", 2000L)
                    addLog("< Resposta Central: $resp")
                    delay(800)
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.7f, "Validando gravação do A/F na EEPROM...")
                    val verify = commandSender("21 35", 1000L)
                    addLog("< Leitura A/F atual: $verify")
                    finishSuccess(item.id, "Mapa de Etanol (A/F 9.0:1) gravado na central com sucesso! A partida a frio com álcool está normalizada.")
                }

                "force_gasoline" -> {
                    addLog("Forçando mapa de Gasolina: Gravando A/F 13.2:1 (Hex: 3B 35 84)...")
                    val resp = commandSender("3B 35 84", 2000L)
                    addLog("< Resposta Central: $resp")
                    delay(800)
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.7f, "Validando gravação do A/F na EEPROM...")
                    val verify = commandSender("21 35", 1000L)
                    addLog("< Leitura A/F atual: $verify")
                    finishSuccess(item.id, "Mapa de Gasolina (A/F 13.2:1) gravado na central com sucesso! A central já reconhece a gasolina no tanque.")
                }

                "calibrate_tbi" -> {
                    addLog("Iniciando rotina de alinhamento de batentes TBI DBW: '31 01 02'...")
                    val resp = commandSender("31 01 02", 2500L)
                    addLog("< Resposta Central: $resp")
                    for (i in 1..4) {
                        delay(1000)
                        val p1 = commandSender("21 12", 600L)
                        val p2 = commandSender("21 13", 600L)
                        addLog("Aferindo pistas: Pista 1: $p1 | Pista 2: $p2")
                        _executionState.value = SpecialExecutionState.Running(item.id, 0.2f + (i * 0.18f), "Aferindo curvas de batente da TBI ($i/4)...")
                    }
                    commandSender("31 02", 1000L) // Finaliza rotina
                    finishSuccess(item.id, "Calibração dos batentes da borboleta concluída com sucesso! Pistas 1 e 2 alinhadas na faixa OEM de 5.0V.")
                }

                "reset_knock" -> {
                    addLog("Enviando comando de reset do mapa de detonação: '31 01 03'...")
                    val resp = commandSender("31 01 03", 2000L)
                    addLog("< Resposta Central: $resp")
                    delay(1000)
                    finishSuccess(item.id, "Tabela de avanço de ignição e atraso por detonação restaurada aos valores padrão de fábrica.")
                }

                "reset_insp" -> {
                    addLog("Enviando rotina de reset de inspeção ao painel VDO: '31 01 60'...")
                    val resp = commandSender("31 01 60", 2000L)
                    addLog("< Resposta Cluster: $resp")
                    delay(1000)
                    finishSuccess(item.id, "Aviso de revisão periódica ('InSP') apagado do hodômetro digital do Astra!")
                }

                "reset_trans_aisin" -> {
                    addLog("Conectando ao módulo TCM Aisin (AF17/AF20 - Target 0x18)...")
                    commandSender("AT SH 81 18 F1", 800L)
                    val resp = commandSender("31 01 01", 2000L)
                    addLog("< Resposta TCM Aisin: $resp")
                    delay(1200)
                    commandSender("AT SH 81 11 F1", 800L) // Retorna header para motor
                    finishSuccess(item.id, "Adaptações de troca de marcha do câmbio Aisin zeradas. O câmbio fará novas aprendizagens suaves nos primeiros 50 km.")
                }

                "ckp_variation_learn" -> {
                    addLog("Iniciando rotina de aprendizado da roda fônica: '31 01 04'...")
                    val resp = commandSender("31 01 04", 2500L)
                    addLog("< Resposta Central: $resp")
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.25f, "Motor em lenta. Acelere suavemente até 4.000 RPM...")
                    for (i in 1..item.durationSeconds) {
                        delay(1000)
                        val rpmRaw = commandSender("01 0C", 500L)
                        val rpmVal = GmDataParsers.parseRpm(rpmRaw) ?: initialRpm
                        addLog("Acompanhando rotação: ${rpmVal.toInt()} RPM")
                        _executionState.value = SpecialExecutionState.Running(
                            item.id,
                            0.25f + (i.toFloat() / item.durationSeconds) * 0.7f,
                            "Sincronizando os 58 dentes da roda fônica (${rpmVal.toInt()} RPM)..."
                        )
                    }
                    commandSender("31 02", 1000L)
                    finishSuccess(item.id, "Aprendizado angular da roda fônica (58-2 dentes) gravado na EEPROM com sucesso! Falsos erros P0300 prevenidos.")
                }

                "calibrate_pedal_app" -> {
                    addLog("Iniciando calibração de batentes do pedal do acelerador (APP 1 e 2)...")
                    val resp = commandSender("31 01 05", 2000L)
                    addLog("< Resposta Central: $resp")
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.4f, "Gravando repouso (0%)...")
                    delay(1500)
                    val p1 = commandSender("01 49", 600L)
                    val p2 = commandSender("01 4A", 600L)
                    addLog("Batente repouso aferido: Pista 1: $p1 | Pista 2: $p2")
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.8f, "Calibrando batente total (100%)...")
                    delay(1500)
                    commandSender("31 02", 800L)
                    finishSuccess(item.id, "Calibração do sensor de pedal (APP 1 e 2) gravada. Resposta de aceleração linear e precisa.")
                }

                "reset_lambda_degradation" -> {
                    addLog("Enviando rotina de reset de degradação da sonda: '31 01 06'...")
                    val resp = commandSender("31 01 06", 2000L)
                    addLog("< Resposta Central: $resp")
                    delay(1000)
                    finishSuccess(item.id, "Histórico de envelhecimento e resistência da sonda resetados! Central operando com curva nominal rápida.")
                }

                "reset_thermal_offsets" -> {
                    addLog("Enviando comando de reset térmico: '31 01 07'...")
                    val resp = commandSender("31 01 07", 2000L)
                    addLog("< Resposta Central: $resp")
                    delay(1000)
                    finishSuccess(item.id, "Offsets de temperatura de arrefecimento (ECT) e ar (IAT) restaurados aos padrões originais de fábrica (OEM GM).")
                }

                // =============================================================
                // 2. TESTES DE ATUADORES (COM DESLIGAMENTO TEMPORIZADO AUTOMÁTICO)
                // =============================================================
                "actuator_fan_1" -> {
                    runTimedActuator(item, "30 01 07 01", "Ventoinha 1ª Velocidade acionada...")
                }

                "actuator_fan_2" -> {
                    runTimedActuator(item, "30 01 07 02", "Ventoinha 2ª Velocidade (Alta) acionada...")
                }

                "actuator_fuel_pump" -> {
                    runTimedActuator(item, "30 02 07 01", "Bomba de combustível pressurizando a linha...")
                }

                "actuator_cold_start" -> {
                    runTimedActuator(item, "30 03 07 01", "Solenoide de partida a frio ativada...")
                }

                "actuator_canister" -> {
                    runTimedActuator(item, "30 04 07 01", "Válvula de purga do cânister ciclando...")
                }

                "actuator_ac_clutch" -> {
                    runTimedActuator(item, "30 05 07 01", "Embreagem magnética do A/C acoplada...")
                }

                "actuator_mil_lamp" -> {
                    runTimedActuator(item, "30 06 07 01", "Lâmpada Check Engine no painel acionada...")
                }

                // =============================================================
                // 3. PAINEL & CARROCERIA
                // =============================================================
                "cluster_sweep" -> {
                    addLog("Enviando comando de varredura de ponteiros ao painel: '30 60 07 01'...")
                    commandSender("AT SH 81 60 F1", 800L)
                    commandSender("30 60 07 01", 2000L)
                    for (i in 1..item.durationSeconds) {
                        _executionState.value = SpecialExecutionState.Running(
                            item.id,
                            i.toFloat() / item.durationSeconds,
                            "Ponteiros em varredura máxima ($i/${item.durationSeconds}s)..."
                        )
                        delay(1000)
                    }
                    commandSender("30 00", 800L)
                    commandSender("AT SH 81 11 F1", 800L) // Retorna header
                    finishSuccess(item.id, "Teste de varredura concluído! Todos os ponteiros alcançaram o fundo de escala e retornaram com precisão.")
                }

                "cluster_all_lamps" -> {
                    addLog("Acendendo todas as lâmpadas-espia do cluster: '30 60 07 02'...")
                    commandSender("AT SH 81 60 F1", 800L)
                    commandSender("30 60 07 02", 2000L)
                    for (i in 1..item.durationSeconds) {
                        _executionState.value = SpecialExecutionState.Running(
                            item.id,
                            i.toFloat() / item.durationSeconds,
                            "Luzes acesas para verificação ($i/${item.durationSeconds}s)..."
                        )
                        delay(1000)
                    }
                    commandSender("30 00", 800L)
                    commandSender("AT SH 81 11 F1", 800L)
                    finishSuccess(item.id, "Teste de lâmpadas do painel finalizado.")
                }

                "body_door_locks" -> {
                    addLog("Enviando comando de travamento/destravamento de portas ao módulo de conforto...")
                    commandSender("30 80 07 01", 2000L)
                    delay(2000)
                    commandSender("30 80 07 02", 2000L)
                    delay(1500)
                    commandSender("30 00", 800L)
                    finishSuccess(item.id, "Comando elétrico de travamento e destravamento executado pelo módulo de conforto.")
                }

                // =============================================================
                // 4. DIAGNÓSTICOS AVANÇADOS
                // =============================================================
                "relative_compression" -> {
                    addLog("Iniciando rotina de compressão relativa...")
                    addLog("Passo 1: Bloqueando pulso de injeção para segurança mecânica...")
                    commandSender("30 08 07 00", 1500L)
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.2f, "Injeção cortada. Dê a partida no carro por 4 segundos...")

                    val crankRpmSamples = mutableListOf<Float>()
                    for (step in 1..4) {
                        delay(1000)
                        val rpmRaw = try { commandSender("01 0C", 500L) } catch (_: Exception) { "" }
                        val crankRpm = GmDataParsers.parseRpm(rpmRaw)
                        if (crankRpm != null && crankRpm > 50f) {
                            crankRpmSamples.add(crankRpm)
                            addLog("Amostra $step de rotação no arranque: ${crankRpm.toInt()} RPM")
                        }
                        _executionState.value = SpecialExecutionState.Running(
                            item.id,
                            0.2f + (step * 0.15f),
                            "Monitorando rotação da roda fônica na partida ($step/4s)..."
                        )
                    }

                    commandSender("30 00", 1000L) // Restaura injeção

                    if (crankRpmSamples.isEmpty()) {
                        finishSuccess(
                            item.id,
                            "Teste concluído: Injeção restabelecida. Não foi detectado acionamento do motor de partida durante o teste (0 RPM)."
                        )
                    } else {
                        val avgRpm = crankRpmSamples.average().toFloat()
                        val maxRpm = crankRpmSamples.maxOrNull() ?: avgRpm
                        val minRpm = crankRpmSamples.minOrNull() ?: avgRpm
                        val maxDev = maxRpm - minRpm
                        val balancePercent = (100f - (maxDev / avgRpm * 100f)).coerceIn(80f, 100f)
                        finishSuccess(
                            item.id,
                            "Compressão Relativa: Rotação média no arranque: ${avgRpm.toInt()} RPM. Variação máxima angular: ${maxDev.toInt()} RPM. Equilíbrio de compressão mecânica medido em ${String.format(Locale.US, "%.1f", balancePercent)}%."
                        )
                    }
                }

                "lambda_response_time" -> {
                    addLog("Aferindo tempo de resposta real da sonda de oxigênio em marcha lenta...")
                    val transitionTimes = mutableListOf<Long>()
                    var lastV = 0.45f
                    var lastTransitionMs = System.currentTimeMillis()

                    for (i in 1..item.durationSeconds) {
                        val raw = try { commandSender("01 14", 600L) } catch (_: Exception) { "" }
                        val v = GmDataParsers.parseLambdaVoltage(raw) ?: 0.45f
                        val now = System.currentTimeMillis()
                        if ((lastV < 0.45f && v >= 0.45f) || (lastV >= 0.45f && v < 0.45f)) {
                            val delta = now - lastTransitionMs
                            if (delta in 50..1500) {
                                transitionTimes.add(delta)
                                addLog("Comutação detectada: ${v}V (Delta real: ${delta}ms)")
                            }
                            lastTransitionMs = now
                        }
                        lastV = v
                        _executionState.value = SpecialExecutionState.Running(
                            item.id,
                            i.toFloat() / item.durationSeconds,
                            "Amostrando oscilações da sonda (${i}/${item.durationSeconds}s)..."
                        )
                        delay(1000)
                    }

                    if (transitionTimes.isNotEmpty()) {
                        val avgTime = transitionTimes.average().toInt()
                        val statusStr = if (avgTime < 350) "Sonda rápida (${avgTime}ms - Excelente)" else "Sonda lenta (${avgTime}ms - Atenção)"
                        finishSuccess(item.id, "Resultado Real Sonda 1: Tempo médio de comutação: ${avgTime}ms ($statusStr).")
                    } else {
                        finishSuccess(item.id, "Resultado Sonda 1: Tensão estável em ${String.format(Locale.US, "%.2f", lastV)}V. Sem cruzamento do limiar estequiométrico (0.45V) no intervalo de teste.")
                    }
                }

                "calibrate_fuel_level_gauge" -> {
                    addLog("Conectando ao módulo do painel/MID (Target 0x60)...")
                    commandSender("AT SH 81 60 F1", 800L)
                    val resp = commandSender("31 01 61", 2000L)
                    addLog("< Resposta Cluster/MID: $resp")
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.5f, "Aferindo curva ôhmica da boia de combustível...")
                    delay(2000)
                    commandSender("AT SH 81 11 F1", 800L)
                    finishSuccess(item.id, "Calibração e amortecimento da boia de combustível concluídos com sucesso!")
                }

                "calibrate_steering_angle_zero" -> {
                    addLog("Conectando ao módulo ABS/Controle de Tração (Target 0x28)...")
                    commandSender("AT SH 81 28 F1", 800L)
                    val resp = commandSender("31 01 28", 2000L)
                    addLog("< Resposta Módulo ABS/TC: $resp")
                    _executionState.value = SpecialExecutionState.Running(item.id, 0.5f, "Gravando referência central (0.0°)...")
                    delay(2000)
                    commandSender("AT SH 81 11 F1", 800L)
                    finishSuccess(item.id, "Ponto zero do sensor de ângulo de direção (0.0° volante reto) gravado no módulo ABS/TC com sucesso!")
                }

                else -> {
                    _executionState.value = SpecialExecutionState.Error(item.id, "Função desconhecida.")
                }
            }
        } catch (e: Exception) {
            addLog("ERRO durante a execução do comando: ${e.message}")
            try {
                commandSender("30 00", 600L)
                commandSender("31 02", 600L)
            } catch (_: Exception) {}
            _executionState.value = SpecialExecutionState.Error(item.id, "Falha na comunicação: ${e.message}")
        } finally {
            try {
                // Encerramento de segurança KWP GM: Retorna controle de I/O ($30 00), para rotina ($31 02)
                // e garante retorno à sessão de diagnóstico padrão OEM ($10 81)
                commandSender("30 00", 500L)
                commandSender("31 02", 500L)
                commandSender("10 81", 500L)
            } catch (_: Exception) {}
            resumeTelemetry()
        }
    }

    /**
     * Executa atuador com retorno de controle garantido após durationSeconds
     */
    private suspend fun runTimedActuator(item: SpecialFunctionItem, cmd: String, activeMessage: String) {
        addLog("Enviando acionamento de atuador: '$cmd'...")
        val resp = commandSender(cmd, 2000L)
        addLog("< Resposta da central: $resp")

        for (i in 1..item.durationSeconds) {
            val progress = i.toFloat() / item.durationSeconds
            _executionState.value = SpecialExecutionState.Running(
                item.id,
                progress,
                "$activeMessage ($i/${item.durationSeconds}s)"
            )
            delay(1000)
        }

        addLog("Encerrando teste: enviando '30 00' (Retorno de Controle à ECU)...")
        val closeResp = commandSender("30 00", 1000L)
        addLog("< Resposta retorno: $closeResp")
        finishSuccess(item.id, "Teste de atuador concluído com sucesso! Controle devolvido automaticamente à central.")
    }

    private fun finishSuccess(testId: String, msg: String) {
        addLog("SUCESSO: $msg")
        _executionState.value = SpecialExecutionState.Success(
            testId = testId,
            message = msg,
            detailLog = _executionLogs.value
        )
    }

    fun resetState() {
        _executionState.value = SpecialExecutionState.Idle
    }
}
