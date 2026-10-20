package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScanState
import com.example.model.VehicleProfile
import com.example.ui.components.CircularGauge
import com.example.ui.components.DigitalMetricCard
import com.example.ui.components.LinearGauge
import com.example.ui.theme.AstraCyan
import com.example.ui.theme.AstraGreen
import com.example.ui.theme.AstraRedline
import com.example.ui.theme.AstraYellow
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonCard
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.GmAmber
import com.example.ui.theme.GmAmberLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Grupos de Parâmetros do Cockpit GM Astra
 */
enum class CockpitGroup(
    val title: String,
    val shortLabel: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val pidsCount: String
) {
    GERAL(
        title = "Cockpit Geral",
        shortLabel = "Geral",
        subtitle = "Velocímetro, tacômetro, ECT, MAP e consumo instantâneo",
        icon = Icons.Default.Speed,
        accentColor = GmAmber,
        pidsCount = "6 PIDs"
    ),
    INJECTION_FUEL(
        title = "Injeção & Combustível",
        shortLabel = "Injeção / Comb.",
        subtitle = "Tempo de injeção T.I., duty cycle, sondas e malhas",
        icon = Icons.Default.LocalGasStation,
        accentColor = GmAmberLight,
        pidsCount = "8 PIDs"
    ),
    IGNITION_KNOCK(
        title = "Injeção & Detonação",
        shortLabel = "Injeção / Det.",
        subtitle = "Avanço de centelha e batida de pino (Knock Cilindros 1-4)",
        icon = Icons.Default.Whatshot,
        accentColor = AstraCyan,
        pidsCount = "7 PIDs"
    ),
    AIR_MANIFOLD(
        title = "Ar & Coletor",
        shortLabel = "Ar / Coletor",
        subtitle = "Pressão MAP, depressão vácuo, borboleta TPS e pedal APP",
        icon = Icons.Default.Sensors,
        accentColor = AstraGreen,
        pidsCount = "8 PIDs"
    ),
    SENSORS_ELECTRICAL(
        title = "Sensores & Elétrica",
        shortLabel = "Sensores / Elétr.",
        subtitle = "Temperaturas ECT/IAT, tensões da central e atuador IAC",
        icon = Icons.Default.ElectricBolt,
        accentColor = AstraYellow,
        pidsCount = "6 PIDs"
    ),
    ALL(
        title = "Todos os Parâmetros",
        shortLabel = "Ver Todos",
        subtitle = "Visualização contínua com todos os parâmetros da ECU",
        icon = Icons.Default.ListAlt,
        accentColor = TextPrimary,
        pidsCount = "35 PIDs"
    )
}

