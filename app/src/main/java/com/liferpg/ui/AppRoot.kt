package com.liferpg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.liferpg.MainViewModel
import com.liferpg.notif.ArenaConvites
import com.liferpg.data.ResultadoSerie
import com.liferpg.domain.*
import com.liferpg.ui.components.GraficoEvolucao
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable fun AppRoot(vm: MainViewModel, openSocialFromNotification: MutableStateFlow<String?>) {
    AppUpdatePrompt()
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val passwordRecovery by vm.recuperandoSenha.collectAsStateWithLifecycle()
    val upgradePending by vm.conversaoPendente.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.prepararEntrada() }
    val pendingInvites by vm.convitesPendentes.collectAsStateWithLifecycle()
    val openSocial by openSocialFromNotification.collectAsStateWithLifecycle()
    val circleState by vm.circles.state.collectAsStateWithLifecycle()
    val circleInvites=if(circleState.accountId==account?.userId) circleState.circles.count { it.status=="pending" } else 0
    val commentNotices=if(circleState.accountId==account?.userId) circleState.notices.size else 0
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(account?.userId) {
        ArenaConvites.acompanhar(context, account?.vinculada == true)
    }
    LaunchedEffect(account?.userId, lifecycleOwner) {
        if (account?.vinculada == true) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.carregarSocial(silencioso = true)
                vm.circles.load()
                delay(45_000)
            }
        }
    }
    val mentor: MentorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val loading by vm.carregando.collectAsStateWithLifecycle()
    val profile by vm.perfil.collectAsStateWithLifecycle()
    LaunchedEffect(profile?.ultimoDiaTreino) {
        if (profile?.ultimoDiaTreino == LocalDate.now().toEpochDay()) com.liferpg.notif.Notificacoes.limparLembrete(context)
    }
    var tab by rememberSaveable("archive-v1-tab") { mutableIntStateOf(0) }
    var routes by rememberSaveable("archive-v1-routes") { mutableStateOf(arrayListOf<String>()) }
    var mentorOpen by rememberSaveable("archive-v1-mentor") { mutableStateOf(false) }
    var socialSection by rememberSaveable { mutableStateOf(SocialSection.FRIENDS) }
    var navDirection by rememberSaveable { mutableIntStateOf(1) }
    val route = routes.lastOrNull()
    val screens = rememberSaveableStateHolder()
    val selectTab: (Int) -> Unit = { nextTab ->
        if (nextTab in 0..5) {
            if (nextTab != tab) navDirection = if (primaryDestinations.indexOfFirst { it.id==nextTab } > primaryDestinations.indexOfFirst { it.id==tab }) 1 else -1
            tab = nextTab
            routes = arrayListOf()
        }
    }
    val open: (String) -> Unit = { next ->
        if (next == RemnantRoute.MENTOR) mentorOpen = true
        else if (RemnantRoute.socialSection(next)!=null) {
            socialSection=RemnantRoute.socialSection(next)!!
            selectTab(RemnantRoute.FRIENDS_TAB)
        }
        else RemnantRoute.tabIndex(next)?.let(selectTab)
            ?: run { if (route != next) { navDirection = 1; routes = ArrayList(routes + next) } }
    }
    LaunchedEffect(openSocial, profile, account?.userId) {
        if (openSocial != null && profile != null && account?.vinculada==true) {
            open(openSocial!!)
            com.liferpg.data.sync.CircleConversationTarget.parse(openSocial)?.let { vm.circles.conversation.open(it.circle,it.post) }
            openSocialFromNotification.value = null
        }
    }
    LaunchedEffect(route) {
        route?.takeIf { RemnantRoute.socialSection(it)!=null }?.let(open)
    }
    LaunchedEffect(route, tab) { mentor.sourceScreen = route ?: RemnantRoute.tab(tab) }
    val back: () -> Unit = {
        navDirection = -1
        if (routes.isNotEmpty()) routes = ArrayList(routes.dropLast(1))
        else if(tab==RemnantRoute.FRIENDS_TAB && socialSection!=SocialSection.FRIENDS) socialSection=SocialSection.FRIENDS
        else selectTab(0)
    }
    val rest by vm.descansoRestante.collectAsStateWithLifecycle()
    var priorRest by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(rest) {
        if ((priorRest ?: 0) > 0 && rest == 0) mentor.announce("Descanso concluído. Pronto para a próxima série?")
        priorRest = rest
    }
    val snackbar = remember { SnackbarHostState() }
    var rewards by remember { mutableStateOf(listOf<Pair<Long, ResultadoSerie>>()) }
    LaunchedEffect(vm) { vm.erros.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) { vm.eventos.collect {
        mentor.react(it)
        if (it.xpGanho > 0 || celebrationStages(it).isNotEmpty()) rewards = rewards + (System.nanoTime() to it)
        else snackbar.showSnackbar("Série registrada • +0 XP", duration = SnackbarDuration.Short)
    } }
    if (loading) {
        Box(Modifier.fillMaxSize().background(Fundo), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TextoPrimario)
        }
        return
    }
    if (profile == null || account?.vinculada != true || passwordRecovery || upgradePending) {
        Scaffold(containerColor = Fundo, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (profile == null && !passwordRecovery) EntryJourney(vm) else AccountGate(vm, profile)
            }
        }
        return
    }
    BackHandler(route != null || tab != 0, back)
    BackHandler(mentorOpen) { mentorOpen = false }
    val immersive = RemnantRoute.isImmersive(route)
    CelebrationHost {
    Scaffold(containerColor = Fundo, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        Column(if (route != null && !mentorOpen && !immersive) Modifier.navigationBarsPadding() else Modifier) {
        if (profile != null && !mentorOpen && !immersive) MentorDock(vm, mentor, quiet = true) { open(RemnantRoute.MENTOR) }
        if (profile != null && route == null && !mentorOpen) {
            MainNavigation(tab,pendingInvites+circleInvites+commentNotices) { target ->
                if(target==RemnantRoute.FRIENDS_TAB) socialSection=SocialSection.FRIENDS
                selectTab(target)
            }
        }
        }
    }) { padding ->
        val contentModifier = if (immersive) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(padding).imePadding()
        MotionScene(contentModifier) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> AnimatedContent(route ?: RemnantRoute.tab(tab), modifier = Modifier.fillMaxSize(), transitionSpec = {
                    val direction = navDirection
                    ((fadeIn(tween(230, 35)) +
                        slideInHorizontally(tween(310, easing = FastOutSlowInEasing)) { direction * it / 13 }) togetherWith
                        (fadeOut(tween(145)) +
                            slideOutHorizontally(tween(210)) { -direction * it / 20 })).using(SizeTransform(clip = false))
                }, label = "screenTransition") { screen ->
                    screens.SaveableStateProvider("archive-v1:$screen") {
                        RemnantRoute.tabIndex(screen)?.let { selected -> when (selected) {
                            0 -> Headquarters(vm, open)
                            1 -> Ranks(vm, open)
                            2 -> Arsenal(vm) { open(RemnantRoute.exercise(it)) }
                            3 -> Training(vm, open)
                            4 -> CharacterArchive(vm, open)
                            5 -> SocialHub(vm, open, socialSection) { socialSection=it }
                            else -> Unit
                        } } ?: Details(vm, screen, back, open)
                    }
                }
            }
            if (mentorOpen) {
                val unfold = remember { Animatable(0f) }
                LaunchedEffect(Unit) { unfold.animateTo(1f, spring(.78f, 320f)) }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable { mentorOpen = false })
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.94f)
                    .graphicsLayer { translationY = (1f-unfold.value)*180.dp.toPx(); alpha = unfold.value.coerceIn(0f,1f) }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Fundo)
                    .border(1.dp, Neon.copy(alpha = .3f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("REM / CENTRAL DE TREINO")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { mentorOpen = false }) { Text("Recolher") }
                    }
                    MentorScreen(vm) { next -> mentorOpen = false; open(next) }
                }
            }
            rewards.firstOrNull()?.let { (id, result) -> key(id) {
                val stages = remember(result) { celebrationStages(result) }
                val finish = { rewards = rewards.filterNot { it.first == id } }
                var celebrated by remember { mutableStateOf(false) }
                if (stages.isEmpty() || celebrated) RewardBurst(result, finish)
                else CelebrationSequence(stages) { celebrated = true }
            } }
        }
    }
    }
}

