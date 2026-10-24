package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.example.model.TelemetrySamplingMode
import com.example.ui.components.EcuSelectionDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScanState
import com.example.model.VehicleProfile
import com.example.ui.components.CircularGauge
import com.example.ui.components.DigitalMetricCard
import com.example.ui.components.LinearGauge
import com.example.ui.components.RealtimeTelemetryChart
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
        icon = Icons.AutoMirrored.Filled.ListAlt,
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
    val statusMsg by viewModel.scannerManager.statusMessage.collectAsState()
    val isRecording by viewModel.isRecordingDatalogger.collectAsState()
    val recordedCount by viewModel.recordedDataloggerCount.collectAsState()
    val isPhysicalEcuConnected by viewModel.isPhysicalEcuConnected.collectAsState()
    val isBluetoothConnected by viewModel.isBluetoothConnected.collectAsState()
    val isManualProfileConfigured by viewModel.isManualProfileConfigured.collectAsState()
    val heartbeat by viewModel.heartbeatStatus.collectAsState()
    val isNightHudMode by viewModel.isNightHudMode.collectAsState()
    val samplingMode by viewModel.samplingMode.collectAsState()
    val sessionPeaks by viewModel.sessionPeaks.collectAsState()
    val busHealth by viewModel.busHealth.collectAsState()

    var selectedGroup by rememberSaveable { mutableStateOf(CockpitGroup.GERAL) }
    var isEcuSelectorOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isConnected = isPhysicalEcuConnected

    val rpmHistory = remember { mutableStateListOf<Float>() }
    val ectHistory = remember { mutableStateListOf<Float>() }
    val mapHistory = remember { mutableStateListOf<Float>() }
    val lambdaHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(telemetry.timestampMs, isConnected) {
        if (isConnected) {
            rpmHistory.add(telemetry.rpm)
            if (rpmHistory.size > 50) rpmHistory.removeAt(0)

            ectHistory.add(telemetry.coolantTempC)
            if (ectHistory.size > 50) ectHistory.removeAt(0)

            mapHistory.add(telemetry.mapKpa)
            if (mapHistory.size > 50) mapHistory.removeAt(0)

            lambdaHistory.add(telemetry.lambdaVoltage)
            if (lambdaHistory.size > 50) lambdaHistory.removeAt(0)
        } else {
            if (rpmHistory.isNotEmpty()) rpmHistory.clear()
            if (ectHistory.isNotEmpty()) ectHistory.clear()
            if (mapHistory.isNotEmpty()) mapHistory.clear()
            if (lambdaHistory.isNotEmpty()) lambdaHistory.clear()
        }
    }

    if (isEcuSelectorOpen) {
        EcuSelectionDialog(
            currentProfile = vehicle,
            onSelectProfile = { selected ->
                viewModel.selectEcuProfile(selected)
            },
            onDismiss = { isEcuSelectorOpen = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isNightHudMode) Color(0xFF000000) else CarbonDark)
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
                .clickable { isEcuSelectorOpen = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                if (isPhysicalEcuConnected) {
                    val title = if (vehicle.engineCode == "Desconhecido" || vehicle.engineCode.isBlank()) {
                        "Astra 2.0 • Injeção GM Ao Vivo"
                    } else {
                        "${vehicle.engineCode} • ${vehicle.valveCount}"
                    }
                    val subtitle = if (vehicle.ecuName == "ECU Não Identificada" || vehicle.ecuName.isBlank()) {
                        "Linha K • Conectada em Tempo Real"
                    } else {
                        "${vehicle.ecuName} • ${vehicle.horsepower}"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AstraGreen,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AstraGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (telemetry.sampleRateHz > 0f) String.format(Locale.US, "AO VIVO • %.1f Hz", telemetry.sampleRateHz) else "AO VIVO",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = AstraGreen
                            )
                        }
                        if (heartbeat.isAlive) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF00381D))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "❤️ 2s (${heartbeat.pulseLatencyMs}ms)",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AstraGreen
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                } else if (isManualProfileConfigured || isInjectionDetected) {
                    val title = if (vehicle.engineCode == "Desconhecido" || vehicle.engineCode.isBlank()) {
                        "Astra 2.0 Flexpower • Offline"
                    } else {
                        "${vehicle.engineCode} • Offline"
                    }
                    val subtitle = if (vehicle.ecuName == "ECU Não Identificada" || vehicle.ecuName.isBlank()) {
                        "Perfil GM Selecionado (Offline)"
                    } else {
                        "${vehicle.ecuName} • ${vehicle.horsepower}"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AstraCyan,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AstraCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TROCAR",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = AstraCyan
                            )
                        }
                    }
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

                val headerBadge = when {
                    isPhysicalEcuConnected -> DashboardHeaderBadgeStyle(
                        AstraGreen.copy(alpha = 0.2f),
                        AstraGreen.copy(alpha = 0.6f),
                        "● AO VIVO",
                        AstraGreen
                    )
                    isBluetoothConnected -> DashboardHeaderBadgeStyle(
                        GmAmber.copy(alpha = 0.2f),
                        GmAmber.copy(alpha = 0.6f),
                        "● ADAPTADOR (IGN OFF)",
                        GmAmber
                    )
                    isManualProfileConfigured -> DashboardHeaderBadgeStyle(
                        AstraCyan.copy(alpha = 0.15f),
                        AstraCyan.copy(alpha = 0.4f),
                        "● OFFLINE",
                        AstraCyan
                    )
                    else -> DashboardHeaderBadgeStyle(
                        CarbonElevated,
                        CarbonBorder,
                        "OFFLINE",
                        TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(headerBadge.bg)
                        .border(1.dp, headerBadge.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = headerBadge.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerBadge.textColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seletor de Frequência de Amostragem (Modo Puxada 18Hz vs Ciclo Completo)
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (samplingMode == TelemetrySamplingMode.HIGH_RATE_FOCUS) AstraCyan.copy(alpha = 0.25f) else CarbonElevated)
                    .border(1.dp, if (samplingMode == TelemetrySamplingMode.HIGH_RATE_FOCUS) AstraCyan else CarbonBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        viewModel.setSamplingMode(
                            if (samplingMode == TelemetrySamplingMode.HIGH_RATE_FOCUS) TelemetrySamplingMode.FULL_DASHBOARD
                            else TelemetrySamplingMode.HIGH_RATE_FOCUS
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (samplingMode == TelemetrySamplingMode.HIGH_RATE_FOCUS) "⚡ 18Hz (PUXADA)" else "🔄 ~5Hz (COMPLETO)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (samplingMode == TelemetrySamplingMode.HIGH_RATE_FOCUS) AstraCyan else TextSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Alternador de Modo Noturno OLED HUD
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isNightHudMode) GmAmber.copy(alpha = 0.25f) else CarbonElevated)
                    .border(1.dp, if (isNightHudMode) GmAmber else CarbonBorder, RoundedCornerShape(8.dp))
                    .clickable { viewModel.toggleNightHudMode() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isNightHudMode) "🌙 HUD ATIVO" else "🔆 MODO HUD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isNightHudMode) GmAmber else TextSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Botão ZERAR PICOS da Sessão
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CarbonElevated)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(8.dp))
                    .clickable { viewModel.resetSessionPeaks() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🔄 ZERAR PICOS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AstraCyan,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (isPhysicalEcuConnected) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CarbonCard)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(telemetry.engineRegime.badgeColorHex).copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "● ${telemetry.engineRegime.label.uppercase()}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(telemetry.engineRegime.badgeColorHex)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Barramento: ${busHealth.healthPercent.toInt()}% [${busHealth.status.take(9)}]",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (busHealth.healthPercent >= 90f) AstraGreen else AstraYellow
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VE: ${String.format(Locale.US, "%.0f", telemetry.volumetricEfficiencyPercent)}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AstraCyan
                    )
                }
            }
        }

        // Alerta Crítico com Vibração (Superaquecimento ECT >= 105°C ou Bateria em Falha)
        val isOverheat = isPhysicalEcuConnected && telemetry.coolantTempC >= 105f
        val isBatteryLow = isPhysicalEcuConnected && telemetry.rpm > 350f && telemetry.ecuBatteryVoltage in 1f..11.6f
        val isBatteryOver = isPhysicalEcuConnected && telemetry.ecuBatteryVoltage >= 15.2f

        if (isOverheat || isBatteryLow || isBatteryOver) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF450A0A))
                    .border(1.dp, AstraRedline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🚨", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val alertTitle = when {
                            isOverheat -> "ALERTA DE SUPERAQUECIMENTO: ${telemetry.coolantTempC.toInt()}°C!"
                            isBatteryLow -> "ALERTA: TENSÃO DA BATERIA CRÍTICA (${String.format(Locale.US, "%.1f", telemetry.ecuBatteryVoltage)}V)!"
                            else -> "ALERTA: SOBRETENSÃO NO REGULADOR (${String.format(Locale.US, "%.1f", telemetry.ecuBatteryVoltage)}V)!"
                        }
                        Text(
                            text = alertTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AstraRedline
                        )
                        Text(
                            text = "Aviso vibratório emitido. Verifique o compartimento do motor.",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Aviso honesto quando não estiver conectado à ECU física do veículo
        if (!isPhysicalEcuConnected) {
            val isTimeoutOrError = scanState == ScanState.ERROR
            val bannerBorderColor = if (isTimeoutOrError) AstraRedline.copy(alpha = 0.6f) else AstraCyan.copy(alpha = 0.4f)
            val bannerBg = if (isTimeoutOrError) Color(0xFF280909) else Color(0xFF131D24)
            val bannerTint = if (isTimeoutOrError) AstraRedline else AstraCyan

            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bannerBg)
                    .border(1.dp, bannerBorderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isTimeoutOrError) Icons.Default.WarningAmber else Icons.Default.Tune,
                        contentDescription = null,
                        tint = bannerTint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isTimeoutOrError -> statusMsg
                            isBluetoothConnected -> "Adaptador Bluetooth conectado. Ligue a ignição do Astra para leitura real dos sensores."
                            else -> "Aguardando dados da ECU física. Conecte o scanner no Astra e ligue a ignição."
                        },
                        fontSize = 11.sp,
                        color = bannerTint,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Alerta Crítico Proativo de Proteção do Motor e Alternador
        if (isPhysicalEcuConnected) {
            val isOverheating = telemetry.coolantTempC >= 104f
            val isAlternatorFailing = telemetry.ecuBatteryVoltage in 1.0f..11.8f && telemetry.rpm > 550f

            if (isOverheating || isAlternatorFailing) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isOverheating) Color(0xFF380808) else Color(0xFF382305))
                        .border(1.dp, if (isOverheating) AstraRedline else GmAmber, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (isOverheating) AstraRedline else GmAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOverheating) {
                                "ALERTA TÉRMICO CRÍTICO: ECT em ${telemetry.coolantTempC.toInt()}°C! Verifique ventoinhas e termostática."
                            } else {
                                "ALERTA DE CARGA: Tensão da ECU em ${String.format(Locale.US, "%.1f", telemetry.ecuBatteryVoltage)}V com motor ligado. Possível falha no alternador."
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverheating) AstraRedline else GmAmberLight,
                            lineHeight = 15.sp
                        )
                    }
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
                    RealtimeTelemetryChart(
                        rpmHistory = rpmHistory,
                        ectHistory = ectHistory,
                        mapHistory = mapHistory,
                        lambdaHistory = lambdaHistory,
                        currentRpm = if (isConnected) telemetry.rpm else 0f,
                        currentEct = if (isConnected) telemetry.coolantTempC else 0f,
                        currentMap = if (isConnected) telemetry.mapKpa else 0f,
                        currentLambda = if (isConnected) telemetry.lambdaVoltage else 0f,
                        isConnected = isConnected
                    )
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

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DigitalMetricCard(
                            title = "Eficiência Volumétrica",
                            value = if (isConnected) String.format(Locale.US, "%.1f", telemetry.volumetricEfficiencyPercent) else "0.0",
                            unit = "%",
                            modifier = Modifier.weight(1f),
                            accentColor = AstraCyan,
                            subtitle = "Respiração do Motor",
                            minMaxIndicator = if (sessionPeaks.hasValidPeaks()) "Pico: ${String.format(Locale.US, "%.0f", sessionPeaks.maxVePercent)}%" else null
                        )

                        val effVolt = if (telemetry.ecuBatteryVoltage > 5f) telemetry.ecuBatteryVoltage else telemetry.elmBatteryVoltage
                        DigitalMetricCard(
                            title = "Tensão Alternador / Bateria",
                            value = if (isConnected) String.format(Locale.US, "%.1f", effVolt) else "0.0",
                            unit = "V",
                            modifier = Modifier.weight(1f),
                            accentColor = if (effVolt in 13.5f..14.8f) AstraGreen else AstraYellow,
                            subtitle = if (effVolt in 13.5f..14.8f) "Alternador Normal" else "Bateria / Repouso",
                            minMaxIndicator = if (sessionPeaks.hasValidPeaks()) "Mín: ${String.format(Locale.US, "%.1f", sessionPeaks.minVolt)} | Máx: ${String.format(Locale.US, "%.1f", sessionPeaks.maxVolt)}" else null
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isFlex = vehicle.supportsFlexPids
                            val afColor = if (isConnected && (telemetry.estimatedAfRatio in 8.8f..9.5f || telemetry.estimatedAfRatio in 12.8f..13.6f)) AstraGreen else AstraYellow
                            DigitalMetricCard(
                                title = "Razão A/F Estimada",
                                value = if (isConnected && telemetry.estimatedAfRatio > 0f) {
                                    String.format(Locale.US, "%.1f:1", telemetry.estimatedAfRatio)
                                } else if (isConnected && !isFlex) {
                                    "13.2:1"
                                } else {
                                    "--"
                                },
                                unit = "",
                                modifier = Modifier.weight(1f),
                                accentColor = afColor,
                                subtitle = if (!isConnected) "Desconectado" else if (telemetry.estimatedAfRatio in 8.5f..10.5f) "Mapa Etanol" else if (telemetry.estimatedAfRatio in 12.5f..14.0f) "Mapa Gasolina" else "Em adaptação"
                            )

                            DigitalMetricCard(
                                title = "Teor de Etanol no Tanque",
                                value = if (isConnected && isFlex) {
                                    "${telemetry.ethanolPercentage.toInt()}%"
                                } else if (isConnected && !isFlex) {
                                    "0% (Gasolina)"
                                } else {
                                    "--"
                                },
                                unit = "",
                                modifier = Modifier.weight(1f),
                                accentColor = if (isFlex && telemetry.ethanolPercentage > 50f) AstraCyan else GmAmberLight,
                                subtitle = if (isFlex) "Cálculo Flexpower GM" else "Astra Monocombustível"
                            )
                        }

                        // OSCILOSCÓPIO EM TEMPO REAL DA SONDA LAMBDA
                        LambdaOscilloscopeCard(
                            lambdaVoltage = telemetry.lambdaVoltage,
                            frequencyHz = telemetry.lambdaFrequencyHz,
                            healthStatus = telemetry.lambdaHealthStatus,
                            history = telemetry.lambdaHistory,
                            isConnected = isConnected
                        )

                        // MONITOR DE SAÚDE DA MISTURA & FUEL TRIMS
                        FuelTrimHealthCard(
                            stft = telemetry.shortTermFuelTrim,
                            ltft = telemetry.longTermFuelTrim,
                            healthLevel = telemetry.fuelTrimHealthLevel,
                            healthMessage = telemetry.fuelTrimHealthMessage,
                            isConnected = isConnected
                        )
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

