package com.liferpg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liferpg.R
import com.liferpg.data.ResultadoSerie
import com.liferpg.domain.Patente
import com.liferpg.domain.RecordePessoal
import com.liferpg.domain.MarcaPessoal
import com.liferpg.domain.ModoMedida
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

internal data class Celebration(
    val kind: String, val number: Int = 0, val rank: Patente? = null,
    val previousLevel: Int? = null, val xpGain: Int? = null, val isDemo: Boolean = false,
    val record: RecordePessoal? = null,
)
internal fun celebrationStages(r: ResultadoSerie): List<Celebration> = buildList {
    if (r.novoRecorde && r.recorde != null) add(Celebration("pr", record = r.recorde, xpGain = r.xpGanho))
    if (r.nivelNovo > r.nivelAnterior) add(Celebration("level", r.nivelNovo, previousLevel = r.nivelAnterior, xpGain = r.xpGanho))
    if (r.patenteNova > r.patenteAnterior) add(Celebration("rank", rank = r.patenteNova))
    if (r.primeiroTreinoDoDia && r.streakAtual > 0) add(Celebration("streak", r.streakAtual))
}

@Composable internal fun CelebrationGallery() {
    var demo by remember { mutableStateOf<Celebration?>(null) }
    var bossDemo by remember { mutableStateOf(false) }
    var streakDays by remember { mutableIntStateOf(7) }
    Page("DEMONSTRAÇÃO", "Celebrações") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GymStreakGlyph(150.dp) }
        Text("Experimente as celebrações. Esta demonstração não altera seu progresso.", color = TextoSecundario)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Primeiro dia", streakDays == 1) { streakDays = 1 }
            Choice("Dia comum", streakDays == 4) { streakDays = 4 }
            Choice("Marco semanal", streakDays == 7) { streakDays = 7 }
        }
        Action("Frequência de treino") { demo = Celebration("streak", streakDays) }
        Action("Subida de nível") { demo = Celebration("level", 16, previousLevel = 15, isDemo = true) }
        Action("Recorde pessoal") { demo = Celebration("pr", isDemo = true, record = RecordePessoal(
            "demo", "Supino reto com barra", ModoMedida.CARGA,
            MarcaPessoal(137.5f, 5, null, 2f), MarcaPessoal(142.5f, 5, null, 2.1f),
        )) }
        Action("Nova patente") { demo = Celebration("rank", rank = Patente.DIAMANTE) }
        Action("Ascensão honrável") { demo = Celebration("rank", rank = Patente.IMORTAL) }
        Action("Combate e queda dos chefes") { bossDemo = true }
    }
    demo?.let { CelebrationSequence(listOf(it)) { demo = null } }
    if (bossDemo) Dialog(onDismissRequest = { bossDemo = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BossCombatRehearsal { bossDemo = false }
    }
}

@Composable internal fun CelebrationSequence(stages: List<Celebration>, onFinished: () -> Unit) {
    val host = LocalCelebrationHost.current
    val token = remember(stages) { Any() }
    val finished by rememberUpdatedState(onFinished)
    DisposableEffect(host, token) {
        host.pending.add(CelebrationPresentation(token, stages) { finished() })
        onDispose { host.pending.removeAll { it.token === token } }
    }
}

@Composable internal fun CelebrationScene(stage: Celebration, next: () -> Unit) {
    if (stage.kind == "pr" && stage.record != null) { PersonalRecordScene(stage, next); return }
    if (stage.rank != null) { RankAscensionScene(stage.rank, next); return }
    if (stage.kind == "level") { PortalLevelAscensionScene(stage, next); return }
    StreakVictoryScene(stage.number, next)
}
