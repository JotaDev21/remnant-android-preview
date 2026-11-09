package com.liferpg.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.liferpg.data.CardioController
import androidx.activity.compose.rememberLauncherForActivityResult
import com.liferpg.data.CardioSession
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object CardioTokens {
    val gap = 12.dp; val inset = 22.dp; val small = 8.dp; val touch = 52.dp
    val radius = 20.dp; val icon = 24.dp; val orbit = 218.dp; val stroke = 3.dp
    val caption = 12.sp; val body = 14.sp; val title = 30.sp; val timer = 46.sp
}

private fun CardioActivity.icon(): ImageVector = when (this) {
    CardioActivity.RUN, CardioActivity.TREADMILL -> Icons.AutoMirrored.Outlined.DirectionsRun
    CardioActivity.WALK -> Icons.AutoMirrored.Outlined.DirectionsWalk
    CardioActivity.BIKE -> Icons.AutoMirrored.Outlined.DirectionsBike
    CardioActivity.ELLIPTICAL -> Icons.Outlined.SportsGymnastics
    CardioActivity.STAIRS -> Icons.Outlined.Stairs
    CardioActivity.ROW -> Icons.Outlined.Rowing
    CardioActivity.AEROBIC -> Icons.Outlined.MusicNote
    CardioActivity.ROPE -> Icons.Outlined.Sports
    CardioActivity.OTHER -> Icons.Outlined.FavoriteBorder
}

@Composable internal fun CardioPresence(controller: CardioController, open: () -> Unit) {
    val active by controller.active.collectAsStateWithLifecycle()
    val history by controller.history.collectAsStateWithLifecycle()
    val week = CardioEngine.week(history, LocalDate.now().toEpochDay())
    CardioEntry(active, week, open)
}

@Composable internal fun CardioEntry(active: CardioSession?, week: CardioSummary, open: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().heightIn(min = CardioTokens.touch).pressMotion(interaction)
        .clip(RoundedCornerShape(CardioTokens.radius)).clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = open)
        .padding(vertical = CardioTokens.gap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
        Icon(Icons.AutoMirrored.Outlined.DirectionsRun, null, Modifier.size(CardioTokens.icon), tint = TextoPrimario)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CardioTokens.small)) {
            Text(if (active == null) "Cardio" else "Retomar ${CardioActivity.valueOf(active.activity).title.lowercase()}", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(if (active != null) if (active.status == "PAUSED") "Sessão pausada e preservada" else "Cronômetro em andamento"
                else if (week.sessions == 0) "Corrida, caminhada e aeróbico" else "${week.minutes} min nesta semana · ${week.sessions} sessões",
                fontSize = CardioTokens.caption, color = TextoSecundario)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Abrir Cardio", Modifier.size(CardioTokens.icon), tint = TextoSecundario)
    }
}

