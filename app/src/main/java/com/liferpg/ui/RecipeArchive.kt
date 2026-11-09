package com.liferpg.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.liferpg.MainViewModel
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import kotlin.math.roundToInt

@Composable internal fun RecipeArchive(vm: MainViewModel) {
    val online by vm.receitas.collectAsStateWithLifecycle()
    val loading by vm.buscandoReceitas.collectAsStateWithLifecycle()
    val error by vm.erroReceitas.collectAsStateWithLifecycle()
    val meals by vm.refeicoesNutricao.collectAsStateWithLifecycle()
    val targets by vm.metasNutricao.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("todas") }
    var category by rememberSaveable { mutableStateOf("todas") }
    var visibleCount by rememberSaveable { mutableIntStateOf(12) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val local = remember(vm) { vm.receitasLocais }
    val logged = Nutrientes.das(meals)
    val remaining = Nutrientes(proteina = (targets.proteinaG - logged.proteina).coerceAtLeast(0f), carbo = (targets.carboG - logged.carbo).coerceAtLeast(0f))
    val suggestions = remember(local, query, filter, category, remaining) {
        RecipeBook.recomendar(RecipeBook.filtrar(RecipeBook.filtrar(local, query, category), "", filter), remaining)
    }
    LaunchedEffect(query, filter, category) { visibleCount = 12 }
    val keyboard = LocalSoftwareKeyboardController.current
    Page("NUTRIÇÃO / RECEITAS", "Cozinhar bem.\nSem complicar.") {
        NutritionSurface {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(34.dp), tint = DouradoLoot)
                Column(Modifier.weight(1f)) {
                    Eyebrow("SEU LIVRO DE RECEITAS", DouradoLoot)
                    Text("${local.size} guias completos, disponíveis offline", color = TextoSecundario, fontSize = 12.sp)
                }
            }
            Text(when {
                remaining.proteina > 20 -> "As opções com mais proteína aparecem primeiro para complementar sua meta registrada."
                remaining.carbo > 40 -> "As opções com mais carboidratos aparecem primeiro para complementar sua meta registrada."
                else -> "Escolha uma ideia que combine com seus ingredientes, horário e rotina."
            }, fontSize = 13.sp, color = TextoSecundario, lineHeight = 19.sp)
            OutlinedTextField(query, { query = it; vm.limparReceitasOnline() }, label = { Text("O que você quer preparar?") },
                placeholder = { Text("Frango, aveia, ovos…") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; vm.limparReceitasOnline() }) { Icon(Icons.Outlined.Close, "Limpar busca") }
                })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("todas" to "Todas", "cafe" to "Café da manhã", "lanche" to "Lanches", "refeicao" to "Refeições", "massas" to "Massas", "saladas" to "Saladas", "cuscuz" to "Cuscuz").forEach { (id, title) -> Choice(title, category == id) { category = id } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("todas" to "Sem restrição", "proteina" to "20 g+ proteína", "vegetal" to "Sem carne", "rapida" to "Até 5 min de montagem").forEach { (id, title) -> Choice(title, filter == id) { filter = id } }
            }
        }
        NutritionSurface {
            Eyebrow("EXPLORE O CATÁLOGO ONLINE", DouradoLoot)
            Text(if (query.isBlank()) "Explore outras receitas sem precisar digitar. Ou busque por frango, arroz, ovos e outros ingredientes."
                else "Ingredientes comuns em português são reconhecidos. Outros pratos podem precisar do nome em inglês.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
            Action(if (loading) "Cancelar busca" else if (query.isBlank()) "Explorar receitas online" else "Buscar online") {
                keyboard?.hide()
                if (loading) vm.limparReceitasOnline() else vm.buscarReceitas(query)
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = DouradoLoot)
                Text("Consultando TheMealDB…", fontSize = 12.sp, color = TextoSecundario)
            }
            error?.let { Text(it, color = DouradoLoot, fontSize = 12.sp) }
            Text("TheMealDB · preparos no idioma original · vídeos quando fornecidos pela fonte", color = TextoSecundario, fontSize = 11.sp)
        }
        if (online.isNotEmpty()) {
            Section("${online.size} receitas online encontradas")
            online.forEach { recipe -> RecipeSuggestion(recipe) { selected = recipe.id } }
        }
        Section("${suggestions.size} receitas" + if (query.isBlank() && category == "todas") " para sua rotina" else " nesta seleção")
        if (suggestions.isEmpty()) NutritionSurface {
            Text("Não encontrei essa combinação nas ideias do REM.", fontWeight = FontWeight.Medium)
            Text("Tente um ingrediente por vez ou retire um filtro. Você também pode consultar receitas online abaixo.", color = TextoSecundario, fontSize = 13.sp)
            TextButton(onClick = { query = ""; filter = "todas"; category = "todas"; vm.limparReceitasOnline() }) { Text("Ver todas as receitas") }
        }
        suggestions.take(visibleCount).forEach { recipe -> RecipeSuggestion(recipe) { selected = recipe.id } }
        if (visibleCount < suggestions.size) Action("Ver mais ${minOf(12, suggestions.size - visibleCount)} receitas") { visibleCount += 12 }
    }
    (local + online).firstOrNull { it.id == selected }?.let { recipe ->
        key(recipe.id) { RecipeDetail(recipe) { selected = null } }
    }
}

@Composable private fun RecipeSuggestion(recipe: Receita, open: () -> Unit) {
    NutritionSurface(Modifier.clickable(onClick = open)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recipe.imagem != null) AsyncImage(recipe.imagem, null, Modifier.size(64.dp), contentScale = ContentScale.Crop)
            else Icon(when {
                "Sanduíche" in recipe.tags -> Icons.Outlined.LunchDining
                "Massas" in recipe.tags -> Icons.Outlined.DinnerDining
                "Café da manhã" in recipe.tags -> Icons.Outlined.BreakfastDining
                "Vegetal" in recipe.tags -> Icons.Outlined.Eco
                else -> Icons.Outlined.Restaurant
            }, null, Modifier.size(30.dp), tint = DouradoLoot)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(recipe.nome, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(if (recipe.minutosMontagem > 0) "${recipe.minutosMontagem} min montagem · ${recipe.etapas.size} etapas" else "TheMealDB · ${recipe.etapas.size} etapas · inglês", color = TextoSecundario, fontSize = 11.sp)
                if (recipe.videos.isNotEmpty()) Text(if (recipe.id.startsWith("rem:")) "Guia de preparo + vídeo de técnica" else "Inclui vídeo da receita", color = DouradoLoot, fontSize = 11.sp)
                else Text("Guia de preparo completo", color = DouradoLoot, fontSize = 11.sp)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Ver receita", Modifier.size(19.dp), tint = DouradoLoot)
        }
        recipe.nutrientes?.let { n ->
            Text("${n.calorias.roundToInt()} kcal  ·  P ${n.proteina.roundToInt()}  ·  C ${n.carbo.roundToInt()}  ·  G ${n.gordura.roundToInt()} g", color = Neon, fontSize = 12.sp)
        }
    }
}
