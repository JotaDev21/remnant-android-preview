package com.liferpg.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.liferpg.MainViewModel
import com.liferpg.R
import com.liferpg.domain.CameraBoss
import com.liferpg.domain.Exercicio
import com.liferpg.domain.PushUpCounterResult
import com.liferpg.domain.PushUpPhase
import com.liferpg.domain.PushUpPoseSample
import com.liferpg.domain.PushUpRepEngine
import com.liferpg.domain.SquatPoseSample
import com.liferpg.domain.SquatRepEngine
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.liferpg.domain.CameraTrialState
import com.liferpg.domain.CameraTrials
import com.liferpg.domain.Patente
import com.liferpg.domain.BossDefeat
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

private val TrialStateSaver = listSaver<CameraTrialState, Any>(
    save = { listOf(it.rank.ordinal, it.attemptReps, it.totalReps, it.victories, it.deaths, it.bestRank, it.lastRepAt, it.revision) + it.completedSets },
    restore = { CameraTrialState(Patente.entries[(it[0] as Number).toInt().coerceIn(1, 14)],
        (it[1] as Number).toInt(), (it[2] as Number).toInt(), (it[3] as Number).toInt(),
        (it[4] as Number).toInt(), (it[5] as Number).toInt(), (it[6] as Number).toLong(), (it[7] as Number).toInt(),
        it.drop(8).map { item -> (item as Number).toInt() }) },
)

@Composable internal fun PushUpCounterScreen(vm: MainViewModel, exercise: Exercicio, back: () -> Unit) {
    val isSquat = exercise.id == CameraBoss.STONE_COLOSSUS.exerciseId
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var started by rememberSaveable(exercise.id) { mutableStateOf(false) }
    val boss = CameraBoss.forExercise(exercise.id)
    val rows by vm.cameraBosses.collectAsStateWithLifecycle()
    var rankChoice by rememberSaveable(exercise.id) { mutableStateOf("") }
    val selectedRank = Patente.entries.firstOrNull { it.name == rankChoice }
        ?: Patente.entries[(boss?.let { rows[it]?.trialRank } ?: 1).coerceIn(1, 14)]
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) started = true
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) permissionDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!started) {
        PushUpPreparation(
            exercise = exercise,
            boss = boss,
            rank = selectedRank,
            selectRank = { rankChoice = it.name },
            isSquat = isSquat,
            permissionDenied = permissionDenied,
            onStart = {
                if (hasPermission) started = true else permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
            back = back,
        )
        return
    }

    PushUpLiveCamera(
        vm = vm,
        exercise = exercise,
        isSquat = isSquat,
        initialRank = selectedRank,
        back = { started = false; back() },
        stop = { started = false },
    )
}

