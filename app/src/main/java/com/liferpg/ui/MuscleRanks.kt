package com.liferpg.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.domain.*
import com.liferpg.ui.theme.*

@Composable internal fun MuscleRanks(vm: MainViewModel, open: (String) -> Unit) {
    val summary by vm.resumoCompetitivo.collectAsStateWithLifecycle()
    val profile by vm.perfil.collectAsStateWithLifecycle()
    val playerStage = EstagioFirmamento.deNivel(RankEngine.nivelDoXp(profile?.xpTotal ?: 0L))
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    val scores by vm.melhoresScores.collectAsStateWithLifecycle()
    Page("PROGRESSO POR MÚSCULO", "Patentes") {
        Row(
            Modifier.fillMaxWidth().border(1.dp, Borda, RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(Superficie, Superficie)), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Insignia(size = 64.dp, stage = playerStage)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("PATENTE DO CAÇADOR", DouradoLoot)
                Text(playerStage.titulo, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("Nível ${RankEngine.nivelDoXp(profile?.xpTotal ?: 0L)} · ${summary.powerScore.toInt()} poder · Temporada ${summary.temporada}", color = TextoSecundario, fontSize = 11.sp)
            }
        }
        Text("Cada músculo guarda as marcas dos exercícios que você registrou. Toque para ver suas patentes e avançar.", color = TextoSecundario, fontSize = 13.sp, lineHeight = 20.sp)
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            summary.ratings.forEach { (muscle, rating) ->
                val honorable = rating.patente.ehEpica
                val accent = TextoPrimario
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(Brush.horizontalGradient(listOf(Fundo, Fundo)))
                        .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = if (honorable) .66f else .25f), accent.copy(alpha = .06f))), shape)
                        .clickable { open(RemnantRoute.muscleRank(muscle.name)) }.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Insignia(rating.patente, if (honorable) 78.dp else 68.dp, rating.divisao)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (honorable) Eyebrow("PATENTE HONRÁVEL", DouradoLoot)
                        Text(muscle.nomeExibicao, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(rating.rotulo, color = accent, fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val registered = catalog.count { it.musculo == muscle && it.benchmarks?.size == 7 && (scores[it.id] ?: 0f) > 0f }
                        Text("$registered ${if (registered == 1) "exercício" else "exercícios"} na média", color = TextoSecundario, fontSize = 11.sp, maxLines = 2)
                    }
                    Icon(Icons.Outlined.ChevronRight, "Registrar desempenho de ${muscle.nomeExibicao}", tint = accent.copy(alpha = .8f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable internal fun MuscleRecord(vm: MainViewModel, muscleId: String, open: (String) -> Unit) {
    val muscle = Musculo.entries.find { it.name == muscleId } ?: return
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    val summary by vm.resumoCompetitivo.collectAsStateWithLifecycle()
    val scores by vm.melhoresScores.collectAsStateWithLifecycle()
    val lastDays by vm.ultimoTreinoPorMusculo.collectAsStateWithLifecycle()
    val today by vm.hoje.collectAsStateWithLifecycle()
    val history by vm.historicoValido.collectAsStateWithLifecycle()
    val memory = remember(muscle, catalog, history) { com.liferpg.domain.MuscleChronicle.build(muscle, catalog, history) }
    val decay = lastDays[muscle]?.let { RankEngine.fatorDecay((today-it).coerceAtLeast(0)) } ?: 1f
    val rating = summary.ratings[muscle]
    val exercises = remember(catalog,muscle,scores) { catalog.filter { it.musculo == muscle }.sortedWith(compareBy<Exercicio> { (scores[it.id] ?: 0f) <= 0f }.thenBy { it.nome }) }
    val counted = exercises.count { it.benchmarks?.size == 7 && (scores[it.id] ?: 0f) > 0f }
    var query by rememberSaveable(muscleId) { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(22.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Header("PATENTE DO MÚSCULO",muscle.nomeExibicao) }
        item { if (rating != null) Row(
            Modifier.fillMaxWidth().border(1.dp, (if (rating.patente.ehEpica) DouradoLoot else Borda).copy(alpha=.4f), RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(Superficie, Superficie)), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)
        ) {
            Insignia(rating.patente,96.dp,rating.divisao)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if (rating.patente.ehEpica) Eyebrow("PATENTE HONRÁVEL",DouradoLoot)
                Text(rating.rotulo,fontSize=23.sp,fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,color=TextoPrimario)
                Text("${rating.rating.toInt()} pontos · $counted exercícios",fontSize=12.sp,color=TextoSecundario)
                Meter(rating.progressoPatente,Neon)
            }
        } }
        item { Text("Cada exercício tem sua patente. A média dos pontos dos exercícios registrados define ${muscle.nomeExibicao.lowercase()}. Os que você ainda não fez ficam fora da conta.",color=TextoSecundario,fontSize=13.sp,lineHeight=20.sp) }
        if (memory.firstDay != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("MEMÓRIA DA CONQUISTA")
                Text("${memory.trainedDays} dias · ${memory.sets} séries válidas · ${memory.exercises} exercícios", color = TextoSecundario, fontSize = 12.sp)
                memory.milestones.takeLast(4).forEach { mark ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Primeira ascensão · ${mark.rank.titulo}", fontSize = 12.sp)
                        Text(java.time.LocalDate.ofEpochDay(mark.day).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")), color = TextoSecundario, fontSize = 11.sp)
                    }
                }
                Text("Marcos históricos permanecem na sua crônica, mesmo após uma pausa. A patente atual continua seguindo a média dos exercícios registrados.", color = TextoSecundario, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
        if (decay < 1f) item { Text("As patentes abaixo incluem o ajuste por tempo sem treinar. Uma nova série válida atualiza a atividade do músculo.",fontSize=12.sp,color=DouradoLoot) }
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Encontrar exercício ou aparelho")},singleLine=true) }
        val filtered = exercises.filter { BuscaExercicios.corresponde(it,query) }
        if(filtered.isEmpty()) item { Text("Nenhum exercício encontrado.",color=TextoSecundario) }
        items(filtered,key={it.id}) { ex ->
            val registered = (scores[ex.id] ?: 0f) > 0f
            val ranked = ex.benchmarks?.size == 7
            val own = RankEngine.ratingMusculo(listOf(ex),scores,decay)
            Column {
                Row(Modifier.fillMaxWidth().clickable { open(RemnantRoute.exercise(ex.id)) }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Insignia(if(registered && ranked) own.patente else Patente.SEM_PATENTE,58.dp,if(registered && ranked)own.divisao else null)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(ex.nome,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text(if(!ranked) "Sem classificação disponível" else if(registered) "${own.rotulo} · ${own.rating.toInt()} pontos" else "Sem patente · não entra na média",fontSize=11.sp,color=if(registered && ranked)TextoPrimario else TextoSecundario)
                        Text(ex.equipamento,fontSize=11.sp,color=TextoSecundario)
                    }
                    Icon(Icons.Outlined.ChevronRight,"Abrir ${ex.nome}",tint=DouradoLoot.copy(alpha=.7f))
                }
                HorizontalDivider(color=DouradoLoot.copy(alpha=.12f))
            }
        }
    }
}
