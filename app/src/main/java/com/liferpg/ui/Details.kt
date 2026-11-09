package com.liferpg.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import com.liferpg.MainViewModel
import com.liferpg.data.SerieRegistro
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.launch

private fun InputStream.readBackupBytes(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "O backup ultrapassa 10 MB." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun serieLabel(r: SerieRegistro): String = listOfNotNull(r.pesoKg?.let { "${Format.peso(it)} kg" }, r.reps?.let { "$it repetições" }, r.segundos?.let { Format.tempo(it) }).joinToString(" × ") + "  /  +${r.xpGanho} XP"

@Composable internal fun Details(vm: MainViewModel, route: String, back: () -> Unit, open: (String) -> Unit) {
    if (RemnantRoute.isHistory(route)) {
        WorkoutHistory(vm,back,open,
            route.takeIf { it.startsWith(RemnantRoute.HISTORY_EXERCISE_PREFIX) }?.removePrefix(RemnantRoute.HISTORY_EXERCISE_PREFIX),
            route.takeIf { it.startsWith(RemnantRoute.HISTORY_SESSION_PREFIX) }?.removePrefix(RemnantRoute.HISTORY_SESSION_PREFIX)?.toLongOrNull())
        return
    }
    if (route == RemnantRoute.SYSTEM) {
        SystemPanel(vm, open, back)
        return
    }
    if (route == RemnantRoute.BOSS_HUNT) {
        BossHuntScreen(vm, open, back)
        return
    }
    RemnantRoute.cameraExerciseId(route)?.let { exerciseId ->
        val all by vm.exercicios.collectAsStateWithLifecycle()
        val ex = all.firstOrNull { it.id == exerciseId }
        if (ex != null) PushUpCounterScreen(vm, ex, back)
        return
    }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = back, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Voltar")
        }
        Box(Modifier.weight(1f)) {
            when {
                route == RemnantRoute.CARDIO -> CardioScreen(vm.cardio)
                route == RemnantRoute.CELEBRATIONS -> CelebrationGallery()
                route == RemnantRoute.BODY_PHOTOS -> BodyProgressScreen()
                route.startsWith(RemnantRoute.MUSCLE_RANK_PREFIX) -> MuscleRecord(vm, route.removePrefix(RemnantRoute.MUSCLE_RANK_PREFIX), open)
                route.startsWith(RemnantRoute.EXERCISE_PREFIX) -> {
                    val all by vm.exercicios.collectAsStateWithLifecycle()
                    val exerciseId = route.removePrefix(RemnantRoute.EXERCISE_PREFIX)
                    val ex = all.firstOrNull { it.id == exerciseId }
                    if (ex != null) ExerciseOverview(vm, ex, open)
                }
                route == RemnantRoute.PLAN || route == RemnantRoute.SESSION -> Session(vm, route == RemnantRoute.SESSION, open, back)
                route == RemnantRoute.TROPHIES || route == RemnantRoute.TITLES -> Collections(vm, route)
                route == RemnantRoute.WEIGHT -> Weight(vm)
                route == RemnantRoute.SETTINGS -> Settings(vm, open)
                route == RemnantRoute.ROUTINES -> RoutineBuilder(vm, open)
                route == RemnantRoute.RECORDS -> PersonalRecords(vm)
                route == RemnantRoute.NUTRITION -> NutritionCommandCenter(vm, open)
                route == RemnantRoute.FOOD_PHOTO -> NutritionVision(vm, back)
                route == RemnantRoute.ARENA -> ArenaGlobal(vm, open)
                route == RemnantRoute.HEALTH -> HealthBridge(vm)
                route == RemnantRoute.WGER -> WgerLibrary(vm)
                route == RemnantRoute.FOOD_SEARCH -> FoodSearch(vm)
                route == RemnantRoute.FOOD_SCANNER -> BarcodeFoodScanner(vm)
                route == RemnantRoute.RECIPES -> RecipeArchive(vm)
                route == RemnantRoute.TOOLS -> TrainingTools()
                route == RemnantRoute.PRIVACY -> PrivacyAndData(vm)
                route == RemnantRoute.JOURNEY -> EvolutionJourney(vm, open)
                route == RemnantRoute.PACTS -> TrainingPacts(vm)
                route == RemnantRoute.CLOUD_SAVE -> CloudSave(vm)
                else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Esta tela não está disponível.", color = TextoSecundario)
                    TextButton(onClick = back) { Text("Voltar ao aplicativo") }
                }
            }
        }
    }
}

