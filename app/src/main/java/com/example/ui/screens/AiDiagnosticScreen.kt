package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ai.ComponentAudit
import com.example.ai.ComponentStatus
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
fun AiDiagnosticScreen(
    viewModel: ScannerViewModel
) {
    val aiReport by viewModel.aiReport.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingAi.collectAsState()
    val componentAudits by viewModel.componentAudits.collectAsState()
    val vehicle by viewModel.scannerManager.vehicleProfile.collectAsState()

    var filterIndex by remember { mutableIntStateOf(0) } // 0 = Todos, 1 = Fora do Padrão, 2 = Normais
    val filters = listOf("Todos os Sensores", "Fora do Padrão / Anomalias", "Dentro do Aceitável")

    val displayedAudits = when (filterIndex) {
        1 -> componentAudits.filter { it.status != ComponentStatus.OK }
        2 -> componentAudits.filter { it.status == ComponentStatus.OK }
        else -> componentAudits
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 14.dp)
            .testTag("ai_diagnostic_screen"),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Banner Header da IA
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF261900), CarbonCard, Color(0xFF00222B))
                        )
                    )
                    .border(1.5.dp, AstraCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AstraCyan.copy(alpha = 0.2f))
                                    .border(1.5.dp, AstraCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AstraCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DIAGNÓSTICO AUTOMOTIVO POR IA",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Motor Gemini 3.5 Flash & Análise OEM GM",
                                    fontSize = 11.sp,
                                    color = AstraCyan,
                                    maxLines = 1
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GmAmber.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "TEMPO REAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GmAmber,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Cruza todos os dados de telemetria da injeção com as tolerâncias de fábrica GM. Identifica anomalias estequiométricas, causas de falhas intermitentes e testes práticos no cofre do motor.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.runAiDiagnosis() },
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = AstraCyan, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_ai_diagnosis_button")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Cruzando Parâmetros com Gemini IA...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EXECUTAR DIAGNÓSTICO INTELIGENTE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Relatório do Diagnóstico da IA se já gerado
        if (aiReport != null) {
            val report = aiReport!!

            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonCard),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (report.abnormalComponents.isNotEmpty()) AstraRedline else AstraGreen),
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
                                Icon(
                                    imageVector = if (report.abnormalComponents.isNotEmpty()) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (report.abnormalComponents.isNotEmpty()) AstraRedline else AstraGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (report.abnormalComponents.isNotEmpty()) "ANOMALIA DETECTADA" else "SISTEMA OPERANDO CONFORME PADRÃO",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.abnormalComponents.isNotEmpty()) AstraRedline else AstraGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (report.isGeneratedByGemini) AstraCyan.copy(alpha = 0.2f) else GmAmber.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (report.isGeneratedByGemini) "Gemini 3.5 Flash" else "Especialista GM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.isGeneratedByGemini) AstraCyan else GmAmber,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Causa Raiz
                        Text(
                            text = "CAUSA RAIZ PROVÁVEL:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GmAmberLight,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = report.rootCause,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Resumo Executivo
                        Text(
                            text = "AVALIAÇÃO GERAL:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = report.summary,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Detalhamento do Cruzamento de Dados
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CarbonElevated)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "CRUZAMENTO ESTEQUIOMÉTRICO & CAUSA E EFEITO:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AstraCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = report.crossAnalysisDetails,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 17.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Plano de Ação Recomendado
                        Text(
                            text = "PLANO DE AÇÃO E TESTES PRÁTICOS NO COFRE:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AstraGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        report.actionPlan.forEachIndexed { idx, action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AstraGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = action,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status dos Componentes e Filtro de Sensores Auditados (Alinhamento Horizontal)
        item(span = { GridItemSpan(maxLineSpan) }) {
            val okCount = componentAudits.count { it.status == ComponentStatus.OK }
            val warningCount = componentAudits.count { it.status == ComponentStatus.WARNING }
            val alertCount = componentAudits.count { it.status == ComponentStatus.ALERT }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STATUS DOS COMPONENTES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonElevated)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${componentAudits.size} TOTAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AstraCyan,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Barra Horizontal de Status dos Componentes (Distribuição e Filtro Rápido)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Padrão / OK
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (filterIndex == 2) AstraGreen.copy(alpha = 0.25f) else CarbonElevated)
                            .border(1.dp, if (filterIndex == 2) AstraGreen else CarbonBorder, RoundedCornerShape(8.dp))
                            .clickable { filterIndex = if (filterIndex == 2) 0 else 2 }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$okCount",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AstraGreen,
                                maxLines = 1
                            )
                            Text(
                                text = "PADRÃO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (filterIndex == 2) AstraGreen else TextMuted,
                                maxLines = 1
                            )
                        }
                    }

                    // 2. Atenção
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (filterIndex == 1) AstraYellow.copy(alpha = 0.25f) else CarbonElevated)
                            .border(1.dp, if (filterIndex == 1) AstraYellow else CarbonBorder, RoundedCornerShape(8.dp))
                            .clickable { filterIndex = if (filterIndex == 1) 0 else 1 }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$warningCount",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AstraYellow,
                                maxLines = 1
                            )
                            Text(
                                text = "ATENÇÃO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (filterIndex == 1) AstraYellow else TextMuted,
                                maxLines = 1
                            )
                        }
                    }

                    // 3. Crítico / Alerta
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (filterIndex == 1 && alertCount > 0) AstraRedline.copy(alpha = 0.25f) else CarbonElevated)
                            .border(1.dp, if (alertCount > 0) AstraRedline.copy(alpha = 0.6f) else CarbonBorder, RoundedCornerShape(8.dp))
                            .clickable { filterIndex = if (filterIndex == 1) 0 else 1 }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$alertCount",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alertCount > 0) AstraRedline else TextMuted,
                                maxLines = 1
                            )
                            Text(
                                text = "CRÍTICO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alertCount > 0) AstraRedline else TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Lista de Cards de Componentes
        if (displayedAudits.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (componentAudits.isEmpty()) "Clique em 'EXECUTAR DIAGNÓSTICO INTELIGENTE' acima para auditar os parâmetros." else "Nenhum componente nesta categoria de filtro.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(displayedAudits) { audit ->
                ComponentAuditCard(audit = audit)
            }
        }
    }
}