@Composable private fun PushUpPreparation(
    exercise: Exercicio,
    boss: CameraBoss?,
    rank: Patente,
    selectRank: (Patente) -> Unit,
    isSquat: Boolean,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    back: () -> Unit,
) {
    var rankMenu by remember { mutableStateOf(false) }
    val steps = if (isSquat) listOf(
        Triple(Icons.Outlined.PhoneAndroid, "Celular ao lado", "Apoie na altura do quadril, a 2–3 metros, com a lateral do corpo voltada à lente."),
        Triple(Icons.Outlined.AccessibilityNew, "Enquadre ombro a tornozelo", "Quadril, joelho e tornozelo da perna visível precisam caber no visor."),
        Triple(Icons.Outlined.CenterFocusStrong, "Calibre em pé", "Fique com as pernas estendidas e imóvel até o visor ficar verde."),
    ) else listOf(
        Triple(Icons.Outlined.PhoneAndroid, "Celular à sua frente", "Apoie a 1–2 metros, baixo e apontado para o peito."),
        Triple(Icons.Outlined.AccessibilityNew, "Enquadre só o tronco", "Cabeça, ombros, cotovelos e as duas mãos precisam aparecer."),
        Triple(Icons.Outlined.CenterFocusStrong, "Calibre no alto", "Estenda os braços e segure firme até o visor ficar verde."),
    )
    Column(
        Modifier.fillMaxSize().background(Fundo).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            Spacer(Modifier.width(8.dp))
            Eyebrow("REM / VISÃO DE COMBATE", Neon)
        }
        if (boss != null) {
            val enemy = CameraTrials.enemy(boss, rank)
            Box(Modifier.fillMaxWidth().height(255.dp).background(Brush.radialGradient(listOf(rank.corBrilho.copy(alpha = .15f), Color.Transparent)))) {
                Image(painterResource(trialArt(rank)), enemy.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Text(enemy.name, fontFamily = HuntSerif, color = Color(0xFFF4EAD7), fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${enemy.hp} ${if (isSquat) "agachamentos" else "flexões"} completos", color = HuntGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Em uma única tentativa", color = TextoSecundario, fontSize = 12.sp)
                }
                Box {
                    OutlinedButton(onClick = { rankMenu = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = rank.corBrilho)) {
                        Text(rank.titulo)
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = rankMenu, onDismissRequest = { rankMenu = false }, modifier = Modifier.heightIn(max = 330.dp)) {
                        Patente.ranqueadas.forEach { option ->
                            val foe = CameraTrials.enemy(boss, option)
                            DropdownMenuItem(text = { Text("${option.titulo} · ${foe.hp} reps", color = option.corBrilho) },
                                onClick = { selectRank(option); rankMenu = false })
                        }
                    }
                }
            }
            Text("Cada execução completa causa 1 dano. Após 15 segundos sem validar outra repetição, você cai e a vida do chefe reinicia. A vitória passa ao próximo chefe automaticamente.",
                color = TextoSecundario, fontSize = 13.sp, lineHeight = 19.sp)
        } else {
            Text("A câmera conta. Sua execução confirma.", fontSize = 30.sp, fontWeight = FontWeight.Black)
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF123235), Color(0xFF16221F))))
                .border(1.dp, Neon.copy(alpha = .35f), RoundedCornerShape(28.dp)).padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Neon.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Visibility, null, tint = Neon)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Validação local", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("O vídeo não é gravado nem enviado.", color = TextoSecundario, fontSize = 12.sp)
                    }
                }
                steps.forEachIndexed { index, (icon, title, description) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = if (index == 2) Neon else TextoPrimario, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text(description, color = TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
        if (permissionDenied) {
            Text("A permissão foi recusada. Toque novamente e permita a câmera para iniciar.", color = Color(0xFFFFC56E), fontSize = 13.sp)
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.Settings, null)
                Spacer(Modifier.width(9.dp))
                Text("Abrir permissões do aplicativo")
            }
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Fundo),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Outlined.PhotoCamera, null)
            Spacer(Modifier.width(10.dp))
            Text("Abrir câmera e calibrar", fontWeight = FontWeight.Black)
        }
        Text(
            "O contador ajuda na consistência, mas não substitui orientação profissional nem identifica dor ou lesão.",
            color = TextoSecundario, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable private fun PushUpLiveCamera(
    vm: MainViewModel,
    exercise: Exercicio,
    isSquat: Boolean,
    initialRank: Patente,
    back: () -> Unit,
    stop: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val pushEngine = remember { PushUpRepEngine() }
    val squatEngine = remember { SquatRepEngine() }
    val boss = remember(exercise.id) { CameraBoss.forExercise(exercise.id) }
    val bossRows by vm.cameraBosses.collectAsStateWithLifecycle()
    val bossProgress = boss?.let { bossRows[it] }
    val sessionId = rememberSaveable(exercise.id) { UUID.randomUUID().toString() }
    var trial by rememberSaveable(sessionId, stateSaver = TrialStateSaver) {
        mutableStateOf(CameraTrialState(rank = initialRank, revision = 1))
    }
    var savedReps by rememberSaveable(sessionId) { mutableIntStateOf(trial.totalReps) }
    val recalibrate = remember { AtomicInteger(savedReps) }
    var result by remember { mutableStateOf(emptyCounterResult(isSquat)) }
    var defeat by remember { mutableStateOf<BossDefeat?>(null) }
    var fell by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    fun checkpoint(next: CameraTrialState) {
        if (next == trial) return
        if (next.deaths > trial.deaths) {
            fell = true
            defeat = null
            recalibrate.set(next.totalReps)
        }
        if (next.victories > trial.victories) {
            defeat = BossDefeat(trial.rank.benchmarkIndex, next.victories - trial.victories, next.victories, next.rank.benchmarkIndex)
        }
        trial = next
        if (boss != null) vm.registrarProvaCamera(boss, sessionId, next)
    }
    LaunchedEffect(Unit) { if (boss != null) vm.registrarProvaCamera(boss, sessionId, trial) }
    LaunchedEffect(fell) { if (fell) { delay(2200); fell = false } }
    var useFrontCamera by rememberSaveable { mutableStateOf(true) }
    var soundEnabled by rememberSaveable { mutableStateOf(true) }
    var manualPause by rememberSaveable { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showSummary by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val registering by vm.registrando.collectAsStateWithLifecycle()
    LaunchedEffect(registering) { if (!registering) saving = false }
    val analysisPaused = remember { AtomicBoolean(false) }
    val count = maxOf(savedReps, result.repetitions)
    val running = foreground && !manualPause && !showSummary && cameraError == null
    SideEffect { analysisPaused.set(!running) }
    var elapsedSeconds by rememberSaveable(sessionId) { mutableIntStateOf(0) }
    val detector = remember {
        PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
        )
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 72) }

    LaunchedEffect(trial.victories) {
        if (foreground && soundEnabled && trial.victories > 0) tone.startTone(ToneGenerator.TONE_PROP_ACK, 220)
    }
    LaunchedEffect(foreground) {
        if (foreground) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                if (boss != null) checkpoint(trial.expire(now))
                delay(250)
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                foreground = false
                analysisPaused.set(true)
                tone.stopTone()
            } else if (event == Lifecycle.Event.ON_RESUME) foreground = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(running, useFrontCamera) {
        // Reset partial movements on the analyzer thread, preserving completed reps.
        if (running) recalibrate.set(count)
    }
    BackHandler {
        if (count > 0 || (bossProgress?.pendingReps ?: 0) > 0) run { if (boss != null) checkpoint(trial.interrupt()); showSummary = true } else back()
    }

    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    DisposableEffect(detector, tone) {
        onDispose {
            analysisPaused.set(true)
            detector.close()
            analysisExecutor.shutdown()
            tone.release()
        }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            delay(1_000)
            elapsedSeconds++
        }
    }
    var previousReps by remember { mutableIntStateOf(savedReps) }
    LaunchedEffect(result.repetitions) {
        if (result.repetitions > previousReps) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (soundEnabled && foreground) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 75)
            savedReps = result.repetitions
            if (boss != null) checkpoint(trial.accept(boss, result.repetitions, SystemClock.elapsedRealtime()))
        }
        previousReps = maxOf(previousReps, result.repetitions)
    }

    DisposableEffect(lifecycleOwner, useFrontCamera) {
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                provider = future.get().also { it.unbindAll() }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(960, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            )
                        ).build()
                    )
                    .build()
                    .also { stream ->
                        val safeWorker = java.util.concurrent.Executor { command ->
                            try { analysisExecutor.execute(command) }
                            catch (_: java.util.concurrent.RejectedExecutionException) { /* Screen already closed. */ }
                        }
                        stream.setAnalyzer(analysisExecutor, PushUpPoseAnalyzer(detector, pushEngine, squatEngine, isSquat, analysisPaused, recalibrate, safeWorker) { next ->
                            mainExecutor.execute { if (!disposed && !analysisPaused.get()) result = next }
                        })
                    }
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider?.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                cameraError = null
            }.onFailure { cameraError = "Não foi possível iniciar esta câmera." }
        }, mainExecutor)
        onDispose {
            disposed = true
            analysis?.clearAnalyzer()
            provider?.unbindAll()
        }
    }

    val stateColor by animateColorAsState(
        when {
            !result.tracking -> Color(0xFFFFB36B)
            result.phase == PushUpPhase.DOWN -> Neon
            result.phase == PushUpPhase.READY -> Color(0xFFBEEA77)
            result.phase == PushUpPhase.DESCENDING || result.phase == PushUpPhase.ASCENDING -> Color(0xFF79D9E2)
            else -> Color(0xFF79D9E2)
        }, tween(180), label = "trackingState",
    )
    val stateLabel = when {
        !result.tracking -> "ENQUADRANDO"
        result.phase == PushUpPhase.CALIBRATING -> "CALIBRANDO"
        result.phase == PushUpPhase.READY -> "PRONTO / DESCER"
        result.phase == PushUpPhase.DESCENDING -> "DESCENDO"
        result.phase == PushUpPhase.DOWN -> "FUNDO / SUBIR"
        result.phase == PushUpPhase.ASCENDING -> "SUBINDO"
        else -> "ALINHANDO"
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val arenaHeight = (maxHeight * .28f).coerceIn(145.dp, 220.dp)
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xDB0E0C12), Color.Black.copy(alpha = .12f), Color.Transparent, Color(0xF00B0C10)))))
        FramingGuide(stateColor, result.tracking && running, isSquat)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SmallIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Encerrar caçada") {
                        if (count > 0 || (bossProgress?.pendingReps ?: 0) > 0) run { if (boss != null) checkpoint(trial.interrupt()); showSummary = true } else back()
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CAÇADA", color = HuntGold, fontFamily = HuntSerif, fontSize = 13.sp, letterSpacing = 2.sp)
                        Text(exercise.nome, color = Color.White.copy(alpha = .72f), fontSize = 11.sp, maxLines = 1)
                    }
                    SmallIconButton(if (soundEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                        if (soundEnabled) "Desativar som" else "Ativar som") { soundEnabled = !soundEnabled; if (!soundEnabled) tone.stopTone() }
                    Spacer(Modifier.width(6.dp))
                    SmallIconButton(Icons.Outlined.Cameraswitch, "Trocar câmera") { useFrontCamera = !useFrontCamera }
                }
                if (boss != null) BossCombatScene(boss, bossProgress, trial, count, defeat,
                    Modifier.fillMaxWidth().height(arenaHeight)) { defeat = null }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    StatusPill(if (manualPause) "PAUSADO" else stateLabel, if (manualPause) HuntGold else stateColor)
                    Text("%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                }
                if (result.phase == PushUpPhase.CALIBRATING && running) LinearProgressIndicator(
                    progress = { result.calibrationProgress }, Modifier.fillMaxWidth().height(3.dp),
                    color = stateColor, trackColor = Color.White.copy(alpha = .1f))
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${if (boss != null) trial.attemptReps else count}", color = Color(0xFFFFF1D8), fontFamily = HuntSerif,
                            fontSize = if (count < 100) 70.sp else 54.sp, lineHeight = 74.sp, fontWeight = FontWeight.Bold)
                        Text(if (boss != null) " / ${CameraTrials.enemy(boss, trial.rank).hp}\n NESTA TENTATIVA" else " REPS\n VALIDADAS", Modifier.padding(bottom = 12.dp, start = 9.dp), color = HuntGold,
                            fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.sp)
                    }
                    Column(Modifier.padding(bottom = 12.dp), horizontalAlignment = Alignment.End) {
                        Text("${trial.victories}", color = HuntGold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("ABATES · $count REPS TOTAIS", color = Color.White.copy(alpha = .6f), fontSize = 9.sp)
                    }
                }
                Text(if (manualPause) "Tentativa encerrada. Retome quando estiver pronto para um novo combate." else if (trial.attemptReps > 0 && trial.remainingSeconds(now) <= 5) "A tentativa termina em ${trial.remainingSeconds(now)}s sem uma repetição completa." else result.feedback,
                    color = Color.White, fontSize = 13.sp, lineHeight = 18.sp, minLines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (result.phase == PushUpPhase.DOWN && running) "AMPLITUDE CONFIRMADA" else "AMPLITUDE",
                        color = stateColor, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(result.lastRepQuality?.let { "EXECUÇÃO $it/100" } ?: "MOVIMENTO COMPLETO = 1 GOLPE",
                        color = Color.White.copy(alpha = .6f), fontSize = 9.sp)
                }
                LinearProgressIndicator(progress = { if (running) result.rangeProgress else 0f },
                    Modifier.fillMaxWidth().height(4.dp), color = stateColor, trackColor = Color.White.copy(alpha = .12f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        analysisPaused.set(true)
                        if (!manualPause && boss != null) checkpoint(trial.interrupt())
                        manualPause = !manualPause
                    }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HuntGold)) {
                        Icon(if (manualPause) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (manualPause) "Retomar" else "Pausar", fontSize = 12.sp)
                    }
                    Button(onClick = { analysisPaused.set(true); if (boss != null) checkpoint(trial.interrupt()); showSummary = true },
                        enabled = count > 0 || (bossProgress?.pendingReps ?: 0) > 0,
                        modifier = Modifier.weight(1.25f).height(48.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HuntGold, contentColor = Color(0xFF171116))) {
                        Text("Encerrar série", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        if (fell) BossFailureVeil(Modifier.align(Alignment.Center).fillMaxWidth().height(180.dp))
        if (manualPause && !fell) Box(Modifier.align(Alignment.Center).background(Color(0xC80C0D13), RoundedCornerShape(15.dp))
            .padding(horizontal = 24.dp, vertical = 17.dp), contentAlignment = Alignment.Center) {
            Text("EM DESCANSO", color = HuntGold, fontFamily = HuntSerif, letterSpacing = 2.sp, fontSize = 18.sp)
        }
        cameraError?.let {
            Box(Modifier.fillMaxSize().background(Fundo.copy(alpha = .94f)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.NoPhotography, null, tint = HuntGold, modifier = Modifier.size(42.dp))
                    Text(it, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Button(onClick = stop) { Text("Voltar à preparação") }
                }
            }
        }
    }

    if (showSummary) {
        AlertDialog(
            onDismissRequest = { if (!saving) showSummary = false },
            containerColor = Color(0xFF151319),
            icon = { Icon(Icons.Outlined.Verified, null, tint = HuntGold) },
            title = { Text("Provação concluída", fontFamily = HuntSerif, color = HuntGold, fontSize = 21.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("$count", fontSize = 42.sp, fontFamily = HuntSerif, color = Color(0xFFF3E3C7))
                            Text(if (isSquat) "agachamentos" else "flexões", color = TextoSecundario)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${trial.victories}", fontSize = 42.sp, fontFamily = HuntSerif, color = HuntGold)
                            Text("chefes abatidos", color = TextoSecundario)
                        }
                    }
                    Text("Tempo em atividade · %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), color = TextoSecundario, fontSize = 12.sp)
                    if (boss != null) {
                        val earlier = ((bossProgress?.pendingReps ?: 0) - count).coerceAtLeast(0)
                        Text("Vitórias e repetições estão salvas. O registro no treino concede o XP da série.", color = TextoSecundario, fontSize = 12.sp)
                        if (earlier > 0) Text("Também há $earlier reps de séries anteriores para registrar.", color = HuntGold, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    saving = true
                    if (boss != null) vm.salvarSerieCamera(exercise, boss, sessionId, count, back, trial)
                    else { vm.registrarSerie(exercise, pesoKg = null, reps = count, segundos = null); back() }
                }, enabled = !saving && !registering,
                    colors = ButtonDefaults.buttonColors(containerColor = HuntGold, contentColor = Color(0xFF171116))) {
                    Text(if (saving) "Salvando…" else "Salvar no treino")
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { showSummary = false }, enabled = !saving) { Text("Continuar série", color = HuntGold) }
                    if (boss != null) TextButton(onClick = back, enabled = !saving) { Text("Sair · registrar depois", color = TextoSecundario, fontSize = 11.sp) }
                }
            },
        )
    }
}

