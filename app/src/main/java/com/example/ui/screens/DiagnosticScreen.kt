package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DtcCode
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

@Composable
fun DiagnosticScreen(
    viewModel: ScannerViewModel
) {
    val activeDtcs by viewModel.scannerManager.activeDtcs.collectAsState()
    val historyDtcs by viewModel.scannerManager.historyDtcs.collectAsState()
    val telemetry by viewModel.scannerManager.telemetry.collectAsState()
    val clearMsg by viewModel.clearDtcMessage.collectAsState()
    val isClearing by viewModel.isClearing.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Ativas, 1 = Histórico
    var showSafetyConfirmDialog by remember { mutableStateOf(false) }

    val isEngineRunning = telemetry.rpm > 0f || telemetry.speedKmh > 0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 14.dp)
            .testTag("diagnostic_screen"),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cabeçalho de Diagnóstico e Controles de Ação
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CarbonBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "SISTEMA DE DIAGNÓSTICO (DTC)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Leitura Dupla: SAE 03/07 + KWP 18 00",
                                fontSize = 11.sp,
                                color = GmAmberLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Indicador de Trava de Segurança
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isEngineRunning) AstraRedline.copy(alpha = 0.2f) else AstraGreen.copy(alpha = 0.2f))
                                .border(1.dp, if (isEngineRunning) AstraRedline else AstraGreen, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isEngineRunning) Icons.Default.Lock else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isEngineRunning) AstraRedline else AstraGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEngineRunning) "MOTOR LIGADO" else "SEGURO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isEngineRunning) AstraRedline else AstraGreen,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.scanDtcs() },
                            colors = ButtonDefaults.buttonColors(containerColor = GmAmber, contentColor = Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("scan_dtc_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LER FALHAS", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showSafetyConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AstraRedline, contentColor = Color.White),
                            enabled = !isClearing,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("clear_dtc_button")
                        ) {
                            if (isClearing) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LIMPAR ECU", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Mensagem de feedback de limpeza
        item {
            AnimatedVisibility(visible = clearMsg != null) {
                clearMsg?.let { msg ->
                    val isSuccess = msg.startsWith("SUCESSO")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSuccess) AstraGreen.copy(alpha = 0.15f) else AstraRedline.copy(alpha = 0.15f))
                            .border(1.dp, if (isSuccess) AstraGreen else AstraRedline, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSuccess) AstraGreen else AstraRedline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissClearMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Tabs: Ativas vs Histórico
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CarbonCard,
                contentColor = GmAmber,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = GmAmber
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, CarbonBorder, RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Presentes / Ativas (${activeDtcs.size})", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Histórico (${historyDtcs.size})", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        }

        // Lista de Falhas
        val listToShow = if (selectedTab == 0) activeDtcs else historyDtcs

        if (listToShow.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CarbonBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AstraGreen,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (selectedTab == 0) "Nenhuma falha ativa registrada" else "Histórico de falhas limpo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Central de injeção sem registros nessa categoria.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(listToShow) { dtc ->
                DtcItemCard(dtc = dtc)
            }
        }
    }

    // Modal de Confirmação de Limpeza com Trava de Segurança
    if (showSafetyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isEngineRunning) Icons.Default.Lock else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isEngineRunning) AstraRedline else GmAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEngineRunning) "Trava de Segurança Automotiva" else "Confirmar Limpeza de Memória",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                if (isEngineRunning) {
                    Column {
                        Text(
                            text = "OPERAÇÃO BLOQUEADA POR PROTOCOLO DE SEGURANÇA GM:",
                            fontWeight = FontWeight.Bold,
                            color = AstraRedline,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "O motor do veículo está em funcionamento (${telemetry.rpm.toInt()} RPM).",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Para apagar a memória da central sem causar corte repentino de ignição ou danos aos atuadores, desligue o motor mantendo apenas a chave ligada (painel aceso).",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = "Você está prestes a enviar os comandos SAE 04 e KWP 14 FF 00 para resetar a memória não-volátil da ECU.",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Condição verificada: Motor Desligado / Chave Ligada (OK).",
                            fontSize = 12.sp,
                            color = AstraGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                if (!isEngineRunning) {
                    Button(
                        onClick = {
                            showSafetyConfirmDialog = false
                            viewModel.clearDtcs()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AstraRedline)
                    ) {
                        Text("Sim, Apagar Falhas")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyConfirmDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = CarbonElevated,
            textContentColor = TextPrimary,
            titleContentColor = TextPrimary
        )
    }
}

@Composable
private fun DtcItemCard(dtc: DtcCode) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (dtc.isPresent) AstraRedline.copy(alpha = 0.5f) else AstraYellow.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (dtc.isPresent) AstraRedline.copy(alpha = 0.2f) else AstraYellow.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = dtc.code,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (dtc.isPresent) AstraRedline else AstraYellow
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = dtc.system,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = dtc.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            // Detalhes Expandidos (Sintomas crônicos, causas e reparo GM)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CarbonElevated)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DtcDetailSection("Descrição Técnica", dtc.description)
                    DtcDetailSection("Sintomas no Astra", dtc.symptoms)
                    DtcDetailSection("Causas Prováveis", dtc.probableCauses)
                    DtcDetailSection("Solução Recomendada", dtc.recommendedFix, isHighlight = true)
                }
            }
        }
    }
}

@Composable
private fun DtcDetailSection(title: String, text: String, isHighlight: Boolean = false) {
    Column {
        Text(
            text = title.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) AstraGreen else GmAmberLight,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextPrimary,
            lineHeight = 16.sp
        )
    }
}
