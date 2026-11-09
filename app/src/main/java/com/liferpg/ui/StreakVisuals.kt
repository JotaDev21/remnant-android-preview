package com.liferpg.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/** One recognizable mark in the home, details and daily celebration. */
@Composable internal fun GymStreakGlyph(size: Dp = 64.dp, flex: Float = 0f,
    ink: Color = TextoPrimario, paper: Color = Fundo, pump: Float = 0f) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 240f, this.size.height / 240f, Offset.Zero) {
            val arm = Path().apply {
                moveTo(38f, 192f)
                cubicTo(18f, 171f, 18f, 130f - pump * 8f, 46f, 116f - pump * 12f)
                cubicTo(71f, 102f - pump * 16f, 99f, 118f - pump * 8f, 105f, 145f)
                cubicTo(119f, 153f, 137f, 148f, 151f, 133f)
                cubicTo(166f, 116f, 193f, 122f, 196f, 143f)
                cubicTo(196f, 183f, 164f, 210f, 119f, 213f)
                cubicTo(85f, 218f, 55f, 212f, 38f, 192f); close()
            }
            drawPath(arm, ink)
            val crease = Path().apply {
                moveTo(49f, 139f - pump * 8f)
                cubicTo(64f, 124f - pump * 12f, 83f, 130f - pump * 8f, 90f, 147f)
            }
            drawPath(crease, paper.copy(alpha = .2f), style = Stroke(5f, cap = StrokeCap.Round))
            // The shoulder stays planted while the elbow lifts the weight.
            rotate(-flex * 54f, Offset(174f, 149f)) {
                drawRoundRect(ink, Offset(150f, 56f), Size(46f, 106f), CornerRadius(23f))
                drawRoundRect(lerp(ink, paper, .24f), Offset(105f, 42f), Size(134f, 13f), CornerRadius(6f))
                drawRoundRect(ink, Offset(115f, 17f), Size(29f, 64f), CornerRadius(12f))
                drawRoundRect(ink, Offset(200f, 17f), Size(29f, 64f), CornerRadius(12f))
                drawRoundRect(lerp(ink, paper, .28f), Offset(106f, 25f), Size(14f, 49f), CornerRadius(7f))
                drawRoundRect(lerp(ink, paper, .28f), Offset(225f, 25f), Size(14f, 49f), CornerRadius(7f))
                drawRoundRect(ink, Offset(154f, 35f), Size(38f, 32f), CornerRadius(12f))
                repeat(3) { i -> drawLine(paper.copy(alpha = .2f), Offset(165f + i * 7f, 38f),
                    Offset(165f + i * 7f, 47f), 2f, StrokeCap.Round) }
            }
        }
    }
}

internal data class StreakCalendarDay(val date: LocalDate, val done: Boolean, val today: Boolean)
internal fun streakCalendar(today: LocalDate, registered: Set<Long>): List<StreakCalendarDay> {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return List(7) { index ->
        val date = monday.plusDays(index.toLong())
        StreakCalendarDay(date, date <= today && date.toEpochDay() in registered, date == today)
    }
}

/** Connected days communicate continuity; gaps and future days never get filled. */
@Composable internal fun StreakWeekRow(days: List<StreakCalendarDay>, ink: Color, paper: Color,
    todayProgress: Float = 1f, celebrateToday: Boolean = false) {
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            val step = size.width / 7f
            val y = size.height - 17.dp.toPx()
            days.zipWithNext().forEachIndexed { index, (left, right) ->
                if (left.done && right.done) {
                    val growth = if (right.today) todayProgress else 1f
                    val start = Offset(step * (index + .5f), y)
                    val end = Offset(step * (index + 1.5f), y)
                    drawLine(ink.copy(alpha = .18f), start, start + (end - start) * growth,
                        9.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val fill by animateFloatAsState(if (day.done) 1f else 0f, tween(350), label = "streakDayFill")
                val phase = if (day.today) todayProgress else 1f
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("pt-BR")).take(3),
                        color = ink.copy(alpha = .6f), fontSize = 10.sp)
                    Box(Modifier.size(34.dp).graphicsLayer {
                        if (day.today && celebrateToday) {
                            val bounce = sin(todayProgress * PI).toFloat()
                            scaleX = 1f + bounce * .24f
                            scaleY = 1f + bounce * .14f
                            translationY = -bounce * 6.dp.toPx()
                        }
                    }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val radius = size.minDimension / 2f - 1.dp.toPx()
                            drawCircle(paper, radius)
                            drawCircle(ink.copy(alpha = if (day.today) 1f else .22f), radius,
                                style = Stroke(if (day.today) 2.dp.toPx() else 1.dp.toPx()))
                            val progress = (fill * phase).coerceIn(0f, 1f)
                            if (progress > 0f) drawCircle(ink, radius * progress)
                            if (progress > .45f) {
                                val check = ((progress - .45f) / .55f).coerceIn(0f, 1f)
                                val a = Offset(size.width * .27f, size.height * .51f)
                                val b = Offset(size.width * .43f, size.height * .68f)
                                val c = Offset(size.width * .74f, size.height * .34f)
                                drawLine(paper, a, a + (b - a) * (check * 2f).coerceIn(0f, 1f),
                                    2.dp.toPx(), StrokeCap.Round)
                                if (check > .5f) drawLine(paper, b, b + (c - b) * (check * 2f - 1f),
                                    2.dp.toPx(), StrokeCap.Round)
                            }
                        }
                        if (!day.done) Text("${day.date.dayOfMonth}", color = ink.copy(alpha = if (day.today) 1f else .6f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