@Composable
private fun ComponentAuditCard(audit: ComponentAudit) {
    val borderColor = when (audit.status) {
        ComponentStatus.ALERT -> AstraRedline
        ComponentStatus.WARNING -> AstraYellow
        ComponentStatus.OK -> AstraGreen.copy(alpha = 0.4f)
    }

    val badgeBg = when (audit.status) {
        ComponentStatus.ALERT -> AstraRedline.copy(alpha = 0.2f)
        ComponentStatus.WARNING -> AstraYellow.copy(alpha = 0.2f)
        ComponentStatus.OK -> AstraGreen.copy(alpha = 0.2f)
    }

    val badgeText = when (audit.status) {
        ComponentStatus.ALERT -> "CRÍTICO"
        ComponentStatus.WARNING -> "ATENÇÃO"
        ComponentStatus.OK -> "PADRÃO"
    }

    val badgeTextColor = when (audit.status) {
        ComponentStatus.ALERT -> AstraRedline
        ComponentStatus.WARNING -> AstraYellow
        ComponentStatus.OK -> AstraGreen
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Linha 1: Nome do Sensor/Parâmetro + Badge de Status Compacto e Sempre Horizontal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = audit.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Linha 2: Valores em Formato Compacto (Lido e Faixa OEM GM)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonElevated)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        text = "Lido",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "${audit.currentValue} ${audit.unit}".trim(),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (audit.status != ComponentStatus.OK) badgeTextColor else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Text(
                        text = "Faixa GM",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "${audit.acceptableRange} ${audit.unit}".trim(),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = AstraCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Linha 3: Diagnóstico ou Nota Técnica compacta
            if (audit.status != ComponentStatus.OK) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Diagnóstico: ${audit.diagnosisNote}",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    lineHeight = 15.sp
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = audit.diagnosisNote,
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
