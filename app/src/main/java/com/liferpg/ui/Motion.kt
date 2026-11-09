package com.liferpg.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.liferpg.data.ResultadoSerie
import com.liferpg.ui.components.FundoVivo
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*

internal class MotionClock(val orbit: State<Float>, val pulse: State<Float>)
internal val LocalMotion = staticCompositionLocalOf<MotionClock> { error("MotionScene required") }

/** One clock shared by visible ornaments; frame reads happen in drawing/layer lambdas. */
@Composable internal fun MotionScene(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "remnantMotion")
    val orbit = transition.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "orbit")
    val pulse = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathing")
    val clock = remember { MotionClock(orbit, pulse) }
    CompositionLocalProvider(LocalMotion provides clock) {
        Box(modifier) {
            Box(Modifier.matchParentSize().background(Fundo))
            content()
        }
    }
}

@Composable internal fun Count(value: Int, suffix: String = "", color: Color = TextoPrimario, fontSize: TextUnit = 12.sp) {
    val count by animateIntAsState(value, tween(430, easing = FastOutSlowInEasing), label = "score")
    Text("$count$suffix", color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
}

@Composable internal fun AnimatedLabel(value: String, color: Color = TextoPrimario, fontSize: TextUnit = 25.sp) {
    AnimatedContent(value, transitionSpec = {
        (fadeIn(tween(170)) + scaleIn(tween(170), initialScale = .985f)) togetherWith fadeOut(tween(110))
    }, label = "valueChange") { Text(it, fontSize = fontSize, color = color, fontWeight = FontWeight.SemiBold) }
}

/** Immediate, non-modal confirmation of a persisted set. Major awards use their own scene. */
@Composable internal fun RewardBurst(result: ResultadoSerie, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(result) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        delay(if (result.experiencia != null) 3200 else 1100)
        visible.targetState = false
        delay(240)
        onFinished()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible, enter = fadeIn(tween(150)) +
            slideInVertically(spring(.85f, 600f)) { -it / 2 }, exit = fadeOut(tween(180)) +
            slideOutVertically(tween(180)) { -it / 4 },
            modifier = Modifier.padding(top = 18.dp, start = 24.dp, end = 24.dp).widthIn(max = 360.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TextoPrimario)
                .padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Icon(if (result.novoRecorde) Icons.Outlined.EmojiEvents else Icons.Outlined.CheckCircle,
                    null, Modifier.size(27.dp).graphicsLayer {
                        scaleX = .8f + progress.value * .2f; scaleY = scaleX
                    }, tint = Fundo)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (result.tipoRegistro == "MISSAO") "Ordem cumprida" else if (result.tipoRegistro == "CARDIO") "Cardio concluído" else if (result.novoRecorde) "Novo recorde" else "Série registrada",
                        color = Fundo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("+${(result.xpGanho * progress.value).roundToInt()} XP",
                        color = Fundo.copy(alpha = .65f), fontSize = 12.sp)
                    result.experiencia?.let { xp -> Text(xp.detalhes(),
                        color = Fundo.copy(alpha = .72f), fontSize = 11.sp, lineHeight = 16.sp) }
                }
            }
        }
    }
}