/**
 * Osciloscópio da Sonda Lambda pré-catalisador com desenho de forma de onda (Waveform em Canvas),
 * linha de referência de 450 mV e indicador de frequência dinâmica de oscilação em Hertz.
 */
@Composable
fun LambdaOscilloscopeCard(
    lambdaVoltage: Float,
    frequencyHz: Float,
    healthStatus: String,
    history: List<Float>,
    isConnected: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonCard),
        border = BorderStroke(1.dp, CarbonBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lambda_oscilloscope_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabeçalho com título, Hz e status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected && frequencyHz >= 0.8f) AstraGreen else if (isConnected) AstraYellow else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OSCILOSCÓPIO SONDA LAMBDA (PRÉ-CAT)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AstraCyan,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = if (isConnected) String.format(Locale.US, "%.3f V", lambdaVoltage) else "0.000 V",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected && lambdaVoltage > 0.45f) AstraGreen else AstraYellow
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtítulo com taxa de oscilação e diagnóstico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isConnected) "Frequência: ${String.format(Locale.US, "%.1f", frequencyHz)} Hz" else "Frequência: 0.0 Hz",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isConnected) healthStatus else "Desconectado",
                    fontSize = 10.sp,
                    color = if (isConnected && frequencyHz >= 0.8f) AstraGreen else if (isConnected) AstraYellow else TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas da Forma de Onda (Waveform)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F141A))
                    .border(1.dp, CarbonBorder, RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val refY = h * 0.55f // Linha de 450 mV

                    // Linha tracejada de referência estequiométrica (450 mV)
                    drawLine(
                        color = Color(0xFF6B5B3E),
                        start = Offset(0f, refY),
                        end = Offset(w, refY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // Linhas de grid secundárias
                    val gridLines = 4
                    for (i in 1..gridLines) {
                        val y = h * (i / (gridLines + 1f))
                        if (kotlin.math.abs(y - refY) > 8f) {
                            drawLine(
                                color = Color(0xFF1E2530),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1f
                            )
                        }
                    }

                    // Se não houver pontos gravados, usa o valor atual ou neutro
                    val points = if (history.isNotEmpty()) history else listOf(if (isConnected) lambdaVoltage else 0.1f)
                    val stepX = if (points.size > 1) w / (points.size - 1) else w

                    val path = Path()
                    points.forEachIndexed { index, volt ->
                        val clampedVolt = volt.coerceIn(0f, 1.0f)
                        val x = index * stepX
                        val y = h * 0.9f - (clampedVolt * h * 0.8f)
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = if (isConnected && lambdaVoltage > 0.45f) AstraGreen else GmAmberLight,
                        style = Stroke(
                            width = 3.5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Marcador luminoso no último ponto amostrado
                    if (points.isNotEmpty()) {
                        val lastVolt = points.last().coerceIn(0f, 1.0f)
                        val lastX = (points.size - 1) * stepX
                        val lastY = h * 0.9f - (lastVolt * h * 0.8f)
                        drawCircle(
                            color = Color.White,
                            radius = 4.5f,
                            center = Offset(lastX, lastY)
                        )
                        drawCircle(
                            color = if (lastVolt > 0.45f) AstraGreen else GmAmberLight,
                            radius = 2.5f,
                            center = Offset(lastX, lastY)
                        )
                    }
                }

                // Indicadores de faixa dentro do canvas
                Text(
                    text = "450 mV • Estequiometria",
                    fontSize = 8.sp,
                    color = Color(0xFFC4A052),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 6.dp)
                )

                Text(
                    text = "Rica (> 450mV)",
                    fontSize = 8.sp,
                    color = AstraGreen.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 6.dp)
                )

                Text(
                    text = "Pobre (< 450mV)",
                    fontSize = 8.sp,
                    color = AstraYellow.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 4.dp, end = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Em regime aquecido de malha fechada, a sonda cicla entre ~100mV e ~900mV em frequência saudável de 1 a 2 Hz.",
                fontSize = 9.sp,
                color = TextMuted,
                lineHeight = 12.sp
            )
        }
    }
}