@Composable internal fun CardioScreen(controller: CardioController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingActivity by rememberSaveable { mutableStateOf("") }
    var pendingGoal by rememberSaveable { mutableStateOf("") }
    var pendingTarget by rememberSaveable { mutableDoubleStateOf(0.0) }
    var pendingResume by rememberSaveable { mutableStateOf(false) }
    var permissionError by rememberSaveable { mutableStateOf<String?>(null) }
    fun startPending() {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val enabled = context.getSystemService(android.location.LocationManager::class.java).isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        if (!fine || !enabled) permissionError = if (!fine) "Para medir o percurso, autorize localização precisa. Você também pode usar o modo sem GPS." else "Ative a localização do celular para registrar o percurso."
        else {
            permissionError = null
            if (pendingResume) controller.resume()
            else if (pendingActivity.isNotEmpty()) controller.startGps(CardioActivity.valueOf(pendingActivity), CardioGoal.valueOf(pendingGoal), pendingTarget)
        }
        pendingActivity = ""; pendingResume = false
    }
    val gpsPermission = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { startPending() }
    fun requestGps(activity: CardioActivity?, goal: CardioGoal?, target: Double) {
        pendingActivity = activity?.name.orEmpty(); pendingGoal = goal?.name.orEmpty(); pendingTarget = target; pendingResume = activity == null
        val permissions = buildList {
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION); add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 29 && activity != CardioActivity.BIKE) add(android.Manifest.permission.ACTIVITY_RECOGNITION)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }.filter { androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (permissions.isEmpty()) startPending() else gpsPermission.launch(permissions.toTypedArray())
    }
    val active by controller.active.collectAsStateWithLifecycle()
    val history by controller.history.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val error by controller.error.collectAsStateWithLifecycle()
    val ready by controller.ready.collectAsStateWithLifecycle()
    val saved by controller.saved.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(controller.now()) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, controller) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = controller.now(); delay(1000) }
        }
    }
    var review by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved?.id) { if (saved != null) { review = false; manual = false } }
    CardioContent(active, history, busy || pendingActivity.isNotEmpty() || pendingResume, error ?: permissionError, ready, now, saved,
        { activity, goal, target -> permissionError = null; controller.start(activity, goal, target) }, controller::pause,
        { if (active?.source == "GPS") requestGps(null, null, 0.0) else controller.resume() },
        { controller.pause(); review = true }, { manual = true; permissionError = null; controller.clearError() }, controller::discard, controller::dismissSaved,
        gpsStart = { activity, goal, target -> requestGps(activity, goal, target) }, steps = { CardioSteps() }, route = { CardioRoutePanel(it) },
        permissionHelp = if (permissionError != null) { {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Ativar GPS") }
                TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Permissões") }
            }
        } } else null)
    val activeReview = active?.takeIf { review && it.status == "PAUSED" }
    if (activeReview != null || manual) {
        CardioFinishSheet(activeReview, now, busy, error,
            { if (!busy) { review = false; manual = false; controller.clearError() } }, controller::finish)
    }
}