@Composable internal fun SetForm(vm: MainViewModel, ex: Exercicio, buttonLabel: String = "Registrar série", restControls: Boolean = false) {
    val profile by vm.perfil.collectAsStateWithLifecycle()
    val scores by vm.melhoresScores.collectAsStateWithLifecycle()
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    var weight by rememberSaveable(ex.id) { mutableStateOf("") }
    var reps by rememberSaveable(ex.id) { mutableStateOf("") }
    var seconds by rememberSaveable(ex.id) { mutableStateOf("") }
    var autoRest by rememberSaveable(ex.id) { mutableStateOf(true) }
    var effort by rememberSaveable(ex.id) { mutableStateOf("") }
    val prefs by vm.prefsEfetivas.collectAsStateWithLifecycle()
    val restSeconds=prefs.descansoPadraoSeg.coerceIn(15,600)
    val busy by vm.registrando.collectAsStateWithLifecycle()
    val kg = weight.replace(',', '.').toFloatOrNull()
    val rep = reps.toIntOrNull()
    val sec = seconds.toIntOrNull()
    val valid = when (ex.modo) {
        ModoMedida.CARGA -> kg != null && kg.isFinite() && kg > 0 && rep != null && rep > 0
        ModoMedida.REPS -> rep != null && rep > 0
        ModoMedida.TEMPO -> sec != null && sec > 0
    }
    Panel {
        ProgressionReference(vm, ex) { target ->
            target.kg?.let { weight = Format.peso(it) }
            target.reps?.let { reps = it.toString() }
            target.seconds?.let { seconds = it.toString() }
        }
        if (ex.modo == ModoMedida.CARGA) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { NumberField("Carga / kg",weight) { weight=it } }
            Box(Modifier.weight(1f)) { NumberField("Repetições",reps) { reps=it } }
        }
        if (ex.modo == ModoMedida.REPS) NumberField("Repetições", reps) { reps = it }
        if (ex.modo == ModoMedida.TEMPO) NumberField("Tempo / segundos", seconds) { seconds = it }
        Text("Como foi esta série? · opcional", color = TextoSecundario, fontSize = 11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SetEffort.entries.forEach { value -> Choice(value.label, effort == value.name) { effort = if (effort == value.name) "" else value.name } }
        }
        if (valid && profile != null) {
            val classe = ClassePersonagem.entries.firstOrNull { it.name == profile!!.classe } ?: ClassePersonagem.AVENTUREIRO
            val score = RankEngine.scoreSerie(ex.modo, profile!!.pesoCorporalKg, kg, rep, sec)
            val xp = com.liferpg.domain.Experience.calcular(ex, classe, score)
            Text("Treino: +${xp.treino} XP · ${xp.detalhes()}", color = TextoSecundario, fontSize = 11.sp)
            Text("PR, patente e missões acrescentam bônus ao salvar. Mais XP não é uma indicação para aumentar a carga.", color = TextoSecundario, fontSize = 11.sp)
        }
        if (valid && ex.benchmarks?.size == 7 && (profile?.pesoCorporalKg ?: 0f) > 0f) {
            val score = RankEngine.scoreSerie(ex.modo, profile!!.pesoCorporalKg, kg, rep, sec)
            if (score.isFinite()) {
                val own = RankEngine.ratingMusculo(listOf(ex), mapOf(ex.id to maxOf(score,scores[ex.id] ?: 0f)))
                Text("Neste exercício: ${own.rotulo}",color=own.patente.cor,fontWeight=FontWeight.Bold)
                val predicted = RankEngine.ratingMusculo(catalog.filter { it.musculo == ex.musculo }, scores + (ex.id to maxOf(score, scores[ex.id] ?: 0f)))
                HorizontalDivider(color = Borda)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Insignia(predicted.patente, 56.dp, predicted.divisao)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Eyebrow("Média / ${ex.musculo.nomeExibicao}")
                        Text(predicted.rotulo, fontWeight = FontWeight.Bold, color = predicted.patente.cor)
                        if (ex.modo == ModoMedida.CARGA) Text("1RM estimado: ${Format.peso(RankEngine.e1rm(kg!!, rep!!))} kg", color = TextoSecundario, fontSize = 12.sp)
                    }
                }
                Text("A patente é confirmada ao salvar uma série válida. O registro também conta no seu treino.", color = TextoSecundario, fontSize = 11.sp)
            }
        }
        if(restControls) Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Descanso automático",fontSize=14.sp,fontWeight=FontWeight.Medium)
                Text("${restSeconds}s após uma série válida",fontSize=11.sp,color=TextoSecundario)
            }
            Switch(autoRest,{autoRest=it},enabled=!busy)
        }
        Action(if (busy) "Salvando…" else buttonLabel, enabled = valid && !busy) {
            vm.registrarSerie(ex, if (ex.modo == ModoMedida.CARGA) kg else null, if (ex.modo != ModoMedida.TEMPO) rep else null, if (ex.modo == ModoMedida.TEMPO) sec else null, if(restControls) { if(autoRest)restSeconds else 0 } else null, effort)
        }
    }
}

