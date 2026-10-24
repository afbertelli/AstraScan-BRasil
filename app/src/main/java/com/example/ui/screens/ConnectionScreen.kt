package com.example.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import com.example.ui.components.EcuSelectionDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EcuSensorProfile
import com.example.model.ProtocolType
import com.example.model.ScanState
import com.example.ui.theme.AstraCyan
import com.example.ui.theme.AstraGreen
import com.example.ui.theme.AstraRedline
import com.example.ui.theme.AstraYellow
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonCard
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.GmAmber
import com.example.ui.theme.GmAmberGlow
import com.example.ui.theme.GmAmberLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ScannerViewModel

@SuppressLint("MissingPermission")
@Composable
fun ConnectionScreen(
    viewModel: ScannerViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val scanState by viewModel.scannerManager.scanState.collectAsState()
    val statusMsg by viewModel.scannerManager.statusMessage.collectAsState()
    val protocol by viewModel.scannerManager.detectedProtocol.collectAsState()
    val vehicle by viewModel.scannerManager.vehicleProfile.collectAsState()
    val isInjectionDetected by viewModel.isInjectionDetected.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val sensorProfile by viewModel.sensorProfile.collectAsState()
    val connectionLogs by viewModel.connectionLogs.collectAsState()
    val detectedEcu by viewModel.detectedEcuData.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isPhysicalEcuConnected by viewModel.isPhysicalEcuConnected.collectAsState()
    val isBluetoothConnected by viewModel.isBluetoothConnected.collectAsState()
    val isManualProfileConfigured by viewModel.isManualProfileConfigured.collectAsState()
    val heartbeat by viewModel.heartbeatStatus.collectAsState()
    val elmBenchmark by viewModel.elmBenchmark.collectAsState()

    var isAdapterExpanded by remember { mutableStateOf(false) }
    var isLogsExpanded by remember { mutableStateOf(false) }
    var isEcuSelectorOpen by remember { mutableStateOf(false) }
    var showHandshakeWarningDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner Header do Astra Brasil
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF221600), CarbonCard, Color(0xFF0F1A24))
                        )
                    )
                    .border(1.dp, GmAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(GmAmber.copy(alpha = 0.2f))
                                .border(1.5.dp, GmAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Astra GM Brasil",
                                tint = GmAmber,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SCANNER GM ASTRA BRASIL",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "100% Tempo Real • Linha K & OBD-II",
                                fontSize = 12.sp,
                                color = GmAmberLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Comunicação direta com o módulo de injeção física do Astra. Sem simulações ou dados artificiais. Ligue a ignição do carro antes de iniciar.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // =====================================================================
        // PASSO 1. CONEXÃO DO ADAPTADOR BLUETOOTH (COLAPSÁVEL)
        // =====================================================================
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.dp, CarbonBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(GmAmber.copy(alpha = 0.2f))
                                    .border(1.5.dp, GmAmber, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GmAmber
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1. ADAPTADOR BLUETOOTH",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { viewModel.loadPairedDevices() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar Pareados",
                                tint = AstraCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Botão Colapsável para Escolher / Alterar Adaptador
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CarbonElevated)
                            .border(1.dp, if (selectedDevice != null) GmAmber.copy(alpha = 0.4f) else CarbonBorder, RoundedCornerShape(8.dp))
                            .clickable { isAdapterExpanded = !isAdapterExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = selectedDevice?.name ?: "Escolher Adaptador Bluetooth",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedDevice != null) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedDevice != null) TextPrimary else GmAmberLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = selectedDevice?.address ?: "${pairedDevices.size} dispositivos pareados no celular",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted,
                                    maxLines = 1
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selectedDevice != null) GmAmber.copy(alpha = 0.2f) else CarbonCard)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (isAdapterExpanded) "FECHAR" else if (selectedDevice != null) "ALTERAR" else "ESCOLHER",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedDevice != null) GmAmber else TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isAdapterExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isAdapterExpanded) "Recolher" else "Expandir",
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Lista de Adaptadores Colapsável com Animação
                    AnimatedVisibility(visible = isAdapterExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (pairedDevices.isEmpty()) {
                                Text(
                                    text = "Nenhum dispositivo Bluetooth pareado no Android. Pareie o adaptador ELM327 nas configurações Bluetooth do seu celular e toque no botão atualizar acima.",
                                    fontSize = 12.sp,
                                    color = AstraYellow,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                pairedDevices.forEach { dev ->
                                    val isSelected = selectedDevice?.address == dev.address
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) GmAmber.copy(alpha = 0.15f) else CarbonElevated)
                                            .border(
                                                1.dp,
                                                if (isSelected) GmAmber else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel.selectDevice(dev)
                                                isAdapterExpanded = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                            contentDescription = null,
                                            tint = if (isSelected) GmAmber else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dev.name ?: "Dispositivo Sem Nome",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = dev.address,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextMuted
                                            )
                                        }
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(GmAmber)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "SELECIONADO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // PASSO 2. CONEXÃO À CENTRAL (ECU)
        // =====================================================================
        item {
            val isScanning = scanState != ScanState.IDLE && scanState != ScanState.CONNECTED && scanState != ScanState.ERROR

            val connectionBorderColor = when {
                isPhysicalEcuConnected -> AstraGreen
                scanState == ScanState.ERROR -> AstraRedline
                isBluetoothConnected -> GmAmber
                isManualProfileConfigured -> AstraCyan
                isScanning -> GmAmber
                else -> CarbonBorder
            }

            val connectionCardBg = when {
                isPhysicalEcuConnected -> Brush.verticalGradient(listOf(Color(0xFF002414), CarbonCard))
                scanState == ScanState.ERROR -> Brush.verticalGradient(listOf(Color(0xFF280909), CarbonCard))
                isBluetoothConnected -> Brush.verticalGradient(listOf(Color(0xFF2B1F05), CarbonCard))
                isManualProfileConfigured -> Brush.verticalGradient(listOf(Color(0xFF0C242E), CarbonCard))
                isScanning -> Brush.verticalGradient(listOf(Color(0xFF2B1F05), CarbonCard))
                else -> Brush.verticalGradient(listOf(CarbonCard, CarbonCard))
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(if (isPhysicalEcuConnected || isManualProfileConfigured || isScanning) 2.dp else 1.dp, connectionBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(connectionCardBg)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                val circleColor = when {
                                    isPhysicalEcuConnected -> AstraGreen
                                    isManualProfileConfigured -> AstraCyan
                                    isBluetoothConnected -> GmAmber
                                    else -> GmAmber
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(circleColor.copy(alpha = 0.2f))
                                        .border(1.5.dp, circleColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "2",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = circleColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "2. CONEXÃO À CENTRAL (ECU)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val badgeStyle = when {
                                isPhysicalEcuConnected -> ConnectionBadgeStyle(
                                    AstraGreen.copy(alpha = 0.25f),
                                    AstraGreen.copy(alpha = 0.6f),
                                    "● CONECTADO À ECU",
                                    AstraGreen
                                )
                                scanState == ScanState.ERROR -> ConnectionBadgeStyle(
                                    AstraRedline.copy(alpha = 0.25f),
                                    AstraRedline.copy(alpha = 0.6f),
                                    "FALHA",
                                    AstraRedline
                                )
                                isBluetoothConnected -> ConnectionBadgeStyle(
                                    GmAmber.copy(alpha = 0.25f),
                                    GmAmber.copy(alpha = 0.6f),
                                    "● ADAPTADOR (IGN OFF)",
                                    GmAmber
                                )
                                isManualProfileConfigured -> ConnectionBadgeStyle(
                                    AstraCyan.copy(alpha = 0.25f),
                                    AstraCyan.copy(alpha = 0.6f),
                                    "● PERFIL CONFIGURADO",
                                    AstraCyan
                                )
                                isScanning -> ConnectionBadgeStyle(
                                    GmAmber.copy(alpha = 0.25f),
                                    GmAmber.copy(alpha = 0.6f),
                                    "BUSCANDO...",
                                    GmAmber
                                )
                                else -> ConnectionBadgeStyle(
                                    CarbonElevated,
                                    CarbonBorder,
                                    "DESCONECTADO",
                                    TextMuted
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeStyle.bg)
                                    .border(1.dp, badgeStyle.border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = badgeStyle.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeStyle.textColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = statusMsg,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // =============================================================
                        // MONITOR HEARTBEAT (Verificação de vida a cada 2s)
                        // =============================================================
                        if (isPhysicalEcuConnected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF072412))
                                    .border(1.dp, AstraGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Text(text = "❤️", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "HEARTBEAT ATIVO (PULSO 2s)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AstraGreen
                                            )
                                            Text(
                                                text = "Pulso de baixo custo: ${heartbeat.lastCommandSent.ifBlank { "Tester Present (3E)" }} • ${heartbeat.totalPulsesSent} pulsos",
                                                fontSize = 10.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AstraGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${heartbeat.pulseLatencyMs}ms",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = AstraGreen
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            if (elmBenchmark.qualityTier != com.example.model.ElmQualityTier.UNKNOWN) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0D1B2A))
                                        .border(1.dp, Color(elmBenchmark.qualityTier.badgeColorHex).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "🔍", fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "CHIP ELM327: ${elmBenchmark.versionString}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(elmBenchmark.qualityTier.badgeColorHex)
                                                )
                                            }
                                            Text(
                                                text = "${elmBenchmark.qualityTier.label} • ${elmBenchmark.recommendationText}",
                                                fontSize = 10.sp,
                                                color = TextSecondary,
                                                lineHeight = 13.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(elmBenchmark.qualityTier.badgeColorHex).copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "${elmBenchmark.latencyMs}ms",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(elmBenchmark.qualityTier.badgeColorHex)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        } else if (scanState == ScanState.ERROR && statusMsg.contains("Heartbeat", ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF330B0B))
                                    .border(1.dp, AstraRedline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "💔", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "DESCONEXÃO POR FALHA NO HEARTBEAT",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AstraRedline
                                        )
                                        Text(
                                            text = "A central parou de responder ao pulso de vida de 2s. Desconexão automática de segurança realizada.",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (isScanning) {
                            LinearProgressIndicator(
                                progress = { scanState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = GmAmber,
                                trackColor = CarbonElevated,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Ações de Conectar / Desconectar
                        if (isPhysicalEcuConnected) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onNavigateToDashboard,
                                    colors = ButtonDefaults.buttonColors(containerColor = AstraGreen, contentColor = Color.Black),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("open_dashboard_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.Start) {
                                            Text(
                                                text = "ABRIR COCKPIT",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                            Text(
                                                text = "ECU Real Conectada • Telemetria Ao Vivo",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.Black.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.disconnect() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraRedline),
                                    border = BorderStroke(1.dp, AstraRedline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("disconnect_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desconectar da Central", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else if (isManualProfileConfigured) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startAutoScan() },
                                    enabled = !isScanning,
                                    colors = ButtonDefaults.buttonColors(containerColor = GmAmber, contentColor = Color.Black),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("start_scan_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(
                                            color = Color.Black,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Buscando Central Real no Carro...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "CONECTAR À CENTRAL REAL (AUTO)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (isPhysicalEcuConnected) {
                                            onNavigateToDashboard()
                                        } else {
                                            showHandshakeWarningDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPhysicalEcuConnected) AstraGreen.copy(alpha = 0.2f) else AstraCyan.copy(alpha = 0.15f),
                                        contentColor = if (isPhysicalEcuConnected) AstraGreen else AstraCyan
                                    ),
                                    border = BorderStroke(1.dp, if (isPhysicalEcuConnected) AstraGreen else AstraCyan),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("open_dashboard_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            if (isPhysicalEcuConnected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isPhysicalEcuConnected) AstraGreen else AstraCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.Start) {
                                            Text(
                                                text = if (isPhysicalEcuConnected) "ABRIR COCKPIT AO VIVO (ECU VALIDADA)" else "ABRIR COCKPIT (AGUARDANDO HANDSHAKE)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPhysicalEcuConnected) AstraGreen else AstraCyan
                                            )
                                            Text(
                                                text = if (isPhysicalEcuConnected) "Handshake físico concluído • Dados reais da injeção" else "Toque para validar conexão ou inspecionar",
                                                fontSize = 10.sp,
                                                color = if (isPhysicalEcuConnected) AstraGreen.copy(alpha = 0.85f) else AstraCyan.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { isEcuSelectorOpen = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                        border = BorderStroke(1.dp, CarbonBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("manual_connection_button"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Trocar Central", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (isBluetoothConnected) {
                                        OutlinedButton(
                                            onClick = { viewModel.disconnect() },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraRedline),
                                            border = BorderStroke(1.dp, AstraRedline.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .testTag("disconnect_button"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Desconectar", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startAutoScan() },
                                    enabled = !isScanning,
                                    colors = ButtonDefaults.buttonColors(containerColor = GmAmber, contentColor = Color.Black),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("start_scan_button")
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(
                                            color = Color.Black,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Buscando Central...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "BUSCAR CENTRAL (AUTO)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { isEcuSelectorOpen = true },
                                    enabled = !isScanning,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraCyan),
                                    border = BorderStroke(1.dp, AstraCyan.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("manual_connection_button")
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SELECIONAR CENTRAL MANUALMENTE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // PASSO 3. CONFIGURAÇÃO DE INJEÇÃO
        // =====================================================================
        item {
            val step3BorderColor = when {
                isPhysicalEcuConnected -> AstraGreen.copy(alpha = 0.6f)
                isManualProfileConfigured -> AstraCyan.copy(alpha = 0.6f)
                isInjectionDetected -> AstraGreen.copy(alpha = 0.6f)
                else -> CarbonBorder
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.5.dp, step3BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val step3Color = when {
                                isPhysicalEcuConnected -> AstraGreen
                                isManualProfileConfigured -> AstraCyan
                                isInjectionDetected -> AstraGreen
                                else -> TextMuted
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(step3Color.copy(alpha = 0.2f))
                                    .border(1.5.dp, step3Color, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "3",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = step3Color
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3. CONFIGURAÇÃO DE INJEÇÃO",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPhysicalEcuConnected || isManualProfileConfigured || isInjectionDetected) TextPrimary else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val (step3BadgeBg, step3BadgeText, step3BadgeColor) = when {
                            isPhysicalEcuConnected -> Triple(
                                AstraGreen.copy(alpha = 0.15f),
                                "DETECTADA NA LINHA K",
                                AstraGreen
                            )
                            isManualProfileConfigured -> Triple(
                                AstraCyan.copy(alpha = 0.15f),
                                "CONFIGURADA MANUALMENTE",
                                AstraCyan
                            )
                            isInjectionDetected -> Triple(
                                AstraGreen.copy(alpha = 0.15f),
                                "DETECTADA",
                                AstraGreen
                            )
                            else -> Triple(
                                CarbonElevated,
                                "AGUARDANDO",
                                TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(step3BadgeBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = step3BadgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = step3BadgeColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isInjectionDetected) {
                        val ecuDisplay = if (vehicle.ecuName == "ECU Não Identificada" || vehicle.ecuName.isBlank()) {
                            "Delphi Multec H / MT27E (GM Astra)"
                        } else vehicle.ecuName

                        val engineDisplay = if (vehicle.engineCode == "Desconhecido" || vehicle.engineCode.isBlank()) {
                            "Astra 2.0 8V Flexpower • Família II"
                        } else {
                            "${vehicle.engineCode} • ${vehicle.engineDisplacement} • ${vehicle.valveCount}"
                        }

                        Text(
                            text = ecuDisplay,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GmAmberGlow
                        )

                        Text(
                            text = engineDisplay,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CarbonElevated)
                                .padding(12.dp)
                        ) {
                            val power = if (vehicle.horsepower == "-" || vehicle.horsepower.isBlank()) "133 cv (Gas) / 140 cv (Etanol)" else vehicle.horsepower
                            val fuel = if (vehicle.fuelType == "-" || vehicle.fuelType.isBlank()) "Flexpower (Bicombustível)" else vehicle.fuelType
                            val years = if (vehicle.years == "-" || vehicle.years.isBlank()) "2004 - 2011" else vehicle.years
                            val trim = if (vehicle.trimLevels == "-" || vehicle.trimLevels.isBlank()) "Advantage, Elegance, Elite, Comfort" else vehicle.trimLevels

                            ProfileDetailRow("Potência:", power)
                            ProfileDetailRow("Combustível:", fuel)
                            ProfileDetailRow("Anos Fabricação:", years)
                            ProfileDetailRow("Versões:", trim)
                            if (vehicle.gmPartNumber.isNotBlank()) {
                                ProfileDetailRow("Part Number GM:", vehicle.gmPartNumber)
                            }
                            if (vehicle.hardwareNumber.isNotBlank()) {
                                ProfileDetailRow("Número Bosch:", vehicle.hardwareNumber)
                            }
                            if (vehicle.calId.isNotBlank() && !vehicle.calId.startsWith("7F")) {
                                ProfileDetailRow("CalID:", vehicle.calId)
                            }
                        }

                        sensorProfile?.let { sp ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "MAPEAMENTO DE SENSORES (ECUFingerprinter)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GmAmberLight
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CarbonElevated)
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Admissão", fontSize = 10.sp, color = TextMuted)
                                        Text(
                                            text = sp.intakeType,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CarbonElevated)
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Borboleta", fontSize = 10.sp, color = TextMuted)
                                        Text(
                                            text = if (sp.hasDriveByWire) "Eletrônica (DBW)" else "Cabo + IAC",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CarbonElevated)
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Injeção", fontSize = 10.sp, color = TextMuted)
                                        Text(
                                            text = sp.fuelDeliveryType,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CarbonElevated)
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Sensores Ativos", fontSize = 10.sp, color = TextMuted)
                                        Text(
                                            text = "${sp.compatibleSensors.size} Mapeados",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AstraGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (vehicle.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nota Técnica: ${vehicle.notes}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    } else {
                        // Injeção ainda não detectada: valores ocultados / placeholders
                        Text(
                            text = "Aguardando detecção da ECU...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Nenhum valor predefinido. Conecte o scanner à ECU física do Astra para identificar e carregar os dados reais da injeção eletrônica.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CarbonElevated)
                                .padding(12.dp)
                        ) {
                            ProfileDetailRow("Central (ECU):", "--")
                            ProfileDetailRow("Motorização:", "--")
                            ProfileDetailRow("Potência:", "--")
                            ProfileDetailRow("Combustível:", "--")
                            ProfileDetailRow("Part Number GM:", "--")
                            ProfileDetailRow("Número Bosch:", "--")
                            ProfileDetailRow("CalID:", "--")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { isEcuSelectorOpen = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GmAmber),
                        border = BorderStroke(1.dp, GmAmber.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("select_ecu_manually_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isInjectionDetected) "ALTERAR CENTRAL / SELECIONAR MANUALMENTE" else "SELECIONAR CENTRAL MANUALMENTE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isEcuSelectorOpen) {
                EcuSelectionDialog(
                    currentProfile = vehicle,
                    onSelectProfile = { selected ->
                        viewModel.selectEcuProfile(selected)
                        isEcuSelectorOpen = false
                    },
                    onDismiss = { isEcuSelectorOpen = false }
                )
            }
        }


        // =====================================================================
        // LOGS DE TENTATIVA DE CONEXÃO E COMUNICAÇÃO OBD2 EM TEMPO REAL
        // =====================================================================
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.dp, CarbonBorder),
                modifier = Modifier.fillMaxWidth().testTag("connection_logs_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isLogsExpanded = !isLogsExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "LOGS DA CONEXÃO OBD2",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${connectionLogs.size} eventos • Status: ${connectionStatus.label}",
                                fontSize = 11.sp,
                                color = GmAmberLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = if (isLogsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isLogsExpanded) "Recolher" else "Expandir",
                            tint = TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (isLogsExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0C0C0E))
                                .border(1.dp, CarbonBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            if (connectionLogs.isEmpty()) {
                                Text(
                                    text = "Nenhum tráfego serial registrado ainda. Inicie a conexão para visualizar o handshake físico e respostas da central.",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(connectionLogs.takeLast(50)) { logEntry ->
                                        Text(
                                            text = logEntry,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = when {
                                                logEntry.contains("ERRO", ignoreCase = true) -> AstraRedline
                                                logEntry.contains("SUCESSO", ignoreCase = true) || logEntry.contains("DETECTADA", ignoreCase = true) -> AstraGreen
                                                logEntry.contains("TX", ignoreCase = true) -> AstraCyan
                                                logEntry.contains("RX", ignoreCase = true) -> GmAmberLight
                                                else -> TextSecondary
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (detectedEcu.isDetected) {
                                Text(
                                    text = "ECU: ${detectedEcu.ecuName}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AstraGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            TextButton(
                                onClick = { viewModel.diagnosisViewModel.clearLogs() }
                            ) {
                                Text("LIMPAR LOGS", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHandshakeWarningDialog) {
        AlertDialog(
            onDismissRequest = { showHandshakeWarningDialog = false },
            containerColor = CarbonCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = GmAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Validação de Handshake",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A central eletrônica (ECU) ainda não validou a troca de dados físicos via cabo/Bluetooth.",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Para visualizar instrumentos com dados 100% reais em tempo real, conecte o scanner no Astra e ligue a ignição do carro.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHandshakeWarningDialog = false
                        viewModel.startAutoScan()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GmAmber, contentColor = Color.Black)
                ) {
                    Text("CONECTAR AGORA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showHandshakeWarningDialog = false }) {
                        Text("CANCELAR", color = TextMuted, fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = {
                            showHandshakeWarningDialog = false
                            onNavigateToDashboard()
                        }
                    ) {
                        Text("ABRIR OFFLINE", color = AstraCyan, fontSize = 11.sp)
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

private data class ConnectionBadgeStyle(
    val bg: Color,
    val border: Color,
    val text: String,
    val textColor: Color
)
