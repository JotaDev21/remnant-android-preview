package com.liferpg.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GraficoEvolucao(
    valores: List<Float>,
    cor: Color,
    corBrilho: Color,
    modifier: Modifier = Modifier,
    altura: Dp = 120.dp,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(valores) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(1100, easing = EaseOutCubic))
    }
    val t = rememberInfiniteTransition(label = "grafico")
    val pulso by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "pulso")

    val minV = valores.minOrNull() ?: 0f
    val maxV = valores.maxOrNull() ?: 1f
    val span = (maxV - minV).takeIf { it > 0f } ?: 1f

    Canvas(modifier.fillMaxWidth().height(altura)) {
        val w = size.width
        val h = size.height
        val padV = h * 0.12f
        val n = valores.size
        val pontos = List(n) { i ->
            Offset(i.toFloat() / (n - 1) * w, (h - padV) - ((valores[i] - minV) / span) * (h - 2f * padV))
        }
        val linha = Path().apply {
            moveTo(pontos[0].x, pontos[0].y)
            for (i in 1 until n) lineTo(pontos[i].x, pontos[i].y)
        }
        val area = Path().apply {
            addPath(linha)
            lineTo(pontos.last().x, h)
            lineTo(pontos.first().x, h)
            close()
        }
        clipRect(right = (anim.value * w).coerceAtLeast(1f)) {
            drawLine(corBrilho.copy(alpha = 0.1f), Offset(0f, h - padV), Offset(w, h - padV), 1f)
            drawPath(area, Brush.verticalGradient(listOf(cor.copy(alpha = 0.45f), cor.copy(alpha = 0f)), 0f, h))
            drawPath(linha, corBrilho.copy(alpha = 0.25f), style = Stroke(9f, cap = StrokeCap.Round))
            drawPath(linha, corBrilho, style = Stroke(3f, cap = StrokeCap.Round))
        }
        val fim = pontos.last()
        if (anim.value * w >= fim.x - 2f) {
            drawCircle(corBrilho.copy(alpha = pulso * 0.25f + 0.25f), pulso * 6f + 6f, fim)
            drawCircle(Color.White, 4f, fim)
        }
    }
}
