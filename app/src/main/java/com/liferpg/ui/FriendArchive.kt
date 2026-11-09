package com.liferpg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liferpg.data.sync.*
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel

@Composable internal fun PersonalRecords(vm: MainViewModel) {
    val records by vm.meusRecordes.collectAsStateWithLifecycle()
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = records.filter { (muscle==null || it.musculo==muscle) && recordMatches(it,query,catalog) }.sortedBy { it.nome }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Header("ARQUIVO DE FORÇA","Seus recordes.") }
        item { Text("${records.size} exercícios registrados. Carga real, repetições e data do PR, separados da pontuação da patente.",color=TextoSecundario,fontSize=13.sp,lineHeight=19.sp) }
        item { OutlinedTextField(query,{query=it},label={Text("Buscar exercício")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Choice("Todos",muscle==null) {muscle=null}
            records.map {it.musculo}.distinct().sorted().forEach {m-> Choice(m,muscle==m) {muscle=m} }
        } }
        if(visible.isEmpty()) item {Text("Nenhum registro neste filtro. Registre suas séries no Arsenal ou durante uma sessão.",color=TextoSecundario,fontSize=13.sp)}
        items(visible,key={it.exercicioId}) { record ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SuperficieAlta).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    catalog.firstOrNull {it.id==record.exercicioId}?.let {ExerciseThumbnail(it,64.dp)}
                    Column(Modifier.weight(1f)) {Text(record.nome,fontWeight=FontWeight.Bold);Text(record.musculo,fontSize=11.sp,color=TextoSecundario)}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    RecordValue("PR REGISTRADO",record,Neon)
                    Column(horizontalAlignment=Alignment.End) {Text(record.patente,color=DouradoLoot,fontWeight=FontWeight.Bold);Text(recordDate(record.dia),fontSize=11.sp,color=TextoSecundario)}
                }
                Text("${record.series} séries no histórico · a patente considera sua melhor pontuação.",color=TextoSecundario,fontSize=11.sp,lineHeight=16.sp)
            }
        }
    }
}

internal fun perfilCor(style: String): Color = when (style) {
    "OURO" -> Color(0xFFE6C78B); "VIOLETA" -> Color(0xFFB5A0CD); "RUBRO" -> Color(0xFFE19A89)
    "GEADA" -> Color(0xFF9DCAD9); "ESMERALDA" -> Color(0xFFA2C9AC); else -> Color(0xFFABB4AB)
}

private val SwordSeal = ImageVector.Builder("SeloEspada",24.dp,24.dp,24f,24f).apply {
    path(fill=SolidColor(Color.White)) {
        moveTo(19f,2f);lineTo(22f,2f);lineTo(22f,5f);lineTo(12f,15f);lineTo(9f,12f);close()
        moveTo(8f,10f);lineTo(14f,16f);lineTo(12.5f,17.5f);lineTo(6.5f,11.5f);close()
        moveTo(8f,14f);lineTo(10f,16f);lineTo(5f,21f);lineTo(3f,19f);close()
        moveTo(2f,18f);lineTo(6f,22f);lineTo(4f,24f);lineTo(0f,20f);close()
    }
}.build()
private val CrownSeal = ImageVector.Builder("SeloCoroa",24.dp,24.dp,24f,24f).apply {
    path(fill=SolidColor(Color.White)) {
        moveTo(2f,7f);lineTo(7f,11f);lineTo(12f,4f);lineTo(17f,11f);lineTo(22f,7f);lineTo(20f,18f);lineTo(4f,18f);close()
        moveTo(4f,20f);lineTo(20f,20f);lineTo(20f,22f);lineTo(4f,22f);close()
    }
}.build()

@Composable internal fun ProfileSeal(selo: String, color: Color, modifier: Modifier = Modifier) {
    val icon = when(selo) {
        "LOBO" -> Icons.Outlined.Pets; "COROA" -> CrownSeal
        "CHAMA" -> Icons.Outlined.Whatshot; "ESCUDO" -> Icons.Outlined.Shield
        "LUA" -> Icons.Outlined.DarkMode; else -> SwordSeal
    }
    Box(modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha=.1f))
        .border(1.dp, color.copy(alpha=.4f), RoundedCornerShape(14.dp)), contentAlignment=Alignment.Center) {
        Icon(icon, IdentidadePerfil.selos[selo] ?: "Selo", Modifier.size(24.dp), tint=color)
    }
}