@Composable private fun FramingGuide(color: Color, tracking: Boolean, isSquat: Boolean) {
    Canvas(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 150.dp)) {
        val corner = 54.dp.toPx()
        val stroke = 3.dp.toPx()
        val alpha = if (tracking) .75f else .42f
        listOf(
            Pair(0f, 0f) to Pair(corner, 0f), Pair(0f, 0f) to Pair(0f, corner),
            Pair(size.width, 0f) to Pair(size.width - corner, 0f), Pair(size.width, 0f) to Pair(size.width, corner),
            Pair(0f, size.height) to Pair(corner, size.height), Pair(0f, size.height) to Pair(0f, size.height - corner),
            Pair(size.width, size.height) to Pair(size.width - corner, size.height), Pair(size.width, size.height) to Pair(size.width, size.height - corner),
        ).forEach { segment ->
            drawLine(color.copy(alpha = alpha), androidx.compose.ui.geometry.Offset(segment.first.first, segment.first.second), androidx.compose.ui.geometry.Offset(segment.second.first, segment.second.second), stroke, StrokeCap.Round)
        }
        val guide = color.copy(alpha = if (tracking) .06f else .16f)
        if (isSquat) {
            val shoulder = androidx.compose.ui.geometry.Offset(size.width * .52f, size.height * .19f)
            val hip = androidx.compose.ui.geometry.Offset(size.width * .54f, size.height * .46f)
            val knee = androidx.compose.ui.geometry.Offset(size.width * .69f, size.height * .66f)
            val ankle = androidx.compose.ui.geometry.Offset(size.width * .60f, size.height * .91f)
            drawLine(guide, shoulder, hip, 4.dp.toPx(), StrokeCap.Round)
            drawLine(guide, hip, knee, 4.dp.toPx(), StrokeCap.Round)
            drawLine(guide, knee, ankle, 4.dp.toPx(), StrokeCap.Round)
            listOf(shoulder, hip, knee, ankle).forEach { drawCircle(guide, 7.dp.toPx(), it) }
            return@Canvas
        }
        val head = androidx.compose.ui.geometry.Offset(center.x, size.height * .20f)
        val leftShoulder = androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .36f)
        val rightShoulder = androidx.compose.ui.geometry.Offset(size.width * .65f, size.height * .36f)
        val leftElbow = androidx.compose.ui.geometry.Offset(size.width * .20f, size.height * .58f)
        val rightElbow = androidx.compose.ui.geometry.Offset(size.width * .80f, size.height * .58f)
        val leftHand = androidx.compose.ui.geometry.Offset(size.width * .29f, size.height * .84f)
        val rightHand = androidx.compose.ui.geometry.Offset(size.width * .71f, size.height * .84f)
        drawCircle(guide, radius = size.minDimension * .075f, center = head, style = Stroke(3.dp.toPx()))
        drawLine(guide, leftShoulder, rightShoulder, 4.dp.toPx(), StrokeCap.Round)
        drawLine(guide, leftShoulder, leftElbow, 4.dp.toPx(), StrokeCap.Round)
        drawLine(guide, leftElbow, leftHand, 4.dp.toPx(), StrokeCap.Round)
        drawLine(guide, rightShoulder, rightElbow, 4.dp.toPx(), StrokeCap.Round)
        drawLine(guide, rightElbow, rightHand, 4.dp.toPx(), StrokeCap.Round)
        listOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftHand, rightHand).forEach {
            drawCircle(guide, 7.dp.toPx(), it)
        }
    }
}