@Composable internal fun CardioContent(active: CardioSession?, history: List<CardioSession>, busy: Boolean, error: String?, ready: Boolean,
    now: CardioClock, saved: CardioSession?, start: (CardioActivity, CardioGoal, Double) -> Unit, pause: () -> Unit,
    resume: () -> Unit, review: () -> Unit, manual: () -> Unit, discard: () -> Unit, dismissSaved: () -> Unit,
    gpsStart: ((CardioActivity, CardioGoal, Double) -> Unit)? = null, steps: (@Composable () -> Unit)? = null,
    route: (@Composable (CardioSession) -> Unit)? = null, permissionHelp: (@Composable () -> Unit)? = null) {
    var outdoor by rememberSaveable { mutableStateOf(true) }
    var activityName by rememberSaveable { mutableStateOf(CardioActivity.RUN.name) }
    var goalName by rememberSaveable { mutableStateOf(CardioGoal.TIME.name) }
    var target by rememberSaveable { mutableStateOf("20") }
    var picker by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var historyCount by rememberSaveable { mutableIntStateOf(10) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val activity = CardioActivity.valueOf(activityName)
    val goal = CardioGoal.valueOf(goalName)
    val today = java.time.Instant.ofEpochMilli(now.wall).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val week = CardioEngine.week(history, today)
    val targetNumber = if (goal == CardioGoal.FREE) 0.0 else target.replace(',', '.').toDoubleOrNull()
    val targetValid = targetNumber?.let { it.isFinite() && when (goal) { CardioGoal.FREE -> true; CardioGoal.TIME -> it in 1.0..360.0; CardioGoal.DISTANCE -> it in 0.1..200.0 } } == true
    val gpsRecording = ready && active?.source == "GPS" && route != null
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(CardioTokens.inset), verticalArrangement = Arrangement.spacedBy(CardioTokens.inset)) {
        item {
            if (gpsRecording) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("MOVIMENTO / AO AR LIVRE")
                Text(CardioActivity.valueOf(active!!.activity).title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            } else Header("MOVIMENTO / CARDIO", if (active == null) "Seu ritmo.\nSua jornada." else CardioActivity.valueOf(active.activity).title)
        }
        if (steps != null && active == null && saved == null) item { steps() }
        if (saved != null) item {
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
                Eyebrow("ATIVIDADE CONCLUÍDA")
                route?.invoke(saved)
                CardioRecordDetails(saved)
                Text(if (saved.accumulatedMs >= CardioEngine.STREAK_SECONDS * 1000L) "Seu movimento conta para a sequência de hoje."
                    else "Atividade salva. A partir de 5 minutos, cardio também conta para a sequência.", color = TextoSecundario, fontSize = CardioTokens.body)
                Action("Voltar ao Cardio", onClick = dismissSaved)
                HorizontalDivider(color = Borda)
            }
        }
        if (!ready) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = TextoPrimario); error?.let { CardioError(it) } }
        else if (active != null) {
            if (route != null && active.source == "GPS") item { route(active) }
            item { CardioLive(active, now, busy, pause, resume, review, showControls = !gpsRecording) }
            if (error != null) item { CardioError(error) }
            if (permissionHelp != null) item { permissionHelp() }
            item { TextButton(onClick = { confirmDiscard = true }, enabled = !busy) { Text("Descartar sessão") } }
        } else if (saved == null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
                    Eyebrow("ATIVIDADE")
                    Row(Modifier.fillMaxWidth().heightIn(min = CardioTokens.touch).clip(RoundedCornerShape(CardioTokens.radius))
                        .background(Superficie).clickable(enabled = !busy) { picker = true }.padding(CardioTokens.inset),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
                        Icon(activity.icon(), null, Modifier.size(32.dp), tint = TextoPrimario)
                        Column(Modifier.weight(1f)) { Text(activity.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(activity.subtitle, fontSize = CardioTokens.caption, color = TextoSecundario) }
                        Icon(Icons.Outlined.ExpandMore, "Escolher atividade", tint = TextoSecundario)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CardioTokens.small)) {
                        // Goal choices stay directly below the activity selection.
                        CardioGoal.entries.filter { it != CardioGoal.DISTANCE || activity.distance }.forEach { option ->
                            Choice(when (option) { CardioGoal.FREE -> "Livre"; CardioGoal.TIME -> "Tempo"; CardioGoal.DISTANCE -> "Distância" }, goal == option) {
                                goalName = option.name; target = if (option == CardioGoal.DISTANCE) "3" else "20"
                            }
                        }
                    }
                    if (gpsStart != null && activity in listOf(CardioActivity.RUN, CardioActivity.WALK, CardioActivity.BIKE)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(CardioTokens.small)) {
                            Choice("Ao ar livre · GPS", outdoor) { outdoor = true }
                            Choice("Sem GPS", !outdoor) { outdoor = false }
                        }
                        Text(if (outdoor) "GPS mede metros, quilômetros e ritmo. Percurso privado; passos pelo sensor quando disponível."
                            else "Use o tempo e informe a distância ao concluir.", fontSize = CardioTokens.caption, color = TextoSecundario)
                    }
                    AnimatedContent(goal, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "cardioGoal") { mode ->
                        if (mode == CardioGoal.FREE) Text("Siga no seu ritmo. Pause quando precisar.", fontSize = CardioTokens.body, color = TextoSecundario)
                        else OutlinedTextField(target, { target = it.take(8) }, Modifier.fillMaxWidth(), label = { Text(if (mode == CardioGoal.TIME) "Meta em minutos" else "Meta em quilômetros") },
                            singleLine = true, isError = target.isNotEmpty() && !targetValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            supportingText = { Text(if (mode == CardioGoal.TIME) "De 1 a 360 minutos" else "De 0,1 a 200 km · informe a distância ao concluir") })
                    }
                    CardioTrackIllustration(activity)
                    if (activity.distance && (gpsStart == null || !outdoor || activity !in listOf(CardioActivity.RUN, CardioActivity.WALK, CardioActivity.BIKE)))
                        Text("Distância informada por você ou pelo painel da máquina.", fontSize = CardioTokens.caption, color = TextoSecundario)
                    Action(if (busy) "Preparando…" else "Iniciar ${activity.title.lowercase()}", !busy && targetValid) {
                        if (gpsStart != null && outdoor && activity in listOf(CardioActivity.RUN, CardioActivity.WALK, CardioActivity.BIKE)) gpsStart(activity, goal, targetNumber!!)
                        else start(activity, goal, targetNumber!!)
                    }
                    TextButton(onClick = manual, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Icon(Icons.Outlined.EditNote, null, Modifier.size(CardioTokens.icon)); Spacer(Modifier.width(CardioTokens.small)); Text("Já fiz meu cardio · registrar")
                    }
                    if (error != null) CardioError(error)
                    permissionHelp?.invoke()
                }
            }
        }
        item { CardioWeek(week) }
        item { Section("Histórico de cardio") }
        if (history.isEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
                Icon(Icons.Outlined.Route, null, tint = TextoSecundario)
                Text("Seu primeiro movimento abre o histórico. Tempo, distância e ritmo ficam reunidos aqui.", color = TextoSecundario, fontSize = CardioTokens.body)
            }
        }
        items(history.take(historyCount), key = { it.id }) { row ->
            CardioHistoryRow(row) { detailId = row.id }
        }
        if (history.size > historyCount) item { TextButton(onClick = { historyCount += 10 }) { Text("Ver mais atividades") } }
        item { Text("5 minutos concluídos garantem a sequência. +2 XP por minuto completo, a partir de 5 minutos, até 120 XP de cardio por dia.", color = TextoSecundario, fontSize = CardioTokens.caption) }
    }
    if (gpsRecording) Surface(color = Fundo, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(color = Borda)
            CardioSessionControls(active, CardioEngine.elapsed(active, now), busy, pause, resume, review, showMetrics = true)
        }
    }
    }
    if (picker) CardioActivitySheet(activity, { picker = false }) { next ->
        activityName = next.name
        if (!next.distance && goal == CardioGoal.DISTANCE) { goalName = CardioGoal.TIME.name; target = "20" }
        picker = false
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { if (!busy) confirmDiscard = false }, title = { Text("Descartar este cardio?") },
        text = { Text("O tempo desta sessão será removido. Suas atividades já concluídas continuam no histórico.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { discard(); confirmDiscard = false }) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Manter sessão") } })
    history.firstOrNull { it.id == detailId }?.let { row -> CardioDetailSheet(row, route) { detailId = null } }
}

