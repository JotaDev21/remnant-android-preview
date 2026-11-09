package com.liferpg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.liferpg.MainViewModel
import com.liferpg.data.Refeicao
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ProteinTint = Color(0xFF7CDEBA)
private val CarbTint = Color(0xFFD9AF72)
private val FatTint = Color(0xFFB9A1E8)

@Composable internal fun NutritionCommandCenter(vm: MainViewModel, open: (String) -> Unit) {
    val meals by vm.refeicoesNutricao.collectAsStateWithLifecycle()
    val history by vm.historicoNutricao.collectAsStateWithLifecycle()
    val water by vm.aguaNutricao.collectAsStateWithLifecycle()
    val targets by vm.metasNutricao.collectAsStateWithLifecycle()
    val day by vm.diaNutricao.collectAsStateWithLifecycle()
    val today by vm.hoje.collectAsStateWithLifecycle()
    val saved by vm.alimentosSalvos.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var goals by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var chosenJson by rememberSaveable { mutableStateOf<String?>(null) }
    var portion by rememberSaveable { mutableFloatStateOf(100f) }
    val total = remember(meals) { Nutrientes.das(meals) }
    val insights = remember(meals, targets) { NutritionInsights.diario(meals, targets) }
    Page("NUTRIÇÃO / O RITUAL DA RECUPERAÇÃO", "Forje seu\ncombustível.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Diário", "Tendência", "Guia").forEachIndexed { i, label -> Choice(label, tab == i) { tab = i } }
            }
            IconButton(onClick = { goals = true }) { Icon(Icons.Outlined.Tune, "Editar metas", tint = DouradoLoot) }
        }
        if (tab == 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { vm.selecionarDiaNutricao(day - 1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Dia anterior") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (day == today) "HOJE" else LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("pt-BR"))).uppercase(), color = DouradoLoot, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text(LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("pt-BR"))), color = TextoSecundario, fontSize = 12.sp)
                }
                IconButton(onClick = { vm.selecionarDiaNutricao(day + 1) }, enabled = day < today) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Próximo dia") }
            }
            NutritionSummary(total, targets, meals.any { !it.macrosCompletos }) { goals = true }
            if (meals.any { it.fibraG != null }) Text("Fibras conhecidas: ${meals.sumOf { (it.fibraG ?: 0f).toDouble() }.roundToInt()} g em ${meals.count { it.fibraG != null }} de ${meals.size} registros.", color = TextoSecundario, fontSize = 11.sp)
            NutritionPhotoEntry { open(RemnantRoute.FOOD_PHOTO) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NutritionShortcut(Icons.Outlined.Search, "Buscar alimento", "Base brasileira + marcas", Modifier.weight(1f)) { open(RemnantRoute.FOOD_SEARCH) }
                NutritionShortcut(Icons.Outlined.QrCodeScanner, "Ler rótulo", "Código de barras", Modifier.weight(1f)) { open(RemnantRoute.FOOD_SCANNER) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { manual = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Registro manual") }
                TextButton(onClick = { open(RemnantRoute.RECIPES) }) { Text("Receitas"); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(17.dp)) }
            }
            NutritionSurface {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WaterDrop, null, tint = Color(0xFF8ABFE5), modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Água", fontWeight = FontWeight.SemiBold); Text("${water.sumOf { it.ml }} ml registrados", fontSize = 12.sp, color = TextoSecundario) }
                    TextButton(onClick = vm::desfazerAgua, enabled = water.isNotEmpty()) { Text("Desfazer", fontSize = 11.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.registrarAgua(250) }, modifier = Modifier.weight(1f)) { Text("+ 250 ml") }
                    OutlinedButton(onClick = { vm.registrarAgua(500) }, modifier = Modifier.weight(1f)) { Text("+ 500 ml") }
                }
                Text("A necessidade varia com calor, atividade e orientação individual.", color = TextoSecundario, fontSize = 11.sp)
            }
            if (saved.isNotEmpty()) {
                Section("Seus alimentos salvos")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(saved, key = { it.id }) { r ->
                        Column(Modifier.width(160.dp).clip(RoundedCornerShape(16.dp)).background(SuperficieAlta).clickable {
                            chosenJson = r.alimentoJson; portion = r.porcaoG
                        }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Bookmark, null, tint = DouradoLoot, modifier = Modifier.size(20.dp))
                            Text(r.nome, fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${r.porcaoG.roundToInt()} g", color = TextoSecundario, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.apagarFavorito(r) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "Remover ${r.nome} dos salvos", Modifier.size(16.dp), tint = TextoSecundario) }
                            }
                        }
                    }
                }
            }
            Section("Refeições do dia", if (day != today) "Hoje" else null) { vm.selecionarDiaNutricao(today) }
            if (meals.isEmpty()) NutritionSurface {
                Icon(Icons.Outlined.Restaurant, null, Modifier.size(36.dp), tint = DouradoLoot)
                Text("Seu próximo ritual começa no prato.", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("Fotografe uma refeição ou escolha os alimentos. Tudo fica salvo neste aparelho.", color = TextoSecundario, fontSize = 13.sp)
            }
            MomentoRefeicao.entries.forEach { moment ->
                val rows = meals.filter { it.categoria == moment.name }
                if (rows.isNotEmpty()) NutritionSurface {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Eyebrow(moment.titulo, DouradoLoot); Text("${rows.sumOf { it.calorias.toDouble() }.roundToInt()} kcal", fontSize = 12.sp, color = TextoSecundario) }
                    rows.forEach { meal ->
                        Row(Modifier.fillMaxWidth().clickable { editingId = meal.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(meal.nome, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("${meal.porcaoG.roundToInt()} g · ${meal.calorias.roundToInt()} kcal", fontSize = 12.sp, color = TextoSecundario)
                                Text("P ${meal.proteinaG.roundToInt()}  ·  C ${if (meal.macrosCompletos || meal.carboG > 0) meal.carboG.roundToInt().toString() else "—"}  ·  G ${if (meal.macrosCompletos || meal.gorduraG > 0) meal.gorduraG.roundToInt().toString() else "—"} g", color = ProteinTint, fontSize = 11.sp)
                                if (meal.estimado) Text("Porção estimada por foto", color = DouradoLoot, fontSize = 10.sp)
                            }
                            Icon(Icons.Outlined.Edit, "Editar ${meal.nome}", tint = TextoSecundario, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            NutritionSurface {
                Eyebrow("REM / LEITURA DO DIÁRIO", ProteinTint)
                insights.forEach { Text(it, fontSize = 13.sp, color = TextoSecundario, lineHeight = 19.sp) }
            }
        } else if (tab == 1) NutritionTrend(history, today, targets, vm) { tab = 0 }
        else NutritionGuide()
    }
    if (goals) NutritionGoalsDialog(vm) { goals = false }
    if (manual) FoodPortionDialog(vm, null, 100f, { manual = false })
    chosenJson?.let { json -> runCatching { NutritionFoodCodec.ler(json) }.getOrNull()?.let { food -> FoodPortionDialog(vm, food, portion, { chosenJson = null }) } }
    meals.firstOrNull { it.id == editingId }?.let { meal -> MealEditDialog(vm, meal) { editingId = null } }
}

@Composable internal fun NutritionSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF222620), Color(0xFF141B19))))
        .border(1.dp, Color(0xFF454838).copy(alpha = .65f), RoundedCornerShape(22.dp)).padding(19.dp), verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
}