@Composable internal fun IdentityRibbon(identity: IdentidadeAmigo) {
    val color = perfilCor(identity.moldura)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Brush.horizontalGradient(listOf(color.copy(alpha=.16f), Color(0xFF171915))))
        .border(1.dp,color.copy(alpha=.3f),RoundedCornerShape(20.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ProfileSeal(identity.selo,color)
            Column(Modifier.weight(1f)) {
                Text(IdentidadePerfil.focos[identity.foco] ?: "Equilíbrio",color=color,fontWeight=FontWeight.Bold,fontSize=17.sp)
                Text(ClassePersonagem.entries.firstOrNull { it.name==identity.classe }?.titulo ?: "Crônica de aliado",color=TextoSecundario,fontSize=11.sp)
            }
            Eyebrow("ALIADO",color)
        }
        if(identity.lema.isNotBlank()) Text("“${identity.lema}”",color=color,fontSize=13.sp)
        if(identity.biografia.isNotBlank()) Text(identity.biografia,color=TextoSecundario,fontSize=12.sp,lineHeight=18.sp)
    }
}

@Composable internal fun FriendArchive(profile: PerfilAmigo, myLevel: Int, myStreak: Int, myMuscleRanks: Map<String,String>,
    mine: List<RecordeAmigo>, catalog: List<Exercicio>, onCopy: (RotinaAmigo) -> Unit, myWeek: WeeklyReview) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedRoutine by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSession by rememberSaveable { mutableStateOf<String?>(null) }
    val confronts = remember(mine,profile.records) { CompeticaoAmigos.comparar(mine,profile.records) }
    val recommendation = remember(profile.routines,confronts) {
        val targets = confronts.filter { it.lideranca==LiderancaExercicio.ALIADO }.map { it.id }.toSet()
        profile.routines.filter { it.itens.isNotEmpty() }.maxByOrNull { routine -> routine.itens.count { it.exercicioId in targets } }
    }
    Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
        IdentityRibbon(profile.identity)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Crônica","Rivalidade","Treinos").forEachIndexed { i,title -> Choice(title,tab==i) { tab=i } }
        }
        when(tab) {
            0 -> {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Stat("$myLevel × ${profile.level}","NÍVEL · VOCÊ / ALIADO")
                    Stat("$myStreak × ${profile.streak}","SEQUÊNCIA · DIAS")
                }
                Text("${profile.totalSessions} sessões · ${profile.totalSets} séries · melhor sequência: ${profile.bestStreak} dias",color=TextoSecundario,fontSize=12.sp,lineHeight=18.sp)
                if(profile.records.isNotEmpty()) {
                    Panel {
                        Eyebrow("${profile.records.size} EXERCÍCIOS COM PR",DouradoLoot)
                        Text("Extensora, leg press, agachamentos e cada variação registrada. Veja quem lidera em cada exercício.",color=TextoSecundario,fontSize=12.sp,lineHeight=18.sp)
                        TextButton(onClick={tab=1}) { Text("Abrir comparação de força →",color=DouradoLoot) }
                    }
                }
                if(recommendation!=null) Panel {
                    Eyebrow("TREINO DO ALIADO PARA EXPLORAR",NeonRoxo)
                    Text(recommendation.nome,fontWeight=FontWeight.Bold)
                    Text(if(confronts.any { it.lideranca==LiderancaExercicio.ALIADO && recommendation.itens.any { d->d.exercicioId==it.id } })
                        "Inclui exercícios nos quais esse aliado está à frente. Explore a rotina e ajuste ao seu objetivo."
                        else "Uma rotina compartilhada para você conhecer. Revise séries, descanso e exercícios antes de adotar.",color=TextoSecundario,fontSize=12.sp,lineHeight=18.sp)
                    TextButton(onClick={selectedRoutine=recommendation.nome;tab=2}) { Text("Conhecer esse treino →",color=NeonRoxo) }
                }
                Eyebrow("PATENTES MUSCULARES",DouradoLoot)
                if(profile.muscleRanks.isEmpty()) Text("Nenhuma patente muscular publicada ainda.",color=TextoSecundario,fontSize=12.sp)
                profile.muscleRanks.forEach { rank ->
                    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(rank.musculo,Modifier.weight(1f),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                        Column(horizontalAlignment=Alignment.End) {
                            Text("Você · ${myMuscleRanks[rank.musculo] ?: "Sem patente"}",fontSize=10.sp,color=TextoSecundario)
                            Text("Aliado · ${rank.patente}",fontSize=12.sp,color=DouradoLoot,fontWeight=FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color=Borda.copy(alpha=.4f))
                }
                Eyebrow("MEMÓRIA DE TREINO",Neon)
                if(profile.recentWorkouts.isEmpty()) Text("Nenhuma sessão compartilhada ainda.",color=TextoSecundario,fontSize=12.sp)
                profile.recentWorkouts.forEachIndexed { index, w ->
                    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(w.nome,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                            Text(recordDate(w.dia),color=Neon,fontSize=11.sp)
                        }
                        Text("${w.series} séries · ${w.exercicios} exercícios · ${w.recordes} PRs · ${w.duracaoSeg/60} min",fontSize=11.sp,color=TextoSecundario)
                    }
                    if(w.itens.isNotEmpty()) {
                        val key = "${w.dia}:$index"
                        val routine = RotinaAmigo(w.nome, w.itens.mapNotNull { d -> catalog.firstOrNull { it.id==d.exercicioId }?.nome },emptyList(),w.itens)
                        SharedRoutine(routine,catalog,selectedSession==key,{selectedSession=if(selectedSession==key)null else key},{onCopy(routine)},heading="Plano desta sessão")
                    }
                    HorizontalDivider(color=Borda.copy(alpha=.4f))
                }
            }
            1 -> {
                profile.evolution?.let { evolution -> FriendEvolutionPanel(evolution, myWeek, catalog) }
                if(profile.records.isEmpty()) Text("Ainda não há PRs compartilhados por este aliado. Se ele já possui registros, precisa abrir a versão nova e sincronizar a Arena.",color=TextoSecundario,fontSize=12.sp,lineHeight=18.sp)
                ExerciseRivalry(confronts,catalog)
            }
            else -> {
                Eyebrow("ARSENAL COMPARTILHADO",NeonRoxo)
                Text("Copiar cria uma rotina sua, com séries, repetições e descanso. As cargas e os PRs continuam sendo seus.",color=TextoSecundario,fontSize=12.sp,lineHeight=18.sp)
                if(profile.routines.isEmpty()) Text("Seu aliado ainda não compartilhou rotinas.",color=TextoSecundario,fontSize=13.sp)
                profile.routines.forEach { routine ->
                    SharedRoutine(routine,catalog,selectedRoutine==routine.nome,{ selectedRoutine=if(selectedRoutine==routine.nome)null else routine.nome },{onCopy(routine)})
                }
            }
        }
        if(profile.updatedAt.isNotBlank()) Text("Atualizado ${runCatching { OffsetDateTime.parse(profile.updatedAt).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm")) }.getOrDefault("recentemente")}. O aliado compartilha ao abrir o app e registrar seu progresso.",color=TextoSecundario,fontSize=10.sp,lineHeight=15.sp)
    }
}