/**
 * Cartão de Diagnóstico de Saúde da Mistura e Compensação de Combustível (STFT + LTFT)
 */
@Composable
fun FuelTrimHealthCard(
    stft: Float,
    ltft: Float,
    healthLevel: Int,
    healthMessage: String,
    isConnected: Boolean
) {
    val totalTrim = stft + ltft
    val badgeColor = when (healthLevel) {
        0 -> AstraGreen
        1 -> AstraYellow
        else -> AstraRedline
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonCard),
        border = BorderStroke(1.dp, CarbonBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fuel_trim_health_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) badgeColor else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAÚDE DA MISTURA (FUEL TRIMS STFT + LTFT)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = if (isConnected) String.format(Locale.US, "%+.1f%%", totalTrim) else "0.0%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) badgeColor else TextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra gráfica com zonas de calibração (-25% a +25%)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CarbonElevated)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(AstraRedline.copy(alpha = 0.25f)))
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(AstraYellow.copy(alpha = 0.25f)))
                    Box(modifier = Modifier.weight(2f).fillMaxSize().background(AstraGreen.copy(alpha = 0.25f)))
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(AstraYellow.copy(alpha = 0.25f)))
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(AstraRedline.copy(alpha = 0.25f)))
                }

                // Marcador da linha zero (ideal)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.7f))
                )

                // Cursor do ponto atual
                if (isConnected) {
                    val normalized = ((totalTrim + 25f) / 50f).coerceIn(0.04f, 0.96f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(normalized)
                            .align(Alignment.CenterStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-25% (Rica)", fontSize = 8.sp, color = TextMuted)
                Text("-8%", fontSize = 8.sp, color = TextMuted)
                Text("0% Ideal", fontSize = 8.sp, color = AstraGreen, fontWeight = FontWeight.Bold)
                Text("+8%", fontSize = 8.sp, color = TextMuted)
                Text("+25% (Pobre)", fontSize = 8.sp, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isConnected) healthMessage else "Conecte para analisar o balanço de injeção em tempo real.",
                fontSize = 10.sp,
                color = if (isConnected) TextPrimary else TextMuted,
                lineHeight = 14.sp
            )
        }
    }
}

private data class DashboardHeaderBadgeStyle(
    val bg: Color,
    val border: Color,
    val text: String,
    val textColor: Color
)

