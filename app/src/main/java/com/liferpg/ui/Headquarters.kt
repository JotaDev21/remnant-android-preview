package com.liferpg.ui

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.R
import com.liferpg.domain.EstagioFirmamento
import com.liferpg.domain.RankEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Monochrome materials shared by the headquarters, streak and ascension.
internal val HearthInk = com.liferpg.ui.theme.Fundo
internal val HearthSurface = com.liferpg.ui.theme.Superficie
internal val HearthIvory = com.liferpg.ui.theme.TextoPrimario
internal val HearthMuted = com.liferpg.ui.theme.TextoSecundario
internal val HearthBrass = com.liferpg.ui.theme.Neon
internal val HearthLine = com.liferpg.ui.theme.Borda
internal val HearthSerif = FontFamily.SansSerif

@Composable internal fun Headquarters(vm: MainViewModel, open: (String) -> Unit) {
    val profile by vm.perfil.collectAsStateWithLifecycle()
    val plan by vm.treinoHoje.collectAsStateWithLifecycle()
    val session by vm.sessao.collectAsStateWithLifecycle()
    val week by vm.metasSemana.collectAsStateWithLifecycle()
    val trainedDays by vm.diasTreinadosRecentes.collectAsStateWithLifecycle()
    val food by vm.nutricaoHoje.collectAsStateWithLifecycle()
    val targets by vm.metasNutricao.collectAsStateWithLifecycle()
    val weather by vm.clima.collectAsStateWithLifecycle()
    val today by vm.hoje.collectAsStateWithLifecycle()
    val title by vm.tituloEquipado.collectAsStateWithLifecycle()
    val level = RankEngine.nivelDoXp(profile?.xpTotal ?: 0L)
    val stage = EstagioFirmamento.deNivel(level)
    val remaining = RankEngine.xpTotalParaNivel(level + 1) - (profile?.xpTotal ?: 0L)
    val active = session?.takeUnless { it.concluida }
    val exercises = active?.itens?.map { it.item.exercicio } ?: plan.itens.map { it.exercicio }
    val date = LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.forLanguageTag("pt-BR")))

    LazyColumn(
        Modifier.fillMaxSize().background(HearthInk),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("REMNANT", color = HearthIvory, fontFamily = HearthSerif, fontSize = 19.sp, letterSpacing = 3.sp)
                    Text(date.replaceFirstChar { it.uppercase() } + (weather?.let { "  ·  ${it.tempC}°" } ?: ""), color = HearthMuted, fontSize = 11.sp)
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).border(1.dp, HearthLine, RoundedCornerShape(50)).clickable { open(RemnantRoute.SYSTEM) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, "Abrir missões e Sistema", tint = HearthBrass, modifier = Modifier.size(21.dp))
                }
            }
        }
        item { StreakPresence(profile, trainedDays, open) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open(RemnantRoute.tab(4)) }, verticalAlignment = Alignment.CenterVertically) {
                    Insignia(size = 54.dp, stage = stage)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(profile?.nome?.ifBlank { "Caçador" } ?: "Caçador", color = HearthIvory, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(title?.texto ?: stage.titulo, color = HearthMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        HearthLabel("NÍVEL")
                        Text("$level", color = HearthIvory, fontFamily = HearthSerif, fontSize = 29.sp)
                    }
                }
                HearthProgress(RankEngine.progressoNoNivel(profile?.xpTotal ?: 0L))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${stage.titulo} · evolução", color = HearthMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("$remaining XP para o próximo nível", color = HearthMuted, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(HearthSurface)
                .border(1.dp, HearthLine, RoundedCornerShape(14.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(HearthBrass, RoundedCornerShape(50)))
                    Spacer(Modifier.width(8.dp))
                    HearthLabel(if (active == null) "SEU TREINO DE HOJE" else "TREINO EM ANDAMENTO")
                }
                Text(active?.nome ?: plan.titulo, color = HearthIvory, fontFamily = HearthSerif, fontSize = 29.sp, lineHeight = 35.sp)
                Text(active?.let { "${it.seriesFeitas} de ${it.seriesPlanejadas} séries registradas" }
                    ?: "${exercises.size} exercícios  ·  ${plan.volumeTotalSeries} séries", color = HearthMuted, fontSize = 12.sp)
                if (active != null) HearthProgress(active.progresso)
                if (exercises.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        exercises.take(3).forEach { ExerciseThumbnail(it, 58.dp) }
                        if (exercises.size > 3) Text("+${exercises.size - 3}", color = HearthMuted, fontSize = 13.sp)
                    }
                }
                HearthButton(if (active == null) "Ver treino" else "Continuar treino") { open(if (active == null) RemnantRoute.PLAN else RemnantRoute.SESSION) }
            }
        }
        item { JourneyPresence(vm, open) }
        item { CardioPresence(vm.cardio) { open(RemnantRoute.CARDIO) } }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open(RemnantRoute.NUTRITION) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Restaurant, null, tint = HearthBrass, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Nutrição", fontFamily = HearthSerif, color = HearthIvory, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Abrir diário alimentar", tint = HearthMuted, modifier = Modifier.size(18.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${food.calorias.toInt()} kcal registradas", color = HearthIvory, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(if (targets.calorias > 0) "Meta ${targets.calorias}" else "Definir meta", color = HearthMuted, fontSize = 11.sp)
                }
                HearthProgress(if (targets.calorias > 0) food.calorias / targets.calorias else 0f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("P  ${food.proteinaG.toInt()} g", color = HearthMuted, fontSize = 11.sp)
                    Text("C  ${food.carboG.toInt()} g", color = HearthMuted, fontSize = 11.sp)
                    Text("G  ${food.gorduraG.toInt()} g", color = HearthMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable internal fun HearthLabel(text: String) {
    Text(text, color = HearthBrass, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
}

@Composable private fun HearthStat(value: String, suffix: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontFamily = HearthSerif, color = HearthIvory, fontSize = 25.sp)
            if (suffix.isNotBlank()) Text(suffix, color = HearthMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(label, color = HearthMuted, fontSize = 11.sp)
    }
}

@Composable internal fun HearthProgress(value: Float) {
    val progress by animateFloatAsState(value.coerceIn(0f, 1f), tween(550), label = "hearthProgress")
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        drawRoundRect(HearthLine, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
        if (progress > 0f) drawRoundRect(HearthBrass, size = androidx.compose.ui.geometry.Size(size.width * progress, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
    }
}

@Composable internal fun HearthButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().pressMotion(interaction).clip(RoundedCornerShape(8.dp)).background(HearthIvory).clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = HearthInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = HearthInk, modifier = Modifier.size(19.dp))
    }
}