@Composable internal fun FriendEvolutionPanel(evolution: FriendEvolution, myWeek: WeeklyReview, catalog: List<Exercicio>) {
    val rivalry = WeeklyCompetition.compare(myWeek, evolution.week, evolution.days, evolution.goal)
    val mine = myWeek.improvements.toMap()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("CONSTÂNCIA / CADA UM NO SEU COMPROMISSO")
        Text("Semana de ${recordDate(evolution.week)}", color = TextoSecundario, fontSize = 11.sp)
        if (rivalry.comparable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Você", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${myWeek.trainedDays}/${myWeek.goal} dias · ${(rivalry.mine * 100).toInt()}%", fontSize = 11.sp, color = TextoSecundario)
                    HearthProgress(rivalry.mine)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aliado", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${evolution.days}/${evolution.goal} dias · ${(rivalry.ally * 100).toInt()}%", fontSize = 11.sp, color = TextoSecundario)
                    HearthProgress(rivalry.ally)
                }
            }
            Text(when (rivalry.lead) {
                CommitmentLead.YOU -> "Você está à frente no compromisso semanal."
                CommitmentLead.ALLY -> "Seu aliado está à frente no compromisso semanal."
                else -> if (rivalry.mine == 0f) "A semana está aberta para os dois." else "Vocês estão empatados no compromisso."
            }, fontSize = 12.sp)
            Text("Compare o percentual da meta de cada um. Dias além da meta não aumentam a pontuação; a sequência diária permanece separada.", fontSize = 10.sp, color = TextoSecundario, lineHeight = 16.sp)
        } else {
            Text("${evolution.days}/${evolution.goal} dias publicados pelo aliado.", fontSize = 13.sp)
            Text("Seu aliado ainda precisa sincronizar esta semana. Semanas diferentes não entram no confronto.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
        }
        evolution.campaignName?.let { Text("$it · ${evolution.chapters}/${evolution.weeks} capítulos", color = TextoSecundario, fontSize = 12.sp) }
        val ids = (evolution.improvements.keys + if (rivalry.comparable) mine.keys else emptySet()).distinct()
            .sortedByDescending { maxOf(evolution.improvements[it] ?: 0f, if (rivalry.comparable) mine[it] ?: 0f else 0f) }
        if (ids.isNotEmpty()) {
            Eyebrow("EVOLUÇÃO / EM RELAÇÃO AO PRÓPRIO HISTÓRICO")
            ids.take(if (expanded) 1000 else 5).forEach { id ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(catalog.firstOrNull { it.id == id }?.nome ?: id, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (rivalry.comparable) Text("Você · " + (mine[id]?.let { "+${Format.peso(it)}%" } ?: "sem melhora comparável"), Modifier.weight(1f), fontSize = 10.sp, color = TextoSecundario)
                        Text("Aliado · " + (evolution.improvements[id]?.let { "+${Format.peso(it)}%" } ?: "sem melhora comparável"), Modifier.weight(1f), fontSize = 10.sp, color = TextoSecundario)
                    }
                }
            }
            if (ids.size > 5) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Mostrar menos" else "Ver todos os exercícios em evolução") }
            Text("Cada percentual usa o próprio histórico e equipamento. São trajetórias diferentes; o confronto de força usa os PRs abaixo. Ausência de melhora não significa perda de força.", color = TextoSecundario, fontSize = 10.sp, lineHeight = 16.sp)
        } else Text("Nenhuma melhora publicada nesta semana. Presença e manutenção também fazem parte do treino.", color = TextoSecundario, fontSize = 12.sp)
        HorizontalDivider(color = Borda)
        Eyebrow("FORÇA / RECORDES POR EXERCÍCIO")
    }
}

