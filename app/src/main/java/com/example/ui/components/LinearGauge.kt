package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AstraCyan
import com.example.ui.theme.AstraRedline
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonCard
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.GmAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Barra Horizontal Esportiva de Telemetria com limites e cores dinâmicas
 */
@Composable
fun LinearGauge(
    title: String,
    value: Float,
    minValue: Float,
    maxValue: Float,
    unit: String,
    modifier: Modifier = Modifier,
    barColor: Color = AstraCyan,
    formatString: String = "%.1f",
    warningThreshold: Float? = null,
    isWarningHigh: Boolean = true
) {
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 200),
        label = "linear_bar_fraction"
    )

    val isWarning = warningThreshold != null && if (isWarningHigh) {
        value >= warningThreshold
    } else {
        value <= warningThreshold
    }

    val activeColor = if (isWarning) AstraRedline else barColor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CarbonCard)
            .border(1.dp, CarbonBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatString.format(value),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isWarning) AstraRedline else TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = activeColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Barra de progresso estilizada
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(CarbonElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(activeColor.copy(alpha = 0.7f), activeColor)
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${minValue.toInt()} $unit",
                fontSize = 10.sp,
                color = TextMuted,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "${maxValue.toInt()} $unit",
                fontSize = 10.sp,
                color = TextMuted,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Card modular de métrica digital com tamanho de quadro padronizado (92dp)
 * e proteção rigorosa contra quebra de linha em números.
 */
@Composable
fun DigitalMetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accentColor: Color = GmAmber,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .height(92.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonCard)
            .border(1.dp, CarbonBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Título fixo em 1 linha sem quebra
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )

        // Número e unidade: garantidos em 1 linha sem quebra
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            val isUnavailable = value.contains("disponível", ignoreCase = true) || value == "N/D"
            Text(
                text = value,
                fontSize = if (value.length > 10) 12.sp else if (value.length > 7) 14.sp else if (value.length > 5) 17.sp else 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isUnavailable) FontFamily.Default else FontFamily.Monospace,
                color = if (isUnavailable) TextMuted else TextPrimary,
                maxLines = 1,
                softWrap = false
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        // Subtítulo ou espaço de reserva para manter o mesmo alinhamento
        Text(
            text = subtitle ?: " ",
            fontSize = 10.sp,
            color = if (subtitle != null) TextMuted else Color.Transparent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
