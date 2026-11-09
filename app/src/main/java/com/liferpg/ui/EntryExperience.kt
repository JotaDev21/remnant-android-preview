@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.liferpg.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.data.EntryAnswers
import com.liferpg.data.Perfil
import com.liferpg.data.sync.Sessao
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import kotlinx.coroutines.launch

/** Fixed safe footer, independently scrolling body, no nested full-screen dialog. */
@Composable private fun EntryFrame(step: Int, title: String, subtitle: String,
    back: (() -> Unit)? = null, progress: Float = 0f, footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            else EntryMark(Modifier.size(38.dp))
            Text("REMNANT", Modifier.weight(1f).padding(start = 12.dp), fontSize = 12.sp,
                letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            if (step in 0..5) Text("${step + 1} / 6", color = TextoSecundario, fontSize = 12.sp)
        }
        if (step in 0..5) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp),
            color = TextoPrimario, trackColor = Borda)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp)
            Text(subtitle, color = TextoSecundario, fontSize = 14.sp, lineHeight = 21.sp)
            content()
            Spacer(Modifier.height(10.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { footer() }
    }
    }
}

@Composable private fun EntryMark(modifier: Modifier) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width * .22f, size.height * .8f); lineTo(size.width * .22f, size.height * .2f)
            lineTo(size.width * .7f, size.height * .2f); lineTo(size.width * .78f, size.height * .45f)
            lineTo(size.width * .48f, size.height * .52f); lineTo(size.width * .82f, size.height * .8f)
        }
        drawPath(p, Color.White.copy(alpha = reveal.value), style = Stroke(2.dp.toPx()))
        drawLine(Color.White.copy(alpha = reveal.value * .3f), Offset(0f, size.height - 1), Offset(size.width * reveal.value, size.height - 1))
    }
}

@Composable internal fun EntryJourney(vm: MainViewModel) {
    var answers by remember { mutableStateOf(vm.entryDraft.load()) }
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val upgrade by vm.conversaoPendente.collectAsStateWithLifecycle()
    val creating by vm.criandoPerfil.collectAsStateWithLifecycle()
    val busy by vm.authCarregando.collectAsStateWithLifecycle()
    val message by vm.authMensagem.collectAsStateWithLifecycle()
    val set: (EntryAnswers) -> Unit = { answers = it; vm.entryDraft.save(it) }
    EntryJourneyContent(answers, catalog, account, upgrade, creating, busy, message, set,
        vm::limparMensagemAuth, { vm.finalizarEntrada(answers) }) { AccountFields(vm, onboarding = true); EntryCloudRecovery(vm) }
}

