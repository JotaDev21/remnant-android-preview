package com.liferpg.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liferpg.R
import com.liferpg.data.CameraBossProgress
import com.liferpg.domain.BossDefeat
import com.liferpg.domain.CameraBoss
import com.liferpg.domain.CameraTrialState
import com.liferpg.domain.CameraTrials
import com.liferpg.domain.Patente
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val HuntGold = Color(0xFFE8CB91)
internal val HuntSerif = FontFamily(Font(R.font.cinzel))
internal fun CameraBoss.ink() = if (this == CameraBoss.ASH_WARDEN) Color(0xFFFF9969) else Color(0xFFBAA1EB)
internal fun CameraBoss.art() = if (this == CameraBoss.ASH_WARDEN) R.drawable.boss_ash_warden else R.drawable.boss_stone_colossus
internal fun trialArt(rank: Patente): Int = intArrayOf(
    R.drawable.trial_enemy_01, R.drawable.trial_enemy_02, R.drawable.trial_enemy_03, R.drawable.trial_enemy_04,
    R.drawable.trial_enemy_05, R.drawable.trial_enemy_06, R.drawable.trial_enemy_07, R.drawable.trial_enemy_08,
    R.drawable.trial_enemy_09, R.drawable.trial_enemy_10, R.drawable.trial_enemy_11, R.drawable.trial_enemy_12,
    R.drawable.trial_enemy_13, R.drawable.trial_enemy_14,
)[rank.benchmarkIndex.coerceIn(0, 13)]