@Composable private fun NutritionSummary(total: Nutrientes, targets: MetasNutricao, incomplete: Boolean, goals: () -> Unit) {
    NutritionSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                val progress by animateFloatAsState(if (targets.calorias > 0) (total.calorias / targets.calorias).coerceIn(0f, 1f) else 0f, tween(800), label = "nutritionEnergy")
                Canvas(Modifier.fillMaxSize()) {
                    val inset = 10.dp.toPx(); val dim = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(Color(0xFF424B3E), 135f, 270f, false, Offset(inset, inset), dim, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Brush.sweepGradient(listOf(DouradoLoot.copy(alpha = .35f), DouradoLoot, Color(0xFFFFE6B5))), 135f, 270f * progress, false, Offset(inset, inset), dim, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(DouradoLoot.copy(alpha = .08f), size.minDimension * .32f, style = Stroke(1.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${total.calorias.roundToInt()}", fontSize = 29.sp, fontWeight = FontWeight.Bold); Text("kcal", fontSize = 11.sp, color = DouradoLoot) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Eyebrow("ENERGIA REGISTRADA", DouradoLoot)
                Text(if (targets.calorias == 0) "Defina sua referência" else "de ${targets.calorias} kcal", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(if (targets.calorias == 0) "Complete seu perfil ou informe uma meta pessoal." else {
                    val gap = targets.calorias - total.calorias.roundToInt()
                    if (gap >= 0) "$gap kcal até a referência" else "${-gap} kcal acima da referência"
                }, color = TextoSecundario, fontSize = 12.sp)
                TextButton(onClick = goals, contentPadding = PaddingValues(0.dp)) { Text("Ajustar metas", color = DouradoLoot, fontSize = 12.sp) }
            }
        }
        HorizontalDivider(color = DouradoLoot.copy(alpha = .12f))
        MacroRail("Proteína", total.proteina, targets.proteinaG, ProteinTint)
        MacroRail("Carboidratos", total.carbo, targets.carboG, CarbTint)
        MacroRail("Gorduras", total.gordura, targets.gorduraG, FatTint)
        if (incomplete) Text("Alguns registros antigos não têm todos os macros. Toque neles para completar.", color = DouradoLoot, fontSize = 11.sp)
    }
}

@Composable private fun MacroRail(label: String, value: Float, target: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(105.dp), color = color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${value.roundToInt()} g", fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(if (target > 0) "/ $target g" else "sem meta", fontSize = 11.sp, color = TextoSecundario) }
            Meter(if (target > 0) value / target else 0f, color)
        }
    }
}