@Composable internal fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp))
}

@Composable private fun Session(vm: MainViewModel, active: Boolean, open: (String) -> Unit, back: () -> Unit) {
    val plan by vm.treinoHoje.collectAsStateWithLifecycle()
    val session by vm.sessao.collectAsStateWithLifecycle()
    var finish by remember { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableStateOf(false) }
    val s = session
    if (!active || s == null) {
        key("session-preview") { Page("PREPARE SUA SESSÃO", plan.titulo) {
            Text(plan.subtitulo, fontSize = 14.sp, color = TextoSecundario)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("${plan.itens.size}", "exercícios")
                Stat("${plan.volumeTotalSeries}", "séries planejadas")
            }
            plan.itens.forEachIndexed { i, item -> ExerciseRow(item.exercicio, "${i + 1}.  ${item.series} séries × ${item.repsRotulo} • ${Format.tempo(item.descansoSeg)} pausa") { open(RemnantRoute.exercise(item.exercicio.id)) } }
            Action(if (s == null) "Iniciar sessão" else "Retomar sessão", plan.itens.isNotEmpty()) { if (s == null) vm.iniciarSessaoSugerida(); open(RemnantRoute.SESSION) }
        } }
    } else if (s.concluida) {
        key("session-completed") { Page("MAIS UM CAPÍTULO ESCRITO", "Treino concluído.") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Insignia(size = 160.dp) }
            Panel {
                Text(s.nome, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("${s.exerciciosConcluidos}/${s.totalExercicios} exercícios concluídos", color = Neon, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${s.seriesFeitas}", "séries")
                    Stat("+${s.xpGanho}", "XP")
                    Stat("${s.recordes.size}", "recordes")
                }
            }
            SessionCircleShare(vm,s)
            Action("Voltar ao meu dia") { vm.encerrarSessao(); back() }
        } }
    } else {
        key("session-live") { Page("SESSÃO EM ANDAMENTO", s.nome) {
            Meter(s.progressoExercicios)
            Text("${s.exerciciosConcluidos}/${s.totalExercicios} exercícios • ${s.seriesFeitas}/${s.seriesPlanejadas} séries • +${s.xpGanho} XP", fontSize = 13.sp, color = TextoSecundario)
            s.itemAtual?.let { current ->
                val exercise = current.item.exercicio
                Section("Exercício atual")
                AnimatedContent(exercise.nome, transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(100))
                }, label = "currentExercise") { name -> Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                AnimatedContent("${current.feitas.size}/${current.item.series} séries  •  ${current.item.repsRotulo} por série",
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(90)) }, label = "completedSets") {
                    Text(it, color = TextoSecundario, fontSize = 13.sp)
                }
                SessionExerciseGallery(exercise)
                if (exercise.instrucoes.isNotEmpty()) {
                    Eyebrow("EXECUÇÃO RÁPIDA", Neon)
                    exercise.instrucoes.take(3).forEachIndexed { index, instruction ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${index + 1}", color = Neon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(instruction, Modifier.weight(1f), color = TextoPrimario, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                }
                if (exercise.dica.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                            .background(DouradoLoot.copy(alpha = .10f)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Eyebrow("DICA TÉCNICA", DouradoLoot)
                        Text(exercise.dica, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                if (current.feitas.isEmpty()) TextButton(onClick = { replacing = true }) { Text("Escolher alternativa para este exercício") }
                OutlinedButton(
                    onClick = { open(RemnantRoute.exercise(exercise.id)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Ver ficha completa e histórico") }
                if (exercise.suportaContadorDeFlexao || exercise.id == com.liferpg.domain.CameraBoss.STONE_COLOSSUS.exerciseId) {
                    Action("Contar e validar com a câmera") { open(RemnantRoute.camera(exercise.id)) }
                }
                Section("Registrar série")
                SetForm(vm, exercise)
                RestTimer(vm)
                val completedBySets = current.feitas.size >= current.item.series
                Action(
                    if (completedBySets) "Séries planejadas concluídas ✓"
                    else if (current.conclusaoManual) "Desmarcar como concluído"
                    else "Marcar exercício como concluído",
                    enabled = !completedBySets,
                ) {
                    vm.definirConclusaoItemSessao(s.indiceAtual, !current.conclusaoManual)
                }
            }
            Section("Exercícios do treino")
            Text("Faça na ordem que quiser. Toque em qualquer exercício para torná-lo o atual.", color = TextoSecundario, fontSize = 12.sp)
            s.itens.forEachIndexed { i, item ->
                val selected = i == s.indiceAtual
                val interaction = remember(item.item.exercicio.id) { MutableInteractionSource() }
                val background by animateColorAsState(if (selected) Neon.copy(alpha = .12f) else Superficie, tween(190), label = "sessionSelection")
                val outline by animateColorAsState(if (selected) Neon.copy(alpha = .72f) else Borda, tween(190), label = "sessionOutline")
                val titleColor by animateColorAsState(if (item.concluido) TextoSecundario else TextoPrimario, tween(180), label = "exerciseCompleted")
                Row(
                    Modifier.fillMaxWidth().pressMotion(interaction, .985f).clip(RoundedCornerShape(16.dp))
                        .background(background)
                        .border(1.dp, outline, RoundedCornerShape(16.dp))
                        .clickable(interactionSource = interaction, indication = LocalIndication.current) { vm.irParaItemSessao(i) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SessionExerciseThumbnail(item.item.exercicio)
                    Checkbox(
                        checked = item.concluido,
                        onCheckedChange = { checked -> vm.definirConclusaoItemSessao(i, checked) },
                        enabled = item.feitas.size < item.item.series,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.item.exercicio.nome, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                        Text(
                            if (selected) "AGORA • ${item.feitas.size}/${item.item.series} séries" else "${item.feitas.size}/${item.item.series} séries • ${item.item.repsRotulo}",
                            color = if (selected) Neon else TextoSecundario,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Text("${i + 1}", color = TextoSecundario, fontSize = 12.sp)
                }
            }
            OutlinedButton(onClick = { finish = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (s.exerciciosConcluidos == s.totalExercicios) "Concluir treino" else "Encerrar treino agora")
            }
        } }
    }
    if (finish) AlertDialog(
        onDismissRequest = { finish = false },
        title = { Text("Concluir este treino?") },
        text = { Text("${s?.exerciciosConcluidos ?: 0}/${s?.totalExercicios ?: 0} exercícios concluídos. As séries registradas serão mantidas no histórico.") },
        confirmButton = { TextButton(onClick = { finish = false; vm.finalizarSessao() }) { Text("Concluir treino") } },
        dismissButton = { TextButton(onClick = { finish = false }) { Text("Continuar treinando") } },
    )
    if (replacing) s?.itemAtual?.item?.exercicio?.let { ExerciseReplacement(vm, it) { replacing = false } }
}

private fun progressionHint(item: ItemTreino): String = when (item.exercicio.modo) {
    ModoMedida.CARGA -> "Quando fizer ${item.series} séries chegando a ${item.repMax} repetições com amplitude e técnica limpas, aumente a menor carga disponível na próxima sessão. Se perder a forma, mantenha a carga."
    ModoMedida.REPS -> "Quando completar ${item.series} séries no topo da faixa, acrescente 1 ou 2 repetições por série. Só avance mantendo o mesmo controle do movimento."
    ModoMedida.TEMPO -> "Quando completar todas as séries sem quebrar a postura, aumente de 5 a 10 segundos. Pare a progressão se a técnica começar a ceder."
}

@Composable private fun SessionExerciseGallery(exercise: Exercicio) {
    if (exercise.imagens.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp)).background(SuperficieAlta),
            contentAlignment = Alignment.Center,
        ) { Text("Demonstração visual indisponível", color = TextoSecundario, fontSize = 12.sp) }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val imageWidth = maxWidth * .86f
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            exercise.imagens.take(2).forEachIndexed { index, path ->
                Box(
                    Modifier.width(imageWidth).height(210.dp).clip(RoundedCornerShape(18.dp)).background(SuperficieAlta),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$path",
                        contentDescription = "${if (index == 0) "Início" else "Final"} de ${exercise.nome}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = { CircularProgressIndicator(Modifier.size(22.dp)) },
                        error = { Text("Imagem indisponível", color = TextoSecundario, fontSize = 11.sp) },
                    )
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .72f))
                            .padding(8.dp)
                    ) {
                        Eyebrow(if (index == 0) "POSIÇÃO INICIAL" else "POSIÇÃO FINAL", Neon)
                    }
                }
            }
        }
    }
}