/** All effects stay in the HUD. No dialog, pointer handler, or pause signal lives here. */
@Composable internal fun BossCombatScene(
    boss: CameraBoss,
    progress: CameraBossProgress?,
    trial: CameraTrialState,
    repetitions: Int,
    defeat: BossDefeat?,
    modifier: Modifier = Modifier,
    onDefeatFinished: () -> Unit,
) {
    val impact = remember { Animatable(0f) }
    val death = remember { Animatable(0f) }
    val entrance = remember { Animatable(1f) }
    val scar = remember { Animatable(1f) }
    var lastRep by remember { mutableIntStateOf(repetitions) }
    LaunchedEffect(repetitions) {
        if (repetitions > lastRep) {
            impact.snapTo(1f)
            impact.animateTo(0f, tween(420))
        }
        lastRep = repetitions
    }
    LaunchedEffect(defeat) {
        if (defeat == null) {
            death.snapTo(0f)
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        } else {
            death.snapTo(0f)
            death.animateTo(1f, tween(1_900, easing = LinearEasing))
            onDefeatFinished()
        }
    }
    val rank = defeat?.let { Patente.entries[(it.defeatedStage + 1).coerceIn(1, 14)] } ?: trial.rank
    val enemy = CameraTrials.enemy(boss, rank)
    val accent = rank.corBrilho
    val stage = rank.ordinal
    val hp = enemy.hp
    val left = if (defeat != null) 0 else (hp - trial.attemptReps).coerceIn(0, hp)
    val target = left.toFloat() / hp
    val health by animateFloatAsState(target, tween(if (defeat != null) 200 else 240), label = "huntHealth")
    LaunchedEffect(target, stage) {
        if (target < scar.value) delay(340)
        scar.animateTo(target, tween(560))
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            // A smoky pool under the creature leaves the camera unobscured around it.
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = .13f), Color.Transparent),
                center = Offset(size.width * .5f, size.height * .50f), radius = size.height * .8f))
            val hit = impact.value
            if (hit > 0f) {
                val start = Offset(size.width * (.31f - (1f - hit) * .09f), size.height * .64f)
                val end = Offset(size.width * (.70f + (1f - hit) * .09f), size.height * .22f)
                drawLine(accent.copy(alpha = hit * .24f), start, end, 19.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFFFFF3D8).copy(alpha = hit), start, end, 2.dp.toPx(), StrokeCap.Round)
            }
        }
        Image(painterResource(trialArt(rank)), enemy.name,
            Modifier.fillMaxWidth(.65f).fillMaxHeight(.76f).align(Alignment.TopCenter)
                .graphicsLayer {
                    val hit = impact.value
                    translationX = sin((1f - hit) * 23f) * hit * 7.dp.toPx()
                    rotationZ = sin((1f - hit) * 15f) * hit * 3f
                    scaleX = .95f + entrance.value * .05f
                    scaleY = scaleX
                    alpha = entrance.value * (1f - (death.value * 1.8f - .25f).coerceIn(0f, 1f))
                }.drawWithContent {
                    val dissolve = (death.value * 1.8f - .08f).coerceIn(0f, 1f)
                    if (dissolve == 0f) drawContent() else {
                        repeat(18) { strip ->
                            val shift = sin(strip * 2.13f) * dissolve * size.width * .23f
                            clipRect(top = size.height * strip / 18, bottom = size.height * (strip + 1) / 18) {
                                translate(left = shift, top = -dissolve * strip * 1.7.dp.toPx()) { this@drawWithContent.drawContent() }
                            }
                        }
                    }
                }, contentScale = ContentScale.Fit)
        if (defeat != null) {
            BossAshes(accent, death.value, Modifier.fillMaxSize())
            val reveal = (death.value * 7f).coerceIn(0f, 1f)
            val fade = ((1f - death.value) * 5f).coerceIn(0f, 1f)
            Column(Modifier.align(Alignment.Center).fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xE6121014), Color.Transparent)))
                .graphicsLayer { alpha = reveal * fade; translationY = (1f - reveal) * 8.dp.toPx() }
                .padding(vertical = 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (defeat.count == 1) "INIMIGO ABATIDO" else "${defeat.count} INIMIGOS ABATIDOS",
                    color = HuntGold, fontFamily = HuntSerif, fontWeight = FontWeight.Bold,
                    fontSize = 23.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(7.dp))
                Text("VITÓRIA REGISTRADA · ${trial.rank.titulo.uppercase()}",
                    color = Color(0xFFF1E9D7), fontSize = 10.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text("A contagem continua", color = HuntGold.copy(alpha = .8f), fontSize = 11.sp)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(enemy.name, Modifier.weight(1f), color = Color(0xFFF4EAD7), fontFamily = HuntSerif,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text(rank.titulo.uppercase(), color = accent, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFF282128))) {
                LinearProgressIndicator(progress = { scar.value }, Modifier.fillMaxSize(), color = HuntGold.copy(alpha = .75f), trackColor = Color.Transparent, gapSize = 0.dp, drawStopIndicator = {})
                LinearProgressIndicator(progress = { health }, Modifier.fillMaxSize(), color = accent, trackColor = Color.Transparent, gapSize = 0.dp, drawStopIndicator = {})
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (defeat != null) "PRÓXIMO CHEFE EM FORMAÇÃO" else "$left / $hp VIDA", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${progress?.wins ?: 0} VITÓRIAS", color = Color(0xFFCAC1B4), fontSize = 10.sp)
            }
        }
    }
}