@Composable private fun CardioTrackIllustration(activity: CardioActivity) {
    val reveal = remember(activity) { Animatable(0f) }
    LaunchedEffect(activity) { reveal.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.fillMaxWidth().height(62.dp).clearAndSetSemantics { }) {
        val inset = CardioTokens.small.toPx(); val radius = size.height / 2 - inset
        for (lane in 0..2) {
            val step = lane * 6.dp.toPx()
            drawRoundRect(Borda, Offset(inset + step, inset + step), androidx.compose.ui.geometry.Size(size.width - 2 * (inset + step), size.height - 2 * (inset + step)),
                androidx.compose.ui.geometry.CornerRadius((radius - step).coerceAtLeast(1f)), style = Stroke(1.dp.toPx()))
        }
        val x = size.width * .2f + size.width * .6f * reveal.value
        drawLine(TextoPrimario, Offset(size.width * .2f, size.height / 2), Offset(x, size.height / 2), CardioTokens.stroke.toPx(), StrokeCap.Round)
        drawCircle(TextoPrimario, 5.dp.toPx(), Offset(x, size.height / 2))
    }
}

@Composable internal fun CardioLive(row: CardioSession, now: CardioClock, busy: Boolean, pause: () -> Unit, resume: () -> Unit, review: () -> Unit, showControls: Boolean = true) {
    val activity = CardioActivity.valueOf(row.activity)
    val elapsed = CardioEngine.elapsed(row, now)
    val goal = CardioGoal.valueOf(row.goal)
    val reached = when (goal) { CardioGoal.TIME -> elapsed >= row.target * 60_000; CardioGoal.DISTANCE -> (row.distanceKm ?: 0.0) >= row.target; else -> false }
    val progress by animateFloatAsState(when (goal) { CardioGoal.TIME -> (elapsed / (row.target * 60_000)).toFloat().coerceIn(0f, 1f)
        CardioGoal.DISTANCE -> ((row.distanceKm ?: 0.0) / row.target).toFloat().coerceIn(0f, 1f); else -> 0f }, tween(650), label = "cardioProgress")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CardioTokens.inset)) {
        Eyebrow(if (row.status == "PAUSED") "PAUSADO" else "EM MOVIMENTO")
        if (row.source == "GPS" && showControls) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Stat(CardioEngine.time(elapsed), "TEMPO ATIVO")
            Stat(CardioRouteEngine.meters((row.distanceKm ?: 0.0) * 1000), "DISTÂNCIA GPS")
        } else if (row.source != "GPS") Box(Modifier.size(CardioTokens.orbit).semantics(mergeDescendants = true) { }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize().padding(CardioTokens.small)) {
                drawArc(Borda, 135f, 270f, false, style = Stroke(CardioTokens.stroke.toPx(), cap = StrokeCap.Round))
                if (progress > 0) drawArc(TextoPrimario, 135f, 270f * progress, false, style = Stroke(CardioTokens.stroke.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
                Icon(activity.icon(), null, Modifier.size(32.dp), tint = TextoPrimario)
                Text(CardioEngine.time(elapsed), fontSize = if (elapsed >= 3_600_000) 32.sp else CardioTokens.timer, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                Text("TEMPO ATIVO", fontSize = CardioTokens.caption, color = TextoSecundario)
            }
        }
        AnimatedContent(reached, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "cardioMilestone") { done ->
            Text(if (done) "Meta ${if (goal == CardioGoal.TIME) "de tempo" else "de distância"} alcançada." else when (goal) {
                CardioGoal.TIME -> "Meta · ${if (row.target % 1 == 0.0) row.target.toInt().toString() else CardioEngine.km(row.target)} minutos"
                CardioGoal.DISTANCE -> "Meta · ${CardioEngine.km(row.target)} km" + if (row.source == "GPS") "" else " · distância ao concluir"
                CardioGoal.FREE -> "Sessão livre"
            }, fontSize = CardioTokens.body, textAlign = TextAlign.Center)
        }
        if (row.source == "GPS") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.steps?.let { Stat(it.toString(), "passos da sessão") }
            }
            CardioEngine.pace((elapsed / 1000).toInt(), row.distanceKm)?.takeIf { activity.pace }?.let { Text("$it min/km · ritmo médio", fontSize = 18.sp) }
            if (activity == CardioActivity.BIKE) CardioEngine.speed((elapsed / 1000).toInt(), row.distanceKm)?.let { Text("$it km/h · velocidade média", fontSize = 18.sp) }
            val tracking by com.liferpg.data.CardioTrackingRuntime.status.collectAsStateWithLifecycle()
            Text(if (row.status == "PAUSED") "GPS pausado · retome para continuar o percurso" else when {
                tracking.id != row.id -> "Preparando registro GPS…"
                tracking.signal == "TRACKING" -> "GPS conectado · percurso sendo registrado"
                tracking.signal == "WEAK" -> "Sinal GPS fraco · distância aguarda uma posição precisa"
                else -> "Buscando sinal GPS preciso…"
            }, color = TextoSecundario, fontSize = CardioTokens.caption, textAlign = TextAlign.Center)
            if (row.steps == null && activity != CardioActivity.BIKE) Text(when (tracking.stepSignal) {
                "UNAVAILABLE" -> "Este aparelho não disponibilizou o contador de passos da sessão."
                "PERMISSION" -> "Passos da sessão precisam da permissão de atividade física."
                else -> "Aguardando a primeira leitura do sensor de passos."
            }, color = TextoSecundario, fontSize = CardioTokens.caption)
        }
        Text("O tempo continua em segundo plano. As pausas ficam fora do tempo ativo.", fontSize = CardioTokens.caption, color = TextoSecundario, textAlign = TextAlign.Center)
        if (showControls) CardioSessionControls(row, elapsed, busy, pause, resume, review)
    }
}