@Composable private fun SessionExerciseThumbnail(exercise: Exercicio) {
    val path = exercise.imagens.firstOrNull()
    Box(
        Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(SuperficieAlta),
        contentAlignment = Alignment.Center,
    ) {
        if (path == null) Text("VER", color = Neon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        else SubcomposeAsyncImage(
            model = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$path",
            contentDescription = "Demonstração de ${exercise.nome}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { Box(Modifier.fillMaxSize().background(SuperficieAlta)) },
            error = { Text("VER", color = Neon, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
        )
    }
}

@Composable private fun Collections(vm: MainViewModel, route: String) {
    val trophies by vm.trofeusGanhos.collectAsStateWithLifecycle()
    val titles by vm.titulosGanhos.collectAsStateWithLifecycle()
    val equipped by vm.tituloEquipado.collectAsStateWithLifecycle()
    Page("COLEÇÃO  /  FEITOS QUE PERMANECEM", if (route == RemnantRoute.TROPHIES) "Sala de troféus." else "Um título.\nA sua história.") {
        if (route == RemnantRoute.TROPHIES) TrofeuEngine.LISTA.forEach { t ->
            val unlocked = t.id in trophies
            Panel {
                Icon(if (unlocked) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock, null, Modifier.size(38.dp), tint = if (unlocked) t.material.corBrilho else TextoSecundario)
                Text(t.nome, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(t.descricao, color = TextoSecundario, fontSize = 13.sp)
                Eyebrow(if (unlocked) "Conquistado • ${t.material.nome}" else "Ainda por conquistar", if (unlocked) t.material.corBrilho else TextoSecundario)
            }
        } else TituloEngine.LISTA.forEach { t ->
            val unlocked = titles.any { it.id == t.id }
            Panel {
                Text(t.texto, fontSize = 23.sp, fontWeight = FontWeight.Medium, color = if (unlocked) DouradoLoot else TextoSecundario)
                Text(t.descricao, color = TextoSecundario, fontSize = 13.sp)
                if (unlocked) Action(if (equipped?.id == t.id) "Título equipado" else "Equipar título", equipped?.id != t.id) { vm.equiparTitulo(t.id) }
                else Eyebrow("Bloqueado", TextoSecundario)
            }
        }
    }
}

@Composable private fun Weight(vm: MainViewModel) {
    val p by vm.perfil.collectAsStateWithLifecycle()
    val history by vm.pesoHistorico.collectAsStateWithLifecycle()
    var value by rememberSaveable { mutableStateOf("") }
    val kg = value.replace(',', '.').toFloatOrNull()
    Page("ACOMPANHAMENTO", "Seu peso,\nao longo do tempo.") {
        Panel {
            Stat("${Format.peso(p?.pesoCorporalKg ?: 0f)} kg", "registro atual")
            NumberField("Novo registro / kg", value) { value = it }
            Action("Salvar peso", kg != null && kg.isFinite() && kg in 30f..350f) { kg?.let { vm.registrarPesoCorporal(it); value = "" } }
        }
        history.sortedByDescending { it.epochDay }.forEach { row -> Panel {
            Text(LocalDate.ofEpochDay(row.epochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), color = TextoSecundario, fontSize = 12.sp)
            Text("${Format.peso(row.pesoKg)} kg", fontSize = 22.sp)
        } }
    }
}

@Composable private fun Settings(vm: MainViewModel, open: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefsEfetivas.collectAsStateWithLifecycle()
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    var signingOut by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var notificationGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
        vm.atualizarPreferencias(prefs.copy(lembretesAtivos = granted))
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input -> input.readBackupBytes(10 * 1024 * 1024) }
                    ?: error("Arquivo indisponível")
                pendingBackup = bytes.toString(Charsets.UTF_8)
            }.onFailure { vm.avisar(it.message ?: "Não consegui abrir o backup.") }
        }
    }
    Page("PERSONALIZE SUA JORNADA", "Do seu jeito.") {
        Section("Seu objetivo")
        Objetivo.entries.forEach { o -> Choice(o.titulo, prefs.objetivo == o.name) { vm.atualizarPreferencias(prefs.copy(objetivo = o.name)) } }
        Section("Onde você treina")
        LocalTreino.entries.forEach { l -> Choice(l.nomeExibicao, prefs.localPreferido == l.name) { vm.atualizarPreferencias(prefs.copy(localPreferido = l.name)) } }
        Section("Experiência")
        NivelExperiencia.entries.forEach { e -> Choice(e.titulo, prefs.experiencia == e.name) { vm.atualizarPreferencias(prefs.copy(experiencia = e.name)) } }
        Section("Dias por semana")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..7).forEach { d -> Choice("$d", prefs.diasPorSemana == d) { vm.atualizarPreferencias(prefs.copy(diasPorSemana = d)) } } }
        Section("Lembretes")
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Lembrete de treino", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (prefs.lembretesAtivos && notificationGranted) "Ativo no horário escolhido" else "Desativado",
                        color = TextoSecundario, fontSize = 12.sp,
                    )
                }
                Switch(checked = prefs.lembretesAtivos && notificationGranted, onCheckedChange = { enabled ->
                    if (!enabled) vm.atualizarPreferencias(prefs.copy(lembretesAtivos = false))
                    else if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.atualizarPreferencias(prefs.copy(lembretesAtivos = true))
                })
            }
            if (!notificationGranted) Text("Permita notificações somente se quiser receber o aviso.", color = DouradoLoot, fontSize = 12.sp)
            Eyebrow("HORÁRIO")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HorarioTreino.entries.forEach { horario ->
                    Choice(horario.titulo, prefs.horarioTreino == horario.name) {
                        vm.atualizarPreferencias(prefs.copy(horarioTreino = horario.name))
                    }
                }
            }
        }
        Section("Arquivo e portabilidade")
        Panel {
            Eyebrow("VERSÃO DO APLICATIVO", Neon)
            Text("REMNANT ${com.liferpg.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text("Quando uma versão nova sair, o REMNANT avisa e baixa a atualização aqui mesmo.", color = TextoSecundario, fontSize = 12.sp)
            Action("Verificar atualização") { scope.launch { AppUpdates.check(context, manual = true); AppUpdates.show() } }
            AppUpdates.message?.let { Text(it, color = Neon, fontSize = 12.sp) }
        }
        Panel {
            Text("Backup completo", fontWeight = FontWeight.SemiBold)
            Text("Exporta perfil, séries, patentes, rotinas, refeições e histórico em um arquivo JSON.", color = TextoSecundario, fontSize = 12.sp)
            Action("Exportar arquivo do Remnant") {
                scope.launch {
                    runCatching {
                        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                        val file = File(directory, "remnant-backup-${LocalDate.now()}.json")
                        file.writeText(vm.gerarBackupJson(), Charsets.UTF_8)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Salvar backup do Remnant"))
                    }.onFailure { vm.avisar("Não consegui gerar o backup.") }
                }
            }
            Action("Restaurar um backup") { importBackup.launch(arrayOf("application/json", "text/json", "text/plain")) }
            Text("A restauração valida o arquivo antes de substituir os dados deste aparelho.", color = TextoSecundario, fontSize = 11.sp)
        }
        MenuRow("Privacidade e dados", "Veja o que fica no aparelho e controle a Arena") { open(RemnantRoute.PRIVACY) }
        MenuRow("Progresso na minha conta", "Salvar ou recuperar sua jornada em outro aparelho") { open(RemnantRoute.CLOUD_SAVE) }
        if(account!=null) MenuRow("Sair da conta", "Encerra o login neste aparelho e preserva os dados locais") { signingOut=true }
    }
    if(signingOut) AlertDialog(onDismissRequest={ signingOut=false },title={ Text("Sair da conta?") },
        text={ Text("Seus treinos e fotos ficam neste aparelho. Para voltar, entre na mesma conta do seu progresso.") },
        confirmButton={ TextButton(onClick={ signingOut=false; vm.sairDaConta() }) { Text("Sair da conta") } },
        dismissButton={ TextButton(onClick={ signingOut=false }) { Text("Ficar") } })
    pendingBackup?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingBackup = null },
            title = { Text("Restaurar este backup?") },
            text = { Text("Perfil, séries, treinos, alimentação e rotinas atuais serão substituídos pelo conteúdo validado do arquivo.") },
            confirmButton = { TextButton(onClick = {
                pendingBackup = null
                scope.launch {
                    runCatching { vm.restaurarBackupJson(json) }
                        .onSuccess(vm::avisar)
                        .onFailure { vm.avisar(it.message ?: "O backup não é válido.") }
                }
            }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { pendingBackup = null }) { Text("Cancelar") } },
        )
    }
}

