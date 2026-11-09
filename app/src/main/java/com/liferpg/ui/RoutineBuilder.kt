package com.liferpg.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.liferpg.MainViewModel
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate

private val weekdayNames = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

@Composable internal fun RoutineBuilder(vm: MainViewModel, open: (String) -> Unit) {
    val routines by vm.rotinas.collectAsStateWithLifecycle()
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    val prefs by vm.prefsEfetivas.collectAsStateWithLifecycle()
    val today by vm.hoje.collectAsStateWithLifecycle()
    val suggestion by vm.treinoSugerido.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableLongStateOf(0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var definitions by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<List<ItemRotinaDef>, String>(
        save = { list -> list.map { "${it.exercicioId}|${it.series}|${it.repMin}|${it.repMax}|${it.descansoSeg}" } },
        restore = { list -> list.map { row -> val d = row.split('|'); ItemRotinaDef(d[0], d[1].toInt(), d[2].toInt(), d[3].toInt(), d[4].toInt()) } },
    )) { mutableStateOf(listOf<ItemRotinaDef>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<Musculo?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }

    fun begin(routine: RotinaResolvida? = null) {
        editId = routine?.id ?: 0L
        name = routine?.nome.orEmpty()
        days = routine?.diasSemana.orEmpty()
        definitions = routine?.itens?.map {
            ItemRotinaDef(it.exercicio.id, it.series, it.repMin, it.repMax, it.descansoSeg)
        }.orEmpty()
        query = ""
        muscle = null
        equipment = null
        showCatalog = routine == null
        editing = true
    }

    if (!editing) {
        val todayRoutine = rotinaDoDia(routines, today)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { Header("PLANEJAMENTO SEMANAL", "Seu treino,\ndo seu jeito.") }
            item {
                Text(
                    "Escolha os dias, exercícios, máquinas, séries, repetições e descanso. O Remnant abre automaticamente o treino certo no dia configurado.",
                    color = TextoSecundario,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            }
            item {
                Action("Criar treino") { begin() }
            }
            if (suggestion.itens.isNotEmpty()) item {
                Panel {
                    Eyebrow("RECOMENDAÇÃO PARA VOCÊ", DouradoLoot)
                    Text(suggestion.titulo, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(suggestion.subtitulo, color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
                    Text("${suggestion.itens.size} exercícios · ${suggestion.volumeTotalSeries} séries · objetivo: ${Objetivo.seguro(prefs.objetivo).titulo}", color = TextoSecundario, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestion.itens.forEach { ExerciseThumbnail(it.exercicio, 62.dp) }
                    }
                    TextButton(onClick = {
                        begin(); name = suggestion.titulo
                        definitions = suggestion.itens.map { ItemRotinaDef(it.exercicio.id, it.series, it.repMin, it.repMax, it.descansoSeg) }
                        showCatalog = false
                    }) { Text("Personalizar e salvar →", color = DouradoLoot) }
                }
            }
            item { Section("Sua semana") }
            items((1..7).toList()) { day ->
                val routine = routines.firstOrNull { day in it.diasSemana }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = routine != null) { begin(routine) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(weekdayNames[day - 1], Modifier.width(38.dp), color = if (day == LocalDate.ofEpochDay(today).dayOfWeek.value) Neon else TextoPrimario, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(routine?.nome ?: "Descanso", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            routine?.let { "${it.itens.size} exercícios · ${it.volumeTotalSeries} séries" } ?: "Nenhum treino configurado",
                            color = TextoSecundario,
                            fontSize = 12.sp,
                        )
                    }
                    if (routine == todayRoutine) Eyebrow("Hoje", Neon)
                }
                HorizontalDivider(color = Borda.copy(alpha = .55f))
            }
            if (routines.isNotEmpty()) {
                item { Section("Seu arsenal · repetir quando quiser") }
                items(routines, key = { it.id }) { routine ->
                    Panel {
                        Text(routine.nome, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${routine.itens.size} exercícios · ${routine.volumeTotalSeries} séries${if (routine.diasSemana.isEmpty()) " · sem dia fixo" else ""}", color = TextoSecundario, fontSize = 12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            routine.itens.take(6).forEach { ExerciseThumbnail(it.exercicio, 54.dp) }
                        }
                        Button(onClick = { if (vm.iniciarSessao(routine.nome, routine.itens)) open(RemnantRoute.SESSION) }, modifier = Modifier.fillMaxWidth()) { Text("Treinar agora") }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { begin(routine) }) { Text("Editar") }
                            TextButton(onClick = { vm.duplicarRotina(routine) }) { Text("Criar variação") }
                        }
                    }
                }
            }
        }
        return
    }

    val selectedIds = definitions.map { it.exercicioId }.toSet()
    val equipmentFamilies = remember(catalog) { catalog.map { it.equipamento }.distinct().sorted() }
    val filtered = remember(catalog, query, muscle, equipment, selectedIds) {
        catalog.filter { ex ->
            ex.id !in selectedIds && BuscaExercicios.corresponde(ex, query) &&
                (muscle == null || ex.musculo == muscle) &&
                (equipment == null || ex.equipamento == equipment)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header("MONTAR TREINO", if (name.isBlank()) "Novo treino" else name) }
        item {
            OutlinedTextField(name, { name = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("Nome do treino") }, placeholder = { Text("Ex.: Costas e bíceps") }, singleLine = true)
        }
        item { Section("Dias deste treino") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weekdayNames.forEachIndexed { index, label ->
                    val day = index + 1
                    FilterChip(day in days, { days = if (day in days) days - day else days + day }, label = { Text(label) })
                }
            }
        }
        item {
            Text("Cada dia recebe um treino. Ao escolher um dia já ocupado, ele será movido para este treino.", color = TextoSecundario, fontSize = 11.sp)
        }
        item { Section("Exercícios escolhidos", "Adicionar máquinas") { showCatalog = !showCatalog } }
        if (definitions.isEmpty()) item { Text("Adicione ao menos um exercício ou máquina.", color = TextoSecundario) }
        items(definitions, key = { it.exercicioId }) { definition ->
            val ex = catalog.firstOrNull { it.id == definition.exercicioId } ?: return@items
            Column(Modifier.fillMaxWidth().background(Superficie, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseThumbnail(ex, 72.dp)
                    Column(Modifier.weight(1f)) {
                        Text(ex.nome, fontWeight = FontWeight.SemiBold)
                        Text("${ex.musculo.nomeExibicao} · ${ex.equipamento}", color = TextoSecundario, fontSize = 11.sp)
                    }
                    IconButton(onClick = { definitions = definitions.filterNot { it.exercicioId == definition.exercicioId } }) {
                        Icon(Icons.Outlined.Remove, "Remover exercício", tint = VermelhoPerigo)
                    }
                }
                RoutineStepper("Séries", definition.series, 1, 10) { value -> definitions = definitions.map { if (it.exercicioId == definition.exercicioId) it.copy(series = value) else it } }
                if (ex.modo == ModoMedida.TEMPO) {
                    RoutineStepper("Tempo aproximado", definition.repMax * 3, 15, 300, 15, "s") { value -> definitions = definitions.map { if (it.exercicioId == definition.exercicioId) it.copy(repMin = value / 3, repMax = value / 3) else it } }
                } else {
                    RoutineStepper("Repetições mínimas", definition.repMin, 1, 50) { value -> definitions = definitions.map { if (it.exercicioId == definition.exercicioId) it.copy(repMin = value.coerceAtMost(it.repMax)) else it } }
                    RoutineStepper("Repetições máximas", definition.repMax, 1, 50) { value -> definitions = definitions.map { if (it.exercicioId == definition.exercicioId) it.copy(repMax = value.coerceAtLeast(it.repMin)) else it } }
                }
                RoutineStepper("Descanso", definition.descansoSeg, 15, 600, 15, "s") { value -> definitions = definitions.map { if (it.exercicioId == definition.exercicioId) it.copy(descansoSeg = value) else it } }
            }
        }
        if (showCatalog) {
            item { Section("Catálogo · ${filtered.size} de ${catalog.size}") }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar exercício ou máquina") }, singleLine = true) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Todos os músculos", muscle == null) { muscle = null }
                    Musculo.entries.forEach { entry -> Choice(entry.nomeExibicao, muscle == entry) { muscle = entry } }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Todo equipamento", equipment == null) { equipment = null }
                    equipmentFamilies.forEach { entry -> Choice(entry, equipment == entry) { equipment = entry } }
                }
            }
            if (filtered.isEmpty()) item { Text("Nenhum exercício encontrado. Tente outro nome ou retire um filtro.", color = TextoSecundario) }
            items(filtered, key = { "catalog:${it.id}" }) { ex ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Superficie.copy(alpha = .72f)).clickable {
                    val objective = Objetivo.seguro(prefs.objetivo)
                    definitions = definitions + ItemRotinaDef(ex.id, objective.seriesBase, objective.repMin, objective.repMax, objective.descansoSeg)
                }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseThumbnail(ex, 82.dp)
                    Column(Modifier.weight(1f)) {
                        Text(ex.nome, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${ex.musculo.nomeExibicao} · ${ex.equipamento} · ${ex.local.nomeExibicao}", color = TextoSecundario, fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.Add, "Adicionar ${ex.nome}", tint = Neon)
                }
                HorizontalDivider(color = Borda.copy(alpha = .45f))
            }
        }
        item {
            Action("Salvar treino", name.isNotBlank() && days.isNotEmpty() && definitions.isNotEmpty()) {
                vm.salvarRotina(name, definitions, days, editId)
                val daysInOtherRoutines = routines
                    .filter { it.id != editId }
                    .flatMap { it.diasSemana }
                    .toSet() - days
                vm.atualizarPreferencias(
                    prefs.copy(diasPorSemana = (daysInOtherRoutines + days).size.coerceAtLeast(1))
                )
                editing = false
            }
        }
        item { TextButton(onClick = { editing = false }, Modifier.fillMaxWidth()) { Text("Cancelar") } }
    }
}

