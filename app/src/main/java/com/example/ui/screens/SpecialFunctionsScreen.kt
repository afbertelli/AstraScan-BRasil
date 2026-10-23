package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import com.example.serial.AstraIdleHealthChecker
import com.example.serial.CorrectionComparisonReport
import com.example.serial.IdleAuditResult
import com.example.serial.SensorHealthCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.model.ScanState
import com.example.serial.AstraSpecialFunctionsEngine
import com.example.serial.SpecialExecutionState
import com.example.serial.SpecialFunctionCategory
import com.example.serial.SpecialFunctionItem
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

@SuppressLint("MissingPermission")
@Composable
fun SpecialFunctionsScreen(
    viewModel: ScannerViewModel
) {
    val engine = viewModel.specialFunctionsEngine
    val scanState by viewModel.scannerManager.scanState.collectAsState()
    val telemetry by viewModel.scannerManager.telemetry.collectAsState()
    val vehicleProfile by viewModel.scannerManager.vehicleProfile.collectAsState()
    val executionState by engine.executionState.collectAsState()
    val executionLogs by engine.executionLogs.collectAsState()
    val comparisonReport by viewModel.activeComparisonReport.collectAsState()

    var selectedCategory by remember { mutableStateOf<SpecialFunctionCategory?>(null) }
    var itemToConfirm by remember { mutableStateOf<SpecialFunctionItem?>(null) }
    var isIdleSensorsExpanded by remember { mutableStateOf(false) }

    val isPhysicalEcuConnected by viewModel.isPhysicalEcuConnected.collectAsState()
    val isConnected = isPhysicalEcuConnected && scanState == ScanState.CONNECTED
    val currentRpm = if (isConnected) telemetry.rpm else 0f

    val idleAuditResult = remember(telemetry, vehicleProfile, isConnected) {
        if (!isConnected) {
            IdleAuditResult(
                isIdleDetected = false,
                idleConditionMessage = "ECU desconectada. Conecte o scanner na central do veículo para auditar os sensores da marcha lenta.",
                totalSensors = 0,
                sensorsOkCount = 0,
                sensorsOutCount = 0,
                checks = emptyList<SensorHealthCheck>(),
                primaryRecommendation = null
            )
        } else {
            AstraIdleHealthChecker.auditIdleSensors(telemetry, vehicleProfile)
        }
    }

    val filteredList = remember(selectedCategory) {
        if (selectedCategory == null) AstraSpecialFunctionsEngine.AVAILABLE_FUNCTIONS
        else AstraSpecialFunctionsEngine.AVAILABLE_FUNCTIONS.filter { it.category == selectedCategory }
    }

    // Modal de Confirmação antes de disparar o comando
    itemToConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToConfirm = null },
            containerColor = CarbonCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = GmAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = item.description,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    if (item.warningNote.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AstraYellow.copy(alpha = 0.15f))
                                .border(1.dp, AstraYellow.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AstraYellow,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.warningNote,
                                    color = AstraYellow,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    val conditionText = if (item.requiresEngineStopped) {
                        "Pré-requisito: Motor DESLIGADO e chave na posição 2 (ignição ligada)."
                    } else if (item.requiresEngineRunning) {
                        "Pré-requisito: Motor LIGADO em marcha lenta (>600 RPM)."
                    } else "Pré-requisito: Chave ligada."

                    Text(
                        text = conditionText,
                        color = AstraCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRun = item
                        itemToConfirm = null
                        viewModel.executeCalibrationWithReport(toRun, currentRpm)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GmAmber, contentColor = Color.Black),
                    modifier = Modifier.testTag("confirm_run_test_button")
                ) {
                    Text("EXECUTAR AGORA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToConfirm = null }) {
                    Text("Cancelar", color = TextMuted)
                }
            }
        )
    }

    // Modal Comparativo "Antes vs Depois": Status do Que Estava Errado e do Que Foi Corrigido
    comparisonReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissComparisonReport() },
            containerColor = CarbonCard,
            modifier = Modifier.padding(horizontal = 8.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AstraGreen.copy(alpha = 0.2f))
                            .border(1.5.dp, AstraGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AstraGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "STATUS DA CALIBRAÇÃO",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AstraGreen
                        )
                        Text(
                            text = report.testTitle,
                            fontSize = 11.sp,
                            color = GmAmberLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = report.summaryMessage,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 16.sp
                    )

                    Text(
                        text = "O QUE ESTAVA ERRADO VS O QUE FOI CORRIGIDO:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GmAmber
                    )

                    val abnormalItems = report.beforeSnapshots.filter { !it.isOk }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (report.resolvedIssues.isNotEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = AstraGreen.copy(alpha = 0.12f)),
                                    border = BorderStroke(1.dp, AstraGreen.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "AÇÕES EXECUTADAS & CONFIRMADAS PELA ECU:",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AstraGreen
                                        )
                                        report.resolvedIssues.forEach { issue ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = AstraGreen,
                                                    modifier = Modifier.size(14.dp).padding(top = 1.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = issue,
                                                    fontSize = 11.sp,
                                                    color = TextPrimary,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (abnormalItems.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CarbonElevated)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "Todos os 9 sensores do Astra Família II estavam em condições normais antes do procedimento. A rotina foi gravada na EEPROM de forma preventiva com sucesso.",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        } else {
                            items(abnormalItems, key = { it.sensorId }) { before ->
                                val after = report.afterSnapshots.firstOrNull { it.sensorId == before.sensorId }
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = CarbonElevated),
                                    border = BorderStroke(1.dp, CarbonBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = before.sensorName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("ANTES (ERRO)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AstraRedline)
                                                Text(before.currentValueStr, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                                before.issueDescription?.let {
                                                    Text(it, fontSize = 9.sp, color = AstraRedline.copy(alpha = 0.85f), maxLines = 2)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("DEPOIS (CORRIGIDO)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AstraGreen)
                                                Text(after?.currentValueStr ?: "--", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AstraGreen)
                                                Text("Faixa: ${before.nominalRange}", fontSize = 9.sp, color = TextMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissComparisonReport() },
                    colors = ButtonDefaults.buttonColors(containerColor = AstraGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("close_comparison_dialog")
                ) {
                    Text("FECHAR E CONTINUAR", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Banner Header com Selo de Segurança de Airbag
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E1602), CarbonCard, Color(0xFF0F1A24))
                        )
                    )
                    .border(1.dp, GmAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GmAmber.copy(alpha = 0.2f))
                                .border(1.5.dp, GmAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = GmAmber,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AJUSTES, RESETS & ATUADORES",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Comunicação Bidirecional KWP2000 GM",
                                fontSize = 11.sp,
                                color = GmAmberLight
                            )
                        }
                    }

                    // Selo de Proteção e Trava Anti-Disparo de Airbag
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AstraGreen.copy(alpha = 0.12f))
                            .border(1.dp, AstraGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AstraGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SEGURANÇA ATIVA: Atuadores de Airbag permanentemente bloqueados.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AstraGreen
                            )
                        }
                    }
                }
            }
        }

        // Estado do Motor e Barramento
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.dp, CarbonBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) AstraGreen else AstraRedline)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) "ECU Conectada" else "Desconectado",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) AstraGreen else AstraRedline
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (!isConnected) {
                                "0 RPM • Desconectado"
                            } else if (currentRpm > 150f) {
                                "Motor Ligado: ${currentRpm.toInt()} RPM"
                            } else {
                                "Motor Desligado: 0 RPM"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected && currentRpm > 150f) AstraCyan else TextMuted
                        )
                    }
                }
            }
        }

        // ASSISTENTE DE SAÚDE DA MARCHA LENTA EM TEMPO REAL
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(
                    1.dp,
                    if (isConnected && idleAuditResult.isIdleDetected) {
                        if (idleAuditResult.sensorsOutCount == 0) AstraGreen.copy(alpha = 0.6f) else AstraRedline.copy(alpha = 0.6f)
                    } else CarbonBorder
                ),
                modifier = Modifier.fillMaxWidth().testTag("idle_health_assistant_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (isConnected && idleAuditResult.isIdleDetected) {
                                    if (idleAuditResult.sensorsOutCount == 0) AstraGreen else AstraYellow
                                } else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "SAÚDE DA MARCHA LENTA",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (!isConnected) {
                                        "Conecte à ECU para auditar sensores"
                                    } else if (idleAuditResult.isIdleDetected) {
                                        if (idleAuditResult.sensorsOutCount == 0) "Todos os 9 sensores nominais" else "${idleAuditResult.sensorsOutCount} sensor(es) fora da tolerância"
                                    } else {
                                        "Ligue o motor em marcha lenta"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isConnected && idleAuditResult.isIdleDetected) {
                                        if (idleAuditResult.sensorsOutCount == 0) AstraGreen else AstraYellow
                                    } else TextMuted
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isConnected && idleAuditResult.isIdleDetected) {
                                        if (idleAuditResult.sensorsOutCount == 0) AstraGreen.copy(alpha = 0.2f) else AstraRedline.copy(alpha = 0.2f)
                                    } else CarbonElevated
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isConnected && idleAuditResult.isIdleDetected) {
                                    if (idleAuditResult.sensorsOutCount == 0) "100% OK" else "${idleAuditResult.sensorsOutCount} FORA"
                                } else {
                                    "OFFLINE"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected && idleAuditResult.isIdleDetected) {
                                    if (idleAuditResult.sensorsOutCount == 0) AstraGreen else AstraRedline
                                } else TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = idleAuditResult.idleConditionMessage,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    // Se houver recomendação ativa: Card de 1 Toque para Calibrar e Corrigir
                    if (isConnected) {
                        idleAuditResult.primaryRecommendation?.let { rec ->
                        rec.recommendedActionId?.let { actionId ->
                            val actionItem = AstraSpecialFunctionsEngine.AVAILABLE_FUNCTIONS.firstOrNull { it.id == actionId }
                            if (actionItem != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF261900))
                                        .border(1.dp, GmAmber.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.FlashOn,
                                                contentDescription = null,
                                                tint = GmAmber,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "AÇÃO RECOMENDADA DETECTADA",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GmAmber
                                            )
                                        }

                                        Text(
                                            text = "${rec.sensorName} (${rec.currentValueStr} ${rec.unit}) está fora da faixa nominal (${rec.nominalRange}). ${rec.issueDescription ?: ""}",
                                            fontSize = 11.sp,
                                            color = TextPrimary,
                                            lineHeight = 15.sp
                                        )

                                        Button(
                                            onClick = {
                                                viewModel.executeCalibrationWithReport(actionItem, currentRpm)
                                            },
                                            enabled = isConnected && executionState !is SpecialExecutionState.Running,
                                            colors = ButtonDefaults.buttonColors(containerColor = GmAmber, contentColor = Color.Black),
                                            modifier = Modifier.fillMaxWidth().height(36.dp).testTag("quick_fix_button")
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("CALIBRAR E CORRIGIR: ${actionItem.title}", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                    // Tabela Colapsável dos 9 Sensores Auditados
                    if (isConnected && idleAuditResult.isIdleDetected) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isIdleSensorsExpanded = !isIdleSensorsExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DETALHES DOS 9 SENSORES EM MARCHA LENTA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GmAmberLight
                            )
                            Icon(
                                imageVector = if (isIdleSensorsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isIdleSensorsExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                idleAuditResult.checks.forEach { check ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CarbonElevated)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                            Text(check.sensorName, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                            Text("Nominal: ${check.nominalRange} ${check.unit}", fontSize = 9.sp, color = TextMuted)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${check.currentValueStr} ${check.unit}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (check.isOk) AstraGreen else AstraRedline
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (check.isOk) AstraGreen else AstraRedline)
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

        // Card de Execução Ativa (quando um teste estiver rodando ou concluído)
        item {
            AnimatedVisibility(
                visible = executionState !is SpecialExecutionState.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                when (val state = executionState) {
                    is SpecialExecutionState.Running -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1700)),
                            border = BorderStroke(1.5.dp, GmAmber),
                            modifier = Modifier.fillMaxWidth().testTag("running_test_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = GmAmber,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "EXECUTANDO COMANDO KWP",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GmAmber
                                        )
                                    }

                                    Button(
                                        onClick = { engine.abortCurrentTest() },
                                        colors = ButtonDefaults.buttonColors(containerColor = AstraRedline, contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("abort_test_button")
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("PARAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = state.statusText,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    color = GmAmber,
                                    trackColor = CarbonCard,
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )

                                if (executionLogs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 100.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = executionLogs.takeLast(4).joinToString("\n"),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = AstraCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is SpecialExecutionState.Success -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF002213)),
                            border = BorderStroke(1.5.dp, AstraGreen),
                            modifier = Modifier.fillMaxWidth().testTag("success_test_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AstraGreen, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("COMANDO CONCLUÍDO COM SUCESSO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AstraGreen)
                                    }

                                    IconButton(
                                        onClick = { engine.resetState() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextMuted)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = state.message,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { engine.resetState() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AstraGreen, contentColor = Color.Black),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Text("CONCLUÍDO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    is SpecialExecutionState.Error -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF280B0B)),
                            border = BorderStroke(1.5.dp, AstraRedline),
                            modifier = Modifier.fillMaxWidth().testTag("error_test_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AstraRedline, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("COMANDO NÃO EXECUTADO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AstraRedline)
                                    }

                                    IconButton(
                                        onClick = { engine.resetState() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextMuted)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = state.errorMessage,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = { engine.resetState() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraRedline),
                                    border = BorderStroke(1.dp, AstraRedline),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Text("FECHAR E REVISAR CONDIÇÕES", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    SpecialExecutionState.Idle -> {}
                }
            }
        }

        // Filtro por Categorias (Horizontal Scroll)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Todos (${AstraSpecialFunctionsEngine.AVAILABLE_FUNCTIONS.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GmAmber.copy(alpha = 0.25f),
                        selectedLabelColor = GmAmber
                    )
                )

                SpecialFunctionCategory.entries.forEach { cat ->
                    val isSel = selectedCategory == cat
                    val count = AstraSpecialFunctionsEngine.AVAILABLE_FUNCTIONS.count { it.category == cat }
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedCategory = cat },
                        label = { Text("${cat.title} ($count)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AstraCyan.copy(alpha = 0.25f),
                            selectedLabelColor = AstraCyan
                        )
                    )
                }
            }
        }

        // Lista de Cards de Funções Especiais
        items(filteredList, key = { it.id }) { item ->
            val isRunningThis = (executionState as? SpecialExecutionState.Running)?.testId == item.id
            val isEngineConditionMet = if (item.requiresEngineStopped) currentRpm <= 150f else if (item.requiresEngineRunning) currentRpm >= 500f else true

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.dp, if (isRunningThis) GmAmber else CarbonBorder),
                modifier = Modifier.fillMaxWidth().testTag("special_item_${item.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = item.category.title,
                                fontSize = 10.sp,
                                color = GmAmberLight
                            )
                        }

                        // Badge de Requisito de Motor
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (item.requiresEngineStopped) AstraGreen.copy(alpha = 0.15f) else AstraCyan.copy(alpha = 0.15f))
                                .border(1.dp, if (item.requiresEngineStopped) AstraGreen.copy(alpha = 0.4f) else AstraCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (item.requiresEngineStopped) "Motor Desligado" else "Motor Ligado",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.requiresEngineStopped) AstraGreen else AstraCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.description,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duração: ~${item.durationSeconds}s",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )

                        Button(
                            onClick = { itemToConfirm = item },
                            enabled = isConnected && executionState !is SpecialExecutionState.Running,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEngineConditionMet) GmAmber else CarbonElevated,
                                contentColor = if (isEngineConditionMet) Color.Black else TextMuted
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("run_btn_${item.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EXECUTAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