@Composable private fun Ranks(vm: MainViewModel, open: (String) -> Unit) { MuscleRanks(vm, open) }

@Composable private fun Arsenal(vm: MainViewModel, open: (String) -> Unit) {
    val exercises by vm.exercicios.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableIntStateOf(0) }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = remember(exercises, query, place, muscle) {
        exercises.filter { ex ->
            BuscaExercicios.corresponde(ex, query) &&
                (place == 0 || ex.local == LocalTreino.AMBOS || ex.local == if (place == 1) LocalTreino.CASA else LocalTreino.ACADEMIA) &&
                (muscle == null || ex.musculo.name == muscle)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("EXERCÍCIOS E EQUIPAMENTOS", "Arsenal") }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar exercício ou equipamento", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(12.dp)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("Todos", "Em casa", "Academia").forEachIndexed { i, s -> Choice(s, place == i) { place = i } } } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Todo o corpo", muscle == null) { muscle = null }
            Musculo.entries.forEach { m -> Choice(m.nomeExibicao, muscle == m.name) { muscle = m.name } }
        } }
        item { AnimatedContent(filtered.size, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) }, label = "catalogResults") {
            Eyebrow("$it exercícios disponíveis", TextoSecundario)
        } }
        if (filtered.isEmpty()) item { Panel { Text("Nenhum exercício encontrado."); Text("Tente outro nome ou remova um filtro.", color = TextoSecundario) } }
        items(filtered, key = { it.id }) { ex -> Column(Modifier.animateItem(fadeInSpec = tween(180),
            placementSpec = spring(.9f, 650f), fadeOutSpec = tween(100))) {
            ExerciseRow(ex) { open(ex.id) }; HorizontalDivider(color = Borda)
        } }
    }
}