@Composable private fun ExerciseRivalry(rows: List<ConfrontoExercicio>, catalog: List<Exercicio>) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyCommon by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable { mutableIntStateOf(12) }
    val shared = rows.filter { it.lideranca!=LiderancaExercicio.SEM_COMPARACAO }
    val filtered = rows.filter { row -> (muscle==null || row.referencia.musculo==muscle) &&
        recordMatches(row.referencia,query,catalog) && (!onlyCommon || row.lideranca!=LiderancaExercicio.SEM_COMPARACAO) }
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Stat(shared.count { it.lideranca==LiderancaExercicio.VOCE }.toString(),"VOCÊ LIDERA")
            Stat(shared.count { it.lideranca==LiderancaExercicio.EMPATE }.toString(),"EMPATES")
            Stat(shared.count { it.lideranca==LiderancaExercicio.ALIADO }.toString(),"ALIADO LIDERA")
        }
        Text("Carga real na mesma variação. Repetições e tempo nos exercícios sem carga. Valores declarados; modelos de máquinas e execução podem variar.",color=TextoSecundario,fontSize=11.sp,lineHeight=16.sp)
        OutlinedTextField(query,{query=it;limit=12},label={Text("Buscar exercício")},singleLine=true,modifier=Modifier.fillMaxWidth(),leadingIcon={Icon(Icons.Outlined.Search,null)})
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Choice("Todos",muscle==null) {muscle=null;limit=12}
            rows.map { it.referencia.musculo }.distinct().sorted().forEach { m -> Choice(m,muscle==m) {muscle=m;limit=12} }
        }
        FilterChip(onlyCommon,{onlyCommon=!onlyCommon;limit=12},label={Text("Só exercícios em comum · ${shared.size}")})
        if(filtered.isEmpty()) Text(if(rows.isEmpty()) "O aliado precisa abrir a versão nova para compartilhar PRs. Nenhum recorde é inventado." else "Nenhum exercício neste filtro. Experimente Todos.",color=TextoSecundario,fontSize=13.sp,lineHeight=19.sp)
        filtered.take(limit).forEach { row -> key(row.id) { RivalryRecord(row,catalog.firstOrNull { it.id==row.id }) } }
        if(filtered.size>limit) TextButton(onClick={limit+=12},modifier=Modifier.fillMaxWidth()) {Text("Ver mais · ${filtered.size-limit} exercícios",color=DouradoLoot)}
    }
}

