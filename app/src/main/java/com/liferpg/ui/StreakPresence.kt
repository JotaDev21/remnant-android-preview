package com.liferpg.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.liferpg.data.Perfil
import com.liferpg.domain.StreakDay
import com.liferpg.domain.TrainingStreak
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun StreakPresence(profile: Perfil?, trainedDays: Set<Long>, open: (String) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var details by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) .985f else 1f, tween(140), label = "streakPress")
    val armFlex by animateFloatAsState(if (pressed) .8f else .08f,
        spring(dampingRatio = .72f, stiffness = 420f), label = "streakArmPress")
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = LocalDateTime.now(); delay(1_000) }
        }
    }
    val today = now.toLocalDate()
    val streak = TrainingStreak.of(profile?.streak ?: 0, profile?.melhorStreak ?: 0, profile?.ultimoDiaTreino ?: 0, today.toEpochDay())
    val secured = streak.day == StreakDay.SECURED
    val pending = streak.day == StreakDay.PENDING
    val remaining = Duration.between(now, today.plusDays(1).atStartOfDay()).seconds.coerceAtLeast(0)
    val time = "%02d:%02d:%02d".format(remaining / 3600, remaining / 60 % 60, remaining % 60)
    val status = when (streak.day) {
        StreakDay.SECURED -> "Hoje está garantido."
        StreakDay.PENDING -> "Não deixe sua sequência terminar."
        StreakDay.BROKEN -> "Sua próxima sequência começa hoje."
        StreakDay.NEW -> "Comece sua primeira sequência."
    }
    Column(Modifier.fillMaxWidth().graphicsLayer { scaleX = pressScale; scaleY = pressScale }
        .clip(RoundedCornerShape(18.dp)).background(HearthIvory)
        .clickable(interactionSource = interaction, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            details = true
        }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(secured, animationSpec = tween(240), label = "daySecured") { done ->
                Icon(if (done) Icons.Outlined.Check else Icons.Outlined.FitnessCenter, null, tint = HearthInk, modifier = Modifier.size(22.dp))
            }
            Text("SEQUÊNCIA", Modifier.weight(1f).padding(start = 9.dp), color = HearthInk, fontSize = 11.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Outlined.Info, "Detalhes da sequência", tint = HearthInk, modifier = Modifier.size(19.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GymStreakGlyph(54.dp, armFlex, HearthInk, HearthIvory)
            AnimatedContent(streak.count, transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it / 5 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(240)) { -it / 5 })
            }, label = "streakCount") { count ->
                Text("$count", color = HearthInk, fontSize = 56.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp)
            }
            Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (streak.count == 1) "dia seguido" else "dias seguidos", color = HearthInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(if (pending) "Proteja hoje · $time" else if (secured) "Próximo marco: ${streak.nextMilestone} dias" else "Uma série válida inicia a contagem", color = Color(0xFF555555), fontSize = 11.sp)
            }
        }
        val registered = trainedDays + if (secured) setOf(today.toEpochDay()) else emptySet()
        StreakWeekRow(streakCalendar(today, registered), HearthInk, HearthIvory)
        Crossfade(status, animationSpec = tween(220), label = "streakStatus") {
            Text(it, color = HearthInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (!secured) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HearthInk)
            .clickable { open(RemnantRoute.BOSS_HUNT) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Treinar em casa", color = HearthIvory, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = HearthIvory, modifier = Modifier.size(18.dp))
        }
    }
    if (details) ModalBottomSheet(onDismissRequest = { details = false }, containerColor = HearthInk) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GymStreakGlyph(64.dp)
                Text("Sua sequência", color = HearthIvory, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(status, color = HearthIvory, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("${streak.count}", fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("atual", color = HearthMuted) }
                Column { Text("${streak.best}", fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("seu recorde", color = HearthMuted) }
                Column { Text("${streak.nextMilestone}", fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("próximo marco", color = HearthMuted) }
            }
            HorizontalDivider(color = HearthLine)
            Text("Uma série válida registrada garante o dia. Academia, flexão ou agachamento em casa contam. Abrir o app ou marcar um treino sem registrar séries não conta.", color = HearthMuted, fontSize = 14.sp, lineHeight = 21.sp)
            Text(if (secured) "O dia de hoje já está garantido. A próxima contagem abre à meia-noite." else "O dia vira em $time. Sem uma série válida hoje, a próxima série inicia uma nova sequência. Seu recorde continua guardado.", color = HearthIvory, fontSize = 13.sp, lineHeight = 20.sp)
            HearthButton("Configurar lembrete diário") { details = false; open(RemnantRoute.SETTINGS) }
        }
    }
}