@Composable internal fun ExerciseRow(ex: Exercicio, subtitle: String = ex.musculo.nomeExibicao + " • " + ex.equipamento, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val nudge = animateDpAsState(if (pressed) 3.dp else 0.dp, tween(110), label = "exerciseArrow")
    Row(Modifier.fillMaxWidth().pressMotion(interaction, .985f).clip(RoundedCornerShape(12.dp))
        .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ExerciseThumbnail(ex, 64.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ex.nome, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, lineHeight = 16.sp, color = TextoSecundario)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp).graphicsLayer { translationX = nudge.value.toPx() }, tint = TextoSecundario)
    }
}

@Composable internal fun Missions(vm: MainViewModel) {
    val missions by vm.missoesHoje.collectAsStateWithLifecycle()
    Panel {
        if (missions.isEmpty()) Text("Suas missões aparecerão aqui.", color = TextoSecundario)
        missions.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = Borda)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (m.concluida) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (m.concluida) Neon else TextoSecundario, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.descricao, fontSize = 13.sp, lineHeight = 18.sp)
                    Text("${m.progresso}/${m.alvoQtd}  •  +${m.xpRecompensa} XP", color = if (m.concluida) Neon else TextoSecundario, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable internal fun RestTimer(vm: MainViewModel) {
    val remaining by vm.descansoRestante.collectAsStateWithLifecycle()
    Panel {
        Eyebrow("Respire. Recupere. Continue.")
        AnimatedLabel(remaining?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "Seu intervalo", fontSize = if (remaining == null) 24.sp else 42.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (remaining == null) listOf(60,90,120,180).forEach { sec -> Choice(Format.tempo(sec), false) { vm.iniciarDescanso(sec) } }
            else {
                Choice("−15s", false) { vm.ajustarDescanso(-15) }
                Choice("+15s", false) { vm.ajustarDescanso(15) }
                Choice("Encerrar", true) { vm.cancelarDescanso() }
            }
        }
    }
}

@Composable private fun Training(vm: MainViewModel, open: (String) -> Unit) {
    val session by vm.sessao.collectAsStateWithLifecycle()
    val today by vm.seriesHoje.collectAsStateWithLifecycle()
    val exercises by vm.exercicios.collectAsStateWithLifecycle()
    val plan by vm.treinoHoje.collectAsStateWithLifecycle()
    Page("SUA ROTINA", "Treino") {
        Section("Sua sessão", "Histórico") { open(RemnantRoute.HISTORY) }
        Panel {
            Eyebrow(if (session == null) "Pronto quando você estiver" else "Treino em andamento")
            Text(session?.nome ?: plan.titulo, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(session?.let { "${it.seriesFeitas} de ${it.seriesPlanejadas} séries concluídas" } ?: "${plan.itens.size} exercícios • ${plan.volumeTotalSeries} séries", color = TextoSecundario, fontSize = 13.sp)
            session?.let { Meter(it.progresso) }
            Action(if (session == null) "Ver treino do dia" else "Retomar sessão") { open(if (session == null) RemnantRoute.PLAN else RemnantRoute.SESSION) }
        }
        Section("Sua semana", "Montar treinos") { open(RemnantRoute.ROUTINES) }
        CardioPresence(vm.cardio) { open(RemnantRoute.CARDIO) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${today.size}", "séries hoje")
            Stat("${today.sumOf { it.xpGanho }}", "XP conquistado")
            Stat("${today.map { it.musculo }.distinct().size}", "grupos treinados")
        }
        RestTimer(vm)
        Section("Registros de hoje")
        if (today.isEmpty()) Panel { Text("A primeira série muda o dia.", fontSize = 19.sp); Text("Inicie seu treino ou registre um exercício pelo Arsenal.", fontSize = 13.sp, color = TextoSecundario) }
        TodayTrainingLog(today,exercises,open)
    }
}
