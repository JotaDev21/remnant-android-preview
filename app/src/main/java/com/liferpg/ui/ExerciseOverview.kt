package com.liferpg.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.liferpg.MainViewModel
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun rankTargetLabel(mode: ModoMedida, threshold: Float, bodyWeight: Float): String = when(mode) {
    ModoMedida.CARGA -> String.format(Locale.forLanguageTag("pt-BR"),"%.2f × peso corporal",threshold) + if(bodyWeight>0) " · ${Format.peso(threshold*bodyWeight)} kg de 1RM" else ""
    ModoMedida.REPS -> "${Format.peso(threshold)} repetições"
    ModoMedida.TEMPO -> "${Format.peso(threshold)} segundos"
}

@Composable internal fun ExerciseOverview(vm: MainViewModel, ex: Exercicio, open: (String) -> Unit) {
    val scores by vm.melhoresScores.collectAsStateWithLifecycle()
    val profile by vm.perfil.collectAsStateWithLifecycle()
    val flow=remember(ex.id) { vm.seriesDoExercicio(ex.id) }
    val history by flow.collectAsStateWithLifecycle(initialValue=emptyList())
    val own=RankEngine.ratingMusculo(listOf(ex),scores)
    val recorded=(scores[ex.id] ?: 0f)>0f
    val thresholds=remember(ex.benchmarks) { ex.benchmarks?.takeIf { it.size==7 }?.let(RankEngine::limiaresCompletos).orEmpty() }
    var allRanks by rememberSaveable(ex.id) { mutableStateOf(false) }
    var videoError by remember { mutableStateOf(false) }
    val context=LocalContext.current
    Page("${ex.musculo.nomeExibicao} / ${ex.local.nomeExibicao}",ex.nome) {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(ex.equipamento,ex.musculo.nomeExibicao,ex.local.nomeExibicao).forEach { label ->
                Text(label,Modifier.clip(RoundedCornerShape(8.dp)).background(SuperficieAlta).padding(horizontal=10.dp,vertical=6.dp),fontSize=11.sp,color=TextoSecundario)
            }
        }
        if(ex.imagens.isNotEmpty()) {
            val pager=rememberPagerState { ex.imagens.size }
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(20.dp)).background(Superficie)) {
                HorizontalPager(pager,Modifier.fillMaxSize()) { i ->
                    SubcomposeAsyncImage("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/${ex.imagens[i]}",
                        "Demonstração de ${ex.nome}, posição ${i+1}",Modifier.fillMaxSize(),contentScale=ContentScale.Fit,
                        loading={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(24.dp))}},
                        error={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Demonstração indisponível agora",color=TextoSecundario,fontSize=12.sp)}})
                }
                Text("DEMONSTRAÇÃO  ${pager.currentPage+1}/${ex.imagens.size}",Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha=.7f)).padding(8.dp),fontSize=10.sp,color=Color.White)
            }
        }
        OutlinedButton(onClick={
            val query=ex.videoQuery.ifBlank { ex.nome+" execução" }
            try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/results?search_query="+Uri.encode(query)))) ;videoError=false }
            catch (_: Exception) { videoError=true }
        },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.PlayArrow,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Ver execução no YouTube")
        }
        if(videoError) Text("Não consegui abrir o vídeo neste aparelho.",color=TextoSecundario)
        if(ex.suportaContadorDeFlexao || ex.id == com.liferpg.domain.CameraBoss.STONE_COLOSSUS.exerciseId) Action("Contar e validar com a câmera") { open(RemnantRoute.camera(ex.id)) }
        Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Insignia(if(recorded)own.patente else Patente.SEM_PATENTE,90.dp,own.divisao)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Eyebrow("Sua marca",TextoSecundario)
                Text(if(recorded)own.rotulo else "Sem patente",fontSize=25.sp,fontWeight=FontWeight.Bold,color=if(recorded)own.patente.cor else TextoPrimario)
                Text(if(recorded) Format.score(ex.modo,scores[ex.id] ?: 0f,profile?.pesoCorporalKg) else "Registre seu desempenho para conquistar a primeira.",fontSize=12.sp,color=TextoSecundario)
                if(recorded) Meter(own.progressoPatente,own.patente.cor)
            }
        }
        Section("Registrar desempenho")
        SetForm(vm,ex,restControls=true)
        if(thresholds.isNotEmpty()) {
            val next=Patente.entries.getOrNull(own.patente.ordinal+1)
            Section("Seu próximo avanço",if(allRanks) "Recolher tabela" else "Todas as patentes") { allRanks=!allRanks }
            if(next!=null) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Insignia(next,58.dp)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(next.titulo,fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=next.cor)
                    Text(rankTargetLabel(ex.modo,thresholds[next.benchmarkIndex],profile?.pesoCorporalKg ?: 0f),fontSize=12.sp,color=TextoSecundario)
                }
            } else Text("Imortal. Você alcançou a patente máxima deste exercício.",color=DouradoLoot)
            if(ex.modo==ModoMedida.CARGA) Text("Os kg da tabela são de 1RM estimado, calculado com carga e repetições. Não é necessário levantar esse peso em uma única repetição.",fontSize=11.sp,color=TextoSecundario,lineHeight=17.sp)
            AnimatedVisibility(allRanks) {
                Column {
                    Patente.ranqueadas.forEach { rank ->
                        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Insignia(rank,42.dp)
                            Column(Modifier.weight(1f)) {
                                Text(rank.titulo+if(recorded && rank==own.patente) " · ATUAL" else "",color=rank.cor,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                                Text(rankTargetLabel(ex.modo,thresholds[rank.benchmarkIndex],profile?.pesoCorporalKg ?: 0f),fontSize=11.sp,color=TextoSecundario)
                            }
                        }
                        HorizontalDivider(color=Borda.copy(alpha=.45f))
                    }
                }
            }
        }
        Section("Como executar")
        if(ex.instrucoes.isEmpty()) Text("Este exercício ainda não tem instruções no catálogo.",color=TextoSecundario,fontSize=13.sp)
        ex.instrucoes.forEachIndexed { i,step ->
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("%02d".format(i+1),fontSize=12.sp,fontWeight=FontWeight.Bold,color=Neon,modifier=Modifier.padding(top=3.dp))
                Text(step,Modifier.weight(1f),fontSize=14.sp,lineHeight=22.sp,color=TextoPrimario)
            }
        }
        if(ex.dica.isNotBlank()) Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(DouradoLoot.copy(alpha=.12f),Color.Transparent))).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Eyebrow("Detalhe que faz diferença",DouradoLoot)
            Text(ex.dica,fontSize=13.sp,lineHeight=21.sp)
        }
        Section("Últimos registros")
        ExerciseHistoryLog(history,ex,open)
    }
}
