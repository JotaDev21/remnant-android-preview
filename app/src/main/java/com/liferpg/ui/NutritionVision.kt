package com.liferpg.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.liferpg.MainViewModel
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

@Composable internal fun NutritionVision(vm: MainViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val draft by vm.pratoRevisao.collectAsStateWithLifecycle()
    val busy by vm.analisandoPrato.collectAsStateWithLifecycle()
    val saving by vm.salvandoPrato.collectAsStateWithLifecycle()
    val error by vm.erroPrato.collectAsStateWithLifecycle()
    val meals by vm.refeicoesNutricao.collectAsStateWithLifecycle()
    val targets by vm.metasNutricao.collectAsStateWithLifecycle()
    var picture by rememberSaveable { mutableStateOf<String?>(null) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf("ALMOCO") }
    var matching by rememberSaveable { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.descartarPrato(); picture = uri.toString()
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { vm.descartarPrato(); picture = captureUri }
    }
    fun capture() {
        runCatching {
            val dir = File(context.cacheDir, "nutrition_photos").apply { mkdirs() }
            val protectedName = draft?.fotoUri?.let(Uri::parse)?.lastPathSegment
            dir.listFiles()?.filter { it.name != protectedName }?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File.createTempFile("plate_", ".jpg", dir))
            captureUri = uri.toString(); camera.launch(uri)
        }.onFailure { vm.avisar("Não consegui abrir a câmera. Você também pode escolher uma foto.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture() else vm.avisar("Autorize a câmera ou escolha uma foto da galeria.")
    }
    val shownPhoto = picture ?: draft?.fotoUri
    val selected = draft?.itens.orEmpty().filter { it.incluido }
    val confirmed = selected.mapNotNull { item -> vm.alimentoDoPrato(item.alimentoId)?.let { it to item.gramas } }
    val total = confirmed.fold(Nutrientes()) { n, (food, grams) -> val f = (grams ?: 0f) / 100; n + Nutrientes(food.calorias * f, food.proteinaG * f, food.carboG * f, food.gorduraG * f) }
    val valid = selected.isNotEmpty() && selected.all { it.gramas != null && vm.alimentoDoPrato(it.alimentoId) != null }
    Page("VISÃO DO REM / RECONHECIMENTO DO PRATO", "Seu prato.\nSua recuperação.") {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(26.dp)).background(Brush.verticalGradient(listOf(Color(0xFF302C23), Color(0xFF141B19)))), contentAlignment = Alignment.Center) {
            if (shownPhoto != null) AsyncImage(Uri.parse(shownPhoto), "Prato escolhido para análise", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(70.dp), tint = DouradoLoot)
                Text("O prato inteiro, visto de cima", fontSize = 14.sp, color = DouradoLoot)
            }
            if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .65f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = DouradoLoot)
                    Text("O REM está reconhecendo os alimentos…", fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) capture()
                else permission.launch(Manifest.permission.CAMERA)
            }, enabled = !busy && !saving, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Fotografar") }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && !saving, modifier = Modifier.weight(1f)) { Text("Escolher foto") }
        }
        if (draft == null) {
            NutritionSurface {
                Eyebrow("ANTES DA LEITURA", DouradoLoot)
                Text("Boa luz, sem filtro e sem cobrir os alimentos. Se houver várias preparações misturadas, revise cada ingrediente depois.", color = TextoSecundario, fontSize = 13.sp, lineHeight = 19.sp)
                Text("Ao tocar em analisar, esta foto será enviada ao serviço do REM para reconhecimento. A quantidade é estimada; a revisão é sua. A análise usa a mesma cota diária do mentor.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
                Action(if (busy) "Analisando…" else "Analisar prato com REM", shownPhoto != null && !busy) { shownPhoto?.let { vm.analisarPrato(Uri.parse(it)) } }
            }
        }
        error?.let { Text(it, color = DouradoLoot, fontSize = 13.sp, lineHeight = 20.sp) }
        draft?.let { review ->
            Section("Revise os componentes")
            Text("A foto sugere os alimentos e a quantidade. Os macros vêm da tabela TACO após você escolher a referência. Confira o preparo e a porção de cada item.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 19.sp)
            if (review.observacao.isNotBlank()) Text(review.observacao, color = DouradoLoot, fontSize = 13.sp)
            review.itens.forEachIndexed { index, item ->
                val food = vm.alimentoDoPrato(item.alimentoId)
                NutritionSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.incluido, { vm.editarPrato(index, item.copy(incluido = it)) }, enabled = !saving)
                        Column(Modifier.weight(1f)) {
                            Text(item.nome, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Identificação: ${when (item.confianca) { "confirmada" -> "confirmada por você"; "alta" -> "mais provável"; "media" -> "confirme o preparo"; else -> "incerta, revise" }}", color = DouradoLoot, fontSize = 11.sp)
                        }
                    }
                    if (item.incluido) {
                        Text(food?.nome ?: "Sem referência confirmada", color = if (food == null) DouradoLoot else TextoSecundario, fontSize = 12.sp)
                        TextButton(onClick = { matching = index }, enabled = !saving) { Text(if (food == null) "Escolher alimento na tabela" else "Trocar alimento ou preparo") }
                        NumberField("Porção estimada / g", item.porcao) { if (!saving) vm.editarPrato(index, item.copy(porcao = it.take(10))) }
                        if (item.gramas == null) Text("Informe de 1 a 3000 g.", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        food?.let { f -> val scale = (item.gramas ?: 0f) / 100
                            Text("${(f.calorias * scale).roundToInt()} kcal · P ${(f.proteinaG * scale).roundToInt()} g · C ${(f.carboG * scale).roundToInt()} g · G ${(f.gorduraG * scale).roundToInt()} g", color = TextoSecundario, fontSize = 12.sp)
                        }
                    }
                }
            }
            TextButton(onClick = { matching = review.itens.size }, enabled = !saving && review.itens.size < 6) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Adicionar alimento que faltou")
            }
            NutritionSurface {
                Eyebrow(if (valid) "ESTIMATIVA DO PRATO REVISADO" else "SUBTOTAL DOS ITENS CONFIRMADOS", DouradoLoot)
                Text("${total.calorias.roundToInt()} kcal", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Proteína ${total.proteina.roundToInt()} g\nCarboidratos ${total.carbo.roundToInt()} g\nGorduras ${total.gordura.roundToInt()} g", fontSize = 14.sp, lineHeight = 23.sp)
                if (valid) {
                    val logged = Nutrientes.das(meals)
                    val remaining = Nutrientes((targets.calorias - logged.calorias).coerceAtLeast(0f), (targets.proteinaG - logged.proteina).coerceAtLeast(0f), (targets.carboG - logged.carbo).coerceAtLeast(0f), (targets.gorduraG - logged.gordura).coerceAtLeast(0f))
                    val vegetables = NutritionInsights.temVegetais(confirmed.map { it.first })
                    Eyebrow("COMO COMPLEMENTAR", DouradoLoot)
                    NutritionInsights.prato(total, remaining, vegetables).forEach { Text(it, color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp) }
                }
            }
            MealMomentSelector(category) { if (!saving) category = it }
            val day by vm.diaNutricao.collectAsStateWithLifecycle()
            Text("Diário de ${java.time.LocalDate.ofEpochDay(day).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))}", color = TextoSecundario, fontSize = 11.sp)
            Action(if (saving) "Salvando o prato…" else "Confirmar e registrar prato", valid && !saving && !busy) { vm.confirmarPrato(category, back) }
            TextButton(onClick = { vm.descartarPrato(); picture = null }, enabled = !saving && !busy) { Text("Descartar revisão") }
        }
    }
    if (matching >= 0) NutritionReferencePicker(vm, chosen = { food ->
        val item = draft?.itens?.getOrNull(matching)
        if (item != null) vm.editarPrato(matching, item.copy(nome = food.nome, alimentoId = food.id, confianca = "confirmada"))
        else vm.adicionarItemPrato(food)
        matching = -1
    }, dismiss = { matching = -1 })
}

@Composable private fun NutritionReferencePicker(vm: MainViewModel, chosen: (AlimentoEncontrado) -> Unit, dismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { vm.alimentosBrasileiros(query).take(24) }
    NutritionDialog("Escolha a referência", "Confira o nome e o preparo: cru, cozido ou grelhado.", dismiss) {
        OutlinedTextField(query, { query = it }, label = { Text("Buscar na tabela brasileira") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (query.isBlank()) Text("Ex.: arroz cozido, frango grelhado, feijão carioca.", color = TextoSecundario, fontSize = 12.sp)
        else if (results.isEmpty()) Text("Nenhuma correspondência. Você pode excluir este item da análise e registrá-lo manualmente pelo diário.", color = TextoSecundario, fontSize = 12.sp)
        results.forEach { food ->
            Column(Modifier.fillMaxWidth().clickable { chosen(food) }.padding(vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(food.nome, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${food.calorias.roundToInt()} kcal · P ${food.proteinaG.roundToInt()} · C ${food.carboG.roundToInt()} · G ${food.gorduraG.roundToInt()} g / 100 g", color = TextoSecundario, fontSize = 11.sp)
            }
        }
    }
}