/** The production layout is also rendered and exercised directly in local Android UI tests. */
@Composable internal fun EntryJourneyContent(answers: EntryAnswers, catalog: List<Exercicio>, account: Sessao?,
    upgrade: Boolean, creating: Boolean, busy: Boolean, message: String?, set: (EntryAnswers) -> Unit,
    clearMessage: () -> Unit, finish: () -> Unit, accountFields: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    val progress by animateFloatAsState((answers.step + 1) / 6f, tween(420, easing = FastOutSlowInEasing), label = "entryProgress")
    val plan = remember(catalog, answers.dias, answers.objetivo, answers.experiencia, answers.local, answers.foco) {
        StarterPlan.criar(catalog, answers.dias, Objetivo.seguro(answers.objetivo),
            NivelExperiencia.seguro(answers.experiencia), LocalTreino.entries.firstOrNull { it.name == answers.local }
                ?: LocalTreino.ACADEMIA, answers.musculo)
    }
    BackHandler(answers.step > 0 || creating || busy) {
        if (!creating && !busy) { clearMessage(); set(answers.copy(step = answers.step - 1)) }
    }
    AnimatedContent(answers.step, modifier = Modifier.fillMaxSize(), transitionSpec = {
        val direction = if (targetState > initialState) 1 else -1
        (fadeIn(tween(260, 60)) + slideInHorizontally(tween(330, easing = FastOutSlowInEasing)) { direction * it / 10 }) togetherWith
            (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -direction * it / 15 })
    }, label = "entryStep") { step ->
        val headings = listOf("Sua jornada.\nSeu nome.", "O ponto\nde partida.", "O que você\nquer construir?",
            "O treino cabe\nna sua vida.", "Seu primeiro\nplano.", "Sua identidade.\nSeu progresso.")
        val subtitles = listOf("Como quer ser chamado? Esse será seu nome no app e na Arena.",
            "Suas medidas ajudam nas patentes e nas referências de nutrição. Você poderá atualizar tudo depois.",
            "Escolha um objetivo e sua experiência. O plano começa simples e evolui com você.",
            "Escolha onde, em quais dias e qual músculo quer priorizar, mantendo o corpo inteiro no plano.",
            "Uma base editável com exercícios do seu Arsenal. As cargas ficam para você ajustar no treino.",
            "Crie uma conta para entrar. Sem assinatura nesta etapa; seu plano continua editável.")
        EntryFrame(step, headings[step], subtitles[step],
            progress = progress,
            back = if (step > 0 && !busy && !creating) ({ set(answers.copy(step = step - 1)); clearMessage() }) else null,
            footer = {
                if (step < 5) Action(if (step == 4) "Proteger minha jornada" else "Continuar",
                    answers.valida(step) && (step != 4 || plan.isNotEmpty()) && !busy && !creating) {
                    focusManager.clearFocus(); set(answers.copy(step = step + 1))
                } else if (account?.anonimo == false && !upgrade) {
                    AuthMessage(message)
                    Action(if (creating) "Preparando seu plano…" else "Entrar no REMNANT", !creating && answers.completa() && plan.isNotEmpty()) {
                        focusManager.clearFocus(); finish()
                    }
                }
                if (step == 4) Text("Troque exercícios, dias e séries em Treino → Minhas rotinas.", color = TextoSecundario, fontSize = 11.sp)
            }) {
            when (step) {
                0 -> {
                    EntryMark(Modifier.size(100.dp).align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
                    EntryField("Nickname", answers.nome, "Ex.: jota", enabled = !busy) { set(answers.copy(nome = it.take(24))) }
                    Text("De 2 a 24 caracteres. Use um apelido, sem e-mail.", color = TextoSecundario, fontSize = 12.sp)
                }
                1 -> {
                    EntryField("Peso · kg", answers.peso, "Ex.: 75,5", KeyboardType.Decimal) { set(answers.copy(peso = number(it, 6, true))) }
                    EntryField("Altura · cm", answers.altura, "Ex.: 175", KeyboardType.Number) { set(answers.copy(altura = number(it, 3))) }
                    EntryField("Idade · anos", answers.idade, "Ex.: 24", KeyboardType.Number) { set(answers.copy(idade = number(it, 3))) }
                    Text("Peso: 30–350 kg · altura: 100–250 cm · idade: 13–100 anos.", color = TextoSecundario, fontSize = 11.sp)
                }
                2 -> {
                    Objetivo.entries.forEach { goal -> EntryOption(goal.titulo, when (goal) {
                        Objetivo.HIPERTROFIA -> "Ganhar massa com uma base consistente"
                        Objetivo.FORCA -> "Evoluir carga e técnica, sem saltar etapas"
                        Objetivo.RESISTENCIA -> "Melhorar a capacidade de sustentar o treino"
                        Objetivo.GERAL -> "Criar uma rotina equilibrada"
                    }, answers.objetivo == goal.name) { set(answers.copy(objetivo = goal.name)) } }
                    Eyebrow("SUA EXPERIÊNCIA", TextoSecundario)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NivelExperiencia.entries.forEach { ex -> Choice(ex.titulo, answers.experiencia == ex.name) { set(answers.copy(experiencia = ex.name)) } }
                    }
                }
                3 -> {
                    Eyebrow("ONDE VOCÊ TREINA", TextoSecundario)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LocalTreino.entries.forEach { location ->
                        Choice(location.nomeExibicao, answers.local == location.name) { set(answers.copy(local = location.name)) }
                    } }
                    Eyebrow("SEUS DIAS · ${answers.dias.size} POR SEMANA", TextoSecundario)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom").forEachIndexed { index, label ->
                            val day = index + 1
                            Choice(label, day in answers.dias) {
                                val next = if (day in answers.dias) answers.dias - day else answers.dias + day
                                if (next.size <= 6) set(answers.copy(dias = next))
                            }
                        }
                    }
                    Text(if (answers.experiencia == "INICIANTE") "Comece com 2 ou 3 dias e reserve tempo para recuperação." else "Escolha até 6 dias. Recuperação também faz parte da evolução.",
                        color = TextoSecundario, fontSize = 12.sp)
                    Eyebrow("MÚSCULO EM FOCO", TextoSecundario)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("Equilíbrio", answers.foco.isBlank()) { set(answers.copy(foco = "")) }
                        Musculo.entries.forEach { muscle -> Choice(muscle.nomeExibicao, answers.foco == muscle.name) { set(answers.copy(foco = muscle.name)) } }
                    }
                    Text(StarterPlan.classe(answers.musculo).descricaoPerk, color = TextoSecundario, fontSize = 12.sp)
                }
                4 -> {
                    if (catalog.isEmpty()) CircularProgressIndicator(color = TextoPrimario)
                    Text("${answers.dias.size} dias · ${Objetivo.seguro(answers.objetivo).titulo} · ${answers.musculo?.nomeExibicao ?: "Corpo inteiro"}",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    var selectedDay by rememberSaveable(answers.dias.sorted().toString()) { mutableIntStateOf(answers.dias.minOrNull() ?: 1) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { plan.forEach { workout ->
                        val day = workout.dias.first()
                        Choice(listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")[day - 1], day == selectedDay) { selectedDay = day }
                    } }
                    val workout = plan.firstOrNull { selectedDay in it.dias } ?: plan.firstOrNull()
                    workout?.let { w ->
                        Text(w.nome, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        w.itens.forEach { item -> catalog.firstOrNull { it.id == item.exercicioId }?.let { ex ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ExerciseThumbnail(ex, 58.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(ex.nome, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text("${item.series} séries · ${if (ex.modo == ModoMedida.TEMPO) "${item.repMin * 3}–${item.repMax * 3}s" else "${item.repMin}–${item.repMax} reps"} · ${item.descansoSeg}s de descanso",
                                        color = TextoSecundario, fontSize = 11.sp)
                                }
                            }
                        } }
                    }
                    Text("Sugestão geral de partida. Use cargas confortáveis, mantenha a técnica e ajuste com seu instrutor. O foco não elimina os demais músculos.",
                        color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
                }
                5 -> if (account?.anonimo != false || upgrade) accountFields() else {
                    Icon(Icons.Outlined.Check, null, Modifier.size(48.dp), tint = TextoPrimario)
                    Text("Tudo pronto, ${answers.nome.trim()}.", fontSize = 21.sp, fontWeight = FontWeight.Medium)
                    Text("Seu plano começa sem séries, PRs ou patentes inventados. Cada conquista será sua.", color = TextoSecundario, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable internal fun AccountGate(vm: MainViewModel, profile: Perfil?) {
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val recovery by vm.recuperandoSenha.collectAsStateWithLifecycle()
    val busy by vm.authCarregando.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { runCatching {
            val backup = vm.gerarBackupJson()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(backup.toByteArray(Charsets.UTF_8)) }
                    ?: error("Não consegui abrir o arquivo.")
            }
        }.onSuccess { vm.avisar("Backup exportado.") }.onFailure { vm.avisar("Não consegui exportar. Seu progresso continua no aparelho.") } }
    }
    EntryFrame(-1, if (recovery) "Uma nova\nsenha." else if (account?.anonimo == true) "Proteja sua\njornada." else "Seu próximo\ncapítulo.",
        if (profile != null) "${profile.nome}, seu progresso está neste aparelho. Entre para continuar com a mesma identidade."
        else "Entre para continuar. Seus dados de acesso aparecem somente aqui.", footer = {
            if (profile != null) TextButton(onClick = { export.launch("REMNANT-backup.json") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("Exportar meu progresso", color = TextoSecundario) }
        }) { AccountFields(vm, onboarding = false) }
}

@Composable private fun AccountFields(vm: MainViewModel, onboarding: Boolean) {
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val busy by vm.authCarregando.collectAsStateWithLifecycle()
    val message by vm.authMensagem.collectAsStateWithLifecycle()
    val google by vm.googleDisponivel.collectAsStateWithLifecycle()
    val recovery by vm.recuperandoSenha.collectAsStateWithLifecycle()
    val upgrade by vm.conversaoPendente.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AccountForm(account, busy, message, google, recovery, upgrade, onboarding, vm::entrarEmail, vm::cadastrar,
        vm::recuperarSenha, vm::salvarNovaSenha, vm::cancelarRecuperacao, vm::limparMensagemAuth) {
        vm.entrarGoogle { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { vm.avisar("Não encontrei um navegador para abrir o Google.") } }
    }
}

@Composable internal fun AccountForm(account: Sessao?, busy: Boolean, message: String?, google: Boolean,
    recovery: Boolean, upgrade: Boolean, onboarding: Boolean, login: (String, String) -> Unit,
    createAccount: (String, String) -> Unit, recover: (String) -> Unit, reset: (String) -> Unit,
    cancel: () -> Unit, clearMessage: () -> Unit, openGoogle: () -> Unit) {
    val converting = account?.anonimo == true || upgrade
    var signup by rememberSaveable { mutableStateOf(onboarding) }
    var email by rememberSaveable { mutableStateOf("") }
    // Password is deliberately not saveable or persisted in the onboarding draft.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val create = signup || converting || recovery
    val validEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!converting && !recovery) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Criar conta", signup) { if (!busy) { signup = true; clearMessage() } }
            Choice("Já tenho conta", !signup) { if (!busy) { signup = false; clearMessage() } }
        }
        if (converting) Text("Esta conta substitui a entrada anônima mantendo seus amigos, sua posição e seu save.",
            color = TextoSecundario, fontSize = 13.sp, lineHeight = 19.sp)
        if (!recovery) EntryField("E-mail", email, "voce@exemplo.com", KeyboardType.Email, !busy) { email = it.take(254) }
        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text(if (recovery) "Nova senha" else "Senha") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = { IconButton(onClick = { reveal = !reveal }) {
                Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (reveal) "Ocultar senha" else "Mostrar senha")
            } })
        if (create) {
            OutlinedTextField(confirmation, { confirmation = it }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text("Confirmar senha") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
            Text("Pelo menos 8 caracteres. Combine palavras, números ou símbolos.", color = TextoSecundario, fontSize = 11.sp)
        }
        AuthMessage(message)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = TextoPrimario, trackColor = Borda)
        Action(if (busy) "Conectando…" else if (recovery) "Salvar nova senha" else if (converting) "Proteger e continuar"
            else if (signup) "Criar minha conta" else "Entrar", !busy && (validEmail || recovery) &&
            password.length >= (if (create) 8 else 6) && (!create || password == confirmation)) {
            focus.clearFocus()
            if (recovery) reset(password)
            else if (signup || converting) createAccount(email, password) else login(email, password)
        }
        if (!create) TextButton(onClick = { focus.clearFocus(); recover(email) }, enabled = validEmail && !busy) { Text("Esqueci minha senha") }
        if (recovery) TextButton(onClick = cancel, enabled = !busy) { Text("Cancelar") }
        if (!recovery && google) {
            HorizontalDivider(color = Borda)
            OutlinedButton(onClick = { focus.clearFocus(); openGoogle() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Continuar com Google", fontWeight = FontWeight.SemiBold)
            }
        }
        Text("A conta identifica você na Arena. Treinos e fotos continuam neste aparelho; exporte um backup antes de trocar de celular.",
            color = TextoSecundario, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable private fun AuthMessage(message: String?) {
    AnimatedVisibility(message != null, enter = fadeIn(tween(180)) + expandVertically(tween(220)), exit = fadeOut(tween(120)) + shrinkVertically(tween(180))) {
        Text(message.orEmpty(), Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
            .border(1.dp, Borda, RoundedCornerShape(12.dp)).padding(14.dp),
            color = TextoPrimario, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable private fun EntryField(label: String, value: String, hint: String,
    keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true, change: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        placeholder = { Text(hint, color = TextoSecundario) }, enabled = enabled, singleLine = true,
        shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }))
}

@Composable private fun EntryOption(title: String, subtitle: String, selected: Boolean, select: () -> Unit) {
    val color by animateColorAsState(if (selected) TextoPrimario else Borda, tween(180), label = "entryChoice")
    OutlinedCard(onClick = select, border = androidx.compose.foundation.BorderStroke(1.dp, color),
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) SuperficieAlta else Color.Transparent),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.RadioButton }) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextoSecundario, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Icon(if (selected) Icons.Outlined.Check else Icons.Outlined.FitnessCenter, null, Modifier.size(21.dp), tint = color)
        }
    }
}

private fun number(value: String, limit: Int, decimal: Boolean = false): String =
    value.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(limit)
