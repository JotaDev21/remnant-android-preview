package com.liferpg.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.liferpg.domain.Patente
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

/** Charge -> single impact -> settle. No jump, elastic scale or vertical travel. */
@Composable internal fun RankAscensionScene(rank: Patente, next: () -> Unit) {
    val time = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val honor = rank.ehEpica
    val color = rank.cor
    val impactAt = if (honor) .34f else .27f
    val duration = if (honor) 2600 else 2200
    LaunchedEffect(rank) {
        launch { time.animateTo(1f,tween(duration,easing=LinearEasing)) }
        delay((duration*impactAt).toLong())
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val emblemSize = minOf(maxWidth*.83f,maxHeight*.43f,320.dp)
        val centerY = maxHeight*.44f
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF080705))
            val t = time.value
            val origin = Offset(center.x,size.height*.44f)
            val charge = (t/impactAt).coerceIn(0f,1f)
            val elapsed = ((t-impactAt)/(1f-impactAt)).coerceIn(0f,1f)
            val arrived = t>=impactAt
            val glow = if(arrived) .24f + (1f-elapsed)*.23f else charge*.13f
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha=glow),Color.Transparent),origin,size.width*.72f),size.width*.72f,origin)
            val sealRadius = size.width*.35f
            repeat(if (honor) 3 else 2) { i ->
                val radius = sealRadius + i*14.dp.toPx()
                val opacity = if (arrived) (.36f-elapsed*.2f).coerceAtLeast(.1f) else charge*.18f
                drawArc(
                    Color(0xFFE0C389).copy(alpha=opacity),
                    startAngle=-93f+i*112f,
                    sweepAngle=if (honor) 185f else 125f,
                    useCenter=false,
                    topLeft=Offset(origin.x-radius,origin.y-radius),
                    size=Size(radius*2,radius*2),
                    style=Stroke((.8f+i*.2f).dp.toPx()),
                )
            }
            if (!arrived) {
                // Blades of energy contract into the centre before the emblem strikes.
                repeat(6) { i ->
                    val a = i*2.39996f
                    val dir = Offset(cos(a),sin(a))
                    val distance = size.width*(1.1f-charge*.87f)
                    val tip = origin+dir*distance
                    val tail = origin+dir*(distance+size.width*.35f)
                    drawLine(color.copy(alpha=charge*.9f),tail,tip,(1f+charge*2f).dp.toPx())
                }
            } else {
                val shock = (elapsed*3.4f).coerceIn(0f,1f)
                drawRect(Color.White.copy(alpha=(1f-elapsed*13f).coerceAtLeast(0f)*.24f))
                drawCircle(color.copy(alpha=(1f-shock)*.8f),size.width*(.15f+shock*.85f),origin,style=Stroke((1f-shock)*5.dp.toPx()+1f))
                repeat(if(honor) 72 else 44) { i ->
                    val a = i*2.39996f
                    val speed = .30f+(i%9)*.075f
                    val travel = (elapsed*2f).coerceAtMost(1.5f)
                    val dir = Offset(cos(a),sin(a))
                    val p = origin+dir*size.width*(.16f+travel*speed)+Offset(0f,travel*travel*size.height*.09f)
                    val tail = p-dir*size.width*.025f*(1f-elapsed)
                    val alpha = (1f-elapsed*1.25f).coerceAtLeast(0f)
                    drawLine(if(i%5==0) Color.White.copy(alpha=alpha) else color.copy(alpha=alpha),tail,p,(1+i%3).dp.toPx(),StrokeCap.Round)
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y=centerY-emblemSize/2).size(emblemSize).graphicsLayer {
            val elapsed = ((time.value-impactAt)/.11f).coerceIn(0f,1f)
            val strike = 1f-(1f-elapsed).pow(4)
            alpha = if(time.value<impactAt) 0f else 1f
            scaleX = 1f+(if(honor).55f else .35f)*(1f-strike)
            scaleY = scaleX
            val tremor = if(time.value<impactAt) 0f else (1f-elapsed)
            translationX = sin(elapsed*52f)*tremor*7.dp.toPx()
            translationY = cos(elapsed*43f)*tremor*3.dp.toPx()
        },contentAlignment=Alignment.Center) {
            Insignia(rank,emblemSize)
        }
        Column(Modifier.align(Alignment.TopCenter).offset(y=centerY-emblemSize/2-46.dp).graphicsLayer {
            alpha = ((time.value-impactAt+.04f)*9f).coerceIn(0f,1f)
        },horizontalAlignment=Alignment.CenterHorizontally) {
            Eyebrow(if(honor) "A MARCA HONRÁVEL FOI FORJADA" else "NOVA PATENTE GRAVADA",DouradoLoot)
        }
        Column(Modifier.align(Alignment.TopCenter).offset(y=centerY+emblemSize/2+22.dp).padding(horizontal=22.dp).graphicsLayer {
            val progress = ((time.value-impactAt-.07f)*8f).coerceIn(0f,1f)
            alpha = progress
            translationX = -(1f-progress)*24.dp.toPx()
        },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(rank.titulo.uppercase(),fontSize=39.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,color=if(honor) DouradoLoot else color)
            Text(if(honor) "Sua força atravessou o limite." else "Forjada série após série.",fontSize=14.sp,color=TextoSecundario)
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=26.dp,vertical=28.dp).graphicsLayer {
            alpha = ((time.value-.65f)*5f).coerceIn(0f,1f)
        }) { Action("Continuar",time.value>.68f,next) }
    }
}