@Composable internal fun CardioSessionControls(row: CardioSession, elapsed: Long, busy: Boolean, pause: () -> Unit, resume: () -> Unit, review: () -> Unit, showMetrics: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showMetrics) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(CardioEngine.time(elapsed), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("TEMPO ATIVO", fontSize = 11.sp, color = TextoSecundario)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(CardioRouteEngine.meters((row.distanceKm ?: 0.0) * 1000), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("DISTÂNCIA GPS", fontSize = 11.sp, color = TextoSecundario)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
            OutlinedButton(onClick = if (row.status == "PAUSED") resume else pause, enabled = !busy && elapsed < CardioEngine.MAX_MS,
                contentPadding = PaddingValues(CardioTokens.gap), modifier = Modifier.weight(1f).heightIn(min = CardioTokens.touch)) {
                Icon(if (row.status == "PAUSED") Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null)
                Text(if (row.status == "PAUSED") "Retomar" else "Pausar")
            }
            Button(onClick = review, enabled = !busy && elapsed >= 60_000, contentPadding = PaddingValues(CardioTokens.gap), modifier = Modifier.weight(1f).heightIn(min = CardioTokens.touch)) { Text("Concluir") }
        }
        if (elapsed < 60_000) Text("Você pode concluir a partir de 1 minuto.", fontSize = CardioTokens.caption, color = TextoSecundario)
        if (elapsed >= CardioEngine.MAX_MS) Text("Limite de 6 horas alcançado. Conclua para salvar seu registro.", fontSize = CardioTokens.caption, color = TextoSecundario)
    }
}

