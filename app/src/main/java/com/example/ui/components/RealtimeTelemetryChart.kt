package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AstraCyan
import com.example.ui.theme.AstraGreen
import com.example.ui.theme.AstraRedline
import com.example.ui.theme.AstraYellow
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonCard
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.GmAmber
import com.example.ui.theme.GmAmberLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Modos de canais de telemetria suportados no gráfico de linhas nativo
 */
enum class ChartTelemetryChannel(
    val title: String,
    val unit: String,
    val defaultMin: Float,
    val defaultMax: Float,
    val color: Color
) {
    RPM("Rotação (RPM)", "RPM", 0f, 7000f, GmAmber),
    ECT("Temperatura (ECT)", "°C", 40f, 115f, AstraCyan),
    MAP("Pressão Coletor (MAP)", "kPa", 20f, 105f, AstraYellow),
    LAMBDA("Sonda Lambda 1", "V", 0.05f, 0.95f, AstraGreen)
}

/**
 * Gráfico de Linha em Tempo Real Nativo (Jetpack Compose Canvas)
 * Renderizado diretamente pela GPU a 60/120 FPS sem overhead de WebView ou JavaScript.
 */
@Composable
fun RealtimeTelemetryChart(
    rpmHistory: List<Float>,
    ectHistory: List<Float>,
    mapHistory: List<Float>,
    lambdaHistory: List<Float>,
    currentRpm: Float,
    currentEct: Float,
    currentMap: Float,
    currentLambda: Float,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedChannel by remember { mutableStateOf(ChartTelemetryChannel.RPM) }

    val dataPoints = remember(selectedChannel, rpmHistory, ectHistory, mapHistory, lambdaHistory) {
        when (selectedChannel) {
            ChartTelemetryChannel.RPM -> rpmHistory
            ChartTelemetryChannel.ECT -> ectHistory
            ChartTelemetryChannel.MAP -> mapHistory
            ChartTelemetryChannel.LAMBDA -> lambdaHistory
        }
    }

    val currentValue = when (selectedChannel) {
        ChartTelemetryChannel.RPM -> currentRpm
        ChartTelemetryChannel.ECT -> currentEct
        ChartTelemetryChannel.MAP -> currentMap
        ChartTelemetryChannel.LAMBDA -> currentLambda
    }

    // Animação de pulso no ponto atual (indicador ao vivo)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonCard),
        border = BorderStroke(1.dp, CarbonBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabeçalho do Gráfico com Seletor de Canais
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = selectedChannel.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "OSCILOSCÓPIO • TEMPO REAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isConnected) "Renderização Nativa GPU • Janela Contínua" else "Sinal em Espera (Offline)",
                            fontSize = 10.sp,
                            color = if (isConnected) selectedChannel.color else TextMuted
                        )
                    }
                }

                // Display do Valor Atual
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CarbonElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val formattedVal = if (selectedChannel == ChartTelemetryChannel.LAMBDA) {
                        String.format(Locale.US, "%.3f V", if (isConnected) currentValue else 0f)
                    } else if (selectedChannel == ChartTelemetryChannel.RPM) {
                        String.format(Locale.US, "%d RPM", if (isConnected) currentValue.toInt() else 0)
                    } else {
                        String.format(Locale.US, "%.1f %s", if (isConnected) currentValue else 0f, selectedChannel.unit)
                    }
                    Text(
                        text = formattedVal,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) selectedChannel.color else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Seletor de Canais (Tabs Rápidas)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChartTelemetryChannel.values().forEach { channel ->
                    val isTabSelected = selectedChannel == channel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isTabSelected) channel.color.copy(alpha = 0.2f) else CarbonElevated
                            )
                            .clickable { selectedChannel = channel }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (channel) {
                                ChartTelemetryChannel.RPM -> "RPM"
                                ChartTelemetryChannel.ECT -> "ECT °C"
                                ChartTelemetryChannel.MAP -> "MAP"
                                ChartTelemetryChannel.LAMBDA -> "LAMBDA"
                            },
                            fontSize = 11.sp,
                            fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isTabSelected) channel.color else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // =================================================================
            // ÁREA DE DESENHO NATIVO (CANVAS GPU JETPACK COMPOSE)
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D1217))
            ) {
                val accentColor = selectedChannel.color
                val minScale = selectedChannel.defaultMin
                val maxScale = selectedChannel.defaultMax

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // 1. Grade de Fundo (Linhas Horizontais Sutis)
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = height * (i.toFloat() / gridLines)
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    // Linhas Verticais de Tempo
                    val timeDivisions = 5
                    for (i in 0..timeDivisions) {
                        val x = width * (i.toFloat() / timeDivisions)
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 1f
                        )
                    }

                    if (dataPoints.isEmpty() || !isConnected) {
                        // Linha zero plana quando desconectado
                        val zeroY = height - 4f
                        drawLine(
                            color = TextMuted.copy(alpha = 0.4f),
                            start = Offset(0f, zeroY),
                            end = Offset(width, zeroY),
                            strokeWidth = 2f
                        )
                        return@Canvas
                    }

                    // 2. Cálculo dos Pontos e Construção do Caminho Suave
                    val pointsCount = dataPoints.size
                    val stepX = if (pointsCount > 1) width / (pointsCount - 1) else width

                    val linePath = Path()
                    val fillPath = Path()

                    val mappedPoints = dataPoints.mapIndexed { index, value ->
                        val normalizedY = ((value - minScale) / (maxScale - minScale)).coerceIn(0f, 1f)
                        val x = index * stepX
                        val y = height - (normalizedY * height)
                        Offset(x, y)
                    }

                    if (mappedPoints.isNotEmpty()) {
                        linePath.moveTo(mappedPoints[0].x, mappedPoints[0].y)
                        fillPath.moveTo(mappedPoints[0].x, height)
                        fillPath.lineTo(mappedPoints[0].x, mappedPoints[0].y)

                        for (i in 1 until mappedPoints.size) {
                            val prev = mappedPoints[i - 1]
                            val curr = mappedPoints[i]

                            // Curva Bézier suave para interpolação fluida entre amostras
                            val cPointX1 = prev.x + (curr.x - prev.x) * 0.5f
                            val cPointY1 = prev.y
                            val cPointX2 = prev.x + (curr.x - prev.x) * 0.5f
                            val cPointY2 = curr.y

                            linePath.cubicTo(cPointX1, cPointY1, cPointX2, cPointY2, curr.x, curr.y)
                            fillPath.cubicTo(cPointX1, cPointY1, cPointX2, cPointY2, curr.x, curr.y)
                        }

                        val lastPoint = mappedPoints.last()
                        fillPath.lineTo(lastPoint.x, height)
                        fillPath.close()

                        // 3. Preenchimento de Gradiente Translúcido abaixo da Linha
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.35f),
                                    accentColor.copy(alpha = 0.02f)
                                ),
                                startY = 0f,
                                endY = height
                            )
                        )

                        // 4. Linha Principal Esportiva com Brilho
                        drawPath(
                            path = linePath,
                            color = accentColor,
                            style = Stroke(
                                width = 3.2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // 5. Ponto de Cabeça Pulsante (Live Head Marker)
                        drawCircle(
                            color = accentColor.copy(alpha = pulseAlpha * 0.4f),
                            radius = 8.dp.toPx(),
                            center = lastPoint
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = lastPoint
                        )
                        drawCircle(
                            color = accentColor,
                            radius = 2.dp.toPx(),
                            center = lastPoint
                        )
                    }
                }

                // Legendas de Escala Mínima e Máxima nos Cantos do Gráfico
                Text(
                    text = "${maxScale.toInt()} ${selectedChannel.unit}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 4.dp)
                )

                Text(
                    text = "${minScale.toInt()} ${selectedChannel.unit}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 4.dp)
                )

                Text(
                    text = "Tempo Real (-6s)",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 4.dp)
                )
            }
        }
    }
}
