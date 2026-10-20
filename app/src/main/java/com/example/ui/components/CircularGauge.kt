package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AstraRedline
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.GmAmber
import com.example.ui.theme.GmAmberLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manômetro / Tacômetro Circular Esportivo estilo painel GM Astra.
 * O ponteiro tem área livre total no mostrador (sem números na frente do ponteiro).
 * A leitura digital fica posicionada em display dedicado ABAIXO do mostrador.
 */
@Composable
fun CircularGauge(
    value: Float,
    minValue: Float,
    maxValue: Float,
    title: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 135.dp,
    primaryColor: Color = GmAmber,
    redlineStart: Float? = null,
    formatString: String = "%.0f"
) {
    val clampedValue = value.coerceIn(minValue, maxValue)
    val fraction = ((clampedValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 200),
        label = "gauge_fraction"
    )

    val startAngle = 135f
    val sweepAngle = 270f

    Column(
        modifier = modifier.width(size),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Mostrador Circular Analógico (Ponteiro desobstruído com visibilidade total)
        Box(
            modifier = Modifier
                .size(size)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                val strokeWidth = this.size.width * 0.085f
                val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
                val arcTopLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                // 1. Arco de Fundo (Pista do manômetro)
                drawArc(
                    color = CarbonBorder,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 2. Arco de Faixa Vermelha (Redline) se definido
                if (redlineStart != null && redlineStart < maxValue) {
                    val redlineFraction = ((redlineStart - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
                    val redlineStartAngle = startAngle + (sweepAngle * redlineFraction)
                    val redlineSweep = sweepAngle * (1f - redlineFraction)

                    drawArc(
                        color = AstraRedline.copy(alpha = 0.55f),
                        startAngle = redlineStartAngle,
                        sweepAngle = redlineSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // 3. Arco Ativo do Valor Atual
                val activeSweep = sweepAngle * animatedFraction
                val isOverRedline = redlineStart != null && clampedValue >= redlineStart
                val activeColor = if (isOverRedline) AstraRedline else primaryColor

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, activeColor, if (isOverRedline) AstraRedline else GmAmberLight)
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 4. Marcas de Graduação (Ticks)
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val outerRadius = (this.size.width / 2f) - (strokeWidth * 0.6f)
                val tickCount = 8
                for (i in 0..tickCount) {
                    val tickFraction = i.toFloat() / tickCount
                    val angleDeg = startAngle + (sweepAngle * tickFraction)
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val isRedlineTick = redlineStart != null && (minValue + tickFraction * (maxValue - minValue)) >= redlineStart
                    val tickColor = if (isRedlineTick) AstraRedline else CarbonBorder.copy(alpha = 0.8f)
                    val innerTickRadius = outerRadius - if (i % 2 == 0) 6.dp.toPx() else 4.dp.toPx()

                    val tickStart = Offset(
                        x = (center.x + innerTickRadius * cos(angleRad)).toFloat(),
                        y = (center.y + innerTickRadius * sin(angleRad)).toFloat()
                    )
                    val tickEnd = Offset(
                        x = (center.x + outerRadius * cos(angleRad)).toFloat(),
                        y = (center.y + outerRadius * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = tickColor,
                        start = tickStart,
                        end = tickEnd,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 5. Ponteiro de Agulha Esportivo GM (100% visível, nada na frente)
                val needleAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
                val needleLength = (this.size.width / 2f) - (strokeWidth * 0.9f)

                val needleEnd = Offset(
                    x = (center.x + needleLength * cos(needleAngleRad)).toFloat(),
                    y = (center.y + needleLength * sin(needleAngleRad)).toFloat()
                )

                drawLine(
                    color = if (isOverRedline) AstraRedline else GmAmberLight,
                    start = center,
                    end = needleEnd,
                    strokeWidth = strokeWidth * 0.50f,
                    cap = StrokeCap.Round
                )

                // Núcleo central do ponteiro
                drawCircle(
                    color = activeColor,
                    radius = strokeWidth * 0.85f,
                    center = center
                )
                drawCircle(
                    color = Color.Black,
                    radius = strokeWidth * 0.40f,
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Display Digital - MUDADO DE LUGAR PARA ABAIXO DO MOSTRADOR
        // Fica fora do raio do ponteiro, eliminando qualquer sobreposição
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = CarbonElevated,
            border = BorderStroke(1.dp, CarbonBorder),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formatString.format(clampedValue),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }

                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