@Composable internal fun CardioWeek(summary: CardioSummary) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(450)) }
    Column(verticalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
        HorizontalDivider(color = Borda)
        Eyebrow("SEU MOVIMENTO NESTA SEMANA")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(summary.minutes.toString(), "minutos ativos")
            Stat(CardioEngine.km(summary.distanceKm), "km registrados")
            Stat(summary.sessions.toString(), "sessões")
        }
        val highest = summary.dailyMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            summary.dailyMinutes.forEachIndexed { index, minutes ->
                val fraction by animateFloatAsState(minutes.toFloat() / highest, tween(500), label = "cardioDay$index")
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CardioTokens.small),
                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "${listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")[index]}: $minutes minutos de cardio" }) {
                    Box(Modifier.height(64.dp).width(CardioTokens.gap), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(CardioTokens.small)).background(SuperficieAlta))
                        Box(Modifier.fillMaxWidth().height((4 + fraction * reveal.value * 60).dp).clip(RoundedCornerShape(CardioTokens.small)).background(if (minutes > 0) TextoPrimario else Borda))
                    }
                    Text(listOf("S", "T", "Q", "Q", "S", "S", "D")[index], fontSize = CardioTokens.caption, color = TextoSecundario)
                }
            }
        }
    }
}

@Composable private fun CardioHistoryRow(row: CardioSession, open: () -> Unit) {
    val activity = CardioActivity.valueOf(row.activity)
    val date = LocalDate.ofEpochDay(row.epochDay).format(DateTimeFormatter.ofPattern("dd MMM"))
    Row(Modifier.fillMaxWidth().heightIn(min = CardioTokens.touch).clip(RoundedCornerShape(CardioTokens.radius)).clickable(onClick = open)
        .padding(vertical = CardioTokens.gap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
        Icon(activity.icon(), null, Modifier.size(CardioTokens.icon), tint = TextoPrimario)
        Column(Modifier.weight(1f)) {
            Text(activity.title, fontSize = CardioTokens.body, fontWeight = FontWeight.SemiBold)
            Text("$date · ${CardioEngine.time(row.accumulatedMs)}" + (row.distanceKm?.let { " · ${CardioEngine.km(it)} km" } ?: ""), fontSize = CardioTokens.caption, color = TextoSecundario)
        }
        Icon(Icons.Outlined.ChevronRight, "Detalhes de ${activity.title}", tint = TextoSecundario)
    }
    HorizontalDivider(color = Borda)
}

@Composable internal fun CardioRecordDetails(row: CardioSession) {
    val activity = CardioActivity.valueOf(row.activity)
    Column(verticalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
        Text(activity.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(CardioEngine.time(row.accumulatedMs), fontSize = CardioTokens.timer, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CardioTokens.inset)) {
            row.distanceKm?.let { Stat(CardioEngine.km(it), if (row.source == "GPS") "km · GPS" else "km informados") }
            val seconds = (row.accumulatedMs / 1000).toInt()
            if (activity.pace) CardioEngine.pace(seconds, row.distanceKm)?.let { Stat(it, "min/km · média") }
            else CardioEngine.speed(seconds, row.distanceKm)?.let { Stat(it, "km/h · média") }
        }
        row.steps?.let { Text("$it passos nesta sessão", fontSize = CardioTokens.body) }
        Text("+${row.xpGained} XP · ${when (row.source) { "GPS" -> "Percurso GPS"; "TIMER" -> "Cronômetro do app"; else -> "Registro manual" }}", fontSize = CardioTokens.body)
        if (row.effort.isNotEmpty()) Text("Esforço percebido · ${CardioEffort.valueOf(row.effort).title}", fontSize = CardioTokens.body, color = TextoSecundario)
        if (row.goal == CardioGoal.TIME.name || row.goal == CardioGoal.DISTANCE.name) {
            val achieved = if (row.goal == CardioGoal.TIME.name) row.accumulatedMs >= row.target * 60_000 else row.distanceKm?.let { it >= row.target }
            Text(if (achieved == true) "Meta alcançada" else if (achieved == null) "Distância não informada · meta sem confirmação" else "Movimento registrado · meta ainda não alcançada",
                fontSize = CardioTokens.caption, color = TextoSecundario)
        }
        if (row.note.isNotBlank()) Text(row.note, fontSize = CardioTokens.body)
    }
}

@Composable private fun CardioError(message: String) { Text(message, color = MaterialTheme.colorScheme.error, fontSize = CardioTokens.body,
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CardioActivitySheet(selected: CardioActivity, dismiss: () -> Unit, choose: (CardioActivity) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Fundo) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.8f), contentPadding = PaddingValues(CardioTokens.inset), verticalArrangement = Arrangement.spacedBy(CardioTokens.small)) {
            item { Header("QUAL É O MOVIMENTO?", "Escolha seu cardio") }
            items(CardioActivity.entries) { activity ->
                Row(Modifier.fillMaxWidth().heightIn(min = CardioTokens.touch).clip(RoundedCornerShape(CardioTokens.radius))
                    .background(if (selected == activity) SuperficieAlta else Fundo).clickable { choose(activity) }.padding(CardioTokens.gap),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
                    Icon(activity.icon(), null, Modifier.size(32.dp), tint = TextoPrimario)
                    Column(Modifier.weight(1f)) { Text(activity.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text(activity.subtitle, fontSize = CardioTokens.caption, color = TextoSecundario) }
                    if (selected == activity) Icon(Icons.Outlined.Check, "Selecionado", tint = TextoPrimario)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CardioDetailSheet(row: CardioSession, route: (@Composable (CardioSession) -> Unit)? = null, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Fundo) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(CardioTokens.inset).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(CardioTokens.inset)) {
            route?.invoke(row)
            CardioRecordDetails(row)
            OutlinedButton(onClick = dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = CardioTokens.touch)) { Text("Fechar detalhes") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CardioFinishSheet(active: CardioSession?, now: CardioClock, busy: Boolean, error: String?, dismiss: () -> Unit,
    finish: (String, Double?, String, String, CardioSession?) -> Unit) {
    val key = active?.id ?: "manual"
    var manualId by rememberSaveable(key) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var activityName by rememberSaveable(key) { mutableStateOf(active?.activity ?: CardioActivity.RUN.name) }
    var distance by rememberSaveable(key) { mutableStateOf("") }
    var minutes by rememberSaveable(key) { mutableStateOf("") }
    var effort by rememberSaveable(key) { mutableStateOf("") }
    var note by rememberSaveable(key) { mutableStateOf("") }
    val activity = CardioActivity.valueOf(activityName)
    val distanceNumber = distance.replace(',', '.').toDoubleOrNull()
    val distanceValid = distance.isBlank() || distanceNumber?.let { it.isFinite() && it in 0.01..200.0 } == true
    val duration = if (active != null) active.accumulatedMs else minutes.toIntOrNull()?.times(60_000L) ?: 0L
    val valid = duration in 60_000..CardioEngine.MAX_MS && distanceValid
    ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Fundo) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = CardioTokens.inset), verticalArrangement = Arrangement.spacedBy(CardioTokens.gap)) {
                Header(if (active != null) "PAUSA PARA REGISTRAR" else "REGISTRO MANUAL / HOJE", if (active != null) "Seu movimento\nficou marcado." else "Fez seu cardio?")
                if (active == null) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CardioTokens.small)) {
                        CardioActivity.entries.forEach { option -> Choice(option.title, activity == option) { activityName = option.name; distance = "" } }
                    }
                    OutlinedTextField(minutes, { minutes = it.take(3) }, Modifier.fillMaxWidth(), label = { Text("Duração em minutos") },
                        singleLine = true, isError = minutes.isNotEmpty() && duration !in 60_000..CardioEngine.MAX_MS,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("De 1 a 360 minutos · registro de hoje") })
                } else {
                    Text(activity.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(CardioEngine.time(duration), fontSize = CardioTokens.timer, fontWeight = FontWeight.SemiBold)
                }
                if (active?.source == "GPS") {
                    Text("${CardioRouteEngine.meters((active.distanceKm ?: 0.0) * 1000)} medidos por GPS", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    active.steps?.let { Text("$it passos nesta sessão", color = TextoSecundario) }
                    Text("O percurso foi salvo no aparelho. A distância medida fica no histórico.", fontSize = CardioTokens.caption, color = TextoSecundario)
                } else if (activity.distance) {
                    OutlinedTextField(distance, { distance = it.take(8) }, Modifier.fillMaxWidth(), label = { Text("Distância em km · opcional") },
                        singleLine = true, isError = !distanceValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("Use o painel da máquina ou sua medição. Deixe em branco se não souber.") })
                    if (distanceValid && duration > 0 && distanceNumber != null) Text(
                        if (activity.pace) "Ritmo médio · ${CardioEngine.pace((duration / 1000).toInt(), distanceNumber)} min/km"
                        else "Velocidade média · ${CardioEngine.speed((duration / 1000).toInt(), distanceNumber)} km/h", fontSize = CardioTokens.body)
                }
                Eyebrow("COMO FOI? / OPCIONAL")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CardioTokens.small)) {
                    CardioEffort.entries.forEach { option -> Choice(option.title, effort == option.name) { effort = if (effort == option.name) "" else option.name } }
                }
                OutlinedTextField(note, { note = it.take(400) }, Modifier.fillMaxWidth(), label = { Text("Anotações · opcional") }, minLines = 2,
                    supportingText = { Text("${note.length}/400") })
                Text("Atividades concluídas a partir de 5 minutos contam para a sequência. A distância não altera o XP.", fontSize = CardioTokens.caption, color = TextoSecundario)
                if (error != null) CardioError(error)
                Spacer(Modifier.height(CardioTokens.small))
            }
            Column(Modifier.fillMaxWidth().background(Fundo).padding(CardioTokens.inset), verticalArrangement = Arrangement.spacedBy(CardioTokens.small)) {
                Action(if (busy) "Salvando…" else "Salvar cardio", !busy && valid) {
                    val manualRow = if (active == null) CardioEngine.start(activity, CardioGoal.FREE, 0.0, now).copy(id = manualId,
                        status = "PAUSED", source = "MANUAL", accumulatedMs = duration, startedAt = now.wall - duration) else null
                    finish(active?.id ?: manualId, if (activity.distance) distanceNumber else null, effort, note, manualRow)
                }
                TextButton(onClick = dismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (active == null) "Cancelar" else "Voltar à sessão pausada") }
            }
        }
    }
}
