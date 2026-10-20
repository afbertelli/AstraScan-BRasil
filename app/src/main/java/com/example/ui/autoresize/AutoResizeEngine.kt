package com.example.ui.autoresize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.viewmodel.ScannerViewModel

/**
 * MOTOR DE AUTO-RESIZE E CALIBRAÇÃO DE TELA
 *
 * Garante fidelidade visual 1:1 entre qualquer celular físico Android e o
 * emulador de desenvolvimento (base de referência: 392.7 dp a 411.4 dp).
 *
 * Como funciona:
 * 1. Calcula a razão exata: (larguraRealDp / larguraReferenciaDp).
 * 2. Ajusta a densidade do Compose (LocalDensity) proporcionalmente.
 * 3. O Compose passa a renderizar a tela com espaço efetivo igual ao emulador,
 *    eliminando quebras de linha em botões, cartões desalinhados ou elementos embolados.
 * 4. Estabiliza a escala de fonte (fontScale = 1.0f) para evitar que configurações de
 *    acessibilidade com fontes gigantes do Android desconfigurem mostradores técnicos.
 */
object AutoResizeConfig {
    const val DEFAULT_REFERENCE_WIDTH_DP = 392.7f
    const val PIXEL_REFERENCE_WIDTH_DP = 411.4f
    const val MIN_SCALE = 0.65f
    const val MAX_SCALE = 1.45f
}

@Composable
fun AutoResizeProvider(
    viewModel: ScannerViewModel,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val isAutoResizeEnabled by viewModel.isAutoResizeEnabled.collectAsState()
    val fineTuneFactor by viewModel.autoResizeFineTune.collectAsState()
    val baseWidthDp by viewModel.autoResizeBaseWidth.collectAsState()

    val currentWidthDp = configuration.screenWidthDp.toFloat()

    // Fator de proporção baseado na largura de referência do emulador
    val calculatedScaleFactor = remember(currentWidthDp, isAutoResizeEnabled, fineTuneFactor, baseDensity, baseWidthDp) {
        if (!isAutoResizeEnabled) {
            1.0f
        } else {
            val refWidth = if (baseWidthDp > 0f) baseWidthDp else AutoResizeConfig.DEFAULT_REFERENCE_WIDTH_DP
            // Em tablets (> 600dp), limita para não esticar excessivamente
            val targetWidth = if (currentWidthDp >= 600f) refWidth else currentWidthDp
            val ratio = targetWidth / refWidth
            (ratio * fineTuneFactor).coerceIn(AutoResizeConfig.MIN_SCALE, AutoResizeConfig.MAX_SCALE)
        }
    }

    val scaledDensity = remember(baseDensity, calculatedScaleFactor, isAutoResizeEnabled) {
        if (!isAutoResizeEnabled) {
            baseDensity
        } else {
            Density(
                density = baseDensity.density * calculatedScaleFactor,
                fontScale = 1.0f // Trava proporção 1:1 fiel ao emulador
            )
        }
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity
    ) {
        content()
    }
}