@Composable internal fun ExerciseThumbnail(exercise: Exercicio, size: androidx.compose.ui.unit.Dp) {
    val imagePath = exercise.imagens.firstOrNull()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(size).clip(shape).background(SuperficieAlta),
        contentAlignment = Alignment.Center,
    ) {
        if (imagePath == null) {
            ExerciseImageFallback(exercise)
        } else {
            SubcomposeAsyncImage(
                model = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$imagePath",
                contentDescription = "Demonstração de ${exercise.nome}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { ExerciseImageFallback(exercise) },
                error = { ExerciseImageFallback(exercise) },
            )
        }
        Text(
            exercise.equipamento.uppercase(),
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .68f)).padding(horizontal = 6.dp, vertical = 4.dp),
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable private fun ExerciseImageFallback(exercise: Exercicio) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.FitnessCenter, null, tint = Neon.copy(alpha = .75f), modifier = Modifier.size(20.dp))
        Text(exercise.musculo.nomeExibicao.uppercase(), color = TextoSecundario, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable private fun RoutineStepper(
    label: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    step: Int = 1,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = TextoSecundario)
        IconButton(onClick = { onChange((value - step).coerceAtLeast(minimum)) }, enabled = value > minimum) { Icon(Icons.Outlined.Remove, null) }
        Text("$value$suffix", Modifier.widthIn(min = 46.dp), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceAtMost(maximum)) }, enabled = value < maximum) { Icon(Icons.Outlined.Add, null) }
    }
}