@Composable private fun PrivacyAndData(vm: MainViewModel) {
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    var confirmRemoteDelete by rememberSaveable { mutableStateOf(false) }
    Page("PRIVACIDADE  /  CONTROLE", "Seus dados.\nSuas regras.") {
        Panel {
            Eyebrow("NESTE APARELHO", Neon)
            Text("Treinos, séries, patentes, rotinas, alimentação, peso e preferências ficam no banco local do Remnant.", color = TextoSecundario, fontSize = 13.sp)
            Text("Fotos de progresso ficam privadas no armazenamento interno e não entram no backup automático.", color = TextoSecundario, fontSize = 13.sp)
            Text("A análise da câmera acontece no próprio aparelho; o Remnant não envia nem grava o vídeo da flexão.", color = TextoSecundario, fontSize = 13.sp)
        }
        Panel {
            Eyebrow("SERVIÇOS CONECTADOS", DouradoLoot)
            Text("Inteligência do REM", fontWeight = FontWeight.SemiBold)
            Text("Quando você conversa, o texto da pergunta e um resumo do contexto de treino são enviados ao serviço configurado. Na Visão do REM, somente quando você toca em Analisar prato, a foto escolhida é enviada ao serviço de IA; o app não a publica na Arena. Os macros confirmados ficam no diário local. O vídeo dos exercícios e as fotos de progresso não são enviados.", color = TextoSecundario, fontSize = 13.sp)
            HorizontalDivider(color = Borda)
            Text("Health Connect", fontWeight = FontWeight.SemiBold)
            Text("Peso e passos só são lidos após sua autorização. As permissões podem ser retiradas nas configurações do Android.", color = TextoSecundario, fontSize = 13.sp)
            HorizontalDivider(color = Borda)
            Text("Arena Global", fontWeight = FontWeight.SemiBold)
            Text("Ao entrar, a Arena publica apelido, pontuação, patente e temporada. Ao aceitar uma amizade, o aliado também pode ver sua apresentação, nível, sequência, patentes musculares, PRs por exercício (carga, repetições, tempo e data), rotinas completas e sessões recentes. Ele pode copiar a configuração de uma rotina, sem copiar suas cargas ou seu progresso. Peso corporal, fotos e e-mail não entram nesse dossiê. Remover a amizade corta o acesso às próximas consultas; cópias de rotinas já feitas continuam no planejamento do aliado.", color = TextoSecundario, fontSize = 13.sp)
        }
        Panel {
            Eyebrow("PORTABILIDADE")
            Text("Use Preferências para exportar ou restaurar um arquivo JSON. Chaves de IA, sessão da Arena, fotos e treino temporário não são incluídos.", color = TextoSecundario, fontSize = 13.sp)
        }
        Panel {
            Eyebrow("APAGAR DA ARENA", Color(0xFFFF8A80))
            Text(if (account == null) "Você não está conectado à Arena." else "Remove ranking, amizades, duelos, pactos e a cópia da conta, depois encerra a sessão. Seus treinos e fotos locais permanecem.", color = TextoSecundario, fontSize = 13.sp)
            Action("Apagar meus dados remotos", account != null && vm.arenaDisponivel) { confirmRemoteDelete = true }
        }
    }
    if (confirmRemoteDelete) AlertDialog(
        onDismissRequest = { confirmRemoteDelete = false },
        title = { Text("Apagar dados da Arena?") },
        text = { Text("Esta ação remove seus registros remotos e encerra a sessão. Ela não apaga os treinos deste aparelho.") },
        confirmButton = { TextButton(onClick = { confirmRemoteDelete = false; vm.apagarDadosRemotos() }) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = { confirmRemoteDelete = false }) { Text("Cancelar") } },
    )
}
