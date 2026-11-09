package com.liferpg.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import kotlin.math.roundToInt

@Composable internal fun RecipeDetail(recipe: Receita, dismiss: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var cooking by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val steps = remember(recipe) { recipe.etapas.ifEmpty { RecipeInstructions.etapas(recipe.instrucoes) } }
    fun start() { step = 0; finished = false; cooking = true }
    Dialog(onDismissRequest = { if (cooking) cooking = false else dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(cooking) { cooking = false }
        Surface(Modifier.fillMaxSize(), color = Fundo) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal = 20.dp).widthIn(max = 640.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(if (cooking) "MODO COZINHAR" else "GUIA DE PREPARO", DouradoLoot)
                        Text(recipe.nome, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { if (cooking) cooking = false else dismiss() }) {
                        Icon(if (cooking) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close,
                            if (cooking) "Voltar à receita" else "Fechar receita")
                    }
                }
                if (cooking && steps.isNotEmpty()) {
                    val index = step.coerceIn(0, steps.lastIndex)
                    val stepScroll = rememberScrollState()
                    LaunchedEffect(index) { stepScroll.scrollTo(0) }
                    val progress by animateFloatAsState((index + 1f) / steps.size, tween(220), label = "recipeStepProgress")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = DouradoLoot)
                    Column(Modifier.weight(1f).verticalScroll(stepScroll).padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Eyebrow("ETAPA ${index + 1} DE ${steps.size}", TextoSecundario)
                        RecipeStep(steps[index], large = true)
                        Text("Avance quando terminar esta etapa. Voltar ou abrir um vídeo mantém seu lugar enquanto esta receita estiver aberta.", fontSize = 12.sp, lineHeight = 18.sp, color = TextoSecundario)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) IconButton(onClick = { step = index - 1 }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Etapa anterior") }
                        Box(Modifier.weight(1f)) {
                            Action(if (index == steps.lastIndex) "Finalizar preparo" else "Próxima etapa") {
                                if (index == steps.lastIndex) { finished = true; cooking = false; tab = 1 } else step = index + 1
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Ingredientes", "Preparo", "Dicas e vídeos").forEachIndexed { i, name -> Choice(name, tab == i) { tab = i } }
                    }
                    key(tab) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            when (tab) {
                                0 -> {
                                    recipe.imagem?.let { AsyncImage(it, recipe.nome, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop) }
                                    RecipeFacts(recipe)
                                    Text("Separe os ingredientes", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Marque o que já está na bancada. Isso não registra uma refeição no diário.", fontSize = 12.sp, lineHeight = 18.sp, color = TextoSecundario)
                                    Column(Modifier.animateContentSize()) {
                                        recipe.ingredientes.forEachIndexed { i, item ->
                                            Row(Modifier.fillMaxWidth().toggleableRecipe(i in checked) { checked = if (i in checked) checked - i else checked + i }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(i in checked, onCheckedChange = null)
                                                Text(item, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 21.sp, color = if (i in checked) TextoSecundario else TextoPrimario)
                                            }
                                        }
                                    }
                                    if (recipe.utensilios.isNotEmpty()) RecipeNote("Utensílios", recipe.utensilios.joinToString(" · "))
                                    Action("Ver modo de preparo") { tab = 1 }
                                }
                                1 -> {
                                    if (finished) RecipeNote("Preparo concluído", "Seu guia está completo. Nenhuma refeição foi adicionada automaticamente ao diário.")
                                    Text("${steps.size} etapas, no seu ritmo", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    if (recipe.idioma != "pt") RecipeNote("Instruções no idioma da fonte", "TheMealDB fornece este preparo em inglês. As etapas abaixo preservam as instruções originais, sem inventar tempos ou ingredientes.")
                                    Action(if (finished) "Cozinhar de novo" else "Começar passo a passo") { start() }
                                    steps.forEachIndexed { i, s ->
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Eyebrow("${i + 1} / ${steps.size}", DouradoLoot); RecipeStep(s) }
                                        HorizontalDivider(color = Borda)
                                    }
                                }
                                else -> {
                                    if (recipe.dicas.isNotEmpty()) {
                                        Text("Para acertar o preparo", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                        recipe.dicas.forEach { RecipeNote(null, it) }
                                    }
                                    Text("Aprenda em vídeo", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    if (recipe.videos.any { it.tecnica }) Text("Referências de técnica e montagem. Os vídeos podem usar outros ingredientes ou métodos; para esta porção, siga os pesos e as etapas do REM.", fontSize = 12.sp, lineHeight = 18.sp, color = TextoSecundario)
                                    if (recipe.videos.isEmpty()) Text("A fonte não disponibilizou um vídeo específico. Você pode procurar uma demonstração pelo nome do prato.", fontSize = 13.sp, lineHeight = 20.sp, color = TextoSecundario)
                                    recipe.videos.forEach { RecipeVideo(it) }
                                    RecipeLink("Procurar outros vídeos", "https://www.youtube.com/results?search_query=" + Uri.encode(recipe.nome + " modo de preparo"))
                                    RecipeNote("Fonte e valores nutricionais", recipe.fonte)
                                    recipe.fonteUrl?.let { RecipeLink("Abrir receita original", it) }
                                    if (recipe.id.startsWith("rem:")) {
                                        RecipeLink("Temperatura e ponto seguro dos alimentos", "https://www.foodsafety.gov/food-safety-charts/safe-minimum-internal-temperatures")
                                        RecipeLink("Referência de conservação", "https://www.foodsafety.gov/food-safety-charts/cold-food-storage-charts")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.toggleableRecipe(checked: Boolean, toggle: () -> Unit): Modifier =
    this.then(Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { toggle() }))

@Composable private fun RecipeFacts(recipe: Receita) {
    Panel {
        Eyebrow(if (recipe.nutrientes != null) "UMA PORÇÃO / ESTIMATIVA" else "RECEITA DA FONTE", DouradoLoot)
        recipe.nutrientes?.let { n ->
            Text("${n.calorias.roundToInt()} kcal", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Proteína ${n.proteina.roundToInt()} g · Carbo ${n.carbo.roundToInt()} g · Gordura ${n.gordura.roundToInt()} g", fontSize = 13.sp, lineHeight = 20.sp)
            Text("Pesos da parte comestível, no preparo indicado. Óleo, molhos, trocas e marcas alteram os valores.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
        } ?: Text("A fonte não fornece macros nem rendimento padronizado. Registre os ingredientes e a porção real no diário para estimar sua refeição.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
        if (recipe.minutosMontagem > 0) Text("${recipe.minutosMontagem} min de montagem com ingredientes prontos. Cozinhar e refrigerar levam tempo adicional.", fontSize = 12.sp, lineHeight = 18.sp, color = TextoSecundario)
    }
}

@Composable private fun RecipeStep(step: EtapaReceita, large: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(step.titulo, fontSize = if (large) 28.sp else 19.sp, lineHeight = if (large) 34.sp else 25.sp, fontWeight = FontWeight.SemiBold)
        Text(step.descricao, fontSize = if (large) 17.sp else 14.sp, lineHeight = if (large) 27.sp else 23.sp)
        step.ponto?.let { RecipeNote("Como saber que está pronto", it) }
    }
}

@Composable private fun RecipeNote(title: String?, text: String) {
    Panel {
        title?.let { Eyebrow(it, DouradoLoot) }
        Text(text, fontSize = 13.sp, lineHeight = 21.sp, color = TextoSecundario)
    }
}

@Composable private fun RecipeVideo(video: VideoReceita) {
    val context = LocalContext.current
    var failed by remember(video.url) { mutableStateOf(false) }
    val id = video.url.substringAfter("v=", "").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    Panel(Modifier.clickable(role = Role.Button, onClick = {
        failed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url))); false }.getOrDefault(true)
    })) {
        if (id != null) Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            AsyncImage("https://i.ytimg.com/vi/$id/hqdefault.jpg", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(shape = RoundedCornerShape(50), color = Fundo.copy(alpha = .85f)) { Icon(Icons.Outlined.PlayArrow, null, Modifier.padding(12.dp).size(30.dp), tint = TextoPrimario) }
        }
        Text(video.titulo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(video.autor + if (video.tecnica) " · referência de técnica" else " · vídeo fornecido pela fonte", fontSize = 12.sp, color = TextoSecundario)
        Text("Assistir no YouTube →", fontWeight = FontWeight.SemiBold, color = DouradoLoot, fontSize = 14.sp)
        if (failed) Text("Não foi possível abrir o vídeo. Confira se há um navegador ou YouTube instalado e tente novamente.", fontSize = 12.sp, color = DouradoLoot)
    }
}

@Composable private fun RecipeLink(label: String, url: String) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    OutlinedButton(onClick = {
        failed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); false }.getOrDefault(true)
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label, fontSize = 13.sp) }
    if (failed) Text("Não foi possível abrir o link. Confira se há um navegador ou YouTube instalado e tente novamente.", fontSize = 12.sp, lineHeight = 18.sp, color = DouradoLoot)
}
