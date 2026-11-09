package com.liferpg.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AnelProgresso(
    progresso: Float,
    cor: Color,
    corBrilho: Color,
    textoCentro: String,
    modifier: Modifier = Modifier,
    tamanho: Dp = 72.dp,
) {
    val anim by animateFloatAsState(progresso.coerceIn(0f, 1f), tween(900, easing = EaseOutCubic), label = "anel")
    Box(modifier = modifier.size(tamanho), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(tamanho)) {
            val sw = size.minDimension * 0.13f
            val d = size.minDimension - sw
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(d, d)
            drawArc(
                color = corBrilho.copy(alpha = 0.15f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(cor, corBrilho, cor)),
                startAngle = -90f, sweepAngle = anim * 360f, useCenter = false,
                topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        Text(textoCentro, style = MaterialTheme.typography.titleMedium, color = corBrilho, fontWeight = FontWeight.Black)
    }
}
