package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.example.model.SensorDescriptor
import com.example.serial.EcuFingerprinter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.theme.AstraCyan
import com.example.ui.theme.AstraGreen
import com.example.ui.theme.AstraRedline
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataloggerScreen(
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecordingDatalogger.collectAsState()
    val recordedCount by viewModel.recordedDataloggerCount.collectAsState()
    val lastFilePath by viewModel.lastExportedFilePath.collectAsState()
    val savedFiles by viewModel.savedDatalogsList.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val telemetry by viewModel.scannerManager.telemetry.collectAsState()
    val vehicle by viewModel.scannerManager.vehicleProfile.collectAsState()
    val sensorProfile by viewModel.sensorProfile.collectAsState()
    val isInjectionDetected by viewModel.isInjectionDetected.collectAsState()

    var selectedPidFilter by remember { mutableIntStateOf(0) }
    val pidFilters = listOf("Todos os PIDs", "Modo 01 (SAE)", "Modo 21 (GM Local)", "Injeção & Mistura", "Admissão & Ignição")

    val activeProfile = sensorProfile ?: remember(vehicle) {
        EcuFingerprinter({ _, _ -> "" }, {}).mapSensorCapabilities(vehicle)
    }

    val allSensors = activeProfile.compatibleSensors

    val filteredSensors = when (selectedPidFilter) {
        1 -> allSensors.filter { it.pidCommand.startsWith("01") }
        2 -> allSensors.filter { it.pidCommand.startsWith("21") }
        3 -> allSensors.filter { it.id in listOf("INJ_TIME", "DUTY_CYCLE", "STFT", "LTFT", "LAMBDA1", "LAMBDA2", "ETHANOL", "AFR", "FUEL_L_H") }
        4 -> allSensors.filter { it.id in listOf("RPM", "SPEED", "MAP", "VACUUM", "TPS", "TPS_VOLT1", "TPS_VOLT2", "PEDAL", "IAC", "ECT", "IAT", "SPARK", "KNOCK_GLOBAL", "KNOCK_CYL", "MAF_EST") }
        else -> allSensors
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark)
            .padding(horizontal = 14.dp)
            .testTag("datalogger_screen"),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Banner Feedback de Exportação
        if (exportMessage != null) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AstraGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AstraGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = exportMessage!!,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = { viewModel.dismissExportMessage() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar aviso",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Card Principal de Gravação e Exportação do Datalogger
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
                                text = "DATALOGGER DE TELEMETRIA",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "Exportação de dados da ECU em CSV com carimbo de data/hora",
                                fontSize = 11.sp,
                                color = GmAmberLight,
                                maxLines = 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isRecording) AstraRedline.copy(alpha = 0.2f) else CarbonElevated)
                                .border(1.dp, if (isRecording) AstraRedline else CarbonBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isRecording) AstraRedline else TextMuted)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRecording) "GRAVANDO" else "PARADO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRecording) AstraRedline else TextMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Estatísticas de Amostragem Estruturadas e Não Emboladas
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = CarbonElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CarbonBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Amostras Gravadas
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "AMOSTRAS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$recordedCount",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isRecording) AstraRedline else TextPrimary,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRecording) "gravando" else "capturadas",
                                    fontSize = 9.sp,
                                    color = if (isRecording) AstraRedline else TextMuted,
                                    maxLines = 1
                                )
                            }

                            // Divisor Vertical 1
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(34.dp)
                                    .background(CarbonBorder)
                            )

                            // 2. Formato de Exportação
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "FORMATO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ".CSV",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AstraCyan,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Padrão GM",
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    maxLines = 1
                                )
                            }

                            // Divisor Vertical 2
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(34.dp)
                                    .background(CarbonBorder)
                            )

                            // 3. Parâmetros Ativos
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "PARÂMETROS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${allSensors.size}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AstraGreen,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "PIDs Mapeados",
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Botão Iniciar / Parar Gravação Contínua
                    Button(
                        onClick = { viewModel.toggleDataloggerRecording() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) AstraRedline else GmAmber,
                            contentColor = if (isRecording) Color.White else Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("toggle_recording_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRecording) "PARAR E SALVAR GRAVAÇÃO" else "INICIAR GRAVAÇÃO CONTÍNUA",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Botão Salvar Sessão Atual como CSV com Carimbo de Data/Hora
                    Button(
                        onClick = { viewModel.exportCurrentSessionCsv() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AstraCyan,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("export_current_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SALVAR SESSÃO ATUAL COMO .CSV",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Botão de Compartilhar último arquivo gerado
                    if (lastFilePath != null) {
                        val file = File(lastFilePath!!)
                        if (file.exists()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { shareCsvFile(context, file) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("share_last_file_button")
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    tint = AstraGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Compartilhar ${file.name}",
                                    color = AstraGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sessões Salvas no Armazenamento do Dispositivo
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = GmAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ARQUIVOS .CSV NO DISPOSITIVO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "${savedFiles.size} arquivos salvos",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        if (savedFiles.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum arquivo gravado ainda.\nClique em 'SALVAR SESSÃO ATUAL COMO .CSV' acima para exportar a telemetria.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        } else {
            items(savedFiles) { file ->
                SavedDatalogItem(
                    file = file,
                    onShare = { shareCsvFile(context, file) },
                    onDelete = { viewModel.deleteSavedDatalog(file) }
                )
            }
        }

        // Matriz Técnica Completa de PIDs Suportados pela ECU
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CarbonBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                text = "MATRIZ DE PIDs SUPORTADOS PELA ECU",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeProfile.ecuFamily} • ${allSensors.size} Parâmetros Mapeados",
                                fontSize = 11.sp,
                                color = GmAmberLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AstraGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${filteredSensors.size} PIDs",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AstraGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filtros Rápidos por Protocolo e Subsistema
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pidFilters.forEachIndexed { index, title ->
                            val isSelected = selectedPidFilter == index
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPidFilter = index },
                                label = {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GmAmber,
                                    selectedLabelColor = Color.Black,
                                    containerColor = CarbonElevated,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) GmAmber else Color.Transparent,
                                    selectedBorderColor = GmAmber,
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Lista Estruturada de PIDs com Formatação Técnica Rigorosa
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredSensors.forEach { sensor ->
                            PidParameterCard(sensor = sensor, telemetry = telemetry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedDatalogItem(
    file: File,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
    val sizeKb = (file.length() / 1024).coerceAtLeast(1)

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CarbonBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AstraCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = AstraCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = file.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "$dateStr  •  $sizeKb KB  •  CSV",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartilhar",
                        tint = AstraGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = AstraRedline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun shareCsvFile(context: Context, file: File) {
    try {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Datalog Astra GM - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Exportar / Salvar Datalog Astra"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun TelemetryDataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = TextMuted)
        Text(text = value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun PidParameterCard(
    sensor: SensorDescriptor,
    telemetry: com.example.model.LiveTelemetry
) {
    val liveValue = getLiveValueForSensor(sensor.id, telemetry)
    val isMode01 = sensor.pidCommand.startsWith("01")
    val isMode21 = sensor.pidCommand.startsWith("21")

    val pidBadgeBg = when {
        isMode01 -> GmAmber.copy(alpha = 0.15f)
        isMode21 -> AstraCyan.copy(alpha = 0.15f)
        else -> AstraGreen.copy(alpha = 0.15f)
    }

    val pidBadgeColor = when {
        isMode01 -> GmAmber
        isMode21 -> AstraCyan
        else -> AstraGreen
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonElevated),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, CarbonBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Linha 1: Código PID + Modo + Valor em Tempo Real
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
                            .clip(RoundedCornerShape(4.dp))
                            .background(pidBadgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (sensor.pidCommand == "DERIVED") "FÍSICO" else sensor.pidCommand,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = pidBadgeColor
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = sensor.mode,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Valor ao vivo formatado
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = liveValue,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = sensor.unit,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Linha 2: Nome do Parâmetro
            Text(
                text = sensor.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Linha 3: Faixa de Tolerância e Descrição Técnica
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Faixa GM: ${sensor.normalRange}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AstraCyan,
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "STATUS: ATIVO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AstraGreen
                )
            }
        }
    }
}

private fun getLiveValueForSensor(sensorId: String, telemetry: com.example.model.LiveTelemetry): String {
    return when (sensorId) {
        "RPM" -> "${telemetry.rpm.toInt()}"
        "SPEED" -> "${telemetry.speedKmh.toInt()}"
        "MAP" -> "${telemetry.mapKpa.toInt()}"
        "VACUUM" -> String.format(Locale.US, "%.2f", telemetry.manifoldVacuumBar)
        "TPS" -> String.format(Locale.US, "%.1f", telemetry.throttlePercent)
        "TPS_VOLT1" -> String.format(Locale.US, "%.2f", telemetry.throttleVoltage1)
        "TPS_VOLT2" -> String.format(Locale.US, "%.2f", telemetry.throttleVoltage2)
        "PEDAL" -> String.format(Locale.US, "%.1f", telemetry.acceleratorPedalPercent)
        "IAC" -> "${telemetry.iacSteps}"
        "ECT" -> "${telemetry.coolantTempC.toInt()}"
        "IAT" -> "${telemetry.intakeAirTempC.toInt()}"
        "INJ_TIME" -> String.format(Locale.US, "%.2f", telemetry.injectionTimeMs)
        "DUTY_CYCLE" -> String.format(Locale.US, "%.1f", telemetry.injectorDutyCyclePercent)
        "STFT" -> String.format(Locale.US, "%+.1f", telemetry.shortTermFuelTrim)
        "LTFT" -> String.format(Locale.US, "%+.1f", telemetry.longTermFuelTrim)
        "LAMBDA1" -> String.format(Locale.US, "%.3f", telemetry.lambdaVoltage)
        "LAMBDA2" -> String.format(Locale.US, "%.3f", telemetry.lambdaPostCatVoltage)
        "ETHANOL" -> "${telemetry.ethanolPercentage.toInt()}"
        "AFR" -> String.format(Locale.US, "%.1f", telemetry.estimatedAfRatio)
        "SPARK" -> String.format(Locale.US, "%.1f", telemetry.sparkAdvanceDeg)
        "KNOCK_GLOBAL" -> String.format(Locale.US, "%.1f", telemetry.knockRetardDeg)
        "KNOCK_CYL" -> "${telemetry.knockCylinder1Deg.toInt()}° / ${telemetry.knockCylinder2Deg.toInt()}°"
        "MAF_EST" -> String.format(Locale.US, "%.1f", telemetry.estimatedMafGramsSec)
        "FUEL_L_H" -> String.format(Locale.US, "%.2f", telemetry.instantConsumptionLitersHour)
        "ECU_VOLT" -> String.format(Locale.US, "%.1f", telemetry.ecuBatteryVoltage)
        "ELM_VOLT" -> String.format(Locale.US, "%.1f", telemetry.elmBatteryVoltage)
        else -> "--"
    }
}
