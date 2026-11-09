package com.liferpg.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.liferpg.ui.theme.DouradoLoot
import com.liferpg.ui.theme.Fundo
import com.liferpg.ui.theme.Neon
import com.liferpg.ui.theme.NeonRoxo
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun FundoVivo(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "fundoVivo")
    val d1 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "d1")
    val d2 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(27000, easing = LinearEasing), RepeatMode.Reverse), label = "d2")
    val d3 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse), label = "d3")
    val subida by t.animateFloat(0f, 1f, infiniteRepeatable(tween(15000, easing = LinearEasing)), label = "subida")
    val cintila by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "cintila")

    val brasas = remember {
        val rnd = Random(7)
        List(26) { Brasa(rnd.nextFloat(), rnd.nextFloat() + 0.45f, rnd.nextFloat() * 6.2832f, rnd.nextFloat() + 0.5f, rnd.nextFloat()) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0D1917), Fundo, Color(0xFF0A1111))))

        fun aurora(fx: Float, fy: Float, cor: Color, raioFrac: Float, alpha: Float) {
            val cx = fx * w
            val cy = fy * h
            val r = raioFrac * maxOf(w, h)
            drawCircle(
                Brush.radialGradient(listOf(cor.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), r),
                r,
                Offset(cx, cy),
            )
        }

        aurora(d1 * 0.18f + 0.2f, d2 * 0.1f + 0.16f, Neon, 0.58f, 0.07f)
        aurora(0.85f - d2 * 0.2f, d3 * 0.12f + 0.34f, NeonRoxo, 0.62f, 0.04f)
        aurora(d3 * 0.12f + 0.52f, 0.88f - d1 * 0.12f, DouradoLoot, 0.5f, 0.07f)

        for (b in brasas) {
            val x = b.x * w
            val y = h - ((subida * b.vel + b.base) % 1f) * h
            val a = ((sin(cintila + b.fase) * 0.5f + 0.5f) * 0.4f + 0.12f) * 0.6f
            drawCircle(
                Color(0xFFBFE3FF).copy(alpha = a.coerceIn(0f, 1f)),
                0.0018f * w * b.tam,
                Offset(x, y),
            )
        }
    }
}

private data class Brasa(
    val x: Float,
    val vel: Float,
    val fase: Float,
    val tam: Float,
    val base: Float,
)
