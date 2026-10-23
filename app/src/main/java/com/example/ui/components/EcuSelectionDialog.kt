package com.example.ui.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.VehicleProfile
import com.example.ui.theme.AstraCyan
import com.example.ui.theme.AstraGreen
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonCard
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.GmAmber
import com.example.ui.theme.GmAmberLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EcuSelectionDialog(
    currentProfile: VehicleProfile,
    onSelectProfile: (VehicleProfile) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonCard),
            border = BorderStroke(1.5.dp, GmAmber.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .testTag("ecu_selection_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GmAmber.copy(alpha = 0.2f))
                                .border(1.dp, GmAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = GmAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SELECIONAR CENTRAL ECU",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Calibração e mapas de injeção Astra Brasil",
                                fontSize = 11.sp,
                                color = GmAmberLight
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A detecção automática lê a central fisicamente, mas você pode selecionar o mapa abaixo para forçar a calibração de sensores e limites exatos.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(VehicleProfile.GM_ASTRA_FINGERPRINTS) { profile ->
                        val isSelected = currentProfile.ecuName == profile.ecuName &&
                                currentProfile.engineCode == profile.engineCode

                        EcuProfileItemCard(
                            profile = profile,
                            isSelected = isSelected,
                            onClick = {
                                onSelectProfile(profile)
                                onDismiss()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EcuProfileItemCard(
    profile: VehicleProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDbw = profile.notes.contains("Drive-by-Wire") ||
            profile.notes.contains("eletrônico") ||
            profile.supportsFlexPids ||
            profile.ecuName.contains("ME 7") ||
            profile.ecuName.contains("MT27E")
    val throttleLabel = if (isDbw) "DBW Eletrônico" else "Cabo + IAC"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) GmAmber.copy(alpha = 0.12f) else CarbonElevated)
            .border(
                1.5.dp,
                if (isSelected) GmAmber else CarbonBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. LINHA PRINCIPAL: ÍCONE, NOME DA ECU E BADGE DE STATUS/ANO
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
                            .background(if (isSelected) GmAmber.copy(alpha = 0.25f) else AstraCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = if (isSelected) GmAmber else AstraCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = profile.ecuName,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) GmAmber else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GmAmber)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ATIVA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CarbonDark)
                            .border(0.8.dp, CarbonBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = profile.years,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AstraGreen
                        )
                    }
                }
            }

            // 2. LINHA DE ESPECIFICAÇÕES TÉCNICAS EM BADGES (COMPACTAS E ALINHADAS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EcuSpecBadge(
                    label = profile.engineDisplacement.substringBefore(" ("),
                    color = AstraCyan
                )
                EcuSpecBadge(
                    label = profile.valveCount.substringBefore(" ("),
                    color = TextPrimary
                )
                EcuSpecBadge(
                    label = profile.horsepower.substringBefore(" /").substringBefore(" (Gas)").substringBefore(" (GNV)"),
                    color = GmAmberLight
                )
                EcuSpecBadge(
                    label = throttleLabel,
                    color = if (isDbw) AstraGreen else GmAmber
                )
            }

            // 3. GRADE ESTRUTURADA DE DADOS (SEM ESPAÇOS VAZIOS OU DESALINHAMENTO)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonDark.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Motor: ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = profile.engineCode,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    if (profile.gmPartNumber.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Nº GM: ",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Text(
                                text = profile.gmPartNumber,
                                fontSize = 9.5.sp,
                                color = AstraCyan
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = profile.fuelType.substringBefore(" ("),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (profile.supportsFlexPids) AstraGreen else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profile.trimLevels,
                        fontSize = 9.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 4. OBSERVAÇÃO TÉCNICA RELEVANTE (NOTAS E ARQUITETURA)
            if (profile.notes.isNotBlank()) {
                Text(
                    text = profile.notes,
                    fontSize = 9.sp,
                    color = TextMuted,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EcuSpecBadge(
    label: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.8.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