@Composable private fun RivalryRecord(row: ConfrontoExercicio, exercise: Exercicio?) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    val winner = row.lideranca
    val color = when(winner) {LiderancaExercicio.VOCE->Neon;LiderancaExercicio.ALIADO->DouradoLoot;else->TextoSecundario}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SuperficieAlta)
        .border(1.dp,color.copy(alpha=.22f),RoundedCornerShape(18.dp)).animateContentSize().clickable {expanded=!expanded}.padding(15.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(exercise!=null) ExerciseThumbnail(exercise,52.dp)
            Column(Modifier.weight(1f)) {
                Text(row.referencia.nome,fontSize=14.sp,fontWeight=FontWeight.Bold)
                Text(row.referencia.musculo,color=TextoSecundario,fontSize=10.sp)
            }
            Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,tint=color)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            RecordValue("VOCÊ",row.meu,Neon)
            RecordValue("ALIADO",row.aliado,DouradoLoot)
        }
        if(winner!=LiderancaExercicio.SEM_COMPARACAO) {
            val a=recordMeasure(row.meu);val b=recordMeasure(row.aliado);val max=maxOf(a,b).coerceAtLeast(1f)
            RivalryBar(a/max,Neon);RivalryBar(b/max,DouradoLoot)
        }
        Text(when(winner) {LiderancaExercicio.VOCE->"Você lidera neste exercício";LiderancaExercicio.ALIADO->"Seu aliado lidera neste exercício";LiderancaExercicio.EMPATE->"Mesma marca · compare também as repetições";else->"Sem registro comparável dos dois"},color=color,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
        AnimatedVisibility(expanded) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color=Borda)
                listOf("Você" to row.meu,"Aliado" to row.aliado).forEach { (label,record) ->
                    Text(if(record==null) "$label · sem registro" else "$label · ${record.patente} · ${record.series} séries registradas\nPR em ${recordDate(record.dia)} · ${recordLabel(record)}",fontSize=11.sp,color=TextoSecundario,lineHeight=17.sp)
                }
                Text("A patente usa a melhor pontuação de carga e repetições. Ela pode vir de outra série que a maior carga exibida.",color=TextoSecundario,fontSize=10.sp,lineHeight=15.sp)
            }
        }
    }
}

@Composable private fun RecordValue(label: String, record: RecordeAmigo?, color: Color) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Eyebrow(label,color)
        Text(if(record==null) "—" else when(record.modo) {"CARGA"->"${kg(record.pesoKg ?: 0f)} kg";"TEMPO"->"${record.segundos} s";else->"${record.reps} reps"},fontSize=23.sp,fontWeight=FontWeight.Black,color=if(record==null)TextoSecundario else TextoPrimario)
        Text(if(record==null) "Sem registro" else if(record.modo=="CARGA") "${record.reps} repetições" else record.patente,color=TextoSecundario,fontSize=10.sp)
    }
}

@Composable private fun RivalryBar(target: Float,color: Color) {
    val value by animateFloatAsState(target.coerceIn(0f,1f),tween(600),label="rivalryForce")
    LinearProgressIndicator(progress={value},modifier=Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),color=color,trackColor=color.copy(alpha=.08f))
}

