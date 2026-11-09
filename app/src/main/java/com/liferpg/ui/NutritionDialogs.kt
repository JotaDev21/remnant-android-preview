package com.liferpg.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.liferpg.MainViewModel
import com.liferpg.data.Refeicao
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import kotlin.math.roundToInt

@Composable internal fun NutritionDialog(title: String, subtitle: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .86f
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 18.dp).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight), shape = RoundedCornerShape(27.dp), color = Superficie) {
            Column(Modifier.padding(21.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 23.sp); Text(subtitle, color = TextoSecundario, fontSize = 12.sp) }
                    IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Fechar") }
                }
                content()
            }
        }
    }
}

@Composable internal fun MealMomentSelector(value: String, change: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        MomentoRefeicao.entries.forEach { Choice(it.titulo, value == it.name) { change(it.name) } }
    }
}

@Composable internal fun FoodPortionDialog(vm: MainViewModel, food: AlimentoEncontrado?, initialGrams: Float, dismiss: () -> Unit, existing: Refeicao? = null) {
    var name by rememberSaveable { mutableStateOf(food?.nome ?: existing?.nome ?: "") }
    var grams by rememberSaveable { mutableStateOf(initialGrams.roundToInt().toString()) }
    var kcal by rememberSaveable { mutableStateOf(food?.takeIf { it.dadosAusentes and 2 == 0 }?.calorias?.let { Format.peso(it) } ?: "") }
    var prot by rememberSaveable { mutableStateOf(food?.takeIf { it.dadosAusentes and 1 == 0 }?.proteinaG?.let { Format.peso(it) } ?: "") }
    var carb by rememberSaveable { mutableStateOf(food?.takeIf { it.dadosAusentes and 4 == 0 }?.carboG?.let { Format.peso(it) } ?: "") }
    var fat by rememberSaveable { mutableStateOf(food?.takeIf { it.dadosAusentes and 8 == 0 }?.gorduraG?.let { Format.peso(it) } ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.categoria ?: "ALMOCO") }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun number(s: String) = s.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val portion = number(grams)?.takeIf { it in 1f..3000f }
    val values = listOf(number(kcal), number(prot), number(carb), number(fat))
    val valid = name.trim().length in 1..160 && portion != null && values.all { it != null } &&
        values[0]!! <= (if (food == null) portion * 10f + 5f else 1000f) &&
        values.drop(1).sumOf { it!!.toDouble() } <= (if (food == null) portion * 1.05f else 105f)
    val factor = if (food == null) 1f else (portion ?: 0f) / 100
    fun resolved(): AlimentoEncontrado {
        val per100 = if (food == null) 100f / portion!! else 1f
        val changed = food == null || food.dadosAusentes != 0 || name.trim() != food.nome ||
            kotlin.math.abs(values[0]!! - food.calorias) > .06f || kotlin.math.abs(values[1]!! - food.proteinaG) > .06f ||
            kotlin.math.abs(values[2]!! - food.carboG) > .06f || kotlin.math.abs(values[3]!! - food.gorduraG) > .06f
        return AlimentoEncontrado(name.trim(), food?.marca, values[1]!! * per100, values[0]!! * per100, values[2]!! * per100, values[3]!! * per100, food?.barcode,
            id = if (changed) "" else food?.id.orEmpty(), fonte = if (changed) "Informado por você" else food!!.fonte,
            fibraG = if (changed) null else food?.fibraG, imagemUrl = food?.imagemUrl)
    }
    NutritionDialog(if (existing != null) "Editar registro" else if (food == null) "Seu registro" else "Confira a porção",
        if (food == null) "Informe os macros da porção consumida." else "A base e o rótulo usam valores por 100 g.", { if (!busy) dismiss() }) {
        food?.imagemUrl?.let { AsyncImage(it, "Foto do produto", Modifier.size(78.dp).align(Alignment.CenterHorizontally)) }
        OutlinedTextField(name, { name = it.take(160) }, label = { Text("Alimento") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        food?.let { Text(it.fonte, color = DouradoLoot, fontSize = 11.sp) }
        if (food?.dadosAusentes != null && food.dadosAusentes != 0) Text("O produto tem dados ausentes. Preencha os campos vazios com os valores do rótulo por 100 g.", color = DouradoLoot, fontSize = 12.sp)
        NumberField("Quantidade consumida / g", grams) { grams = it.take(10) }
        if (food != null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(50, 100, 150, 200).forEach { g -> Choice("$g g", grams == g.toString()) { grams = g.toString() } }
        }
        Eyebrow(if (food == null) "MACROS DA PORÇÃO" else "TABELA POR 100 G", DouradoLoot)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(Modifier.weight(1f)) { NumberField("kcal", kcal) { kcal = it }; NumberField("Carbo / g", carb) { carb = it } }
            Column(Modifier.weight(1f)) { NumberField("Proteína / g", prot) { prot = it }; NumberField("Gordura / g", fat) { fat = it } }
        }
        if (valid) NutritionSurface {
            Text("Sua porção: ${(values[0]!! * factor).roundToInt()} kcal", color = DouradoLoot, fontWeight = FontWeight.SemiBold)
            Text("P ${(values[1]!! * factor).roundToInt()} g  ·  C ${(values[2]!! * factor).roundToInt()} g  ·  G ${(values[3]!! * factor).roundToInt()} g", fontSize = 12.sp)
        }
        MealMomentSelector(category) { category = it }
        val selectedDay by vm.diaNutricao.collectAsStateWithLifecycle()
        Text("Diário de ${java.time.LocalDate.ofEpochDay(existing?.epochDay ?: selectedDay).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))}", color = TextoSecundario, fontSize = 11.sp)
        Action(if (busy) "Salvando…" else if (existing != null) "Salvar alterações" else "Adicionar ao diário", valid && !busy) {
            val chosen = resolved(); val g = portion!!
            busy = true
            if (existing != null) {
                val f = g / 100
                vm.editarRefeicao(existing.copy(nome = chosen.nome, porcaoG = g, calorias = chosen.calorias * f, proteinaG = chosen.proteinaG * f,
                    carboG = chosen.carboG * f, gorduraG = chosen.gorduraG * f, fibraG = chosen.fibraG?.times(f), fonte = chosen.fonte, macrosCompletos = true, categoria = category), onError = { busy = false }) { busy = false; dismiss() }
            } else vm.adicionarAlimento(chosen, g, category, onError = { busy = false }) { busy = false; dismiss() }
        }
        OutlinedButton(onClick = { vm.salvarFavorito(resolved(), portion!!) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Salvar para repetir") }
        existing?.let {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { vm.repetirRefeicao(it); dismiss() }, enabled = !busy) { Text("Repetir no dia selecionado") }
                TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Excluir este registro?") }, text = { Text("${existing?.nome.orEmpty()} será removido do diário.") },
        confirmButton = { TextButton(onClick = { existing?.let(vm::removerRefeicao); dismiss() }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

@Composable internal fun MealEditDialog(vm: MainViewModel, meal: Refeicao, dismiss: () -> Unit) {
    val food = NutritionFoodCodec.daRefeicao(meal)
    FoodPortionDialog(vm, food, meal.porcaoG.takeIf { it > 0 } ?: 100f, dismiss, meal)
}

@Composable internal fun NutritionGoalsDialog(vm: MainViewModel, dismiss: () -> Unit) {
    val prefs by vm.prefsEfetivas.collectAsStateWithLifecycle()
    val profile by vm.perfil.collectAsStateWithLifecycle()
    val targets by vm.metasNutricao.collectAsStateWithLifecycle()
    var custom by rememberSaveable { mutableStateOf(prefs.metasNutricaoManuais) }
    var kcal by rememberSaveable { mutableStateOf(targets.calorias.takeIf { it > 0 }?.toString() ?: "") }
    var protein by rememberSaveable { mutableStateOf(targets.proteinaG.takeIf { it > 0 }?.toString() ?: "") }
    var carbs by rememberSaveable { mutableStateOf(targets.carboG.takeIf { it > 0 }?.toString() ?: "") }
    var fat by rememberSaveable { mutableStateOf(targets.gorduraG.takeIf { it > 0 }?.toString() ?: "") }
    var activity by rememberSaveable { mutableStateOf(prefs.atividadeNutri) }
    var goal by rememberSaveable { mutableStateOf(prefs.objetivoNutri) }
    var age by rememberSaveable { mutableStateOf(profile?.idade?.takeIf { it > 0 }?.toString() ?: "") }
    var height by rememberSaveable { mutableStateOf(profile?.alturaCm?.takeIf { it > 0 }?.toString() ?: "") }
    var sex by rememberSaveable { mutableStateOf(profile?.sexo ?: Sexo.OUTRO.name) }
    var saving by remember { mutableStateOf(false) }
    val preview = profile?.let { NutricaoEngine.calcular(it.copy(idade = age.toIntOrNull() ?: 0, alturaCm = height.toIntOrNull() ?: 0, sexo = sex), prefs.copy(metaCalorias = 0, metasNutricaoManuais = false, atividadeNutri = activity, objetivoNutri = goal)) }
    NutritionDialog("Suas referências", "Metas pessoais ou uma estimativa inicial transparente.", { if (!saving) dismiss() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice("Estimativa", !custom) { custom = false }; Choice("Personalizadas", custom) { custom = true } }
        if (custom) {
            NumberField("Energia diária / kcal", kcal) { kcal = it }
            NumberField("Proteína diária / g", protein) { protein = it }
            NumberField("Carboidratos diários / g", carbs) { carbs = it }
            NumberField("Gorduras diárias / g", fat) { fat = it }
            Text("Use os valores do seu planejamento. Calorias e macros podem não coincidir exatamente por arredondamentos.", color = TextoSecundario, fontSize = 12.sp)
        } else {
            Text("Dados para a estimativa", fontWeight = FontWeight.SemiBold)
            Text("Peso atual: ${Format.peso(profile?.pesoCorporalKg ?: 0f)} kg. Você pode atualizá-lo no perfil.", color = TextoSecundario, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Column(Modifier.weight(1f)) { NumberField("Idade / anos", age) { age = it.take(3) } }
                Column(Modifier.weight(1f)) { NumberField("Altura / cm", height) { height = it.take(3) } }
            }
            Text("Sexo usado na equação", fontSize = 12.sp, color = TextoSecundario)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Sexo.entries.forEach { option -> Choice(option.titulo, sex == option.name) { sex = option.name } } }
            Text("Atividade ao longo do dia", fontWeight = FontWeight.SemiBold)
            listOf("BAIXA" to "Pouca atividade", "LEVE" to "Levemente ativo", "MODERADA" to "Ativo", "ALTA" to "Muito ativo").forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().clickable { activity = id }, verticalAlignment = Alignment.CenterVertically) { RadioButton(activity == id, { activity = id }); Text(label, fontSize = 13.sp) }
            }
            Text("Objetivo de alimentação", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("MANTER" to "Manter", "GANHAR" to "Ganho gradual", "REDUZIR" to "Redução gradual").forEach { (id, label) -> Choice(label, goal == id) { goal = id } }
            }
            NutritionSurface {
                Text(if (preview?.calorias == null || preview.calorias == 0) "Complete os dados acima para estimar energia, ou use uma meta personalizada. A estimativa automática é para adultos." else "${preview.calorias} kcal · P ${preview.proteinaG} g · C ${preview.carboG} g · G ${preview.gorduraG} g", color = DouradoLoot, fontSize = 13.sp)
                Text("Mifflin–St Jeor + atividade diária. Ganho: +5%; redução: −10%. É um ponto de partida, ajustável, e não mede seu gasto real.", color = TextoSecundario, fontSize = 11.sp)
            }
        }
        val k = kcal.toIntOrNull(); val p = protein.toIntOrNull(); val c = carbs.toIntOrNull(); val g = fat.toIntOrNull()
        val valid = if (custom) k != null && k in 500..10_000 && p != null && p in 0..600 && c != null && c in 0..1500 && g != null && g in 0..500
            else (age.isBlank() || age.toIntOrNull()?.let { it in 0..120 } == true) && (height.isBlank() || height.toIntOrNull()?.let { it in 0..260 } == true)
        Action(if (saving) "Salvando…" else "Salvar referências", valid && !saving) {
            saving = true
            vm.salvarMetasNutri(prefs.copy(metasNutricaoManuais = custom, metaCalorias = if (custom) k!! else 0,
                metaProteinaG = if (custom) p!! else 0, metaCarboG = if (custom) c!! else 0, metaGorduraG = if (custom) g!! else 0,
                atividadeNutri = activity, objetivoNutri = goal), age.toIntOrNull() ?: 0, height.toIntOrNull() ?: 0, sex,
                onError = { saving = false }, onSaved = { saving = false; dismiss() })
        }
    }
}