@Composable private fun NutritionPhotoEntry(click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 4.dp, topEnd = 26.dp, bottomStart = 26.dp, bottomEnd = 4.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFF453822), Color(0xFF232E25))))
        .border(1.dp, DouradoLoot.copy(alpha = .45f), RoundedCornerShape(topStart = 4.dp, topEnd = 26.dp, bottomStart = 26.dp, bottomEnd = 4.dp))
        .clickable(onClick = click).padding(21.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(DouradoLoot.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(29.dp), tint = DouradoLoot) }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow("VISÃO DO REM", DouradoLoot)
            Text("Leia seu prato", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Fotografe, revise e veja os macros", fontSize = 12.sp, color = TextoSecundario)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp), tint = DouradoLoot)
    }
}

@Composable internal fun NutritionShortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, click: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(SuperficieAlta).clickable(onClick = click).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = DouradoLoot, modifier = Modifier.size(24.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, fontSize = 11.sp, color = TextoSecundario)
    }
}

@Composable private fun NutritionTrend(history: List<Refeicao>, today: Long, targets: MetasNutricao, vm: MainViewModel, selected: () -> Unit) {
    val days = (today - 6..today).map { day -> day to history.filter { it.epochDay == day } }
    val logged = days.filter { it.second.isNotEmpty() }
    val average = if (logged.isEmpty()) 0.0 else logged.sumOf { Nutrientes.das(it.second).calorias.toDouble() } / logged.size
    NutritionSurface {
        Eyebrow("ÚLTIMOS SETE DIAS", DouradoLoot)
        Text("${logged.size} dias registrados", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(if (logged.isEmpty()) "O histórico nasce com suas refeições." else "${average.roundToInt()} kcal em média nos dias com registro. Dias vazios ficam fora da média.", color = TextoSecundario, fontSize = 12.sp)
        days.forEach { (day, rows) ->
            val n = Nutrientes.das(rows)
            Row(Modifier.fillMaxWidth().clickable { vm.selecionarDiaNutricao(day); selected() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM")), Modifier.width(48.dp), fontSize = 12.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (rows.isEmpty()) "Sem registro" else "${n.calorias.roundToInt()} kcal · ${n.proteina.roundToInt()} g proteína", color = if (rows.isEmpty()) TextoSecundario else DouradoLoot, fontSize = 12.sp)
                    Meter(if (targets.calorias > 0) n.calorias / targets.calorias else n.calorias / days.maxOf { Nutrientes.das(it.second).calorias }.coerceAtLeast(1f), DouradoLoot)
                }
            }
        }
        Text("O diário pode estar incompleto; estes números descrevem seus registros, não uma avaliação da sua alimentação total.", fontSize = 11.sp, color = TextoSecundario)
    }
    Section("Repita seus rituais")
    history.filter { it.epochDay < today && it.porcaoG > 0 }.distinctBy { "${it.nome}:${it.porcaoG}:${it.calorias}" }.take(12).forEach { meal ->
        NutritionSurface {
            Text(meal.nome, fontWeight = FontWeight.SemiBold)
            Text("${meal.porcaoG.roundToInt()} g · ${meal.calorias.roundToInt()} kcal · ${meal.proteinaG.roundToInt()} g proteína", color = TextoSecundario, fontSize = 12.sp)
            Action("Registrar novamente no dia selecionado") { vm.repetirRefeicao(meal); vm.avisar("Refeição repetida no diário.") }
        }
    }
}

@Composable internal fun NutritionGuide() {
    val uri = LocalUriHandler.current
    listOf(
        Triple("Comida de verdade", "Use alimentos in natura ou minimamente processados como base. Varie os preparos e reserve tempo para comer.", "https://www.gov.br/saude/pt-br/composicao/saps/promocao-da-saude/guias-alimentares"),
        Triple("Proteína e recuperação", "A ISSN descreve 1,4 a 2,0 g/kg/dia como uma faixa para a maioria dos adultos saudáveis que treinam. Sua necessidade individual pode mudar. Distribuir fontes nas refeições ajuda a organizar a rotina.", "https://link.springer.com/article/10.1186/s12970-017-0177-8"),
        Triple("Como estimamos a energia", "A equação Mifflin–St Jeor usa peso, altura, idade e sexo para estimar o gasto em repouso. Multiplicamos por um fator de atividade escolhido por você; isso não mede seu gasto real. Ajuste a referência com seu acompanhamento individual.", "https://pubmed.ncbi.nlm.nih.gov/2305711/"),
        Triple("Cru não é cozido", "O preparo muda a concentração por 100 g. Pese a porção como será consumida e escolha a entrada correspondente. Adicione o óleo e os molhos usados.", "https://nepa.unicamp.br/publicacoes/"),
        Triple("De onde vêm os dados", "TACO: NEPA/UNICAMP, 4ª edição, 2011; 578 entradas com os quatro macros disponíveis. Valores por 100 g de parte comestível. Traços ficam abaixo do limite de quantificação. Produtos de marca: Open Food Facts, base colaborativa ODbL; confirme no rótulo.", "https://world.openfoodfacts.org/terms-of-use")
    ).forEach { (title, body, url) -> NutritionSurface {
        Eyebrow("BIBLIOTECA NUTRICIONAL", DouradoLoot)
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = TextoSecundario, fontSize = 13.sp, lineHeight = 20.sp)
        TextButton(onClick = { uri.openUri(url) }) { Text("Consultar a fonte") }
    } }
    TextButton(onClick = { uri.openUri("https://www.tbca.net.br/") }) { Text("Explorar a TBCA da USP") }
}

@Composable internal fun NutritionHomeCard(vm: MainViewModel, open: () -> Unit) {
    val total by vm.nutricaoHoje.collectAsStateWithLifecycle()
    val targets by vm.metasNutricao.collectAsStateWithLifecycle()
    NutritionSurface(Modifier.clickable(onClick = open)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Restaurant, null, tint = DouradoLoot, modifier = Modifier.size(31.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Eyebrow("NUTRIÇÃO", DouradoLoot); Text("O treino continua no prato", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = DouradoLoot, modifier = Modifier.size(20.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${total.calorias.roundToInt()} kcal", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${total.proteinaG.roundToInt()}${if (targets.proteinaG > 0) " / ${targets.proteinaG}" else ""} g proteína", fontSize = 13.sp, color = ProteinTint)
        }
        Meter(if (targets.proteinaG > 0) total.proteinaG / targets.proteinaG else 0f, ProteinTint)
        Text("Câmera do prato · diário · metas · alimentos salvos", fontSize = 11.sp, color = TextoSecundario)
    }
}
