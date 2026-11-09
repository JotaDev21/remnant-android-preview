package com.liferpg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlin.math.*

/** Choreography follows the supplied recording: arrival, count, performance, day, CTA.
 * This scene only presents an already awarded day. Gallery demonstrations never write a save. */
@Composable internal fun StreakVictoryScene(days: Int, next: () -> Unit) {
    val timeline = remember(days) { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val milestone = days in listOf(3, 7, 14, 30, 50, 100, 200, 365) || days >= 400 && days % 100 == 0
    LaunchedEffect(days) { timeline.animateTo(6000f, tween(6000, easing = LinearEasing)) }
    LaunchedEffect(days, haptic) {
        delay(850); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(2600); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val ms = timeline.value
    fun phase(start: Float, duration: Float) = ((ms - start) / duration).coerceIn(0f, 1f)
    fun smooth(start: Float, duration: Float) = FastOutSlowInEasing.transform(phase(start, duration))
    val arrival = smooth(80f, 660f)
    val impact = phase(580f, 520f)
    val countVisible = ms >= 760f
    val curl = smooth(1350f, 700f)
    val release = smooth(2450f, 900f)
    val squeeze = sin(phase(1850f, 900f) * PI).toFloat().coerceAtLeast(0f)
    val recoil = if (ms in 2050f..2900f) {
        val p = phase(2050f, 850f)
        sin(p * PI.toFloat() * 2f) * exp(-p * 4f) * .12f
    } else 0f
    val flex = .1f + curl * (1f - release) * .78f + recoil
    val ready = ms >= 5200f
    val today = remember { LocalDate.now() }
    val registered = remember(days, today) {
        (0 until minOf(days, 7)).map { today.minusDays(it.toLong()).toEpochDay() }.toSet()
    }
    val calendar = remember(today, registered) { streakCalendar(today, registered) }
    val heading = when {
        days == 1 -> "Sua sequência nasceu."
        days == 7 -> "Uma semana. Sem quebrar."
        days == 14 -> "Duas semanas de constância."
        days == 30 -> "Um mês. Um novo ritmo."
        milestone -> "Mais um marco conquistado."
        else -> "Mais um dia na sua história."
    }
    BackHandler { if (ready) next() }

    Box(Modifier.fillMaxSize().background(Fundo)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("REMNANT", color = TextoSecundario, fontSize = 11.sp, letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer { alpha = phase(650f, 300f) })
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val markSize = minOf(maxWidth * .58f, maxHeight * .32f, 184.dp)
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // The caption arrives with the performance, before the calendar.
                    Column(Modifier.fillMaxWidth().height(70.dp).graphicsLayer {
                        alpha = phase(1500f, 360f)
                        translationY = (1f - smooth(1500f, 450f)) * 10.dp.toPx()
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.background(TextoPrimario.copy(alpha = .06f), RoundedCornerShape(16.dp))
                            .border(1.dp, TextoPrimario.copy(alpha = .18f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(heading, color = TextoPrimario, fontSize = 15.sp, lineHeight = 21.sp,
                                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        }
                        Canvas(Modifier.size(14.dp, 8.dp)) {
                            val tail = Path().apply {
                                moveTo(0f, 0f); lineTo(size.width / 2f, size.height); lineTo(size.width, 0f)
                            }
                            drawPath(tail, TextoPrimario.copy(alpha = .18f), style = Stroke(1.dp.toPx()))
                        }
                    }
                    Box(Modifier.size(markSize), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            // Uneven curved flecks follow the landing and the contraction.
                            val land = phase(350f, 850f)
                            val pump = phase(1950f, 950f)
                            val burst = if (land < 1f) land else pump
                            val strength = if (land < 1f) sin(land * PI).toFloat() else
                                sin(pump * PI).toFloat() * if (milestone) 1f else .5f
                            if (strength > 0f) repeat(if (milestone) 11 else 7) { i ->
                                val angle = i * 2.39996f
                                val direction = Offset(cos(angle), sin(angle))
                                val point = center + direction * size.minDimension * (.33f + burst * .35f)
                                val tangent = Offset(-direction.y, direction.x)
                                val length = (6f + (i % 3) * 3f).dp.toPx() * (1f - burst * .5f)
                                val path = Path().apply {
                                    moveTo(point.x, point.y)
                                    quadraticTo(point.x + tangent.x * length, point.y + tangent.y * length,
                                        point.x + direction.x * length * .7f, point.y + direction.y * length * .7f)
                                }
                                drawPath(path, TextoPrimario.copy(alpha = strength * .7f),
                                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                        Box(Modifier.graphicsLayer {
                            // Large silhouette settles into the same mark, instead of a static icon fading in.
                            val settle = sin(impact * PI.toFloat()) * exp(-impact * 2f)
                            val scale = 1f + (1f - arrival) * 7f
                            alpha = phase(0f, 80f)
                            scaleX = scale * (1f + settle * .18f + squeeze * .06f)
                            scaleY = scale * (1f - settle * .18f - squeeze * .05f)
                            rotationZ = (1f - arrival) * -32f - curl * (1f - release) * 5f
                            translationY = ((1f - arrival) * 170f + settle * 8f - squeeze * 3f).dp.toPx()
                        }) { GymStreakGlyph(markSize * .9f, flex, pump = squeeze) }
                    }
                    Box(Modifier.height(88.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(countVisible,
                            enter = fadeIn(tween(160)) + slideInVertically(tween(330, easing = FastOutSlowInEasing)) { it / 3 }) {
                            AnimatedContent(if (ms >= 850f) days else (days - 1).coerceAtLeast(0), transitionSpec = {
                                (fadeIn(tween(200)) + slideInVertically(tween(320)) { it / 2 }) togetherWith
                                    (fadeOut(tween(130)) + slideOutVertically(tween(250)) { -it / 2 })
                            }, label = "streakCount") { count ->
                                Text("$count", color = TextoPrimario, fontSize = 78.sp, lineHeight = 86.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = (-3).sp, maxLines = 1)
                            }
                        }
                    }
                    Text(if (days == 1) "dia de sequência" else "dias de sequência", color = TextoSecundario,
                        fontSize = 16.sp, modifier = Modifier.graphicsLayer { alpha = phase(1200f, 260f) })
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).graphicsLayer {
                        alpha = phase(2950f, 350f)
                        translationY = (1f - smooth(2950f, 450f)) * 14.dp.toPx()
                    }) { StreakWeekRow(calendar, TextoPrimario, Fundo, phase(3300f, 650f), celebrateToday = true) }
                    Text(if (days == 1) "É assim que a constância começa." else "Hoje está garantido. Amanhã tem mais.",
                        color = TextoSecundario, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.heightIn(min = 30.dp).graphicsLayer { alpha = phase(4150f, 350f) })
                }
            }
            Box(Modifier.fillMaxWidth().height(54.dp)) {
                androidx.compose.animation.AnimatedVisibility(ready,
                    enter = fadeIn(tween(240)) + slideInVertically(tween(350)) { it / 4 }) {
                    HearthButton("Continuar") { next() }
                }
            }
        }
        // A brief organic wipe across the entire viewport connects the opening to the landing.
        Canvas(Modifier.fillMaxSize()) {
            val sweep = phase(0f, 430f)
            if (sweep < 1f) {
                val fade = (1f - phase(200f, 230f)) * phase(0f, 60f)
                val top = size.height * (1.05f - sweep * 1.8f)
                val wave = Path().apply {
                    moveTo(-size.width * .2f, top + size.height * .15f)
                    cubicTo(size.width * .12f, top - size.height * .4f,
                        size.width * .62f, top + size.height * .3f, size.width * 1.2f, top - size.height * .1f)
                    lineTo(size.width * 1.2f, top + size.height * .8f)
                    cubicTo(size.width * .64f, top + size.height * .5f,
                        size.width * .2f, top + size.height * .8f, -size.width * .2f, top + size.height * .5f)
                    close()
                }
                drawPath(wave, TextoPrimario.copy(alpha = fade))
            }
        }
    }
}
