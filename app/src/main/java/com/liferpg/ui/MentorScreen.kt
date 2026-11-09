package com.liferpg.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.data.net.*
import com.liferpg.domain.MentorAction
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.Normalizer
import java.util.Locale

internal class MentorViewModel : ViewModel() {
    var messages by mutableStateOf(listOf<MentorMessage>()); private set
    var actions by mutableStateOf(listOf<MentorAction>()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var tokens by mutableIntStateOf(0); private set
    var remainingToday by mutableIntStateOf(25); private set
    var sourceScreen by mutableStateOf(RemnantRoute.tab(0))
    var share by mutableStateOf(true)
    var draft by mutableStateOf("")
    var reaction by mutableStateOf<String?>(null); private set
    private var reactionJob: kotlinx.coroutines.Job? = null
    fun react(result: com.liferpg.data.ResultadoSerie) {
        announce(mentorReaction(result))
    }
    fun announce(text: String) {
        reactionJob?.cancel()
        reaction = text
        reactionJob = viewModelScope.launch { kotlinx.coroutines.delay(10000); reaction = null }
    }
    private val api = RemMentor.fromBuildConfig()
    val providerLabel: String get() = api.providerLabel
    fun clear() { if (!busy) { messages = emptyList(); actions = emptyList(); tokens = 0; error = null; draft = "" } }
    fun dismiss(action: MentorAction) { actions = actions - action }
    fun syncBudget(budget: MentorDailyBudget) { remainingToday = budget.remaining() }
    fun send(store: GeminiKey, installToken: MentorInstallToken, budget: MentorDailyBudget, vm: MainViewModel) {
        if (busy || draft.isBlank()) return
        val question = draft.trim().take(2000)
        busy = true; error = null; actions = emptyList(); draft = ""
        val pending = messages + MentorMessage("user", question)
        val snapshot = if (share) mentorSnapshot(vm, sourceScreen) else "Contexto do app desativado. Nenhum dado pessoal disponível."
        val catalog = mentorEligibleExercises(vm)
        viewModelScope.launch {
            try {
                budget.requireAvailable()
                val credential = withContext(Dispatchers.IO) {
                    if (api.usesProxy) installToken.getOrCreate() else store.read()
                }
                val result = api.ask(credential, pending, snapshot) { query ->
                    fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
                    val words = norm(query).split(" ").filter { it.length > 2 }
                    catalog.filter { ex -> words.isEmpty() || words.any { norm("${ex.nome} ${ex.musculo.nomeExibicao} ${ex.equipamento}").contains(it) } }
                        .take(16).joinToString("\n") { "id=${it.id}; ${it.nome}; ${it.musculo.nomeExibicao}; ${it.local.nomeExibicao}; ${it.modo}" }.ifBlank { "Nenhum resultado. Pesquise outro termo." }
                }
                actions = result.actions.filter { it.valid(catalog.map { ex -> ex.id }.toSet()) }
                val proposals = actions.joinToString { actionSummary(it, vm) }
                messages = (pending + MentorMessage("assistant", result.text + if (proposals.isNotBlank()) "\n\nPropostas: $proposals" else "")).takeLast(12)
                if (result.actions.size != actions.size) error = "Uma ação inválida foi descartada."
                tokens += result.tokens
                budget.recordSuccess()
                remainingToday = budget.remaining()
            } catch (e: CancellationException) { throw e
            } catch (e: MentorFailure) { error = e.explanation; draft = question
            } catch (_: Exception) { error = "Não consegui conectar a inteligência do REM. Confira sua internet e a configuração."; draft = question }
            finally { busy = false }
        }
    }
    fun execute(action: MentorAction, vm: MainViewModel, open: (String) -> Unit) {
        if (busy || action !in actions) return
        if (!action.valid(vm.exercicios.value.map { it.id }.toSet())) { error = "Essa ação não está mais disponível."; dismiss(action); return }
        if (action.kind == "start_workout" && !action.ids.all { id -> mentorEligibleExercises(vm).any { it.id == id } }) { error = "O treino não corresponde mais ao local ou às restrições das suas preferências. Peça uma nova proposta."; dismiss(action); return }
        if (vm.sessao.value?.concluida == false && action.kind.startsWith("start_")) { error = "Você já tem um treino em andamento. Retome ou finalize antes de iniciar outro."; return }
        val result = when(action.kind) {
            "navigate" -> { open(action.target); "Tela aberta: ${actionSummary(action, vm)}" }
            "rest" -> { vm.iniciarDescanso(action.seconds); "Descanso iniciado: ${action.seconds}s." }
            "cancel_rest" -> { vm.cancelarDescanso(); "Descanso encerrado." }
            "start_suggested" -> {
                if (vm.treinoSugerido.value.itens.isEmpty()) { error = "O plano ainda não tem exercícios disponíveis."; return }
                vm.iniciarSessaoSugerida(); open(RemnantRoute.SESSION); "Treino do dia iniciado."
            }
            "start_workout" -> { vm.iniciarSessaoDeIds(action.name, action.ids); open(RemnantRoute.SESSION); "Treino iniciado: ${action.name}." }
            else -> return
        }
        dismiss(action); error = null
        messages = (messages + MentorMessage("user", "Ação executada no app: $result")).takeLast(12)
    }
    fun propose(action: MentorAction) { if (!busy) actions = listOf(action) }
}

internal fun actionSummary(a: MentorAction, vm: MainViewModel): String = when(a.kind) {
    "rest" -> "Iniciar descanso de ${a.seconds}s"
    "cancel_rest" -> "Encerrar descanso"
    "start_suggested" -> "Iniciar ${vm.treinoSugerido.value.titulo}\n" + vm.treinoSugerido.value.itens.joinToString("\n") { "${it.exercicio.nome}: ${it.series} × ${it.repsRotulo}, descanso ${it.descansoSeg}s" }
    "start_workout" -> {
        val goal = Objetivo.seguro(vm.prefsEfetivas.value.objetivo)
        a.name + "\n" + a.ids.mapNotNull { id -> vm.exercicios.value.find { it.id == id } }.joinToString("\n") {
            val reps = if (it.modo == ModoMedida.TEMPO) "${goal.repMin * 3}–${goal.repMax * 3}s" else "${goal.repMin}–${goal.repMax} repetições"
            "${it.nome}: ${goal.seriesBase} × $reps, descanso ${goal.descansoSeg}s"
        }
    }
    else -> "Abrir " + (mapOf(RemnantRoute.tab(0) to "QG", RemnantRoute.tab(1) to "Patentes", RemnantRoute.tab(2) to "Arsenal", RemnantRoute.tab(3) to "Treino", RemnantRoute.tab(4) to "Perfil", RemnantRoute.tab(5) to "Amigos", RemnantRoute.FRIENDS to "Amigos", RemnantRoute.CIRCLES to "Círculos", RemnantRoute.PACTS to "Pactos", RemnantRoute.DUELS to "Duelos", RemnantRoute.PLAN to "plano do dia", RemnantRoute.SESSION to "treino em andamento", RemnantRoute.NUTRITION to "diário alimentar", RemnantRoute.WEIGHT to "peso corporal", RemnantRoute.TROPHIES to "troféus", RemnantRoute.TITLES to "títulos", RemnantRoute.SETTINGS to "preferências", RemnantRoute.ARENA to "Arena Global", RemnantRoute.HEALTH to "Health Connect", RemnantRoute.WGER to "biblioteca Wger", RemnantRoute.FOOD_SEARCH to "busca de alimentos", RemnantRoute.FOOD_SCANNER to "scanner nutricional", RemnantRoute.RECIPES to "receitas", RemnantRoute.TOOLS to "ferramentas de carga")[a.target]
        ?: vm.exercicios.value.find { RemnantRoute.exercise(it.id) == a.target }?.nome ?: a.target.removePrefix(RemnantRoute.MUSCLE_RANK_PREFIX))
}

internal fun mentorEligibleExercises(vm: MainViewModel): List<Exercicio> {
    val prefs = vm.prefsEfetivas.value
    val excluded = CondicaoSaude.musculosEvitados(prefs.condicoes)
    val avoidLoad = CondicaoSaude.evitarCargaAlta(prefs.condicoes)
    return vm.exercicios.value.filter {
        (prefs.localPreferido == LocalTreino.AMBOS.name || it.local == LocalTreino.AMBOS || it.local.name == prefs.localPreferido) &&
            it.musculo !in excluded && (!avoidLoad || it.modo != ModoMedida.CARGA)
    }
}

@Composable internal fun MentorScreen(vm: MainViewModel, open: (String) -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val store = remember { GeminiKey(appContext) }
    val installToken = remember { MentorInstallToken(appContext) }
    val budget = remember { MentorDailyBudget(appContext) }
    val mentor: MentorViewModel = viewModel()
    var configured by remember { mutableStateOf(RemMentor.proxyConfigured || store.configured()) }
    var settings by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var configError by remember { mutableStateOf<String?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val liveSession by vm.sessao.collectAsStateWithLifecycle()
    val liveRest by vm.descansoRestante.collectAsStateWithLifecycle()
    val liveCue = remember(liveSession, liveRest, mentor.sourceScreen) { mentorLocalCue(vm, mentor.sourceScreen) }
    LaunchedEffect(Unit) {
        mentor.syncBudget(budget)
    }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { ttsReady = it == TextToSpeech.SUCCESS }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { mentor.draft = it.take(2000) }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(mentor.messages.size, mentor.actions.size, mentor.busy) {
        if (mentor.messages.isNotEmpty() || mentor.actions.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(scroll).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF102C32),Fundo))).padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Eyebrow(if(mentor.busy) "REM / ANALISANDO SEU MOMENTO" else "REM / SEU COMPANHEIRO",Color(0xFF8BF4EE))
            MentorOrb(if(mentor.messages.isEmpty()) 128.dp else 70.dp,mentor.busy)
            Text(if(mentor.busy) "Estou montando seu próximo passo." else if(liveSession?.concluida==false) liveSession?.itemAtual?.item?.exercicio?.nome ?: "Treino em andamento" else "Seu treino. Minha atenção.",fontSize=23.sp,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Text(mentor.reaction ?: liveCue,Modifier.padding(horizontal=12.dp),fontSize=13.sp,color=TextoSecundario,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
        if ((liveRest ?: 0) > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${liveRest}s", Modifier.weight(1f), fontSize = 48.sp, fontWeight = FontWeight.Light, color = Neon)
                Eyebrow("RECUPERAÇÃO EM CURSO", Neon)
            }
            Text("Recupere o fôlego. Os controles permanecem na sua sessão de treino.", fontSize = 13.sp, color = TextoSecundario)
        }
        liveSession?.takeIf { !it.concluida }?.let { session ->
            HorizontalDivider(color = Borda)
            Eyebrow("AGORA / ${session.seriesFeitas} DE ${session.seriesPlanejadas} SÉRIES")
            Text(session.itemAtual?.item?.exercicio?.nome ?: session.nome, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Meter(session.progresso)
            Text("REM acompanha a sessão sem repetir os controles de execução aqui.", color = TextoSecundario, fontSize = 11.sp)
        }
        val commands=listOf(
            "Me guie agora" to "Analise a tela e minha sessão atual e me oriente no próximo passo com uma ação concreta.",
            "Montar treino" to "Monte um treino adequado às minhas preferências com exercícios do meu catálogo e prepare a ação para iniciar.",
            "Minha evolução" to "Analise os resultados dos exercícios e as patentes dos meus músculos. Explique meu próximo avanço.",
            "Alimentação" to "Analise meu diário alimentar registrado hoje e as metas. O que falta registrar ou ajustar?")
        commands.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label,prompt) ->
                    OutlinedButton(modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp),enabled=!mentor.busy,onClick={
                        mentor.draft=prompt
                        if(configured) mentor.send(store, installToken, budget, vm) else settings=true
                    }) { Text(label,fontSize=12.sp) }
                }
            }
        }
        TextButton(onClick = { settings = !settings }) { Text(if(settings) "Fechar ajustes" else "Conexão e contexto",color=TextoSecundario) }
        if(settings) Panel {
            Eyebrow("Conectado ao seu momento")
            Text("Treino, patentes, metas e alimentação. O REM envia apenas o resumo atual quando você pede uma análise. Reações da sessão continuam locais e não gastam cota.", fontSize = 12.sp, color = TextoSecundario)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(mentor.share, { mentor.clear(); mentor.share = it }, enabled = !mentor.busy)
                Text("Contexto do Remnant", Modifier.padding(start = 12.dp), fontSize = 14.sp)
            }
        }
        if ((settings || !configured) && !RemMentor.proxyConfigured) Panel {
            Text(if (configured) "Substituir chave Gemini" else "Ativar inteligência do REM", fontWeight = FontWeight.Bold)
            Text("Use uma chave gratuita criada no Google AI Studio. Ela fica protegida neste aparelho e não entra em backups.", fontSize = 12.sp, color = TextoSecundario)
            OutlinedTextField(key, { key = it }, label = { Text("Chave Gemini API") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = !mentor.busy)
            configError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Action("Conectar", !mentor.busy && (key.trim().startsWith("AIza") || key.trim().startsWith("AQ."))) {
                try { store.save(key.trim()); key = ""; configured = true; settings = false; configError = null }
                catch (_: Exception) { configError = "Essa chave Gemini não parece válida." }
            }
            if (configured) TextButton(enabled = !mentor.busy, onClick = { store.remove(); configured = false; mentor.clear() }) { Text("Desconectar e limpar conversa") }
        } else if (settings && RemMentor.proxyConfigured) Panel {
            Text("REM protegido pelo servidor", fontWeight = FontWeight.Bold)
            Text("Nenhuma chave de IA fica neste aparelho. O servidor controla o limite e pode trocar o modelo sem atualizar o app.", fontSize = 12.sp, color = TextoSecundario)
        }
        mentor.messages.forEach { message -> Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Eyebrow(if (message.role == "user") "Você / Remnant" else "REM", if (message.role == "user") TextoSecundario else Neon)
            MentorRichText(message.text)
            if (message.role == "assistant") TextButton(enabled = ttsReady, onClick = {
                val supported = tts?.setLanguage(Locale.forLanguageTag("pt-BR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (supported < 0) voiceError = "Instale uma voz em português nas configurações do celular."
                else tts?.speak(mentorPlainText(message.text), TextToSpeech.QUEUE_FLUSH, null, "rem-reply")
            }) { Text("Ouvir resposta") }
        } }
        mentor.actions.forEach { action -> HonorablePanel(com.liferpg.domain.Patente.PLATINA) {
            Eyebrow("Pronto para executar", Neon)
            Text(actionSummary(action, vm), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (action.kind == "start_suggested" || action.kind == "start_workout") Text("Usa as séries, repetições e descanso do seu objetivo. Uma sessão ativa será preservada.", color = TextoSecundario, fontSize = 12.sp)
            Action("Executar no Remnant", !mentor.busy) { mentor.execute(action, vm, open) }
            TextButton(enabled = !mentor.busy, onClick = { mentor.dismiss(action) }) { Text("Dispensar") }
        } }
        if (mentor.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        mentor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        voiceError?.let { Text(it, color = TextoSecundario) }
        Text("${mentor.tokens} tokens nesta conversa · ${mentor.providerLabel} · ${mentor.remainingToday} análises restantes hoje", fontSize = 11.sp, color = TextoSecundario)
        TextButton(enabled = !mentor.busy, onClick = { mentor.clear(); tts?.stop() }) { Text("Nova conversa") }
    }
    Column(Modifier.fillMaxWidth().background(Fundo).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF15363B),Color(0xFF142328))))
            .border(1.dp,Color(0xFF32636A),RoundedCornerShape(26.dp)).padding(horizontal=6.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(enabled=!mentor.busy,onClick={
                try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR").putExtra(RecognizerIntent.EXTRA_PROMPT,"Fale com REM"));voiceError=null }
                catch (_: Exception) { voiceError="Reconhecimento de voz indisponível. Você pode digitar." }
            }) { Icon(Icons.Outlined.Mic,"Falar com REM",tint=Color(0xFF8BF4EE)) }
            androidx.compose.foundation.text.BasicTextField(mentor.draft,{mentor.draft=it.take(2000)},
                modifier=Modifier.weight(1f).padding(vertical=12.dp),enabled=!mentor.busy,maxLines=4,
                textStyle=androidx.compose.ui.text.TextStyle(color=TextoPrimario,fontSize=15.sp,lineHeight=21.sp),
                cursorBrush=androidx.compose.ui.graphics.SolidColor(Color(0xFF8BF4EE)),
                decorationBox={ field ->
                    if(mentor.draft.isEmpty()) Text(if(mentor.busy) "REM está analisando..." else "Oriente meu próximo passo",fontSize=14.sp,color=TextoSecundario)
                    field()
                })
            IconButton(enabled=configured && mentor.remainingToday > 0 && !mentor.busy && mentor.draft.isNotBlank(),onClick={mentor.send(store,installToken,budget,vm)}) {
                Icon(Icons.Outlined.ArrowUpward,"Enviar para REM",tint=if(configured && mentor.remainingToday > 0 && !mentor.busy && mentor.draft.isNotBlank())Color(0xFF8BF4EE) else TextoSecundario.copy(alpha=.4f))
            }
        }
        if(!configured) TextButton(onClick={settings=true}) { Text("Conectar Gemini para conversar") }
    }
    }
}