@Composable private fun BossAshes(color: Color, elapsed: Float, modifier: Modifier) {
    Canvas(modifier) {
        val t = elapsed.coerceIn(0f, 1f)
        repeat(46) { index ->
            val seed = index * 2.39996f
            val start = Offset(size.width * (.5f + sin(seed) * .20f), size.height * (.42f + cos(seed * .7f) * .22f))
            val travel = t * (20f + index % 11 * 8f).dp.toPx()
            val pos = start + Offset(cos(seed) * travel, -travel * .75f + sin(seed) * travel * .5f)
            val alpha = (sin(t * PI).toFloat() * .85f).coerceIn(0f, 1f)
            drawLine(if (index % 3 == 0) HuntGold.copy(alpha = alpha) else color.copy(alpha = alpha),
                pos, pos + Offset(2.dp.toPx(), -(3 + index % 4).dp.toPx()), (1 + index % 2).dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable internal fun BossFailureVeil(modifier: Modifier = Modifier) {
    val motion = remember { Animatable(0f) }
    LaunchedEffect(Unit) { motion.animateTo(1f, tween(2_150, easing = LinearEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = motion.value
            val opacity = (t * 8f).coerceIn(0f, 1f) * ((1f - t) * 4f).coerceIn(0f, 1f)
            drawRect(Brush.radialGradient(listOf(Color(0xFF761F26).copy(alpha = opacity * .28f), Color.Transparent),
                center = center, radius = size.width * .75f))
            repeat(17) { i ->
                val x = size.width * ((i * .618034f) % 1f)
                val y = size.height * .55f + t * (30 + i % 8 * 12).dp.toPx()
                drawLine(Color(0xFFE98E6C).copy(alpha = opacity * .55f), Offset(x, y), Offset(x + 2.dp.toPx(), y - 4.dp.toPx()), 1.dp.toPx())
            }
        }
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xE6100C0F), Color.Transparent)))
            .graphicsLayer {
                val t = motion.value
                alpha = (t * 8).coerceIn(0f, 1f) * ((1f - t) * 4).coerceIn(0f, 1f)
                scaleX = .98f + t * .04f
                scaleY = scaleX
            }.padding(vertical = 21.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VOCÊ CAIU", color = Color(0xFFE18372), fontFamily = HuntSerif,
                fontSize = 30.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Text("VIDA RESTAURADA · NOVA TENTATIVA PRONTA", color = Color(0xFFC7B9AE), fontSize = 9.sp, letterSpacing = .8.sp)
        }
    }
}

/** Uses the production HUD with simulated reps; no camera, repository, or profile is involved. */
@Composable internal fun BossCombatRehearsal(close: () -> Unit) {
    val boss = CameraBoss.ASH_WARDEN
    var trial by remember { mutableStateOf(CameraTrialState(rank = Patente.DIAMANTE, attemptReps = 48, totalReps = 48, lastRepAt = 1_000)) }
    var defeat by remember { mutableStateOf<BossDefeat?>(null) }
    var fallen by remember { mutableStateOf(false) }
    var replay by remember { mutableIntStateOf(0) }
    LaunchedEffect(replay) {
        fallen = false
        defeat = null
        trial = CameraTrialState(rank = Patente.DIAMANTE, attemptReps = 48, totalReps = 48, lastRepAt = 1_000)
        repeat(5) { i ->
            delay(1_000)
            val next = trial.accept(boss, 49 + i, 2_000L + i * 1000)
            if (next.victories > trial.victories) defeat = BossDefeat(trial.rank.benchmarkIndex, 1, next.victories, next.rank.benchmarkIndex)
            trial = next
        }
        delay(1_300)
        trial = trial.interrupt()
        fallen = true
        delay(2_200)
        fallen = false
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF201824), Color(0xFF07090F))))
        .safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("PROVAÇÕES / DEMONSTRAÇÃO", color = HuntGold, fontSize = 10.sp, letterSpacing = 1.6.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(310.dp), contentAlignment = Alignment.Center) {
            BossCombatScene(boss, CameraBossProgress(boss.name, wins = trial.victories), trial, trial.totalReps, defeat, Modifier.fillMaxSize()) { defeat = null }
            if (fallen) BossFailureVeil(Modifier.fillMaxWidth().height(180.dp))
        }
        Text("${trial.attemptReps} / ${CameraTrials.enemy(boss, trial.rank).hp}", color = Color(0xFFF4EAD7), fontFamily = HuntSerif, fontSize = 50.sp)
        Text("Vitória automática. Próximo chefe. Tentativa reiniciada ao parar.", color = Color(0xFFC9BFB4), fontSize = 13.sp, textAlign = TextAlign.Center)
        Text("Simulação sem câmera e sem alteração no save.", color = HuntGold.copy(alpha = .65f), fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = { replay++ }, modifier = Modifier.weight(1f)) { Text("Rever", color = HuntGold) }
            androidx.compose.material3.Button(onClick = close, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = HuntGold, contentColor = Color(0xFF171116))) { Text("Fechar") }
        }
    }
}
