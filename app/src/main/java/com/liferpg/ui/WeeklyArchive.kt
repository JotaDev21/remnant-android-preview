package com.liferpg.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable internal fun WeeklyArchivePanel(weeks: List<WeeklySnapshot>, catalog: List<Exercicio>) {
    if (weeks.isEmpty()) return
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val index = selected.coerceIn(0, minOf(7, weeks.lastIndex))
    val snapshot = weeks[index]
    val previous = weeks.getOrNull(index + 1)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        weeks.take(8).forEachIndexed { i, item -> Choice(when (i) { 0 -> "Esta semana"; 1 -> "Anterior"; else -> archiveDate(item.review.firstDay) }, i == index) { selected = i; expanded = false } }
    }
    Panel {
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow("${archiveDate(snapshot.review.firstDay)} — ${archiveDate(snapshot.lastDay)} / ${if (snapshot.closed) "SEMANA ENCERRADA" else "EM ANDAMENTO"}")
            AnimatedContent(snapshot.review.firstDay, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(100)) }, label = "weeklyArchive") { day ->
                val visible = weeks.firstOrNull { it.review.firstDay == day } ?: snapshot
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (visible.closed) "${visible.review.trainedDays} ${if (visible.review.trainedDays == 1) "dia" else "dias"} de treino. ${visible.exercises} ${if (visible.exercises == 1) "exercício" else "exercícios"} no seu histórico."
                        else visible.review.message, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ArchiveStat(visible.review.trainedDays, "DIAS")
                        ArchiveStat(visible.review.sets, "SÉRIES")
                        ArchiveStat(visible.exercises, "EXERCÍCIOS")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (0..6).forEach { n ->
                            val epoch = day + n
                            val recorded = epoch in visible.days
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier.semantics { contentDescription = "${archiveDate(epoch)}: ${if (recorded) "treino registrado" else "sem registro"}" }) {
                                Text(listOf("S", "T", "Q", "Q", "S", "S", "D")[n], fontSize = 10.sp, color = TextoSecundario)
                                Box(Modifier.size(26.dp).clip(CircleShape).background(if (recorded) TextoPrimario else SuperficieAlta), contentAlignment = Alignment.Center) {
                                    if (recorded) Text("✓", color = Fundo, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            previous?.let {
                Text("Semana anterior · ${it.review.trainedDays} dias e ${it.review.sets} séries. ${if (snapshot.closed) "" else "A semana atual ainda está em andamento."}", fontSize = 11.sp, color = TextoSecundario, lineHeight = 17.sp)
            }
            if (snapshot.review.improvements.isNotEmpty()) {
                HorizontalDivider(color = Borda)
                Eyebrow("MARCAS EM EVOLUÇÃO")
                snapshot.review.improvements.take(if (expanded) 1000 else 4).forEach { (id, pct) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(catalog.firstOrNull { it.id == id }?.nome ?: id, Modifier.weight(1f), fontSize = 12.sp)
                        Text("+${Format.peso(pct)}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (snapshot.muscles.isNotEmpty()) {
                HorizontalDivider(color = Borda)
                Eyebrow("SÉRIES REGISTRADAS / GRUPOS MUSCULARES")
                snapshot.muscles.entries.take(if (expanded) 30 else 3).forEach { (muscle, count) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Musculo.entries.firstOrNull { it.name == muscle }?.nomeExibicao ?: muscle, fontSize = 12.sp, color = TextoSecundario)
                        Text("$count ${if (count == 1) "série" else "séries"}", fontSize = 12.sp)
                    }
                }
            }
            if (snapshot.review.improvements.size > 4 || snapshot.muscles.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Mostrar menos" else "Ver retrospectiva completa") }
            Text("Percentuais comparam desempenho registrado à sessão anterior à semana, no mesmo equipamento. Cargas usam força estimada; isso não mede massa muscular. Os grupos mostram séries registradas, sem prescrever volume ou recuperação.", fontSize = 10.sp, lineHeight = 16.sp, color = TextoSecundario)
        }
    }
}

@Composable private fun ArchiveStat(value: Int, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AnimatedLabel(value.toString(), fontSize = 28.sp)
        Text(label, fontSize = 9.sp, color = TextoSecundario)
    }
}
private fun archiveDate(day: Long) = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM"))
