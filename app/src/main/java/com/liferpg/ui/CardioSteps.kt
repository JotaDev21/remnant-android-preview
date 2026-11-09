package com.liferpg.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.liferpg.Graph
import com.liferpg.data.DailySteps
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun CardioSteps() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(DailySteps()) }
    var loading by remember { mutableStateOf(false) }
    suspend fun refresh() {
        if (loading) return
        loading = true
        try { snapshot = Graph.health.stepsSnapshot() } finally { loading = false }
    }
    val permission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { scope.launch { refresh() } }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { refresh(); delay(60_000) } }
    }
    val prefs = remember { context.getSharedPreferences("cardio_steps", android.content.Context.MODE_PRIVATE) }
    var goal by remember { mutableIntStateOf(prefs.getInt("goal", 8000).coerceIn(1000, 30_000)) }
    StepsDashboard(if (snapshot.day == java.time.LocalDate.now()) snapshot else DailySteps(), goal, loading,
        { if (Graph.health.disponivel) permission.launch(Graph.health.permissaoPassos)
          else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) } },
        { scope.launch { refresh() } }, { goal = it; prefs.edit().putInt("goal", it).apply() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun StepsDashboard(snapshot: DailySteps, goal: Int, loading: Boolean,
    connect: () -> Unit, refresh: () -> Unit, changeGoal: (Int) -> Unit) {
    val format = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(goal) { mutableStateOf(goal.toString()) }
    val fraction by animateFloatAsState(((snapshot.count ?: 0).toFloat() / goal).coerceIn(0f, 1f), tween(800), label = "dailySteps")
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("PASSOS / HOJE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    drawArc(Borda, -90f, 360f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    if (fraction > 0) drawArc(TextoPrimario, -90f, fraction * 360, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
                Text(if (snapshot.count == null) "—" else "${(fraction * 100).toInt()}%", fontSize = 14.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(snapshot.count?.let(format::format) ?: "—", fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
                Text("Meta pessoal · ${format.format(goal)}", fontSize = 13.sp, color = TextoSecundario)
            }
        }
        Text(when (snapshot.status) {
            "READY" -> if ((snapshot.count ?: 0) >= goal) "Meta alcançada. Seu dia ganhou movimento." else "Health Connect · celular e relógio, sem somar registros duplicados."
            "PERMISSION" -> "Conecte seus passos. Você escolhe quais dados o REMNANT pode ler."
            "UNAVAILABLE" -> "Ative o Health Connect para acompanhar seus passos diários."
            "EMPTY" -> "Conectado. Ainda não há passos disponíveis hoje; o provedor pode levar alguns minutos para sincronizar."
            "ERROR" -> "Não consegui consultar seus passos. Tente atualizar."
            else -> "Consultando seus passos…"
        }, fontSize = 14.sp, color = TextoSecundario)
        if (snapshot.status in listOf("PERMISSION", "UNAVAILABLE")) Action(if (snapshot.status == "PERMISSION") "Conectar passos" else "Abrir Health Connect", !loading, connect)
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = refresh, enabled = !loading) { Text(if (loading) "Atualizando…" else "Atualizar") }
            TextButton(onClick = { edit = true }) { Text("Editar meta") }
        }
        if (snapshot.updatedAt > 0) Text("Atualizado às ${Instant.ofEpochMilli(snapshot.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))} · não gera XP automaticamente",
            fontSize = 12.sp, color = TextoSecundario)
        Text("Samsung Health: habilite o compartilhamento de passos com Health Connect nas configurações de saúde do celular.", fontSize = 12.sp, color = TextoSecundario)
        HorizontalDivider(color = Borda)
    }
    if (edit) ModalBottomSheet(onDismissRequest = { edit = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Fundo) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header("MOVIMENTO DIÁRIO", "Sua meta de passos")
            OutlinedTextField(draft, { draft = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text("Passos por dia") }, singleLine = true,
                supportingText = { Text("De 1.000 a 30.000 · escolha o que combina com você") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Action("Salvar meta", draft.toIntOrNull() in 1000..30_000) { changeGoal(draft.toInt()); edit = false }
            TextButton(onClick = { edit = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
    }
}
