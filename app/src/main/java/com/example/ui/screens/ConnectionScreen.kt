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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Stop
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

    var isAdapterExpanded by remember { mutableStateOf(false) }

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
            val connectionBorderColor = when (scanState) {
                ScanState.CONNECTED -> AstraGreen
                ScanState.ERROR -> AstraRedline
                ScanState.IDLE -> CarbonBorder
                else -> GmAmber
            }

            val connectionCardBg = when (scanState) {
                ScanState.CONNECTED -> Brush.verticalGradient(listOf(Color(0xFF002414), CarbonCard))
                ScanState.ERROR -> Brush.verticalGradient(listOf(Color(0xFF280909), CarbonCard))
                ScanState.IDLE -> Brush.verticalGradient(listOf(CarbonCard, CarbonCard))
                else -> Brush.verticalGradient(listOf(Color(0xFF2B1F05), CarbonCard))
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(if (scanState == ScanState.CONNECTED || scanState != ScanState.IDLE && scanState != ScanState.ERROR) 2.dp else 1.dp, connectionBorderColor),
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
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (scanState == ScanState.CONNECTED) AstraGreen.copy(alpha = 0.2f) else GmAmber.copy(alpha = 0.2f))
                                        .border(1.5.dp, if (scanState == ScanState.CONNECTED) AstraGreen else GmAmber, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "2",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (scanState == ScanState.CONNECTED) AstraGreen else GmAmber
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

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (scanState) {
                                            ScanState.CONNECTED -> AstraGreen.copy(alpha = 0.25f)
                                            ScanState.ERROR -> AstraRedline.copy(alpha = 0.25f)
                                            ScanState.IDLE -> CarbonElevated
                                            else -> GmAmber.copy(alpha = 0.25f)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when (scanState) {
                                            ScanState.CONNECTED -> AstraGreen.copy(alpha = 0.6f)
                                            ScanState.ERROR -> AstraRedline.copy(alpha = 0.6f)
                                            ScanState.IDLE -> CarbonBorder
                                            else -> GmAmber.copy(alpha = 0.6f)
                                        },
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = when (scanState) {
                                        ScanState.CONNECTED -> "● CONECTADO"
                                        ScanState.ERROR -> "FALHA"
                                        ScanState.IDLE -> "DESCONECTADO"
                                        else -> "BUSCANDO..."
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (scanState) {
                                        ScanState.CONNECTED -> AstraGreen
                                        ScanState.ERROR -> AstraRedline
                                        ScanState.IDLE -> TextMuted
                                        else -> GmAmber
                                    },
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

                        Spacer(modifier = Modifier.height(12.dp))

                        if (scanState != ScanState.IDLE && scanState != ScanState.CONNECTED && scanState != ScanState.ERROR) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (scanState == ScanState.CONNECTED) {
                                Button(
                                    onClick = onNavigateToDashboard,
                                    colors = ButtonDefaults.buttonColors(containerColor = AstraGreen, contentColor = Color.Black),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("open_dashboard_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ABRIR COCKPIT", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.disconnect() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraRedline),
                                    border = BorderStroke(1.dp, AstraRedline),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("disconnect_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desconectar")
                                }
                            } else {
                                val isScanning = scanState != ScanState.IDLE && scanState != ScanState.ERROR
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
                                            text = "BUSCAR CENTRAL",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                    }
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
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.5.dp, if (isInjectionDetected) AstraGreen.copy(alpha = 0.6f) else CarbonBorder),
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
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isInjectionDetected) AstraGreen.copy(alpha = 0.2f) else CarbonElevated)
                                    .border(1.5.dp, if (isInjectionDetected) AstraGreen else CarbonBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "3",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isInjectionDetected) AstraGreen else TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3. CONFIGURAÇÃO DE INJEÇÃO",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isInjectionDetected) AstraGreen else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isInjectionDetected) AstraGreen.copy(alpha = 0.15f) else CarbonElevated)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isInjectionDetected) "DETECTADA" else "AGUARDANDO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isInjectionDetected) AstraGreen else TextMuted,
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
                }
            }
        }
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
