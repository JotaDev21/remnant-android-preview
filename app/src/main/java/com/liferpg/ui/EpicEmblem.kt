package com.liferpg.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liferpg.domain.*

@Composable internal fun Insignia(patente: Patente? = null, size: Dp = 96.dp, divisao: Divisao? = null, stage: EstagioFirmamento = EstagioFirmamento.ESTELAR, reveal: Float = 1f) {
    ArtworkRelief(patente, size, divisao, stage, reveal)
}