@Composable
fun DashboardScreen(
    viewModel: ScannerViewModel
) {
    val telemetry by viewModel.scannerManager.telemetry.collectAsState()
    val vehicle by viewModel.scannerManager.vehicleProfile.collectAsState()
    val isInjectionDetected by viewModel.isInjectionDetected.collectAsState()
    val scanState by viewModel.scannerManager.scanState.collectAsState()
    val isRecording by viewModel.isRecordingDatalogger.collectAsState()
    val recordedCount by viewModel.recordedDataloggerCount.collectAsState()

    var selectedGroup by rememberSaveable { mutableStateOf(CockpitGroup.GERAL) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isConnected = scanState == ScanState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 14.dp)
            .testTag("dashboard_screen")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 1. CABEÇALHO PERMANENTE DA CENTRAL (Sempre fixo no topo da tela, independente da aba ou rolagem)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CarbonCard)
                .border(1.dp, CarbonBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                if (isInjectionDetected && isConnected) {
                    val title = if (vehicle.engineCode == "Desconhecido" || vehicle.engineCode.isBlank()) {
                        "Astra 2.0 • Injeção GM Conectada"
                    } else {
                        "${vehicle.engineCode} • ${vehicle.valveCount}"
                    }
                    val subtitle = if (vehicle.ecuName == "ECU Não Identificada" || vehicle.ecuName.isBlank()) {
                        "Delphi Multec / Motronic • GM Brasil"
                    } else {
                        "${vehicle.ecuName} • ${vehicle.horsepower}"
                    }
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GmAmber,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text(
                        text = "Injeção: Aguardando detecção...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GmAmber,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Inicie a varredura para ler a central física",
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AstraRedline.copy(alpha = 0.2f))
                            .border(1.dp, AstraRedline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(AstraRedline)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REC ($recordedCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AstraRedline,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isConnected) AstraGreen.copy(alpha = 0.2f) else CarbonElevated)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isConnected) "AO VIVO" else "OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) AstraGreen else TextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // CONTEÚDO ROLÁVEL COM TELEMETRIA E BOTÕES PADRÃO
        // =====================================================================
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // =====================================================================
            // 2. TODOS OS BOTÕES DE SELEÇÃO VISÍVEIS NA TELA (GRID 2x3 FIXO SEM ROLAGEM)
            // =====================================================================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CarbonCard)
                        .border(1.dp, CarbonBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GRUPOS DE TELEMETRIA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )

                        Text(
                            text = "Toque para exibir o grupo",
                            fontSize = 10.sp,
                            color = GmAmberLight
                        )
                    }

                    // Grid 2x3 com botões em tamanho padrão padronizado
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Linha 1: Geral & Injeção/Combustível
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CockpitGroupButton(
                                group = CockpitGroup.GERAL,
                                isSelected = selectedGroup == CockpitGroup.GERAL,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedGroup = CockpitGroup.GERAL
                                    coroutineScope.launch { listState.animateScrollToItem(1) }
                                }
                            )
                            CockpitGroupButton(
                                group = CockpitGroup.INJECTION_FUEL,
                                isSelected = selectedGroup == CockpitGroup.INJECTION_FUEL,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedGroup = CockpitGroup.INJECTION_FUEL
                                    coroutineScope.launch { listState.animateScrollToItem(1) }
                                }
                            )
                        }

                        // Linha 2: Injeção/Detonação & Ar/Coletor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CockpitGroupButton(
                                group = CockpitGroup.IGNITION_KNOCK,
                                isSelected = selectedGroup == CockpitGroup.IGNITION_KNOCK,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedGroup = CockpitGroup.IGNITION_KNOCK
                                    coroutineScope.launch { listState.animateScrollToItem(1) }
                                }
                            )
                            CockpitGroupButton(
                                group = CockpitGroup.AIR_MANIFOLD,
                                isSelected = selectedGroup == CockpitGroup.AIR_MANIFOLD,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedGroup = CockpitGroup.AIR_MANIFOLD
                                    coroutineScope.launch { listState.animateScrollToItem(1) }
                                }
                            )
                        }

                        // Linha 3: Sensores/Elétrica & Ver Todos
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CockpitGroupButton(
                                group = CockpitGroup.SENSORS_ELECTRICAL,
                                isSelected = selectedGroup == CockpitGroup.SENSORS_ELECTRICAL,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedGroup = CockpitGroup.SENSORS_ELECTRICAL
                                    coroutineScope.launch { listState.animateScrollToItem(1) }
                                }
                            )
                            CockpitGroupButton(
                                group = CockpitGroup.ALL,
                                isSelected = selectedGroup == CockpitGroup.ALL,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedGroup = CockpitGroup.ALL
                                    coroutineScope.launch { listState.animateScrollToItem(1) }
                                }
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 3. DADOS DE TELEMETRIA EM TEMPO REAL (SEM BOTÕES REDUNDANTES ABAIXO)
            // =====================================================================

            // -----------------------------------------------------------------
            // GRUPO 1: COCKPIT GERAL
            // -----------------------------------------------------------------
            if (selectedGroup == CockpitGroup.GERAL || selectedGroup == CockpitGroup.ALL) {
                if (selectedGroup == CockpitGroup.ALL) {
                    item { CleanSectionTitle(group = CockpitGroup.GERAL) }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarbonCard),
                        border = BorderStroke(1.dp, CarbonBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularGauge(
                                    value = if (isConnected) telemetry.rpm else 0f,
                                    minValue = 0f,
                                    maxValue = vehicle.maxRpm,
                                    title = "ROTAÇÃO",
                                    unit = "RPM",
                                    size = 135.dp,
                                    primaryColor = GmAmber,
                                    redlineStart = vehicle.redlineRpm
                                )

                                CircularGauge(
                                    value = if (isConnected) telemetry.speedKmh else 0f,
                                    minValue = 0f,
                                    maxValue = 240f,
                                    title = "VELOCIDADE",
                                    unit = "km/h",
                                    size = 135.dp,
                                    primaryColor = AstraCyan
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CarbonElevated)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isConnected && telemetry.targetIdleRpm > 0f) {
                                        "Alvo Marcha Lenta: ${telemetry.targetIdleRpm.toInt()} RPM"
                                    } else {
                                        "Alvo Marcha Lenta: 0 RPM"
                                    },
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = if (isConnected) "Malha: ${telemetry.fuelLoopStatus}" else "Malha: OFFLINE",
                                    fontSize = 11.sp,
                                    color = if (isConnected) AstraCyan else TextMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearGauge(
                            title = "Temperatura do Arrefecimento (ECT)",
                            value = if (isConnected) telemetry.coolantTempC else 0f,
                            minValue = 0f,
                            maxValue = 125f,
                            unit = "°C",
                            barColor = if (isConnected && telemetry.coolantTempC >= 98f) AstraRedline else GmAmber,
                            warningThreshold = 98f,
                            isWarningHigh = true
                        )

                        LinearGauge(
                            title = "Pressão no Coletor (MAP) / Vácuo: ${if (isConnected && telemetry.baroKpa > 0f) String.format(Locale.US, "%.2f", telemetry.manifoldVacuumBar) else "0.00"} bar",
                            value = if (isConnected) telemetry.mapKpa else 0f,
                            minValue = 0f,
                            maxValue = 110f,
                            unit = "kPa",
                            barColor = AstraCyan
                        )

                        LinearGauge(
                            title = "Abertura da Borboleta (TPS %)",
                            value = if (isConnected) telemetry.throttlePercent else 0f,
                            minValue = 0f,
                            maxValue = 100f,
                            unit = "%",
                            barColor = AstraGreen
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DigitalMetricCard(
                            title = "Tempo de Injeção",
                            value = if (isConnected) String.format(Locale.US, "%.2f", telemetry.injectionTimeMs) else "0.00",
                            unit = "ms",
                            modifier = Modifier.weight(1f),
                            accentColor = GmAmber,
                            subtitle = if (isConnected) "Duty Cycle: ${String.format(Locale.US, "%.1f", telemetry.injectorDutyCyclePercent)}%" else "Duty Cycle: 0.0%"
                        )

                        DigitalMetricCard(
                            title = "Consumo Instantâneo",
                            value = if (!isConnected) {
                                "0.0"
                            } else if (telemetry.speedKmh > 3f) {
                                String.format(Locale.US, "%.1f", telemetry.instantConsumptionKmPerLiter)
                            } else {
                                String.format(Locale.US, "%.1f", telemetry.instantConsumptionLitersHour)
                            },
                            unit = if (!isConnected) "L/h" else if (telemetry.speedKmh > 3f) "km/L" else "L/h",
                            modifier = Modifier.weight(1f),
                            accentColor = AstraGreen,
                            subtitle = if (!isConnected) "Desconectado" else if (telemetry.speedKmh > 3f) "Em movimento" else "Em marcha lenta"
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // GRUPO 2: INJEÇÃO & COMBUSTÍVEL
            // -----------------------------------------------------------------
            if (selectedGroup == CockpitGroup.INJECTION_FUEL || selectedGroup == CockpitGroup.ALL) {
                if (selectedGroup == CockpitGroup.ALL) {
                    item { CleanSectionTitle(group = CockpitGroup.INJECTION_FUEL) }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Tempo de Injeção (T.I.)",
                                value = if (isConnected) String.format(Locale.US, "%.3f", telemetry.injectionTimeMs) else "0.000",
                                unit = "ms",
                                modifier = Modifier.weight(1f),
                                accentColor = GmAmber,
                                subtitle = "Tempo de abertura dos bicos"
                            )

                            DigitalMetricCard(
                                title = "Duty Cycle dos Bicos",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.injectorDutyCyclePercent) else "0.0",
                                unit = "%",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.injectorDutyCyclePercent > 85f) AstraRedline else AstraCyan,
                                subtitle = "Carga dos injetores"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Sonda Lambda 1 (Pré)",
                                value = if (isConnected) String.format(Locale.US, "%.3f", telemetry.lambdaVoltage) else "0.000",
                                unit = "V",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.lambdaVoltage > 0.45f) AstraGreen else AstraYellow,
                                subtitle = if (!isConnected) "Desconectado" else if (telemetry.lambdaVoltage > 0.45f) "Mistura Rica (>450mV)" else "Mistura Pobre (<450mV)"
                            )

                            // Sonda Lambda 2: no Astra GSi 16V C20SEL não há sonda pós-catalisador de fábrica
                            val hasPostCatLambda = vehicle != VehicleProfile.ASTRA_GSI_16V && vehicle.supportsFlexPids
                            DigitalMetricCard(
                                title = "Sonda Lambda 2 (Pós)",
                                value = if (!hasPostCatLambda) {
                                    "Não disponível"
                                } else if (isConnected) {
                                    String.format(Locale.US, "%.3f", telemetry.lambdaPostCatVoltage)
                                } else {
                                    "0.000"
                                },
                                unit = if (hasPostCatLambda) "V" else "",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraCyan,
                                subtitle = if (!hasPostCatLambda) "Central mono-sonda (GSi)" else "Eficiência catalisador"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Short Term Fuel Trim (STFT)",
                                value = if (isConnected) String.format(Locale.US, "%+.1f", telemetry.shortTermFuelTrim) else "0.0",
                                unit = "%",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && Math.abs(telemetry.shortTermFuelTrim) > 15f) AstraRedline else AstraGreen,
                                subtitle = "Correção rápida"
                            )

                            DigitalMetricCard(
                                title = "Long Term Fuel Trim (LTFT)",
                                value = if (isConnected) String.format(Locale.US, "%+.1f", telemetry.longTermFuelTrim) else "0.0",
                                unit = "%",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && Math.abs(telemetry.longTermFuelTrim) > 12f) AstraRedline else AstraGreen,
                                subtitle = "Mapa autoadaptativo"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Fator Lambda (λ)",
                                value = if (isConnected && telemetry.equivalenceRatioLambda > 0f) {
                                    String.format(Locale.US, "%.3f", telemetry.equivalenceRatioLambda)
                                } else {
                                    "0.000"
                                },
                                unit = "λ",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.equivalenceRatioLambda in 0.95f..1.05f) AstraGreen else AstraYellow,
                                subtitle = "Estequiometria (1.000)"
                            )

                            DigitalMetricCard(
                                title = "Consumo em Marcha Lenta",
                                value = if (isConnected) String.format(Locale.US, "%.2f", telemetry.instantConsumptionLitersHour) else "0.00",
                                unit = "L/h",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraCyan,
                                subtitle = "Vazão estimada"
                            )
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // GRUPO 3: INJEÇÃO & DETONAÇÃO
            // -----------------------------------------------------------------
            if (selectedGroup == CockpitGroup.IGNITION_KNOCK || selectedGroup == CockpitGroup.ALL) {
                if (selectedGroup == CockpitGroup.ALL) {
                    item { CleanSectionTitle(group = CockpitGroup.IGNITION_KNOCK) }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Avanço de Ignição Atual",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.sparkAdvanceDeg) else "0.0",
                                unit = "° APMS",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraCyan,
                                subtitle = "Ponto dinâmico da centelha"
                            )

                            DigitalMetricCard(
                                title = "Retardo por Detonação Geral",
                                value = if (isConnected) String.format(Locale.US, "%.2f", telemetry.knockRetardDeg) else "0.00",
                                unit = "° Retardo",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.knockRetardDeg > 0f) AstraRedline else AstraGreen,
                                subtitle = if (isConnected && telemetry.knockRetardDeg > 0f) "Atraso de ponto ativo" else "Sem batida de pino"
                            )
                        }

                        Text(
                            text = "MONITORAMENTO DE DETONAÇÃO POR CILINDRO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Detonação Cilindro 1",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.knockCylinder1Deg) else "0.0",
                                unit = "°",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.knockCylinder1Deg > 0f) AstraRedline else AstraGreen,
                                subtitle = if (isConnected && telemetry.knockCylinder1Deg > 0f) "Atraso no Cil 1" else "Normal"
                            )
                            DigitalMetricCard(
                                title = "Detonação Cilindro 2",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.knockCylinder2Deg) else "0.0",
                                unit = "°",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.knockCylinder2Deg > 0f) AstraRedline else AstraGreen,
                                subtitle = if (isConnected && telemetry.knockCylinder2Deg > 0f) "Atraso no Cil 2" else "Normal"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Detonação Cilindro 3",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.knockCylinder3Deg) else "0.0",
                                unit = "°",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.knockCylinder3Deg > 0f) AstraRedline else AstraGreen,
                                subtitle = if (isConnected && telemetry.knockCylinder3Deg > 0f) "Atraso no Cil 3" else "Normal"
                            )
                            DigitalMetricCard(
                                title = "Detonação Cilindro 4",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.knockCylinder4Deg) else "0.0",
                                unit = "°",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.knockCylinder4Deg > 0f) AstraRedline else AstraGreen,
                                subtitle = if (isConnected && telemetry.knockCylinder4Deg > 0f) "Atraso no Cil 4" else "Normal"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Marcha Lenta Alvo",
                                value = if (isConnected && telemetry.targetIdleRpm > 0f) "${telemetry.targetIdleRpm.toInt()}" else "0",
                                unit = "RPM",
                                modifier = Modifier.weight(1f),
                                accentColor = GmAmberLight,
                                subtitle = "Programado na ECU"
                            )
                            DigitalMetricCard(
                                title = "Desvio de Marcha Lenta",
                                value = if (isConnected && telemetry.targetIdleRpm > 0f) "${(telemetry.rpm - telemetry.targetIdleRpm).toInt()}" else "0",
                                unit = "RPM",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && Math.abs(telemetry.rpm - telemetry.targetIdleRpm) > 100) AstraYellow else AstraGreen,
                                subtitle = "Diferença em tempo real"
                            )
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // GRUPO 4: AR & COLETOR
            // -----------------------------------------------------------------
            if (selectedGroup == CockpitGroup.AIR_MANIFOLD || selectedGroup == CockpitGroup.ALL) {
                if (selectedGroup == CockpitGroup.ALL) {
                    item { CleanSectionTitle(group = CockpitGroup.AIR_MANIFOLD) }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearGauge(
                            title = "Carga Calculada do Motor",
                            value = if (isConnected) telemetry.engineLoadPercent else 0f,
                            minValue = 0f,
                            maxValue = 100f,
                            unit = "%",
                            barColor = AstraCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Pressão MAP",
                                value = if (isConnected) "${telemetry.mapKpa.toInt()}" else "0",
                                unit = "kPa",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraCyan,
                                subtitle = "Pressão absoluta"
                            )

                            DigitalMetricCard(
                                title = "Vácuo do Coletor",
                                value = if (isConnected && telemetry.baroKpa > 0f) String.format(Locale.US, "%.2f", telemetry.manifoldVacuumBar) else "0.00",
                                unit = "bar",
                                modifier = Modifier.weight(1f),
                                accentColor = GmAmber,
                                subtitle = "Diferencial de vácuo"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Pressão Barométrica (BARO)",
                                value = if (isConnected && telemetry.baroKpa > 0f) "${telemetry.baroKpa.toInt()}" else "0",
                                unit = "kPa",
                                modifier = Modifier.weight(1f),
                                accentColor = TextSecondary,
                                subtitle = "Pressão do ambiente"
                            )

                            DigitalMetricCard(
                                title = "Vazão de Ar (MAF Estimada)",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.estimatedMafGramsSec) else "0.0",
                                unit = "g/s",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraGreen,
                                subtitle = "Cálculo Speed-Density"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Tensão TPS Pista 1",
                                value = if (isConnected) String.format(Locale.US, "%.2f", telemetry.throttleVoltage1) else "0.00",
                                unit = "V",
                                modifier = Modifier.weight(1f),
                                accentColor = GmAmberLight,
                                subtitle = "Potenciômetro 1"
                            )

                            DigitalMetricCard(
                                title = "Tensão TPS Pista 2",
                                value = if (isConnected) String.format(Locale.US, "%.2f", telemetry.throttleVoltage2) else "0.00",
                                unit = "V",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraCyan,
                                subtitle = "Potenciômetro 2"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Posição do Pedal (APP)",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.acceleratorPedalPercent) else "0.0",
                                unit = "%",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraGreen,
                                subtitle = "Sensor do pedal"
                            )

                            DigitalMetricCard(
                                title = "Purga do Canister (EVAP)",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.evapPurgePercent) else "0.0",
                                unit = "%",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraYellow,
                                subtitle = "Válvula de vapor"
                            )
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // GRUPO 5: SENSORES & ELÉTRICA
            // -----------------------------------------------------------------
            if (selectedGroup == CockpitGroup.SENSORS_ELECTRICAL || selectedGroup == CockpitGroup.ALL) {
                if (selectedGroup == CockpitGroup.ALL) {
                    item { CleanSectionTitle(group = CockpitGroup.SENSORS_ELECTRICAL) }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Temperatura Água (ECT)",
                                value = if (isConnected) "${telemetry.coolantTempC.toInt()}" else "0",
                                unit = "°C",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.coolantTempC >= 98f) AstraRedline else GmAmber,
                                subtitle = if (!isConnected) "Desconectado" else if (telemetry.coolantTempC in 88f..96f) "Faixa Ideal" else "Alerta Térmico"
                            )

                            DigitalMetricCard(
                                title = "Temperatura do Ar (IAT)",
                                value = if (isConnected) "${telemetry.intakeAirTempC.toInt()}" else "0",
                                unit = "°C",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraCyan,
                                subtitle = "Duto de admissão"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Tensão Bateria (ECU)",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.ecuBatteryVoltage) else "0.0",
                                unit = "V",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.ecuBatteryVoltage in 13.2f..14.8f) AstraGreen else TextSecondary,
                                subtitle = "Lida nos pinos da central"
                            )

                            DigitalMetricCard(
                                title = "Tensão no ELM327",
                                value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.elmBatteryVoltage) else "0.0",
                                unit = "V",
                                modifier = Modifier.weight(1f),
                                accentColor = AstraGreen,
                                subtitle = "Pino 16 do conector DLC"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DigitalMetricCard(
                                title = "Atuador Marcha Lenta (IAC)",
                                value = if (isConnected) "${telemetry.iacSteps}" else "0",
                                unit = "Passos",
                                modifier = Modifier.weight(1f),
                                accentColor = GmAmber,
                                subtitle = "Motor de passo TBI"
                            )

                            DigitalMetricCard(
                                title = "Relé Bomba Combustível",
                                value = if (isConnected && telemetry.fuelPumpRelayStatus) "LIGADO" else "DESLIGADO",
                                unit = "",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isConnected && telemetry.fuelPumpRelayStatus) AstraGreen else TextMuted,
                                subtitle = "Comando da ECU"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Título de Seção Limpo (Não-botão, sem redundância)
 */
@Composable
private fun CleanSectionTitle(group: CockpitGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(group.accentColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = group.icon,
                contentDescription = null,
                tint = group.accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = group.title.uppercase(Locale.ROOT),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = group.accentColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "• ${group.subtitle}",
            fontSize = 10.sp,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Botão Seletor de Grupo no topo do Cockpit (Grid 2x3 sem nenhuma rolagem)
 * Todos os 6 botões possuem dimensões padronizadas e idênticas.
 */
@Composable
private fun CockpitGroupButton(
    group: CockpitGroup,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) group.accentColor.copy(alpha = 0.20f) else CarbonElevated,
        border = BorderStroke(
            1.dp,
            if (isSelected) group.accentColor else CarbonBorder
        ),
        modifier = modifier
            .height(54.dp)
            .testTag("group_btn_${group.name.lowercase(Locale.ROOT)}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) group.accentColor else CarbonCard)
                    .border(1.dp, if (isSelected) group.accentColor else CarbonBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.Black else group.accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = group.shortLabel,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) group.accentColor else TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = group.pidsCount,
                    fontSize = 9.sp,
                    color = if (isSelected) TextPrimary else TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) group.accentColor else Color.Transparent)
            )
        }
    }
}