@Composable private fun StatusPill(label: String, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .52f)).border(1.dp, color.copy(alpha = .3f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
    }
}

@Composable private fun SmallIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .52f))) {
        Icon(icon, label, tint = Color.White)
    }
}

private fun emptyCounterResult(isSquat: Boolean = false) = PushUpCounterResult(
    repetitions = 0,
    phase = PushUpPhase.SEARCHING,
    feedback = if (isSquat) "Fique de lado, mostrando ombro, quadril, joelho e tornozelo."
        else "Fique de frente, mostrando cabeça, ombros, cotovelos e mãos.",
    bodyAligned = false,
)

private class PushUpPoseAnalyzer(
    private val detector: PoseDetector,
    private val pushEngine: PushUpRepEngine,
    private val squatEngine: SquatRepEngine,
    private val isSquat: Boolean,
    private val paused: AtomicBoolean,
    private val recalibrate: AtomicInteger,
    private val worker: java.util.concurrent.Executor,
    private val onResult: (PushUpCounterResult) -> Unit,
) : ImageAnalysis.Analyzer {
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (paused.get()) { imageProxy.close(); return }
        val preserved = recalibrate.getAndSet(-1)
        if (preserved >= 0) { pushEngine.recalibrate(preserved); squatEngine.recalibrate(preserved) }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener(worker) { pose ->
                if (paused.get()) return@addOnSuccessListener
                onResult(if (isSquat) squatEngine.process(pose.toSquatSample(image.width, image.height))
                    else pushEngine.process(pose.toPushUpSample(image.width, image.height)))
            }
            .addOnFailureListener(worker) { if (!paused.get()) onResult(if (isSquat) squatEngine.process(null) else pushEngine.process(null)) }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/** Take the clearer side; a mirrored front camera does not change the joint angle. */
private fun Pose.toSquatSample(frameWidth: Int, frameHeight: Int): SquatPoseSample? {
    data class Side(val shoulder: PoseLandmark, val hip: PoseLandmark, val knee: PoseLandmark, val ankle: PoseLandmark) {
        val confidence: Float get() = listOf(shoulder, hip, knee, ankle).minOf { it.inFrameLikelihood }
    }
    val left = listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        .mapNotNull(::getPoseLandmark).takeIf { it.size == 4 }?.let { Side(it[0], it[1], it[2], it[3]) }
    val right = listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        .mapNotNull(::getPoseLandmark).takeIf { it.size == 4 }?.let { Side(it[0], it[1], it[2], it[3]) }
    val side = listOfNotNull(left, right).maxByOrNull { it.confidence } ?: return null
    val kneeAngle = jointAngle(side.hip.position, side.knee.position, side.ankle.position)
    val torso = side.shoulder.position
    val hip = side.hip.position
    val tilt = abs(Math.toDegrees(atan2((torso.x - hip.x).toDouble(), (hip.y - torso.y).toDouble()))).toFloat()
    val landmarks = listOf(side.shoulder, side.hip, side.knee, side.ankle)
    val xs = landmarks.map { it.position.x }; val ys = landmarks.map { it.position.y }
    val span = hypot((xs.maxOrNull()!! - xs.minOrNull()!!).toDouble(), (ys.maxOrNull()!! - ys.minOrNull()!!).toDouble()).toFloat() /
        maxOf(frameWidth, frameHeight).coerceAtLeast(1)
    return SquatPoseSample(kneeAngle, tilt, side.confidence, span, SystemClock.elapsedRealtime())
}

private fun Pose.toPushUpSample(frameWidth: Int, frameHeight: Int): PushUpPoseSample? {
    val nose = getPoseLandmark(PoseLandmark.NOSE) ?: return null
    val leftShoulder = getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return null
    val leftElbow = getPoseLandmark(PoseLandmark.LEFT_ELBOW) ?: return null
    val leftWrist = getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return null
    val rightShoulder = getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return null
    val rightElbow = getPoseLandmark(PoseLandmark.RIGHT_ELBOW) ?: return null
    val rightWrist = getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return null
    val armLandmarks = listOf(leftShoulder, leftElbow, leftWrist, rightShoulder, rightElbow, rightWrist)
    val allLandmarks = armLandmarks + nose
    val armMinimum = armLandmarks.minOf { it.inFrameLikelihood }
    val confidence = .72f * minOf(armMinimum, nose.inFrameLikelihood) +
        .28f * allLandmarks.map { it.inFrameLikelihood }.average().toFloat()

    val leftAngle = jointAngle(leftShoulder.position, leftElbow.position, leftWrist.position)
    val rightAngle = jointAngle(rightShoulder.position, rightElbow.position, rightWrist.position)
    val shoulderCenter = midpoint(leftShoulder.position, rightShoulder.position)
    val wristCenter = midpoint(leftWrist.position, rightWrist.position)
    val xs = allLandmarks.map { it.position.x }
    val ys = allLandmarks.map { it.position.y }
    val upperWidth = (xs.maxOrNull()!! - xs.minOrNull()!!).coerceAtLeast(1f)
    val upperHeight = (ys.maxOrNull()!! - ys.minOrNull()!!).coerceAtLeast(1f)
    val upperSpanPixels = hypot(upperWidth.toDouble(), upperHeight.toDouble()).toFloat()
    val span = upperSpanPixels / maxOf(frameWidth, frameHeight).coerceAtLeast(1)
    val tiltRaw = abs(Math.toDegrees(atan2(
        (rightShoulder.position.y - leftShoulder.position.y).toDouble(),
        (rightShoulder.position.x - leftShoulder.position.x).toDouble(),
    ))).toFloat()
    val tilt = if (tiltRaw > 90f) 180f - tiltRaw else tiltRaw
    val chestToHands = hypot(
        (wristCenter.x - shoulderCenter.x).toDouble(),
        (wristCenter.y - shoulderCenter.y).toDouble(),
    ).toFloat() / upperSpanPixels
    return PushUpPoseSample(
        elbowAngle = (leftAngle + rightAngle) / 2f,
        bodyAngle = 180f - abs(leftAngle - rightAngle),
        confidence = confidence,
        timestampMs = SystemClock.elapsedRealtime(),
        bodySpanRatio = span,
        bodyTiltDegrees = tilt,
        chestClearanceRatio = chestToHands.coerceIn(0f, 1f),
    )
}

private fun midpoint(first: PointF, second: PointF) =
    PointF((first.x + second.x) / 2f, (first.y + second.y) / 2f)

private fun jointAngle(first: PointF, middle: PointF, last: PointF): Float {
    val raw = Math.toDegrees(
        atan2((last.y - middle.y).toDouble(), (last.x - middle.x).toDouble()) -
            atan2((first.y - middle.y).toDouble(), (first.x - middle.x).toDouble())
    )
    var angle = abs(raw).toFloat()
    if (angle > 180f) angle = 360f - angle
    return angle
}