@Composable private fun SharedRoutine(routine: RotinaAmigo,catalog: List<Exercicio>,expanded: Boolean,onToggle: ()->Unit,onCopy: ()->Unit,heading:String=routine.nome) {
    var confirmation by remember { mutableStateOf(false) }
    val available = remember(routine,catalog) {CompeticaoAmigos.itensImportaveis(routine,catalog)}
    if(confirmation) AlertDialog(onDismissRequest={confirmation=false},title={Text("Adotar ${routine.nome}?")},
        text={Text("${available.size} exercícios serão copiados com séries, repetições e descanso, sem substituir seus treinos ou os dias da semana.${if(available.size<routine.itens.size) " ${routine.itens.size-available.size} exercícios indisponíveis ficarão fora." else ""} As cargas devem ser ajustadas por você.")},
        confirmButton={TextButton(onClick={confirmation=false;onCopy()}) {Text("Copiar treino")}},dismissButton={TextButton(onClick={confirmation=false}) {Text("Voltar")}},containerColor=SuperficieAlta)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SuperficieAlta).border(1.dp,NeonRoxo.copy(alpha=.25f),RoundedCornerShape(18.dp)).animateContentSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick=onToggle),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(heading,fontSize=17.sp,fontWeight=FontWeight.Bold)
                Text("${routine.exercicios.size} exercícios${if(routine.itens.isNotEmpty()) " · ${routine.itens.sumOf { it.series }} séries" else ""}",fontSize=11.sp,color=NeonRoxo)
            }
            Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,tint=NeonRoxo)
        }
        if(routine.diasSemana.isNotEmpty()) Text(routine.diasSemana.joinToString(" · ") { listOf("SEG","TER","QUA","QUI","SEX","SÁB","DOM").getOrElse(it-1){"?"}},fontSize=10.sp,color=TextoSecundario)
        AnimatedVisibility(expanded) {
            Column(verticalArrangement=Arrangement.spacedBy(13.dp)) {
                if(routine.itens.isEmpty()) Text(routine.exercicios.joinToString(" · "),color=TextoSecundario,fontSize=12.sp,lineHeight=18.sp)
                routine.itens.forEach { item ->
                    val ex=catalog.firstOrNull { it.id==item.exercicioId }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(ex!=null) ExerciseThumbnail(ex,54.dp)
                        Column(Modifier.weight(1f)) {
                            Text(ex?.nome ?: "Exercício indisponível nesta versão",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                            val reps = if(ex?.modo==ModoMedida.TEMPO) "${item.repMin*3}–${item.repMax*3}s" else "${item.repMin}–${item.repMax} reps"
                            Text("${item.series} séries · $reps · ${item.descansoSeg}s descanso",fontSize=10.sp,color=TextoSecundario)
                        }
                    }
                }
                Button(onClick={confirmation=true},enabled=available.isNotEmpty(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=NeonRoxo,contentColor=Fundo)) {
                    Icon(Icons.Outlined.ContentCopy,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text("Adotar no meu planejamento")
                }
                if(routine.itens.isEmpty()) Text("O aliado precisa atualizar o app para liberar a cópia completa.",fontSize=11.sp,color=TextoSecundario)
            }
        }
    }
}

private fun recordDate(day: Long): String = runCatching {LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM/yy"))}.getOrDefault("—")
private fun kg(weight: Float): String = String.format(Locale.forLanguageTag("pt-BR"),"%.1f",weight).removeSuffix(",0")
private fun recordMeasure(r: RecordeAmigo?): Float = r?.let {when(it.modo){"CARGA"->it.pesoKg ?: 0f;"TEMPO"->it.segundos.toFloat();else->it.reps.toFloat()}} ?: 0f
private fun recordLabel(r: RecordeAmigo): String = when(r.modo){"CARGA"->"${kg(r.pesoKg ?: 0f)} kg × ${r.reps} reps";"TEMPO"->"${r.segundos}s";else->"${r.reps} reps"}
private fun recordMatches(r: RecordeAmigo,query: String,catalog: List<Exercicio>): Boolean = catalog.firstOrNull {it.id==r.exercicioId}
    ?.let {BuscaExercicios.corresponde(it,query)} ?: BuscaExercicios.normalizar("${r.nome} ${r.musculo}").contains(BuscaExercicios.normalizar(query))
